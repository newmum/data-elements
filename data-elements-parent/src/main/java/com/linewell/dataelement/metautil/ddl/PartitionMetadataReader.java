package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import java.sql.*;
import java.util.*;

/** Catalog facts for one explicitly opened source table. Never scans source records. */
public final class PartitionMetadataReader {
    private PartitionMetadataReader() {}
    public static Map<String,Object> read(Connection connection,DataSourceConfig config,String tableName) throws Exception {
        var family=DdlDialect.family(config.getDatabaseType());
        var names=DdlDialect.names(tableName);var table=names.getLast();
        String owner=names.size()>1?names.get(names.size()-2).value():DdlDialect.scope(config),name=table.value();
        if(!table.quoted()&&family==DdlDialect.Family.ORACLE)name=name.toUpperCase(Locale.ROOT);
        if(family!=DdlDialect.Family.ORACLE)return Map.of("status","unsupported","message","当前来源尚无可自动继承的分区目录适配，可手工配置目标分区");
        var definition=NativeSourceDefinition.read(connection,config.getDatabaseType(),owner,name);
        Map<String,Object> detected;
        try {detected=new LinkedHashMap<>(readCatalog(connection,owner,name));}
        catch(Exception unavailable) {detected=new LinkedHashMap<>(Map.of("status","unknown","message","来源分区目录读取未完成，可手工配置"));}
        if(!"detected".equals(detected.get("status"))&&definition.table()!=null) {
            try {
                var nativePlan=NativeSourceDefinition.partitions(definition.table());
                if(nativePlan!=null&&"detected".equals(nativePlan.get("status"))) {
                    detected=new LinkedHashMap<>(nativePlan);detected.put("detectedFrom","native_ddl");
                }
            } catch(RuntimeException expression){/* Unknown expressions remain editable; never execute them. */}
        }
        if("detected".equals(detected.get("status"))&&definition.table()!=null) {
            try {
                var nativePlan=NativeSourceDefinition.partitions(definition.table());
                if(nativePlan!=null&&"detected".equals(nativePlan.get("status"))) {
                    boolean matches=sameBoundaries((Map<?,?>)detected.get("plan"),(Map<?,?>)nativePlan.get("plan"));
                    detected.put("ddlPartitionMatch",matches);
                    if(!matches) {
                        detected.put("status","unknown");
                        detected.put("message","来源DDL与系统目录的分区信息不一致，暂不自动勾选，可手工配置或编辑DDL");
                    }
                }
            } catch(RuntimeException unsupported){/* Native vendor expressions can still use catalog facts. */}
        }
        detected.putIfAbsent("detectedFrom","catalog");
        detected.put("ddlReadStatus",definition.status());detected.put("ddlReadMessage",definition.message());
        List<Map<String,Object>> constraints;
        try {constraints=readConstraints(connection,owner,name);detected.put("constraintsFrom","catalog");}
        catch(Exception unavailable) {constraints=NativeSourceDefinition.constraints(definition.table());detected.put("constraintsFrom",definition.table()==null?"unavailable":"native_ddl");}
        detected.put("constraints",constraints);
        return detected;
    }
    static boolean sameBoundaries(Map<?,?> first,Map<?,?> second) {
        if(!Objects.toString(first.get("field")).equalsIgnoreCase(Objects.toString(second.get("field")))
                ||!Objects.equals(first.get("strategy"),second.get("strategy")))return false;
        var left=(List<?>)first.get("partitions");var right=(List<?>)second.get("partitions");
        if(left.size()!=right.size())return false;
        for(int i=0;i<left.size();i++) {
            var a=(Map<?,?>)left.get(i);var b=(Map<?,?>)right.get(i);
            if(!Objects.equals(a.get("upper"),b.get("upper"))||!Objects.equals(a.get("values"),b.get("values"))
                    ||Boolean.TRUE.equals(a.get("default"))!=Boolean.TRUE.equals(b.get("default")))return false;
        }
        return true;
    }
    private static Map<String,Object> readCatalog(Connection connection,String owner,String name) throws Exception {
        List<Map<String,Object>> headers;
        try {headers=query(connection,"SELECT PARTITIONING_TYPE, SUBPARTITIONING_TYPE, INTERVAL FROM ALL_PART_TABLES WHERE OWNER=? AND TABLE_NAME=?",owner,name);}
        catch(SQLException oldCatalog){headers=query(connection,"SELECT PARTITIONING_TYPE, SUBPARTITIONING_TYPE FROM ALL_PART_TABLES WHERE OWNER=? AND TABLE_NAME=?",owner,name);}
        if(headers.isEmpty())return Map.of("status","none","message","来源不是分区表");
        String strategy=Objects.toString(headers.getFirst().get("PARTITIONING_TYPE"),"").toUpperCase(Locale.ROOT),sub=Objects.toString(headers.getFirst().get("SUBPARTITIONING_TYPE"),"NONE");
        if(!Set.of("LIST","RANGE").contains(strategy)||!sub.equalsIgnoreCase("NONE"))return Map.of("status","unsupported","partitioned",true,"message","来源采用"+strategy+"/"+sub+"分区，需手工确认目标方案");
        var keys=query(connection,"SELECT K.COLUMN_NAME, C.DATA_TYPE FROM ALL_PART_KEY_COLUMNS K JOIN ALL_TAB_COLUMNS C ON C.OWNER=K.OWNER AND C.TABLE_NAME=K.NAME AND C.COLUMN_NAME=K.COLUMN_NAME WHERE K.OWNER=? AND K.NAME=? AND K.OBJECT_TYPE='TABLE' ORDER BY K.COLUMN_POSITION",owner,name);
        if(keys.size()!=1)return Map.of("status","unsupported","partitioned",true,"message","来源为多列分区键，请手工确认目标方案");
        String field=Objects.toString(keys.getFirst().get("COLUMN_NAME")),type=Objects.toString(keys.getFirst().get("DATA_TYPE"));
        var rows=query(connection,"SELECT PARTITION_NAME, HIGH_VALUE FROM ALL_TAB_PARTITIONS WHERE TABLE_OWNER=? AND TABLE_NAME=? ORDER BY PARTITION_POSITION",owner,name);
        return analyze(strategy,sub,field,type,rows,Objects.toString(headers.getFirst().get("INTERVAL"),"").trim());
    }
    static Map<String,Object> analyze(String strategy,String sub,String field,String type,List<Map<String,Object>> rows,String interval) {
        if(!Set.of("LIST","RANGE").contains(strategy)||!sub.equalsIgnoreCase("NONE"))return Map.of("status","unsupported","partitioned",true,"message","来源分区方式需手工确认");
        String kind=type.matches("(?i).*(DATE|TIME).*" )?"TIME":type.matches("(?i)(NUMBER|INT|DECIMAL|NUMERIC|FLOAT).*" )?"NUMBER":"STRING";
        if(rows.isEmpty())throw new IllegalArgumentException("来源分区目录为空，不能当作完整分区方案");
        List<Map<String,Object>> partitions=new ArrayList<>();
        for(var row:rows) {
            String raw=Objects.toString(row.get("HIGH_VALUE"),"").trim(),generated="p_"+String.format(Locale.ROOT,"%04d",partitions.size()+1);
            Map<String,Object> p=new LinkedHashMap<>();p.put("name",generated);p.put("bucket",generated);p.put("sourceName",row.get("PARTITION_NAME"));
            if(strategy.equals("LIST")) {
                if(raw.equalsIgnoreCase("DEFAULT"))p.put("default",true);
                else {
                    var expr=SQLUtils.toSQLExpr("x IN ("+raw+")",DbType.oracle);
                    if(!(expr instanceof SQLInListExpr in))throw new IllegalArgumentException("来源LIST边界无法识别");
                    List<Object> values=new ArrayList<>();for(SQLExpr v:in.getTargetList())values.add(value(v,kind));p.put("values",values);
                    if(values.size()==1&&values.getFirst()!=null&&values.getFirst().toString().matches("[A-Za-z0-9_-]{1,64}"))p.put("bucket",values.getFirst().toString());
                }
            } else p.put("upper",raw.equalsIgnoreCase("MAXVALUE")?"MAXVALUE":value(SQLUtils.toSQLExpr(raw,DbType.oracle),kind));
            partitions.add(p);
        }
        Map<String,Object> plan=new LinkedHashMap<>(Map.of("enabled",true,"field",field,"kind",kind,"strategy",strategy,"partitions",partitions));
        if(!interval.isEmpty()) {
            var matcher=java.util.regex.Pattern.compile("(?i)NUMTO(YM|DS)INTERVAL\\s*\\(\\s*(\\d+)\\s*,\\s*'(MONTH|YEAR|DAY)'\\s*\\)").matcher(interval);
            if(matcher.matches())plan.put("sourceInterval",Map.of("step",Integer.parseInt(matcher.group(2)),"unit",matcher.group(3).toUpperCase(Locale.ROOT)));
            else {
                var literal=java.util.regex.Pattern.compile("(?i)INTERVAL\\s*'\\+?(\\d+)-(\\d+)'\\s+YEAR(?:\\s*\\(\\d+\\))?\\s+TO\\s+MONTH").matcher(interval);
                if(!literal.matches())return Map.of("status","unsupported","partitioned",true,"message","来源INTERVAL步长表达式需手工确认，未按普通RANGE降级");
                plan.put("sourceInterval",Map.of("step",Math.addExact(Math.multiplyExact(Integer.parseInt(literal.group(1)),12),Integer.parseInt(literal.group(2))),"unit","MONTH"));
            }
        }
        String display=displayStrategy(plan);plan.put("displayStrategy",display);plan.put("sourceBoundaries",true);
        String label=switch(display){case "YEAR"->"按年时间";case "MONTH"->"按月时间";case "TIME"->"时间（不规则边界）";case "REGION"->"区域";case "LIST"->"枚举";default->"数值／其他范围";};
        return Map.of("status","detected","partitioned",true,"message","已识别来源"+label+"分区，共"+partitions.size()+"个；保留真实历史边界"+(interval.isEmpty()?"":"；自动间隔在不支持原生INTERVAL的目标预建未来24个月并保留future兜底"),"plan",plan);
    }
    private static String displayStrategy(Map<String,Object> plan) {
        if("LIST".equals(plan.get("strategy"))) {
            var values=((List<?>)plan.get("partitions")).stream().map(p->((Map<?,?>)p).get("values"))
                    .filter(List.class::isInstance).map(List.class::cast).flatMap(List::stream).map(value->Objects.toString(value,"<NULL>")).toList();
            return !values.isEmpty()&&values.stream().allMatch(TablePartitions.REGION_CODES::contains)?"REGION":"LIST";
        }
        if(!"TIME".equals(plan.get("kind")))return "RANGE";
        if(plan.get("sourceInterval") instanceof Map<?,?> interval) {
            int step=Integer.parseInt(interval.get("step").toString());String unit=interval.get("unit").toString();
            if(unit.equals("YEAR")||(unit.equals("MONTH")&&step==12))return "YEAR";
            if(unit.equals("MONTH")&&step==1)return "MONTH";
        }
        List<java.time.LocalDateTime> bounds=new ArrayList<>();
        for(Object item:(List<?>)plan.get("partitions")) {
            String value=Objects.toString(((Map<?,?>)item).get("upper"),"");
            if(!value.equals("MAXVALUE"))bounds.add(java.time.LocalDateTime.parse(value.replace(' ','T')));
        }
        if(bounds.size()<2)return "TIME";
        boolean years=true,months=true;
        for(int i=1;i<bounds.size();i++) {
            years&=bounds.get(i-1).plusYears(1).equals(bounds.get(i));months&=bounds.get(i-1).plusMonths(1).equals(bounds.get(i));
        }
        return years?"YEAR":months?"MONTH":"TIME";
    }
    private static List<Map<String,Object>> readConstraints(Connection connection,String owner,String table) throws Exception {
        var rows=query(connection,"SELECT C.CONSTRAINT_NAME, C.CONSTRAINT_TYPE, K.COLUMN_NAME FROM ALL_CONSTRAINTS C JOIN ALL_CONS_COLUMNS K ON C.OWNER=K.OWNER AND C.CONSTRAINT_NAME=K.CONSTRAINT_NAME AND C.TABLE_NAME=K.TABLE_NAME WHERE C.OWNER=? AND C.TABLE_NAME=? AND C.CONSTRAINT_TYPE IN ('P','U') ORDER BY C.CONSTRAINT_NAME,K.POSITION",owner,table);
        Map<String,Map<String,Object>> facts=new LinkedHashMap<>();
        for(var row:rows) {
            String name=Objects.toString(row.get("CONSTRAINT_NAME")),type=Objects.toString(row.get("CONSTRAINT_TYPE"));
            if(!Set.of("P","U").contains(type))continue;
            var fact=facts.computeIfAbsent(name,n->new LinkedHashMap<>(Map.of("name",n,"type",type,"columns",new ArrayList<String>())));
            @SuppressWarnings("unchecked") var columns=(List<String>)fact.get("columns");columns.add(Objects.toString(row.get("COLUMN_NAME")));
        }
        return new ArrayList<>(facts.values());
    }
    private static Object value(SQLExpr expression,String kind) {
        if(expression instanceof SQLNullExpr)return null;
        if(expression instanceof SQLNumericLiteralExpr number)return number.getNumber().toString();
        if(expression instanceof SQLCharExpr string)return kind.equals("TIME")?TablePartitions.timestamp(string.getText()):string.getText();
        if(expression instanceof SQLDateExpr date)return TablePartitions.timestamp(date.getLiteral());
        if(expression instanceof SQLTimestampExpr time)return TablePartitions.timestamp(time.getLiteral());
        if(expression instanceof SQLMethodInvokeExpr method&&Set.of("TO_DATE","TO_TIMESTAMP").contains(method.getMethodName().toUpperCase(Locale.ROOT))
                &&method.getArguments().size()>=2&&method.getArguments().getFirst() instanceof SQLCharExpr date
                &&method.getArguments().get(1) instanceof SQLCharExpr format
                &&Set.of("YYYY-MM-DD","SYYYY-MM-DD","YYYY-MM-DD HH24:MI:SS","SYYYY-MM-DD HH24:MI:SS").contains(format.getText().toUpperCase(Locale.ROOT))
                &&(method.getArguments().size()==2||(method.getArguments().size()==3&&method.getArguments().get(2) instanceof SQLCharExpr nls&&nls.getText().equalsIgnoreCase("NLS_CALENDAR=GREGORIAN"))))return TablePartitions.timestamp(date.getText());
        throw new IllegalArgumentException("来源分区含不能安全转换的表达式，请手工配置，不会执行边界表达式");
    }
    private static List<Map<String,Object>> query(Connection connection,String sql,String owner,String table) throws Exception {
        try(var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(15);statement.setMaxRows(TablePartitions.MAX_PARTITIONS+1);statement.setString(1,owner);statement.setString(2,table);
            try(var rs=statement.executeQuery()) {
                List<Map<String,Object>> rows=new ArrayList<>();var metadata=rs.getMetaData();
                while(rs.next()){if(rows.size()==TablePartitions.MAX_PARTITIONS)throw new IllegalArgumentException("来源分区超过"+TablePartitions.MAX_PARTITIONS+"个，请手工规划，系统不会截断后自动建表");Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=metadata.getColumnCount();i++)row.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT),rs.getString(i));rows.add(row);}
                return rows;
            }
        }
    }
}
