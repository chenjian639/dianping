package com.dianping.interceptor;

import com.dianping.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginInterceptorTest {
    @Test void adminEndpointsRejectOrdinaryUser() throws Exception {
        JwtUtil jwt = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        when(jwt.parseToken("test")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("role", String.class)).thenReturn("USER");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/%61dmin/cache-recovery/start");
        request.addHeader("Authorization", "Bearer test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var controller = new com.dianping.controller.CacheRecoveryController(null,null);
        var handler = new org.springframework.web.method.HandlerMethod(controller,controller.getClass().getMethod("start"));
        assertFalse(new LoginInterceptor(jwt).preHandle(request,response,handler));
        assertEquals(403,response.getStatus());
    }
}
