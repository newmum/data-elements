package com.haitong;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
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
}
