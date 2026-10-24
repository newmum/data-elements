package com.haitong.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PushCredentialStoreTest {
    @Test
    void issuesOneTimePlaintextAndInvalidatesItOnRotation() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:push_credentials;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                create table ht_push_api_client (
                  client_id varchar(128) primary key, api_key_sha256 char(64) not null unique,
                  is_enable smallint not null, is_del smallint not null, created_at bigint not null, updated_at bigint not null)
                """);
        PushCredentialStore store = new PushCredentialStore(jdbc);

        String key="synthetic-push-key-0123456789abcdef";
        jdbc.update("INSERT INTO ht_push_api_client VALUES (?,?,1,0,0,0)", "push-source-001", com.haitong.core.Rules.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNotNull(store.authenticate(key));
        assertTrue(store.authenticate(key).channel("table-to-ftp"));
        assertFalse(store.authenticate(key).channel("demo"));
        jdbc.update("UPDATE ht_push_api_client SET is_enable=0");
        assertNull(store.authenticate(key));
    }
}
