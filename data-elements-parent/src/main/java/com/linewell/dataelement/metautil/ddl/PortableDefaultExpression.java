package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Locale;
import java.util.Set;

/** AST-based defaults. Unresolved expressions remain editable; preview never rejects a complete table. */
public final class PortableDefaultExpression {
    private PortableDefaultExpression() { }
    public record Converted(String value, String warning, boolean unresolved) { }

    public static Converted convert(String raw, String sourceCode, DatabaseType target) {
        if (raw == null || raw.isBlank()) return new Converted(null, null, false);
        if (DdlDialect.family(target) == DdlDialect.Family.HIVE) return new Converted(null, null, false);
        DatabaseType source = source(sourceCode);
        if (source == null && raw.contains("::")) source = DatabaseType.POSTGRESQL;
        DbType parser = source == null ? raw.contains("::") ? DbType.postgresql : DdlDialect.parser(target) : DdlDialect.parser(source);
        try {
            SQLExpr expression = SQLUtils.toSQLExpr(raw.trim(), parser);
            return expression(expression, source, target);
        } catch (RuntimeException unsupported) {
            return unresolved(raw, "默认值暂不能自动转换，请在默认值或DDL中填写目标表达式");
        }
    }

    private static Converted expression(SQLExpr expr, DatabaseType source, DatabaseType target) {
        if (expr instanceof SQLNullExpr) return ok("NULL");
        if (expr instanceof SQLCharExpr literal) return ok("'" + literal.getText().replace("'", "''") + "'");
        if (expr instanceof SQLBooleanExpr value) return ok(switch (DdlDialect.family(target)) {
            case ORACLE, MYSQL, SQLSERVER -> value.getBooleanValue() ? "1" : "0";
            default -> value.getBooleanValue() ? "TRUE" : "FALSE";
        });
        if (expr instanceof SQLNumericLiteralExpr) return ok(expr.toString());
        if (expr instanceof SQLCastExpr cast) {
            SQLExpr inner = cast.getExpr();
            PortableColumnType.Kind kind = PortableColumnType.kind(cast.getDataType().toString());
            if (inner instanceof SQLNullExpr) return ok("NULL");
            if (inner instanceof SQLCharExpr literal && Set.of(PortableColumnType.Kind.VARCHAR, PortableColumnType.Kind.CHAR, PortableColumnType.Kind.TEXT).contains(kind)) {
                var arguments = cast.getDataType().getArguments();
                if (!arguments.isEmpty() && arguments.getFirst() instanceof SQLIntegerExpr length
                        && literal.getText().codePointCount(0, literal.getText().length()) > length.getNumber().longValue()) {
                    return unresolved(expr.toString(), "默认值的字符转换会截短内容，请确认目标表达式");
                }
                return expression(inner, source, target);
            }
            if (inner instanceof SQLBooleanExpr && kind == PortableColumnType.Kind.BOOLEAN) return expression(inner, source, target);
            if (kind == PortableColumnType.Kind.DATE && inner instanceof SQLCharExpr) {
                Converted value = expression(inner, source, target);
                return ok(DdlDialect.family(target) == DdlDialect.Family.ORACLE || DdlDialect.family(target) == DdlDialect.Family.POSTGRES
                        ? "DATE " + value.value() : "CAST(" + value.value() + " AS DATE)");
            }
            if (kind == PortableColumnType.Kind.TIMESTAMP) {
                Converted value = expression(inner, source, target);
                if (!value.unresolved() && "CURRENT_TIMESTAMP".equals(value.value())) return value;
            }
            // An integer/numeric cast can round or overflow. Keep native semantics instead of dropping it.
            if (source != null && DdlDialect.family(source) == DdlDialect.family(target))
                return ok(SQLUtils.toSQLString(expr, DdlDialect.parser(target)));
            return unresolved(expr.toString(), "默认值的类型转换需要确认目标库语义，请编辑目标表达式");
        }
        String name = expr instanceof SQLMethodInvokeExpr method ? method.getMethodName()
                : expr instanceof SQLIdentifierExpr identifier ? identifier.getName() : "";
        String function = name.toUpperCase(Locale.ROOT);
        if (Set.of("NOW", "CURRENT_TIMESTAMP", "LOCALTIMESTAMP", "GETDATE", "SYSDATE", "SYSTIMESTAMP").contains(function)) {
            if (!(expr instanceof SQLMethodInvokeExpr method) || method.getArguments().isEmpty()) return ok("CURRENT_TIMESTAMP");
        }
        if (function.equals("CURRENT_DATE")) return ok(switch (DdlDialect.family(target)) {
            case SQLSERVER -> "CAST(CURRENT_TIMESTAMP AS DATE)";
            case ORACLE -> "TRUNC(CURRENT_DATE)";
            default -> "CURRENT_DATE";
        });
        if (source != null && DdlDialect.family(source) == DdlDialect.family(target))
            return ok(SQLUtils.toSQLString(expr, DdlDialect.parser(target)));
        return unresolved(expr.toString(), "默认值含函数或复杂表达式，请确认目标库可执行后使用");
    }

    private static Converted ok(String value) { return new Converted(value, null, false); }
    private static Converted unresolved(String value, String warning) { return new Converted(value, warning, true); }
    private static DatabaseType source(String code) {
        if (code == null || code.isBlank()) return null;
        try { return DatabaseType.fromCode(code); } catch (IllegalArgumentException unknown) { return null; }
    }
}
