package com.linewell.dataelement.platform.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis和Redisson集群配置类
 */
@Configuration
public class RedisConfiguration {

    @Value("${spring.redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${spring.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.password:}")
    private String sentinelPassword;

    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:123456}")
    private String password;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * Redisson defaults to preheating 24 connections. That is unnecessarily aggressive for
     * this application's cache and can make the whole backend fail to start when a remote
     * Redis server is temporarily slow to accept concurrent PING commands.
     */
    @Value("${spring.redis.redisson.connection-minimum-idle-size:1}")
    private int redissonConnectionMinimumIdleSize;

    @Value("${spring.redis.redisson.connection-pool-size:8}")
    private int redissonConnectionPoolSize;

    @Value("${spring.redis.redisson.connect-timeout:5000}")
    private int redissonConnectTimeoutMillis;

    @Value("${spring.redis.redisson.timeout:5000}")
    private int redissonTimeoutMillis;

    @Value("${spring.redis.redisson.retry-attempts:1}")
    private int redissonRetryAttempts;

    @Value("${spring.redis.redisson.retry-interval:1500}")
    private int redissonRetryIntervalMillis;

    /**
     * RedisTemplate配置
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(om, Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jacksonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jacksonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis连接工厂配置 - 支持单机和集群模式
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 哨兵模式优先于集群和单机
        if (hasText(sentinelMaster) && hasText(sentinelNodes)) {
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
            sentinelConfig.master(sentinelMaster.trim());

            for (String node : splitNodes(sentinelNodes)) {
                String[] parts = node.split(":");
                sentinelConfig.sentinel(parts[0], Integer.parseInt(parts[1]));
            }

            if (hasText(password)) {
                sentinelConfig.setPassword(RedisPassword.of(password));
            }
            if (hasText(sentinelPassword)) {
                sentinelConfig.setSentinelPassword(RedisPassword.of(sentinelPassword));
            }
            sentinelConfig.setDatabase(database);
            return new LettuceConnectionFactory(sentinelConfig);
        }

        // 检查是否配置了集群节点
        if (hasText(clusterNodes)) {
            // 集群模式配置
            List<RedisNode> nodeList = new ArrayList<>();
            for (String node : splitNodes(clusterNodes)) {
                String[] parts = node.split(":");
                nodeList.add(new RedisNode(parts[0], Integer.parseInt(parts[1])));
            }

            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
            clusterConfig.setClusterNodes(nodeList);

            // 如果配置了密码，则设置密码
            if (hasText(password)) {
                clusterConfig.setPassword(RedisPassword.of(password));
            }

            return new LettuceConnectionFactory(clusterConfig);
        } else {
            // 单机模式配置
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(host);
            standaloneConfig.setPort(port);
            standaloneConfig.setDatabase(database);

            // 如果配置了密码，则设置密码
            if (hasText(password)) {
                standaloneConfig.setPassword(RedisPassword.of(password));
            }

            return new LettuceConnectionFactory(standaloneConfig);
        }
    }

    /**
     * Redisson客户端配置 - 支持单机和集群模式
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 哨兵模式优先于集群和单机
        if (hasText(sentinelMaster) && hasText(sentinelNodes)) {
            List<String> nodeAddresses = new ArrayList<>();
            for (String node : splitNodes(sentinelNodes)) {
                nodeAddresses.add(toRedisAddress(node));
            }

            config.useSentinelServers()
                .setMasterName(sentinelMaster.trim())
                .addSentinelAddress(nodeAddresses.toArray(new String[0]))
                .setDatabase(database);

            if (hasText(password)) {
                config.useSentinelServers().setPassword(password);
            }
            if (hasText(sentinelPassword)) {
                config.useSentinelServers().setSentinelPassword(sentinelPassword);
            }
            return Redisson.create(config);
        }

        // 检查是否配置了集群节点
        if (hasText(clusterNodes)) {
            // 集群模式配置
            List<String> nodeAddresses = new ArrayList<>();
            for (String node : splitNodes(clusterNodes)) {
                nodeAddresses.add(toRedisAddress(node));
            }

            config.useClusterServers()
                .addNodeAddress(nodeAddresses.toArray(new String[0]));

            // 如果配置了密码，则设置密码
            if (hasText(password)) {
                config.useClusterServers().setPassword(password);
            }
        } else {
            // 单机模式配置
            int minimumIdleSize = Math.max(1, redissonConnectionMinimumIdleSize);
            int poolSize = Math.max(minimumIdleSize, redissonConnectionPoolSize);
            config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(minimumIdleSize)
                .setConnectionPoolSize(poolSize)
                .setConnectTimeout(Math.max(1_000, redissonConnectTimeoutMillis))
                .setTimeout(Math.max(1_000, redissonTimeoutMillis))
                .setRetryAttempts(Math.max(0, redissonRetryAttempts))
                .setRetryInterval(Math.max(100, redissonRetryIntervalMillis));

            // 如果配置了密码，则设置密码
            if (hasText(password)) {
                config.useSingleServer().setPassword(password);
            }
        }

        return Redisson.create(config);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<String> splitNodes(String nodes) {
        List<String> result = new ArrayList<>();
        for (String node : nodes.split(",")) {
            String trimmed = node.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String toRedisAddress(String node) {
        return node.startsWith("redis://") ? node : "redis://" + node;
    }
}
