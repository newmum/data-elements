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
  "requestBody": "{\n    \"tid\": \"30253579e54d4061bd95c7af602e17e7\",\n    \"type\": \"catalogSubscribe\",\n    \"propList\": {\n        \"tid\": \"30253579e54d4061bd95c7af602e17e7\",\n        \"applyName\": \"完成剧申报数据目录(res_1772095968985_copy28989)\",\n        \"applyOrgId\": \"2026505033717514242\",\n        \"usci\": \"9111000071780321XQ\",\n        \"applyType\": \"政务服务\",\n        \"applyTime\": \"2026-04-08 16:06:53\",\n        \"userName\": \"33\",\n        \"applicantName\": \"zeng343\",\n        \"userPhone\": \"SFS\",\n        \"applicantPhone\": \"EEERFE\",\n        \"userEmail\": \"3333\",\n        \"applicantEmail\": \"3334\",\n        \"applyResource\": [\n            \"685ee1e783064ff8a9d7b3d08c5fe89a\"\n        ],\n        \"useTitle\": \"测试\",\n        \"averageFrequency\": \"测试\",\n        \"peakFrequency\": \"测试\",\n        \"useTime\": \"2026-07-20 10:00:00\",\n        \"useDays\": \"测试\",\n        \"otherRequests\": \"测试\",\n        \"applyBasis\": \"测试\",\n        \"appId\": \"a60c267ab6c1416d93e5965f0c2c15b5\",\n        \"useScope\": \"1\",\n        \"sceneType\": \"1\",\n        \"businessScene\": \"SSSS\"\n    }\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"b71ef91ddcc940a99b220cc3d7cd9120\"\n    }\n}",
  "description": "1. 保存主表 data_apply_form_t；使用请求参数 tid、applyName、type、propList。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.资产申请单保存更新
// 路由：POST /dws/market/applyForm/saveOrUpdate
// 业务说明：1. 保存主表 data_apply_form_t；使用请求参数 tid、applyName、type、propList。
import cn.hutool.core.util.IdUtil
import log
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import "@post:/sym/user/me" as me
import tenantRuntime
/**
 * 保存或更新申请表单
 * POST /api/apply/form/saveOrUpdate
 *
 * 业务说明：
 *   1. 保存主表 data_apply_form_t
 *   2. 保存动态属性 da_prop_t（对比新旧差量更新）
 *   3. apply_resource 数组拆分存入 data_apply_form_catalog_rela（差量同步）
 *   4. 同步写入 ES 索引 dataassets_apply_form
 *
 * 请求参数示例：
 * {
 *   "tid": "",
 *   "apply_user_id": "u001",
 *   "apply_user_name": "张三",
 *   "type": "apply",
 *   "tenant_id": "t001",
 *   "apply_resource": ["catalogId1", "catalogId2"],
 *   "propList": {
 *     "remark": "备注内容",
 *     "priority_num": 1,
 *     "expire_date": "2026-12-31"
 *   }
 * }
 */

// ========== 获取请求参数 ==========
var tid           = body?.tid

var userInfo = me()

var applyUserId    = userInfo.id
var applyUserName  = userInfo.id
var applyOrgId     = userInfo.orgId

var applyName     = body?.applyName
var type          = body?.type
var tenantId      = tenantRuntime.id()
var applyResource = body?.propList?.applyResource  // Array, e.g. ["111","222"]
var propList      = body?.propList
var isUpdate      = tid != null && tid != ''
var now           = new Date()

// ========== 参数校验 ==========
if (tid == null || tid == '') {
    // exit 500, 'tid不能为空'
}

// ========== 工具函数：属性类型推断 ==========
var getPropType = (key) => {
    if (key == null) {
        // 返回当前业务分支的标准结果。
        return '0'
    }
    if (key.endsWith('_date')) {
        // 返回当前业务分支的标准结果。
        return '4'  // 日期类型
    }
    if (key.endsWith('_num')) {
        // 返回当前业务分支的标准结果。
        return '1'  // 数字类型
    }
    if (key.endsWith('_file')) {
        // 返回当前业务分支的标准结果。
        return '5'  // 文件类型
    }
    if (key.endsWith('_dict')) {
        // 返回当前业务分支的标准结果。
        return '3'  // 字典类型
    }
    if (key.endsWith('_range')) {
        // 返回当前业务分支的标准结果。
        return '6'  // 日期区间类型
    }
    if (key.endsWith('_time')) {
        // 返回当前业务分支的标准结果。
        return '7'  // 时间区间类型
    }
    // 返回当前业务分支的标准结果。
    return '0'  // 默认字符串类型
}


var getRealPropName = (key) => {
    if (key == null) {
        // 返回当前业务分支的标准结果。
        return key
    }
    var suffixes = ['_date', '_num', '_file', '_dict', '_range', '_time']
    for (suffix in suffixes) {
        if (key.endsWith(suffix)) {
            // 返回当前业务分支的标准结果。
            return key.substring(0, key.length() - suffix.length())
        }
    }
    // 返回当前业务分支的标准结果。
    return key
}


// ========== 1. 处理 apply_resource -> catalog_ids ==========
var catalogIds = ''
if (applyResource != null && applyResource.size() > 0) {
    catalogIds = applyResource.join(',')
}

// ========== 2. 保存主表 data_apply_form_t ==========
if (isUpdate) {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_apply_form_t').primary('tid').update({
        tid: tid,
        apply_user_id:   applyUserId,
        apply_org_id:   applyOrgId,
        apply_user_name: applyUserName,
        catalog_ids: catalogIds,
        applyName:       applyName,
        type:            type,
        updated_by:      applyUserId,
        updated_time:    now
    })
    log.info('更新申请表单成功, tid: {}', tid)
} else {
    tid = IdUtil.getSnowflakeNextIdStr()
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_apply_form_t').primary('tid').insert({
        tid:             tid,
        apply_user_id:   applyUserId,
        apply_org_id:   applyOrgId,
        apply_user_name: applyUserName,
        catalog_ids: catalogIds,
        applyName:       applyName,
        type:            type,
        flow_status:     0,
        tenant_id:       tenantId,
        created_by:      applyUserId,
        created_time:    now,
        updated_by:      applyUserId,
        updated_time:    now,
        is_del:          0
        
    })
    log.info('新增申请表单成功, tid: {}', tid)
}

// ========== 3. 差量同步 data_apply_form_catalog_rela ==========
if (applyResource != null) {
    // 查询现有关联记录
    var oldRelas = db.table('data_apply_form_catalog_rela')
        .where().eq('apply_form_id', tid).eq('is_del', 0).select()

    // 构建旧数据 Map: catalog_id -> tid
    var oldRelaMap = {}
    for (rela in oldRelas) {
        oldRelaMap[rela.catalogId] = rela
    }

    // 对比新数据
    var relaInsertList = []
    var sortIndex = 0
    for (catalogId in applyResource) {
        if (catalogId == null || catalogId == '') 
        {
            continue;
        }
        if (oldRelaMap[catalogId] != null && oldRelaMap[catalogId].sortNo == sortIndex) {
            // 已存在，标记已处理
            oldRelaMap.remove(catalogId)
        } else {
            // 不存在，加入插入列表
            relaInsertList.add({
                tid:           IdUtil.getSnowflakeNextIdStr(),
                apply_form_id: tid,
                catalog_id:    catalogId,
                sort_no:       sortIndex,
                tenant_id:     tenantId,
                created_by:    applyUserId,
                created_time:  now,
                updated_by:    applyUserId,
                updated_time:  now,
                // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
                catalog_org_id: esCommonService.getDocument("dataassets", catalogId).orgId,
                catalog_org_path: "",
                is_del:        0
            })
        }
        sortIndex = sortIndex + 1
    }

    // 插入新关联
    if (relaInsertList.size() > 0) {
        // 从 MySQL 业务表读取当前有效数据。
        db.table('data_apply_form_catalog_rela').batchInsert(relaInsertList)
        log.info('新增申请单目录关联, 数量: {}', relaInsertList.size())
    }

    // 软删除已移除的关联
    var removeIds = []
    for (val in oldRelaMap.values()) {
        removeIds.add(val.tid)
    }
    if (removeIds.size() > 0) {
        // 从 MySQL 业务表读取当前有效数据。
        db.table('data_apply_form_catalog_rela')
            .where().in('tid', removeIds).delete()
            //.update({ is_del: 1, updated_time: now })
        log.info('移除申请单目录关联, 数量: {}', removeIds.size())
    }
}

// ========== 4. 差量同步 da_prop_t 动态属性 ==========
var insertList = []
var updateList = []

if (isUpdate) {
    // 查询旧属性
    var oldProps = db.table('da_prop_t')
        .where().eq('parent_id', tid).eq('is_del', 0).select()

    var oldPropMap = {}
    for (prop in oldProps) {
        oldPropMap[prop.propName] = { tid: prop.tid, prop_value: prop.propValue, prop_type: prop.propType }
    }
    log.info('旧属性数量: {}', oldPropMap.size())

    if (propList != null) {
        for (key in propList.keySet()) {
            var value    = propList[key]?.toString()
            var propType = getPropType(key)
            var propName = getRealPropName(key)
            var oldProp  = oldPropMap[propName]

            if (oldProp != null) {
                if (oldProp.prop_value != value || oldProp.prop_type != propType) {
                    updateList.add({
                        tid:          oldProp.tid,
                        prop_value:   value,
                        prop_type:    propType,
                        updated_time: now
                    })
                }
                oldPropMap.remove(propName)
            } else {
                insertList.add({
                    tid:          IdUtil.getSnowflakeNextIdStr(),
                    tenant_id:    tenantId,
                    parent_id:    tid,
                    prop_name:    propName,
                    prop_value:   value,
                    prop_type:    propType,
                    data_type:    'apply_form',
                    is_del:       0,
                    created_by:   applyUserId,
                    created_time: now,
                    updated_by:   applyUserId,
                    updated_time: now
                })
            }
        }
    }

    // 软删除旧属性中已不存在的项
    // var deletePropIds = []
    // for (propName in oldPropMap.keySet()) {
    //     deletePropIds.add(oldPropMap[propName].tid)
    // }
    // if (deletePropIds.size() > 0) {
    //     db.table('da_prop_t').where().in('tid', deletePropIds)
    //         .update({ is_del: 1, updated_time: now })
    //     log.info('删除旧属性, 数量: {}', deletePropIds.size())
    //}

    // 更新变更属性
    for (item in updateList) {
        // 从 MySQL 业务表读取当前有效数据。
        db.table('da_prop_t').where().eq('tid', item.tid)
            .update({ prop_value: item.prop_value, prop_type: item.prop_type, updated_time: item.updated_time }, true)
    }
    if (updateList.size() > 0) {
        log.info('更新属性, 数量: {}', updateList.size())
    }

} else {
    // 新增模式：直接构建插入列表
    if (propList != null) {
        for (key in propList.keySet()) {
            var value    = propList[key]?.toString()
            var propType = getPropType(key)
            var propName = getRealPropName(key)
            insertList.add({
                tid:          IdUtil.getSnowflakeNextIdStr(),
                tenant_id:    tenantId,
                parent_id:    tid,
                prop_name:    propName,
                prop_value:   value,
                prop_type:    propType,
                data_type:    'apply_form',
                is_del:       0,
                created_by:   applyUserId,
                created_time: now,
                updated_by:   applyUserId,
                updated_time: now
            })
        }
    }
}

// 批量插入属性
if (insertList.size() > 0) {
    // 从 MySQL 业务表读取当前有效数据。
    db.table('da_prop_t').batchInsert(insertList)
    log.info('插入属性, 数量: {}', insertList.size())
}

// ========== 5. 构建 ES 文档并同步 ==========
var mainData = db.table('data_apply_form_t')
    .where().eq('tid', tid).eq('is_del', 0).selectOne()

// 从 MySQL 业务表读取当前有效数据。
var propData = db.table('da_prop_t')
    .where().eq('parent_id', tid).eq('is_del', 0).select()

var esDoc = {}
if (mainData != null) {
    esDoc.tid           = mainData.tid
    esDoc.applyUserId   = mainData.applyUserId
    esDoc.applyUserName = mainData.applyUserName
    esDoc.applyName          = mainData.applyName
    esDoc.type          = mainData.type
    esDoc.catalogIds    = mainData.catalogIds
    esDoc.flowStatus    = mainData.flowStatus
    esDoc.tenantId      = mainData.tenantId
    esDoc.createdTime   = mainData.createdTime
    esDoc.updatedTime   = mainData.updatedTime
}

for (prop in propData) {
    esDoc[prop.propName] = prop.propValue
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
esCommonService.saveOrUpdate('dataassets_apply_form', tid, jsons.toJSONStr(esDoc), isUpdate)
log.info('同步ES成功, tid: {}', tid)

// ========== 6. 清理关联的购物车记录 ==========
if (!isUpdate && applyResource != null && applyResource.size() > 0) {
    // 从 MySQL 业务表读取当前有效数据。
    var deletedCount = db.table('data_market_shopping_cart_t')
        .where()
        .in('catalog_id', applyResource)
        .eq('apply_org_id', applyOrgId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: new Date() })
    log.info('清理购物车记录成功, 影响行数: {}', deletedCount)
}

// ========== 返回结果 ==========
return {
  tid
}
