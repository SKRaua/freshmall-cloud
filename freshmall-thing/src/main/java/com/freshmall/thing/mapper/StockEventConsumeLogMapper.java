package com.freshmall.thing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.thing.event.StockEventConsumeLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockEventConsumeLogMapper extends BaseMapper<StockEventConsumeLog> {

    @Select("SELECT status FROM b_stock_event_consume_log WHERE event_id=#{eventId} LIMIT 1")
    String findStatusByEventId(@Param("eventId") String eventId);

    @Update("UPDATE b_stock_event_consume_log SET status='SUCCESS', update_time=#{now} WHERE event_id=#{eventId}")
    int markSuccess(@Param("eventId") String eventId, @Param("now") long now);

    @Delete("DELETE FROM b_stock_event_consume_log WHERE event_id=#{eventId}")
    int deleteByEventId(@Param("eventId") String eventId);
}
