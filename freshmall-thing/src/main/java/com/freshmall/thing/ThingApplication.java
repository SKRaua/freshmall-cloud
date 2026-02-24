package com.freshmall.thing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.freshmall.thing.mapper")
public class ThingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThingApplication.class, args);
    }
}
