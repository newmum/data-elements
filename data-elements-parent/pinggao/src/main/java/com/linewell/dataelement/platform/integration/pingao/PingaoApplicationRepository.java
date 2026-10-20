package com.linewell.dataelement.platform.integration.pingao;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists only the minimal application fields needed by data-source registration. */
@Repository
public class PingaoApplicationRepository {

    static final String PINGAO = "PINGAO";
    static final String LEGACY = "LEGACY";

    private final JdbcTemplate jdbcTemplate;

    public PingaoApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SyncWriteResult replacePingaoSnapshot(String tenantId, List<PingaoAbility> abilities, Instant syncedAt) {
        Timestamp timestamp = Timestamp.from(syncedAt);
        // A complete remote snapshot was already fetched. Missing records become unselectable, never deleted.
        jdbcTemplate.update("""
                update sym_application_t
                   set sync_active = 0, last_synced_at = ?
                 where tenant_id = ? and data_origin = ? and is_del = 0
                """, timestamp, tenantId, PINGAO);

        int inserted = 0;
        int updated = 0;
        for (PingaoAbility ability : abilities) {
            int changed = jdbcTemplate.update("""
                    update sym_application_t
                       set app_name = ?, sync_active = ?, last_synced_at = ?, is_del = 0
                     where tenant_id = ? and data_origin = ? and source_key = ?
                    """, ability.abilityName(), ability.isOnline() ? 1 : 0, timestamp,
                    tenantId, PINGAO, ability.id());
            if (changed > 0) {
                updated++;
                continue;
            }
            jdbcTemplate.update("""
                    insert into sym_application_t
                        (tid, app_name, is_del, tenant_id, data_origin, source_key, sync_active, last_synced_at)
                    values (?, ?, 0, ?, ?, ?, ?, ?)
                    """, NumericId.nextId(), ability.abilityName(), tenantId, PINGAO, ability.id(),
                    ability.isOnline() ? 1 : 0, timestamp);
            inserted++;
        }
        return new SyncWriteResult(inserted, updated);
    }

    public List<Map<String, Object>> selectableOptions(String tenantId) {
        return jdbcTemplate.query("""
                select app_name, tid
                  from sym_application_t
                 where tenant_id = ? and data_origin = ? and sync_active = 1 and is_del = 0
                 order by app_name asc, tid asc
                """, (resultSet, rowNumber) -> Map.<String, Object>of(
                "label", resultSet.getString(1),
                "value", resultSet.getString(2)), tenantId, PINGAO);
    }

    /**
     * New registrations may use history only before the first successful Pingao snapshot.
     * Once a snapshot has succeeded, an empty official result is still authoritative.
     */
    public List<Map<String, Object>> registrationOptions(String tenantId) {
        if (hasSuccessfulSnapshot(tenantId)) {
            return selectableOptions(tenantId);
        }
        return jdbcTemplate.query("""
                select app_name, tid
                  from sym_application_t
                 where tenant_id = ? and data_origin = ? and is_del = 0
                 order by app_name asc, tid asc
                """, (resultSet, rowNumber) -> Map.<String, Object>of(
                "label", resultSet.getString(1) + "（历史）",
                "value", resultSet.getString(2)), tenantId, LEGACY);
    }

    public SyncState syncState(String tenantId) {
        List<SyncState> states = jdbcTemplate.query("""
                select has_success, last_status, last_attempt_at, last_success_at,
                       last_fetched_count, last_online_count
                  from pingao_application_sync_state_t
                 where tenant_id = ?
                """, (resultSet, rowNumber) -> new SyncState(
                resultSet.getInt(1) == 1,
                resultSet.getString(2),
                timestampToInstant(resultSet.getTimestamp(3)),
                timestampToInstant(resultSet.getTimestamp(4)),
                resultSet.getLong(5),
                resultSet.getLong(6)), tenantId);
        return states.isEmpty() ? SyncState.never() : states.getFirst();
    }

    public boolean hasSuccessfulSnapshot(String tenantId) {
        return syncState(tenantId).hasSuccess();
    }

    public void recordSuccessfulSync(String tenantId, Instant syncedAt, long fetched, long online) {
        int updated = jdbcTemplate.update("""
                update pingao_application_sync_state_t
                   set has_success = 1, last_status = 'SUCCESS', last_attempt_at = ?,
                       last_success_at = ?, last_fetched_count = ?, last_online_count = ?, updated_time = ?
                 where tenant_id = ?
                """, Timestamp.from(syncedAt), Timestamp.from(syncedAt), fetched, online,
                Timestamp.from(syncedAt), tenantId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into pingao_application_sync_state_t
                        (tenant_id, has_success, last_status, last_attempt_at, last_success_at,
                         last_fetched_count, last_online_count, updated_time)
                    values (?, 1, 'SUCCESS', ?, ?, ?, ?, ?)
                    """, tenantId, Timestamp.from(syncedAt), Timestamp.from(syncedAt), fetched, online,
                    Timestamp.from(syncedAt));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedSync(String tenantId, Instant attemptedAt) {
        int updated = jdbcTemplate.update("""
                update pingao_application_sync_state_t
                   set last_status = 'FAILED', last_attempt_at = ?, updated_time = ?
                 where tenant_id = ?
                """, Timestamp.from(attemptedAt), Timestamp.from(attemptedAt), tenantId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into pingao_application_sync_state_t
                        (tenant_id, has_success, last_status, last_attempt_at, last_fetched_count,
                         last_online_count, updated_time)
                    values (?, 0, 'FAILED', ?, 0, 0, ?)
                    """, tenantId, Timestamp.from(attemptedAt), Timestamp.from(attemptedAt));
        }
    }

    public DuplicatePreview duplicatePreview(String tenantId) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                select legacy_app.tid as legacy_tid,
                       legacy_app.app_name as legacy_app_name,
                       pingao_app.tid as pingao_tid,
                       pingao_app.app_name as pingao_app_name,
                       pingao_app.source_key as pingao_source_key,
                       count(datasource.tid) as reference_count
                  from sym_application_t legacy_app
                  join sym_application_t pingao_app
                    on pingao_app.tenant_id = legacy_app.tenant_id
                   and pingao_app.data_origin = ? and pingao_app.is_del = 0
                   and lower(trim(pingao_app.app_name)) = lower(trim(legacy_app.app_name))
                  left join db_datasource_t datasource
                    on datasource.app_id = legacy_app.tid and datasource.tenant_id = legacy_app.tenant_id
                   and coalesce(datasource.is_del, 0) = 0
                 where legacy_app.tenant_id = ? and legacy_app.data_origin = ? and legacy_app.is_del = 0
                 group by legacy_app.tid, legacy_app.app_name, pingao_app.tid, pingao_app.app_name,
                          pingao_app.source_key
                 order by legacy_app.app_name asc, legacy_app.tid asc, pingao_app.tid asc
                """, PINGAO, tenantId, LEGACY);
        return new DuplicatePreview(candidates.size(), candidates);
    }

    public LegacyPurgePreview legacyPurgePreview(String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select a.tid, a.app_name, count(d.tid) as reference_count
                  from sym_application_t a
                  left join db_datasource_t d
                    on d.app_id = a.tid and d.tenant_id = a.tenant_id and coalesce(d.is_del, 0) = 0
                 where a.tenant_id = ? and a.data_origin = ? and a.is_del = 0
                 group by a.tid, a.app_name
                 order by a.app_name asc, a.tid asc
                """, tenantId, LEGACY);
        List<Map<String, Object>> removable = new ArrayList<>();
        List<Map<String, Object>> referenced = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("reference_count")).longValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tid", row.get("tid"));
            item.put("appName", row.get("app_name"));
            item.put("referenceCount", count);
            (count == 0 ? removable : referenced).add(item);
        }
        return new LegacyPurgePreview(rows.size(), removable, referenced);
    }

    public int softDeleteLegacy(String tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return jdbcTemplate.update("""
                    update sym_application_t a
                       set is_del = 1
                     where a.tenant_id = ? and a.data_origin = ? and a.is_del = 0
                       and not exists (
                         select 1 from db_datasource_t d
                          where d.app_id = a.tid and d.tenant_id = a.tenant_id and coalesce(d.is_del, 0) = 0
                       )
                    """, tenantId, LEGACY);
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(LEGACY);
        params.addAll(ids);
        String sql = "update sym_application_t a set is_del = 1 "
                + "where a.tenant_id = ? and a.data_origin = ? and a.is_del = 0 "
                + "and a.tid in (" + placeholders + ") "
                + "and not exists (select 1 from db_datasource_t d "
                + "where d.app_id = a.tid and d.tenant_id = a.tenant_id and coalesce(d.is_del, 0) = 0)";
        return jdbcTemplate.update(sql, params.toArray());
    }

    public record SyncWriteResult(int inserted, int updated) { }

    public record SyncState(
            boolean hasSuccess,
            String lastStatus,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            long lastFetchedCount,
            long lastOnlineCount) {
        static SyncState never() {
            return new SyncState(false, "NEVER", null, null, 0, 0);
        }
    }

    public record DuplicatePreview(int total, List<Map<String, Object>> candidates) { }

    public record LegacyPurgePreview(
            int total, List<Map<String, Object>> removable, List<Map<String, Object>> referenced) { }

    private static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
