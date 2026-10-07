{
  "properties": {},
  "id": "0080724117804c1480eac250e14073b1",
  "script": null,
  "groupId": "5ac60a54d85b4682ab46fbb8def36ea2",
  "name": "01.资产全景标题",
  "createTime": 1782955343347,
  "updateTime": 1784437771540,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/title",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 100120,\n    \"msg\": \"no_login\",\n    \"desc\": \"未登录\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "返回脚本内固定的资产全景标题、导航和跳转地址；当前不从租户库实时计算。",
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
        "value": "no_login",
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
        "name": "desc",
        "value": "未登录",
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
// 接口：01.资产全景标题
// 路由：GET /scene/title
// 业务说明：返回脚本内固定的资产全景标题、导航和跳转地址；当前不从租户库实时计算。
// 返回当前业务分支的标准结果。
return {
        scenceTitle: '“海上福建”数据专区',
        nav: [
            {
            "title": "海洋要素中心",
            "url": "http://192.168.175.86:10083/me/#/",
            // "url": "http://14.103.233.56/me",
            "target": "_self"
            },
            {
            "title": "数据资源管理",
            "url": "http://192.168.175.86:10083/#/zl/zl-sjzz/list",
            "target": "_blank"
            }
        ],
        layer: ['数据开放层', '数据整合层', '数源层'],

        overview: ['数据汇聚', '数据共享'],
        overviewTab: ['数据资源','数据目录','业务系统'],
        // 页面上+中标题
        topLeft: ["数据总量","今日新增"],
        topRight: ["数据汇聚","数据共享"],
        topUnit: ["条","条","条","条"],
        middleLeft: "数据增长趋势",
        middleRight: "数据汇聚共享情况",

        bottomLeft: '局内数据归集情况',
        bottomRight: '外部数据归集情况',
        bottomRightTab: [],

        // 第一层标题
        shareLayerTitle: ['数据汇聚','应用场景','数据共享'],
        shareLayerLeft: ['汇聚系统', '汇聚目录', '汇聚数据'],
        shareLayerMiddle: ['特色场景', '应用系统', '数据指标'],
        shareLayerRight: ['API接口', '开放目录', '共享数据'],

        // 第三层标题
        collectLayerLeft: ['国家部委', '部委数据', '数据总量'],
        collectLayerMiddle: ['局内系统', '局内数据', '数据总量'],
        collectLayerRight: ['横向厅局', '厅局数据', '数据总量'],
      }
