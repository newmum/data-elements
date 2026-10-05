package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DslCompilerOutletTest {
    @Test
    void acceptsOnlyUnambiguousLegacySuccessForDefaultOutlet() {
        var node = new DslCompiler.NodeCompilation();
        var outlet = new DslCompiler.Outlet("processor", "success");
        node.outlets.put("default", outlet);
        assertThat(DslCompiler.resolveOutlet(node, "default")).isSameAs(outlet);
        assertThat(DslCompiler.resolveOutlet(node, "success")).isSameAs(outlet);
        assertThat(DslCompiler.resolveOutlet(node, "typo")).isNull();
    }

    @Test
    void explicitNamedBranchAlwaysWins() {
        var node = new DslCompiler.NodeCompilation();
        node.outlets.put("default", new DslCompiler.Outlet("first", "success"));
        var branch = new DslCompiler.Outlet("branch", "matched");
        node.outlets.put("success", branch);
        assertThat(DslCompiler.resolveOutlet(node, "success")).isSameAs(branch);
    }

    @Test
    void neverGuessesWhenThereAreMultipleBranchesOrDifferentRelationship() {
        var node = new DslCompiler.NodeCompilation();
        node.outlets.put("default", new DslCompiler.Outlet("first", "success"));
        node.outlets.put("reject", new DslCompiler.Outlet("other", "failure"));
        assertThat(DslCompiler.resolveOutlet(node, "success")).isNull();
        node.outlets.remove("reject");
        node.outlets.put("default", new DslCompiler.Outlet("first", "unmatched"));
        assertThat(DslCompiler.resolveOutlet(node, "success")).isNull();
    }

    @Test
    void preservesOriginalDefaultSelectionAndEmptyOutputBehavior() {
        var node = new DslCompiler.NodeCompilation();
        assertThat(DslCompiler.resolveOutlet(node, null)).isNull();
        assertThat(DslCompiler.resolveOutlet(null, "default")).isNull();
        var first = new DslCompiler.Outlet("first", "matched");
        node.outlets.put("positive", first);
        node.outlets.put("negative", new DslCompiler.Outlet("second", "unmatched"));
        assertThat(DslCompiler.resolveOutlet(node, null)).isSameAs(first);
        assertThat(DslCompiler.resolveOutlet(node, "default")).isNull();
    }
}
