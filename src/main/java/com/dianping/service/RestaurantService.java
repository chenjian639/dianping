package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.UpdateRestaurantDTO;
import com.dianping.entity.OutboxEvent;
import com.dianping.entity.Restaurant;
import com.dianping.event.RestaurantChangedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private ObjectMapper objectMapper;
    @Autowired
    private  OutboxEventMapper outboxEventMapper;
    @Autowired
    private RestaurantCacheService restaurantCacheService;
    @Autowired
    private RestaurantLocalCacheService restaurantLocalCacheService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private RestaurantDatabaseService restaurantDatabaseService;

    static final String RESTAURANT_DETAIL_KEY_PREFIX =
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

        return restaurantLocalCacheService.getOrLoad(
                id,
                this::loadRestaurantDetail
        );
    }

    private RestaurantDetailVO loadRestaurantDetail(Long id) {

        String key =
                RESTAURANT_DETAIL_KEY_PREFIX + id;

        boolean redisAvailable = true;
        String json = null;

        // 1. L2 Redis
        try {

            json = restaurantCacheService.get(key);

        } catch (Exception e) {

            redisAvailable = false;

            log.warn(
                    "读取餐厅详情Redis缓存失败，降级数据库, restaurantId={}, error={}",
                    id,
                    e.getMessage()
            );
        }

        // 2. Redis HIT
        if (json != null) {

            if (NULL_CACHE_VALUE.equals(json)) {
                throw new BusinessException(
                        "餐厅不存在"
                );
            }

            try {

                return objectMapper.readValue(
                        json,
                        RestaurantDetailVO.class
                );

            } catch (JsonProcessingException e) {

                log.warn(
                        "餐厅详情Redis缓存反序列化失败，降级数据库, restaurantId={}, error={}",
                        id,
                        e.getMessage()
                );
            }
        }

        // 3. Redis MISS / Redis故障 → DB
        RestaurantDetailVO vo;

        try {

            vo = restaurantDatabaseService
                    .loadRestaurantDetail(id);

        } catch (
                io.github.resilience4j.bulkhead.BulkheadFullException e) {

            log.warn(
                    "餐厅详情数据库回源并发已满, restaurantId={}",
                    id
            );

            throw new BusinessException(
                    "系统繁忙，请稍后重试"
            );

        } catch (BusinessException e) {

            // DB中确实不存在
            if (redisAvailable) {

                try {
                    restaurantCacheService.set(
                            key,
                            NULL_CACHE_VALUE,
                            5,
                            TimeUnit.MINUTES
                    );
                } catch (Exception cacheException) {
                    log.warn(
                            "写入餐厅空值缓存失败, restaurantId={}, error={}",
                            id,
                            cacheException.getMessage()
                    );
                }
            }

            throw e;
        }

        // 4. 回填 Redis
        if (redisAvailable) {

            try {

                String value =
                        objectMapper.writeValueAsString(vo);

                long ttlMinutes =
                        30 + ThreadLocalRandom.current()
                                .nextLong(0, 11);

                restaurantCacheService.set(
                        key,
                        value,
                        ttlMinutes,
                        TimeUnit.MINUTES
                );

            } catch (Exception e) {

                log.warn(
                        "回填餐厅详情Redis缓存失败，不影响业务结果, restaurantId={}, error={}",
                        id,
                        e.getMessage()
                );
            }
        }

        // 不需要手工 put Caffeine
        // cache.get(id, loader) 会把这里返回的 vo 自动写入 L1

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
        String cached = null;

        try {
            cached = restaurantCacheService.get(cacheKey);

        } catch (Exception e) {
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

        try {
            String json =
                    objectMapper.writeValueAsString(result);

            restaurantCacheService.set(
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
        applicationEventPublisher.publishEvent(
                new RestaurantChangedEvent(restaurantId)
        );
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
        // 3. 发事件（事务提交后删本实例本地缓存）
        applicationEventPublisher.publishEvent(new RestaurantChangedEvent(restaurantId));
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
}