{
  "properties": {},
  "id": "4fabfd48722c4b1a8d1aeb88acce5289",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "04.数据整合层",
  "createTime": 1782955343581,
  "updateTime": 1784437771690,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/data-zh",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"layerId\": \"dwm\",\n        \"fileData\": \"1\",\n        \"layerName\": \"整合层 DWM\",\n        \"list\": [\n            {\n                \"unit\": \"个\",\n                \"name\": \"渔船渔港主题库目录数\",\n                \"value\": \"1\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"渔船渔港主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"防灾减灾主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"防灾减灾主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋经济主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋经济主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋执法主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋执法主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"渔业养殖主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"渔业养殖主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋安全主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋安全主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋资源主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋资源主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋环境主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋环境主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋科创主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋科创主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"个\",\n                \"name\": \"海洋地理主题库目录数\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            },\n            {\n                \"unit\": \"条\",\n                \"name\": \"海洋地理主题库数据量\",\n                \"value\": \"0\",\n                \"status\": \"\"\n            }\n        ],\n        \"tid\": \"2b550e9f09e243cd94b1ce981f960559\"\n    }\n}",
  "description": "返回脚本内预设的整合层主题库展示内容；当前不从租户库实时计算。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.数据整合层
// 路由：GET /scene/data-count/data-zh
// 业务说明：返回脚本内预设的整合层主题库展示内容；当前不从租户库实时计算。
// 返回当前业务分支的标准结果。
return {
    "bigdata_platform_list": [
          {
            "layerId": "1d51245d5d6b41c08cbdfcf3b286d533",
            "fileData": "/assets/scense/bigdata1.png",
            "icon": "0b22abd726834b81b55119ad71f19bc2",
            "layerName": "渔船渔港 主题库",
            "list": [
              {
                "unit": "个",
                "name": "渔船总数",
                "value": "3000",
                "status": true
              },
              {
                "unit": "个",
                "name": "渔港总数",
                "value": "87",
                "status": true
              }
            ],
            "tid": "a80c8fd8972840e38850699e7a5ec19a"
          },
          {
            "layerId": "4327e401b68143a28edf9530d9d977ed",
            "fileData": "/assets/scense/bigdata2.png",
            "icon": "41056cbd13074e03b58343e73a6d0177",
            "layerName": "渔业机构 主题库",
            "list": [
              {
                "unit": "个",
                "name": "养殖区数",
                "value": "1500",
                "status": true
              },
              {
                "unit": "个",
                "name": "水产市场",
                "value": "80",
                "status": true
              }
            ],
            "tid": "6c10ff68702747359f3bce772900afd1"
          },
          {
            "layerId": "116bf4ea10aa46a6898b78bec1c6b21b",
            "fileData": "/assets/scense/bigdata3.png",
            "icon": "185c4a81c5414894a446f4dbedeebbe7",
            "layerName": "海洋经济 主题库",
            "list": [
              {
                "unit": "家",
                "name": "龙头企业",
                "value": "396",
                "url": "",
                "status": true
              },
              {
                "unit": "亿",
                "name": "总产值",
                "value": "19,786.3",
                "status": true
              }
            ],
            "tid": "2385a87748ba475a97f25bff6886d64e"
          },
          {
            "layerId": "187bb4c7cdbf489d8fff6cc479bb3e5d",
            "fileData": "/assets/scense/bigdata4.png",
            "icon": "cd9fe84b48a1432f847e81b8443b2ba5",
            "layerName": "水产养殖 主题库",
            "list": [
              {
                "unit": "个",
                "name": "养殖区数",
                "value": "1500",
                "status": true
              },
              {
                "unit": "个",
                "name": "水产市场",
                "value": "80",
                "status": true
              }
            ],
            "tid": "1720b20a0b1c470d8d4338cd8d69e3fe"
          },
          {
            "layerId": "61f1891ffcb2453ab572f5055f0daaa0b",
            "fileData": "/assets/scense/bigdata5.png",
            "icon": "d652e5cd3903449bba6b7e9ecdc27f10",
            "layerName": "渔业资源 主题库",
            "list": [
              {
                "unit": "条",
                "name": "物种总数",
                "value": "20",
                "status": true
              },
              {
                "unit": "条",
                "name": "矿区数量",
                "value": "28",
                "status": true
              }
            ],
            "tid": "bac40c7610b24f73b56c8e2999a0f317"
          },
          {
            "layerId": "d27be954a63b4f3bb92c0b8d5919a69c",
            "fileData": "/assets/scense/bigdata6.png",
            "icon": "881d6b0b17c449aaa5014804fd9447e1",
            "layerName": "海洋环境 主题库",
            "list": [
              {
                "unit": "个",
                "name": "生态保护区",
                "value": "12",
                "status": true
              },
              {
                "unit": "万公里",
                "name": "保护区面积",
                "value": "34.34",
                "status": true
              }
            ],
            "tid": "ea8483aa157545a4bc1025d8bad6c344"
          }
        ],
}
