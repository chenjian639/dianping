package com.dianping.security;

import com.dianping.config.SecurityConfig;
import com.dianping.controller.AdminCanteenController;
import com.dianping.controller.CanteenController;
import com.dianping.controller.RestaurantLocationController;
import com.dianping.dto.CanteenDTO;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.service.*;
import com.dianping.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, ApiErrorWriter.class, GlobalExceptionHandler.class,
        AdminCanteenController.class, CanteenController.class, RestaurantLocationController.class})
class CanteenSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean JwtUtil jwt;
    @MockBean CanteenService canteens;
    @MockBean CanteenGeoIndexService index;
    @MockBean NearbyCanteenService nearby;
    @MockBean RestaurantLocationService locations;
    private final JwtUtil real = new JwtUtil("location-test-secret-at-least-32-characters");
    private static final String BODY = """
            {"name":"Synthetic test","address":"Not a real campus","longitude":0.001,
             "latitude":0,"entranceInstructions":"Test only","enabled":true}
            """;

    private String auth(String role) {
        return "Bearer " + real.generateToken(1L, "test", role, role.equals("MERCHANT") ? 5L : null);
    }

    @BeforeEach
    void setup() {
        when(jwt.parseToken(anyString())).thenAnswer(call -> real.parseToken(call.getArgument(0)));
        when(nearby.nearby(any())).thenReturn(List.of());
    }

    @Test
    void nearbyRequiresLoginAndValidatesCoordinateRadiusAndLimit() throws Exception {
        mvc.perform(post("/canteens/nearby").contentType("application/json")
                        .content("{\"longitude\":0,\"latitude\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/canteens/nearby").header("Authorization", auth("USER"))
                        .contentType("application/json").content("{\"longitude\":0,\"latitude\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(nearby).nearby(argThat(q -> q.getRadiusMeters() == 1000 && q.getLimit() == 20));
        String[][] invalid = {{"longitude", "181"}, {"latitude", "85.05112879"},
                {"latitude", "NaN"}, {"radiusMeters", "0"}, {"radiusMeters", "10001"},
                {"limit", "101"}, {"longitude", "0.000000001"}};
        for (String[] field : invalid) {
            var body = new java.util.HashMap<String, String>();
            body.put("longitude", "0");
            body.put("latitude", "0");
            body.put(field[0], field[1]);
            mvc.perform(post("/canteens/nearby").header("Authorization", auth("USER"))
                            .contentType("application/json")
                            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(post("/canteens/nearby").header("Authorization", auth("USER"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/canteens/nearby").header("Authorization", auth("USER"))
                        .contentType("application/json")
                        .content("{\"longitude\":0,\"latitude\":0,\"coordinateSystem\":\"GCJ02\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanManageCanteensAndRebuildIndex() throws Exception {
        for (String role : List.of("USER", "MERCHANT")) {
            mvc.perform(post("/admin/canteens").header("Authorization", auth(role))
                            .contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/canteens/geo-index/rebuild").header("Authorization", auth(role)))
                    .andExpect(status().isForbidden());
        }
        when(canteens.create(any(CanteenDTO.class))).thenReturn(9L);
        mvc.perform(post("/admin/canteens").header("Authorization", auth("ADMIN"))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(9));
        mvc.perform(post("/admin/canteens/geo-index/rebuild").header("Authorization", auth("ADMIN")))
                .andExpect(status().isOk());
        verify(index).rebuild();
        mvc.perform(put("/admin/canteens/9").header("Authorization", auth("ADMIN"))
                        .contentType("application/json").content(BODY.replace("0.001", "181")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/canteens").header("Authorization", auth("ADMIN"))
                        .contentType("application/json").content(BODY.replace("Synthetic test", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantCanOnlyChangeOwnStallInstructionsAndNotItsCanteenBinding() throws Exception {
        mvc.perform(put("/merchant/restaurant/location").header("Authorization", auth("MERCHANT"))
                        .contentType("application/json").content("""
                                {"restaurantId":999,"canteenId":999,"longitude":180,
                                 "floor":"2F","windowNumber":"08","pickupInstructions":"East stairs"}
                                """))
                .andExpect(status().isOk());
        verify(locations).update(eq(5L), argThat(dto -> "08".equals(dto.getWindowNumber())));
        verify(locations, never()).bindCanteen(anyLong(), anyLong());
        mvc.perform(put("/admin/restaurants/999/canteen").header("Authorization", auth("MERCHANT"))
                        .contentType("application/json").content("{\"canteenId\":9}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/merchant/restaurant/location").header("Authorization", auth("USER"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        String unbound = "Bearer " + real.generateToken(1L, "unbound", "MERCHANT", null);
        mvc.perform(put("/merchant/restaurant/location").header("Authorization", unbound)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/restaurants/1/location"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminMayBindOrUnbindAndTextLengthsAreBounded() throws Exception {
        mvc.perform(put("/admin/restaurants/1/canteen").header("Authorization", auth("ADMIN"))
                        .contentType("application/json").content("{\"canteenId\":9}"))
                .andExpect(status().isOk());
        verify(locations).bindCanteen(1L, 9L);
        mvc.perform(put("/admin/restaurants/1/canteen").header("Authorization", auth("ADMIN"))
                        .contentType("application/json").content("{\"canteenId\":null}"))
                .andExpect(status().isOk());
        verify(locations).bindCanteen(1L, null);
        mvc.perform(put("/merchant/restaurant/location").header("Authorization", auth("MERCHANT"))
                        .contentType("application/json").content("{\"floor\":\"" + "x".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(locations, never()).update(anyLong(), argThat(dto -> dto.getFloor() != null && dto.getFloor().length() > 30));
    }
}
