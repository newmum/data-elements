package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.io.Reader;
import java.sql.Connection;
import java.util.*;

/** Bounded, read-only native definition lookup for one already-authorized source object. */
final class NativeSourceDefinition {
    private NativeSourceDefinition() {}
    record Facts(String status,String message,SQLCreateTableStatement table) {}
    static Facts read(Connection connection,DatabaseType type,String owner,String table) {
        if(type!=DatabaseType.ORACLE&&type!=DatabaseType.OCEANBASE_ORACLE)
            return new Facts("unsupported","当前来源使用系统目录识别",null);
        try(var statement=connection.prepareStatement("SELECT DBMS_METADATA.GET_DDL('TABLE', ?, ?) AS TABLE_DDL FROM DUAL")) {
            statement.setQueryTimeout(15);statement.setMaxRows(1);statement.setString(1,table);statement.setString(2,owner);
            try(var result=statement.executeQuery()) {
                if(!result.next())return new Facts("unavailable","未取得来源DDL，继续按系统目录识别",null);
                String ddl;
                try(Reader reader=result.getCharacterStream(1)) {
                    if(reader==null)ddl=result.getString(1);
                    else {
                        StringBuilder text=new StringBuilder();char[] chunk=new char[8192];int size;
                        while((size=reader.read(chunk))!=-1) {
                            if(text.length()+size>2*1024*1024)return new Facts("too_large","来源DDL超过2MiB，未截断解析，继续按系统目录识别",null);
                            text.append(chunk,0,size);
                        }
                        ddl=text.toString();
                    }
                }
                if(ddl==null||ddl.isBlank())return new Facts("unavailable","来源DDL为空，继续按系统目录识别",null);
                if(ddl.length()>2*1024*1024)return new Facts("too_large","来源DDL超过2MiB，未截断解析，继续按系统目录识别",null);
                try {
                    var statements=PortableSqlParser.parse(ddl,type);
                    if(!statements.isEmpty()&&statements.getFirst() instanceof SQLCreateTableStatement create)
                        return new Facts("available","已读取来源原生DDL并与系统目录交叉识别",create);
                } catch(RuntimeException vendorSyntax) {
                    return new Facts("read_unparsed","已取得来源DDL；部分原生语法未自动解析，继续按系统目录识别",null);
                }
                return new Facts("read_unparsed","已取得来源DDL，未识别为普通表定义，继续按系统目录识别",null);
            }
        } catch(Exception unavailable) {
            return new Facts("unavailable","来源原生DDL读取未完成，请检查版本或对象权限；继续按系统目录识别",null);
        }
    }

    static List<Map<String,Object>> constraints(SQLCreateTableStatement table) {
        List<Map<String,Object>> facts=new ArrayList<>();
        if(table==null)return facts;
        for(var element:table.getTableElementList())if(element instanceof SQLUniqueConstraint key) {
            facts.add(Map.of("name",key.getName()==null?"":SQLUtils.normalize(key.getName().toString()),
                    "type",key instanceof SQLPrimaryKey?"P":"U","columns",key.getColumns().stream()
                            .map(c->SQLUtils.normalize(c.getExpr().toString())).toList()));
        }
        for(var column:table.getColumnDefinitions())for(var key:column.getConstraints()) {
            if(key instanceof SQLColumnPrimaryKey||key instanceof SQLColumnUniqueKey)
                facts.add(Map.of("name",key.getName()==null?"":SQLUtils.normalize(key.getName().toString()),
                        "type",key instanceof SQLColumnPrimaryKey?"P":"U","columns",List.of(SQLUtils.normalize(column.getColumnName()))));
        }
        return facts;
    }

    static Map<String,Object> partitions(SQLCreateTableStatement table) {
        if(table==null||table.getPartitioning()==null)return null;
        var partitioning=table.getPartitioning();
        if(partitioning.getSubPartitionBy()!=null||partitioning.getColumns().size()!=1
                ||!(partitioning instanceof SQLPartitionByRange||partitioning instanceof SQLPartitionByList))return null;
        String field=SQLUtils.normalize(partitioning.getColumns().getFirst().toString());
        var column=table.getColumnDefinitions().stream().filter(c->SQLUtils.normalize(c.getColumnName()).equalsIgnoreCase(field)).findFirst();
        if(column.isEmpty())return null;
        boolean range=partitioning instanceof SQLPartitionByRange;
        String interval=range&&((SQLPartitionByRange)partitioning).getInterval()!=null?((SQLPartitionByRange)partitioning).getInterval().toString():"";
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var partition:partitioning.getPartitions()) {
            if(rows.size()==TablePartitions.MAX_PARTITIONS)return null;
            if(partition.getValues()==null||partition.getValues().getItems().isEmpty())return null;
            String values=partition.getValues().getItems().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
            rows.add(Map.of("PARTITION_NAME",SQLUtils.normalize(partition.getName().toString()),"HIGH_VALUE",values));
        }
        return PartitionMetadataReader.analyze(range?"RANGE":"LIST","NONE",field,column.get().getDataType().getName(),rows,interval);
    }
}
