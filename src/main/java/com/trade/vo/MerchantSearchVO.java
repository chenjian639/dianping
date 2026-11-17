package com.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantSearchVO {

    private Long id;

    private String name;

    private String description;

    private String address;

    private BigDecimal averageRating;

    private Long reviewCount;
}