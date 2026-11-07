package com.dianping.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RestaurantLocationDTO {
    @Size(max = 30)
    private String floor;
    @Size(max = 30)
    private String windowNumber;
    @Size(max = 500)
    private String pickupInstructions;
}
