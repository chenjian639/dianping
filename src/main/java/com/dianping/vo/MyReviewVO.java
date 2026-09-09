package com.dianping.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyReviewVO {

    private Long id;

    private Long orderId;

    private Long restaurantId;

    private String restaurantName;

    private Integer rating;

    private String content;

    private LocalDateTime createdAt;
}