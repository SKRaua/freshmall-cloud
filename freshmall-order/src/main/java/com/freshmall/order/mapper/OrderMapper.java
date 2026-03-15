package com.freshmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.common.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    List<Order> getList(@Param("orderNumber") String orderNumber,
            @Param("status") String status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    List<Order> getUserOrderList(@Param("userId") String userId, @Param("status") String status);

    @Select("SELECT * FROM b_order WHERE status = #{status} AND CAST(order_time AS UNSIGNED) <= #{deadline} ORDER BY id ASC LIMIT #{limit}")
    List<Order> listTimeoutOrders(@Param("status") String status, @Param("deadline") long deadline,
            @Param("limit") int limit);

    @Update("UPDATE b_order SET status=#{newStatus} WHERE id=#{id} AND status=#{currentStatus}")
    int updateStatusIfCurrent(@Param("id") Long id, @Param("currentStatus") String currentStatus,
            @Param("newStatus") String newStatus);
}
