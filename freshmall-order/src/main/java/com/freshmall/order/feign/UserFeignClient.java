package com.freshmall.order.feign;

import com.freshmall.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 客户端
 * 用于订单服务获取用户信息
 */
@FeignClient(name = "freshmall-user")
public interface UserFeignClient {

    /**
     * 获取用户详情（仅供服务间调用）
     */
    @GetMapping("/user/inner/detail")
    User getUserById(@RequestParam("id") String id);
}
