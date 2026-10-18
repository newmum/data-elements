package com.linewell.dataelement.metautil.ddl;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDdlStatementsTest {
    @Test void commentsQuotedNamesAndNativeOptionsAreNotParsedOrRewritten() {
        String create="/* retain ; hint */ CREATE TABLE `ods;native` (note STRING COMMENT 'a;''b') STORED AS vendor_format";
        String comment="-- retain ; line\nCOMMENT ON TABLE \"ods;native\" IS '中文;注释'";
        assertEquals(List.of(create,comment),NativeDdlStatements.split(create+";\n"+comment+";"));
    }
    @Test void supportsOracleAlternativeQuotesAndPostgresDollarQuotes() {
        String first="COMMENT ON TABLE t IS q'[a;'b]'";
        String second="COMMENT ON TABLE t IS $tag$a;'b$tag$";
        assertEquals(List.of(first,second),NativeDdlStatements.split(first+";"+second+";"));
    }
    @Test void malformedSqlIsSentIntactForDatabaseDiagnosis() {
        String invalid="CREATE TABLE t( broken 'syntax;";
        assertEquals(List.of(invalid),NativeDdlStatements.split(invalid));
    }
    @Test void preservesMultiStatementCreateIndexAndPartitionCommands() {
        assertEquals(List.of("CREATE TABLE t(id INT)","CREATE INDEX i ON t(id) GLOBAL", "ALTER TABLE t ADD PARTITION p VALUES LESS THAN (10)"),
                NativeDdlStatements.split("CREATE TABLE t(id INT); CREATE INDEX i ON t(id) GLOBAL; ALTER TABLE t ADD PARTITION p VALUES LESS THAN (10);"));
    }
}
