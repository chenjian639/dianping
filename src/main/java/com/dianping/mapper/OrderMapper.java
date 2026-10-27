package com.dianping.mapper;

import com.dianping.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;
@Mapper
public interface OrderMapper {
    @Select("SELECT * FROM orders WHERE id = #{id}")
    Order selectById(Long id);
    @Update("UPDATE orders SET status = 4 WHERE id = #{id} AND restaurant_id = #{restaurantId} AND status = 3 AND pickup_code = #{code}")
    int verifyPickup(Long id, Long restaurantId, String code);


    @Insert("""
    INSERT INTO orders(
        user_id,
        restaurant_id,
        total_amount,
        status,
        request_id, pickup_slot_id, pickup_code, expires_at
    )
    VALUES(
        #{userId},
        #{restaurantId},
        #{totalAmount},
        #{status},
        #{requestId}, #{pickupSlotId}, #{pickupCode},
        LEAST(DATE_ADD(NOW(), INTERVAL 30 MINUTE), (SELECT starts_at FROM pickup_slot WHERE id = #{pickupSlotId}))
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
              AND restaurant_id = #{restaurantId}
              AND status = #{currentStatus}
            """)
    int updateStatusByRestaurant(
            Long orderId,
            Long restaurantId,
            Integer currentStatus,
            Integer newStatus
    );

    List<Order> selectMerchantOrdersPage(
            Long restaurantId,
            Integer status,
            Integer offset,
            Integer pageSize
    );

    Long countMerchantOrders(
            Long restaurantId,
            Integer status
    );

    @Select("""
            SELECT *
            FROM orders
            WHERE id = #{orderId}
              AND restaurant_id = #{restaurantId}
            """)
    Order selectByIdAndRestaurantId(
            Long orderId,
            Long restaurantId
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
