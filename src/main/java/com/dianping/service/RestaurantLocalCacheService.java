package com.dianping.service;

import com.dianping.vo.RestaurantDetailVO;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantLocalCacheService {

    private final Cache<Long, RestaurantDetailVO> restaurantDetailLocalCache;

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