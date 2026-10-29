[
  {
    "type": "UTitle",
    "props": {
      "name": "基本信息"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "8px 0 12px",
      "minHeight": "38px"
    }
  },
  {
    "type": "input",
    "field": "name",
    "title": "服务名称",
    "props": {
      "placeholder": "请输入清晰、完整的服务名称",
      "maxlength": 255
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "serviceCode",
    "title": "服务编码",
    "props": {
      "placeholder": "留空时由系统生成",
      "maxlength": 64
    },
    "col": {
      "span": 12
    },
    "$required": false,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "orgName",
    "title": "所属部门",
    "props": {
      "disabled": true,
      "placeholder": "按当前登录部门登记"
    },
    "col": {
      "span": 12
    },
    "$required": false,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "version",
    "value": "1.0",
    "title": "服务版本",
    "props": {
      "placeholder": "例如 1.0",
      "maxlength": 64
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "description",
    "title": "服务说明",
    "props": {
      "type": "textarea",
      "rows": 2,
      "maxlength": 500,
      "showWordLimit": true,
      "placeholder": "说明服务用途、适用场景和数据内容"
    },
    "col": {
      "span": 24
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "UTitle",
    "props": {
      "name": "访问配置"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "8px 0 12px",
      "minHeight": "38px"
    }
  },
  {
    "type": "DictSelect",
    "field": "protocol",
    "value": "HTTP",
    "title": "访问协议",
    "props": {
      "options": "accessProtocol",
      "placeholder": "请选择访问协议",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "DictSelect",
    "field": "returnType",
    "title": "返回格式",
    "props": {
      "options": "returnType",
      "placeholder": "请选择返回格式",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "ElRadioGroup",
    "field": "httpMethod",
    "title": "请求方式",
    "value": "GET",
    "props": {},
    "col": {
      "span": 24
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    },
    "class": "request-methods",
    "children": [
      {
        "type": "ElRadio",
        "props": {
          "value": "GET",
          "border": true
        },
        "style": {
          "minWidth": "88px",
          "height": "34px",
          "margin": "0",
          "padding": "0 14px"
        },
        "children": [
          "GET"
        ]
      },
      {
        "type": "ElRadio",
        "props": {
          "value": "POST",
          "border": true
        },
        "style": {
          "minWidth": "88px",
          "height": "34px",
          "margin": "0",
          "padding": "0 14px"
        },
        "children": [
          "POST"
        ]
      },
      {
        "type": "ElRadio",
        "props": {
          "value": "PUT",
          "border": true
        },
        "style": {
          "minWidth": "88px",
          "height": "34px",
          "margin": "0",
          "padding": "0 14px"
        },
        "children": [
          "PUT"
        ]
      },
      {
        "type": "ElRadio",
        "props": {
          "value": "DELETE",
          "border": true
        },
        "style": {
          "minWidth": "88px",
          "height": "34px",
          "margin": "0",
          "padding": "0 14px"
        },
        "children": [
          "DELETE"
        ]
      },
      {
        "type": "ElRadio",
        "props": {
          "value": "PATCH",
          "border": true
        },
        "style": {
          "minWidth": "88px",
          "height": "34px",
          "margin": "0",
          "padding": "0 14px"
        },
        "children": [
          "PATCH"
        ]
      }
    ],
    "style": {
      "display": "flex",
      "flexWrap": "wrap",
      "gap": "8px",
      "padding": "2px 0"
    }
  },
  {
    "type": "input",
    "field": "address",
    "title": "服务地址",
    "props": {
      "placeholder": "例如 https://api.example.com/services/query",
      "maxlength": 1000
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "publishAddress",
    "title": "发布路径",
    "props": {
      "placeholder": "例如 /services/query",
      "maxlength": 1000
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "DictSelect",
    "field": "shareType",
    "title": "共享类型",
    "props": {
      "options": "shareType",
      "placeholder": "请选择共享类型",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "DictSelect",
    "field": "dataUpdateFreq",
    "title": "更新频率",
    "props": {
      "options": "dataUpdateFreq",
      "placeholder": "请选择更新频率",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "UTitle",
    "props": {
      "name": "资源分类"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "8px 0 12px",
      "minHeight": "38px"
    }
  },
  {
    "type": "DictSelect",
    "field": "dataDomain",
    "title": "数据所属领域",
    "props": {
      "options": "dataDomain",
      "placeholder": "请选择数据所属领域",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "cascader",
    "field": "dataCategory",
    "title": "数据资源分类",
    "$dict": "dataCategory",
    "props": {
      "props": {
        "checkStrictly": true,
        "emitPath": false
      },
      "placeholder": "请选择数据资源分类",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "DictSelect",
    "field": "dataLevel",
    "title": "数据分级",
    "props": {
      "options": "dataLevel",
      "placeholder": "请选择数据分级",
      "clearable": true
    },
    "col": {
      "span": 12
    },
    "$required": true,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "input",
    "field": "apiDescription",
    "title": "补充说明",
    "props": {
      "type": "textarea",
      "rows": 2,
      "maxlength": 255,
      "placeholder": "可补充接口约定或使用注意事项"
    },
    "col": {
      "span": 24
    },
    "$required": false,
    "wrap": {
      "labelWidth": "144px"
    }
  },
  {
    "type": "UTitle",
    "props": {
      "name": "服务参数"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "16px 0 8px"
    }
  },
  {
    "type": "p",
    "native": true,
    "style": {
      "margin": "0 0 8px",
      "fontSize": "12px",
      "lineHeight": "20px",
      "color": "#758298"
    },
    "children": [
      "填写调用所需的参数；没有参数的服务可以留空。参数名称和参数位置须与实际接口一致。"
    ]
  },
  {
    "type": "FormTable",
    "name": "serviceParameters",
    "field": "publishParams",
    "value": [],
    "wrap": {
      "labelWidth": "0px"
    },
    "col": {
      "span": 24,
      "style": {
        "border": "0"
      }
    },
    "props": {
      "options": {
        "minHeight": 180,
        "importConfig": {
          "mode": "covering"
        },
        "toolbarConfig": {
          "buttons": [
            {
              "code": "import",
              "icon": "vxe-icon-cloud-upload",
              "mode": "text",
              "status": "primary",
              "name": "导入参数"
            },
            {
              "code": "open_export",
              "icon": "vxe-icon-download",
              "mode": "text",
              "status": "primary",
              "name": "导出参数"
            },
            {
              "code": "append_edit",
              "icon": "vxe-icon-add",
              "mode": "text",
              "status": "primary",
              "name": "添加参数"
            }
          ],
          "tools": []
        },
        "columns": [
          {
            "type": "seq",
            "width": 58,
            "title": "序号"
          },
          {
            "field": "paramName",
            "title": "参数名称",
            "minWidth": 150,
            "editRender": {
              "name": "VxeInput",
              "props": {
                "placeholder": "例如 pageSize",
                "maxlength": 255
              }
            }
          },
          {
            "field": "description",
            "title": "参数说明",
            "minWidth": 180,
            "editRender": {
              "name": "VxeInput",
              "props": {
                "placeholder": "说明参数含义",
                "maxlength": 255
              }
            }
          },
          {
            "field": "paramType",
            "title": "参数类型",
            "width": 120,
            "editRender": {
              "name": "ElSelect",
              "options": $dict.getDictItems("paramType"),
              "props": {
                "placeholder": "请选择类型"
              }
            }
          },
          {
            "field": "paramPosition",
            "title": "参数位置",
            "width": 120,
            "editRender": {
              "name": "ElSelect",
              "options": $dict.getDictItems("paramPosition"),
              "props": {
                "placeholder": "请选择位置"
              }
            }
          },
          {
            "field": "required",
            "title": "是否必填",
            "width": 110,
            "editRender": {
              "name": "ElSelect",
              "options": $dict.getDictItems("requiredAttribute"),
              "props": {
                "placeholder": "请选择"
              }
            }
          },
          {
            "field": "defaultValue",
            "title": "默认值",
            "minWidth": 120,
            "editRender": {
              "name": "VxeInput",
              "props": {
                "maxlength": 255
              }
            }
          },
          {
            "field": "showcaseValue",
            "title": "示例值",
            "minWidth": 120,
            "editRender": {
              "name": "VxeInput",
              "props": {
                "maxlength": 255
              }
            }
          }
        ],
        "editRules": {
          "paramName": [
            {
              "required": true,
              "message": "请填写参数名称",
              "trigger": "blur"
            }
          ],
          "paramType": [
            {
              "required": true,
              "message": "请选择参数类型",
              "trigger": "change"
            }
          ]
        }
      },
      "showOperation": true,
      "insertData": {
        "paramType": "string",
        "paramPosition": "query",
        "required": "0",
        "paramScope": "request"
      },
      "height": "auto"
    },
    "validate": [
      {
        "type": "array",
        "defaultField": {
          "type": "object",
          "fields": {
            "paramName": [
              {
                "required": true,
                "message": "请填写参数名称"
              }
            ],
            "paramType": [
              {
                "required": true,
                "message": "请选择参数类型"
              }
            ]
          }
        }
      }
    ]
  },
  {
    "type": "UTitle",
    "props": {
      "name": "调用示例"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "16px 0 8px"
    }
  },
  {
    "type": "ElTabs",
    "native": true,
    "class": "registration-examples",
    "style": {
      "width": "100%",
      "minWidth": "0"
    },
    "props": {
      "modelValue": "request"
    },
    "children": [
      {
        "type": "ElTabPane",
        "native": true,
        "props": {
          "name": "request",
          "label": "请求示例"
        },
        "children": [
          {
            "type": "CodeEditor",
            "field": "requestExample",
            "value": "",
            "wrap": {
              "labelWidth": "0px"
            },
            "col": {
              "span": 24,
              "style": {
                "border": "0"
              }
            },
            "style": {
              "width": "100%",
              "height": "280px",
              "minWidth": "0",
              "border": "1px solid #e5eaf1",
              "borderRadius": "4px",
              "overflow": "hidden"
            },
            "props": {
              "lang": "json",
              "height": "280px"
            }
          }
        ]
      },
      {
        "type": "ElTabPane",
        "native": true,
        "props": {
          "name": "response",
          "label": "响应示例"
        },
        "children": [
          {
            "type": "CodeEditor",
            "field": "responseExample",
            "value": "",
            "wrap": {
              "labelWidth": "0px"
            },
            "col": {
              "span": 24,
              "style": {
                "border": "0"
              }
            },
            "style": {
              "width": "100%",
              "height": "280px",
              "minWidth": "0",
              "border": "1px solid #e5eaf1",
              "borderRadius": "4px",
              "overflow": "hidden"
            },
            "props": {
              "lang": "json",
              "height": "280px"
            }
          }
        ]
      }
    ]
  }
]
