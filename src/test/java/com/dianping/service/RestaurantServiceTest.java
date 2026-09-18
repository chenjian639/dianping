package com.dianping.service;

import com.dianping.exception.BusinessException;
import com.dianping.vo.RestaurantDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantCacheService restaurantCacheService;

    @Mock
    private RestaurantDatabaseService restaurantDatabaseService;

    @Mock
    private RestaurantLocalCacheService restaurantLocalCacheService;

    @Spy
    private ObjectMapper objectMapper =
            new ObjectMapper();

    @InjectMocks
    private RestaurantService restaurantService;

    /**
     * 让 L1 getOrLoad 直接执行 loader，从而走到 loadRestaurantDetail 内部的 L2/DB 逻辑。
     */
    @SuppressWarnings("unchecked")
    private void stubLocalCacheToLoad(Long id) {
        when(restaurantLocalCacheService.getOrLoad(
                eq(id),
                any(Function.class)
        )).thenAnswer(inv -> {
            Function<Long, RestaurantDetailVO> loader =
                    inv.getArgument(1);
            return loader.apply(id);
        });
    }

    @Test
    void getRestaurantDetail_shouldReturnCache_whenRedisHit()
            throws Exception {

        Long restaurantId = 1L;

        RestaurantDetailVO cachedVO =
                new RestaurantDetailVO();

        cachedVO.setId(1L);
        cachedVO.setName("老王烧烤");
        cachedVO.setDescription("主打烧烤");
        cachedVO.setAddress("中关村");
        cachedVO.setStatus(1);
        cachedVO.setAverageRating(
                new BigDecimal("5.0")
        );
        cachedVO.setReviewCount(10L);

        String json =
                objectMapper.writeValueAsString(
                        cachedVO
                );

        stubLocalCacheToLoad(restaurantId);

        when(restaurantCacheService.get(
                "restaurant:detail:1"
        )).thenReturn(json);

        RestaurantDetailVO result =
                restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        );

        assertEquals(
                "老王烧烤",
                result.getName()
        );

        assertEquals(
                new BigDecimal("5.0"),
                result.getAverageRating()
        );

        assertEquals(
                10L,
                result.getReviewCount()
        );

        // L2 命中时不应回源 DB
        verify(
                restaurantDatabaseService,
                never()
        ).loadRestaurantDetail(anyLong());

        // 命中后不应再回填缓存
        verify(
                restaurantCacheService,
                never()
        ).set(
                anyString(),
                anyString(),
                anyLong(),
                any()
        );
    }

    @Test
    void getRestaurantDetail_shouldQueryDatabaseAndCache_whenRedisMiss() {

        Long restaurantId = 1L;

        stubLocalCacheToLoad(restaurantId);

        when(restaurantCacheService.get(
                "restaurant:detail:1"
        )).thenReturn(null);

        RestaurantDetailVO vo =
                new RestaurantDetailVO();

        vo.setId(1L);
        vo.setName("老王烧烤");
        vo.setDescription("主打烧烤和夜宵");
        vo.setAddress("海淀区中关村大街1号");
        vo.setStatus(1);
        vo.setAverageRating(new BigDecimal("5.0"));
        vo.setReviewCount(1L);

        when(
                restaurantDatabaseService
                        .loadRestaurantDetail(restaurantId)
        ).thenReturn(vo);

        RestaurantDetailVO result =
                restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        );

        assertEquals(
                "老王烧烤",
                result.getName()
        );

        assertEquals(
                new BigDecimal("5.0"),
                result.getAverageRating()
        );

        assertEquals(
                1L,
                result.getReviewCount()
        );

        verify(
                restaurantDatabaseService,
                times(1)
        ).loadRestaurantDetail(restaurantId);

        // 未命中后应回填 L2，TTL 在 30~40 分钟
        verify(restaurantCacheService).set(
                eq("restaurant:detail:1"),
                anyString(),
                longThat(ttl ->
                        ttl >= 30 && ttl <= 40
                ),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void getRestaurantDetail_shouldCacheNull_whenRestaurantNotExists() {

        Long restaurantId = 99999999L;

        stubLocalCacheToLoad(restaurantId);

        when(restaurantCacheService.get(
                "restaurant:detail:99999999"
        )).thenReturn(null);

        when(
                restaurantDatabaseService
                        .loadRestaurantDetail(restaurantId)
        ).thenThrow(new BusinessException("餐厅不存在"));

        assertThrows(
                BusinessException.class,
                () -> restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        )
        );

        // DB 中不存在时应写入空值缓存，防穿透
        verify(restaurantCacheService).set(
                "restaurant:detail:99999999",
                "__NULL__",
                5,
                TimeUnit.MINUTES
        );

        verify(
                restaurantDatabaseService,
                times(1)
        ).loadRestaurantDetail(restaurantId);
    }

    @Test
    void getRestaurantDetail_shouldThrowDirectly_whenNullCacheHit() {

        Long restaurantId = 99999999L;

        stubLocalCacheToLoad(restaurantId);

        when(restaurantCacheService.get(
                "restaurant:detail:99999999"
        )).thenReturn("__NULL__");

        assertThrows(
                BusinessException.class,
                () -> restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        )
        );

        // 空值缓存命中时不应回源 DB
        verify(
                restaurantDatabaseService,
                never()
        ).loadRestaurantDetail(anyLong());
    }
}
