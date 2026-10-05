package com.linewell.dataelement.platform.tenant.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IdentityProtectedValuesTest {
    private final IdentityTransportMagicModule module = new IdentityTransportMagicModule(
            Base64.getEncoder().encodeToString(new byte[32]), false);
    @Test void protectedFieldsBindCiphertextToTheObjectAndUseIndependentIv() throws Exception {
        String context = "workforce/SUBJECT/owned-id/clearance";
        String first = module.protectValue("{\"value\":\"private\"}", context);
        String second = module.protectValue("{\"value\":\"private\"}", context);
        assertNotEquals(first, second);
        assertFalse(first.contains("private"));
        assertEquals("{\"value\":\"private\"}", module.unprotectValue(first, context));
        assertThrows(Exception.class, () -> module.unprotectValue(first, "public/SUBJECT/other-id/clearance"));
        assertThrows(Exception.class, () -> module.unprotectValue(first.substring(0, first.length() - 2) + "AA", context));
    }
    @Test void fieldsDoNotShareTheProvisioningSecretFormat() throws Exception {
        String field = module.protectValue("short", "field-context");
        assertThrows(IllegalArgumentException.class, () -> module.decrypt(field));
        assertThrows(IllegalArgumentException.class, () -> module.protectValue("x".repeat(4097), "field-context"));
    }
}
