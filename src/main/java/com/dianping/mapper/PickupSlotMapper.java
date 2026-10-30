package com.dianping.mapper;

import com.dianping.entity.PickupSlot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PickupSlotMapper {
    @Insert("INSERT INTO pickup_slot(restaurant_id, starts_at, ends_at, capacity) " +
            "VALUES(#{restaurantId}, #{startsAt}, #{endsAt}, #{capacity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PickupSlot slot);

    @Select("SELECT * FROM pickup_slot WHERE id = #{id}")
    PickupSlot selectById(Long id);

    @Select("""
            SELECT id FROM pickup_slot
            WHERE restaurant_id = #{restaurantId}
              AND starts_at < #{endsAt}
              AND ends_at > #{startsAt}
            LIMIT 1 FOR UPDATE
            """)
    Long selectOverlappingId(Long restaurantId, LocalDateTime startsAt, LocalDateTime endsAt);

    @Select("SELECT * FROM pickup_slot WHERE restaurant_id = #{restaurantId} " +
            "AND starts_at > NOW() AND reserved < capacity ORDER BY starts_at LIMIT 100")
    List<PickupSlot> selectAvailable(Long restaurantId);

    @Update("UPDATE pickup_slot SET reserved = reserved + 1 WHERE id = #{id} " +
            "AND restaurant_id = #{restaurantId} AND reserved < capacity AND starts_at > NOW() " +
            "AND EXISTS (SELECT 1 FROM restaurant WHERE id = #{restaurantId} AND status = 1)")
    int reserve(Long id, Long restaurantId);

    @Update("UPDATE pickup_slot SET reserved = reserved - 1 WHERE id = #{id} " +
            "AND reserved > 0")
    int release(Long id);
}
