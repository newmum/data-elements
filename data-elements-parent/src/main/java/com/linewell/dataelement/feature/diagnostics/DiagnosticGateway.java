package com.linewell.dataelement.feature.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Message;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Minimal Chat Completions/SSE adapter. No tools, retries, redirects, raw upstream errors or reasoning output. */
@Component
public class DiagnosticGateway {
    private final DiagnosticProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;
    public DiagnosticGateway(DiagnosticProperties properties, ObjectMapper json) {
        this.properties = properties; this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    String stream(List<Message> messages, DiagnosticRun run, Consumer<String> delta) throws Exception {
        var request = HttpRequest.newBuilder(properties.endpoint()).timeout(Duration.ofSeconds(properties.deadlineSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey()).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("model", properties.getModel(), "messages", messages, "stream", true))))
                .build();
        run.check();
        var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()); run.pending(future);
        var response = future.get(); run.body(response.body()); run.check();
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException(switch (response.statusCode()) {
                case 401, 403 -> "模型网关认证或授权失败，请联系管理员检查平台专用Key";
                case 429 -> "模型网关繁忙，请稍后重试";
                default -> "模型网关调用失败（HTTP " + response.statusCode() + "），请联系管理员";
            });
            if (!response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream")) throw new IllegalStateException("模型网关未返回预期的流式响应");
            var reader = new InputStreamReader(body, StandardCharsets.UTF_8);
            StringBuilder answer = new StringBuilder(), line = new StringBuilder(), data = new StringBuilder();
            boolean completed = false;
            int next;
            while ((next = reader.read()) >= 0) {
                run.check();
                if (next == '\r') continue;
                if (next != '\n') {
                    if (line.length() >= 65536) throw new IllegalStateException("模型响应单条消息超过限制");
                    line.append((char) next); continue;
                }
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        String payload = data.toString(); data.setLength(0);
                        if ("[DONE]".equals(payload.trim())) { completed = true; break; }
                        JsonNode event = json.readTree(payload);
                        if (event.has("error")) throw new IllegalStateException("模型生成中断，请重试；已返回内容仅供参考");
                        JsonNode choice = event.path("choices").path(0);
                        if ("length".equals(choice.path("finish_reason").asText())) throw new IllegalStateException("模型回答达到长度上限，已返回内容不完整");
                        String text = choice.path("delta").path("content").asText("");
                        if (answer.length() + text.length() > 64000) throw new IllegalStateException("模型回答超过限制，已停止生成");
                        if (!text.isEmpty()) { answer.append(text); delta.accept(text); }
                    }
                } else if (line.toString().startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                    if (data.length() > 65536) throw new IllegalStateException("模型响应消息超过限制");
                }
                line.setLength(0);
            }
            if (!completed || answer.isEmpty()) throw new IllegalStateException("模型流未正常完成，请重试；已返回内容仅供参考");
            return answer.toString();
        }
    }
}
