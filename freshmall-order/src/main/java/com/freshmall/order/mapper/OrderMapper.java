package com.freshmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.common.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    List<Order> getList(@Param("orderNumber") String orderNumber,
            @Param("status") String status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    List<Order> getUserOrderList(@Param("userId") String userId, @Param("status") String status);
}
