package com.dianping.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdminPaymentVO {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String buyerName;
    private String merchantName;
}
