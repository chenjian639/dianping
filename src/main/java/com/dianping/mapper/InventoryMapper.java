package com.dianping.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
@Mapper
public interface InventoryMapper {
    @Insert("INSERT INTO inventory_movement(product_id,delta,reason,business_key) VALUES(#{productId},#{delta},#{reason},#{key})")
    int record(Long productId,int delta,String reason,String key);
    @Select("SELECT delta FROM inventory_movement WHERE product_id=#{productId} AND reason='ADJUST' AND business_key=#{key}")
    Integer adjustment(Long productId,String key);
    @Update("UPDATE dish SET stock=stock+#{delta} WHERE id=#{id} AND stock+CAST(#{delta} AS SIGNED) BETWEEN 0 AND 2147483647")
    int adjust(Long id,int delta);
    @Select("SELECT * FROM inventory_movement WHERE product_id=#{id} ORDER BY id DESC LIMIT 100")
    List<Map<String,Object>> history(Long id);
}
