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
