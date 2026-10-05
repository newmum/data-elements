package com.linewell.dataelement.dataservice.flowserve.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.flowserve.FlowServeDataSourceRuntime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 云梯编排 Kafka 消费与生产运行模块。 */
@Component
@MagicModule("kafka")
public class FlowKafkaModule {
    private final FlowServeDataSourceRuntime dataSources;
    private final ObjectMapper mapper;

    public FlowKafkaModule(FlowServeDataSourceRuntime dataSources, ObjectMapper mapper) {
        this.dataSources = dataSources;
        this.mapper = mapper;
    }

    @Comment("拉取式消费 Kafka 消息")
    public List<Map<String, Object>> consume(String datasource, Map<String, Object> options) {
        Map<String, Object> values = options == null ? Map.of() : options;
        String topic = String.valueOf(values.getOrDefault("topic", ""));
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic 不能为空");
        }
        Properties properties = properties(datasource);
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                String.valueOf(values.getOrDefault("consumerGroup", "flowserve-" + datasource)));
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                String.valueOf(values.getOrDefault("offsetReset", "latest")));
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        int maxRecords = intValue(values.get("maxRecords"), 100);
        long timeout = longValue(values.get("pollTimeoutMs"), 2000);
        List<Map<String, Object>> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(timeout)).forEach(record -> {
                if (messages.size() < maxRecords) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("key", record.key());
                    message.put("value", parse(record.value(), String.valueOf(values.getOrDefault("valueFormat", "json"))));
                    message.put("partition", record.partition());
                    message.put("offset", record.offset());
                    message.put("timestamp", Instant.ofEpochMilli(record.timestamp()).toString());
                    messages.add(message);
                }
            });
            if (Boolean.parseBoolean(String.valueOf(values.getOrDefault("commit", false)))) {
                consumer.commitSync();
            }
            return messages;
        }
    }

    @Comment("生产 Kafka 消息")
    public Map<String, Object> send(
            String datasource, String topic, Object key, Object value, Map<String, Object> options) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic 不能为空");
        }
        Properties properties = properties(datasource);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            var metadata = producer.send(new ProducerRecord<>(
                    topic,
                    key == null ? null : String.valueOf(key),
                    value instanceof String text ? text : mapper.writeValueAsString(value))).get();
            return Map.of(
                    "success", true,
                    "topic", metadata.topic(),
                    "partition", metadata.partition(),
                    "offset", metadata.offset());
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka 发送失败：" + exception.getMessage(), exception);
        }
    }

    private Properties properties(String datasource) {
        Map<String, Object> config = dataSources.configByName(datasource);
        Properties properties = new Properties();
        properties.put(
                "bootstrap.servers",
                String.valueOf(config.getOrDefault(
                        "bootstrap.servers", config.getOrDefault("bootstrapServers", ""))));
        properties.put("request.timeout.ms", String.valueOf(config.getOrDefault("requestTimeoutMs", 5000)));
        properties.put("default.api.timeout.ms", String.valueOf(config.getOrDefault("defaultApiTimeoutMs", 5000)));
        put(properties, "security.protocol", config.get("securityProtocol"));
        put(properties, "sasl.mechanism", config.get("saslMechanism"));
        put(properties, "sasl.jaas.config", config.get("saslJaasConfig"));
        return properties;
    }

    private void put(Properties properties, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            properties.put(key, String.valueOf(value));
        }
    }

    private Object parse(String value, String format) {
        if ("json".equalsIgnoreCase(format)) {
            try {
                return mapper.readValue(value, Object.class);
            } catch (Exception ignored) {
                return value;
            }
        }
        return value;
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
