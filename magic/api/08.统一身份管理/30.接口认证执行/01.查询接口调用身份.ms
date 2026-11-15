{
  "properties": {},
  "id": "8733f3234268544bbe84d00927f55e99",
  "script": null,
  "groupId": "69d8099e140554c19b063ba451355aad",
  "name": "01.查询接口调用身份",
  "createTime": 1790636675438,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/whoami",
  "description": "校验 Bearer 令牌的签名、受众、应用权限和撤销状态，返回当前接口调用身份及有效授权。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.查询接口调用身份
// 路由：GET /idaas/api/whoami
// 业务说明：校验 Bearer 令牌的签名、受众、应用权限和撤销状态，返回当前接口调用身份及有效授权。
import identityRuntime;
import identityApi;
import request;
import org.slf4j.LoggerFactory;
import '@/idaas/api-call' as apiCall;

// Executable JWT integration contract. appId must match the token audience;
// permissions and revocation are recalculated on the server for this real path.
return identityRuntime.control(()=>apiCall(()=>{
    try {
        var values=request.getValues('appId');
        return identityApi.authorize(values==null || values.size()!=1?null:values[0]);
    }
    catch(e) {
        LoggerFactory.getLogger('idaas.protocol').warn('API integration rejected: '+e.getClass().getSimpleName());
        exit 403,'访问令牌或当前接口授权无效';
    }
}));
