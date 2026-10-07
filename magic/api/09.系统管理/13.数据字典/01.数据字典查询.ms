{
  "properties": {},
  "id": "fa5e60254452443a8d512df21c6eed53",
  "script": null,
  "groupId": "9668e1a8b17d458f8739c9558d60ca98",
  "name": "01.数据字典查询",
  "createTime": 1782660715884,
  "updateTime": 1790479749699,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dict",
  "method": "GET",
  "parameters": [
    {
      "name": "code",
      "value": "db",
      "description": "字典编码；db 返回当前数据源选项，app 返回当前部门应用系统",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [
    {
      "name": "code",
      "value": null,
      "description": null,
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "paths": [],
  "responseBody": null,
  "description": "数据字典查询（GET /sym/dict），只读接口。code=db 从 db_datasource_t 的 Elasticsearch 查询投影读取数据源，code=app 从应用系统投影读取并按当前登录部门过滤，code=catalog 读取目录资产；其他编码读取 sym_dict_t。请求示例：code=db；涉及 sym_application_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "100120",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "您访问的内容需要登录账号后才能查看",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
// 接口：01.数据字典查询
// 路由：GET /sym/dict
// 业务说明：数据字典查询（GET /sym/dict），只读接口。code=db 从 db_datasource_t 的 Elasticsearch 查询投影读取数据源，code=app 从应用系统投影读取并按当前登录部门过滤，code=catalog 读取目录资产；其他编码读取 sym_dict_t。请求示例：code=db；涉及 sym_application_t 等业务表。
import "@post:/dst/application/list" as applicationList;
import "@post:/dst/database/list" as databaseList;
import "@post:/dst/catalog/list" as assetList;
import "@post:/sym/org/getOrgTree" as ssoOrg;
import "@post:/sym/getDict" as getDict;
import "@post:/sym/user/me" as me;
import '@/common/configData' as configData

if (code){
    if (code === "app"){
        // 编辑态必须以当前租户的应用主数据为准，不能依赖有索引延迟或缺项的 ES 列表。
        var applications = db.table('sym_application_t').where()
            .eq('is_del', 0).orderBy('sort_no').select()
        return applications.map(el => ({
            label: el.appName || el.app_name,
            value: el.tid,
            assetStatus: el.assetStatus || el.asset_status,
            ...el,
            icon: el.assetType || 'app'
        }));
    }
    if (code === "db"){
        // 数据源来自 db_datasource_t 的 ES 查询投影，不复用数据目录接口。
        body = {}
        return databaseList().map(el => ({
            label: el.dbName || el.database || el.name,
            value: el.tid || el.id,
            assetStatus: el.assetStatus,
            ...el,
            icon: el.assetType || "db"
        }));
    }
    if (code === "catalog"){
        body = {"assetType": "catalog"}
        // 返回当前业务分支的标准结果。
        return assetList().map(el => ({label: el.catalogName, value: el.tid, assetStatus: el.assetStatus, ...el, icon: el.assetType}));
    }
    if (code === "org"){
        body = {"parentId":configData().orgRootId}
        return ssoOrg();
    }
    // 指定字典编码只返回当前租户的选项数组，不能落入下方全量字典分类分支。
    // storageDomain 优先读取当前租户的新字典；公安等历史租户仍使用 SSWL 时再兼容回退。
    var readDict = (dictCode) => {
        body = {dictCode: dictCode}
        try {
            return getDict();
        } catch (e) {
            return []
        }
    }
    var hasItems = (items) => {
        if (items == null) { return false }
        for (item in items) { return true }
        return false
    }
    var dictItems = readDict(code)
    if (code === "storageDomain" && !hasItems(dictItems)) {
        dictItems = readDict("SSWL")
    }
    return dictItems || []
}

// 加载部门数据
body = {"parentId":configData().orgRootId}
const orgs = ssoOrg();

body = {"parentId": "0"}
var dicts = getDict()
var dictMap = {
  // ...dictGa(),
  "org": {
    "label": "部门",
    "options": orgs
  },
  "app": {
    "label": "业务系统",
    "options": null
  },
  "db": {
    "label": "数据源",
    "options": null
  },
  "catalog": {
    "label": "数据目录",
    "options": null
  },
  "broadcast_root": {
    "label": "全部字典",
    "options": null
  }
}

for (item in dicts) {
    dictMap[item.dictCode] = {
        label: item.dictName,
        options: item.children
    }
}
// 返回当前业务分支的标准结果。
return dictMap
