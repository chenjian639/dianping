package com.dianping.controller;

import com.dianping.dto.CreateOrderDTO;
import com.dianping.dto.CreatePickupSlotDTO;
import com.dianping.dto.PickupVerificationDTO;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.PickupSlotMapper;
import com.dianping.service.PickupSlotService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampusValidationTest {
    @Test void missingSlotAndMalformedPickupCodeAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var order = new CreateOrderDTO();
            order.setRequestId("request");
            assertFalse(validator.validate(order).isEmpty());
            order.setPickupSlotId(1L);
            assertTrue(validator.validate(order).isEmpty());
            var code = new PickupVerificationDTO();
            code.setPickupCode("123456");
            assertFalse(validator.validate(code).isEmpty());
        }
    }
    @Test void timeRangeMustBeIncreasingAndMerchantCannotBeChosenByCaller() {
        var mapper = mock(PickupSlotMapper.class);
        var service = new PickupSlotService(mapper);
        var dto = new CreatePickupSlotDTO();
        dto.setStartsAt(LocalDateTime.now().plusHours(2));
        dto.setEndsAt(LocalDateTime.now().plusHours(1));
        dto.setCapacity(20);
        assertThrows(BusinessException.class, () -> service.create(1L,dto));
        verifyNoInteractions(mapper);
        dto.setEndsAt(dto.getStartsAt().plusMinutes(15));
        assertThrows(BusinessException.class, () -> new PickupSlotController(mapper,service).create("USER",1L,dto));
        verifyNoInteractions(mapper);
    }
}
