package com.linewell.dataelement.platform.tenant.api;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.ssssssss.magicapi.core.annotation.MagicModule;
/** RFC 6238 crypto and Redis single-use challenges; binding, policy, replay steps and SQL stay in Magic. */
@Component
@MagicModule("iamMfa")
public class IamMfaMagicModule {
 private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
 private final SecureRandom random=new SecureRandom();
 private final ConcurrentHashMap<String,Map<String,Object>> challenges=new ConcurrentHashMap<>();
 private final StringRedisTemplate redis;
 /** Isolated unit tests only. Spring always uses the Redis constructor. */
 public IamMfaMagicModule(){this.redis=null;}
 @Autowired public IamMfaMagicModule(StringRedisTemplate redis){this.redis=redis;}
 private static final DefaultRedisScript<Long> CREATE=new DefaultRedisScript<>("redis.call('HSET',KEYS[1],'kind',ARGV[1],'id',ARGV[2],'version',ARGV[3],'purpose',ARGV[4],'expires',ARGV[5],'attempts','0');redis.call('EXPIRE',KEYS[1],300);return 1",Long.class);
 @SuppressWarnings("rawtypes") private static final DefaultRedisScript<List> ATTEMPT=new DefaultRedisScript<>("if redis.call('EXISTS',KEYS[1])==0 then return {} end;if redis.call('HGET',KEYS[1],'purpose')~=ARGV[1] or tonumber(redis.call('HGET',KEYS[1],'attempts'))>=5 then redis.call('DEL',KEYS[1]);return {} end;redis.call('HINCRBY',KEYS[1],'attempts',1);return redis.call('HMGET',KEYS[1],'kind','id','version','purpose','expires','attempts')",List.class);
 private static final DefaultRedisScript<Long> CONSUME=new DefaultRedisScript<>("return redis.call('DEL',KEYS[1])",Long.class);
 public String secret(){byte[] value=new byte[20];random.nextBytes(value);StringBuilder out=new StringBuilder();int buffer=0,bits=0;for(byte b:value){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;out.append(ALPHABET.charAt((buffer>>bits)&31));}}return out.toString();}
 public long matchingStep(String secret,String code){return matchingStepAt(secret,code,Instant.now().getEpochSecond());}
 long matchingStepAt(String secret,String code,long seconds){if(code==null || !code.matches("[0-9]{6}"))return -1;long step=seconds/30;for(long s=step-1;s<=step+1;s++)if(MessageDigest.isEqual(code(secret,s).getBytes(StandardCharsets.US_ASCII),code.getBytes(StandardCharsets.US_ASCII)))return s;return -1;}
 String code(String secret,long step){try{byte[] bytes=new byte[secret.length()*5/8];int buffer=0,bits=0,index=0;for(char c:secret.toUpperCase(Locale.ROOT).toCharArray()){int n=ALPHABET.indexOf(c);if(n<0)throw new IllegalArgumentException("TOTP密钥格式无效");buffer=(buffer<<5)|n;bits+=5;if(bits>=8){bits-=8;bytes[index++]=(byte)(buffer>>bits);}}var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(bytes,"HmacSHA1"));byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());int offset=hash[hash.length-1]&15;int value=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);return String.format(Locale.ROOT,"%06d",value%1000000);}catch(GeneralSecurityException e){throw new IllegalStateException("TOTP组件不可用",e);}}
 public String challenge(String kind,String id,long version,String purpose){
  long now=Instant.now().getEpochSecond();if(redis!=null){String raw=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");redis.execute(CREATE,List.of(redisKey(raw)),kind,id,Long.toString(version),purpose,Long.toString(now+300));return raw;}challenges.entrySet().removeIf(e->((Number)e.getValue().get("expires")).longValue()<now);
  if(challenges.size()>=10000)throw new IllegalStateException("认证挑战过多，请稍后重试");String raw=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");var item=new HashMap<String,Object>();item.put("kind",kind);item.put("id",id);item.put("version",version);item.put("purpose",purpose);item.put("expires",now+300);item.put("attempts",0);challenges.put(fingerprint(raw),item);return raw;
 }
 public String challenge(String kind,String id,Number version,String purpose){return challenge(kind,id,version.longValue(),purpose);}
 public synchronized Map<String,Object> attempt(String value,String purpose){if(value==null || !value.matches("[a-f0-9]{64}"))return Map.of();if(redis!=null){var result=redis.execute(ATTEMPT,List.of(redisKey(value)),purpose);if(result==null || result.size()!=6)return Map.of();return Map.of("kind",result.get(0),"id",result.get(1),"version",Long.parseLong(result.get(2).toString()),"purpose",result.get(3),"expires",Long.parseLong(result.get(4).toString()),"attempts",Integer.parseInt(result.get(5).toString()));}String key=fingerprint(value);var item=challenges.get(key);if(item==null || ((Number)item.get("expires")).longValue()<Instant.now().getEpochSecond() || !purpose.equals(item.get("purpose")) || ((Number)item.get("attempts")).intValue()>=5){challenges.remove(key);return Map.of();}item.put("attempts",((Number)item.get("attempts")).intValue()+1);return Map.copyOf(item);}
 public synchronized boolean consume(String value){if(value==null || !value.matches("[a-f0-9]{64}"))return false;return redis==null?challenges.remove(fingerprint(value))!=null:Long.valueOf(1).equals(redis.execute(CONSUME,List.of(redisKey(value))));}
 private String redisKey(String value){return "idaas:mfa:challenge:"+fingerprint(value);}
 private String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
