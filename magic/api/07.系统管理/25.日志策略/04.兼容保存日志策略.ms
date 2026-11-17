{
  "id": "tenant_logpolicy_save_post_v1",
  "groupId": "sys_logpolicy_v1",
  "name": "04.兼容保存日志策略",
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "保留历史 POST 语义，复用 PUT 的参数、权限和事务。"
}
================================
import '@/common/logpolicysave' as policy
import '@/common/logresponse' as respond

// POST /sym/logpolicy/save。保留历史 POST 语义，复用 PUT 的参数、权限和事务。 共享实现位于 /common/logpolicysave。
return respond(() => policy(body))
