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
        List<Map<String,Object>> headers;
        try {headers=query(connection,"SELECT PARTITIONING_TYPE, SUBPARTITIONING_TYPE, INTERVAL FROM ALL_PART_TABLES WHERE OWNER=? AND TABLE_NAME=?",owner,name);}
        catch(SQLException oldCatalog){headers=query(connection,"SELECT PARTITIONING_TYPE, SUBPARTITIONING_TYPE FROM ALL_PART_TABLES WHERE OWNER=? AND TABLE_NAME=?",owner,name);}
        if(headers.isEmpty())return Map.of("status","none","message","来源不是分区表");
        String strategy=Objects.toString(headers.getFirst().get("PARTITIONING_TYPE"),"").toUpperCase(Locale.ROOT),sub=Objects.toString(headers.getFirst().get("SUBPARTITIONING_TYPE"),"NONE");
        if(!Set.of("LIST","RANGE").contains(strategy)||!sub.equalsIgnoreCase("NONE"))return Map.of("status","unsupported","partitioned",true,"message","来源采用"+strategy+"/"+sub+"分区，需手工确认目标方案");
        var keys=query(connection,"SELECT K.COLUMN_NAME, C.DATA_TYPE FROM ALL_PART_KEY_COLUMNS K JOIN ALL_TAB_COLUMNS C ON C.OWNER=K.OWNER AND C.TABLE_NAME=K.NAME AND C.COLUMN_NAME=K.COLUMN_NAME WHERE K.OWNER=? AND K.NAME=? AND K.OBJECT_TYPE='TABLE' ORDER BY K.COLUMN_POSITION",owner,name);
        if(keys.size()!=1)return Map.of("status","unsupported","partitioned",true,"message","来源为多列分区键，请手工确认目标方案");
        String field=Objects.toString(keys.getFirst().get("COLUMN_NAME")),type=Objects.toString(keys.getFirst().get("DATA_TYPE"));
        String kind=type.matches("(?i).*(DATE|TIME).*" )?"TIME":type.matches("(?i)(NUMBER|INT|DECIMAL|NUMERIC|FLOAT).*" )?"NUMBER":"STRING";
        var rows=query(connection,"SELECT PARTITION_NAME, HIGH_VALUE FROM ALL_TAB_PARTITIONS WHERE TABLE_OWNER=? AND TABLE_NAME=? ORDER BY PARTITION_POSITION",owner,name);
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
        String interval=Objects.toString(headers.getFirst().get("INTERVAL"),"").trim();
        if(!interval.isEmpty()) {
            var matcher=java.util.regex.Pattern.compile("(?i)NUMTO(YM|DS)INTERVAL\\s*\\(\\s*(\\d+)\\s*,\\s*'(MONTH|YEAR|DAY)'\\s*\\)").matcher(interval);
            if(matcher.matches())plan.put("sourceInterval",Map.of("step",Integer.parseInt(matcher.group(2)),"unit",matcher.group(3).toUpperCase(Locale.ROOT)));
            else {
                var literal=java.util.regex.Pattern.compile("(?i)INTERVAL\\s*'\\+?(\\d+)-(\\d+)'\\s+YEAR(?:\\s*\\(\\d+\\))?\\s+TO\\s+MONTH").matcher(interval);
                if(!literal.matches())return Map.of("status","unsupported","partitioned",true,"message","来源INTERVAL步长表达式需手工确认，未按普通RANGE降级");
                plan.put("sourceInterval",Map.of("step",Math.addExact(Math.multiplyExact(Integer.parseInt(literal.group(1)),12),Integer.parseInt(literal.group(2))),"unit","MONTH"));
            }
        }
        return Map.of("status","detected","partitioned",true,"message","已识别来源"+strategy+"分区，共"+partitions.size()+"个；保留真实历史边界"+(interval.isEmpty()?"":"；自动间隔在不支持原生INTERVAL的目标预建未来24个月并保留future兜底"),"plan",plan);
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
