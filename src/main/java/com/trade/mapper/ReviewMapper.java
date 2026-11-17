package com.trade.mapper;

import com.trade.entity.Review;
import com.trade.vo.MyReviewVO;
import com.trade.vo.ReviewVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ReviewMapper {

    @Insert("""
        INSERT INTO review(
            user_id,
            merchant_id,
            order_id,
            rating,
            content
        )
        VALUES(
            #{userId},
            #{merchantId},
            #{orderId},
            #{rating},
            #{content}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    @Select("""
    SELECT
        r.id,
        u.nickname,
        r.rating,
        r.content,
        r.created_at AS createdAt
    FROM review r
    JOIN user u ON r.user_id = u.id
    WHERE r.merchant_id = #{merchantId}
    ORDER BY r.created_at DESC
    LIMIT #{pageSize}
    OFFSET #{offset}
    """)
    List<ReviewVO> selectByMerchantIdPage(
            Long merchantId,
            Integer offset,
            Integer pageSize
    );

    @Select("""
    SELECT COUNT(*)
    FROM review
    WHERE merchant_id = #{merchantId}
    """)
    Long countByMerchantId(Long merchantId);

    @Select("""
    SELECT
        rv.id,
        rv.order_id AS orderId,
        rv.merchant_id AS merchantId,
        r.name AS merchantName,
        rv.rating,
        rv.content,
        rv.created_at AS createdAt
    FROM review rv
    JOIN merchant r
        ON rv.merchant_id = r.id
    WHERE rv.user_id = #{userId}
    ORDER BY rv.created_at DESC
    LIMIT #{pageSize}
    OFFSET #{offset}
    """)
    List<MyReviewVO> selectMyReviewsPage(
            Long userId,
            Integer offset,
            Integer pageSize
    );

    @Select("""
    SELECT COUNT(*)
    FROM review
    WHERE user_id = #{userId}
    """)
    Long countMyReviews(Long userId);
}
