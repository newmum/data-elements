package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HiveSinkPublicationPolicyTest {
    private Pipeline.Node node(String id,String key){return new Pipeline.Node(id,key,id,key.startsWith("sink")?"sink":"transform",0,0,Map.of());}
    private Pipeline.Edge edge(String source,String target){return new Pipeline.Edge(source+"-"+target,source,target,"default");}
    private final Pipeline.Node source=node("source","source.mysql"),hive=node("hive","sink.hive"),jdbc=node("jdbc","sink.jdbc"),
            a=node("a","transform.field-enrichment"),b=node("b","transform.field-mapping");

    @Test void targetHiveBypassesBothMappingVariantsAndPreservesSavedCanvas() {
        var saved=new Pipeline.Dsl(1,List.of(source,a,b,hive),List.of(edge("source","a"),edge("a","b"),edge("b","hive")));
        var result=HiveSinkPublicationPolicy.forPublication(saved);
        assertThat(result.nodes()).containsExactly(source,hive);
        assertThat(result.edges()).singleElement().satisfies(e->{assertThat(e.source()).isEqualTo("source");assertThat(e.target()).isEqualTo("hive");});
        assertThat(saved.nodes()).hasSize(4);assertThat(saved.edges()).hasSize(3);
        assertThat(HiveSinkPublicationPolicy.forPublication(result)).isSameAs(result);
    }
    @Test void sharedJdbcBranchKeepsItsMappingChain() {
        var saved=new Pipeline.Dsl(1,List.of(source,a,b,hive,jdbc),List.of(edge("source","a"),edge("a","b"),edge("b","hive"),edge("b","jdbc")));
        var result=HiveSinkPublicationPolicy.forPublication(saved);
        assertThat(result.nodes()).containsExactlyElementsOf(saved.nodes());
        assertThat(result.edges()).contains(edge("b","jdbc"),edge("a","b"),edge("source","a"));
        assertThat(result.edges()).anyMatch(e->e.source().equals("source")&&e.target().equals("hive"));
        assertThat(result.edges()).noneMatch(e->e.source().equals("b")&&e.target().equals("hive"));
    }
    @Test void diamondDoesNotDuplicateRawRows() {
        var saved=new Pipeline.Dsl(1,List.of(source,a,b,hive),List.of(edge("source","a"),edge("source","b"),edge("a","hive"),edge("b","hive")));
        assertThat(HiveSinkPublicationPolicy.forPublication(saved).edges()).hasSize(1);
    }
    @Test void cyclesFailBeforePublishing() {
        var saved=new Pipeline.Dsl(1,List.of(a,b,hive),List.of(edge("a","b"),edge("b","a"),edge("b","hive")));
        assertThatThrownBy(()->HiveSinkPublicationPolicy.forPublication(saved)).hasMessageContaining("循环");
    }
    @Test void missingInputFailsBeforePublishing() {
        var saved=new Pipeline.Dsl(1,List.of(a,hive),List.of(edge("a","hive")));
        assertThatThrownBy(()->HiveSinkPublicationPolicy.forPublication(saved)).hasMessageContaining("缺少来源");
    }
    @Test void hiveSourceToJdbcKeepsGovernance() {
        var saved=new Pipeline.Dsl(1,List.of(node("source","source.hive"),a,jdbc),List.of(edge("source","a"),edge("a","jdbc")));
        assertThat(HiveSinkPublicationPolicy.forPublication(saved)).isSameAs(saved);
    }
    @Test void bypassedJavaMappingDoesNotRequireNarButSharedJdbcMappingDoes() {
        var mapping=new Pipeline.Node("a",a.manifestKey(),a.label(),a.category(),0,0,Map.of("mappings","{\"schemaVersion\":\"2.0\",\"rules\":[]}"));
        var raw=new Pipeline.Dsl(1,List.of(source,mapping,hive),List.of(edge("source","a"),edge("a","hive")));
        assertThat(DslHasher.usesJavaMapping(raw)).isFalse();
        var shared=new Pipeline.Dsl(1,List.of(source,mapping,hive,jdbc),List.of(edge("source","a"),edge("a","hive"),edge("a","jdbc")));
        assertThat(DslHasher.usesJavaMapping(shared)).isTrue();
        assertThat(DslHasher.deploymentHash(raw,DslHasher.CURRENT_COMPILER_REVISION)).hasSize(64);
    }
}
