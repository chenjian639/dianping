package com.dianping.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;

    private UserVO user;
}