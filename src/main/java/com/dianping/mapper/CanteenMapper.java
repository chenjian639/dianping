package com.dianping.mapper;

import com.dianping.entity.Canteen;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CanteenMapper {
    @Select("SELECT * FROM canteen WHERE id = #{id} AND deleted = FALSE")
    Canteen selectById(Long id);

    @Select("SELECT * FROM canteen WHERE deleted = FALSE ORDER BY id")
    List<Canteen> selectAll();

    @Select("SELECT * FROM canteen WHERE enabled = TRUE AND deleted = FALSE ORDER BY id")
    List<Canteen> selectEnabled();

    @Insert("""
            INSERT INTO canteen(name,address,longitude,latitude,entrance_instructions,enabled)
            VALUES(#{name},#{address},#{longitude},#{latitude},#{entranceInstructions},#{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Canteen canteen);

    @Update("""
            UPDATE canteen SET name=#{name},address=#{address},longitude=#{longitude},latitude=#{latitude},
                entrance_instructions=#{entranceInstructions},enabled=#{enabled}
            WHERE id=#{id} AND deleted=FALSE
            """)
    int update(Canteen canteen);

    @Update("UPDATE canteen SET enabled=FALSE,deleted=TRUE WHERE id=#{id} AND deleted=FALSE")
    int softDelete(Long id);

    @Select("SELECT revision FROM canteen_geo_state WHERE id=1")
    long revision();

    @Select("SELECT revision FROM canteen_geo_state WHERE id=1 FOR UPDATE")
    long lockRevision();

    @Update("UPDATE canteen_geo_state SET revision=revision+1 WHERE id=1")
    int advanceRevision();
}
