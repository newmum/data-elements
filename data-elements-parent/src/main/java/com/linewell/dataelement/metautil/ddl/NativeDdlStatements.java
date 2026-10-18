package com.linewell.dataelement.metautil.ddl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Splits JDBC script delimiters without parsing, rewriting or validating vendor SQL. */
public final class NativeDdlStatements {
    private NativeDdlStatements() {}
    private static final Pattern DOLLAR = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");

    public static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        if (sql == null || sql.isBlank()) return result;
        int start = 0;
        char quote = 0;
        boolean line = false;
        int block = 0;
        String delimiter = null;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i), next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (line) { if (c == '\n' || c == '\r') line = false; continue; }
            if (block > 0) {
                if (c == '/' && next == '*') { block++; i++; }
                else if (c == '*' && next == '/') { block--; i++; }
                continue;
            }
            if (delimiter != null) {
                if (sql.startsWith(delimiter,i)) { i += delimiter.length() - 1; delimiter = null; }
                continue;
            }
            if (quote != 0) {
                if (c == quote) { if (next == quote) i++; else quote = 0; }
                continue;
            }
            if (c == '-' && next == '-') { line = true; i++; continue; }
            if (c == '/' && next == '*') { block = 1; i++; continue; }
            if ((c == 'q' || c == 'Q') && next == '\'' && i + 2 < sql.length()) {
                char opening = sql.charAt(i + 2);
                char closing = switch (opening) { case '[' -> ']'; case '(' -> ')'; case '{' -> '}'; case '<' -> '>'; default -> opening; };
                delimiter = "" + closing + '\''; i += 2; continue;
            }
            if (c == '$') {
                var match = DOLLAR.matcher(sql).region(i,sql.length());
                if (match.lookingAt()) { delimiter = match.group(); i += delimiter.length() - 1; continue; }
            }
            if (c == '\'' || c == '"' || c == '`' || c == '[') { quote = c == '[' ? ']' : c; continue; }
            if (c == ';') { add(result,sql.substring(start,i)); start = i + 1; }
        }
        add(result,sql.substring(start));
        return result;
    }

    private static void add(List<String> statements,String statement) {
        if (!statement.isBlank()) statements.add(statement.trim());
    }
}
