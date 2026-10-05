package com.linewell.dataelement.platform.magic.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MagicPlatformSqlDialectInterceptorTest {

    @Test
    void convertsMysqlIdentifierQuotesForDameng() {
        assertEquals(
                "select \"key\" from \"sym_tenant_t\"",
                MagicPlatformSqlDialectInterceptor.adaptDamengIdentifiers(
                        "select `key` from `sym_tenant_t`"));
    }

    @Test
    void leavesQuotedTextAndCommentsUntouched() {
        String sql = """
                select '`literal`', "quoted`text"
                  from `sym_tenant_t`
                 where code = 'PUBLIC`SECURITY'
                -- keep `comment`
                /* keep `block` */
                """;
        String expected = """
                select '`literal`', "quoted`text"
                  from "sym_tenant_t"
                 where code = 'PUBLIC`SECURITY'
                -- keep `comment`
                /* keep `block` */
                """;
        assertEquals(expected, MagicPlatformSqlDialectInterceptor.adaptDamengIdentifiers(sql));
    }
}
