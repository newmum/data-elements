{
  "properties": {},
  "id": "nifi_pipeline_deploy_post_v1",
  "script": null,
  "groupId": "56eecf4a0fcb5b6cac8df60e39962bb0",
  "name": "01.部署流程",
  "path": "/deploy",
  "method": "POST",
  "description": "在可信租户内核验设计与原生组件后部署流程。支持创建候选和显式重建。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiDeployCanvas' as deploy
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiLifecycleResponse' as respond
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiDeploymentParameters$DeployRequest as DeployRequest

// POST /nifi/life/deploy 部署流程。 查询参数 id。
// POST /nifi/life/deploy 处理当前行业租户的已保存设计。
// 核对请求体后，与登记表按需部署共用 @/common/nifiDeployCanvas 的权限、锁、组件预检和失败清理。

return respond(rootCause => {
    var request = readBody(body, DeployRequest,
        'com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController$DeployRequest', null)
    var options = request == null ? null : {catalogTid: request.catalogTid(),
        preserveExisting: request.preserveExisting(), forceRebuild: request.forceRebuild()}
    return deploy(param.id, options, false)
})
