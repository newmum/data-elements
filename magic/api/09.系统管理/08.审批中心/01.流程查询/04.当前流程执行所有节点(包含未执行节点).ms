{
  "properties": {},
  "id": "512ab6be54cc45b28f67828f280c3bd3",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "04.当前流程执行所有节点(包含未执行节点)",
  "createTime": 1782955344470,
  "updateTime": 1787153759554,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/executeNodes",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"instanceId\": \"2039888528212860929\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "审批中心 - 当前流程执行所有节点(包含未执行节点)；使用请求参数 instanceId；涉及 rm_user_org_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.当前流程执行所有节点(包含未执行节点)
// 路由：POST /sym/approval/query/executeNodes
// 业务说明：审批中心 - 当前流程执行所有节点(包含未执行节点)；使用请求参数 instanceId；涉及 rm_user_org_rela_t 等业务表。
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.InsService as insService
import org.dromara.warm.flow.core.service.TaskService as taskService
import org.dromara.warm.flow.core.service.NodeService as nodeService
import org.dromara.warm.flow.core.service.HisTaskService as hisTaskService
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowTask;
import log
import jsons
import tenantAccounts

/*
 * 审批中心 - 当前流程执行所有节点(包含未执行节点)
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */

// 解析请求体并为可选参数设置默认值。
var instance = insService.getById(body?.instanceId::long)

var flowHisTasks = hisTaskService.orderById().list(new FlowHisTask().setInstanceId(instance.id));

if(flowHisTasks != null)
{
    flowHisTasks = JSONUtil.parse(jsons.toJSONStr(flowHisTasks));
}else {
    flowHisTasks = []
}

var nodeList = []
var nodeCodeList = []
var lastTargetNodeCode = "" 
flowHisTasks.forEach( item ->{
    var node = {
        "nodeCode": item.nodeCode,
        "nodeName": item.nodeName,
        "skipType": item.skipType,
        "flowStatus": item.flowStatus,
        "message": item.message,
        "createTime": item.createTime,
        "updateTime": item.updateTime,
        "targetNodeCode": item.targetNodeCode,
        "targetNodeName": item.targetNodeName
    }
    ext = jsons.toJSONObject(item.variable)
    var orgSQL = """
        SELECT
        u.user_id,
        org.id,
        org.name,
        org.serial_number,
        org.org_path 
        FROM
        rm_user_org_rela_t u,
        rm_org_t org 
        WHERE
        u.user_id = #{ext.handle} 
        AND u.job_type = 1 
        AND u.org_id = org.id
    """
    // 从 MySQL 业务表读取当前有效数据。
    var orgList = db.selectOne(orgSQL)

    var userNames = tenantAccounts.names([ext.handle])
    var user = { realName: userNames[ext.handle] || ext.handle }

    node.orgName = orgList.name
    node.userName = user.realName
    node.message = ext.message == null ? "无":ext.message//审批意见
    node.hisTaskId = item.id + ""//办理节点任务id 用于附件查看

    nodeList.add(node)
    nodeCodeList.add(item.nodeCode)
    lastTargetNodeCode = item.targetNodeCode
})

if( lastTargetNodeCode == "end"){
     var node = {
                    "nodeCode": "end",
                    "nodeName": "结束",
                    "skipType": "PASS",
                    "flowStatus": "2",
                    "message": null,
                    "createTime": flowHisTasks.last.createTime,
                    "updateTime": flowHisTasks.last.updateTime
                }
    nodeList.add(node)
}else {
    if(flowHisTasks.size() != 0){
        var node = {
                        "nodeCode": flowHisTasks.last.targetNodeCode,
                        "nodeName": flowHisTasks.last.targetNodeName,
                        "skipType": null,
                        "flowStatus": null,
                        "message": null,
                        "createTime": null,
                        "updateTime": null
                    }
         nodeList.add(node)  

        var list = nodeService.suffixNodeList(instance.definitionId, lastTargetNodeCode)
        if( list != null && list.size() != 0){
            list = JSONUtil.parse(jsons.toJSONStr(list))
            list.forEach(item -> {
                    var node = {
                        "nodeCode": item.nodeCode,
                        "nodeName": item.nodeName,
                        "skipType": null,
                        "flowStatus": null,
                        "message": null,
                        "createTime": null,
                        "updateTime": null
                    }
                    nodeCodeList.add(item.nodeCode)
                    nodeList.add(node)
        })
        }
    }
}

// 返回当前业务分支的标准结果。
return nodeList
