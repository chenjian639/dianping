package com.dianping.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Review {

    private Long id;
    private Long userId;
    private Long restaurantId;
    private Long orderId;

    private Integer rating;
    private String content;

    private LocalDateTime createdAt;
}