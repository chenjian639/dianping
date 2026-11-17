package com.trade.mapper;

import com.trade.entity.Product;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper {
    @Select("SELECT * FROM product WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Product lockById(Long id);

    @Select("""
        SELECT *
        FROM product
        WHERE merchant_id = #{merchantId}
          AND status = 1
          AND deleted = 0
        """)
    List<Product> selectByMerchantId(Long merchantId);

    @Select("""
    SELECT *
    FROM product
    WHERE id = #{id}
      AND deleted = 0
    """)
    Product selectById(Long id);

    @Update("""
        UPDATE product
        SET stock = stock - #{quantity}
        WHERE id = #{productId}
          AND stock >= #{quantity}
        AND status = 1
        AND deleted = 0
        """)
    int decreaseStock(Long productId, Integer quantity);

    @Update("""
        UPDATE product
        SET stock = stock + #{quantity}
        WHERE id = #{productId}
        """)
    int increaseStock(Long productId, Integer quantity);

    @Insert("""
    INSERT INTO product(
        merchant_id,
        name,
        description,
        price,
        stock,
        status
    )
    VALUES(
        #{merchantId},
        #{name},
        #{description},
        #{price},
        #{stock},
        #{status}
    )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Update("""
            UPDATE product
            SET name = #{name},
                description = #{description},
                price = #{price}
            WHERE id = #{id}
              AND merchant_id = #{merchantId}
            AND deleted = 0
            """)
    int updateProduct(
            Long id,
            Long merchantId,
            String name,
            String description,
            BigDecimal price
    );

    @Update("""
            UPDATE product
            SET status = #{status}
            WHERE id = #{productId}
              AND merchant_id = #{merchantId}
            AND deleted = 0
            """)
    int updateStatus(
            Long productId,
            Long merchantId,
            Integer status
    );

    @Update("""
    UPDATE product
    SET deleted = 1,
        status = 0
    WHERE id = #{productId}
      AND merchant_id = #{merchantId}
      AND deleted = 0
    """)
    int deleteProduct(
            Long productId,
            Long merchantId
    );

    List<Product> selectMerchantProductsPage(
            Long merchantId,
            Integer status,
            Integer offset,
            Integer pageSize
    );

    Long countMerchantProducts(
            Long merchantId,
            Integer status
    );
}
