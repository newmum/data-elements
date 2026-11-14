import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

public class ServiceFlowHttpMagicTest {
    public static class Tenant {
        public String id() { return "validation"; }
    }

    public static class Environment {
        public String getProperty(String key, String fallback) { return fallback; }
    }

    public static class Scope {
        private final boolean allowBoth;
        Scope(boolean allowBoth) { this.allowBoth = allowBoth; }
        public List<Map<String,Object>> visibleDataSources(List<Map<String,Object>> rows) {
            return allowBoth ? rows : rows.subList(0, 1);
        }
    }

    public static class Database {
        int reads;
        public Database table(String name) { check(name.equals("api_info_t"), "one authoritative service table"); return this; }
        public Database where() { return this; }
        public Database eq(String field, Object value) { return this; }
        public Database in(String field, List<?> ids) {
            check(field.equals("tid") && ids.equals(List.of("validation-login", "validation-business")), "batch by reference ids");
            return this;
        }
        public List<Map<String,Object>> select() {
            reads++;
            return List.of(Map.of("tid", "validation-login", "address", "/portal/login", "method", "POST"),
                    Map.of("tid", "validation-business", "address", "/sym/tenant/current", "method", "GET"));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String,Object> node(String id, String type, Map<String,Object> data) {
        return Map.of("id", id, "type", type, "name", id, "data", data);
    }

    private static Map<String,Object> fixture() {
        return Map.of("nodes", List.of(node("trigger", "trigger", Map.of("method", "POST")),
                node("login", "http-call", Map.of("serviceId", "validation-login", "outputVar", "loginResult",
                        "body", "{\"password\":\"{{input.password}}\"}")),
                node("business", "http-call", Map.of("serviceId", "validation-business", "outputVar", "businessResult",
                        "headers", List.of(Map.of("name", "token", "value", "{{loginResult.body.data.token}}")))),
                node("response", "response", Map.of("mode", "var", "source", "businessResult.body.data.name"))),
                "edges", List.of(Map.of("source", "trigger", "target", "login"), Map.of("source", "login", "target", "business"),
                        Map.of("source", "business", "target", "response")));
    }

    private static Object run(String script, Database db, boolean authenticated, boolean allowBoth, List<String> calls) {
        Function<Object[], Map<String,Object>> transport = args -> {
            var options = (Map<?,?>) args[0];
            String address = String.valueOf(options.get("address"));
            calls.add(address);
            if (address.equals("/portal/login")) {
                check(((Map<?,?>) options.get("payload")).get("password").equals("validation-input"), "bound login input");
                return authenticated
                        ? Map.of("ok", true, "status", 200, "body", Map.of("code", 0, "data", Map.of("token", "validation-issued")))
                        : Map.of("ok", false, "status", 200, "businessCode", 401, "body", Map.of("code", 401));
            }
            check("validation-issued".equals(((Map<?,?>) options.get("headers")).get("token")), "issued token reaches next header");
            return Map.of("ok", true, "status", 200, "body", Map.of("code", 0, "data", Map.of("name", "validation tenant")));
        };
        Function<Object[], Object> preview = args -> ((Map<?,?>) args[0]).get("response");
        return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                "environment", new Environment(), "tenantRuntime", new Tenant(), "dataScope", new Scope(allowBoth), "db", db,
                "serviceHttp", transport, "preview", preview,
                "options", Map.of("flow", fixture(), "input", Map.of("password", "validation-input")))));
    }

    public static void main(String[] args) throws Exception {
        String file = Files.readString(Path.of("magic", "function", "01.通用函数", "28.执行服务编排HTTP链路.ms"));
        String script = file.substring(file.indexOf("================================") + 32)
                .replaceAll("(?m)^import (?:'[^']+' as \\w+|org.springframework.core.env.Environment as environment|dataScope|tenantRuntime)\\r?$", "");
        var calls = new ArrayList<String>();
        var db = new Database();
        var success = (Map<?, ?>) run(script, db, true, true, calls);
        check(Boolean.TRUE.equals(success.get("success")) && "validation tenant".equals(success.get("response")), "flow returns selected response");
        check(calls.equals(List.of("/portal/login", "/sym/tenant/current")) && db.reads == 1, "one batch read, two ordered calls");
        check(!success.toString().contains("validation-issued"), "issued token absent from response and trace");
        calls.clear();
        var failed = (Map<?, ?>) run(script, new Database(), false, true, calls);
        check(Boolean.FALSE.equals(failed.get("success")) && calls.equals(List.of("/portal/login")), "auth failure skips downstream");
        check(failed.get("trace").toString().contains("skipped"), "trace marks skipped nodes");
        calls.clear();
        var denied = run(script, new Database(), true, false, calls);
        check(denied instanceof ExitValue rejection && rejection.getValues()[0].toString().equals("404")
                && calls.isEmpty(), "one unauthorized reference prevents all HTTP calls");
        System.out.println("PASS: service flow Magic binding, batch scope and failure handling");
    }
}
