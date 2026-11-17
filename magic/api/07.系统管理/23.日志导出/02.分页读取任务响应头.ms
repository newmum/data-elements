{
  "id": "tenant_logexport_page_head_v1",
  "groupId": "sys_logexport_v1",
  "name": "02.分页读取任务响应头",
  "path": "/page",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "按当前行业会话分页读取日志导出任务。权限、筛选及排序由 /common/logtaskpage 共用。"
}
================================
import '@/common/logtaskpage' as readTasks
import '@/common/logresponse' as respond
import request

// HEAD /sym/logexport/page。按当前行业会话分页读取日志导出任务。权限、筛选及排序由 /common/logtaskpage 共用。
return respond(() => readTasks('exports', request))
