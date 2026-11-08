package com.dianping.service;

import com.dianping.entity.Payment;
import com.dianping.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes=TradeOrderIT.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class TradeOrderIT {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
    @MapperScan("com.dianping.mapper")
    @Import({OrderService.class,PaymentService.class,InventoryService.class,OutboxEventService.class,CartService.class})
    static class Config {}
    @Container static MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.0")
        .withCommand("--log-bin-trust-function-creators=1").withDatabaseName("trade_test").withUsername("trade").withPassword("test_only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",mysql::getJdbcUrl);
        r.add("spring.datasource.username",mysql::getUsername);
        r.add("spring.datasource.password",mysql::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size",()->24);
        r.add("spring.rabbitmq.username",()->"test");
        r.add("spring.rabbitmq.password",()->"test");
    }
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate db;
    @BeforeEach void seed() {
        db.execute("DROP TRIGGER IF EXISTS reject_movement");
        db.execute("DROP TRIGGER IF EXISTS reject_event");
        for(String table:List.of("refund","payment","inventory_movement","review","order_item","orders","outbox_event","cart_item","pickup_slot","dish","user","restaurant"))
            db.update("DELETE FROM "+table);
        db.update("INSERT INTO restaurant(id,name,status) VALUES(1,'Shop',1),(2,'Other',1)");
        db.update("INSERT INTO dish(id,restaurant_id,name,price,stock,status) VALUES(1,1,'Product',15,20,1)");
        for(int i=1;i<=40;i++) {
            db.update("INSERT INTO user(id,username,password) VALUES(?,?,?)",i,"buyer"+i,"test");
            db.update("INSERT INTO cart_item(user_id,dish_id,quantity) VALUES(?,1,1)",i);
        }
    }
    int number(String sql) {return db.queryForObject(sql,Integer.class);}
    Long order() {return orders.createOrder(1L,"request-1","Test address");}
    Payment paid(Long id) {
        Payment p=payments.create(1L,id);
        payments.callback(p.getId(),"channel-"+p.getId(),p.getAmount());
        return p;
    }
    void parallel(int count,IntConsumer action) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(20);
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<?>> tasks=new ArrayList<>();
            for(int i=1;i<=count;i++) {int n=i;tasks.add(pool.submit(()->{try {start.await();}catch(InterruptedException e){throw new RuntimeException(e);}action.accept(n);}));}
            start.countDown();
            for(Future<?> task:tasks) task.get(60,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentStockCannotOversell() throws Exception {
        parallel(40,i->{try {orders.createOrder((long)i,"buy","Address");}catch(BusinessException expected){assertTrue(expected.getMessage().contains("库存"));}});
        assertEquals(20,number("SELECT COUNT(*) FROM orders"));
        assertEquals(0,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(20,number("SELECT COUNT(*) FROM inventory_movement WHERE reason='ORDER'"));
    }
    @Test void sameKeyAndCartAreIdempotent() throws Exception {
        parallel(20,i->orders.createOrder(1L,"same","Address"));
        assertEquals(1,number("SELECT COUNT(*) FROM orders"));
        assertEquals(19,number("SELECT stock FROM dish WHERE id=1"));
        assertThrows(BusinessException.class,()->orders.createOrder(1L,"same","Changed"));
        assertThrows(BusinessException.class,()->orders.createOrder(1L,"different","Address"));
    }
    @Test void callbackChecksAmountAndDeduplicates() throws Exception {
        Long id=order();Payment p=payments.create(1L,id);
        assertThrows(BusinessException.class,()->payments.callback(p.getId(),"ref",BigDecimal.ONE));
        assertEquals(0,number("SELECT status FROM orders"));
        parallel(20,i->payments.callback(p.getId(),"ref",new BigDecimal("15.00")));
        assertEquals(1,number("SELECT status FROM orders"));
        assertEquals(1,number("SELECT COUNT(*) FROM payment WHERE status='SUCCEEDED'"));
        assertThrows(BusinessException.class,()->payments.callback(p.getId(),"different",p.getAmount()));
    }
    @Test void paymentAndCancellationHaveOneWinner() throws Exception {
        Long id=order();Payment p=payments.create(1L,id);
        parallel(20,i->{try {if(i%2==0) payments.callback(p.getId(),"ref",p.getAmount()); else orders.cancelOrder(1L,id);}catch(BusinessException expected){}});
        int status=number("SELECT status FROM orders");
        assertTrue(status==1 || status==5);
        assertEquals(status==1?19:20,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(status==1?1:0,number("SELECT COUNT(*) FROM payment WHERE status='SUCCEEDED'"));
    }
    @Test void expiredOrderCannotPayAndReleasesOnce() throws Exception {
        Long id=order();Payment p=payments.create(1L,id);
        db.update("UPDATE orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND)");
        assertThrows(BusinessException.class,()->payments.callback(p.getId(),"late",p.getAmount()));
        parallel(20,i->orders.timeoutCancelOrder(id));
        assertEquals(20,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(1,number("SELECT COUNT(*) FROM inventory_movement WHERE reason='CANCEL'"));
    }
    @Test void refundIsFullAndIdempotent() throws Exception {
        Long id=order();Payment p=paid(id);
        parallel(20,i->payments.refund(1L,id));
        payments.callback(p.getId(),"channel-"+p.getId(),p.getAmount());
        assertEquals(6,number("SELECT status FROM orders"));
        assertEquals(20,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(1,number("SELECT COUNT(*) FROM refund"));
        assertThrows(BusinessException.class,()->orders.ship(id,1L));
    }
    @Test void shipAndRefundHaveOneWinner() throws Exception {
        Long id=order();paid(id);
        parallel(20,i->{try {if(i%2==0) payments.refund(1L,id);else orders.ship(id,1L);}catch(BusinessException expected){}});
        int state=number("SELECT status FROM orders");
        assertTrue(state==3 || state==6);
        assertEquals(state==6?20:19,number("SELECT stock FROM dish WHERE id=1"));
    }
    @Test void fulfillmentChecksOwnership() {
        Long id=order();paid(id);
        assertThrows(BusinessException.class,()->orders.ship(id,2L));
        orders.ship(id,1L);
        assertThrows(BusinessException.class,()->payments.refund(1L,id));
        assertThrows(BusinessException.class,()->orders.receive(id,2L));
        orders.receive(id,1L);orders.receive(id,1L);
        assertEquals(4,number("SELECT status FROM orders"));
    }
    @Test void movementFailureRollsBackEntireRefund() {
        Long id=order();paid(id);
        db.execute("CREATE TRIGGER reject_movement BEFORE INSERT ON inventory_movement FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'");
        assertThrows(Exception.class,()->payments.refund(1L,id));
        assertEquals(1,number("SELECT status FROM orders"));
        assertEquals(19,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(0,number("SELECT COUNT(*) FROM refund"));
        assertEquals(1,number("SELECT COUNT(*) FROM payment WHERE status='SUCCEEDED'"));
    }
    @Test void outboxFailureRollsBackOrderStockAndCart() {
        db.execute("CREATE TRIGGER reject_event BEFORE INSERT ON outbox_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'");
        assertThrows(Exception.class,this::order);
        assertEquals(0,number("SELECT COUNT(*) FROM orders"));
        assertEquals(20,number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(1,number("SELECT COUNT(*) FROM cart_item WHERE user_id=1"));
    }
    @Test void adjustmentIsOwnedIdempotentAndCannotGoNegative() throws Exception {
        parallel(20,i->inventory.adjust(1L,1L,"restock",5));
        assertEquals(25,number("SELECT stock FROM dish WHERE id=1"));
        assertThrows(BusinessException.class,()->inventory.adjust(1L,1L,"restock",6));
        assertThrows(BusinessException.class,()->inventory.adjust(2L,1L,"other",5));
        assertThrows(BusinessException.class,()->inventory.adjust(1L,1L,"negative",-26));
        assertEquals(25,number("SELECT stock FROM dish WHERE id=1"));
    }
}
