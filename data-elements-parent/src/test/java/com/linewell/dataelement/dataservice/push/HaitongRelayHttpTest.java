package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.common.BizException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HaitongRelayHttpTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registrationUsesDedicatedHeaderAndCompleteSnapshotOverBlockingHttp() throws Exception {
        try (var server = new Reply(201, "{\"datasourceId\":\"source-1\"}")) {
            var properties = properties(server);
            new HaitongStandaloneRegistryClient(properties, json).synchronize("source-1",
                    Map.of("datasourceId", "source-1", "tables", java.util.List.of()));
            String request = server.request.get(5, TimeUnit.SECONDS);
            assertThat(request).startsWith("POST /api/v1/data-push HTTP/1.1")
                    .contains("X-Standalone-Registry-Key: test-sync-key", "Idempotency-Key: registry-sync-source-1-")
                    .doesNotContain("X-Relay-Registry-Key:");
            assertThat(json.readTree(request.substring(request.indexOf("\r\n\r\n") + 4)).path("deliveryType").asText())
                    .isEqualTo("REGISTRY_SYNC");
        }
    }

    @Test
    void credentialIssuePreservesOneTimeRevealAndRotateBody() throws Exception {
        try (var server = new Reply(200, "{\"data\":{\"clientId\":\"push-source-1\",\"revealed\":true,\"apiKey\":\"test-business-key\"}}")) {
            var issued = new HaitongPushCredentialClient(properties(server), json).issue("push-source-1", true);
            assertThat(issued.apiKey()).isEqualTo("test-business-key");
            assertThat(issued.revealed()).isTrue();
            String request = server.request.get(5, TimeUnit.SECONDS);
            assertThat(request).startsWith("POST /internal/push-credentials/issue HTTP/1.1")
                    .contains("X-Relay-Registry-Key: test-issuer-key").doesNotContain("X-Standalone-Registry-Key:");
            assertThat(json.readTree(request.substring(request.indexOf("\r\n\r\n") + 4)).path("rotate").asBoolean()).isTrue();
        }
        try (var server = new Reply(200, "{\"data\":{\"clientId\":\"push-source-1\",\"revealed\":false}}")) {
            var issued = new HaitongPushCredentialClient(properties(server), json).issue("push-source-1", false);
            assertThat(issued.revealed()).isFalse();
            assertThat(issued.apiKey()).isEmpty();
        }
    }

    @Test
    void rejectsRedirectWithoutForwardingKeysAndReportsStageWithoutErrorBody() throws Exception {
        try (var server = new Reply(302, "do-not-expose-response", "Location: http://127.0.0.1:1/steal\r\n")) {
            assertThatThrownBy(() -> new HaitongStandaloneRegistryClient(properties(server), json)
                    .synchronize("source-1", Map.of())).isInstanceOf(BizException.class)
                    .hasMessageContaining("HTTP 302").hasMessageNotContaining("do-not-expose-response");
        }
        try (var server = new Reply(401, "do-not-expose-response")) {
            assertThatThrownBy(() -> new HaitongPushCredentialClient(properties(server), json).issue("push-source-1", false))
                    .isInstanceOf(BizException.class).hasMessageContaining("HTTP 401")
                    .hasMessageNotContaining("test-issuer-key").hasMessageNotContaining("do-not-expose-response");
        }
    }

    @Test
    void boundsResponseAndRejectsWrongReturnedSourceOrCaller() throws Exception {
        try (var server = new Reply(200, " ".repeat(1024 * 1024 + 1))) {
            assertThatThrownBy(() -> HaitongRelayHttp.post(server.url(), Map.of(), new byte[0], 2))
                    .isInstanceOf(java.io.IOException.class).hasMessageContaining("size limit");
        }
        try (var server = new Reply(201, "{\"datasourceId\":\"other-source\"}")) {
            assertThatThrownBy(() -> new HaitongStandaloneRegistryClient(properties(server), json)
                    .synchronize("source-1", Map.of())).isInstanceOf(BizException.class).hasMessageContaining("数据源不匹配");
        }
        try (var server = new Reply(200, "{\"data\":{\"clientId\":\"push-other\",\"revealed\":false}}")) {
            assertThatThrownBy(() -> new HaitongPushCredentialClient(properties(server), json).issue("push-source-1", false))
                    .isInstanceOf(BizException.class).hasMessageContaining("调用方不匹配");
        }
    }

    private DataPushRegistryProperties properties(Reply server) {
        var properties = new DataPushRegistryProperties();
        properties.setRelayBaseUrl(server.url());
        properties.setStandaloneSyncKey("test-sync-key");
        properties.setSharedKey("test-issuer-key");
        properties.setRelayTimeoutSeconds(3);
        return properties;
    }

    /** A plain socket fixture also works on hosts where JDK HttpServer's Selector fails. */
    private static final class Reply implements AutoCloseable {
        private final ServerSocket server;
        final CompletableFuture<String> request = new CompletableFuture<>();
        Reply(int status, String body) throws Exception { this(status, body, ""); }
        Reply(int status, String body, String headers) throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(5000);
            Thread.ofPlatform().daemon().start(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = socket.getInputStream();
                    var bytes = new java.io.ByteArrayOutputStream();
                    int tail = 0;
                    while (tail != 0x0d0a0d0a) {
                        int value = input.read();
                        if (value < 0 || bytes.size() > 65536) throw new java.io.IOException("Invalid request headers");
                        bytes.write(value); tail = (tail << 8) | value;
                    }
                    String head = bytes.toString(StandardCharsets.UTF_8);
                    int length = head.lines().filter(line -> line.toLowerCase().startsWith("content-length:"))
                            .map(line -> Integer.parseInt(line.substring(line.indexOf(':') + 1).trim())).findFirst().orElse(0);
                    bytes.write(input.readNBytes(length));
                    request.complete(bytes.toString(StandardCharsets.UTF_8));
                    byte[] content = body.getBytes(StandardCharsets.UTF_8);
                    var output = socket.getOutputStream();
                    output.write(("HTTP/1.1 " + status + " Test\r\nContent-Type: application/json\r\nContent-Length: "
                            + content.length + "\r\nConnection: close\r\n" + headers + "\r\n").getBytes(StandardCharsets.UTF_8));
                    output.write(content); output.flush();
                } catch (Exception error) { request.completeExceptionally(error); }
            });
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); }
    }
}
