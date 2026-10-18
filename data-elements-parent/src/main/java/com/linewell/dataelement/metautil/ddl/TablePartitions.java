package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** One validated partition contract for preview, execution and the Hive writer. No database reads. */
@Component
@MagicModule("tablePartitions")
public class TablePartitions {
    public static final int MAX_PARTITIONS = 2000;
    public boolean supported(DatabaseType type) {
        return Set.of("oracle","oceanbaseoracle","dameng","hive","mysql","mariadb","oceanbasemysql").contains(type.getCode().toLowerCase(Locale.ROOT));
    }
    private static String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private static boolean yes(Object value) { return "true".equalsIgnoreCase(text(value)); }
    private static String id(Object value) {
        String name=text(value);
        if(!name.matches("[A-Za-z_][A-Za-z0-9_$]*")) throw new IllegalArgumentException("分区字段或名称无效："+name);
        return name;
    }
    private static String quote(String name,DatabaseType type) { return DdlDialect.quote(type,name); }
    private static String literal(Object value,String kind,DatabaseType type) {
        if(value==null)return "NULL";
        String s=text(value);
        if(kind.equals("NUMBER")) {
            try { return new java.math.BigDecimal(s).toPlainString(); }
            catch(NumberFormatException bad){throw new IllegalArgumentException("分区边界不是有效数字："+s);}
        }
        if(kind.equals("TIME")) {
            s=timestamp(s);
            if(DdlDialect.family(type)==DdlDialect.Family.ORACLE)return "TO_DATE('"+s+"', 'YYYY-MM-DD HH24:MI:SS')";
        }
        if(DdlDialect.family(type)==DdlDialect.Family.MYSQL&&s.contains("\\"))throw new IllegalArgumentException("MySQL分区枚举含反斜杠，SQL_MODE含义不明确，请调整分区规则");
        return "'"+s.replace("'","''")+"'";
    }
    static String timestamp(String value) {
        String s=value.trim().replace('T',' ');
        if(s.length()==10)s+=" 00:00:00";
        try { return java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").format(LocalDateTime.parse(s,java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT))); }
        catch(RuntimeException bad){throw new IllegalArgumentException("分区时间边界必须是有效的 yyyy-MM-dd HH:mm:ss："+value);}
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> normalize(Map<String,Object> request,List<ColumnInfo> columns,DatabaseType type) {
        if(request==null||!yes(request.get("enabled")))return new LinkedHashMap<>(Map.of("enabled",false));
        if(!supported(type))throw new IllegalArgumentException("当前目标数据库不支持自动分区建表");
        String field=id(request.get("field"));
        ColumnInfo key=columns.stream().filter(c->c.getColumnName().equalsIgnoreCase(field)).findFirst().orElseThrow(()->new IllegalArgumentException("分区字段不在目标字段中："+field));
        String raw=text(key.getColumnType()).isEmpty()?text(key.getDataType()):text(key.getColumnType());
        String kind=raw.toUpperCase(Locale.ROOT).matches(".*(DATE|TIME).*" )?"TIME":raw.toUpperCase(Locale.ROOT).matches("^(NUMBER|NUMERIC|DECIMAL|INT|BIGINT|SMALLINT|TINYINT|FLOAT|DOUBLE|REAL).*" )?"NUMBER":"STRING";
        String strategy=text(request.get("strategy")).toUpperCase(Locale.ROOT);
        List<Map<String,Object>> parts=new ArrayList<>();
        if(strategy.equals("MONTH")) {
            if(!kind.equals("TIME"))throw new IllegalArgumentException("按月分区请选择目标 DATE/TIMESTAMP/DATETIME 字段");
            YearMonth start;
            int months;
            try {start=YearMonth.parse(text(request.get("startMonth")));months=Integer.parseInt(text(request.get("months")));}
            catch(RuntimeException bad){throw new IllegalArgumentException("请填写起始月份和分区月数");}
            if(months<1||months>240)throw new IllegalArgumentException("一次按月创建范围为1至240个月");
            parts.add(part("p_before",start.atDay(1)+" 00:00:00","before"));
            for(int i=0;i<months;i++) {
                YearMonth month=start.plusMonths(i);
                parts.add(part("p_"+month.toString().replace("-",""),month.plusMonths(1).atDay(1)+" 00:00:00",month.toString().replace("-","")));
            }
            parts.add(part("p_future","MAXVALUE","future"));
            strategy="RANGE";
        } else {
            Object rows=request.get("partitions");
            if(!(rows instanceof List<?> list)||list.isEmpty()||list.size()>MAX_PARTITIONS)throw new IllegalArgumentException("请配置1至"+MAX_PARTITIONS+"个分区");
            for(Object row:list) {
                if(!(row instanceof Map<?,?> map))throw new IllegalArgumentException("分区定义无效");
                Map<String,Object> copy=new LinkedHashMap<>();
                copy.put("name",id(map.get("name")));
                String bucket=text(map.get("bucket")==null?map.get("name"):map.get("bucket"));
                if(!bucket.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Hive分桶值只能包含字母、数字、下划线及短横线");
                copy.put("bucket",bucket);
                // Hive path components may start with digits, so use generated names for generic LIST buckets.
                if(map.get("values") instanceof List<?> values)copy.put("values",new ArrayList<>(values));
                copy.put("upper",text(map.get("upper")));copy.put("default",yes(map.get("default")));
                parts.add(copy);
            }
        }
        if(!Set.of("LIST","RANGE").contains(strategy))throw new IllegalArgumentException("当前支持区域/枚举LIST和时间/数值RANGE分区；组合或HASH分区请手工确认");
        Set<String> names=new HashSet<>(),buckets=new HashSet<>(),valuesSeen=new HashSet<>();
        String previous=null;
        boolean terminal=false;
        for(var p:parts) {
            if(!names.add(text(p.get("name")).toLowerCase(Locale.ROOT))||!buckets.add(text(p.get("bucket"))))throw new IllegalArgumentException("分区名称或Hive分桶值重复");
            if(terminal)throw new IllegalArgumentException("DEFAULT/MAXVALUE必须位于最后一个分区");
            if(strategy.equals("LIST")) {
                if(yes(p.get("default"))){terminal=true;continue;}
                if(!(p.get("values") instanceof List<?> list)||list.isEmpty())throw new IllegalArgumentException("LIST分区必须填写枚举值");
                for(Object v:list){String canonical=v==null?"<NULL>":literal(v,kind,type);if(!valuesSeen.add(canonical))throw new IllegalArgumentException("LIST分区值重复："+canonical);}
            } else {
                String upper=text(p.get("upper"));
                if(upper.equalsIgnoreCase("MAXVALUE")){p.put("upper","MAXVALUE");terminal=true;continue;}
                String canonical=kind.equals("TIME")?timestamp(upper):kind.equals("NUMBER")?new java.math.BigDecimal(upper).toPlainString():upper;
                if(previous!=null&&(kind.equals("NUMBER")?new java.math.BigDecimal(canonical).compareTo(new java.math.BigDecimal(previous)):canonical.compareTo(previous))<=0)throw new IllegalArgumentException("RANGE分区上界必须严格递增");
                p.put("upper",canonical);previous=canonical;
            }
        }
        if(type==DatabaseType.MYSQL||type==DatabaseType.MARIADB||type==DatabaseType.OCEANBASE_MYSQL) {
            if(strategy.equals("LIST")&&parts.stream().anyMatch(p->yes(p.get("default")))&&(type==DatabaseType.MYSQL||type==DatabaseType.MARIADB))throw new IllegalArgumentException("该MySQL目标不支持LIST DEFAULT分区，请调整枚举或异常处理");
        }
        // Physical key/index restrictions depend on the server version and user-edited DDL.
        // Render the partition clause without changing keys; the database decides whether it can execute it.
        Map<String,Object> plan=new LinkedHashMap<>();plan.put("version",1);plan.put("enabled",true);plan.put("field",key.getColumnName());plan.put("kind",kind);plan.put("strategy",strategy);plan.put("partitions",parts);plan.put("hiveColumn","ods_partition");
        String timezone=text(request.get("timezone"));if(timezone.isEmpty())timezone="Asia/Shanghai";
        ZoneId.of(timezone);plan.put("timezone",timezone);
        plan.put("dateOnly",raw.equalsIgnoreCase("DATE")&&type==DatabaseType.HIVE);
        if(!text(request.get("strategy")).equalsIgnoreCase("MONTH")&&request.get("sourceInterval") instanceof Map<?,?> interval) {
            String unit=text(interval.get("unit")).toUpperCase(Locale.ROOT);int step=Integer.parseInt(text(interval.get("step")));
            if(!kind.equals("TIME")||!strategy.equals("RANGE")||!Set.of("MONTH","YEAR","DAY").contains(unit)||step<1||step>120)throw new IllegalArgumentException("来源自动分区间隔不受支持，请手工配置");
            plan.put("sourceInterval",Map.of("unit",unit,"step",step));
            if(type==DatabaseType.ORACLE) {
                if(parts.stream().anyMatch(p->text(p.get("upper")).equals("MAXVALUE")))throw new IllegalArgumentException("Oracle原生INTERVAL不能与MAXVALUE分区混用");
                plan.put("nativeInterval",Map.of("unit",unit,"step",step));
            }
            else if(!text(parts.getLast().get("upper")).equals("MAXVALUE")) {
                LocalDateTime upper=LocalDateTime.parse(text(parts.getLast().get("upper")).replace(' ','T'));
                LocalDateTime horizon=YearMonth.now(ZoneId.of(timezone)).plusMonths(24).atDay(1).atStartOfDay();
                int generated=0;
                while(upper.isBefore(horizon)) {
                    upper=switch(unit){case "DAY"->upper.plusDays(step);case "YEAR"->upper.plusYears(step);default->upper.plusMonths(step);};
                    if(parts.size()>=MAX_PARTITIONS-1)throw new IllegalArgumentException("自动间隔展开超过分区上限，请调整目标时间粒度");
                    String name="p_auto_"+(++generated);
                    parts.add(part(name,upper.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")),name));
                }
                parts.add(part("p_future","MAXVALUE","future"));
            }
        }
        if(type==DatabaseType.HIVE&&columns.stream().anyMatch(c->c.getColumnName().equalsIgnoreCase("ods_partition")))throw new IllegalArgumentException("ods_partition已被业务字段占用，请先调整分区列命名");
        return plan;
    }
    private static Map<String,Object> part(String name,String upper,String bucket){Map<String,Object> p=new LinkedHashMap<>();p.put("name",name);p.put("upper",upper);p.put("bucket",bucket);return p;}

    @SuppressWarnings("unchecked")
    public String apply(String ddl,Map<String,Object> plan,DataSourceConfig config) {
        if(plan==null||!yes(plan.get("enabled")))return ddl;
        DatabaseType type=config.getDatabaseType();
        var statements=PortableSqlParser.parse(ddl,type);
        if(!(statements.getFirst() instanceof SQLCreateTableStatement create))throw new IllegalArgumentException("分区仅支持CREATE TABLE");
        String table=create.getName().toString();
        String field=create.getColumnDefinitions().stream().filter(c->SQLUtils.normalize(c.getColumnName()).equalsIgnoreCase(text(plan.get("field"))))
                .findFirst().orElseThrow(()->new IllegalArgumentException("DDL缺少分区字段")).getColumnName();
        String strategy=text(plan.get("strategy")),kind=text(plan.get("kind"));
        List<Map<String,Object>> parts=(List<Map<String,Object>>)plan.get("partitions");
        StringBuilder clause=new StringBuilder();
        if(type==DatabaseType.HIVE) {
            clause.append("\nPARTITIONED BY (`ods_partition` STRING COMMENT '物化分区')");
        } else {
            boolean mysql=Set.of(DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL).contains(type);
            clause.append("\nPARTITION BY ").append(strategy).append(mysql?" COLUMNS (":" (").append(field).append(")");
            if(plan.get("nativeInterval") instanceof Map<?,?> interval)clause.append(" INTERVAL (").append(text(interval.get("unit")).equals("DAY")?"NUMTODSINTERVAL":"NUMTOYMINTERVAL").append('(').append(interval.get("step")).append(", '").append(interval.get("unit")).append("'))");
            clause.append(" (\n");
            for(int i=0;i<parts.size();i++) {
                var p=parts.get(i);if(i>0)clause.append(",\n");
                clause.append("  PARTITION ").append(quote(text(p.get("name")),type));
                if(strategy.equals("LIST")) {
                    clause.append(mysql?" VALUES IN (":" VALUES (");
                    if(yes(p.get("default")))clause.append("DEFAULT");
                    else clause.append(((List<?>)p.get("values")).stream().map(v->literal(v,kind,type)).collect(java.util.stream.Collectors.joining(", ")));
                } else clause.append(" VALUES LESS THAN (").append(text(p.get("upper")).equals("MAXVALUE")?"MAXVALUE":literal(p.get("upper"),kind,type));
                clause.append(")");
            }
            clause.append("\n)");
        }
        String sql=SQLUtils.toSQLString(create,DdlDialect.parser(type)).stripTrailing().replaceFirst(";\\s*$","");
        // Hive partition clauses precede ROW FORMAT and storage declarations.
        int insert=sql.length();
        if(type==DatabaseType.HIVE){var matcher=java.util.regex.Pattern.compile("(?im)^\\s*(ROW FORMAT|STORED AS|LOCATION|TBLPROPERTIES)\\b").matcher(sql);if(matcher.find())insert=matcher.start();}
        StringBuilder out=new StringBuilder(sql.substring(0,insert)).append(clause).append("\n").append(sql.substring(insert)).append(";\n");
        for(int i=1;i<statements.size();i++)out.append(SQLUtils.toSQLString(statements.get(i),DdlDialect.parser(type)).replaceFirst(";\\s*$","")).append(";\n");
        if(type==DatabaseType.HIVE)for(var p:parts)out.append("ALTER TABLE ").append(table).append(" ADD IF NOT EXISTS PARTITION (`ods_partition`='").append(p.get("bucket")).append("');\n");
        return out.toString();
    }
    /** Only the exact server-rendered ancillary partition statements may accompany a CREATE. */
    public String validate(String ddl,Map<String,Object> plan,DataSourceConfig config) {
        var statements=PortableSqlParser.parse(ddl,config.getDatabaseType());
        if(plan==null||!yes(plan.get("enabled"))) {
            if(statements.stream().anyMatch(s->s instanceof SQLCreateTableStatement c&&(c.getPartitioning()!=null||!c.getPartitionColumns().isEmpty())))throw new IllegalArgumentException("DDL包含分区定义，请勾选创建分区并确认配置");
            return ddl;
        }
        if(!(statements.getFirst() instanceof SQLCreateTableStatement create))throw new IllegalArgumentException("分区DDL必须以CREATE TABLE开始");
        List<ColumnInfo> columns=new ArrayList<>();
        var primary=new HashSet<String>();create.getPrimaryKeyNames().forEach(k->primary.add(SQLUtils.normalize(k).toLowerCase(Locale.ROOT)));
        for(var c:create.getColumnDefinitions()){
            var col=new ColumnInfo();col.setColumnName(SQLUtils.normalize(c.getColumnName()));col.setColumnType(c.getDataType().toString());
            col.setPrimaryKey(c.isPrimaryKey()||primary.contains(col.getColumnName().toLowerCase(Locale.ROOT)));
            col.setUnique(c.getConstraints().stream().anyMatch(SQLColumnUniqueKey.class::isInstance));columns.add(col);
        }
        var normalized=normalize(plan,columns,config.getDatabaseType());
        // Druid's Hive clone omits row-format delimiters; reparse to preserve the storage contract.
        var base=(SQLCreateTableStatement)PortableSqlParser.parse(SQLUtils.toSQLString(create,DdlDialect.parser(config.getDatabaseType())),config.getDatabaseType()).getFirst();
        base.setPartitioning(null);base.getPartitionColumns().clear();
        String baseSql=SQLUtils.toSQLString(base,DdlDialect.parser(config.getDatabaseType()))+";";
        String expected=apply(baseSql,normalized,config);
        var expectedStatements=PortableSqlParser.parse(expected,config.getDatabaseType());
        List<String> actual=statements.stream().filter(s->!(s instanceof SQLCommentStatement)).map(s->SQLUtils.toSQLString(s,DdlDialect.parser(config.getDatabaseType()))).toList();
        List<String> rendered=expectedStatements.stream().map(s->SQLUtils.toSQLString(s,DdlDialect.parser(config.getDatabaseType()))).toList();
        if(!actual.equals(rendered))throw new IllegalArgumentException("建表语句中的分区定义与创建分区配置不一致，请重新生成DDL");
        plan.clear();plan.putAll(normalized);
        // CanvasTableDdl still checks the base CREATE and all COMMENT targets; ancillary SQL is generated above.
        StringBuilder safe=new StringBuilder(baseSql);
        statements.stream().filter(SQLCommentStatement.class::isInstance).forEach(s->safe.append(SQLUtils.toSQLString(s,DdlDialect.parser(config.getDatabaseType()))).append(';'));
        return safe.toString();
    }

    public void validatePrimaryKeys(String ddl,List<Map<String,Object>> items,DataSourceConfig config) {
        if(items==null)throw new IllegalArgumentException("创建分区需要目标字段契约，请重新生成DDL");
        var create=(SQLCreateTableStatement)PortableSqlParser.parse(ddl,config.getDatabaseType()).getFirst();
        Set<String> actual=new HashSet<>(),expected=new HashSet<>();
        create.getPrimaryKeyNames().forEach(k->actual.add(SQLUtils.normalize(k).toLowerCase(Locale.ROOT)));
        create.getColumnDefinitions().stream().filter(SQLColumnDefinition::isPrimaryKey).forEach(c->actual.add(SQLUtils.normalize(c.getColumnName()).toLowerCase(Locale.ROOT)));
        for(var item:items)if("1".equals(text(item.get("primaryKey")))||yes(item.get("primaryKey")))expected.add(text(item.get("columnName")).toLowerCase(Locale.ROOT));
        if(!actual.equals(expected))throw new IllegalArgumentException("分区DDL主键与字段配置不一致，请在物化建表字段配置中明确调整主键后重新生成，不能只修改SQL而保留旧写入键");
    }

    @SuppressWarnings("unchecked")
    public void compatible(Map<String,Object> expected,Map<String,Object> detected) {
        if(expected==null||!yes(expected.get("enabled"))) {
            if("detected".equals(text(detected.get("status"))))throw new IllegalArgumentException("已有表是分区表，请勾选创建分区并采用兼容的写入配置");
            return;
        }
        if(!"detected".equals(text(detected.get("status"))))throw new IllegalArgumentException("无法核验已有目标表的分区结构，不会按新建模板假定兼容");
        var actual=(Map<String,Object>)detected.get("plan");
        if(!text(expected.get("field")).equalsIgnoreCase(text(actual.get("field")))||!text(expected.get("strategy")).equals(text(actual.get("strategy"))))throw new IllegalArgumentException("已有目标表的分区字段或方式与本次配置不一致");
        var wanted=(List<Map<String,Object>>)expected.get("partitions");var found=(List<Map<String,Object>>)actual.get("partitions");
        if(wanted.size()!=found.size())throw new IllegalArgumentException("已有目标表的分区数量与本次配置不一致");
        for(int i=0;i<wanted.size();i++) {
            var a=wanted.get(i);var b=found.get(i);
            if(!text(a.get("upper")).equals(text(b.get("upper")))||yes(a.get("default"))!=yes(b.get("default"))
                    ||!Objects.equals(a.get("values"),b.get("values")))throw new IllegalArgumentException("已有目标表的分区边界或枚举值与本次配置不一致");
        }
    }
}
