import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

public class ServiceHttpDebugMagicTest {
    private static final Path FUNCTIONS = Path.of("magic", "function", "01.通用函数");

    public static class Tenant {
        public String id() { return "validation"; }
    }

    public record Environment(String base) {
        public String getProperty(String key, String fallback) {
            return switch (key) {
                case "flowserve.http.platform-base-url" -> base;
                case "flowserve.http.allowed-targets.validation" -> base + "/validation/";
                default -> fallback;
            };
        }
    }

    private static String script(Path path) throws Exception {
        String file = Files.readString(path);
        return file.substring(file.indexOf("================================") + 32)
                .replaceAll("(?m)^import (tenantRuntime|org.springframework.core.env.Environment as environment)\\r?$", "");
    }

    private static Map<?, ?> execute(String script, Map<String, Object> variables) {
        return (Map<?, ?>) MagicScript.create(script, null).execute(new MagicScriptContext(variables));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String requestScript = script(FUNCTIONS.resolve("28.执行服务HTTP请求.ms"));
        String previewScript = script(FUNCTIONS.resolve("29.脱敏服务HTTP结果.ms"));
        MagicScript.create(requestScript, null).compile();
        MagicScript.create(previewScript, null).compile();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/validation/echo", exchange -> {
            calls.incrementAndGet();
            check(exchange.getRequestMethod().equals("PATCH"), "method is preserved");
            check(exchange.getRequestURI().getRawQuery().equals("item=001&item=002"), "repeated query parameters");
            check("Bearer validation-secret".equals(exchange.getRequestHeaders().getFirst("Authorization")), "auth header");
            check(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).equals("{\"page\":1}"), "body");
            byte[] response = "{\"code\":422,\"message\":\"validation-secret\",\"data\":{\"access_token\":\"issued-secret\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Set-Cookie", "secret=session");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/validation/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/validation/echo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/validation/large", exchange -> {
            byte[] response = new byte[65537];
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var env = new Environment("http://127.0.0.1:" + server.getAddress().getPort());
            var request = Map.<String, Object>of("address", "/validation/echo", "method", "PATCH",
                    "query", Map.of("item", java.util.List.of("001", "002")),
                    "headers", Map.of("Authorization", "Bearer validation-secret"), "payload", "{\"page\":1}");
            var result = execute(requestScript, Map.of("environment", env, "tenantRuntime", new Tenant(), "options", request));
            check(Boolean.FALSE.equals(result.get("ok")) && "200".equals(String.valueOf(result.get("status")))
                    && "422".equals(String.valueOf(result.get("businessCode"))), "HTTP success and business failure are separate");
            var preview = execute(previewScript, Map.of("options", Map.of("request", request, "response", result)));
            check(!preview.toString().contains("validation-secret") && !preview.toString().contains("issued-secret")
                    && !preview.toString().contains("Set-Cookie"), "preview masks credentials and response cookies");
            var redirect = execute(requestScript, Map.of("environment", env, "tenantRuntime", new Tenant(),
                    "options", Map.of("address", "/validation/redirect", "method", "GET")));
            check("302".equals(String.valueOf(redirect.get("status"))) && calls.get() == 1, "redirect is not followed");
            var large = execute(requestScript, Map.of("environment", env, "tenantRuntime", new Tenant(),
                    "options", Map.of("address", "/validation/large", "method", "GET")));
            check(Boolean.TRUE.equals(large.get("truncated")), "bounded response");
            var largePreview = execute(previewScript, Map.of("options", Map.of("response", large)));
            check(String.valueOf(largePreview.get("body")).contains("已省略"), "truncated body cannot leak secrets");
            System.out.println("PASS: service HTTP Magic transport and preview");
        } finally {
            server.stop(0);
        }
    }
}
