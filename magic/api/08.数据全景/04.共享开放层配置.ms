{
  "properties": {},
  "id": "ab058fb415d746c8bf6010de0dfaf95c",
  "script": null,
  "groupId": "5ac60a54d85b4682ab46fbb8def36ea2",
  "name": "04.共享开放层配置",
  "createTime": 1782955343389,
  "updateTime": 1784437771617,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/shared-open-title",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": -1,\n    \"msg\": \"系统内部出现错误\",\n    \"desc\": \"系统内部出现错误\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "读取共享开放层配置并返回允许展示的当前设置。",
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
// 接口：04.共享开放层配置
// 路由：GET /scene/shared-open-title
// 业务说明：读取共享开放层配置并返回允许展示的当前设置。
// 返回当前业务分支的标准结果。
return {
        leftTopLayer: '资源汇聚情况',
        leftTopBtn: ['数据库表', 'API服务', '文件'],
        leftBottomLayer: '数据共享情况',
        leftBottomBtn: ['横向厅局数据归集', '局内数据对外共享'],
        leftBottomBar: [ '共享库表','共享API'],
        rightLayer: '数据应用成效',
        rightTab: '数据应用Top10',
        rightTopCard: ['特色场景', '数据目录', '数据指标'],
        rightBottomCard: ['应用系统', '数据目录', '数据指标'],
        rightBtn: ['特色场景', '应用系统'],
        // 左侧上方列配置
        cols1: [
          {
              title: "数据表名称",
              dataIndex: "tableName",
              key: "tableName",
              align: "left",
              ellipsis: true,
              width:200
          },
          {
              title: "所属数据库",
              dataIndex: "datasourceName",
              key: "datasourceName",
              align: "left",
              ellipsis: true,
              width:160
          },
          {
              title: "所属目录",
              dataIndex: "catalogName",
              key: "catalogName",
              align: "left",
              ellipsis: true,
              width:160
          },
          {
              title: "提供部门",
              dataIndex: "organName",
              key: "organName",
              align: "left",
              ellipsis: true,
              width:160
          },
          {
              title: "数据量",
              dataIndex: "rowTotal",
              key: "rowTotal",
              align: "left",
              ellipsis: true,
              width:100
          },
        ],
        cols2: [
            {
                title: "API服务名称",
                dataIndex: "apiName",
                key: "apiName",
                align: "left",
                ellipsis: true,
                width:200
            },
            {
                title: "所属目录",
                dataIndex: "catalogName",
                key: "catalogName",
                align: "left",
                ellipsis: true,
                width:160
            },
            {
                title: "提供部门",
                dataIndex: "organName",
                key: "organName",
                align: "left",
                ellipsis: true,
                width:160
            },
            {
                title: "总调用量",
                dataIndex: "totalApiRequest",
                key: "totalApiRequest",
                align: "left",
                ellipsis: true,
                width:100
            },
        ],
        cols3: [
          {
              title: "文件名",
              dataIndex: "fileName",
              key: "fileName",
              align: "left",
              ellipsis: true,
              width:200
          },
          {
              title: "所属目录",
              dataIndex: "catalogName",
              key: "catalogName",
              align: "left",
              ellipsis: true,
              width:160
          },
          {
              title: "提供部门",
              dataIndex: "organName",
              key: "organName",
              align: "left",
              ellipsis: true,
              width:160
          },
          {
              title: "下载量",
              dataIndex: "downloadTotal",
              key: "downloadTotal",
              align: "left",
              ellipsis: true,
              width:100
          },
        ],

        // 左侧下方弹框表格列配置
        barCols: [
          {
            title: '序号',
            dataIndex: 'num',
            key: 'num',
            width: 60,
            align: 'center',
            scopedSlots: {
              customRender: 'num'
            },
          },
          // {
          //   title: '来源类型',
          //   dataIndex: 'sourceType',
          //   key: 'sourceType',
          //   align: 'center',
          //   ellipsis: true,
          //   width: '20%',
          // },
          {
            title: '来源单位/处室内',
            dataIndex: 'organName',
            key: 'organName',
            align: 'left',
            ellipsis: true,
          },
          {
            title: '来源系统',
            dataIndex: 'sourceSystem',
            key: 'sourceSystem',
            align: 'left',
            ellipsis: true,
          },
          {
            title: '共享库表',
            dataIndex: 'count1',
            key: 'count1',
            align: 'center',
            ellipsis: true,
          },
          {
            title: '共享API',
            dataIndex: 'count2',
            key: 'count2',
            align: 'center',
            ellipsis: true,
          },
          {
            title: '数据更新时间',
            dataIndex: 'updateTime',
            key: 'updateTime',
            align: 'center',
            ellipsis: true,
          },
        ]
}
