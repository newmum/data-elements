{
  "id": "tenant_logpolicy_list_head_v1",
  "groupId": "sys_logpolicy_v1",
  "name": "02.读取日志策略响应头",
  "path": "/list",
  "method": "HEAD",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "复用策略读取的权限与查询，返回空响应体。"
}
================================
import '@/common/logpolicylist' as policy
import '@/common/logresponse' as respond
import request

// HEAD /sym/logpolicy/list。复用策略读取的权限与查询，返回空响应体。 共享实现位于 /common/logpolicylist。
return respond(() => policy(request))
