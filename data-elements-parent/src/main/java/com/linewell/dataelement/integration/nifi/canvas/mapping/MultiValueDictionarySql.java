package com.linewell.dataelement.integration.nifi.canvas.mapping;

import java.util.Map;

/** SQL executed by NiFi's per-record dictionary lookup for MySQL 8 registrations. */
final class MultiValueDictionarySql {
    private MultiValueDictionarySql() {}

    static String standard(String target, Map<String, String> values, String separator) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("多值标准翻译缺少编码映射");
        }
        StringBuilder rows = new StringBuilder();
        values.forEach((code, label) -> {
            if (!rows.isEmpty()) rows.append(" UNION ALL ");
            rows.append("SELECT ").append(literal(code)).append(" AS dict_code, ")
                    .append(literal(label)).append(" AS dict_name");
        });
        return query(target, "(" + rows + ")", separator);
    }

    static String query(String target, String dictionaryRows, String separator) {
        identifier(target);
        if (separator == null || separator.isBlank() || separator.length() > 8
                || separator.indexOf('"') >= 0 || separator.indexOf('\\') >= 0) {
            throw new IllegalStateException("多值翻译分隔符须为 1–8 个非引号、非反斜杠字符");
        }
        return "SELECT CASE WHEN SUM(CASE WHEN dict.dict_name IS NOT NULL AND dict.dict_name <> '' "
                + "THEN 1 ELSE 0 END) = 0 THEN NULL ELSE "
                + "GROUP_CONCAT(COALESCE(NULLIF(dict.dict_name, ''), TRIM(tokens.code)) "
                + "ORDER BY tokens.ord SEPARATOR " + literal(separator) + ") END AS " + target
                + " FROM JSON_TABLE(CONCAT('[', REPLACE(JSON_QUOTE(?), " + literal(separator)
                + ", CHAR(34,44,34)), ']'), '$[*]' COLUMNS (ord FOR ORDINALITY, "
                + "code VARCHAR(1024) PATH '$')) tokens LEFT JOIN " + dictionaryRows
                + " dict ON CAST(dict.dict_code AS BINARY) = CAST(TRIM(tokens.code) AS BINARY) "
                + "WHERE TRIM(tokens.code) <> ''";
    }

    static String literal(String text) {
        return "'" + String.valueOf(text).replace("'", "''") + "'";
    }

    private static void identifier(String text) {
        if (text == null || !text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("多值翻译目标字段不合法: " + text);
        }
    }
}
