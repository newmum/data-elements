package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleProvider;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
public class HttpSurveillanceRuleProvider implements SurveillanceRuleProvider {
    private final RestClient client;
    private final ObjectMapper mapper;

    public HttpSurveillanceRuleProvider(RestClient.Builder builder, ObjectMapper mapper) {
        this.client = builder.build();
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "API";
    }

    @Override
    public SurveillanceRuleSnapshot load(String engineCode, String channelCode, String sourceRef) {
        try {
            URI uri = URI.create(sourceRef);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("第三方规则接口只允许 HTTP/HTTPS");
            }
            String body = client.get().uri(sourceRef).retrieve().body(String.class);
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            SurveillanceRuleSnapshot snapshot = mapper.treeToValue(data, SurveillanceRuleSnapshot.class);
            if (!engineCode.equals(snapshot.engineCode())
                    || (!channelCode.equals(snapshot.channelCode()) && !"all".equalsIgnoreCase(snapshot.channelCode()))) {
                throw new IllegalArgumentException("第三方规则快照的引擎或通道不匹配");
            }
            return snapshot;
        } catch (Exception exception) {
            throw new IllegalStateException("读取第三方布控信息接口失败: " + exception.getMessage(), exception);
        }
    }
}
