package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the real users.ms payload projection and Hutool restore expressions, without a DB. */
class IdaasRecoveryPayloadTest {

    @Test
    void credentialsAndUnknownNestedAppointmentFieldsNeverEnterPersistedPayload() throws Exception {
        Map<String, Object> input = validInput();
        input.put("password", "outer-secret");
        Map<String, Object> record = record(input);
        record.put("initialPassword", "initial-secret");
        record.put("password", "record-secret");
        record.put("accessToken", "token-secret");
        record.put("unknown", Map.of("credentials", "unknown-secret"));
        record.put("appointments", List.of(Map.of(
                "orgId", "org-1", "post", "staff", "primary", true,
                "password", "nested-secret", "metadata", Map.of("token", "deep-secret")
        )));

        Map<?, ?> result = assertInstanceOf(Map.class, sanitize(input));
        Map<?, ?> safeInput = assertInstanceOf(Map.class, result.get("safeInput"));
        Map<?, ?> safeRecord = assertInstanceOf(Map.class, safeInput.get("record"));
        List<?> appointments = assertInstanceOf(List.class, safeRecord.get("appointments"));
        assertEquals(Map.of("orgId", "org-1", "post", "staff", "primary", true), appointments.getFirst());
        assertFalse(safeRecord.containsKey("initialPassword"));
        assertFalse(safeRecord.containsKey("password"));
        assertFalse(safeRecord.containsKey("accessToken"));
        assertFalse(safeRecord.containsKey("unknown"));
        assertFalse(JSONUtil.toJsonStr(safeInput).contains("secret"));
    }

    @Test
    void ignoredCredentialsDoNotChangeRecoverableRequestIdentity() throws Exception {
        Map<String, Object> firstInput = validInput();
        record(firstInput).put("initialPassword", "first-secret");
        Map<String, Object> nextInput = validInput();
        record(nextInput).put("initialPassword", "different-secret");
        Map<?, ?> first = assertInstanceOf(Map.class, sanitize(firstInput));
        Map<?, ?> second = assertInstanceOf(Map.class, sanitize(nextInput));
        assertEquals(first.get("requestHash"), second.get("requestHash"));
    }

    @Test
    void actualHutoolRestoreRetainsNestedMapBooleanNumberAndNullSemantics() throws Exception {
        Map<String, Object> input = validInput();
        record(input).put("email", null);
        Map<?, ?> sanitized = assertInstanceOf(Map.class, sanitize(input));
        Map<?, ?> safeInput = assertInstanceOf(Map.class, sanitized.get("safeInput"));
        String serialized = JSONUtil.toJsonStr(safeInput);
        String source = usersSource();
        String restore = "safeInput=JSONUtil.toBean(stored.safePayload,LinkedHashMap);";
        assertTrue(source.contains(restore), "Keep this regression test attached to the actual restore expression");
        Object value = execute(imports(source) + "\nvar safeInput={};\n" + restore + "\n" + """
                return {input:safeInput,
                    containsName:safeInput.record.containsKey('name'),
                    primary:safeInput.record.appointments[0].primary==true,
                    version:safeInput.record.version==1,
                    emailNull:safeInput.record.email==null,
                    creating:safeInput.creating==true};
                """, Map.of("stored", Map.of("safePayload", serialized)));
        Map<?, ?> recovered = assertInstanceOf(Map.class, value);
        assertInstanceOf(LinkedHashMap.class, recovered.get("input"));
        for (String key : List.of("containsName", "primary", "version", "emailNull", "creating")) {
            assertEquals(Boolean.TRUE, recovered.get(key), key);
        }
        Map<?, ?> restoredInput = (Map<?, ?>) recovered.get("input");
        Map<?, ?> restoredRecord = assertInstanceOf(Map.class, restoredInput.get("record"));
        assertEquals("sample.user", restoredRecord.get("account"));
        assertFalse(JSONUtil.toJsonStr(restoredInput).contains("password"));
    }

    @TestFactory
    Stream<DynamicTest> malformedKnownFieldsCannotSmuggleNestedCredentials() {
        List<String> scalarFields = List.of("id", "name", "account", "status", "locked", "existingAccount", "version");
        return scalarFields.stream().map(field -> DynamicTest.dynamicTest("record." + field, () -> {
            Map<String, Object> input = validInput();
            record(input).put(field, Map.of("password", "secret"));
            assertInstanceOf(ExitValue.class, sanitize(input));
        }));
    }

    @TestFactory
    Stream<DynamicTest> malformedOperationEnvelopeCannotEnterRecoveryPayload() {
        return List.of("id", "version", "creating").stream().map(field -> DynamicTest.dynamicTest(field, () -> {
            Map<String, Object> input = validInput();
            input.put(field, Map.of("token", "secret"));
            assertInstanceOf(ExitValue.class, sanitize(input));
        }));
    }

    @Test
    void malformedAppointmentKnownFieldIsRejectedBeforePersistence() throws Exception {
        Map<String, Object> input = validInput();
        record(input).put("appointments", List.of(Map.of(
                "orgId", "org-1", "post", Map.of("password", "secret"), "primary", true
        )));
        assertInstanceOf(ExitValue.class, sanitize(input));
    }

    private Object sanitize(Map<String, Object> input) throws Exception {
        String source = usersSource();
        int start = source.indexOf("var safeRecord={};");
        int end = source.indexOf("var stored=identityRuntime.control", start);
        assertTrue(start >= 0 && end > start, "Actual payload projection must be present");
        String actualProjection = source.substring(start, end);
        return execute(imports(source) + "\n" + actualProjection
                + "\nreturn {safeInput:safeInput,requestHash:requestHash};", Map.of(
                "input", input, "requestId", "request-12345678"
        ));
    }

    private static Object execute(String source, Map<String, Object> variables) {
        MagicScriptContext context = new MagicScriptContext();
        variables.forEach(context::set);
        return MagicScript.create(source, null).execute(context);
    }

    private String usersSource() throws Exception {
        return Files.readString(Path.of(System.getProperty(
                "idaas.magic.sourceRoot", "db/migrations/resources/idaas-phase1-20260927"
        ), "functions/users.ms"), StandardCharsets.UTF_8);
    }

    private String imports(String source) {
        return source.lines().filter(line -> line.startsWith("import java.util.")
                || line.equals("import cn.hutool.json.JSONUtil;")
                || line.equals("import cn.hutool.crypto.digest.DigestUtil;"))
                .reduce("", (left, right) -> left + right + "\n");
    }

    private Map<String, Object> validInput() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("account", "sample.user");
        record.put("name", "Sample User");
        record.put("domain", "workforce");
        record.put("status", "enabled");
        record.put("locked", false);
        record.put("existingAccount", false);
        record.put("version", 1);
        record.put("orgId", "org-1");
        record.put("appointments", List.of(Map.of("orgId", "org-1", "post", "staff", "primary", true)));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("record", record);
        input.put("creating", true);
        return input;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> record(Map<String, Object> input) {
        return (Map<String, Object>) input.get("record");
    }
}
