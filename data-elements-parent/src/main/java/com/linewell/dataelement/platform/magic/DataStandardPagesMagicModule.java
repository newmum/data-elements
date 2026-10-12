package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.persistence.ServerTableOrder;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Tenant-scoped standard pages. JSON_TABLE runs as native SQL with explicit ownership predicates. */
@Component
@MagicModule("dataStandardPages")
public class DataStandardPagesMagicModule {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final Map<String,Boolean> dameng = new ConcurrentHashMap<>();
    @Autowired
    public DataStandardPagesMagicModule(JdbcTemplate jdbc) {this(jdbc,new NamedParameterJdbcTemplate(jdbc));}
    DataStandardPagesMagicModule(JdbcTemplate jdbc,NamedParameterJdbcTemplate named) {this.jdbc=jdbc;this.named=named;}
    private static final String FROM = """

 FROM (SELECT original.*,codes.tid _code_id,ROW_NUMBER() OVER (
    PARTITION BY original.tid ORDER BY CASE WHEN codes.tid=original.value_domain_id THEN 0
       WHEN codes.dict_code=original.value_domain_id THEN 1 ELSE 2 END,codes.tid ASC
 ) _code_rank
 FROM da_metadata_t original LEFT JOIN da_metadata_code_t codes
    ON codes.tenant_id=original.tenant_id AND codes.is_del=0
      AND (codes.tid=original.value_domain_id OR codes.code_value='__HEADER__'
         AND (codes.dict_code=original.value_domain_id OR codes.code_set=original.value_domain_id))
 WHERE original.tenant_id=:tenantId AND original.is_del=0) m
 LEFT JOIN (SELECT c.tid,COUNT(j.ordinal) dictionary_count
    FROM da_metadata_code_t c LEFT JOIN JSON_TABLE(
      CASE WHEN JSON_VALID(c.dict_item_value)=1 AND SUBSTR(TRIM(c.dict_item_value),1,1)='['
         THEN c.dict_item_value ELSE '[]' END,'$[*]' COLUMNS(ordinal FOR ORDINALITY)) j ON 1=1
    WHERE c.tenant_id=:tenantId AND c.is_del=0 GROUP BY c.tid) counts ON counts.tid=m._code_id

""";
    private static final Map<String,String> FILTERS = Map.of("metaName","m.meta_name","metaCode","m.meta_code",
        "standardEncode","m.standard_encode","dataLevel","m.data_level","dataCategoryId","m.data_category_id",
        "publishStatus","m.publish_status","categoryPath","m.data_category_path","valueDomainId","m.value_domain_id");
    private static final Map<String,String> SORTS = Map.ofEntries(
        Map.entry("metaName","m.meta_name"),Map.entry("name","m.meta_name"),Map.entry("metaCode","m.meta_code"),Map.entry("code","m.meta_code"),
        Map.entry("standardEncode","m.standard_encode"),Map.entry("dictionaryCount","coalesce(counts.dictionary_count,0)"),
        Map.entry("fieldLength","m.field_length"),Map.entry("length","m.field_length"),Map.entry("createdTime","m.created_time"),
        Map.entry("updatedTime","m.updated_time"),Map.entry("updatedAt","m.updated_time"));
    public Map<String,Object> elements(Map<String,Object> request) {
        Map<String,Object> p=request==null?Map.of():request;
        String tenant=TenantContext.requireTenantId();
        if(TenantContext.usesControlDatabase() || TenantContext.isIgnored())throw new IllegalStateException("标准查询必须绑定行业租户");
        String order=ServerTableOrder.sql(text(p.get("sortField")),text(p.get("sortDir")),SORTS,"createdTime","desc","m.tid");
        int page=Math.max(1,number(p.get("pageNum"),1)),size=Math.min(100,Math.max(1,number(p.get("pageSize"),20)));
        Map<String,Object> args=new HashMap<>();args.put("tenantId",tenant);args.put("size",size);args.put("offset",Math.multiplyExact((long)page-1,size));
        StringBuilder where=new StringBuilder(" WHERE m._code_rank=1");
        FILTERS.forEach((key,column)->{
            String value=text(p.get(key));if(value.isEmpty())return;
            if(value.length()>500)throw new IllegalArgumentException("标准筛选内容过长");
            if(Set.of("metaName","metaCode","standardEncode","categoryPath").contains(key)){
                String escaped=value.replace("!","!!").replace("%","!%").replace("_","!_");
                args.put(key,key.equals("categoryPath")?escaped+"%":"%"+escaped+"%");where.append(" AND ").append(column).append(" LIKE :").append(key).append(" ESCAPE '!'");
            }else{args.put(key,value);where.append(" AND ").append(column).append("=:").append(key);}
        });
        boolean dm=dameng.computeIfAbsent(tenant,t->Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getMetaData().getURL().toLowerCase(Locale.ROOT).contains(":dm:"))));
        if(Boolean.TRUE.equals(p.get("encodingOnly")))where.append(" AND m.format_pattern IS NOT NULL AND TRIM(m.format_pattern)<>''");
        String base=FROM+where;
        Number count=named.queryForObject(adapt("SELECT COUNT(*)"+base,dm),args,Number.class);
        List<Map<String,Object>> rows=named.queryForList(adapt("SELECT m.*,coalesce(counts.dictionary_count,0) dictionary_count"+base+" ORDER BY "+order+" LIMIT :size OFFSET :offset",dm),args);
        List<Map<String,Object>> mapped=new ArrayList<>();
        for(Map<String,Object> raw:rows){Map<String,Object> row=new LinkedHashMap<>();raw.forEach((key,value)->{String[] words=key.toLowerCase(Locale.ROOT).split("_");StringBuilder camel=new StringBuilder(words[0]);for(int i=1;i<words.length;i++)if(!words[i].isEmpty())camel.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));row.put(camel.toString(),value);});mapped.add(row);}
        return Map.of("list",mapped,"total",count==null?0:count.longValue(),"pageNum",page,"pageSize",size);
    }
    public Map<String,Object> codes(Map<String,Object> request) {
        Map<String,Object> p=request==null?Map.of():request;
        String tenant=TenantContext.requireTenantId();
        if(TenantContext.usesControlDatabase()||TenantContext.isIgnored())throw new IllegalStateException("标准查询必须绑定行业租户");
        int page=Math.max(1,number(p.get("pageNum"),1)),size=Math.min(100,Math.max(1,number(p.get("pageSize"),20)));
        Map<String,Object> args=new HashMap<>();args.put("tenantId",tenant);args.put("size",size);args.put("offset",Math.multiplyExact((long)page-1,size));
        StringBuilder where=new StringBuilder(" WHERE c.tenant_id=:tenantId AND c.is_del=0 AND c.code_value='__HEADER__'");
        Map<String,String> filters=Map.of("dictName","dict_name","dictCode","dict_code","categoryPath","data_category_path");
        filters.forEach((key,column)->{String value=text(p.get(key));if(value.isEmpty())return;if(value.length()>500)throw new IllegalArgumentException("标准筛选内容过长");
            String escaped=value.replace("!","!!").replace("%","!%").replace("_","!_");args.put(key,key.equals("categoryPath")?escaped+"%":"%"+escaped+"%");where.append(" AND c.").append(column).append(" LIKE :").append(key).append(" ESCAPE '!'");});
        String references="""
          LEFT JOIN (SELECT ranked._code_id,COUNT(*) reference_count FROM (
            SELECT original.tid,codes.tid _code_id,ROW_NUMBER() OVER(PARTITION BY original.tid ORDER BY
              CASE WHEN codes.tid=original.value_domain_id THEN 0 WHEN codes.dict_code=original.value_domain_id THEN 1 ELSE 2 END,codes.tid) _rank
            FROM da_metadata_t original JOIN da_metadata_code_t codes ON codes.tenant_id=original.tenant_id AND codes.is_del=0
              AND (codes.tid=original.value_domain_id OR codes.code_value='__HEADER__' AND (codes.dict_code=original.value_domain_id OR codes.code_set=original.value_domain_id))
            WHERE original.tenant_id=:tenantId AND original.is_del=0
          ) ranked WHERE ranked._rank=1 GROUP BY ranked._code_id) refs ON refs._code_id=c.tid
          LEFT JOIN (SELECT codes.tid,COUNT(j.ordinal) item_count FROM da_metadata_code_t codes
            LEFT JOIN JSON_TABLE(CASE WHEN JSON_VALID(codes.dict_item_value)=1 AND SUBSTR(TRIM(codes.dict_item_value),1,1)='['
              THEN codes.dict_item_value ELSE '[]' END,'$[*]' COLUMNS(ordinal FOR ORDINALITY)) j ON 1=1
            WHERE codes.tenant_id=:tenantId AND codes.is_del=0 GROUP BY codes.tid) counts ON counts.tid=c.tid
          """;
        String order=ServerTableOrder.sql(text(p.get("sortField")),text(p.get("sortDir")),Map.ofEntries(
          Map.entry("name","c.dict_name"),Map.entry("dictName","c.dict_name"),Map.entry("dictCode","c.dict_code"),
          Map.entry("itemCount","coalesce(counts.item_count,0)"),Map.entry("referenceCount","coalesce(refs.reference_count,0)"),
          Map.entry("createdTime","c.created_time"),Map.entry("updatedAt","c.updated_time"),Map.entry("updatedTime","c.updated_time")),"createdTime","desc","c.tid");
        boolean dm=dameng.computeIfAbsent(tenant,key->Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection->connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("dm"))));
        Number total=named.queryForObject(adapt("SELECT COUNT(*) FROM da_metadata_code_t c"+where,dm),args,Number.class);
        List<Map<String,Object>> rows=named.queryForList(adapt("SELECT c.*,coalesce(counts.item_count,0) item_count,coalesce(refs.reference_count,0) reference_count FROM da_metadata_code_t c "+references+where+" ORDER BY "+order+" LIMIT :size OFFSET :offset",dm),args);
        List<Map<String,Object>> mapped=new ArrayList<>();
        for(var raw:rows){Map<String,Object> row=new LinkedHashMap<>();raw.forEach((key,value)->{String[] words=key.toLowerCase(Locale.ROOT).split("_");StringBuilder camel=new StringBuilder(words[0]);for(int i=1;i<words.length;i++)if(!words[i].isEmpty())camel.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));row.put(camel.toString(),value);});mapped.add(row);}
        return Map.of("list",mapped,"total",total==null?0:total.longValue(),"pageNum",page,"pageSize",size);
    }
    private static String adapt(String sql,boolean dm) {
        if(!dm)return sql;
        sql=sql.replace("da_metadata_code_t","\"da_metadata_code_t\"").replace("da_metadata_t","\"da_metadata_t\"");
        // Physical lowercase identifiers are quoted; derived aliases remain native SQL identifiers.
        return sql.replaceAll("\\b(original|codes|c|m)\\.(tid|tenant_id|is_del|value_domain_id|dict_code|code_set|code_value|dict_item_value|meta_name|meta_code|standard_encode|data_level|data_category_id|publish_status|data_category_path|field_length|format_pattern|dict_name|data_category_path|created_time|updated_time)\\b", "$1.\"$2\"");
    }
    private static int number(Object value,int fallback){return value==null?fallback:Integer.parseInt(value.toString());}
    private static String text(Object value){return value==null?"":value.toString().trim();}
}
