package com.freshmall.order.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("b_order_event_outbox")
public class OrderEventOutbox {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("order_id")
    private Long orderId;

    @TableField("event_type")
    private String eventType;

    @TableField("payload")
    private String payload;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_time")
    private Long nextRetryTime;

    @TableField("create_time")
    private Long createTime;

    @TableField("update_time")
    private Long updateTime;
}
