{
  "id": "tenant_logarchive_page_get_v1",
  "groupId": "sys_logarchive_v1",
  "name": "01.分页读取任务",
  "path": "/page",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "description": "按当前行业会话分页读取日志归档任务。权限、筛选及排序由 /common/logtaskpage 共用。"
}
================================
import '@/common/logtaskpage' as readTasks
import '@/common/logresponse' as respond
import request

// GET /sym/logarchive/page。按当前行业会话分页读取日志归档任务。权限、筛选及排序由 /common/logtaskpage 共用。
return respond(() => readTasks('archives', request))
