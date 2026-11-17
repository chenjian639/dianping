package com.trade.mapper;

import com.trade.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("""
        INSERT INTO order_item(order_id, product_id, product_name, price, quantity)
        VALUES(#{orderId}, #{productId}, #{productName}, #{price}, #{quantity})
        """)
    int insert(OrderItem orderItem);

    @Select("""
        SELECT *
        FROM order_item
        WHERE order_id = #{orderId}
        """)
    List<OrderItem> selectByOrderId(Long orderId);
}