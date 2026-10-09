package com.linewell.dataelement.metautil.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a durable, tenant-scoped snapshot of a datasource's logical table list.
 *
 * <p>The registration page must never need to keep an HTTP request open while a
 * multi-thousand-table datasource is scanned.  This service starts a bounded background
 * job, writes the table headers into {@code db_table_t}, and exposes only durable job
 * state to the low-code page.  The physical datasource is therefore contacted only by an
 * explicit collection or re-collection action.</p>
 */
@Service
public class TableMetadataCollectionService {

    private static final int WRITE_BATCH_SIZE = 200;
    /** Connector-private flag: scan every object visible to the connection account. */
    private static final String COLLECT_ALL_VISIBLE_OWNERS = "metadataCollectAllVisibleOwners";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate jdbc;
    private final MetadataExplorerService explorer;
    private final DataSourceConnectionPropertyResolver connections;
    private final HiveModule huaweiMrsHive;
    private final ObjectMapper json;
    private final ExecutorService workers = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().name("table-metadata-collection-", 0).factory());
    private final Set<String> inFlightJobIds = ConcurrentHashMap.newKeySet();

    public TableMetadataCollectionService(
            JdbcTemplate jdbc,
            MetadataExplorerService explorer,
            DataSourceConnectionPropertyResolver connections,
            HiveModule huaweiMrsHive,
            ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.explorer = explorer;
        this.connections = connections;
        this.huaweiMrsHive = huaweiMrsHive;
        this.json = json;
    }

    /** Starts a collection for the datasource in the current tenant. */
    public Map<String, Object> start(String datasourceId, Object requestedMode) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        boolean refresh = "REFRESH".equalsIgnoreCase(text(requestedMode))
                || "REEXPLORE".equalsIgnoreCase(text(requestedMode));
        requireCollectableDatasource(tenantId, sourceId);

        // Never replace a job that is still running in this process.  A second
        // click (or a reopened browser page) must attach to its durable state,
        // rather than start a second scan against the same physical database.
        String runningJobId = runningJobId(tenantId, sourceId);
        if (!runningJobId.isEmpty() && inFlightJobIds.contains(runningJobId)) {
            return status(runningJobId, sourceId, false);
        }
        // A RUNNING row that is not owned by this JVM can only be left by a
        // process that exited before completing its work.  Mark it terminal so
        // the user can retry instead of being trapped in a permanent spinner.
        if (!runningJobId.isEmpty()) {
            jdbc.update("""
                    update metadata_table_collection_job_t
                       set status=?, phase='FAILED', error_message=?, finished_time=?, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=? and status=?
                    """, FAILED, "采集服务已重启，请重新发起采集", now(), now(),
                    runningJobId, tenantId, sourceId, RUNNING);
        }

        String jobId = NumericId.nextId();
        jdbc.update("""
                insert into metadata_table_collection_job_t
                    (tid, tenant_id, datasource_id, collection_mode, status, phase,
                     scanned_count, persisted_count, total_count, added_count, unchanged_count,
                     deleted_count, deleted_detail, created_time, updated_time, started_time, is_del)
                values (?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, 0, '[]', ?, ?, ?, 0)
                """, jobId, tenantId, sourceId, refresh ? "REFRESH" : "INITIAL", RUNNING,
                now(), now(), now());
        inFlightJobIds.add(jobId);
        workers.submit(() -> TenantContext.run(tenantId, () -> runCollection(jobId, tenantId, sourceId, refresh)));
        return status(jobId, sourceId, false);
    }

    /** Returns the current or most recently completed durable collection state. */
    public Map<String, Object> status(String requestedJobId, String datasourceId, boolean includeDeletedDetails) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        List<Map<String, Object>> rows;
        if (!blank(requestedJobId)) {
            rows = jdbc.queryForList("""
                    select * from metadata_table_collection_job_t
                     where tid=? and tenant_id=? and datasource_id=? and is_del=0
                    """, requestedJobId.trim(), tenantId, sourceId);
        } else {
            rows = jdbc.queryForList("""
                    select * from metadata_table_collection_job_t
                     where tenant_id=? and datasource_id=? and is_del=0
                     order by created_time desc, tid desc
                    """, tenantId, sourceId);
        }
        if (rows.isEmpty()) return notCollected(sourceId);
        Map<String, Object> row = rows.getFirst();
        List<Map<String, Object>> previous = jdbc.queryForList("""
                select finished_time from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and is_del=0 and status=? and tid<>?
                 order by finished_time desc, tid desc
                """, tenantId, sourceId, SUCCEEDED, text(row.get("tid")));
        return statusResult(row, sourceId, previous.isEmpty() ? null : previous.getFirst().get("finished_time"),
                includeDeletedDetails);
    }

    /** Tenant-scoped status snapshot for a whole datasource list, with no per-row SQL. */
    public Map<String, Object> batchStatus(Collection<?> datasourceIds) {
        String tenantId = TenantContext.requireTenantId();
        if (datasourceIds == null || datasourceIds.isEmpty() || datasourceIds.size() > 500)
            throw new IllegalArgumentException("dbIds 需包含 1 至 500 个数据源标识");
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : datasourceIds) {
            if (!(raw instanceof String)) throw new IllegalArgumentException("dbId 格式不正确");
            ids.add(requiredDatasourceId((String) raw));
        }
        String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(); args.add(tenantId); args.addAll(ids);
        Set<String> owned = new HashSet<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select tid from db_datasource_t where tenant_id=? and is_del=0 and tid in (" + marks + ")",
                args.toArray())) owned.add(text(row.get("tid")));
        if (!owned.containsAll(ids)) throw new IllegalArgumentException("数据源不存在或无访问权限");

        String latestSql = "select * from (select j.*, row_number() over ("
                + "partition by j.datasource_id order by j.created_time desc,j.tid desc) as job_rank "
                + "from metadata_table_collection_job_t j where j.tenant_id=? and j.is_del=0 "
                + "and j.datasource_id in (" + marks + ")) ranked where ranked.job_rank=1";
        List<Map<String, Object>> latest = jdbc.queryForList(latestSql, args.toArray());
        Map<String, Map<String, Object>> latestBySource = new HashMap<>();
        Set<String> latestJobIds = new HashSet<>();
        for (Map<String, Object> row : latest) {
            latestBySource.put(text(row.get("datasource_id")), row);
            latestJobIds.add(text(row.get("tid")));
        }
        Map<String, Object> previousBySource = new HashMap<>();
        if (!latestJobIds.isEmpty()) {
            String jobMarks = String.join(",", java.util.Collections.nCopies(latestJobIds.size(), "?"));
            List<Object> previousArgs = new ArrayList<>(args); previousArgs.addAll(latestJobIds);
            String previousSql = "select datasource_id,finished_time from ("
                    + "select j.datasource_id,j.finished_time,row_number() over ("
                    + "partition by j.datasource_id order by j.finished_time desc,j.tid desc) as success_rank "
                    + "from metadata_table_collection_job_t j where j.tenant_id=? and j.is_del=0 "
                    + "and j.status='SUCCEEDED' and j.datasource_id in (" + marks + ") "
                    + "and j.tid not in (" + jobMarks + ")) ranked where ranked.success_rank=1";
            for (Map<String, Object> row : jdbc.queryForList(previousSql, previousArgs.toArray()))
                previousBySource.put(text(row.get("datasource_id")), row.get("finished_time"));
        }
        List<Map<String, Object>> items = new ArrayList<>(ids.size());
        for (String id : ids) {
            Map<String, Object> row = latestBySource.get(id);
            items.add(row == null ? notCollected(id) : statusResult(row, id, previousBySource.get(id), false));
        }
        return Map.of("items", items);
    }

    private static Map<String, Object> notCollected(String sourceId) {
        return Map.of("exists", false, "status", "NOT_COLLECTED", "phase", "NOT_COLLECTED",
                "datasourceId", sourceId, "progressPercent", 0);
    }

    private Map<String, Object> statusResult(Map<String, Object> row, String sourceId,
            Object previousSuccessfulAt, boolean includeDeletedDetails) {
        long total = number(row.get("total_count"));
        long persisted = number(row.get("persisted_count"));
        String state = text(row.get("status"));
        int percent = progressPercent(state, text(row.get("phase")), total, persisted);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("jobId", text(row.get("tid")));
        result.put("datasourceId", sourceId);
        result.put("mode", text(row.get("collection_mode")));
        result.put("status", state);
        result.put("phase", text(row.get("phase")));
        result.put("scannedCount", number(row.get("scanned_count")));
        result.put("persistedCount", persisted);
        result.put("totalCount", total);
        result.put("addedCount", number(row.get("added_count")));
        result.put("unchangedCount", number(row.get("unchanged_count")));
        result.put("deletedCount", number(row.get("deleted_count")));
        result.put("startedAt", row.get("started_time"));
        result.put("finishedAt", row.get("finished_time"));
        result.put("savedBeforeCount", number(row.get("unchanged_count")) + number(row.get("deleted_count")));
        result.put("previousSuccessfulAt", previousSuccessfulAt);
        if (includeDeletedDetails) result.put("deleted", deletedRows(row.get("deleted_detail")));
        result.put("error", text(row.get("error_message")));
        result.put("progressPercent", percent);
        return result;
    }

    /** A database-paged list of every saved table/view for this tenant and datasource. */
    public Map<String, Object> savedTables(String datasourceId, int pageNo, int pageSize, String keyword) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        requireSavedDatasource(tenantId, sourceId);
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String dedup = """
                from (
                    select t.tid, t.datasource_id, t.table_name, t.table_name_en,
                           t.table_name_cn, t.table_comment, t.table_type, t.field_count,
                           t.annotated, t.created_time, t.updated_time,
                           row_number() over (partition by lower(t.table_name)
                               order by coalesce(t.annotated, 0) desc, t.updated_time desc, t.tid desc) as name_rank
                      from db_table_t t
                     where t.tenant_id=? and t.datasource_id=? and t.is_del=0
                ) saved where saved.name_rank=1
                """;
        boolean searching = !blank(keyword);
        String filter = searching ? " and (lower(saved.table_name) like ? or lower(coalesce(saved.table_name_cn, '')) like ? or lower(coalesce(saved.table_comment, '')) like ?)" : "";
        List<Object> args = new ArrayList<>(List.of(tenantId, sourceId));
        if (searching) {
            String match = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(match);
            args.add(match);
            args.add(match);
        }
        long total = jdbc.queryForObject("select count(*) " + dedup + filter, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((long) (safePage - 1) * safeSize);
        pageArgs.add((long) safePage * safeSize);
        List<Map<String, Object>> pageRows = jdbc.queryForList("""
                select * from (
                    select saved.*, row_number() over (order by lower(saved.table_name), saved.tid) as page_rank
                """ + dedup + filter + """
                ) numbered where numbered.page_rank>? and numbered.page_rank<=?
                 order by numbered.page_rank
                """, pageArgs.toArray());
        return pagedTables(pageRows, safePage, safeSize, total);
    }

    /** Newly inserted table headers from the latest successful run, returned one page at a time. */
    public Map<String, Object> addedTables(String datasourceId, int pageNo, int pageSize) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        requireSavedDatasource(tenantId, sourceId);
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Map<String, Object>> jobs = jdbc.queryForList("""
                select started_time, finished_time from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and is_del=0 and status=?
                 order by created_time desc, tid desc
                """, tenantId, sourceId, SUCCEEDED);
        if (jobs.isEmpty()) return pagedTables(List.of(), safePage, safeSize, 0);
        Map<String, Object> job = jobs.getFirst();
        Object started = job.get("started_time");
        Object finished = job.get("finished_time");
        String where = " from db_table_t where tenant_id=? and datasource_id=? and is_del=0 and created_time>=? and created_time<=?";
        Object[] args = {tenantId, sourceId, started, finished};
        long total = jdbc.queryForObject("select count(*)" + where, Long.class, args);
        List<Map<String, Object>> pageRows = jdbc.queryForList("""
                select * from (
                    select tid, datasource_id, table_name, table_name_en, table_name_cn,
                           table_comment, table_type, field_count, annotated, created_time, updated_time,
                           row_number() over (order by created_time, tid) as page_rank
                """ + where + """
                ) numbered where numbered.page_rank>? and numbered.page_rank<=?
                 order by numbered.page_rank
                """, tenantId, sourceId, started, finished,
                (long) (safePage - 1) * safeSize, (long) safePage * safeSize);
        return pagedTables(pageRows, safePage, safeSize, total);
    }

    private Map<String, Object> pagedTables(List<Map<String, Object>> pageRows, int page, int size, long total) {
        List<Map<String, Object>> rows = new ArrayList<>(pageRows.size());
        for (Map<String, Object> row : pageRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tid", text(row.get("tid")));
            item.put("tableName", text(row.get("table_name")));
            item.put("tableNameCn", first(row, "table_name_cn", "table_comment", "table_name"));
            item.put("tableType", first(row, "table_type"));
            item.put("fieldCount", number(row.get("field_count")));
            item.put("createdAt", row.get("created_time"));
            item.put("updatedAt", row.get("updated_time"));
            rows.add(item);
        }
        return Map.of("pageNo", page, "pageSize", size, "total", total, "rows", rows, "list", rows);
    }

    private void requireSavedDatasource(String tenantId, String sourceId) {
        Long count = jdbc.queryForObject("""
                select count(*) from db_datasource_t where tid=? and tenant_id=? and is_del=0
                """, Long.class, sourceId, tenantId);
        if (count == null || count == 0) throw new IllegalArgumentException("数据源不存在或无访问权限");
    }

    /**
     * Returns the most recent re-collection's missing registered tables as a
     * compact, searchable page. The durable job stores the complete diff as
     * JSON, but the browser must not receive every changed row at once.
     */
    public Map<String, Object> changedTables(String datasourceId, int pageNo, int pageSize, String keyword) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<Map<String, Object>> jobs = jdbc.queryForList("""
                select deleted_detail from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and is_del=0
                 order by created_time desc, tid desc
                """, tenantId, sourceId);
        List<Map<String, Object>> all = jobs.isEmpty() ? List.of() : deletedRows(jobs.getFirst().get("deleted_detail"));
        String normalizedKeyword = text(keyword).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : all) {
            String tableName = first(row, "tableName", "tableNameEn", "name");
            if (normalizedKeyword.isEmpty() || tableName.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                matched.add(row);
            }
        }
        int maxPage = Math.max(1, (matched.size() + safeSize - 1) / safeSize);
        int effectivePage = Math.min(safePage, maxPage);
        int from = Math.min((effectivePage - 1) * safeSize, matched.size());
        int to = Math.min(from + safeSize, matched.size());
        List<Map<String, Object>> pageRows = new ArrayList<>();
        for (int index = from; index < to; index++) {
            Map<String, Object> row = new LinkedHashMap<>(matched.get(index));
            row.put("serialNo", index + 1);
            pageRows.add(row);
        }
        return Map.of(
                "pageNo", effectivePage,
                "pageSize", safeSize,
                "total", matched.size(),
                "rows", pageRows,
                "list", pageRows);
    }

    /**
     * Pages only the collected {@code db_table_t} snapshot.  It deliberately
     * does not call a metadata explorer: opening step 2 is now a local-MySQL
     * operation even for a datasource with tens of thousands of physical tables.
     */
    public Map<String, Object> snapshot(String datasourceId, int pageNo, int pageSize, Integer offset,
                                        String keyword, String businessType) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        int safePage = Math.max(1, pageNo);
        // The registration UI can append a deliberately chosen 200/500/1000
        // records in one snapshot read.  This stays bounded while avoiding
        // several 100-record round trips for one user action.
        int safeSize = Math.min(1000, Math.max(1, pageSize));
        Set<String> missing = latestDeletedNames(tenantId, sourceId);
        StringBuilder where = new StringBuilder(" tenant_id=? and datasource_id=? and is_del=0 and (annotated=0 or annotated is null) ");
        List<Object> args = new ArrayList<>(List.of(tenantId, sourceId));
        if (!blank(keyword)) {
            where.append(" and (lower(table_name) like ? or lower(coalesce(table_comment, table_name_cn, '')) like ?)");
            String match = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(match);
            args.add(match);
        }
        if (!blank(businessType) && !"全部".equals(businessType.trim())) {
            where.append(" and business_type=?");
            args.add(businessType.trim());
        }
        List<Map<String, Object>> candidateRows = jdbc.queryForList("select * from db_table_t where " + where
                + " order by updated_time desc, tid desc", args.toArray());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : candidateRows) {
            if (!missing.contains(normalize(text(row.get("table_name"))))) rows.add(snapshotRow(row));
        }
        int requestedOffset = offset == null ? (safePage - 1) * safeSize : Math.max(0, offset);
        int from = Math.min(requestedOffset, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        long marked = jdbc.queryForObject("""
                select count(*) from db_table_t
                 where tenant_id=? and datasource_id=? and is_del=0 and annotated=1
                """, Long.class, tenantId, sourceId);
        return Map.of(
                "pageNo", safePage,
                "pageSize", safeSize,
                "offset", from,
                "total", rows.size(),
                "physicalTotal", rows.size() + marked,
                "unmarkedTotal", rows.size(),
                "rows", rows.subList(from, to),
                "list", rows.subList(from, to));
    }

    /**
     * Binds a missing registered table to the identically named table found by
     * the latest re-collection.  The original registered asset keeps its
     * governance configuration; the newly collected, unmarked snapshot row is
     * removed so two rows cannot represent the same physical table.
     */
    @Transactional
    public Map<String, Object> renameMissingTable(String datasourceId, String tableId, String requestedTableName) {
        String tenantId = TenantContext.requireTenantId();
        String sourceId = requiredDatasourceId(datasourceId);
        String assetId = text(tableId);
        String newTableName = text(requestedTableName);
        if (blank(assetId)) throw new IllegalArgumentException("缺少登记表标识");
        if (blank(newTableName)) throw new IllegalArgumentException("数据表名不能为空");
        if (newTableName.length() > 255) throw new IllegalArgumentException("数据表名不能超过255个字符");

        List<Map<String, Object>> targetRows = jdbc.queryForList("""
                select tid, table_name, annotated from db_table_t
                 where tid=? and tenant_id=? and datasource_id=? and is_del=0
                """, assetId, tenantId, sourceId);
        if (targetRows.isEmpty()) throw new IllegalArgumentException("登记表不存在或无访问权限");
        Map<String, Object> target = targetRows.getFirst();
        String oldTableName = text(target.get("table_name"));
        if (!latestDeletedNames(tenantId, sourceId).contains(normalize(oldTableName))) {
            throw new IllegalStateException("该表不在当前变更列表中，请重新探查后再修改");
        }
        if (oldTableName.equalsIgnoreCase(newTableName)) {
            return Map.of("tableId", assetId, "oldTableName", oldTableName,
                    "newTableName", newTableName, "mergedSnapshot", false);
        }

        List<Map<String, Object>> duplicateRows = jdbc.queryForList("""
                select tid, annotated from db_table_t
                 where tenant_id=? and datasource_id=? and is_del=0 and lower(table_name)=lower(?) and tid<>?
                 order by annotated desc, updated_time desc, tid desc
                """, tenantId, sourceId, newTableName, assetId);
        if (duplicateRows.isEmpty()) {
            throw new IllegalArgumentException("新表名尚未在本次探查结果中发现，请先重新探查后再关联");
        }
        for (Map<String, Object> duplicate : duplicateRows) {
            if (number(duplicate.get("annotated")) == 1) {
                throw new IllegalStateException("新表名已对应另一张已标注登记表，不能覆盖");
            }
        }

        Timestamp timestamp = now();
        for (Map<String, Object> duplicate : duplicateRows) {
            jdbc.update("""
                    update db_table_t set is_del=1, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=? and annotated=0 and is_del=0
                    """, timestamp, text(duplicate.get("tid")), tenantId, sourceId);
        }
        jdbc.update("""
                update db_table_t set table_name=?, table_name_en=?, updated_time=?
                 where tid=? and tenant_id=? and datasource_id=? and is_del=0
                """, newTableName, newTableName, timestamp, assetId, tenantId, sourceId);
        removeLatestDeletedName(tenantId, sourceId, oldTableName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableId", assetId);
        result.put("oldTableName", oldTableName);
        result.put("newTableName", newTableName);
        result.put("mergedSnapshot", true);
        result.put("removedSnapshotCount", duplicateRows.size());
        return result;
    }

    private void runCollection(String jobId, String tenantId, String datasourceId, boolean refresh) {
        try {
            mark(jobId, "SCANNING", 0, 0, 0, 0, 0, 0, List.of(), "");
            DataSourceConfig config = loadConfig(tenantId, datasourceId);
            List<TableInfo> physical = collectPhysicalTables(tenantId, datasourceId, config);
            if (physical == null) physical = List.of();

            List<TableInfo> distinctPhysical = distinctTables(physical);
            Map<String, Map<String, Object>> registeredByName = registeredByName(tenantId, datasourceId);
            migrateLegacyCurrentOwnerSnapshotNames(
                    tenantId, datasourceId, config, distinctPhysical, registeredByName);
            // A legacy bare name may have been migrated to OWNER.TABLE above.
            // Reload before calculating additions/deletions so the current
            // physical object remains one stable registration record.
            registeredByName = registeredByName(tenantId, datasourceId);
            Set<String> physicalNames = new HashSet<>();
            for (TableInfo table : distinctPhysical) physicalNames.add(normalize(table.getTableName()));
            List<Map<String, Object>> deleted = refresh
                    ? deletedRows(registeredByName, physicalNames)
                    : List.of();
            int unchanged = 0;
            List<TableInfo> additions = new ArrayList<>();
            for (TableInfo table : distinctPhysical) {
                if (registeredByName.containsKey(normalize(table.getTableName()))) unchanged++;
                else additions.add(table);
            }
            mark(jobId, "PERSISTING", distinctPhysical.size(), 0, distinctPhysical.size(), 0, unchanged,
                    deleted.size(), deleted, "");

            int persisted = 0;
            for (int offset = 0; offset < additions.size(); offset += WRITE_BATCH_SIZE) {
                List<TableInfo> batch = additions.subList(offset, Math.min(offset + WRITE_BATCH_SIZE, additions.size()));
                insertSnapshotRows(tenantId, datasourceId, batch);
                persisted += batch.size();
                mark(jobId, "PERSISTING", distinctPhysical.size(), persisted, distinctPhysical.size(), persisted,
                        unchanged, deleted.size(), deleted, "");
            }
            jdbc.update("""
                    update db_datasource_t set table_num=?, updated_time=?
                     where tid=? and tenant_id=? and is_del=0
                    """, distinctPhysical.size(), now(), datasourceId, tenantId);
            com.linewell.dataelement.dataassets.runtime.DatasourceRegistrationStatus.refresh(
                    java.util.Objects.requireNonNull(jdbc.getDataSource()), tenantId, datasourceId, false);
            jdbc.update("""
                    update metadata_table_collection_job_t
                       set status=?, phase='COMPLETED', scanned_count=?, persisted_count=?, total_count=?,
                           added_count=?, unchanged_count=?, deleted_count=?, deleted_detail=?,
                           error_message='', finished_time=?, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=?
                    """, SUCCEEDED, distinctPhysical.size(), persisted, distinctPhysical.size(), persisted,
                    unchanged, deleted.size(), json.writeValueAsString(deleted), now(), now(), jobId, tenantId, datasourceId);
        } catch (Exception exception) {
            jdbc.update("""
                    update metadata_table_collection_job_t
                       set status=?, phase='FAILED', error_message=?, finished_time=?, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=?
                    """, FAILED, message(exception), now(), now(), jobId, tenantId, datasourceId);
        } finally {
            inFlightJobIds.remove(jobId);
        }
    }

    private void insertSnapshotRows(String tenantId, String datasourceId, List<TableInfo> tables) {
        Timestamp timestamp = now();
        jdbc.batchUpdate("""
                insert into db_table_t
                    (tid, datasource_id, table_name, table_name_en, table_name_cn, table_comment, table_type,
                     business_type, annotated, record_count, field_count, tenant_id, created_time, updated_time,
                     reg_time, asset_status, flow_status, is_del)
                values (?, ?, ?, ?, ?, ?, ?, '业务表', 0, ?, ?, ?, ?, ?, ?, 0, 0, 0)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
                TableInfo table = tables.get(index);
                String tableName = table.getTableName().trim();
                String comment = blank(table.getTableComment()) ? tableName : table.getTableComment().trim();
                statement.setString(1, NumericId.nextId());
                statement.setString(2, datasourceId);
                statement.setString(3, tableName);
                statement.setString(4, tableName);
                statement.setString(5, comment);
                statement.setString(6, comment);
                statement.setString(7, blank(table.getTableType()) ? "数据表" : table.getTableType());
                statement.setObject(8, table.getRowCount());
                statement.setObject(9, table.getColumnCount());
                statement.setString(10, tenantId);
                statement.setTimestamp(11, timestamp);
                statement.setTimestamp(12, timestamp);
                statement.setTimestamp(13, timestamp);
            }

            @Override
            public int getBatchSize() {
                return tables.size();
            }
        });
    }

    /**
     * The production Huawei MRS profile deliberately has no browser-visible
     * JDBC URL or password.  It must therefore use the server-owned Hive pool,
     * while ordinary JDBC datasources continue through MetadataExplorerService.
     */
    private List<TableInfo> collectPhysicalTables(String tenantId, String datasourceId, DataSourceConfig config)
            throws Exception {
        if (!isServerManagedHuaweiMrs(tenantId, datasourceId)) {
            if (usesOwnerQualifiedSnapshot(config.getDatabaseType())) {
                return collectVisibleOwnerTables(config);
            }
            return explorer.getTables(config);
        }
        List<TableInfo> tables = new ArrayList<>();
        for (String name : huaweiMrsHive.showTables(config.getDatabase())) {
            if (blank(name)) continue;
            TableInfo table = new TableInfo();
            table.setTableName(name.trim());
            table.setTableType("TABLE");
            table.setSchemaName(config.getDatabase());
            tables.add(table);
        }
        return tables;
    }

    /**
     * Collects every object the registered account can read for the database
     * families that expose objects from several users/schemas.  This method is
     * intentionally used only by the asynchronous collector: opening step 2
     * continues to read the durable local snapshot and never opens a physical
     * database connection.
     */
    private List<TableInfo> collectVisibleOwnerTables(DataSourceConfig config) {
        DatabaseType type = config.getDatabaseType();
        if (type == DatabaseType.ORACLE || type == DatabaseType.OCEANBASE_ORACLE) {
            // Oracle explorers interpret a blank schema as ALL_* metadata for
            // the current account.  A configured default schema must not turn
            // an all-access collection back into a one-owner scan.
            String originalSchema = config.getSchema();
            try {
                config.setSchema(null);
                return qualifyOwnerTables(config, explorer.getTables(config));
            } finally {
                config.setSchema(originalSchema);
            }
        }

        Map<String, Object> originalProperties = config.getConnectorProperties();
        Map<String, Object> collectionProperties = new LinkedHashMap<>();
        if (originalProperties != null) collectionProperties.putAll(originalProperties);
        collectionProperties.put(COLLECT_ALL_VISIBLE_OWNERS, true);
        try {
            config.setConnectorProperties(collectionProperties);
            return qualifyOwnerTables(config, explorer.getTables(config));
        } finally {
            config.setConnectorProperties(originalProperties);
        }
    }

    private boolean usesOwnerQualifiedSnapshot(DatabaseType type) {
        return type == DatabaseType.ORACLE
                || type == DatabaseType.KINGBASE
                || type == DatabaseType.OCEANBASE_MYSQL
                || type == DatabaseType.OCEANBASE_ORACLE;
    }

    /**
     * Persist a qualified table name whenever a visible-owner database can
     * expose more than one user/schema.  Oracle, Kingbase, and OceanBase in
     * Oracle mode intentionally qualify even the registered account's own
     * tables.  That makes the name unambiguous in the snapshot and lets the
     * table search work consistently for every owner.
     */
    private List<TableInfo> qualifyOwnerTables(DataSourceConfig config, List<TableInfo> tables) {
        if (tables == null || tables.isEmpty()) return List.of();
        DatabaseType type = config.getDatabaseType();
        boolean alwaysQualify = alwaysOwnerQualifiedSnapshot(type);
        String currentOwner = currentOwner(config);
        for (TableInfo table : tables) {
            if (table == null || blank(table.getTableName())) continue;
            String tableName = table.getTableName().trim();
            String owner = text(table.getSchemaName());
            if (!blank(owner) && (alwaysQualify || !owner.equalsIgnoreCase(currentOwner))
                    && tableName.indexOf('.') < 0) {
                table.setTableName(owner + "." + tableName);
            }
        }
        return tables;
    }

    /**
     * Previous releases stored the registered owner's Oracle-family tables as
     * bare names.  During the first re-collection after enabling uniform
     * owner-qualified names, rename that existing snapshot row in place.  It
     * preserves its annotations and prevents a false “deleted + new” pair.
     */
    private void migrateLegacyCurrentOwnerSnapshotNames(
            String tenantId,
            String datasourceId,
            DataSourceConfig config,
            List<TableInfo> physicalTables,
            Map<String, Map<String, Object>> registeredByName
    ) {
        if (!alwaysOwnerQualifiedSnapshot(config.getDatabaseType())
                || physicalTables == null || physicalTables.isEmpty()) {
            return;
        }
        String currentOwner = currentOwner(config);
        if (blank(currentOwner)) return;
        Timestamp timestamp = now();
        for (TableInfo table : physicalTables) {
            if (table == null || !currentOwner.equalsIgnoreCase(text(table.getSchemaName()))) continue;
            String qualifiedName = text(table.getTableName()).trim();
            int separator = qualifiedName.indexOf('.');
            if (separator < 1 || separator == qualifiedName.length() - 1) continue;
            String legacyName = qualifiedName.substring(separator + 1);
            Map<String, Object> legacy = registeredByName.get(normalize(legacyName));
            if (legacy == null || registeredByName.containsKey(normalize(qualifiedName))) continue;
            jdbc.update("""
                    update db_table_t set table_name=?, table_name_en=?, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=? and is_del=0
                    """, qualifiedName, qualifiedName, timestamp, text(legacy.get("tid")), tenantId, datasourceId);
            registeredByName.remove(normalize(legacyName));
            legacy.put("table_name", qualifiedName);
            registeredByName.put(normalize(qualifiedName), legacy);
        }
    }

    private boolean alwaysOwnerQualifiedSnapshot(DatabaseType type) {
        return type == DatabaseType.ORACLE
                || type == DatabaseType.KINGBASE
                || type == DatabaseType.OCEANBASE_ORACLE;
    }

    private String currentOwner(DataSourceConfig config) {
        if (config.getDatabaseType() == DatabaseType.OCEANBASE_MYSQL) {
            return text(config.getDatabase());
        }
        if (config.getDatabaseType() == DatabaseType.KINGBASE) {
            return blank(config.getSchema()) ? "public" : text(config.getSchema());
        }
        return text(config.getUsername());
    }

    private boolean isServerManagedHuaweiMrs(String tenantId, String datasourceId) {
        Map<String, Object> source = connections.resolve(tenantId, datasourceId);
        String dbType = first(source, "dbType", "db_type");
        if (!"HIVE".equalsIgnoreCase(dbType)) return false;
        String accessMode = first(source, "metadataAccessMode", "metadata_access_mode");
        String connectionMode = first(source, "hiveConnectionMode", "hive_connection_mode");
        return "server-managed-mrs".equalsIgnoreCase(accessMode)
                || "huawei-mrs".equalsIgnoreCase(connectionMode)
                || !blank(first(source, "hiveProfile", "hive_profile"));
    }

    private Map<String, Map<String, Object>> registeredByName(String tenantId, String datasourceId) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                select tid, table_name, table_name_cn, table_comment, table_type, business_type, annotated,
                       field_count, record_count
                  from db_table_t
                 where tenant_id=? and datasource_id=? and is_del=0
                """, tenantId, datasourceId)) {
            String name = normalize(text(row.get("table_name")));
            if (!name.isEmpty()) result.putIfAbsent(name, row);
        }
        return result;
    }

    private String runningJobId(String tenantId, String datasourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and status=? and is_del=0
                 order by created_time desc, tid desc
                """, tenantId, datasourceId, RUNNING);
        return rows.isEmpty() ? "" : text(rows.getFirst().get("tid"));
    }

    private List<Map<String, Object>> deletedRows(Map<String, Map<String, Object>> registered, Set<String> physicalNames) {
        List<Map<String, Object>> deleted = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : registered.entrySet()) {
            if (physicalNames.contains(entry.getKey())) continue;
            Map<String, Object> row = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tid", text(row.get("tid")));
            item.put("tableId", text(row.get("tid")));
            item.put("tableName", text(row.get("table_name")));
            item.put("tableNameEn", text(row.get("table_name")));
            item.put("tableNameCn", first(row, "table_name_cn", "table_comment", "table_name"));
            item.put("tableComment", first(row, "table_comment", "table_name_cn", "table_name"));
            item.put("tableType", text(row.get("table_type")));
            item.put("businessType", text(row.get("business_type")));
            item.put("fieldCount", number(row.get("field_count")));
            item.put("recordCount", number(row.get("record_count")));
            deleted.add(item);
        }
        return deleted;
    }

    private List<TableInfo> distinctTables(Collection<TableInfo> tables) {
        Map<String, TableInfo> result = new LinkedHashMap<>();
        for (TableInfo table : tables) {
            if (table == null || blank(table.getTableName())) continue;
            result.putIfAbsent(normalize(table.getTableName()), table);
        }
        return new ArrayList<>(result.values());
    }

    private void mark(String jobId, String phase, long scanned, long persisted, long total, long added,
                      long unchanged, long deletedCount, List<Map<String, Object>> deleted, String error) throws Exception {
        jdbc.update("""
                update metadata_table_collection_job_t
                   set phase=?, scanned_count=?, persisted_count=?, total_count=?, added_count=?,
                       unchanged_count=?, deleted_count=?, deleted_detail=?, error_message=?, updated_time=?
                 where tid=?
                """, phase, scanned, persisted, total, added, unchanged, deletedCount,
                json.writeValueAsString(deleted), error, now(), jobId);
    }

    private DataSourceConfig loadConfig(String tenantId, String datasourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, db_name, db_type, jdbc_url, username, password, pool_cfg
                  from db_datasource_t where tid=? and tenant_id=? and is_del=0
                """, datasourceId, tenantId);
        if (rows.isEmpty()) throw new IllegalArgumentException("数据源不存在或无访问权限");
        Map<String, Object> source = new LinkedHashMap<>(rows.getFirst());
        source.putAll(connections.resolve(tenantId, datasourceId));
        DatabaseType type = DatabaseType.fromCode(first(source, "dbType", "db_type"));
        // FTP/SFTP files are logical tables.  They use the FtpFileMetadataExplorer
        // rather than JDBC, but must follow the same durable step-2 collection
        // workflow as relational sources.  API/Kafka remain configuration-driven
        // sources and deliberately do not enter this physical/file scanner.
        if (type == null || (!type.isRelational() && type != DatabaseType.FTP)) {
            throw new IllegalArgumentException("当前数据源不支持数据表采集");
        }
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(type);
        // FTP/SFTP keeps its connection values under ftp* keys in pool_cfg.
        // Do not fall back to FTP's protocol default here: doing so changes a
        // saved SFTP port 22 into 21 before FtpFileMetadataExplorer is called.
        boolean ftpSource = type == DatabaseType.FTP;
        config.setHost(ftpSource
                ? first(source, "ftpHost", "host", "dbIp", "hostname")
                : first(source, "host", "dbIp", "hostname"));
        config.setPort(integer(ftpSource
                ? first(source, "ftpPort", "port", "dbPort")
                : first(source, "port", "dbPort"), type.getDefaultPort()));
        config.setDatabase(first(source, "database", "dbName", "db_name", "dbMetaDbName"));
        config.setUsername(ftpSource
                ? first(source, "ftpUsername", "username", "dbUser", "dbMetaUser", "user")
                : first(source, "username", "dbUser", "dbMetaUser", "user"));
        config.setPassword(ftpSource
                ? first(source, "ftpPassword", "password", "dbPassword", "dbMetaPassword")
                : first(source, "password", "dbPassword", "dbMetaPassword"));
        config.setJdbcUrl(first(source, "jdbcURL", "jdbcUrl", "jdbc_url"));
        config.setSchema(first(source, "schema", "defaultSchema", "currentSchema"));
        config.setExtraParams(first(source, "extraParams", "connectParams", "jdbcParams"));
        config.setAuthMode(first(source, "authMode", "auth"));
        config.setPrincipal(first(source, "principal", "hivePrincipal", "servicePrincipal"));
        config.setUserPrincipal(first(source, "userPrincipal", "clientPrincipal"));
        config.setKeytabPath(first(source, "keytabPath", "keytab"));
        config.setKrb5ConfPath(first(source, "krb5ConfPath", "krb5Conf"));
        config.setJaasConfPath(first(source, "jaasConfPath", "jaasConfig"));
        config.setZookeeperQuorum(first(source, "zookeeperQuorum", "zkQuorum"));
        config.setZookeeperNamespace(first(source, "zookeeperNamespace", "zooKeeperNamespace"));
        config.setServiceDiscoveryMode(first(source, "serviceDiscoveryMode", "serviceDiscovery"));
        config.setSaslQop(first(source, "saslQop"));
        config.setClientConfigDir(first(source, "clientConfigDir", "mrsClientConfigDir"));
        // Preserve the complete connector profile (protocol, remote path,
        // wildcard, recursion and CSV options).  FTP discovery is not possible
        // with only the denormalized host/port/user fields.
        config.setConnectorProperties(new LinkedHashMap<>(source));
        config.setConnectionTimeout(30);
        return config;
    }

    private void requireCollectableDatasource(String tenantId, String datasourceId) {
        loadConfig(tenantId, datasourceId);
    }

    private List<Map<String, Object>> deletedRows(Object raw) {
        if (raw == null || raw.toString().isBlank()) return List.of();
        try {
            List<Map<String, Object>> rows = json.readValue(raw.toString(), new TypeReference<List<Map<String, Object>>>() { });
            return rows == null ? List.of() : rows;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Set<String> latestDeletedNames(String tenantId, String datasourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select deleted_detail from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and status=? and is_del=0
                 order by created_time desc, tid desc
                """, tenantId, datasourceId, SUCCEEDED);
        if (rows.isEmpty()) return Set.of();
        Set<String> names = new HashSet<>();
        for (Map<String, Object> row : deletedRows(rows.getFirst().get("deleted_detail"))) {
            String name = normalize(text(row.get("tableName")));
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    /** Removes a resolved rename from the durable change list shown by step 2. */
    private void removeLatestDeletedName(String tenantId, String datasourceId, String tableName) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, deleted_detail from metadata_table_collection_job_t
                 where tenant_id=? and datasource_id=? and status=? and is_del=0
                 order by created_time desc, tid desc
                """, tenantId, datasourceId, SUCCEEDED);
        if (rows.isEmpty()) return;
        Map<String, Object> job = rows.getFirst();
        List<Map<String, Object>> remaining = new ArrayList<>();
        for (Map<String, Object> row : deletedRows(job.get("deleted_detail"))) {
            String candidate = first(row, "tableName", "tableNameEn", "name");
            if (!normalize(candidate).equals(normalize(tableName))) remaining.add(row);
        }
        try {
            jdbc.update("""
                    update metadata_table_collection_job_t
                       set deleted_count=?, deleted_detail=?, updated_time=?
                     where tid=? and tenant_id=? and datasource_id=? and is_del=0
                    """, remaining.size(), json.writeValueAsString(remaining), now(),
                    text(job.get("tid")), tenantId, datasourceId);
        } catch (Exception exception) {
            throw new IllegalStateException("更新变更表记录失败", exception);
        }
    }

    private Map<String, Object> snapshotRow(Map<String, Object> row) {
        String tableName = text(row.get("table_name"));
        String comment = first(row, "table_comment", "table_name_cn", "table_name");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tid", text(row.get("tid")));
        result.put("tableId", text(row.get("tid")));
        result.put("dbId", text(row.get("datasource_id")));
        result.put("datasourceId", text(row.get("datasource_id")));
        result.put("tableName", tableName);
        result.put("tableNameEn", first(row, "table_name_en", "table_name"));
        result.put("sourceTableName", tableName);
        result.put("physicalTableComment", comment);
        result.put("savedResourceName", comment);
        result.put("tableNameCn", first(row, "table_name_cn", "table_comment", "table_name"));
        result.put("tableComment", comment);
        result.put("tableType", first(row, "table_type"));
        result.put("businessType", first(row, "business_type"));
        result.put("businessTypeReason", first(row, "business_type_reason"));
        result.put("recordCount", number(row.get("record_count")));
        result.put("fieldCount", number(row.get("field_count")));
        result.put("storageSize", first(row, "storage_size", "total_size_formatted"));
        result.put("totalSizeFormatted", first(row, "total_size_formatted", "storage_size"));
        result.put("totalSizeBytes", number(row.get("total_size_bytes")));
        result.put("annotated", false);
        result.put("registered", false);
        return result;
    }

    private int progressPercent(String status, String phase, long total, long persisted) {
        if (SUCCEEDED.equals(status)) return 100;
        if (FAILED.equals(status)) return 0;
        if ("QUEUED".equals(phase) || "SCANNING".equals(phase)) return 8;
        if (total <= 0) return 92;
        return Math.max(10, Math.min(96, 10 + (int) Math.round(86D * persisted / total)));
    }

    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private static String normalize(String value) { return text(value).toLowerCase(Locale.ROOT); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ignored) { return 0L; }
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static String first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }
    private static String requiredDatasourceId(String value) {
        String id = text(value);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("dbId 格式不正确");
        return id;
    }
    private static String message(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }
}
