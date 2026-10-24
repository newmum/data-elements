package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** 登记连接配置与协议适配之间的契约：保存ES认证/索引范围，允许主动清空旧值。 */
class ElasticsearchRegistrationMagicTest {
    private static final List<String> IDS = List.of(
            "datapd_save_update_datasource_01", "metadata_columns_collect_24", "bc30a3edb1f04bceaeee373ed5952d4a",
            "metadata_tables_reexplore_diff_01", "a9dc328184b7410781b389f67fad1233", "metadata_db_info_01",
            "06ee6f0e37e44762be967dbd2d764780");

    @TestFactory List<DynamicTest> compileRegistrationScripts() throws Exception {
        return CanonicalMagicSources.under("api/02.数据盘点/02.数据库表").stream()
                .filter(source -> IDS.contains(source.metadataId()))
                .map(source -> DynamicTest.dynamicTest(source.relativePath(),
                        () -> MagicScript.create(source.source(), null).compile())).toList();
    }

    @Test void esAliasesSaveConnectionOptionsAndCanClearIndexScopeAndStaleCredentials() throws Exception {
        String full = CanonicalMagicSources.byId("datapd_save_update_datasource_01");
        String names = full.substring(full.indexOf("var connectionNames ="), full.indexOf("// Existing values"));
        String configuration = full.substring(full.indexOf("var requestedConnectionType ="), full.indexOf("var showConnect ="));
        String prefix = "import java.util.Map\nvar text=v=>v==null?null:v+''; var blank=v=>v==null||text(v).trim()=='';"
                + "var first=(row,keys)=>{for(key in keys){if(row[key]!=null){return row[key]}}return null};"
                + "var connectionConfig={};var connectionCount=0;\n";
        for (String type : List.of("es", "elastic", "elasticsearch")) {
            var props = Map.of("dbType", type, "authMethod", "NONE", "apiKey", "", "username", "",
                    "password", "", "database", "", "nodes", "http://localhost:9200", "defaultIndex", "", "indexName", "");
            Object actual = MagicScript.create(prefix + names + configuration + "return connectionConfig", null)
                    .execute(new MagicScriptContext(Map.of("props", props)));
            var saved = assertInstanceOf(Map.class, actual);
            props.forEach((key,value) -> { if (!key.equals("dbType")) assertEquals(value, saved.get(key), key); });
            assertFalse(saved.containsKey("dbName"));
        }
    }
}
