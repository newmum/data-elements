package com.linewell.dataelement.metautil.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Temporary, server-owned physical exploration results. No business records are duplicated in storage. */
@RestController
@MagicModule("metadataDiffView")
@RequestMapping("/dst/database/metadata/tables/diff-view")
public class MetadataDiffViewController {
    private record View(String tenant,String actor,String source,long expires,List<Map<String,Object>> rows) {}
    private final Map<String,View> views=new LinkedHashMap<>();
    private final JdbcTemplate jdbc;
    private final DataScopeAuthorizationService scopes;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public MetadataDiffViewController(JdbcTemplate jdbc,DataScopeAuthorizationService scopes){this(jdbc,scopes,Clock.systemUTC());}
    MetadataDiffViewController(JdbcTemplate jdbc,DataScopeAuthorizationService scopes,Clock clock){this.jdbc=jdbc;this.scopes=scopes;this.clock=clock;}

    /** Invoked only by the authorized server exploration script, never from a browser body. */
    public synchronized Map<String,Object> store(String source,Map<String,Object> result) {
        String tenant=TenantContext.requireTenantId(),actor=StpUtil.getLoginIdAsString();requireSource(tenant,source);
        var rows=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();
        for(String kind:List.of("added","modified","deleted","unchanged")) {
            Object input=result.get(kind);if(!(input instanceof Collection<?> group))throw new IllegalArgumentException("探查结果格式无效");
            for(Object value:group){
                if(!(value instanceof Map<?,?> original))throw new IllegalArgumentException("探查记录格式无效");
                Map<?,?> values="modified".equals(kind)&&original.get("after") instanceof Map<?,?> after?after:original;
                var row=new LinkedHashMap<String,Object>();values.forEach((k,v)->{if(!"columns".equals(k))row.put(String.valueOf(k),v);});
                if("modified".equals(kind)&&original.get("before") instanceof Map<?,?> before){
                    row.put("before",before);row.put("changes",original.get("changes"));row.put("tid",before.get("tid"));
                }
                String name=text(row.get("tableName"));if(name.isEmpty()||!keys.add(name))throw new IllegalArgumentException("探查表名为空或重复");
                row.put("changeStatus",Map.of("added","新增","modified","变更","deleted","删除","unchanged","无变化").get(kind));
                if(text(row.get("businessType")).isEmpty())row.put("businessType","业务表");
                row.put("registered",!"added".equals(kind));row.put("tableAssetId",row.get("tid"));
                row.put("physicallyDeleted","deleted".equals(kind));rows.add(Collections.unmodifiableMap(row));
                if(rows.size()>10000)throw new IllegalArgumentException("本次探查超过10000张表，请缩小数据源范围");
            }
        }
        if(rows.size()>10000)throw new IllegalArgumentException("本次探查超过10000张表，请缩小数据源范围");
        purge();views.entrySet().removeIf(e->e.getValue().tenant.equals(tenant)&&e.getValue().actor.equals(actor)&&e.getValue().source.equals(source));
        // Bound retained work across users and sources as well as each individual exploration.
        while(views.size()>=64||retainedRows()+rows.size()>100000
            ||views.values().stream().filter(v->v.tenant.equals(tenant)&&v.actor.equals(actor)).count()>=4){
            String oldest=views.entrySet().stream()
                .filter(e->views.values().stream().filter(v->v.tenant.equals(tenant)&&v.actor.equals(actor)).count()<4
                    ||e.getValue().tenant.equals(tenant)&&e.getValue().actor.equals(actor))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
            views.remove(oldest);
        }
        String id=UUID.randomUUID().toString();views.put(id,new View(tenant,actor,source,clock.millis()+600_000,List.copyOf(rows)));
        return Map.of("viewId",id,"summary",result.get("summary"),"total",rows.size());
    }

    @PostMapping("/page")
    public Map<String,Object> page(@RequestBody Map<String,Object> body) {
        StpUtil.checkLogin();String tenant=TenantContext.requireTenantId(),actor=StpUtil.getLoginIdAsString();
        View view;
        synchronized(this){purge();view=views.get(text(body.get("viewId")));}
        if(view==null||!view.tenant.equals(tenant)||!view.actor.equals(actor))throw new IllegalArgumentException("探查结果已过期，请刷新数据表");
        requireSource(tenant,view.source);
        return Map.of("code",0,"data",ordered(view.rows,body));
    }
    private void requireSource(String tenant,String source){
        var rows=jdbc.queryForList("SELECT tid,tenant_id,created_by,org_id FROM db_datasource_t WHERE tenant_id=? AND tid=? AND is_del=0",tenant,source);
        // Registration exploration uses the current tenant's source; it does not reapply menu OWNER/ORG scope.
        if(rows.size()!=1)throw new IllegalArgumentException("数据源不存在或已删除");
    }
    private synchronized void purge(){views.entrySet().removeIf(e->e.getValue().expires<=clock.millis());}
    private int retainedRows(){return views.values().stream().mapToInt(view->view.rows.size()).sum();}
    static Map<String,Object> ordered(List<Map<String,Object>> originals,Map<String,Object> body){
        int page=number(body.get("pageNo"),1),size=number(body.get("pageSize"),20);
        if(page<1||page>100000||size<1||size>100)throw new IllegalArgumentException("分页参数无效，每页最多100项");
        String field=text(body.get("sortField"));if(field.isEmpty())field="tableName";
        if(!Set.of("tableName","fieldCount","recordCount","createdTime","updatedTime").contains(field))throw new IllegalArgumentException("不支持的排序字段");
        String direction=text(body.get("sortDir"));if(direction.isEmpty())direction="asc";
        if(!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("排序方向无效");
        String business=text(body.get("businessType")),change=text(body.get("changeStatus"));
        var overrides=body.get("overrides") instanceof Map<?,?> m?m:Map.of();
        if(overrides.size()>1000)throw new IllegalArgumentException("本次预览标注超过1000项，请先保存");
        var ids=new HashSet<String>();for(var row:originals)ids.add(text(row.get("tableName")));
        if(overrides.keySet().stream().anyMatch(id->!ids.contains(String.valueOf(id))))throw new IllegalArgumentException("包含不属于当前探查的数据表");
        var rows=new ArrayList<Map<String,Object>>();var stats=new HashMap<String,Integer>();var changes=new HashMap<String,Integer>();
        int tables=0,viewCount=0;long fields=0;
        for(var original:originals){
            var row=new LinkedHashMap<>(original);Object patch=overrides.get(text(row.get("tableName")));
            if(patch instanceof Map<?,?> m){for(String key:List.of("businessType","tableComment")){if(m.containsKey(key)){String value=text(m.get(key));if(value.length()>1000)throw new IllegalArgumentException("预览标注过长");row.put(key,value);}}}
            stats.merge(text(row.get("businessType")),1,Integer::sum);changes.merge(text(row.get("changeStatus")),1,Integer::sum);
            if(!business.isEmpty()&&!business.equals("全部")&&!business.equals(text(row.get("businessType"))))continue;
            if(!change.isEmpty()&&!change.equals("全部")&&!change.equals(text(row.get("changeStatus"))))continue;
            if("视图".equals(row.get("tableType")))viewCount++;else tables++;
            fields+=longNumber(row.get("fieldCount"));rows.add(row);
        }
        String key=field;boolean descending=direction.equals("desc");var collator=Collator.getInstance(Locale.CHINA);
        rows.sort((a,b)->{
            Object x=a.get(key),y=b.get(key);int tie=text(a.get("tableName")).compareTo(text(b.get("tableName")));
            if(x==null||text(x).isEmpty())return y==null||text(y).isEmpty()?tie:1;
            if(y==null||text(y).isEmpty())return -1;
            int compared=Set.of("fieldCount","recordCount").contains(key)?new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())):collator.compare(text(x),text(y));
            return compared==0?tie:descending?-compared:compared;
        });
        int start=(int)Math.min((long)(page-1)*size,rows.size());
        return Map.of("list",rows.subList(start,Math.min(start+size,rows.size())),"total",rows.size(),"allTotal",originals.size(),"businessStats",stats,"changeStats",changes,
            "summary",Map.of("tableCount",tables,"viewCount",viewCount,"fieldCount",fields));
    }
    private static String text(Object value){return value==null?"":value.toString().trim();}
    private static int number(Object value,int fallback){return text(value).isEmpty()?fallback:Integer.parseInt(text(value));}
    private static long longNumber(Object value){return text(value).isEmpty()?0:Long.parseLong(text(value));}
}
