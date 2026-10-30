package com.dianping.service;

import com.dianping.dto.CreatePickupSlotDTO;
import com.dianping.entity.PickupSlot;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.PickupSlotMapper;
import com.dianping.mapper.RestaurantMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PickupSlotService {
    private final PickupSlotMapper mapper;
    private final RestaurantMapper restaurantMapper;

    public PickupSlotService(PickupSlotMapper mapper, RestaurantMapper restaurantMapper) {
        this.mapper = mapper;
        this.restaurantMapper = restaurantMapper;
    }

    public List<PickupSlot> available(Long restaurantId) {
        return mapper.selectAvailable(restaurantId);
    }

    @Transactional
    public Long create(Long restaurantId, CreatePickupSlotDTO dto) {
        if (!dto.getEndsAt().isAfter(dto.getStartsAt())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        // Serialize creation per restaurant, including when no slots exist yet.
        if (restaurantMapper.lockById(restaurantId) == null) {
            throw new BusinessException(404, "档口不存在");
        }
        if (mapper.selectOverlappingId(restaurantId, dto.getStartsAt(), dto.getEndsAt()) != null) {
            throw new BusinessException("该档口已存在重叠的取餐时段");
        }

        PickupSlot slot = new PickupSlot();
        slot.setRestaurantId(restaurantId);
        slot.setStartsAt(dto.getStartsAt());
        slot.setEndsAt(dto.getEndsAt());
        slot.setCapacity(dto.getCapacity());
        try {
            mapper.insert(slot);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该档口已存在相同开始时间的时段");
        }
        return slot.getId();
    }
}
