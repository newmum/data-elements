package com.linewell.dataelement.metautil.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class JdbcDriverPropertyResolverTest {

    @Test
    void resolvesOraclePropertiesWrittenByTheDatasourceCustomPropertyPanel() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("oracle.jdbc.encryption_client", "required");
        config.put("oracle.jdbc.encryption_algorithms_client", "(AES256,AES192,AES128)");
        config.put("username", "scott");

        assertThat(JdbcDriverPropertyResolver.resolve(config)).containsExactly(
                Map.entry("oracle.jdbc.encryption_client", "required"),
                Map.entry("oracle.jdbc.encryption_algorithms_client", "(AES256,AES192,AES128)"));
    }

    @Test
    void acceptsNormalizedPropertiesAndDoesNotTreatStandardFieldsAsDriverProperties() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("poolCfg", Map.of("oracle.net.CONNECT_TIMEOUT", 30000));
        config.put("Password", "must-not-replace-nifi-password");

        Properties properties = new Properties();
        JdbcDriverPropertyResolver.apply(properties, config);

        assertThat(properties).containsEntry("oracle.net.CONNECT_TIMEOUT", "30000")
                .doesNotContainKey("Password");
    }
}
