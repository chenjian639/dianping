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
    SELECT COUNT(*)
    FROM restaurant
    WHERE status = 1
      AND name LIKE CONCAT('%', #{keyword}, '%')
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
}
