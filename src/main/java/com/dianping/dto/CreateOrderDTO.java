package com.dianping.dto;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class CreateOrderDTO {
    @NotBlank @Size(max = 64)
    private String requestId;
    @NotBlank @Size(max = 500)
    private String shippingAddress;
}
