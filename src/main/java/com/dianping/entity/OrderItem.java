package com.dianping.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItem {

    private Long id;

    private Long orderId;

    private Long dishId;

    private String dishName;

    private BigDecimal price;

    private Integer quantity;
}