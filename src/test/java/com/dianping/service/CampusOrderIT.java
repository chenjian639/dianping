package com.dianping.service;

import com.dianping.entity.Order;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OrderMapper;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL transactions and constraints; no mock database, no skipped Docker failures. */
@Testcontainers
@SpringBootTest(classes = CampusOrderIT.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CampusOrderIT {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
    @MapperScan("com.dianping.mapper")
    @Import({OrderService.class, OutboxEventService.class, RestaurantService.class,
            RestaurantDatabaseService.class, ReviewService.class, RestaurantLocalCacheService.class,
            com.dianping.config.LocalCacheConfig.class,
            com.dianping.listener.RestaurantLocalCacheInvalidationListener.class,
            CartService.class, PickupSlotService.class})
    static class Config {}

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("campus_test").withUsername("campus").withPassword("campus_test_only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 24);
        r.add("spring.rabbitmq.username", () -> "test");
        r.add("spring.rabbitmq.password", () -> "test");
    }
    @org.springframework.boot.test.mock.mockito.MockBean RestaurantCacheService redis;
    @Autowired RestaurantService restaurants;
    @Autowired ReviewService reviews;
    @Autowired CartService carts;
    @Autowired OutboxEventService events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.github.benmanes.caffeine.cache.Cache<Long,com.dianping.vo.RestaurantDetailVO> cache;
    @Autowired OrderService service;
    @Autowired OrderMapper orders;
    @Autowired JdbcTemplate db;

    @BeforeEach void seed() {
        cache.invalidateAll();
        for (String table : List.of("review", "order_item", "orders", "outbox_event", "cart_item", "pickup_slot", "dish", "user", "restaurant"))
            db.update("DELETE FROM " + table);
        db.update("INSERT INTO restaurant(id,name,status) VALUES(1,'Campus canteen',1),(2,'Other canteen',1)");
        db.update("INSERT INTO dish(id,restaurant_id,name,price,stock,status) VALUES(1,1,'Lunch',15,100,1)");
        db.update("INSERT INTO pickup_slot(id,restaurant_id,starts_at,ends_at,capacity) VALUES(1,1,DATE_ADD(NOW(),INTERVAL 2 HOUR),DATE_ADD(NOW(),INTERVAL 3 HOUR),20)");
        for (int i = 1; i <= 100; i++) {
            db.update("INSERT INTO user(id,username,password) VALUES(?,?,?)", i, "student" + i, "test");
            db.update("INSERT INTO cart_item(user_id,dish_id,quantity) VALUES(?,1,1)", i);
        }
    }
    private int number(String sql) { return db.queryForObject(sql, Integer.class); }
    private void parallel(int count, IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(24);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                int user = i;
                jobs.add(pool.submit(() -> { try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } action.accept(user); }));
            }
            start.countDown();
            for (Future<?> job : jobs) job.get(60, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @Test void hundredRequestsCannotExceedTwentySlots() throws Exception {
        parallel(100, i -> {
            try { service.createOrder((long)i, "capacity-" + i, 1L); }
            catch (BusinessException e) { assertTrue(e.getMessage().contains("时段")); }
        });
        assertEquals(20, number("SELECT COUNT(*) FROM orders"));
        assertEquals(20, number("SELECT reserved FROM pickup_slot WHERE id=1"));
        assertEquals(80, number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(20, number("SELECT COUNT(*) FROM outbox_event"));
    }

    @Test void lastDishOnlySoldOnceAndFailedOrdersReleaseSlot() throws Exception {
        db.update("UPDATE dish SET stock=1 WHERE id=1");
        parallel(30, i -> {
            try { service.createOrder((long)i, "stock-" + i, 1L); }
            catch (BusinessException e) { assertTrue(e.getMessage().contains("库存不足")); }
        });
        assertEquals(1, number("SELECT COUNT(*) FROM orders"));
        assertEquals(1, number("SELECT reserved FROM pickup_slot WHERE id=1"));
        assertEquals(0, number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(99, number("SELECT COUNT(*) FROM cart_item"));
    }

    @Test void sameRequestReturnsSameOrderUnderConcurrency() throws Exception {
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        parallel(30, i -> ids.add(service.createOrder(1L, "repeat", 1L)));
        assertEquals(1, ids.size());
        assertEquals(1, number("SELECT reserved FROM pickup_slot WHERE id=1"));
        assertEquals(99, number("SELECT stock FROM dish WHERE id=1"));
        assertThrows(BusinessException.class, () -> service.createOrder(1L,"repeat",2L));
    }

    @Test void differentRequestsCannotBuySameCartTwice() throws Exception {
        parallel(20, i -> {
            try { service.createOrder(1L,"different-" + i,1L); }
            catch (BusinessException e) { assertEquals("购物车为空", e.getMessage()); }
        });
        assertEquals(1, number("SELECT COUNT(*) FROM orders"));
    }

    @Test void wrongRestaurantAndExpiredSlotsCannotReserve() {
        db.update("UPDATE restaurant SET status=0 WHERE id=1");
        assertThrows(BusinessException.class, () -> service.createOrder(1L,"closed",1L));
        db.update("UPDATE restaurant SET status=1 WHERE id=1");
        db.update("UPDATE pickup_slot SET restaurant_id=2 WHERE id=1");
        assertThrows(BusinessException.class, () -> service.createOrder(1L,"wrong",1L));
        db.update("UPDATE pickup_slot SET restaurant_id=1,starts_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=1");
        assertThrows(BusinessException.class, () -> service.createOrder(1L,"late",1L));
        assertEquals(0, number("SELECT reserved FROM pickup_slot WHERE id=1"));
        assertEquals(100, number("SELECT stock FROM dish WHERE id=1"));
    }

    @Test void repeatedTimeoutAndCancelReleaseExactlyOnce() throws Exception {
        Long id = service.createOrder(1L,"timeout",1L);
        db.update("UPDATE orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?", id);
        parallel(20, i -> {
            if (i % 2 == 0) service.timeoutCancelOrder(id);
            else { try { service.cancelOrder(1L,id); } catch (BusinessException e) { assertTrue(e.getMessage().contains("取消")); } }
        });
        assertEquals(5, orders.selectById(id).getStatus());
        assertEquals(100, number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(0, number("SELECT reserved FROM pickup_slot WHERE id=1"));
    }

    @Test void earlyTimeoutDoesNothingAndExpiredPaymentIsRejected() {
        Long id = service.createOrder(1L,"deadline",1L);
        service.timeoutCancelOrder(id);
        assertEquals(0, orders.selectById(id).getStatus());
        db.update("UPDATE orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);
        assertThrows(BusinessException.class, () -> service.payOrder(1L,id));
        service.timeoutCancelOrder(id);
        assertEquals(5, orders.selectById(id).getStatus());
    }

    @Test void paymentRacingCancellationHasConsistentInventory() throws Exception {
        Long id = service.createOrder(1L,"race",1L);
        parallel(20, i -> {
            try { if (i%2==0) service.payOrder(1L,id); else service.cancelOrder(1L,id); }
            catch (BusinessException e) { /* Only one conditional transition wins. */ }
        });
        int status = orders.selectById(id).getStatus();
        assertTrue(status==1 || status==5);
        assertEquals(status==1 ? 99 : 100, number("SELECT stock FROM dish WHERE id=1"));
        assertEquals(status==1 ? 1 : 0, number("SELECT reserved FROM pickup_slot WHERE id=1"));
    }

    @Test void pickupRequiresCorrectMerchantCodeAndReadyState() throws Exception {
        Long id = service.createOrder(1L,"pickup",1L);
        Order order = orders.selectById(id);
        assertNotNull(order.getExpiresAt());
        assertTrue(order.getPickupCode().matches("[a-f0-9]{32}"));
        assertThrows(BusinessException.class, () -> service.completeOrder(id,1L,order.getPickupCode()));
        service.payOrder(1L,id); service.startPreparing(id,1L); service.markReady(id,1L);
        assertThrows(BusinessException.class, () -> service.completeOrder(id,2L,order.getPickupCode()));
        assertThrows(BusinessException.class, () -> service.completeOrder(id,1L,"bad"));
        parallel(10, i -> { try { service.completeOrder(id,1L,order.getPickupCode()); } catch (BusinessException e) { assertTrue(e.getMessage().contains("取餐")); } });
        assertEquals(4, orders.selectById(id).getStatus());
        assertEquals(1, number("SELECT reserved FROM pickup_slot WHERE id=1"));
        assertEquals(99, number("SELECT stock FROM dish WHERE id=1"));
    }

    @Test void cancellationRollsBackIfCapacityReleaseFails() {
        Long id = service.createOrder(1L,"rollback",1L);
        db.update("UPDATE pickup_slot SET reserved=0 WHERE id=1");
        assertThrows(BusinessException.class, () -> service.cancelOrder(1L,id));
        assertEquals(0, orders.selectById(id).getStatus());
        assertEquals(99, number("SELECT stock FROM dish WHERE id=1"));
    }

    @Test void restaurantCacheInvalidationOccursOnlyAfterCommit() {
        var cached = new com.dianping.vo.RestaurantDetailVO();
        cached.setId(1L);
        cached.setStatus(1);
        cache.put(1L,cached);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            restaurants.updateRestaurantStatus(1L,0);
            assertSame(cached,cache.getIfPresent(1L), "Do not evict before the business commit");
            status.setRollbackOnly();
        });
        assertEquals(1,number("SELECT status FROM restaurant WHERE id=1"));
        assertEquals(0,number("SELECT COUNT(*) FROM outbox_event"));
        assertSame(cached,cache.getIfPresent(1L));
        restaurants.updateRestaurantStatus(1L,0);
        assertNull(cache.getIfPresent(1L));
        assertEquals(1,number("SELECT COUNT(*) FROM outbox_event WHERE event_type='RESTAURANT_CACHE_EVICT'"));
    }

    @Test void outboxInsertFailureRollsBackRestaurantAndReviewChanges() {
        Long order = service.createOrder(1L,"review-rollback",1L);
        db.update("UPDATE orders SET status=4 WHERE id=?",order);
        var cached = new com.dianping.vo.RestaurantDetailVO();
        cache.put(1L,cached);
        db.execute("ALTER TABLE outbox_event ADD CONSTRAINT test_reject_outbox CHECK (event_type <> 'RESTAURANT_CACHE_EVICT')");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> restaurants.updateRestaurantStatus(1L,0));
            assertEquals(1,number("SELECT status FROM restaurant WHERE id=1"));
            var dto = new com.dianping.dto.CreateReviewDTO();
            dto.setOrderId(order); dto.setRating(5); dto.setContent("test");
            assertThrows(org.springframework.dao.DataAccessException.class, () -> reviews.createReview(1L,dto));
            assertEquals(0,number("SELECT COUNT(*) FROM review"));
            assertEquals(0,number("SELECT rating_sum FROM restaurant WHERE id=1"));
            assertEquals(0,number("SELECT review_count FROM restaurant WHERE id=1"));
            assertSame(cached,cache.getIfPresent(1L));
            org.mockito.Mockito.verifyNoInteractions(redis);
        } finally { db.execute("ALTER TABLE outbox_event DROP CHECK test_reject_outbox"); }
    }

    @Test void ratingsUseExactTotalsAndAllQueriesAgree() {
        int[] ratings = {1,1,2,1};
        for (int i=0; i<ratings.length; i++) {
            long user=i+1;
            Long order=service.createOrder(user,"rating-"+i,1L);
            db.update("UPDATE orders SET status=4 WHERE id=?",order);
            var cached=new com.dianping.vo.RestaurantDetailVO();
            cache.put(1L,cached);
            var dto=new com.dianping.dto.CreateReviewDTO();
            dto.setOrderId(order); dto.setRating(ratings[i]); dto.setContent("test");
            reviews.createReview(user,dto);
            assertNull(cache.getIfPresent(1L));
        }
        org.mockito.Mockito.verifyNoInteractions(redis);
        assertEquals(5,number("SELECT rating_sum FROM restaurant WHERE id=1"));
        assertEquals(4,number("SELECT review_count FROM restaurant WHERE id=1"));
        var expected=new java.math.BigDecimal("1.3");
        assertEquals(expected,reviews.getRestaurantReviewSummary(1L).getAverageRating());
        assertEquals(expected,restaurants.getRestaurantDetail(1L).getAverageRating());
        assertEquals(expected,restaurants.searchRestaurants("","rating",1,10).getRecords().get(0).getAverageRating());
        assertEquals(4,number("SELECT COUNT(*) FROM outbox_event WHERE event_type='RESTAURANT_CACHE_EVICT'"));
    }

    @Test void cartUpdatesUseOneValidatedBusinessEntry() {
        Long item=db.queryForObject("SELECT id FROM cart_item WHERE user_id=1",Long.class);
        var dto=new com.dianping.dto.UpdateCartItemDTO();
        dto.setQuantity(101);
        assertThrows(BusinessException.class, () -> carts.updateQuantity(1L,item,dto));
        dto.setQuantity(2);
        db.update("UPDATE dish SET status=0 WHERE id=1");
        assertThrows(BusinessException.class, () -> carts.updateQuantity(1L,item,dto));
        db.update("UPDATE dish SET status=1 WHERE id=1");
        carts.updateQuantity(1L,item,dto);
        assertEquals(2,number("SELECT quantity FROM cart_item WHERE id="+item));
        assertThrows(BusinessException.class, () -> carts.updateQuantity(2L,item,dto));
    }

    @Test void outboxRecorderRequiresABusinessTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> events.restaurantChanged(1L));
        assertEquals(0,number("SELECT COUNT(*) FROM outbox_event"));
    }

    @Test void existingLegacyDatabaseIsBaselinedAndOrderHistoryIsPreserved() {
        // Only the disposable Testcontainers database. Simulate the previous release with no Flyway history.
        var legacy = org.flywaydb.core.Flyway.configure()
                .dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())
                .cleanDisabled(false).target("1").load();
        legacy.clean();
        legacy.migrate();
        db.update("INSERT INTO user(id,username,password) VALUES(1,'legacy','test')");
        db.update("INSERT INTO restaurant(id,name) VALUES(1,'Legacy canteen')");
        db.update("INSERT INTO orders(id,user_id,restaurant_id,total_amount,status,request_id) VALUES(1,1,1,12,0,'legacy-order')");
        int[] legacyRatings={1,1,2,1};
        for(int i=0;i<legacyRatings.length;i++) {
            db.update("INSERT INTO orders(id,user_id,restaurant_id,total_amount,status,request_id) VALUES(?,1,1,12,4,?)",i+2,"legacy-review-"+i);
            db.update("INSERT INTO review(user_id,restaurant_id,order_id,rating) VALUES(1,1,?,?)",i+2,legacyRatings[i]);
        }
        db.update("UPDATE restaurant SET average_rating=1.2,review_count=4 WHERE id=1");
        db.execute("DROP TABLE flyway_schema_history");
        var upgraded = org.flywaydb.core.Flyway.configure()
                .dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())
                .baselineOnMigrate(true).baselineVersion("1").load();
        upgraded.migrate();
        assertEquals(5,number("SELECT rating_sum FROM restaurant WHERE id=1"));
        assertEquals(new java.math.BigDecimal("1.3"),db.queryForObject("SELECT average_rating FROM restaurant WHERE id=1",java.math.BigDecimal.class));
        Order old = orders.selectById(1L);
        assertEquals("legacy-order",old.getRequestId());
        assertNull(old.getPickupSlotId());
        assertNotNull(old.getExpiresAt());
        db.update("UPDATE orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=1");
        service.timeoutCancelOrder(1L);
        assertEquals(5,orders.selectById(1L).getStatus());
    }
}
