package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly models the second hop: original source -> staging database -> this Hive target. */
public final class HiveDownstreamFixtures {
    private HiveDownstreamFixtures() {}
    public static Pipeline.Node rawTarget(Pipeline.Node node) {
        var config=new LinkedHashMap<String,Object>(node.config()==null?Map.of():node.config());
        config.put("targetTableId",node.id());
        config.put("accessChain",Map.of("rootSourceTableId","root-source","sourceTableId","staging-table",
                "targetTableId",node.id(),"targetCount",2,"targetDatasourceCount",2,"targetIndex",1));
        return new Pipeline.Node(node.id(),node.manifestKey(),node.label(),node.category(),node.x(),node.y(),config);
    }
}
