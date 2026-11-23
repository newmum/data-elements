package com.linewell.dataelement.metautil.jdbc;

import java.io.Reader;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Collection;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** JDBC-only Oracle vendor-type binding. The original driver owns connections and transactions. */
public final class NifiOracleJdbcDriver implements Driver {
    public static final String URL_PREFIX = "jdbc:haitong:oracle:";
    public static final String NATIVE_URL_PREFIX = "jdbc:oracle:";
    public static final String ASSET_NAME = "haitong-oracle-jdbc-compat-1.0.4.jar";
    private static final String NATIVE_DRIVER = "oracle.jdbc.OracleDriver";

    static {
        try { DriverManager.registerDriver(new NifiOracleJdbcDriver()); }
        catch (SQLException error) { throw new ExceptionInInitializerError(error); }
    }

    @Override public boolean acceptsURL(String url) { return url != null && url.startsWith(URL_PREFIX); }
    @Override public Connection connect(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) return null;
        Driver driver = nativeDriver();
        Connection connection = driver.connect(nativeUrl(url), properties);
        return connection == null ? null : wrap(connection, driver.getClass().getClassLoader());
    }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException {
        return acceptsURL(url) ? nativeDriver().getPropertyInfo(nativeUrl(url), properties) : new DriverPropertyInfo[0];
    }
    @Override public int getMajorVersion() { return 1; }
    @Override public int getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant() { return false; }
    @Override public Logger getParentLogger() { return Logger.getLogger(NifiOracleJdbcDriver.class.getName()); }

    private static String nativeUrl(String url) { return NATIVE_URL_PREFIX + url.substring(URL_PREFIX.length()); }
    private Driver nativeDriver() throws SQLException {
        try {
            return (Driver) Class.forName(NATIVE_DRIVER, true, getClass().getClassLoader()).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new SQLException("NiFi的Oracle兼容驱动需要同一路径中的原生Oracle JDBC客户端", error);
        }
    }
    static Connection wrap(Connection connection, ClassLoader nativeLoader) {
        return (Connection) new Context(nativeLoader).wrap(connection);
    }

    private static final class Context {
        private final ClassLoader nativeLoader;
        private final Map<Object, Object> statements = new IdentityHashMap<>();
        private final Map<Integer, Constructor<?>> datumConstructors = new HashMap<>();
        private final Map<Object, List<Blob>> allocatedBlobs = new IdentityHashMap<>();
        Context(ClassLoader nativeLoader) { this.nativeLoader = nativeLoader; }

        synchronized Object wrap(Object value) {
            if (value == null) return null;
            Class<?> type = value instanceof Connection ? Connection.class
                    : value instanceof CallableStatement ? CallableStatement.class
                    : value instanceof PreparedStatement ? PreparedStatement.class
                    : value instanceof Statement ? Statement.class
                    : value instanceof ResultSet ? ResultSet.class
                    : value instanceof DatabaseMetaData ? DatabaseMetaData.class : null;
            if (type == null) return value;
            // Result sets/metadata do not need identity caching. Only keep open statements,
            // so a pooled physical connection cannot retain every closed query forever.
            if (type == ResultSet.class || type == DatabaseMetaData.class) return proxy(value, type);
            return statements.computeIfAbsent(value, delegate -> proxy(delegate, type));
        }
        private Object proxy(Object value, Class<?> type) {
            return Proxy.newProxyInstance(NifiOracleJdbcDriver.class.getClassLoader(), new Class<?>[]{type},
                    new Handler(this, value, type));
        }
        synchronized void closed(Object delegate) {
            if (delegate instanceof Connection) {
                allocatedBlobs.values().forEach(Context::freeBlobs); allocatedBlobs.clear();
                statements.clear(); datumConstructors.clear();
            } else { freeBlobs(allocatedBlobs.remove(delegate)); statements.remove(delegate); }
        }
        private static void freeBlobs(List<Blob> blobs) {
            if (blobs != null) for (Blob blob : blobs) try { blob.free(); } catch (SQLException ignored) { /* Connection cleanup remains authoritative. */ }
        }
        private synchronized void emptyBlob(PreparedStatement statement, int index) throws SQLException {
            // Oracle 12.2 rejects setBlob(stream, 0), while an empty temporary LOB
            // keeps zero bytes distinct from SQL NULL. Retain it until close/batch completion.
            Blob blob = statement.getConnection().createBlob();
            allocatedBlobs.computeIfAbsent(statement, ignored -> new ArrayList<>()).add(blob);
            statement.setBlob(index, blob);
        }
        synchronized Object datum(Object value, int sqlType) throws SQLException {
            try {
                Constructor<?> constructor = datumConstructors.get(sqlType);
                if (constructor == null) {
                    boolean single = sqlType == 100;
                    Class<?> type = Class.forName(single ? "oracle.sql.BINARY_FLOAT" : "oracle.sql.BINARY_DOUBLE", true, nativeLoader);
                    constructor = type.getConstructor(single ? float.class : double.class);
                    datumConstructors.put(sqlType, constructor);
                }
                if (constructor.getDeclaringClass().isInstance(value)) return value;
                double number = value instanceof Number numeric ? numeric.doubleValue()
                        : value instanceof CharSequence text ? Double.parseDouble(text.toString().trim()) : Double.NaN;
                if (!Double.isFinite(number)) throw new SQLDataException("Oracle二进制浮点绑定需要有限数值", "22003");
                if (sqlType == 100) {
                    float single = (float) number;
                    if (!Float.isFinite(single) || single == 0f && number != 0d)
                        throw new SQLDataException("数值超出Oracle BINARY_FLOAT可表示范围", "22003");
                    return constructor.newInstance(single);
                }
                return constructor.newInstance(number);
            } catch (NumberFormatException error) {
                // NumberFormatException embeds the original field value in its message.
                // Keep the public JDBC error useful without leaking a business value through its cause.
                throw new SQLDataException("Oracle二进制浮点绑定需要有效数值", "22018");
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new SQLException("原生Oracle JDBC缺少二进制浮点绑定能力，请核对已配置的驱动客户端", error);
            }
        }
        boolean bind(PreparedStatement statement, Object[] arguments) throws SQLException {
            if (arguments == null || arguments.length < 3 || !(arguments[0] instanceof Integer index)) return false;
            Integer sqlType = arguments[2] instanceof Integer code ? code
                    : arguments[2] instanceof SQLType type ? type.getVendorTypeNumber() : null;
            if (sqlType == null) return false;
            Object value = arguments[1];
            if (sqlType == Types.BLOB) {
                if (value == null) statement.setBlob(index, (InputStream) null);
                else if (value instanceof Blob blob) statement.setBlob(index, blob);
                else if (value instanceof InputStream stream) blobStream(statement, new Object[]{index, stream});
                else {
                    byte[] bytes = binary(value);
                    if (bytes.length == 0) emptyBlob(statement, index);
                    else statement.setBlob(index, new ByteArrayInputStream(bytes), (long) bytes.length);
                }
                return true;
            }
            if (sqlType == Types.NCLOB) {
                if (value == null) statement.setNClob(index, (Reader) null);
                else if (value instanceof NClob clob) statement.setNClob(index, clob);
                else if (value instanceof Reader reader) statement.setNClob(index, reader);
                else if (value instanceof CharSequence text) statement.setNClob(index, new StringReader(text.toString()));
                else throw new SQLDataException("NCLOB绑定需要字符值、字符流或NClob", "22018");
                return true;
            }
            if (sqlType == 100 || sqlType == 101) {
                if (value == null) statement.setNull(index, sqlType == 100 ? Types.REAL : Types.DOUBLE);
                // Oracle's generic setObject(value, vendorCode) rejects these types. A
                // vendor Datum via the untyped overload keeps their full binary range;
                // standard setDouble may instead bind an intermediate Oracle NUMBER.
                else statement.setObject(index, datum(value, sqlType));
                return true;
            }
            return false;
        }

        boolean blobStream(PreparedStatement statement, Object[] arguments) throws SQLException {
            if (arguments == null || arguments.length < 2 || !(arguments[0] instanceof Integer index)
                    || !(arguments[1] instanceof InputStream stream)) return false;
            if (arguments.length == 3 && arguments[2] instanceof Number length) {
                if (length.longValue() == 0) { emptyBlob(statement, index); return true; }
                return false;
            }
            if (arguments.length != 2) return false;
            try {
                int first = stream.read();
                if (first < 0) emptyBlob(statement, index);
                else statement.setBlob(index, new SequenceInputStream(new ByteArrayInputStream(new byte[]{(byte) first}), stream));
                return true;
            } catch (IOException invalid) {
                throw new SQLDataException("无法读取BLOB字节流，原记录保留", "22000");
            }
        }

        private static byte[] binary(Object value) throws SQLException {
            if (value instanceof byte[] bytes) { binaryLength(bytes.length); return bytes.clone(); }
            if (value instanceof ByteBuffer buffer) {
                var copy = buffer.asReadOnlyBuffer(); binaryLength(copy.remaining());
                byte[] bytes = new byte[copy.remaining()]; copy.get(bytes); return bytes;
            }
            if (value instanceof CharSequence text) {
                if (text.length() > 11_184_812) throw invalidBinary();
                try { byte[] bytes = Base64.getDecoder().decode(text.toString()); binaryLength(bytes.length); return bytes; }
                catch (IllegalArgumentException invalid) { throw invalidBinary(); }
            }
            if (value instanceof Collection<?> items) {
                binaryLength(items.size()); byte[] bytes = new byte[items.size()]; int index = 0;
                for (Object item : items) bytes[index++] = octet(item); return bytes;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value); binaryLength(length); byte[] bytes = new byte[length];
                for (int i = 0; i < length; i++) bytes[i] = octet(Array.get(value, i)); return bytes;
            }
            throw invalidBinary();
        }
        private static byte octet(Object value) throws SQLException {
            if (!(value instanceof Number number)) throw invalidBinary();
            try {
                long octet = number instanceof BigDecimal decimal ? decimal.longValueExact()
                        : number instanceof BigInteger integer ? integer.longValueExact() : number.longValue();
                if (number instanceof Double d && (!Double.isFinite(d) || d != octet)
                        || number instanceof Float f && (!Float.isFinite(f) || f != octet)
                        || octet < -128 || octet > 255) throw invalidBinary();
                return (byte) octet;
            } catch (ArithmeticException invalid) { throw invalidBinary(); }
        }
        private static void binaryLength(long length) throws SQLException {
            if (length < 0 || length > 8_388_608) throw new SQLDataException("BLOB字节载体超过8MiB上限", "22001");
        }
        private static SQLDataException invalidBinary() {
            return new SQLDataException("BLOB绑定需要原始字节、有效Base64或-128至255的整数数组", "22018");
        }
    }

    private record Handler(Context context, Object delegate, Class<?> type) implements InvocationHandler {
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return switch (name) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Oracle typed JDBC compatibility " + type.getSimpleName();
                default -> throw new UnsupportedOperationException(name);
            };
            if ((name.equals("unwrap") || name.equals("isWrapperFor")) && arguments != null
                    && arguments.length == 1 && arguments[0] instanceof Class<?> requested) {
                if (requested.isInstance(proxy)) return name.equals("unwrap") ? proxy : true;
                if (requested.isInstance(delegate)) return name.equals("unwrap") ? delegate : true;
            }
            try {
                if (name.equals("close") && (delegate instanceof Connection || delegate instanceof Statement)) context.closed(delegate);
                if (name.equals("setObject") && delegate instanceof PreparedStatement statement && context.bind(statement, arguments)) return null;
                if (name.equals("setBlob") && delegate instanceof PreparedStatement statement && context.blobStream(statement, arguments)) return null;
                return context.wrap(method.invoke(delegate, arguments));
            } catch (InvocationTargetException error) { throw error.getCause(); }
        }
    }
}
