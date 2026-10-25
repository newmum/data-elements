package com.linewell.dataelement.platform.audit;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AuditFingerprintTest {
    @TempDir Path directory;
    @Test void fingerprintSurvivesRestartWithoutProvisioningConfiguration(){var file=directory.resolve("validation.key");var first=new AuditFingerprint("",file.toString());var restarted=new AuditFingerprint("",file.toString());assertEquals(first.fingerprint("validation.user","AUTH_WORKFORCE"),restarted.fingerprint("validation.user","AUTH_WORKFORCE"));assertNotEquals(first.fingerprint("validation.user","AUTH_PUBLIC"),first.fingerprint("validation.user","AUTH_WORKFORCE"));}
    @Test void malformedKeyFailsAtConfigurationTime(){assertThrows(IllegalStateException.class,()->new AuditFingerprint("invalid",directory.resolve("unused").toString()));}
}
