package com.freshmall.thing;

import com.freshmall.common.config.CommonCacheConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
// 显式引入 common 缓存配置，避免受包扫描边界影响
@Import(CommonCacheConfig.class)
@MapperScan("com.freshmall.thing.mapper")
public class ThingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThingApplication.class, args);
    }
}
