package com.dianping.service;

import com.dianping.dto.LoginDTO;
import com.dianping.dto.RegisterDTO;
import com.dianping.entity.User;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.UserMapper;
import com.dianping.util.JwtUtil;
import com.dianping.vo.LoginVO;
import com.dianping.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    public UserVO findUserById(Long id){
        User user = userMapper.selectById(id);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setPhone(user.getPhone());
        return userVO;
    }

    public UserVO register(RegisterDTO dto){
        User existedUser = userMapper.selectByUsername(dto.getUsername());
        if (existedUser != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        userMapper.insertUser(user);
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setPhone(user.getPhone());
        return userVO;
    }

    public LoginVO login(LoginDTO dto) {

        User user = userMapper.selectByUsername(dto.getUsername());

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        boolean matched = passwordEncoder.matches(
                dto.getPassword(),
                user.getPassword()
        );

        if (!matched) {
            throw new BusinessException("用户名或密码错误");
        }

        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setPhone(user.getPhone());

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getRestaurantId()
        );

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(userVO);
        return loginVO;
    }
}
