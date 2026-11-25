package com.linewell.dataelement.metautil.jdbc;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;

public final class JdbcPreviewValues {
    private static final Pattern LOCAL_TIMESTAMP = Pattern.compile("TIMESTAMP(?:\\(\\d+\\))?(?:\\s+WITHOUT\\s+TIME\\s+ZONE)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MYSQL_DATETIME = Pattern.compile("DATETIME(?:\\(\\d+\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQLSERVER_DATETIME = Pattern.compile("DATETIME2?(?:\\(\\d+\\))?", Pattern.CASE_INSENSITIVE);
    private static final Set<DatabaseType> LOCAL_TIMESTAMP_DATABASES=Set.of(DatabaseType.ORACLE,DatabaseType.HIVE,
            DatabaseType.POSTGRESQL,DatabaseType.KINGBASE,DatabaseType.GAUSSDB,DatabaseType.HIGHGO,
            DatabaseType.DAMENG,DatabaseType.OCEANBASE_ORACLE);
    private static final Pattern ZERO_FRACTION = Pattern.compile("\\.0+$");

    private JdbcPreviewValues() {
    }

    public static boolean isLocalTimestamp(DatabaseType databaseType, String columnType) {
        if (databaseType == null || columnType == null) return false;
        String type = columnType.strip();
        return (LOCAL_TIMESTAMP_DATABASES.contains(databaseType)
                && LOCAL_TIMESTAMP.matcher(type).matches())
            || ((databaseType == DatabaseType.MYSQL || databaseType == DatabaseType.MARIADB)
                && MYSQL_DATETIME.matcher(type).matches())
            || (databaseType == DatabaseType.SQLSERVER && SQLSERVER_DATETIME.matcher(type).matches());
    }

    public static Object read(ResultSet resultSet, int column, boolean localTimestamp) throws SQLException {
        if (!localTimestamp) {
            return resultSet.getObject(column);
        }
        // 无时区时间戳表示物理时钟值，转成 java.sql.Timestamp 会引入 JVM 时区的历史夏令时规则。
        String value = resultSet.getString(column);
        return value == null ? null : ZERO_FRACTION.matcher(value).replaceFirst("");
    }
}
