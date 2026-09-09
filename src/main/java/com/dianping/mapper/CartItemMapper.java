package com.dianping.mapper;

import com.dianping.entity.CartItem;
import com.dianping.vo.CartItemVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CartItemMapper {

    @Select("""
        SELECT *
        FROM cart_item
        WHERE user_id = #{userId}
          AND dish_id = #{dishId}
        """)
    CartItem selectByUserIdAndDishId(Long userId, Long dishId);

    @Insert("""
        INSERT INTO cart_item(user_id, dish_id, quantity)
        VALUES(#{userId}, #{dishId}, #{quantity})
        """)
    int insert(CartItem cartItem);

    @Update("""
        UPDATE cart_item
        SET quantity = #{quantity}
        WHERE id = #{id}
        """)
    int updateQuantity(CartItem cartItem);

    @Select("""
        SELECT
            c.id,
            c.dish_id AS dishId,
            d.name AS dishName,
            d.price,
            c.quantity
        FROM cart_item c
        JOIN dish d ON c.dish_id = d.id
        WHERE c.user_id = #{userId}
    """)
    List<CartItemVO> selectCartByUserId(Long userId);

    @Update("""
        UPDATE cart_item
        SET quantity = #{quantity}
        WHERE id = #{id}
          AND user_id = #{userId}
        """)
    int updateQuantityById(Long id, Long userId, Integer quantity);

    @Delete("""
        DELETE FROM cart_item
        WHERE id = #{id}
          AND user_id = #{userId}
        """)
    int deleteById(Long id, Long userId);

    @Delete("""
        DELETE FROM cart_item
        WHERE user_id = #{userId}
        """)
    int deleteByUserId(Long userId);
}