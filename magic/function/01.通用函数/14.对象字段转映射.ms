{
  "properties": {},
  "id": "80c67a380dda4f0595e84d16e365f3db",
  "script": "// 共享函数：14.对象字段转映射\n// 函数路径：/common/objConvertMap\n// 业务说明：调用 BeanUtil.beanToMap 将传入 Java Bean 的属性转为映射，供需要键值结构的接口复用。\n// 函数输入：bean ：对象。\nimport cn.hutool.core.bean.BeanUtil\nimport cn.hutool.core.util.StrUtil\n\nvar res = new HashMap();\n// 假设 bean 是你的原始对象\nvar temp = BeanUtil.beanToMap(bean,null)\n\nreturn temp\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "14.对象字段转映射",
  "createTime": 1782660730450,
  "updateTime": 1770273772417,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/objConvertMap",
  "description": "调用 BeanUtil.beanToMap 将传入 Java Bean 的属性转为映射，供需要键值结构的接口复用。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "bean ",
      "value": null,
      "description": "对象",
      "required": false,
      "dataType": "String",
      "type": "java.lang.Object",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}