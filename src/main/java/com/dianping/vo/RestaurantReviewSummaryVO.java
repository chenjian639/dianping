package com.dianping.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantReviewSummaryVO {

    private BigDecimal averageRating;

    private Long reviewCount;
}