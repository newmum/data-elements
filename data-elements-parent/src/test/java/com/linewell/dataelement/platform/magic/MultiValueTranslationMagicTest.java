package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class MultiValueTranslationMagicTest {
    @Test
    void multiDictionaryQueryHasBoundedCodeMarkerAndRetainsConditions() throws Exception {
        String source = CanonicalMagicSources.byId("ods_data_agg_task_ensure_01");
        String helper = source.substring(source.indexOf("var multiDictionaryQuery ="), source.indexOf("// 字段统一格式"));
        String script = "var dictionaryLookupConditions = (relation) => ['enabled = 1']\n" + helper
                + "\nreturn multiDictionaryQuery(relation, 'SCHEMA.DICT')";
        Object value = MagicScript.create(script, null).execute(new MagicScriptContext(Map.of("relation", Map.of(
                "dictionaryKeyField", "CODE", "dictionaryLabelField", "LABEL"))));
        assertEquals("SELECT CODE, LABEL FROM SCHEMA.DICT WHERE CODE IN (:codes) AND enabled = 1", value);
        assertFalse(source.contains("多值翻译目前只支持"));
        assertFalse(source.contains("JSON_TABLE"));
        assertFalse(source.contains("GROUP_CONCAT"));
    }
}
