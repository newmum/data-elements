package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCompilerLookupSqlTest {

    @Test
    void combinesLookupsIntoOneEnrichmentQueryWithOracleSafeResultReferences() throws Exception {
        var first = lookup("target_one", "DETAIL_ONE", "__lookup_0_0");
        var second = lookup("target_two", "DETAIL_TWO", "__lookup_1_0");

        String query = (String) invoke("buildLookupEnrichmentQuery",
                List.of(first, second), List.of("__lookup_0_0", "__lookup_1_0"));

        assertTrue(query.startsWith("SELECT "));
        assertEquals(2, count(query, " LEFT JOIN ("));
        assertTrue(query.contains("AS \"__lookup_0_0\""));
        assertTrue(query.contains("lookup_result_0.DETAIL_ONE AS \"target_one\""));
        assertTrue(query.contains("lookup_result_1.DETAIL_TWO AS \"target_two\""));
        assertTrue(query.contains("AS \"__lookup_anchor\" FROM DUAL"));
    }

    @Test
    void usesOracleFoldedLookupAliasButPreservesGeneratedCnOutputName() throws Exception {
        var lookup = lookup("SCR_CYZJDM_cn", "SCR_CYZJDM_cn", "__lookup_0_0");

        String query = (String) invoke("buildLookupEnrichmentQuery",
                List.of(lookup), List.of("__lookup_0_0"));

        assertTrue(query.contains("lookup_result_0.SCR_CYZJDM_cn AS \"SCR_CYZJDM_cn\""));
        assertTrue(!query.contains("lookup_result_0.\"SCR_CYZJDM_cn\""));
    }

    @Test
    void extractsLookupArgumentsFromTheSplitRecordObjectRoot() throws Exception {
        String path = (String) invoke("jsonPath", "__lookup_0_0");

        assertEquals("$.__lookup_0_0", path);
    }

    private FieldMappingService.LookupPlan lookup(String target, String result, String parameterRecordField) {
        return new FieldMappingService.LookupPlan(target, result,
                "SELECT " + result + " FROM T_DICT WHERE CODE = ?",
                List.of("code"), List.of(parameterRecordField), "NULL", Map.of());
    }

    private Object invoke(String methodName, Object... args) throws Exception {
        Method method;
        if ("jsonPath".equals(methodName)) {
            method = DslCompiler.class.getDeclaredMethod(methodName, String.class);
        } else {
            method = DslCompiler.class.getDeclaredMethod(methodName, List.class, List.class);
        }
        method.setAccessible(true);
        return method.invoke(new DslCompiler(null, null, null, null), args);
    }
    private int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
