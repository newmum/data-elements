package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

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
        String names = full.substring(full.indexOf("var connectionNames ="), full.indexOf("var old ="));
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
    @Test void pushSourceAddressUsesHostForIpAndUrlAndPreservesOmittedValues() throws Exception {
        for (String host : List.of("192.0.2.8", "2001:db8::8", "https://upstream.example.test:8443/events?from=business")) {
            Map<?, ?> config = assertInstanceOf(Map.class, pushConfiguration(Map.of("host", "  " + host + "  "), Map.of("host", "old.example.test")));
            assertEquals(host, config.get("host"));
            assertEquals(true, config.get("pushChannelProvided"));
        }
        Map<?, ?> retained = assertInstanceOf(Map.class, pushConfiguration(Map.of(), Map.of("host", "existing.example.test")));
        assertEquals("existing.example.test", retained.get("host"));
    }

    @Test void pushSourceAddressCannotBeMissingOrExplicitlyCleared() throws Exception {
        var missing = assertInstanceOf(ExitValue.class, pushConfiguration(Map.of(), Map.of()));
        assertEquals("400", missing.getValues()[0].toString());
        for (String blank : List.of("", "  ")) {
            var result = assertInstanceOf(ExitValue.class, pushConfiguration(Map.of("host", blank), Map.of("host", "old.example.test")));
            assertEquals("400", result.getValues()[0].toString());
            assertEquals("请填写数据来源地址", result.getValues()[1]);
        }
        var nullHost = new HashMap<String, Object>(); nullHost.put("host", null);
        assertInstanceOf(ExitValue.class, pushConfiguration(nullHost, Map.of("host", "old.example.test")));
    }

    private Object pushConfiguration(Map<String, ?> props, Map<String, ?> previous) throws Exception {
        String full = CanonicalMagicSources.byId("datapd_save_update_datasource_01");
        String guard = full.substring(full.indexOf("// 数据推送由平台提供统一接入通道"), full.indexOf("// 数据内容类型独立于接入方式"));
        String script = "import java.util.Map\nvar text=v=>v==null?null:v+'';var blank=v=>v==null||text(v).trim()=='';"
                + "var accessMode='receive';var showConnect=0;var connectionConfig={};\n" + guard + "\nreturn connectionConfig;";
        return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of("props", props, "oldConnectionConfig", previous)));
    }
}
