package com.dianping.mapper;

import com.dianping.entity.OutboxEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OutboxEventMapper {

    @Insert("""
        INSERT INTO outbox_event
        (event_id, event_type, aggregate_id, payload, status, retry_count)
        VALUES
        (#{eventId}, #{eventType}, #{aggregateId},
         #{payload}, #{status}, #{retryCount})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OutboxEvent event);

    @Select("""
    SELECT id, event_id, event_type, aggregate_id,
           payload, status, retry_count,
           created_at, updated_at
    FROM outbox_event
    WHERE status = 'PENDING'
    ORDER BY id
    LIMIT #{limit}
    """)
    List<OutboxEvent> selectPending(int limit);

    @Update("""
    UPDATE outbox_event
    SET status = 'SENT',
        updated_at = NOW()
    WHERE id = #{id}
      AND status = 'PENDING'
    """)
    int markSent(Long id);

    @Update("""
    UPDATE outbox_event
    SET retry_count = retry_count + 1,
        updated_at = NOW()
    WHERE id = #{id}
    """)
    int incrementRetry(Long id);
}