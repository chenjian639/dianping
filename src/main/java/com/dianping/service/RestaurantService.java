package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.UpdateRestaurantDTO;
import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.mapper.ReviewMapper;
import com.dianping.vo.RestaurantDetailVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import com.dianping.vo.RestaurantSearchVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String RESTAURANT_DETAIL_KEY_PREFIX =
            "restaurant:detail:";

    private static final String NULL_CACHE_VALUE =
            "__NULL__";

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

        String key = RESTAURANT_DETAIL_KEY_PREFIX + id;

        // 1. 查 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json != null) {
            // 2. Redis 告诉我们：
            //    这个餐厅之前已经确认不存在
            if (NULL_CACHE_VALUE.equals(json)) {
                log.debug(
                        "命中餐厅详情缓存, restaurantId={}", id);
                throw new BusinessException("餐厅不存在");
            }

            try {
                return objectMapper.readValue(
                        json,
                        RestaurantDetailVO.class
                );
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Redis缓存反序列化失败",
                        e
                );
            }
        }

        log.debug("餐厅详情缓存未命中, 查询数据库, restaurantId={}", id);

        // 3. Redis 没有，查询 MySQL
        Restaurant restaurant = restaurantMapper.selectById(id);

        // 4. MySQL 也没有：
        //    写一个短时间的空值缓存
        if (restaurant == null) {
            stringRedisTemplate
                    .opsForValue()
                    .set(
                            key,
                            NULL_CACHE_VALUE,
                            5,
                            TimeUnit.MINUTES
                    );

            throw new BusinessException(
                    "餐厅不存在"
            );
        }

        // 5. 查询评价统计
        RestaurantReviewSummaryVO summary =
                reviewService
                        .getRestaurantReviewSummary(id);

        // 6. 组装 VO
        RestaurantDetailVO vo =
                new RestaurantDetailVO();

        vo.setId(restaurant.getId());
        vo.setName(restaurant.getName());
        vo.setDescription(restaurant.getDescription());
        vo.setAddress(restaurant.getAddress());
        vo.setStatus(restaurant.getStatus());

        vo.setAverageRating(summary.getAverageRating());

        vo.setReviewCount(
                summary.getReviewCount()
        );

        // 7. 正常数据缓存 30 分钟
        try {

            String value = objectMapper.writeValueAsString(vo);
            long ttlMinutes = 30 + ThreadLocalRandom.current().nextLong(0, 11);
            stringRedisTemplate
                    .opsForValue()
                    .set(
                            key,
                            value,
                            ttlMinutes,
                            TimeUnit.MINUTES
                    );

        } catch (JsonProcessingException e) {

            throw new RuntimeException("Redis缓存序列化失败", e);
        }

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

    public void updateRestaurant(
            Long restaurantId,
            UpdateRestaurantDTO dto) {

        int rows = restaurantMapper.updateRestaurant(
                restaurantId,
                dto.getName(),
                dto.getDescription(),
                dto.getAddress()
        );

        if (rows == 0) {
            throw new BusinessException("餐厅不存在");
        }
        deleteRestaurantDetailCache(
                restaurantId
        );
    }
    private void deleteRestaurantDetailCache(Long restaurantId) {

        String key = "restaurant:detail:" + restaurantId;

        Boolean deleted = stringRedisTemplate.delete(key);

        log.debug("删除餐厅详情缓存, restaurantId={}, deleted={}", restaurantId, deleted);
    }

    public void updateRestaurantStatus(
            Long restaurantId,
            Integer status) {

        if (status != 0 && status != 1) {
            throw new BusinessException(
                    "餐厅状态只能是0或1"
            );
        }

        int rows = restaurantMapper.updateStatus(
                restaurantId,
                status
        );

        if (rows == 0) {
            throw new BusinessException(
                    "餐厅不存在"
            );
        }
    }
}