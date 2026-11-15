{
  "properties": {},
  "id": "05715b7dbe1b4bf5a0015cd11431a45a",
  "script": null,
  "groupId": "5a14723f65f3549a985a3ccdfb7bfcfe",
  "name": "03.字典保存修改",
  "createTime": 1782660715681,
  "updateTime": 1786191954954,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"5cb5dade27db44bdbfe53d9d9bd01b76\",\n    \"dictName\": \"1\",\n    \"dictCode\": \"111\",\n    \"parentId\": \"e9731ceff80a4d54be2fb3d06ddc207d\",\n    \"sortNo\": 0,\n    \"levelNo\": 3,\n    \"treePath\": \"0.e9731ceff80a4d54be2fb3d06ddc207d.5cb5dade27db44bdbfe53d9d9bd01b76\",\n    \"tagType\": \"app\",\n    \"icon\": \"align\",\n    \"children\": [\n    ],\n    \"hasChildren\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前认证租户新增、修改或删除字典项，接收标识、名称、编码、父节点和排序。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import log
import cn.hutool.core.util.IdUtil
import tenantRuntime

// POST /sym/dict/save 按当前认证租户新增、修改或删除字典项，接收标识、名称、编码、父节点和排序。

// 获取请求参数
var action = body?.action
var tid = body?.tid
var dictName = body?.dictName
var dictCode = body?.dictCode
var parentId = body?.parentId
var sortNo = body?.sortNo
var levelNo = body?.levelNo
var treePath = body?.treePath
var tenantId = tenantRuntime.id()
var icon = body?.icon
var tagType = body?.tagType
var dictDesc = body?.dictDesc
var bizType = body?.bizType
var createdBy = body?.createdBy ? body?.createdBy : "system"
var updatedBy = body?.updatedBy ? body?.updatedBy : "system"

var now = new Date()

// 字典项的读取与写入始终限定在当前认证租户，只处理有效记录。
var activeItems = () => db.table('sym_dict_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)

// ========== 删除操作 ==========
if (action == "delete") {
    if (tid == null || tid == '') {
        // 返回当前业务分支的标准结果。
        return {
            code: 400,
            success: false,
            message: "删除时 tid 不能为空"
        }
    }
    
    var delResult = activeItems()
        .eq('tid', tid)
        .update({
            is_del: 1,
            updated_by: updatedBy,
            updated_time: date_format(now)
        })
    
    if (delResult > 0) {
        log.info('字典逻辑删除成功, tid: {}', tid)
    }
    return
}

// ========== 新增或修改 ==========
var isUpdate = tid != null && tid != ''

if (isUpdate) {
    // ========== 修改 ==========
    var updateData = {
        tid: tid,
        updated_by: updatedBy,
        updated_time: date_format(now)
    }
    if (dictName != null) {
        updateData.dict_name = dictName
    }
    if (dictCode != null) {
        updateData.dict_code = dictCode
    }
    if (parentId != null) {
        // 兼容历史根节点标识；普通父节点必须属于当前租户。
        if (parentId != '' && parentId != '0' && parentId != 'root'
            && activeItems().eq('tid', parentId).selectOne() == null) {
            exit 500, "parentId参数错误"
        }
        updateData.parent_id = parentId
    }
    if (sortNo != null) {
        updateData.sort_no = sortNo
    }
    if (levelNo != null) {
        updateData.level_no = levelNo
    }
    if (treePath != null) {
        updateData.tree_path = treePath
    }
    if (dictDesc != null) {
        updateData.dict_desc = dictDesc
    }
    if (icon != null) {
        updateData.icon = icon
    }
    if (tagType != null) {
        updateData.tag_type = tagType
    }
    if (bizType != null) { updateData.biz_type = bizType }
    
    var updateResult = activeItems()
        .eq('tid', tid)
        .update(updateData, true)
    
    if (updateResult == 0) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "更新的记录不存在或已删除"
    }
    
    log.info('字典修改成功, tid: {}', tid)
    // 返回当前业务分支的标准结果。
    return true
} else {
    // ========== 新增 ==========
    if (!dictName || !dictCode) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "新增时 dictName或dictCode 不能为空"
    }
    
    tid = IdUtil.getSnowflakeNextIdStr()

    
    // 计算 level_no 和 tree_path
    var level = 1
    var path = tid
    if (!parentId){
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "parentId不能为空";
    }

    // 判断该父级字典下是否有相同dictCode
    var isExist = activeItems()
        .like('tree_path', "%" + parentId + "%")
        .eq('dict_code', dictCode)
        .selectOne()
    if (isExist){
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "当前字典类型下存在相同的字典code"
    }


    var parent = activeItems().eq('tid', parentId).selectOne()
    if (parent != null) {
        var parentLevel = parent.levelNo ? parent.levelNo : 1
        var parentPath = parent.treePath ? parent.treePath : parentId
        level = parentLevel + 1
        path = parentPath + '.' + tid
    } else if (parentId == '0' || parentId == 'root') {
        // 根标识表示逻辑层级，无需从其他租户借用根记录。
        level = 2
        path = parentId + '.' + tid
    } else {
        exit 500, "parentId参数错误"
    }
    
    var sortVal = 0
    if (sortNo != null) {
        sortVal = sortNo.toString()::int(0)
    }
    
    // 从 MySQL 业务表读取当前有效数据。
    db.table('sym_dict_t')
        .primary('tid')
        .insert({
            tid: tid,
            dict_name: dictName,
            dict_code: dictCode != null ? dictCode : dictName,
            parent_id: parentId != null ? parentId : 'root',
            sort_no: sortVal,
            level_no: level,
            tree_path: path,
            icon: icon,
            dict_desc: dictDesc,
            biz_type: bizType,
            tag_type: tagType,
            created_by: createdBy,
            created_time: date_format(now),
            updated_by: updatedBy,
            updated_time: date_format(now),
            is_del: 0,
            tenant_id: tenantId
        })
    
    log.info('字典新增成功, tid: {}, dict_name: {}', tid, dictName)
    // 返回当前业务分支的标准结果。
    return {"tid":tid}
}
