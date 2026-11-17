package com.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantReviewSummaryVO {

    private BigDecimal averageRating;

    private Long reviewCount;
}