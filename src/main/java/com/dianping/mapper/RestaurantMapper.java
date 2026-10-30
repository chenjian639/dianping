package com.dianping.mapper;

import com.dianping.entity.Restaurant;
import com.dianping.vo.RestaurantSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RestaurantMapper {
    @Select("SELECT average_rating AS averageRating, review_count AS reviewCount FROM restaurant WHERE id = #{restaurantId}")
    com.dianping.vo.RestaurantReviewSummaryVO selectReviewSummary(Long restaurantId);

    @Select("SELECT * FROM restaurant WHERE status = 1")
    List<Restaurant> selectAll();

    @Select("SELECT * FROM restaurant WHERE id = #{id}")
    Restaurant selectById(Long id);

    @Select("SELECT id FROM restaurant WHERE id = #{id} FOR UPDATE")
    Long lockById(Long id);

    List<RestaurantSearchVO> searchByKeywordPage(
            String keyword,
            String sort,
            Integer offset,
            Integer pageSize
    );

    @Update("""
    UPDATE restaurant
    SET name = #{name},
        description = #{description},
        address = #{address}
    WHERE id = #{restaurantId}
    """)
    int updateRestaurant(
            Long restaurantId,
            String name,
            String description,
            String address
    );

    @Update("""
    UPDATE restaurant
    SET status = #{status}
    WHERE id = #{restaurantId}
    """)
    int updateStatus(
            Long restaurantId,
            Integer status
    );

    @Update("""
    UPDATE restaurant
    SET average_rating = ROUND((rating_sum + #{rating}) / (review_count + 1), 1),
        rating_sum = rating_sum + #{rating},
        review_count = review_count + 1
    WHERE id = #{restaurantId}
    """)
    int addReviewStatistics(
            Long restaurantId,
            Integer rating
    );
}
