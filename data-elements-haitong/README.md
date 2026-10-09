# 海通数据集成中心

海通任务工作台和 data-element-nifi 画布合入同一个 React 应用。全屏 NiFi 画布恢复原工程的组件选择方式、节点尺寸和画布布局，保留节点配置、字段映射、SQL、校验、保存、部署及监控。海通工作台的导航和主题样式保持独立，画布采用原 NiFi 工程的浅色样式。

NiFi 原工程的迁入基线固定为 2026-09-29 14:42:43 的 `c122f605`；之后新增的结构化数据布控功能未迁入，见 [NiFi 画布迁入基线](doc/nifi-source-baseline.md)。

集成任务、NiFi 流程、监控、节点管理和对账等已接入的数据来自当前登录租户的共享后端；任务登记等尚无等价后端契约的演示能力继续保留在独立的浏览器本地工作区，不会冒充为真实任务或运行结果。真实页面不依赖 IndexedDB 是否可用。

## 安装和启动

要求 Node.js 22.12+ 和 pnpm 12.6.0。在本目录运行：

```bash
pnpm install --frozen-lockfile
pnpm run deps:check
pnpm run typecheck
pnpm test
pnpm run test:nifi
pnpm run build
pnpm run dev
```

浏览器打开 `http://localhost:3002/#/integration/overview`。这个端口和入口与数据中台“数据集成中心”菜单一致；跨端口登录态通过平台现有的 `capability-session.html` 获取，不在地址栏传递 Token。生产环境使用同源 `/haitong/` 入口；如平台地址不同，构建时配置 `VITE_SOURCE_PLATFORM_HOME_URL`。项目沿用 React 18、Ant Design 5、X6 2、TypeScript 5 和 Vite 5；NiFi 画布直接复用同一安装树中的版本。依赖风险预检由 `predev` 和 `prebuild` 执行。原始依赖清单与历史检查记录保存在 `doc/`，其中较早的“无法构建”说明已不是当前集成版状态。

左上角的系统切换入口与万象使用同一组中心地址约定。治理、资源和资产中心使用 `VITE_WANXIANG_GOVERNANCE_URL`（开发默认 `http://localhost:3010/`，生产默认 `/wanxiang-governance/`）；启智等中心使用 `VITE_QIZHI_BASE_URL`（开发默认 `http://localhost:3001/`，生产默认 `/qizhi/`）。云图、云搜和统一身份管理平台分别可由 `VITE_REPORT_APP_URL`、`VITE_SEARCH_APP_URL`、`VITE_IDAAS_CENTER_URL` 覆盖。切换在当前标签页进行，不传递会话令牌。

## NiFi 流程使用方式

生产构建的 JavaScript 和 CSS 目标均为 Chrome 109。画布的数据源选择框挂载在当前主题工作区，避开图形画布的裁剪容器；空间不足时双向移动，并限制列表高度。不要只更新控制库低代码组件来部署这类静态画布修复，需更新 `/haitong/` 下的完整构建目录，并让浏览器重新加载入口和资源。

1. 从左侧进入“NiFi 流程”，在列表中打开真实流程，或点“新建流程”进入空白画布。
2. 画布按原 NiFi 工程的方式添加组件；节点配置、映射、SQL、保存和部署沿用原有实现。海通合并时新增的常驻组件栏、紧凑节点和顶部小导航已撤回。
3. 从“集成任务”列表打开真实接入任务时，使用后端返回的 `pipelineId` / `accessTaskId` 选择流程，不把本地任务 ID 误当作后端流程 ID。NiFi 的真实部署和运行状态在画布中查看。
4. 无平台登录态时页面提示回到数据中台登录；登录后自动同步会话，不要求手工填写 Token。

画布路由支持 `#/development/canvas?pipelineId=<真实流程ID>`，也兼容原来的 `jobId`、`accessTaskId` 和 `tid` 选择参数；旧嵌入地址 `#/?communication=postMessage&tid=...` 会转入新画布。`?task=<本地任务ID>` 只用于查找本地关联，绝不作为后端流程 ID 发送。流程读取和授权仍由后端根据当前会话判断。

生产构建和预览均以 `/haitong/` 为静态资源基址。网关应在 `/haitong/` 提供本工程的 `dist/index.html`，并在 `/haitong/assets/`、`/haitong/brand/` 提供对应文件。可直接访问 `/haitong/#/integration/overview`、`/haitong/#/development/tasks` 或 `/haitong/#/development/canvas?pipelineId=...`；Hash 路由刷新仍请求同一入口。也支持把 `/haitong/canvas?pipelineId=...` 作为直接入口，页面启动时会将它规范到 `/haitong/#/development/canvas?pipelineId=...`。若使用这个无 `#` 的入口，Nginx 还需将 `/haitong/canvas` 回退到 `/haitong/index.html`（例如在现有 `/haitong/` 静态目录规则内配置 `try_files $uri $uri/ /haitong/index.html;`）。旧地址若把 `tid`、`accessTaskId` 或 `jobId` 放在 `#` 前的查询串，首次加载会迁移到 Hash 路由并从外层查询串移除，避免后续在应用内新建流程时误打开旧流程。登录 Token 会由 NiFi bridge 单独从 URL 清除。

原“任务登记”页面仍识别本地 Flow JSON 契约（`nodes / edges`），它不是 NiFi JSON 导入器。批量创建页只对后端已物化、唯一匹配且尚未创建任务的表调用真实接入任务接口；无法满足条件的项目说明原因。其他尚无等价接口的页面会明确提示缺口。

现有接口与库表复用情况、仍需补充的多表、分发、跨网、核销及批量作业契约见 [后端对接与剩余契约](doc/backend-integration-gaps.md)。

## 会话和后端连接

NiFi 与业务接口使用数据平台的登录会话。独立打开海通时，会从同源平台存储或跨源的 `capability-session.html` 安全桥获取当前会话，并在内存中提供给 NiFi 客户端；嵌入场景继续支持原 `postMessage` 的 `SET_TOKEN`、`INIT` 协议。画布不提供手工输入 Token 的表单，Token 不写入 URL、海通 IndexedDB 或工作区备份。会话失效时回到平台登录后可重新进入。

嵌入消息只接受实际父窗口，且来源必须为海通同源、数据中台来源，或部署时显式配置的 `VITE_NIFI_EMBED_ORIGINS` / `iframe-allowed-origins`。独立页面忽略外部窗口发来的 NiFi Token 消息。

本地开发代理见 `vite.config.ts`：`/nifi/api`、`/nifi-api`、`/nifi-ui` 代理到 `http://localhost:8088`，`/dev-api` 也转向同一服务并去掉前缀。NiFi API 默认基址是 `/nifi/api`，可用 `VITE_APP_BASE_API` 覆盖。生产构建的 `.env.production` 使用 `/prod-api/nifi/api`；部署网关需要把该路径及原生 NiFi 页面路径正确代理到现有后端，并提供页面同源访问。前端请求携带当前 token，租户及资源权限由后端校验。仅启动前端不会使后端接口可用。

## 原海通本地工作区

原工作区使用 IndexedDB `haitong-local-workspace-v2`，首次打开会建立任务、数据源说明、本地表和集群样例。样例表内容是虚构数据，不是生产数据库记录。本地运行器在浏览器页面打开时推进，历史运行、账单和工作区备份沿用原行为。清除站点存储会丢失本地修改；备份不应包含真实凭据或敏感数据。

NiFi 流程本身不保存在 IndexedDB；本地工作区只保存可选的 `nifiPipelineId` 关联。若从另一浏览器、另一租户或已删除的本地任务进入，仍应以 NiFi 后端流程列表为准。登录到不同租户时不能因为流程名称相同而复用其他租户的流程。

## 验证与结构

当前集成版已通过 `pnpm run typecheck`、`pnpm test`（66 项领域及会话测试）、`pnpm run test:nifi`（20 项 NiFi 专项测试）及 `pnpm run build`。构建可能提示大分包体积；该提示不代表构建失败。真实部署、特定数据库和生产网关仍须在目标环境联调验收。

```text
src/app/             路由、工作台上下文
src/layout/          海通应用壳与导航
src/pages/           本地任务、运维、对账及 NiFi 入口
src/nifi/            原 NiFi 画布、组件、配置、API 与运行逻辑
src/design/          海通主题与图标
src/domain/          本地任务类型、规则与模拟引擎
src/services/        IndexedDB 仓储与备份
src/styles/          海通布局和主题
```

历史设计、测试报告和依赖审查材料保留在 `doc/`；这些文档记录对应当时版本，不取代本页的集成版说明。
