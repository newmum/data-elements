{
  "properties": {},
  "id": "metadata_nifi_connection_test_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "02.统一连接测试(NiFi兼容)",
  "createTime": 1783639496542,
  "updateTime": 1784437772000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/connection-test",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"manifestKey\": \"source.mysql\",\n    \"config\": {\n        \"host\": \"127.0.0.1\",\n        \"port\": 3306,\n        \"database\": \"test\",\n        \"username\": \"root\",\n        \"password\": \"***\"\n    }\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "兼容 data-element-nifi 现有入参：{ manifestKey, config }。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.统一连接测试(NiFi兼容)
// 路由：POST /dst/database/metadata/connection-test
// 业务说明：兼容 data-element-nifi 现有入参：{ manifestKey, config }。
import com.linewell.dataelement.integrated.nifi.canvas.controller.ConnectionTestController as connectionTestController

// 兼容 data-element-nifi 现有入参：{ manifestKey, config }
// 返回 { success, url, product, version, elapsedMs, error }
var response = connectionTestController.test(body)
// 返回当前业务分支的标准结果。
return response.getBody()
