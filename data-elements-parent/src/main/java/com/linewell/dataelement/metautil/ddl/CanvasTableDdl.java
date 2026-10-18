package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLCommentStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Dialect parsing used by the existing createTable Magic API's canvas entry. */
@Component
@MagicModule("canvasTableDdl")
public class CanvasTableDdl {
    /** Materialization executes the user's SQL verbatim; only the selected connection supplies metadata scope. */
    public Map<String, Object> submitted(String ddl, String tableName, DataSourceConfig config) {
        if (ddl == null || ddl.isBlank()) throw new IllegalArgumentException("请填写建表语句");
        if (tableName == null || tableName.isBlank()) throw new IllegalArgumentException("请填写目标表名");
        String name = tableName.trim();
        if (config.getDatabaseType() == DatabaseType.HIVE) name = name.toLowerCase(Locale.ROOT);
        return Map.of("ddl",ddl,"tableName",name,"lookupName",unquote(name),
                "schemaName",scope(config),"quoted",isQuoted(name),"syntaxChecked",false);
    }

    /** Preview stays editable; execution must not send a newly promoted LOB key to Oracle. */
    public static void validateStringKeys(String ddl,DataSourceConfig config) {
        if(DdlDialect.family(config.getDatabaseType())!=DdlDialect.Family.ORACLE)return;
        if(!ddl.matches("(?is)^\\s*CREATE\\s+TABLE\\b.*"))return;
        for(var statement:SQLUtils.parseStatements(ddl,DdlDialect.parser(config.getDatabaseType()))) {
            if(!(statement instanceof SQLCreateTableStatement create))continue;
            var keys=new java.util.HashSet<String>();
            create.getPrimaryKeyNames().forEach(name->keys.add(SQLUtils.normalize(name).toLowerCase(Locale.ROOT)));
            for(var element:create.getTableElementList())if(element instanceof com.alibaba.druid.sql.ast.statement.SQLUniqueConstraint unique)
                unique.getColumns().forEach(key->keys.add(SQLUtils.normalize(key.getExpr().toString()).toLowerCase(Locale.ROOT)));
            for(var column:create.getColumnDefinitions()) {
                boolean key=column.isPrimaryKey()||keys.contains(SQLUtils.normalize(column.getColumnName()).toLowerCase(Locale.ROOT))
                        ||column.getConstraints().stream().anyMatch(com.alibaba.druid.sql.ast.statement.SQLColumnUniqueKey.class::isInstance);
                if(key&&java.util.Set.of("CLOB","NCLOB","BLOB","LONG").contains(column.getDataType().getName().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("字段 "+column.getColumnName()+" 的容量需要大字段类型，但Oracle兼容库不允许其作为主键或唯一键；请调整键设计或显式确认可用的字段容量，不会自动截断或删除键约束");
            }
        }
    }

    public Map<String, Object> parse(String ddl, DataSourceConfig config) {
        if (ddl == null || ddl.isBlank()) throw new IllegalArgumentException("请输入 CREATE TABLE 建表语句");
        DbType dialect = dialect(config.getDatabaseType());
        var statements = PortableSqlParser.parse(ddl, config.getDatabaseType());
        if (statements.isEmpty() || !(statements.getFirst() instanceof SQLCreateTableStatement create)
                || create.isReplace() || create.getType() != null || create.getSelect() != null
                || create.getColumnDefinitions().isEmpty()) {
            throw new IllegalArgumentException("请输入一张表的 CREATE TABLE 字段定义，可附带该表的 COMMENT ON 注释语句");
        }
        List<String> name = parts(create.getName());
        if (name.size() > 2) throw new IllegalArgumentException("表名最多包含 Schema 和表名两部分");
        String scope = scope(config);
        if (name.size() == 2 && !unquote(name.getFirst()).equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("DDL 中的数据库或 Schema 与所选目标库不一致：" + scope);
        }
        List<String> table = List.of(scope, unquote(name.getLast()));
        for (int index = 1; index < statements.size(); index++) {
            if (!(statements.get(index) instanceof SQLCommentStatement comment)) {
                throw new IllegalArgumentException("一次只能创建一张表，不能包含其他建表、修改、删除或数据写入语句");
            }
            List<String> target = new ArrayList<>(parts(comment.getOn().getExpr()));
            if (comment.getType() == SQLCommentStatement.Type.COLUMN) target.removeLast();
            else if (comment.getType() != SQLCommentStatement.Type.TABLE) {
                throw new IllegalArgumentException("仅支持当前表及其字段的注释");
            }
            if (target.size() == 1) target.addFirst(scope);
            if (target.size() != 2 || !unquote(target.getFirst()).equalsIgnoreCase(table.getFirst())
                    || !unquote(target.getLast()).equals(unquote(name.getLast()))) {
                throw new IllegalArgumentException("COMMENT ON 必须指向本次新建的表");
            }
        }
        // The validate-only path and actual CREATE share the same key/type checks.
        validateStringKeys(ddl,config);
        // Strict CREATE makes a concurrent duplicate observable even if the user supplied IF NOT EXISTS.
        create.setIfNotExiists(false);
        if (config.getDatabaseType() == DatabaseType.HIVE) {
            // The selected Hive database is applied by the managed execution
            // path. Keep the preview/editable DDL focused on the table name
            // and never add a database qualifier to CREATE TABLE.
            create.setName(new SQLIdentifierExpr(name.getLast()));
        }
        String normalized = SQLUtils.toSQLString(statements, dialect).trim();
        if (normalized.endsWith(";")) normalized = normalized.substring(0, normalized.length() - 1);
        return Map.of("ddl", normalized, "tableName", name.getLast(), "lookupName", unquote(name.getLast()),
                "schemaName", scope, "quoted", isQuoted(name.getLast()));
    }

    public boolean matches(Map<String, Object> parsed, String candidate, String schema) {
        if (candidate == null) return false;
        String expected = parsed.get("lookupName").toString();
        boolean nameMatches = Boolean.TRUE.equals(parsed.get("quoted")) ? expected.equals(candidate) : expected.equalsIgnoreCase(candidate);
        return nameMatches && (schema == null || schema.isBlank() || schema.equalsIgnoreCase(parsed.get("schemaName").toString()));
    }

    public boolean alreadyExists(Throwable failure) {
        for (Throwable e = failure; e != null; e = e.getCause()) {
            if (e instanceof SQLException sql && (sql.getErrorCode() == 955 || sql.getErrorCode() == 1050
                    || sql.getErrorCode() == 2714 || "42P07".equals(sql.getSQLState()) || "X0Y32".equals(sql.getSQLState()))) return true;
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("already exists") || message.contains("already used by an existing object")
                    || message.contains("已存在") || message.contains("对象名已存在")) return true;
            if (e.getCause() == e) break;
        }
        return false;
    }

    private static List<String> parts(SQLExpr expr) {
        if (expr instanceof SQLIdentifierExpr id) return List.of(id.getName());
        if (expr instanceof SQLPropertyExpr property) {
            List<String> names = new ArrayList<>(parts(property.getOwner()));
            names.add(property.getName());
            return names;
        }
        throw new IllegalArgumentException("无法识别 DDL 表名");
    }

    private static String scope(DataSourceConfig config) { return DdlDialect.scope(config); }

    private static boolean isQuoted(String value) {
        return value.startsWith("\"") || value.startsWith("`") || value.startsWith("[");
    }

    private static String unquote(String value) {
        if (!isQuoted(value)) return value;
        char quote = value.charAt(0) == '[' ? ']' : value.charAt(0);
        return value.substring(1, value.length() - 1).replace("" + quote + quote, "" + quote);
    }

    private static DbType dialect(DatabaseType type) { return DdlDialect.parser(type); }
}
