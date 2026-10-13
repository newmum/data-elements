package com.linewell.dataelement.metautil.model.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseTypeTest {
    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void allRegisteredCodesAndEnumNamesResolveToTheirOwnProtocol(DatabaseType type) {
        assertEquals(type, DatabaseType.fromCode(type.getCode()));
        assertEquals(type, DatabaseType.fromCode(" " + type.name() + " "));
    }

    @ParameterizedTest
    @CsvSource({
        "pg,POSTGRESQL", "postgres,POSTGRESQL", " Postgre SQL ,POSTGRESQL",
        "mssql,SQLSERVER", "MS SQL 2012+,SQLSERVER", "MS SQL 2008,SQLSERVER", "SQL Server,SQLSERVER",
        "oracle12,ORACLE", "oracle12+,ORACLE", " Oracle 12+ ,ORACLE",
        "kingbasees,KINGBASE", "King Base 8,KINGBASE", "elastic,ELASTICSEARCH", "Elastic Search,ELASTICSEARCH",
        "sftp,FTP", "mrs-hive,HIVE", "Ocean Base Oracle,OCEANBASE_ORACLE",
        "tdsql_pg,POSTGRESQL", "tdsql-mysql,MYSQL", "vastbase,POSTGRESQL", "openGauss,GAUSSDB"
    })
    void confirmedRegistrationAndCanvasAliasesShareOneResolution(String code, DatabaseType expected) {
        assertEquals(expected, DatabaseType.fromCode(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown-vendor", "oceanbase", "jdbc", "http", "s3"})
    void actualProtocolResolutionNeverGuessesAnUnknownOrAmbiguousType(String code) {
        assertThrows(IllegalArgumentException.class, () -> DatabaseType.fromCode(code));
    }

    @Test
    void absentTypeKeepsExistingNullContract() {
        assertNull(DatabaseType.fromCode(null));
    }
}
