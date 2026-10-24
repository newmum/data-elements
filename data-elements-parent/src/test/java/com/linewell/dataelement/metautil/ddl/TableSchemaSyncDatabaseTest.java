package com.linewell.dataelement.metautil.ddl;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.model.common.BizException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in validation against a disposable table in the configured validation source. */
@EnabledIfEnvironmentVariable(named="SCHEMA_SYNC_VALIDATION_URL",matches="jdbc:mysql:.*")
class TableSchemaSyncDatabaseTest {
    @TempDir Path backups;
    @Test void realMysqlPreservesDataAndUnchangedAttributesAndRejectsStalePlan() throws Exception {
        var config=Map.<String,Object>of("jdbcURL",System.getenv("SCHEMA_SYNC_VALIDATION_URL"),"username",System.getenv("SCHEMA_SYNC_VALIDATION_USER"),"password",System.getenv("SCHEMA_SYNC_VALIDATION_PASSWORD"));
        String table="validation_schema_sync_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var module=new TableSchemaSync(c->DriverManager.getConnection((String)c.get("jdbcURL"),(String)c.get("username"),(String)c.get("password")),backups);
        boolean created=false;
        try(var connection=DriverManager.getConnection((String)config.get("jdbcURL"),(String)config.get("username"),(String)config.get("password"));var sql=connection.createStatement()) {
            try {
                sql.execute("CREATE TABLE `"+table+"` (`id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号', `code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT 'A' COMMENT '旧注释', `obsolete` varchar(8), PRIMARY KEY (`id`), KEY code_idx (`code`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");created=true;
                sql.executeUpdate("INSERT INTO `"+table+"` (code,obsolete) VALUES ('A','x'),('B','y')");
                var fields=List.of(field("id","bigint","编号",false,true),field("code","varchar(32)","新注释",true,false),field("note","varchar(64)","备注",true,false));
                var plan=module.preview(config,table,fields,"validation-actor");
                String firstFingerprint=(String)plan.get("fingerprint");
                sql.execute("ALTER TABLE `"+table+"` ADD COLUMN concurrent_field int");
                assertThrows(BizException.class,()->module.apply(config,table,fields,"validation-actor",firstFingerprint,table,true));
                sql.execute("ALTER TABLE `"+table+"` DROP COLUMN concurrent_field");
                plan=module.preview(config,table,fields,"validation-actor");
                var response=module.apply(config,table,fields,"validation-actor",(String)plan.get("fingerprint"),table,true);
                assertEquals(true,response.get("verified"));
                try(var rows=sql.executeQuery("SELECT COUNT(*) FROM `"+table+"`")){assertTrue(rows.next());assertEquals(2,rows.getInt(1));}
                try(var rows=sql.executeQuery("SHOW CREATE TABLE `"+table+"`")){assertTrue(rows.next());String ddl=rows.getString(2);assertTrue(ddl.contains("utf8mb4_bin"));assertTrue(ddl.contains("code_idx"));assertTrue(ddl.contains("AUTO_INCREMENT"));assertTrue(ddl.contains("DEFAULT 'A'"));assertFalse(ddl.contains("obsolete"));}
                assertTrue(((List<?>)module.preview(config,table,fields,"validation-actor").get("changes")).isEmpty());
                System.out.println("Real validation source: compare, stale rejection, confirmed ALTER, structure readback, two rows preserved, original defaults/index/collation/auto-increment retained");
            } finally {
                if(created && table.startsWith("validation_schema_sync_")) sql.execute("DROP TABLE `"+table+"`");
            }
        }
    }
    private static Map<String,Object> field(String name,String type,String comment,boolean nullable,boolean pk) {
        return Map.of("columnName",name,"columnType",type,"columnComment",comment,"nullable",nullable,"primaryKey",pk);
    }
}
