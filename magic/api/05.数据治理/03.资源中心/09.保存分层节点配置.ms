{
  "properties": {},
  "id": "dwm_center_node_save_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "09.保存分层节点配置",
  "createTime": 1785067200000,
  "updateTime": 1785067200000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/node/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"targetType\": \"layer\",\n  \"targetId\": \"8961bb1c926143a89dd9d0b0e0adb4ef\",\n  \"name\": \"数源层 DST\",\n  \"datasourceId\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"success\": true\n}",
  "description": "保存资源中心分层节点配置（POST /dwm/center/node/save）。写接口；更新 sym_dict_t.dict_name，维护 dwm_center_layer_source_t 中分层/分域节点到数据源的关联；选择关联数据源后同步将 db_datasource_t.node_id 设置为 nodeType=数据中心 的 sym_dict_t.tid；使用请求参数 targetType、targetId、name、datasourceId、layerCode、applyToAllDomains。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.保存分层节点配置
// 路由：POST /dwm/center/node/save
// 业务说明：保存资源中心分层节点配置（POST /dwm/center/node/save）。写接口；更新 sym_dict_t.dict_name，维护 dwm_center_layer_source_t 中分层/分域节点到数据源的关联；选择关联数据源后同步将 db_datasource_t.node_id 设置为 nodeType=数据中心 的 sym_dict_t.tid；使用请求参数 targetType、targetId、name、datasourceId、layerCode、applyToAllDomains。
/*
 * 保存资源中心分层/分域节点配置。
 * - 名称写回 sym_dict_t.dict_name；
 * - 数据源关联写入 dwm_center_layer_source_t，确保下次打开编辑弹框可以反填；
 * - 选择任意有效数据源后，将该数据源 node_id 归一设置为 nodeType=数据中心 的字典 tid。
 */
import cn.hutool.core.util.IdUtil

var textOf = value => value == null ? '' : (value + '').trim()
var targetType = textOf(body?.targetType)
var targetId = textOf(body?.targetId)
var name = textOf(body?.name)
var datasourceId = textOf(body?.datasourceId)
var layerCode = textOf(body?.layerCode)
var applyToAllDomains = body?.applyToAllDomains === true

if (!['layer', 'domain'].contains(targetType)) {
    exit 400, 'targetType 只能是 layer 或 domain'
}
if (targetId == '') {
    exit 400, 'targetId 不能为空'
}

var node = db.table('sym_dict_t').where().eq('tid', targetId).eq('is_del', 0).selectOne()
if (node == null) {
    exit 404, '分层节点不存在'
}

if (name != '') {
    db.table('sym_dict_t').where().eq('tid', targetId).eq('is_del', 0).update({
        dict_name: name,
        updated_time: new Date()
    })
}

var dataCenterNode = db.selectOne("""
    SELECT c.tid
      FROM sym_dict_t c
      JOIN sym_dict_t p ON p.tid = c.parent_id AND p.is_del = 0
     WHERE c.is_del = 0
       AND p.dict_code = 'nodeType'
       AND c.dict_name = '数据中心'
     LIMIT 1
""")
if (dataCenterNode == null) {
    exit 500, '缺少 nodeType=数据中心 字典项，无法保存关联数据源'
}

if (datasourceId == '') {
    db.table('dwm_center_layer_source_t').where()
        .eq('target_id', targetId)
        .eq('target_type', targetType)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: new Date() })
} else {
    var source = db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).selectOne()
    if (source == null) {
        exit 404, '关联数据源不存在'
    }

    // 业务约束：被资源中心分层/分域关联的数据源统一标记为 nodeType=数据中心。
    db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).update({
        node_id: dataCenterNode.tid,
        updated_time: new Date()
    })

    // 整层关联时，将已选数据源批量写入该层的所有二级分类。
    var targets = [{ id: targetId, type: targetType, code: layerCode }]
    if (applyToAllDomains && targetType == 'layer') {
        var domains = db.table('sym_dict_t').where()
            .eq('parent_id', targetId)
            .eq('is_del', 0)
            .orderBy('sort_no')
            .select()
        for (domain in (domains == null ? [] : domains)) {
            targets.add({ id: domain.tid, type: 'domain', code: domain.dictCode })
        }
    }

    for (target in targets) {
        var relation = db.table('dwm_center_layer_source_t').where()
            .eq('target_id', target.id)
            .eq('target_type', target.type)
            .eq('is_del', 0)
            .selectOne()
        if (relation == null) {
            db.table('dwm_center_layer_source_t').primary('tid').insert({
                tid: IdUtil.getSnowflakeNextIdStr(),
                target_id: target.id,
                target_type: target.type,
                layer_code: target.code,
                datasource_id: datasourceId,
                is_del: 0,
                created_time: new Date(),
                updated_time: new Date()
            })
        } else {
            db.table('dwm_center_layer_source_t').where().eq('tid', relation.tid).update({
                layer_code: target.code,
                datasource_id: datasourceId,
                updated_time: new Date()
            })
        }
    }
}

return { success: true }
