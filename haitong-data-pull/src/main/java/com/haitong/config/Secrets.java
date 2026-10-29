package com.haitong.config;

import com.haitong.core.Rules;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

@Component
public final class Secrets {
    public record Identity(String id,boolean admin,Set<String> channels,Set<String> actions) {
        public boolean channel(String code) { return admin || channels.contains("*") || channels.contains(code); }
        public boolean action(String code) { return admin || actions.contains("*") || actions.contains(code); }
    }
    private record Client(String key,Identity identity) {}
    private final List<Client> clients=new ArrayList<>();
    private final String adminKey;
    private final Path root;
    public Secrets(RelayProperties p) throws IOException {
        root=Path.of(p.getWorkDir()).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("keys"));
        adminKey=loadOrGenerate(p.getSecurity().getAdminKey(),"admin.key");
        // Unvalidated push requests use a fixed non-admin identity. Business
        // keys are neither loaded nor required in this mode; admin remains protected.
        if (!p.getSecurity().isPushValidationEnabled()) return;
        if(p.getSecurity().getClients().isEmpty() || !p.getSecurity().getApiKey().isBlank()) {
            String key=loadOrGenerate(p.getSecurity().getApiKey(),"client.key");
            clients.add(new Client(key,new Identity("default",false,Set.of("*"),Set.of("*"))));
        }
        for(var entry:p.getSecurity().getClients().entrySet()) {
            Rules.code(entry.getKey());
            if(Set.of("admin","default").contains(entry.getKey())) throw new IllegalStateException("客户端编号 admin/default 为保留名称");
            String key=resolve(entry.getValue().getApiKey()); check(key);
            clients.add(new Client(key,new Identity(entry.getKey(),false,Set.copyOf(entry.getValue().getChannels()),Set.copyOf(entry.getValue().getActions()))));
        }
        Set<String> keys=new HashSet<>(); keys.add(adminKey);
        for(Client client:clients) if(!keys.add(client.key())) throw new IllegalStateException("客户端与管理员密钥必须不同，且客户端密钥不得重复");
    }
    public String resolve(String value) {
        if(value==null || value.isBlank()) return "";
        if(value.startsWith("env:")) {
            String name=value.substring(4);
            if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("非法环境变量引用");
            String s=System.getenv(name);
            if(s==null || s.isBlank()) throw new IllegalStateException("必需的环境变量未设置："+name);
            return s;
        }
        if(value.startsWith("file:")) {
            Path safeRoot=Path.of("config/secrets").toAbsolutePath().normalize();
            Path path=Rules.within(safeRoot,value.substring(5));
            try {
                if(!path.toRealPath().startsWith(safeRoot.toRealPath())) throw new IllegalArgumentException("密钥符号链接越界");
                if(Files.size(path)>16384) throw new IllegalArgumentException("密钥文件过大");
                return Files.readString(path,StandardCharsets.UTF_8).strip();
            } catch(IOException e) { throw new IllegalStateException("无法读取配置指定的密钥文件",e); }
        }
        return value;
    }
    public Identity client(String key) {
        for(Client c:clients) if(Rules.constantEquals(c.key(),key)) return c.identity();
        return null;
    }
    public Identity admin(String key) { return Rules.constantEquals(adminKey,key)?new Identity("admin",true,Set.of("*"),Set.of("*")):null; }
    private String loadOrGenerate(String configured,String name) throws IOException {
        if(!configured.isBlank()) { String key=resolve(configured); check(key); return key; }
        Path path=root.resolve("keys").resolve(name);
        if(!Files.exists(path)) {
            byte[] b=new byte[32];new SecureRandom().nextBytes(b);
            String value=Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            try { Files.writeString(path,value+"\n",StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE); }
            catch(FileAlreadyExistsException ignored) { /* Another startup wrote it; read that value below. */ }
            try { Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------")); }
            catch(UnsupportedOperationException ignored) { /* Windows ACL is managed by the deploying account. */ }
        }
        if(Files.isSymbolicLink(path)) throw new IllegalStateException("自动生成的密钥文件不得是符号链接");
        String value=Files.readString(path).strip();check(value);return value;
    }
    private static void check(String key) {
        if(key==null || key.length()<24 || key.length()>512 || key.chars().anyMatch(c->c<33 || c>126))
            throw new IllegalStateException("API 密钥必须至少 24 字符，建议使用随机生成的 32 字节密钥");
    }
}
