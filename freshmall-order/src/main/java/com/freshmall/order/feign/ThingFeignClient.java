package com.freshmall.order.feign;

import com.freshmall.common.APIResponse;
import com.freshmall.common.entity.Thing;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端
 * 用于订单服务调用商品服务的内部接口
 */
@FeignClient(name = "freshmall-thing")
public interface ThingFeignClient {

    /**
     * 获取商品详情（不计PV，仅供服务间调用）
     */
    @GetMapping("/thing/inner/detail")
    Thing getThingById(@RequestParam("id") String id);

    /**
     * 扣减库存
     */
    @PostMapping("/thing/inner/deductStock")
    APIResponse deductStock(@RequestParam("thingId") String thingId,
                            @RequestParam("count") int count);
}
