package com.trade.service;

import com.trade.entity.Merchant;
import com.trade.exception.BusinessException;
import com.trade.mapper.MerchantMapper;
import com.trade.vo.MerchantDetailVO;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;

@Service
public class MerchantDatabaseService {

    private final MerchantMapper merchantMapper;

    public MerchantDatabaseService(
            MerchantMapper merchantMapper) {

        this.merchantMapper = merchantMapper;
    }

    @Bulkhead(
            name = "merchantDb",
            type = Bulkhead.Type.SEMAPHORE
    )
    public MerchantDetailVO loadMerchantDetail(Long id) {
        Merchant merchant =
                merchantMapper.selectById(id);

        if (merchant == null) {
            throw new BusinessException("商家不存在");
        }

        MerchantDetailVO vo =
                new MerchantDetailVO();

        vo.setId(merchant.getId());
        vo.setName(merchant.getName());
        vo.setDescription(merchant.getDescription());
        vo.setAddress(merchant.getAddress());
        vo.setStatus(merchant.getStatus());
        vo.setAverageRating(merchant.getAverageRating());
        vo.setReviewCount(merchant.getReviewCount());

        return vo;
    }
}