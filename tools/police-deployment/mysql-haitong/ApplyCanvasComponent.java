import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Selected deployment-resource update, not an application endpoint or business initializer. */
public class ApplyCanvasComponent {
    static final String[] FILES={"source.vue","script.js","style.css"};
    static String hash(String value) throws Exception {
        return value==null?"-":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    static String[] hashes(String[] values) throws Exception {
        String[] result=new String[3];for(int i=0;i<3;i++)result[i]=hash(values[i]);return result;
    }
    static String[] expected(Properties properties,String prefix) {
        return new String[]{properties.getProperty(prefix+"0"),properties.getProperty(prefix+"1"),properties.getProperty(prefix+"2")};
    }
    static String[] row(Connection connection,String id) throws SQLException {
        try(var statement=connection.prepareStatement("select name,tenant_id,source_code,compile_js,compile_css from ui_component_t where tid=? and is_del=0 for update")) {
            statement.setString(1,id);
            try(var result=statement.executeQuery()) {
                if(!result.next()||!"schedule-setting".equals(result.getString(1))||!"00000000000000000000000000000000".equals(result.getString(2)))
                    throw new IllegalStateException("未找到R3任务配置组件，未修改数据");
                String[] values={result.getString(3),result.getString(4),result.getString(5)};
                if(result.next())throw new IllegalStateException("组件身份重复，未修改数据");
                return values;
            }
        }
    }
    static void update(Connection connection,String id,String[] values) throws SQLException {
        try(var statement=connection.prepareStatement("update ui_component_t set source_code=?,compile_js=?,compile_css=?,updated_time=current_timestamp where tid=? and is_del=0")) {
            for(int i=0;i<3;i++)statement.setString(i+1,values[i]);statement.setString(4,id);
            if(statement.executeUpdate()!=1)throw new IllegalStateException("组件更新数量异常");
        }
    }
    static void backup(Path backup,String[] values) throws Exception {
        Files.createDirectories(backup);
        Properties metadata=new Properties();
        for(int i=0;i<3;i++) {
            metadata.setProperty("null."+i,Boolean.toString(values[i]==null));
            Files.writeString(backup.resolve(FILES[i]),values[i]==null?"":values[i],StandardCharsets.UTF_8);
        }
        try(var writer=Files.newBufferedWriter(backup.resolve("backup.properties"),StandardCharsets.UTF_8)){metadata.store(writer,"Selected public component before update; no connection credentials");}
    }
    static String[] readBackup(Path backup) throws Exception {
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(backup.resolve("backup.properties"),StandardCharsets.UTF_8)){properties.load(reader);}
        String[] values=new String[3];for(int i=0;i<3;i++)values[i]=Boolean.parseBoolean(properties.getProperty("null."+i))?null:Files.readString(backup.resolve(FILES[i]));return values;
    }
    static void execute(String action,Path payload,Path backup) throws Exception {
        if(!Set.of("check","apply","restore").contains(action))throw new IllegalArgumentException("更新操作不合法");
        Properties metadata=new Properties();
        try(var reader=Files.newBufferedReader(payload.resolve("component.properties"),StandardCharsets.UTF_8)){metadata.load(reader);}
        String[] next=new String[3];for(int i=0;i<3;i++)next[i]=Files.readString(payload.resolve(FILES[i]));
        if(!Arrays.equals(hashes(next),expected(metadata,"after.")))throw new IllegalStateException("组件更新文件摘要不符");
        Properties credentials=new Properties();credentials.setProperty("user",System.getenv("CANVAS_CONTROL_USER"));credentials.setProperty("password",System.getenv("CANVAS_CONTROL_PASSWORD"));
        credentials.setProperty("connectTimeout","5000");credentials.setProperty("socketTimeout","15000");
        try(var connection=DriverManager.getConnection(System.getenv("CANVAS_CONTROL_URL"),credentials)) {
            connection.setAutoCommit(false);
            try {
                String id=metadata.getProperty("id");String[] current=row(connection,id);String[] currentHashes=hashes(current);
                boolean before=Arrays.equals(currentHashes,expected(metadata,"before."));
                boolean after=Arrays.equals(currentHashes,expected(metadata,"after."));
                if(!before&&!after)throw new IllegalStateException("任务配置组件有其他修改，已拒绝覆盖，请先核对生产版本");
                if("check".equals(action)){connection.rollback();System.out.println("任务配置组件身份和版本检查通过");return;}
                if("restore".equals(action)) {
                    if(after&&Files.exists(backup.resolve("backup.properties"))) {
                        String[] original=readBackup(backup);
                        if(!Arrays.equals(hashes(original),expected(metadata,"before.")))throw new IllegalStateException("组件备份摘要不符");
                        update(connection,id,original);connection.commit();
                    } else connection.rollback();
                    System.out.println("组件回退检查完成");return;
                }
                if(after){connection.rollback();System.out.println("任务配置组件已经是修复版，无需重复写入");return;}
                backup(backup,current);update(connection,id,next);
                if(!Arrays.equals(hashes(row(connection,id)),expected(metadata,"after.")))throw new IllegalStateException("组件写入后回读不一致");
                connection.commit();System.out.println("任务配置组件源码、JS和CSS已更新并回读核验");
            } catch(Exception error){connection.rollback();throw error;}
        }
    }
    public static void main(String[] args) {
        try{execute(args[0],Path.of(args[1]),Path.of(args[2]));}
        catch(IllegalStateException|IllegalArgumentException error){System.err.println(error.getMessage());System.exit(1);}
        catch(Exception error){System.err.println("组件连接或更新失败，未输出数据库连接和口令；请检查控制库权限、网络和JDK21");System.exit(1);}
    }
}
