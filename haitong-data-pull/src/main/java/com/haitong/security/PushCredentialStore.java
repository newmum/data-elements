package com.haitong.security;

import com.haitong.config.Secrets;
import com.haitong.core.Rules;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Service-side storage for data-source push credentials.  The plaintext key is
 * generated here and returned only by the protected issue call; the database
 * retains a one-way SHA-256 digest instead of the credential itself.
 */
@Component
public class PushCredentialStore {
    private static final Set<String> PUSH_CHANNELS = Set.of("table-to-ftp", "table-to-kafka");
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public PushCredentialStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Secrets.Identity authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return null;
        String hash = Rules.sha256(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select client_id from ht_push_api_client
                 where api_key_sha256=? and is_enable=1 and is_del=0
                """, hash);
        if (rows.isEmpty()) return null;
        return new Secrets.Identity(String.valueOf(rows.getFirst().get("client_id")), false, PUSH_CHANNELS, Set.of());
    }

    /** Returns plaintext only when a credential was newly created or rotated. */
    public synchronized IssuedCredential issue(String clientId, boolean rotate) {
        String client = requiredClient(clientId);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                select client_id from ht_push_api_client
                 where client_id=? and is_enable=1 and is_del=0
                """, client);
        if (!existing.isEmpty() && !rotate) return new IssuedCredential(client, "", false);

        String key = nextKey();
        String hash = Rules.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        try {
            if (existing.isEmpty()) {
                jdbc.update("""
                        insert into ht_push_api_client(client_id,api_key_sha256,is_enable,is_del,created_at,updated_at)
                        values (?,?,1,0,?,?)
                        """, client, hash, now, now);
            } else {
                jdbc.update("""
                        update ht_push_api_client set api_key_sha256=?, updated_at=?
                         where client_id=? and is_enable=1 and is_del=0
                        """, hash, now, client);
            }
        } catch (DuplicateKeyException conflict) {
            // A concurrent registration generated this source credential first.
            return new IssuedCredential(client, "", false);
        }
        return new IssuedCredential(client, key, true);
    }

    private String requiredClient(String value) {
        String client = value == null ? "" : value.trim();
        if (!client.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("clientId 格式不正确");
        if (!client.startsWith("push-")) throw new IllegalArgumentException("仅允许签发数据源推送调用方");
        return client;
    }

    private String nextKey() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedCredential(String clientId, String apiKey, boolean revealed) { }
}
