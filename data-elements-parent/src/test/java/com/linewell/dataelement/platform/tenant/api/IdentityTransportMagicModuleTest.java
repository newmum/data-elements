package com.linewell.dataelement.platform.tenant.api;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IdentityTransportMagicModuleTest {
    private IdentityTransportMagicModule module(boolean local) {
        return new IdentityTransportMagicModule(Base64.getEncoder().encodeToString(new byte[32]), local);
    }
    @Test void protectsEveryAuthenticationFieldAndRejectsExpiredReplays() throws Exception {
        var module = module(false); String secret = "A".repeat(48);
        String cipher = module.encrypt(secret); String nonce = "b".repeat(32); long now = Instant.now().getEpochSecond();
        String signature = module.signature(secret, "ga-key", "ga", now, nonce, "{\"event\":1}");
        assertTrue(module.verify(cipher, "ga-key", "ga", now, nonce, "{\"event\":1}", signature));
        assertFalse(module.verify(cipher, "gd-key", "ga", now, nonce, "{\"event\":1}", signature));
        assertFalse(module.verify(cipher, "ga-key", "gd", now, nonce, "{\"event\":1}", signature));
        assertFalse(module.verify(cipher, "ga-key", "ga", now, "c".repeat(32), "{\"event\":1}", signature));
        assertFalse(module.verify(cipher, "ga-key", "ga", now, nonce, "{\"event\":2}", signature));
        assertFalse(module.verify(cipher, "ga-key", "ga", now - 301, nonce, "{\"event\":1}", signature));
    }
    @Test void authenticatedEncryptionDetectsTamperingAndMissingMasterKey() throws Exception {
        var module = module(false); String secret = "B".repeat(48); String encrypted = module.encrypt(secret);
        assertEquals(secret, module.decrypt(encrypted)); assertNotEquals(encrypted, module.encrypt(secret));
        String[] fields = encrypted.split(":"); byte[] bytes = Base64.getDecoder().decode(fields[2]); bytes[0] ^= 1;
        assertThrows(Exception.class, () -> module.decrypt(fields[0]+":"+fields[1]+":"+Base64.getEncoder().encodeToString(bytes)));
        assertThrows(IllegalStateException.class, () -> new IdentityTransportMagicModule("", false).encrypt(secret));
    }
    @Test void permitsOnlyHttpsOrExplicitLocalDevelopmentAndNoUrlCredentials() {
        var production = module(false); var development = module(true);
        assertEquals("https://receiver.example.org/apply", production.validateUrl("https://receiver.example.org/apply"));
        assertThrows(IllegalArgumentException.class, () -> production.validateUrl("http://localhost:8088/apply"));
        assertEquals("http://localhost:8088/apply", development.validateUrl("http://localhost:8088/apply"));
        assertThrows(IllegalArgumentException.class, () -> development.validateUrl("http://192.168.1.1/apply"));
        assertThrows(IllegalArgumentException.class, () -> development.validateUrl("https://user:pass@receiver.example.org/apply"));
        assertThrows(IllegalArgumentException.class, () -> development.validateUrl("https://receiver.example.org/apply?token=x"));
        assertThrows(IllegalArgumentException.class, () -> development.validateUrl("https://receiver.example.org/apply#x"));
    }
}
