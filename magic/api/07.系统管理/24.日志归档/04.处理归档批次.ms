{
  "id": "tenant_logarchive_action_post_v1",
  "groupId": "sys_logarchive_v1",
  "name": "04.处理归档批次",
  "path": "/action",
  "method": "POST",
  "parameters": [],
  "paths": [],
  "options": [
    {"name": "access", "value": "TENANT_SESSION"}
  ],
  "description": "按当前行业权限浏览、冻结或解冻归档批次；只清理已校验文件与热库范围共同包含的事件。"
}
================================
import '@/common/loggrants' as grants
import '@/common/logresponse' as respond
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.Long
import java.lang.Number
import java.lang.SecurityException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayList
import java.util.HashSet
import java.util.Map
import auditEvents
import request
import tableRead
import tableWrite
import tenantRuntime

// POST /sym/logarchive/action。验证归档和来源日志权限，冻结与清理均在同一行业库事务中审计。
return respond(() => {
    var tenant = tenantRuntime.id()
    var actor = StpUtil.getLoginIdAsString()
    var application = StpUtil.getTokenSession().get('appId')
    var appId = application == null ? '' : application + ''
    var permission = grants(actor, appId, null)
    var mode = permission.modes.get('archives')
    if (mode == null) { throw new SecurityException('没有此日志模块的访问权限') }
    if (mode != 'ALL') { throw new SecurityException('归档管理需要该日志模块的完整范围授权') }
    if (!(body instanceof Map)) { throw new IllegalArgumentException('请求体必须是 JSON 对象') }

    // 旧地址由兼容层将路径 ID 置入查询参数。同名正文和原有查询参数均不能覆盖路径 ID。
    var values = request.getValues('id')
    var id = values == null || values.isEmpty() ? body.get('id') : values[0]
    if (!(id instanceof String) || id.isBlank() || id.length() > 64) {
        throw new SecurityException('归档不存在或无权访问')
    }
    var text = (value, maximum) => {
        var rendered = value == null ? '' : value + ''
        if (rendered.length() > maximum) { throw new IllegalArgumentException('日志查询内容过长') }
        return rendered
    }
    var number = (name, fallback, minimum, maximum) => {
        var value = body.get(name)
        try {
            var parsed = value == null ? fallback : Long.parseLong(value + '')
            if (parsed >= minimum && parsed <= maximum) { return parsed }
        } catch (error) { /* 使用原版本及分页参数错误。 */ }
        throw new IllegalArgumentException('分页参数无效')
    }
    var action = text(body.get('action'), 20)
    if (!['browse', 'hold', 'release', 'purge-plan', 'purge'].contains(action)) {
        throw new IllegalArgumentException('归档操作无效')
    }
    var archiveTable = 'log_archive_batch_t'
    var conditions = {scope_kind: 'TENANT', scope_id: tenant, identity_domain: 'global'}
    var archiveColumns = ['tid', 'event_store', 'status', 'hold_status', 'version', 'range_start', 'range_end',
        'object_key', 'sha256', 'record_count']
    var requireArchive = row => {
        if (row == null) { throw new SecurityException('归档不存在或无权访问') }
        return row
    }
    var writeAudit = (operation, details) => {
        var event = auditEvents.prepare('OPERATION', operation, 'SUCCESS', 'LOG_ARCHIVE', id, null, details)
        db.table(event.table).primary('tid').insert(event.row)
    }
    var updateArchive = (archive, changes) => {
        changes.put('version', BigDecimal.valueOf(archive.version.longValue()).add(BigDecimal.ONE))
        changes.put('updated_time', LogTimes.utc(Instant.now()))
        if (db.table(archiveTable).where().eq('tenant_id', tenant).eq('scope_kind', 'TENANT')
            .eq('scope_id', tenant).eq('identity_domain', 'global').eq('tid', id).eq('version', archive.version)
            .update(changes, true) != 1) {
            throw new IllegalStateException('归档状态已变更，请刷新后重试')
        }
    }

    if (['hold', 'release'].contains(action)) {
        return db.transaction(() => {
            var archive = requireArchive(tableWrite.lockRow(archiveTable, tenant, 'tid', id, ['version'], conditions))
            var reason = text(body.get('reason'), 512).trim()
            if (reason.isEmpty()) { throw new IllegalArgumentException('请填写冻结或解除冻结的原因') }
            updateArchive(archive, {hold_status: action == 'hold' ? 'HELD' : 'NONE', hold_reason: reason})
            writeAudit(action == 'hold' ? 'LOG_ARCHIVE_HELD' : 'LOG_ARCHIVE_RELEASED', {phase: action})
            return {saved: true}
        })
    }

    var column = (name, type) => tableRead.column('a', name, type)
    var selected = tableRead.from(archiveTable, 'a', tenant)
    selected.where(tableRead.equal(column('tid', 'TEXT'), id))
    for (name in conditions.keySet()) { selected.where(tableRead.equal(column(name, 'TEXT'), conditions.get(name))) }
    var projection = {}
    for (name in archiveColumns) {
        var type = ['version', 'record_count'].contains(name) ? 'NUMBER' : name.startsWith('range_') ? 'TIME' : 'TEXT'
        projection.put(name, column(name, type))
    }
    var candidates = selected.rows(projection, 1)
    var archive = requireArchive(candidates.isEmpty() ? null : candidates[0])
    var requireFullSource = sourceMode => {
        if (sourceMode == null) { throw new SecurityException('没有此日志模块的访问权限') }
        if (sourceMode != 'ALL') { throw new SecurityException('归档管理需要该日志模块的完整范围授权') }
    }
    var files = SpringUtil.getBean('logArchiveService')
    if (action == 'browse') {
        requireFullSource(permission.modes.get(archive.event_store))
        var events = files.readVerifiedFile(archive)
        var field = text(body.getOrDefault('sortField', 'event_time'), 40)
        var direction = text(body.getOrDefault('sortDir', 'asc'), 4)
        if (!['event_time', 'action_code', 'actor_name', 'duration_ms'].contains(field) || !['asc', 'desc'].contains(direction)) {
            throw new IllegalArgumentException('排序参数无效')
        }
        var ordered = events.sort((left, right) => {
            var a = left.get(field)
            var b = right.get(field)
            var compared = field == 'duration_ms' ? Long.compare(a instanceof Number ? a.longValue() : -1,
                b instanceof Number ? b.longValue() : -1) : (a == null ? '' : a + '').compareTo(b == null ? '' : b + '')
            return direction == 'desc' ? -compared : compared
        })
        var page = number('page', 1, 1, 10000)
        var size = number('size', 20, 1, 100)
        var start = Math.min(ordered.size(), (page - 1) * size)::int
        return {list: ordered.subList(start, Math.min(ordered.size(), start + size)::int),
            total: ordered.size(), page: page, size: size}
    }

    return db.transaction(() => {
        // 同一来源的重叠区间按主键顺序锁定。冻结和确认清理视锁顺序读取现行版本及状态。
        var kind = archive.event_store
        var start = LogTimes.instant(archive.range_start)
        var end = LogTimes.instant(archive.range_end)
        var utcStart = LogTimes.utc(start)
        var utcEnd = LogTimes.utc(end)
        var locks = {archive: null, held: false}
        tableWrite.forEachOverlappingLockedRow(archiveTable, tenant, 'tid', archiveColumns,
            {scope_kind: 'TENANT', scope_id: tenant, identity_domain: 'global', event_store: kind},
            'range_start', 'range_end', utcStart, utcEnd, row => {
                if (row.tid == id) { locks.put('archive', row) }
                if (row.hold_status == 'HELD') { locks.put('held', true) }
            })
        var current = requireArchive(locks.archive)
        if (!['VERIFIED', 'COMPLETE'].contains(current.status)) {
            throw new IllegalStateException('只允许清理已经校验完成的归档')
        }
        if (current.hold_status == 'HELD') { throw new IllegalStateException('此归档已冻结，不能清理') }
        // 等待区间锁期间授权可能撤销，清理前重新核验来源权限。
        requireFullSource(grants(actor, appId, kind).modes.get(kind))
        if (locks.held) { throw new IllegalStateException('时间区间内存在冻结归档，不能清理') }
        var events = files.readVerifiedFile(current)
        if (Duration.between(start, end).compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException('请选择不超过90天的有效时间区间')
        }
        var archivedIds = new HashSet()
        for (event in events) { archivedIds.add(event.tid == null ? 'null' : event.tid + '') }

        var relation = {auth: 'log_user_login_t', operations: 'log_system_t', requests: 'log_request_t', security: 'log_system_t'}.get(kind)
        var query = tableRead.from(relation, 'e', tenant).withTimeoutSeconds(15)
        var eventId = tableRead.column('e', 'tid', 'TEXT')
        var category = tableRead.column('e', 'category', 'TEXT')
        if (kind == 'security') { query.where(tableRead.equal(category, 'SECURITY')) }
        if (kind == 'operations') {
            query.where(tableRead.any([tableRead.isNull(category), tableRead.in(category, ['OPERATION', 'TASK'])]))
        }
        var zone = ZoneId.of('Asia/Shanghai')
        var localStart = LocalDateTime.ofInstant(start, zone)
        var localEnd = LocalDateTime.ofInstant(end, zone)
        var eventTime = tableRead.column('e', 'event_time', 'TIME')
        var createdTime = tableRead.column('e', 'created_time', 'TIME')
        var range = (field, from, until) => tableRead.all([
            tableRead.any([tableRead.greaterThan(field, from), tableRead.equal(field, from)]),
            tableRead.not(tableRead.any([tableRead.greaterThan(field, until), tableRead.equal(field, until)]))
        ])
        query.where(tableRead.any([range(eventTime, utcStart, utcEnd),
            tableRead.all([tableRead.isNull(eventTime), range(createdTime, localStart, localEnd)])]))
        var eligible = new ArrayList()
        var matched = {count: 0}
        // 一次有界扫描热库。只有已通过文件校验且仍在当前行业、来源及时间区间的 ID 可以删除。
        query.forEachOrderedRow({tid: eventId}, [eventId], ['asc'], true, 100001, row => {
            matched.put('count', matched.count + 1)
            if (matched.count > 100000) { throw new IllegalStateException('当前区间超过单批上限，请重新分段归档') }
            if (archivedIds.contains(row.tid + '')) { eligible.add(row.tid) }
        })
        if (action == 'purge-plan') {
            return {count: eligible.size(), archiveCount: events.size(), version: current.version, verified: true}
        }
        if (id != body.get('confirmationId') || number('version', -1, 1, Long.MAX_VALUE) != current.version.longValue()) {
            throw new IllegalStateException('请先预览清理范围并再次确认')
        }
        var deleted = 0
        var offset = 0
        while (offset < eligible.size()) {
            var ids = eligible.subList(offset, Math.min(eligible.size(), offset + 500))
            var mutation = db.table(relation).where().eq('tenant_id', tenant).in('tid', ids)
                .and(time => time.and(utc => utc.gte('event_time', utcStart).lt('event_time', utcEnd)).or()
                    .and(legacy => legacy.isNull('event_time').gte('created_time', localStart).lt('created_time', localEnd)))
            if (kind == 'security') { mutation.eq('category', 'SECURITY') }
            if (kind == 'operations') { mutation.and(group => group.isNull('category').or().in('category', ['OPERATION', 'TASK'])) }
            deleted += mutation.delete()
            offset += 500
        }
        if (deleted != eligible.size()) { throw new IllegalStateException('清理范围已变更，请重新预览') }
        updateArchive(current, {status: 'COMPLETE', purge_completed_at: LogTimes.utc(Instant.now())})
        writeAudit('LOG_ARCHIVE_HOT_PURGED', {count: deleted})
        return {deleted: deleted, archiveRetained: true}
    })
})
