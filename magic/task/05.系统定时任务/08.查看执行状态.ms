{
  "properties" : { },
  "id" : "a0934f8c5154518b8ce4c698ff7ee319",
  "script" : null,
  "groupId" : "30fc31e9c7e057f4983f7342850f1811",
  "name" : "08.查看执行状态",
  "createTime" : 1790900656413,
  "updateTime" : 1790900990908,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "system-job-status",
  "cron" : "0 0 0 * * ?",
  "enabled" : false,
  "description" : "在 Magic API 编辑器中手动执行，查看七个系统任务的最近执行时间、结果与累计次数。此项不会自动运行。"
}
================================
import systemJobs;

return systemJobs.status();
