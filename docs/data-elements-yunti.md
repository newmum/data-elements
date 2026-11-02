# 云梯应用设计器（data-elements-yunti）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。仓库仅保留根目录 README 总入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](data-elements.md#database-schema)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 目录

- [工程概览与使用](#overview)

<a id="overview"></a>

## 工程概览与使用

本工程仅保留 React 可视化编排前端，后端由 `data-elements-parent` 提供。
共享 Magic 资源以根目录 `magic/api/06.数据服务/01.服务编排` 的当前源码为准。
前端声明的接口不能作为后端已实现、已发布的依据。


<a id="overview--当前架构"></a>

### 当前架构

- 前端：React 19、TypeScript、Vite、Ant Design、AntV X6。
- 唯一后端：`data-elements`，默认地址 `http://127.0.0.1:8088`。
- 管理接口：`/dws/flowserve/**`。
- 已发布服务：`/dws/flowserve/published/**`。
- 运行时管理适配：`/api/v1/runtime/**`。
- Magic 在线编辑器：`/api/web/`。
- Magic 资源主表：`api_file_t`。
- Magic 资源备份表：`api_backup_t`。

旧 `chengtian` 后端源码及发布配置已删除，本工程不包含独立后端。


<a id="overview--本地开发"></a>

### 本地开发

先启动 `data-elements`，再启动本前端：

```bash
pnpm install
pnpm dev
```

开发服务默认监听 `http://localhost:3030`；端口占用时 Vite 会顺延。
主平台“数据服务 → 编排”会验证画布应用标识并识别 3030–3034（兼容旧 6174–6178）端口。
嵌入画布先与主平台通过明确来源的 `postMessage` 初始化登录会话，再读取流程；登录令牌不放入 URL，也不在云梯端持久化。

#### 打开编排与页面状态

点击服务列表的“编排”时，主平台先确认云梯应用地址，再调用 `POST /dws/flowserve/gateway/orchestration/open`，请求只需 `apiId`。服务ID与流程ID分别处理，不能在没有流程关联时直接把服务ID当作已存在的流程打开。接口从当前认证租户读取服务，按既有 ALL／ORG／OWNER 范围逐ID核验；锁定服务行后复用原流程，只有尚未关联流程的服务才初始化“API触发器 → 响应”草稿并绑定。重复打开复用同一草稿；损坏或已删除的原关联返回明确错误，不能自动覆盖。`dryRun=true`只预检，不创建草稿。没有结构变更，也不批量给列表创建流程。

主平台从地址检查开始只显示一套“请求 → 编排 → 响应”加载画面，iframe在加载时保留布局但暂不展示；会话就绪消息不会提前关闭遮罩。云梯在图形装载并完成首次适配后发送 `YUNTI_EDITOR_READY`，主平台核对当前iframe来源、origin和本次 `entryId` 后一次切换到画布。`YUNTI_EDITOR_ERROR`采用同样校验，旧打开尝试的迟到消息不改变当前页面。会话初始化、路由懒加载、独立打开及路由错误使用同风格状态组件。

404、无权访问／登录失效、流程内容冲突、读取超时及网络错误均提供与加载画面一致的提示页，显示重试、返回数据服务和可展开的脱敏详情。不存在的流程不自动重试；仅对暂时性读取失败重试一次。动画遵守减少动态效果设置，不展示虚构进度。前端启动成功只证明页面可访问，不能证明服务已关联流程或后端读取成功。

列表20/100行不增加探测或流程补查；一次主动打开只有一次有界地址探测（默认端口可用时）和一次打开请求。打开接口在既有范围解析之外执行2条业务读取，无逐行查询；流程与运行节点各1次初始化读取。运行节点20/100项仍只执行1条有界SQL，不逐节点探测。

Vite 会将 `/dws`、`/api` 和 `/magic` 代理到 `http://127.0.0.1:8088`。
嵌入画布通过主平台初始化的 `token` 请求头访问后端；主平台未勾选“记住密码”时也支持会话登录。

打开画布所需的 `GET /dws/flowserve/flows/{id}` 和 `GET /dws/flowserve/runtime-nodes`
由后端 `FlowServeCanvasReadController` 提供。流程详情在当前租户库中一次查询，
并通过关联服务记录执行与服务列表相同的人员、部门数据范围校验。
运行节点一次读取最多 100 项，不返回部署密钥，也不逐节点发起健康检查。
这两个接口只负责初始化读取；保存、调试、发布和版本管理须另行核对当前后端实现，不能从画布已能打开推断其可用。

常用命令：

```bash
pnpm test
pnpm typecheck
pnpm build
```


<a id="overview--生产构建"></a>

### 生产构建

```bash
pnpm build --base=/yunti/
```

默认生产接口前缀由 `.env.production` 提供：

```text
VITE_API_BASE=/yunti/dws/flowserve
```

如果网关直接暴露后端 `/dws`，可在构建时改为
`VITE_API_BASE=/dws/flowserve`。


<a id="overview--数据和配置归属"></a>

### 数据和配置归属

业务数据与主平台共用当前业务库。数据源不再由 Yunti 单独维护，编排器直接读取
主平台 `db_datasource_t` 的 `/dst/database/list` 接口，新增、修改、测试和删除统一
回到主平台“数据源管理”完成。

FlowServe 自有业务表仅包括：

- `fs_flow`
- `fs_flow_version`
- `fs_publish_record`
- `fs_runtime_node`
- `fs_audit_log`
- `fs_call_log`
- `fs_alert_rule`
- `fs_alert_event`

Nacos 统一使用 `data-element` 命名空间的 `data-element.yml`，其中配置
Magic 存储表、运行时地址、密钥和白名单。
