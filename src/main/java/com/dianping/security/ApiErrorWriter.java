package com.dianping.security;

import com.dianping.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class ApiErrorWriter {
    private final ObjectMapper json;
    public ApiErrorWriter(ObjectMapper json) { this.json = json; }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), Result.error(status, message));
    }
}
