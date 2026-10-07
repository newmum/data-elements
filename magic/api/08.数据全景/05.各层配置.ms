{
  "properties": {},
  "id": "cee025f4d4034311bb3885e19ec1307b",
  "script": null,
  "groupId": "5ac60a54d85b4682ab46fbb8def36ea2",
  "name": "05.各层配置",
  "createTime": 1782955343402,
  "updateTime": 1784437771619,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/layer/title",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"sysCols\": [\n            {\n                \"title\": \"序号\",\n                \"dataIndex\": \"num\",\n                \"key\": \"num\",\n                \"width\": 60,\n                \"align\": \"center\",\n                \"scopedSlots\": {\n                    \"customRender\": \"num\"\n                }\n            },\n            {\n                \"title\": \"区域名称\",\n                \"dataIndex\": \"areaName\",\n                \"key\": \"areaName\",\n                \"align\": \"left\",\n                \"ellipsis\": true\n            },\n            {\n                \"title\": \"部门名称\",\n                \"dataIndex\": \"departName\",\n                \"key\": \"departName\",\n                \"ellipsis\": true,\n                \"align\": \"left\"\n            },\n            {\n                \"title\": \"业务系统\",\n                \"dataIndex\": \"appName\",\n                \"key\": \"appName\",\n                \"align\": \"left\",\n                \"ellipsis\": true,\n                \"width\": \"16%\"\n            },\n            {\n                \"title\": \"资源目录\",\n                \"dataIndex\": \"categoryCount\",\n                \"key\": \"categoryCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"汇聚目录\",\n                \"dataIndex\": \"categoryHjCount\",\n                \"key\": \"categoryHjCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"汇聚数据量\",\n                \"dataIndex\": \"dataTotalCount\",\n                \"key\": \"dataTotalCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新及时\",\n                \"dataIndex\": \"gxjsCount\",\n                \"key\": \"gxjsCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新延时\",\n                \"dataIndex\": \"gxyhCount\",\n                \"key\": \"gxyhCount\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"更新及时率\",\n                \"dataIndex\": \"gxjsRateStr\",\n                \"key\": \"gxjsRateStr\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"8%\"\n            },\n            {\n                \"title\": \"数据更新情况\",\n                \"dataIndex\": \"dataUpdateInfo\",\n                \"key\": \"dataUpdateInfo\",\n                \"align\": \"center\",\n                \"ellipsis\": true,\n                \"width\": \"11%\"\n            }\n        ]\n    }\n}",
  "description": "读取各层配置并返回允许展示的当前设置。",
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
// 接口：05.各层配置
// 路由：GET /scene/layer/title
// 业务说明：读取各层配置并返回允许展示的当前设置。
// 返回当前业务分支的标准结果。
return {
    // 数源层俩个列配置
    sysCols: [{
            title: '序号',
            dataIndex: 'num',
            key: 'num',
            width: 60,
            align: 'center',
            scopedSlots: {
                customRender: 'num'
            }
        },
        // {
        //     title: '来源类型',
        //     dataIndex: 'areaName',
        //     key: 'areaName',
        //     align: 'left',
        //     ellipsis: true,
        // },
        {
            title: '来源单位/处室',
            dataIndex: 'orgName',
            key: 'orgName',
            ellipsis: true,
            align: 'left',
            width: '16%',
        },
        // {
        //     title: '来源系统',
        //     dataIndex: 'appName',
        //     key: 'appName',
        //     align: 'left',
        //     ellipsis: true,
        //     width: '17%',
        // },
        {
            title: '数据目录',
            dataIndex: 'categoryHjCount',
            key: 'categoryHjCount',
            align: 'center',
            ellipsis: true,
        },
        {
            title: '数据资源',
            dataIndex: 'categoryCount',
            key: 'categoryCount',
            align: 'center',
            ellipsis: true,
        },
        {
            title: '数据量',
            dataIndex: 'dataHjCount',
            key: 'dataHjCount',
            align: 'center',
            ellipsis: true,
        },
        {
            title: '数据更新时间',
            dataIndex: 'updateTime',
            key: 'updateTime',
            align: 'center',
            ellipsis: true,
            // width: '8%',
        },
    ],
    sysCols2: [{
            title: '序号',
            dataIndex: 'num',
            key: 'num',
            width: 60,
            align: 'center',
            scopedSlots: {
                customRender: 'num'
            }
        },
        {
            title: '来源单位/处室',
            dataIndex: 'orgName',
            key: 'orgName',
            ellipsis: true,
            align: 'left',
        },
        {
            title: '数据目录',
            dataIndex: 'categoryHjCount',
            key: 'categoryHjCount',
            align: 'center',
            ellipsis: true,
        },
        // {
        //     title: '数据指标',
        //     dataIndex: 'tagCount',
        //     key: 'tagCount',
        //     align: 'center',
        //     ellipsis: true,
        // },
        {
            title: '数据资源',
            dataIndex: 'categoryCount',
            key: 'categoryCount',
            align: 'center',
            ellipsis: true,
        },
        {
            title: '数据量',
            dataIndex: 'dataHjCount',
            key: 'dataHjCount',
            align: 'center',
            ellipsis: true,
        },
        {
            title: '数据更新时间',
            dataIndex: 'updateTime',
            key: 'updateTime',
            align: 'center',
            ellipsis: true,
            // width: '8%',
        },
    ],
    
    // 数据整合层列配置
    zhCols: [
      {
        title: '序号',
        dataIndex: 'num',
        key: 'num',
        width: 60,
        align: 'center',
        scopedSlots: {
          customRender: 'num'
        }
      },
      {
        title: '主题库名称',
        dataIndex: 'layerName',
        key: 'layerName',
        align: 'left',
        ellipsis: true,
        width: '18%'
      },
      {
        title: '统计项一',
        key: 'item1',
        width: '12%',
        scopedSlots: { customRender: 'item1' }
      },
      {
        title: '统计项二',
        key: 'item2',
        width: '12%',
        scopedSlots: { customRender: 'item2' }
      },
      {
        title: '图层ID',
        dataIndex: 'layerId',
        key: 'layerId',
        align: 'center',
        ellipsis: true,
        width: '22%'
      },
      {
        title: '唯一标识TID',
        dataIndex: 'tid',
        key: 'tid',
        align: 'center',
        ellipsis: true,
        width: '22%'
      },
      {
        title: '图标ID',
        dataIndex: 'icon',
        key: 'icon',
        align: 'center',
        ellipsis: true,
        width: '14%'
      }
    ],
}
