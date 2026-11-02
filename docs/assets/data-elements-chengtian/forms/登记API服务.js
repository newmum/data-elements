[
  {
    "type": "UTitle",
    "props": {
      "name": "基本信息"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "0 0 12px",
      "minHeight": "20px",
      "height": "20px",
      "padding": "0"
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
    "title": "服务描述",
    "props": {
      "type": "textarea",
      "rows": 3,
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
      "name": "接口信息"
    },
    "class": "registration-section-title",
    "style": {
      "margin": "8px 0 12px",
      "minHeight": "38px"
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
      "span": 24
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
    "type": "div",
    "native": true,
    "class": "registration-parameters-section",
    "style": {
      "position": "relative",
      "width": "100%",
      "margin": "16px 0 8px"
    },
    "children": [
      {
        "type": "UTitle",
        "props": {
          "name": "参数信息"
        },
        "class": "registration-section-title registration-parameters-heading",
        "style": {
          "position": "absolute",
          "top": "0",
          "left": "0",
          "width": "auto",
          "height": "40px",
          "padding": "0",
          "margin": "0",
          "zIndex": 2
        }
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
          },
          "class": "registration-parameter-field"
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
                "field": "paramScope",
                "title": "参数类型",
                "width": 110,
                "editRender": {
                  "name": "ElSelect",
                  "options": [
                    { "label": "入参", "value": "request" },
                    { "label": "出参", "value": "response" }
                  ],
                  "props": { "placeholder": "选择入参或出参" }
                }
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
                "title": "数据类型",
                "width": 120,
                "editRender": {
                  "name": "ElSelect",
                  "options": [...$dict.getDictItems("paramType"), ...["string", "number", "boolean", "object", "array"].filter(value => !$dict.getDictItems("paramType").some(item => item.value === value)).map(value => ({ label: value, value }))],
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
                  "options": [{ "label": "是", "value": "1" }, { "label": "否", "value": "0" }],
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
              "paramScope": [{ "required": true, "pattern": "^(request|response)$", "message": "请选择入参或出参" }],
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
                  "message": "请选择数据类型",
                  "trigger": "change"
                }
              ]
            }
          },
          "showOperation": true,
          "normalizeSelectValues": true,
          "valueAliases": { "required": { "true": "1", "false": "0", "必填": "1", "可选": "0" } },
          "preserveCsvText": true,
          "importColumnMigrations": [{ "from": "paramScope", "to": "paramType", "values": [...$dict.getDictItems("paramType").flatMap(item => [item.label, item.value]), "string", "number", "boolean", "object", "array", "int", "integer", "long", "float", "double", "decimal", "date", "datetime"], "fallback": "request" }],
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
                "required": [{ "type": "enum", "enum": ["1", "0"], "message": "是否必填须选择是或否" }],
                "paramScope": [{ "type": "enum", "enum": ["request", "response"], "required": true, "message": "参数类型须为入参或出参" }],
                "paramName": [
                  {
                    "required": true,
                    "message": "请填写参数名称"
                  }
                ],
                "paramType": [
                  {
                    "required": true,
                    "message": "请选择数据类型"
                  }
                ]
              }
            }
          }
        ]
      }
    ]
  },
  {
    "type": "div",
    "native": true,
    "class": "registration-examples",
    "style": { "position": "relative", "width": "100%", "minWidth": "0" },
    "children": [
      {
        "type": "ElButton",
        "native": true,
        "name": "generateActiveExample",
        "props": { "link": true, "type": "primary", "aria-label": "自动生成当前示例" },
        "style": { "position": "absolute", "top": "0", "right": "0", "height": "40px", "margin": "0", "zIndex": 1 },
        "children": ["自动生成"]
      },
      {
        "type": "ElTabs",
        "native": true,
        "class": "registration-example-tabs",
        "name": "serviceExamples",
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
  }
]
