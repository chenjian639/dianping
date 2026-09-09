package com.dianping.mapper;

import com.dianping.entity.Restaurant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RestaurantMapper {
    @Select("SELECT * FROM restaurant WHERE status = 1")
    List<Restaurant> selectAll();

    @Select("SELECT * FROM restaurant WHERE id = #{id}")
    Restaurant selectById(Long id);
}
