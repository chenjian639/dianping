package com.dianping.entity;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class Payment {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String status;
    private String channelReference;
}
