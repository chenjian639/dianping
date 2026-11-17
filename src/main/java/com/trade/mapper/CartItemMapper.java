package com.trade.mapper;

import com.trade.entity.CartItem;
import com.trade.vo.CartItemVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CartItemMapper {

    @Select("""
        SELECT *
        FROM cart_item
        WHERE user_id = #{userId}
          AND product_id = #{productId}
        """)
    CartItem selectByUserIdAndProductId(Long userId, Long productId);

    @Insert("""
        INSERT INTO cart_item(user_id, product_id, quantity)
        VALUES(#{userId}, #{productId}, #{quantity})
        """)
    int insert(CartItem cartItem);

    @Update("""
    UPDATE cart_item
    SET quantity = #{quantity}
    WHERE id = #{cartItemId}
      AND user_id = #{userId}
    """)
    int updateQuantity(
            Long cartItemId,
            Long userId,
            Integer quantity
    );

    @Select("""
        SELECT
            c.id,
            c.product_id AS productId,
            d.name AS productName,
            d.price,
            c.quantity
        FROM cart_item c
        JOIN product d ON c.product_id = d.id
        WHERE c.user_id = #{userId}
    """)
    List<CartItemVO> selectCartByUserId(Long userId);

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

    @Select("""
    SELECT DISTINCT d.merchant_id
    FROM cart_item c
    JOIN product d ON c.product_id = d.id
    WHERE c.user_id = #{userId}
    """)
    List<Long> selectMerchantIdsByUserId(Long userId);

    @Select("""
    SELECT *
    FROM cart_item
    WHERE id = #{cartItemId}
      AND user_id = #{userId}
    """)
    CartItem selectByIdAndUserId(
            Long cartItemId,
            Long userId
    );
}