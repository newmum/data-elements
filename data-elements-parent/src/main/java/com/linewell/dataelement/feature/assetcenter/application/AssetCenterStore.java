package com.linewell.dataelement.feature.assetcenter.application;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit tenant predicates are intentional: JdbcTemplate does not use MyBatis tenant interceptors. */
@Component
public class AssetCenterStore {
    final JdbcTemplate jdbc;
    final ObjectMapper json;
    private final IdentityUserLookupMapper identities;
    public AssetCenterStore(JdbcTemplate jdbc, ObjectMapper json, IdentityUserLookupMapper identities) {
        this.jdbc=jdbc; this.json=json; this.identities=identities;
    }
    public String tenant() { return TenantContext.requireTenantId(); }
    public String user() { return String.valueOf(StpUtil.getLoginId()); }
    public List<String> organizations() { return identities.selectOrganizationIds(user(), tenant()); }
    public void organization(String id) {
        if (id.isBlank() || !organizations().contains(id)) throw error(403,"FORBIDDEN","不能代表该部门提交申请");
    }
    public boolean table(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Long.class,table)>0;
    }
    public boolean column(String table,String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",Long.class,table,column)>0;
    }
    public void require(String... tables) {
        for(String table:tables) if(!table(table)) throw error(503,"ASSET_SCHEMA_NOT_READY","资产中心数据库尚未升级，请执行皓月租户迁移后重试");
    }
    public Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        return rows.isEmpty()?null:lower(rows.get(0));
    }
    public List<Map<String,Object>> rows(String sql,Object...args) { return jdbc.queryForList(sql,args).stream().map(AssetCenterStore::lower).toList(); }
    public Map<String,Object> owned(String table,String id,boolean lock) {
        Map<String,Object> row=one("SELECT * FROM "+table+" WHERE tenant_id=? AND tid=?"+(column(table,"is_del")?" AND COALESCE(is_del,0)=0":"")+(lock?" FOR UPDATE":""),tenant(),id);
        if(row==null) throw error(404,"NOT_FOUND","对象不存在或无权访问");
        return row;
    }
    public void manage(Map<String,Object> row) {
        if(canManage(row)) return;
        throw error(403,"FORBIDDEN","只有登记人或所属部门成员可以维护该资产");
    }
    public boolean canManage(Map<String,Object> row) {
        return user().equals(text(row,"created_by")) || organizations().contains(text(row,"org_id"));
    }
    public void revision(Map<String,Object> row,Map<String,Object> input) {
        String expected=required(input,"expectedRevision",64),actual=text(row,"revision");
        if(actual.isBlank()) actual="0";
        if(!expected.equals(actual)) throw error(409,"REVISION_CONFLICT","记录已更新，请刷新后重试");
    }
    public void insert(String table,Map<String,Object> values) {
        String names=String.join(",",values.keySet().stream().map(k->"`"+k+"`").toList());
        String marks=String.join(",",Collections.nCopies(values.size(),"?"));
        jdbc.update("INSERT INTO "+table+" ("+names+") VALUES ("+marks+")",values.values().toArray());
    }
    public Map<String,Object> audit(String id) {return map("tid",id,"tenant_id",tenant(),"created_by",user(),"updated_by",user(),"created_time",now(),"updated_time",now(),"revision",0);}
    public Object idempotent(String operation,String key,Map<String,Object> input,Supplier<Object> action) {
        require("da_asset_command_t");
        if(key==null || !key.matches("[A-Za-z0-9._:-]{16,128}")) throw error(400,"INVALID_ARGUMENT","缺少有效的 Idempotency-Key");
        String opKey=hash(tenant()+"|"+user()+"|"+operation+"|"+key),bodyHash=hash(encode(new TreeMap<>(input)));
        // The durable event key provides cross-process serialization; transaction rollback removes unfinished claims.
        Map<String,Object> old=one("SELECT payload FROM da_asset_command_t WHERE tenant_id=? AND idempotency_key=? FOR UPDATE",tenant(),opKey);
        if(old!=null) return replay(old,bodyHash);
        String eventId=id();
        try {
            insert("da_asset_command_t",map("tid",eventId,"tenant_id",tenant(),"operation",operation,"payload",encode(map("requestHash",bodyHash)),"actor_user_id",user(),"idempotency_key",opKey,"created_time",now()));
        } catch(org.springframework.dao.DuplicateKeyException duplicate) {
            old=one("SELECT payload FROM da_asset_command_t WHERE tenant_id=? AND idempotency_key=? FOR UPDATE",tenant(),opKey);
            if(old==null) throw duplicate;
            return replay(old,bodyHash);
        }
        Object result=action.get();
        jdbc.update("UPDATE da_asset_command_t SET payload=? WHERE tenant_id=? AND tid=?",encode(map("requestHash",bodyHash,"response",result)),tenant(),eventId);
        return result;
    }
    private Object replay(Map<String,Object> row,String bodyHash) {
        Map<String,Object> payload=object(row.get("payload"));
        if(!bodyHash.equals(text(payload,"requestHash"))) throw error(409,"IDEMPOTENCY_CONFLICT","相同操作标识对应的内容已改变");
        if(!payload.containsKey("response")) throw error(409,"INVALID_STATE","操作仍在处理，请稍后重试");
        return payload.get("response");
    }
    public String encode(Object v) {try{return json.writeValueAsString(v);}catch(Exception e){throw error(400,"INVALID_ARGUMENT","数据格式不正确");}}
    public Map<String,Object> object(Object v) { if(v instanceof Map<?,?> m) return (Map<String,Object>)m; try{return v==null?new LinkedHashMap<>():json.readValue(String.valueOf(v),new TypeReference<>(){});}catch(Exception e){return new LinkedHashMap<>();} }
    public List<Map<String,Object>> array(Object v) {if(v instanceof List<?> l)return (List<Map<String,Object>>)l;try{return v==null?List.of():json.readValue(String.valueOf(v),new TypeReference<>(){});}catch(Exception e){return List.of();}}
    public static String required(Map<String,Object> v,String key,int max) {String s=text(v,key);if(s.isBlank()||s.length()>max)throw error(400,"INVALID_ARGUMENT",key+"不能为空且不能超过"+max+"字");return s;}
    public static String text(Map<String,Object> v,String k){Object x=v.get(k);return x==null?"":String.valueOf(x);}
    public static int number(Object v,int fallback){try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return fallback;}}
    public static Map<String,Object> map(Object... entries){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)out.put(String.valueOf(entries[i]),entries[i+1]);return out;}
    public static Map<String,Object> lower(Map<String,Object> in){Map<String,Object> out=new LinkedHashMap<>();in.forEach((k,v)->out.put(k.toLowerCase(Locale.ROOT),v));return out;}
    public static String id(){return UUID.randomUUID().toString().replace("-","");}
    public static Timestamp now(){return Timestamp.from(Instant.now());}
    public static String iso(Object v){if(v instanceof Timestamp t)return t.toInstant().toString();if(v instanceof java.time.LocalDateTime t)return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toString();return v==null?null:String.valueOf(v);}
    public static String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static AssetCenterException error(int status,String code,String message){return new AssetCenterException(status,code,message);}
}

