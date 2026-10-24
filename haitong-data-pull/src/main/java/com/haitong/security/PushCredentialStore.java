package com.haitong.security;

import com.haitong.config.Secrets;
import com.haitong.core.Rules;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Authentication adapter only. Credential lifecycle SQL is maintained in Magic.
 */
@Component
public class PushCredentialStore {
    private static final Set<String> PUSH_CHANNELS = Set.of("table-to-ftp", "table-to-kafka");
    private final JdbcTemplate jdbc;

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


}
