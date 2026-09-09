package com.dianping.mapper;

import com.dianping.entity.Dish;
import com.dianping.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("""
        INSERT INTO orders(user_id, restaurant_id, total_amount, status)
        VALUES(#{userId}, #{restaurantId}, #{totalAmount}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Select("""
        SELECT *
        FROM orders
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        """)
    List<Order> selectByUserId(Long userId);

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
            SET status = 1
            WHERE id = #{orderId}
              AND user_id = #{userId}
              AND status = 0
            """)
    int payOrder(Long orderId, Long userId);

    @Update("""
            UPDATE orders
            SET status = 5
            WHERE id = #{orderId}
              AND user_id = #{userId}
              AND status = 0
            """)
    int cancelOrder(Long orderId, Long userId);

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

    @Select("""
    SELECT *
    FROM dish
    WHERE restaurant_id = #{restaurantId}
      AND status = 1
      AND deleted = 0
    """)
    List<Dish> selectByRestaurantId(Long restaurantId);

    @Select("""
            SELECT *
            FROM orders
            WHERE restaurant_id = #{restaurantId}
            ORDER BY created_at DESC
            LIMIT #{pageSize}
            OFFSET #{offset}
            """)
    List<Order> selectByRestaurantIdPage(
            Long restaurantId,
            Integer offset,
            Integer pageSize
    );

    @Select("""
            SELECT COUNT(*)
            FROM orders
            WHERE restaurant_id = #{restaurantId}
            """)
    Long countByRestaurantId(Long restaurantId);

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
              AND status = 4
            """)
    Order selectCompletedOrderByUser(
            Long orderId,
            Long userId
    );
}