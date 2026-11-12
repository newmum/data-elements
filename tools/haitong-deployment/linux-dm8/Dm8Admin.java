import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import org.bouncycastle.crypto.digests.SM3Digest;

/** Optional new-install-only bootstrap. Refuses to change an existing account. */
public class Dm8Admin {
    private static final String TENANT = "8fb3fdba44d54a729098000000000001";
    private static final String ORG = "8fb3fdba44d54a729098000000000003";
    private static final String ROLE = "8fb3fdba44d54a729098000000000004";
    private static final String ADMIN = "8fb3fdba44d54a729098000000000007";

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        String url = value(console, "HAITONG_DM_URL", "租户 JDBC URL（jdbc:dm://主机:5236?schema=baseline_haitong）: ");
        String user = value(console, "HAITONG_DM_USER", "数据库账号: ");
        String password = secret(console, "HAITONG_DM_PASSWORD", "数据库密码: ");
        String admin = secret(console, "HAITONG_ADMIN_PASSWORD", "海通 admin 初始密码（至少 12 位）: ");
        if (!url.startsWith("jdbc:dm:") || admin.length() < 12) {
            throw new IllegalArgumentException("需要达梦 JDBC URL，初始密码至少 12 位。");
        }
        if (System.getenv("HAITONG_ADMIN_PASSWORD") == null && !admin.equals(secret(console, "", "确认初始密码: "))) {
            throw new IllegalArgumentException("两次输入的初始密码不同。");
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                try (var check = connection.prepareStatement("SELECT COUNT(*) FROM rm_user_t WHERE tenant_id=?")) {
                    check.setString(1, TENANT);
                    try (var result = check.executeQuery()) {
                        if (!result.next() || result.getInt(1) != 0) {
                            throw new IllegalStateException("该租户已有账号，初始账号脚本不会修改它。");
                        }
                    }
                }
                execute(connection, "INSERT INTO rm_user_t (id,tenant_id,user_name,real_name,password,status,deleted,create_time) "
                        + "VALUES (?,?,?,?,?,0,0,CURRENT_TIMESTAMP)", ADMIN, TENANT, "admin", "海通管理员", digest(admin));
                execute(connection, "INSERT INTO rm_user_org_rela_t (id,tenant_id,user_id,org_id,job_type,serial_number) VALUES (?,?,?,?,?,?)",
                        "8fb3fdba44d54a729098000000000008", TENANT, ADMIN, ORG, "1", "1000");
                execute(connection, "INSERT INTO rm_user_role_rela_t (id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
                        "8fb3fdba44d54a729098000000000009", TENANT, ADMIN, ROLE);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            System.err.printf("初始账号创建失败（SQLState=%s，错误码=%d）。请核对租户库和初始化 SQL。%n",
                    failure.getSQLState(), failure.getErrorCode());
            System.exit(1);
        }
        System.out.println("初始账号 admin 已创建。");
    }

    private static String value(Console console, String key, String label) {
        String value = System.getenv(key);
        if (value != null) return value;
        if (console == null) throw new IllegalArgumentException("请从交互终端运行，或提供环境变量 " + key);
        return console.readLine("%s", label);
    }

    private static String secret(Console console, String key, String label) {
        String value = System.getenv(key);
        if (value != null) return value;
        if (console == null) throw new IllegalArgumentException("请从交互终端运行，或提供环境变量 " + key);
        char[] chars = console.readPassword("%s", label);
        if (chars == null) throw new IllegalArgumentException("没有读取到密码。");
        try {
            return new String(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static void execute(Connection connection, String sql, String... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setString(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }

    private static String digest(String password) {
        String[] salt = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT).split("-");
        return salt[4] + salt[0] + salt[2] + sm3(salt[0] + salt[1] + sm3(password) + salt[3] + salt[4]) + salt[1] + salt[3];
    }

    private static String sm3(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        SM3Digest hash = new SM3Digest();
        hash.update(bytes, 0, bytes.length);
        byte[] result = new byte[hash.getDigestSize()];
        hash.doFinal(result, 0);
        return HexFormat.of().withUpperCase().formatHex(result);
    }
}
