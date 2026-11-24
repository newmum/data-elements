package com.linewell.dataelement.nifi;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.kafka.service.Kafka3ConnectionService;

/** Uses NiFi's connection contract with a Kafka 3 client isolated by this service's NAR. */
@Tags({"Kafka", "legacy", "connection"})
@CapabilityDescription("为 Kafka 0.10 等旧实例提供独立客户端连接。沿用 NiFi 连接、安全与消费组契约，旧实例须配置 read_uncommitted。")
public class HaitongLegacyKafkaConnectionService extends Kafka3ConnectionService {
}
