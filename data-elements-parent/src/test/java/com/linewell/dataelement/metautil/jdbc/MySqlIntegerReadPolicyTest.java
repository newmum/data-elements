package com.linewell.dataelement.metautil.jdbc;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MySqlIntegerReadPolicyTest {
    @Test void onlyMysqlReadersDisableDisplayWidthBooleanInferenceAndKeepAllOtherUrlValues() {
        for(String url:List.of("jdbc:mysql://validation.invalid/db","jdbc:mysql://validation.invalid/db?",
                "jdbc:mysql://validation.invalid/db?serverTimezone=UTC&")) {
            String result=MySqlIntegerReadPolicy.url(url);assertTrue(result.endsWith("tinyInt1isBit=false"));assertEquals(result,MySqlIntegerReadPolicy.url(result));
        }
        String before="jdbc:mysql://validation.invalid/db?password=validation%26secret%3DtinyInt1isBit%3Dtrue&serverTimezone=UTC&TINYINT1ISBIT=true&tinyInt1isBit=true";
        String expected="jdbc:mysql://validation.invalid/db?password=validation%26secret%3DtinyInt1isBit%3Dtrue&serverTimezone=UTC&tinyInt1isBit=false&tinyInt1isBit=false";
        assertEquals(expected,MySqlIntegerReadPolicy.url(before));
        assertEquals("jdbc:mysql://validation.invalid/db?tinyInt1isBit=false",MySqlIntegerReadPolicy.url("jdbc:mysql://validation.invalid/db?%74inyInt1isBit=true"));
        for(String url:List.of("jdbc:postgresql://validation.invalid/db?tinyInt1isBit=true","jdbc:oracle:thin:@validation.invalid:1521/validation","jdbc:clickhouse://validation.invalid/db"))assertSame(url,MySqlIntegerReadPolicy.url(url));
        assertNull(MySqlIntegerReadPolicy.url(null));
    }
    @Test void readNormalizationDoesNotChangeTheRegistrationOrIntermediateUrlConstruction() {
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.MYSQL);config.setHost("validation.invalid");config.setDatabase("validation");
        assertFalse(config.buildJdbcUrl().contains("tinyInt1isBit"));
        assertTrue(MySqlIntegerReadPolicy.url(config.buildJdbcUrl()).endsWith("&tinyInt1isBit=false"));
        String explicit="jdbc:mysql://validation.invalid/db?tinyInt1isBit=true&useSSL=false";config.setJdbcUrl(explicit);
        assertEquals("jdbc:mysql://validation.invalid/db?tinyInt1isBit=false&useSSL=false",MySqlIntegerReadPolicy.url(config.buildJdbcUrl()));assertEquals(explicit,config.getJdbcUrl());assertEquals(explicit,config.buildJdbcUrl());
    }
}
