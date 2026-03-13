package com.freshmall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * 通用缓存配置：放在 common 模块，便于多服务复用。
 *
 * 使用方式：
 * 1) 服务侧在 Application 上显式 @Import(CommonCacheConfig.class)
 * 2) 业务代码使用 Spring Cache 注解（@Cacheable/@CacheEvict 等）
 * 3) 默认走 simple 本地缓存；仅在设置 SPRING_CACHE_TYPE=redis 时启用 Redis
 */
@Configuration
@EnableCaching
public class CommonCacheConfig {
    private static final Logger logger = LoggerFactory.getLogger(CommonCacheConfig.class);

    /**
     * 缓存异常容错：缓存层失败时只记录日志，不影响主业务链路。
     * 这样 Redis 异常/抖动时，接口会自动回退 DB，不会因为缓存报错导致 5xx。
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Cache GET failed, cache={}, key={}", cacheName(cache), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logger.warn("Cache PUT failed, cache={}, key={}", cacheName(cache), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Cache EVICT failed, cache={}, key={}", cacheName(cache), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logger.warn("Cache CLEAR failed, cache={}", cacheName(cache), exception);
            }

            private String cacheName(Cache cache) {
                return cache == null ? "unknown" : cache.getName();
            }
        };
    }

    /**
     * 定义业务缓存的默认策略。
     * 这里先给商品详情缓存配置 30 分钟 TTL，列表缓存配置 2 分钟 TTL。
     * 列表缓存使用较短 TTL，兼顾首页性能与后台改动后的可见性。
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration(
                        "thingDetail",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(30))
                                .disableCachingNullValues())
                .withCacheConfiguration(
                        "thingList",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(2))
                                .disableCachingNullValues());
    }
}
