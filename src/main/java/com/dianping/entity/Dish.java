package com.dianping.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Dish {

    private Long id;

    private Long restaurantId;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private Integer status;

    private LocalDateTime createdAt;

    private Integer deleted;
}