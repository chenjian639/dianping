package com.trade.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CartItem {

    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private LocalDateTime createdAt;
}