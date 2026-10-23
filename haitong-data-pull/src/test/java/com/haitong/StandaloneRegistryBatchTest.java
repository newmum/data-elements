package com.haitong;

import com.haitong.core.Json;
import com.haitong.core.RelayException;
import com.haitong.registry.StandaloneRegistryStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneRegistryBatchTest {
    static class CountingJdbc extends JdbcTemplate {
        int reads,writes;
        CountingJdbc(DriverManagerDataSource ds){super(ds);}
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper,Object... args){reads++;return super.query(sql,mapper,args);}
        @Override public int update(String sql,Object... args){writes++;return super.update(sql,args);}
    }
    static class Fixture {
        final Json json=new Json();final CountingJdbc jdbc;final StandaloneRegistryStore store;final TransactionTemplate tx;
        Fixture(){var ds=new DriverManagerDataSource("jdbc:h2:mem:registry-batch-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("db/h2.sql")).execute(ds);jdbc=new CountingJdbc(ds);store=new StandaloneRegistryStore(jdbc,json);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));}
        StandaloneRegistryStore.BatchSyncResult sync(String key,List<Map<String,Object>> sources){byte[] input=json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",sources));return tx.execute(s->store.synchronizeBatch(key,input));}
    }
    static Map<String,Object> source(String id){return new LinkedHashMap<>(Map.of("datasourceId",id,"datasourceName",id,"clientIds",List.of("push-"+id),"tables",List.of(Map.of("tableName","cases","schemaVersion","1","columns",List.of(Map.of("columnName","id","dataType","bigint","ordinal",1,"required",true))))));}

    @Test void twentyAndOneHundredSourcesUseOneReadAndRetryDoesNotDuplicate(){
        for(int count:List.of(20,100)){
            var f=new Fixture();var sources=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)sources.add(source("s"+i));
            var result=f.sync("batch",sources);assertEquals(count,result.datasourceIds().size());assertEquals(count,result.tableCount());assertEquals(count,result.columnCount());assertEquals(1,f.jdbc.reads);assertEquals(8,f.jdbc.writes);
            assertTrue(f.sync("batch",sources).duplicate());assertEquals(count,f.jdbc.queryForObject("select count(*) from ht_standalone_datasource",Integer.class));
            assertEquals(count,f.jdbc.queryForObject("select count(*) from ht_standalone_table_column",Integer.class));
            var replacement=source("s0");replacement.put("tables",List.of(Map.of("tableName","new_cases","columns",List.of(Map.of("columnName","case_id","ordinal",1)))));
            f.sync("replace",List.of(replacement));assertEquals(count,f.jdbc.queryForObject("select count(*) from ht_standalone_table where is_del=0",Integer.class));
            assertThrows(RelayException.class,()->f.sync("batch",List.of(source("other"))));
        }
    }
    @Test void invalidOrDuplicateSourceRejectsWholeBatchBeforeAnyWrite(){
        var f=new Fixture();var invalid=source("bad");invalid.put("tables",List.of(Map.of("tableName","cases","columns",List.of())));
        assertThrows(RelayException.class,()->f.sync("bad",List.of(source("good"),invalid)));
        assertThrows(RelayException.class,()->f.sync("duplicate",List.of(source("good"),source("good"))));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_standalone_datasource",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_standalone_registry_sync",Integer.class));
    }
    @Test void emptyTableSnapshotProvisionsDatasourceIdentityAndRevokesPreviousTableGrants(){
        var f=new Fixture();var source=source("key-before-finish");f.sync("registered",List.of(source));
        source.put("tables",List.of());var result=f.sync("empty",List.of(source));
        assertEquals(0,result.tableCount());assertEquals(Set.of("push-key-before-finish"),f.store.clients("key-before-finish"));
        assertTrue(f.store.datasource("key-before-finish").orElseThrow().enabled());
        assertTrue(f.store.table("key-before-finish","cases").orElseThrow().deleted());
        assertTrue(f.store.columns("key-before-finish","cases").isEmpty());
    }
    @Test void failureDuringApplyRollsBackAllSourcesAndAudit(){
        var f=new Fixture();f.jdbc.execute("alter table ht_standalone_datasource add constraint fail_test check(datasource_id <> 'bad')");
        assertThrows(RuntimeException.class,()->f.sync("rollback",List.of(source("good"),source("bad"))));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_standalone_datasource",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from ht_standalone_registry_sync",Integer.class));
    }
}
