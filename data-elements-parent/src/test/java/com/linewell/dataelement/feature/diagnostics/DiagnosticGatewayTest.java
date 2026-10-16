package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Message;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class DiagnosticGatewayTest {
    private HttpServer server;
    private ExecutorService requests;
    private DiagnosticProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requests = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }); server.setExecutor(requests);
        properties = new DiagnosticProperties(); properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setApiKey("fake-test-key"); properties.setModel("test-alias"); properties.setEnabled(true);
    }
    @AfterEach void close() { server.stop(0); requests.shutdownNow(); }
    @Test void realHttpStreamsUtf8WithoutUnsupportedOptionsOrTools() throws Exception {
        var received = new CompletableFuture<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            received.complete(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            byte[] bytes = ("data: {\"choices\":[{\"delta\":{\"content\":\"故障建议\",\"reasoning_content\":\"private-thoughts\"}}]}\r\n\r\ndata: [DONE]\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            for (byte value : bytes) { exchange.getResponseBody().write(value); exchange.getResponseBody().flush(); }
            exchange.close();
        }); server.start();
        StringBuilder output = new StringBuilder();
        assertThat(new DiagnosticGateway(properties, json).stream(List.of(new Message("user", "help")), new DiagnosticRun(), output::append)).isEqualTo("故障建议");
        assertThat(output.toString()).isEqualTo("故障建议");
        assertThat(received.get()).contains("test-alias").doesNotContain("tools", "response_format", "fake-test-key");
    }
    @Test void interruptedStreamNeverBecomesSuccess() {
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".getBytes(StandardCharsets.UTF_8)); exchange.close();
        }); server.start();
        assertThatThrownBy(() -> new DiagnosticGateway(properties, json).stream(List.of(new Message("user", "help")), new DiagnosticRun(), text -> {}))
                .hasMessageContaining("未正常完成");
    }
    @Test void upstreamAuthenticationResponseIsNotExposedOrRetried() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            count.incrementAndGet(); byte[] body = "private token response".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(401, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        assertThatThrownBy(() -> new DiagnosticGateway(properties, json).stream(List.of(new Message("user", "help")), new DiagnosticRun(), text -> {}))
                .hasMessageContaining("认证或授权失败").hasMessageNotContaining("private token response");
        assertThat(count.get()).isEqualTo(1);
    }
    @Test void cancellationClosesAnIdleUpstreamBody() throws Exception {
        CountDownLatch headers = new CountDownLatch(1), release = new CountDownLatch(1);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush(); headers.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } finally { exchange.close(); }
        }); server.start();
        DiagnosticRun run = new DiagnosticRun();
        Future<?> task = requests.submit(() -> {
            run.worker = Thread.currentThread();
            try { new DiagnosticGateway(properties, json).stream(List.of(new Message("user", "help")), run, text -> {}); }
            catch (Exception expected) { /* cancelled */ }
        });
        try { assertThat(headers.await(2, TimeUnit.SECONDS)).isTrue(); run.cancel(); task.get(2, TimeUnit.SECONDS); }
        finally { release.countDown(); }
        assertThat(run.stopped()).isTrue();
    }
}
