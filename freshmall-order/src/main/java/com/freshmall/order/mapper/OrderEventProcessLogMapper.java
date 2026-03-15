package com.freshmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.order.event.OrderEventProcessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderEventProcessLogMapper extends BaseMapper<OrderEventProcessLog> {

    @Select("SELECT COUNT(1) FROM b_order_event_process_log WHERE order_id=#{orderId} AND event_type=#{eventType}")
    long countByOrderAndType(@Param("orderId") Long orderId, @Param("eventType") String eventType);
}
