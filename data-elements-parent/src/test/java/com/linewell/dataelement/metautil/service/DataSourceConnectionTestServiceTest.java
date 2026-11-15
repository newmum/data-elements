package com.linewell.dataelement.metautil.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class DataSourceConnectionTestServiceTest {

    @Test void explicitOpenHiveWinsOverStaleMrsProfile() {
        var metadata=mock(MetadataExplorerService.class);var magic=mock(MagicAPIService.class);
        var probe=mock(StructuredSourceProbeService.class);
        when(metadata.testConnection(any())).thenReturn(true);
        var service=new DataSourceConnectionTestService(metadata,magic,probe);
        var result=service.test(Map.of("dbType","hive","hiveConnectionMode","open-source","hiveProfile","default",
                "host","validation.invalid","database","business"));
        assertTrue((Boolean)result.get("connected"));
        verify(metadata).testConnection(any());org.mockito.Mockito.verifyNoInteractions(magic);
    }

    @Test
    void elasticsearchConnectionUsesRegisteredEndpointAndPort() {
        var magic=mock(MagicAPIService.class);
        var probe=mock(StructuredSourceProbeService.class);
        when(magic.execute(eq("POST"), eq("/nifi/api/connection-test"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> variables = invocation.getArgument(2);
            variables.put("apiInfo", "/nifi/api/connection-test");
            return ResponseEntity.ok(Map.of("success", true));
        });
        var service=new DataSourceConnectionTestService(mock(MetadataExplorerService.class),magic,probe);
        var result=service.test(Map.of("dbType","es","host","127.0.0.1","port",9200,"jdbcURL","http://127.0.0.1:19200"));
        assertTrue((Boolean)result.get("connected"));
        ArgumentCaptor<Map<String, Object>> request=ArgumentCaptor.captor();
        verify(magic).execute(eq("POST"), eq("/nifi/api/connection-test"), request.capture());
        var body=(Map<?,?>)request.getValue().get("body");
        assertEquals("http://127.0.0.1:19200",((Map<?,?>)body.get("config")).get("nodes"));
    }

    @Test
    void permitsMrsHiveZooKeeperServiceDiscoveryWithoutFixedHost() {
        MetadataExplorerService metadataExplorerService = mock(MetadataExplorerService.class);
        StructuredSourceProbeService structuredSourceProbeService = mock(StructuredSourceProbeService.class);
        when(metadataExplorerService.testConnection(any(DataSourceConfig.class))).thenReturn(true);
        when(structuredSourceProbeService.supports(any())).thenReturn(false);

        DataSourceConnectionTestService service = new DataSourceConnectionTestService(
                metadataExplorerService,
                mock(MagicAPIService.class),
                structuredSourceProbeService);

        Map<String, Object> result = service.test(Map.of(
                "dbType", "mrs_hive",
                "database", "default",
                "zookeeperQuorum", "172.22.202.13:24002,172.22.203.10:24002",
                "zookeeperNamespace", "hiveserver2",
                "authMode", "KERBEROS",
                "principal", "hive/cluster.example.com@EXAMPLE.COM"
        ));

        ArgumentCaptor<DataSourceConfig> config = ArgumentCaptor.forClass(DataSourceConfig.class);
        verify(metadataExplorerService).testConnection(config.capture());
        assertTrue((Boolean) result.get("connected"));
        assertEquals("", config.getValue().getHost());
        assertEquals(
                "jdbc:hive2://172.22.202.13:24002,172.22.203.10:24002/default"
                        + ";serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2"
                        + ";auth=KERBEROS;principal=hive/cluster.example.com@EXAMPLE.COM",
                config.getValue().buildJdbcUrl());
    }
}
