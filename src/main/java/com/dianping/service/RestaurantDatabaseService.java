package com.dianping.service;

import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.vo.RestaurantDetailVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;

@Service
public class RestaurantDatabaseService {

    private final RestaurantMapper restaurantMapper;
    private final ReviewService reviewService;

    public RestaurantDatabaseService(
            RestaurantMapper restaurantMapper,
            ReviewService reviewService) {

        this.restaurantMapper = restaurantMapper;
        this.reviewService = reviewService;
    }

    @Bulkhead(
            name = "restaurantDb",
            type = Bulkhead.Type.SEMAPHORE
    )
    public RestaurantDetailVO loadRestaurantDetail(Long id) {

        Restaurant restaurant =
                restaurantMapper.selectById(id);

        if (restaurant == null) {
            throw new BusinessException("餐厅不存在");
        }

        RestaurantReviewSummaryVO summary =
                reviewService.getRestaurantReviewSummary(id);

        RestaurantDetailVO vo =
                new RestaurantDetailVO();

        vo.setId(restaurant.getId());
        vo.setName(restaurant.getName());
        vo.setDescription(restaurant.getDescription());
        vo.setAddress(restaurant.getAddress());
        vo.setStatus(restaurant.getStatus());
        vo.setAverageRating(summary.getAverageRating());
        vo.setReviewCount(summary.getReviewCount());

        return vo;
    }
}