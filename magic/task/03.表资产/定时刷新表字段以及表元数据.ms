{
  "properties" : { },
  "id" : "5e47680fc09447d6a200f542436cf7ac",
  "script" : "import scheduledTask\r\n\r\n//定时刷新表元数据、以及表字段\r\n//action：tableMetaRefreshTask 定时刷新表元数据、以及表字段固定传值\r\n//param：指定刷新的表id，不传则默认刷新全部挂接资源目录的表的元数据信息\r\nscheduledTask.scheduledRun(\"tableMetaRefreshTask\",\"1881263750861074432\");",
  "groupId" : "87fdde50f33c4c139c40f0820af3083a",
  "name" : "定时刷新表字段以及表元数据",
  "createTime" : 1782660730535,
  "updateTime" : 1737450778720,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "table-meta",
  "cron" : "0 0 0 * * ?",
  "enabled" : false,
  "description" : null
}