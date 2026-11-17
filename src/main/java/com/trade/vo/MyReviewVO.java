package com.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyReviewVO {

    private Long id;

    private Long orderId;

    private Long merchantId;

    private String merchantName;

    private Integer rating;

    private String content;

    private LocalDateTime createdAt;
}