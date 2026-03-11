package com.freshmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshmall.common.entity.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {

    List<Cart> getUserCartList(@Param("userId") String userId);

    List<Cart> getUserCartListByIds(@Param("userId") String userId, @Param("ids") List<Long> ids);
}
