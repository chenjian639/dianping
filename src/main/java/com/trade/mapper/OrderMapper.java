package com.trade.mapper;

import com.trade.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;
@Mapper
public interface OrderMapper {
    @Select("SELECT (status=0 AND expires_at > NOW()) FROM orders WHERE id=#{id}")
    boolean isPayable(Long id);
    @Select("SELECT * FROM orders WHERE status=7 ORDER BY id LIMIT 100")
    List<Order> refundingOrders();

    @Select("SELECT * FROM orders WHERE id=#{id} FOR UPDATE")
    Order lockById(Long id);

    @Update("UPDATE orders SET status=#{next} WHERE id=#{id} AND status=#{expected}")
    int transition(Long id, int expected, int next);
    @Select("SELECT * FROM orders WHERE id = #{id}")
    Order selectById(Long id);



    @Insert("""
    INSERT INTO orders(
        user_id,
        merchant_id,
        total_amount,
        status,
        request_id, shipping_address, goods_amount, freight_amount, expires_at
    )
    VALUES(
        #{userId},
        #{merchantId},
        #{totalAmount},
        #{status},
        #{requestId}, #{shippingAddress}, #{goodsAmount}, #{freightAmount}, DATE_ADD(NOW(), INTERVAL 30 MINUTE)
    )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("""
    SELECT *
    FROM orders
    WHERE user_id = #{userId}
      AND request_id = #{requestId}
    LIMIT 1
    """)
    Order selectByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    @Select("""
            SELECT *
            FROM orders
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{pageSize}
            OFFSET #{offset}
            """)
    List<Order> selectByUserIdPage(
            Long userId,
            Integer offset,
            Integer pageSize
    );

    @Select("""
            SELECT COUNT(*)
            FROM orders
            WHERE user_id = #{userId}
            """)
    Long countByUserId(Long userId);

    @Select("""
        SELECT *
        FROM orders
        WHERE id = #{orderId}
        AND user_id = #{userId}
        """)
    Order selectByIdAndUserId(Long orderId, Long userId);

    @Update("""
    UPDATE orders
    SET status = #{newStatus}
    WHERE id = #{orderId}
      AND user_id = #{userId}
      AND status = #{currentStatus}
      AND expires_at > NOW()
    """)
    int payOrder(
            Long orderId,
            Long userId,
            Integer currentStatus,
            Integer newStatus
    );

    @Update("""
    UPDATE orders
    SET status = #{newStatus}
    WHERE id = #{orderId}
      AND user_id = #{userId}
      AND status = #{currentStatus}
    """)
    int cancelOrder(
            Long orderId,
            Long userId,
            Integer currentStatus,
            Integer newStatus
    );

    @Update("""
            UPDATE orders
            SET status = #{newStatus}
            WHERE id = #{orderId}
              AND merchant_id = #{merchantId}
              AND status = #{currentStatus}
            """)
    int updateStatusByMerchant(
            Long orderId,
            Long merchantId,
            Integer currentStatus,
            Integer newStatus
    );

    List<Order> selectMerchantOrdersPage(
            Long merchantId,
            Integer status,
            Integer offset,
            Integer pageSize
    );

    Long countMerchantOrders(
            Long merchantId,
            Integer status
    );

    @Select("""
            SELECT *
            FROM orders
            WHERE id = #{orderId}
              AND merchant_id = #{merchantId}
            """)
    Order selectByIdAndMerchantId(
            Long orderId,
            Long merchantId
    );

    @Select("""
            SELECT *
            FROM orders
            WHERE id = #{orderId}
              AND user_id = #{userId}
              AND status = #{status}
            """)
    Order selectCompletedOrderByUser(
            Long orderId,
            Long userId,
            Integer status
    );

    @Update("""
    UPDATE orders
    SET status = #{newStatus}
    WHERE id = #{orderId}
      AND status = #{currentStatus}
      AND expires_at <= NOW()
    """)
    int timeoutCancelOrder(
            @Param("orderId") Long orderId,
            @Param("currentStatus") Integer currentStatus,
            @Param("newStatus") Integer newStatus
    );

    @Select("""
    SELECT id
    FROM orders
    WHERE status = #{status}
      AND expires_at <= NOW()
    ORDER BY id
    LIMIT #{limit}
    """)
    List<Long> selectExpiredPendingOrderIds(
            @Param("status") Integer status,
            @Param("limit") Integer limit
    );
}
