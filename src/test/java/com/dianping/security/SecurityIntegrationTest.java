package com.dianping.security;

import com.dianping.config.SecurityConfig;
import com.dianping.dto.CreateOrderDTO;
import com.dianping.exception.BusinessException;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.Map;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, ApiErrorWriter.class,
        GlobalExceptionHandler.class, SecurityIntegrationTest.Probe.class})
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean JwtUtil jwt;
    private final JwtUtil real = new JwtUtil("security-test-secret-at-least-32-characters");
    private String token(String role) { return real.generateToken(1L,"test",role,role.equals("MERCHANT") ? 1L : null); }

    @BeforeEach void parseRealTokens() {
        when(jwt.parseToken(anyString())).thenAnswer(call -> real.parseToken(call.getArgument(0)));
    }

    @RestController
    static class Probe {
        @GetMapping("/restaurants/search") Object publicSearch() { return Map.of("code",0); }
        @GetMapping({"/probe/ok", "/merchant/probe", "/admin/probe"})
        Object authenticated(@RequestAttribute("userId") Long userId) { return Map.of("userId",userId); }
        @GetMapping("/probe/business") Object business() { throw new BusinessException("invalid action"); }
        @GetMapping("/probe/conflict") Object conflict() { throw new BusinessException(409,"conflict"); }
        @GetMapping("/probe/internal") Object internal() { throw new IllegalStateException("private detail"); }
        @PostMapping("/probe/validated") Object validate(@Valid @RequestBody CreateOrderDTO dto) { return Map.of("code",0); }
    }

    @Test void anonymousAndInvalidCredentialsGet401WhilePublicSearchIsAllowed() throws Exception {
        mvc.perform(get("/restaurants/search")).andExpect(status().isOk());
        mvc.perform(get("/probe/ok")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/probe/ok").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/probe/ok").header("Authorization","Bearer "+token("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1));
    }

    @Test void rolesAreAuthorizedInTheSecurityChain() throws Exception {
        mvc.perform(get("/merchant/probe").header("Authorization","Bearer "+token("USER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/admin/probe").header("Authorization","Bearer "+token("MERCHANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/merchant/probe").header("Authorization","Bearer "+token("MERCHANT")))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/probe").header("Authorization","Bearer "+token("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void httpStatusAndResultCodeAgree() throws Exception {
        String auth = "Bearer "+token("USER");
        mvc.perform(get("/probe/business").header("Authorization",auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/probe/conflict").header("Authorization",auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        mvc.perform(get("/probe/internal").header("Authorization",auth))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("系统异常，请稍后重试"));
        mvc.perform(post("/probe/validated").header("Authorization",auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/probe/validated").header("Authorization",auth).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/probe/ok").header("Authorization",auth))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(405));
        mvc.perform(post("/probe/validated").header("Authorization",auth).contentType("text/plain").content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
    }

    @Test void authenticationInfrastructureFailureIs500Not401() throws Exception {
        doThrow(new java.util.NoSuchElementException()).when(jwt).parseToken("internal");
        mvc.perform(get("/probe/ok").header("Authorization","Bearer internal"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500));
    }
}
