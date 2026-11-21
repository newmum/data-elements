{
  "id": "tenant_logpolicy_save_put_v1",
  "groupId": "sys_logpolicy_v1",
  "name": "02.更新日志策略",
  "path": "/save",
  "method": "PUT",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "校验日志策略参数与记录版本，在事务内保存并写审计。"
}
================================
import '@/common/logpolicysave' as policy
import '@/common/logresponse' as respond

// PUT /sym/logpolicy/save。校验日志策略参数与记录版本，在事务内保存并写审计。 共享实现位于 /common/logpolicysave。
return respond(() => policy(body))
