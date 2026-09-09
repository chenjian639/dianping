package com.dianping.mapper;

import com.dianping.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(String username);

    @Options(useGeneratedKeys = true,keyProperty = "id")
    @Insert("INSERT INTO user(username, password, nickname, phone)  VALUES(#{username}, #{password}, #{nickname}, #{phone}) ")
    int insertUser(User user);
}
