package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.mapper.ReviewMapper;
import com.dianping.vo.RestaurantDetailVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import com.dianping.vo.RestaurantSearchVO;
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
    @Autowired
    private ReviewService reviewService;

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

    public RestaurantDetailVO getRestaurantDetail(Long id) {

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

        vo.setAverageRating(
                summary.getAverageRating()
        );

        vo.setReviewCount(
                summary.getReviewCount()
        );

        return vo;
    }

    public PageResult<RestaurantSearchVO> searchRestaurants(
            String keyword,
            String sort,
            Integer page,
            Integer pageSize) {

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(
                    "pageSize必须在1到100之间"
            );
        }

        if (keyword == null) {
            keyword = "";
        }

        keyword = keyword.trim();

        if (sort == null || sort.isBlank()) {
            sort = "latest";
        }

        if (!sort.equals("latest")
                && !sort.equals("rating")
                && !sort.equals("reviews")) {

            throw new BusinessException(
                    "sort只能是latest、rating或reviews"
            );
        }

        int offset = (page - 1) * pageSize;

        List<RestaurantSearchVO> records =
                restaurantMapper.searchByKeywordPage(
                        keyword,
                        sort,
                        offset,
                        pageSize
                );

        Long total =
                restaurantMapper.countByKeyword(keyword);

        PageResult<RestaurantSearchVO> result =
                new PageResult<>();

        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
}