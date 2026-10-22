package com.haitong.connector;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.serialization.*;
import org.springframework.kafka.core.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Component
public class KafkaConnector {
    private final RelayProperties p;private final Secrets secrets;
    private final ConcurrentMap<KafkaAddress,KafkaTemplate<String,byte[]>> templates=new ConcurrentHashMap<>();
    private final List<DefaultKafkaProducerFactory<String,byte[]>> factories=new CopyOnWriteArrayList<>();
    public KafkaConnector(RelayProperties p,Secrets secrets) {this.p=p;this.secrets=secrets;}
    public Map<String,Object> common(KafkaAddress a) {
        Map<String,Object> values=new HashMap<>();values.put("bootstrap.servers",a.servers());values.put("security.protocol",a.securityProtocol());
        values.put("ssl.endpoint.identification.algorithm","https");
        if(!a.truststore().isBlank()) {
            values.put("ssl.truststore.location",a.truststore());
            String password=secrets.resolve(p.getKafka().getTruststorePassword());if(!password.isBlank()) values.put("ssl.truststore.password",password);
        }
        if(a.securityProtocol().startsWith("SASL")) {
            values.put("sasl.mechanism",a.mechanism());
            String module=a.mechanism().equals("PLAIN")?"org.apache.kafka.common.security.plain.PlainLoginModule":"org.apache.kafka.common.security.scram.ScramLoginModule";
            values.put("sasl.jaas.config",module+" required username=\""+escape(a.username())+"\" password=\""+escape(secrets.resolve(p.getKafka().getPassword()))+"\";");
        }
        return values;
    }
    private static String escape(String value) {return value.replace("\\","\\\\").replace("\"","\\\"");}
    protected KafkaTemplate<String,byte[]> template(KafkaAddress address) {
        return templates.computeIfAbsent(address,a->{
            Map<String,Object> c=common(a);c.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);c.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,ByteArraySerializer.class);
            c.put(ProducerConfig.ACKS_CONFIG,"all");c.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);c.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,5);
            c.put(ProducerConfig.RETRIES_CONFIG,Integer.MAX_VALUE);c.put(ProducerConfig.LINGER_MS_CONFIG,0);
            c.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,Math.min(10000,a.timeoutSeconds()*1000));
            c.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,a.timeoutSeconds()*1000);c.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,a.timeoutSeconds()*1000);
            c.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG,p.getLimits().getMaxMessageBytes()+65536);
            var factory=new DefaultKafkaProducerFactory<String,byte[]>(c);factories.add(factory);return new KafkaTemplate<>(factory);
        });
    }
    public Map<String,Object> deliver(TargetPlan target,Packet packet) throws Exception {
        if(packet.isFile()) throw new DeliveryFailure("KAFKA_BINARY_FILE_NOT_ALLOWED",true,false);
        ProducerRecord<String,byte[]> record=new ProducerRecord<>(target.topic(),packet.messageId(),packet.bytes());
        record.headers().add("relay-message-id",packet.messageId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("relay-delivery-id",packet.deliveryId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("relay-route-code",packet.routeCode().getBytes(StandardCharsets.UTF_8));
        addRegistrationHeader(record,"relay-datasource-id",packet.metadata().get("datasourceId"));
        addRegistrationHeader(record,"relay-table-id",packet.metadata().get("tableId"));
        addRegistrationHeader(record,"relay-schema-version",packet.metadata().get("schemaVersion"));
        try {
            var result=template(target.kafka()).send(record).get(target.kafka().timeoutSeconds()+5L,TimeUnit.SECONDS);
            return Map.of("topic",target.topic(),"partition",result.getRecordMetadata().partition(),"offset",result.getRecordMetadata().offset(),"sha256",Rules.sha256(packet.bytes()),"bytes",packet.bytes().length,"completion","BROKER_ACK");
        } catch(ExecutionException e) {
            Throwable cause=e.getCause();
            boolean permanent=cause instanceof AuthenticationException || cause instanceof AuthorizationException || cause instanceof RecordTooLargeException || cause instanceof InvalidTopicException;
            throw new DeliveryFailure(permanent?"KAFKA_CONFIGURATION_OR_PERMISSION":"KAFKA_SEND_FAILED",permanent,false,e);
        }
    }
    private void addRegistrationHeader(ProducerRecord<String,byte[]> record,String name,Object value) {
        if(value!=null && !String.valueOf(value).isBlank()) record.headers().add(name,String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }
    public Map<String,Object> health(KafkaAddress address,Collection<String> topics) throws Exception {
        Map<String,Object> config=common(address);config.put("request.timeout.ms",5000);config.put("default.api.timeout.ms",7000);
        try(Admin admin=Admin.create(config)) {
            int nodes=admin.describeCluster().nodes().get(7,TimeUnit.SECONDS).size();
            // No createTopics call. Application never alters an existing cluster.
            if(!topics.isEmpty()) admin.describeTopics(topics).allTopicNames().get(7,TimeUnit.SECONDS);
            return Map.of("connector","kafka","status","UP","brokerCount",nodes,"topicsChecked",topics.size(),"test","metadata only; write/group ACL still require integration testing");
        }
    }
    @PreDestroy public void close() {for(var factory:factories) factory.destroy();}
}
