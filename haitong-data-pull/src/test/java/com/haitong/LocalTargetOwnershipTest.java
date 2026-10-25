package com.haitong;

import com.haitong.config.*;
import com.haitong.core.Models.Kind;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalTargetOwnershipTest {
    @TempDir Path work;
    @Test void ftpAndKafkaDestinationsUseOnlyTheDeploymentConfiguration() throws Exception {
        var p=new RelayProperties();p.setWorkDir(work.toString());
        p.getSecurity().setApiKey("validation-client-key-0123456789");p.getSecurity().setAdminKey("validation-admin-key-9876543210");
        p.getFtp().setEnabled(true);p.getFtp().setHost("local-files.invalid");p.getFtp().setOutputDir("/incoming");p.getFtp().setUsername("validation");p.getFtp().setPassword("test-only");
        p.getKafka().setEnabled(true);p.getKafka().setBootstrapServers("local-broker.invalid:9092");p.getKafka().setProducerTopic("configured-topic");
        // A stale platform address can never participate in resolving a destination.
        p.getRegistrationRegistry().setBaseUrl("http://127.0.0.1:1");
        for(var item:Map.of("ftp","FTP","kafka","KAFKA").entrySet()){
            var target=new RelayProperties.Target();target.setType(item.getValue());p.getTargets().put(item.getKey(),target);
            var route=new RelayProperties.Route();route.setTargets(List.of(item.getKey()));p.getRoutes().put(item.getKey(),route);
        }
        var catalog=new RouteCatalog(p,new Secrets(p));catalog.afterPropertiesSet();
        var ftp=catalog.route("ftp").targets().getFirst();assertEquals(Kind.FTP,ftp.kind());assertEquals("local-files.invalid",ftp.ftp().host());assertEquals("/incoming",ftp.directory());
        var kafka=catalog.route("kafka").targets().getFirst();assertEquals("local-broker.invalid:9092",kafka.kafka().servers());assertEquals("configured-topic",kafka.topic());
        p.getRoutes().get("ftp").setRegistrationManaged(true);
        assertThrows(IllegalStateException.class,catalog::afterPropertiesSet);
    }
}
