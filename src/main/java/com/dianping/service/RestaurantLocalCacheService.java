package com.dianping.service;

import com.dianping.vo.RestaurantDetailVO;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class RestaurantLocalCacheService {

    @Autowired
    private Cache<Long, RestaurantDetailVO>
            restaurantDetailLocalCache;

    public RestaurantDetailVO get(Long restaurantId) {
        return restaurantDetailLocalCache
                .getIfPresent(restaurantId);
    }

    public void put(
            Long restaurantId,
            RestaurantDetailVO vo) {

        restaurantDetailLocalCache.put(
                restaurantId,
                vo
        );
    }

    public void evict(Long restaurantId) {
        restaurantDetailLocalCache.invalidate(
                restaurantId
        );
    }

    public RestaurantDetailVO getOrLoad(
            Long restaurantId,
            Function<Long, RestaurantDetailVO> loader) {

        return restaurantDetailLocalCache.get(
                restaurantId,
                loader
        );
    }
}