{
  "properties": {},
  "id": "0dc8254bb39040ebad3a1ad704fb1a31",
  "script": null,
  "groupId": "dc910e69a395588a85505154089816f4",
  "name": "03.查询任务链路原生入口",
  "path": "/native",
  "method": "GET",
  "description": "按 accessTaskId 查询当前认证行业租户的任务链路及可访问的原生 NiFi 入口。需要时读取一次根组状态，不改变流程。",
  "parameters": [],
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
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain
import java.lang.String
import request

// GET /nifi/chain/native。按接入任务定位当前行业链路，共用链路校验并按需核对原生流程组。

var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, true)
