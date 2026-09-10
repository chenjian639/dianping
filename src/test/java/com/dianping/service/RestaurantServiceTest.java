package com.dianping.service;

import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.vo.RestaurantDetailVO;
import com.dianping.vo.RestaurantReviewSummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantMapper restaurantMapper;

    @Mock
    private ReviewService reviewService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper =
            new ObjectMapper();

    @InjectMocks
    private RestaurantService restaurantService;

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

        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
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

        verify(
                restaurantMapper,
                never()
        ).selectById(anyLong());

        verify(
                reviewService,
                never()
        ).getRestaurantReviewSummary(
                anyLong()
        );
    }

    @Test
    void getRestaurantDetail_shouldQueryDatabaseAndCache_whenRedisMiss()
            throws Exception {

        Long restaurantId = 1L;

        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
                "restaurant:detail:1"
        )).thenReturn(null);

        Restaurant restaurant =
                new Restaurant();

        restaurant.setId(1L);
        restaurant.setName("老王烧烤");
        restaurant.setDescription(
                "主打烧烤和夜宵"
        );
        restaurant.setAddress(
                "海淀区中关村大街1号"
        );
        restaurant.setStatus(1);

        when(
                restaurantMapper
                        .selectById(restaurantId)
        ).thenReturn(restaurant);

        RestaurantReviewSummaryVO summary =
                new RestaurantReviewSummaryVO();

        summary.setAverageRating(
                new BigDecimal("5.0")
        );
        summary.setReviewCount(1L);

        when(
                reviewService
                        .getRestaurantReviewSummary(
                                restaurantId
                        )
        ).thenReturn(summary);

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
                restaurantMapper,
                times(1)
        ).selectById(restaurantId);

        verify(
                reviewService,
                times(1)
        ).getRestaurantReviewSummary(
                restaurantId
        );

        verify(valueOperations).set(
                eq("restaurant:detail:1"),
                anyString(),
                longThat(ttl ->
                        ttl >= 30
                                && ttl <= 40
                ),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void getRestaurantDetail_shouldCacheNull_whenRestaurantNotExists() {

        Long restaurantId = 99999999L;

        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
                "restaurant:detail:99999999"
        )).thenReturn(null);

        when(
                restaurantMapper
                        .selectById(restaurantId)
        ).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        )
        );

        verify(valueOperations).set(
                "restaurant:detail:99999999",
                "__NULL__",
                5,
                TimeUnit.MINUTES
        );

        verify(
                reviewService,
                never()
        ).getRestaurantReviewSummary(
                anyLong()
        );
    }

    @Test
    void getRestaurantDetail_shouldThrowDirectly_whenNullCacheHit() {

        Long restaurantId = 99999999L;

        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
                "restaurant:detail:99999999"
        )).thenReturn("__NULL__");

        assertThrows(
                BusinessException.class,
                () -> restaurantService
                        .getRestaurantDetail(
                                restaurantId
                        )
        );

        verify(
                restaurantMapper,
                never()
        ).selectById(anyLong());

        verify(
                reviewService,
                never()
        ).getRestaurantReviewSummary(
                anyLong()
        );
    }
}