package com.freshmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.order.event.OrderEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderEventOutboxMapper extends BaseMapper<OrderEventOutbox> {

    @Select("SELECT * FROM b_order_event_outbox WHERE status IN ('NEW','RETRY') AND next_retry_time <= #{now} ORDER BY id ASC LIMIT #{limit}")
    List<OrderEventOutbox> listDispatchable(@Param("now") long now, @Param("limit") int limit);

    @Update("UPDATE b_order_event_outbox SET status='PROCESSING', update_time=#{now} WHERE id=#{id} AND status IN ('NEW','RETRY')")
    int markProcessing(@Param("id") Long id, @Param("now") long now);

    @Update("UPDATE b_order_event_outbox SET status='DONE', update_time=#{now} WHERE id=#{id}")
    int markDone(@Param("id") Long id, @Param("now") long now);

    @Update("UPDATE b_order_event_outbox SET status='RETRY', retry_count=retry_count+1, next_retry_time=#{nextRetryTime}, update_time=#{now} WHERE id=#{id}")
    int markRetry(@Param("id") Long id, @Param("nextRetryTime") long nextRetryTime, @Param("now") long now);
}
