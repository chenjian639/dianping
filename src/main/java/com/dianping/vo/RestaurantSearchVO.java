package com.dianping.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantSearchVO {

    private Long id;

    private String name;

    private String description;

    private String address;

    private BigDecimal averageRating;

    private Long reviewCount;
}