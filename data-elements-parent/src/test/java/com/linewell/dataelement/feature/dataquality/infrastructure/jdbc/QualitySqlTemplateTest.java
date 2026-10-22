package com.linewell.dataelement.feature.dataquality.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualitySqlTemplateTest {
    @Test void bindsValuesInOccurrenceOrderAndNeverInterpolatesValues() {
        String attack="' OR 1=1; --";
        var result=QualitySqlTemplate.compile("${column} = #{value} OR ${relatedColumn} = #{value}",Map.of("value",attack),"\"name\"","\"other\"");
        assertThat(result.sql()).isEqualTo("\"name\" = ? OR \"other\" = ?");
        assertThat(result.arguments()).containsExactly(attack,attack);
    }
    @Test void preservesNullZeroFalseAndRejectsMissingParameters() {
        Map<String,Object> values=new LinkedHashMap<>();values.put("nil",null);values.put("zero",0);values.put("flag",false);
        var result=QualitySqlTemplate.compile("${column} IN (#{nil},#{zero},#{flag})",values,"col",null);
        assertThat(result.arguments()).containsExactly(null,0,false);
        assertThatThrownBy(()->new QualitySqlTemplate().validate("${column}>#{minimum}",Map.of())).hasMessageContaining("minimum");
    }
    @ParameterizedTest @ValueSource(strings={
        "${column} IS NULL; DELETE FROM target", "${column} IN (SELECT value FROM other)",
        "${column}=SLEEP(10)", "${column}=other_column", "${column}=1 -- ignored", "${column}=1 /* ignored */",
        "${column}=?", "'${column}'='x'", "${column} = 0 SELECT 1", "${column} = other.function()"})
    void rejectsStatementsExternalReadsAndUncontrolledIdentifiers(String template) {
        assertThatThrownBy(()->new QualitySqlTemplate().validate(template,Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"${column} IS NULL","${column} <> TRIM(${column})",
        "COALESCE(${column},0) BETWEEN #{min} AND #{max}","${column} <> ${relatedColumn}",
        "CASE WHEN ${column} IS NULL THEN 0 ELSE 1 END = 0"})
    void supportsCommonPortableConditions(String template) {
        assertThat(new QualitySqlTemplate().validate(template,Map.of("min",0,"max",100))).containsKey("parameterNames");
    }
}
