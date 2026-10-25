package com.dianping.mapper;
import com.dianping.entity.PickupSlot;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface PickupSlotMapper {
    @Insert("INSERT INTO pickup_slot(restaurant_id, starts_at, ends_at, capacity) VALUES(#{restaurantId}, #{startsAt}, #{endsAt}, #{capacity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PickupSlot slot);
    @Select("SELECT * FROM pickup_slot WHERE id = #{id}")
    PickupSlot selectById(Long id);
    @Select("SELECT * FROM pickup_slot WHERE restaurant_id = #{restaurantId} AND starts_at > NOW() AND reserved < capacity ORDER BY starts_at LIMIT 100")
    List<PickupSlot> selectAvailable(Long restaurantId);
    @Update("UPDATE pickup_slot SET reserved = reserved + 1 WHERE id = #{id} AND restaurant_id = #{restaurantId} AND reserved < capacity AND starts_at > NOW() AND EXISTS (SELECT 1 FROM restaurant WHERE id = #{restaurantId} AND status = 1)")
    int reserve(Long id, Long restaurantId);
    @Update("UPDATE pickup_slot SET reserved = reserved - 1 WHERE id = #{id} AND reserved > 0")
    int release(Long id);
}
