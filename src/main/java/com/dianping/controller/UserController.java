package com.dianping.controller;

import com.dianping.common.Result;
import com.dianping.dto.LoginDTO;
import com.dianping.dto.RegisterDTO;
import com.dianping.service.UserService;
import com.dianping.util.JwtUtil;
import com.dianping.vo.LoginVO;
import com.dianping.vo.UserVO;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        if (!id.equals(userId)) throw new com.dianping.exception.BusinessException("无权查看其他用户信息");
        return Result.success(userService.findUserById(id));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser( @RequestAttribute("userId") Long userId) {
        return Result.success(userService.findUserById(userId));
    }
}
