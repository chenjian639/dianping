package com.trade.service;

import com.trade.common.PageResult;
import com.trade.exception.BusinessException;
import com.trade.mapper.MerchantMapper;
import com.trade.vo.MerchantDetailVO;
import com.trade.vo.MerchantSearchVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantCacheService merchantCacheService;

    @Mock
    private MerchantMapper merchantMapper;

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

        // 未命中后应抢互斥锁重建
        when(merchantCacheService.tryLock(
                eq("merchant:detail:1:lock"),
                anyString(),
                eq(10L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);

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

        // 重建完成后应释放锁
        verify(merchantCacheService).unlock(
                eq("merchant:detail:1:lock"),
                anyString()
        );
    }

    @Test
    void getMerchantDetail_shouldCacheNull_whenMerchantNotExists() {

        Long merchantId = 99999999L;



        when(merchantCacheService.get(
                "merchant:detail:99999999"
        )).thenReturn(null);

        when(merchantCacheService.tryLock(
                eq("merchant:detail:99999999:lock"),
                anyString(),
                eq(10L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);

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

    @Test
    void getMerchantDetail_shouldWaitAndReadCache_whenLockNotAcquired()
            throws Exception {

        Long merchantId = 1L;

        MerchantDetailVO cachedVO =
                new MerchantDetailVO();

        cachedVO.setId(1L);
        cachedVO.setName("老王烧烤");

        String json =
                objectMapper.writeValueAsString(
                        cachedVO
                );

        // 首次读未命中；等待期间持锁线程完成回填
        when(merchantCacheService.get(
                "merchant:detail:1"
        )).thenReturn(null, json);

        // 锁被其他线程持有
        when(merchantCacheService.tryLock(
                eq("merchant:detail:1:lock"),
                anyString(),
                eq(10L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        MerchantDetailVO result = merchantService
                .getMerchantDetail(merchantId);

        assertEquals(
                "老王烧烤",
                result.getName()
        );

        // 未抢到锁的线程不应回源DB，也不应重复回填
        verify(
                merchantDatabaseService,
                never()
        ).loadMerchantDetail(anyLong());

        verify(
                merchantCacheService,
                never()
        ).set(
                anyString(),
                anyString(),
                anyLong(),
                any()
        );

        // 未持锁自然也无需释放锁
        verify(
                merchantCacheService,
                never()
        ).unlock(anyString(), anyString());
    }

    @Test
    void getMerchantDetail_shouldFallbackToDatabase_whenWaitTimeout() {

        Long merchantId = 1L;

        // 缓存一直未回填
        when(merchantCacheService.get(
                "merchant:detail:1"
        )).thenReturn(null);

        when(merchantCacheService.tryLock(
                eq("merchant:detail:1:lock"),
                anyString(),
                eq(10L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        MerchantDetailVO vo =
                new MerchantDetailVO();

        vo.setId(1L);
        vo.setName("老王烧烤");

        when(
                merchantDatabaseService
                        .loadMerchantDetail(merchantId)
        ).thenReturn(vo);

        MerchantDetailVO result = merchantService
                .getMerchantDetail(merchantId);

        assertEquals(
                "老王烧烤",
                result.getName()
        );

        // 等待超时后降级直接回源，仍回填缓存供后续请求
        verify(
                merchantDatabaseService,
                times(1)
        ).loadMerchantDetail(merchantId);

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
    void searchMerchants_shouldReturnCache_whenRedisHit()
            throws Exception {

        MerchantSearchVO vo =
                new MerchantSearchVO();

        vo.setId(1L);
        vo.setName("老王烧烤");

        PageResult<MerchantSearchVO> cached =
                new PageResult<>();

        cached.setRecords(List.of(vo));
        cached.setHasNext(false);

        String json =
                objectMapper.writeValueAsString(
                        cached
                );

        when(merchantCacheService.get(
                "merchant:search:烧烤:latest:1:10"
        )).thenReturn(json);

        PageResult<MerchantSearchVO> result =
                merchantService.searchMerchants(
                        "烧烤",
                        null,
                        1,
                        10
                );

        assertEquals(
                1,
                result.getRecords().size()
        );

        // 命中缓存不应回源DB
        verify(
                merchantMapper,
                never()
        ).searchByKeywordPage(
                anyString(),
                anyString(),
                anyInt(),
                anyInt()
        );

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
    void searchMerchants_shouldQueryDatabaseAndCache_whenRedisMiss() {

        when(merchantCacheService.get(
                "merchant:search:烧烤:latest:1:2"
        )).thenReturn(null);

        when(merchantCacheService.tryLock(
                eq("merchant:search:烧烤:latest:1:2:lock"),
                anyString(),
                eq(10L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);

        MerchantSearchVO first =
                new MerchantSearchVO();

        first.setId(1L);
        first.setName("老王烧烤");

        MerchantSearchVO second =
                new MerchantSearchVO();

        second.setId(2L);
        second.setName("老李烧烤");

        MerchantSearchVO third =
                new MerchantSearchVO();

        third.setId(3L);
        third.setName("老张烧烤");

        // 查pageSize+1条判断是否有下一页
        when(
                merchantMapper.searchByKeywordPage(
                        "烧烤",
                        "latest",
                        0,
                        3
                )
        ).thenReturn(
                List.of(first, second, third)
        );

        PageResult<MerchantSearchVO> result =
                merchantService.searchMerchants(
                        "烧烤",
                        "latest",
                        1,
                        2
                );

        assertEquals(
                2,
                result.getRecords().size()
        );

        assertEquals(
                Boolean.TRUE,
                result.getHasNext()
        );

        // 回填搜索缓存，TTL 2分钟
        verify(merchantCacheService).set(
                eq("merchant:search:烧烤:latest:1:2"),
                anyString(),
                eq(2L),
                eq(TimeUnit.MINUTES)
        );

        // 重建完成后释放锁
        verify(merchantCacheService).unlock(
                eq("merchant:search:烧烤:latest:1:2:lock"),
                anyString()
        );
    }
}
