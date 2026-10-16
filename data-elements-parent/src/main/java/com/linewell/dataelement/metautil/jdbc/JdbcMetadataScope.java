package com.linewell.dataelement.metautil.jdbc;

import com.linewell.dataelement.metautil.ddl.DdlDialect;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.Locale;
import java.util.Map;

/** One metadata scope policy for canvas probes and registered field-mapping contracts. */
public final class JdbcMetadataScope {
    private JdbcMetadataScope() {}

    /** Empty optional keys must not hide a later, populated registration property. */
    public static String configuredSchema(Map<String, ?> config) {
        String value=text(config,"schema","defaultSchema","currentSchema");
        return value==null?null:identifier(value);
    }

    /**
     * Called once per datasource, never once per table in a metadata batch. Explicit scope
     * wins; a legacy Oracle driver can read its session scope without a JDBC 4.1 accessor.
     * Service names, SIDs and Oracle catalogs are never interpreted as schema owners.
     */
    public static String schema(Connection connection,Map<String, ?> config,String manifestKey)throws SQLException {
        String schema=configuredSchema(config);
        if(schema!=null)return schema;
        String type=normalized(text(config,"dbType","databaseType"));
        if("source.hive".equals(manifestKey)||"sink.hive".equals(manifestKey)||type.equals("HIVE")||type.equals("MRSHIVE")) {
            String database=text(config,"database","dbName");
            if(database!=null)return identifier(database);
        }
        schema=connectionSchema(connection);
        if(schema!=null)return schema;
        DatabaseMetaData metadata=connection.getMetaData();
        String product=metadata==null?"":normalized(metadata.getDatabaseProductName());
        String url=lower(text(config,"jdbcURL","jdbcUrl","url"));
        boolean oceanbase=type.startsWith("OCEANBASE")||product.contains("OCEANBASE")
                ||url.startsWith("jdbc:oceanbase")||"source.oceanbase".equals(manifestKey);
        String mode=normalized(text(config,"compatibleMode","compatibilityMode","compatibility","mode"));
        boolean oracle=type.startsWith("ORACLE")||type.equals("OCEANBASEORACLE")||type.equals("OCEANBASECOMPATIBLEORACLE")
                ||product.contains("ORACLE")||"source.oracle".equals(manifestKey)||url.startsWith("jdbc:oracle:")
                ||url.startsWith("jdbc:oceanbase:oracle:")||url.startsWith("jdbc:oceanbaseoracle:")
                ||(oceanbase&&(mode.equals("ORACLE")||url.matches(".*[?;&](?:compatiblemode|compatibilitymode)=oracle(?:[;&].*)?$")));
        if(oracle) {
            schema=oracleSessionSchema(connection);
            if(schema!=null)return schema;
        }
        boolean ownerSchema=oracle||type.equals("DM")||type.equals("DAMENG")||product.equals("DM")||product.contains("DAMENG");
        if(!ownerSchema)return null;
        String user=metadataUser(metadata);
        if(user!=null)return oceanbase?owner(user,false,true):user;
        user=text(config,"username","dbUser","user");
        return user==null?null:owner(user,true,oceanbase);
    }

    public static String catalog(Connection connection)throws SQLException {
        try{return nonBlank(connection.getCatalog());}
        catch(SQLFeatureNotSupportedException|AbstractMethodError|UnsupportedOperationException ignored){return null;}
        catch(SQLException error){rethrowConnectionFailure(error);return null;}
    }

    private static String connectionSchema(Connection connection)throws SQLException {
        try{return nonBlank(connection.getSchema());}
        catch(SQLFeatureNotSupportedException|AbstractMethodError|UnsupportedOperationException ignored){return null;}
        catch(SQLException error){rethrowConnectionFailure(error);return null;}
    }

    private static String oracleSessionSchema(Connection connection)throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL")) {
            statement.setMaxRows(1);statement.setQueryTimeout(10);
            try(ResultSet result=statement.executeQuery()){return result.next()?nonBlank(result.getString(1)):null;}
        }catch(AbstractMethodError|UnsupportedOperationException ignored){return null;}
        catch(SQLException error){rethrowConnectionFailure(error);return null;}
    }

    private static String metadataUser(DatabaseMetaData metadata)throws SQLException {
        if(metadata==null)return null;
        try{return nonBlank(metadata.getUserName());}
        catch(SQLFeatureNotSupportedException|AbstractMethodError|UnsupportedOperationException ignored){return null;}
        catch(SQLException error){rethrowConnectionFailure(error);return null;}
    }

    private static void rethrowConnectionFailure(SQLException error)throws SQLException {
        // Optional metadata methods can be unsupported; a disconnected or timed-out session cannot be trusted.
        if(error instanceof SQLTimeoutException||(error.getSQLState()!=null&&error.getSQLState().startsWith("08")))throw error;
    }

    private static String owner(String user,boolean fold,boolean oceanbase) {
        String value=user.trim();
        if(value.startsWith("\""))return identifier(value);
        if(oceanbase)value=value.split("[@#]",2)[0].trim();
        if(value.isBlank())return null;
        return fold?value.toUpperCase(Locale.ROOT):value;
    }

    private static String identifier(String value) {
        var names=DdlDialect.names(value);
        if(names.size()!=1)throw new IllegalArgumentException("Schema须为单个标识符，请将库名与表名分别配置");
        return names.getFirst().value();
    }
    private static String text(Map<String, ?> config,String... keys) {
        if(config!=null)for(String key:keys){Object value=config.get(key);if(value!=null&&!value.toString().isBlank())return value.toString().trim();}
        return null;
    }
    private static String nonBlank(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    private static String normalized(String value){return value==null?"":value.toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+","");}
}
