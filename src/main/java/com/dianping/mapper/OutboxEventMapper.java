package com.dianping.mapper;

import com.dianping.entity.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
}