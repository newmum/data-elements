package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataPushFjghccDeliveryServiceTest {
    private TenantDataSourceRegistry tenants;
    private DataSourceConnectionPropertyResolver connections;
    private AtomicInteger reads;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ftp_delivery_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table sym_dict_t(tid varchar,tenant_id varchar,parent_id varchar,dict_code varchar,is_del int,sort_no int)");
        jdbc.execute("create table db_datasource_t(tid varchar,tenant_id varchar,db_name varchar,db_type varchar,is_del int,show_connect int)");
        jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar,datasource_id varchar,is_del int,target_type varchar,target_id varchar,updated_time timestamp,tid varchar)");
        jdbc.update("insert into sym_dict_t values('ods','tenant-1','0','ods',0,1),('files','tenant-1','ods','fjghcc',0,1)");
        jdbc.update("insert into db_datasource_t values('ftp-1','tenant-1','文件存储','ftp',0,1)");
        jdbc.update("insert into dwm_center_layer_source_t values('tenant-1','ftp-1',0,'domain','files',current_timestamp,'binding')");
        reads = new AtomicInteger();
        var counted = new DelegatingDataSource(ds) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    if (method.getName().equals("prepareStatement") && args[0] instanceof String sql && sql.stripLeading().toLowerCase().startsWith("select")) reads.incrementAndGet();
                    try { return method.invoke(connection,args); }
                    catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
            }
        };
        tenants = mock(TenantDataSourceRegistry.class);
        when(tenants.dataSourceForTenant(anyString())).thenReturn(counted);
        connections = mock(DataSourceConnectionPropertyResolver.class);
        when(connections.resolve("tenant-1","ftp-1")).thenReturn(Map.of("ftpHost","127.0.0.1","ftpUsername","validation","ftpPassword","validation-password","ftpPath","/out"));
    }

    private Map<String,Object> deliver(boolean marker,int count,Map<String,byte[]> files) throws Exception {
        try (var ftp = mockConstruction(FTPClient.class,(client,context)->{
            when(client.login(anyString(),anyString())).thenReturn(true);
            when(client.changeWorkingDirectory(anyString())).thenReturn(true);
            when(client.listFiles(anyString())).thenAnswer(call->{
                if (!files.containsKey(call.getArgument(0))) return new FTPFile[0];
                FTPFile file = new FTPFile(); file.setType(FTPFile.FILE_TYPE);return new FTPFile[]{file};
            });
            when(client.storeFile(anyString(),any(InputStream.class))).thenAnswer(call->{files.put(call.getArgument(0),((InputStream)call.getArgument(1)).readAllBytes());return true;});
            when(client.retrieveFile(anyString(),any(OutputStream.class))).thenAnswer(call->{((OutputStream)call.getArgument(1)).write(files.get(call.getArgument(0)));return true;});
            when(client.rename(anyString(),anyString())).thenAnswer(call->{files.put(call.getArgument(1),files.remove(call.getArgument(0)));return true;});
            when(client.deleteFile(anyString())).thenAnswer(call->{files.remove(call.getArgument(0));return true;});
        })) {
            var service = new DataPushFjghccDeliveryService(tenants,connections,json,marker);
            List<Map<String,Object>> rows = new ArrayList<>();for(int i=0;i<count;i++)rows.add(Map.of("id",i));
            var result = service.deliver("tenant-1","source-1","alarm_info","batch-1",rows,1);
            verify(ftp.constructed().getFirst()).rename(matches("batch-1\\.json\\..*\\.tmp"),eq("batch-1.json"));
            assertThat(json.readTree(files.get("batch-1.json")).size()).isEqualTo(count);
            return result;
        }
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void defaultPublishesOnlyVerifiedJsonWithConstantReads(int count) throws Exception {
        Map<String,byte[]> files = new HashMap<>();var result = deliver(false,count,files);
        assertThat(files.keySet()).containsExactly("batch-1.json");
        assertThat(result).containsEntry("completion","FILE_VERIFIED").doesNotContainKey("completionMarker");
        assertThat(reads.get()).isEqualTo(2);verify(connections).resolve("tenant-1","ftp-1");
    }

    @Test void legacyModePublishesAConsistentManifest() throws Exception {
        Map<String,byte[]> files = new HashMap<>();var result = deliver(true,20,files);
        assertThat(files.keySet()).containsExactlyInAnyOrder("batch-1.json","batch-1.json.done");
        var manifest = json.readTree(files.get("batch-1.json.done"));
        assertThat(manifest.get("sha256").asText()).isEqualTo(result.get("sha256"));
        assertThat(manifest.get("recordCount").asInt()).isEqualTo(20);
        assertThat(result).containsEntry("completion","FILE_AND_MARKER_VERIFIED").containsEntry("completionMarker","batch-1.json.done");
    }

    @Test void anotherTenantCannotUseThisBinding() {
        var service = new DataPushFjghccDeliveryService(tenants,connections,json,false);
        assertThatThrownBy(()->service.deliver("tenant-2","source-1","alarm_info","batch-1",List.of(Map.of("id",1)),1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("未配置 ODS");
        verifyNoInteractions(connections);
    }
}
