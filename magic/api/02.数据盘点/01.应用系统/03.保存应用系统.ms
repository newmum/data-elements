{
  "properties" : { },
  "id" : "datapd_application_save_01",
  "script" : null,
  "groupId" : "7868bf218e904ebc999a94f041d91c36",
  "name" : "03.保存应用系统",
  "createTime" : null,
  "updateTime" : 1791112153775,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/save",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"tid\": \"2e83a7abf33140c7a3df27d0c79e98c1\",\n    \"propList\": {\n    }\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"tid\":\"\"}",
  "description" : "保存当前租户的业务系统主记录与动态属性；先校验业务系统归属、一次预取已有属性，再逐项保存变更，并同步刷新检索投影；返回业务系统 ID。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：03.保存应用系统
// 路由：POST /dst/application/save
// 业务说明：业务系统和扩展属性只写当前登录租户；一次预取已有属性，避免逐属性查库。
import '@post:/dst/maintenance/refresh' as refreshAssetIndex

import cn.hutool.core.util.IdUtil
import jsons
import java.util.List
import java.util.Map
import tenantRuntime

var text = (value) => value == null ? null : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var now = new Date()
var tid = blank(body?.tid) ? IdUtil.getSnowflakeNextIdStr() : body.tid
var props = body?.propList == null ? {} : body.propList
if (!(props instanceof Map) || blank(props.appName)) {
    exit 400, '业务系统名称不能为空'
}
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再保存应用系统'
}
var valueText = (value) => value == null ? null : (value instanceof Map || value instanceof List ? jsons.toJSONStr(value) : text(value))
var propType = (key) => key.endsWith('_date') ? '4' : (key.endsWith('_num') ? '1' : '0')
var propName = (key) => key.endsWith('_date') || key.endsWith('_num') ? key.substring(0, key.length() - 5) : key

var main = {
    tid: tid,
    app_name: props.appName,
    app_desc: props.assetDesc,
    app_url: props.provinceUrl,
    asset_class: props.applicationClass,
    org_id: props.orgId,
    is_use: 1,
    is_del: 0,
    tenant_id: tenantId,
    asset_status: 2,
    flow_status: 2,
    flow_order_id: null,
    updated_time: now,
    reg_time: now
}
var old = db.table('sym_application_t').where().eq('tid', tid).eq('tenant_id', tenantId).selectOne()
if (!blank(body?.tid) && (old == null || old.isDel == 1 || old.is_del == 1)) {
    exit 404, '业务系统不存在或不属于当前租户'
}
if (old == null) {
    main.created_time = now
    db.table('sym_application_t').primary('tid').insert(main)
} else {
    db.table('sym_application_t').primary('tid').update(main, true)
}

var physicalProp = (key) => ['tid','id','assetType','tenantId','appName','appDesc','assetDesc','appUrl','provinceUrl','applicationClass','assetClass','orgId','dbTotal','clientId','clientSecret','publicKey','privateKey','createdTime','updatedTime','regTime','assetStatus','flowStatus','flowOrderId'].contains(key)

// 属性增改仍逐条写入，但已有属性只读一次；保留同名历史记录的首条匹配语义。
var existingProps = {}
for (existingRow in db.table('da_prop_t').where().eq('parent_id', tid)
    .eq('tenant_id', tenantId).eq('data_type', 'app').eq('is_del', 0).select()) {
    var existingName = text(existingRow.propName == null ? existingRow.prop_name : existingRow.propName)
    if (!blank(existingName) && existingProps[existingName] == null) {
        existingProps[existingName] = existingRow
    }
}
for (rawKey in props.keySet()) {
    var name = propName(rawKey)
    if (physicalProp(rawKey) || physicalProp(name)) {
        continue
    }
    var row = existingProps[name]
    var data = {
        parent_id: tid, tenant_id: tenantId, prop_name: name, prop_value: valueText(props[rawKey]),
        prop_type: propType(rawKey), data_type: 'app', is_del: 0, updated_time: now
    }
    if (row == null) {
        data.tid = IdUtil.getSnowflakeNextIdStr()
        data.created_time = now
        db.table('da_prop_t').primary('tid').insert(data)
        existingProps[name] = { tid: data.tid }
    } else {
        data.tid = row.tid
        db.table('da_prop_t').primary('tid').update(data, true)
    }
}
var savedTid = tid
body = { tid: savedTid }
refreshAssetIndex()
return { tid: savedTid }
