package com.linewell.dataelement.platform.magic.module;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * 基于 Redis 的 ID 发号器实现
 * <p>
 * 使用 Redis INCR/INCRBY 原子操作，保证并发安全。
 * 输出格式：前缀 + 自动补零的递增序号，如 order_000000000001（总长 18 位，从 1 开始）
 * </p>
 *
 * @author MetaUtil
 */
@Component
@MagicModule("IdGenerator")
@Slf4j
public class IdGenerator {

    private static final String DEFAULT_BIZ_TYPE = "default";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${id.generator.key-prefix:id:generator}")
    private String keyPrefix;

    @Value("${id.generator.default-biz-type:default}")
    private String defaultBizType;

    @Value("${id.generator.output-prefix:order_}")
    private String outputPrefix;

    @Value("${id.generator.output-length:18}")
    private int outputLength;

    public IdGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Comment("获取下一个唯一 ID（默认业务类型）")
    //@Override
    public String nextId() {
        return nextId(defaultBizType);
    }

    @Comment("获取指定业务类型的下一个唯一 ID")
    //@Override
    public String nextId(String bizType) {
        long seq = nextSeq(bizType);
        return formatId(seq);
    }

    @Comment("批量获取 ID（减少 Redis 往返次数，适合高并发场景）")
    //@Override
    public List<String> nextIdBatch(int count) {
        return nextIdBatch(defaultBizType, count);
    }

    @Comment("批量获取指定业务类型的 ID")
    //@Override
    public List<String> nextIdBatch(String bizType, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, got: " + count);
        }
        long startSeq = nextSeqBatch(bizType, count);
        List<String> ids = new ArrayList<>(count);
        for (long i = 0; i < count; i++) {
            ids.add(formatId(startSeq + i));
        }
        return ids;
    }

    //@Override
    public void reset(String bizType, long value) {
        String key = buildKey(bizType);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(value));
        log.warn("ID generator reset: bizType={}, value={}", bizType, value);
    }

    private long nextSeq(String bizType) {
        String key = buildKey(bizType);
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException("Redis INCR returned null for key: " + key);
        }
        return seq;
    }

    private long nextSeqBatch(String bizType, int count) {
        String key = buildKey(bizType);
        Long endSeq = stringRedisTemplate.opsForValue().increment(key, count);
        if (endSeq == null) {
            throw new IllegalStateException("Redis INCRBY returned null for key: " + key);
        }
        return endSeq - count;
    }

    /**
     * 格式化为：前缀 + 自动补零的序号，总长 outputLength
     * 如 order_000000000001（前缀 6 位 + 数字 12 位 = 18）
     */
    private String formatId(long seq) {
        int digitLen = outputLength - outputPrefix.length();
        if (digitLen <= 0) {
            throw new IllegalStateException("output-length must be greater than output-prefix length");
        }
        return outputPrefix + String.format("%0" + digitLen + "d", seq);
    }

    private String buildKey(String bizType) {
        return keyPrefix + ":" + (bizType != null && !bizType.isEmpty() ? bizType : DEFAULT_BIZ_TYPE);
    }
}
