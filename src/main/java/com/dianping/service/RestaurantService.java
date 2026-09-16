package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.UpdateRestaurantDTO;
import com.dianping.entity.OutboxEvent;
import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.OutboxEventMapper;
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
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
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
    @Autowired
    private  OutboxEventMapper outboxEventMapper;

    private volatile long redisDisabledUntil = 0L;
    private static final long REDIS_COOLDOWN_MS = 10_000;

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
        boolean redisAvailable = isRedisAvailable();;

        // 1. 尝试读取 Redis
        String json = null;
        long redisGetStart = System.currentTimeMillis();
        try {
            json = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn(
                    "读取餐厅详情缓存失败，降级查询数据库, restaurantId={}, cost={}ms,error={}",
                    id,
                    System.currentTimeMillis() - redisGetStart,
                    e.getMessage()
            );
        }

        // 2. Redis 正常且命中缓存
        if (json != null) {

            if (NULL_CACHE_VALUE.equals(json)) {
                log.debug(
                        "命中餐厅空值缓存, restaurantId={}",
                        id
                );

                throw new BusinessException("餐厅不存在");
            }

            try {
                return objectMapper.readValue(
                        json,
                        RestaurantDetailVO.class
                );
            } catch (JsonProcessingException e) {
                log.warn(
                        "餐厅详情缓存反序列化失败，降级查询数据库, restaurantId={}, error={}",
                        id,
                        e.getMessage()
                );
            }
        }

        // 3. Redis MISS / Redis 故障 / 缓存数据异常
        log.debug(
                "查询数据库获取餐厅详情, restaurantId={}",
                id
        );

        long dbStart = System.currentTimeMillis();
        Restaurant restaurant =
                restaurantMapper.selectById(id);

        log.info(
                "查询餐厅数据库完成, restaurantId={}, cost={}ms",
                id,
                System.currentTimeMillis() - dbStart
        );

        // 4. MySQL 中也不存在
        if (restaurant == null) {
            if (redisAvailable) {
                try {
                    stringRedisTemplate
                            .opsForValue()
                            .set(
                                    key,
                                    NULL_CACHE_VALUE,
                                    5,
                                    TimeUnit.MINUTES
                            );
                } catch (Exception e) {
                    log.warn(
                            "写入餐厅空值缓存失败, restaurantId={}, error={}",
                            id,
                            e.getMessage()
                    );
                }
            }
            throw new BusinessException("餐厅不存在");
        }

        // 5. 查询评价统计
        long reviewStart = System.currentTimeMillis();

        RestaurantReviewSummaryVO summary =
                reviewService.getRestaurantReviewSummary(id);

        log.info(
                "查询评价统计完成, restaurantId={}, cost={}ms",
                id,
                System.currentTimeMillis() - reviewStart
        );

        // 6. 组装 VO
        RestaurantDetailVO vo =
                new RestaurantDetailVO();

        vo.setId(restaurant.getId());
        vo.setName(restaurant.getName());
        vo.setDescription(restaurant.getDescription());
        vo.setAddress(restaurant.getAddress());
        vo.setStatus(restaurant.getStatus());
        vo.setAverageRating(summary.getAverageRating());
        vo.setReviewCount(summary.getReviewCount());

        // 7. 尝试写 Redis
        long redisSetStart = System.currentTimeMillis();
        if(redisAvailable) {
            try {

                String value =
                        objectMapper.writeValueAsString(vo);

                long ttlMinutes =
                        30 + ThreadLocalRandom.current()
                                .nextLong(0, 11);

                stringRedisTemplate
                        .opsForValue()
                        .set(
                                key,
                                value,
                                ttlMinutes,
                                TimeUnit.MINUTES
                        );

            } catch (Exception e) {

                log.warn(
                        "写入餐厅详情缓存失败，不影响本次查询结果, restaurantId={}, cost={}ms, error={}",
                        id,
                        System.currentTimeMillis() - redisSetStart,
                        e.getMessage()
                );
            }
        }else {

            log.debug(
                    "Redis本次请求已判定不可用，跳过缓存写入, restaurantId={}",
                    id
            );
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

        boolean redisAvailable = isRedisAvailable();;
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
        String cached = null;

        try {
            cached = stringRedisTemplate
                    .opsForValue()
                    .get(cacheKey);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn(
                    "读取餐厅搜索缓存失败，降级查询数据库, cacheKey={}, error={}",
                    cacheKey,
                    e.getMessage()
            );
        }
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
                log.warn(
                        "餐厅搜索缓存反序列化失败，按缓存未命中处理, cacheKey={}, error={}",
                        cacheKey,
                        e.getMessage()
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
        if (redisAvailable) {
            try {
                String json =
                        objectMapper.writeValueAsString(result);

                stringRedisTemplate
                        .opsForValue()
                        .set(
                                cacheKey,
                                json,
                                SEARCH_CACHE_TTL_MINUTES,
                                TimeUnit.MINUTES
                        );

            } catch (Exception e) {
                log.warn(
                        "写入餐厅搜索缓存失败，不影响本次搜索结果, cacheKey={}, error={}",
                        cacheKey,
                        e.getMessage()
                );
            }
        }

        return result;
    }

    @Transactional
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

        createRestaurantCacheEvictEvent(restaurantId);
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

        createRestaurantCacheEvictEvent(restaurantId);
    }

    private void createRestaurantCacheEvictEvent(
            Long restaurantId) {

        OutboxEvent event = new OutboxEvent();

        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("RESTAURANT_CACHE_EVICT");
        event.setAggregateId(restaurantId);
        event.setPayload(String.valueOf(restaurantId));
        event.setStatus("PENDING");
        event.setRetryCount(0);

        outboxEventMapper.insert(event);

        log.info(
                "创建餐厅缓存失效Outbox事件, restaurantId={}, eventId={}",
                restaurantId,
                event.getEventId()
        );
    }

    private boolean isRedisAvailable() {
        return System.currentTimeMillis() >= redisDisabledUntil;
    }

    private void markRedisUnavailable() {
        redisDisabledUntil =
                System.currentTimeMillis()
                        + REDIS_COOLDOWN_MS;
    }
}