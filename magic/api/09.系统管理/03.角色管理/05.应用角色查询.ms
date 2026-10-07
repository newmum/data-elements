{
  "properties": {},
  "id": "102ab47a48044492a06eed24a5321079",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "05.应用角色查询",
  "createTime": 1782660716201,
  "updateTime": 1784437771353,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/appRoleList",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"current\": 1,\n    \"size\": 10\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按应用标识及可选用户标识查询当前租户角色，补充角色与菜单的展示资料后返回。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.应用角色查询
// 路由：POST /sym/role/appRoleList
// 业务说明：按应用标识及可选用户标识查询当前租户角色，补充角色与菜单的展示资料后返回。
import tenantAccounts
import log
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.NumberUtil
import java.util.List
import com.linewell.dataelement.utils.MenuTreeUtil
import cn.hutool.json.JSONUtil
import com.linewell.dataelement.model.Menu



try{
  tenantAccounts.session();
}catch(e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";  
}

// 没有入参就注销退出登录
//     exit 400, 'appId参数不能为空';
// }

//优先读取body中的参数
var appId = body?.appId || tenantAccounts.session().get("appId");
var userId = body?.userId || StpUtil.getLoginId();
//严格校验参数类型


var roleList = db.table("rm_role_t").where()
             .eq("app_id",appId)
             .select();

// 返回当前业务分支的标准结果。
return roleList
