package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Pure conversion module: no tenant lookup, connection, database reads or writes. */
@Component
@MagicModule("portableDdl")
public class PortableDdlConverter {
    public Map<String,Object> read(String ddl, String sourceCode) {
        DatabaseType source = DatabaseType.fromCode(sourceCode);
        var statements = PortableSqlParser.parse(ddl, source);
        if (statements.isEmpty() || !(statements.getFirst() instanceof SQLCreateTableStatement create)
                || create.getColumnDefinitions().isEmpty() || create.getSelect() != null)
            throw new IllegalArgumentException("请输入一张来源表的CREATE TABLE字段定义");
        for (int index = 1; index < statements.size(); index++) {
            if (!(statements.get(index) instanceof SQLCommentStatement comment))
                throw new IllegalArgumentException("来源DDL只支持建表定义及其注释");
            if (!create.apply(comment)) throw new IllegalArgumentException("注释不属于当前来源表");
        }
        Set<String> primary = new HashSet<>(), unique = new HashSet<>();
        create.getPrimaryKeyNames().forEach(name -> primary.add(SQLUtils.normalize(name).toLowerCase(Locale.ROOT)));
        List<String> warnings = new ArrayList<>();
        for (var element : create.getTableElementList()) {
            if (element instanceof SQLUniqueConstraint constraint && !(element instanceof SQLPrimaryKey)) {
                if (constraint.getColumns().size() == 1) unique.add(SQLUtils.normalize(constraint.getColumns().getFirst().getExpr().toString()).toLowerCase(Locale.ROOT));
                else warnings.add("来源表的组合唯一约束需在目标DDL中确认");
            }
            if (element instanceof SQLForeignKeyConstraint) warnings.add("来源表的外键需在目标库相关表就绪后配置");
            if (element instanceof SQLCheck) warnings.add("来源表的CHECK约束需在目标DDL中确认：" + element);
            if (!(element instanceof SQLColumnDefinition) && !(element instanceof SQLConstraint))
                warnings.add("来源表的索引或存储定义需在目标DDL中确认：" + element);
        }
        List<ColumnInfo> columns = new ArrayList<>();
        for (var definition : create.getColumnDefinitions()) {
            ColumnInfo column = new ColumnInfo();
            String name = SQLUtils.normalize(definition.getColumnName());
            column.setColumnName(name);
            column.setQuotedIdentifier(DdlDialect.names(definition.getColumnName()).getLast().quoted());
            column.setColumnComment(definition.getComment() instanceof SQLCharExpr comment ? comment.getText() : null);
            column.setSourceDatabaseType(sourceCode);
            column.setColumnType(definition.getDataType().toString());
            column.setDataType(definition.getDataType().getName());
            var arguments = definition.getDataType().getArguments();
            if (!arguments.isEmpty() && arguments.getFirst() instanceof SQLIntegerExpr size) {
                column.setLength(size.getNumber().longValue()); column.setPrecision(size.getNumber().intValue());
                if (arguments.size() > 1 && arguments.get(1) instanceof SQLIntegerExpr scale) column.setScale(scale.getNumber().intValue());
            }
            column.setNullable(definition.getConstraints().stream().noneMatch(SQLNotNullConstraint.class::isInstance));
            column.setPrimaryKey(definition.isPrimaryKey() || primary.contains(name.toLowerCase(Locale.ROOT)));
            column.setUnique(unique.contains(name.toLowerCase(Locale.ROOT)) || definition.getConstraints().stream().anyMatch(SQLColumnUniqueKey.class::isInstance));
            column.setAutoIncrement(definition.isAutoIncrement() || definition.getIdentity() != null || Set.of("serial", "bigserial", "smallserial").contains(column.getDataType().toLowerCase(Locale.ROOT)));
            column.setDefaultValue(definition.getDefaultExpr() == null ? null : definition.getDefaultExpr().toString());
            if (definition.getGeneratedAlawsAs() != null) warnings.add("字段“" + name + "”的生成表达式需在目标DDL中确认：" + definition.getGeneratedAlawsAs());
            column.setOrdinalPosition(columns.size());
            columns.add(column);
        }
        String comment = create.getComment() instanceof SQLCharExpr value ? value.getText() : "";
        var table = DdlDialect.names(create.getName().toString()).getLast();
        String tableName = table.quoted() ? DdlDialect.quote(source,table.value()) : table.value();
        return Map.of("columns", columns, "tableItems", logicalItems(columns), "tableName", tableName, "tableComment", comment, "warnings", warnings);
    }

    public Map<String,Object> prepare(List<ColumnInfo> original, DatabaseType target) {
        var config=new DataSourceConfig();config.setDatabaseType(target);return prepare(original,config);
    }

    public Map<String,Object> prepare(List<ColumnInfo> original, DataSourceConfig config) {
        DatabaseType target=config.getEffectiveDatabaseType();
        List<ColumnInfo> columns = new ArrayList<>();
        List<Map<String,Object>> warnings = new ArrayList<>();
        for (ColumnInfo source : original) {
            ColumnInfo column = new ColumnInfo(); BeanUtils.copyProperties(source, column);
            String raw = source.getColumnType() == null || source.getColumnType().isBlank() ? source.getDataType() : source.getColumnType();
            String rendered = PortableColumnType.render(config, source, raw);
            column.setColumnType(rendered); column.setDataType(rendered.replaceAll("\\(.*$", ""));
            var kind=PortableColumnType.kind(raw);
            if(kind==PortableColumnType.Kind.UUID&&PortableColumnType.kind(rendered)==PortableColumnType.Kind.VARCHAR){column.setLength(36L);column.setLengthSemantics("CHAR");}
            if((kind==PortableColumnType.Kind.CHAR||kind==PortableColumnType.Kind.VARCHAR)
                    && !target.getCode().equals(source.getDdlTargetDatabaseType())) {
                var capacity=PortableStringCapacity.render(config,source,raw,kind);
                column.setLength(capacity.length());column.setLengthSemantics(capacity.unit());
                column.setCharacterLength(capacity.unit().equals("CHAR")?capacity.length():null);
                column.setByteLength(capacity.unit().equals("BYTE")?capacity.length():null);
                if(capacity.reason()!=null)warnings.add(Map.of("field",source.getColumnName(),"message",capacity.reason()+"，目标类型为 "+rendered,"requiresEdit",false));
            }
            if(Set.of(PortableColumnType.Kind.CHAR,PortableColumnType.Kind.VARCHAR,PortableColumnType.Kind.TEXT,PortableColumnType.Kind.JSON).contains(kind)
                    &&config.getCharset()!=null&&!config.getCharset().isBlank()
                    &&!Set.of("AL32UTF8","UTF8","UTF8MB4","UTF16","UTF16LE","AL16UTF16").contains(PortableStringCapacity.charset(config.getCharset()))
                    &&(source.getCharset()==null||!PortableStringCapacity.charset(source.getCharset()).equals(PortableStringCapacity.charset(config.getCharset()))))
                warnings.add(Map.of("field",source.getColumnName(),"message","目标字符集 "+config.getCharset()+" 不能保证表示来源的全部字符；扩展长度不能解决字符集不兼容，请确认目标字符集","requiresEdit",true));
            var value = PortableDefaultExpression.convert(source.getDefaultValue(), source.getSourceDatabaseType(), target);
            column.setDefaultValue(value.value());
            if (value.warning() != null) warnings.add(Map.of("field", source.getColumnName(), "message", value.warning(), "requiresEdit", value.unresolved()));
            if(target==DatabaseType.MYSQL&&PortableColumnType.kind(rendered)==PortableColumnType.Kind.TEXT
                    &&value.value()!=null&&!value.value().isBlank())
                warnings.add(Map.of("field",source.getColumnName(),"message","目标已使用大文本类型，默认值语法受MySQL版本限制，请确认默认值表达式后再建表","requiresEdit",true));
            if (PortableColumnType.kind(raw) == PortableColumnType.Kind.UNKNOWN && !rendered.equalsIgnoreCase(raw))
                warnings.add(Map.of("field", source.getColumnName(), "message", "来源原生类型已转为目标库长文本，请确认写入表示", "requiresEdit", false));
            if (DdlDialect.family(target) == DdlDialect.Family.ORACLE && Set.of("CLOB", "NCLOB", "BLOB", "LONG").contains(rendered)
                    && (Boolean.TRUE.equals(column.getPrimaryKey()) || Boolean.TRUE.equals(column.getUnique())))
                warnings.add(Map.of("field", source.getColumnName(), "message", "目标库不支持长文本或二进制大字段作为键，请编辑类型或取消键约束", "requiresEdit", true));
            // Already rendered for the target. Do not reinterpret target DATE as an origin Oracle DATE.
            column.setSourceDatabaseType(null);
            column.setDdlTargetDatabaseType(target.getCode());
            columns.add(column);
        }
        fitMysqlRow(config,columns,warnings);
        return Map.of("columns", columns, "tableItems", items(columns), "warnings", warnings);
    }

    /** MySQL's row byte budget is shared by columns, not a per-column 65535-character allowance. */
    private static void fitMysqlRow(DataSourceConfig config,List<ColumnInfo> columns,List<Map<String,Object>> warnings) {
        if(!Set.of(DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL).contains(config.getEffectiveDatabaseType()))return;
        long rowBytes=columns.size()*32L;
        var candidates=new ArrayList<ColumnInfo>();
        for(var column:columns)if(Set.of(PortableColumnType.Kind.CHAR,PortableColumnType.Kind.VARCHAR).contains(PortableColumnType.kind(column.getColumnType()))) {
            long n=column.getLength()==null?0:column.getLength();rowBytes+=Math.min(n,65535)*4+2;
            if(!Boolean.TRUE.equals(column.getPrimaryKey())&&!Boolean.TRUE.equals(column.getUnique())&&!Boolean.TRUE.equals(column.getIndexed())
                    &&(column.getDefaultValue()==null||column.getDefaultValue().isBlank()))candidates.add(column);
        }
        candidates.sort(Comparator.comparingLong((ColumnInfo c)->c.getLength()==null?0:c.getLength()).reversed());
        for(var column:candidates) {
            if(rowBytes<=60000)break;
            rowBytes-=Math.min(column.getLength(),65535)*4+2;
            column.setColumnType("LONGTEXT");column.setDataType("LONGTEXT");column.setLength(0L);column.setCharacterLength(null);column.setByteLength(null);
            warnings.add(Map.of("field",column.getColumnName(),"message","目标行的多字节字符串容量超过MySQL行字节预算，已将此非键字段转为LONGTEXT","requiresEdit",false));
        }
        if(rowBytes>60000)warnings.add(Map.of("field","","message","目标行的键/索引字段超过MySQL保守行字节预算，请调整字段及索引设计","requiresEdit",true));
    }

    public String logicalType(String raw, String sourceCode) {
        var kind = PortableColumnType.kind(raw);
        // Registered snapshots remain editable even when an old source has no
        // protocol label. The optional origin only refines vendor semantics;
        // actual connection and imported-DDL parsing still use strict resolution.
        DatabaseType source = optionalSourceType(sourceCode);
        boolean oracleSource = source != null && DdlDialect.family(source) == DdlDialect.Family.ORACLE;
        if (oracleSource && PortableColumnType.base(raw).equals("FLOAT")) kind = PortableColumnType.Kind.DECIMAL;
        if (!oracleSource && PortableColumnType.base(raw).equals("LONG")) kind = PortableColumnType.Kind.BIGINT;
        if (source == DatabaseType.SQLSERVER && PortableColumnType.isSqlServerTimestamp(raw)) kind = PortableColumnType.Kind.BLOB;
        if (kind == PortableColumnType.Kind.DATE && oracleSource)
            kind = PortableColumnType.Kind.TIMESTAMP;
        return switch (kind) {
            case CHAR, VARCHAR, UUID -> "varchar";
            case TEXT, UNKNOWN -> "text";
            case SMALLINT, INTEGER -> "int";
            case BIGINT -> "bigint";
            case DECIMAL -> "decimal";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case BOOLEAN -> "tinyint";
            case DATE -> "date";
            case TIME, TIMESTAMP -> "datetime";
            case BINARY -> "varbinary";
            case BLOB -> "blob";
            case JSON -> "json";
        };
    }

    private static DatabaseType optionalSourceType(String code) {
        if (code == null || code.isBlank()) return null;
        try { return DatabaseType.fromCode(code); }
        catch (IllegalArgumentException unknown) { return null; }
    }

    /** Keep origin semantics such as UUID and time zones until the user selects another type family. */
    public String originTypeForEditor(String selected, String origin, String sourceCode) {
        if (origin == null || origin.isBlank() || PortableColumnType.kind(origin) == PortableColumnType.Kind.UNKNOWN) return null;
        return logicalType(origin,sourceCode).equalsIgnoreCase(selected) ? origin : null;
    }

    private List<Map<String,Object>> logicalItems(List<ColumnInfo> columns) {
        List<Map<String,Object>> items = items(columns);
        for (int index = 0; index < columns.size(); index++) {
            ColumnInfo column = columns.get(index);
            String type = logicalType(column.getColumnType(), column.getSourceDatabaseType());
            Map<String,Object> item = items.get(index);
            item.put("sourceColumnType", column.getColumnType()); item.put("sourceDataType", column.getDataType());
            item.put("dataType", type); item.put("columnType", type);
            if (PortableColumnType.kind(column.getColumnType()) == PortableColumnType.Kind.UUID) item.put("length", 36);
        }
        return items;
    }

    public boolean validName(String value) {
        try { DdlDialect.names(value); return true; } catch (IllegalArgumentException invalid) { return false; }
    }

    public List<Map<String,Object>> items(List<ColumnInfo> columns) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (ColumnInfo column : columns) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("columnName", column.getColumnName()); item.put("columnComment", column.getColumnComment());
            item.put("quotedIdentifier",Boolean.TRUE.equals(column.getQuotedIdentifier()));
            item.put("dataType", column.getDataType()); item.put("columnType", column.getColumnType());
            item.put("length", column.getLength() == null ? 0 : column.getLength()); item.put("precisionLength", column.getPrecision()); item.put("scale", column.getScale());
            item.put("lengthSemantics",column.getLengthSemantics());item.put("characterLength",column.getCharacterLength());item.put("byteLength",column.getByteLength());
            item.put("defaultValue", column.getDefaultValue()); item.put("primaryKey", Boolean.TRUE.equals(column.getPrimaryKey()) ? 1 : 0);
            item.put("isUnique", Boolean.TRUE.equals(column.getUnique()) ? 1 : 0); item.put("autoIncrement", Boolean.TRUE.equals(column.getAutoIncrement()) ? 1 : 0);
            item.put("nullable", Boolean.FALSE.equals(column.getNullable()) ? 0 : 1); item.put("sourceDatabaseType", column.getSourceDatabaseType());
            item.put("ordinalPosition", result.size()); result.add(item);
        }
        return result;
    }
}
