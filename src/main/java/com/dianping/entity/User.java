package com.dianping.entity;

import lombok.Data;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private LocalDateTime createdAt;
    private String role;
    private Long restaurantId;
}