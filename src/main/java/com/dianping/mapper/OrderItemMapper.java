package com.dianping.mapper;

import com.dianping.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("""
        INSERT INTO order_item(order_id, dish_id, dish_name, price, quantity)
        VALUES(#{orderId}, #{dishId}, #{dishName}, #{price}, #{quantity})
        """)
    int insert(OrderItem orderItem);

    @Select("""
        SELECT *
        FROM order_item
        WHERE order_id = #{orderId}
        """)
    List<OrderItem> selectByOrderId(Long orderId);
}