package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NativeCanvasLayoutTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void laysOutExpandedProcessorsInFlowOrderWithTwoCardsPerRow() {
        var processors = json.createArrayNode();
        var links = new ArrayList<NativeCanvasLayout.Link>();
        for (int i = 11; i >= 0; i--) {
            processors.addObject().put("id", "p" + i).putObject("component")
                    .put("name", "p" + i).putObject("position").put("x", i * 600).put("y", 0);
            if (i > 0) links.add(new NativeCanvasLayout.Link("e" + i, "p" + (i - 1), "p" + i, List.of("success")));
        }
        links.add(new NativeCanvasLayout.Link("failure", "p3", "p3", List.of("failure")));
        var positions = NativeCanvasLayout.positions(processors, links);
        assertThat(new ArrayList<>(positions.keySet())).containsExactly(
                "p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11");
        assertThat(positions.values().stream().map(NativeCanvasLayout.Point::x).distinct()).hasSize(2);
        assertThat(positions.values().stream().map(NativeCanvasLayout.Point::y).distinct()).hasSize(6);
        for (var link : links) {
            var route = NativeCanvasLayout.route(link, positions, 0);
            if (!link.source().equals(link.target())) assertThat(route.bends()).isEmpty();
            else assertThat(route.bends()).allMatch(p -> p.x() < positions.get("p3").x());
        }
    }

    @Test
    void routesBranchEdgesOutsideCardsAndKeepsCyclesFinite() {
        var processors = json.createArrayNode();
        for (String id : List.of("a", "b", "c", "d")) processors.addObject().put("id", id);
        var links = List.of(link("a", "b"), link("a", "c"), link("b", "d"), link("c", "d"));
        var positions = NativeCanvasLayout.positions(processors, links);
        var first = NativeCanvasLayout.route(links.get(1), positions, 0);
        var second = NativeCanvasLayout.route(links.get(2), positions, 1);
        assertThat(first.bends()).hasSize(4);
        assertThat(first.bends().get(1).x()).isGreaterThan(NativeCanvasLayout.LEFT
                + NativeCanvasLayout.COLUMN_GAP + NativeCanvasLayout.CARD_WIDTH);
        assertThat(first.bends().get(1).x()).isNotEqualTo(second.bends().get(1).x());
        var cycle = new ArrayList<>(links);
        cycle.add(link("d", "a"));
        assertThat(NativeCanvasLayout.positions(processors, cycle).values()).hasSize(4).doesNotHaveDuplicates();
    }

    private NativeCanvasLayout.Link link(String from, String to) {
        return new NativeCanvasLayout.Link(from + to, from, to, List.of("success"));
    }
}
