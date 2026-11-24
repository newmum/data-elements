package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.mapping.core.MappingException;
import java.sql.Connection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedHiveMappingConnectionTest {
    @Test void serverManagedMrsBorrowsTheExistingAuthenticatedPoolInsteadOfOpeningAnotherJdbcSession()throws Exception {
        var hive=mock(HiveModule.class);var connection=mock(Connection.class);
        when(hive.getConnection()).thenReturn(connection);
        var engine=new UnifiedMappingService(null,null);engine.setServerHive(hive);
        for(var config:java.util.List.of(
                Map.<String,Object>of("dbType","hive","metadataAccessMode","server-managed-mrs"),
                Map.<String,Object>of("dbType","HIVE","hiveConnectionMode","huawei-mrs"),
                Map.<String,Object>of("dbType","HIVE","hiveProfile","default"))){
            assertSame(connection,engine.openRegistered("warehouse",Map.of("warehouse",config)));
        }
        verify(hive,times(3)).getConnection();verifyNoMoreInteractions(hive);
        assertThrows(MappingException.class,()->engine.openRegistered("forbidden",Map.of()));
        assertThrows(MappingException.class,()->engine.openRegistered("warehouse",Map.of("warehouse",
                Map.of("dbType","HIVE","hiveProfile","another-profile"))));
        verifyNoMoreInteractions(hive);
    }

    @Test void explicitOpenSourceOrJdbcModeNeverUsesTheServerMrsProfile()throws Exception {
        var hive=mock(HiveModule.class);var engine=new UnifiedMappingService(null,null);engine.setServerHive(hive);
        for(var config:java.util.List.of(
                Map.<String,Object>of("dbType","hive","metadataAccessMode","jdbc","hiveProfile","default"),
                Map.<String,Object>of("dbType","hive","hiveConnectionMode","open-source","metadataAccessMode","server-managed-mrs"),
                Map.<String,Object>of("dbType","GAUSSDB","metadataAccessMode","server-managed-mrs"))){
            assertThrows(MappingException.class,()->engine.openRegistered("warehouse",Map.of("warehouse",config)));
        }
        verifyNoInteractions(hive);
    }

    @Test void missingManagedAuthenticationFilesProduceAnActionableCredentialFreeError()throws Exception {
        var hive=mock(HiveModule.class);when(hive.getConnection()).thenThrow(new java.io.IOException("password=private-value"));
        var engine=new UnifiedMappingService(null,null);engine.setServerHive(hive);
        var error=assertThrows(MappingException.class,()->engine.openRegistered("warehouse",Map.of("warehouse",
                Map.of("dbType","hive","metadataAccessMode","server-managed-mrs"))));
        assertTrue(error.getMessage().contains("托管客户端"));assertFalse(error.getMessage().contains("private-value"));
    }
}
