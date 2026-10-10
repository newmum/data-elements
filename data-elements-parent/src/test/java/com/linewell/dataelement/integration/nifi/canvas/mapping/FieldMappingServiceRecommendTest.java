package com.linewell.dataelement.integration.nifi.canvas.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldMappingServiceRecommendTest {

    private final FieldMappingService service = new FieldMappingService();

    @Test
    void recommendsIdentifiersThatDifferOnlyByCaseWithoutRewritingThem() {
        var response = service.recommend(
                List.of(new FieldMappingService.FieldMeta("CASE_ID", "/CASE_ID", "varchar", true, false, false, false)),
                List.of(new FieldMappingService.FieldMeta("case_id", "/case_id", "varchar", true, false, false, false)));

        assertEquals(1, response.recommendations().size());
        var recommendation = response.recommendations().getFirst();
        assertEquals("/CASE_ID", recommendation.from());
        assertEquals("/case_id", recommendation.to());
        assertEquals("英文名忽略大小写匹配", recommendation.reason());
        assertEquals(1.0, recommendation.confidence());
    }

    @Test
    void skipsBlankNamesInsteadOfCreatingAnUnrelatedRecommendation() {
        var response = service.recommend(
                List.of(new FieldMappingService.FieldMeta("", "/", "varchar", true, false, false, false)),
                List.of(new FieldMappingService.FieldMeta("", "/", "varchar", true, false, false, false)));

        assertTrue(response.recommendations().isEmpty());
    }

    @Test
    void identifierMatchWinsWhenDisplayCommentsDifferOrPointAtAnotherField() {
        var response = service.recommend(List.of(
                field("案件编号", "/unrelated"), field("来源案件主键", "/CASE_ID")),
                List.of(field("案件编号", "/case_id")));
        var recommendation = response.recommendations().getFirst();
        assertEquals("/CASE_ID", recommendation.from());
        assertEquals("/case_id", recommendation.to());
        assertEquals("英文名忽略大小写匹配", recommendation.reason());
        assertEquals(1.0, recommendation.confidence());
    }

    @Test
    void lowercaseSourcesMatchUppercaseTargetsEvenWithoutComments() {
        var response = service.recommend(List.of(field("", "/case_id"), field("", "/log_id")),
                List.of(field("案件编号", "/CASE_ID"), field("", "/LOG_ID")));
        assertEquals(2, response.recommendations().size());
        assertEquals("/case_id", response.recommendations().get(0).from());
        assertEquals("/CASE_ID", response.recommendations().get(0).to());
        assertEquals("/log_id", response.spec().path("mappings").get(1).path("from").asText());
        assertEquals("/LOG_ID", response.spec().path("mappings").get(1).path("to").asText());
    }

    @Test
    void ambiguousCaseVariantsAreNotArbitrarilyAutoMapped() {
        assertTrue(service.recommend(List.of(field("", "/ID"), field("", "/id")),
                List.of(field("", "/Id"))).recommendations().isEmpty());
        assertTrue(service.recommend(List.of(field("", "/id")),
                List.of(field("", "/Id"), field("", "/ID"))).recommendations().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"RAW(16),varbinary(16)", "BYTEA,blob", "LONG RAW,blob", "NCLOB,text",
            "BINARY_FLOAT,float", "BINARY_DOUBLE,double", "float8,double", "NUMBER(18),decimal"})
    void nativeTypesDoNotIntroduceSpuriousCastsInDirectMappings(String sourceType, String targetType) {
        var response = service.validate("""
                {"version":"1.0","mappings":[{"from":"/value","to":"/value"}]}
                """, List.of(new FieldMappingService.FieldMeta("value", "/value", sourceType, true, false, false, false)),
                List.of(new FieldMappingService.FieldMeta("value", "/value", targetType, true, false, false, false)));
        assertTrue(response.valid(), response.errors().toString());
        assertTrue(response.warnings().stream().noneMatch(issue -> issue.code().equals("TYPE_NEEDS_CAST")),
                response.warnings().toString());
    }

    private FieldMappingService.FieldMeta field(String label, String path) {
        return new FieldMappingService.FieldMeta(label, path, "varchar", true, false, false, false);
    }
}
