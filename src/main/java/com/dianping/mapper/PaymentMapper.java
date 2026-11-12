package com.dianping.mapper;
import com.dianping.entity.Payment;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PaymentMapper {
    @Select("""
        SELECT p.id,p.order_id,p.amount,u.nickname AS buyer_name,r.name AS merchant_name
        FROM payment p JOIN orders o ON o.id=p.order_id
        JOIN user u ON u.id=o.user_id JOIN restaurant r ON r.id=o.restaurant_id
        WHERE p.status='PENDING' AND o.status=0 AND o.expires_at>NOW()
        ORDER BY p.id DESC LIMIT 100
        """)
    java.util.List<com.dianping.vo.AdminPaymentVO> pendingForAdmin();
    // 必须使用当前读，不能复用等待订单锁之前建立的可重复读快照。
    @Select("SELECT * FROM payment WHERE order_id=#{orderId} FOR UPDATE")
    Payment byOrder(Long orderId);
    @Select("SELECT * FROM payment WHERE id=#{id}")
    Payment byId(Long id);
    @Insert("INSERT INTO payment(order_id,amount,status) VALUES(#{orderId},#{amount},'PENDING')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Payment payment);
    @Update("UPDATE payment SET status='SUCCEEDED',channel_reference=#{reference} WHERE id=#{id} AND status='PENDING'")
    int succeed(Long id,String reference);
    @Update("UPDATE payment SET status='REFUNDED' WHERE id=#{id} AND status='SUCCEEDED'")
    int refund(Long id);
    @Insert("INSERT INTO refund(order_id,payment_id,amount,status) VALUES(#{orderId},#{id},#{amount},'SUCCEEDED')")
    int insertRefund(Payment payment);
}
