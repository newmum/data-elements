import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.digests.SM3Digest;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Installer only: empty-schema DM8 initialization; no application business services. */
public class Dm8Bootstrap {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TENANT = "2084109831682699264";
    private static final String ADMIN = "police-demo-admin-user";
    private static final String ROLE = "police-demo-admin-role";
    private static Map<String,Object> config;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected check, check-empty, create-users, init or sm3");
        if (args[0].equals("sm3")) {
            System.out.println(sm3(required("POLICE_PASSWORD_INPUT")));
            return;
        }
        config = JSON.readValue(Files.readString(Path.of(required("POLICE_DM_CONFIG"))), new TypeReference<>() {});
        String url = value(config,"jdbcUrl");
        if (!url.matches("jdbc:dm://[^?;\\s]+") || url.contains("@"))
            throw new IllegalArgumentException("请填写不含账号口令和schema参数的达梦 JDBC URL");
        Class.forName("dm.jdbc.driver.DmDriver");
        DriverManager.setLoginTimeout(15);
        if (args[0].equals("create-users")) {
            try { createUsers(); }
            catch (SQLException error) { throw new IllegalStateException("达梦账号准备失败，SQL错误码="+error.getErrorCode()); }
            return;
        }
        try (Connection control=connect("control"); Connection tenant=connect("tenant")) {
            environment(control); environment(tenant);
            if (args[0].equals("check")) {
                System.out.println("DM8连接、字符集、大小写和兼容模式检查通过");
                return;
            }
            if (args[0].equals("check-empty")) {
                empty(control); empty(tenant);
                System.out.println("两个独立schema为空，可执行首次初始化");
                return;
            }
            if (!args[0].equals("init")) throw new IllegalArgumentException("Unknown action");
            empty(control); empty(tenant);
            execute(control,Path.of("db/01-control-dm8.sql"));
            execute(tenant,Path.of("db/02-police-dm8.sql"));
            List<Map<String,Object>> seeds=JSON.readValue(Files.readString(Path.of("db/seeds.json")),new TypeReference<>() {});
            for (Map<String,Object> seed:seeds)
                load(seed.get("scope").equals("control")?control:tenant,seed);
            createAdmin(tenant);
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("controlTables",scalar(control,"SELECT COUNT(*) FROM USER_TABLES"));
            report.put("tenantTables",scalar(tenant,"SELECT COUNT(*) FROM USER_TABLES"));
            report.put("resourceTables",seeds.size());
            report.put("verifiedAt",java.time.Instant.now().toString());
            Files.writeString(Path.of("data/local/dm-initialization-report.json"),JSON.writeValueAsString(report));
            System.out.println("达梦空库结构、Magic、低代码、配置、组织及初始账号已导入");
        } catch (SQLException error) {
            // Driver messages can include connection properties: emit only the numeric diagnostic.
            throw new IllegalStateException("达梦初始化未完成，SQL错误码="+error.getErrorCode()+"；保留当前数据，请核查初始化日志");
        }
    }

    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank())throw new IllegalArgumentException("缺少运行参数 "+name);
        return value;
    }
    private static String value(Map<String,Object> map,String key) {
        Object value=map.get(key);
        if(value==null || value.toString().isBlank())throw new IllegalArgumentException("配置缺少 "+key);
        return value.toString();
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> scope(String name) {return (Map<String,Object>)config.get(name);}
    private static String identifier(String text) {
        if(!text.matches("[A-Za-z][A-Za-z0-9_]{0,127}"))throw new IllegalArgumentException("数据库对象名只能使用字母、数字、下划线，最长128位");
        return "\""+text+"\"";
    }
    private static Connection connect(String name) throws SQLException {
        Map<String,Object> item=scope(name);
        String user=value(item,"username"), schema=value(item,"schema");
        identifier(user);identifier(schema);
        if(!user.equalsIgnoreCase(schema))throw new IllegalArgumentException("初始化账号须拥有同名独立schema："+name);
        Connection result=DriverManager.getConnection(value(config,"jdbcUrl"),user,value(item,"password"));
        try(Statement statement=result.createStatement()){statement.execute("SET SCHEMA "+identifier(schema));}
        return result;
    }
    private static long scalar(Connection connection,String sql) throws SQLException {
        try(Statement statement=connection.createStatement();ResultSet row=statement.executeQuery(sql)){
            if(!row.next())throw new IllegalStateException("达梦预检查未返回结果");
            return row.getLong(1);
        }
    }
    private static void environment(Connection connection) throws SQLException {
        if(scalar(connection,"SELECT SF_GET_UNICODE_FLAG()")!=1)
            throw new IllegalStateException("目标达梦实例须使用UTF-8字符集；安装器不会修改实例参数");
        if(scalar(connection,"SELECT SF_GET_CASE_SENSITIVE_FLAG()")!=0)
            throw new IllegalStateException("目标达梦实例须大小写不敏感 CASE_SENSITIVE=0；请使用相符的独立实例");
        if(scalar(connection,"SELECT SF_GET_PARA_VALUE(2,'COMPATIBLE_MODE')")!=4)
            throw new IllegalStateException("目标达梦实例须使用 COMPATIBLE_MODE=4；安装器不会更改或重启数据库");
        if(scalar(connection,"SELECT PAGE()")<32768)
            throw new IllegalStateException("完整结构的目标达梦实例须使用32KB页，避免宽字段表和索引超限；安装器不会重建实例");
    }
    private static void empty(Connection connection) throws SQLException {
        if(scalar(connection,"SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_TYPE IN ('TABLE','VIEW','SEQUENCE','TRIGGER','PROCEDURE','FUNCTION')")!=0)
            throw new IllegalStateException("初始化只能使用两个空schema，已有对象时拒绝导入；不会删除或覆盖已有数据");
    }
    private static void createUsers() throws SQLException {
        Map<String,Object> dba=scope("dba");
        try(Connection db=DriverManager.getConnection(value(config,"jdbcUrl"),value(dba,"username"),value(dba,"password"))){
            environment(db);
            for(String name:List.of("control","tenant")){
                Map<String,Object> item=scope(name);String user=value(item,"username"),password=value(item,"password");
                identifier(user);
                if(!user.equalsIgnoreCase(value(item,"schema")))throw new IllegalArgumentException("DBA创建的账号和schema必须同名");
                try(PreparedStatement query=db.prepareStatement("SELECT COUNT(*) FROM DBA_USERS WHERE UPPER(USERNAME)=UPPER(?)")){
                    query.setString(1,user);
                    try(ResultSet row=query.executeQuery()){row.next();if(row.getInt(1)>0)continue;}
                }
                if(password.length()<12 || password.length()>48 || !password.matches("[A-Za-z0-9_!%+=@#-]+"))
                    throw new IllegalArgumentException("自动建用户的密码须为12–48位字母、数字及 _!%+=@#- 字符");
                try(Statement statement=db.createStatement()){
                    statement.execute("CREATE USER "+identifier(user)+" IDENTIFIED BY \""+password+"\"");
                    statement.execute("GRANT RESOURCE TO "+identifier(user));
                }
            }
        }
        System.out.println("独立应用账号已准备；已有账号的密码与授权未修改");
    }
    private static void execute(Connection connection,Path path) throws Exception {
        int count=0;
        for(String part:Files.readString(path).split("\\R-- DM STATEMENT --\\R")){
            String sql=part.strip();if(sql.isEmpty())continue;
            boolean trigger=sql.toUpperCase(Locale.ROOT).matches("(?s)CREATE OR REPLACE\\s+TRIGGER.*");
            if(sql.endsWith("/"))sql=sql.substring(0,sql.length()-1).strip();
            if(!trigger && sql.endsWith(";"))sql=sql.substring(0,sql.length()-1);
            if(path.getFileName().toString().contains("police")){
                String schema=identifier(value(scope("tenant"),"schema"));
                sql=sql.replaceAll("(?i)(?<![A-Za-z0-9_])(?:\"baseline_ga\"|baseline_ga)\\.",java.util.regex.Matcher.quoteReplacement(schema+"."));
            }
            try(Statement statement=connection.createStatement()){statement.execute(sql);}
            catch(SQLException error){throw new IllegalStateException(path.getFileName()+" 第"+(count+1)+"条结构语句失败，SQL错误码="+error.getErrorCode()+"，对象="+sql.lines().findFirst().orElse(""));}
            count++;
        }
        System.out.println(path.getFileName()+" statements="+count);
    }
    private static void load(Connection connection,Map<String,Object> seed) throws Exception {
        String table=value(seed,"table");identifier(table);
        Path path=Path.of("db",value(seed,"file"));
        String actual=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        if(!actual.equals(value(seed,"sha256")))throw new IllegalStateException("初始化数据校验失败 "+table);
        Map<String,Integer> types=new HashMap<>();
        try(ResultSet columns=connection.getMetaData().getColumns(null,connection.getSchema(),table,null)){
            while(columns.next())types.put(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),columns.getInt("DATA_TYPE"));
        }
        int count=0;
        try(var reader=Files.newBufferedReader(path)){
            String[] columns=reader.readLine().split("\\t");
            if(!types.keySet().containsAll(Arrays.asList(columns)))throw new IllegalStateException("目标表缺少初始化列 "+table);
            String sql="INSERT INTO "+identifier(table)+" ("+String.join(",",Arrays.stream(columns).map(Dm8Bootstrap::identifier).toList())+") VALUES ("+String.join(",",Collections.nCopies(columns.length,"?"))+")";
            connection.setAutoCommit(false);
            try(PreparedStatement statement=connection.prepareStatement(sql)){
                for(String line;(line=reader.readLine())!=null;){
                    String[] values=line.split("\\t",-1);
                    if(values.length!=columns.length)throw new IllegalStateException("初始化字段数不符 "+table);
                    for(int i=0;i<columns.length;i++){
                        int type=types.get(columns[i]);
                        if(values[i].equals("-")){statement.setNull(i+1,type);continue;}
                        String value=new String(Base64.getDecoder().decode(values[i].substring(1)),StandardCharsets.UTF_8);
                        if(type==Types.CLOB || type==Types.NCLOB)statement.setCharacterStream(i+1,new StringReader(value),value.length());
                        else statement.setString(i+1,value);
                    }
                    statement.executeUpdate();count++;
                }
                if(count!=((Number)seed.get("count")).intValue())throw new IllegalStateException("初始化记录数不符 "+table);
                connection.commit();
            }catch(Exception error){connection.rollback();throw new IllegalStateException("初始化数据导入失败，表="+table+"，已处理="+count,error instanceof SQLException?new IllegalStateException("SQL错误码="+((SQLException)error).getErrorCode()):error);}
            finally{connection.setAutoCommit(true);}
        }
        System.out.println(table+" loaded="+count);
    }
    private static void createAdmin(Connection db) throws Exception {
        String root,serial;
        try(PreparedStatement q=db.prepareStatement("SELECT config_value FROM sym_config_t WHERE tenant_id=? AND config_group='RUNTIME' AND config_code='orgRootId' AND is_use=1 AND is_del=0")){
            q.setString(1,TENANT);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalStateException("公安根机构配置缺失");root=r.getString(1);}
        }
        try(PreparedStatement q=db.prepareStatement("SELECT serial_number FROM rm_org_t WHERE tenant_id=? AND id=? AND deleted=0")){
            q.setString(1,TENANT);q.setString(2,root);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalStateException("公安根机构记录缺失");serial=r.getString(1);}
        }
        db.setAutoCommit(false);
        try{
            insert(db,"INSERT INTO rm_user_t(id,tenant_id,user_name,real_name,password,status,deleted,create_time) VALUES(?,?,?,?,?,0,0,CURRENT_TIMESTAMP)",ADMIN,TENANT,"admin","公安管理员",encode(required("POLICE_ADMIN_PASSWORD")));
            insert(db,"INSERT INTO rm_user_org_rela_t(id,tenant_id,user_id,org_id,job_type,serial_number) VALUES(?,?,?,?,?,?)","police-demo-admin-org",TENANT,ADMIN,root,"1",serial);
            insert(db,"INSERT INTO rm_user_role_rela_t(id,tenant_id,user_id,role_id) VALUES(?,?,?,?)","police-demo-admin-membership",TENANT,ADMIN,ROLE);
            db.commit();
        }catch(Exception error){db.rollback();throw error;}
        finally{db.setAutoCommit(true);}
    }
    private static void insert(Connection db,String sql,String... values) throws SQLException {
        try(PreparedStatement statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setString(i+1,values[i]);statement.executeUpdate();}
    }
    private static String encode(String password){
        String[] salt=UUID.randomUUID().toString().toUpperCase(Locale.ROOT).split("-");
        return salt[4]+salt[0]+salt[2]+sm3(salt[0]+salt[1]+sm3(password)+salt[3]+salt[4])+salt[1]+salt[3];
    }
    private static String sm3(String value){
        byte[] input=value.getBytes(StandardCharsets.UTF_8),output=new byte[32];SM3Digest digest=new SM3Digest();digest.update(input,0,input.length);digest.doFinal(output,0);return HexFormat.of().formatHex(output);
    }
}
