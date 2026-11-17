{
  "properties": {},
  "id": "c30f3718868148ce84e65217f49a7720",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "07.处理节点完成事件",
  "createTime": 1782955344570,
  "updateTime": 1784446270719,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/finish",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "处理 Warm-Flow 完成事件，更新关联业务单及资产状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalFlow
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest

// 处理 Warm-Flow 完成事件，更新关联业务单及资产状态。
if (!(engineEvent instanceof ApprovalCallbackRequest)) {
    exit 403, '仅接受流程引擎完成事件'
}

// 业务状态和索引更新与引擎回调耦合，继续由 Java 事务/外部客户端完成。
return approvalFlow.finish(engineEvent)
