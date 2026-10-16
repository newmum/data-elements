package com.linewell.dataelement.metautil.jdbc;

import java.util.Locale;

/** Catalog SQL returns only explicitly bound table identities, never an entire owner namespace. */
public final class JdbcColumnBatchQuery {
    private JdbcColumnBatchQuery() { }

    public static String sql(String jdbcUrl, String product, int tables) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return null;
        String url = jdbcUrl.toLowerCase(Locale.ROOT), name = product.toLowerCase(Locale.ROOT);
        String select, schema, table, order;
        if (url.startsWith("jdbc:oracle:") || url.startsWith("jdbc:oceanbase:oracle:")
                || url.startsWith("jdbc:oceanbaseoracle:") || name.contains("oracle")
                || url.startsWith("jdbc:dm:")) {
            select = "SELECT c.OWNER AS TABLE_SCHEM,c.TABLE_NAME,c.COLUMN_NAME,c.DATA_TYPE AS TYPE_NAME,"
                    + "CASE WHEN c.DATA_TYPE IN ('CLOB','NCLOB','LONG') THEN 0 WHEN c.DATA_TYPE IN ('CHAR','VARCHAR2','NCHAR','NVARCHAR2') THEN c.CHAR_LENGTH ELSE COALESCE(c.DATA_PRECISION,c.DATA_LENGTH) END AS COLUMN_SIZE,"
                    + "c.DATA_SCALE AS DECIMAL_DIGITS,CASE WHEN c.NULLABLE='N' THEN 0 ELSE 1 END AS NULLABLE,"
                    + (url.startsWith("jdbc:dm:")?"c.DATA_DEFAULT":"CASE WHEN c.DEFAULT_LENGTH>0 THEN 'DEFAULT' ELSE NULL END")
                    + " AS COLUMN_DEF,'NO' AS IS_AUTOINCREMENT FROM ALL_TAB_COLUMNS c";
            schema = "c.OWNER"; table = "c.TABLE_NAME"; order = "c.OWNER,c.TABLE_NAME,c.COLUMN_ID";
        } else if (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:kingbase")
                || url.startsWith("jdbc:opengauss:") || url.startsWith("jdbc:gaussdb:") || url.startsWith("jdbc:highgo:")) {
            select = "SELECT c.table_schema AS TABLE_SCHEM,c.table_name AS TABLE_NAME,c.column_name AS COLUMN_NAME,c.data_type AS TYPE_NAME,"
                    + "COALESCE(c.character_maximum_length,c.numeric_precision,0) AS COLUMN_SIZE,c.numeric_scale AS DECIMAL_DIGITS,"
                    + "CASE WHEN c.is_nullable='NO' THEN 0 ELSE 1 END AS NULLABLE,c.column_default AS COLUMN_DEF,"
                    + "CASE WHEN c.column_default LIKE 'nextval(%' THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT FROM information_schema.columns c";
            schema = "c.table_schema"; table = "c.table_name"; order = "c.table_schema,c.table_name,c.ordinal_position";
        } else if (url.startsWith("jdbc:sqlserver:")) {
            select = "SELECT c.TABLE_SCHEMA AS TABLE_SCHEM,c.TABLE_NAME,c.COLUMN_NAME,c.DATA_TYPE AS TYPE_NAME,"
                    + "CASE WHEN c.CHARACTER_MAXIMUM_LENGTH<0 THEN 0 ELSE COALESCE(c.CHARACTER_MAXIMUM_LENGTH,c.NUMERIC_PRECISION,0) END AS COLUMN_SIZE,c.NUMERIC_SCALE AS DECIMAL_DIGITS,"
                    + "CASE WHEN c.IS_NULLABLE='NO' THEN 0 ELSE 1 END AS NULLABLE,c.COLUMN_DEFAULT AS COLUMN_DEF,"
                    + "CASE WHEN COLUMNPROPERTY(OBJECT_ID(QUOTENAME(c.TABLE_SCHEMA)+'.'+QUOTENAME(c.TABLE_NAME)),c.COLUMN_NAME,'IsIdentity')=1 THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT FROM INFORMATION_SCHEMA.COLUMNS c";
            schema = "c.TABLE_SCHEMA"; table = "c.TABLE_NAME"; order = "c.TABLE_SCHEMA,c.TABLE_NAME,c.ORDINAL_POSITION";
        } else if (url.startsWith("jdbc:db2:")) {
            select = "SELECT c.TABSCHEMA AS TABLE_SCHEM,c.TABNAME AS TABLE_NAME,c.COLNAME AS COLUMN_NAME,c.TYPENAME AS TYPE_NAME,"
                    + "c.LENGTH AS COLUMN_SIZE,c.SCALE AS DECIMAL_DIGITS,CASE WHEN c.NULLS='N' THEN 0 ELSE 1 END AS NULLABLE,"
                    + "c.\"DEFAULT\" AS COLUMN_DEF,CASE WHEN c.IDENTITY='Y' THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT FROM SYSCAT.COLUMNS c";
            schema = "c.TABSCHEMA"; table = "c.TABNAME"; order = "c.TABSCHEMA,c.TABNAME,c.COLNO";
        } else return null;
        return select + " WHERE " + String.join(" OR ", java.util.Collections.nCopies(tables, "("+schema+"=? AND "+table+"=?)")) + " ORDER BY " + order;
    }
}
