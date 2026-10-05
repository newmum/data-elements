package com.linewell.dataelement.platform.integration.pingao.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Atomic one-use, browser-bound storage shared across backend instances. */
@Component
public class PingaoSsoTickets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            local prefix = ARGV[1] .. '\\n'
            if string.sub(value, 1, string.len(prefix)) ~= prefix then return nil end
            redis.call('DEL', KEYS[1])
            return string.sub(value, string.len(prefix) + 1)
            """, String.class);
    private final StringRedisTemplate redis;
    private final PingaoSsoProperties properties;

    public PingaoSsoTickets(StringRedisTemplate redis, PingaoSsoProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public static String randomValue() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String issueState(String browserBinding) { return issue("state", browserBinding, "valid", Duration.ofMinutes(5)); }
    public boolean consumeState(String state, String browserBinding) { return "valid".equals(consume("state", state, browserBinding)); }
    public String issueExchange(String browserBinding, PingaoSsoClient.Identity identity) {
        if (identity == null || blank(identity.directoryUserId()) || blank(identity.externalOrganizationId())) {
            throw new IllegalArgumentException("external identity is incomplete");
        }
        // Only short-lived external identifiers are carried here. External access tokens are never stored.
        return issue("exchange", browserBinding, pack(identity), Duration.ofSeconds(60));
    }
    public PingaoSsoClient.Identity consumeExchange(String ticket, String browserBinding) {
        String value = consume("exchange", ticket, browserBinding);
        return value == null ? null : unpack(value);
    }

    private String issue(String kind, String binding, String value, Duration ttl) {
        properties.requireConfigured();
        requireBinding(binding);
        for (int attempt = 0; attempt < 3; attempt++) {
            String ticket = randomValue();
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(kind, ticket), digest(binding) + "\n" + value, ttl))) return ticket;
        }
        throw new IllegalStateException("无法生成一次性认证凭据");
    }
    private String consume(String kind, String ticket, String binding) {
        properties.requireConfigured();
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}") || binding == null || !binding.matches("[A-Za-z0-9_-]{43}")) return null;
        return redis.execute(CONSUME, List.of(key(kind, ticket)), digest(binding));
    }
    private String key(String kind, String ticket) {
        return "pingao:sso:" + digest(properties.getLocalTenantId() + "\n" + properties.getClientId()) + ":" + kind + ":" + digest(ticket);
    }
    private static void requireBinding(String binding) {
        if (binding == null || !binding.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid browser binding");
    }
    private static String pack(PingaoSsoClient.Identity identity) {
        return encode(identity.directoryUserId()) + "." + encode(identity.externalOrganizationId()) + "."
                + encode(identity.externalOrganizationCode());
    }
    private static PingaoSsoClient.Identity unpack(String value) {
        String[] fields = value.split("\\.", -1);
        if (fields.length != 3) return null;
        try {
            String userId = decode(fields[0]);
            String organizationId = decode(fields[1]);
            String organizationCode = decode(fields[2]);
            if (blank(userId) || blank(organizationId)) return null;
            return new PingaoSsoClient.Identity(userId, userId, organizationId, organizationCode);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private static boolean blank(String value) { return value == null || value.isBlank() || value.length() > 128; }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
