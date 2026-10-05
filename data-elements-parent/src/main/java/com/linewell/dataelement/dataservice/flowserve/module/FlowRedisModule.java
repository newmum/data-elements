package com.linewell.dataelement.dataservice.flowserve.module;

import com.linewell.dataelement.dataservice.flowserve.FlowServeDataSourceRuntime;
import com.linewell.dataelement.dataservice.flowserve.FlowServeRedisClient;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 云梯编排 Redis 节点运行模块，名称与平台内置 redis 模块隔离。 */
@Component
@MagicModule("flowRedis")
public class FlowRedisModule {
    private final FlowServeDataSourceRuntime dataSources;
    private final FlowServeRedisClient redis;

    public FlowRedisModule(FlowServeDataSourceRuntime dataSources, FlowServeRedisClient redis) {
        this.dataSources = dataSources;
        this.redis = redis;
    }

    @Comment("执行 Redis get/set/del/expire/incr/hget/hset")
    public Object op(
            String datasource, String operation, String key, Object value, Map<String, Object> options) {
        Map<String, Object> config = dataSources.configByName(datasource);
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        String op = operation == null ? "get" : operation.toLowerCase();
        return switch (op) {
            case "set" -> {
                Object ttl = safeOptions.get("ttl");
                if (ttl == null || String.valueOf(ttl).isBlank()) {
                    yield redis.execute(config, "SET", key, String.valueOf(value));
                }
                yield redis.execute(config, "SETEX", key, String.valueOf(ttl), String.valueOf(value));
            }
            case "del" -> redis.execute(config, "DEL", key);
            case "expire" -> redis.execute(config, "EXPIRE", key, String.valueOf(safeOptions.getOrDefault("ttl", 60)));
            case "incr" -> redis.execute(config, "INCR", key);
            case "hget" -> redis.execute(config, "HGET", key, String.valueOf(value));
            case "hset" -> redis.execute(
                    config,
                    "HSET",
                    key,
                    String.valueOf(safeOptions.getOrDefault("field", "")),
                    String.valueOf(value));
            default -> redis.execute(config, "GET", key);
        };
    }
}
