package com.linewell.dataelement.platform.tenant.api;

import cn.hutool.crypto.SmUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Credential primitives only. Account lifecycle, policies and authorization are Magic rules. */
@Component
@MagicModule("iamSecurity")
public class IamSecurityMagicModule {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        if (password == null || password.length() > 256) throw new IllegalArgumentException("密码长度不正确");
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(digest);
    }

    public boolean verify(String password, String algorithm, String encoded) {
        if (password == null || password.length() > 256 || encoded == null) return false;
        try {
            if ("LEGACY_SM3_SALT".equals(algorithm)) {
                return PwdRuleUtil.checkPwdBySalt(SmUtil.sm3(password).toUpperCase(java.util.Locale.ROOT), encoded);
            }
            if (!"PBKDF2_SHA256".equals(algorithm)) return false;
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (RuntimeException ex) { return false; }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("密码组件不可用", ex); }
        finally { spec.clearPassword(); }
    }
}
