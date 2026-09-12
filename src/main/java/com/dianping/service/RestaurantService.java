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
    private static final String RESTAURANT_SEARCH_KEY_PREFIX =
            "restaurant:search:";

    private static final long SEARCH_CACHE_TTL_MINUTES = 2;

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

        // 1. 生成缓存 key
        String cacheKey =
                RESTAURANT_SEARCH_KEY_PREFIX
                        + keyword
                        + ":"
                        + sort
                        + ":"
                        + page
                        + ":"
                        + pageSize;

        // 2. 先查 Redis
        String cached =
                stringRedisTemplate
                        .opsForValue()
                        .get(cacheKey);

        if (cached != null) {
            try {
                return objectMapper.readValue(
                        cached,
                        objectMapper
                                .getTypeFactory()
                                .constructParametricType(
                                        PageResult.class,
                                        RestaurantSearchVO.class
                                )
                );
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "搜索缓存反序列化失败",
                        e
                );
            }
        }

        // 3. Redis 未命中，再查 MySQL
        int offset = (page - 1) * pageSize;

        int querySize = pageSize + 1;

        List<RestaurantSearchVO> records =
                restaurantMapper.searchByKeywordPage(
                        keyword,
                        sort,
                        offset,
                        querySize
                );

        boolean hasNext =
                records.size() > pageSize;

        if (hasNext) {
            records =
                    records.subList(
                            0,
                            pageSize
                    );
        }

        PageResult<RestaurantSearchVO> result =
                new PageResult<>();

        result.setTotal(null);
        result.setHasNext(hasNext);
        result.setRecords(records);

        // 4. 写入 Redis
        try {

            String json =
                    objectMapper.writeValueAsString(
                            result
                    );

            stringRedisTemplate
                    .opsForValue()
                    .set(
                            cacheKey,
                            json,
                            SEARCH_CACHE_TTL_MINUTES,
                            TimeUnit.MINUTES
                    );

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "搜索缓存序列化失败",
                    e
            );
        }

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