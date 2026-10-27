package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * GaussDB 元数据探查器
 *
 * GaussDB（openGauss / GaussDB(for PostgreSQL)）通常兼容 PostgreSQL 协议与系统视图，
 * 因此直接复用 PostgreSQL 的探查实现，并在数据库类型/版本展示上做适配。
 */
@Slf4j
public class GaussDbMetadataExplorer extends PostgreSqlMetadataExplorer {

    @Override
    protected String resolveMetadataSchema(DataSourceConfig config) {
        if(config.getSchema()!=null&&!config.getSchema().isBlank())return config.getSchema();
        if(config.getResolvedSchema()!=null&&!config.getResolvedSchema().isBlank())return config.getResolvedSchema();
        try(Connection connection=getConnection(config)) {
            String schema=null;
            try {schema=connection.getSchema();} catch(java.sql.SQLFeatureNotSupportedException ignored) { }
            if(schema==null||schema.isBlank())try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT current_schema()")) {
                if(rows.next())schema=rows.getString(1);
            }
            if(schema==null||schema.isBlank())throw new IllegalStateException("当前 GaussDB 会话没有可用的默认 Schema，请显式填写");
            config.setResolvedSchema(schema);
            return schema;
        } catch(Exception failure){throw new IllegalStateException("无法解析 GaussDB 默认 Schema，请检查连接权限或显式填写",failure);}
    }

    @Override public java.util.List<TableInfo> getTableDirectory(DataSourceConfig config) {
        resolveMetadataSchema(config);return super.getTableDirectory(config);
    }

    @Override public java.util.Map<String,java.util.List<ColumnInfo>> getColumnsByTables(DataSourceConfig config,java.util.List<String> tables) {
        var result=new java.util.LinkedHashMap<String,java.util.List<ColumnInfo>>();
        if(tables==null||tables.isEmpty())return result;
        var names=new java.util.LinkedHashSet<String>();
        for(String name:tables)if(name!=null&&!name.isBlank())names.add(name);
        if(names.size()>200)throw new IllegalArgumentException("单次最多读取200张表的字段");
        if(names.isEmpty())return result;
        for(String name:names)result.put(name.toLowerCase(java.util.Locale.ROOT),new java.util.ArrayList<>());
        String schema=resolveMetadataSchema(config);
        String placeholders=String.join(",",java.util.Collections.nCopies(names.size(),"?"));
        String sql="""
            SELECT c.table_name,c.column_name,c.data_type,c.udt_name,c.character_maximum_length,
                   c.numeric_precision,c.numeric_scale,c.is_nullable,c.column_default,c.ordinal_position,
                   col_description((quote_ident(c.table_schema)||'.'||quote_ident(c.table_name))::regclass,c.ordinal_position) AS column_comment,
                   (pk.column_name IS NOT NULL) AS is_primary
              FROM information_schema.columns c
              LEFT JOIN (
                  SELECT k.table_schema,k.table_name,k.column_name
                    FROM information_schema.table_constraints t
                    JOIN information_schema.key_column_usage k
                      ON k.constraint_catalog=t.constraint_catalog AND k.constraint_schema=t.constraint_schema
                     AND k.constraint_name=t.constraint_name AND k.table_name=t.table_name
                   WHERE t.constraint_type='PRIMARY KEY'
              ) pk ON pk.table_schema=c.table_schema AND pk.table_name=c.table_name AND pk.column_name=c.column_name
             WHERE c.table_schema=? AND c.table_name IN (
            """+placeholders+") ORDER BY c.table_name,c.ordinal_position";
        try(Connection connection=getConnection(config);var statement=connection.prepareStatement(sql)) {
            int index=1;statement.setString(index++,schema);
            for(String name:names)statement.setString(index++,name);
            try(var rows=statement.executeQuery()) {
                while(rows.next()) {
                    String table=rows.getString("table_name").toLowerCase(java.util.Locale.ROOT);
                    if(!result.containsKey(table))continue;
                    var column=new ColumnInfo();column.setSourceDatabaseType("GAUSSDB");
                    column.setColumnName(rows.getString("column_name"));column.setColumnComment(rows.getString("column_comment"));
                    String type=rows.getString("data_type"),nativeType=rows.getString("udt_name");
                    column.setDataType(type);column.setLength(rows.getLong("character_maximum_length"));
                    column.setPrecision(rows.getInt("numeric_precision"));column.setScale(rows.getInt("numeric_scale"));
                    column.setNullable("YES".equals(rows.getString("is_nullable")));
                    column.setDefaultValue(rows.getString("column_default"));column.setOrdinalPosition(rows.getInt("ordinal_position"));
                    column.setPrimaryKey(rows.getBoolean("is_primary"));column.setUnique(column.getPrimaryKey());
                    column.setAutoIncrement(column.getDefaultValue()!=null&&column.getDefaultValue().contains("nextval("));
                    var kind=com.linewell.dataelement.metautil.ddl.PortableColumnType.kind(type);
                    if("ARRAY".equalsIgnoreCase(type)&&nativeType!=null&&nativeType.startsWith("_"))nativeType=nativeType.substring(1)+"[]";
                    if(column.getLength()>0&&(kind==com.linewell.dataelement.metautil.ddl.PortableColumnType.Kind.VARCHAR
                            ||kind==com.linewell.dataelement.metautil.ddl.PortableColumnType.Kind.CHAR))nativeType+="("+column.getLength()+")";
                    else if(kind==com.linewell.dataelement.metautil.ddl.PortableColumnType.Kind.DECIMAL&&column.getPrecision()>0)
                        nativeType+="("+column.getPrecision()+","+column.getScale()+")";
                    column.setColumnType(nativeType==null?type:nativeType);
                    result.get(table).add(column);
                }
            }
            return result;
        } catch(Exception failure){throw new IllegalStateException("GaussDB 批量字段读取失败，请核对 Schema 和元数据权限",failure);}
    }

    @Override public java.util.List<ColumnInfo> getColumns(DataSourceConfig config,String table) {
        return getColumnsByTables(config,java.util.List.of(table)).getOrDefault(table.toLowerCase(java.util.Locale.ROOT),java.util.List.of());
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = super.getDatabaseInfo(config);
        info.setDatabaseType("GaussDB");
        String schema = resolveMetadataSchema(config);

        // 尝试拿到更“像 GaussDB”的 version() 输出
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }

            // openGauss/GaussDB 在部分版本上过程对象可能主要表现为 FUNCTION，做兼容兜底
            if (info.getProcedureCount() == null || info.getProcedureCount() == 0) {
                String fallbackProcedureSql = String.format(
                    "SELECT COUNT(*) FROM information_schema.routines " +
                        "WHERE routine_schema = '%s' AND routine_type IN ('PROCEDURE', 'FUNCTION')",
                    schema
                );
                try (ResultSet rs = stmt.executeQuery(fallbackProcedureSql)) {
                    if (rs.next()) {
                        info.setProcedureCount(rs.getInt(1));
                    }
                } catch (Exception e) {
                    log.warn("获取GaussDB存储过程数量失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("获取GaussDB版本信息失败（忽略）: {}", e.getMessage());
        }

        return info;
    }
}

