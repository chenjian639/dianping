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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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

        boolean redisAvailable = true;
        String json = null;

        // Redis 只承载展示数据，交易校验不读取此缓存。
        try {

            json = merchantCacheService.get(key);

        } catch (Exception e) {

            redisAvailable = false;

            log.warn(
                    "读取商家详情Redis缓存失败，降级数据库, merchantId={}, error={}",
                    id,
                    e.getMessage()
            );
        }

        // 2. Redis HIT
        if (json != null) {

            if (NULL_CACHE_VALUE.equals(json)) {
                throw new BusinessException(
                        "商家不存在"
                );
            }

            try {

                return objectMapper.readValue(
                        json,
                        MerchantDetailVO.class
                );

            } catch (JsonProcessingException e) {

                log.warn(
                        "商家详情Redis缓存反序列化失败，降级数据库, merchantId={}, error={}",
                        id,
                        e.getMessage()
                );
            }
        }

        // 3. Redis MISS / Redis故障 → DB
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

            // DB中确实不存在
            if (redisAvailable) {

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
        }


        return vo;
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
                MERCHANT_SEARCH_KEY_PREFIX
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
            cached = merchantCacheService.get(cacheKey);

        } catch (Exception e) {
            log.warn(
                    "读取商家搜索缓存失败，降级查询数据库, cacheKey={}, error={}",
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
                                        MerchantSearchVO.class
                                )
                );
            } catch (JsonProcessingException e) {
                log.warn(
                        "商家搜索缓存反序列化失败，按缓存未命中处理, cacheKey={}, error={}",
                        cacheKey,
                        e.getMessage()
                );
            }
        }

        // 3. Redis 未命中，再查 MySQL
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

        // 4. 写入 Redis

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
