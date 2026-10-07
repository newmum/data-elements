{
  "properties": {},
  "id": "a1000000000000000000000000000015",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "16.规则模板",
  "path": "/options/templates",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/options/templates；只读；返回 Java 执行内核支持的受控规则模板，不包含任意SQL；规则表单选项接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：16.规则模板
// 业务说明：POST /dwm/quality/options/templates；只读；返回 Java 执行内核支持的受控规则模板，不包含任意SQL；规则表单选项接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 内置规则定义直接保存在可编辑接口中，页面可实时看到模板调整。
return [
    {ruleType:'NOT_NULL', ruleName:'非空校验', dimension:'COMPLETENESS', description:'字段值不能为 NULL'},
    {ruleType:'NOT_EMPTY', ruleName:'非空白校验', dimension:'COMPLETENESS', description:'文本字段不能为 NULL 或空字符串'},
    {ruleType:'UNIQUE', ruleName:'唯一性校验', dimension:'UNIQUENESS', description:'一个或多个字段的组合不能重复'},
    {ruleType:'RANGE', ruleName:'数值范围校验', dimension:'ACCURACY', description:'数值必须位于配置的最小值和最大值之间'},
    {ruleType:'ENUM', ruleName:'枚举值校验', dimension:'VALIDITY', description:'字段值必须属于允许值集合'},
    {ruleType:'LENGTH', ruleName:'长度校验', dimension:'VALIDITY', description:'文本长度必须位于配置范围'},
    {ruleType:'REGEX', ruleName:'格式校验', dimension:'VALIDITY', description:'文本必须匹配配置的正则表达式'},
    {ruleType:'TIMELINESS', ruleName:'时效性校验', dimension:'TIMELINESS', description:'时间字段处于执行提交时间的允许过去/未来区间'},
    {ruleType:'REFERENCE', ruleName:'跨表引用校验', dimension:'CONSISTENCY', description:'同一登记数据源的目标表中存在对应单字段或联合键'}
]
