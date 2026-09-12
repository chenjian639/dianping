package com.dianping.mapper;

import com.dianping.entity.Restaurant;
import com.dianping.vo.RestaurantSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RestaurantMapper {
    @Select("SELECT * FROM restaurant WHERE status = 1")
    List<Restaurant> selectAll();

    @Select("SELECT * FROM restaurant WHERE id = #{id}")
    Restaurant selectById(Long id);

    List<RestaurantSearchVO> searchByKeywordPage(
            String keyword,
            String sort,
            Integer offset,
            Integer pageSize
    );

    @Select("""
    <script>
    SELECT COUNT(*)
    FROM restaurant
    WHERE status = 1
    <if test="keyword != null and keyword != ''">
      AND MATCH(name) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE)
    </if>
    </script>
    """)
    Long countByKeyword(String keyword);

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
    SET average_rating =
            ROUND(
                (
                    average_rating * review_count
                    + #{rating}
                )
                / (review_count + 1),
                1
            ),
        review_count = review_count + 1
    WHERE id = #{restaurantId}
    """)
    int addReviewStatistics(
            Long restaurantId,
            Integer rating
    );
}
