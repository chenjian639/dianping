package com.trade.service;

import com.trade.exception.BusinessException;
import com.trade.vo.MerchantDetailVO;
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
class MerchantServiceTest {

    @Mock
    private MerchantCacheService merchantCacheService;

    @Mock
    private MerchantDatabaseService merchantDatabaseService;



    @Spy
    private ObjectMapper objectMapper =
            new ObjectMapper();

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void getMerchantDetail_shouldReturnCache_whenRedisHit()
            throws Exception {

        Long merchantId = 1L;

        MerchantDetailVO cachedVO =
                new MerchantDetailVO();

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



        when(merchantCacheService.get(
                "merchant:detail:1"
        )).thenReturn(json);

        MerchantDetailVO result =
                merchantService
                        .getMerchantDetail(
                                merchantId
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
                merchantDatabaseService,
                never()
        ).loadMerchantDetail(anyLong());

        // 命中后不应再回填缓存
        verify(
                merchantCacheService,
                never()
        ).set(
                anyString(),
                anyString(),
                anyLong(),
                any()
        );
    }

    @Test
    void getMerchantDetail_shouldQueryDatabaseAndCache_whenRedisMiss() {

        Long merchantId = 1L;



        when(merchantCacheService.get(
                "merchant:detail:1"
        )).thenReturn(null);

        MerchantDetailVO vo =
                new MerchantDetailVO();

        vo.setId(1L);
        vo.setName("老王烧烤");
        vo.setDescription("主打烧烤和夜宵");
        vo.setAddress("海淀区中关村大街1号");
        vo.setStatus(1);
        vo.setAverageRating(new BigDecimal("5.0"));
        vo.setReviewCount(1L);

        when(
                merchantDatabaseService
                        .loadMerchantDetail(merchantId)
        ).thenReturn(vo);

        MerchantDetailVO result =
                merchantService
                        .getMerchantDetail(
                                merchantId
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
                merchantDatabaseService,
                times(1)
        ).loadMerchantDetail(merchantId);

        // 未命中后应回填 L2，TTL 在 30~40 分钟
        verify(merchantCacheService).set(
                eq("merchant:detail:1"),
                anyString(),
                longThat(ttl ->
                        ttl >= 30 && ttl <= 40
                ),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void getMerchantDetail_shouldCacheNull_whenMerchantNotExists() {

        Long merchantId = 99999999L;



        when(merchantCacheService.get(
                "merchant:detail:99999999"
        )).thenReturn(null);

        when(
                merchantDatabaseService
                        .loadMerchantDetail(merchantId)
        ).thenThrow(new BusinessException("商家不存在"));

        assertThrows(
                BusinessException.class,
                () -> merchantService
                        .getMerchantDetail(
                                merchantId
                        )
        );

        // DB 中不存在时应写入空值缓存，防穿透
        verify(merchantCacheService).set(
                "merchant:detail:99999999",
                "__NULL__",
                5,
                TimeUnit.MINUTES
        );

        verify(
                merchantDatabaseService,
                times(1)
        ).loadMerchantDetail(merchantId);
    }

    @Test
    void getMerchantDetail_shouldThrowDirectly_whenNullCacheHit() {

        Long merchantId = 99999999L;



        when(merchantCacheService.get(
                "merchant:detail:99999999"
        )).thenReturn("__NULL__");

        assertThrows(
                BusinessException.class,
                () -> merchantService
                        .getMerchantDetail(
                                merchantId
                        )
        );

        // 空值缓存命中时不应回源 DB
        verify(
                merchantDatabaseService,
                never()
        ).loadMerchantDetail(anyLong());
    }
}
