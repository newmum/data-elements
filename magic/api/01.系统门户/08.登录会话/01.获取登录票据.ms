{
  "properties": {},
  "id": "9ea20d0b193b46d5988a997fa522965f",
  "script": null,
  "groupId": "652292437ed443acba737511e3597522",
  "name": "01.获取登录票据",
  "createTime": 1782660716313,
  "updateTime": 1784437771366,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getTicket",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验当前登录会话，为已登录用户生成业务系统可使用的短期登录票据并返回。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.获取登录票据
// 路由：GET /portal/sso/getTicket
// 业务说明：核验当前登录会话，为已登录用户生成业务系统可使用的短期登录票据并返回。
import tenantAccounts
import cn.hutool.core.util.StrUtil
import cn.dev33.satoken.stp.StpUtil;
import jsons
import '@/common/configData' as configData
import systemConfig
import http
import cn.hutool.crypto.digest.DigestUtil



configData().minio_url
var name = tenantAccounts.session().get("name")
var pwd =  tenantAccounts.session().get("pwd")

url = `${configData().ssoServerUrl}/oauth/token?client_id=${configData().client_id}&client_secret=${systemConfig.legacySsoClientSecret()}&grant_type=password&username=${name}&password=${pwd}&scope=all`
tokenInfo =  http.connect(url)
    .post()
    .getBody()

if(tokenInfo.access_token == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,"获取token令牌报错"
}
//获取ticket    
url = `${configData().ssoServerUrl}/ticket/getTicket?access_token=${tokenInfo.access_token}`
ticketInfo = http.connect(url)
    .get()
    .getBody()

if(ticketInfo.data == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,"获取ticket报错"
}

// 返回当前业务分支的标准结果。
return ticketInfo.data
