{
  "properties": {},
  "id": "6e2238e0fe8046b0b24420282879c43a",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "06.测试编排数据源",
  "createTime": 1784826967966,
  "updateTime": 1784826967966,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources/test",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"type\":\"jdbc\",\"config\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/demo\",\"username\":\"root\",\"password\":\"123456\"}}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "测试 JDBC、Kafka、ES、HTTP、Redis 连接。请求示例：{\"type\":\"jdbc\",\"config\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/demo\",\"username\":\"root\",\"password\":\"123456\"}}。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.测试编排数据源
// 路由：POST /dws/flowserve/datasources/test
// 业务说明：测试 JDBC、Kafka、ES、HTTP、Redis 连接。请求示例：{"type":"jdbc","config":{"url":"jdbc:mysql://127.0.0.1:3306/demo","username":"root","password":"123456"}}。
// 测试编排数据源：处理 POST /datasources/test 的核心查询和组装返回数据逻辑。
import flowDataSource
return flowDataSource.test(body.type,body.config)
