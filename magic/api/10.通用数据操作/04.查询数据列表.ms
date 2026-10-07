{
  "properties": {},
  "id": "ce9e9585422a4ff5a46eb41b595a1a0e",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "04.查询数据列表",
  "createTime": 1782660730153,
  "updateTime": 1784437771468,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [
    {
      "name": "page",
      "value": "1",
      "description": "真实数据示例参数：page",
      "required": false,
      "dataType": "Long",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "size",
      "value": "20",
      "description": "真实数据示例参数：size",
      "required": false,
      "dataType": "Long",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n    \"modelCode\": \"data-catalog-report\",\n    \"conditions\": [\n        {\n            \"field\": \"createdTime\",\n            \"condition\": \"orderBy\",\n            \"value\": \"desc\"\n        },\n        {\n            \"field\": \"orgaId\",\n            \"condition\": \"eq\",\n            \"value\": \"1690909298869555201\"\n        }\n    ],\n    \"tableName\": \"app_workshop_repository\",\n    \"columns\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"desc\": \"操作成功\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 71,\n        \"list\": [\n            {\n                \"tid\": \"1967875533520019456\",\n                \"assetName\": \"测试ORCLE09016\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-09-16T16:57:43\",\n                \"registerTime\": \"2025-09-16T16:57:43\",\n                \"assetDesc\": \"1111\",\n                \"appId\": null,\n                \"identifier\": \"R-35052501\",\n                \"appName\": null,\n                \"dbId\": \"1887764833800323072\",\n                \"sourceName\": \"table_1758013035696\",\n                \"targetTableId\": \"1967875541132681216\",\n                \"rowTotal\": 22364,\n                \"itemNum\": 2\n            },\n            {\n                \"tid\": \"1967833035749801984\",\n                \"assetName\": \"0916目录\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-09-16T14:08:50\",\n                \"registerTime\": \"2025-09-16T14:08:50\",\n                \"assetDesc\": \"0916目录\",\n                \"appId\": null,\n                \"identifier\": \"R-35052409\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1758002017962\",\n                \"targetTableId\": \"1967843555840180224\",\n                \"rowTotal\": null,\n                \"itemNum\": 2\n            },\n            {\n                \"tid\": \"1967553958195314688\",\n                \"assetName\": \"目录091501\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-09-15T19:39:53\",\n                \"registerTime\": \"2025-09-15T19:39:53\",\n                \"assetDesc\": \"测试\",\n                \"appId\": null,\n                \"identifier\": \"R-35058027\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1757936357046\",\n                \"targetTableId\": \"1967553965598261248\",\n                \"rowTotal\": 22364,\n                \"itemNum\": 2\n            },\n            {\n                \"tid\": \"1967472758474752000\",\n                \"assetName\": \"目录09015\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-09-15T14:17:14\",\n                \"registerTime\": \"2025-09-15T14:17:14\",\n                \"assetDesc\": \"目录09015\",\n                \"appId\": null,\n                \"identifier\": \"R-35058579\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1757917016939\",\n                \"targetTableId\": \"1967553063067926528\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1966417541943607296\",\n                \"assetName\": \"目录0912\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-09-12T16:24:10\",\n                \"registerTime\": \"2025-09-12T16:24:10\",\n                \"assetDesc\": \"目录0912\",\n                \"appId\": null,\n                \"identifier\": \"R-35054301\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1757665426233\",\n                \"targetTableId\": \"1966417625183764480\",\n                \"rowTotal\": null,\n                \"itemNum\": 2\n            },\n            {\n                \"tid\": \"1965599683298754560\",\n                \"assetName\": \"测试0910\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-09-10T10:14:18\",\n                \"registerTime\": \"2025-09-10T10:14:18\",\n                \"assetDesc\": \"1111\",\n                \"appId\": null,\n                \"identifier\": \"R-35051889\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1757470305266\",\n                \"targetTableId\": \"1965599691368595456\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1963082427507441664\",\n                \"assetName\": \"本地测试目录090301\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-09-03T11:31:37\",\n                \"registerTime\": \"2025-09-03T11:31:37\",\n                \"assetDesc\": \"333333\",\n                \"appId\": null,\n                \"identifier\": \"R-35058762\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756870272414\",\n                \"targetTableId\": \"1963082429671702528\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1963081381913591808\",\n                \"assetName\": \"测试090301\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-09-03T11:27:28\",\n                \"registerTime\": \"2025-09-03T11:27:28\",\n                \"assetDesc\": \"1112312\",\n                \"appId\": null,\n                \"identifier\": \"R-35051208\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756870031469\",\n                \"targetTableId\": \"1963081393313710080\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1963079809922007040\",\n                \"assetName\": \"测试目录0903\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-09-03T11:21:13\",\n                \"registerTime\": \"2025-09-03T11:21:13\",\n                \"assetDesc\": \"12312313\",\n                \"appId\": null,\n                \"identifier\": \"R-35053127\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756869655797\",\n                \"targetTableId\": \"1963079811884941312\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1961361387039264768\",\n                \"assetName\": \"测试目录0829\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-29T17:32:49\",\n                \"registerTime\": \"2025-08-29T17:32:49\",\n                \"assetDesc\": \"3333\",\n                \"appId\": null,\n                \"identifier\": \"R-35059798\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756459855359\",\n                \"targetTableId\": \"1963073144644468736\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1961359930290376704\",\n                \"assetName\": \"测试目录\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-29T17:27:02\",\n                \"registerTime\": \"2025-08-29T17:27:02\",\n                \"assetDesc\": \"1111\",\n                \"appId\": null,\n                \"identifier\": \"R-1961359930277793792\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756459607513\",\n                \"targetTableId\": null,\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1960643877889413120\",\n                \"assetName\": \"测试目录2\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-08-27T18:01:42\",\n                \"registerTime\": \"2025-08-27T18:01:42\",\n                \"assetDesc\": \"测试目录2\",\n                \"appId\": null,\n                \"identifier\": \"R-1960643877876830208\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756288879667\",\n                \"targetTableId\": \"1960643912286900224\",\n                \"rowTotal\": 6,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1960639047460491264\",\n                \"assetName\": \"测试目录\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-27T17:42:30\",\n                \"registerTime\": \"2025-08-27T17:42:30\",\n                \"assetDesc\": \"测试目录\",\n                \"appId\": null,\n                \"identifier\": \"R-1960639047439519744\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1756287730659\",\n                \"targetTableId\": \"1960639066909478912\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1957002001449750528\",\n                \"assetName\": \"互联网目录\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-08-17T16:50:11\",\n                \"registerTime\": \"2025-08-17T16:50:11\",\n                \"assetDesc\": \"互联网目录\",\n                \"appId\": null,\n                \"identifier\": \"R-111\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755420597207\",\n                \"targetTableId\": \"1957002023868305408\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1957000277129433088\",\n                \"assetName\": \"公安目录\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 2,\n                \"createdTime\": \"2025-08-17T16:43:20\",\n                \"registerTime\": \"2025-08-17T16:43:20\",\n                \"assetDesc\": \"公安目录\",\n                \"appId\": null,\n                \"identifier\": \"R-1957000277116850176\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755420181582\",\n                \"targetTableId\": \"1957003846201118720\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            },\n            {\n                \"tid\": \"1956977720376430592\",\n                \"assetName\": \"12\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-17T15:13:42\",\n                \"registerTime\": \"2025-08-17T15:13:42\",\n                \"assetDesc\": \"12\",\n                \"appId\": null,\n                \"identifier\": \"R-1956977720368041984\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755414814281\",\n                \"targetTableId\": \"1958428393009844224\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1956738947508080640\",\n                \"assetName\": \"2\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-16T23:24:54\",\n                \"registerTime\": \"2025-08-16T23:24:54\",\n                \"assetDesc\": \"321321\",\n                \"appId\": null,\n                \"identifier\": \"123\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755357666836\",\n                \"targetTableId\": \"1960245433190354944\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1956738213106421760\",\n                \"assetName\": \"1\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-16T23:21:59\",\n                \"registerTime\": \"2025-08-16T23:21:59\",\n                \"assetDesc\": \"321321\",\n                \"appId\": null,\n                \"identifier\": \"R-1956738213093838848\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755357666836\",\n                \"targetTableId\": \"1956966515305877504\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1956738025449066496\",\n                \"assetName\": \"321323\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-16T23:21:14\",\n                \"registerTime\": \"2025-08-16T23:21:14\",\n                \"assetDesc\": \"321321\",\n                \"appId\": null,\n                \"identifier\": \"R-1956738025436483584\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755357666836\",\n                \"targetTableId\": \"1964951780880318464\",\n                \"rowTotal\": null,\n                \"itemNum\": 0\n            },\n            {\n                \"tid\": \"1956737029792600064\",\n                \"assetName\": \"3213\",\n                \"orgaId\": \"1690909298869555201\",\n                \"orgaName\": \"治安总队\",\n                \"assetStatus\": 0,\n                \"createdTime\": \"2025-08-16T23:17:16\",\n                \"registerTime\": \"2025-08-16T23:17:16\",\n                \"assetDesc\": \"3213\",\n                \"appId\": null,\n                \"identifier\": \"R-1956737029775822848\",\n                \"appName\": null,\n                \"dbId\": \"1934562413636751360\",\n                \"sourceName\": \"table_1755357365818\",\n                \"targetTableId\": \"1956960407614918656\",\n                \"rowTotal\": null,\n                \"itemNum\": 1\n            }\n        ]\n    }\n}",
  "description": "根据表名或模型编码、字段和过滤条件，调用共享查询函数返回匹配的数据列表。",
  "requestBodyDefinition": {
    "name": "root",
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
        "name": "modelCode",
        "value": "data-catalog-report",
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
        "name": "conditions",
        "value": "",
        "description": "",
        "required": false,
        "dataType": "Array",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
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
                "name": "field",
                "value": "createdTime",
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
                "name": "condition",
                "value": "orderBy",
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
                "name": "value",
                "value": "desc",
                "description": "",
                "required": false,
                "dataType": "String",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              }
            ]
          }
        ]
      }
    ]
  },
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
        "value": "0",
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
        "value": "success",
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
        "value": "操作成功",
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
        "value": "true",
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
            "name": "total",
            "value": "1",
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
            "name": "list",
            "value": "",
            "description": "",
            "required": false,
            "dataType": "Array",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": [
              {
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
                    "name": "tid",
                    "value": "1954158862632534016",
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
                    "name": "assetName",
                    "value": "测试",
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
                    "name": "orgaId",
                    "value": "1690909298869555201",
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
                    "name": "orgaName",
                    "value": "治安总队",
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
                    "name": "assetStatus",
                    "value": "2",
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
                    "name": "registerTime",
                    "value": "2025-08-09T20:32:34",
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
                    "name": "appId",
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
                  },
                  {
                    "name": "appName",
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
                  },
                  {
                    "name": "dbId",
                    "value": "1934562413636751360",
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
                    "name": "sourceName",
                    "value": "table_1754742730776",
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
                    "name": "targetTableId",
                    "value": "1954158869892874240",
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
                    "name": "rowTotal",
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
                  },
                  {
                    "name": "itemNum",
                    "value": "2",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
================================
// 接口：04.查询数据列表
// 路由：POST /data/list
// 业务说明：根据表名或模型编码、字段和过滤条件，调用共享查询函数返回匹配的数据列表。
import log;
import '@/common/table-search' as tableSearch;
import '@/common/model-search' as modelSearch;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 解析请求体并为可选参数设置默认值。
const {tableName,modelCode,columns,conditions} = body;
var result;
if(!size || size>9999){
    size = 9999
}
if(tableName){
    result = tableSearch(tableName,columns,conditions).page(size, (page-1)*size);
}else{
    result = db.page(modelSearch(modelCode,columns,conditions));
}
// 返回当前业务分支的标准结果。
return result;
