{
  "id": "haitong-ftp-diagnostics-probe-01",
  "groupId": "9310c3b9b222506b84a54ce10f0061ec",
  "name": "01.测试文件传输与写入",
  "path": "/probe",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员可调用。使用 application.yml 的 FTP 配置测试连接、逐级进入或创建目录、上传/回读/删除随机测试文件。不会接受外部 FTP 地址、账号或密码。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import ftpDiagnostics
import request

// POST /ftp/connect/probe。仅管理员可检查服务端配置的 FTP。默认只读，显式启用 createDirectories 或 uploadTest 才写入。

// 使用当前 HTTP 请求校验管理员身份，再按配置连接或写入目标目录。
var httpRequest = request.get().getRequest()
return ftpDiagnostics.probe(body == null ? {} : body, httpRequest)
