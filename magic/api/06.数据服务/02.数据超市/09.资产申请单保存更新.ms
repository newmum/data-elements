{
  "properties": {},
  "id": "597176b6460544c395498e374bf8bfe8",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "09.资产申请单保存更新",
  "createTime": 1782955344136,
  "updateTime": 1787153757200,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyForm/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\r\n  \"orders\": [\r\n    {\r\n      \"draftKey\": \"30253579e54d4061bd95c7af602e17e7\",\r\n      \"propList\": {\r\n        \"applyResource\": [\r\n          \"685ee1e783064ff8a9d7b3d08c5fe89a\"\r\n        ]\r\n      },\r\n      \"type\": \"catalogSubscribe\"\r\n    }\r\n  ]\r\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"b71ef91ddcc940a99b220cc3d7cd9120\"\n    }\n}",
  "description": "保存当前申请人的数据超市草稿。兼容单项 tid、type、propList，支持最多100项 orders。只校验本次新增目录的上架资格，在同一事务同步申请单、属性、目录关系及购物车。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import java.util.List
import java.util.Map
import jsons
import log
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import org.springframework.data.elasticsearch.core.ElasticsearchOperations as esOperations
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.UpdateQuery
import "@post:/sym/user/me" as me
import '@/common/market-catalog-eligibility' as marketCatalogEligibility
import tenantRuntime

// POST /dws/market/applyForm/saveOrUpdate 保存一项或最多 100 项数据超市申请草稿。
// 旧调用返回 {tid}。批量 orders 按输入顺序返回 items，draftKey 用于重试同一新建请求。
// 本次新增的目录一次核验发布资格，原草稿保留的目录和重复保存不重新检查上架状态。

var tenantId = tenantRuntime.id()
// 用户接口从会话选择当前应用，申请参数不能改变身份解析。
var submitted = body
var user = null
try {
    body = {}
    user = me()
} finally {
    body = submitted
}
var actor = user?.id
if (tenantId == null || actor == null || (actor + '').trim() == '') {
    exit 401, '请先登录'
}

var text = value => value == null ? '' : (value + '').trim()
var batch = body?.orders != null
var orders = batch ? body.orders : [body]
if (!(orders instanceof List) || orders.size() == 0 || orders.size() > 100) {
    exit 400, '每次保存需包含 1 至 100 项申请'
}

var submittedIds = []
var catalogIds = []
var prepared = []
var normalizedProperty = name => {
    var suffixTypes = {'_date': '4', '_num': '1', '_file': '5', '_dict': '3', '_range': '6', '_time': '7'}
    for (suffix in suffixTypes.keySet()) {
        if (name.endsWith(suffix)) {
            return {name: name.substring(0, name.length() - suffix.length()), type: suffixTypes[suffix]}
        }
    }
    return {name: name, type: '0'}
}
var reservedProperties = ['tid', 'id', 'type', 'applyname', 'applyuserid', 'applyusername',
    'applyorgid', 'tenantid', 'catalogids', 'flowstatus', 'createdtime', 'updatedtime',
    'createdby', 'updatedby', 'isdel', 'revision']

for (order in orders) {
    if (!(order instanceof Map) || order.type != 'catalogSubscribe'
        || !(order.propList instanceof Map) || order.propList.size() > 100) {
        exit 400, '申请草稿格式不正确'
    }

    var tid = text(order.tid)
    var draftKey = text(order.draftKey)
    if ((tid != '' && !tid.matches('[A-Za-z0-9_-]{1,32}'))
        || (draftKey != '' && !draftKey.matches('[0-9a-f]{32}'))
        || (tid != '' && draftKey != '')) {
        exit 400, '申请草稿编号不正确'
    }
    var suppliedId = tid != '' ? tid : draftKey
    if (suppliedId != '') {
        if (submittedIds.contains(suppliedId)) {
            exit 400, '申请草稿编号重复'
        }
        submittedIds.add(suppliedId)
    }

    var selected = order.propList.applyResource
    if (!(selected instanceof List) || selected.size() == 0 || selected.size() > 100) {
        exit 400, '请为每项申请选择有效的数据目录'
    }
    var selectedIds = []
    for (value in selected) {
        var catalogId = text(value)
        if (!catalogId.matches('[A-Za-z0-9_-]{1,32}') || catalogIds.contains(catalogId)) {
            exit 400, '数据目录编号无效或重复'
        }
        selectedIds.add(catalogId)
        catalogIds.add(catalogId)
    }
    var selectedKey = selectedIds.join(',')
    if (selectedKey.length() > 500 || catalogIds.size() > 100) {
        exit 400, '申请关联的数据目录超出允许范围'
    }

    var props = {}
    for (key in order.propList.keySet()) {
        var name = text(key)
        if (name == '' || name.length() > 100) {
            exit 400, '申请表单字段名称不正确'
        }
        var property = normalizedProperty(name)
        if (reservedProperties.contains(property.name.replace('_', '').toLowerCase())) {
            continue
        }
        var rawValue = order.propList[key]
        var value = rawValue == null ? null : rawValue.toString()
        if (property.name == '' || props.containsKey(property.name) || (value != null && value.length() > 65535)) {
            exit 400, '申请表单字段重复或内容超出限制'
        }
        props[property.name] = {value: value, type: property.type}
    }
    prepared.add({id: suppliedId, update: tid != '', catalogIds: selectedIds, catalogKey: selectedKey, props: props})
}

var now = new Date()
var dryRun = body?.dryRun == true
var scoped = table => db.table(table).where().eq('tenant_id', tenantId).eq('is_del', 0)
var insertRows = (table, rows) => {
    var start = 0
    while (start < rows.size()) {
        var end = Math.min(start + 250, rows.size())
        db.table(table).batchInsert(rows.subList(start, end))
        start = end
    }
}

var output = db.transaction(() => {
    // 目录和草稿在集合外一次批量读取；每项仍按当前租户和申请人核验。
    var catalogs = {}
    var available = scoped('da_catalog_t').in('tid', catalogIds)
        .eq('asset_status', 2).eq('flow_status', 2).select()
    for (catalog in available) {
        catalogs[catalog.tid] = catalog
    }
    if (catalogs.size() != catalogIds.size()) {
        exit 404, '部分数据目录已下架、未完成登记或不属于当前租户'
    }

    var forms = {}
    if (submittedIds.size() > 0) {
        var current = scoped('data_apply_form_t').in('tid', submittedIds).select()
        for (form in current) {
            forms[form.tid] = form
        }
    }

    var updateIds = []
    var newCatalogs = []
    for (draft in prepared) {
        var form = forms[draft.id]
        if (draft.update && form == null) {
            exit 404, '部分申请草稿不存在或无权访问'
        }
        if (form != null) {
            var owner = text(form.applyUserId) == '' ? text(form.createdBy) : text(form.applyUserId)
            if (owner != text(actor) || form.type != 'catalogSubscribe') {
                exit 404, '部分申请草稿不存在或无权访问'
            }
            if (![0, 4, 5].contains(form.flowStatus::int(-1))) {
                exit 409, '部分申请已提交或处于审批中，请刷新后重试'
            }
            if (!draft.update && form.catalogIds != draft.catalogKey) {
                exit 409, '草稿重试编号与原数据目录不一致'
            }
            updateIds.add(form.tid)
        }

        // 历史草稿可能只有逗号分隔的目录编号，保留原有目录时沿用关系库校验。
        var previousCatalogIds = []
        for (storedId in text(form?.catalogIds).split(',')) {
            previousCatalogIds.add(storedId.trim())
        }
        for (catalogId in draft.catalogIds) {
            if (!previousCatalogIds.contains(catalogId)) {
                newCatalogs.add({tid: catalogId, targetTableId: catalogs[catalogId].targetTableId})
            }
        }
    }

    // 所有草稿通过归属检查后再合并核验，任一新增目录不可申请时整批不写入。
    if (newCatalogs.size() > 0) {
        var eligibility = marketCatalogEligibility(newCatalogs)
        var eligibleIds = eligibility?.eligibleIds
        if (!(eligibleIds instanceof List)) {
            exit 502, '目录状态暂时无法确认，请稍后重试'
        }
        for (catalog in newCatalogs) {
            if (!eligibleIds.contains(catalog.tid)) {
                exit 404, '部分新增目录已下架或不可申请，请刷新后重新选择'
            }
        }
    }

    if (dryRun) {
        return {dryRun: true, count: prepared.size()}
    }

    var propsByForm = {}
    var relationsByForm = {}
    if (updateIds.size() > 0) {
        var oldProps = scoped('da_prop_t').eq('data_type', 'apply_form').in('parent_id', updateIds).select()
        for (row in oldProps) {
            var byName = propsByForm[row.parentId]
            if (byName == null) {
                byName = {}
                propsByForm[row.parentId] = byName
            }
            byName[row.propName] = row
        }
        var oldRelations = scoped('da_catalog_apply_form_rela').in('apply_form_id', updateIds)
            .orderBy('sort_no').select()
        for (row in oldRelations) {
            if (relationsByForm[row.applyFormId] == null) {
                relationsByForm[row.applyFormId] = []
            }
            relationsByForm[row.applyFormId].add(row.catalogId)
        }
    }

    var inserts = []
    var relationInserts = []
    var changedRelationForms = []
    var propInserts = []
    var cartCatalogs = []
    var documents = []
    var receipts = []

    for (draft in prepared) {
        var form = forms[draft.id]
        var tid = draft.id == '' ? IdUtil.getSnowflakeNextIdStr() : draft.id
        var catalogName = catalogs[draft.catalogIds[0]].catalogName
        if (form == null) {
            inserts.add({tid: tid, tenant_id: tenantId, is_del: 0, type: 'catalogSubscribe',
                apply_user_id: actor, apply_user_name: user.userName || actor, apply_org_id: user.orgId,
                apply_name: catalogName, catalog_ids: draft.catalogKey, flow_status: 0,
                created_by: actor, created_time: now, updated_by: actor, updated_time: now})
            cartCatalogs.addAll(draft.catalogIds)
        } else {
            // 状态和原目录参与更新条件，避免提交后修改或并发重加已移除的目录。
            var update = scoped('data_apply_form_t').eq('tid', tid)
                .eq('type', 'catalogSubscribe').eq('flow_status', form.flowStatus)
            if (form.catalogIds == null) {
                update.isNull('catalog_ids')
            } else {
                update.eq('catalog_ids', form.catalogIds)
            }
            if (text(form.applyUserId) == '') {
                update.eq('created_by', actor)
                    .and(owner => owner.isNull('apply_user_id').or().eq('apply_user_id', ''))
            } else {
                update.eq('apply_user_id', actor)
            }
            if (update.update({apply_name: catalogName, catalog_ids: draft.catalogKey,
                updated_by: actor, updated_time: now}) != 1) {
                exit 409, '部分申请草稿已发生变化，请刷新后重试'
            }
        }

        var storedRelations = relationsByForm[tid] || []
        if (storedRelations.join(',') != draft.catalogKey) {
            if (storedRelations.size() > 0) {
                changedRelationForms.add(tid)
            }
            var sortNo = 0
            for (catalogId in draft.catalogIds) {
                relationInserts.add({tid: IdUtil.getSnowflakeNextIdStr(), tenant_id: tenantId,
                    apply_form_id: tid, catalog_id: catalogId, catalog_org_id: catalogs[catalogId].orgId,
                    sort_no: sortNo, created_by: actor, created_time: now,
                    updated_by: actor, updated_time: now, is_del: 0})
                sortNo = sortNo + 1
            }
        }

        var oldProps = propsByForm[tid] || {}
        var document = {}
        for (old in oldProps.values()) {
            document[old.propName] = old.propValue
        }
        for (name in draft.props.keySet()) {
            var property = draft.props[name]
            var old = oldProps[name]
            document[name] = property.value
            if (old == null) {
                propInserts.add({tid: IdUtil.getSnowflakeNextIdStr(), tenant_id: tenantId, parent_id: tid,
                    prop_name: name, prop_value: property.value, prop_type: property.type,
                    data_type: 'apply_form', is_del: 0, created_by: actor, created_time: now,
                    updated_by: actor, updated_time: now})
            } else if (old.propValue != property.value || old.propType != property.type) {
                scoped('da_prop_t').eq('tid', old.tid).eq('parent_id', tid).eq('data_type', 'apply_form')
                    .update({prop_value: property.value, prop_type: property.type, updated_by: actor, updated_time: now}, true)
            }
        }
        document.putAll({tid: tid, id: tid, tenantId: tenantId, type: 'catalogSubscribe',
            applyUserId: actor, applyUserName: form?.applyUserName || user.userName || actor,
            applyOrgId: form?.applyOrgId || user.orgId, applyName: catalogName, catalogIds: draft.catalogKey,
            flowStatus: form == null ? 0 : form.flowStatus,
            createdTime: form?.createdTime || now, updatedTime: now})
        documents.add(document)
        receipts.add({tid: tid, catalogIds: draft.catalogIds})
    }

    insertRows('data_apply_form_t', inserts)
    if (changedRelationForms.size() > 0) {
        scoped('da_catalog_apply_form_rela').in('apply_form_id', changedRelationForms)
            .update({is_del: 1, updated_by: actor, updated_time: now})
    }
    insertRows('da_catalog_apply_form_rela', relationInserts)
    insertRows('da_prop_t', propInserts)

    // 购物车按部门共享；仅移出本租户、当前部门本次新建的目录。
    if (cartCatalogs.size() > 0 && text(user.orgId) != '') {
        scoped('data_market_shopping_cart_t').eq('apply_org_id', user.orgId).in('catalog_id', cartCatalogs)
            .update({is_del: 1, updated_by: actor, updated_time: now})
    }
    return {receipts: receipts, documents: documents}
})

if (dryRun) {
    return output
}

// 主表已经保存；投影延迟到达时也不能覆盖后续提交已写入的生效状态。
try {
    var physicalIndex = esCommonService.ensureTenantIndexReady('dataassets_apply_form')
    var updates = output.documents.map(item => {
        var full = jsons.toJSONObject(jsons.toJSONStr(item))
        var partial = {}
        partial.putAll(full)
        partial.remove('flowStatus')
        partial.remove('updatedTime')
        return UpdateQuery.builder(item.tid).withDocument(Document.from(partial))
            .withUpsert(Document.from(full)).build()
    })
    esOperations.bulkUpdate(updates, IndexCoordinates.of(physicalIndex))
} catch (error) {
    log.warn('保存申请草稿后同步索引失败，本次草稿数: {}', output.receipts.size())
}
return batch ? {items: output.receipts} : {tid: output.receipts[0].tid}
