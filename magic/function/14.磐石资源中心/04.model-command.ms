{
  "properties" : { },
  "id" : "ad740688aec155419e89ac237559c727",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "04.model-command",
  "createTime" : null,
  "updateTime" : 1791068444380,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/model-command",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 05.逻辑建模、冻结版本和数据项候选。模型草稿与标准候选在当前租户事务内写入。
import tenantRuntime
import jsons
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/model-read' as readModels
import '@/resource-center/model-validate' as validateModel
import '@/resource-center/technical-read' as technicalRead
import '@/resource-center/planning-read' as readPlanning
var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var now = new Date()
var text = v => v == null ? '' : (v + '').trim()
var clone = v => JSONUtil.parse(jsons.toJSONStr(v))
var required = (v, label, max) => { var s = text(v); if (s == '' || s.length() > max) { exit 400,label + '不能为空或超过长度限制' }; return s }
var key = v => { var s = text(v); if (!s.matches('[A-Za-z_][A-Za-z0-9_]{0,62}')) { exit 400,'技术代码格式无效' }; return s }
var find = (rows,id) => { for (r in rows) { if (r.id == id) { return r } }; exit 404,'对象不存在或不属于当前租户' }
var payload = input.body == null ? {} : input.body
var kind = text(input.kind); var id = text(input.id); var action = text(input.action); var method = text(input.method)
var state = readModels({entities:[]})
var content = m => ({entities:m.entities,relationships:m.relationships,nodes:m.nodes,outputEntityId:m.outputEntityId,fusion:m.fusion})
var check = m => { if (text(input.version) != text(m.version)) { exit 409,'模型已更新，请刷新后重试' } }
var audit = (verb,m,detail) => db.table('res_resource_operation_log').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,
    actor_id:actorId,action_name:verb,object_id:m.id,object_name:m.name,detail_text:text(detail),created_time:now})
var designSummary = m => {
    var fields = 0; var sources = 0
    for (e in m.entities) { fields = fields + e.fields.size(); if (e.role == 'SOURCE') { sources = sources + 1 } }
    var mappings = m.fusion == null || m.fusion.mappings == null ? 0 : m.fusion.mappings.size()
    return '来源表 ' + sources + ' 张，设计实体 ' + m.entities.size() + ' 个，字段 ' + fields + ' 个，关系 ' + m.relationships.size() + ' 条，字段映射 ' + mappings + ' 条'
}
var logical = f => {
    var t = text(f.typeFamily).toLowerCase()
    if (t == 'integer') { return 'Int64' }; if (t == 'decimal' || t == 'float') { return 'Decimal' }
    if (t == 'date') { return 'Date' }; if (t == 'timestamp') { return 'Timestamp' }; if (t == 'boolean') { return 'Boolean' }
    if (t == 'binary') { return 'Binary' }; return 'String'
}
var reverse = (ids,sourceId,occupiedNames) => {
    if (ids == null || ids.size() == 0 || ids.size() > 300) { exit 400,'请选择1至300张来源表' }
    var result = {entities:[],relationships:[],nodes:[]}
    var technical = technicalRead({entityIds:ids})
    if (technical.entities.size() != ids.size()) { exit 404,'部分来源表不存在或不属于当前租户' }
    var usedNames = occupiedNames == null ? [] : occupiedNames
    for (source in technical.entities) {
        if (text(sourceId) != '' && text(source.sourceId) != text(sourceId)) { exit 409,'所选技术表不属于当前来源数据库' }
        var e = clone(source); var fmap = {}; var originMap = {}
        e.id = IdUtil.fastSimpleUUID(); e.sourceId = 'panshi-design'; e.grain = ''; e.role = 'SOURCE'
        var originalName = text(source.name)
        var safeName = originalName.replaceAll('[^A-Za-z0-9_]','_')
        if (!safeName.matches('[A-Za-z_].*')) { safeName = 'SRC_' + safeName }
        if (safeName.length() > 63) { safeName = safeName.substring(0,63) }
        var alias = safeName; var suffix = 2
        while (usedNames.contains(alias.toLowerCase())) { var ending = '_S' + suffix; alias = safeName.substring(0,Math.min(safeName.length(),63-ending.length())) + ending; suffix = suffix + 1 }
        e.name = alias; usedNames.add(alias.toLowerCase())
        for (f in e.fields) { var oldId = f.id; f.id = IdUtil.fastSimpleUUID(); fmap[oldId] = f.id; originMap[f.id] = oldId
            f.logicalType = logical(f); if (f.logicalType == 'String' && (f.length == null || f.length < 1)) { f.length = 128 }
            if (f.logicalType == 'String' && f.length > 4000) { f.logicalType = 'Text' }
            f.businessName = text(f.comment); f.remove('standard') }
        for (k in e.keys == null ? [] : e.keys) { k.id = IdUtil.fastSimpleUUID(); var ids2 = []; for (fid in k.fieldIds) { ids2.add(fmap[fid]) }; k.fieldIds = ids2 }
        e.origin = {entityId:source.id,sourceId:source.sourceId,tableName:originalName,snapshotId:technical.manifest[source.sourceId],fieldMap:originMap}
        result.entities.add(e); result.nodes.add({entityId:e.id,position:{x:120 + (result.entities.size() % 4) * 340,y:120 + (result.entities.size() / 4)::int * 500}})
    }
    return result
}
var outputFrom = (m, first) => {
    var outputName = m.code; var suffix = 1; var occupied = true
    while (occupied) {
        occupied = false
        for (source in m.entities) { if (text(source.name).toLowerCase() == outputName.toLowerCase()) { occupied = true } }
        if (occupied) { var ending = suffix == 1 ? '_D' : '_D' + suffix; outputName = m.code.substring(0,Math.min(m.code.length(),63-ending.length())) + ending; suffix = suffix + 1 }
    }
    var e = {id:IdUtil.fastSimpleUUID(),sourceId:'panshi-design',role:'OUTPUT',name:outputName,displayName:m.name,
        kind:'table',fields:[],keys:[],grain:'',comment:text(m.description)}
    var mappings = []
    if (first != null) {
        var fieldIds = {}
        for (f in first.fields) {
            var field = clone(f); var previous = field.id; field.id = IdUtil.fastSimpleUUID(); field.path = field.name
            field.remove('origin'); e.fields.add(field); fieldIds[previous] = field.id
            mappings.add({id:IdUtil.fastSimpleUUID(),targetFieldId:field.id,sourceEntityId:first.id,
                sourceFieldId:previous,transform:'DIRECT'})
        }
        for (k in first.keys == null ? [] : first.keys) {
            var members = []; for (fid in k.fieldIds) { if (fieldIds[fid] != null) { members.add(fieldIds[fid]) } }
            if (members.size() > 0) { e.keys.add({id:IdUtil.fastSimpleUUID(),kind:k.kind,fieldIds:members,enforced:'unknown',scope:'full'}) }
        }
    }
    m.entities.add(e); m.nodes.add({entityId:e.id,position:{x:Math.max(700,m.entities.size()*350),y:120}})
    m.outputEntityId = e.id; m.fusion = {baseEntityId:first == null ? null : first.id,joins:[],mappings:mappings}
    return e
}
var save = (m, fresh) => {
    var designJson = jsons.toJSONStr(content(m))
    if (fresh) {
        db.table('res_logical_model').primary('tid').insert({tid:m.id,tenant_id:tenantId,model_code:m.code,model_name:m.name,model_type:m.method,
            domain_name:m.domain,database_id:m.databaseId,owner_name:m.owner,model_status:m.state,model_description:m.description,
            design_json:designJson,revision_no:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
    } else {
        var changed = db.update('UPDATE res_logical_model SET model_name=#{name}, model_description=#{description},design_json=#{designJson},model_status=#{state}, revision_no=revision_no+1,updated_by=#{actorId},updated_time=#{now} WHERE tid=#{id} AND tenant_id=#{tenantId} AND is_del=0 AND revision_no=#{version}',
            {name:m.name,description:m.description,designJson:designJson,state:m.state,actorId:actorId,now:now,id:m.id,tenantId:tenantId,version:m.version})
        if (changed != 1) { exit 409,'模型已更新，请刷新后重试' }; m.version = m.version + 1
    }
    // 历史单实体字段表继续作为查询投影；entity_id将同名字段隔离到各自实体。
    var modelId = m.id
    db.update('DELETE FROM res_logical_model_field WHERE tenant_id=#{tenantId} AND model_id=#{modelId}')
    for (e in m.entities) { for (f in e.fields) {
        var pk = false; for (k in e.keys == null ? [] : e.keys) { if (k.kind == 'primary' && k.fieldIds.contains(f.id)) { pk = true } }
        db.table('res_logical_model_field').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,model_id:m.id,entity_id:e.id,
            design_field_id:f.id,field_code:f.name,field_name:text(f.businessName),data_type:f.logicalType,data_length:f.length,
            data_precision:f.precision,numeric_scale:f.scale,nullable_flag:f.nullable == 'false' ? 0 : 1,primary_key_flag:pk ? 1 : 0,
            standard_id:f.standard == null ? null : f.standard.id,standard_revision:f.standard == null ? null : f.standard.revisionId,
            field_description:text(f.comment),sort_no:f.ordinal,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
    } }
    m.updatedAt = now; return m
}
if (kind == 'itemsets') {
    var set = find(state.itemSets,id); check(set)
    if (action != 'submit' || set.state != 'DRAFT') { exit 409,'数据项集已提交或操作不支持' }
    var m = find(state.models,set.modelId)
    return db.transaction(() => {
        var items = clone(set.items); var created = 0
        for (item in items) {
            if (item.standardId != null || item.candidateId != null) { continue }
            var e = find(m.entities,item.entityId); var f = find(e.fields,item.fieldId); var candidateId = IdUtil.fastSimpleUUID()
            var candidateCode = 'PS_' + candidateId.substring(0,20).toUpperCase()
            var candidateName = text(f.businessName) == '' ? text(f.name) : text(f.businessName)
            if (candidateName.length() > 50) { candidateName = candidateName.substring(0,50) }
            var definition = text(f.comment); if (definition.length() > 255) { definition = definition.substring(0,255) }
            db.table('da_metadata_t').primary('tid').insert({tid:candidateId,tenant_id:tenantId,meta_name:candidateName,meta_code:candidateCode,
                standard_encode:candidateCode,biz_def:definition,field_type:f.logicalType,field_length:f.length,numeric_precision:f.precision,
                numeric_scale:f.scale,is_nullable:f.nullable == 'false' ? 0 : 1,publish_status:0,version_no:'1',created_by:actorId,
                created_time:now,updated_by:actorId,updated_time:now,is_del:0})
            item.candidateId = candidateId
            created = created + 1
        }
        var changed = db.update('UPDATE res_model_itemset SET items_json=#{json},itemset_status=\'SUBMITTED\',revision_no=revision_no+1,updated_time=#{now},updated_by=#{actorId} WHERE tid=#{id} AND tenant_id=#{tenantId} AND revision_no=#{version} AND is_del=0',
            {json:jsons.toJSONStr(items),now:now,actorId:actorId,id:id,tenantId:tenantId,version:set.version})
        if (changed != 1) { exit 409,'数据项集已经更新' }; audit('提交标准候选',set,'提交 ' + items.size() + ' 个数据项，新建 ' + created + ' 个未发布标准候选草稿')
        set.items = items; set.state = 'SUBMITTED'; set.version = set.version + 1; return set
    })
}
if (kind != 'models') { exit 400,'不支持的模型对象类型' }
if (method == 'POST' && id == '') {
    var code = key(payload.code); for (old in state.models) { if (text(old.code).toLowerCase() == code.toLowerCase()) { exit 409,'模型代码已存在' } }
    var planning = readPlanning({})
    var database = find(planning.databases,required(payload.databaseId,'目标分库',64))
    if (database.state != 'ACTIVE') { exit 409,'目标分库已归档' }
    var layer = find(planning.layers,text(database.layerId))
    if (layer.state != 'ACTIVE') { exit 409,'目标分层已归档' }
    if (text(payload.layerId) != '' && text(payload.layerId) != text(layer.id)) { exit 400,'目标分库不属于所选分层' }
    var domain = text(database.domain) == '' ? text(database.name) : text(database.domain)
    if (domain.length() > 128) { domain = domain.substring(0,128) }
    var m = {id:IdUtil.fastSimpleUUID(),version:1,name:required(payload.name,'模型名称',255),code:code,domain:domain,
        description:text(payload.description),databaseId:database.id,owner:text(payload.owner) == '' ? actorId : text(payload.owner),
        method:text(payload.method) == '' ? 'MANUAL' : payload.method,state:'ACTIVE',entities:[],relationships:[],nodes:[],frozen:[],createdAt:now,updatedAt:now}
    if (!['MANUAL','STANDARD','REVERSE'].contains(m.method)) { exit 400,'不支持的建模方式' }
    if (m.method == 'REVERSE') { var r = reverse(payload.entityIds,payload.sourceId,[]); if(r.entities.size()==0){exit 400,'请选择至少一张来源技术表'}; m.entities = r.entities; m.nodes = r.nodes; outputFrom(m,m.entities[0]) }
    if (m.method == 'STANDARD') {
        if (payload.standardIds == null || payload.standardIds.size() == 0) { exit 400,'请选择已发布标准' }
        var standards = technicalRead({entityIds:[]}).standards
        var e = outputFrom(m,null)
        for (sid in payload.standardIds) {
            var st = find(standards,sid); if (st.status != 'PUBLISHED' || st.publishedRevisionId == null) { exit 409,'仅可引用已发布标准' }
            var name = text(st.code).matches('[A-Za-z_][A-Za-z0-9_]{0,62}') ? st.code : 'field_' + (e.fields.size() + 1)
            var f = {id:IdUtil.fastSimpleUUID(),name:name,path:name,ordinal:e.fields.size(),nativeType:st.type,typeFamily:st.typeFamily == null ? 'string' : st.typeFamily,
                nullable:'true',businessName:st.name,comment:st.definition,length:st.length,precision:st.precision,scale:st.scale,standard:{id:st.id,revisionId:st.publishedRevisionId}}
            f.logicalType = logical(f)
            if (f.logicalType == 'String' && (f.length == null || f.length < 1)) { f.length = 128 }
            if (f.logicalType == 'String' && f.length > 4000) { f.logicalType = 'Text' }
            e.fields.add(f)
        }
    }
    if (m.method == 'MANUAL') { outputFrom(m,null) }
    var methodName = m.method == 'REVERSE' ? '逆向解析' : m.method == 'STANDARD' ? '依标建模' : '手工建模'
    return db.transaction(() => { save(m,true); audit('创建逻辑模型',m,'创建方式：' + methodName + '；目标分层：' + layer.name + '；目标分库：' + database.name + '；' + designSummary(m)); return m })
}
var m = find(state.models,id)
if (method == 'GET' && action == '') { return m }
if (action == 'validate') { return validateModel(clone(m)) }
check(m)
if (m.state != 'ACTIVE' && action != 'copy') { exit 409,'模型已归档' }
var logDetail = ''
if (method == 'PUT' && action == '') {
    var previousName = m.name; var previousDescription = m.description; var previousDesign = jsons.toJSONStr(content(m))
    if (payload.content != null) {
        var c = payload.content
        if (c.entities == null || c.entities.size() > 300 || c.relationships == null || c.nodes == null || jsons.toJSONStr(c).length() > 5000000) { exit 400,'无效或过大的模型内容' }
        var ids = []
        for (e in c.entities) { if (text(e.id) == '' || ids.contains(e.id) || e.fields == null || e.fields.size() > 1000) { exit 400,'实体标识重复或字段不合法' }; ids.add(e.id)
            var original = null; for (old in m.entities) { if (old.id == e.id) { original = old } }
            if (e.reference != null && (original == null || original.reference == null)) { exit 400,'请通过引用入口添加参照实体' }
            if (original == null && m.outputEntityId != null) { exit 409,'融合模型请通过逆向解析添加来源表，目标表 D 只能有一张' }
            if (original != null && original.reference != null && jsons.toJSONStr(original) != jsons.toJSONStr(e)) { exit 409,'参照实体只读' }
            if (original != null && original.role == 'SOURCE' && jsons.toJSONStr(original) != jsons.toJSONStr(e)) { exit 409,'来源技术快照只读' }
        }
        m.entities = c.entities; m.relationships = c.relationships; m.nodes = c.nodes
        if (m.outputEntityId != null || c.outputEntityId != null) {
            if (m.outputEntityId != null && text(c.outputEntityId) != text(m.outputEntityId)) { exit 409,'目标输出表不能在保存时改写' }
            m.outputEntityId = c.outputEntityId; m.fusion = c.fusion
        }
    }
    if (payload.name != null) { m.name = required(payload.name,'模型名称',255) }; if (payload.description != null) { m.description = text(payload.description) }
    var changes = []
    if (previousName != m.name) { changes.add('模型名称由“' + previousName + '”改为“' + m.name + '”') }
    if (previousDescription != m.description) { changes.add('业务说明已修改') }
    if (previousDesign != jsons.toJSONStr(content(m))) { changes.add('画布实体、字段、关系或映射已修改') }
    logDetail = (changes.size() == 0 ? '未检测到设计内容变化' : changes.join('；')) + '；' + designSummary(m)
} else if (action == 'freeze') {
    var validation = validateModel(clone(m)); if (!validation.valid) { exit 422,validation.errors.join('；') }
    var standards = technicalRead({entityIds:[]}).standards
    for (e in m.entities) { for (f in e.fields) { if (f.standard != null) {
        var st = find(standards,f.standard.id)
        if (st.status != 'PUBLISHED' || st.publishedRevisionId != f.standard.revisionId) { exit 409,'引用标准已变化，请重新核对：' + f.name }
    } } }
    return db.transaction(() => {
        save(m,false)
        var rev = content(clone(m)); rev.id = IdUtil.fastSimpleUUID(); rev.number = m.frozen.size() + 1
        rev.note = required(payload.note,'版本说明',1000); rev.createdAt = now; rev.signature = DigestUtil.sha256Hex(jsons.toJSONStr(content(m)))
        db.table('res_logical_model_revision').primary('tid').insert({tid:rev.id,tenant_id:tenantId,model_id:id,revision_number:rev.number,
            content_json:jsons.toJSONStr(content(m)),content_hash:rev.signature,note:rev.note,created_by:actorId,created_time:now})
        m.frozen.add(rev); audit('冻结设计版本',m,'冻结为 v' + rev.number + '；版本说明：' + rev.note + '；' + designSummary(m)); return rev
    })
} else if (action == 'reverse') {
    var occupied = [];for(existing in m.entities){occupied.add(text(existing.name).toLowerCase())}
    var r = reverse(payload.entityIds,payload.sourceId,occupied); for (e in r.entities) { for (existing in m.entities) { if (existing.origin != null && text(existing.origin.entityId) == text(e.origin.entityId)) { exit 409,'该技术表已加入模型：' + text(e.origin.tableName) } }; m.entities.add(e) }
    m.nodes.addAll(r.nodes)
    if (m.outputEntityId == null && r.entities.size() > 0) { outputFrom(m,r.entities[0]) }
    logDetail = '导入来源技术表 ' + r.entities.size() + ' 张；' + designSummary(m)
} else if (action == 'bind') {
    var e = find(m.entities,text(payload.entityId)); if (e.reference != null || e.role == 'SOURCE') { exit 409,'来源或参照实体只读' }
    var f = find(e.fields,text(payload.fieldId)); var st = find(technicalRead({entityIds:[]}).standards,text(payload.standardId))
    if (st.status != 'PUBLISHED') { exit 409,'仅允许关联已发布标准' }
    f.standard = {id:st.id,revisionId:st.publishedRevisionId}
    if (payload.apply == true) { f.businessName = st.name; f.nativeType = st.type; f.typeFamily = st.typeFamily == null ? 'string' : st.typeFamily
        f.length = st.length; f.precision = st.precision; f.scale = st.scale; f.logicalType = logical(f) }
    logDetail = '设计字段 ' + e.name + '.' + f.name + ' 绑定标准 ' + st.name + '（' + st.code + '）' + (payload.apply == true ? '，并应用标准字段定义' : '，仅建立引用')
} else if (action == 'copy' || action == 'reference') {
    var source = action == 'copy' ? m : find(state.models,text(payload.modelId))
    var sourceName = source.name
    if (action == 'reference' && source.id == m.id) { exit 409,'模型不能引用自身' }
    var copied = action == 'copy' ? content(clone(source)) : clone(find(source.frozen,text(payload.revisionId)))
    var map = {}
    for (e in copied.entities) { map[e.id] = IdUtil.fastSimpleUUID(); for (f in e.fields) { map[f.id] = IdUtil.fastSimpleUUID() } }
    for (e in copied.entities) {
        if (action == 'reference' && e.reference != null && e.reference.modelId == m.id) { exit 409,'不允许循环参照' }
        e.id = map[e.id]; for (f in e.fields) { f.id = map[f.id] }
        for (k in e.keys == null ? [] : e.keys) { k.id = IdUtil.fastSimpleUUID(); var fields = []; for (fid in k.fieldIds) { fields.add(map[fid]) }; k.fieldIds = fields }
        if (action == 'reference') { e.reference = {modelId:source.id,revisionId:payload.revisionId} } else { e.remove('reference') }
    }
    for (r in copied.relationships) { r.id = IdUtil.fastSimpleUUID(); r.sourceEntityId = map[r.sourceEntityId]; r.targetEntityId = map[r.targetEntityId]
        for (p in r.mappings) { p.sourceFieldId = map[p.sourceFieldId]; p.targetFieldId = map[p.targetFieldId] } }
    for (n in copied.nodes) { n.entityId = map[n.entityId] }
    if (copied.outputEntityId != null) {
        copied.outputEntityId = map[copied.outputEntityId]
        if (copied.fusion != null) {
            copied.fusion.baseEntityId = map[copied.fusion.baseEntityId]
            for (j in copied.fusion.joins == null ? [] : copied.fusion.joins) {
                j.id = IdUtil.fastSimpleUUID(); j.leftEntityId = map[j.leftEntityId]; j.rightEntityId = map[j.rightEntityId]
                j.orderFieldId = j.orderFieldId == null ? null : map[j.orderFieldId]
                for (p in j.pairs) { p.leftFieldId = map[p.leftFieldId]; p.rightFieldId = map[p.rightFieldId] }
            }
            for (mapping in copied.fusion.mappings == null ? [] : copied.fusion.mappings) {
                mapping.id = IdUtil.fastSimpleUUID(); mapping.targetFieldId = map[mapping.targetFieldId]
                mapping.sourceEntityId = map[mapping.sourceEntityId]; mapping.sourceFieldId = map[mapping.sourceFieldId]
            }
        }
    }
    if (action == 'copy') {
        m = clone(m); m.id = IdUtil.fastSimpleUUID(); m.name = required(payload.name,'副本名称',255); m.code = key(payload.code)
        for (existing in state.models) { if (text(existing.code).toLowerCase() == text(m.code).toLowerCase()) { exit 409,'副本代码已存在' } }
        m.version = 1; m.method = 'COPY'; m.state = 'ACTIVE'; m.frozen = []; m.createdAt = now
        m.entities = copied.entities; m.relationships = copied.relationships; m.nodes = copied.nodes
        m.outputEntityId = copied.outputEntityId; m.fusion = copied.fusion
        return db.transaction(() => { save(m,true); audit('复制逻辑模型',m,'复制自模型 ' + sourceName + '；' + designSummary(m)); return m })
    }
    for (e in copied.entities) { for (old in m.entities) { if (text(old.name).toLowerCase() == text(e.name).toLowerCase()) { exit 409,'引用实体名称已存在：' + e.name } } }
    m.entities.addAll(copied.entities); m.relationships.addAll(copied.relationships); m.nodes.addAll(copied.nodes)
    logDetail = '引用模型 ' + sourceName + ' 的冻结版本，共加入 ' + copied.entities.size() + ' 个只读实体；' + designSummary(m)
} else if (action == 'archive') {
    for (p in state.materializations) { if (p.modelId == id && ['READY','QUEUED','RUNNING','CANCELLING'].contains(p.state)) { exit 409,'尚有未完成物化方案' } }; m.state = 'ARCHIVED'
    logDetail = '模型已归档；历史版本和操作日志保留，后续不再参与设计与标准化'
} else if (action == 'items') {
    var items = []; for (e in m.entities) { if (e.reference != null || (m.outputEntityId != null && e.id != m.outputEntityId)) { continue }; for (f in e.fields) { items.add({entityId:e.id,fieldId:f.id,name:text(f.businessName) == '' ? f.name : f.businessName,standardId:f.standard == null ? null : f.standard.id}) } }
    if (items.size() == 0) { exit 400,'模型没有可生成数据项集的字段' }
    var bound = 0; for (item in items) { if (item.standardId != null) { bound = bound + 1 } }
    var setId = IdUtil.fastSimpleUUID(); var name = m.name + '数据项集'
    db.table('res_model_itemset').primary('tid').insert({tid:setId,tenant_id:tenantId,model_id:id,itemset_name:name,items_json:jsons.toJSONStr(items),itemset_status:'DRAFT',revision_no:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
    audit('生成模型数据项集',{id:setId,name:name},'来源模型：' + m.name + '；生成 ' + items.size() + ' 个数据项，其中已绑定标准 ' + bound + ' 个'); return {id:setId,name:name,modelId:id,items:items,state:'DRAFT',version:1,createdAt:now,updatedAt:now}
} else { exit 400,'不支持的模型操作' }
return db.transaction(() => { save(m,false); audit('维护逻辑模型：' + (action == '' ? '保存' : action),m,logDetail + '；设计修订 v' + m.version); return m })
