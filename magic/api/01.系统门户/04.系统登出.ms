{
  "properties": {},
  "id": "10def1f21f6e42769e696534efbb91a9",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "04.系统登出",
  "createTime": 1782660713650,
  "updateTime": 1784798379882,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logout",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "清理当前登录会话及相关票据，记录退出结果并返回登出状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.系统登出
// 路由：POST /portal/logout
// 业务说明：清理当前登录会话及相关票据，记录退出结果并返回登出状态。
import log
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.http.useragent.UserAgentUtil
import cn.hutool.http.useragent.UserAgent
import cn.dev33.satoken.secure.SaSecureUtil
import cn.hutool.core.util.IdUtil
import '@/common/configData' as configData
import http

//UserAgent ua = UserAgentUtil.parse(request.getHeaders("User-Agent")[0])

//广播登出
access_token = StpUtil.getTokenValue()//?.get("accessToken")

//判断是否走显性登入
if(configData().logoutType == 0 || configData().logoutType == 2){
    if(configData().logoutType == 2)
    {
        //调用统一隐形登出    
        url = `${configData().ssoServerUrl}/notice/logout?access_token=${access_token}`
        ticketInfo = http.connect(url)
            .post()
            .getBody()
    }
    StpUtil.logout()
    // 返回当前业务分支的标准结果。
    return "/login"
}else if(configData().logoutType == 1){
    //前端重定向退出
    StpUtil.logout()
    url = `${configData().ssoServerUrl}/notice/logout?access_token=${access_token}&redirect_uri=${configData().redirect_uri}`
    // 返回当前业务分支的标准结果。
    return url
}
