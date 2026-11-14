# 跨应用 Mock 设计与接手说明

本文按用户要求集中整理本次 Mock 的设计、固定要求、已完成内容和后续工作，供新的 Codex 窗口接手。后续直接更新本文件，不再生成按日期或阶段命名的交接副本。

长期协作约束以根 [AGENTS.md](../AGENTS.md) 和公共主文档的 [Mock 场景与接口契约同步](data-elements.md#mock-contracts)、[批量查询与分页规范](data-elements.md#batch-pagination) 为准。应用细节分别见 [澄天主文档](data-elements-chengtian.md)、[风驰主文档](data-elements-fengchi.md)。涉及真实 Magic、低代码发布时，必须使用当前环境的 `data-elements-lowcode` skill。

## 1. 新窗口先掌握的结论

1. 根 `mock/` 已建立，澄天的数据源闭环和风驰共享只读工作区已接入。普通保存、预检和结束登记的场景行为见 7.2。行业日志列表、详情、批量、统计及本地导出已接入统一 runtime。公共浏览器宿主已复用 `parseMockBody`／`mockResponse`，支持裸数组及原始 CSV／JSONL；Node 已使用实际 MSW 响应验证字节和响应头，日志整页和浏览器下载待验收。
2. 澄天仍使用 **3000**，风驰仍使用 **3020**。切换真实模式与 Mock 模式时停止同一端口的当前前端，再使用对应命令启动。不能为绕过端口占用改成 3006 等新端口。
3. 菜单、组织机构、行业及品牌配置来自实际数据库的公开配置白名单；数据源、表及预览记录是虚构业务样例。两类数据的来源和维护方式不同。
4. 风驰与澄天元数据平台使用同一组数据源和表 ID、同一字段定义、同一预览处理器以及同一份浏览器业务状态。风驰原独立模式的 `commerce_db` 不参与共享模式。
5. Mock 模式直接从根 `lowcode/` 编译组件；真实模式仍消费共享库中已发布的组件。读取本地源码和发布真实资源是两个独立动作。
6. **真实导航尚未发布。** 用户已明确要求同时发布真实导航，并表示会提供更新后的有效本地发布会话。源码合并、编译与 Mock 页面验证已完成，待有效会话后继续发布、刷新及运行回读；不能将构建成功当成已发布。
7. 本次没有完成真实 Java 迁 Magic，没有修改真实业务表结构，也没有将 Mock 业务数据写入实际租户库。

数据源与风驰原批次的验证日期为 2026-11-08；日志核心本批核对日期为 2026-11-13。新窗口必须先检查工作区、运行进程和资源比较结果，再继续写入；文中的端口、数量及发布状态不能替代现场核对。

## 2. 用户目标与固定要求

目标是先用本地数据完成前端业务调试和演示，再逐步优化真实接口。长期方向是各前端具有独立 Mock 开关；实施顺序已收敛为先完成澄天的一批功能，再按需求扩展，不一次性改造全部工程。

- 通过应用初始化和公共网络层切换，现有页面继续调用原接口，不给每个页面、每个请求增加一套 Mock 分支。
- 开启 Mock 后，已接入业务从根 `mock/` 及其专用浏览器状态读取。未覆盖或失败的业务请求明确拒绝，不回退真实后端；关闭 Mock 后使用正常服务器接口。
- 沿用登录页原有 Logo 五次连击和行业选择入口，不再增加“本地演示”悬浮框或另一套行业切换器。
- 配置 Mock 尽量按当前数据库实际值维护。特别是菜单的层级、隐藏状态和组织父子关系，不得为了演示方便重新设计。
- 菜单没有可见二级项时，Mock 也不能增加可见二级项。某租户配置为空时保留为空，不从公安或其他行业复制补齐。
- 业务演示使用丰富的行业样例，可以编辑并在浏览器保存；不能把真实密码、账号授权、token、连接信息或密钥导入 Mock。
- 澄天左上角 Logo 的产品导航可进入当前风驰，风驰提供返回元数据平台首页的入口，使用现有端口和配置地址。
- 风驰目录为“实际组织机构 → 数据源 → 表／视图 → 具体对象”，不插入澄天树中的应用系统层。相同能力优先复用元数据现有接口和数据口径。

## 3. 架构与数据来源

### 3.1 公共切换点

澄天 `src/main.ts` 先判断 `VITE_MOCK_ENABLED`，Mock 就绪后才加载正常 `bootstrap.ts`，避免设置、会话和路由在拦截器初始化前请求真实服务。`src/mock/browser.ts` 使用 MSW 统一处理业务 HTTP；`vite.config.ts` 在 Mock 模式关闭业务代理，并启用固定端口检查。

普通模式启动会注销本应用作用域内的 Mock Service Worker。Mock 的 UI 设置、记住密码、会话、广播和详情状态与真实模式隔离。普通生产模式禁止意外开启 Mock，演示构建使用显式 `build:mock`。

当前实际覆盖以 `mock/contracts/chengtian.json` 及测试为准。流式读取、上传下载、WebSocket、NiFi 或其他嵌入应用不能因 HTTP 拦截已启动就视为可用，必须单独补齐和验证。

NiFi 画布错误清理和流程部署尚未接入根 Mock，本批没有新增模拟执行入口。错误清理真实响应增加 `nativeCleared`，画布兼容旧204响应和原生清理失败后的本地显示回退；以真实8088接口、隔离流程和浏览器契约回归核验，不把界面清除提示当作原生告警删除或任务执行成功。

NiFi 启停、失败状态和流程组关联持久化共用内部 `@/common/nifiTaskState`。这次实现迁移沿用现有 HTTP 契约，业务写入在隔离数据库验证。真实四租户页面、状态查询和登记预检用于回归现有调用。Mock 覆盖范围按契约清单核对。

### 3.2 唯一可维护来源

| 内容 | 可维护来源 | 运行方式 |
| --- | --- | --- |
| 行业、公开门户、菜单、组织配置 | `mock/scenarios/configuration.json` | 从当前运行配置和实际租户绑定只读导出白名单，Mock 运行时读取本地文件 |
| 虚构业务样例 | `mock/scenarios/index.mjs`、`forms.mjs`、`logs.mjs` | 数据源编辑保存到专用状态；日志按行业惰性生成，不接触真实请求 |
| 模拟接口与权限、分页行为 | `mock/core/` | 统一处理原有接口契约，不执行 SQL 或真实 Magic/Java |
| 接口与真实实现的关联 | `mock/contracts/chengtian.json` | 记录方法、路由、必要 action、实现位置、资源身份及已审阅源码摘要 |
| 共享低代码 Vue | 根 `lowcode/` 及 `.manifest.json` | Mock 编译成本地组件包；真实模式按所选资源发布到控制库 |
| 真实 Magic | 根 `magic/` 及 `.manifest.json` | 保持编辑器真实资源身份，按当前发布流程维护 |

不能复制第二份 Vue 源码给 Mock，不能把编译包或历史运行快照当成源码，也不能把固定演示样例写进真实 Magic。

### 3.3 配置镜像与业务状态

配置导出工具为 `tools/export-mock-config.py`。它通过公开接口和仓库外受限连接配置读取当前租户实际绑定，菜单与组织查询限制租户及有效记录，输出保留原 ID、名称、父子关系、排序、隐藏状态和组件标识。工具只读，不写服务器业务数据。

```powershell
# 工程根目录；需要运行服务及有效的私有数据库定位配置
python tools/export-mock-config.py --with-structure

# 只比较，不改本地文件或服务器
python tools/export-mock-config.py --with-structure --check
```

已有镜像包含菜单结构时，后续普通导出也会保留结构范围。导出后必须审阅差异，不因行业库名相似读取旧副本。

本次已镜像的公安配置为 96 条菜单记录、102 条机构记录，机构包含虚拟 `ROOT`；树接口沿用原契约，返回 ROOT 下的 101 个真实机构。其他三个行业的菜单、组织表在本次连接环境中为空，镜像保持为空。这是当时实际配置，不是必须永久保持的固定数量。

每行业当前生成 120 个虚构数据源、6 个虚构业务系统；每数据源 5 张表和 1 个视图，即每行业 600 张表、120 个视图。公安样例分配到实际机构；没有机构配置的行业不虚构机构。业务记录初始化后使用：

- 业务状态：`data-elements:mock:chengtian:v3:<场景>`，保存在元数据平台 origin 的 localStorage。
- 模拟会话：`data-elements:mock:session:v3`，保存在该标签页 sessionStorage。
- UI 缓存前缀：`data-elements:mock:ui:v3:<角色>:`。

配置镜像与组织树每次从当前源码恢复，不随业务编辑缓存固化。运行时每次请求重新读取共享业务状态，避免主页面与桥分别持有过期基线。编辑不会改工程文件；修改种子也不会自动覆盖已有浏览器编辑结果。需要恢复种子时，只删除选定场景的业务键并刷新，不清空真实模式或其他应用存储。

## 4. 风驰如何复用元数据平台

### 4.1 一份状态与只读桥

3000 与 3020 是不同 origin，不能直接共用 localStorage。当前方案由风驰创建指向元数据平台的隐藏 iframe，元数据平台在 `?mockBridge=1` 且处于 iframe 时仅启动只读桥，不重复启动整个 Vue 应用或加载低代码包。

风驰通过 `src/sharedWorkspace.ts` 调用现有元数据接口契约；桥在元数据平台 origin 内读取同一会话和业务状态。风驰只保存页面显示结果，不保存另一份可维护场景或业务缓存。

桥的边界：

- 双向验证 origin 和窗口 source，元数据端只接受配置中的风驰父窗口。
- 用 nonce 关联响应，限制只读操作白名单及载荷大小，处理超时与卸载清理。
- 每次请求从元数据平台读取当前模拟凭据，由 runtime 校验租户、角色范围和具体数据源／表归属。
- 不向风驰传递 token、角色或数据库连接凭据，不在导航 URL 中携带这些信息。
- 401、403、404 清理不可访问的共享工作区，不靠前端隐藏树节点代替对象授权。

这个桥仅用于当前共享 Mock，不代表已实现两套真实系统的统一认证或真实数据接入。不要把风驰地址加入真实能力中心的 token 接收白名单来绕过它。

### 4.2 已复用的契约

| 能力 | 原有方法和路由 | 关键参数／结果 |
| --- | --- | --- |
| 当前租户 | `GET /sym/tenant/current` | 当前模拟会话租户 |
| 组织树 | `POST /sym/org/getOrgTree` | 实际机构层级 |
| 组织下的数据源 | `POST /dst/database/treeStatistics` | `mode=tree`，复用 `orgApps/datasources`，风驰展示时省去应用层 |
| 数据源详情 | `POST /dst/database/detail` | `tid`，复用数据源名称和数据库名 |
| 表／视图目录 | `GET /dst/database/metadata/tables` | `dbId`，保留同一表 ID 和对象类型 |
| 字段结构 | `GET /dst/database/metadata/columns` | `tid`、`snapshotOnly` |
| 分页预览 | `POST /dst/database/metadata/table/preview-data` | `tableId`、页码、每页行数、条件、排序 |
| 元数据页面抽样 | `POST /dst/database/metadata/table/sample-data` | 复用同一表和记录生成器，非桥开放操作 |

组织树与统计批量读取；用户选中一个数据源时读取一次目录和详情。首次打开一张表可读取一次字段，后续 20／100 行分页各只新增一次预览请求。不按目录项、表、列表行逐个补查，不用并发循环或缓存掩盖 N+1。

表结构与数据共享 `mock/core/metadata.mjs`。示例字段为 `id`、`name`、`status`、`amount`、`created_at`；`view_active_records` 是基础表有效记录的子集，不是另一套随机记录。字段条件、排序和分页都交给共享处理器。

### 4.3 导航与会话限制

全局导航的“风驰数据计算平台”使用 `VITE_FENGCHI_BASE_URL`；开发默认 `http://localhost:3020/`，部署默认 `/fengchi/`。从数据源详情进入时可带 `sourceId`，风驰仍检查对象是否可访问。风驰返回入口由 `VITE_METADATA_BASE_URL` 构造，当前默认返回 3000 的 `#/datasource-manage`。

推荐在元数据平台登录后通过同页导航进入风驰，再用返回入口切回。独立新开窗口可能没有原标签页模拟会话，应返回元数据平台登录，不自动创建会话或扩大权限。共享模式没有真实数据库连接、SQL 执行或服务端写入。

## 5. Magic、Java 与 Mock 的同步规约

核心是保持接口契约一致，不是要求两个目录内同名文件机械成对修改。

| 变更 | 必须同步的内容 |
| --- | --- |
| 新增／修改／删除接口，参数、响应结构、字段类型、分页排序、枚举、状态、错误或权限变化 | 同一任务更新真实实现、Mock 处理器／场景、调用方、契约关联和验证 |
| Mock 先行改变业务结构或新增能力 | 明确标记拟建契约，设计并同步对应真实接口；不能宣称真实实现已完成 |
| 只改示例名称、数量、日期等，契约不变 | 更新场景及相关验证，不要求无意义修改 Magic |
| 真实实现内部重构、Java 迁 Magic，对外契约不变 | 核查影响，更新实现关联、已审阅摘要及说明，通过原契约回归 |
| 菜单、组织等配置在数据库中变化 | 审阅后只读刷新配置镜像，并验证实际层级、隐藏状态与行业隔离 |

`node tools/check-mock-contracts.mjs` 校验身份、路由和已审阅源码摘要。顶层 `dependencies` 用 `operations` 关联现有 HTTP 项，`source.kind=magic-function` 单独核对函数源码摘要、元数据 ID 和分组完整路径，不把非 HTTP 函数伪造成接口，也不只把依赖写进不执行的说明字段。摘要变化要求人工核查契约影响，不能只替换摘要绕过同步；检查器不自动生成接口，也不能替代业务回归。

业务优先指向根 `magic/` 的实际实现。仍由 Java 提供的接口先关联真实 Java 文件，不能为了目录对应关系额外造一个 Magic 脚本：

- 可迁移业务标为 `pending-magic`，按完整业务链分批迁入 Magic，完成共同依赖、现有测试、发布和真实回读后再删除 Java。
- 必要认证、会话或框架能力可标为 `technical-retained`，须写具体原因，不能扩大日常 Java 业务保留范围。
- 不创建只转发 Java 的空壳 Magic 来声称完成迁移。

当前 56 项 HTTP 契约中，25 项指向 Magic，31 项指向 Java。其中租户登录选项、登录配置、当前租户、可用租户、租户切换这 5 项属于 `TenantController.java` 的认证／会话技术能力，记录为 `technical-retained`；表元数据采集状态与新增 25 项日志查询／导出业务均为 `pending-magic`。日志路由关联 `LogController`，查询／统计／详情关联 `LogQueryService`，组件读取关联 `LogComponentService`，任务／下载关联 `LogLifecycleService`。契约另以非 HTTP 条目的 `technicalRetention` 说明请求生命周期、脱敏、安全快照及可靠写入等必要采集技术保留，不将查询或导出编排算作技术保留。没有创建真实 Magic 样例或转发空壳，也没有迁移真实采集启动、后台执行及补偿链路。

用户曾要求本窗口承担遇到的 Java 业务迁移，但随后缩小为先看一批效果。接手时保留这个迁移方向，同时明确当前交付没有完成迁移；不把整套异步采集系统混入已验收的只读范围。与其他窗口协作时核对实际代码和任务范围，不假设已读取或同步了名为“肖烨辉”的窗口。

## 6. 主要源码落点

| 路径 | 作用 |
| --- | --- |
| `mock/vite-plugin.mjs` | 根低代码组件编译与 Mock 资源接入 |
| `mock/core/runtime.mjs` | 模拟路由、会话、持久状态、原接口处理 |
| `mock/core/logs.mjs`、`mock/scenarios/logs.mjs` | 四行业日志种子、同一权限与筛选、批量／统计、六态详情及本地导出 |
| `mock/core/transport.mjs`、`transport.d.mts` | 公共宿主已复用的裸数组解析、JSON／原始文件响应及 Fetch／MSW 类型声明 |
| `mock/core/configuration.mjs` | 实际菜单转换与组织树 |
| `mock/core/data.mjs` | 数据范围、逐 ID 校验、筛选与分页 |
| `mock/core/metadata.mjs` | 表／视图、字段、预览与抽样公共行为 |
| `mock/scenarios/` | 配置镜像、四行业业务种子与动态表单样例 |
| `mock/contracts/chengtian.json` | 56 项 HTTP 契约、2 项共享函数依赖、已审阅真实实现及明确未同步差异 |
| `data-elements-chengtian/src/main.ts`、`src/bootstrap.ts` | 先 Mock 后应用初始化 |
| `data-elements-chengtian/src/mock/` | 环境入口、存储隔离、MSW、跨应用只读桥 |
| `data-elements-chengtian/src/utils/capabilityCenters.ts` | 风驰导航地址，保持真实认证信任边界 |
| `lowcode/10.通用组件/全局导航(global-nav).vue` | 唯一共享导航源码，含风驰入口 |
| `data-elements-fengchi/src/sharedWorkspace.ts` | 共享桥客户端和现有接口适配 |
| `data-elements-fengchi/src/SharedFengchi.tsx` | 共享工作区加载、错误、切源和刷新 |
| `data-elements-fengchi/src/SharedConnectionTree.tsx` | 实际组织 → 数据源 → 表／视图树 |
| `data-elements-fengchi/src/App.tsx` 等 | 通过可选共享 workspace 接入现有界面，限制共享模式写操作 |
| `tools/export-mock-config.py` | 实际公开配置、菜单、组织的只读导出／比较 |
| `tools/check-mock-contracts.mjs` | 契约关联和审阅摘要检查 |
| `tools/check-mock-types.mjs`、`data-elements-chengtian/tests/mock-transport.types.ts` | 真实 tsconfig 下公共传输正反向类型验证及宿主入口诊断基线比较 |
| `tools/tests/chengtian-mock.test.mjs` | 场景、契约、权限、分页与共享数据回归 |
| `tools/tests/log-mock.test.mjs` | 日志大数据、20／100 固定请求、对象授权、参数、导出一致性与并发回归 |
| `data-elements-fengchi/tests/sharedWorkspace.test.ts` | 风驰共享适配与接口复用回归 |

## 7. 已完成与边界

已完成：

- 统一 Mock 开关、初始化顺序、原端口运行、真实与模拟缓存隔离、未知业务请求拒绝。
- 四行业公开配置镜像、实际菜单／组织映射，移除重复的可见数据源二级菜单和悬浮演示入口。
- 澄天数据源卡片／列表、筛选、排序、分页、详情、编辑保存、本地删除及表快照读取。
- 元数据字段、抽样与分页预览，风驰与元数据平台复用同一数据口径。
- 数据源和单表概览使用同一场景状态。独立探查校验有效租户、来源及表关联，刷新额外校验父源 ALL/ORG/OWNER 范围。预检不保存，正式刷新只保存虚构概览及更新时间。真实数据库的并发锁和索引投影在后端单独验证。
- 风驰组织数据源树、表／视图打开、字段结构、条件筛选、排序、分页和实际刷新。
- 风驰保留所选 `sourceId`，刷新可恢复当前数据源；产品导航和返回链接在 Mock 页面已验证。
- 共享模式只读：数据／结构写入、SQL、连接创建等不支持的操作禁用；导出只提供已读取当前页或显式选择，不把当前页当作全部记录。
- 真实导航已合并当时远端已有的“人工智能中心”入口，保留其行为，并编译当前本地源码。

尚未完成：

- 全部前端或全部业务接口的 Mock；当前覆盖以 56 项清单和分批验证为限，日志浏览器完整链路尚未验收。
- 完整登记后续步骤、真实采集执行、NiFi、治理、服务、审批等未覆盖流程。
- 风驰真实后端接入、统一真实认证、SQL 执行、结构与记录写入、DDL 编辑。
- 表元数据异步采集业务的 Java → Magic 迁移。
- **真实服务中的全局导航发布和发布后的实际验证。**

没有结构变更，因此本次未生成数据库结构增量。后续真实结构修改仍须同步完整 `db/` 定义及方言增量，不能用 Mock 验证代替真实数据库验证。

### 7.1 行业日志：核心、公共宿主及组件 fixture，整页验证边界

日志 Mock 复用 `ChengtianMockRuntime` 当前模拟会话、行业切换和 `ALL/ORG/OWNER`，不新建认证模型，不补菜单、机构或第二份低代码。无机构配置的行业，ORG 返回空范围；OWNER 只读本人，ALL 不跨租户。模块授权只收窄角色，不能由请求 `tenantId/scope/orgId` 提权。当前真实实现与验证边界以本节及 7.2 为准，其他历史分批记录不代表当前日志尚未发布。

- 每行业惰性生成 **5760 条**虚构日志，四类各 **1440 条**，跨 96 天、三名虚构人员和现有实际机构；默认七天每类 105 条，足以验证 20／100 行。各机构与采集状态独立轮换，避免只有某部门才存在历史或某状态。没有机构的行业保持空归属。日志种子仅保留内存，不混入数据源编辑快照，不读取真实请求或向真实 Magic／租户库写样例。
- 能力数组为当前授权的 `auth/operations/requests/security/exports`；有 `operations` 或 `requests` 才追加 `requestParameters`。不模拟 IDAAS，不展示未实现的 `archives/policies` 能力；对应路由／动作明确 501，不伪造成功，不回退真实。
- 四类原路由各有 GET 列表、统计、详情、批次明细及 POST 裸 ID 数组批量读取；列表／批量只返回展示摘要，operations／requests／security 的参数字段只有状态，auth 表无参数列，不伪造参数状态。均无 `parameter_summary`、大快照、旧参数或搜索正文。明细先鉴权父事件；批量限 1–100 个不同非空字符串 ID，纯空白 ID 在读取前按参数错误拒绝（HTTP 200、业务码 500），与 Java 一致，不再保留 403 分类差异。一次预取后逐 ID 校验，混入跨租户、越部门／本人、错误类别或不存在 ID 整批拒绝，按输入顺序返回；不逐项补查。详情／批量不套默认七天。
- 事件选择默认最近七天、最大九十天，起点含／终点不含，时区输入必须明确；普通空白时间按默认处理。Mock 严格检查日期、offset 与最多 9 位小数，纳秒比较边界；双空时间排除，历史种子本已是 UTC ISO，不再次减 8 小时。`page=1..10000`、`size=1..100`；授权及完整筛选先于统计、排序、分页和导出截断，同值 `tid asc`。历史按 `created_time` 替代缺失发生时间，不用当前时间补历史事实。扩展年份、24:00、闰秒等 Java Instant 特殊输入未作等价性认证。
- 新 `parameterKeyword` 已对齐真实查询：缺失／null／空字符串不附条件，其余必须是 **≤200 UTF-16 单位字符串，完整原样、不 trim**；纯空白也是字面筛选。大小写不敏感包含，不拆词、不解释 `%/_/!` 或正则，与 `keyword/result/time` 及其他条件 AND。按安全 `path/query/body/verified` 的“路径=叶值”投影搜索，保留中文、引号、反斜杠、数组、false／0／嵌套 null，不搜脱敏占位、notices、状态；LEGACY 不伪造新索引。旧成对精确白名单及小写布尔继续兼容，新旧条件 AND；敏感原文在检索前拒绝，禁止先掩码再命中旧索引，修正已有测试并部署本机。实际服务先应用可信租户／角色范围，再通过共享 Magic 函数产生 contains 条件；每条读取 SQL 配置 15 秒超时，不是 HTTP 或整个导出总期限，Mock 零 SQL 不证明真实超时已执行验收。
- operations／requests／security 详情 `parameter_snapshot` 为对象 `{version:1,status,path,query,body,verified,notices}`，六态 `EMPTY/CAPTURED/PARTIAL/OMITTED/FAILED/LEGACY`，`body` 可为任意安全 JSON；状态与对象一致，`request_parameters` 仅旧安全白名单非空时返回。auth 详情无参数对象或状态；Mock security 使用 OMITTED，不开放参数能力。敏感字段只有 `[REDACTED]`，无原值；历史六态、正文 false／0／null 与无采集状态分开，采集 FAILED 不推断业务失败。
- 预检意图与执行事实分开。只有非 dryRun 且明细实际 SUCCESS 才进入 `verified.deletedTaskId/deletedTaskName` 和旧删除事实白名单；PARTIAL 仅保存成功项，失败项不冒充已删除。批次计数与明细一致；HTTP 200 的虚构接口事件仍可能为业务 FAILED，不能据 HTTP 状态改写业务结果。

真实端 `AuditParameterSnapshot.fromLegacy(Object)` 只对白名单旧 `details.requestParameters` 再统一脱敏，安全内容放额外 `legacy` 对象，明确 `LEGACY`／来源未记录，不伪造 `path/query/body/verified`。技术入口与解析兼容已有测试，但没有自动重建或回填历史，不能把 Mock 的虚构六态当作历史补采结果。

### 7.2 本地导出、真实对齐与验证边界

- 使用真实任务路由 `POST/GET /sym/logs/exports` 与 `GET /sym/logs/exports/{id}/download`，提交只返回 `{id,status:'PENDING'}`；两次合法列表读取分别推进 RUNNING、实际生成文件并计算摘要后 SUCCESS，失败为 FAILED。这个确定性 Mock 调度不是 Java 后台队列。任务列表仅当前租户当前人员可见，即使 ALL 也不看他人；不暴露内部过滤快照、内容或摘要。
- CSV／JSONL 共用列表和统计的完整选择条件；冻结 `end=min(用户终点,提交截止)`、`receivedBefore=提交截止`，事件 `<end`、接收 `<=cutoff` 或为空。默认起点从冻结终点减七天，真实端本就不会随后台执行漂移。默认列表终点是 `now+1s`，导出是提交当前，不无条件断言两者严格同窗。冻结的是条件，**不是 ID／事务一致性快照**：提交后迟可见但满足截止的记录仍可入选；Java 每 500 行 offset 重读，Mock 一次选择后每 500 行编码，不认证并发强一致。忽略列表页码／大小，发生时间升序／ID 升序，上限 100000 行／256 MiB、本地 32 项。24 小时有效，下载复核本人、模块与导出权限、范围及 SHA256。按场景串行，浏览器支持时用 Web Locks，否则仅同一 Storage 对象内串行，不声称不支持 Web Locks 的跨标签页原子性。
- CSV 带 UTF-8 BOM，清洗控制字符、双引号转义，防止首字符或前导空白后的 `=+@-` 公式注入；JSONL 每行安全详情。CSV 历史发生时间使用 `created_time`，不丢弃页面上的历史时间。下载保留真实 MIME、attachment 和 no-store，为原始正文而非 JSON 成功包装。
- 模拟导出另用隔离键 `data-elements:mock:chengtian:logs:v1:<场景>`，保存虚构任务和文件供刷新恢复；不清理真实存储或其他行业，存储失败明确报错。日志状态独立于共享数据源状态，不给风驰复制业务种子。
- **传输与组件 fixture 已验证，Mock 整页未验证：**唯一公共拦截器调用 `parseMockBody(path,text)` 与 `mockResponse(result,HttpResponse)`，不增加页面分支。裸数组只允许既有四类 batch 路由，下载保留 raw 字节、HTTP 状态及响应头；普通结果仍 JSON。`transport.d.mts` 区分 JSON／raw，runtime 签名接受对象或数组。Node 实际 MSW 已验证数组、BOM／CSV／JSONL 字节及响应头。浏览器 fixture 使用唯一真实 SFC 的编译产物，覆盖共享、Element Plus、平台宿主的 1440／390 布局、查询、参数详情与复制，但接口由 fixture 注入，没有接入真实 Mock 应用壳、登录／路由及 MSW 全链路。真实 Mock 整页及浏览器下载尚未独立运行，不冒称全栈 E2E。
- **真实日志已在本机 86 发布：**后端已授权换包重启，所选 31 个 class 字节与当前 Maven classes 全部相等；新增共享函数 `log_parameter_condition_v1`（`/common/log-parameter-keyword-condition`）、所选日志 Magic 和唯一日志组件已发布，两级缓存刷新、运行回读及函数脚本冷加载通过，所选资源无未发布差异。仍为 56 项 HTTP 加 1 项非 HTTP 函数依赖；查询／导出继续 `pending-magic`，不扩大 Java 技术保留或 IDAAS 参数范围。范围不含 14／生产；另有 9 条历史／并发差异及远端 44 个 Magic／3 个低代码新资源未覆盖。
- **CSV／批量既有差异已闭合：**Java 与 Mock 均先清洗控制符，再防前导空白后的公式；CSV 历史时间只回退已归一化的 `created_time`，不二次偏移。空白 ID 参数错误及输入顺序也已对齐，不再列作遗留差异。JSONL 与新归档安全快照编码、旧归档字节／形状兼容有测试，未改旧归档文件；真实新快照归档尚未运行，不称 VERIFIED。Java 可按授权开放 archives／policies，Mock 仍明确 501，此能力差异继续保留。
- `POST /dst/database/saveOrUpdate` 的普通预检保留保存响应，跳过持久化和索引同步。正式保存支持 `deferIndexRefresh` 的布尔 `true`、数字 `1` 和字符串 `1`。Mock 在场景快照提交前检查已观察的版本变化，存储失败时保留原快照。代理结束登记从同一场景的测试服务检查租户、来源和角色范围，普通结束登记按有效同源表统计。真实事务、数据库方言和并发锁由后端验证。Mock 场景尚未接入服务编排。

当前限定回归为共享页面、Mock 与澄天 **125/125**，IDAAS **117/117**（含无 `window` 请求头兼容）、日志／排版／紧凑布局浏览器 **34/34**、静态 UI **6397/6397**，海通 **142/142**；后端完整 **415 套／3304 项，跳过 7 项，失败及错误为 0**，四前端生产构建和澄天 Mock 构建通过。Mock 列表及批量 20／100 各一次请求、零 SQL 只说明模拟路径；真实列表 20／100 各一次请求由[真实页面／API／导出报告](../logs/audit-parameters/real-validation.json)确认，两条事件 SQL 与一次批量 SQL 由后端针对性测试确认，身份／范围解析另有固定开销。真实 API 保留嵌套、`false/0/null`、`businessCode`，敏感测试值详情无原值且搜索 0；同一显式筛选 CSV 摘要与 JSONL 安全快照各 1 条，当前请求按策略排除采集时明确为 `OMITTED`。[跨物理库隔离](../logs/audit-parameters/real-isolation.json)确认既存外租户详情及混 ID 整批拒绝，未写业务数据或授权；[所选资源](../logs/audit-parameters/resource-verification.json)与[三方言四库 124/124、24 列 DDL 及五库导出](../logs/audit-parameters/native-86/result.json)分别核验，不相互代替。

上述日志专项中，全库检查澄天既有 53 项（缺 43 表／9 列／1 索引）前后相同、本批新增 0。导航“人工智能中心”预期与源码不一致，万象主题 hash 基线待核对。数据源 `saveOrUpdate` 摘要和行为由本节的独立回归核对。Mock 整页／浏览器下载与真实新快照归档仍须独立验证。

## 8. 启动、验证与验收方法

### 8.1 启动

在工程根目录使用两个终端分别启动，先确认端口未被另一个前端占用。运行中已有服务时先核对模式和所属进程，不盲目重复启动或停止后端。

```powershell
# 终端一：澄天，保持 3000
pnpm --dir data-elements-chengtian run dev:mock
```

```powershell
# 终端二：风驰，保持 3020
npm --prefix data-elements-fengchi run dev:mock
```

澄天 `.env.mock` 当前配置：

```dotenv
VITE_MOCK_ENABLED=true
VITE_MOCK_SCENARIO=police
VITE_MOCK_ROLE=admin
VITE_MOCK_LATENCY_MS=120
VITE_APP_PORT=3000
VITE_APP_BASE_API=/dev-api
VITE_APP_NIFI_API=/nifi/api
```

行业可选 `police`、`broadcasting`、`sea-fishery`、`chengtian`；角色可选 `admin`（ALL）、`department`（ORG）、`registrar`（OWNER）。环境中的行业只是新会话的初始选择，页面仍复用原有行业切换流程。

风驰 `.env.mock` 使用 `VITE_MOCK_ENABLED=true` 和 `VITE_METADATA_BASE_URL=http://localhost:3000/`。模拟登录使用虚构账号及至少六位演示密码，不使用真实账号密码。

### 8.2 检查命令

```powershell
# 工程根目录
node tools/check-mock-contracts.mjs
node tools/check-mock-types.mjs
node --test tools/tests/chengtian-mock.test.mjs
node --test tools/tests/log-mock.test.mjs
node data-elements-chengtian/scripts/test-capability-navigation.mjs
node data-elements-chengtian/scripts/test-login-input.mjs

# 风驰
npm --prefix data-elements-fengchi test
npm --prefix data-elements-fengchi run typecheck
npm --prefix data-elements-fengchi run build:mock

# 澄天
pnpm --dir data-elements-chengtian run build:mock
pnpm --dir data-elements-chengtian run build
```

数据源／风驰原批次的历史结果：Mock 22 项通过；当时契约 31 项检查通过；风驰 31 个测试文件共 432 项通过；澄天现有回归 70 项通过；导航 87 项检查通过；登录输入检查通过。两端 Mock 构建及澄天普通构建通过。澄天完整类型检查仍有 66 条既有诊断，与当时基线按文件和诊断代码比较没有新增；不能声称全仓 TypeScript 检查通过。修改后需按实际影响重新运行必要检查，历史数字不是当前通过证明。

日志收尾专项 **27／27 通过**。首次与原澄天 Mock 合跑 **48／49 通过**，当时失败的是数据源保存摘要，后续数据源契约校验见 7.2。首次专项的 MSW 私有文件路径与截止预期两项测试已修正。登录输入回归通过。导航 Node 回归仍要求当前导航源码没有的“人工智能中心”。该测试通过现有编译器生成忽略的临时产物。

局部 TypeScript 由 `tools/check-mock-types.mjs` 使用真实应用 tsconfig 和已安装 TypeScript 验证：Fetch／MSW 正反向类型样例 **0 诊断**；宿主入口依赖图当前 **24**、只读替换为 HEAD 入口的同依赖基线 **25**，新增 **0**、Mock 范围 **0**（修正原入口依赖 DOM.Iterable 的用法）。24 条既有依赖诊断位于 `src/plugins/common.ts`、`src/store/modules/detail-store.ts`、`src/store/modules/setting-store.ts`，不是全仓类型检查通过。初期未加载完整配置的 CLI 探查及类型样例缺 HttpResponse 泛型的问题均已修正后复验。全部本批文件编辑器诊断无错误，限定范围 `git diff --check` 通过。未运行 Maven、前端完整构建、服务起停、发布或浏览器验收。

### 8.3 页面验收与请求／权限证据

1. 打开 `http://localhost:3000/`，选择公安并模拟登录。检查菜单沿用真实配置，数据源管理没有新增可见二级菜单。
2. 检查组织树保留原层级；在数据源详情通过 Logo 产品导航进入风驰，地址保持 3020 并携带当前 `sourceId`。
3. 风驰树检查“组织 → 数据源 → 表／视图”，打开 `demo_record`，核对两边五个字段的名称、类型、注释、主键、可空性。
4. 核对同一数据源、同一张表的记录。已验证示例 `police-source-014`／`demo_police_14`：基础表 120 条，风驰每页 100 条；元数据抽样 10 条，值来自同一记录生成器；视图 80 条，等于基础表有效记录子集。
5. 切换数据源、刷新、返回平台、重新进入，确认所选对象和会话行为；等待当前数据源加载完成后再判断，不能以旧表格残留内容作为验收结果。
6. 检查 20／100 行均只新增一次分页预览请求，没有按行补查；字段首次读取与分页请求分别计数。
7. 分别验证 ALL／ORG／OWNER；详情、字段、预览和批量 ID 都按当前租户及数据源范围拒绝越权，不能因为传多个 ID 扩大权限。无机构归属不能自动获得 ORG 范围。

Mock 路径不执行 SQL，SQL 数为 0；这个结果只能证明模拟路径没有访问数据库，不能当成真实后端 SQL 数、事务、方言或安全验收。真实接口改造仍须提供实际 20／100 项请求和 SQL 证据。

截图、构建日志、测试输出和资源报告在忽略目录 `logs/mock/chengtian/`，包括 `shared-browser-evidence.json`、`fengchi-shared.png`、`fengchi-shared-fields.png`、`metadata-shared-preview.png`、`type-comparison.json`、`resource-status-current.json`。这些是本机辅助证据，不是需要复制或提交的源码；新机器可以按以上步骤重新生成。

## 9. 优先待办：完成真实导航发布

用户已经明确选择“同时发布真实导航；我会提供更新后的有效本地发布会话”。需要的是有效会话，不是重复申请发布授权。最后检查时约定的会话文件仍为旧版本，尚未执行真实资源写入。

- 目标：本机 `http://localhost:8088` 对应的控制库 `baseline`，以当前实际绑定及资源身份核对为准。
- 唯一所选资源：`lowcode/10.通用组件/全局导航(global-nav).vue`。
- 组件 ID：`2009111573765361664`；身份和目录不能改。
- 会话交付位置：`C:\Users\Administrator\.codex\private\data-elements\publish-token.tmp`，只包含会话值，不能贴进聊天、命令参数、Git 或日志。
- 工具默认私有连接配置：用户目录下 `.codex/private/data-elements/resources.local.json`，内容不写入本文。

接手步骤：

1. 检查用户更新的会话是否有效，只读核对运行目标；通过受限配置或进程环境提供 `RESOURCE_API_TOKEN`／`RESOURCE_API_URL`，不得输出实际值。
2. 重新生成所选资源计划。之前已显式合并远端“人工智能中心”入口，并只更新该资源索引的已审阅基准；上次计划是本地变化、远端无新增变化。不能沿用旧计划直接覆盖并发修改。
3. 重新从当前根源码编译，使用编译命令返回的产物路径发布，不能发布旧包。
4. 用现有工具逐资源发布，保存 `source_code`、`compile_js`、`compile_css`，刷新 `/sym/component/cache/refresh`，运行回读源码及编译结果，验证实际导航地址与入口。
5. 再次比较选定资源，确认内容一致。其他 Magic／低代码差异分别报告，不全量同步，不删除、不覆盖其他窗口工作。

```powershell
# 工程根目录；先按上文提供有效的受限运行会话
python tools/resource-sync.py plan --kind lowcode --file 'lowcode/10.通用组件/全局导航(global-nav).vue'
node tools/compile-lowcode.mjs 'lowcode/10.通用组件/全局导航(global-nav).vue'

# 将占位路径替换为上一条编译命令实际返回的 compiled 路径
python tools/resource-sync.py publish --kind lowcode --file 'lowcode/10.通用组件/全局导航(global-nav).vue' --compiled '<本次编译输出的 JSON 路径>' --apply

python tools/resource-sync.py plan --kind lowcode --file 'lowcode/10.通用组件/全局导航(global-nav).vue'
```

发布工具有身份、远端摘要和运行版本前置检查。出现并发变化必须先合并；数据库已保存但刷新／回读失败时必须按真实状态补齐，不能直接再次覆盖。正常低代码发布不需要重启后端。本授权不包含生产发布或全量资源覆盖。

## 10. 给新窗口的任务开场文字

可以直接把以下内容交给新的 Codex 窗口：

> 先阅读工程根 `AGENTS.md` 和 `docs/mock-design.md`，按链接查阅公共 Mock 规约及澄天、风驰主文档。接管时核对工作区和运行状态，保留并行改动。澄天 3000 与风驰 3020 共享目录、字段和预览场景。数据源保存和结束登记按 7.2 接续。日志公共宿主已接裸数组和原始文件，参数查询已登记共享函数依赖。日志 Mock 整页及下载、CSV 真实端差异和导航旧测试按 7.2、8.2 继续核对。对外行为变化时同步真实实现、Mock、调用方和验证。风驰继续读取同一份业务状态，使用现有端口、菜单和组织配置。
