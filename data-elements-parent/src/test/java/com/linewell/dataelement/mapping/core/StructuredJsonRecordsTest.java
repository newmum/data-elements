package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StructuredJsonRecordsTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void stringCastIsLosslessForContainersAndPreservesPlainTextAndBinary() throws Exception {
        for(Object value:List.of(new Object[]{"x",2,null},java.util.Map.of("items",List.of("中文",7)),new int[]{1,2})) {
            Object text=ValueTypes.cast(value,"STRING",0,0,java.math.RoundingMode.UNNECESSARY);
            assertThat(json.readTree(text.toString())).isEqualTo(json.valueToTree(value));
        }
        assertThat(ValueTypes.cast("{not JSON}","STRING",0,0,java.math.RoundingMode.UNNECESSARY)).isEqualTo("{not JSON}");
        assertThat(ValueTypes.cast(new byte[]{1,2},"STRING",0,0,java.math.RoundingMode.UNNECESSARY)).isEqualTo("AQI=");
    }
    @Test void originalPathsAreSeparateFromAliasesAndContainersRemainLosslessText() throws Exception {
        var input=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("""
                {"@timestamp":"2026-11-21T01:02:03Z","@metadata":{"beat":"validation"},
                 "agent":{"id":"source-agent"},"tags":["中文",null,7]}
                """);
        input.put("literal","{\"key\":1}");
        var entry=StructuredJsonRecords.normalize(input);
        assertThat(entry.values()).containsEntry("timestamp","2026-11-21T01:02:03Z")
                .containsEntry("metadata_beat","validation").containsEntry("agent_id","source-agent")
                .containsEntry("literal","{\"key\":1}");
        assertThat(json.readTree(entry.values().get("tags").toString())).isEqualTo(json.readTree("[\"中文\",null,7]"));
        assertThat(entry.paths()).containsEntry("timestamp",List.of("@timestamp"))
                .containsEntry("metadata_beat",List.of("@metadata","beat")).containsEntry("agent_id",List.of("agent","id"));
    }
    @Test void literalUnderscoreAndNestedPathCannotSilentlyOverwriteEachOther() throws Exception {
        assertThatThrownBy(()->StructuredJsonRecords.normalize(json.readTree("{\"agent_id\":1,\"agent\":{\"id\":2}}")))
                .isInstanceOf(MappingException.class).hasMessageContaining("agent_id");
        var shape=new StructuredJsonRecords.Shape();
        shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"agent_id\":1}")));
        assertThatThrownBy(()->shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"agent\":{\"id\":2}}"))))
                .isInstanceOf(MappingException.class);
    }
    @Test void scalarAndNestedArrayCarriersKeepValuesAndOptionalFieldsKeepNullability() throws Exception {
        var shape=new StructuredJsonRecords.Shape();
        shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"id\":1}")));
        shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"id\":2,\"name\":null}")));
        assertThat(shape.json().path("fieldKinds").path("name").toString()).contains("NULL");
        assertThat(StructuredJsonRecords.normalize(json.readTree("7")).values()).containsEntry("value",7L);
        assertThat(StructuredJsonRecords.normalize(json.readTree("[1,2]")).values()).containsEntry("value","[1,2]");
        assertThat(StructuredJsonRecords.normalize(json.readTree("null")).values()).containsEntry("value",null);
        assertThat(StructuredJsonRecords.alias("2021年")).isEqualTo("f_2021年");
    }
    @Test void jsonTextUsesTheRecordBudgetAndDoesNotReturnJavaArrayIdentity() throws Exception {
        String value=StructuredJsonRecords.jsonText(new Object[]{"中文",null,7});
        assertThat(json.readTree(value)).isEqualTo(json.readTree("[\"中文\",null,7]"));
        assertThatThrownBy(()->StructuredJsonRecords.jsonText(new String[]{"a".repeat((int)RecordBudget.RECORD_BYTES+1)}))
                .isInstanceOf(MappingException.class).hasMessageContaining("预算");
    }
    @Test void managedRecordHeadersPreserveNumericAndLiteralUnderscoreNames() {
        var original=new java.util.LinkedHashMap<String,Object>();original.put("月份",null);original.put("2021年",null);original.put("literal_name",null);
        var shape=new StructuredJsonRecords.Shape();shape.accept(StructuredJsonRecords.record(original));
        var contract=new StructuredJsonRecords.Contract(shape.json());
        assertThat(contract.mode()).isEqualTo("RECORD");
        assertThat(contract.normalize(java.util.Map.of("月份","一月","2021年",17L,"literal_name","原值")).values())
                .containsEntry("f_2021年",17L).containsEntry("月份","一月").containsEntry("literal_name","原值");
    }
    @Test void managedJsonPathsRejectDriftAndKeepOptionalNulls() throws Exception {
        var shape=new StructuredJsonRecords.Shape();
        shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"@timestamp\":\"a\",\"agent\":{\"id\":1}}")));
        shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"@timestamp\":\"b\"}")));
        var contract=new StructuredJsonRecords.Contract(shape.json());
        assertThat(contract.normalize(json.readTree("{\"@timestamp\":\"c\",\"agent\":{\"id\":2}}")).values()).containsEntry("agent_id",2L);
        assertThat(contract.normalize(json.readTree("{\"@timestamp\":\"c\"}")).values()).containsEntry("timestamp","c");
        assertThatThrownBy(()->contract.normalize(json.readTree("{\"@timestamp\":\"c\",\"agent_id\":2}"))).isInstanceOf(MappingException.class);
        assertThatThrownBy(()->contract.normalize(json.readTree("{\"agent\":{\"id\":2}}"))).isInstanceOf(MappingException.class);
    }
    @Test void sampledEmptyAndNonemptyObjectParentsRetainExactJsonAndChildrenInEitherSampleOrder() throws Exception {
        String empty="{\"params\":{}}",full="{\"params\":{\"content\":\"中文\",\"log_level\":7,\"items\":[null,2]}}";
        for(var samples:List.of(List.of(empty,full),List.of(full,empty))) {
            var shape=new StructuredJsonRecords.Shape();for(String sample:samples)shape.accept(StructuredJsonRecords.normalize(json.readTree(sample)));
            var contract=new StructuredJsonRecords.Contract(shape.json());
            for(String input:List.of(empty,full)) {
                var record=contract.normalize(json.readTree(input));
                assertThat(json.readTree(record.values().get("params").toString())).isEqualTo(json.readTree(input).path("params"));
                if(input.equals(full))assertThat(record.values()).containsEntry("params_content","中文").containsEntry("params_log_level",7L);
            }
            assertThatThrownBy(()->contract.normalize(json.readTree("{\"params\":\"changed\"}"))).isInstanceOf(MappingException.class);
        }
    }
    @Test void permittingObservedObjectParentsDoesNotPermitScalarDriftOrPathCollisions() throws Exception {
        var shape=new StructuredJsonRecords.Shape();shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"params\":1}")));
        assertThatThrownBy(()->new StructuredJsonRecords.Contract(shape.json()).normalize(json.readTree("{\"params\":{\"child\":2}}")))
                .isInstanceOf(MappingException.class).hasMessageContaining("叶字段");
        var objects=new StructuredJsonRecords.Shape();objects.accept(StructuredJsonRecords.normalize(json.readTree("{\"@params\":{}}")));
        assertThatThrownBy(()->new StructuredJsonRecords.Contract(objects.json()).normalize(json.readTree("{\"@params\":{\"child\":1},\"params\":2}")))
                .isInstanceOf(MappingException.class);
    }
    @Test void retainedParentAndChildrenShareOneRecordBudget() throws Exception {
        var shape=new StructuredJsonRecords.Shape();shape.accept(StructuredJsonRecords.normalize(json.readTree("{\"params\":{}}")));
        var input=json.createObjectNode();input.putObject("params").put("content","a".repeat((int)RecordBudget.RECORD_BYTES/2+1024));
        shape.accept(StructuredJsonRecords.normalize(input));
        assertThatThrownBy(()->new StructuredJsonRecords.Contract(shape.json()).normalize(input))
                .isInstanceOf(MappingException.class).hasMessageContaining("上限");
    }
}
