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

        var first = store.issue("push-source-001", false);
        assertTrue(first.revealed());
        assertFalse(first.apiKey().isBlank());
        assertNotNull(store.authenticate(first.apiKey()));
        assertTrue(store.authenticate(first.apiKey()).channel("table-to-ftp"));
        assertTrue(store.authenticate(first.apiKey()).channel("table-to-kafka"));
        assertFalse(store.authenticate(first.apiKey()).channel("demo"));
        assertFalse(store.issue("push-source-001", false).revealed());

        var rotated = store.issue("push-source-001", true);
        assertTrue(rotated.revealed());
        assertNull(store.authenticate(first.apiKey()));
        assertNotNull(store.authenticate(rotated.apiKey()));
    }
}
