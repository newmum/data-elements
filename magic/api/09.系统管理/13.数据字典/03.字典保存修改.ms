{
  "properties": {},
  "id": "05715b7dbe1b4bf5a0015cd11431a45a",
  "script": null,
  "groupId": "9668e1a8b17d458f8739c9558d60ca98",
  "name": "03.字典保存修改",
  "createTime": 1782660715681,
  "updateTime": 1786191954954,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dictSaveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"5cb5dade27db44bdbfe53d9d9bd01b76\",\n    \"dictName\": \"1\",\n    \"dictCode\": \"111\",\n    \"parentId\": \"e9731ceff80a4d54be2fb3d06ddc207d\",\n    \"sortNo\": 0,\n    \"levelNo\": 3,\n    \"treePath\": \"0.e9731ceff80a4d54be2fb3d06ddc207d.5cb5dade27db44bdbfe53d9d9bd01b76\",\n    \"tagType\": \"app\",\n    \"icon\": \"align\",\n    \"children\": [\n    ],\n    \"hasChildren\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据字典 sym_dict_t 新增、修改、删除；使用请求参数 action、tid、dictName、dictCode、parentId、sortNo、levelNo、treePath。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.字典保存修改
// 路由：POST /sym/dictSaveOrUpdate
// 业务说明：数据字典 sym_dict_t 新增、修改、删除；使用请求参数 action、tid、dictName、dictCode、parentId、sortNo、levelNo、treePath。
import log
import cn.hutool.core.util.IdUtil
import tenantRuntime
/**
 * 数据字典 sym_dict_t 新增、修改、删除
 * POST /api/dict/saveOrUpdate
 * 
 * 根据 action 或 tid 判断操作类型：
 *   - action='delete' 且 tid 有值 → 逻辑删除
 *   - tid 有值 → 修改
 *   - tid 无值 → 新增
 * 
 * 请求示例：
 * 新增: { "dict_name": "xxx", "dict_code": "xxx", "parent_id": "0", "sort_no": 1, "tenant_id": "xxx" }
 * 修改: { "tid": "xxx", "dict_name": "xxx", "dict_code": "xxx", "sort_no": 1 }
 * 删除: { "action": "delete", "tid": "xxx" }
 */

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
    
    // 从 MySQL 业务表读取当前有效数据。
    var delResult = db.table('sym_dict_t')
        .where()
        .eq('tid', tid)
        .eq('is_del', 0)
        .update({
            is_del: 1,
            updated_by: updatedBy,
            updated_time: date_format(now)
        })
    
    log.info('字典逻辑删除成功, tid: {}', tid)
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
    if (tenantId != null) {
        updateData.tenant_id = tenantId
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
    
    // 从 MySQL 业务表读取当前有效数据。
    var updateResult = db.table('sym_dict_t')
        .where()
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
    var isExist = db.table('sym_dict_t')
            .where()
            .like('tree_path', "%"+ parentId +"%")
            .eq('dict_code', dictCode)
            .eq('is_del', 0)
            .selectOne();
    if (isExist){
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "当前字典类型下存在相同的字典code"
    }


    // 从 MySQL 业务表读取当前有效数据。
    var parent = db.table('sym_dict_t')
        .where()
        .eq('tid', parentId)
        .eq('is_del', 0)
        .selectOne()
        if (parent == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "parentId参数错误";
        }   
    
    if (parent) {
        var parentLevel = parent.levelNo ? parent.levelNo : 1
        var parentPath = parent.treePath ? parent.treePath : parentId
        level = parentLevel + 1
        path = parentPath + '.' + tid
    }
    // }
    // }
    
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
