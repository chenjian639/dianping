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
        processing_at = NULL,
        updated_at = NOW()
    WHERE id = #{id}
      AND status = 'PROCESSING'
    """)
    int markSent(Long id);

    @Update("""
    UPDATE outbox_event
    SET status = 'FAILED',
        retry_count = retry_count + 1,
        processing_at = NULL,
        updated_at = NOW()
    WHERE id = #{id}
      AND status = 'PROCESSING'
    """)
    int markFailed(Long id);

    @Update("""
    UPDATE outbox_event
    SET status = 'PROCESSING',
        processing_at = NOW(),
        updated_at = NOW()
    WHERE id = #{id}
      AND status = 'PENDING'
    """)
    int tryClaim(Long id);

    @Update("""
    UPDATE outbox_event
    SET status = 'PENDING',
        retry_count = retry_count + 1,
        processing_at = NULL,
        updated_at = NOW()
    WHERE id = #{id}
      AND status = 'PROCESSING'
    """)
    int releaseForRetry(Long id);

    @Update("""
    UPDATE outbox_event
    SET status = 'PENDING',
        processing_at = NULL,
        updated_at = NOW()
    WHERE status = 'PROCESSING'
      AND processing_at < DATE_SUB(NOW(), INTERVAL 1 MINUTE)
    """)
    int recoverStuckProcessing();
}