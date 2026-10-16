package com.linewell.dataelement.feature.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Actor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** No raw errors or credentials are persisted. Keys are scoped to tenant AND authenticated user. */
@Component
public class DiagnosticStore {
    public static final Duration TTL = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public DiagnosticStore(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }
    public String id() { return UUID.randomUUID().toString().replace("-", ""); }
    String key(Actor actor, String kind, String id) {
        if (!id.matches("[a-f0-9]{32}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "诊断标识无效");
        return "diagnostics:v1:" + UUID.nameUUIDFromBytes((actor.tenantId() + ":" + actor.userId()).getBytes(StandardCharsets.UTF_8)) + ":" + kind + ":" + id;
    }
    public void save(Actor actor, String kind, String id, Object value) {
        try { redis.opsForValue().set(key(actor, kind, id), json.writeValueAsString(value), TTL); }
        catch (ResponseStatusException invalid) { throw invalid; }
        catch (Exception unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "诊断会话存储暂不可用，请稍后重试"); }
    }
    public <T> T load(Actor actor, String kind, String id, Class<T> type) {
        String key = key(actor, kind, id);
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "诊断记录不存在或已过期，请重新发起");
            T result = json.readValue(value, type);
            redis.expire(key, TTL);
            return result;
        } catch (ResponseStatusException missing) { throw missing; }
        catch (Exception unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "诊断会话存储暂不可用，请稍后重试"); }
    }
    public String lock(Actor actor) {
        String id = id();
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(actor, "lock", "00000000000000000000000000000000"), id, Duration.ofMinutes(4))))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "已有诊断正在进行，请停止或等待完成");
            return id;
        } catch (ResponseStatusException busy) { throw busy; }
        catch (Exception unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "诊断会话存储暂不可用，请稍后重试"); }
    }
    public boolean stopped(Actor actor, String generationId) {
        try { return Boolean.TRUE.equals(redis.hasKey(key(actor, "stop", generationId))); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "诊断会话存储暂不可用"); }
    }

    public void unlock(Actor actor, String id) {
        try { redis.execute(UNLOCK, List.of(key(actor, "lock", "00000000000000000000000000000000")), id); }
        catch (RuntimeException unavailable) { /* bounded lease expires without erasing a newer owner */ }
    }
}
