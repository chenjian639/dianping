package com.dianping.mapper;

import com.dianping.entity.Review;
import com.dianping.vo.ReviewVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ReviewMapper {

    @Insert("""
        INSERT INTO review(
            user_id,
            restaurant_id,
            order_id,
            rating,
            content
        )
        VALUES(
            #{userId},
            #{restaurantId},
            #{orderId},
            #{rating},
            #{content}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    @Select("""
        SELECT COUNT(*)
        FROM review
        WHERE order_id = #{orderId}
        """)
    int countByOrderId(Long orderId);

    @Select("""
    SELECT
        r.id,
        u.nickname,
        r.rating,
        r.content,
        r.created_at AS createdAt
    FROM review r
    JOIN user u ON r.user_id = u.id
    WHERE r.restaurant_id = #{restaurantId}
    ORDER BY r.created_at DESC
    LIMIT #{pageSize}
    OFFSET #{offset}
    """)
    List<ReviewVO> selectByRestaurantIdPage(
            Long restaurantId,
            Integer offset,
            Integer pageSize
    );

    @Select("""
    SELECT COUNT(*)
    FROM review
    WHERE restaurant_id = #{restaurantId}
    """)
    Long countByRestaurantId(Long restaurantId);

    @Select("""
    SELECT AVG(rating)
    FROM review
    WHERE restaurant_id = #{restaurantId}
    """)
    BigDecimal getAverageRating(Long restaurantId);

    @Select("""
    SELECT COUNT(*)
    FROM review
    WHERE restaurant_id = #{restaurantId}
    """)
    Long getReviewCount(Long restaurantId);
}