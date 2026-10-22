# 澄天数据中台（data-elements-chengtian）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。工程 README 仅保留入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](../db/README.md)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 目录

- [工程概览与使用](#overview)
- [业务演示数据边界](#police-demo)
- [公安资源复制到澄天](#police-resource-import)

<a id="overview"></a>

## 工程概览与使用

`data-elements-chengtian` 是澄天数据中台的 Vue 前端工程，面向管理、配置、流程操作、低代码表单和数据要素业务场景，基于 Vue 3、Vite 7、TypeScript、Element Plus、Pinia、UnoCSS、VXE Table 和 form-create 构建。


<a id="overview--功能概览"></a>

### 功能概览

- 登录、权限、动态菜单和动态路由。
- 数据要素业务页面与后台管理页面。
- 动态组件与低代码表单运行能力。
- 字典、WebSocket、全局请求、文件上传、数据表格等基础能力。
- 支持本地代理后端接口和本地 Mock。


<a id="overview--技术栈"></a>

### 技术栈

- Vue 3 + TypeScript
- Vite 7
- Element Plus
- Pinia + Vue Router
- UnoCSS + SCSS
- VXE Table / vxe-pc-ui
- form-create
- ECharts / AntV / Ace Editor


<a id="overview--环境要求"></a>

### 环境要求

- Node.js：`^20.19.0 || >=22.12.0`
- 包管理器：pnpm
- 后端服务：开发环境通过 `/dev-api` 代理访问


<a id="overview--本地启动"></a>

### 本地启动

```bash
cd data-elements-chengtian
npm install -g pnpm
pnpm install --frozen-lockfile
pnpm run dev
```

默认端口由 `.env.development` 的 `VITE_APP_PORT` 控制，当前为 `3000`。


<a id="overview--构建命令"></a>

### 构建命令

```bash
# 常规生产构建
pnpm run build

# 严格类型检查 + 生产构建
pnpm run build:check

# 仅执行 Vite 构建
pnpm run build-only

# 单独执行类型检查
pnpm run type-check
```

说明：当前项目存在一批历史类型声明问题，因此日常部署建议使用 `pnpm run build`；需要集中治理类型问题时使用 `pnpm run build:check` 或 `pnpm run type-check`。


<a id="overview--常用环境变量"></a>

### 常用环境变量

```env
VITE_APP_PORT=3000
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://192.168.175.86:8088
VITE_APP_WS_ENDPOINT=
VITE_MOCK_DEV_SERVER=false
```

- `VITE_APP_BASE_API`：前端请求代理前缀。
- `VITE_APP_API_URL`：后端服务地址，开发环境由 Vite 代理转发。
- `VITE_APP_WS_ENDPOINT`：WebSocket 地址，未配置时跳过 WebSocket 初始化。
- `VITE_MOCK_DEV_SERVER`：是否启用本地 Mock。


<a id="overview--后端联调"></a>

### 后端联调

1. 启动后端 `data-elements`。
2. 修改 `.env.development`：

```env
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://localhost:8088
```

3. 启动前端后访问：

```text
http://localhost:3000/
```


<a id="overview--构建优化说明"></a>

### 构建优化说明

- 生产环境不加载 Vue DevTools。
- 主要第三方库已拆为独立 chunk：Vue、Element Plus、ECharts、AntV、VXE Table、ExcelJS、form-create、Ace Editor 等。
- 低代码设计器组件采用异步加载，减少首屏主包体积。


<a id="overview--部署建议"></a>

### 部署建议

```bash
pnpm run build
```

将 `dist/` 内容部署到 Nginx 或其他静态资源服务器，并配置后端接口反向代理。例如：

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /prod-api/ {
        proxy_pass http://backend-host:8088/;
    }
}
```


<a id="overview--常见问题"></a>

### 常见问题


<a id="overview--页面接口返回-no-static-resource"></a>

#### 页面接口返回 No static resource

通常说明后端 magic-api 脚本未加载或接口未注册，请检查后端 magic-api 工作目录、数据库资源配置或 Nacos 下发配置。


<a id="overview--动态组件或表单接口返回结构不是数组"></a>

#### 动态组件或表单接口返回结构不是数组

项目已兼容数组、`records`、`list`、`data` 等常见返回结构；若仍异常，请检查后端接口返回内容。


<a id="overview--类型检查失败"></a>

#### 类型检查失败

`pnpm run type-check` 当前可能暴露历史类型问题。日常部署使用 `pnpm run build`，类型治理时逐步修复相关文件。


<a id="overview--统一身份管理入口"></a>

### 统一身份管理入口

系统导航的“平台支撑 → 统一身份管理平台”对应 `data-elements-idaas`。开发入口为 `http://localhost:3005/#/console/workforce/overview`，生产默认前缀 `/idaas/`，可通过 `VITE_IDAAS_CENTER_URL` 覆盖为实际部署地址。点击在当前标签页打开，目标系统仍执行自身登录校验，不在 URL 中传递口令或令牌。

数据中台入口来自共享后端控制库中的低代码组件 `global-nav`，而不是本前端仓库内的静态菜单。历史工作副本路径 `data/working/global-nav.vue` 不在当前 Git 树内；维护时应从运行环境读取实际组件。统一身份管理平台的导航地址独立于 `capabilityCenterBases`，没有扩大数据中台会话桥的接收范围。

<a id="police-demo"></a>

## 业务演示数据边界

共享 Magic 目录保存平台功能接口，不混入固定值业务样例或自动生成的地区查询服务。原公安合成场景、业务实验台、复杂编排草稿及其生成工具已退役，不随初始化或资源发布恢复。功能验证使用隔离的明确测试对象，运行材料放忽略的 `logs/`。

“已发布服务”是服务编排的平台运行目录，保留目录和发布能力；后续真实服务必须经现用平台流程创建并关联其业务服务记录。清理按资源 ID 和生成来源限定，不能因名称含“演示”或“测试”删除真实资产。真实迁入资源及其独立接入目标继续按下一节维护。

<a id="police-resource-import"></a>

## 公安资源复制到澄天

该流程按用户授权，从当前运行绑定的公安业务库 `baseline_ga` 读取应用系统、数据源、表和字段、目录及数据项、接入任务与字段映射、逻辑模型、数据标准和应用配置，复制到澄天业务库 `chengtian`。固定来源租户为 `2084109831682699264`，固定目标为 `2084109831682699267`，不提供由请求参数选择任意租户的接口。


<a id="police-resource-import--导航与数据范围"></a>

### 导航与数据范围

澄天复用现有 `/datasource-manage/index` 和共享 `department-datasource` 组件，顶级菜单显示为“数据登记”，位于“数据盘点”右侧。只为已经拥有数据盘点入口的现有角色补齐这两个菜单记录的授权，继续启用菜单数据范围校验；不新增身份、角色、管理员标志或功能副本。

有效资源保持原业务名称，主键及关联标识按稳定命名空间重新生成，租户字段统一写入澄天。原组织仍存在于澄天时沿用；原组织缺失或未登记归属的导入记录由现有科信总队代管，不凭原库记录扩大授权范围。

已删除记录、布控、联调测试、无法关联有效数据源的表、无法关联有效来源表的目录和任务不作为有效业务资源导入。运行监控、执行历史、人工审批、旧流程组和不可变发布计划不冒充新租户的运行结果。公安持续变化的运行计数与配置变更分开比较，配置变化仍会阻止写入。

此前澄天的合成演示资源及现有业务数据保留。该复制过程不会把源租户的账号、角色、成员关系或 API 授权同步过来；必要的数据源连接设置仅保存在受限运行库与仓库外快照中，不进入 Git、日志或 ES 投影。


<a id="police-resource-import--接入目标与运行状态"></a>

### 接入目标与运行状态

复制的接入任务初始为未启动配置；复制的 NiFi 加工图初始为 `SAVED`，公安的流程组标识、部署结果、队列和运行计数全部清除。

已关联的目标表在 `ct_police_copy_targets` 中创建，目标连接属于澄天。加工图的 JDBC 写入节点重绑这些独立目标，不能通过复制结果停止、重部署或写入公安原任务的目标。原目标表也是下一段接入的来源时，下一段来源连接一并指向对应的实验表。

下一段来源迁入MySQL实验库时，`registeredDatasourceId`、`selectedDatabaseId`、`sourceDbId`必须一起指向实验数据源；物理表名和`sourceTableName`一起更新，来源组件采用`source.mysql`，移除旧厂商驱动及owner等连接属性。保留节点标识、位置、连线、过滤及增量配置。仅更新可见连接选项会留下旧达梦/Oracle来源标识，组合链路会因画布与任务记录不一致退回单任务视图，不能把该提示解释成NiFi断线或物理数据丢失。

对原目录中缺少挂接目标的有效表目录，预览指向已有来源表；任务缺少来源目录时补齐来源目录和数据项。数据源、表、目录和属性表中的关联采用同一组新标识。

“复制配置”不代表外部 Oracle、FTP、API、Hive 等网络环境均已恢复连接，也不把旧租户的运行成功复制为新租户成功。启动任务前仍需使用现行页面核对连接、字段映射、调度和实验目标；原有已验证的澄天合成接入任务不受影响。


<a id="police-resource-import--工具与验证"></a>

### 工具与验证

维护工具为根目录 `tools/copy-police-resources.py`，默认只读计划；通用连接能力由 `tools/chengtian-maintenance.py` 提供。准备迁入前必须已有独立目标连接，工具不再从合成数据源复制配置。分阶段入口为 `plan`、`menu`、`prepare`、`apply`、`index` 和 `verify`；`reprepare` 仅从仓库外已准备快照重新生成候选内容，写入前仍须重新比较来源配置。

已迁入批次的来源兼容使用`repair-chain`只读规划，再以`repair-chain --apply`执行所选批次；`--pipeline-id`可重复指定当前核对的画布，所选ID必须属于受限复制快照。只处理至多100个明确画布，批量读取当前任务/登记关联并一次校验全部数据源的当前角色范围。仅当画布仍与原复制快照一致、保持SAVED且没有部署痕迹时补齐来源绑定；人工修改、已部署、跨租户、失效或无权引用均拒绝覆盖。执行前保存仓库外受限备份，按原DSL、版本时间及当前任务/登记关联进行原子并发比较，任一冲突整批回退。此操作不重建表、不写来源数据、不操作NiFi，也不在打开画布时隐式修复。

读取采用固定租户及每页 1,000 条的游标，限制单表和本批总记录数。关联预取后在内存中逐 ID 校验；写入按有限批次执行，不按列表行补查。首次写入前保存受限回退材料，目标 ID 已存在时不覆盖；中断后只允许核对并复用本批定义一致、仍为空的实验表。

ES 资产投影从当前澄天主表及白名单属性重新生成，按每批 200 条写入澄天独立索引。连接口令、令牌、连接池设置及治理快照不进入 ES；已退役的合成目录和业务明细不再进入索引。

日期属性统一转换为带时区的标准日期；无法解析的日期只从检索投影省略，数据库原值保留。登记、标注和连接状态沿用实际主表，不统一标记成功。资产投影不再附加合成业务索引和模拟标记。

超市列表直接使用现有 `tenantEs` 查询租户索引，不再先通过另一套客户端额外检查索引存在性；索引不存在时仍返回空列表，其余查询错误继续报告。收藏、购物车和申请状态保持按租户批量关联，页面不按行补查。

验收包括菜单位置与页面、真实澄天会话、导入资源关联、空的独立实验目标、未启动的新任务、20/100 行的请求与 SQL 数，以及无权或异租户 ID 的拒绝。运行材料位于根目录 `logs/police-copy/`，不提交 Git。


## 节点配置

后台管理的“节点配置”统一使用 `#/node-config`。原 `#/register/nifi-node-config` 和 `#/register/node-config` 保留跳转，菜单权限与低代码组件标识 `nifi-node-config` 不变。

抽取、推送节点的行内保留连通检测及适用的同步配置操作，编辑和删除收进“更多”，不提供复制菜单。推送节点不展示底层工程名称，编辑说明、连通检测与同步提示使用中文业务名称。

页面分为数据抽取节点、数据推送节点和数据服务节点。抽取节点继续使用 `nifi_node_t`；推送节点使用独立的 `push_node_t`，按当前租户的所属网络字典分组，不混入 NiFi 任务的节点选项。三个网络的示例地址仅供配置参考；没有部署的节点应停用，启用前先替换真实服务地址并执行连通检测。

推送节点左侧图标显示最近一次连通检测结果：绿色可连通、红色不可连通、灰色尚未检测。鼠标悬停或键盘聚焦会显示状态说明、检测时间和耗时。检测由用户点击触发，列表不自动逐节点探测；推送节点检测结果保存在当前后端进程缓存，刷新页面可回显，服务重启或节点地址变更后恢复灰色。启用开关与连通状态分别显示，启用不等于可连通。

推送节点的“同步配置”直接打开确认框。确认后，服务端批量读取当前租户、当前角色可见的全部推送数据源，以一个 `REGISTRY_SYNC` 请求同步到所选节点。数据源登记未完成、推送停用、没有已登记表或字段不完整时，结果列明跳过的数据源和原因。同步完成显示实际数据源、表、字段数量；取消确认不发送同步请求。只同步登记配置，不发送业务记录，也不生成或轮换业务密钥。目标地址取自当前租户已保存且启用的节点；请求不能指定地址、租户或凭据。

`POST /dst/database/push/sync` 接受 `{all:true,nodeId}`，加 `dryRun:true` 可只读检查；原单数据源 `datasourceId` 调用保持兼容。批量最多100个推送数据源、1000张表（每源100张）、10000字段和1MiB，超限整体拒绝，避免截断快照。节点列表20/100行均为2次SQL读取；全量同步20/100数据源均为预检9次/正式10次业务SQL读取和1次节点请求，角色计算固定开销另计。海通批量接收端先校验整个批次，只读取一次幂等账本，然后在同一事务中保存全部镜像。逐数据源角色校验、租户过滤、节点归属及启用检查均保留。
