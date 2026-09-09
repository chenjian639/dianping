package com.dianping.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RestaurantDetailVO {

    private Long id;

    private String name;

    private String description;

    private String address;

    private Integer status;

    private BigDecimal averageRating;

    private Long reviewCount;
}