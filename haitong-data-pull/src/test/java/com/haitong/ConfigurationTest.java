package com.haitong;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.registry.StandalonePushSchema;
import com.haitong.registry.StandaloneRegistryStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    @TempDir Path work;
    RelayProperties base() {
        var p=new RelayProperties();p.setWorkDir(work.toString());
        p.getSecurity().setApiKey("test-client-0123456789abcdef0123456789");p.getSecurity().setAdminKey("test-admin-0123456789abcdef0123456789");
        var target=new RelayProperties.Target();target.setType("LOCAL");p.getTargets().put("local",target);
        var route=new RelayProperties.Route();route.setTargets(List.of("local"));p.getRoutes().put("demo",route);return p;
    }
    @Test void defaultLocalPlanAndSnapshotRoundTrip() throws Exception {
        var p=base();var catalog=new RouteCatalog(p,new Secrets(p));catalog.afterPropertiesSet();var json=new Json();
        var plan=catalog.route("demo");assertEquals(plan,json.read(json.write(plan),RoutePlan.class));
    }
    @Test void disabledKafkaCannotBeReferencedByAnEnabledRoute() throws Exception {
        var p=base();p.getTargets().get("local").setType("KAFKA");
        var catalog=new RouteCatalog(p,new Secrets(p));assertThrows(IllegalStateException.class,catalog::afterPropertiesSet);
    }
    @Test void unsafeSynchronousOperationIsRejected() throws Exception {
        var p=base();var a=new RelayProperties.Action();a.setHandler("inspect");a.setMode("SYNC");a.setSafeRetry(false);p.getActions().put("bad",a);
        var catalog=new RouteCatalog(p,new Secrets(p));assertThrows(IllegalStateException.class,catalog::afterPropertiesSet);
    }
    @Test void secretsAreGeneratedOnceAndCredentialsStayOutOfSnapshots() throws Exception {
        var p=base();p.getSecurity().setApiKey("");p.getSecurity().setAdminKey("");p.getFtp().setPassword("private-not-in-snapshot");
        new Secrets(p);String before=Files.readString(work.resolve("keys/client.key"));new Secrets(p);
        assertEquals(before,Files.readString(work.resolve("keys/client.key")));
        var catalog=new RouteCatalog(p,new Secrets(p));assertFalse(new Json().write(catalog.route("demo")).contains("private-not-in-snapshot"));
    }
    @Test void standaloneContractValidatesWithoutRegistrationRegistry() {
        var p=base();
        var source=new RelayProperties.StandaloneDatasource();source.setClients(List.of("publisher"));
        var table=new RelayProperties.StandaloneTable();table.setSchemaVersion("v1");
        table.setRequiredFields(List.of("id"));table.setAllowedFields(List.of("id","name"));
        source.getTables().put("customer",table);p.getStandalone().getDatasources().put("source-001",source);
        var standalone=new StandalonePushSchema(p,new Json(),null);
        var input=new Json().bytes(Map.of("datasourceId","source-001","tableName","customer","schemaVersion","v1",
            "records",List.of(Map.of("id","1001","name","张三"))));
        var metadata=standalone.validate("publisher",input);
        assertEquals("standalone",metadata.get("mode"));assertEquals("source-001",metadata.get("datasourceId"));
        var error=assertThrows(RelayException.class,()->standalone.validate("publisher",new Json().bytes(Map.of(
            "datasourceId","source-001","tableName","customer","schemaVersion","v1","records",List.of(Map.of("name","张三"))))));
        assertEquals("STANDALONE_REQUIRED_FIELD_MISSING",error.code());
    }
    @Test void platformRegistrySyncPersistsStandaloneContractForPushValidation() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:standalone_registry;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/h2.sql")).execute(ds);
        var json=new Json();var store=new StandaloneRegistryStore(new JdbcTemplate(ds),json);
        byte[] sync=json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasource",Map.of(
            "datasourceId","source-001","datasourceName","人员系统","clientIds",List.of("publisher"),"tables",List.of(Map.of(
                "tableId","table-001","tableName","person","schemaVersion","v1","columns",List.of(
                    Map.of("columnName","id","dataType","varchar","ordinal",1,"required",true),
                    Map.of("columnName","name","dataType","varchar","ordinal",2,"required",false)
                ))))));
        var first=store.synchronize("registry-sync-001",sync);assertFalse(first.duplicate());assertEquals(1,first.tableCount());assertEquals(2,first.columnCount());
        assertTrue(store.synchronize("registry-sync-001",sync).duplicate());
        var p=base();var standalone=new StandalonePushSchema(p,json,store);
        assertEquals("standalone",standalone.validate("publisher",json.bytes(Map.of("datasourceId","source-001","tableName","person","schemaVersion","v1","records",List.of(Map.of("id","1","name","张三"))))).get("mode"));
        var error=assertThrows(RelayException.class,()->standalone.validate("publisher",json.bytes(Map.of("datasourceId","source-001","tableName","person","schemaVersion","v1","records",List.of(Map.of("id","1","unknown","x"))))));
        assertEquals("STANDALONE_FIELD_NOT_ALLOWED",error.code());
    }
}
