package com.haitong.connector;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import java.util.*;

@Component
public final class KafkaSource implements SmartLifecycle {
    private final RelayProperties p;private final RouteCatalog catalog;private final KafkaConnector kafka;private final IngressService ingress;
    private KafkaMessageListenerContainer<byte[],byte[]> container;
    private volatile boolean running;
    public KafkaSource(RelayProperties p,RouteCatalog catalog,KafkaConnector kafka,IngressService ingress) {this.p=p;this.catalog=catalog;this.kafka=kafka;this.ingress=ingress;}
    @Override public synchronized void start() {
        if(running || !p.getKafka().getConsumer().isEnabled()) return;
        var settings=p.getKafka().getConsumer();Map<String,Object> config=kafka.common(catalog.kafka());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,settings.getGroupId());config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,settings.getOffsetReset());config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG,false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,10);config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,300000);
        config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG,p.getLimits().getMaxMessageBytes()*2);
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,p.getLimits().getMaxMessageBytes());
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,"read_committed");
        ContainerProperties props=new ContainerProperties(settings.getTopic());props.setGroupId(settings.getGroupId());
        props.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);props.setSyncCommits(true);
        props.setMessageListener((AcknowledgingMessageListener<byte[],byte[]>)this::receive);
        container=new KafkaMessageListenerContainer<>(new DefaultKafkaConsumerFactory<>(config),props);
        // Infinite retries for infrastructure failures: no default recoverer silently discards records.
        var errors=new DefaultErrorHandler((record,error)->{throw new IllegalStateException("KAFKA_RECORD_NOT_PERSISTED",error);},new FixedBackOff(1000L,Long.MAX_VALUE));
        errors.setClassifications(Map.of(Exception.class,true),true);errors.setCommitRecovered(false);errors.setAckAfterHandle(false);
        container.setCommonErrorHandler(errors);container.setBeanName("relay-source");container.start();running=true;
    }
    /** Called on the Kafka consumer thread. Acknowledgment only follows a returned, committed DB transaction. */
    public void receive(ConsumerRecord<byte[],byte[]> record,Acknowledgment acknowledgment) {
        var c=p.getKafka().getConsumer();RoutePlan plan=catalog.route(c.getChannel());
        String coordinate=record.topic()+":"+record.partition()+":"+record.offset();
        byte[] raw=record.value()==null?new byte[0]:record.value();
        ingress.accept("system:kafka",record.value()==null?"KAFKA_TOMBSTONE":"KAFKA","kafka:main:"+c.getSourceEpoch(),plan.code(),coordinate,raw,plan,true,Map.of("topic",record.topic(),"partition",record.partition(),"offset",record.offset(),
            "groupId",c.getGroupId(),"sourceEpoch",c.getSourceEpoch(),"keySha256",record.key()==null?"":Rules.sha256(record.key())));
        acknowledgment.acknowledge();
    }
    @Override public synchronized void stop() {if(container!=null) container.stop();running=false;}
    @Override public void stop(Runnable callback) {stop();callback.run();}
    @Override public boolean isRunning() {return running;}
    @Override public int getPhase() {return Integer.MAX_VALUE-100;}
}
