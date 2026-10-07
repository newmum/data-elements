{
  "properties": {},
  "id": "c1a7019f5e4a4a92a34543c1f5290015",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "05.保存网关接口资产",
  "createTime": 1784450000000,
  "updateTime": 1787153758049,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveGatewayAsset",
  "description": "保存网关接口对应的资产资料，并维护数据库与检索索引中的可查询投影。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "apiId",
      "value": null,
      "description": "网关 API ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "data",
      "value": null,
      "description": "驼峰格式的 API 详情",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "catalogId",
      "value": null,
      "description": "服务目录 ID",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 共享函数：05.保存网关接口资产
// 函数路径：/common/saveGatewayAsset
// 业务说明：保存网关接口对应的资产资料，并维护数据库与检索索引中的可查询投影。
// 函数输入：apiId：网关 API ID；data：驼峰格式的 API 详情；catalogId：服务目录 ID。
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import java.util.List
import java.util.Map

if (apiId == null || (apiId + '').trim() == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'apiId不能为空'
}
if (data == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'API资产数据不能为空'
}

var now = new Date()
var apiKey = apiId + ''
data.tid = apiKey
data.assetType = 'api'
data.createdTime = data.createdTime || date_format(now)
data.updatedTime = date_format(now)

// 从 MySQL 业务表读取当前有效数据。
if (catalogId != null && (catalogId + '').trim() != '' && db.table('da_asset_api_rela').where()
    .eq('api_id', apiKey).eq('catalog_id', catalogId).eq('is_del', 0).count() == 0) {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('da_asset_api_rela').primary('tid').insert({
        tid: IdUtil.getSnowflakeNextIdStr(),
        api_id: apiKey,
        catalog_id: catalogId,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}

// 从 MySQL 业务表读取当前有效数据。
var asset = db.table('da_asset_t').where().eq('tid', apiKey).selectOne()
if (asset == null) {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('da_asset_t').primary('tid').insert({
        tid: apiKey,
        asset_type: 'api',
        asset_status: 2,
        flow_status: 2,
        is_del: 0,
        reg_time: now,
        created_time: now,
        updated_time: now
    })
} else {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('da_asset_t').where().eq('tid', apiKey).update({
        asset_type: 'api',
        asset_status: 2,
        flow_status: 2,
        is_del: 0,
        updated_time: now
    })
}

for (entry in data.entrySet()) {
    var propName = keyName + ''
    var value = value[keyName]
    var propValue = value instanceof Map || value instanceof List
        ? jsons.toJSONStr(value)
        : value == null ? null : value + ''
    // 从 MySQL 业务表读取当前有效数据。
    var prop = db.table('da_prop_t').where()
        .eq('parent_id', apiKey).eq('prop_name', propName).selectOne()
    if (prop == null) {
        // 将校验后的业务数据写入 MySQL 主表。
        db.table('da_prop_t').primary('tid').insert({
            tid: IdUtil.getSnowflakeNextIdStr(),
            parent_id: apiKey,
            prop_name: propName,
            prop_value: propValue,
            prop_type: '0',
            data_type: 'api',
            is_del: 0,
            created_time: now,
            updated_time: now
        })
    } else {
        // 将校验后的业务数据写入 MySQL 主表。
        db.table('da_prop_t').where().eq('tid', prop.tid).update({
            prop_value: propValue,
            data_type: 'api',
            is_del: 0,
            updated_time: now
        })
    }
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
esCommonService.saveOrUpdate('dataassets', apiKey, jsons.toJSONStr(data), false)
// 返回当前业务分支的标准结果。
return data
