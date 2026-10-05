package com.linewell.dataelement.platform.tenant.api;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IamSecurityMagicModuleTest {
    private final IamSecurityMagicModule security = new IamSecurityMagicModule();
    @Test void saltedCredentialsAndMalformedInput() {
        String one = security.hash("Private-test@2026");
        String two = security.hash("Private-test@2026");
        assertNotEquals(one, two);
        assertTrue(security.verify("Private-test@2026", "PBKDF2_SHA256", one));
        assertFalse(security.verify("incorrect", "PBKDF2_SHA256", one));
        assertFalse(security.verify("Private-test@2026", "UNKNOWN", one));
        assertFalse(security.verify("x", "PBKDF2_SHA256", "pbkdf2-sha256$999999999$broken$broken"));
        assertFalse(security.verify(null, "PBKDF2_SHA256", one));
    }
}
