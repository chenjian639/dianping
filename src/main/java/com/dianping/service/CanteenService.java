package com.dianping.service;

import com.dianping.dto.CanteenDTO;
import com.dianping.entity.Canteen;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.CanteenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CanteenService {
    private final CanteenMapper mapper;
    private final OutboxEventService events;

    public CanteenService(CanteenMapper mapper, OutboxEventService events) {
        this.mapper = mapper;
        this.events = events;
    }

    public List<Canteen> list() {
        return mapper.selectAll();
    }

    @Transactional
    public Long create(CanteenDTO dto) {
        mapper.lockRevision();
        Canteen canteen = from(dto);
        mapper.insert(canteen);
        changed(canteen.getId());
        return canteen.getId();
    }

    @Transactional
    public void update(Long id, CanteenDTO dto) {
        mapper.lockRevision();
        Canteen canteen = from(dto);
        canteen.setId(id);
        if (mapper.update(canteen) == 0) {
            throw new BusinessException(404, "食堂不存在");
        }
        changed(id);
    }

    @Transactional
    public void delete(Long id) {
        mapper.lockRevision();
        if (mapper.softDelete(id) == 0) {
            throw new BusinessException(404, "食堂不存在");
        }
        changed(id);
    }

    private void changed(Long id) {
        mapper.advanceRevision();
        events.canteenGeoChanged(id);
    }

    private Canteen from(CanteenDTO dto) {
        Canteen canteen = new Canteen();
        canteen.setName(dto.getName());
        canteen.setAddress(dto.getAddress());
        canteen.setLongitude(dto.getLongitude());
        canteen.setLatitude(dto.getLatitude());
        canteen.setEntranceInstructions(dto.getEntranceInstructions());
        canteen.setEnabled(dto.getEnabled());
        return canteen;
    }
}
