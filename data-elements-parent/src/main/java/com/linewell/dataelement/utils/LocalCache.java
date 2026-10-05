package com.linewell.dataelement.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import java.util.concurrent.TimeUnit;

/**
 * @author zwenbo
 * @Description: TODO
 * @date 2026/2/11
 */
@Component
public class LocalCache {


    // 构建缓存实例
    private static final Cache<String, String> cache = CacheBuilder.newBuilder()
        .initialCapacity(1000)        // 初始容量
        .maximumSize(100000)           // 最大缓存条目数，超过后按 LRU 算法剔除
        .expireAfterWrite(30, TimeUnit.DAYS) // 过期时间
        .recordStats()               // 开启统计功能（可选）
        .build();

    /**
     * 存入数据
     */
    public static void put(String key, String value) {
        cache.put(key, value);
    }

    /**
     * 获取数据，如果不存在则返回 null
     */
    public static String get(String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 批量删除
     */
    public static void invalidate(String key) {
        cache.invalidate(key);
    }

    /**
     * 获取当前缓存统计信息（命中率等）
     */
    public static String getStats() {
        return cache.stats().toString();
    }
}

