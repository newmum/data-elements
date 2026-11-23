{
  "properties": {},
  "id": "6138f2d3794847a78cb00dd1c8c14385",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "04.管理接入模板版本",
  "path": "/templateversion",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "在当前行业内创建、发布或停用 NiFi 接入模板版本。"
}
================================
import tenantRuntime
import dataScope
import tableWrite
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import java.lang.System
import java.util.List
import java.util.ArrayList
import tableRead
import cn.hutool.json.JSONUtil
import java.util.Map
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as datasourceTypes
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry as manifests

// POST /nifi/canvas/templateversion 保存模板新版本、发布或停用。已发布定义通过创建新版本修改。
var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId.isBlank()) { exit 401, '请重新登录' }
if (dataScope.dataSource().scope != 'ALL') { exit 403, '当前角色无权管理接入模板' }
var action = (body?.action || '').toString()
var requested = (body?.tid || '').toString().trim()
if (!['create', 'save', 'publish', 'retire', 'bind', 'default'].contains(action)) { exit 400, '不支持的模板操作' }
if (requested != '' && !requested.matches('[A-Za-z0-9_-]{1,32}')) { exit 400, '模板版本标识无效' }
var spec = body?.spec
var name = (body?.name || '').toString().trim()
var validateTargets = (recipe, ids, required) => {
    if (ids != null && !(ids instanceof List)) { exit 400, '目标库绑定须为数组' }
    if (ids == null || ids.isEmpty()) {
        if (required) { exit 409, '请先在模板管理配置所有目标库' }
        return []
    }
    if (!(ids instanceof List) || ids.size() != recipe.targets.size() || ids.size() > 8) {
        exit 400, '目标库绑定数量与模板目标数量不一致'
    }
    var unique = []
    for (id in ids) {
        var value = (id || '').toString().trim()
        if (!value.matches('[A-Za-z0-9_-]{1,64}') || unique.contains(value)) { exit 400, '目标库标识无效或重复' }
        unique.add(value)
    }
    var query = tableRead.from('db_datasource_t','d',tenantId)
    var column = (name,type) => tableRead.column('d',name,type)
    query.where(tableRead.in(column('tid','TEXT'),unique))
    query.where(tableRead.equal(column('is_del','NUMBER'),0))
    query.where(tableRead.equal(column('show_connect','NUMBER'),1))
    var rows = query.rows({tid:column('tid','TEXT'),dbType:column('db_type','TEXT'),poolCfg:column('pool_cfg','LOB'),
        createdBy:column('created_by','TEXT'),orgId:column('org_id','TEXT')},column('tid','TEXT'),'asc',unique.size())
    var byId = {}
    for (row in dataScope.visibleDataSourcesFor('MENU:2081000000000000002',rows)) { byId[row.tid] = row }
    for (index, id in unique) {
        var row = byId[id]
        if (row == null) { exit 403, '目标库不存在、未提供连接或超出当前数据范围' }
        var cfg = row.poolCfg == null ? {} : JSONUtil.parseObj(row.poolCfg)
        var kind = datasourceTypes.configType(row.dbType,cfg)
        if (kind == 'DAMENG') { kind = 'DM' }
        if (kind != recipe.targets[index]) { exit 400, '目标库类型与模板目标不匹配' }
    }
    return unique
}

var definition = null
if (action == 'create' || action == 'save') {
    if (name == '' || name.length() > 96) { exit 400, '模板名称须为1至96个字符' }
    var targets = spec?.targets
    if (!(targets instanceof List) || targets.isEmpty() || targets.size() > 8) {
        exit 400, '模板须指定1至8个目标类型'
    }
    var supported = ['MYSQL', 'POSTGRESQL', 'ORACLE', 'OCEANBASE_ORACLE', 'OCEANBASE_MYSQL', 'DM', 'KINGBASE', 'SQLSERVER', 'HIVE']
    for (target in targets) {
        if (!supported.contains(target)) { exit 400, '模板含不支持的目标类型' }
    }
    var topology = spec?.topology
    if (!['SERIAL', 'PARALLEL'].contains(topology)) { exit 400, '请选择有效的任务拓扑' }
    var sourceType = spec?.sourceType
    var sources = ['ANY','JDBC']
    for (type in DatabaseType.values()) {
        if (type != DatabaseType.MAXCOMPUTE) { sources.add(type == DatabaseType.DAMENG ? 'DM' : type.name()) }
    }
    if (!sources.contains(sourceType)) { exit 400, '来源类型尚无受支持的采集组件' }
    var sourceSchedule = (spec?.sourceSchedule || (sourceType == 'ANY' ? 'REGISTERED' : 'TEMPLATE')).toString()
    if (!['REGISTERED','TEMPLATE'].contains(sourceSchedule)) { exit 400, '请选择有效的来源调度策略' }
    var interval = (spec?.intervalSeconds || '').toString()
    var fetchSize = (spec?.fetchSize || '').toString()
    if (!interval.matches('[0-9]{1,5}') || interval::int < 10 || interval::int > 86400
        || !fetchSize.matches('[0-9]{1,5}') || fetchSize::int < 1 || fetchSize::int > 50000) {
        exit 400, '调度间隔为10至86400秒，读取批次为1至50000行'
    }
    var normalized = {topology: topology, sourceType: sourceType, targets: targets,
        intervalSeconds: interval::int, fetchSize: fetchSize::int,sourceSchedule:sourceSchedule,
        engine:spec?.engine == 'GENERATED' ? 'GENERATED' : 'CANVAS'}
    if (spec?.hiveMergeMaxAgeSeconds != null) {
        var maxAge = spec.hiveMergeMaxAgeSeconds.toString()
        if (!targets.contains('HIVE') || !maxAge.matches('[0-9]{1,5}') || maxAge::int < 1 || maxAge::int > 86400) {
            exit 400, '仅 Hive 目标支持合并等待上限，范围为1至86400秒'
        }
        normalized.hiveMergeMaxAgeSeconds = maxAge::int
    }
    normalized.targetDatasourceIds = validateTargets(normalized,spec?.targetDatasourceIds,false)
    if (spec?.previewImage != null && spec.previewImage != '') {
        var preview = spec.previewImage.toString()
        if (preview.length() > 700000 || !preview.matches('data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+')) {
            exit 400, '流程截图须为PNG或JPEG，最大500KB'
        }
        normalized.previewImage = preview
    }
    // 完整画布模板保存全部节点和连线；来源与目标使用逻辑槽位，连接由当前租户实例化。
    var flow = spec?.flow
    if (flow != null) {
        if (!(flow.nodes instanceof List) || flow.nodes.isEmpty() || flow.nodes.size() > 100
            || !(flow.edges instanceof List) || flow.edges.size() > 300) { exit 400, '流程须包含1至100个组件，最多300条连接' }
        if (targets.size() > 1 && topology != 'PARALLEL') { exit 400, '多目标完整画布请选择并行写入' }
        var nodesById = {}
        var bindings = {}
        var incoming = {}
        var outgoing = {}
        for (node in flow.nodes) {
            var nodeId = (node.id || '').toString()
            if (!nodeId.matches('[A-Za-z0-9_-]{1,96}') || nodesById[nodeId] != null) { exit 400, '流程组件标识无效或重复' }
            var manifest = manifests.get(node.manifestKey)
            if (manifest == null || manifest.category() != node.category) { exit 400, '流程包含不支持的组件类型' }
            if (!(node.config instanceof Map)) { exit 400, '组件配置须为对象' }
            var binding = (node.config.templateBinding || '').toString()
            if (binding != '') {
                if (bindings[binding] != null) { exit 400, '模板绑定槽位重复' }
                bindings[binding] = node
            }
            if (node.category == 'source' && !['source','source:backfill'].contains(binding)) {
                exit 400, '来源组件须绑定source或source:backfill槽位'
            }
            if (binding == 'source:backfill') {
                if (node.category != 'source') { exit 400, '备用补数绑定必须使用来源组件' }
                // 模板中的补数入口默认禁用，窗口条件由操作者填写后单独启用，不复用主源水位。
                node.config.backfillSource = true
                node.config.enabled = false
                node.config.scheduledState = 'DISABLED'
                node.config.syncMode = 'FULL'
                var backfillRange = (node.config.customWherePredicate || node.config.whereClause
                    || node.config.additionalWhereClause || node.config.customQuery || '').toString().trim()
                if (backfillRange == '') { node.config.customWherePredicate = '1=0' }
            } else if (node.config.backfillSource == true) { exit 400, '备用补数来源须绑定source:backfill槽位' }
            if (node.category == 'sink' && !binding.matches('target:[0-7]')) { exit 400, '目标组件须绑定目标槽位' }
            if (binding.startsWith('target:') && (node.category != 'sink'
                || binding.substring(7)::int >= targets.size())) { exit 400, '目标槽位与模板目标数量不一致' }
            if (binding.startsWith('governance:') && !binding.matches('governance:[0-7]')) { exit 400, '字段治理绑定槽位无效' }
            if (binding.startsWith('target:')) {
                var targetKind = targets[binding.substring(7)::int]
                if ((targetKind == 'HIVE' && node.manifestKey != 'sink.hive')
                    || (targetKind != 'HIVE' && node.manifestKey != 'sink.jdbc')) {
                    exit 400, '目标组件与声明的数据源类型不匹配'
                }
            }
            nodesById[nodeId] = node
            incoming[nodeId] = 0
            outgoing[nodeId] = []
        }
        if (bindings.source == null) { exit 400, '流程缺少来源绑定' }
        for (index, kind in targets) {
            if (bindings['target:' + index] == null) { exit 400, '流程缺少所需的目标绑定' }
        }
        var edgeIds = {}
        for (edge in flow.edges) {
            if (nodesById[edge.source] == null || nodesById[edge.target] == null || edge.source == edge.target
                || edgeIds[edge.id] != null) { exit 400, '流程连接断开、重复或指向自身' }
            edgeIds[edge.id] = true
            incoming[edge.target] = incoming[edge.target] + 1
            outgoing[edge.source].add(edge.target)
        }
        var ready = []
        for (node in flow.nodes) { if (incoming[node.id] == 0) { ready.add(node.id) } }
        var allowedRoots = [bindings.source.id]
        if (bindings['source:backfill'] != null) { allowedRoots.add(bindings['source:backfill'].id) }
        if (ready.size() != allowedRoots.size() || !ready.containsAll(allowedRoots)) {
            exit 400, '流程仅允许主来源和默认禁用的备用补数来源作为起点'
        }
        var visited = 0
        while (visited < ready.size()) {
            var currentId = ready[visited]
            visited = visited + 1
            for (nextId in outgoing[currentId]) {
                incoming[nextId] = incoming[nextId] - 1
                if (incoming[nextId] == 0) { ready.add(nextId) }
            }
        }
        if (visited != flow.nodes.size()) { exit 400, '接入画布存在环路或不可达组件' }
        for (node in flow.nodes) {
            if (node.category == 'sink' && outgoing[node.id].size() > 0) { exit 400, '目标写入组件须位于分支末端' }
            if (node.category != 'sink' && outgoing[node.id].isEmpty()) { exit 400, '流程存在未连接到目标的组件' }
        }
        normalized.flow = flow
    }
    var nativeSnapshot = spec?.nativeSnapshot
    if (nativeSnapshot != null) {
        if (!(nativeSnapshot.flowContents instanceof Map)) { exit 400, '原始NiFi定义缺少flowContents' }
        normalized.nativeSnapshot = nativeSnapshot
    }
    // 凭据和具体资产绑定不进入可复用模板。脱敏原始属性描述中标为敏感的值。
    var sanitize = null
    sanitize = value => {
        if (value instanceof Map) {
            var descriptors = value.propertyDescriptors
            if (descriptors instanceof Map && value.properties instanceof Map) {
                for (key, descriptor in descriptors) {
                    if (descriptor?.sensitive == true) { value.properties[key] = null }
                }
            }
            for (key in new ArrayList(value.keySet())) {
                var field = key.toString().toLowerCase()
                if (field.matches('.*(password|passwd|secret|token|credential|privatekey|keytab).*')) { value[key] = null }
                else if (value[key] instanceof CharSequence
                    && (value[key].toString().trim().startsWith('{') || value[key].toString().trim().startsWith('['))) {
                    var parsed = null
                    try { parsed = JSONUtil.parse(value[key].toString()) } catch (ignored) { parsed = null }
                    if (parsed != null) { sanitize(parsed); value[key] = JSONUtil.toJsonStr(parsed) }
                } else { sanitize(value[key]) }
            }
        } else if (value instanceof List) { for (item in value) { sanitize(item) } }
    }
    sanitize(normalized)
    definition = JSONUtil.toJsonStr(normalized)
    if (definition.length() > 2000000) { exit 400, '模板定义最多2MB' }
}
var actor = StpUtil.getLoginIdAsString()
return db.transaction(() => {
    // 默认选择使用行业现有配置表的一条确定记录，所有默认切换与版本发布共用此行锁。
    var settingId = 'nifiaccessdefault' + tenantId
    var setting = tableWrite.lockRow('sym_config_t',tenantId,'tid',settingId,['tid','config_value'],{is_del:0})
    if (setting == null) {
        // 新行业首次管理时创建空默认配置，后续切换由同一记录串行化。
        db.table('sym_config_t').insert({tid:settingId,tenant_id:tenantId,config_code:'access_default_template',
            config_name:'接入默认模板',config_value:'',config_group:'NIFI',is_use:1,sort_no:0,is_encrypt:0,
            is_del:0,value_type:'STRING',config_scope:'INTERNAL',cache_ttl_seconds:0,version_no:1})
        setting = {config_value:''}
    }
    var defaultId = (setting.configValue || setting.config_value || setting.CONFIG_VALUE || '').toString()
    var setDefault = id => tableWrite.batchUpdate('sym_config_t',tenantId,['config_value'],['tid'],
        {is_del:0},[{tid:settingId,config_value:id}])
    var current = requested == '' ? null : db.table('nifi_access_template_t').where()
        .eq('tenant_id', tenantId).eq('tid', requested).selectOne()
    if (requested != '' && current == null) { exit 404, '模板版本不存在' }
    var family = current?.familyId
    if (current != null && tableWrite.lockRow('nifi_access_template_t', tenantId, 'tid', family,
        ['tid'], {}) == null) { exit 409, '模板已变化，请刷新' }
    var now = System.currentTimeMillis()
    if (action == 'create') {
        var tid = IdUtil.getSnowflakeNextIdStr()
        var nextVersion = 1
        if (family != null) {
            var revision = tableRead.column('t', 'revision_no', 'NUMBER')
            var query = tableRead.from('nifi_access_template_t', 't', tenantId)
            query.where(tableRead.equal(tableRead.column('t', 'family_id', 'TEXT'), family))
            var latest = query.rows({revision: revision}, revision, 'desc', 1)
            nextVersion = latest[0].revision + 1
        } else { family = tid }
        db.table('nifi_access_template_t').insert({tid: tid, tenant_id: tenantId, family_id: family,
            revision_no: nextVersion, display_name: name, status: 'DRAFT', definition: definition,
            created_by: actor, created_at: now, updated_at: now})
        return {tid: tid, version: nextVersion, status: 'DRAFT'}
    }
    if (current == null) { exit 400, '请选择模板版本' }
    if (body?.expectedUpdatedAt != null && current.updatedAt::long != body.expectedUpdatedAt::long) {
        exit 409, '模板已被其他操作更新，请刷新后重试'
    }
    if (action == 'default') {
        if (current.status != 'PUBLISHED') { exit 409, '只能将已发布模板设为默认' }
        var configured = JSONUtil.parseObj(current.definition)
        validateTargets(configured,configured.targetDatasourceIds,true)
        if (setDefault(requested) != 1) { exit 409, '默认配置已变化，请刷新' }
        return {tid:requested,isDefault:true}
    }
    if (action == 'bind') {
        if (current.status == 'RETIRED') { exit 409, '停用版本须通过新建版本修改' }
        var configured = JSONUtil.parseObj(current.definition)
        configured.targetDatasourceIds = validateTargets(configured,body?.targetDatasourceIds,true)
        var displayName = (body?.name || current.displayName).toString().trim()
        if (displayName == '' || displayName.length() > 96) { exit 400, '模板名称须为1至96个字符' }
        var changed = db.table('nifi_access_template_t').where().eq('tenant_id',tenantId).eq('tid',requested)
            .eq('updated_at',current.updatedAt).update({definition:JSONUtil.toJsonStr(configured),display_name:displayName,updated_at:now})
        if (changed != 1) { exit 409, '模板已变化，请刷新' }
        return {tid:requested,status:current.status}
    }
    if (action == 'retire' && defaultId == requested) { exit 409, '请先将其他模板设为默认，再停用当前默认模板' }
    if (action == 'save') {
        if (current.status != 'DRAFT') { exit 409, '已发布或停用版本须通过新建版本修改' }
        var changed = db.table('nifi_access_template_t').where().eq('tenant_id', tenantId).eq('tid', requested)
            .eq('status','DRAFT').eq('updated_at', current.updatedAt)
            .update({display_name: name, definition: definition, updated_at: now})
        if (changed != 1) { exit 409, '模板已被其他操作更新，请刷新后重试' }
        return {tid: requested, version: current.revisionNo, status: 'DRAFT'}
    }
    if (action == 'publish') {
        var savedSpec = JSONUtil.parseObj(current.definition)
        if (savedSpec.flow == null && savedSpec.engine != 'GENERATED') { exit 409, '请完善完整接入流程后再发布此模板' }
        validateTargets(savedSpec,savedSpec.targetDatasourceIds,true)
    }
    var next = action == 'publish' ? 'PUBLISHED' : 'RETIRED'
    var expected = action == 'publish' ? 'DRAFT' : 'PUBLISHED'
    var changed = db.table('nifi_access_template_t').where().eq('tenant_id', tenantId)
        .eq('tid', requested).eq('status', expected).update({status: next, updated_at: now})
    if (changed != 1) { exit 409, '模板状态已变化，请刷新' }
    if (action == 'publish') {
        if (defaultId != '' && defaultId != requested) {
            var selected = db.table('nifi_access_template_t').where().eq('tenant_id',tenantId).eq('tid',defaultId).selectOne()
            if (selected != null && selected.familyId == family) { setDefault(requested) }
        }
        db.table('nifi_access_template_t').where().eq('tenant_id', tenantId).eq('family_id', family)
            .eq('status', 'PUBLISHED').ne('tid', requested).update({status: 'RETIRED', updated_at: now})
    }
    return {tid: requested, status: next}
})
