package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** The retired users/recovery model is replaced by credential-free central personnel. */
class IdaasRecoveryPayloadTest {
    @TestFactory
    Stream<DynamicTest> centralPersonnelRejectsCredentialAndAccountCreationFieldsBeforeWrites() {
        return List.of("password", "initialPassword", "existingAccount", "linkExisting").stream()
                .flatMap(field -> Stream.of(false, true).map(inRecord -> DynamicTest.dynamicTest(
                        (inRecord ? "record." : "payload.") + field,
                        () -> {
                            Map<String, Object> record = new LinkedHashMap<>(Map.of("name", "Example person"));
                            Map<String, Object> payload = new LinkedHashMap<>(Map.of("record", record, "creating", true));
                            (inRecord ? record : payload).put(field, field.endsWith("Password") || field.equals("password")
                                    ? "test-only-secret" : true);
                            DbStub db = new DbStub();
                            String source = CanonicalMagicSources.byId("6b934eef4c6756029a274852f9b15424")
                                    .replaceAll("(?m)^import db;\\r?\\n", "")
                                    .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
                            String prefix = "var directoryScope=(permission,domain)=>({ctx:{userId:'operator'},all:true,orgIds:[]});\n";
                            Object result = MagicScript.create(prefix + source, null).execute(new MagicScriptContext(Map.of(
                                    "db", db, "entity", "subjects", "operation", "save", "payload", payload)));
                            assertInstanceOf(ExitValue.class, result);
                            assertEquals(0, db.reads);
                            assertEquals(0, db.writes);
                        })));
    }

    public static class DbStub {
        int reads;
        int writes;
        public DbStub normal() { return this; }
        public Object selectOne(String sql) { reads++; throw new AssertionError("Credential input reached a database read"); }
        public List<?> select(String sql) { reads++; throw new AssertionError("Credential input reached a database read"); }
        public int update(String sql) { writes++; throw new AssertionError("Credential input reached a database write"); }
    }
}
