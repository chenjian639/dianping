package com.dianping.dto;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class PickupVerificationDTO {
    @NotBlank @Pattern(regexp = "[a-f0-9]{32}") private String pickupCode;
}
