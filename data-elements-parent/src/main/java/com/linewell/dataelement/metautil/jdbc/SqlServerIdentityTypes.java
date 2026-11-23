package com.linewell.dataelement.metautil.jdbc;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JDBC TYPE_NAME may include SQL Server's IDENTITY property; it is not a data type. */
public final class SqlServerIdentityTypes {
    private SqlServerIdentityTypes() {}
    private static final Pattern DECLARATION = Pattern.compile(
            "^\\s*((?:tinyint|smallint|int|integer|bigint)|(?:decimal|numeric)(?:\\s*\\(\\s*(?:[1-9]|[12][0-9]|3[0-8])\\s*(?:,\\s*0\\s*)?\\))?)"
                    + "\\s+identity(?:\\s*\\(\\s*([+-]?\\d{1,38})\\s*,\\s*([+-]?\\d{1,38})\\s*\\))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static Matcher identity(String raw) {
        if (raw == null || raw.length() > 512) return null;
        Matcher match = DECLARATION.matcher(raw);
        if (!match.matches() || match.group(3) != null && new BigInteger(match.group(3)).signum() == 0) return null;
        return match;
    }

    public static boolean hasIdentity(String raw) { return identity(raw) != null; }

    public static String baseType(String raw) {
        Matcher match = identity(raw);
        return match == null ? raw : match.group(1).replaceAll("\\s*([(),])\\s*", "$1").trim();
    }

    public static String forSource(String raw, String sourceCode) {
        try { return DatabaseType.fromCode(sourceCode) == DatabaseType.SQLSERVER ? baseType(raw) : raw; }
        catch (IllegalArgumentException unknown) { return raw; }
    }

    public static String forConnection(String raw, Map<String, ?> config) {
        if (config == null) return raw;
        for (String key : new String[]{"dbType", "db_type", "databaseType", "database_type"}) {
            Object value = config.get(key);
            if (value == null || value.toString().isBlank()) continue;
            try { return DatabaseType.fromCode(value.toString()) == DatabaseType.SQLSERVER ? baseType(raw) : raw; }
            catch (IllegalArgumentException unknown) { /* An unrecognized label may still have a JDBC protocol. */ }
        }
        for (String key : new String[]{"jdbcURL", "jdbcUrl", "url"}) {
            Object value = config.get(key);
            if (value != null && value.toString().toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver:")) return baseType(raw);
        }
        return raw;
    }
}
