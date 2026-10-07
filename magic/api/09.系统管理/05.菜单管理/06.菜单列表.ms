{
  "properties" : { },
  "id" : "3b9d8682329c4311a28d5a343d19b91e",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "06.菜单列表",
  "createTime" : 1782660716338,
  "updateTime" : 1791088093993,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/all",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "权限管理默认查询当前租户全部菜单；明确传入 appId 时才按应用筛选；涉及 rm_menu_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：06.菜单列表
// 路由：POST /sym/menu/all
// 业务说明：权限管理默认查询当前租户全部菜单；明确传入 appId 时才按应用筛选；涉及 rm_menu_t 等业务表。
import tenantAccounts
import log
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.NumberUtil


try{
  tenantAccounts.session();
}catch(e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";  
}

//优先读取body中的参数
 // 权限管理默认查询当前租户全部菜单；明确传入 appId 时才按应用筛选。
var requestedAppId = body?.appId;

//先读取数据库，后面考虑读取缓存
var menuQuery = db.table("rm_menu_t").where().eq('deleted', 0)
if (requestedAppId != null && (requestedAppId + '').trim() != '') {
    menuQuery = menuQuery.eq("app_id", requestedAppId)
}
var menuList = menuQuery.orderBy('sortNum').select()
             
// 返回当前业务分支的标准结果。
return menuList
