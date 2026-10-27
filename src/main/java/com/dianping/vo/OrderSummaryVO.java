package com.dianping.vo;

import com.dianping.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public list representation: never includes pickup credentials or idempotency keys. */
public record OrderSummaryVO(Long id, Long userId, Long restaurantId, BigDecimal totalAmount,
                             Integer status, Long pickupSlotId, LocalDateTime expiresAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OrderSummaryVO from(Order order) {
        return new OrderSummaryVO(order.getId(), order.getUserId(), order.getRestaurantId(),
                order.getTotalAmount(), order.getStatus(), order.getPickupSlotId(), order.getExpiresAt(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
