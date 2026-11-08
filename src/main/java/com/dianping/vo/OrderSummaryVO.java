package com.dianping.vo;

import com.dianping.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Order list representation: never includes idempotency keys. */
public record OrderSummaryVO(Long id, Long userId, Long restaurantId, BigDecimal totalAmount,
                             Integer status, LocalDateTime expiresAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OrderSummaryVO from(Order order) {
        return new OrderSummaryVO(order.getId(), order.getUserId(), order.getRestaurantId(),
                order.getTotalAmount(), order.getStatus(), order.getExpiresAt(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
