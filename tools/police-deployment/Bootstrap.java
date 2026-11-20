import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Types;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.bouncycastle.crypto.digests.SM3Digest;

public class Bootstrap {
    private static final String TENANT = "2084109831682699264";
    private static final String ORG = "police-demo-organization";
    private static final String ROLE = "police-demo-admin-role";

    public static void main(String[] args) throws Exception {
        String url = required("POLICE_DM_URL");
        if (!url.startsWith("jdbc:dm:") || url.contains("?")) {
            throw new IllegalArgumentException("配置不带 schema 的达梦 JDBC URL");
        }
        Class.forName("dm.jdbc.driver.DmDriver");
        if (args.length == 1 && args[0].equals("create-users")) {
            try (var db = DriverManager.getConnection(url, "SYSDBA", required("POLICE_DM_SYSDBA_PASSWORD"))) {
                createOwner(db, "baseline", required("POLICE_CONTROL_PASSWORD"));
                createOwner(db, "baseline_ga", required("POLICE_TENANT_PASSWORD"));
            }
            return;
        }
        try (var control = DriverManager.getConnection(url, "baseline", required("POLICE_CONTROL_PASSWORD"));
             var tenant = DriverManager.getConnection(url, "baseline_ga", required("POLICE_TENANT_PASSWORD"))) {
            requireEmpty(control);
            requireEmpty(tenant);
            String password = required("POLICE_ADMIN_PASSWORD");
            if (password.length() < 12) throw new IllegalArgumentException("管理员密码至少 12 位");
            executeScript(control, Path.of("db/01-control-dm8.sql"));
            executeScript(tenant, Path.of("db/02-police-dm8.sql"));
            loadRows(control, "api_file_t", "file_content");
            loadRows(control, "ui_component_t", "source_code", "compile_js", "compile_css");
            loadRows(tenant, "sym_form", "form_json", "form_config");
            createAdmin(tenant, password);
            System.out.println("公安租户、平台配置与初始账号已导入。");
        }
    }

    private static void createOwner(Connection connection, String schema, String password) throws Exception {
        if (!password.matches("[A-Za-z0-9_!%+=@#-]{12,48}")) {
            throw new IllegalArgumentException("DM schema password must be 12–48 supported characters");
        }
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE USER \"" + schema + "\" IDENTIFIED BY \"" + password + "\"");
            statement.execute("GRANT RESOURCE TO \"" + schema + "\"");
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("DM user creation failed for " + schema + " (" + error.getErrorCode() + ")");
        }
    }

    private static void requireEmpty(Connection conn) throws Exception {
        try (var stmt = conn.createStatement(); var rows = stmt.executeQuery("SELECT COUNT(*) FROM USER_TABLES")) {
            if (!rows.next() || rows.getInt(1) != 0) throw new IllegalStateException("目标 schema 必须为空");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少运行参数 " + name);
        return value;
    }

    private static void executeScript(Connection connection, Path path) throws Exception {
        int number = 0;
        try (var statement = connection.createStatement()) {
            for (String sql : Files.readString(path).split("\\R-- DM STATEMENT --\\R")) {
                if (sql.isBlank()) continue;
                number++;
                try {
                    statement.execute(sql.strip());
                } catch (java.sql.SQLException error) {
                    String firstLine = sql.strip().lines().findFirst().orElse("");
                    if (firstLine.startsWith("INSERT")) firstLine = firstLine.split(" VALUES ")[0];
                    throw new IllegalStateException(path + " statement " + number + " " + firstLine, error);
                }
            }
        }
        System.out.println(path + " statements " + number);
    }

    private static void loadRows(Connection connection, String table, String... clobs) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("db", table + ".tsv"))) {
            String[] columns = reader.readLine().split("\t");
            var placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
            try (var statement = connection.prepareStatement("INSERT INTO " + table + " (" +
                    String.join(",", columns) + ") VALUES (" + placeholders + ")")) {
                connection.setAutoCommit(false);
                int count = 0;
                for (String line; (line = reader.readLine()) != null;) {
                    String[] fields = line.split("\t", -1);
                    for (int i = 0; i < columns.length; i++) {
                        boolean clob = java.util.Arrays.asList(clobs).contains(columns[i]);
                        if (fields[i].equals("-")) {
                            statement.setNull(i+1, clob ? Types.CLOB : Types.VARCHAR);
                            continue;
                        }
                        String content = new String(Base64.getDecoder().decode(fields[i].substring(1)), StandardCharsets.UTF_8);
                        if (clob) statement.setCharacterStream(i+1, new StringReader(content), content.length());
                        else statement.setString(i+1, content);
                    }
                    statement.executeUpdate();
                    count++;
                }
                connection.commit();
                connection.setAutoCommit(true);
                System.out.println(table + " loaded " + count);
            }
        }
    }

    private static void createAdmin(Connection connection, String password) throws Exception {
        connection.setAutoCommit(false);
        insert(connection, "INSERT INTO rm_user_t (id,tenant_id,user_name,real_name,password,status,deleted,create_time) " +
                "VALUES (?,?,?,?,?,0,0,CURRENT_TIMESTAMP)", "police-demo-admin-user", TENANT, "admin", "公安演示管理员", encode(password));
        insert(connection, "INSERT INTO rm_user_org_rela_t (id,tenant_id,user_id,org_id,job_type,serial_number) VALUES (?,?,?,?,?,?)",
                "police-demo-admin-org", TENANT, "police-demo-admin-user", ORG, "1", "1000");
        insert(connection, "INSERT INTO rm_user_role_rela_t (id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
                "police-demo-admin-membership", TENANT, "police-demo-admin-user", ROLE);
        connection.commit();
    }

    private static void insert(Connection connection, String sql, String... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i=0; i<values.length; i++) statement.setString(i+1, values[i]);
            statement.executeUpdate();
        }
    }

    private static String encode(String password) {
        String[] s = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT).split("-");
        return s[4] + s[0] + s[2] + sm3(s[0] + s[1] + sm3(password) + s[3] + s[4]) + s[1] + s[3];
    }

    private static String sm3(String input) {
        var digest = new SM3Digest();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        return HexFormat.of().withUpperCase().formatHex(hash);
    }
}
