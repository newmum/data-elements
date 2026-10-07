{
  "properties": {},
  "id": "d2798a4f10be4633ba12b75156e89fb5",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "03.登入配置",
  "createTime": 1782660713639,
  "updateTime": 1784798375116,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/loginConfig",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取当前租户登录页需要的公开配置，返回登录方式及可展示的系统资料。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.登入配置
// 路由：POST /portal/loginConfig
// 业务说明：读取当前租户登录页需要的公开配置，返回登录方式及可展示的系统资料。
 import '@/common/configData' as configData
import systemConfig
 import cn.dev33.satoken.stp.StpUtil
 import cn.hutool.core.util.URLUtil
 
 //判断是否走显性登入
if(configData().loginType == 0 || configData().loginType == 2){
    StpUtil.logout()
    // 返回当前业务分支的标准结果。
    return "/login"
}else if(configData().loginType == 1){
    StpUtil.logout()
    enUrl = URLUtil.encode(configData().redirect_uri)
    url = `${configData().ssoServerUrl}/oauth/authorize?client_id=${configData().client_id}&client_secret=${systemConfig.legacySsoClientSecret()}&response_type=code&redirect_uri=${enUrl}&scope=all`
    // 返回当前业务分支的标准结果。
    return url
}
