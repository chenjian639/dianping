package com.dianping.service;

import com.dianping.dto.LoginDTO;
import com.dianping.dto.RegisterDTO;
import com.dianping.entity.User;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.UserMapper;
import com.dianping.util.JwtUtil;
import com.dianping.vo.LoginVO;
import com.dianping.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

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

        String token = JwtUtil.generateToken(
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
