package com.trade.vo;

import com.trade.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Order list representation: never includes idempotency keys. */
public record OrderSummaryVO(Long id, Long userId, Long merchantId, BigDecimal totalAmount,
                             Integer status, LocalDateTime expiresAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OrderSummaryVO from(Order order) {
        return new OrderSummaryVO(order.getId(), order.getUserId(), order.getMerchantId(),
                order.getTotalAmount(), order.getStatus(), order.getExpiresAt(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
