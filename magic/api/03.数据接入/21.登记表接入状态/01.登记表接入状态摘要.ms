{
  "properties": {
    "leafRoute": true
  },
  "id": "789968fc62ea4333ab4e41f1f95c1606",
  "script": null,
  "groupId": "6f0ef420df525a7caa6cccc7acdc8819",
  "name": "01.登记表接入状态摘要",
  "createTime": 1792474539017,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "第五步按本页表ID批量查询申请和实时任务状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
/*
 * 功能：第五步当前页的统一接入状态及数据源汇总。
 * 输入：datasourceId、tableIds（1～100项）；输出与公共登记表接入状态汇总函数一致。
 * 权限：当前租户和登记源角色范围，逐ID校验；只读、不修改任务或登记。
 * 依赖：/common/registrationAccessSummary；固定批量SQL及一次NiFi快照。
 */
import '@/common/registrationAccessSummary' as registrationAccessSummary
return registrationAccessSummary(body)
