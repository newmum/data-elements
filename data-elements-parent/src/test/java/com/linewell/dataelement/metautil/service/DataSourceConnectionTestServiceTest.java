package com.linewell.dataelement.metautil.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataSourceConnectionTestServiceTest {

    @Test
    void permitsMrsHiveZooKeeperServiceDiscoveryWithoutFixedHost() {
        MetadataExplorerService metadataExplorerService = mock(MetadataExplorerService.class);
        StructuredSourceProbeService structuredSourceProbeService = mock(StructuredSourceProbeService.class);
        when(metadataExplorerService.testConnection(any(DataSourceConfig.class))).thenReturn(true);
        when(structuredSourceProbeService.supports(any())).thenReturn(false);

        DataSourceConnectionTestService service = new DataSourceConnectionTestService(
                metadataExplorerService,
                mock(ConnectionTestController.class),
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
