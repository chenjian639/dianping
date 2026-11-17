package com.trade.service;

import com.trade.common.PageResult;
import com.trade.dto.UpdateMerchantDTO;
import com.trade.entity.Merchant;
import com.trade.exception.BusinessException;
import com.trade.mapper.MerchantMapper;
import com.trade.vo.MerchantDetailVO;
import com.trade.vo.MerchantSearchVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;
    private final OutboxEventService outboxEventService;
    private final MerchantCacheService merchantCacheService;
    private final MerchantDatabaseService merchantDatabaseService;

    static final String MERCHANT_DETAIL_KEY_PREFIX =
            "merchant:detail:";

    private static final String NULL_CACHE_VALUE =
            "__NULL__";
    private static final String MERCHANT_SEARCH_KEY_PREFIX =
            "merchant:search:";

    private static final long SEARCH_CACHE_TTL_MINUTES = 2;

    /**
     * 重建锁TTL需大于一次DB回源+回填耗时；
     * 持锁线程宕机后锁自动过期，不会死锁。
     */
    private static final long REBUILD_LOCK_TTL_SECONDS = 10;

    /**
     * 未抢到锁的线程轮询等待新缓存，最长约1秒；
     * 超时后降级直接回源，详情回源仍受Bulkhead并发上限保护。
     */
    private static final long REBUILD_WAIT_INTERVAL_MILLIS = 100;

    private static final int REBUILD_WAIT_ATTEMPTS = 10;

    public List<Merchant> findAll() {
        return merchantMapper.selectAll();
    }

    public Merchant findById(Long id) {
        Merchant merchant = merchantMapper.selectById(id);

        if (merchant == null) {
            throw new BusinessException("商家不存在");
        }

        return merchant;
    }

    public MerchantDetailVO getMerchantDetail(Long id) {

        return loadMerchantDetail(id);
    }

    private MerchantDetailVO loadMerchantDetail(Long id) {

        String key =
                MERCHANT_DETAIL_KEY_PREFIX + id;

        // Redis 只承载展示数据，交易校验不读取此缓存。
        // 未命中时走互斥锁重建，防止热点key失效瞬间击穿DB。
        return loadWithRebuildLock(
                key,
                () -> readDetailCache(key),
                () -> loadDetailFromDatabase(id, key)
        );
    }

    private MerchantDetailVO readDetailCache(String key) {

        String json;

        try {

            json = merchantCacheService.get(key);

        } catch (Exception e) {

            log.warn(
                    "读取商家详情Redis缓存失败，降级数据库, key={}, error={}",
                    key,
                    e.getMessage()
            );

            return null;
        }

        return parseDetailCache(key, json);
    }

    private MerchantDetailVO parseDetailCache(
            String key,
            String json) {

        if (json == null) {
            return null;
        }

        // 空值缓存命中：DB中确实不存在，防穿透
        if (NULL_CACHE_VALUE.equals(json)) {
            throw new BusinessException("商家不存在");
        }

        try {

            return objectMapper.readValue(
                    json,
                    MerchantDetailVO.class
            );

        } catch (JsonProcessingException e) {

            log.warn(
                    "商家详情Redis缓存反序列化失败，按未命中处理, key={}, error={}",
                    key,
                    e.getMessage()
            );

            return null;
        }
    }

    private MerchantDetailVO loadDetailFromDatabase(
            Long id,
            String key) {

        MerchantDetailVO vo;

        try {

            vo = merchantDatabaseService
                    .loadMerchantDetail(id);

        } catch (
                io.github.resilience4j.bulkhead.BulkheadFullException e) {

            log.warn(
                    "商家详情数据库回源并发已满, merchantId={}",
                    id
            );

            throw new BusinessException(
                    "系统繁忙，请稍后重试"
            );

        } catch (BusinessException e) {

            // DB中确实不存在，写入空值缓存防穿透
            try {

                merchantCacheService.set(
                        key,
                        NULL_CACHE_VALUE,
                        5,
                        TimeUnit.MINUTES
                );

            } catch (Exception cacheException) {
                log.warn(
                        "写入商家空值缓存失败, merchantId={}, error={}",
                        id,
                        cacheException.getMessage()
                );
            }

            throw e;
        }

        // 回填 Redis，TTL随机30~40分钟防雪崩
        try {

            String value =
                    objectMapper.writeValueAsString(vo);

            long ttlMinutes =
                    30 + ThreadLocalRandom.current()
                            .nextLong(0, 11);

            merchantCacheService.set(
                    key,
                    value,
                    ttlMinutes,
                    TimeUnit.MINUTES
            );

        } catch (Exception e) {

            log.warn(
                    "回填商家详情Redis缓存失败，不影响业务结果, merchantId={}, error={}",
                    id,
                    e.getMessage()
            );
        }

        return vo;
    }

    /**
     * 缓存未命中时用SET NX互斥锁重建，防止缓存击穿：
     * 1. 抢到锁的线程先double-check缓存，再回源DB并回填；
     * 2. 未抢到锁的线程短暂轮询等待读新缓存；
     * 3. 等待超时或Redis不可用时降级直接回源。
     */
    private <T> T loadWithRebuildLock(
            String key,
            Supplier<T> cacheReader,
            Supplier<T> databaseLoader) {

        T cached = cacheReader.get();

        if (cached != null) {
            return cached;
        }

        String lockKey = key + ":lock";
        String owner = UUID.randomUUID().toString();

        boolean locked = false;

        try {

            locked = merchantCacheService.tryLock(
                    lockKey,
                    owner,
                    REBUILD_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );

        } catch (Exception e) {

            log.warn(
                    "获取缓存重建锁失败，降级直接回源数据库, key={}, error={}",
                    key,
                    e.getMessage()
            );

            return databaseLoader.get();
        }

        if (!locked) {
            return waitForRebuild(
                    lockKey,
                    cacheReader,
                    databaseLoader
            );
        }

        try {

            // double-check：抢锁期间可能已被上一个持锁线程回填
            cached = cacheReader.get();

            if (cached != null) {
                return cached;
            }

            return databaseLoader.get();

        } finally {
            releaseLockQuietly(lockKey, owner);
        }
    }

    private <T> T waitForRebuild(
            String lockKey,
            Supplier<T> cacheReader,
            Supplier<T> databaseLoader) {

        for (int attempt = 0;
                attempt < REBUILD_WAIT_ATTEMPTS;
                attempt++) {

            try {
                Thread.sleep(REBUILD_WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            T cached = cacheReader.get();

            if (cached != null) {
                return cached;
            }
        }

        log.warn(
                "等待缓存重建超时，降级直接回源数据库, lockKey={}",
                lockKey
        );

        return databaseLoader.get();
    }

    private void releaseLockQuietly(
            String lockKey,
            String owner) {

        try {
            merchantCacheService.unlock(lockKey, owner);
        } catch (Exception e) {
            log.warn(
                    "释放缓存重建锁失败，锁将随TTL自动过期, lockKey={}, error={}",
                    lockKey,
                    e.getMessage()
            );
        }
    }

    public PageResult<MerchantSearchVO> searchMerchants(
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

        // 规范化后不再重新赋值，保证lambda可捕获
        String effectiveKeyword =
                keyword == null ? "" : keyword.trim();

        String effectiveSort =
                sort == null || sort.isBlank()
                        ? "latest"
                        : sort;

        if (!effectiveSort.equals("latest")
                && !effectiveSort.equals("rating")
                && !effectiveSort.equals("reviews")) {

            throw new BusinessException(
                    "sort只能是latest、rating或reviews"
            );
        }

        // 1. 生成缓存 key
        String cacheKey =
                MERCHANT_SEARCH_KEY_PREFIX
                        + effectiveKeyword
                        + ":"
                        + effectiveSort
                        + ":"
                        + page
                        + ":"
                        + pageSize;

        // 2. 搜索缓存同样走互斥锁重建，
        //    防止热门关键词缓存失效瞬间集中回源DB
        return loadWithRebuildLock(
                cacheKey,
                () -> readSearchCache(cacheKey),
                () -> searchFromDatabase(
                        cacheKey,
                        effectiveKeyword,
                        effectiveSort,
                        page,
                        pageSize
                )
        );
    }

    private PageResult<MerchantSearchVO> readSearchCache(
            String cacheKey) {

        String cached;

        try {

            cached = merchantCacheService.get(cacheKey);

        } catch (Exception e) {
            log.warn(
                    "读取商家搜索缓存失败，降级查询数据库, cacheKey={}, error={}",
                    cacheKey,
                    e.getMessage()
            );

            return null;
        }

        if (cached == null) {
            return null;
        }

        try {

            return objectMapper.readValue(
                    cached,
                    objectMapper
                            .getTypeFactory()
                            .constructParametricType(
                                    PageResult.class,
                                    MerchantSearchVO.class
                            )
            );

        } catch (Exception e) {
            log.warn(
                    "商家搜索缓存反序列化失败，按缓存未命中处理, cacheKey={}, error={}",
                    cacheKey,
                    e.getMessage()
            );

            return null;
        }
    }

    private PageResult<MerchantSearchVO> searchFromDatabase(
            String cacheKey,
            String keyword,
            String sort,
            int page,
            int pageSize) {

        // Redis 未命中，再查 MySQL
        int offset = (page - 1) * pageSize;

        int querySize = pageSize + 1;

        List<MerchantSearchVO> records =
                merchantMapper.searchByKeywordPage(
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

        PageResult<MerchantSearchVO> result =
                new PageResult<>();

        result.setTotal(null);
        result.setHasNext(hasNext);
        result.setRecords(records);

        // 写入 Redis，TTL 2分钟

        try {
            String json =
                    objectMapper.writeValueAsString(result);

            merchantCacheService.set(
                            cacheKey,
                            json,
                            SEARCH_CACHE_TTL_MINUTES,
                            TimeUnit.MINUTES
                    );

        } catch (Exception e) {
            log.warn(
                    "写入商家搜索缓存失败，不影响本次搜索结果, cacheKey={}, error={}",
                    cacheKey,
                    e.getMessage()
            );
        }

        return result;
    }

    @Transactional
    public void updateMerchant(
            Long merchantId,
            UpdateMerchantDTO dto) {

        int rows = merchantMapper.updateMerchant(
                merchantId,
                dto.getName(),
                dto.getDescription(),
                dto.getAddress()
        );

        if (rows == 0) {
            throw new BusinessException("商家不存在");
        }

        outboxEventService.merchantChanged(merchantId);
    }

    @Transactional
    public void updateMerchantStatus(
            Long merchantId,
            Integer status) {

        if (status != 0 && status != 1) {
            throw new BusinessException(
                    "商家状态只能是0或1"
            );
        }

        int rows = merchantMapper.updateStatus(
                merchantId,
                status
        );

        if (rows == 0) {
            throw new BusinessException(
                    "商家不存在"
            );
        }

        outboxEventService.merchantChanged(merchantId);
    }

}
