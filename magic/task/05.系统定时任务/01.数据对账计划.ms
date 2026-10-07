{
  "properties" : { },
  "id" : "a04f8a372f525a8594a1972cf697cd8f",
  "script" : null,
  "groupId" : "30fc31e9c7e057f4983f7342850f1811",
  "name" : "01.数据对账计划",
  "createTime" : 1790900480658,
  "updateTime" : 1790901094066,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "reconciliation-schedule",
  "cron" : "*/30 * * * * ?",
  "enabled" : true,
  "description" : "扫描已启用且到期的对账策略并生成执行记录。单个策略的周期仍由策略自身的 Cron 表达式决定。"
}
================================
import systemJobs;

systemJobs.run("reconciliation-schedule");
