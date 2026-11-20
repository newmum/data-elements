import java.io.StringReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Add the reviewed police configuration and organization defaults to a DM8 instance. */
public class PoliceDefaults {
    private static final String TENANT = "2084109831682699264";
    private static final String DEMO_USER = "police-demo-admin-user";
    private static final String DEMO_ORG = "police-demo-organization";
    private static final List<String> CONFIG_COLUMNS = List.of(
            "tid", "config_code", "config_name", "config_value", "is_use", "sort_no", "is_encrypt", "is_del",
            "tenant_id", "config_group", "value_type", "config_scope", "cache_ttl_seconds", "version_no");
    private static final List<String> ORG_COLUMNS = List.of(
            "id", "tenant_id", "name", "parent_id", "serial_number", "org_path", "nature", "status", "deleted", "sort_num");
    private static final Set<String> PUBLIC_CODES = Set.of(
            "appId", "loginUrl", "logo", "needLogin", "showLogo", "showShoppingCar", "showTagsView",
            "showWatermark", "sidebarOpen", "systemCode", "systemName", "themeColor", "version", "watermarkContent");

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--refresh-cache")) {
            evictConfigCache(true);
            return;
        }
        boolean apply = args.length == 1 && args[0].equals("--apply");
        if (args.length != 1 || !(apply || args[0].equals("--plan"))) {
            throw new IllegalArgumentException("Usage: PoliceDefaults.java --plan|--apply");
        }
        String url = required("POLICE_DM_URL");
        if (!url.startsWith("jdbc:dm:") || url.contains("?")) {
            throw new IllegalArgumentException("POLICE_DM_URL must identify a DM8 instance without schema parameters");
        }
        Class.forName("dm.jdbc.driver.DmDriver");
        List<Map<String, String>> configs = readRows("db/police-defaults-config.tsv", CONFIG_COLUMNS);
        List<Map<String, String>> organizations = readRows("db/police-defaults-org.tsv", ORG_COLUMNS);
        validate(configs, organizations);
        try (var control = DriverManager.getConnection(url, "baseline", required("POLICE_CONTROL_PASSWORD"));
             var tenant = DriverManager.getConnection(url, "baseline_ga", required("POLICE_TENANT_PASSWORD"))) {
            try (var statement = control.prepareStatement("SELECT COUNT(*) FROM sym_tenant_t WHERE tid=? AND is_del=0 AND status=1")) {
                statement.setString(1, TENANT);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Expected one active police tenant");
                }
            }
            List<Map<String, String>> global = configs.stream().filter(row -> row.get("tenant_id").equals("GLOBAL")).toList();
            List<Map<String, String>> local = configs.stream().filter(row -> row.get("tenant_id").equals(TENANT)).toList();
            var globalPlan = missing(control, "sym_config_t", global, "GLOBAL", "config_group", "config_code");
            var localPlan = missing(tenant, "sym_config_t", local, TENANT, "config_group", "config_code");
            var orgPlan = missing(tenant, "rm_org_t", organizations, TENANT, "id");
            String root = local.stream().filter(row -> row.get("config_code").equals("orgRootId"))
                    .findFirst().orElseThrow().get("config_value");
            String rootSerial = organizations.stream().filter(row -> row.get("id").equals(root))
                    .findFirst().orElseThrow().get("serial_number");
            List<String> memberships = new ArrayList<>();
            try (var statement = tenant.prepareStatement(
                    "SELECT id FROM rm_user_org_rela_t WHERE tenant_id=? AND user_id=? AND org_id=?")) {
                statement.setString(1, TENANT);
                statement.setString(2, DEMO_USER);
                statement.setString(3, DEMO_ORG);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) memberships.add(rows.getString(1));
                }
            }
            System.out.printf("Defaults plan — global %d, police settings %d, organizations %d, demo memberships %d%n",
                    globalPlan.size(), localPlan.size(), orgPlan.size(), memberships.size());
            if (!apply) return;
            Files.createDirectories(Path.of("logs"));
            Path journal = Files.createTempDirectory(Path.of("logs"), "police-defaults-");
            writeJournal(journal.resolve("control-insert.tsv"), CONFIG_COLUMNS, globalPlan);
            writeJournal(journal.resolve("tenant-config-insert.tsv"), CONFIG_COLUMNS, localPlan);
            writeJournal(journal.resolve("organization-insert.tsv"), ORG_COLUMNS, orgPlan);
            writeJournal(journal.resolve("demo-membership-before.tsv"), List.of("id", "tenant_id", "user_id", "org_id"),
                    memberships.stream().map(id -> Map.of("id", id, "tenant_id", TENANT, "user_id", DEMO_USER, "org_id", DEMO_ORG)).toList());
            Files.writeString(journal.resolve("started.txt"), Instant.now().toString());
            // The schemas use separate accounts. Control defaults are insert-only and can
            // safely remain if the tenant transaction fails. Rerunning completes missing rows.
            control.setAutoCommit(false);
            try {
                insert(control, "sym_config_t", CONFIG_COLUMNS, globalPlan);
                control.commit();
            } catch (Exception failure) {
                control.rollback();
                throw failure;
            }
            tenant.setAutoCommit(false);
            try {
                insert(tenant, "sym_config_t", CONFIG_COLUMNS, localPlan);
                insert(tenant, "rm_org_t", ORG_COLUMNS, orgPlan);
                try (var statement = tenant.prepareStatement(
                        "UPDATE rm_user_org_rela_t SET org_id=?,serial_number=? WHERE tenant_id=? AND user_id=? AND org_id=?")) {
                    statement.setString(1, root);
                    statement.setString(2, rootSerial);
                    statement.setString(3, TENANT);
                    statement.setString(4, DEMO_USER);
                    statement.setString(5, DEMO_ORG);
                    statement.executeUpdate();
                }
                tenant.commit();
            } catch (Exception failure) {
                tenant.rollback();
                throw failure;
            }
            evictConfigCache(false);
            Files.writeString(journal.resolve("completed.txt"), Instant.now().toString());
            System.out.println("Police defaults imported. Journal " + journal);
        }
    }

    private static void evictConfigCache(boolean requireRunning) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("POLICE_REDIS_PORT", "18179"));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            socket.setSoTimeout(3000);
            try (var output = new BufferedOutputStream(socket.getOutputStream());
                 var input = new BufferedInputStream(socket.getInputStream())) {
                redis(output, input, List.of("AUTH", required("POLICE_REDIS_PASSWORD")));
                List<String> command = new ArrayList<>();
                command.add("DEL");
                for (String tenant : List.of("GLOBAL", TENANT)) {
                    for (String group : List.of("portal", "runtime", "auth_data_scope")) {
                        for (String scope : List.of("public-scope", "all-scopes")) {
                            for (String visibility : List.of("public", "internal")) {
                                command.add("data-elements:system-config:v1:" + tenant + ":" + group + ":" + scope + ":" + visibility);
                            }
                        }
                    }
                }
                redis(output, input, command);
            }
        } catch (ConnectException absent) {
            if (requireRunning) throw new IllegalStateException("Local Redis must be running to refresh configuration", absent);
            System.out.println("Local Redis is stopped. platform.sh start refreshes configuration after Redis starts.");
        }
    }

    private static void redis(BufferedOutputStream output, BufferedInputStream input, List<String> arguments) throws Exception {
        output.write(("*" + arguments.size() + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        output.flush();
        int type = input.read();
        while (true) {
            int next = input.read();
            if (next < 0) throw new IllegalStateException("Local Redis did not finish its response");
            if (next == '\n') break;
        }
        if (type != '+' && type != ':') throw new IllegalStateException("Local Redis configuration cache refresh failed");
    }

    private static List<Map<String, String>> readRows(String file, List<String> columns) throws Exception {
        try (var input = Files.newBufferedReader(Path.of(file), StandardCharsets.US_ASCII)) {
            if (!String.join("\t", columns).equals(input.readLine())) throw new IllegalArgumentException("Unexpected seed columns");
            var rows = new ArrayList<Map<String, String>>();
            for (String line; (line = input.readLine()) != null;) {
                String[] fields = line.split("\t", -1);
                if (fields.length != columns.size()) throw new IllegalArgumentException("Invalid seed row");
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < fields.length; i++) {
                    row.put(columns.get(i), fields[i].equals("-") ? null :
                            new String(Base64.getDecoder().decode(fields[i].substring(1)), StandardCharsets.UTF_8));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private static void validate(List<Map<String, String>> configs, List<Map<String, String>> organizations) {
        Set<String> orgIds = new HashSet<>();
        for (var row : organizations) {
            if (!TENANT.equals(row.get("tenant_id")) || !"0".equals(row.get("deleted")) || !orgIds.add(row.get("id"))) {
                throw new IllegalArgumentException("Organizations must be active, unique, and belong to the police tenant");
            }
        }
        Set<String> keys = new HashSet<>();
        for (var row : configs) {
            boolean police = TENANT.equals(row.get("tenant_id"));
            boolean publicSetting = "PORTAL".equals(row.get("config_group")) && "PUBLIC".equals(row.get("config_scope"))
                    && PUBLIC_CODES.contains(row.get("config_code"));
            boolean tenantSetting = police && ("RUNTIME".equals(row.get("config_group")) && "orgRootId".equals(row.get("config_code"))
                    || "AUTH_DATA_SCOPE".equals(row.get("config_group")) && "RESOURCE_SCOPES".equals(row.get("config_code")));
            if (!(police || "GLOBAL".equals(row.get("tenant_id"))) || !(publicSetting || tenantSetting)
                    || !"0".equals(row.get("is_encrypt")) || !"1".equals(row.get("is_use"))
                    || !keys.add(row.get("tenant_id") + ":" + row.get("config_group") + ":" + row.get("config_code"))) {
                throw new IllegalArgumentException("Configuration is outside the reviewed initialization scope");
            }
            if ("orgRootId".equals(row.get("config_code")) && !orgIds.contains(row.get("config_value"))) {
                throw new IllegalArgumentException("Police organization root is absent");
            }
        }
    }

    private static List<Map<String, String>> missing(Connection conn, String table,
            List<Map<String, String>> desired, String tenant, String... keys) throws Exception {
        // One scoped read per table/scope, irrespective of the number of imported rows.
        Set<List<String>> existing = new HashSet<>();
        try (var query = conn.prepareStatement("SELECT " + String.join(",", keys) + " FROM " + table + " WHERE tenant_id=?")) {
            query.setString(1, tenant);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    List<String> key = new ArrayList<>();
                    for (int i = 1; i <= keys.length; i++) key.add(rows.getString(i));
                    existing.add(key);
                }
            }
        }
        return desired.stream().filter(row -> !existing.contains(java.util.Arrays.stream(keys).map(row::get).toList())).toList();
    }

    private static void insert(Connection connection, String table, List<String> columns,
            List<Map<String, String>> rows) throws Exception {
        if (rows.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
        try (var statement = connection.prepareStatement(
                "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")")) {
            for (var row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    String value = row.get(columns.get(i));
                    if (value == null) statement.setNull(i + 1, Types.VARCHAR);
                    else if (columns.get(i).equals("config_value")) statement.setCharacterStream(i + 1, new StringReader(value), value.length());
                    else statement.setString(i + 1, value);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeJournal(Path path, List<String> columns, List<Map<String, String>> rows) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(String.join("\t", columns));
        for (var row : rows) {
            List<String> fields = new ArrayList<>();
            for (String column : columns) {
                String value = row.get(column);
                fields.add(value == null ? "-" : "+" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
            }
            lines.add(String.join("\t", fields));
        }
        Files.write(path, lines, StandardCharsets.US_ASCII);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable " + name);
        return value;
    }
}
