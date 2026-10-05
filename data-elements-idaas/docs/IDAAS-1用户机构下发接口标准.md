# IDAAS/1 用户机构下发接口标准

> 当前接入暂停：2026-09-28 已撤回共享后端的租户示例接收接口及基础身份表。本标准保留为后续接入设计资料，不意味着接口当前可调用；用户目前要求只开发卓鉴平台。最新状态见 [租户改造回退与平台优先开发说明](租户改造回退与平台优先开发说明.md)。

版本：1，2026-09-28。发布方：卓鉴统一身份管理平台 `data-elements-idaas`。这是本工程的用户机构下发协议，不宣称是完整 SCIM 或单点登录协议。

## 接入步骤

接收系统实现一个 HTTPS POST 接口，在自己的系统中生成至少 32 个字符的随机服务密钥，并将 `keyId` 与一个固定目标租户绑定。由该系统自己的授权管理账号登记密钥和启停；平台用户 token 不能直接操作接收系统普通业务 API。随后在卓鉴“同步配置”登记目标租户、完整地址、相同 `keyId` 与密钥，测试连接后执行下发。

共享后端示例接收地址 `/sym/identity/provision/apply`，实现位于 Magic API 的“09.系统管理 → 10.身份下发 → 01.标准接收”。本地开发可以使用显式开启的 `http://localhost:8088`。跨部署接收方可以采用自己的框架认证与数据库连接，但必须遵守以下校验、版本与回执语义，业务接收逻辑仍应放 Magic API。

## 请求外层

Content-Type 为 `application/json`。`payload` 是 **JSON 文本字符串**，签名按这段字符串的原始 UTF-8 字节计算；不能先解析后重新序列化再验签。

```json
{
  "protocol": "IDAAS/1",
  "keyId": "ga-v1",
  "targetTenantId": "receiver-pinned-tenant",
  "timestamp": 1790000000,
  "nonce": "0123456789abcdef0123456789abcdef",
  "payload": "{\"eventId\":\"event-id\",\"issuer\":\"data-elements-idaas\",\"type\":\"PING\"}",
  "signature": "64位小写十六进制HMAC值"
}
```

时间是 UTC Unix 秒；允许与接收端时钟差不超过 300 秒。`nonce` 是 32 位十六进制随机数，签名是 64 位十六进制。资料字符串最多 65536 字符；接收端还应在网关限制整个请求体大小。

签名计算：密钥取接入时登记的随机字符串，算法 HMAC-SHA256。连接以下各行，使用 `\n`（LF），最后一行后没有额外换行：

```text
IDAAS/1
keyId
targetTenantId
timestamp十进制
nonce
payload原始字符串
```

先验证协议、登记密钥的启停、密钥固定租户、时间、随机数和签名，再读取/修改业务数据。共享实现的验签前数据源访问只允许读取指定租户登记凭据，不能读取业务人员或直接写数据。验签成功后所有业务归属均使用已验证服务上下文；不可把普通请求体租户当作业务权限。

## 资料事件

每个事件处理一个对象。公共字段：

| 字段 | 约束 |
| --- | --- |
| `eventId` | 不可变事件标识，1–32 位字母、数字、下划线或短横线 |
| `issuer` | 当前为 `data-elements-idaas` |
| `type` | `org`、`user`；连接检查为 `PING` |
| `objectId` | 中央稳定对象 ID，1–32 位同上字符；不直接当成本地用户 ID |
| `sourceVersion` | 1 起递增的整数，同一版本不应更改资料 |
| `action` | `UPSERT`、`DISABLE`、`REMOVE` |
| `record` | 机构或人员资料，禁止密码、初始密码、用户 token |

机构 `record`：

```json
{"name":"业务部门","code":"DEPT-001","parentId":null,"status":"enabled"}
```

`parentId` 是中央上级机构 ID，根机构为 null。接收方将中央 ID 映射到本地 ID。名称最长 200、编码最长 100 字符；上级必须先接收，不能形成循环。资料编码和本地层级编码分开；避免按名称合并现有机构。

人员 `record`：

```json
{
  "name":"人员姓名","account":"proposed.account",
  "email":"","phone":"","status":"enabled",
  "orgId":"central-org-id","post":"业务岗位",
  "appointments":[{"orgId":"central-org-id","post":"业务岗位","primary":true}]
}
```

人员名称最长 50，拟用账号 3–50 字符、以字母开头，后续为字母数字或 `_ . -`。邮箱最长 255、电话最长 128、岗位最长 100 字符；任职最多 20 个且启用人员恰好有一个主职。机构必须先接收。新本地账号没有密码、没有自动授予的角色。接收方另行设置本地凭据和角色。更新不得覆盖本地密码、本地停用开关及本地角色；替换任职时只处理对应中央来源关系。

`DISABLE` 与 `REMOVE` 在基础账号侧都关闭来源开关，保留本地业务引用和回执。`REMOVE` 表示中央归档，不要求物理删除本地用户。中央重新启用也不能清除本地停用。服务停用用户后应撤销其本地会话，不能仅更新中央页面。

## 回执

HTTP 200，Magic API 包装中 `code=0` 为成功，`data` 必须包含：

```json
{
  "code":0,"message":"success",
  "data":{
    "eventId":"event-id","tenantId":"receiver-pinned-tenant",
    "objectId":"central-object-id","localId":"local-object-id",
    "sourceVersion":1,"result":"APPLIED"
  }
}
```

结果 `APPLIED` 表示本地事务已提交；`DUPLICATE` 表示相同事件已处理，返回原本地映射；`STALE` 表示本地已有相同或更新来源版本，本次不覆盖。平台核对事件 ID、固定租户及结果枚举，不将任意 HTTP 200 当作下发成功。

同一 `(固定租户, issuer, eventId)` 保存资料摘要和持久回执，业务写入与回执必须在同一事务。相同事件但不同内容返回 `409`；签名/时间/目标错误返回 `403`；资料错误返回 `400`；依赖机构缺失或同名冲突返回 `409`。Magic 包装错误可能仍使用 HTTP 200，调用方必须读取 `code`。

PING 经过同样服务认证，但不修改对象，返回 `data: {protocol:"IDAAS/1",tenantId:"固定租户"}`。平台同时核对协议与租户。密钥停用后 PING 和普通下发均应拒绝。

回执不包含密钥、密码或用户 token。接收系统不要向响应输出异常堆栈或连接信息。错误必须使当前事件事务回滚，不写“已成功”的假回执。
