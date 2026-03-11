package com.freshmall.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("b_cart")
public class Cart implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField
    private String thingId;

    @TableField
    private String userId;

    @TableField
    private Integer count;

    @TableField
    private String createTime;

    @TableField
    private String updateTime;

    @TableField(exist = false)
    private String title;

    @TableField(exist = false)
    private String cover;

    @TableField(exist = false)
    private String price;

    @TableField(exist = false)
    private Integer repertory;
}
