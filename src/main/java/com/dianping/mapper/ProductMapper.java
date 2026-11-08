package com.dianping.mapper;

import com.dianping.entity.Product;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper {
    @Select("SELECT * FROM dish WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Product lockById(Long id);

    @Select("""
        SELECT *
        FROM dish
        WHERE restaurant_id = #{restaurantId}
          AND status = 1
          AND deleted = 0
        """)
    List<Product> selectByRestaurantId(Long restaurantId);

    @Select("""
    SELECT *
    FROM dish
    WHERE id = #{id}
      AND deleted = 0
    """)
    Product selectById(Long id);

    @Update("""
        UPDATE dish
        SET stock = stock - #{quantity}
        WHERE id = #{dishId}
          AND stock >= #{quantity}
        AND status = 1
        AND deleted = 0
        """)
    int decreaseStock(Long dishId, Integer quantity);

    @Update("""
        UPDATE dish
        SET stock = stock + #{quantity}
        WHERE id = #{dishId}
        """)
    int increaseStock(Long dishId, Integer quantity);

    @Insert("""
    INSERT INTO dish(
        restaurant_id,
        name,
        description,
        price,
        stock,
        status
    )
    VALUES(
        #{restaurantId},
        #{name},
        #{description},
        #{price},
        #{stock},
        #{status}
    )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product dish);

    @Update("""
            UPDATE dish
            SET name = #{name},
                description = #{description},
                price = #{price}
            WHERE id = #{id}
              AND restaurant_id = #{restaurantId}
            AND deleted = 0
            """)
    int updateProduct(
            Long id,
            Long restaurantId,
            String name,
            String description,
            BigDecimal price
    );

    @Update("""
            UPDATE dish
            SET status = #{status}
            WHERE id = #{dishId}
              AND restaurant_id = #{restaurantId}
            AND deleted = 0
            """)
    int updateStatus(
            Long dishId,
            Long restaurantId,
            Integer status
    );

    @Update("""
    UPDATE dish
    SET deleted = 1,
        status = 0
    WHERE id = #{dishId}
      AND restaurant_id = #{restaurantId}
      AND deleted = 0
    """)
    int deleteProduct(
            Long dishId,
            Long restaurantId
    );

    List<Product> selectMerchantProductsPage(
            Long restaurantId,
            Integer status,
            Integer offset,
            Integer pageSize
    );

    Long countMerchantProducts(
            Long restaurantId,
            Integer status
    );
}
