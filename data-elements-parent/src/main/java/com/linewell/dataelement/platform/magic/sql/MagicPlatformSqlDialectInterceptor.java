package com.linewell.dataelement.platform.magic.sql;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;

/**
 * Adapts platform Magic SQL identifiers to the configured database dialect.
 */
@Component
@Order(-200)
public class MagicPlatformSqlDialectInterceptor implements SQLInterceptor {

    private final boolean dameng;

    public MagicPlatformSqlDialectInterceptor(
            @Value("${spring.datasource.url:}") String datasourceUrl
    ) {
        this.dameng = datasourceUrl != null
                && datasourceUrl.toLowerCase(Locale.ROOT).contains(":dm:");
    }

    @Override
    public void preHandle(BoundSql boundSql, RequestEntity requestEntity) {
        if (!dameng
                || boundSql == null
                || boundSql.getSql() == null
                || !isPrimaryDataSource(boundSql)) {
            return;
        }
        boundSql.setSql(adaptDamengIdentifiers(boundSql.getSql()));
    }

    static String adaptDamengIdentifiers(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (lineComment) {
                result.append(current);
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                result.append(current);
                if (current == '*' && next == '/') {
                    result.append(next);
                    index++;
                    blockComment = false;
                }
                continue;
            }
            if (singleQuoted) {
                result.append(current);
                if (current == '\'' && next == '\'') {
                    result.append(next);
                    index++;
                } else if (current == '\'' && !escaped(sql, index)) {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                result.append(current);
                if (current == '"' && next == '"') {
                    result.append(next);
                    index++;
                } else if (current == '"' && !escaped(sql, index)) {
                    doubleQuoted = false;
                }
                continue;
            }

            if (current == '-' && next == '-') {
                result.append(current).append(next);
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                result.append(current).append(next);
                index++;
                blockComment = true;
            } else if (current == '\'') {
                result.append(current);
                singleQuoted = true;
            } else if (current == '"') {
                result.append(current);
                doubleQuoted = true;
            } else if (current == '`') {
                result.append('"');
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static boolean escaped(String value, int index) {
        int backslashes = 0;
        for (int cursor = index - 1; cursor >= 0 && value.charAt(cursor) == '\\'; cursor--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private boolean isPrimaryDataSource(BoundSql boundSql) {
        String name = boundSql.getSqlModule() == null
                ? null
                : boundSql.getSqlModule().getDataSourceName();
        return name == null
                || name.isBlank()
                || "default".equalsIgnoreCase(name)
                || "master".equalsIgnoreCase(name);
    }
}
