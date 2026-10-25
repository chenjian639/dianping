package com.dianping.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class PickupSlot {
    private Long id;
    private Long restaurantId;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Integer capacity;
    private Integer reserved;
}
