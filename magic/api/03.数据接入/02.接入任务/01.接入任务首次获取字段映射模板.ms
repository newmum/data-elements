{
  "properties": {},
  "id": "433544a14a5245e2b1be6ed6fef630c3",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "01.接入任务首次获取字段映射模板",
  "createTime": 1782955344828,
  "updateTime": 1782955348384,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/autoMappingTemplate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"0d50a16cf9b5458398a22f6fa158ea57\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"sourceDbId\": \"38ef1b566eb4477ebfc07b4c0c8aa973\",\n        \"sourceTableId\": \"192db5c37e69463c86061e66b3c89d89\",\n        \"sourceTablePrimaryKey\": \"id\",\n        \"sourceTableIncrementKey\": \"\",\n        \"targetDbId\": \"62d25cc8bf1344d6b9f41fed23af4eaf\",\n        \"targetTableId\": \"e85f4253dc8d4564a3bcef3b00053219\",\n        \"targetTablePrimaryKey\": \"\",\n        \"columnMapping\": [\n            {\n                \"sourceField\": \"id\",\n                \"sourceFieldCn\": \"序号\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"int\",\n                \"targetField\": \"id\",\n                \"targetFieldCn\": \"主键\",\n                \"targetFieldLength\": 36,\n                \"targetDataType\": \"varchar\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"name\",\n                \"sourceFieldCn\": \"姓名\",\n                \"sourceFieldLength\": 50,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"name\",\n                \"targetFieldCn\": \"角色名称\",\n                \"targetFieldLength\": 255,\n                \"targetDataType\": \"varchar\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"nationality\",\n                \"sourceFieldCn\": \"国籍\",\n                \"sourceFieldLength\": 50,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"id_number\",\n                \"sourceFieldCn\": \"证件号码\",\n                \"sourceFieldLength\": 50,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"entry_date\",\n                \"sourceFieldCn\": \"入境日期\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"date\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"entry_port\",\n                \"sourceFieldCn\": \"入境口岸\",\n                \"sourceFieldLength\": 100,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"current_location\",\n                \"sourceFieldCn\": \"当前位置\",\n                \"sourceFieldLength\": 100,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"in_border_status\",\n                \"sourceFieldCn\": \"在境状态\",\n                \"sourceFieldLength\": 20,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"risk_level\",\n                \"sourceFieldCn\": \"风险等级\",\n                \"sourceFieldLength\": 20,\n                \"sourceDataType\": \"varchar\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"last_activity_time\",\n                \"sourceFieldCn\": \"最后活动时间\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"datetime\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"is_del\",\n                \"sourceFieldCn\": \"删除标记（0-未删除，1-已删除）\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"tinyint\",\n                \"targetField\": \"is_del\",\n                \"targetFieldCn\": \"是否删除：0未删除，1已删除\",\n                \"targetFieldLength\": 0,\n                \"targetDataType\": \"int\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"create_time\",\n                \"sourceFieldCn\": \"创建时间\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"datetime\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            },\n            {\n                \"sourceField\": \"update_time\",\n                \"sourceFieldCn\": \"更新时间\",\n                \"sourceFieldLength\": 0,\n                \"sourceDataType\": \"datetime\",\n                \"targetField\": \"\",\n                \"targetFieldCn\": \"\",\n                \"targetFieldLength\": null,\n                \"targetDataType\": \"\",\n                \"dictEnable\": 0,\n                \"dictValue\": null,\n                \"dictCode\": null,\n                \"funcEnable\": 0,\n                \"funcValue\": null,\n                \"funcCode\": null\n            }\n        ]\n    }\n}",
  "description": "按接入任务关联的源表读取字段定义，生成首次配置字段映射时使用的候选模板。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.接入任务首次获取字段映射模板
// 路由：POST /autoMappingTemplate
// 业务说明：按接入任务关联的源表读取字段定义，生成首次配置字段映射时使用的候选模板。
import log
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService


//查询es 获取主表数据
var catalog = esCommonService.getDocument("dataassets", body?.tid)

var sourceTableId = catalog.sourceTableId
var sourceTablePrimaryKey = ""
var sourceTableIncrementKey = ""
var targetTableId = catalog.targetTableId
var targetTablePrimaryKey = ""


if( sourceTableId == null || sourceTableId == ""){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,"sourceTableId参数不能为空"
}

if( targetTableId == null || targetTableId == ""){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,"targetTableId参数不能为空"
}

//查询源表字段
var sourceTableColumns = db.table("db_table_column_t").where()
    .eq("table_id", sourceTableId)
    .eq("is_del","0")
    .orderBy("ordinal_position", "asc")
    .select()
    

//查询目标表字段
var targetTableColumns = db.table("db_table_column_t").where()
    .eq("table_id", targetTableId)
    .eq("is_del","0")
    .select()



//构建目标字段映射表（字段名 -> 字段信息）
var targetColumnMap = {}
if (targetTableColumns != null && targetTableColumns.size() > 0) {
    for (col in targetTableColumns) {
        targetColumnMap[col.columnName] = col
        //获取 主键
        if(col.primaryKey == 1){
            targetTablePrimaryKey =  col.columnName
        }
        
    }
}

//生成字段映射结果
var mappingResult = []
var now = new Date()


if (sourceTableColumns != null && sourceTableColumns.size() > 0) {
    for (index, sourceCol in sourceTableColumns) {
        //获取 主键
        if(sourceCol.primaryKey == 1){
            sourceTablePrimaryKey =  sourceCol.columnName
        }
        var mappingItem = {
            sourceField: sourceCol.columnName,
            sourceFieldCn: sourceCol.columnComment,
            sourceFieldLength: sourceCol.length,
            sourceDataType: sourceCol.dataType,
            targetField: "",
            targetFieldCn: "",
            targetFieldLength: null,
            targetDataType: "",
            dictEnable: 0,
            dictValue: null,
            dictCode: null,
            funcEnable: 0,
            funcValue: null,
            funcCode: null
        }
        
        //通过字段名称匹配目标字段
        var targetCol = targetColumnMap[sourceCol.columnName]
        if (targetCol != null) {
            mappingItem.targetField = targetCol.columnName
            mappingItem.targetFieldCn = targetCol.columnComment
            mappingItem.targetFieldLength = targetCol.length
            mappingItem.targetDataType = targetCol.dataType
        }
        
        mappingResult.add(mappingItem)
    }
}

var template = {
    sourceDbId : catalog.dbId,
    sourceTableId : catalog.sourceTableId,
    sourceTablePrimaryKey : sourceTablePrimaryKey,
    sourceTableIncrementKey : "",
    targetDbId : catalog.targetDbId,
    targetTableId : catalog.targetTableId,
    targetTablePrimaryKey : targetTablePrimaryKey,
    columnMapping : mappingResult

}

log.info('自动字段映射成功, 源表字段数: {}, 匹配数: {}', sourceTableColumns?.size() || 0, mappingResult.filter(item => item.targetField != "").size())

// 返回当前业务分支的标准结果。
return template
