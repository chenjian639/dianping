package com.dianping.service;

import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.mapper.ReviewMapper;
import com.dianping.vo.RestaurantReviewSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;
    @Autowired
    private ReviewMapper reviewMapper;

    public List<Restaurant> findAll() {
        return restaurantMapper.selectAll();
    }

    public Restaurant findById(Long id) {
        Restaurant restaurant = restaurantMapper.selectById(id);

        if (restaurant == null) {
            throw new BusinessException("商家不存在");
        }

        return restaurant;
    }
}