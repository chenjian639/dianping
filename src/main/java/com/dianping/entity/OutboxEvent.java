package com.dianping.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OutboxEvent {

    private Long id;

    private String eventId;

    private String eventType;

    private Long aggregateId;

    private String payload;

    private String status;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}