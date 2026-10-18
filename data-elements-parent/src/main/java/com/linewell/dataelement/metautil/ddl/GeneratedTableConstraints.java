package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Enriches generated DDL only. Submitted/manual SQL never passes through this renderer. */
final class GeneratedTableConstraints {
    private GeneratedTableConstraints() {}

    static Map<String,Object> render(String ddl,List<Map<String,Object>> source,DataSourceConfig config) {
        try {return Map.of("ddl",apply(ddl,source,config),"warnings",List.of());}
        catch(RuntimeException unsupported) {
            return Map.of("ddl",ddl,"warnings",List.of(Map.of("field","","message","当前原生表达式未自动转换约束名称，请在DDL中确认主键及唯一约束；已保留可编辑SQL。")));
        }
    }

    static String apply(String ddl,List<Map<String,Object>> source,DataSourceConfig config) {
        boolean named=DdlDialect.family(config.getDatabaseType())==DdlDialect.Family.ORACLE;
        if(DdlDialect.family(config.getDatabaseType())==DdlDialect.Family.HIVE)return ddl;
        if(!named&&(source==null||source.isEmpty()))return ddl;
        var statements=PortableSqlParser.parse(ddl,config.getDatabaseType());
        if(statements.isEmpty()||!(statements.getFirst() instanceof SQLCreateTableStatement create))return ddl;
        Map<String,String> fields=new LinkedHashMap<>();
        for(var column:create.getColumnDefinitions())fields.put(SQLUtils.normalize(column.getColumnName()).toLowerCase(Locale.ROOT),column.getColumnName());
        Set<String> existing=new HashSet<>();
        List<SQLUniqueConstraint> constraints=new ArrayList<>();
        for(var element:create.getTableElementList())if(element instanceof SQLUniqueConstraint key)constraints.add(key);
        for(var column:create.getColumnDefinitions()) {
            if(column.getConstraints().removeIf(SQLColumnUniqueKey.class::isInstance)) {
                var key=new SQLUnique();key.addColumn(new SQLIdentifierExpr(column.getColumnName()));
                create.getTableElementList().add(key);constraints.add(key);
            }
        }
        for(var key:constraints) {
            var columns=key.getColumns().stream().map(c->SQLUtils.normalize(c.getExpr().toString()).toLowerCase(Locale.ROOT)).toList();
            existing.add(signature(columns));
            if(named)key.setName(new SQLIdentifierExpr(name(key instanceof SQLPrimaryKey?"PK":"UK",create.getName().toString(),columns)));
        }
        if(source!=null)for(var fact:source) {
            // A UNIQUE constraint called PK_xxx remains UNIQUE. Source primary keys do not override edited field keys.
            if(!"U".equalsIgnoreCase(Objects.toString(fact.get("type")))||!(fact.get("columns") instanceof List<?> columns)||columns.isEmpty())continue;
            List<String> normalized=columns.stream().map(c->SQLUtils.normalize(c.toString()).toLowerCase(Locale.ROOT)).toList();
            if(normalized.stream().anyMatch(c->!fields.containsKey(c))||!existing.add(signature(normalized)))continue;
            var unique=new SQLUnique();unique.setName(new SQLIdentifierExpr(name("UK",create.getName().toString(),normalized)));
            normalized.forEach(c->unique.addColumn(new SQLIdentifierExpr(fields.get(c))));
            create.getTableElementList().add(unique);
        }
        return SQLUtils.toSQLString(statements,DdlDialect.parser(config.getDatabaseType()));
    }

    private static String signature(List<String> fields){return fields.stream().sorted().collect(java.util.stream.Collectors.joining("|"));}
    private static String name(String prefix,String table,List<String> columns) {
        String label=SQLUtils.normalize(table).replaceAll("[^A-Za-z0-9_]","_").toUpperCase(Locale.ROOT);
        if(label.length()>14)label=label.substring(0,14);
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((table+"|"+prefix+"|"+signature(columns)).getBytes(StandardCharsets.UTF_8))).substring(0,10).toUpperCase(Locale.ROOT);
            return prefix+"_"+label+"_"+hash; // <=28 ASCII bytes, including older Oracle constraint-name limits.
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
