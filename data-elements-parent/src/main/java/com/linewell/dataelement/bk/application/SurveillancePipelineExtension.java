package com.linewell.dataelement.bk.application;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineTemplateProvider;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupingPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** All canvas behavior specific to the optional surveillance engine. */
@Component
public class SurveillancePipelineExtension implements PipelineTemplateProvider, PipelineGroupingPolicy {
    @Override
    public boolean supports(String templateType) {
        return "structured-surveillance".equalsIgnoreCase(templateType);
    }

    @Override
    public Optional<Group> group(Pipeline pipeline) {
        return pipeline != null && pipeline.name() != null
                && pipeline.name().startsWith("结构化数据布控引擎流组")
                ? Optional.of(new Group("布控引擎组", "structured-surveillance")) : Optional.empty();
    }

    @Override
    public Pipeline template() {
        List<Pipeline.Node> nodes = new ArrayList<>();
        List<Pipeline.Edge> edges = new ArrayList<>();
        String[] channels = {"person", "mobile", "vehicle"};
        int index = 0;
        for (String channel : channels) {
            int x = index * 1280;
            String source = "surveillance-" + channel + "-source";
            String match = "surveillance-" + channel + "-match";
            String matched = "surveillance-" + channel + "-matched";
            nodes.add(new Pipeline.Node(source, "source.kafka", channel + " 输入 Kafka", "source", x, 80,
                    new LinkedHashMap<>(Map.of("groupId", "structured-surveillance-" + channel,
                            "schedulingPeriod", "0 sec",
                            "messageFormat", "JSON",
                            "schemaRequired", true,
                            "credentialRef", "credential://kafka/structured-surveillance"))));
            nodes.add(new Pipeline.Node(match, "transform.surveillance-match", channel + " 布控匹配", "transform", x + 420, 80,
                    new LinkedHashMap<>(Map.of("engineCode", "structured-surveillance", "channelCode", channel,
                            "matchUrl", "/api/v1/surveillance/match/batch",
                            "batchSize", 100, "requestTimeout", "30 sec"))));
            nodes.add(kafkaOutput(matched, channel + " 命中结果 Topic", x + 860, 0, channel + ".result"));
            edges.add(new Pipeline.Edge("edge-" + source + "-" + match, source, match, null));
            edges.add(new Pipeline.Edge("edge-" + match + "-" + matched, match, matched, "matched"));
            index++;
        }
        return new Pipeline(null, "结构化数据布控引擎", "Kafka 多通道布控匹配模板", null, null,
                new Pipeline.Dsl(1, nodes, edges), null, null, null, null, null, null, null);
    }

    private Pipeline.Node kafkaOutput(String id, String label, int x, int y, String topic) {
        return new Pipeline.Node(id, "sink.kafka", label, "sink", x, y,
                new LinkedHashMap<>(Map.of("topic", topic, "deliveryGuarantee", "EXACTLY_ONCE",
                        "credentialRef", "credential://kafka/structured-surveillance")));
    }

}
