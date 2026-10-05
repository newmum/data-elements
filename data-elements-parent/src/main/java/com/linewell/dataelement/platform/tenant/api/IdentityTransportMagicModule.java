package com.linewell.dataelement.platform.tenant.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.json.JSONNull;
import java.util.ArrayList;
import java.util.Collection;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import cn.hutool.json.JSONUtil;

/** Transport and cryptography only; dispatch, trust records and receipts remain in Magic. */
@Component
@MagicModule("identityTransport")
public class IdentityTransportMagicModule {
    private final String masterKey;
    private final boolean allowLocalHttp;
    private volatile HttpClient client;
    @Value("${idaas.transport.allowed-hosts:localhost}")
    private String allowedHosts = "localhost";

    public String canonicalJson(Object value) {
        try { return new ObjectMapper().writeValueAsString(canonical(value)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("资料不能编码为规范JSON", e); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> parseJson(String value) {
        try { return new ObjectMapper().readValue(value, Map.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("协议JSON格式不正确", e); }
    }
    public String canonicalText(String value) { return canonicalJson(parseJson(value)); }
    public String protectedFingerprint(String value,String context)throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(encryptionKey().getEncoded(),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((context+"\n"+value).getBytes(StandardCharsets.UTF_8)));
    }
    public Map<String,Object> sendVerification(String endpoint,String stored,String provider,String requestId,String payload)throws Exception {
        validateUrl(endpoint);String host=URI.create(endpoint).getHost();if(java.util.Arrays.stream(allowedHosts.split(",")).map(String::trim).noneMatch(h->h.equalsIgnoreCase(host)))throw new IllegalArgumentException("核验地址未列入部署允许清单");
        if(payload.length()>16384)throw new IllegalArgumentException("核验报文超限");
        String signature=signature2(decrypt(stored),"IDAAS_VERIFY/1",provider,requestId,0,"",payload);
        var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("X-IDAAS-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var response=httpClient().send(request,HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] value=stream.readNBytes(16385);if(value.length>16384 || response.statusCode()!=200)throw new IllegalStateException("核验服务响应异常");var result=parseJson(new String(value,StandardCharsets.UTF_8));Object receipt=result.get("receipt");String returned=(String)result.get("signature");String expected=signature2(decrypt(stored),"IDAAS_VERIFY/1",provider,requestId,0,"",canonicalJson(receipt));if(returned==null || !MessageDigest.isEqual(returned.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)))throw new IllegalStateException("核验回执签名无效");@SuppressWarnings("unchecked")Map<String,Object> verified=(Map<String,Object>)receipt;return verified;}
    }
    private Object canonical(Object value) {
        if (value == null || value == JSONNull.NULL) return null;
        if (value instanceof Map<?, ?> map) { var sorted = new TreeMap<String, Object>(); map.forEach((k,v)->sorted.put(String.valueOf(k),canonical(v))); return sorted; }
        if (value instanceof Collection<?> list) { var result=new ArrayList<Object>(); list.forEach(v->result.add(canonical(v))); return result; }
        return value;
    }
    public String signature2(String secret, String keyId, String app, String instance, long timestamp, String nonce, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String input="IDAAS/2\n"+keyId+"\n"+app+"\n"+instance+"\n"+timestamp+"\n"+nonce+"\n"+payload;
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }
    public boolean verify2(String stored, String keyId, String app, String instance, long timestamp, String nonce, String payload, String signature) throws Exception {
        if (Math.abs(Instant.now().getEpochSecond()-timestamp)>300 || nonce==null || !nonce.matches("[a-f0-9]{32}") || payload==null || payload.length()>65536 || signature==null || !signature.matches("[a-f0-9]{64}")) return false;
        return MessageDigest.isEqual(signature2(decrypt(stored),keyId,app,instance,timestamp,nonce,payload).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII));
    }
    public String receiptSignature(String stored, Object receipt) throws Exception { return signature2(decrypt(stored),"ACK","","",0,"",canonicalJson(receipt)); }
    public boolean verifyReceipt(String stored, Object receipt, String signature) throws Exception {
        return signature!=null && MessageDigest.isEqual(receiptSignature(stored,receipt).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII));
    }
    public Map<String,Object> send2(String endpoint,String stored,String keyId,String app,String instance,String payload,int timeout) throws Exception {
        validateUrl(endpoint); String host=URI.create(endpoint).getHost();
        if (java.util.Arrays.stream(allowedHosts.split(",")).map(String::trim).noneMatch(h->h.equalsIgnoreCase(host))) throw new IllegalArgumentException("接收地址主机未列入部署允许清单");
        if (timeout<1 || timeout>30 || payload.length()>65536) throw new IllegalArgumentException("下发参数超限");
        long timestamp=Instant.now().getEpochSecond();String nonce=java.util.UUID.randomUUID().toString().replace("-","");
        String signature=signature2(decrypt(stored),keyId,app,instance,timestamp,nonce,payload);
        String body=canonicalJson(Map.of("protocol","IDAAS/2","keyId",keyId,"targetAppId",app,"receiverInstanceId",instance,"timestamp",timestamp,"nonce",nonce,"payload",payload,"signature",signature));
        var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(timeout)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=httpClient().send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()) {byte[] bytes=stream.readNBytes(65537);if(bytes.length>65536 || response.statusCode()!=200) return Map.of("code",502,"message","接收端HTTP响应异常","httpStatus",response.statusCode());var result=new java.util.LinkedHashMap<String,Object>(parseJson(new String(bytes,StandardCharsets.UTF_8)));result.put("httpStatus",response.statusCode());return result;}
    }

    private synchronized HttpClient httpClient() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return client;
    }

    public IdentityTransportMagicModule(
            @Value("${idaas.transport.master-key:${IDAAS_TRANSPORT_KEY:}}") String masterKey,
            @Value("${idaas.transport.allow-local-http:${IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP:false}}") boolean allowLocalHttp) {
        this.masterKey = masterKey;
        this.allowLocalHttp = allowLocalHttp;
    }
    private SecretKeySpec encryptionKey() {
        byte[] key;
        try { key = Base64.getDecoder().decode(masterKey); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("下发密钥加密配置无效"); }
        if (key.length != 32) throw new IllegalStateException("请配置独立的 IDAAS_TRANSPORT_KEY（32字节Base64）");
        return new SecretKeySpec(key, "AES");
    }
    public String encrypt(String secret) throws Exception {
        if (secret == null || secret.length() < 32 || secret.length() > 256)
            throw new IllegalArgumentException("接入密钥长度应为32–256个字符");
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
        return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
    }
    public String decrypt(String stored) throws Exception {
        String[] parts = stored.split(":", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("接入密钥格式无效");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    }
    /** Generic protected field primitive. Business field policy and SQL stay in Magic. */
    public String protectValue(String value, String context) throws Exception {
        if (value == null || value.length() > 4096 || context == null || context.length() > 256)
            throw new IllegalArgumentException("受保护字段参数超限");
        return protect(value,context);
    }
    public String protectState(String value,String context)throws Exception {
        if(value==null || value.length()>524288 || context==null || context.length()>256)throw new IllegalArgumentException("认证状态参数超限");
        return protect(value,"protocol:"+context);
    }
    private String protect(String value,String context)throws Exception {
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return "data1:" + Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    public String unprotectValue(String stored, String context) throws Exception {
        if (stored == null || stored.length() > 12000 || context == null || context.length() > 256)
            throw new IllegalArgumentException("受保护字段参数超限");
        return unprotect(stored,context);
    }
    public String unprotectState(String value,String context)throws Exception {
        if(value==null || value.length()>800000 || context==null || context.length()>256)throw new IllegalArgumentException("认证状态参数超限");
        return unprotect(value,"protocol:"+context);
    }
    private String unprotect(String stored,String context)throws Exception {
        String[] parts = stored.split(":", -1);
        if (parts.length != 3 || !"data1".equals(parts[0])) throw new IllegalArgumentException("受保护字段格式无效");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    }
    public String validateUrl(String url) {
        URI uri = URI.create(url);
        boolean local = "localhost".equalsIgnoreCase(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) ||
                    (allowLocalHttp && local && "http".equalsIgnoreCase(uri.getScheme()))))
            throw new IllegalArgumentException("接收地址须使用HTTPS；本地开发仅允许显式开启的localhost HTTP");
        return uri.toString();
    }
    public String signature(String secret, String keyId, String tenant, long timestamp, String nonce, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String input = "IDAAS/1\n" + keyId + "\n" + tenant + "\n" + timestamp + "\n" + nonce + "\n" + payload;
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }
    public boolean verify(String encryptedSecret, String keyId, String tenant, long timestamp, String nonce,
                          String payload, String signature) throws Exception {
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300 || nonce == null
                || !nonce.matches("[a-fA-F0-9]{32}") || payload == null || payload.length() > 65536
                || signature == null || !signature.matches("[a-fA-F0-9]{64}")) return false;
        return MessageDigest.isEqual(signature(decrypt(encryptedSecret), keyId, tenant, timestamp, nonce, payload)
                .getBytes(StandardCharsets.US_ASCII), signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
    public Map<String, Object> send(String endpoint, String encryptedSecret, String keyId, String tenant,
                                    String payload, int timeout) throws Exception {
        validateUrl(endpoint);
        if (timeout < 1 || timeout > 30 || payload.length() > 65536) throw new IllegalArgumentException("下发参数超限");
        long timestamp = Instant.now().getEpochSecond();
        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        String signature = signature(decrypt(encryptedSecret), keyId, tenant, timestamp, nonce, payload);
        String body = JSONUtil.toJsonStr(Map.of("protocol", "IDAAS/1", "keyId", keyId, "targetTenantId", tenant,
                "timestamp", timestamp, "nonce", nonce, "payload", payload, "signature", signature));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.io.InputStream> response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            byte[] bytes = stream.readNBytes(65537);
            if (bytes.length > 65536 || response.statusCode() != 200) return Map.of("code", 502, "message", "接收端HTTP响应异常");
            return JSONUtil.parseObj(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
