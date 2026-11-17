package com.trade.mapper;

import com.trade.entity.Merchant;
import com.trade.vo.MerchantSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MerchantMapper {
    @Select("SELECT average_rating AS averageRating, review_count AS reviewCount FROM merchant WHERE id = #{merchantId}")
    com.trade.vo.MerchantReviewSummaryVO selectReviewSummary(Long merchantId);

    @Select("SELECT * FROM merchant WHERE status = 1")
    List<Merchant> selectAll();

    @Select("SELECT * FROM merchant WHERE id = #{id}")
    Merchant selectById(Long id);

    @Select("SELECT id FROM merchant WHERE id = #{id} FOR UPDATE")
    Long lockById(Long id);

    List<MerchantSearchVO> searchByKeywordPage(
            String keyword,
            String sort,
            Integer offset,
            Integer pageSize
    );

    @Update("""
    UPDATE merchant
    SET name = #{name},
        description = #{description},
        address = #{address}
    WHERE id = #{merchantId}
    """)
    int updateMerchant(
            Long merchantId,
            String name,
            String description,
            String address
    );

    @Update("""
    UPDATE merchant
    SET status = #{status}
    WHERE id = #{merchantId}
    """)
    int updateStatus(
            Long merchantId,
            Integer status
    );

    @Update("""
    UPDATE merchant
    SET average_rating = ROUND((rating_sum + #{rating}) / (review_count + 1), 1),
        rating_sum = rating_sum + #{rating},
        review_count = review_count + 1
    WHERE id = #{merchantId}
    """)
    int addReviewStatistics(
            Long merchantId,
            Integer rating
    );
}
