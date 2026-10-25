package com.linewell.dataelement.platform.audit;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Persistent keyed pseudonym, independent of provisioning/encryption credentials. Never exposes the key. */
@Component
public class AuditFingerprint {
    private final byte[] key;
    public AuditFingerprint(@Value("${DATA_ELEMENTS_AUDIT_FINGERPRINT_KEY:}") String configured,@Value("${data-element.audit.fingerprint-key-file:${user.home}/.data-elements/audit-fingerprint.key}") String filename){
        try{
            String encoded=configured;
            if(encoded==null||encoded.isBlank()){
                Path file=Path.of(filename).toAbsolutePath().normalize();Files.createDirectories(file.getParent());
                if(!Files.exists(file)){
                    byte[] candidate=new byte[32];new SecureRandom().nextBytes(candidate);
                    try{Files.writeString(file,Base64.getEncoder().encodeToString(candidate),StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);}
                    catch(FileAlreadyExistsException anotherInstance){/* Read the established key. */}
                    try{Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException windows){/* User-home ACLs apply. */}
                }
                if(Files.isSymbolicLink(file)||Files.size(file)>128)throw new IllegalStateException("日志指纹密钥文件无效");
                encoded=Files.readString(file,StandardCharsets.US_ASCII).trim();
            }
            key=Base64.getDecoder().decode(encoded);if(key.length!=32)throw new IllegalStateException("日志指纹密钥必须是32字节Base64");
        }catch(Exception error){throw new IllegalStateException("日志指纹密钥不可用，请检查独立配置或受限文件目录",error);}
    }
    public String fingerprint(String value,String purpose){
        if(value==null||value.length()>512||purpose==null||!purpose.matches("[A-Z0-9_]{1,64}"))throw new IllegalArgumentException("账号指纹输入无效");
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((purpose+"\u0000"+value).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error){throw new IllegalStateException("账号指纹生成失败",error);}
    }
}
