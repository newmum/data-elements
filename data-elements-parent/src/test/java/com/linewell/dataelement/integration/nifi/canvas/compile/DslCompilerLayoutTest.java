package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DslCompilerLayoutTest {

    @Test
    void placesConnectedNodesInSuccessiveColumnsAndBranchesInSeparateRows() {
        Pipeline.Dsl dsl = new Pipeline.Dsl(1, List.of(
                node("source", 0, 0),
                node("upper", 460, 0),
                node("lower", 460, 460),
                node("sink", 920, 180)), List.of(
                edge("source-upper", "source", "upper"),
                edge("source-lower", "source", "lower"),
                edge("upper-sink", "upper", "sink"),
                edge("lower-sink", "lower", "sink")));

        Map<String, DslCompiler.NodeLayout> layout = DslCompiler.assignNodeLayouts(dsl);

        assertThat(layout.get("source")).isEqualTo(new DslCompiler.NodeLayout(0, 0));
        assertThat(layout.get("upper")).isEqualTo(new DslCompiler.NodeLayout(1, 0));
        assertThat(layout.get("lower")).isEqualTo(new DslCompiler.NodeLayout(1, 1));
        // The third topology level folds below the first pair instead of
        // opening a third, very distant horizontal column.
        assertThat(layout.get("sink")).isEqualTo(new DslCompiler.NodeLayout(1, 2));
    }

    @Test
    void foldsALinearMaterializationFlowIntoTwoNativeCanvasColumns() {
        Pipeline.Dsl dsl = new Pipeline.Dsl(1, List.of(
                node("one", 0, 0), node("two", 460, 0), node("three", 920, 0),
                node("four", 1_380, 0), node("five", 1_840, 0), node("six", 2_300, 0)), List.of(
                edge("one-two", "one", "two"), edge("two-three", "two", "three"),
                edge("three-four", "three", "four"), edge("four-five", "four", "five"),
                edge("five-six", "five", "six")));

        Map<String, DslCompiler.NodeLayout> layout = DslCompiler.assignNodeLayouts(dsl);

        assertThat(layout.get("one")).isEqualTo(new DslCompiler.NodeLayout(0, 0));
        assertThat(layout.get("two")).isEqualTo(new DslCompiler.NodeLayout(1, 0));
        assertThat(layout.get("three")).isEqualTo(new DslCompiler.NodeLayout(1, 1));
        assertThat(layout.get("four")).isEqualTo(new DslCompiler.NodeLayout(0, 1));
        assertThat(layout.get("five")).isEqualTo(new DslCompiler.NodeLayout(0, 2));
        assertThat(layout.get("six")).isEqualTo(new DslCompiler.NodeLayout(1, 2));
        assertThat(layout.values().stream().map(DslCompiler.NodeLayout::column).distinct()).hasSize(2);
    }

    @Test
    void producesDistinctStableSlotsForAStoredCycle() {
        Pipeline.Dsl dsl = new Pipeline.Dsl(1, List.of(
                node("first", 0, 0),
                node("second", 460, 0),
                node("third", 920, 0)), List.of(
                edge("first-second", "first", "second"),
                edge("second-third", "second", "third"),
                edge("third-first", "third", "first")));

        Map<String, DslCompiler.NodeLayout> layout = DslCompiler.assignNodeLayouts(dsl);

        assertThat(layout).hasSize(3);
        assertThat(layout.values()).doesNotHaveDuplicates();
        assertThat(layout.get("first").column()).isEqualTo(0);
    }

    private Pipeline.Node node(String id, double x, double y) {
        return new Pipeline.Node(id, "test.manifest", id, "transform", x, y, Map.of());
    }

    private Pipeline.Edge edge(String id, String source, String target) {
        return new Pipeline.Edge(id, source, target, null);
    }
}
