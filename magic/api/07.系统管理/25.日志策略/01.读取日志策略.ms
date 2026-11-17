{
  "id": "tenant_logpolicy_list_get_v1",
  "groupId": "sys_logpolicy_v1",
  "name": "01.读取日志策略",
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "paths": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "读取行业日志策略，补齐各类别默认值并沿用原排序。"
}
================================
import '@/common/logpolicylist' as policy
import '@/common/logresponse' as respond
import request

// GET /sym/logpolicy/list。读取行业日志策略，补齐各类别默认值并沿用原排序。 共享实现位于 /common/logpolicylist。
return respond(() => policy(request))
