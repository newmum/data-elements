{
  "properties": {},
  "id": "473f26c73d35445982c07897e97c1319",
  "script": "// 共享函数：01.数据条件查询\n// 函数路径：/common/table-search\n// 业务说明：按调用方传入的表与过滤条件组装数据查询，返回匹配业务行；用于多个通用数据读取接口复用。\n// 函数输入：tableName：目标业务表名；columns：查询字段，以,隔开；conditions：查询条件集合。\nconst dbKey = dbKey || \"\";\nvar result = db[dbKey].table(tableName);\nif(columns){\n    result.column(columns);\n}\nif(conditions){\n    result = result.where();\n    for(val in conditions){\n        if(val.condition && val.condition == 'eq'){// eq --> ==\n            result.eq(val.field, val.value);\n        }else if(val.condition == 'ne'){// ne --> <>\n            result.ne(val.field, val.value);\n        }else if(val.condition == 'lt'){// lt --> <\n            result.lt(val.field, val.value);\n        }else if(val.condition == 'gt'){// gt --> >\n            result.gt(val.field, val.value);\n        }else if(val.condition == 'lte'){// lte --> <=\n            result.lte(val.field, val.value);\n        }else if(val.condition == 'gte'){// gte --> >=\n            result.gte(val.field, val.value);\n        }else if(val.condition == 'in'){// in --> in\n            result.in(val.field, val.value);\n        }else if(val.condition == 'notIn'){// notIn --> not in\n            result.notIn(val.field, val.value);\n        }else if(val.condition == 'like'){// like --> like\n            result.like(val.field, val.value);\n        }else if(val.condition == 'notLike'){// notLike --> not like\n            result.notLike(val.field, val.value);\n        }else if(val.condition == 'orderBy'){// orderBy --> ORDER BY\n            result.orderBy(val.field, val.value);\n        }else if(val.condition == 'groupBy'){// groupBy --> GROUP BY\n            result.groupBy(val.field);\n        }\n    }\n}\nreturn result;\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "01.数据条件查询",
  "createTime": 1782660730357,
  "updateTime": 1762960094412,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/table-search",
  "description": "按调用方传入的表与过滤条件组装数据查询，返回匹配业务行；用于多个通用数据读取接口复用。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "tableName",
      "value": null,
      "description": "目标业务表名",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "columns",
      "value": null,
      "description": "查询字段，以,隔开",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "conditions",
      "value": null,
      "description": "查询条件集合",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}