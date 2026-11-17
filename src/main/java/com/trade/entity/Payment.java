package com.trade.entity;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class Payment {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String status;
    private String channel;
    private String channelReference;
}
