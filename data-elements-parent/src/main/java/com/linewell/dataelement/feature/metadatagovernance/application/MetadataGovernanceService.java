package com.linewell.dataelement.feature.metadatagovernance.application;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.persistence.ServerTableOrder;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataSourceScopeSql;
import com.linewell.dataelement.metautil.service.MetadataRelationJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped metadata catalog projection and logical ER relation ledger.
 * Physical collection remains the established MetadataExplorer Magic flow;
 * this service only consumes its db_table_t/db_table_column_t snapshots.
 */
@Service
public class MetadataGovernanceService {
    public Map<String,Object> catalogServices(String catalogId,int page,int size,String field,String direction){
        String tenant=TenantContext.requireTenantId();
        if(catalogId==null||catalogId.isBlank()||page<1||page>100000||size<1||size>100)throw new IllegalArgumentException("目录和分页参数无效");
        var args=new ArrayList<Object>(List.of(tenant,catalogId));
        String catalogScope=DataSourceScopeSql.predicate(scopes,"c",args);
        var parents=jdbc.queryForList("SELECT c.tid FROM da_catalog_t c WHERE c.tenant_id=? AND c.tid=? AND c.is_del=0 AND "+catalogScope,args.toArray());
        if(parents.size()!=1)throw new IllegalArgumentException("目录不存在或无访问权限");
        String order=ServerTableOrder.sql(field,direction,Map.of("assetName","a.service_name","count","COALESCE(p.param_count,0)","updateTime","a.updated_time"),"assetName","asc","a.tid");
        args=new ArrayList<Object>(List.of(tenant,catalogId));
        String where=" WHERE a.tenant_id=? AND a.catalog_id=? AND a.is_del=0 AND "+DataSourceScopeSql.predicate(scopes,"a",args);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM api_info_t a"+where,Long.class,args.toArray());
        args.add(size);args.add((long)(page-1)*size);
        // Aggregate parameter counts once, scoped to this catalog; no query is issued for individual APIs.
        var queryArgs=new ArrayList<Object>(List.of(tenant,catalogId));queryArgs.addAll(args);
        var rows=jdbc.queryForList("""
            SELECT a.tid AS "id",a.service_name AS "assetName",COALESCE(p.param_count,0) AS "count",a.updated_time AS "updateTime"
            FROM api_info_t a LEFT JOIN (
                SELECT ap.api_id,COUNT(*) AS param_count FROM api_param_t ap
                JOIN api_info_t owner ON owner.tid=ap.api_id AND owner.tenant_id=ap.tenant_id AND owner.is_del=0
                WHERE ap.tenant_id=? AND owner.catalog_id=? AND ap.is_del=0 GROUP BY ap.api_id
            ) p ON p.api_id=a.tid
            """+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",queryArgs.toArray());
        return Map.of("list",rows,"total",total==null?0:total);
    }

    /** Reads the current catalog's authoritative target association, with both catalog and source scope checked. */
    public Map<String,Object> catalogResources(String catalogId,int page,int size,String field,String direction){
        String tenant=TenantContext.requireTenantId();
        if(catalogId==null||catalogId.isBlank()||page<1||page>100000||size<1||size>100)throw new IllegalArgumentException("目录和分页参数无效");
        String order=ServerTableOrder.sql(field,direction,Map.ofEntries(
            Map.entry("tableName","t.table_name"),Map.entry("tableNameCn","COALESCE(NULLIF(t.table_name_cn,''),t.table_name)"),
            Map.entry("fieldCount","t.field_count"),Map.entry("recordCount","t.record_count"),Map.entry("createdTime","t.created_time"),Map.entry("updatedTime","t.updated_time")),"tableName","asc","t.tid");
        var args=new ArrayList<Object>(List.of(tenant,catalogId));
        String catalogScope=DataSourceScopeSql.predicate(scopes,"c",args);
        var parents=jdbc.queryForList("SELECT c.target_table_id FROM da_catalog_t c WHERE c.tenant_id=? AND c.tid=? AND c.is_del=0 AND "+catalogScope,args.toArray());
        if(parents.size()!=1)throw new IllegalArgumentException("目录不存在或无访问权限");
        Object target=parents.getFirst().get("target_table_id");
        if(target==null||target.toString().isBlank())return Map.of("list",List.of(),"total",0);
        args=new ArrayList<Object>(List.of(tenant,target));
        String sourceScope=DataSourceScopeSql.predicate(scopes,"d",args);
        var rows=jdbc.queryForList("""
            SELECT t.tid AS "tableId",t.table_name AS "tableName",COALESCE(NULLIF(t.table_name_cn,''),t.table_name) AS "tableNameCn",
                   d.tid AS "dbId",d.db_name AS "dbName",d.db_type AS "dbType",d.app_id AS "appId",a.app_name AS "appName",
                   t.field_count AS "fieldCount",t.record_count AS "recordCount",t.created_time AS "createdTime",t.updated_time AS "updatedTime",'0' AS "relaType"
            FROM db_table_t t JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0
            LEFT JOIN sym_application_t a ON a.tid=d.app_id AND a.tenant_id=d.tenant_id AND a.is_del=0
            WHERE t.tenant_id=? AND t.tid=? AND t.is_del=0 AND
            """+sourceScope+" ORDER BY "+order+" LIMIT 2",args.toArray());
        if(rows.size()!=1)throw new IllegalArgumentException("关联资源不存在或无访问权限，请刷新目录");
        return Map.of("list",page==1?rows:List.of(),"total",1);
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MetadataRelationJdbcService physical;

    public MetadataGovernanceService(JdbcTemplate jdbc) { this(jdbc,new ObjectMapper(),null); }
    private final DataScopeAuthorizationService scopes;
    public MetadataGovernanceService(JdbcTemplate jdbc,ObjectMapper json,MetadataRelationJdbcService physical) { this(jdbc,json,physical,null); }
    @Autowired
    public MetadataGovernanceService(JdbcTemplate jdbc,ObjectMapper json,MetadataRelationJdbcService physical,DataScopeAuthorizationService scopes) { this.jdbc=jdbc;this.json=json;this.physical=physical;this.scopes=scopes; }

    public Map<String, Object> catalog() {
        String tenantId = tenantId();
        List<Map<String, Object>> sources = jdbc.queryForList("""
            SELECT d.tid, d.db_name, COALESCE(NULLIF(d.db_type,''), d.database_type, 'mysql') AS db_type,
                   d.jdbc_url, d.is_enable, COUNT(t.tid) AS table_count
              FROM db_datasource_t d
              LEFT JOIN db_table_t t ON t.datasource_id=d.tid AND t.tenant_id=? AND t.is_del=0
             WHERE d.tenant_id=? AND d.is_del=0
             GROUP BY d.tid,d.db_name,d.db_type,d.database_type,d.jdbc_url,d.is_enable
             ORDER BY d.db_name,d.tid
            """, tenantId, tenantId);
        List<Map<String, Object>> tables = jdbc.queryForList("""
            SELECT t.tid, t.datasource_id, t.table_name, t.table_name_cn, t.table_comment,
                   t.table_type, t.field_count, t.record_count, t.annotated, t.updated_time,
                   COALESCE(d.db_name,'未命名数据源') AS datasource_name,
                   COALESCE(NULLIF(d.db_type,''), d.database_type, 'mysql') AS datasource_type
              FROM db_table_t t
              JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=? AND d.is_del=0
             WHERE t.tenant_id=? AND t.is_del=0
             ORDER BY d.db_name, COALESCE(t.table_name_cn,t.table_comment,t.table_name), t.table_name
            """, tenantId, tenantId);
        List<Map<String, Object>> columns = jdbc.queryForList("""
            SELECT c.tid, c.table_id, c.column_name, c.column_comment, c.data_type, c.column_type,
                   c.length, c.nullable, c.primary_key, c.is_unique, c.indexed, c.ordinal_position,
                   c.updated_time
              FROM db_table_column_t c
             WHERE c.tenant_id=? AND c.is_del=0
             ORDER BY c.table_id,c.ordinal_position,c.column_name
            """, tenantId);
        return Map.of("tenantId", tenantId, "sources", sources, "tables", tables,
                "columns", columns, "relations", relations(tenantId));
    }

    /** The catalog browser reads one tenant-scoped page without transferring every field snapshot. */
    public Map<String, Object> catalogTablesPage(List<String> sourceIds, String kind, String keyword, int pageNo, int pageSize) {
        return catalogTablesPage(sourceIds, kind, keyword, pageNo, pageSize, null);
    }

    /** Exact tenant-scoped lookup used by asset-to-governance deep links. */
    public Map<String, Object> catalogTablesPage(List<String> sourceIds, String kind, String keyword, int pageNo, int pageSize, String tableId) {
        return catalogTablesPage(sourceIds,kind,keyword,pageNo,pageSize,tableId,null,null);
    }

    public Map<String, Object> catalogTablesPage(List<String> sourceIds, String kind, String keyword, int pageNo, int pageSize, String tableId, String sortField, String sortDir) {
        String tenantId = tenantId();
        int page = Math.max(1, pageNo), size = Math.min(100, Math.max(1, pageSize));
        StringBuilder where = new StringBuilder(" FROM db_table_t t JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 WHERE t.tenant_id=? AND t.is_del=0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        where.append(" AND ").append(DataSourceScopeSql.predicate(scopes,"d",args));
        String order = ServerTableOrder.sql(sortField,sortDir,Map.of("tableName","COALESCE(NULLIF(t.table_name_cn,''),NULLIF(t.table_comment,''),t.table_name)","fieldCount","t.field_count","recordCount","t.record_count","createdTime","t.created_time","updatedTime","t.updated_time"),"tableName","asc","t.tid");
        if (sourceIds != null && sourceIds.size() > 1000) throw new IllegalArgumentException("单次最多选择 1000 个数据源");
        if (tableId != null && !tableId.isBlank()) {
            where.append(" AND t.tid=?");
            args.add(tableId.trim());
        }
        if (sourceIds != null) {
            List<String> ids = sourceIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
            if (ids.isEmpty()) return Map.of("rows", List.of(), "catalogs", List.of(), "total", 0, "pageNo", page, "pageSize", size);
            where.append(" AND t.datasource_id IN (").append(String.join(",", java.util.Collections.nCopies(ids.size(), "?"))).append(")");
            args.addAll(ids);
        }
        if ("视图".equals(kind) || "数据表".equals(kind)) {
            where.append(" AND ").append("视图".equals(kind) ? "" : "NOT ").append("(UPPER(COALESCE(t.table_type,'')) LIKE '%VIEW%' OR COALESCE(t.table_type,'') LIKE '%视图%')");
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(COALESCE(t.table_name,'')) LIKE ? OR LOWER(COALESCE(t.table_name_cn,'')) LIKE ? OR LOWER(COALESCE(t.table_comment,'')) LIKE ?)");
            String match = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(match); args.add(match); args.add(match);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) (page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.tid, t.datasource_id, t.table_name, t.table_name_cn, t.table_comment,
                   t.table_type, t.field_count, t.record_count, t.created_time, t.updated_time
            """ + where + " ORDER BY " + order + " LIMIT ? OFFSET ?", pageArgs.toArray());
        List<Map<String, Object>> catalogs = List.of();
        if (!rows.isEmpty()) {
            List<String> tableIds = rows.stream().map(row -> text(row.get("tid"))).toList();
            List<Object> catalogArgs = new ArrayList<>();
            catalogArgs.add(tenantId);
            catalogArgs.addAll(tableIds);
            catalogs = jdbc.queryForList("""
                SELECT c.tid, c.catalog_name, c.catalog_name_en, c.asset_desc, c.source_table_id, c.db_id
                  FROM da_catalog_t c
                 WHERE c.tenant_id=? AND c.is_del=0 AND c.source_table_id IN (
                """ + String.join(",", java.util.Collections.nCopies(tableIds.size(), "?")) + ") ORDER BY c.updated_time DESC, c.tid DESC", catalogArgs.toArray());
        }
        return Map.of("rows", rows, "catalogs", catalogs, "total", total, "pageNo", page, "pageSize", size);
    }

    public List<Map<String, Object>> catalogTableColumns(String tableId) {
        return catalogTableColumns(tableId,null,null);
    }

    public List<Map<String, Object>> catalogTableColumns(String tableId,String sortField,String sortDir) {
        String tenantId = tenantId();
        List<Object> args = new ArrayList<>(List.of(tenantId,tableId));
        String scope = DataSourceScopeSql.predicate(scopes,"d",args);
        String order = ServerTableOrder.sql(sortField,sortDir,Map.of("column_name","c.column_name","length","c.length","updated_time","c.updated_time","ordinal_position","c.ordinal_position"),"ordinal_position","asc","c.tid");
        var rows=jdbc.queryForList("""
            SELECT c.tid, c.table_id, c.column_name, c.column_comment, c.data_type, c.column_type,
                   c.length, c.nullable, c.primary_key, c.is_unique, c.indexed, c.ordinal_position
              FROM db_table_column_t c
              JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id AND t.is_del=0
              JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0
             WHERE c.tenant_id=? AND c.table_id=? AND c.is_del=0 AND
            """ + scope + " ORDER BY " + order+" LIMIT 1001",args.toArray());
        if(rows.size()>1000)throw new IllegalArgumentException("字段超过 1000 项，请使用字段分页查询");
        return rows;
    }

    public Map<String,Object> catalogColumnsPage(String tableId,int pageNo,int pageSize,String sortField,String sortDir) {
        String tenant=tenantId();int page=Math.max(1,pageNo),size=Math.min(100,Math.max(1,pageSize));
        List<Object> args=new ArrayList<>(List.of(tenant,tableId));
        String scope=DataSourceScopeSql.predicate(scopes,"d",args);
        String from=" FROM db_table_column_t c JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id AND t.is_del=0 JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 WHERE c.tenant_id=? AND c.table_id=? AND c.is_del=0 AND "+scope;
        String order=ServerTableOrder.sql(sortField,sortDir,Map.of("column_name","c.column_name","length","c.length","updated_time","c.updated_time","ordinal_position","c.ordinal_position"),"ordinal_position","asc","c.tid");
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        args.add(size);args.add((long)(page-1)*size);
        var rows=jdbc.queryForList("SELECT c.tid,c.table_id,c.column_name,c.column_comment,c.data_type,c.column_type,c.length,c.nullable,c.primary_key,c.ordinal_position"+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray());
        return Map.of("list",rows,"total",total,"page",page,"size",size);
    }

    /** Every requested parent is authorized in one read, followed by one bounded field read. */
    public Map<String,List<Map<String,Object>>> catalogColumnsBatch(List<String> requested) {
        if(requested==null||requested.isEmpty()||requested.size()>100)throw new IllegalArgumentException("每批请选择 1 至 100 张表");
        if(requested.stream().anyMatch(id->id==null||id.isBlank()||id.length()>64))throw new IllegalArgumentException("表标识不正确");
        var ids=requested.stream().distinct().toList();String tenant=tenantId();
        List<Object> args=new ArrayList<>(List.of(tenant));args.addAll(ids);
        String scope=DataSourceScopeSql.predicate(scopes,"d",args);
        String in=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        String from=" FROM db_table_t t JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 WHERE t.tenant_id=? AND t.tid IN ("+in+") AND t.is_del=0 AND "+scope;
        var parents=jdbc.queryForList("SELECT t.tid"+from,args.toArray());
        if(parents.size()!=ids.size())throw new IllegalArgumentException("包含不存在或无权读取的数据表");
        String fields="SELECT c.tid,c.table_id,c.column_name,c.column_comment,c.data_type,c.column_type,c.length,c.nullable,c.primary_key,c.is_unique,c.indexed,c.ordinal_position FROM db_table_column_t c JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 WHERE t.tenant_id=? AND t.tid IN ("+in+") AND t.is_del=0 AND c.is_del=0 AND "+scope+" ORDER BY c.table_id,c.ordinal_position,c.tid LIMIT 10001";
        var rows=jdbc.queryForList(fields,args.toArray());
        if(rows.size()>10000)throw new IllegalArgumentException("字段超过 10000 项，请缩小批次");
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();ids.forEach(id->result.put(id,new ArrayList<>()));
        rows.forEach(row->result.get(String.valueOf(row.get("table_id"))).add(row));return result;
    }

    /** Structure-only discovery against the latest collected physical snapshots. */
    public Map<String, Object> discover(Map<String, Object> body) {
        String tenantId = tenantId();
        Set<String> allowed = textSet(body.get("tableIds"));
        List<Map<String, Object>> tables = jdbc.queryForList("""
            SELECT t.tid,t.datasource_id,t.table_name,COALESCE(t.table_name_cn,t.table_comment,t.table_name) AS display_name
              FROM db_table_t t WHERE t.tenant_id=? AND t.is_del=0 ORDER BY t.table_name
            """, tenantId);
        if (!allowed.isEmpty()) tables = tables.stream().filter(row -> allowed.contains(text(row.get("tid")))).toList();
        if (tables.size() > 300) throw new IllegalArgumentException("单次关系识别最多选择 300 张表，请缩小范围");
        if (tables.size() < 2) return Map.of("items", List.of(), "suppressed", 0,
                "note", "至少选择两张已采集数据表后才能识别关系");
        Set<String> tableIds = tables.stream().map(row -> text(row.get("tid"))).collect(Collectors.toSet());
        List<Map<String, Object>> columns = jdbc.queryForList("""
            SELECT tid,table_id,column_name,COALESCE(column_type,data_type,'') AS native_type,
                   primary_key,is_unique,nullable
              FROM db_table_column_t WHERE tenant_id=? AND is_del=0
             ORDER BY table_id,ordinal_position,column_name
            """, tenantId).stream().filter(row -> tableIds.contains(text(row.get("table_id")))).toList();
        Map<String, List<Map<String, Object>>> byTable = columns.stream()
                .collect(Collectors.groupingBy(row -> text(row.get("table_id")), LinkedHashMap::new, Collectors.toList()));
        Set<String> existing = relations(tenantId).stream().map(this::fingerprint).collect(Collectors.toSet());
        List<Map<String, Object>> candidates = new ArrayList<>();
        int suppressed = 0;
        for (Map<String, Object> target : tables) {
            List<Map<String, Object>> targetKeys = byTable.getOrDefault(text(target.get("tid")), List.of()).stream()
                    .filter(column -> truthy(column.get("primary_key")) || truthy(column.get("is_unique")))
                    .toList();
            if (targetKeys.isEmpty()) targetKeys = byTable.getOrDefault(text(target.get("tid")), List.of()).stream()
                    .filter(column -> likelyKey(text(column.get("column_name")), text(target.get("table_name")))).limit(2).toList();
            for (Map<String, Object> targetKey : targetKeys) for (Map<String, Object> source : tables) {
                if (text(source.get("tid")).equals(text(target.get("tid")))) continue;
                for (Map<String, Object> sourceColumn : byTable.getOrDefault(text(source.get("tid")), List.of())) {
                    if (!matches(sourceColumn, targetKey, target) || !compatible(sourceColumn, targetKey)) continue;
                    String key = fingerprint(text(source.get("tid")), text(sourceColumn.get("tid")), text(target.get("tid")), text(targetKey.get("tid")));
                    if (existing.contains(key)) { suppressed++; continue; }
                    boolean crossSource = !text(source.get("datasource_id")).equals(text(target.get("datasource_id")));
                    int confidence = (truthy(targetKey.get("primary_key")) ? 78 : truthy(targetKey.get("is_unique")) ? 72 : 58) + (crossSource ? -8 : 5);
                    candidates.add(candidate(source, sourceColumn, target, targetKey, Math.max(35, confidence), crossSource));
                }
            }
        }
        candidates.sort(Comparator.comparing(row -> -number(row.get("confidence"))));
        return Map.of("items", candidates.stream().limit(500).toList(), "suppressed", suppressed,
                "note", "依据已采集的字段名、类型与主键/唯一键结构生成推荐；未读取业务数据、未创建物理外键。");
    }

    private List<Map<String, Object>> relations(String tenantId) {
        return jdbc.queryForList("""
            SELECT r.*,
                   st.table_name AS source_table_name,sc.column_name AS source_column_name,
                   tt.table_name AS target_table_name,tc.column_name AS target_column_name
              FROM dwm_metadata_relation_t r
              JOIN db_table_t st ON st.tid=r.source_table_id AND st.tenant_id=r.tenant_id AND st.is_del=0
              JOIN db_table_column_t sc ON sc.tid=r.source_column_id AND sc.table_id=st.tid AND sc.tenant_id=r.tenant_id AND sc.is_del=0
              JOIN db_table_t tt ON tt.tid=r.target_table_id AND tt.tenant_id=r.tenant_id AND tt.is_del=0
              JOIN db_table_column_t tc ON tc.tid=r.target_column_id AND tc.table_id=tt.tid AND tc.tenant_id=r.tenant_id AND tc.is_del=0
             WHERE r.tenant_id=? AND r.is_del=0 ORDER BY r.updated_time DESC,r.tid DESC
            """, tenantId);
    }

    public Map<String,Object> validateRelationData(Map<String,Object> body) {
        String tenant=tenantId(),id=required(body,"tid","关系标识不能为空");Map<String,Object> row=relation(tenant,id);
        Map<String,Object> definition=physicalDefinition(row,tenant);
        Map<String,Object> evidence=new LinkedHashMap<>(physical.validate(definition,body.get("sampleLimit")==null?200:number(body.get("sampleLimit"))));
        evidence.put("relationshipId",id);
        int changed=jdbc.update("UPDATE dwm_metadata_relation_t SET validation_json=?,updated_time=?,version_no=version_no+1 WHERE tenant_id=? AND tid=? AND version_no=? AND is_del=0",encode(evidence),LocalDateTime.now(),tenant,id,number(row.get("version_no")));
        if(changed!=1)throw new IllegalArgumentException("读取期间关系已修改，结果未覆盖新定义；请重新验证");
        return evidence;
    }
    public Map<String,Object> previewPhysicalForeignKey(Map<String,Object> body) {
        String tenant=tenantId(),id=required(body,"tid","关系标识不能为空");Map<String,Object> row=relation(tenant,id);
        if(!"CONFIRMED".equals(row.get("relation_status")))throw new IllegalArgumentException("请先确认并保存逻辑关系");
        return physical.preview(physicalDefinition(row,tenant),"fk_wx_"+id.replaceAll("[^A-Za-z0-9]","").substring(0,Math.min(24,id.replaceAll("[^A-Za-z0-9]","").length())));
    }
    @Transactional
    public Map<String,Object> createPhysicalForeignKey(Map<String,Object> body) {
        if(!Boolean.TRUE.equals(body.get("confirmPhysicalChange")))throw new IllegalArgumentException("必须明确确认源数据库结构变更");
        String tenant=tenantId(),id=required(body,"tid","关系标识不能为空");
        // Keep the logical definition stable while an independent physical database executes DDL.
        jdbc.queryForList("SELECT tid FROM dwm_metadata_relation_t WHERE tenant_id=? AND tid=? AND is_del=0 FOR UPDATE",tenant,id);
        Map<String,Object> row=relation(tenant,id);
        if(!"CONFIRMED".equals(row.get("relation_status")))throw new IllegalArgumentException("请先确认并保存逻辑关系");
        String name=text(previewPhysicalForeignKey(body).get("constraintName"));
        Map<String,Object> result=physical.execute(physicalDefinition(row,tenant),name,required(body,"previewHash","请先预览物理外键"));
        int changed=jdbc.update("UPDATE dwm_metadata_relation_t SET physical_fk_json=?,updated_time=?,version_no=version_no+1 WHERE tenant_id=? AND tid=? AND version_no=? AND is_del=0",encode(result),LocalDateTime.now(),tenant,id,number(row.get("version_no")));
        if(changed!=1)throw new IllegalStateException("物理外键已执行，但台账并发更新导致记录未写入；请重新预检查同名外键，不要重复创建");return result;
    }
    private Map<String,Object> relation(String tenant,String id) {return relations(tenant).stream().filter(r->id.equals(r.get("tid"))).findFirst().orElseThrow(()->new IllegalArgumentException("关系不存在或无权访问"));}

    /** Shared structural validation for Magic saves and physical relation checks. */
    public Map<String, Object> prepareDefinition(Map<String, Object> body) {
        String tenant = tenantId();
        return relationDefinition(body, tenant,
                required(body, "sourceTableId", "来源数据表不能为空"),
                required(body, "targetTableId", "目标数据表不能为空"));
    }

    private Map<String,Object> relationDefinition(Map<String,Object> body,String tenant,String source,String target) {
        List<Map<String,Object>> mappings=mapList(body.get("mappings"));
        if(mappings.isEmpty())mappings=List.of(Map.of("sourceColumnId",required(body,"sourceColumnId","来源字段不能为空"),"targetColumnId",required(body,"targetColumnId","目标字段不能为空")));
        if(mappings.size()>64)throw new IllegalArgumentException("最多 64 组字段映射");
        Set<String> sources=new java.util.HashSet<>(),targets=new java.util.HashSet<>();Set<FieldRef> fields=new LinkedHashSet<>();List<Map<String,Object>> safe=new ArrayList<>();
        for(var pair:mappings){String a=required(pair,"sourceColumnId","来源字段不能为空"),b=required(pair,"targetColumnId","目标字段不能为空");fields.add(new FieldRef(source,a));fields.add(new FieldRef(target,b));if(!sources.add(a)||!targets.add(b))throw new IllegalArgumentException("同一侧字段不能重复映射");if(source.equals(target)&&a.equals(b))throw new IllegalArgumentException("不能将同一字段关联到自身");if(pair.get("comparisonRule")!=null&&!"exact".equals(pair.get("comparisonRule")))throw new IllegalArgumentException("目前支持精确字段比较");safe.add(Map.of("sourceColumnId",a,"targetColumnId",b,"comparisonRule","exact"));}
        List<Map<String,Object>> terms=new ArrayList<>();for(var term:mapList(body.get("conditions"))){String side=required(term,"side","条件范围不能为空"),field=required(term,"columnId","条件字段不能为空"),op=required(term,"operator","条件操作不能为空");if(!Set.of("source","target").contains(side)||!Set.of("eq","in","is_not_null").contains(op))throw new IllegalArgumentException("条件范围或操作符不支持");fields.add(new FieldRef("source".equals(side)?source:target,field));Object value=term.get("value");if("in".equals(op)&&(!(value instanceof List<?> list)||list.isEmpty()||list.size()>100))throw new IllegalArgumentException("IN 条件需要 1 至 100 个值");if(value instanceof Map<?,?>||value instanceof List<?> list&&list.stream().anyMatch(v->v instanceof Map<?,?>||v instanceof List<?>))throw new IllegalArgumentException("条件值必须为普通值");Map<String,Object> item=new LinkedHashMap<>();item.put("side",side);item.put("columnId",field);item.put("operator",op);item.put("value","is_not_null".equals(op)?null:value);terms.add(item);}
        if(terms.size()>32)throw new IllegalArgumentException("最多 32 个 AND 条件");
        loadFields(tenant,fields);
        return Map.of("mappings",safe,"conditions",terms,"targetsPerSource",multiplicity(body.get("targetsPerSource")),"sourcesPerTarget",multiplicity(body.get("sourcesPerTarget")));
    }
    private Map<String,Object> multiplicity(Object value){Map<String,Object> row=asMap(value);String min=defaultText(text(row.get("min")),"unknown"),max=defaultText(text(row.get("max")),"unknown");if(!Set.of("0","1","unknown").contains(min)||!Set.of("1","many","unknown").contains(max))throw new IllegalArgumentException("基数边界不正确");return Map.of("min",min,"max",max,"basis","unknown".equals(max)&&"unknown".equals(min)?"unknown":"business");}
    private Map<String,Object> physicalDefinition(Map<String,Object> row,String tenant) {
        String source=text(row.get("source_table_id")),target=text(row.get("target_table_id"));Map<String,Object> definition=decode(row.get("definition_json"));if(definition.isEmpty())definition=relationDefinition(Map.of("sourceColumnId",row.get("source_column_id"),"targetColumnId",row.get("target_column_id")),tenant,source,target);
        Map<String,Object> st=table(tenant,source),tt=table(tenant,target),result=new LinkedHashMap<>(definition);result.put("sourceId",st.get("datasource_id"));result.put("targetId",tt.get("datasource_id"));result.put("sourceTable",st.get("table_name"));result.put("targetTable",tt.get("table_name"));result.put("sourceKind",physicalKind(st.get("table_type")));result.put("targetKind",physicalKind(tt.get("table_type")));result.put("relationType",row.get("relation_type"));
        List<Map<String,Object>> mappings=mapList(definition.get("mappings")),conditions=mapList(definition.get("conditions"));Set<FieldRef> refs=new LinkedHashSet<>();
        for(var mapping:mappings){refs.add(new FieldRef(source,text(mapping.get("sourceColumnId"))));refs.add(new FieldRef(target,text(mapping.get("targetColumnId"))));}
        for(var term:conditions)refs.add(new FieldRef("source".equals(term.get("side"))?source:target,text(term.get("columnId"))));
        Map<FieldRef,String> names=loadFields(tenant,refs);
        result.put("mappings",mappings.stream().map(m->Map.<String,Object>of("sourceName",names.get(new FieldRef(source,text(m.get("sourceColumnId")))),"targetName",names.get(new FieldRef(target,text(m.get("targetColumnId")))))).toList());
        result.put("conditions",conditions.stream().map(term->{Map<String,Object> item=new LinkedHashMap<>(term);item.put("name",names.get(new FieldRef("source".equals(term.get("side"))?source:target,text(term.get("columnId")))));return item;}).toList());return result;
    }
    private Map<String,Object> table(String tenant,String id){return jdbc.queryForList("SELECT t.table_name,t.table_type,t.datasource_id FROM db_table_t t JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 WHERE t.tenant_id=? AND t.tid=? AND t.is_del=0",tenant,id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("表不存在或数据源已删除"));}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("关系定义不能序列化",e);}}
    private String physicalKind(Object value){String kind=defaultText(text(value),"TABLE");return "数据表".equals(kind)||"BASE TABLE".equalsIgnoreCase(kind)?"TABLE":kind.toUpperCase(Locale.ROOT);}
    private Map<String,Object> decode(Object value){if(value==null||value.toString().isBlank())return Map.of();try{return json.readValue(value.toString(),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalArgumentException("历史关系定义已损坏，未自动覆盖",e);}}
    @SuppressWarnings("unchecked") private Map<String,Object> asMap(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
    private List<Map<String,Object>> mapList(Object value){if(value==null)return List.of();if(!(value instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof Map<?,?>)))throw new IllegalArgumentException("关系映射或条件格式不正确");return list.stream().map(this::asMap).toList();}

    private Map<String, Object> candidate(Map<String, Object> source, Map<String, Object> sourceColumn,
            Map<String, Object> target, Map<String, Object> targetColumn, int confidence, boolean crossSource) {
        return Map.ofEntries(
                Map.entry("sourceTableId", text(source.get("tid"))), Map.entry("sourceColumnId", text(sourceColumn.get("tid"))),
                Map.entry("targetTableId", text(target.get("tid"))), Map.entry("targetColumnId", text(targetColumn.get("tid"))),
                Map.entry("sourceTableName", text(source.get("table_name"))), Map.entry("sourceColumnName", text(sourceColumn.get("column_name"))),
                Map.entry("targetTableName", text(target.get("table_name"))), Map.entry("targetColumnName", text(targetColumn.get("column_name"))),
                Map.entry("relationName", text(source.get("display_name")) + "引用" + text(target.get("display_name"))),
                Map.entry("relationType", "REFERENCE"), Map.entry("origin", "INFERENCE"), Map.entry("status", "SUGGESTED"),
                Map.entry("confidence", confidence), Map.entry("crossSource", crossSource),
                Map.entry("evidence", (crossSource ? "跨数据源；" : "同数据源；") + "字段命名、类型与目标键结构匹配")
        );
    }
    private record FieldRef(String tableId,String columnId) {}
    private Map<FieldRef,String> loadFields(String tenantId,Set<FieldRef> requested) {
        if(requested.isEmpty())return Map.of();
        List<String> tables=requested.stream().map(FieldRef::tableId).distinct().toList();
        List<String> ids=requested.stream().map(FieldRef::columnId).distinct().toList();
        Map<FieldRef,String> found=new HashMap<>();
        for(int offset=0;offset<ids.size();offset+=500){List<String> chunk=ids.subList(offset,Math.min(offset+500,ids.size()));List<Object> args=new ArrayList<>();args.add(tenantId);args.addAll(tables);args.addAll(chunk);
            String sql="SELECT c.table_id,c.tid,c.column_name FROM db_table_column_t c JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id AND t.is_del=0 WHERE c.tenant_id=? AND c.is_del=0 AND c.table_id IN ("+String.join(",",java.util.Collections.nCopies(tables.size(),"?"))+") AND c.tid IN ("+String.join(",",java.util.Collections.nCopies(chunk.size(),"?"))+")";
            for(var row:jdbc.queryForList(sql,args.toArray())){FieldRef ref=new FieldRef(text(row.get("table_id")),text(row.get("tid")));if(requested.contains(ref))found.put(ref,text(row.get("column_name")));}
        }
        if(!found.keySet().containsAll(requested))throw new IllegalArgumentException("选择的表或字段不存在，可能尚未采集或无权访问");
        return found;
    }
    private boolean matches(Map<String, Object> source, Map<String, Object> target, Map<String, Object> targetTable) {
        String a = normalize(text(source.get("column_name"))); String b = normalize(text(target.get("column_name")));
        if (a.equals(b) && !Set.of("id", "name", "code", "status", "type").contains(a)) return true;
        String root = singular(normalize(text(targetTable.get("table_name"))));
        return "id".equals(b) && (a.equals(root + "_id") || a.endsWith("_" + root + "_id"));
    }
    private boolean compatible(Map<String, Object> source, Map<String, Object> target) { return family(text(source.get("native_type"))).equals(family(text(target.get("native_type")))); }
    private boolean likelyKey(String column, String table) { String n=normalize(column), root=singular(normalize(table)); return n.equals("id") || n.equals(root+"_id") || n.equals(root+"_code"); }
    private String fingerprint(Map<String,Object> relation) { return fingerprint(text(relation.get("source_table_id")), text(relation.get("source_column_id")), text(relation.get("target_table_id")), text(relation.get("target_column_id"))); }
    private String fingerprint(String st,String sc,String tt,String tc) { return st+":"+sc+"->"+tt+":"+tc; }
    private String normalize(String value) { return defaultText(value, "").replaceAll("([a-z\\d])([A-Z])", "$1_$2").replaceAll("[\\s-]+", "_").replaceFirst("^(tbl_|biz_|t_)", "").toLowerCase(Locale.ROOT); }
    private String singular(String value) { return value.endsWith("ies") ? value.substring(0,value.length()-3)+"y" : value.endsWith("s")&&!value.endsWith("ss") ? value.substring(0,value.length()-1) : value; }
    private String family(String value) { String t=normalize(value); if(t.matches(".*(int|number|serial).*"))return "integer"; if(t.matches(".*(decimal|numeric|float|double|real).*"))return "decimal"; if(t.matches(".*(date|time).*"))return "date"; return "string"; }
    private Set<String> textSet(Object value) { if (!(value instanceof List<?> list)) return Set.of(); return list.stream().map(this::text).filter(item -> item != null && !item.isBlank()).collect(Collectors.toSet()); }
    private int number(Object value) { try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; } }
    private boolean truthy(Object value) { return value instanceof Boolean flag ? flag : "1".equals(text(value)) || "true".equalsIgnoreCase(text(value)); }
    private String text(Object value) { if (value == null) return null; String result=String.valueOf(value).trim(); return result.isBlank() || "null".equalsIgnoreCase(result) ? null : result; }
    private String defaultText(String value,String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String required(Map<String,Object> body,String field,String message) { String value=text(body.get(field)); if(value==null)throw new IllegalArgumentException(message); return value; }
    private String tenantId() { return TenantContext.requireTenantId(); }
}
