{
  "properties": {},
  "id": "e3b19fbc235b446687d170ed13abd124",
  "script": null,
  "groupId": "5ac60a54d85b4682ab46fbb8def36ea2",
  "name": "06.整合层数据",
  "createTime": 1782955343415,
  "updateTime": 1784437771627,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/layer/zh",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"sysCols\": [\n            {\n                \"title\": \"序号\",\n                \"dataIndex\": \"num\",\n                \"key\": \"num\",\n                \"width\": 60,\n                \"align\": \"center\",\n                \"scopedSlots\": {\n                    \"customRender\": \"num\"\n                }\n            },\n            {\n                \"title\": \"区域名称\",\n                \"dataIndex\": \"areaName\",\n                \"key\": \"areaName\",\n                \"align\": \"left\",\n                \"ellipsis\": true\n            },\n            {\n                \"title\": \"部门名称\",\n                \"dataIndex\": \"departName\",\n                \"key\": \"departName\",\n                \"ellipsis\": true,\n                \"align\": \"left\"\n            },\n            {\n                \"title\": \"业务系统\",\n                \"dataIndex\": \"appName\",\n                \"key\": \"appName\",\n                \"align\": \"left\",\n                \"ellipsis\": true,\n                \"width\": \"16%\"\n            },\n            {\n                \"title\": \"资源目录\",\n                \"dataIndex\": \"categoryCount\",\n                \"key\": \"categoryCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"汇聚目录\",\n                \"dataIndex\": \"categoryHjCount\",\n                \"key\": \"categoryHjCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"汇聚数据量\",\n                \"dataIndex\": \"dataTotalCount\",\n                \"key\": \"dataTotalCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新及时\",\n                \"dataIndex\": \"gxjsCount\",\n                \"key\": \"gxjsCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新延时\",\n                \"dataIndex\": \"gxyhCount\",\n                \"key\": \"gxyhCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新及时率\",\n                \"dataIndex\": \"gxjsRateStr\",\n                \"key\": \"gxjsRateStr\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"数据更新情况\",\n                \"dataIndex\": \"dataUpdateInfo\",\n                \"key\": \"dataUpdateInfo\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"11%\"\n            }\n        ]\n    }\n}",
  "description": "读取整合层概览接口并返回大屏展示数据。",
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
        "value": "null",
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
            "name": "assetPercentage",
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
                "name": "apiCount",
                "value": "31",
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
                "name": "tableCount",
                "value": "739",
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
                "name": "fileCount",
                "value": "210",
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
          },
          {
            "name": "tableDetail",
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
                    "name": "tableName",
                    "value": "report_business_matter_item_t",
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
                    "value": "72329",
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
                    "name": "datasourceName",
                    "value": "1014db-2",
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
                    "name": "catalogName",
                    "value": "report_business_matter_item_t",
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
                    "name": "organName",
                    "value": "驻马店市民政局",
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
          },
          {
            "name": "apiDetail",
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
                    "name": "apiName",
                    "value": "身份证信息查询",
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
                    "name": "gatewayServiceId",
                    "value": "f35384bf67a14539b30893e5e4eccd04",
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
                    "name": "catalogName",
                    "value": "个人账户信息",
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
                    "name": "organName",
                    "value": "驻马店市市直机构",
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
          },
          {
            "name": "organGJSharedView",
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
                    "name": "tableCount",
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
                    "name": "apiCount",
                    "value": "19",
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
                    "name": "totalCount",
                    "value": "20",
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
                    "name": "organName",
                    "value": "山东政务服务",
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
          },
          {
            "name": "organBMSharedView",
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
                    "name": "tableCount",
                    "value": "6",
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
                    "name": "apiCount",
                    "value": "8",
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
                    "name": "totalCount",
                    "value": "14",
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
                    "name": "organName",
                    "value": "驻马店市民政局",
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
          },
          {
            "name": "organCatalogCount",
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
                "name": "bmCount",
                "value": "30",
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
                "name": "sjCount",
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
                "name": "gjCount",
                "value": "15",
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
          },
          {
            "name": "organDemandCount",
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
                "name": "bmCount",
                "value": "15",
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
                "name": "sjCount",
                "value": "6",
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
                "name": "gjCount",
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
          },
          {
            "name": "organShareCatalogRank",
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
                    "name": "categoryName",
                    "value": "驻马店市民政局",
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
                    "name": "catalogCount",
                    "value": "55",
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
          },
          {
            "name": "organShareApplyRank",
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
                    "name": "applyOrgName",
                    "value": "驻马店市民政局",
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
                    "name": "applyCount",
                    "value": "14",
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
// 接口：06.整合层数据
// 路由：GET /scene/layer/zh
// 业务说明：读取整合层概览接口并返回大屏展示数据。
import "@get:/scene/zh/overview" as zh;

// 返回当前业务分支的标准结果。
return zh();

// 返回当前业务分支的标准结果。
return [
  {
    "warehouseName": "标准库建设", // 仓名
    "warehouseCount": 9, // 库数量
    "tableTotalCount": 101, // 表总数量
    "dataVolume": "862万条", // 仓级总数据量
    "databaseList": [ // 库列表
      {
        "databaseName": "涉渔涉海人员标准库", // 库名
        "tableCount": 8, // 表数量
        "dataVolume": "32.6万条", // 库级数据量
        "tableList": [ // 表列表
          {"tableName": "海洋运输从业人员信息表", "dataVolume": "5.2万条"},
          {"tableName": "渔业从业人员信息表", "dataVolume": "18.6万条"},
          {"tableName": "海洋工程人员信息表", "dataVolume": "2.1万条"},
          {"tableName": "海上执法人员信息表", "dataVolume": "0.8万条"},
          {"tableName": "海洋科研人员信息表", "dataVolume": "0.5万条"},
          {"tableName": "海事管理人员信息表", "dataVolume": "0.3万条"},
          {"tableName": "海洋旅游从业人员信息表", "dataVolume": "4.7万条"},
          {"tableName": "船员培训记录表", "dataVolume": "0.4万条"}
        ]
      },
      {
        "databaseName": "涉渔涉海法人标准库",
        "tableCount": 8,
        "dataVolume": "7.9万条",
        "tableList": [
          {"tableName": "法人基本信息表", "dataVolume": "3.2万条"},
          {"tableName": "法人信用记录表", "dataVolume": "1.8万条"},
          {"tableName": "法人黑名单记录表", "dataVolume": "0.02万条"},
          {"tableName": "企业违法违规记录表", "dataVolume": "0.3万条"},
          {"tableName": "企业安全事故记录表", "dataVolume": "0.08万条"},
          {"tableName": "企业被暂停或取消资格记录表", "dataVolume": "0.05万条"},
          {"tableName": "福建省涉海上企业名录", "dataVolume": "2.1万条"},
          {"tableName": "海洋高新技术企业信息表", "dataVolume": "0.35万条"}
        ]
      },
      {
        "databaseName": "海上执法标准库",
        "tableCount": 11,
        "dataVolume": "46.2万条",
        "tableList": [
          {"tableName": "执法部门信息表", "dataVolume": "0.03万条"},
          {"tableName": "执法案件信息表", "dataVolume": "12.5万条"},
          {"tableName": "执法行动记录表", "dataVolume": "8.7万条"},
          {"tableName": "执法船只信息表", "dataVolume": "0.06万条"},
          {"tableName": "执法船只设备表", "dataVolume": "0.12万条"},
          {"tableName": "执法证据记录表", "dataVolume": "15.3万条"},
          {"tableName": "海上事故记录表", "dataVolume": "0.45万条"},
          {"tableName": "涉案物品记录表", "dataVolume": "3.2万条"},
          {"tableName": "海上救援记录表", "dataVolume": "0.28万条"},
          {"tableName": "执法监督记录表", "dataVolume": "1.8万条"},
          {"tableName": "渔业许可证信息表", "dataVolume": "3.76万条"}
        ]
      },
      {
        "databaseName": "海洋与渔业电子证照标准库",
        "tableCount": 5,
        "dataVolume": "12.4万条",
        "tableList": [
          {"tableName": "海洋捕捞许可证信息表", "dataVolume": "5.8万条"},
          {"tableName": "水产养殖许可证信息表", "dataVolume": "4.2万条"},
          {"tableName": "渔业证照变更记录表", "dataVolume": "1.1万条"},
          {"tableName": "证照注销记录表", "dataVolume": "0.7万条"},
          {"tableName": "证照审核记录表", "dataVolume": "0.6万条"}
        ]
      },
      {
        "databaseName": "海洋自然资源标准库",
        "tableCount": 28,
        "dataVolume": "325.8万条",
        "tableList": [
          {"tableName": "海洋生态系统信息表", "dataVolume": "0.8万条"},
          {"tableName": "海洋矿产资源信息表", "dataVolume": "0.2万条"},
          {"tableName": "海洋渔业资源信息表", "dataVolume": "12.5万条"},
          {"tableName": "海洋水质信息表", "dataVolume": "85.6万条"},
          {"tableName": "海洋地质资源信息表", "dataVolume": "3.2万条"},
          {"tableName": "海洋可再生能源资源信息表", "dataVolume": "0.15万条"},
          {"tableName": "海洋物种保护名录表", "dataVolume": "0.08万条"},
          {"tableName": "海洋资源开发利用记录表", "dataVolume": "0.42万条"},
          {"tableName": "海洋生态保护区表", "dataVolume": "0.05万条"},
          {"tableName": "主要海洋景区名录表", "dataVolume": "0.03万条"},
          {"tableName": "用岛状况信息表", "dataVolume": "0.12万条"},
          {"tableName": "用海状况信息表", "dataVolume": "0.35万条"},
          {"tableName": "用海坐标信息表", "dataVolume": "1.2万条"},
          {"tableName": "宗海基本信息表", "dataVolume": "0.28万条"},
          {"tableName": "海岛信息表", "dataVolume": "0.14万条"},
          {"tableName": "海域信息表", "dataVolume": "0.09万条"},
          {"tableName": "新修测大陆岸线信息表", "dataVolume": "0.02万条"},
          {"tableName": "无居民海岛岸线信息表", "dataVolume": "0.01万条"},
          {"tableName": "三区三线信息表", "dataVolume": "0.03万条"},
          {"tableName": "国空规划数据信息表", "dataVolume": "0.07万条"},
          {"tableName": "岸线勘测信息表", "dataVolume": "0.04万条"},
          {"tableName": "围填海现状调查信息表", "dataVolume": "0.06万条"},
          {"tableName": "海域权属信息表", "dataVolume": "0.18万条"},
          {"tableName": "福建省近岸海域环境功能区划信息表", "dataVolume": "0.02万条"},
          {"tableName": "海洋预报观测站信息表", "dataVolume": "0.05万条"},
          {"tableName": "航道经纬度信息表", "dataVolume": "0.03万条"},
          {"tableName": "红树林信息表", "dataVolume": "0.08万条"},
          {"tableName": "重要滨海湿地信息表", "dataVolume": "219.8万条"}
        ]
      },
      {
        "databaseName": "渔船标准库",
        "tableCount": 12,
        "dataVolume": "69.5万条",
        "tableList": [
          {"tableName": "在册船舶基本信息表", "dataVolume": "3.8万条"},
          {"tableName": "乡镇船舶基本信息表", "dataVolume": "1.2万条"},
          {"tableName": "渔船安全检查记录表", "dataVolume": "15.6万条"},
          {"tableName": "渔船事故记录表", "dataVolume": "0.32万条"},
          {"tableName": "渔船修理记录表", "dataVolume": "2.8万条"},
          {"tableName": "渔船渔获量记录表", "dataVolume": "28.5万条"},
          {"tableName": "渔船危废物处置记录表", "dataVolume": "0.15万条"},
          {"tableName": "渔船违法违规处罚记录表", "dataVolume": "4.2万条"},
          {"tableName": "渔船作业日志表", "dataVolume": "8.7万条"},
          {"tableName": "渔船检修历史表", "dataVolume": "1.5万条"},
          {"tableName": "渔船航行日志表", "dataVolume": "2.4万条"},
          {"tableName": "采矿船工程船数据信息", "dataVolume": "0.23万条"}
        ]
      },
      {
        "databaseName": "渔港标准库",
        "tableCount": 6,
        "dataVolume": "15.7万条",
        "tableList": [
          {"tableName": "渔港设施信息表", "dataVolume": "0.08万条"},
          {"tableName": "渔港船舶停泊记录表", "dataVolume": "8.5万条"},
          {"tableName": "渔港安全检查记录表", "dataVolume": "2.3万条"},
          {"tableName": "渔港作业管理表", "dataVolume": "1.2万条"},
          {"tableName": "渔港渔获物交易记录表", "dataVolume": "3.2万条"},
          {"tableName": "渔港港口运输记录表", "dataVolume": "0.42万条"}
        ]
      },
      {
        "databaseName": "观测网标准库",
        "tableCount": 8,
        "dataVolume": "286.3万条",
        "tableList": [
          {"tableName": "传感器配置表", "dataVolume": "0.25万条"},
          {"tableName": "实时数据表", "dataVolume": "192.6万条"},
          {"tableName": "历史数据归档表", "dataVolume": "85.3万条"},
          {"tableName": "观测站点表", "dataVolume": "0.08万条"},
          {"tableName": "规划方案表", "dataVolume": "0.02万条"},
          {"tableName": "操作日志表", "dataVolume": "6.8万条"},
          {"tableName": "异常告警表", "dataVolume": "1.15万条"},
          {"tableName": "设备维护记录表", "dataVolume": "0.1万条"}
        ]
      },
      {
        "databaseName": "硬件设施标准库",
        "tableCount": 10,
        "dataVolume": "65.6万条",
        "tableList": [
          {"tableName": "海洋环境观测设备基本信息表", "dataVolume": "0.12万条"},
          {"tableName": "海洋环境观测设备安装记录表", "dataVolume": "0.08万条"},
          {"tableName": "海洋环境观测设备检测记录表", "dataVolume": "0.25万条"},
          {"tableName": "海洋生态预警监测设备基本信息表", "dataVolume": "0.06万条"},
          {"tableName": "内陆水域环境监测设备基本信息表", "dataVolume": "0.04万条"},
          {"tableName": "执法装备基本信息表", "dataVolume": "0.15万条"},
          {"tableName": "执法装备使用记录表", "dataVolume": "1.2万条"},
          {"tableName": "海洋渔船基本信息表", "dataVolume": "3.8万条"},
          {"tableName": "乡镇船舶基本信息表", "dataVolume": "1.2万条"},
          {"tableName": "海洋渔船通信设备信息表", "dataVolume": "58.7万条"}
        ]
      }
    ]
  },
  {
    "warehouseName": "主题库建设", // 仓名
    "warehouseCount": 9, // 库数量
    "tableTotalCount": 72, // 表总数量
    "dataVolume": "328.5万条", // 仓级总数据量
    "databaseList": [ // 库列表
      {
        "databaseName": "海洋经济主题库", // 库名
        "tableCount": 8, // 表数量
        "dataVolume": "26.3万条", // 库级数据量
        "tableList": [ // 表列表
          {"tableName": "海洋经济运行监测表", "dataVolume": "8.5万条"},
          {"tableName": "海洋经济效益评估表", "dataVolume": "2.1万条"},
          {"tableName": "海洋产业布局调整表", "dataVolume": "0.3万条"},
          {"tableName": "海洋产业发展趋势分析表", "dataVolume": "0.8万条"},
          {"tableName": "海洋产业产值统计表", "dataVolume": "5.2万条"},
          {"tableName": "渔业生产统计监测表", "dataVolume": "7.6万条"},
          {"tableName": "海洋渔业资源评估表", "dataVolume": "1.2万条"},
          {"tableName": "海洋可再生能源主要企业分布表", "dataVolume": "0.6万条"}
        ]
      },
      {
        "databaseName": "海洋地理主题库",
        "tableCount": 6,
        "dataVolume": "18.7万条",
        "tableList": [
          {"tableName": "海洋地貌表", "dataVolume": "0.5万条"},
          {"tableName": "海洋水文表", "dataVolume": "12.8万条"},
          {"tableName": "海洋气候表", "dataVolume": "3.2万条"},
          {"tableName": "海洋生态表", "dataVolume": "1.1万条"},
          {"tableName": "海洋地质表", "dataVolume": "0.8万条"},
          {"tableName": "海洋资源分布表", "dataVolume": "0.3万条"}
        ]
      },
      {
        "databaseName": "海洋资源主题库",
        "tableCount": 6,
        "dataVolume": "22.4万条",
        "tableList": [
          {"tableName": "海洋生物表", "dataVolume": "8.5万条"},
          {"tableName": "海洋矿产表", "dataVolume": "0.2万条"},
          {"tableName": "海洋能源表", "dataVolume": "0.15万条"},
          {"tableName": "海洋化学表", "dataVolume": "10.2万条"},
          {"tableName": "海洋空间表", "dataVolume": "0.3万条"},
          {"tableName": "海洋生态旅游表", "dataVolume": "3.05万条"}
        ]
      },
      {
        "databaseName": "海洋环境主题库",
        "tableCount": 6,
        "dataVolume": "36.8万条",
        "tableList": [
          {"tableName": "海洋物理环境表", "dataVolume": "12.5万条"},
          {"tableName": "海水检测表", "dataVolume": "18.6万条"},
          {"tableName": "珊瑚礁生态表", "dataVolume": "0.8万条"},
          {"tableName": "海洋污染表", "dataVolume": "3.2万条"},
          {"tableName": "海岸带湿地分布表", "dataVolume": "0.4万条"},
          {"tableName": "海洋资源可持续利用监测表", "dataVolume": "1.3万条"}
        ]
      },
      {
        "databaseName": "海洋安全主题库",
        "tableCount": 6,
        "dataVolume": "29.1万条",
        "tableList": [
          {"tableName": "航行安全监测表", "dataVolume": "15.2万条"},
          {"tableName": "海上船舶安全表", "dataVolume": "6.8万条"},
          {"tableName": "海洋环境风险评估表", "dataVolume": "2.1万条"},
          {"tableName": "船舶环境保护表", "dataVolume": "1.5万条"},
          {"tableName": "海上安全事故表", "dataVolume": "0.45万条"},
          {"tableName": "海上安全监控表", "dataVolume": "3.05万条"}
        ]
      },
      {
        "databaseName": "海洋科创主题库",
        "tableCount": 9,
        "dataVolume": "12.6万条",
        "tableList": [
          {"tableName": "海洋科技研发表", "dataVolume": "2.1万条"},
          {"tableName": "海洋科技成果转化表", "dataVolume": "0.8万条"},
          {"tableName": "海洋专利与知识产权表", "dataVolume": "1.5万条"},
          {"tableName": "海洋科技人才表", "dataVolume": "0.5万条"},
          {"tableName": "海洋科技合作表", "dataVolume": "0.3万条"},
          {"tableName": "海洋科技创新表", "dataVolume": "0.6万条"},
          {"tableName": "海洋科技推广应用表", "dataVolume": "1.2万条"},
          {"tableName": "渔业对外合作与技术引进表", "dataVolume": "0.25万条"},
          {"tableName": "对台港澳渔业合作表", "dataVolume": "5.35万条"}
        ]
      },
      {
        "databaseName": "海洋执法主题库",
        "tableCount": 10,
        "dataVolume": "53.7万条",
        "tableList": [
          {"tableName": "非法捕捞执法表", "dataVolume": "18.5万条"},
          {"tableName": "海上污染违法案件表", "dataVolume": "6.2万条"},
          {"tableName": "无证开采查处表", "dataVolume": "0.3万条"},
          {"tableName": "走私案件执法表", "dataVolume": "1.2万条"},
          {"tableName": "海上交通违法表", "dataVolume": "8.7万条"},
          {"tableName": "海上联合执法表", "dataVolume": "3.5万条"},
          {"tableName": "渔政执法表", "dataVolume": "10.2万条"},
          {"tableName": "海洋违法行为举报受理表", "dataVolume": "2.1万条"},
          {"tableName": "海洋执法装备管理表", "dataVolume": "1.8万条"},
          {"tableName": "水上水下作业施工表", "dataVolume": "1.2万条"}
        ]
      },
      {
        "databaseName": "渔船渔港主题库",
        "tableCount": 9,
        "dataVolume": "67.2万条",
        "tableList": [
          {"tableName": "船舶基本信息表", "dataVolume": "3.8万条"},
          {"tableName": "船舶设计单位表", "dataVolume": "0.12万条"},
          {"tableName": "船舶修造企业表", "dataVolume": "0.08万条"},
          {"tableName": "特殊状态船舶表", "dataVolume": "0.25万条"},
          {"tableName": "船舶定位表", "dataVolume": "32.6万条"},
          {"tableName": "船舶轨迹表", "dataVolume": "25.3万条"},
          {"tableName": "运输航线表", "dataVolume": "0.15万条"},
          {"tableName": "船舶保险表", "dataVolume": "2.8万条"},
          {"tableName": "船舶保险理赔表", "dataVolume": "2.1万条"}
        ]
      },
      {
        "databaseName": "渔业养殖主题库",
        "tableCount": 12,
        "dataVolume": "61.7万条",
        "tableList": [
          {"tableName": "养殖基地表", "dataVolume": "0.35万条"},
          {"tableName": "智能养殖设备表", "dataVolume": "0.28万条"},
          {"tableName": "养殖水环境监测表", "dataVolume": "28.5万条"},
          {"tableName": "养殖投入品使用表", "dataVolume": "3.2万条"},
          {"tableName": "养殖生物生长表", "dataVolume": "12.6万条"},
          {"tableName": "养殖病害监测与防治表", "dataVolume": "4.8万条"},
          {"tableName": "养殖产量统计分析表", "dataVolume": "5.2万条"},
          {"tableName": "养殖全过程追溯表", "dataVolume": "2.1万条"},
          {"tableName": "养殖用海调查表", "dataVolume": "0.42万条"},
          {"tableName": "养殖证表", "dataVolume": "3.8万条"},
          {"tableName": "兽药表", "dataVolume": "0.15万条"},
          {"tableName": "水产养殖排放口在线监测表", "dataVolume": "0.3万条"}
        ]
      }
    ]
  },
  {
    "warehouseName": "专题库建设", // 仓名
    "warehouseCount": 10, // 库数量
    "tableTotalCount": 39, // 表总数量
    "dataVolume": "126.8万条", // 仓级总数据量
    "databaseList": [ // 库列表
      {
        "databaseName": "海洋经济运行监测专题库", // 库名
        "tableCount": 4, // 表数量
        "dataVolume": "15.2万条", // 库级数据量
        "tableList": [ // 表列表
          {"tableName": "海洋经济总览表", "dataVolume": "0.2万条"},
          {"tableName": "涉海涉渔企业生产观测表", "dataVolume": "8.5万条"},
          {"tableName": "主要海洋产业生产规模情况表", "dataVolume": "3.2万条"},
          {"tableName": "海洋经济区域产业分析模型表", "dataVolume": "3.3万条"}
        ]
      },
      {
        "databaseName": "海洋经济智能评估专题库",
        "tableCount": 4,
        "dataVolume": "12.8万条",
        "tableList": [
          {"tableName": "沿海地区旅游产值能力分析表", "dataVolume": "4.2万条"},
          {"tableName": "省市海洋经济统计表", "dataVolume": "2.8万条"},
          {"tableName": "海洋产业全景分析表", "dataVolume": "3.5万条"},
          {"tableName": "渔业统计监测表", "dataVolume": "2.3万条"}
        ]
      },
      {
        "databaseName": "海洋产业资源专题库",
        "tableCount": 2,
        "dataVolume": "6.3万条",
        "tableList": [
          {"tableName": "海域资源分布表", "dataVolume": "0.3万条"},
          {"tableName": "海水养殖资源表", "dataVolume": "6.0万条"}
        ]
      },
      {
        "databaseName": "海上综合执法专题库",
        "tableCount": 4,
        "dataVolume": "18.5万条",
        "tableList": [
          {"tableName": "海上执法案件表", "dataVolume": "8.7万条"},
          {"tableName": "违法违规线索表", "dataVolume": "5.2万条"},
          {"tableName": "海上执法任务调度表", "dataVolume": "2.1万条"},
          {"tableName": "海上联合执法表", "dataVolume": "2.5万条"}
        ]
      },
      {
        "databaseName": "智慧渔港专题库",
        "tableCount": 3,
        "dataVolume": "12.6万条",
        "tableList": [
          {"tableName": "渔港船舶进出港监管表", "dataVolume": "7.8万条"},
          {"tableName": "渔港进出港调度模型表", "dataVolume": "2.3万条"},
          {"tableName": "渔港应急处置统计分析表", "dataVolume": "2.5万条"}
        ]
      },
      {
        "databaseName": "船舶动态管理专题库",
        "tableCount": 4,
        "dataVolume": "21.4万条",
        "tableList": [
          {"tableName": "船员身份管理表", "dataVolume": "3.2万条"},
          {"tableName": "船员违法违规管理表", "dataVolume": "1.8万条"},
          {"tableName": "船员违法违规模型表", "dataVolume": "5.6万条"},
          {"tableName": "船舶违法违规模型表", "dataVolume": "10.8万条"}
        ]
      },
      {
        "databaseName": "海洋气象融合创新专题库",
        "tableCount": 3,
        "dataVolume": "8.7万条",
        "tableList": [
          {"tableName": "海洋气象监测模型表", "dataVolume": "3.5万条"},
          {"tableName": "海洋气象预报模型表", "dataVolume": "4.2万条"},
          {"tableName": "航海基础地图模型表", "dataVolume": "1.0万条"}
        ]
      },
      {
        "databaseName": "海洋生态与空间管理专题",
        "tableCount": 3,
        "dataVolume": "7.2万条",
        "tableList": [
          {"tableName": "生态保护红线表", "dataVolume": "0.2万条"},
          {"tableName": "用海规划表", "dataVolume": "0.5万条"},
          {"tableName": "海洋环境监测模型表", "dataVolume": "6.5万条"}
        ]
      },
      {
        "databaseName": "涉海涉渔科创专题",
        "tableCount": 4,
        "dataVolume": "13.5万条",
        "tableList": [
          {"tableName": "涉海涉渔项目申报主体档案表", "dataVolume": "0.8万条"},
          {"tableName": "涉海涉渔科创项目申报表", "dataVolume": "0.35万条"},
          {"tableName": "涉海涉渔科创成果表", "dataVolume": "1.2万条"},
          {"tableName": "涉海涉渔专家人才档案表", "dataVolume": "11.15万条"}
        ]
      },
      {
        "databaseName": "智慧渔业监管专题库",
        "tableCount": 4,
        "dataVolume": "10.6万条",
        "tableList": [
          {"tableName": "养殖主体档案表", "dataVolume": "3.2万条"},
          {"tableName": "养殖水质异常预警分析表", "dataVolume": "2.8万条"},
          {"tableName": "养殖水质监测分析模型表", "dataVolume": "3.5万条"},
          {"tableName": "无证养殖案件列表", "dataVolume": "1.1万条"}
        ]
      }
    ]
  }
]
