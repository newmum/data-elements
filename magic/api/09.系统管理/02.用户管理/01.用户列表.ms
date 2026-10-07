{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "99198f1d6e154a938f9fd64542c52834",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "01.用户列表",
  "createTime": 1782660716529,
  "updateTime": 1785920400000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；读取或计算后返回业务结果。"
}
================================
// 接口：01.用户列表
// 路由：GET /sym/user/list
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；读取或计算后返回业务结果。

import '@/tenant-account/users' as localUsers
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime
// 用户列表：处理 GET /list 的核心查询和组装返回数据逻辑。

try {
    tenantAccounts.session()
} catch (e) {
    exit -1, "账号未登录或登录已过期"
}

return localUsers('list',{userName:userName})
