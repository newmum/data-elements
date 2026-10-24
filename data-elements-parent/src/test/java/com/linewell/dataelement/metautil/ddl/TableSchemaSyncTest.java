package com.linewell.dataelement.metautil.ddl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.model.common.BizException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableSchemaSyncTest {
    @TempDir Path backups;
    private static Map<String,Object> field(String name,String type,String comment,boolean nullable,boolean pk) {
        return new LinkedHashMap<>(Map.of("columnName",name,"columnType",type,"columnComment",comment,"nullable",nullable,"primaryKey",pk));
    }
    @Test void metadataEqualDoesNotProduceFalseDiffsOrTreatIntegerDisplayWidthsAsChanges() {
        var fields=List.of(field("id","bigint(20)","编号",false,true),field("name","varchar(64)","姓名",true,false));
        var plan=TableSchemaSync.plan("CREATE TABLE t (`id` bigint NOT NULL COMMENT '编号', `name` varchar(64) DEFAULT NULL COMMENT '姓名', PRIMARY KEY (`id`)) ENGINE=InnoDB",fields,"`db`.`t`","actor");
        assertTrue(plan.changes().isEmpty(),plan.sql());
    }
    @Test void missingHistoricPrimaryFlagNeverMeansDroppingTheLiveKey() {
        var field=field("id","bigint","编号",false,true);field.remove("primaryKey");
        var plan=TableSchemaSync.plan("CREATE TABLE t (`id` bigint NOT NULL COMMENT '编号', PRIMARY KEY (`id`)) ENGINE=InnoDB",List.of(field),"`db`.`t`","actor");
        assertTrue(plan.changes().isEmpty(),plan.sql());
    }
    @Test void commentAndKeyChangesPreserveDefaultCollationAutoIncrementOnUpdateAndGeneratedDefinition() {
        var fields=List.of(field("id","bigint","新编号",false,true),field("name","varchar(64)","新姓名",true,false),field("updated_at","timestamp","修改时间",false,false));
        var plan=TableSchemaSync.plan("CREATE TABLE t (`id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号', `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT '中文' COMMENT '姓名', `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', PRIMARY KEY (`id`), KEY `name_idx` (`name`)) ENGINE=InnoDB",fields,"`db`.`t`","actor");
        assertEquals(2,plan.changes().size());
        assertTrue(plan.sql().contains("AUTO_INCREMENT")); assertTrue(plan.sql().contains("utf8mb4_bin")); assertTrue(plan.sql().contains("DEFAULT '中文'"));
        assertFalse(plan.sql().contains("updated_at")); assertFalse(plan.sql().contains("DROP INDEX"));
    }
    @Test void addDropModifyAndPrimaryAreExplicitHighRiskAndPlanChangesOnConcurrency() {
        String ddl="CREATE TABLE t (`id` bigint NOT NULL, `old` varchar(8), PRIMARY KEY (`id`)) ENGINE=InnoDB";
        var fields=List.of(field("id","varchar(64)","标识",false,false),field("new_field","varchar(20)","新增",false,true));
        var plan=TableSchemaSync.plan(ddl,fields,"`db`.`t`","actor");
        assertTrue(plan.sql().contains("DROP COLUMN `old`")); assertTrue(plan.sql().contains("ADD COLUMN `new_field`"));
        assertTrue(plan.sql().contains("DROP PRIMARY KEY")); assertTrue(plan.sql().contains("ADD PRIMARY KEY (`new_field`)"));
        assertTrue((boolean)plan.view("t").get("highRisk"));
        assertNotEquals(plan.fingerprint(),TableSchemaSync.plan(ddl.replace("varchar(8)","varchar(9)"),fields,"`db`.`t`","actor").fingerprint());
        assertNotEquals(plan.fingerprint(),TableSchemaSync.plan(ddl,fields,"`db`.`t`","another-actor").fingerprint());
    }
    @Test void rejectsInjectedTypesDuplicateNamesAndIncompleteGeneratedColumns() {
        String ddl="CREATE TABLE t (id bigint) ENGINE=InnoDB";
        assertThrows(RuntimeException.class,()->TableSchemaSync.plan(ddl,List.of(field("id","int); DROP TABLE t; --","",true,false)),"`db`.`t`","actor"));
        assertThrows(BizException.class,()->TableSchemaSync.plan(ddl,List.of(field("id","int","",true,false),field("ID","int","",true,false)),"`db`.`t`","actor"));
        var generated=field("new_field","int","",true,false); generated.put("extra","VIRTUAL GENERATED");
        assertFalse(TableSchemaSync.plan(ddl,List.of(generated),"`db`.`t`","actor").blockers().isEmpty());
    }
    private record Fixture(TableSchemaSync sync,Connection connection,Statement statement,ResultSet result) {}
    private Fixture fixture(String ddl) throws Exception {
        var connection=mock(Connection.class); var statement=mock(Statement.class); var result=mock(ResultSet.class); var metadata=mock(DatabaseMetaData.class);
        when(connection.getCatalog()).thenReturn("db"); when(connection.createStatement()).thenReturn(statement); when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL"); when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(statement.executeQuery(anyString())).thenReturn(result); when(result.next()).thenReturn(true); when(result.getString(2)).thenReturn(ddl);
        return new Fixture(new TableSchemaSync(config->connection,backups),connection,statement,result);
    }
    @Test void twentyAndHundredColumnsUseOnePhysicalReadAndNeverIssuePerColumnQueries() throws Exception {
        for (int count:List.of(20,100)) {
            List<Map<String,Object>> fields=new ArrayList<>(); List<String> columns=new ArrayList<>();
            for(int i=0;i<count;i++){fields.add(field("f"+i,"varchar(64)","字段"+i,true,false));columns.add("`f"+i+"` varchar(64) COMMENT '字段"+i+"'");}
            var f=fixture("CREATE TABLE t ("+String.join(",",columns)+") ENGINE=InnoDB");
            assertTrue(((List<?>)f.sync.preview(Map.of(),"t",fields,"actor").get("changes")).isEmpty());
            verify(f.statement,times(1)).executeQuery("SHOW CREATE TABLE `db`.`t`"); verify(f.statement,never()).executeUpdate(anyString());
            System.out.println("Schema comparison: fields="+count+", physical metadata SQL reads=1, no writes");
        }
    }
    @Test void confirmationAndStalePlanPreventAllWritesAndCannotTargetOtherDatabases() throws Exception {
        var f=fixture("CREATE TABLE t (id bigint) ENGINE=InnoDB"); var fields=List.of(field("id","bigint","新注释",true,false));
        assertThrows(BizException.class,()->f.sync.apply(Map.of(),"t",fields,"actor","old","t",false));
        assertThrows(BizException.class,()->f.sync.apply(Map.of(),"t",fields,"actor","old","t",true));
        assertThrows(BizException.class,()->f.sync.preview(Map.of(),"foreign.t",fields,"actor"));
        verify(f.statement,never()).executeUpdate(anyString());
    }
    @Test void unsupportedEngineIsVisibleBeforeConfirmationAndCannotBeSpoofedInComments() throws Exception {
        var f=fixture("CREATE TABLE t (id bigint COMMENT 'ENGINE=InnoDB') ENGINE=MyISAM");
        var result=f.sync.preview(Map.of(),"t",List.of(field("id","bigint","新注释",true,false)),"actor");
        assertEquals(false,result.get("canApply")); assertFalse(((List<?>)result.get("blockers")).isEmpty());
        verify(f.statement,never()).executeUpdate(anyString());
    }
    @Test void confirmedApplyUsesServerDdlBacksUpStructureAndReadsItBack() throws Exception {
        String before="CREATE TABLE t (id bigint COMMENT '旧') ENGINE=InnoDB", after=before.replace("旧","新");
        var f=fixture(before); var fields=List.of(field("id","bigint","新",true,false));
        String fingerprint=(String)f.sync.preview(Map.of(),"t",fields,"actor").get("fingerprint");
        when(f.result.getString(2)).thenReturn(before,after);
        var response=f.sync.apply(Map.of(),"t",fields,"actor",fingerprint,"t",true);
        assertEquals(true,response.get("verified")); assertEquals(true,response.get("applied"));
        verify(f.statement,times(1)).executeUpdate(startsWith("ALTER TABLE `db`.`t`"));
        assertTrue(java.nio.file.Files.list(backups).anyMatch(p->p.toString().endsWith(".sql")));
    }
}
