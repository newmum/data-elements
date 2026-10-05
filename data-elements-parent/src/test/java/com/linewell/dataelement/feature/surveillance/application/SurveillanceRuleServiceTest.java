package com.linewell.dataelement.feature.surveillance.application;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceControlItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveillanceRuleServiceTest {
    private static final String TENANT = "tenant-a";

    @Test
    void batchRequestHasExplicitTenThousandEventLimit() {
        SurveillanceRuleService.Event event = new SurveillanceRuleService.Event("event", null, Map.of());
        assertEquals(10000, new SurveillanceRuleService.BatchMatchRequest("engine", "person",
                java.util.Collections.nCopies(10000, event), List.of()).events().size());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SurveillanceRuleService.BatchMatchRequest("engine", "person",
                        java.util.Collections.nCopies(10001, event), List.of()));
        assertTrue(error.getMessage().contains("10000"));
    }

    @Test
    void matchesAllMappedFieldsAndReturnsOneResultWithMultipleRules() {
        SurveillanceRuleService service = new SurveillanceRuleService(List.of());
        service.put(TENANT, new SurveillanceRuleSnapshot(
                "structured-surveillance", "vehicle", 12, Instant.now(), null, "digest-12", "TABLE", "rules", "ACTIVE",
                List.of(
                        new SurveillanceRuleSnapshot.Rule("rule-low", "vehicle", 1, "ACTIVE", null, null,
                                Map.of("plate_no", "闽A12345", "color", "蓝"), Map.of()),
                        new SurveillanceRuleSnapshot.Rule("rule-high", "vehicle", 10, "ACTIVE", null, null,
                                Map.of("plate_no", "闽A12345", "color", "蓝"), Map.of())
                )));

        SurveillanceRuleService.BatchMatchResponse response = service.match(TENANT,
                new SurveillanceRuleService.BatchMatchRequest(
                        "structured-surveillance", "vehicle",
                        List.of(new SurveillanceRuleService.Event("event-1", Instant.now(),
                                Map.of("plateNo", "闽A12345", "vehicleColor", "蓝"))),
                        List.of(new SurveillanceRuleService.FieldMapping("plateNo", "plate_no"),
                                new SurveillanceRuleService.FieldMapping("vehicleColor", "color"))));

        assertEquals(12, response.snapshotVersion());
        assertEquals(1, response.results().size());
        assertTrue(response.results().getFirst().matched());
        assertEquals("rule-high", response.results().getFirst().matches().getFirst().controlItemId());
        assertEquals(2, response.results().getFirst().matches().size());
        assertEquals("闽A12345", response.results().getFirst().data().get("plateNo"));
    }

    @Test
    void ignoresDisabledAndOutOfWindowRulesAndDoesNotTreatFailureAsUnmatched() {
        SurveillanceRuleService service = new SurveillanceRuleService(List.of());
        service.put(TENANT, new SurveillanceRuleSnapshot(
                "structured-surveillance", "person", 1, Instant.now(), null, null, "TABLE", "rules", "ACTIVE",
                List.of(
                        new SurveillanceRuleSnapshot.Rule("disabled", "person", 1, "DISABLED", null, null,
                                Map.of("id_card", "3501"), Map.of()),
                        new SurveillanceRuleSnapshot.Rule("expired", "person", 1, "ACTIVE", null,
                                Instant.now().minusSeconds(1), Map.of("id_card", "3501"), Map.of())
                )));

        SurveillanceRuleService.MatchResult result = service.match(TENANT,
                new SurveillanceRuleService.BatchMatchRequest("structured-surveillance", "person",
                        List.of(new SurveillanceRuleService.Event("event-2", null, Map.of("id_card", "3501"))),
                        List.of())).results().getFirst();

        assertFalse(result.matched());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void matchesOneDefinitionAgainstManyControlItemsAndReturnsControlItemId() {
        SurveillanceRuleSnapshot.RuleDefinition definition = new SurveillanceRuleSnapshot.RuleDefinition(
                "person.id_card.equal", "身份证号精确匹配", "PERSON", "AND", List.of("idCardNo"),
                10, "ACTIVE", null, null, "third-party", "重点人员", Map.of());
        SurveillanceRuleSnapshot.ControlItem item = new SurveillanceRuleSnapshot.ControlItem(
                "person-item-001", "person.id_card.equal", "PERSON",
                List.of(new SurveillanceRuleSnapshot.Identifier("ID_CARD_NO", "3501001234561234")),
                Map.of("name", "张三"), "THIRD-001", "涉案重点人员", null, null, "ACTIVE", Map.of());

        SurveillanceRuleService service = new SurveillanceRuleService(List.of());
        service.put(TENANT, new SurveillanceRuleSnapshot(
                "structured-surveillance", "person", 12, Instant.now(), null, "digest-12", "HTTP", "rules", "ACTIVE",
                List.of(definition), List.of(item)));

        SurveillanceRuleService.MatchResult result = service.match(TENANT,
                new SurveillanceRuleService.BatchMatchRequest("structured-surveillance", "person",
                        List.of(new SurveillanceRuleService.Event("event-1", null,
                                Map.of("idCardNo", "3501001234561234"))), List.of())).results().getFirst();

        assertTrue(result.matched());
        assertEquals("person.id_card.equal", result.matches().getFirst().ruleCode());
        assertEquals("person-item-001", result.matches().getFirst().controlItemId());
    }

    @Test
    void rejectsTwoActiveDefinitionsInOneChannelSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new SurveillanceRuleSnapshot(
                "structured-surveillance", "person", 1, Instant.now(), null, null, "HTTP", "rules", "ACTIVE",
                List.of(
                        new SurveillanceRuleSnapshot.RuleDefinition("person.id_card.equal", "身份证号", "PERSON", "AND", List.of("idCardNo"), 1, "ACTIVE", null, null, null, null, Map.of()),
                        new SurveillanceRuleSnapshot.RuleDefinition("person.name.equal", "姓名", "PERSON", "AND", List.of("name"), 1, "ACTIVE", null, null, null, null, Map.of())
                ), List.of()));
    }

    @Test
    void runtimeMatchingUsesOnlyControlItemsAndReturnsControlItemMetadata() {
        SurveillanceControlItemRepository repository = new SurveillanceControlItemRepository(null, null) {
            @Override
            public Map<String, List<ControlItemMatch>> findActiveMatches(String tenantId, String engineCode,
                                                                          String channelCode, Set<String> identifierValues) {
                assertEquals(Set.of("3501001234561234"), identifierValues);
                return Map.of("3501001234561234", List.of(new ControlItemMatch(
                        "person-item-001", "person.id_card.equal", "PERSON", 12, "3501001234561234",
                        Map.of("name", "张三"), "THIRD-001", "涉案重点人员")));
            }
        };
        SurveillanceRuleService service = new SurveillanceRuleService(List.of(), null, repository);

        SurveillanceRuleService.MatchResult result = service.match(TENANT,
                new SurveillanceRuleService.BatchMatchRequest("structured-surveillance", "person",
                        List.of(new SurveillanceRuleService.Event("event-1", null,
                                Map.of("idCardNo", "3501001234561234"))), List.of())).results().getFirst();

        assertTrue(result.matched());
        assertEquals("person-item-001", result.matches().getFirst().controlItemId());
        assertEquals(12, result.snapshotVersion());
    }
}
