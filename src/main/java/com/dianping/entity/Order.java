package com.dianping.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {

    private Long id;

    private Long userId;

    private Long restaurantId;

    private BigDecimal totalAmount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String requestId;
}