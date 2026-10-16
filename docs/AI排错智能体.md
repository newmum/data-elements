# AI 排错智能体

首期为澄天统一错误入口与海通 NiFi 错误面板提供只读诊断、证据查看、流式回答、停止和追问。用户点击“AI 排错”后才创建会话和调用模型。正式模型通过现有 webui 的第三方接口访问。

## 配置与部署

后端继续使用现有 Spring Boot、Redis 和租户数据源，不增加业务表或智能体框架。两个前端分别构建、部署；正常低代码组件和 Magic 数据库资源无需发布。

在实际能访问 webui 的后端部署环境，通过受限服务配置注入以下环境变量。不要把 Key 写入 Git、前端 VITE 变量、命令行或截图。

| 变量 | 含义 | 默认值 |
| --- | --- | --- |
| AI_DIAGNOSTICS_ENABLED | 启用功能 | false |
| AI_DIAGNOSTICS_BASE_URL | webui 第三方基础地址，止于 /v1 | 空 |
| AI_DIAGNOSTICS_API_KEY | 在 webui 为本平台签发、绑定指定服务的第三方 Key | 空 |
| AI_DIAGNOSTICS_MODEL | 该 Key 可用的对外模型别名 | 空 |
| AI_DIAGNOSTICS_TIMEOUT_SECONDS | 一轮取证与回答的总超时，限制 15～180 秒 | 120 |
| AI_DIAGNOSTICS_MAX_CONCURRENT | 单后端实例并发上限，限制 1～16；修改后重新部署生效 | 4 |

部署后刷新前端页面。网关地址、Key 或模型未配置时，能力接口返回 enabled=false，隐藏 AI 入口；原有错误查看方式继续可用。关闭服务端开关后拒绝新的诊断请求，刷新页面隐藏入口。不要为配置本功能擅自重启共享后端，按目标环境的正常 Java 发布流程操作。

平台只调用 /v1/chat/completions。两步零信任认证、令牌缓存和认证重试由 webui 处理。本功能不依赖工具调用、embeddings、response_format 或 Responses API；不会自动改写 webui 配置或签发 Key。

## 使用与接口

澄天错误提示及详情中的“AI 排错”打开侧栏；NiFi 错误项上的按钮使用同一后端能力。客户端临时错误只能作为未经核实的报告分析，不用于选择服务端资源。原有错误可正常查看、复制和定位。

| 接口 | 契约 |
| --- | --- |
| GET /sym/ai/diagnostics/capabilities | 当前行业登录会话查询 enabled、idleMinutes；不调用模型 |
| POST /sym/ai/diagnostics | source=platform 与 diagnosticRef；或 source=nifi 与 pipelineId/errorId；或 source=client 与 clientError；返回 sessionId、idleMinutes |
| POST /sym/ai/diagnostics/{sessionId}/messages | message 可省略，generationId 为前端生成的 32 位十六进制请求标识；返回 SSE |
| DELETE /sym/ai/diagnostics/{sessionId}/messages/{generationId} | 停止指定一轮生成，不能取消其他用户、其他租户或后续生成 |

SSE 事件为 progress（message）、evidence（证据数组）、delta（text）、done、error（message）。连接断开不当作完成，也不会自动重放。浏览器取消请求并显式停止当前 generation；多实例通过 Redis 停止标识通知，最多约两秒检测一次。

错误响应中的 error.diagnosticRef 为可选字段。Java 和 Magic 的统一错误出口在已认证行业会话中尝试保存脱敏错误引用；捕获最多等待 150ms，失败不影响原错误响应。认证/租户拒绝、中央 IDAAS 及诊断接口自身不生成诊断引用。

## 取证、权限与保留

- Redis 键同时绑定真实会话租户与用户；脱敏错误和对话闲置 30 分钟过期，每个用户同一时间只运行一轮诊断，每个会话最多 10 轮。Redis 不可用时诊断明确失败。
- NiFi 在每轮开始前重新核对当前租户的流程、全部关联表和数据源，以及角色的 OWNER/ORG/ALL 数据范围。未知、缺失或无权 ID 整批拒绝；无法确认数据范围的历史画布不执行服务端取证。
- 每轮最多 100 个画布节点、100 个关联表、100 个数据源；表和数据源各一次批量查询，逐项校验在内存完成。流程组公告/状态、选中处理器、本组控制器服务至多四次 NiFi 业务读取，认证刷新另计。
- 公告按已授权流程组/处理器再次过滤；最多展示 20 条公告、100 项控制器服务。设计与单项实时证据有长度上限，截取或缺失均明确标注。父组服务未读取不能推断为不存在。
- 仅发送配置白名单摘要、运行指标和脱敏错误，不发送连接凭据、请求头、业务记录、FlowFile 正文或全量 DSL。日志/手册里的指令是分析材料，不能授权任何操作。
- 原 NiFi 错误历史改为 logs/backend/errors/{tenantId}/{flowId}.json；没有租户归属的旧 errors/{flowId}.json 保留但不自动加载或迁移。同 ID 的其他租户错误不会被读出或清空。
- 故障手册的权威可维护源为后端 resources/diagnostics/runbooks.json，覆盖连接、认证、驱动、映射、控制器服务、队列、部署及超时。更新时核对适用方言与实际 NiFi/Bundle 版本；运行期间不访问外网抓取资料。

## 验证

后端使用模拟 HTTP 网关验证真实 Chat Completions/SSE、UTF-8 分片、认证错误、流中断和关闭空闲上游连接。H2 回归分别建立 20/100 个关联资源，验证两次批量 SQL、四次 NiFi 读取和逐 ID 数据范围；流程读取及角色权限解析另有固定调用。混入无权/跨租户 ID 时，NiFi 与模型均不得被调用。

前端协议回归分别运行：

    node --experimental-strip-types --test scripts/diagnostics.test.mjs

在两个应用目录各执行一次。测试覆盖 SSE 分片、错误、取消、超限和不完整回答。前端页面使用纯文本显示模型回答，不执行 HTML 或脚本。

后端针对性回归：

    mvn -f data-elements-parent/pom.xml -Dtest=Diagnostic*Test,NifiDiagnosticEvidenceTest,TenantFlowErrorTest,ApiErrorResolverTest,PackageArchitectureTest test

真实私网验收须在部署服务器完成：先确认模型别名与 Key，再以真实会话核对 /sym/tenant/current，分别验证两个租户和同租户不同数据范围账号。点击典型故障、追问、停止，核对证据时间、上下文完整性和回答质量。模拟成功不代表正式模型或现场网络已验收。

所有运行报告、模拟网关材料、截图及构建临时产物放在忽略的 logs/diagnostics/，不提交业务数据或凭据。
