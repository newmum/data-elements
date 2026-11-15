package com.linewell.dataelement.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class TablePreviewOrderMagicTest {
    private static final String PREVIEW = "function/01.通用函数/75.草稿排序与分页.ms";

    @Test
    void ordersTheWholeDraftBeforeTwentyAndHundredPagesWithNoObjectLookup() throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 150; i++) {
            rows.add(Map.of("id", "draft-" + i, "value", 149 - i));
        }
        var small = order(Map.of("items", rows, "kind", "number", "page", 1, "size", 20));
        var large = order(Map.of("items", rows, "kind", "number", "page", 1, "size", 100));
        assertThat(small.get("total")).isEqualTo(150);
        var twenty = (List<?>) small.get("keys");
        var hundred = (List<?>) large.get("keys");
        assertThat(twenty).hasSize(20).isEqualTo(hundred.subList(0, 20));
        assertThat(twenty.getFirst()).isEqualTo("draft-149");
        assertThat(new HashSet<>(rows.stream().map(row -> row.get("id")).toList()).containsAll(hundred)).isTrue();
        assertThat(order(Map.of("items", rows, "kind", "number", "page", 20, "size", 20)).get("keys"))
                .isEqualTo(List.of());
        assertThat((List<?>) order(Map.of("items", rows, "kind", "number", "wholeCollection", true)).get("keys"))
                .hasSize(150);
    }

    @Test
    void rejectsInvalidValuesDuplicateKeysAndOversizedPayloadsAndKeepsNullTimesLast() throws Exception {
        var rows = List.of(Map.of("id", "a"), Map.of("id", "b", "value", "2026-10-01T00:00:00Z"),
                Map.of("id", "c", "value", "2026-10-02T00:00:00Z"));
        assertThat(order(Map.of("items", rows, "kind", "time", "direction", "desc")).get("keys"))
                .isEqualTo(List.of("c", "b", "a"));
        assertThatThrownBy(() -> order(Map.of("items", Collections.nCopies(1001, rows.getFirst()))))
                .hasStackTraceContaining("1000");
        assertThatThrownBy(() -> order(Map.of("items", List.of(rows.getFirst(), rows.getFirst()))))
                .hasStackTraceContaining("重复");
        assertThatThrownBy(() -> order(Map.of("items", List.of(Map.of("id", "a", "value", Map.of("other", "x"))))))
                .hasStackTraceContaining("排序值");
        assertThatThrownBy(() -> order(Map.of("items", rows, "kind", "time", "direction", "SQL")))
                .hasStackTraceContaining("排序方向");
    }

    private static Map<?, ?> order(Map<String, Object> body) throws Exception {
        String source = CanonicalMagicSources.byPath(PREVIEW, "preview_draft_order_v1");
        source = source.replaceAll("(?m)^import (?:'@/common/[^']+' as \\w+|tableSort)\\s*\\R", "");
        source = source.replace("return withHttp(() => {", "var preview = () => {")
                .replaceFirst("(?m)^}\\)\\s*$", "}\nreturn preview()");
        MagicScriptLambdaFunction readBody = (vars, arguments) ->
                new ObjectMapper().convertValue(arguments[0], TableSortMagicModule.PreviewRequest.class);
        var context = new MagicScriptContext(Map.of("input", body, "readBody", readBody,
                "tableSort", new TableSortMagicModule()));
        var response = (ResponseEntity<?>) MagicScript.create(source, null).execute(context);
        return (Map<?, ?>) ((CommonResponse<?>) response.getBody()).getData();
    }
}
