package com.dianping.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CartItem {

    private Long id;

    private Long userId;

    private Long dishId;

    private Integer quantity;

    private LocalDateTime createdAt;
}