{
  "properties": {},
  "id": "288235e0cc4047b09933797f75ecfe12",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "04.机构排序",
  "createTime": 1782660716444,
  "updateTime": 1784437771401,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sort",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验同级机构标识及当前租户范围，按请求顺序更新机构排序号并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.机构排序
// 路由：POST /sym/org/sort
// 业务说明：校验同级机构标识及当前租户范围，按请求顺序更新机构排序号并返回处理结果。
// 组织机构排序接口
// 路径: /api/org/sort
// 方法: POST
// 数据源: 当前会话绑定的行业租户库
// 参数: ids (必填,同级机构ID数组,按新顺序排列,如 ["id3","id1","id2"])

import tenantRuntime;
import cn.dev33.satoken.stp.StpUtil;
import '@/common/audit-operation' as auditOperation;

var now = new Date();

// 获取入参
var ids = body?.ids;

// 参数校验
if (ids == null || ids.size() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '排序机构ID列表不能为空';
}

if(ids.size()>1000){exit 400,'一次最多排序1000个机构';}
var tenantId=tenantRuntime.id();var sql=db.camel();
return sql.transaction(()=>{
    var updateId = StpUtil.getLoginId() + '';
    var selectedIds=[];
    for(value in ids){
        var id=value==null?'':(value+'').trim();
        if(id=='' || id=='ROOT' || selectedIds.contains(id)){exit 400,'排序机构ID无效或重复';}
        selectedIds.add(id);
    }
    // 一次预取所有排序对象，逐 ID 验证当前租户归属和同级关系后才允许写入。
    var rows=sql.table('rm_org_t').where().eq('tenant_id',tenantId).eq('deleted',0).in('id',selectedIds).select();
    if(rows.size()!=selectedIds.size()){exit 404,'机构不存在或不属于当前租户';}
    var parentId=rows[0].parentId;
    if(rows.filter(row=>row.parentId!=parentId).size()>0){exit 400,'只能对同一上级下的机构排序';}

    // 按传入顺序依次设置sortNum（for迭代的是元素，手动维护序号）
    var sortNum = 0;
    for (id in selectedIds) {
        sortNum = sortNum + 1;
        // 从 MySQL 业务表读取当前有效数据。
        sql.table("rm_org_t")
            .where().eq('tenant_id',tenantId).eq('deleted',0).eq("id", id)
            .update({ sortNum: sortNum, updateId: updateId, updateTime: now });
    }

    // 返回当前业务分支的标准结果。
    auditOperation('ORG_SORTED','ORGANIZATION',null,null,{orgIds:selectedIds,count:selectedIds.size()});
    return { count: selectedIds.size() };
});
