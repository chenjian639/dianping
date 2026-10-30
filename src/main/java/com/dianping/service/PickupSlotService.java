package com.dianping.service;
import com.dianping.dto.CreatePickupSlotDTO;
import com.dianping.entity.PickupSlot;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.PickupSlotMapper;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
@Service
public class PickupSlotService {
    private final PickupSlotMapper mapper;
    public PickupSlotService(PickupSlotMapper mapper) { this.mapper = mapper; }
    public java.util.List<PickupSlot> available(Long restaurantId) {

        return mapper.selectAvailable(restaurantId);
    }

    public Long create(Long restaurantId, CreatePickupSlotDTO dto) {
        if (!dto.getEndsAt().isAfter(dto.getStartsAt())) throw new BusinessException("结束时间必须晚于开始时间");
        PickupSlot slot = new PickupSlot();
        slot.setRestaurantId(restaurantId); slot.setStartsAt(dto.getStartsAt());
        slot.setEndsAt(dto.getEndsAt()); slot.setCapacity(dto.getCapacity());
        try { mapper.insert(slot); }
        catch (DuplicateKeyException e) { throw new BusinessException("该档口已存在相同开始时间的时段"); }
        return slot.getId();
    }
}
