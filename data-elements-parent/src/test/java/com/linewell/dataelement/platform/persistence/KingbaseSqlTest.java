package com.linewell.dataelement.platform.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingbaseSqlTest {
    @Test void adaptsOnlyTheReviewedTextRevisionIncrement() {
        String update = "UPDATE data_apply_form_t SET revision=COALESCE(CAST(revision AS UNSIGNED),0)+1 WHERE tenant_id=? AND tid=?";
        String adapted = KingbaseSql.adapt(update);
        assertFalse(adapted.contains("UNSIGNED"));
        assertTrue(adapted.contains("THEN CAST(TRIM(revision) AS NUMERIC(32,0)) ELSE 0 END"));
        assertTrue(adapted.endsWith("WHERE tenant_id=? AND tid=?"));
        String text = "SELECT 'COALESCE(CAST(revision AS UNSIGNED),0)+1'";
        assertEquals(text, KingbaseSql.adapt(text));
    }
    @Test void preservesLiteralsWhileAdaptingIdentifiersAndAliases() {
        String sql=KingbaseSql.adapt("SELECT `name` AS userName,'`name` DATABASE()' AS note FROM rm_user_t WHERE table_schema=DATABASE()");
        assertTrue(sql.contains("\"name\" AS \"userName\""));
        assertTrue(sql.contains("'`name` DATABASE()'"));
        assertTrue(sql.contains("table_schema=current_schema()"));
    }
    @Test void translatesJsonTableWithoutDuplicatingBindingsOrLosingOrdinality() {
        String input="SELECT j.* FROM JSON_TABLE(CASE WHEN JSON_VALID(?)=1 THEN ? ELSE '[]' END,'$[*]' COLUMNS(ordinal FOR ORDINALITY,field_count BIGINT PATH '$.fieldCount',name VARCHAR(500) PATH '$.name')) j LIMIT ? OFFSET ?";
        String output=KingbaseSql.adapt(input);
        assertFalse(output.contains("JSON_TABLE("));assertTrue(output.contains("WITH ORDINALITY"));
        assertTrue(output.contains("AS \"field_count\""));assertTrue(output.contains("AS \"name\""));
        assertEquals(4,output.chars().filter(c->c=='?').count());
        assertTrue(output.contains("LIMIT ? OFFSET ?"));
    }
    @Test void supportsNestedArrayAndNullOnErrorNumericColumn() {
        String nested=KingbaseSql.adapt("SELECT n.ordinal FROM nifi_pipeline_t flow LEFT JOIN JSON_TABLE(CASE WHEN JSON_VALID(flow.dsl_json)=1 THEN flow.dsl_json ELSE '{}' END,'$.nodes[*]' COLUMNS(ordinal FOR ORDINALITY)) n ON 1=1");
        assertTrue(nested.contains("'$.nodes[*]'"));assertTrue(nested.contains("LEFT JOIN LATERAL"));
        String version=KingbaseSql.adapt("SELECT position FROM JSON_TABLE(?,'$[*]' COLUMNS(position FOR ORDINALITY,name VARCHAR(500) PATH '$.name',item_length DECIMAL(20,4) PATH '$.length' NULL ON ERROR)) item");
        assertTrue(version.contains("public.json_number("));assertEquals(1,version.chars().filter(c->c=='?').count());
    }
    @Test void keepsAtomicUpsertWithReviewedTenantConflictKey() {
        String sql=KingbaseSql.adapt("INSERT INTO surveillance_topic_daily_stat_t(tid,tenant_id,input_count,updated_time) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE input_count=input_count+VALUES(input_count),updated_time=VALUES(updated_time)");
        assertTrue(sql.contains("ON CONFLICT (tenant_id,engine_code,resource_code,stat_date)"));
        assertTrue(sql.contains("input_count=surveillance_topic_daily_stat_t.input_count+EXCLUDED.input_count"));
        assertTrue(sql.contains("updated_time=EXCLUDED.updated_time"));
    }
    @Test void rejectsUnknownUpsertRatherThanGuessingPermissionOrUniqueKeys() {
        assertThrows(IllegalArgumentException.class,()->KingbaseSql.adapt("INSERT INTO unrelated(tid) VALUES(?) ON DUPLICATE KEY UPDATE tid=VALUES(tid)"));
    }
    @Test void doesNotInterpretSqlTextStoredAsLiteralContent() {
        String literal="SELECT 'JSON_TABLE(a,b) ON DUPLICATE KEY UPDATE' AS note /* JSON_TABLE(a,b) */";
        assertEquals(literal,KingbaseSql.adapt(literal));
        String insert="INSERT INTO surveillance_kafka_connection_t(tid,status) VALUES(?,?) ON DUPLICATE KEY UPDATE status='status'";
        assertTrue(KingbaseSql.adapt(insert).contains("status='status'"));
        String storedSql="INSERT INTO surveillance_kafka_connection_t(tid,status) VALUES(?,?) ON DUPLICATE KEY UPDATE status='VALUES(status)'";
        assertTrue(KingbaseSql.adapt(storedSql).contains("status='VALUES(status)'"));
    }
    @Test void mapsMySqlCaseInsensitiveColumnIdentifiersToNativeLowercase() {
        assertEquals("SELECT \"tid\" FROM \"sym_config_t\"",KingbaseSql.adapt("SELECT `TID` FROM `SYM_CONFIG_T`"));
    }
    @Test void preservesCompleteIdentifiersInLengthlessCharCasts() {
        String sql=KingbaseSql.adapt("SELECT CAST(source.id AS CHAR),CAST(? AS CHAR),CAST(source.code AS CHAR(8)) FROM source");
        assertTrue(sql.contains("CAST(source.id AS TEXT)"));
        assertTrue(sql.contains("CAST(? AS TEXT)"));
        assertTrue(sql.contains("CAST(source.code AS CHAR(8))"));
        assertEquals("SELECT 'CAST(id AS CHAR)'",KingbaseSql.adapt("SELECT 'CAST(id AS CHAR)'"));
    }
}
