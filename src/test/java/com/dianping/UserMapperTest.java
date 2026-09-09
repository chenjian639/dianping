package com.dianping;

import com.dianping.entity.User;
import com.dianping.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testSelectById() {

        User user = userMapper.selectById(1L);

        System.out.println(user);
    }
}