package com.dianping.dto;
import lombok.Data;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
@Data
public class CreatePickupSlotDTO {
    @NotNull @Future private LocalDateTime startsAt;
    @NotNull @Future private LocalDateTime endsAt;
    @NotNull @Min(1) @Max(10000) private Integer capacity;
}
