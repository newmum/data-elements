package com.linewell.dataelement.metautil.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/** JDBC-only bridge for Hive's unsupported optional result metadata; also packaged as a small NiFi driver asset. */
public final class NifiHiveJdbcDriver implements Driver {
    public static final String URL_PREFIX = "jdbc:haitong:hive2:";
    public static final String NATIVE_URL_PREFIX = "jdbc:hive2:";
    public static final String ASSET_NAME = "haitong-hive-jdbc-compat-1.0.1.jar";
    public static final String LEGACY_ASSET_NAME = "haitong-hive-jdbc-compat-1.0.0.jar";
    private static final String NATIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

    static {
        try { DriverManager.registerDriver(new NifiHiveJdbcDriver()); }
        catch (SQLException error) { throw new ExceptionInInitializerError(error); }
    }

    @Override public boolean acceptsURL(String url) { return url != null && url.startsWith(URL_PREFIX); }

    @Override public Connection connect(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) return null;
        // A distinct protocol prevents DriverManager from selecting the unwrapped Hive driver first.
        Connection connection = HiveKerberosSupport.connect(nativeDriver(), nativeUrl(url), properties);
        return connection == null ? null : wrap(connection);
    }

    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) return new DriverPropertyInfo[0];
        return nativeDriver().getPropertyInfo(nativeUrl(url), properties);
    }
    @Override public int getMajorVersion() { return 1; }
    @Override public int getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant() { return false; }
    @Override public Logger getParentLogger() { return Logger.getLogger(NifiHiveJdbcDriver.class.getName()); }

    private static String nativeUrl(String url) throws SQLException {
        if (url == null || !url.startsWith(URL_PREFIX)) throw new SQLException("Hive兼容驱动需要显式的兼容协议地址");
        return NATIVE_URL_PREFIX + url.substring(URL_PREFIX.length());
    }

    private Driver nativeDriver() throws SQLException {
        try {
            return (Driver) Class.forName(NATIVE_DRIVER, true, getClass().getClassLoader()).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new SQLException("NiFi的Hive兼容驱动需要同一驱动路径中的原生Hive JDBC客户端", error);
        }
    }

    static Connection wrap(Connection connection) { return (Connection) new Context().wrap(connection); }

    private static final class Context {
        private final Map<Object, Object> wrappers = new IdentityHashMap<>();

        synchronized Object wrap(Object value) {
            if (value == null) return null;
            Class<?> type = value instanceof Connection ? Connection.class
                    : value instanceof CallableStatement ? CallableStatement.class
                    : value instanceof PreparedStatement ? PreparedStatement.class
                    : value instanceof Statement ? Statement.class
                    : value instanceof ResultSet ? ResultSet.class
                    : value instanceof ResultSetMetaData ? ResultSetMetaData.class
                    : value instanceof DatabaseMetaData ? DatabaseMetaData.class : null;
            if (type == null) return value;
            return wrappers.computeIfAbsent(value, delegate -> Proxy.newProxyInstance(
                    NifiHiveJdbcDriver.class.getClassLoader(), new Class<?>[]{type}, new Handler(this, delegate, type)));
        }

        synchronized void close() { wrappers.clear(); }
    }

    private record Handler(Context context, Object delegate, Class<?> type) implements InvocationHandler {
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return switch (name) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Hive JDBC compatibility " + type.getSimpleName();
                default -> throw new UnsupportedOperationException(name);
            };
            if ((name.equals("unwrap") || name.equals("isWrapperFor")) && arguments != null
                    && arguments.length == 1 && arguments[0] instanceof Class<?> requested) {
                if (requested.isInstance(proxy)) return name.equals("unwrap") ? proxy : true;
                if (requested.isInstance(delegate)) return name.equals("unwrap") ? delegate : true;
            }
            try {
                return context.wrap(method.invoke(delegate, arguments));
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (delegate instanceof ResultSetMetaData metadata && cause instanceof SQLFeatureNotSupportedException
                        && (name.equals("getTableName") || name.equals("getSchemaName")
                        || name.equals("getCatalogName") || name.equals("isSigned"))) {
                    int column = (Integer) arguments[0];
                    if (column < 1 || column > metadata.getColumnCount()) throw new SQLException("JDBC列序号越界");
                    if (name.equals("getTableName") || name.equals("getSchemaName") || name.equals("getCatalogName")) return "";
                    if (name.equals("isSigned")) return switch (metadata.getColumnType(column)) {
                        case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC,
                                Types.DECIMAL, Types.REAL, Types.FLOAT, Types.DOUBLE -> true;
                        case Types.BIT, Types.BOOLEAN, Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                                Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.DATE, Types.TIME,
                                Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIME_WITH_TIMEZONE,
                                Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.ARRAY, Types.STRUCT,
                                Types.CLOB, Types.NCLOB, Types.BLOB, Types.NULL -> false;
                        default -> throw cause;
                    };
                }
                throw cause;
            } finally {
                if (delegate instanceof Connection && name.equals("close")) context.close();
            }
        }
    }
}
