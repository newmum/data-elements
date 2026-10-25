# 数据要素仓库协作与资源维护（data-elements）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。仓库仅保留根目录 README 总入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](#database-schema)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 维护方式

每个工程的说明统一保存在 `docs/<工程名>.md`，共用规范在本文件原位维护，索引见 [根目录文档索引](../README.md#documentation)。新增或变更时更新现有章节，不追加日期、版本、阶段文件。独立海通库使用 `python tools/export-db.py --project haitong-data-pull` 只读更新根 `db/haitong/init.sql` 与结构清单，私有连接配置使用 `project_databases.haitong-data-pull`。已有库的临时差异执行语句和验收产物放忽略的根 `logs/`，不替代完整结构基线。

## 目录

- [本地资源版本管理](#resource-versioning)
- [数据库结构基线](#database-schema)
- [统一维护工具](#maintenance-tools)
- [前后端批量查询与分页规范](#batch-pagination)
- [低代码与 Magic 业务约束](#lowcode-magic)
- [资源目录组织建议](#resource-layout)
- [系统日志管理详细设计](#logging-design)
- [演示环境部署与维护](#demo-environment)

<a id="resource-versioning"></a>

## 本地资源版本管理

Magic、低代码和数据库结构从本仓库维护，采用“先改本地，审查差异，再发布所选资源”的流程。当前共享 Magic 和低代码的发布目标是控制库 `baseline`；根 AGENTS 已将同一任务内发布、刷新和实际验证列为每次修改的完成条件。用户明确要求只读、只改源码不发布或另行指定环境时，以该次指令为准。四个前端仍独立安装和构建，不需要根目录 Node workspace。


<a id="resource-versioning--目录与版本源"></a>

### 目录与版本源

| 目录 | Git 保存内容 | 来源与用途 |
| --- | --- | --- |
| `magic/` | 完整 `.ms`、`group.json` 及资源索引 | `baseline.api_file_t`；本地路径去掉 `/magic-api/` 前缀，其他层级保持一致 |
| `lowcode/` | Vue 源码及组件索引 | `baseline.ui_component_t`；按组件管理器真实 `pid` 树和中文显示名称建目录及 Vue 文件 |
| `db/` | 平台五库及独立工程数据库各一份完整 `init.sql` 和结构清单 | 表、视图、索引、约束、注释，以及实际存在的其他结构；不含业务数据和账号 |
| `tools/` | 可维护的导出、比较、编译、发布工具及其安全测试 | 所有操作入口共用一份实现 |
| `logs/` | 不提交 Git | 日志、审计报告、截图、编译结果、临时变更 SQL 和运行备份 |

`magic/.manifest.json` 保存数据库原始路径、表行 ID、Magic 元数据 ID 和基准摘要。Magic 文件中的 JSON、`================================` 分隔符和脚本均保留；不得把 `tid` 当成在线编辑器使用的元数据 `id`。

Magic 目录使用编辑器的 `parentId` / `groupId` 分组树及中文 `name`；数据库物理 `file_path` 与本地必须同步为该目录。接口 `path` 是公共路由，目录重排不能改变它。顶层分组 `parentId` 使用字符串 `0`；NULL 会导致分组在冷加载后的编辑器树中不可见。当前公安定制目录统一为编辑器原名 `11.省厅公安定制`，网关对接也放在其中；不再另建带 `-GA` 的副本。工具会报告重复 ID、断链、无效根父级和物理/编辑器路径差异，目录整理后这些警告须清零。

部分通用函数原生为纯 JSON，正文位于 `script` 字段，保留这种格式。三个原始资源存在运行凭据或具体示例账号：云梯网关数据源配置、公安零信任网关配置检查、低代码 `api-build-dialog`。其 Git 副本只保留脱敏公开内容，索引标记禁止直接发布，避免把空值或占位符写回服务器。后续如修改这些资源，先将凭据改为服务端受限配置并审查发布方式；本次整理不改变服务器内容，秘密值及其变化不纳入 Git 差异。

`lowcode/.manifest.json` 保存组件 ID、父级、英文可导入名称、中文展示名 `remark`、排序和基准摘要。组使用数据库 `name`；组件有展示名时，目录/文件使用组件管理器同样的 `remark(name)`，没有展示名时使用 `name`。有效父级既可能是分组，也可能是带子组件的组件，不把组件 ID 建成替代目录。例如登记子组件位于 `02.数据盘点/02.登记流程组件/登记组件(register-modal)/1. 登记系统(register-app).vue`。英文导入名称和数据库 ID 不因文件重排而改变。普通前端 `src/**/*.vue` 是应用静态源码，继续留在各自应用中。低代码编译 JS/CSS 是生成产物，不复制进 `lowcode/`。

对 Windows 不支持的数据库目录名，仅对非法字符作可逆 `%XX` 转义，索引仍保留真实远端路径。父记录确实缺失的低代码才临时保存在 `_unattached/<原父级ID>/`，索引记录警告；应核查并修复断链，不能作为正常基线长期保留，也不能猜测父级修改服务器。当前资源没有断链或 `_unattached` 文件，旧 ID 空目录已删除。空目录的身份由索引保存，Git 不需要为每个目录添加占位文件。


<a id="resource-versioning--私有连接配置"></a>

### 私有连接配置

默认配置位于当前用户的 `~/.codex/private/data-elements/resources.local.json`，在工程外。连接密码和运行 token 不写入 Git、skill、日志或前端环境变量。

本机维护工具使用 Python 3；需要 `PyMySQL`、`PyYAML`。达梦结构导出还使用 `JayDeBeApi`、`JPype1` 及已安装的 JDBC 驱动。低代码编译使用澄天现有依赖和现有在线编译器，先在澄天应用内安装依赖。

确认当前环境变量中的 `NACOS_USER`、`NACOS_PASSWORD` 后，读取当前 Nacos API 配置：

```powershell
python tools/configure-resources.py --api-url http://localhost:8088
```

可用 `--nacos-url`、`--namespace`、`--profile`、`--output` 指定目标。工具拒绝把含凭据的配置写入当前仓库。Nacos 底层数据库缓存可能过期，应通过当前 API/运行配置确认绑定；每次改变目标环境或租户路由后重新读取配置并核对目标。

资源工具也支持 `RESOURCE_DB_HOST`、`RESOURCE_DB_PORT`、`RESOURCE_DB_USER`、`RESOURCE_DB_PASSWORD`、`RESOURCE_DB_DATABASE` 覆盖私有配置。发布时以 `RESOURCE_API_URL` 和 `RESOURCE_API_TOKEN` 明确运行服务及已认证会话。数据库地址和运行服务必须属于同一环境；工具发布前会比较运行资源与数据库内容。

本地后端可通过 `NACOS_CONFIG_IMPORTS` 选择一份完整配置，例如 `optional:nacos:data-elements.yml?group=DEFAULT_GROUP&refreshable=true`。未设置时保持原来的 `data-element.yml` 与 profile 配置加载方式。Nacos 控制台地址与 SDK 地址分别核实；端口和命名空间不能按页面 URL 猜测。合并启动参数中的租户数据库覆盖后再准备完整配置，并核对实际控制库和各行业业务库。

Windows 本地启动使用 `tools/start-local-backend.ps1`，默认读取仓库外 `~/.codex/private/data-elements/local-runtime.private.json` 的 `executable`、`argumentLine`、`port` 与 `environment`。Java 参数应指向本仓库 `.runtime/data-element-runtime.jar`；环境配置只允许 Nacos、Spring 和项目环境变量，凭据不写入源码或命令行。脚本默认拒绝中断已有服务；已授权重启才使用 `-Restart`，且只停止本仓库 JAR 占用的端口进程。启动后另行验证健康、真实登录、租户路由和所选资源加载。连接共享演示环境的本地进程应关闭 Nacos 服务注册，避免接收演示环境的外部流量。

该启动入口同时加载 IDAAS 持久加密主密钥：优先使用进程环境或私有配置 `environment.IDAAS_TRANSPORT_KEY`，否则读取私有配置的 `transportKeyFile`，默认路径为 `~/.codex/private/idaas-provision/transport-master.key`，与根启动脚本一致。密钥文件必须在仓库外，内容为原有 32 字节主密钥的 Base64；不要重新生成或输出其内容。人员保存的幂等回执也依赖这把密钥。缺失或格式不正确时，在停止旧服务之前拒绝启动，避免服务看似启动正常但保存全部失败。私有配置只引用路径时，无需在配置中再复制密钥。

本机接收地址使用 `http://localhost` 时，在该私有配置的 `environment` 中显式设置 `IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP=true`；启动工具会校验并传给后端。它只允许 `localhost` 的 HTTP 开发地址，正式接收地址仍使用 HTTPS。接收端还需要在私有 `SPRING_APPLICATION_JSON` 或当前部署配置的 `idaas.receiver.routes` 中，按已登记密钥标识配置真实 `tenantId`、`appId` 和 `instanceId`；这些值从已核验的应用运行绑定和接收配置取得，不能由下发报文指定或凭应用名称猜测。更换启动入口时须同时保留这两项设置，否则连接测试和立即下发会失败。


<a id="resource-versioning--同步与审查"></a>

### 同步与审查

在工程根目录执行：

```powershell
python tools/resource-sync.py status
python tools/resource-sync.py plan
python tools/resource-sync.py pull
git diff -- magic lowcode db
```

`status` / `plan` 只读比较本地、索引基准和当前数据库。输出本地修改、远端修改、缺失文件、远端新增和未注册本地文件；不会发布。可用 `--kind magic` 或 `--kind lowcode` 缩小范围。

`pull` 导出当前完整资源树；只要会覆盖未合并的本地修改就整体拒绝该类资源的写入。远端删除的文件仅在本地未改动时移除，不会反向删除数据库资源。旧索引路径退役后，仅清理已经为空且当前资源树不再需要的旧父目录，保留当前空分组和未登记的本地文件，避免重排后遗留 ID/旧名空目录。源内容摘要忽略 Git 换行转换，发布事务仍对远端原始内容进行并发保护。

本地文件修改后，不需要再次 pull。先核对 Git 差异，再编译或执行相关测试。目标服务器有新改动时，先合并冲突；不能用旧本地副本强制覆盖服务器。

每次修改 Magic / 低代码，先在 `magic/` / `lowcode/` 保存源码，再按所选文件发布到 `baseline`。低代码的源码及当前编译 JS/CSS 必须一起保存，Magic 必须刷新实际运行资源。前端构建、Java JAR 更新和 Git 提交都不会自动同步这些数据库资源。发布后对当前任务文件再次执行 `plan`，并用真实会话验证业务路由或页面；最后用整体 `status` 列出仍需处理的其他差异。`sameContent=true` 且索引摘要变化时先核对运行版本，再同步索引，不误报为未发布。发布或运行验证失败须明确报告，不能以源码已改标记完成。


<a id="resource-versioning--发布一个现有-magic-脚本"></a>

### 发布一个现有 Magic 脚本

```powershell
python tools/resource-sync.py plan --file 'magic/api/真实目录/接口.ms'
python tools/resource-sync.py publish --apply --file 'magic/api/真实目录/接口.ms'
```

`publish` 要求明确的 `--apply`，每次只接受一个已登记的文件。当前实现支持现有非空 `.ms` 内容更新，保留 ID、所属分组、公共路径和方法；重复 ID、远端身份变化或并发修改会阻止发布。发布先保存受限回退内容，再在控制库执行带并发校验的事务更新，刷新 Magic 资源并重新读取运行脚本。

新增资源、分组创建/移动、删除或路由变化需单独审查完整目录元数据和调用方。新增共享 Magic 资源可使用 `register --kind magic --file <规范文件>`，多文件重复传入 `--file`，默认只生成计划；审阅后追加 `--apply`。单批最多50个新脚本或分组，校验路径、父级和全局资源ID，自动补齐新分组的物理目录记录；已有路径或ID、断链、目录名称不一致及并发修改均拒绝。登记在事务中只插入所选新记录，刷新完整运行资源树、回读脚本后更新本地索引。该命令不移动、删除或覆盖已有资源，不支持独立海通资源。分组移动、删除及已有路由变化仍需另行审查，不得另建本地缓存副本绕过限制。

发布成功后的运行脚本比较只证明资源已经加载；仍需用正确身份和租户验证本次涉及的业务路由。持久化完成但刷新失败时，工具会明确报告“数据库已保存、运行验证未完成”，不能当作发布成功。


<a id="resource-versioning--编译与发布一个现有低代码组件"></a>

### 编译与发布一个现有低代码组件

```powershell
$compiled = node tools/compile-lowcode.mjs 'lowcode/05.数据治理/2.5.4 数据质量(data-quality).vue' | ConvertFrom-Json
python tools/resource-sync.py plan --file 'lowcode/05.数据治理/2.5.4 数据质量(data-quality).vue'
python tools/resource-sync.py publish --apply --file 'lowcode/05.数据治理/2.5.4 数据质量(data-quality).vue' --compiled $compiled.compiled
```

编译工具调用澄天在线编辑器同一份 SFC 编译器，校验生成 JavaScript 的语法及返回组件工厂，并把源码摘要和编译结果放在忽略的 `logs/lowcode/compiled/`。以工具实际输出的编译文件路径为准，不手写或复制旧编译代码。

发布检查组件身份和当前源码摘要，在同一事务中保存源、编译 JS/CSS 和历史记录，再刷新组件缓存并重新核对运行源与编译结果。编译后又修改源码会被拒绝；还需验证对应租户的实际页面。正常低代码发布不需要重启后端。

全量版本核对还要比较运行编译产物：`status` 中源码相同不代表 `compile_js`、`compile_css` 与当前源码相同。用同一编译器重新编译待核对组件，比较时仅归一化编译器随机生成的 SFC 文件名及其对应作用域 ID，保留其余代码和样式差异。运行读取使用一次组件列表和批量 Magic 资源导出，不能逐组件、逐接口补查。

仅编译产物过期时，复用 `tools/resource-sync.py` 的 `repair_compiled` 批量修复函数。每次显式选择 1–200 个已登记组件，核对本地源码、当前数据库源码、编译源码摘要及已认证运行组件，再加锁复核并批量更新编译 JS/CSS，保留源码、身份和父级，刷新缓存后回读验证。并发源码或编译产物变化会阻止写入。含凭据的组件只能从仓库外受限保存的当前真实源码编译，恢复材料也保存到仓库外，不能将脱敏占位符作为运行源码发布。


<a id="resource-versioning--数据库结构"></a>

### 数据库结构

```powershell
python tools/export-db.py
git diff -- db
```

`db/` 当前维护控制库 `baseline`、公安 `baseline_ga`、广电 `baseline_beijing_gd`（达梦）、海渔 `baseline_sea_fishery`、澄天 `chengtian`。结构导出只读取元数据，五库全部读取成功后才写入本地基线；不导出业务记录、口令、创建用户或授权。

本机登记的外部验证来源库 `personaldb` 的完整结构保存在 `db/environments/local/validation-sources/`，连接仍由仓库外受限配置维护。多值翻译验证表使用 `demo_multivalue_gender_20261003`，公安和澄天的登记均引用同一张物理表。此目录不属于行业租户统一结构，不能把该验证表创建到租户业务库，也不能用初始化文件覆盖现有来源库。

四个行业租户统一为以 `baseline_ga` 为权威的 162 张业务表，控制库独立维护。结构变更（含新增表、字段、索引、约束）先同步修改所有行业租户的完整初始化定义并审阅，再按实际方言应用所有行业库；保留各自租户默认值和数据。临时 ALTER 语句与备份放在 `logs/` 或仓库外受限目录，不累计日期增量 SQL。服务器变更后运行 `python tools/check-db.py` 检查表集合、列、主键、索引、外键列及原生自动更新时间机制，再重新只读导出全部数据库，确保完整初始化结构与实际数据库一致。`init.sql` 仅用于新空库；它不包含应用启动所需的业务种子和资源数据，不能直接在现有库执行。更多方言与依赖条件见 [数据库结构基线](#database-schema)。


<a id="resource-versioning--提交前"></a>

### 提交前

核对 `git status` 和差异，确认只有可维护源、结构、配置和必要说明。根 `logs/`、旧 `exports/`、整个后端 `data-elements-parent/data/`、依赖、构建产物、私有配置和日志文件均被忽略；不使用 `git add -f` 绕过。已被 Git 跟踪的旧产物还需提交删除，添加忽略规则不会自动移除历史跟踪。仓库内不得继续保存分散 `.ms`、低代码工作副本、旧发布包或数据库增量缓存。

后端错误快照默认写入根 `logs/backend/errors/`，跟随 `DATA_ELEMENTS_LOG_DIR`，可用服务端 `storage.error-root-dir` 明确配置。旧 `storage.root-dir` 仅用于显式启用的管道文件迁移输入，不用于生成新的错误日志。

项目约束放在根 `AGENTS.md` 和它引用的业务文档，由 Git 同步给团队。个人 skill 保存如何定位环境、编译、发布和验证；不会代替仓库规则，也不会自动赋予写服务器或重启权限。

<a id="database-schema"></a>

## 数据库结构基线

根目录 `db/` 保存各环境可维护的完整结构。每个库只有一份 `init.sql`；`schema-manifest.json` 记录方言、定义对象数量、校验值及实际验证状态。结构包括表、完整列定义、主键、索引、外键、注释、视图，以及实际存在的触发器、存储程序、事件或序列。不含业务记录、账号密码、创建用户、授权或 Magic/低代码资源 INSERT。

初始化文件用于新空库或新空 schema。现有库的修改先审查结构差异、备份，再执行本次受控变更，完成后重新导出完整结构。共享 Magic 与低代码分别在根 `magic/`、`lowcode/` 维护。

### 当前数据库与统一结构

| 角色 | 实际数据库 | 方言 | 表数量 |
| --- | --- | --- | ---: |
| 控制库 | `baseline` | MySQL | 55 |
| 公安 | `baseline_ga` | MySQL | 162 |
| 澄天 | `chengtian` | MySQL | 162 |
| 海渔 | `baseline_sea_fishery` | MySQL | 162 |
| 广电 | `baseline_beijing_gd`，运行配置绑定 `BASELINE_BEIJING_GD` | 达梦 | 162 |
| 海通数据推送服务（独立工程） | `haitong` | MySQL | 15 |

`haitong-data-pull` 对应的独立库完整结构为 [haitong/init.sql](../db/haitong/init.sql)，已通过真实 MySQL 的 `SHOW CREATE` 导出：15 张表、51 个索引（含主键及唯一索引）、5 个外键，没有视图、触发器、例程或事件。它不属于四行业同构基线。历史工程内 `sql/` 已整合移除，后续只在本目录更新完整定义；对账查询归入[海通推送服务文档](haitong-data-pull.md#reconciliation-queries)。

原有达梦适配定义保存在 [haitong/compatibility/dameng.sql](../db/haitong/compatibility/dameng.sql)，明确标记为未连接实库核验的 9 表模板，缺少 MySQL 中的推送凭据和 Magic 资源表；不能作为当前 15 表结构、完整兼容承诺或已有库升级脚本。应用打包使用的 H2 本地初始化资源继续保留在应用 `src/main/resources/db/h2.sql`，不作为生产 MySQL 基线。

上表为当前本机后端实际绑定数据库的完整读取结果。四行业结构已统一至162张：原140张、字段映射三张版本表、现用布控模块13张表及数据推送节点配置表`push_node_t`，加上日志批次、可靠投递、策略、导出、归档五张辅助表；只补充缺失空结构，不复制其他租户业务记录。登记第五步的接入申请三字段已同步应用到四行业`db_table_t`。`check-db.py`核对完整表集合、列、主键、索引及外键通过，五库完整结构已重新导出。演示环境仍单独维护在`environments/demo/`，其143张结构未因本次本机更新而自动升级。

四个行业租户维护同一套业务表、列、主键、索引和约束，以实际已确认的公安租户库为统一结构权威，不能按旧库名选择发布目标。原共同结构包含公安原 139 张表和以下一张有现用功能依据的公共表；新增版本表在各方言定义中完整维护，新增空表不复制其他租户数据。

| 公共表 | 业务依据 |
| --- | --- |
| `service_node_t` | 数据服务节点列表、保存、删除接口及有效节点记录 |
| `data_access_mapping_plan_t` | 字段映射不可变执行计划与节点版本历史 |
| `data_access_mapping_resource_t` | 不可变字段规则模板及完整字典快照 |
| `data_access_mapping_publication_t` | 当前流程节点发布计划引用及租户外键 |

控制库保存租户路由、共享资源与身份控制结构，独立于行业表集合。`baseline.api_backup_t` 和 `baseline.ui_component_history_t` 是编辑器历史功能表，保留。各租户保留自己的租户 ID 默认值、部署设置和业务数据；NiFi 证书校验设置按部署维护。

演示环境的实际五库结构单独维护在 [environments/demo/](#demo-database-schema)，本目录表格仍表示本机环境。环境目录只保留当前完整定义，不保存日期快照或业务数据。导出另一环境时必须指定其目录，不能覆盖本机基线。

### 每次结构变更的约束

1. 新增表、增删字段、索引、主键、外键或自动更新时间规则，先修改 `db/` 中所有行业租户的完整定义并审查。
2. 按各租户真实绑定和数据库方言应用同一业务结构；有效公共功能要补齐，清理旧表须核实调用、数据资产和依赖。
3. 运行 `python tools/check-db.py`。工具只读比较行业表集合、列定义/注释/默认值、主键、索引语义与外键列，并检查达梦生成列及自动更新时间触发器；发现差异返回非零状态。约束表达式、触发器具体逻辑及部署视图继续结合完整 DDL 审查。
4. 运行 `python tools/export-db.py`，同步五库完整初始化文件和清单，审查全部 `db/` 差异。不能只更新公安文件，也不能只让表数量相同。

跨库视图依赖的数据库须先存在；控制库与行业库及各自部署视图按实际环境维护。临时增量 SQL、日志、发布包放根 `logs/`；含凭据或业务数据的必要恢复材料保存在仓库外受限目录。Git 只保存最新完整结构与长期维护工具。

### 达梦原生表示

达梦初始化使用原生 DDL，保留实际引号标识符、大小写、MAIN 表空间及存储属性。由已有权限的 DBA 在兼容设置一致的环境初始化；本目录不创建数据库用户。JSON/长文本映射为原生长文本，无符号整数按范围表示，自动更新时间使用原生触发器；目前有 20 个自动更新时间触发器。

登录名字段为 `VARCHAR(50)`，有效登录名生成规则为 `CASE WHEN deleted=0 THEN lower(trim(user_name)) ELSE NULL END`，联合唯一约束为租户 ID 与有效登录名。达梦以虚拟列/函数索引表达；其系统索引元数据会展开依赖列，因此校验使用实际索引 DDL 核对逻辑列。布控连接引用生成列采用同一JSON路径与回退常量；达梦将COALESCE展开为CASE，结构检查逐路径、逐分支和回退值比较。标识值的MySQL前缀索引在达梦以SUBSTR(列,1,128)函数索引表达，检查保留前缀长度、列序和排序方向。

以下是明确的方言表示，初始化文件保存各库真实结构：

| 范围 | MySQL | 当前达梦 |
| --- | --- | --- |
| `res_logical_model_field.entity_id` | 非空、默认空字符串 | Oracle 兼容模式将空字符串视为 NULL，保留可空列，不填写虚假实体 ID |
| `rm_user_t.date_birth` | `DATE` | 原生日期元数据/导出为 `TIMESTAMP(0)` |
| 时间字段 | `DATETIME` / `DATETIME(6)` | `TIMESTAMP`，部分原生列保留更高小数秒精度，校验要求至少覆盖源精度 |

默认数值按数值语义比较，标识符按实际方言引用。上述表示不要求变更整库兼容模式。类型、生成列与修改限制可查阅 [达梦官方数据定义文档](https://eco.dameng.com/document/dm/zh-cn/pm/definition-statement.html)。

### 工具与私有连接配置

从仓库根目录运行：

```powershell
python tools/check-db.py
python tools/export-db.py
python tools/export-db.py --project haitong-data-pull
```

工具默认读取用户目录 `.codex/private/data-elements/resources.local.json`，也可用 `--config` 指定。私有配置含控制库连接及当前 Nacos API/运行配置的租户映射，不提交 Git。改变环境或租户路由后先重新定位，不能从过期 Nacos 数据库缓存猜测目标。环境参数优先取本机环境变量。

独立工程通过私有配置的 `project_databases` 定义，例如 `project_databases.haitong-data-pull` 包含 `dialect: mysql`、`database: haitong` 及实际 MySQL 连接项。支持环境变量占位；账号、口令不写到本仓库。无 `--project` 时导出当前平台五库及已配置的独立工程库；指定工程时只替换该工程的 `init.sql` 和清单条目，其他库文件及清单条目保持原样。未配置的工程、重复输出目录或读取失败都会拒绝替换。

独立工程清单条目带有 `project` 标识。只配置平台连接的其他开发环境导出五库时，会保留已有独立工程基线及其清单条目，不会误删；保留条目不表示本次已连接验证该工程库。

工具使用 `pymysql`；达梦还使用 `jaydebeapi`、`JPype1` 和现有 JDBC 驱动。可用 `DATA_ELEMENTS_DM_JDBC_JAR` 指定驱动，或从当前用户 Maven 仓库发现。导出移除源账号 `DEFINER` 与随业务变化的表级自增计数，保留列自增属性；五库全部成功读取后才写本地文件。

### 登记接入申请字段

`db_table_t.access_request_status`为0未申请、1待处理、2已受理，默认0；`access_requested_by`保存认证申请人标识，`access_requested_at`保存申请时间。申请字段不替代`asset_status`登记进度或NiFi实时状态。MySQL时间使用DATETIME(6)，达梦使用TIMESTAMP(6)。接口和状态规则见[接入任务状态契约](data-elements-haitong.md#access-task-lifecycle)。

现有部署升级时，必须先在每个实际行业业务库检查这三列，再发布申请相关Magic和页面。控制库`baseline`不是这些列的升级对象。完整初始化结构已经包含三列，但将新程序或Magic资源发布到已有库不会自动执行ALTER；生产缺列时只补缺失列，保留已有申请和任务数据。增量执行材料放在当次`logs/`或受限目录，先保存原表结构并核对目标方言，不能直接重跑`init.sql`。升级后重新检查实际列定义，再验证待审批分页和创建任务后的已受理/待启动流转。

<a id="maintenance-tools"></a>

## 统一维护工具

根目录 `tools/` 的工具源码和测试提交 Git，供各工程共用。Magic、低代码和数据库的统一维护入口集中在这里；一次性执行脚本、日志、编译产物、发布包和恢复备份不放在本目录。

| 文件 | 用途 |
| --- | --- |
| `configure-resources.py` | 从当前 Nacos API 配置定位控制库和租户库，把私有连接配置保存到仓库外 |
| `resource-sync.py` | 导出、比较与规划根 `magic/`、`lowcode/` 的资源；在明确目标后发布单个已登记资源 |
| `compile-lowcode.mjs` | 使用澄天现有编译器编译选定 Vue 组件，校验生成代码语法，结果写入 `logs/lowcode/compiled/` |
| `package-haitong.py` | 核对已构建 JAR 的独立 Magic 资源和运行依赖，生成不含运行凭据或业务数据的 Linux / Windows 原生部署包 |
| `export-db.py` | 只读导出平台五库及私有配置中独立工程库的完整结构；`--project haitong-data-pull` 只更新独立海通库及其清单条目，不执行数据库变更 |
| `check-db.py` | 只读校验所有行业租户与 `baseline_ga` 的表集合、列、主键、索引和外键列；按达梦原生语义识别类型、生成列和自动更新时间，差异返回失败 |
| `start-local-backend.ps1` | 从工程外受限配置启动已验证运行包；根目录 `start-data-elements.cmd` 默认也读取同一份 `local-runtime.private.json`，提供前台日志及登录接口就绪检查 |
| `audit-api.py` | 只读整理 Magic HTTP 接口的静态、有限模板及内部依赖引用；可加入受限的当前编译代码、菜单、表单、服务绑定和运行 Java 上下文，报告只写根 `logs/`，不自动删除 |
| `tests/test_resource_sync.py` | 检查资源身份、路径、并发修改、凭据脱敏和发布保护 |
| `tests/test_schema_gate.py` | 检查同数量不同表、额外唯一索引、主键变化、错误租户默认值与 Magic 断链/路径漂移的拦截 |
| `tests/test_api_audit.py` | 检查注释请求、代理/环境前缀、模板条件分支、有限表映射、绝对服务地址及路径参数的引用识别 |

从仓库根目录执行。参数、依赖、私有配置和完整流程见 [本地资源版本管理](#resource-versioning)，命令参数以各工具 `--help` 为准。

```powershell
python tools/resource-sync.py status
python tools/resource-sync.py plan
python tools/check-db.py
python tools/audit-api.py
python tools/export-db.py
python tools/export-db.py --project haitong-data-pull
python -m unittest discover -s tools/tests
```

工具默认只读；`pull` 只写本地副本并保护本地修改。发布需要指定资源、环境和已认证会话，不能因本地缺少文件自动删除数据库资源。含凭据配置和恢复材料保持在仓库外的受限目录，其他运行产物放根 `logs/`。

接口引用清单不能直接当删除计划。菜单导航并非接口请求；动态表达式、资源 ID 调度、外部回调和服务登记需要人工核对。当前运行上下文通过 `audit-api.py --context <受限上下文文件>` 传入，原始内容不写入报告；工具不会读取用户会话凭据或探测生产接口。目录建议见 [资源目录组织建议](#resource-layout)。

<a id="batch-pagination"></a>

## 前后端批量查询与分页规范

适用范围：本仓库静态前端、数据库发布的低代码页面、Magic API、Java 服务，以及列表加载、导出、批量编辑/保存等请求链。该规则是接口设计、前后端联调和代码评审的准入条件。


<a id="batch-pagination--禁止的调用形态"></a>

### 禁止的调用形态

- 表格/列表拿到 `rows` 后，按每行 ID 再调用读接口补字段、状态、计数、权限或字典；`for`、`map`、`Promise.all`、限流队列、组件子项 `useEffect` 均不例外。缓存和并发上限只能降低伤害，不能让这种设计合规。
- 后端在集合、分页结果或批量保存项的遍历中执行 `SELECT`、`selectOne`、`count`，包括间接调用 repository/mapper/helper、ORM 懒加载、Magic `db.table(...).select()`；先逐项查询再拼成一个“批量”响应仍是 N+1。
- 后端在同一集合遍历中逐项读取 Elasticsearch、NiFi 或其他远程服务，同样应先寻找批量接口、一次树读取或有界分组；外部系统确实不支持批量时，须给出调用量上限、超时及失败处理证据。
- 用无上限 `IN (...)`、一次性全量加载、无界并发或前端无限翻页，代替逐行查询。减少请求数不能以数据库连接、内存或响应体失控为代价。


<a id="batch-pagination--接口与页面实现"></a>

### 接口与页面实现

1. 列表接口一次返回当前页实际展示所需的基础字段、可见状态与计数。派生数据在数据库侧关联、聚合，或由后端按本页全部 ID 一次批量查询后在内存映射；不能把补查责任留给页面。
2. 大字段和真正的详情在用户打开单条记录时按需读取。字段/字典/状态等跨多条记录的共同需求提供批量接口，输入去重、数量上限、分页或游标、只返回所需列。前端按有界批次请求，且验证响应 ID 属于本批输入；不要默默接受缺项或越权项。
3. 后端先取得当前会话的租户及数据范围，再对批量中的每个 ID 做可见性校验；禁止信任前端传来的租户/部门 ID。跨租户业务库不能直接合并 SQL 时，先按真实绑定的库分组，每个库一次聚合查询，限制不同库数量及连接并发；此类不可跨库合并的例外需在接口说明和评审中写明。
4. `IN` 参数数量与单页大小按接口显式限制；大量 ID 分段、临时表或数据库原生批量能力处理。使用参数绑定，绝不拼接未经校验的 ID。数据库分页、去重和排序在服务端完成；导出按有界页窗口读取，不一次性全表加载。
5. 批量写入前需要读取校验信息时，一次预取所有相关主数据/版本/关系，再在内存逐项校验并执行写入；业务要求逐项写的事务不等于允许逐项读。


<a id="batch-pagination--验收与评审"></a>

### 验收与评审

- 对同一路径分别准备约 20、100 行/项的数据，记录浏览器读请求数和后端 SQL 数。分页自身、用户明确点击详情除外，请求/SQL 数应为固定或按有界批次增长，不能接近行数。必要时用 SQL 计数器、测试替身或日志断言。
- 覆盖空列表、重复 ID、超上限、无权 ID、跨租户 ID、部分缺失、分页末页和后端错误。批量接口不能通过丢弃无权项掩盖权限错误，除非接口契约明确要求只返回可见子集。
- 检查页面模板及编译产物、请求封装和子组件；检查 Java Service/Mapper、Magic 函数及接口的直接与间接调用。搜索和静态扫描只能发现候选，必须人工追踪调用链并做真实租户回归。
- 代码评审发现 `rows.map(fetch)`、循环内 `repository.findById`、循环内 Magic `db.select`、单行子组件挂载请求时，应要求先提供批量方案与查询次数证据。确有无法批量化的外部系统约束，需记录理由、最大批次、并发/超时/降级与监控，不能作为普通列表默认路径。

Magic 资源可先导出当前启用脚本，再用静态扫描寻找循环内直接 DB 调用候选。扫描不会发现 helper、Java Service 或外部客户端里的间接访问，也可能把逐项写入列为候选；结果须人工分类，不能以扫描零告警代替验收。

允许的独立场景：用户点击一条记录后读取详情；按页加载下一页；固定数量的初始化资源请求；已明确限制的状态轮询。这些请求也要有超时、取消和错误处理，不能借此把自动逐行补查包装成“详情”。


<a id="batch-pagination--表格排序"></a>

### 表格排序

- 可见的时间列、第一列业务名称（如名称、姓名、账号、表名、字段名）及统计列（如数量、次数、大小、长度、进度）必须提供升序、降序和取消排序。勾选框、序号与操作列不算第一列业务名称。
- 持久化业务列表使用后端排序。后端先应用当前租户、角色数据范围及筛选，再对完整结果排序，最后分页；禁止拿到当前页后用浏览器 `sort()` 排序。排序变更回到第一页，翻页保留排序条件。
- 请求统一使用 `sortField` 和 `sortDir=asc|desc`，字段须在各接口的白名单中映射到固定 SQL 表达式；不能把客户端字段名直接拼入 SQL。相同值使用稳定标识作次排序，空值排在末尾。列的显示名称、业务统计单位与实际排序字段一致。
- 排序、列表、总数和统计必须使用相同授权范围。总数和汇总不能从当前页长度或当前页统计推断。分页结果与授权前置读取不能触发逐行补查。
- 已完整加载且明确限量的编辑配置、字段结构或试算样例，可以调用服务端配置排序接口返回行标识；浏览器仅按标识映射原行，不改写保存顺序或行身份。这条规则不能用于持久化业务分页列表，不能把某一页误当成完整配置。配置超过接口上限时应提示并使用对应领域的后端分页接口。
- 树形表格排序须保留父子关系，在服务端决定同级节点顺序。查看时的表头排序与显式拖拽保存顺序属于不同操作，不能在排序展示期间误保存行顺序。
- 验证至少覆盖超过一页的数据、20/100 条时的固定查询数量、同值稳定顺序、空值、非法排序字段及无权/跨租户数据。静态扫描发现排序箭头不代表后端已实现排序；实际请求参数、跨页顺序和权限范围须同时核对。

<a id="lowcode-magic"></a>

## 低代码与 Magic 业务约束

本文件由根目录 `AGENTS.md` 按领域引用，保存现行业务契约与通用页面规则；运行环境、编译和发布步骤由个人 `data-elements-lowcode` skill 提供。Magic / 共享低代码唯一版本源分别为根目录 `magic/` 与 `lowcode/`，资源映射以各自 `.manifest.json` 为准。结构唯一版本源是 `db/`；执行输出只写 `logs/`。首次导出、差异核对及工具用法见 [本地资源版本管理](#resource-versioning)。不能依赖旧工程路径、过程快照或历史发布器。


<a id="lowcode-magic--共享能力与数仓规划"></a>

### 共享能力与数仓规划

万象数据质量的模板、SQL条件边界、任务快照、分页与执行契约见[万象数据质量模板与执行](data-elements-wanxiang.md#quality-templates)。新增模板沿用现有质量规则表，管理与选择编排保留在Magic，不新增等价业务主表或执行引擎。

四个前端独立安装和发布。涉及相同业务能力时复用当前后端、公共 Magic 契约、权威表及租户边界，不能复制业务主表或等价后端；不因这个要求强制抽取会话桥、导航、图标等共同前端包。运行配置中的能力入口若跳转到其他独立应用，应保持对象 ID 和当前认证租户上下文；同页跳转，URL 不携带 token/密码，生产地址从环境配置取得。认证或 API 失败不得默默退回 mock 数据。

数仓分层/分域共用低代码组件 `data-warehouse` 和 `/register/zl-sjzz/list`，契约为 `GET /dwm/center/layer-list`、`POST /dwm/center/datasource-options`、`POST /dwm/center/node/save`、`GET /sym/dict?code=dataSourceType`、`POST /sym/dictSaveOrUpdate` 和 `/sym/tenant/current`。层与逻辑域使用租户 `sym_dict_t`，绑定使用 `dwm_center_layer_source_t`，数据源主表为 `db_datasource_t`。这里的“分库”指逻辑分域，不能执行物理 CREATE DATABASE；无字典 ID 的 DST 组织汇总不作为可编辑分域。绑定将节点类型标为数据中心，不搬移物理数据，解绑不自动恢复历史节点类型。绑定成功不能充当真实连接测试，构建成功不能充当部署完成。


<a id="lowcode-magic--important-pages"></a>

### Important Pages

Low-code component names:

- `register-db`: step 1, register data source.
- `register-dbTable`: step 2, explore/mark tables.
- `register-dbTable-catalog`: step 3 dictionary table registration and step 4 business/log table registration.
- `register-confirm`: step 5 registration review.
- `register-finish`: old completion page; the current flow generally stops on step 5.
- `db-table`: registered database detail, data table information tab.
- `my-db`: database list page.
- `my-app`: application-system list page.

Useful URLs:

- Database list: `http://localhost:3000/#/register/register-sjkb/list`


<a id="lowcode-magic--interface-map"></a>

### Interface Map

Common metadata interfaces:

- `POST /dst/database/metadata/tables/reExplore`
  - Re-explore registered database tables and return differences: added, deleted, modified, unchanged.
  - Legacy difference-comparison contract; source detail no longer calls it on entry.
  - Should not automatically write ES or asset tables.
- `GET /dst/database/metadata/tables`
  - Registration step 2 and source detail share paged saved snapshots. Default `paged=true` remains annotated-only; source detail uses `allSnapshots=true` to include unannotated snapshots, with `includeGovernance=false`.
  - Explicit tenant and source role scope checks precede page reads; no source connection or per-row column queries.
- `POST /dst/database/metadata/tables/collection/start` and `GET /dst/database/metadata/tables/collection/status`
  - Shared registration/source-detail background collection, started only by an explicit probe action. Source detail may read an existing job status without starting one.
- `POST /dst/database/metadata/tables/deleteAsset`
  - Explicitly remove a selected table asset snapshot and related platform registration metadata.
  - Deletes metadata snapshots only, not the physical database table.
- `POST /dst/database/metadata/table/sample-data`
  - Magic API sample-data endpoint used by preview dialogs; requires `tableId`.
- `POST /dst/database/metadata/test-connection`
  - Data source connection test.
- `POST /dst/application/save`
  - Save an application system directly to `sym_application_t` and `data_prop_t`.
- `POST /dst/database/saveOrUpdate`
  - Save a data source directly to `db_datasource_t` and `da_app_db_rela`; connection extensions belong in `db_datasource_t.pool_cfg`. Metadata summaries use the physical summary columns in `db_datasource_t`, not `pool_cfg` or `data_prop_t`.
- `POST /dst/database/push/sync`
  - 当前租户及菜单角色范围内的数据推送配置同步。请求只含 `datasourceId`、可选 `dryRun`；服务器从登记主表生成数据源、应用系统、已完成登记表和字段快照，通过服务间密钥写入独立海通库 `ht_standalone_*`。预检不写库，不接收客户端租户、表字段、目的地址或密钥，也不签发/轮换业务密钥。完整协议见 [海通推送服务](haitong-data-pull.md#platform-registry-sync)。
- `POST /dst/database/assets/saveOrUpdate`
  - Compatibility endpoint for catalog assets. Data tables must use `/dst/database/table/*` and persist to `db_table_t` / `db_table_column_t`.

When testing locally, try the frontend proxy path first if the page uses `dev-api`; direct `8088` calls may require a fresh `token`.


<a id="lowcode-magic--low-code-runtime-publishing-and-registration-modal-rules"></a>

### Low-Code Runtime Publishing and Registration Modal Rules

- Treat `baseline.ui_component_t.compile_js` and `compile_css` as the released runtime artifacts. Saving only `source_code` does not fix a browser page.
- Before accepting a low-code fix, inspect the target component through `POST /sym/component?action=list`. Its compiled JS must be non-empty and, for an SFC compiled by this project, contain a factory that ends by returning the component (normally `return __sfc__`).
- A registration dialog whose header, steps, and bottom actions render while one step is blank usually means that the dynamic step component's compiled artifact is stale or invalid. Inspect that named component directly; do not diagnose it only from the parent wrapper or static frontend source.
- First reconcile the local `lowcode/` source with the current control-database source; compile the confirmed local source and save source + compiled JS + compiled CSS together through `action=saveCode`, then call `/sym/component/cache/refresh`. Verify the refreshed component again through the list endpoint. Do not overwrite newer database source with an old local copy; compare source hashes before publication.
- The component-cache refresh must be usable by ordinary authorized users; do not add a separate platform-administrator gate to `/sym/component/cache/refresh` unless a future explicit authorization model requires it.
- `register-modal` is a static shell around dynamically registered low-code steps (`register-db`, `register-dbTable`, and subsequent registration pages). When it opens, ensure the runtime SFC bundle has been loaded with `loadAllRuntimeSfc(app)` before rendering the step, and surface a visible error if loading fails. Route guards alone are insufficient after a cache refresh, direct portal entry, or hot update.
- Keep the static registration components (`RegisterModal`, `AssetRegister`, `RegisterBody`, `RegisterAction`) registered in the application component entry and directly imported by `BaseLayout`; a Vue warning such as `Failed to resolve component: register-modal` is a blocking integration defect, not cosmetic logging.
- `useRegisterModal` must have a safe shared/fallback context for low-code pages that are evaluated outside the normal Vue injection tree. Opening registration must never silently create an empty dialog.
- Cache refresh normally removes the need for a backend restart. A restart is a recovery step only after verifying the saved runtime artifact and the two cache levels.


<a id="lowcode-magic--dynamic-forms-form-create-and-form-configuration"></a>

### Dynamic Forms (`form-create`) and Form Configuration

The frontend uses `@form-create/element-ui` to render configurable business forms. Treat this as a tenant-owned dynamic-form subsystem, distinct from the shared low-code component subsystem.


<a id="lowcode-magic--ownership-storage-and-tenant-boundary-mandatory"></a>

#### Ownership, storage, and tenant boundary (mandatory)

- The form-configuration page is the released low-code component named `form-config`, available at `/setting/form-config`. It lists, edits, previews, creates, renames, and soft-deletes form definitions for the active environment.
- Dynamic form definitions are stored in **each selected tenant's business database**, in `sym_form`; they are not stored in `baseline.ui_component_t`, `baseline.api_file_t`, or another control-plane table. Resolve and record the active tenant/database before inspecting or changing a form.
- `sym_form` fields are: `tid`, `form_name`, `form_json`, `form_config`, `env`, `created_time`, `updated_time`, `is_del`, and `tenant_id`. `form_json` and `env` are required; deletion is logical (`is_del=1`). The delivered schema has no physical uniqueness key for `(form_name, env)`, so preserve the application's duplicate check and audit for duplicates when directly repairing data.
- All form read/write/delete requests use `POST /sym/form?action=get|save|delete`. The Magic resource is `/magic-api/api/09.系统管理/12.动态表单/01.动态表单配置.ms`; it delegates to the backend `tenantForm` Magic module.
- The backend module `TenantFormMagicModule` obtains the database only from `TenantContext.requireTenantId()` and `TenantDataSourceRegistry.dataSourceForTenant(...)`. Never add a request `tenantId`, route/query tenant value, form record's historic `tenant_id`, or browser-selected value as an alternate source of business-data ownership. A missing tenant context must fail closed.
- `tenant_id` is recorded from the active server-side tenant when a row is inserted. Do not let the client choose it. Preserve the current tenant's data-source routing even if the physical schema also contains `tenant_id`.


<a id="lowcode-magic--configuration-contract"></a>

#### Configuration contract

- The authoritative editable payload is `sym_form.form_json`. `form-config` currently stores an array-like JavaScript/form-create rule expression (for example `[ { type: "input", title: "名称", field: "name", ... } ]`) and renders it with `<form-create :rule="...">`. The administrative editor presently evaluates this expression in order to support the existing configuration format; do not silently change existing records to a different schema without a migration and backward-compatible reader.
- The visual `FcDesigner` screen is `data-elements-chengtian/src/views/lowcode/designer.vue`. Its designer payload is a different wrapper format: the outer `formJson` is `JSON.stringify(data)`, while `data.rule` and `data.options` are themselves serialized strings. When consuming or modifying designer-created forms, parse the outer JSON first, then parse `rule` and `options`; when saving, preserve that same nesting.
- `form_config` is optional auxiliary configuration. Preserve it on read/update unless the feature deliberately owns it; do not move rendering rules out of `form_json` merely because the column exists.
- Every data-bearing rule needs a stable, non-empty `field`. Use a unique field name within the form. Layout/display-only rules (such as `UTitle`) may omit `field`; they must not be included in persistence payloads.
- Keep form-create rule data serializable. Use the project helpers `FormUtils.toJson` / `FormUtils.parseJson` where functions or form-create's special JSON encoding are involved; do not replace them with plain `JSON.stringify` for rules that contain callbacks. Retain the project's registered effects/components from `data-elements-chengtian/src/plugins/formCreate.ts` and the custom rules/components they depend on.
- The shared renderer is `data-elements-chengtian/src/components/form/json-form.vue`. It clones incoming rules, supports preview/read-only mode, and exposes validation/value methods. Reuse it for normal business-form rendering rather than reimplementing rule parsing, save-field filtering, or validation in each page.
- `json-form` considers fields with `model === "0"` or `0` to be dynamic fields. Its `get2SaveData()` separates ordinary `baseData` from `dynamicData`; use that split when the receiving interface distinguishes master columns from dynamic properties. `getSaveData()` excludes `ignore` rules and normalizes saveable blank values to empty strings by default.
- Retain the current presentation defaults unless a product requirement says otherwise: no built-in submit/reset buttons, right-aligned `180px` labels, no label colon, and `FormUtils.fixJson` defaults for placeholders, clearability, full-row label width, dates, and number controls.


<a id="lowcode-magic--api-and-editing-rules"></a>

#### API and editing rules

- `get` accepts optional `tid`, `formName`, and `env` filters and returns rows from the current tenant only. Pass the correct `env`: ordinary runtime helpers use `$setting.systemCode`; the designer uses its route `pid`; the configuration page uses its selected current environment. Do not assume a same-named form in another environment is the intended definition.
- `save` requires `formName` and `formJson`, and creates a new row only when `tid` is absent. On create, the backend rejects an existing active row with the same `(form_name, env)` in the active tenant. On update, send the complete persisted payload—at minimum `tid`, `formName`, `formJson`, and `env`, plus `formConfig` when it is owned—because the backend replaces those values rather than performing a partial patch.
- In particular, do not implement a rename request that sends only `tid` and `formName`: the current save contract requires and writes `formJson`, so load/preserve the current configuration before renaming. Likewise, do not write an empty `formConfig` or change `env` incidentally during a rule-only update.
- `delete` requires `tid` and performs a soft delete. Before adding or using deletion in a business flow, find every form lookup/caller by its `formName` and environment; deleting an active form can make the target dynamic page unusable.
- Validate syntax before saving and display a clear configuration error instead of persisting an unparsable rule expression. Preserve unsaved-change confirmation when switching forms, changing environment, or navigating away from the configuration page.
- Do not use `eval` on new, arbitrary user-generated content outside the existing compatibility boundary. If the format is modernized, introduce an explicit safe parser/validator and a migration plan rather than broadening executable configuration privileges.


<a id="lowcode-magic--change-and-validation-workflow"></a>

#### Change and validation workflow

1. Identify the target form name, `env`, active tenant, and tenant database. Query only the tenant `sym_form` row(s), including `tid`, `form_json`, `form_config`, `is_del`, and timestamps; do not confuse it with the shared `form-config` page source in `baseline.ui_component_t`.
2. Back up the exact form record before a persistent tenant-database change. For page code changes, first compare the current `form-config` runtime source/artifacts against its canonical `lowcode/` file and reconcile any changes according to the local-first publishing workflow.
3. Keep the existing storage format for the selected form. Verify that every form-create `type` is registered/importable in the runtime, custom effect names exist in `formCreate.ts`, each persisted data field is unique, and rule references/slots still match the consuming page.
4. Test with a real authenticated session in the intended tenant: load `POST /sym/form?action=get` with the target `env`, render the actual consuming page, populate values, validate required fields, verify preview/read-only behavior when applicable, and inspect the API payload (including `baseData`/`dynamicData` where used). Repeat after switching to another tenant to prove definitions neither leak nor overwrite across tenant databases.
5. If the changed artifact is the shared `/setting/form-config` UI itself, publish `source_code`, `compile_js`, and `compile_css` together to `baseline.ui_component_t`, invoke `/sym/component/cache/refresh`, and re-open the page. A `sym_form` data-only update does not require component-cache refresh or a backend restart.


<a id="lowcode-magic--dynamic-detail-page-completeness-and-save-performance-mandatory"></a>

#### Dynamic detail-page completeness and save performance (mandatory)

- For a page rendered through `base-detail` / `json-form` (for example `/register/register-sjkb/detail`), `sym_form.form_json` is the source of truth for every user-visible form element: data fields, display order, sections, titles, labels, required/visible/read-only rules, selector choices, icons, and title-toolbar actions. Do not add page-specific controls or labels in the consuming low-code component merely because a particular detail form currently needs them.
- Keep shared component code declarative and generic. It may render a `UTitle` rule's configured `props.actions`, dispatch standard editing actions, resolve documented placeholders, and render configured control props; it must not branch on a business title such as `基本信息`, form name, database type, or page route in order to inject business UI.
- Configure `UTitle` toolbar actions in `props.actions`. An action has at least `key`, `label`, `visibleWhen` (`view`, `editing`, or `always`), and optional `icon`, Element button props, `requiresEditable`, `successMessage`, and `request`. Standard keys are `edit`, `cancel`, and `save`; nonstandard actions supply `request: { method, url, payload }`. Supported request placeholders must be explicit and stable (currently `$tid` and `$detailClass`); add a generic resolver instead of hardcoding an action per page.
- A control whose options are business choices must obtain those choices from its form rule props. In particular, the `db-type-select` rule carries `props.options` with group label/name and option `{ label, value, icon }`; database-type names and icons are not to be maintained as a second hardcoded page list. A legacy fallback may remain only to render older records while their `sym_form` rows are migrated.
- 数据源登记状态以 `db_datasource_t.asset_status` 为准：`0` 为“待登记”（尚未结束登记），`1` 为“未登记完成”（已结束登记，但仍有有效表状态不为 `2`），`2` 为“已完成”（全部有效表状态为 `2`）。第五步允许结束部分登记，服务端汇总当前租户、当前数据源下 `is_del=0` 的全部表决定状态，不信任客户端传入的完成状态；空表清单保留为 `1`。暂不处理、过程表、备份表等如果仍处于表状态 `0`，也不能把数据源算为全部完成。数据源状态 `1` 不代表审批中，不复用目录审批状态字典。
- 数据源管理卡片的“字典表／业务表／日志表”数量仅统计当前租户、可见数据源下 `is_del=0` 且 `db_table_t.asset_status > 0` 的对应类型表，状态 `1`、`2` 均计入，`0`、NULL、负值不计入。主表批量聚合为权威来源，不能沿用 ES 残留数量；前端备用统计保持同一规则。“数据表总数／已标注／待标注”仍使用探查与标注范围，不因本规则排除未登记表；数据源本身是否结束登记不影响管理卡片这三类数量。
- 海通来源节点的 `incrementalColumn` 是持久化的流程配置，不是随启动或停止重置的运行水位。只有新建模板时根据来源表登记的抽取时间初始化一次，不按主键或字段名猜测；Java 模板与 Magic 接入任务创建保持同一规则，以本跳来源表明确登记的时间戳匹配真实物理字段名。级联下游无登记选择时可沿用已生成 ODS 契约的 `ODS_GXSJ`，不能覆盖明确登记的选择。来源节点的增量配置在全量模式下也保持可查看、可预先编辑，切换增量同步后生效；不能只显示空分组标题，也不能为显示字段而自动修改已保存的同步方式。打开已有画布、来源配置面板、保存和启动均不异步重新读取登记配置或自动补齐历史任务；历史任务缺失字段由用户手工填写。保存及启动只校验当前配置，不能用登记值覆盖手工选择；缺少必填主增量字段时明确提示并拒绝增量操作，全量同步不依赖该字段。停止、监控刷新和保存回读必须保持已保存的字段及完整设计配置，服务端返回空值或不同字段时不能静默覆盖当前画布。
- 数据库来源发布使用现有 `com.linewell.microservice:datadevs-database-nar:2.9.0` 的 `GenerateDateTableFetch`，不依赖自创后缀版本。按月、间隔 1、`Partition Size=10000`、`Max Wait Time=0 seconds`、Min/Max Date 默认未设置，不自动填入当前时间或最大时间。普通增量及首次全量后增量的 `Maximum-value Columns` 与 `Column for Value Partitioning` 同为初始化自来源登记表的时间戳字段；单次/定时全量只保留时间分片字段，不启用增量水位。现有 2.9.0 未声明 `Initial Load Strategy`，不得把该属性作为动态属性写入；“从当前最大值开始”保留既有原生 QueryDatabaseTableRecord 初始化路径，不能变成历史全量，也不使用 Max Date 模拟初始水位。新模板的 Java/Magic 默认批量大小同为 10000，来源 ExecuteSQLRecord 每个 FlowFile 最多 10000 行。发布前一次核验指定扩展并在内存校验所有来源配置，禁止逐节点补查登记表或读取来源数据。非数据库协议保留相应采集组件。
- 表保存、重新标注、驳回、删除快照、模板导入及探查新增表后同步数据源状态；未结束登记的 `0` 不因单表保存自动升为 `1/2`。数据接入树、列表、统计及任务创建采用相同范围：`db_datasource_t.asset_status > 0` 且 `db_table_t.asset_status > 0`，并保留有效记录、业务表/日志表/字典表、当前租户和角色数据范围条件。状态 `0` 或 NULL 不计入接入候选；状态 `1` 的表也可接入，但数据源“全部登记完成”的聚合仍要求所有有效表状态为 `2`。推送契约仍只发布状态 `2` 的表，其发布范围独立于接入候选。接入任务展开区按真实任务来源展示“来源库／来源表 → 目标库／目标表”的数据链路，级联任务不能用根来源替代上一跳，历史多目标任务须展示全部已授权目标；所需元数据随分页接口批量返回，不在展开时逐任务补查。状态聚合每批最多 `100` 个数据源，在集合外一次分组查询，不逐表/逐数据源补查。
- The `是否可连通` field binds to `connectionStatus` / `connection_status`, is display-only in the detail form, and its result may be updated only by an actual connection test or the deliberate removal of connection information. Enforce this in the save API as well as by disabling the form control.
- Before changing a detail page, audit the complete runtime path: the consuming component, `base-detail`, `json-form`, all custom rule components, and the selected tenant's `sym_form` row. Move any discovered page-specific visual element or behavior into the applicable form configuration, unless it is a truly generic renderer capability or an authorization/data-integrity rule that must stay server-side.
- Saving the authoritative tenant database record must complete before success is returned. Non-authoritative search/index projection is best effort and must not hold the edit dialog open: request `deferIndexRefresh` for database-detail saves, return `indexRefreshPending: true` from the save API when accepted, then schedule `/dst/maintenance/refresh` after the UI has returned to view mode. Preserve error logging and reconciliation capability; a background index failure must not make a successfully persisted form save appear to fail.
- Validate a dynamic-detail change in an authenticated intended tenant: confirm `/sym/form?action=get` returns the configured actions/options, verify no duplicate hardcoded buttons/options remain, open edit mode, check type icons in both selector and read-only display, save a representative record, and measure that user-visible completion no longer waits on search-index refresh. Repeat against a second tenant where that form exists to prove tenant isolation.


<a id="lowcode-magic--data-access-page-interaction-and-responsive-layout-rules"></a>

### Data-Access Page Interaction and Responsive Layout Rules

- 画布支持独立配置与数据中台登记集成两种入口。直接添加来源/目标并填写连接、物理表名和时间戳列的流程，不要求预先登记数据源或数据表；没有登记引用不属于权限错误，也不应显示“来源表关联不完整”或降为只读。独立流程仍须绑定当前行业租户及正常画布权限。登记集成流程继续使用真实登记 ID、登记字段治理规则和接入链路；已明确引用的数据源、数据表、字典及服务按当前租户、角色范围逐 ID 校验，不能因独立模式忽略无权或不存在的登记 ID。
- 接入任务中的 `source_db_id` / `target_db_id` 和 `source_table_id` / `target_table_id` 只保存真实登记引用，不用 JDBC URL、物理库名或表名填充。历史画布记录中确实等于当前手工连接及目标物理表名的值按配置值兼容；其他未知 ID 仍须拒绝。未绑定登记来源表的任务展示当前独立画布，不虚构上下游链路；确有登记链路损坏时继续显示准确提示并保留已保存配置。

- On the data-convergence / task-management page, implement confirmation dialogs with imported `ElMessageBox.confirm`; do not assume that a dynamically evaluated low-code component has an injected global `$confirm`.
- A reject-for-modification action must send the real table identifier (`sourceTableId`, then `tid`, then `tableId`) and datasource identifier to `/ods/dataAggReject`. It returns the table to re-registration/rejected state; it must not delete the datasource.
- Magic API maintenance can leave duplicate rows for the same HTTP method/path. Before relying on or changing a route, test the active runtime route with a harmless missing-parameter request, inspect the duplicate rows, and retain exactly one clearly active implementation after a reference audit. Do not delete legacy rows blindly.
- Split-pane inventory and data-access pages must not add a page-level horizontal scrollbar. Give outer panes and right panels `min-width: 0` with controlled `overflow: hidden`; keep any necessary table scrolling inside the table only. Toolbars and filters must wrap, and keyword inputs should use responsive widths rather than forcing a fixed wide layout.


<a id="lowcode-magic--table-toolbar-conventions"></a>

### Table Toolbar Conventions

- For management-list tables, place the primary creation action at the left of the top toolbar. Use a plus icon and a specific label such as `新增租户`, `新增角色`, or `新增用户`; do not leave the action as an ambiguous `新增`.
- Place keyword filters/search controls at the right of the same toolbar. The search input should contain a blue, clickable magnifier at its right edge, invoke the query on click and Enter, and retain a clear action that restores the full list.
- Do not add a separate refresh button by default. Add one only when the user explicitly needs an operation that cannot be represented by search, clear, pagination reload, or the normal post-save refresh.
- Keep the toolbar responsive: preserve the create action and search as distinct controls, allow them to wrap on narrow screens, and avoid forcing a page-level horizontal scrollbar.


<a id="lowcode-magic--standard-split-tree-management-list-my-db-template"></a>

### Standard Split-Tree Management List (`my-db` Template)

Use the released `my-db` page at `/register/register-sjkb/list` as the default visual and structural template for inventory lists that have an organization tree at left and a paged management table at right (for example, `my-catalog`). Keep domain fields, APIs, and operations specific to the target page; reuse the layout contract rather than copying data-source behavior.

- Use `el-splitter` with a `300px` default left `el-splitter-panel` for administrators. The left tree is `UTree`, with its built-in `search`, `search-placeholder`, and `loading` support; do not add a second manually managed tree-search input unless the target has a distinct filtering need. Expand only the initial organization path (`全部 → 第一层机构 → 内设机构`) on first load; leave all lower-level departments collapsed.
- A left-tree or left-classification header's add action is a compact icon action, separate from the main-list primary creation button. Match the data-standard classification reference: `<el-button type="text" class="left-header-btn"><Icon icon="el-icon-plus" /></el-button>`, with `padding: 4px 8px`, `color: var(--el-color-primary)`, and hover `background-color: #1890ff1a`. Do not substitute a filled/circular primary button or a neutral custom icon treatment. Keep its title and accessible name specific to the object being created.
- Use `card-container ... w-full h-full` plus `datasource-explorer-page` / `datasource-explorer-splitter` styles. The page root, splitter panels, and right panel need `min-width: 0`, `min-height: 0`, and controlled `overflow: hidden`; tables may scroll internally but the document must not gain a horizontal scrollbar.
- The left panel is a column: title/header first, then the custom tree fills the remaining height. Preserve tree label ellipsis and its right-side breathing space. Do not set an arbitrary persistent width on the tree itself.
- The right panel is a flex column. Its toolbar is a `list-toolbar`: primary creation action left-aligned; `list-filters` right-aligned and content-sized rather than stretched to fill the row. Give the right panel an inline-size container and let select/search controls grow within readable ranges rather than locking them to a small fixed width: ordinary selects `120–160px`, status selects `108–144px`, and keyword inputs `210–360px`. This preserves a compact row in a constrained panel while allowing long placeholders to become fully readable on wide screens. Below the narrow breakpoint filters may wrap, with selects sharing a row and the keyword field using a full row. Use concise product labels such as `登记目录` instead of `登记数据目录` when the page context already identifies the object.
- The keyword input is clearable, searches on Enter and on a blue clickable magnifier, and restores the full list on clear. Reuse the project `data-table`; do not introduce a standalone table/pager pair. Default to the shared page size unless the product requirement explicitly needs another size.
- Pagination comes only from `data-table` → shared `Pagination`. The shared component must not wrap pagination with `el-scrollbar`; use a compact `4px` top spacing in both the shared pagination and table wrapper. In a constrained right panel, use a container-query rule to hide the total-record text and jump control below `680px`, then page-size control below `480px`; keep previous/next, page numbers, and page size when space allows. Do not clip pagination or create a page-level horizontal scrollbar.
- Table alignment follows the Ant Design data-format convention: ordinary text headers and values (including English and Chinese names) are left-aligned; explicit state/tag columns may remain centered; operation values are right-aligned while the `操作` header is centered. Numeric columns declare `type: "num"`, have a fixed `precision` for the column (default `0`), use thousands separators by default, put any unit in the header, and align both header and values right. Use `--` for empty numeric values. Do not append a repeated unit such as `张` to every numeric table cell.
- Operation columns are fixed right, right-aligned, and compact. Do not reserve a large trailing gap after the final action; the right edge padding should match the table's normal cell padding. When every row has exactly one short action (for example, `创建任务` or `任务设置`), use a narrow column and center that one action so its left and right whitespace are visually symmetric; do not retain a wide right-aligned action column that leaves a large blank area on the left.
- Validate at the target desktop viewport: left panel is 300px, toolbar controls share one row, `document.documentElement.scrollWidth === clientWidth`, the page root has no horizontal overflow, and the pagination element has no horizontal overflow. Refresh the low-code component cache after publishing source, compiled JS, and compiled CSS.

- 接入任务当前表视图复用响应 `accessStatus`：`0` 待接入、`1` 已接入、`2` 待审批、`3` 待启动；已接入由 NiFi 实际运行判定，筛选先于数据库分页。登记进度和登记审批字段继续保持原职责，完整口径与登记申请与任务受理规则见 [接入任务状态契约](data-elements-haitong.md#access-task-lifecycle)。


<a id="lowcode-magic--huawei-mrs-hive--kerberos-configuration-rules"></a>

### Huawei MRS Hive / Kerberos Configuration Rules

- Do not store a keytab in the repository, low-code source, Elasticsearch, or database configuration. Put it in a server-only restricted secrets directory; datasource configuration stores only paths and non-secret connection settings.
- The supplied `krb5.conf` and `user.keytab` identify the realm/KDC and client principal, but they do **not** supply the HiveServer2/HA ZooKeeper endpoint, Hive service principal, or target Hive database. Do not claim that two file paths alone are sufficient for a connection.
- Prefer a concise standard setup: `keytab` path, `krb5.conf` path, MRS client configuration directory or `hiveclient.properties` path, and Hive database (default allowed). Read endpoint/service-discovery values from the MRS client configuration; expose advanced manual overrides only when necessary.
- Validate Kerberos/Hive on the host that will run the service or NiFi process, because local desktop paths and credentials are not portable to a server.
- Hive 分区入湖除已有 HiveServer2/HDFS 配置外，服务端 MRS 客户端目录须提供含 `hive.metastore.uris` 的 `hive-site.xml`，Kerberos 模式沿用已登记服务端 principal/keytab 及 `core-site.xml`。部署按本次计划一次调用 Metastore `getPartitionsByNames` 批量读取真实分区 Location（最多2000个），保留自定义分区目录；不能按分区逐条 DESCRIBE，也不能猜测或覆盖目录。目标表和 NiFi CSV Writer 均须使用 Ctrl-A 文本格式；缺少实际分区、权限或配置时拒绝部署，不将数据写入未核验路径。
- Hive 分隔符核验按 `field.delim`、`serialization.format`、默认 Ctrl-A 的优先级解析，兼容 `DESCRIBE FORMATTED` 的控制字符转义、数字字节值及格式化空白；去除空白时不能吞掉真实的 U+0001。真实格式冲突须显示目标库表、属性、实际值及要求值，与连接/认证失败分别报告；不能自动修改已有表或全局 Writer 来绕过冲突。


<a id="lowcode-magic--magic-api-organization"></a>

### Magic API Organization

- Public route roots are fixed by domain:
  - `/dst`: data inventory. Its direct children are `/application`, `/database`, and `/catalog`.
  - `/ods`: data access, ETL, and NiFi orchestration.
  - `/dwm`: data governance.
  - `/dws`: data services and cloud-gateway operations.
  - `/das`: data applications, application repository, screen designer, and application-tab operations.
  - `/idaas`: unified identity management and its explicitly configured tenant-receiver interfaces; platform and tenant-local accounts remain separate.
  - `/sym` and `/portal`: the existing tenant system-management and portal routes. Preserve their deployed paths when improving Magic editor names or documentation.
- A Magic group metadata `path` must contain one URL segment only. Compose deeper URLs through nested groups; never put `dst/database` or another multi-segment value in one group.
- Number sibling API groups, APIs, functions, and tasks with two digits: `01.`, `02.`, `03.`. Do not use unnumbered names, `1.1`, `未定义名称`, `test`, or copied names in active resources.
- Physical `file_path` numbering is for readable ordering. Runtime imports and routes come from group/API metadata `path`; do not rewrite imports merely because a display/file name gained a numeric prefix.
- Direct database moves must preserve the complete resource tree:
  - Every group has one `group.json`.
  - Every physical folder has one active directory row ending in `/` with `file_content='this is directory'`.
  - This applies to `api`, `function`, and `task`. Missing function/task directory rows can leave scripts visible in the database while cold-start imports fail with `找不到函数`.
- After folder or numbering changes, restart the backend and inspect cold-start `MagicApiRuntimeProbe`: `rootFunctionDirs`, `rootTaskDirs`, and `missingFileGroups=[]`. A hot registry is not sufficient evidence.
- Before deleting an interface, cross-check low-code `source_code` and `compile_js`, the four current frontend `src` trees (`data-elements-chengtian`, `data-elements-haitong`, `data-elements-idaas`, `data-elements-wanxiang`) plus known external callbacks, Magic imports, Java callers, scheduled tasks, and known external callbacks. Referenced but empty/broken interfaces must be implemented, not deleted.
- NiFi clients and cloud-gateway signing/token/HTTP clients may remain Java runtime adapters. Parameter validation, orchestration, persistence, and response shaping belong in Magic API.
- Shared inventory aggregations such as `/dst/statistics/common` belong in the dedicated `02.数据盘点/04.盘点统计` group. Do not move a shared endpoint into one page domain merely because one caller is being debugged; verify every low-code caller first.
- Personal-center Magic resources belong under `01.平台门户/07.个人中心`; their nested public route is `/portal/account`.
- Data-application Magic resources belong under `07.数据应用` and use `/das` as the public root. Do not retain `/workshop` calls in low-code source/compiled JS or any frontend project.


<a id="lowcode-magic--magic-api-readability-and-documentation"></a>

#### Magic API readability and documentation

- Keep public interface URLs and function import paths in their existing English form. Give each active interface and function a clear Chinese display name. Within each sibling group, use consecutive two-digit display numbers starting at `01`; renumber the remaining siblings after moving or retiring a resource. Keep the physical file name in sync with its display name while preserving the Magic metadata `id`, route `path`, method, and group identity.
- The Magic editor `description` is a business explanation, not a repeat of the name or an English route. State what the interface reads or changes, key input and validation rules, authentication and tenant/application scope, important persistence or downstream effects, and the returned result or meaningful error. Describe unfinished compatibility endpoints truthfully as unfinished. Give shared functions equally specific descriptions and readable parameter descriptions.
- Put an interface-level comment at the start of every script with its purpose, HTTP route, and business flow. Add concise Chinese comments beside decisions that are easy to misunderstand: scope checks, important branches, version/transaction handling, data synchronization, and writes. Format MagicScript consistently so the flow is readable in the visual editor. Do not move endpoint-specific business logic into a one-caller function merely to shorten the endpoint; retain functions for rules actually reused across interfaces or required shared framework boundaries.
- Format complete static SQL as MagicScript's SQL-aware multiline literal `"""..."""` when supported by the installed runtime/editor, with readable clauses and indentation. Preserve Magic binding expressions (`#{...}`), identifier/alias case, quoted values, and SQL semantics exactly; those can affect parameter binding and result keys. Keep dynamically assembled SQL fragments in their necessary string form and validate identifiers with a whitelist. Do not mechanically convert all strings or uppercase SQL tokens.
- Before bulk editing, inventory active `api_file_t` rows and back up the exact affected rows. Match resources by the Magic metadata `id` when unique; if legacy rows share an ID or have no row `tid`, distinguish them by their exact `file_path` and content, and report editor-invisible orphan resources separately. Check for duplicate paths and preserve the JSON/`================================`/script format plus directory and `group.json` records. After publication, refresh or cold-load the Magic registry, inspect representative scripts in the editor, and test real read and write routes for each affected business area. A database-only comparison or unrefreshed test does not prove the new script runs.


<a id="lowcode-magic--magic-api-metadata-management"></a>

### Magic API Metadata Management

- The Magic API folder `/magic-api/api/02.数据盘点/02.数据库表/01.元数据管理/` is the target home for metadata operations that were formerly implemented in Java. Do not assume an interface is unused just because the current script is empty, `null`, or only calls `MetadataAssetService`.
- For 06 metadata interfaces, classify each file before deleting or rewriting:
  - **Useful and implemented**: keep, verify with an authenticated HTTP request, and preserve route compatibility.
  - **Useful but only a Java wrapper/empty shell**: implement the Magic script instead of deleting it.
  - **Truly unused**: delete only after checking frontend calls, Java controller/service calls, Magic resource tree, and related workflow usage.
- Prefer Magic scripts that make the data flow explicit: load assets from ES with database fallback, build `DataSourceConfig`, call low-level metadata utilities such as `MetadataExplorerService` / `MetadataDataQueryService`, then write asset tables and ES only where the endpoint semantics require persistence.
- Add a short header comment to each metadata Magic script explaining purpose, read/write behavior, core tables touched, and safe validation mode. For write-heavy interfaces, support `dryRun=true` when practical so validation can avoid dirtying asset data.
- Use explanatory comments for purpose and data flow, but do not leave commented-out legacy Java code, old test branches, disabled imports, or stale implementation blocks in scripts.
- Do not leave stale response samples that imply broken behavior, huge historical payloads, or `literal cannot be used alone null` failures after fixing an interface.
- Updating `baseline.api_file_t` directly is not enough if the backend is already running. Use Magic editor save endpoint `POST /api/web/resource/file/api/save` with the parsed JSON metadata plus `script` field, or restart the backend, then verify the business route.
- After moving or bulk-updating Magic files, query each active API with `GET /api/web/resource/file/{metadata.id}` and verify `data.script` is not null. A row may contain code after the `================================` separator while the running Magic registry still has a null script. Re-save those files through `/api/web/resource/file/api/save`, then verify again.
- The identifier used by Magic editor detail/save APIs is the JSON metadata `id`, which can differ from `api_file_t.tid`. Do not assume the table row ID is the runtime API ID.
- Every Magic directory needs one active directory row whose `file_path` ends with `/` and whose `file_content` is exactly `this is directory`. Keep one `group.json` and one script row per path; audit duplicates after direct database maintenance.
- When moving a Magic group, search all Magic scripts for internal imports such as `@post:/old/path` and update them together with frontend calls. A valid group tree can still fail at runtime if cross-script imports retain the old route.
- For destructive or persistent endpoints, verify non-destructively first: use missing-parameter tests, invalid IDs, or `dryRun=true`; do not create/drop/delete real assets unless the user explicitly asks or the workflow requires it.


<a id="lowcode-magic--project-specific-rules"></a>

### Project-Specific Rules

- Avoid automatic save/submit behavior unless the user explicitly asks for it; prefer explicit Save/Next/Delete clicks.
- Asset registration has no approval workflow:
  - Do not expose or call an asset `submit` endpoint.
  - 应用、已完成登记的数据表及无需审批的目录保存为直接可用（`asset_status=2`、`flow_status=2`、无 `flow_order_id`）。数据源使用上述 `0/1/2` 登记进度，结束登记后 `flow_status=2`，无需审批。
  - Do not create legacy order-asset relation rows for `app`, `db`, `table`, or `catalog`.
  - Registration steps after the first data-source step show only Previous/Next; they must not auto-save or auto-submit.
- The standalone approval center remains available independently of asset registration:
  - Keep the Warm-Flow dependencies, `com.linewell.dataelement.flow` runtime package, approval controller/service, flow suggestions, and flow relation infrastructure.
  - Its Magic tree is `/magic-api/api/09.系统管理/08.审批中心/`; public routes start with `/sym/approval`.
  - Warm-Flow completion uses the current `/sym/approval/callback/finshHandle` Magic contract. Permission resolution uses `ApprovalPrincipalService`; unused legacy permission/query wrappers are not restored.
  - Do not reconnect `register-db`, `register-dbTable`, catalog registration, or registration completion to automatic approval submission.
  - Associate current approval operations through the authoritative `data_apply_form_t` / `res_catalog_publish_request` business ID and Warm-Flow `flow_instance.business_id`, not a second asset-order relation table. Load the business record with an explicit current-tenant predicate, resolve applicant/organization and display information in one bounded query, and check initiator ownership or current-tenant organization membership. The subscription and publication approval types must match their actual business records; asset registration must not be reconnected to the retired `checkIn` / `assetUpdate` path. Keep current subscription, publication, task handling and callback contracts.
- Application systems use `sym_application_t` as their only master table. Data sources use `db_datasource_t` as their only master table. Preserve migrated IDs in each table's `tid`; do not recreate app/db rows in `da_asset_t`.
- Data tables use `db_table_t` as their only master table and `db_table_column_t.table_id` for field metadata. Preserve migrated table IDs in `db_table_t.tid`; do not create table rows in `da_asset_t`, `da_asset_table_column_t`, or `data_prop_t`.
- Every data-table property used by registration, governance, preview, resource-center, or indexing logic must be represented by a physical `db_table_t` / `db_table_column_t` column. Do not add `data_type='table'` dynamic properties.
- Data-source connection options and migration extensions belong in the valid JSON object stored in `db_datasource_t.pool_cfg`. Data-source metadata summaries belong in the physical `db_datasource_t` summary columns; data-source interfaces must not read or write `data_prop_t` for these values.
- 数据源登记的显式“保存”提交主记录后，对提供连接的数据库刷新一次概览，更新版本、表/视图/过程/用户数量和容量；不隐式全量登记表或逐表补查。远程采集失败保留已完成的保存并提示可重试；未提供连接的手工快照不执行物理探查。详情显式编辑保存遵循同一规则。
- 数据推送服务与平台分别运行：海通的 FTP/Kafka 投递目标仅来自其本地 YAML，业务请求不回调平台或行业库。平台的推送来源独立解析当前租户 ODS/FJGHCC 绑定的文件存储，目录为 `根目录/登记源ID/表名/`；两侧须由部署配置对齐。手工登记按字段快照物化，不要求来源 SQL 方言。任务预检及生成均将 `receive/push` 解析为该文件来源，并校验文件存储的租户与角色范围。
- 推送文件接入策略由 Magic 设置 `fileConsumptionPolicy=DELETE_AFTER_DATABASE_COMMIT`。NiFi 编译器以完成标记清单、持久队列及单 JDBC 目标实现该通用协议：仅列举 `.json.done`，下载时保留源文件，数据库成功提交后删除对应 JSON 及标记；失败或隔离记录不触发删除。多目标须拆成接入链路，首个数据库承接可靠落地后继续后续库；普通主动抽取和手工上报不自动启用该删除策略。主键更新用于异常重试幂等，不能把“下载完成”当成“落库成功”。
- 推送文件的字段处理使用 `FAIL_FLOWFILE`，任意记录失败时保留整份输入，不能仅将部分成功记录写库后删除整份源文件。推送链路后续数据库节点在对应字段存在时继承来源登记的时间戳字段，避免退回只有日期精度的 ODS 水位而漏掉同日新批次；已保存、已发布的流程须显式迁移，保留原生来源组件标识及水位/文件状态，不能清空状态绕过重建保护。
- 数据抽取类型卡片按 MySQL、Oracle、OceanBase MySQL、OceanBase Oracle、人大金仓、PostgreSQL、达梦、Hive、GaussDB、FTP、Kafka、MinIO、Elasticsearch、GBase 8a、SQL Server、Vertica 排列；MaxCompute 不再作为新建选项，历史登记的类型映射仍保留。
- ES 索引、FTP/SFTP 文件、MinIO 对象与 Kafka 主题均可显式采集为登记表，后续列表读取服务端分页快照。Kafka 主题配置允许最多100个明确名称，以逗号或换行分隔，整批描述主题及读取分区边界，不逐主题读业务库；业务字段以消息正文为准，不把 Broker 的键、分区、偏移等传输属性伪造成必填业务字段。每个接入任务只订阅本登记表对应主题或查询本索引，不把数据源范围通配符、多个主题整体复制到单表任务。MinIO 的实际对象定位独立于可编辑中文注释，连接专属参数与文件过滤规则保留到采集器。
- FTP 连续文件采样对小文件先有界读取尾部并消费传输完成响应，避免额外 ABOR 响应污染下一次读取；大文件仍有界采样。文件名存在 `event`、`event_log` 等前缀重叠时优先精确匹配物理文件名，再按工作表/嵌套数据集规则定位，不能因前缀相同误判歧义。
- 数据抽取节点的 JDBC 驱动位置兼容 Linux 绝对目录以及 `DEFAULT=/默认目录;KINGBASE=/实际驱动.jar` 等按数据库类型的绝对路径映射，最多32项、4096字符。可通过 NiFi 参数上下文的资产上传能力部署已有官方驱动，并使用其返回的节点实际路径；这不改变其他数据库的默认目录，也不要求放宽服务器登录或证书配置。NiFi 2.9 的 ES/Kafka 组件按实际描述符设置客户端服务、认证、主题、输出格式及各自支持的超时单位。Kafka 消费节点使用持续轮询的 `0 sec` 调度周期，以完成分区分配、消息消费与心跳；缺失驱动、组件无效或投递失败都不能计为落库成功。
- 库详情旧表单的 `procCount` 显示字段映射到现行 `procedureCount`；旧 `totalData` 概览项显示现行 `totalSize`，标题为“总占用空间”，不能加“条”后缀或把字节容量当作全库记录总数。展示适配不改写租户原始动态表单。
- 数据表与数据源详情共用 `detail-page` / `base-detail`，分别使用当前租户 `sym_form` 的“表详情”与“库详情”。表详情的“刷新元数据”只探查当前已授权表；`GET /dst/database/metadata/refreshDbInfo` 的 `tid` 始终为数据源 ID，可选 `tableId` 必须属于该数据源、当前租户及角色范围，`dryRun=true` 不写入。表统计保存到 `db_table_t.record_count/field_count/storage_size/total_size_bytes/total_size_formatted/ddl_create_time/ddl_update_time`，不覆盖 `db_table_column_t` 字段治理设置；不支持的来源指标保持空值，零值不能回退到旧 ES 统计。详情“元数据更新时间”使用资产 `updated_time`，不能称为来源库最后数据变更时间；搜索索引只是展示投影，重新读取详情应以业务主表为准。
- OceanBase Oracle 的单表概览优先沿用明确的 schema 或物理表所有者；未设置时只从 `用户名@租户#集群` 中提取逻辑用户名作为所有者，登录后缀不属于 schema。探查结果可以返回 `所有者.表名`，只按当前指定所有者匹配，不能因去掉前缀而选中其他所有者的同名表。
- 数据表详情预览表头固定为两行：中文名与英文名各自单行省略，悬停展示完整名称；没有中文名时首行显示 `—`。托管 MRS Hive 每页固定执行字段描述、总数统计和当前页查询，不逐行补查；空登记字段快照仍可由一次 `DESCRIBE` 获取物理字段。分页使用首屏 `LIMIT`、后续页 `ROW_NUMBER()`，空值用 `CASE` 排后，兼容不支持偏移 `LIMIT` 或 `NULLS LAST` 的旧版 Hive；相关版本边界见 [Hive SELECT 文档](https://hive.apache.org/docs/latest/language/languagemanual-select/) 和 [Hive ORDER BY 文档](https://hive.apache.org/docs/latest/language/languagemanual-sortby/)。错误提示区分字段读取、总数统计、字典分组与分页查询阶段，服务日志只记录表标识、阶段及异常类型，不输出连接凭据、过滤值或完整 SQL。
- Hive 预览 SQL 的内部聚合别名使用字母开头的名称，如 `preview_total`；不生成未引用的 `__preview_total` 等下划线开头别名。Hive 会将其识别为字符集标记而非 `Identifier`，造成计数 SQL 在 `AS` 后解析失败，规则见 [Hive 3.1 词法定义](https://github.com/apache/hive/blob/rel/release-3.1.3/ql/src/java/org/apache/hadoop/hive/ql/parse/HiveLexer.g)。SQL 结果执行测试同时覆盖该方言边界，不能仅凭 H2 执行成功认定 Hive 语法正确。
- 登记与数据探查的“同步字段”读取该授权来源表的全部字段，将最新非空物理注释同步为字段中文名，来源无注释时保留已填写中文名；加入新增字段并更新结构。按字段名忽略大小写匹配并保留已有字段 ID、字典翻译、统一格式和时间属性等治理设置；同步成功使旧预览失效。字段读取、同步及保存不得以 200 等固定字段数量截断，预览数据行的采样/分页上限与字段数无关；多表请求可按表 ID 有界分批，但每张表的字段必须完整返回，整批逐 ID 校验租户和角色范围。
- Application systems and data sources keep `sym_application_t` and `db_datasource_t` as their authoritative master tables. Their high-frequency list/search interfaces read the denormalized Elasticsearch index for speed; in this environment the configured physical index is `elements_dataassets` (`configData().es_dataassets`), while `EsCommonService` receives the logical name `dataassets` and adds the `elements_` prefix. Detail interfaces may fall back to MySQL when an index document is absent.
- Ordinary application-system and data-source save/delete interfaces update MySQL first and then update or remove the matching Elasticsearch document. Dynamic-detail saves that explicitly request `deferIndexRefresh` follow the validated best-effort asynchronous projection contract above and return `indexRefreshPending: true`; do not make a completed authoritative save appear failed because indexing is slow. Use `POST /dst/maintenance/refresh` for one asset and `POST /dst/maintenance/batchRefresh` for reconciliation or full index rebuilds.
- Keep indexing logic in the `/dst/maintenance` implementations. The unused `/dataassets/manage/assets/refresh` and `/batchRefresh` aliases have been retired; do not recreate them without a confirmed current caller. The service builder still uses `/dataassets/manage/assets/page`.
- Never treat Elasticsearch as the source of truth for application systems or data sources. Index refresh must rebuild app documents from `sym_application_t`, db documents from `db_datasource_t` plus `pool_cfg`, and remove stale app/db documents that no longer exist in the master tables.
- Elasticsearch documents are search projections, not storage for full configuration. Keep datasource credentials and complex `pool_cfg` objects out of ES, convert Hutool `JSONNull` values to real nulls or strings before indexing, and exclude large governance payloads such as `fieldGovernanceConfig`, dictionary profiles, and column arrays. Full values remain in MySQL.
- For `showConnect=0`, persist the data-source master record but clear direct connection columns and connection keys in `pool_cfg` so credentials cannot reappear in detail views.
- Low-code pages should use existing project table components where practical; avoid inventing unrelated table systems.
- Organization hierarchy edits use the tenant database's `rm_org_t`, not the control database. When a user may change `parent_id` from an organization form, enforce the move in the save API rather than only in the low-code page: reject modification of the virtual `ROOT` row, self-parenting, missing parents, and descendant-parent cycles; then update the moved row's `parent_id` and `org_path` and rewrite the old path prefix for every active descendant. Keep the hierarchy's `serial_number` prefix consistent for the moved subtree as well, because organization-scope list queries use it. Do not split a parent change into a UI-only update or two independently successful save requests.
- Keep UI language consistent:
  - `字典对标` is now `字典翻译`.
  - `业务对标` is now `统一格式`.
  - `表数据查看` should be `表数据预览`.
- 字段登记的统一格式 `DATE`（日期）/`DATETIME`（时间）必须进入 ODS 物化字段、DDL 与接入映射：日期使用目标库的 `DATE`；时间按目标方言使用 Oracle/达梦/OceanBase Oracle 的 `DATE`、PostgreSQL/人大金仓/Hive 等的 `TIMESTAMP`、MySQL/OceanBase MySQL 的 `DATETIME`、SQL Server 的 `DATETIME2`。多选目标库时分别生成类型，DDL 预览与提交元数据使用同一份目标字段。来源物理字段类型保持不变，字符型日期/时间由生成的字段映射显式转换，空字符串转 NULL；转换规则同时保存在画布 DSL 与字段映射记录中。仅采用已保存的登记配置，尊重显式清空，不从字段名猜测时间格式，也不自动改造已经存在的目标物理表。
- 物化建表多选 ODS 目标库时，默认勾选、建表预览、物化提交和后台任务创建统一遵循资源中心 ODS 分域的配置顺序（`sym_dict_t.sort_no`，同序号按分域 ID 稳定排序），不能按复选框点击顺序、传入数组顺序或物化表创建时间决定链路。配置为 `huiju` 在 `yuanshi` 前时，生成原来源表→汇聚库、汇聚库→原始库的独立级联任务；管理员调整分域位置后，新建任务采用新的顺序。仅处理所选且已逐 ID 校验租户、角色范围与来源关联的目标，后台一次批量读取有效分域绑定；非 ODS 分发目标保留显式选择顺序。每个任务和画布只包含一个目标库，任务记录的来源表/来源库必须是该跳的真实来源；在原来源表的展开列表汇总全部任务。后续跳直接映射前一跳物化字段，保留 `ODS_UUID`、`ODS_RKSJ`、`ODS_GXSJ` 和翻译结果，不重复生成主键或重复治理。重复提交按真实来源与目标复用任务；历史多目标画布只在明确确认后重建，任一目标校验失败不得留下部分任务。删除任务只影响指定任务；物化表仍被下游任务使用时拒绝物理删除。
- 新建物化建表的目标数据源默认勾选当前租户、角色可见的 ODS 分域编码 `huiju`、`yuanshi` 对应数据源，并按上述分域配置顺序排列；只存在其中一个时选择该分域，均不存在时留空。不得用名称或选项首项猜测，也不覆盖已有目标表查看时的选择。接入任务外层列表的“数据表”和“接入任务”列不提供排序。
- 分发任务新建物化建表的目标选项采用接入左树同口径的当前租户、角色可见非 ODS 已登记数据源，按“部门→数据源”级联展示，不默认选择 ODS 分域或任意首项。未完成登记或未提供连接的候选不可选；已保存目标查看时保留原选择。接入与分发展开区使用同一套真实表链路展示，箭头及状态在数据库/表名块之间上下居中；分发列表“数据表”“分发任务”两列不提供排序。
- 接入与分发链路的当前运行状态采用本页一次批量读取的有效 `nifi_pipeline_t.status`，与画布生命周期保持一致；无有效绑定流程的历史任务才回退 `task_status`。`monitor_status` 保存最近一次执行结果，`SUCCESS` 不能解释为任务停止，也不能覆盖运行态。批量状态查询保留租户、有效记录与链路角色范围，不逐任务请求 NiFi。
- 接入任务与分发任务按当前租户资源中心 `dataSourceType` 根下有效 ODS 层及其直接子分域在 `dwm_center_layer_source_t` 的实际绑定分类。接入候选排除此数据源集合；分发候选只包含此集合，不能用库名、数据库类型或旧 Hive 标志分类。两类列表、左树与统计均要求来源数据源、来源表 `asset_status > 0`，有效记录、业务表/日志表/字典表及当前租户、角色范围一致；ODS 主目标绑定也须计入。菜单显示名为“接入任务”“分发任务”，保留 `/data-convergence`、`/hive-metastore` 路由、菜单 ID 和权限配置。
- 分页批量返回关联任务的实际来源和全部授权目标，展开区按表 ID 展示连续“业务来源 → 汇聚 → 原始”链路；同一历史任务的多目标是并列写入，不能伪画成串行。候选分类不裁剪已关联下游任务；既有任务的中间来源登记状态为 `0` 时仍可按租户、角色及有效记录只读查看，不能据此放宽新任务创建的登记要求。不得根据同名表推断链路。
- 海通组合画布通过只读批量接口显示各独立任务的 DSL，并用展示连线表达任务间真实表关系。当前操作任务须明确标识；编辑、保存、部署、启动、停止仍只作用于选中任务，切换保留未保存草稿，操作进行中不得切换任务。展示节点和展示连线不得写入其他任务 DSL、部署为运行连接或自动移动 NiFi 流程组。聚合接口有界限制链跳、任务、关联表及画布体积，在集合外批量查询并逐 ID 校验租户和角色权限；详情读取及操作任务切换不得修复或保存 DSL，组合画布使用批量快照而不逐任务自动补查。历史来源及字段映射的调整只在明确的写操作中执行，按该任务真实来源处理；主画布加载不依赖 NiFi 在线。
- 更换来源库、登记关联不一致或关联链路超过展示上限时，允许降级为当前任务已保存画布的只读视图，明确提示关联冲突，不猜测新库、不自动改写任务或 DSL。降级仍校验当前任务、画布、每个引用表的租户及全部记录/实际数据源的角色范围；同租户软删除登记仅可用于授权历史查看，硬删除、跨租户、无权限或不可解析的当前画布不得绕过校验。只读视图不提供保存、复制部署、启动、停止或原生操作入口；正常关联任务保持原编辑行为。20/100 个关联任务时降级读取同样有界，不逐任务补查。
- NiFi 原生入口只在用户点击时进行一次有界组树读取。有直属共同父组且该父组直属子组恰好属于当前已授权链路时可提供“组总览”，该视图展示子流程组，不代表把各组内部合并；同时保留各任务流程组入口。无法核验共同父组或父组还有其他任务时选择要进入的已部署任务，不为展示合并迁移运行流程组。选择弹框不依赖预先打开空白窗口，实际跳转由用户点击已核验入口触发。
- 所有Hive目标自动生成的DDL（含普通表、分区表及来源DDL转换）统一包含ROW FORMAT DELIMITED、FIELDS TERMINATED BY '\001'、LINES TERMINATED BY '\002\002\002\003\003\003\n'及STORED AS TEXTFILE；多字符行分隔符由用户明确确认已在生产Hive验证，属于本项目部署契约，不声明为Apache Hive通用兼容。预览、分区插入及物化执行保留可见转义和最终SQL原文，不改成实际控制字符或丢失末尾\n，不重复追加存储声明。实际文件写入须与此分隔契约一致，不能自动修改共享NiFi Writer或已有物理表。
- Hive（含华为 MRS Hive）物化目标表名统一使用小写，包含 `ods_` 前缀。按每个目标数据源的真实类型规范表名，混选其他数据库时保留其确认名称；目标计划、DDL 预览、提交、元数据与后续画布必须使用同一规范名称。手工 DDL 中的大写 Hive 表名在实际建表前拒绝，不改写字段、注释、字符串或既有物理表。
- 物化建表的“创建分区”按目标库独立保存，位于DDL目标页签行右侧，仅对已适配的数据库及写入方式显示。显式进入DDL或提交时可探查当前已授权Oracle/OceanBase Oracle/达梦来源的系统分区目录，登记字段仍使用已保存快照，不扫描来源数据、不逐目标重复探查；所有新建目标默认不勾选创建分区，包括已识别标准单列LIST/RANGE的来源；识别结果只预填候选配置和展示参考；识别到来源分区时在“查看DDL语句”旁提示“来源表是分区表，是否创建分区？”，未识别到或探查失败不显示此确认提示；移除顶部目标库/表名长摘要及未勾选时的默认说明横条。须用户人工勾选后才将计划用于自动DDL及物化提交。未知、权限失败、多列/组合/HASH或不能安全转换的表达式说明原因，保留人工配置。重复探查、目标切换或异步回读不得覆盖本次人工勾选/取消的状态；每次重新打开新建弹框恢复未勾选。自动生成时根据字段、枚举或时间规则生成分区计划，取消后生成普通表SQL；未勾选不得强制要求分区配置或探查分区目录，普通Hive表可直接新建/复用。自动生成保留用户配置的主键和唯一性，不因预判MySQL/OceanBase主键必须包含分区列而阻断DDL输出，也不自动扩大键范围。来源原生INTERVAL仅Oracle目标保留原生语法，其他已适配目标预建至未来24个月并设future兜底，保留真实历史边界；兜底不代表永久按月扩展。计划保存在目标表现有field_governance_config.partitionPlan，不新增等价主表。Hive自动分区入湖使用ods_partition STRING及与写入一致的文本存储，按计划注册分区，并用实际Location路由；启用此写入能力时仍须确认目录可写，未启用不要求该配置。
- 物化建表的手工DDL以最终编辑文本为准，通过已授权目标连接原样执行；不再用本地SQL解析器、LOB主键规则、分区主键包含规则、SQL与自动分区计划一致性检查拦截或改写。支持手工添加原生存储、索引及分区等语句，实际语法和约束合法性由目标数据库执行并返回底层错误。validateOnly及批量预检只检查目标资源授权与请求完整性，返回syntaxChecked=false，不宣称已验证SQL语法；真实执行仍保持当前租户、逐ID角色范围、目标连接及服务端配置边界。手工编辑后不得在提交前强制重新生成DDL或用生成失败清空后覆盖已编辑SQL。自动生成器仍校验生成所必需的配置；Hive目录路由计划独立于SQL语法，不能据手工SQL假定目录已存在。
- Oracle/OceanBase Oracle 来源分区探查在当前已授权单表连接上读取 `DBMS_METADATA.GET_DDL` 的完整 CLOB，并与系统分区目录核对；不按分区逐条查询，不将来源原始DDL写入响应或运行日志。权限不足、版本不支持或原生语法不能解析时返回可操作提示，保留目录识别及手工配置能力；可比较边界存在差异时不自动勾选。单次探查同时一次关联查询主键与唯一约束，使用真实 `P`/`U` 类型，不能根据约束名称中的 `PK_` 判断主键。
- 分区页面分别展示按年、按月、区域、枚举四类；根据时间边界间距或原生INTERVAL识别年/月类型，非规则时间及数值范围保留准确类型。区域分区默认编码为350100、350200、350300、350400、350500、350600、350700、350800、350900、351000；手工枚举不自动填入区域值，两类编辑值分别保留，内部均采用LIST计划。已读取的LIST边界值全部属于这些区域编码时展示为区域类型，其他值展示为枚举。继承方案默认保留全部历史边界、分组和DEFAULT，不因展示类型或格式化输入框而重新生成；用户主动重新划分、编辑分区值或切换类型后才生成新边界。手工按年与按月均生成规范RANGE计划及兜底边界，Hive沿用同一计划路由。新生成及继承历史边界的Hive时间分区值使用实际覆盖周期：完整自然年为YYYY、完整自然月为YYYYMM；不能按排他的上界年份/月直接命名。首个无下界分区标为before_边界，末尾兜底为future，不规则时间区间使用from_起点_to_终点，不拆分或更改真实边界。DDL注册和NiFi目录路由使用相同规范计划；已有明确自定义分区值不自动改名，现有任务不批量重写。
- 自动生成的Oracle家族DDL使用命名主键和命名唯一约束；来源真实U约束必须纠正同字段旧登记主键标记，并保留其他单列/复合唯一约束，不能因PRIMARY KEY先生成后按字段去重而漏掉UNIQUE。OceanBase Oracle分区物化目标的逻辑主键按UNIQUE生成，即使来源探查不可用也不沿用旧标记生成物理PRIMARY KEY；字段分组和用户选择的逻辑键范围不扩大，其他数据库及非分区目标不套用此规则。本地解析器不能理解的生成表达式只提示确认并保留可编辑SQL，不增加手工DDL执行拦截，手工编辑文本仍原样提交给目标库。
- 物化建表模板使用 db_table_column_t 登记字段快照和统一目标方言族转换，保留来源字段ID、原始类型、长度、精度及治理设置。历史快照缺少名称、原生类型或只有含义不确定的展示类型时，返回可编辑字段和逐字段提示，允许在登记流程确认字段或在建表模板中手工调整，不以409/422关闭整个抽屉；完整但尚未识别的扩展类型采用可见的长文本转换提示，不能偷偷猜成varchar(255)。转换器本身不查询租户、连接或业务数据；资源读取由现有认证路由与数据范围统一限定。
- 模板读取支持全部已登记来源字段快照，来源是否具有SQL方言不作为打开抽屉的前置条件。来源类型缺失、历史别名或新增非SQL来源按中性列型模型返回，并提示日期、时间戳等需确认的来源语义；Oracle DATE/FLOAT和SQL Server原生TIMESTAMP等已知语义仍按真实方言处理。含义明确的历史字段标签（如“字符串(64)”）在统一列型模型中归一，并保留原始标签、长度和主键；“数字”“数值”等含义不确定的标签仍返回可编辑提示，不猜测精度。类型别名在Java统一登记，不能在字段遍历中重复查询来源连接，也不能把未知来源默认为MySQL进行物理探查。真正连接、DDL解析与执行仍要求明确的协议/目标类型。
- FTP/SFTP 物化字段只读取当前租户、角色可见来源的 db_table_column_t 快照；打开和提交抽屉不采集文件、不调用 collectColumns，也不提供来源DDL导入或重新采集入口。登记字段采集时同时在既有 field_governance_config.sourceFile 保存无凭据的文件名、目录、格式、编码、分隔符及工作表/JSON路径，字段本身不在此重复存储；后续治理保存保留该服务端描述，客户端不能覆盖它。任务创建优先使用该描述，旧登记缺失时仅列一次目录定位唯一文件，不采样字段或按目标重复探查；文件内容由NiFi任务读取，保留来源文件。同名歧义或缺少数据集定位时在登记流程确认，不猜文件扩展名或扩大成整个目录采集。CSV/分隔TXT、JSON/JSON TXT、XLS/XLSX按登记解析器配置处理。普通FTP/SFTP始终按真实文件表头映射，不能因CSV/Excel格式或字段中文说明而改用展示名称；仅明确声明fileHeaderMode=registered-chinese的中文上报模板使用登记说明作表头。治理保存后的字段匹配按当前租户和表一次预取，再按ID/物理名称解析，不逐项读库。旧画布只在明确确认后重建。
- 物化提交在创建或清理目标表前调用一次 `dataAggTaskEnsure` 的 `dryRun=true, validateSourceOnly=true` 来源预检，无需已有目标表；按服务端真实登记类型判断是否需要文件定位，JDBC来源不进入FTP分支，分区配置不改变来源连接器。预检及正式任务创建分别在各自请求内核验来源租户、角色和登记状态；FTP旧登记每次最多列一次目录、不采样文件、不按目标重复探查，正式创建重新核验服务端登记且不信任客户端定位信息。网络超时、认证、目录权限、文件歧义、缺少工作表/JSON路径分别返回固定可操作提示，不将所有异常统称字段缺失，也不返回底层凭据或连接串。
- 接入目标清理统一使用已选择连接的Schema、完整连接参数和标识符引用规则；转换及物理删除执行器不额外校验租户，业务资源定位仍由当前认证路由与现有数据范围负责。Hive（含MRS和普通JDBC Hive）只清理接入登记及流程，保留物理表和数据，不连接Hive执行DROP。非Hive先停止相关写入流程，再执行带Schema的原生DROP，并核验物理表不存在；已不存在按幂等成功处理，厂商“表不存在”错误也须核验，不能把权限不足误报为成功。流程、连接、驱动、权限或删除后核验失败保留已保存画布和目标登记供重试，成功后在同一事务内批量清理映射、快照、任务和流程记录；不能伪报删除成功。现用来源、其他数据源及未授权对象不能因删除适配而被清理。
- 物化重名探查和删除核验只读取表标识，不逐表查询统计、大小或字段数；普通JDBC每个数据源/Schema调用一次表目录元数据，托管Hive调用一次SHOW TABLES。物化的目录探查不可用时返回unknown及提示，不冒充不存在，也不阻断新表DDL执行；CREATE直接执行，已存在由数据库错误码识别，复用仍须读取真实字段。删除时目录不可用或最终清理未确认仍保留可重试状态，不能把Oracle监听服务配置错误当成目标库已删除。
- 复用已有物理表必须先批量校验本次所选目标的真实字段，核对所需字段、类型族、长度与数值精度，保存实际字段顺序和类型；缺少或不兼容字段须列出并停止，不能照抄新建模板伪造元数据。既有表登记缺失时补齐目标表、字段、目录与来源关系，已有登记更新时保留表和匹配字段 ID、原统计及数据。不得抢占关联其他来源或有独立活动引用的目标，也不能复用来源表自身。普通 JDBC 按已授权数据源与 Schema 分组批量读取字段；托管 MRS Hive 网关暂无批量 DESCRIBE 契约，单次限制 20 个目标，沿用查询超时并在任何物化写入前完成整批检查。来源、字典、治理属性和目标候选均按租户、角色范围批量预取，字段遍历及辅助函数不逐字段查询。
- 物化目标的 `db_table_t.source_table_id` 可保留初始业务来源，级联任务的 `data_access_agg_task_t.source_table_id` 表示真实上一跳。清理不能只比较两字段是否相等：非直接来源须由当前租户有效任务精确绑定来源 ID、目标 ID，并确认与实际来源元数据相同的初始来源在当前租户有效。重新登记的目标若把来源标记成自身，还须从有效同租户目录及目录属性证明原来源和明确目标关联。此兼容不修改业务数据或元数据来源，不放宽竞争来源任务、活动下游任务、来源表自身及角色数据范围的保护；证明记录一次批量预取，不在逐目标循环中查库。
- 已有表批量字段结果按 `MetadataExplorer.getColumnsByTables` 的小写表名键读取，不能以展示名称大小写误判字段为空。Hive DESCRIBE 兼容列标签前缀、大小写、空白、表头和重复分区段；仍须取得真实字段名与类型。用户明确复用时，Hive 无长度限制的 `STRING` 可承接数字、日期时间、布尔值的文本表示，页面用简洁的信息提示说明沿用现有文本类型且可继续创建任务，保存实际 `STRING` 元数据，不把兼容成功显示为错误或长条告警；有限 `VARCHAR`、字符串转数字、缺字段及精度不足不据此放行，不自动修改物理表。
- 接入任务字段映射的 `func_value` 保存完整 JSON/表达式，MySQL 基线为 `TEXT`、达梦为 `CLOB`，不能截断规则或删减枚举。新建及 `dryRun` 在任务、画布写入前一次读取当前租户真实列容量，TEXT 按 UTF-8 字节、VARCHAR 按字符检查；旧短列或容量不足返回升级提示且不留下部分任务。物化已完成而任务创建失败时保留目标表，页面提示修复后重试“创建任务”，不能引导重复建表或删表。后端容量校验类须先于引用它的 Magic 资源发布。
- 新建接入任务的 `transform.field-mapping` / `transform.field-enrichment` 默认中文名统一为“字段治理”，保留原 manifestKey 和映射契约。JDBC 目标直接以写入处理器为入口，不自动添加 MergeRecord；兼容路径仍需日期规范化时由转换处理器直接连接写入。Oracle/OB Oracle 的数据库 MERGE 写入方式、失败重试和无效记录分流保持原契约。该规则用于新生成流程，不修改已部署任务的处理器、队列或增量水位；不因该模板调整将现有部署标记失效。流内 SQL 多输入等确实需要合并的独立用途继续保留 MergeRecord。
- 字段治理编辑器仅对实际使用 JDBC 连接池的相邻来源/目标组件读取数据库 Schema 契约；SFTP/FTP、API、Kafka 等使用已有字段探查结果，不把逻辑数据集名称或遗留数据库别名作为物理表引用。数据库引用成对传入数据源 ID 与已有登记表 ID，无登记表 ID 的旧画布兼容表名定位，服务端继续核对租户、角色范围及归属。手工换表清除上一张表的登记 ID；引用变化后重新读取契约，不能继续展示旧表结构或在打开面板时改写保存的来源配置。
- For `register-confirm`, dictionary tables do not need timestamp validation. Only `业务表` and `日志表` require a timestamp field, and each table only needs one timestamp field.
- 多值字典翻译与标准枚举的拆分、匹配、拼接在 NiFi 记录处理中执行，不按来源库、字典库或目标库类型限制能力，不使用 MySQL 专属的 `JSON_TABLE`、`GROUP_CONCAT` 查询。标准枚举使用已登记的编码映射，不借来源库执行查询；字典关联保留真实数据源、schema 和附加条件，只按有界编码批次执行参数化 `IN` 查询。保留原始顺序和重复值，忽略空编码，部分匹配时保留未匹配的原始编码，全部未匹配时遵守 NULL/KEEP_SOURCE/FAIL 策略。旧自动生成的多值规则在恢复画布时根据登记元数据修复，不能退化成单值匹配或丢弃字典过滤条件。已物化并关联的目标表可保留并只补建任务流程。
- 来源字段的历史兼容列 `code_table_id` 可能保存数据库字典表 ID、标准字典 ID 或字典表名，不能一律当成 `db_table_t.tid`。物化及任务生成先批量读取来源表/目录治理配置，按 `dictionaryRelation.sourceType`、`mode`、`forceStandardEnabled` 判别：标准/枚举采用已登记的编码映射，不查询或鉴权同 ID 的物理表；明确关闭的关联不由旧兼容列重新启用。真实数据库字典关联仍须按当前租户和角色范围批量校验，缺失、删除或越权时指出关联字段并拒绝写入。Magic 的租户变量须在相关闭包定义前从认证上下文解析，或显式传入 helper，避免读取治理属性及写入目录属性时捕获空租户。物化 `dryRun=true` 只读校验登记、关联字典和 DDL，不验证物理表存在性或写入元数据。
- 关联枚举的单值 SQL 匹配必须按登记元数据中的来源字段类型生成字面量：数字型字段使用不带引号的数字编码，字符型字段保留引号和前导零，不能仅凭编码外观判断类型。来源类型随自动画布 DSL 和字段映射记录保存；旧画布部署时从当前登记规则补齐。数字编码须校验为有效且有界的数值，中文显示值仍为字符串；预览匹配与实际 SQL 的数值比较保持一致。
- 物化字段编辑的dataType、length与隐藏columnType在预览和提交时同步；此页面不展示默认值列，自动生成的目标字段defaultValue统一为空，保留sourceDataType/sourceColumnType。长文本保持目标库的大字段类型；目标库不支持的大字段键在预览中逐字段提示，仍提供完整可编辑DDL，不在Magic与Java多处重复拦截。执行入口负责限定当前连接的一张表及相关注释，数据库负责最终类型和约束执行；不能通过静默截断长文本或取消约束伪造兼容。
- 跨库默认值使用SQL表达式树统一转换，支持带括号的字符常量、日期常量、布尔值和常见当前时间函数，并保留字符串内部的::。数字舍入、序列、扩展函数等不能证明等价的表达式保留在预览及字段编辑器中并提示确认，不能直接中止整份DDL预览或机械删除转换；Hive不输出来源DEFAULT，也不对已忽略的DEFAULT作额外拦截。纯文本转换支持importSourceDdl/convertSourceDdl、sourceDdl、sourceDatabaseType、targetDatabaseType；不需要数据源ID、租户参数或数据库连接。来源DDL中的组合唯一、外键等无法由字段编辑器完整承载的约束须明确提示用户在目标DDL中确认。
- NiFi QueryRecord 的单值枚举翻译须统一字符串比较的字符集：字符字段用 `CONVERT(字段, UTF8, UTF8)` 保持 Java 字符串内容并声明 UTF-8，编码字面量统一 `_UTF8`；数字字段仍直接比较数字，不作字符转换。描述及字符串回退值显式使用无长度限制的 UTF-8 `VARCHAR`，避免中文与 ASCII 混用字符集或短描述被 CASE 补空格。不能将 Calcite 的字符集声明带入来源 JDBC SQL；来源库使用其原生字符串字面量。针对 NiFi 2.9.0 的 SQL 验证使用同版本 `nifi-calcite-utils` 和实际 Calcite 引擎，不能只以 H2 执行或 SQL 文本断言代替。升级字段映射编译规则须同步相应部署指纹，使受影响任务的下次明确部署生成新 SQL，不自动改写运行中的 NiFi 流程。
- For `db-table` preview:
  - Row data preview should not apply added/modified/deleted row coloring.
  - Field structure view should show added fields in green, modified fields in yellow, deleted fields in gray/strikethrough.
- For `db-table` deleted table rows:
  - Show deleted physical tables as gray/strikethrough table names.
  - Operation must be a real Delete action with confirmation, then call `/dst/database/metadata/tables/deleteAsset`.

<a id="resource-layout"></a>

## 资源目录组织建议

<a id="resource-layout--接口保留依据"></a>

### 接口保留依据

当前工程范围为澄天、海通、统一身份管理、万象及共享 Java 后端。祺智和智能搜索独立应用已停用；旧应用的菜单跳转地址不能证明它们的业务接口仍在使用。

接口核查覆盖前端请求、共享低代码源及数据库编译产物、动态表单、租户菜单、服务编排及发布记录、Magic 内部依赖、定时任务、Java 源码和当前运行包。保存和归档接口可能由通用方法拼接地址；必须检查地址的实际取值范围。审批回调、被引用的共享接口及登记在现用服务中的调用不能按“页面未直接请求”删除。

`tools/audit-api.py` 生成只读引用清单到忽略的 `logs/api-audit/`。自动清单中的无引用和动态候选只是核查入口，删除仍须人工复核：区分页面导航与接口请求，排查不同代理前缀、同名路由、模板拼接和按资源 ID 调用。必要的运行内容与恢复材料保存在仓库外受限目录。


<a id="resource-layout--magic-目录"></a>

### Magic 目录

- 按业务模块保存；同一模块不再同时建立“数据盘点”和“租户端”两个同路由前缀的根分组。现用数据库相关接口归数据盘点的数据库表分组。
- 停用应用没有当前调用时清理其接口和空分组，不为保存旧代码建立空的新中心。
- 兼容接口逐条核查。当前服务构建仍调用 `/dataassets/manage/assets/page`，这条接口保留；仅名称含“兼容”不能作为删除理由。其他旧地址没有当前调用时可清理。
- 不因整理编辑器目录改变有效 HTTP 方法、公共路由、资源 ID 或函数导入名称。移入不同路由前缀的父分组前，先确认路由保持方法，不能只移动文件夹。
- API 文件、分组元数据、数据库物理路径和本地清单保持一致；清理后重新从持久化存储加载并核对运行资源。


<a id="resource-layout--lowcode-建议结构"></a>

### Lowcode 建议结构

下列是建议目标，尚未修改组件管理器的父级、名称或当前本地目录。目录应先在组件管理元数据中调整，再用规范工具同步到本地；不能只移动本地 Vue 文件。

```text
lowcode/
├─01.工作台/
│  └─工作台(my-workbench).vue
├─02.数据盘点/
│  ├─01.数据源管理/
│  ├─02.业务系统/
│  ├─03.数据探查/
│  ├─04.数据目录/
│  ├─05.数据导入/
│  └─06.盘点公共组件/
│     ├─登记流程/
│     ├─资产详情/
│     └─数据源类型选择/
├─03.数据接入/
│  ├─01.OB原始库/
│  ├─02.Hive原始库/
│  ├─03.任务监控/
│  ├─04.数据对账/
│  └─05.接入公共组件/
├─04.数据治理/
│  ├─01.资源中心/
│  ├─02.数据目录/
│  ├─03.数据质量/
│  ├─04.数据标准/
│  └─05.治理公共组件/
├─05.数据服务/
│  ├─01.服务管理/
│  ├─02.数据开发/
│  └─03.服务公共组件/
├─06.数据超市/
│  ├─01.超市列表/
│  ├─02.超市详情/
│  ├─03.购物车/
│  ├─04.资源申请/
│  └─05.超市公共组件/
├─07.应用工坊/
│  ├─01.应用仓库/
│  ├─02.应用编辑/
│  └─03.发布预览/
├─08.个人中心/
│  └─01.我的资源申请/
├─09.系统管理/
│  ├─01.组织用户权限/
│  ├─02.租户管理/
│  ├─03.字典管理/
│  ├─04.表单配置/
│  ├─05.系统配置/
│  ├─06.节点配置/
│  └─07.审批中心/
└─10.公共组件/
   └─全局导航(global-nav).vue
```

目录示例只列目前有对应代码的功能。组件管理页、可视化设计器、部分报表与大屏入口是静态应用或外部应用页面，不因为菜单中有名称就创建新的低代码文件或空分组。审批弹窗等局部组件应放所属业务的公共组件分组；确实跨业务复用时才放根公共组件。


<a id="resource-layout--主要迁移映射"></a>

#### 主要迁移映射

|当前组织|建议目标|原因|
|---|---|---|
|门户的数据源管理及登记步骤|数据盘点／数据源管理、盘点公共组件／登记流程|数据源登记和其他盘点页面属于同一业务；不再拆到门户和盘点两处|
|以组件本身作为目录父级的登记步骤、详情子页|明确的登记流程、资产详情分组|父组件仍可合法拥有子组件；调整组织是为了降低层级，不是把现有父级认定为断链|
|登记订阅申请单下的购物车、打印、申请表格|数据超市／购物车、资源申请、超市公共组件|资源申请属于超市订阅流程，不是资产登记步骤|
|门户／协作任务下的 `my-apply`|个人中心／我的资源申请|四个租户的现有菜单都将资源申请归个人中心|
|数据治理下的 `data-deal`|数据服务／数据开发|四个租户的现有菜单都将数据开发归数据服务|
|数据服务下嵌套的数据超市|独立数据超市目录|现有菜单将超市作为独立入口|
|混用 `2.2.x`、`2.3.x`、`2.5.x` 的显示名称|各分组内两位顺序号和业务名称|显示编号不再携带旧菜单位置；可导入英文名称保持稳定|
|系统管理下用户权限目录中的 NiFi 配置|系统管理／节点配置|节点配置与用户权限无关|


<a id="resource-layout--租户菜单差异需要先确认"></a>

#### 租户菜单差异需要先确认

共享低代码只有一套组件树，租户菜单目前并不完全一致：

|功能|公安 `baseline_ga`|澄天|海渔|广电|
|---|---|---|---|---|
|资源中心|后台管理|数据治理|后台管理|数据治理|
|数据质量|后台管理|数据治理|后台管理|数据治理|
|数据标准|后台管理|数据治理|数据治理|数据治理|
|节点配置|后台管理|系统配置|系统配置|系统配置|
|数据开发|数据服务|数据服务|数据服务|数据服务|
|资源申请|个人中心|个人中心|个人中心|个人中心|

建议按业务语义把资源中心、数据质量和数据标准统一归治理，把节点配置统一归系统管理。公安结构是行业表结构基准，这不等于其菜单当前父级也是统一组件目录的强制基准。整理低代码时不应静默修改租户菜单、角色授权或显示权限。


<a id="resource-layout--实施边界"></a>

#### 实施边界

1. 确认共享目录与租户菜单的目标关系，然后调整组件管理器的分组、`pid`、展示名和排序。
2. 保持组件 `tid`、可导入英文 `name`、路由引用及 Vue 源码稳定；不凭相似名称合并 `access-apply` 和 `access-apply_1` 等不同组件。
3. 子组件不要求成为菜单；一个组件可以被多个菜单复用。公共组件不是独立应用，不增加跨前端构建依赖。
4. 同步根 `lowcode/` 与清单，刷新组件缓存并验证菜单入口和登记、详情、申请等关键流程。仅整理元数据时不需要重新编译未修改的源码。
5. 清理旧路径，只保留一份有效源；不把建议树提前建立成空文件夹。

<a id="demo-environment"></a>

## 演示环境部署与维护

本说明维护当前演示部署的稳定入口和恢复边界。每次发布仍须重新确认服务、路由、资源版本和用户授权，不能直接执行历史发布材料。


<a id="demo-environment--当前入口"></a>

### 当前入口

| 项目 | 部署位置或入口 |
| --- | --- |
| 平台前端（澄天） | `http://14.103.233.56/wanxiang/`，保留原公共路径 |
| 海通画布 | `http://14.103.233.56/haitong/`，由平台嵌入 |
| Java 服务 | `data-element.service`，`/opt/data-element.jar` |
| 服务工作目录 | `/opt/data-element-data` |
| nginx 配置 | `/opt/nginx-1.26.2/conf/nginx.conf` |
| nginx 命令 | `/opt/nginx/sbin/nginx` |

前端构建时，平台 API 使用 `/prod-api/`，海通 NiFi Java API 使用 `/prod-api/nifi/api`。nginx 的 `/prod-api/` 转发到本机 8088 时去掉该前缀；直接 `/nifi/api/`、`/nifi-ui/` 和 `/nifi-api/` 入口转发时保留原路径。旧 `/haitong-api/` 不是当前画布构建使用的入口。

上线后同时验证静态页面和实际 API 路由。iframe 能显示页面、接口返回 HTTP 200、数据库内容一致都不能单独证明登录、下拉选项和运行资源正确。


<a id="demo-environment--身份与数据库"></a>

### 身份与数据库

当前使用各应用本地账号，不恢复已退役的共享账号后端。控制库为 `baseline`，四个租户分别绑定 `baseline_demo_ga`、`baseline_demo_broadcasting`、`baseline_demo_sea_fishery`、`baseline_demo_chengtian`。完整结构见 [演示数据库基线](#demo-database-schema)。

租户路由由受限的 `/etc/data-element/tenant-runtime.env` 提供，通过 `/etc/systemd/system/data-element.service.d/tenant-runtime.conf` 载入。它补充当前 Nacos 配置，包含部署专用连接信息，不进入 Git、日志或发布包。维护时必须核对最终生效配置，不能只看 Nacos 中旧数据库名。

升级保留原账号 ID、密码校验值、成员状态、角色和组织归属，业务数据只从演示环境原租户范围迁移。原 `baseline_ga`、`baseline_chengtian` 不再用于现行业务路由，保留为恢复材料。原登记流程的旧辅助列不恢复到新公共结构；现用登记状态使用 `asset_status`。


<a id="demo-database-schema"></a>

### 演示环境数据库结构

`db/environments/demo/` 保存演示环境当前实际运行的完整结构。五份 `init.sql` 和 `schema-manifest.json` 由 `tools/export-db.py` 从实际库导出，只用于新空库初始化，不能在已有演示库直接执行。

| 用途 | 实际数据库 | 方言 | 表数 |
| --- | --- | --- | ---: |
| 控制库 | `baseline` | MySQL | 50 |
| 公安 | `baseline_demo_ga` | MySQL | 143 |
| 广电 | `baseline_demo_broadcasting` | MySQL | 143 |
| 海渔 | `baseline_demo_sea_fishery` | MySQL | 143 |
| 澄天 | `baseline_demo_chengtian` | MySQL | 143 |

控制库另有两张部署视图；对象集合以本目录实际导出的结构为准，不按历史表数量恢复旧身份表。当前行业账号在所属租户的 `rm_user_t` 中维护，四个租户物理分库并同时校验 `tenant_id`。字段映射 1.1.1 新增计划、模板/快照及当前发布引用三张行业表，四库结构比较和五库完整导出已通过。

本机的统一结构权威仍是 `baseline_ga`；演示环境校验使用实际绑定的公安库 `baseline_demo_ga`。升级时先审阅本机统一结构，再按演示租户路由应用、核对和导出。本目录与本机结构都需要同步维护，不能只让表数量一致。

连接配置保存于仓库外受限目录。对已定位的演示配置使用：

演示环境另有两个独立接入验证库 `demo_ingest_source` 和 `demo_ingest_target`，结构与用途见 [演示接入验证库](#demo-ingestion-fixtures)。它们不加入五个控制/租户数据库的统一结构校验。

```powershell
python tools/check-db.py --config <受限演示配置路径> --authority baseline_demo_ga
python tools/export-db.py --config <受限演示配置路径> --output db/environments/demo
```

旧演示库 `baseline_ga` 和 `baseline_chengtian` 保留作为升级前恢复材料，当前业务路由不再使用。目录中不保存旧业务数据备份、账号授权或连接凭据。部署与恢复流程见 [演示环境部署](#demo-environment)。

<a id="demo-environment--发布与核验"></a>

### 发布与核验

实际数据接入验证使用演示服务器自己的 `demo_ingest_source` 和 `demo_ingest_target`，不依赖本机或旧内网数据源。结构与保留的测试任务说明见 [演示接入验证库](#demo-ingestion-fixtures)。当前公安演示租户的默认 NiFi 节点连接 `https://14.103.233.56:8443`，使用服务器现有 NiFi 的认证信息；MySQL 驱动位于容器 `/opt/nifi/drivers/mysql-connector-j-8.0.33.jar`。凭据只保存在受限运行配置和对应节点记录中。

驱动的持久化副本位于宿主机 `/opt/data-element-data/nifi/drivers/`。重建 NiFi 容器时，须将该目录挂载或恢复到上述容器路径，并重新核对节点的 JDBC 驱动配置；只保留容器内文件会在重建时丢失。

不要仅更新画布静态包：登记、字段采集、物化模板及数据源查询的所选 Magic 资源也须与后端配套。新查询依赖的 Java 模块应在上传前检查完整 JAR，不能保留旧后端再发布新脚本。

跨应用导航由控制库 `baseline.ui_component_t` 中的 `global-nav` 组件提供，不包含在海通静态包中。调整应用入口时，须单独核对本地 `lowcode/10.通用组件/全局导航(global-nav).vue` 与目标运行版本，同步发布源码及编译后的 JS/CSS，刷新组件缓存并实际点击验证。数据集成中心使用同源 `/haitong/#/integration/overview`，海通会转到 `/haitong/#/overview`；不能继续使用旧导航统一指向 `/qizhi/` 的规则。独立应用的地址优先读取平台地址配置，演示构建不得注入本机开发地址。

1. 先确认当前运行版本和租户路由。对共享 Magic/低代码只生成所选文件的状态与发布计划，保存受限恢复材料。重复 ID 的不同内容必须先审阅，不能按时间猜运行版本。
2. 前端、完整 JAR 和所选低代码从当前审阅源码构建，核验上传摘要。前端归档必须含 `index.html`，后端包必须包含完整运行依赖。暂存后原子替换，保留原版本。
3. Magic 保留资源 ID、目录和完整内容，发布前再次核对远端版本；低代码的源码、JS 和 CSS 同步更新。刷新后回读热运行资源，目录变动按已获授权完成冷加载验证。
4. 验证 `/portal/login`、`/sym/user/me`、`/sym/tenant/current` 和菜单接口，再验证接入/分发列表、链路和画布。只更新登录接口而遗漏用户信息接口，会出现登录成功后再次返回登录页。
5. 比较列表 20/100 行请求和 SQL 数，并验证批量 ID 的租户、角色范围。用户点击详情可按需读取，展开链路不得逐任务补查。
6. 打包版验证覆盖画布来源配置、数据库选择、表选择、添加节点和监控浮层。既要确认控件响应，也要区分服务器读取数据库失败与浏览器点击失败。外部来源库或 NiFi 不可达时明确报告，不能用本地成功替代远端证据。


<a id="demo-ingestion-fixtures"></a>

### 演示接入验证库

这两个数据库位于演示服务器 `14.103.233.56` 的 MySQL，用于验证打包后的海通画布、物化建表和实际 NiFi 数据接入。

| 用途 | 物理数据库 | 物理表 | 平台显示名称 |
| --- | --- | --- | --- |
| 来源 | `demo_ingest_source` | `demo_customer` | 演示接入来源库 |
| 目标 | `demo_ingest_target` | `ods_demo_customer` | 演示接入目标库 |
| SFTP 目标 | `demo_ingest_target` | `ods_codex_snapshot_sftp_5bc63309f3d1` | SFTP登记快照物化验证 |

目标表在平台登记中使用 `ODS_demo_customer`；演示 MySQL 的表名配置将物理名称保存为小写。目标库通过贴源层 ODS 的独立“演示接入验证”分域配置，原有分域关联保留。

来源表包含客户编号、生成的客户名称、城市、金额及修改时间，全部是专门生成的演示记录。已用真实 NiFi 流程验证首次全量 20 条及后续新增 1 条，来源和目标均保留 21 条记录。验证后流程停止，测试库及任务保留，后续可以在平台重新启动。

SFTP 验证来源为演示服务器端口 22 下的独立模拟文件目录 `/home/codex_snapshot_sftp_5bc63309f3d1`，文件含三个真实表头和两条模拟人员记录，使用 GB18030 编码与 TAB 分隔符。字段在登记时采集一次，后续物化与任务创建复用 `db_table_column_t` 快照；目标保留三项业务字段和三项 ODS 系统字段，无自动默认值。真实 `GetSFTP` 流程已验证中文业务值与 ODS 字段写入，并在结束后停止，原文件保留。已有客户验证表不受本次默认值取消策略的影响。

来源库当前还包含已存在的登记投影等辅助表。完整初始化结构保留其实际定义，不以示例客户表清单裁剪数据库；这些辅助表不是本次 SFTP 物化创建的表。

这些库是平台登记的外部来源/目标库，不属于 `baseline` 控制库或四个行业租户的 140 张统一业务表。不要把测试表加入行业租户初始化结构，也不要使用本机或旧内网业务库作为它们的连接目标。

两份 `init.sql` 及 `schema-manifest.json` 由根目录 `tools/export-db.py` 从演示服务器实际结构导出，仅用于新空库初始化，不包含演示数据、账号、密码或授权语句。连接信息和恢复材料保存于仓库外受限目录；测试执行材料、结果和截图位于忽略的根目录 `logs/demo-local-databases/`。

<a id="demo-environment--恢复边界"></a>

### 恢复边界

恢复材料存放服务器受限 `/opt/deploy-backups/` 及操作者仓库外受限目录。只有在明确恢复授权下执行恢复，先检查发布后是否已有新编辑或业务写入。

- 服务恢复需成套恢复 JAR、前端、nginx 配置及身份/租户路由。回到旧版本时也需处理新增的 systemd 路由覆盖配置，不能只换 JAR。
- 共享资源按发布计划中的原记录逐项恢复，先比较当前内容与当次部署摘要；不能从后来变化的本地源码推断远端已部署版本。遇到并发编辑先解决差异。
- 账号配置恢复按原租户记录和原成员关系核对。新租户库中的发布后写入需评估和保留，不能直接丢弃或全库覆写。
- 原演示业务库保留完整，数据库备份另存受限目录；恢复过程不得导入本机业务数据，也不得把初始化结构当成数据恢复脚本。

运行报告、截图、发布包和临时执行文件放根 `logs/`。结构文件只保留各环境当前完整版本；凭据和含业务数据的备份禁止提交 Git。

<a id="logging-design"></a>

## 系统日志管理详细设计说明书

适用范围为共享 Java 后端、统一身份管理平台、公安广电海渔澄天四行业租户，以及澄天海通万象前端。独立海通数据推送服务保留自身交换日志，通过明确映射关联业务链路。

建议建设统一的日志框架与查询契约，在各租户自己的系统中提供日志管理入口。行业业务日志按真实租户物理分库并保留 tenant_id；平台操作员与统一认证账号的日志仍由控制库管理。优先改造现有日志表，补充可靠投递、批量明细、策略、导出和归档能力，不以一张跨租户共享大表替代现行隔离。

本文保留完整目标设计，并在下节维护当前实现与发布状态。目标章节中的建议字段和接口须以实现契约为准，不能把目标描述当成环境已上线的证据。

### 当前实现与发布状态

当前本机已应用控制库和四行业库的结构升级：控制库 55 张表，四行业库各 162 张表，包含五张日志辅助表。MySQL 与达梦的表、列、索引及约束检查无未解释差异；完整结构见 `db/`，演示环境不随本机自动升级。

本机 8088 已经用户授权换包并正常冷启动；22 个日志 Magic 脚本及唯一低代码页面 `log-management` 已选择性发布到 baseline；原八个入口/统计组件经全库源码、编译内容与 Magic 引用核对后已软删除，编辑器历史保留。本轮另更新平台日志查询脚本，保留各页签独立权限。实际运行源、编译 JavaScript 和 CSS 均已回读核对。行业日志菜单挂到各库当前有效的“后台管理”根节点，不复用已停用的旧菜单。演示与生产环境尚未发布。

| 入口 | 页面实现与资源 |
| --- | --- |
| 行业后台管理／日志管理 | 单一菜单、单一页面；顶部六页签：登录日志、操作日志、接口日志、安全日志、导出日志、归档日志；同栏右侧为日志策略配置按钮 |
| 统一身份平台／审计日志／日志管理 | 同一个六页签低代码页面及策略弹窗，按当前平台身份域与权限显示；不聚合行业数据库日志 |
| 唯一页面源码 | `lowcode/09.系统管理/09.日志管理/日志管理(log-management).vue`，复用原共用组件 ID；无独立统计页、顶部标题或指标卡 |
| 多应用承载 | 澄天使用现有动态组件；海通、万象、IDAAS 仅增加编译组件加载器，页面业务与样式继续由共享低代码资源提供 |

页面仅含顶部页签及右侧日志策略按钮、查询工具栏、滚动表格和分页；序号、人员（姓名/账号）、时间、功能路径及具体操作优先，其余列随后。导出、归档、策略同样保存管理人员快照；不按行补查当前人员姓名。旧行业子菜单转换为按钮资源，保留原代码和角色范围，单个 `logs:group` 菜单承载页面；按钮权限不会展示成菜单，页签由服务端 capabilities 过滤。

澄天直接复用已注册的 DataTable、Pagination，表格仅呈现接口返回的当前页（关闭组件内部分页），保留服务端排序及外部分页控制；只注册 Element Plus 的其他宿主保持原生组件兼容渲染，不引入跨工程构建依赖。节点配置与日志页使用同一 Element Plus 页签及系统主题，日志页不单独覆盖字号、行高和表头。列表不显示用途、默认日期范围等辅助说明。

日志策略仅在点击配置按钮时一次读取全部五个日志类别；弹窗支持类别切换、百分比采样、保留期校验与未保存修改确认，保存沿用服务端版本校验。主列表为每个页签保留筛选、时间区间、排序及页码，配置弹窗不切换或重载当前列表。搜索沿用接入任务的输入框与查询按钮，支持回车、可点击放大镜及清空；默认最近 7 天，时间跨度上限 90 天。安全日志用于审计风险行为；导出日志是当前人员提交的日志文件任务；归档日志用于长期保存、校验、冻结及单独确认后的在线清理，归档本身不会删除在线日志。

菜单上下文由四前端只发送去掉查询参数的 `X-Audit-Route`。行业侧一次批量读取同租户、同应用菜单及当前角色授权，在内存恢复完整父链并核验启用状态后保存 `menu_id/menu_path/page_route`；未授权、断链、循环及未知路径不生成菜单标签。统一身份平台使用与实际导航一致的服务端目录，对可信权限上下文中的同一角色分配及身份域范围进行核验，不把 `audit:read` 等内部编码直接当菜单名称。该上下文仅描述操作入口，不决定租户或替代 API 权限校验。无法核实的后台请求不伪造路径，历史缺失快照显示“历史未记录”。

登录页签按认证阶段显示登录、密码验证与退出，避免同一登录接口的不同阶段被显示成重复操作。Magic 请求拦截器在业务调用前冻结当前 API 元数据名称及 ID，分别保存 `operation_name/function_code`，API 名称去掉排序编号。业务动作的 `action_code` 继续保留；Java 显式动作使用语义动作标签。操作日志由“菜单层级 + 具体操作 + 对象 + 结果”构成，不仅展示接口名，也不拿当前菜单名称反填过去事件。

生产数据库交付按用户确认的“日志模块改造前版本”收口为一个文件：`logs/logging/日志模块升级_公安租户及baseline.sql`。仅包含 baseline 控制库及公安 baseline_ga 的本次全部结构增量、旧登录日志凭据清理与必要菜单权限配置，不包含其他租户、Magic 或低代码资源数据。脚本按库顺序切换，开头集中列明部署标识；执行前按生产实际值核对，同一文件只执行一次。此前包中的 full-upgrade 表示从改造前完整升级到当前日志功能，并非重建或全库数据覆盖；此次单文件已经包含对应内容，不应再与此前多文件包叠加执行。生产未连接或执行，本机已对合并内容及参数化配置 SQL 做核对和事务回滚验证。

#### 已实现的后端契约

行业路由为 `/sym/logs`：`GET /capabilities`、`GET /components?kind=…`；`GET /{kind}`、`/{kind}/statistics`、`/{kind}/{id}`、`/{kind}/{id}/items`，以及 `POST /{kind}/batch`。事件类型仅允许 `auth/operations/requests/security`。列表接收 `page/size/keyword/result/startTime/endTime/sortField/sortDir`，单页最多 100 条、时间窗口最多 90 天，时间必须带时区。旧记录以创建时间按部署时区换算为 UTC 后与新事件一起排序，升降序及导出均在分页前处理。总数、统计、详情、批量明细与导出均保留当前租户、同角色分配中的功能与部门范围条件；客户端参数不能覆盖这些条件。

行业辅助管理使用 `GET /exports|archives|policies`、`POST /exports|archives`、`PUT/POST /policies`、`GET /exports/{id}/download`。归档的冻结、解除、回读查询、清理预览及确认清理为 `POST /archives/{id}`，动作分别是 `hold/release/browse/purge-plan/purge`。清理必须提供预览返回的版本及归档标识确认值；同事务检查冻结、范围和精确归档 ID，保留未归档及迟到事件，不按时间区间直接删除。

平台沿用 Magic 规则：`GET /idaas/logs/query`、`POST /idaas/logs/command`，二进制下载为 `/idaas/logs/export-download`。模块与操作严格匹配，不能拿导出／归档权限调用日志详情或统计。平台事件读取沿用 `audit:read`；新增 `audit:export/audit:configure/audit:archive` 分别保护导出、策略及归档。行业使用当前菜单资源中的 `logs:auth/operations/requests/security/exports/archives/policies`，仍从现有角色的数据范围计算权限，不增加管理员标志。

#### 采集、事务与文件策略

`platform.audit` 冻结可信会话、人员及部门快照，生成随机请求号和会话引用；合法 W3C traceparent 只用于关联，不参与认证或数据库路由。Java 返回信封与 Magic 返回信封均识别实际业务码，HTTP 200 内的失败不会记成成功。行业登录失败与最终登录成功分开，成功审计失败时撤销本次签发会话；退出先撤销真实会话，审计故障不得阻止退出。

当前用户、角色授权和机构维护等显式接入路径通过 `common/audit-operation` 在原 Magic 事务中追加成功事件；平台共用审计函数也在调用方事务中插入。其他行业写接口由框架记录请求及操作事实，语义来自现用 API 名称；事后观测记录不宣称拥有业务事务的原子性。后续新增关键业务必须在业务事务内显式接入，不能仅依赖请求拦截器。

普通访问记录使用有界队列，失败进入受限本地恢复缓冲。`AuditDestination` 提供批量、租约及退避投递扩展，默认没有外部目标，不自动依赖 Kafka 或 Elasticsearch，也不触发身份人员下发。运行文件使用 JSONL，包含请求关联号，屏蔽 SQL 绑定值和凭据键值；审计详情使用白名单，禁止复制请求正文、登录 token、密码或完整异常消息。

导出单批最多 10 万条、文件最多 256 MiB，执行每批与下载时复核当前权限；下载仅允许创建者，权限范围缩小时须重新生成。CSV 防公式注入，默认 24 小时失效。归档采用完整快照代次，迟到数据在后续快照中补入；写出摘要与独立回读摘要及行数一致才标记 VERIFIED。冻结阻止在线清理，归档文件本身不会随在线清理删除。归档文件的最终销毁和外部对象存储接入属于受控运维扩展，不执行隐式历史证据删除。

账号指纹使用独立 HMAC 密钥，优先读取 `DATA_ELEMENTS_AUDIT_FINGERPRINT_KEY`（32字节Base64）；未配置时在后端用户目录 `.data-elements/audit-fingerprint.key` 生成并持久保存，只用于日志关联，不依赖身份下发密钥。多实例部署须通过受限配置提供相同值；不得将密钥放入仓库、运行日志或发布包。

默认路径相对于后端工作目录：`data-element.audit.spool-path=../logs/backend/audit-spool`，`data-element.audit.files-path=../logs/backend/log-files`，运行日志 `DATA_ELEMENTS_LOG_DIR=../logs/backend`。部署时将这些路径指向受限、可持久化的目录。主运行文件滚动上限 2 GiB，错误文件额外保留上限 500 MiB，均保留 14 天；这些文件策略独立于数据库审计保留策略。

#### 验证与发布要求

回归测试覆盖密码空格失败后正常重试、成功事件不含 token、最终审计失败撤销会话、业务与审计同事务提交及回滚、20/100 行固定查询次数、跨租户 ID 混入整批拒绝、部门范围约束、HTTP 200 业务拒绝、归档摘要损坏、有限范围归档拒绝及权限类型混用。运行报告只放忽略的 `logs/logging/implementation/`。数据库检查、源码测试、低代码发布回读与真实浏览器验收必须分别报告，不相互代替。

当前 14 套针对性后端回归共 39 项通过，保留原有认证、导出、归档及权限用例，并新增菜单层级的 20/100 项固定查询、未知路径和同角色权限/范围校验。3 项单页浏览器用例覆盖 1440/390 像素、六页签与策略弹窗、列顺序、无顶部统计和 20/100 行请求次数；四前端生产构建通过。React 日志页冷加载固定三次请求（组件、能力、列表），原生低代码页在框架组件预加载后固定两次（能力、列表）；切页或切换 20/100 行均只发一次列表请求，无统计或逐行补查。事件列表业务 SQL 固定为计数和分页两次，身份/角色权限读取另有固定开销；单请求菜单树核验为一次批量 SQL，与菜单项数无关。真实四行业绑定、分页及跨租户混入 ID 拒绝持续验证；平台/行业 CSV、JSONL 导出和登录退出沿用同一已验证契约。

真实归档验证使用无业务事件的历史时间段，完成 VERIFIED、文件回读、冻结阻止清理、解除冻结及清理预览；未以删除真实历史日志作验收。非空文件、摘要损坏与范围拒绝由针对性测试覆盖。日志策略以原有默认值保存验证版本冲突，未缩短实际保留期。

四行业旧登录日志的 password/token 兼容字段已经清空，新采集器不再写入；事件及其他历史事实保留。归档、策略需要现有角色对该日志模块的 ALL 数据范围：当前公安 manager 为 ALL，广电、海渔和澄天 manager 为 OWNER，后者保留本人日志及导出能力，单页不展示需要完整范围的归档页签和策略按钮，不自动扩大角色权限。如需专门管理全租户日志，使用现有角色资源模型授予相应日志资源和范围。

收尾资源比较仅剩原有用户管理、全局导航两项远端编辑差异，均未覆盖；本次日志资源无未发布差异。

### 1 设计结论与实施范围

#### 1.1 关键设计决定

| 事项 | 建议方案 |
| --- | --- |
| 日志归属 | 行业日志留在对应行业库；平台日志留在 baseline；系统运行文件由运维集中采集但访问仍按日志归属过滤 |
| 现有表复用 | 改造 log_user_login_t、log_system_t、log_request_t、log_template_t；平台沿用 iam_audit_event_t |
| 新增辅助表 | 拟建 log_batch_item_t、log_audit_outbox_t、log_policy_t、log_export_job_t、log_archive_batch_t，共五种逻辑结构 |
| 采集方式 | 请求过滤器采集技术上下文；会话适配器采集登录退出；Java 与 Magic 在业务语义明确的位置记录动作 |
| 可靠性 | 关键业务成功事件与业务同库同事务；异步检索或外部投递使用同事务 outbox；普通访问日志有界异步并监测丢失 |
| 展示方式 | 每个租户一个日志管理入口，以七页签容纳事件与管理记录；任务与交换日志链接现有领域页面 |
| 实施次序 | 先去除凭据记录并统一上下文，再完善登录退出与操作日志，随后做查询导出，最后做归档与集中检索 |

#### 1.2 管理者应能够回答的问题

日志应清楚回答：哪个系统、哪个租户、哪个部门、哪个人或客户端，在什么时间，通过什么入口，对哪个对象执行了什么动作，是否通过授权，执行结果如何，失败原因是什么，以及相关请求和后续任务在哪里。

例如，“某部门某人修改了数据源”必须同时包含当时的部门和姓名快照、稳定动作编码、数据源标识及名称快照、脱敏后的修改字段、执行结果、请求号。仅记录“调用了 POST 接口”不足以回答业务问题。

#### 1.3 首次交付边界

第一批优先覆盖登录、退出、账号停用、角色权限变更、用户管理、机构管理、数据源登记、表与目录登记、任务创建启停、审批、发布撤销、导入导出、同步下发和密钥管理。普通页面访问可按容量策略采样，关键审计不得采样。日志采集不改变原有应用本地账号、身份域、授权或 SSO 开关。

### 2 当前系统核查结果

#### 2.1 已存在的实现

| 已核查对象 | 当前实现 | 设计处理 |
| --- | --- | --- |
| 共享后端 | Spring Boot 3.3.8、Java 21、Sa Token、Magic API 2.2.2、MyBatis Plus，已有租户路由 | 增强现有 platform.audit 包，不另建等价业务后端 |
| 行业登录表 | log_user_login_t 有账号、成功标志、IP、终端、租户等字段 | 扩展为认证及会话事件表，保留历史可读 |
| 行业系统表 | log_system_t 有模块、方法、结果、耗时、详情等字段 | 作为操作和安全事件的权威表，按类型区分任务摘要等事件 |
| 行业请求表 | log_request_t 有请求路径、人员部门、请求响应及耗时字段 | 作为技术访问记录，正文与请求头改为白名单摘要 |
| 平台审计 | iam_audit_event_t 区分 AUTH、OPERATION、API、MIGRATION | 沿用该表，补齐人员部门快照、会话及请求关联 |
| 平台查询页 | IDAAS Audit.tsx 已调用 /idaas/audit/list，支持分页及当前页导出 | 继续完善真实服务端筛选、汇总和异步全量导出 |
| 审计列填充 | AuditNamedTableInterceptor 自动填创建人及更新时间 | 保留，但它只是业务行元数据填充，不代表已有完整操作日志 |
| 错误返回 | ApiErrorResolver、MagicApiResultProvider、GlobalExceptionHandler 已有错误编号和详情 | 请求入口建立统一链路号，错误编号单独保留并关联 |
| 排错会话 | DiagnosticStore 在 Redis 按租户及用户隔离，TTL 为 30 分钟并可续期 | 供临时排错使用，不能充当长期审计档案 |
| 领域日志 | 数据安全、资产、审批、服务调用、任务、独立海通交换等已有专用记录 | 保留权威事实；通用日志保存摘要与关联标识，避免复制完整执行明细 |

设计核查时，结构清单 db/schema-manifest.json 记录控制库 50 张表，四行业库各 157 张表；当前升级后的数量见本节“当前实现与发布状态”。这是仓库结构基线信息；实施时仍须通过当前运行配置确认真实绑定，不把旧库或演示环境当作升级目标。

#### 2.2 已确认的缺口

1. 租户本地登录函数当前成功后向 log_user_login_t.token 写入真实 token。目标方案禁止记录 token；已有 password 与 token 列需制定脱敏清理和兼容退役方案，不能仅修改表注释。
2. 本地登录函数主要写成功日志，账号不存在、停用、密码错误、来源停用等提前退出分支缺少统一认证审计。失败账号也不能被错误标记为已认证操作者。
3. /portal/logout 的代码注释提到记录退出，但当前可见脚本主要处理会话退出和历史 SSO 分支，没有写入退出审计的语句。该接口在 WebConfiguration 中还被若干拦截器排除，不能假设普通拦截器会自动补齐身份。
4. 平台登录脚本在 MFA 挑战完成和最终签发会话前就可能写 SUCCESS 的 auth:login 事件。目标统计必须把密码校验通过、等待二次认证与最终登录成功分开。
5. iam_audit_event_t 的共用写函数把每条事件的新 ID 同时当作 trace_id；ApiErrorResolver 则为错误另生成 16 位大写编号。两者并非统一请求链路，当前不能据此证明跨接口可追踪。
6. 平台 API 包装器回调正常返回时会设置 SUCCESS，并以推断值填写 HTTP 状态。Magic 的业务退出、HTTP 200 内业务失败、部分成功、异步接受等都需要按最终业务结果判定。
7. 平台查询 SQL 通过当前 iam_operator_profile_v 展示姓名，没有完整保存当时的姓名与部门快照。人员改名、调岗或归档后，历史展示可能变化或缺失。
8. Logback 当前采用三个 ThresholdFilter 输出，较高等级会进入多个文件；根级别为 DEBUG，文件策略为单文件 100MB、每个滚动策略 500MB、历史 3 天。它们是运行日志策略，不等于业务审计的保留策略。

以上属于静态核查事实。数据库有表不证明所有运行路径均已写日志；完整性仍需在实施阶段按真实会话和故障场景验证。

### 3 日志分类与记录口径

#### 3.1 应建设的日志类别

| 类别 | 主要事件 | 主要使用人 | 默认记录策略 |
| --- | --- | --- | --- |
| 登录与会话 | 登录成功失败、二次认证、主动退出、踢出、撤销、到期、租户切换 | 租户审计人员、平台审计人员 | 全量记录，严禁凭据 |
| 业务操作 | 新增、修改、归档、审批、授权、发布、启停、配置变更 | 业务负责人、审计人员 | 写操作全量，敏感读取全量 |
| 数据访问与流转 | 预览、下载、导出、共享授权、数据推送、文件下载 | 数据负责人、安全人员 | 按风险记录摘要、范围和数量，不记录整批数据 |
| 接口访问 | 调用路径、主体、HTTP 与业务状态、耗时、流量、下游状态 | 运维及接口管理人员 | 失败、拒绝、慢请求全量，普通成功读取可采样 |
| 安全事件 | 越权、跨租户尝试、锁定、签名失败、频率限制、策略变更 | 有相应权限的安全审计人员 | 全量，独立告警规则 |
| 任务与集成 | 任务提交、开始、结束、重试、取消、部分成功、下发回执 | 运维、任务负责人 | 生命周期全量，明细复用原任务记录 |
| 系统运行与异常 | 启停、依赖不可用、异常、连接池、队列、归档失败 | 运维人员 | 结构化文件及受控集中采集 |
| 日志自身管理 | 日志查询、敏感详情读取、导出下载、策略变更、归档清理 | 审计人员 | 查询访问记录与关键操作审计分层处理 |

页面点击和菜单浏览可作为体验统计，但浏览器上报是不可信输入，不能证明服务器已执行某项业务操作。SQL 调试日志也不能替代业务操作日志。

#### 3.2 登录退出的准确含义

建议动作包括 AUTH_LOGIN_SUCCEEDED、AUTH_LOGIN_FAILED、AUTH_PASSWORD_VERIFIED、AUTH_MFA_CHALLENGED、AUTH_MFA_FAILED、AUTH_LOGOUT、AUTH_SESSION_REVOKED、AUTH_SESSION_KICKED、AUTH_SESSION_EXPIRED、AUTH_TENANT_SWITCHED。

“登录成功”表示全部认证步骤通过，服务端已建立有效会话；不等于用户已经收到或阅读页面。密码通过但等待 MFA，只记录阶段事件。前端请求超时不能反向推断认证失败，须按服务端会话与事件事实展示。

主动退出要区分退出发起人和被结束会话的主体。被管理员踢出的账号是目标对象，管理员才是操作者。自动到期事件要区分过期时间与服务器发现时间；不能把后续请求发现 token 过期的时刻伪称为用户主动退出时间。

关闭浏览器、关闭标签页、网络掉线通常不能让服务端准确知道退出时刻。不使用 beforeunload 作为权威退出证据。仅本地清除会话但服务端未确认撤销时，页面应明确提示确认状态。

#### 3.3 结果与数量口径

统一结果建议为 SUCCESS、FAILED、DENIED、PARTIAL、ACCEPTED、CANCELLED、TIMEOUT、UNKNOWN。HTTP 状态、业务错误码和业务结果分别保存。HTTP 200 内 code 非成功值属于业务失败；ACCEPTED 仅代表受理，不代表任务完成。下游超时可能无法确认是否执行，业务状态应为 UNKNOWN 或待确认，而非盲目判定未执行。

批量操作记录一个父事件及必要子项，包含请求数量、成功数量、失败数量、跳过数量。界面统计明确区分请求次数、业务操作次数、目标对象数和成功登录会话数，不把同一请求的 API 记录与业务记录重复计数。

### 4 租户归属与访问权限

#### 4.1 存储与查询边界

| 身份及来源 | 可信归属依据 | 权威存储 | 查询边界 |
| --- | --- | --- | --- |
| 行业本地账号 | 已校验 token 的 tenantId 与真实数据库绑定 | 该行业数据库的日志表 | 当前租户加当前角色日志权限及部门范围 |
| 平台操作员 | platformSession 独立会话及平台权限 | baseline.iam_audit_event_t | 当前身份域与授权管理范围 |
| 统一认证账号 | authSession 独立会话与认证账号身份域 | baseline.iam_audit_event_t | 对应认证域及显式权限 |
| 应用客户端 | 服务端校验的客户端与应用绑定 | 该绑定对应的控制或行业日志 | 绑定应用范围；不能信任传入 tenantId |
| 后台任务 | 创建时保存并复核的租户绑定及执行身份 | 对应租户库及原领域任务记录 | 当前任务所属租户与任务权限 |
| 未认证且租户不明 | 不建立行业归属，仅记录未知来源事实 | 控制面安全事件或受限运行日志 | 平台安全权限，行业列表不可见 |

登录选择接口是允许接收候选 tenantId 的专用认证边界；只有经 TenantAccessService 等确认候选租户有效后，才能把失败登录记到该行业库。普通已认证业务接口一律从可信会话和 TenantContext 获取租户，不能从 body、param、旧记录或浏览器菜单决定归属。

#### 4.2 多前端与租户不是同一个概念

同一行业租户可能在澄天门户、海通集成和万象治理之间操作。tenant_id 表示数据归属，source_app 表示操作入口，例如 chengtian、haitong、wanxiang；它们不能混用。前端来源标签需要来源约束或会话桥校验，只用于展示和分析，不参与扩大权限。

独立 haitong-data-pull 的 haitong 库不是行业业务库。现有 ht_exchange_log 留在独立库，记录协议、方向、attempt_id、delivery_id 和路由。需要关联行业事件时，必须使用已登记的路由与租户映射及稳定关联号；不得根据文件名、客户端自报值或同名账号猜租户。

#### 4.3 权限能力与部门范围

沿用现有角色和资源授权模型，拟增加 logs:auth:read、logs:operation:read、logs:request:read、logs:security:read、logs:detail:read、logs:export、logs:policy:write、logs:archive:manage 等功能资源。它们不是新的平台管理员或租户管理员标志。

行业权限范围可配置为本人、指定部门、部门及下级、租户全域，且必须由当前角色现行授权计算。默认无授权不可见。部门名称、账号、姓名和角色显示使用事件快照，授权使用当前有效范围及稳定部门 ID。历史部门已归档且无法匹配现行范围时，不默认开放；通过显式历史审计范围处理。

平台日志查询除 identity_domain 外，还要应用角色允许的应用和组织管理范围。目标 app_id 为空的平台全局动作需有明确的平台事件查看权限，不能因为 app_id 为空而绕过应用范围。列表、总数、统计、详情、链路和导出使用同一权限条件。

读取日志不等于获得业务数据权限。详情内业务对象链接按当前对象权限校验；批量事件的可下载明细也须限定在日志授权范围，必要时隐藏超范围目标及其摘要。会话和凭据本体不通过日志详情回读。

### 5 后端框架设计

#### 5.1 分层架构

框架入口建立请求上下文，业务层产生语义事件，持久化层按可信归属路由，查询层重新执行权限与脱敏。运行文件和业务审计分别维护保留策略，并使用请求号与链路号关联。

架构图见随文图示。业务审计的权威数据在当前归属数据库；集中检索只是可重建投影，不能在检索服务不可用时丢掉关键操作审计。

#### 5.2 建议扩展的 Java 组件

| 所在层 | 拟建组件 | 职责 |
| --- | --- | --- |
| platform.audit.domain | AuditEvent、AuditContext、AuditOutcome | 统一事件契约、不可变请求快照和结果 |
| platform.audit.web | AuditTraceFilter | 生成请求号、校验或生成 trace context、计时、清理 MDC |
| platform.audit.application | AuditContextResolver | 识别平台、统一认证、本地租户、客户端、任务身份 |
| platform.audit.application | AuditRecorder | 校验动作、字段、归属、大小和脱敏后交给明确的写入器 |
| platform.audit.infrastructure.magic | AuditMagicModule、MagicAuditResultAdapter | 为 Magic 提供上下文及记录原语，接收最终业务结果 |
| platform.audit.infrastructure.security | AuthenticationAuditAdapter | 适配独立 Sa Token 命名空间、退出、踢出和过期 |
| platform.audit.infrastructure.persistence | TenantAuditWriter、PlatformAuditWriter | 显式数据源与现有表适配，不共享未验证租户上下文 |
| platform.audit.infrastructure.delivery | AuditOutboxWorker、AccessLogBuffer | 有界投递、批次写入、租约、重试、积压监控 |
| platform.audit.infrastructure.redaction | AuditSanitizer、AuditFieldPolicy | 统一白名单、字段屏蔽、长度限制、换行转义 |
| feature.logmanagement | 查询导出归档服务及控制器 | 领域查询契约、范围校验、导出和生命周期管理 |

Java 提供框架原语及通用技术能力。IDAAS 的业务权限判定、动作、对象和数据变更仍在 13.统一身份管理 的 Magic 规则中，不能迁出另造一套平台业务逻辑。行业现有 Java 业务服务可使用显式调用或注解适配，但最终结果与事务语义必须一致。

#### 5.3 请求处理顺序

1. 最外层请求过滤器生成独立 request_id；合法 traceparent 可用于关联，但外部 trace 不作为身份或权限证据。
2. 建立待解析上下文，完成真实认证和可信租户路由后，补全身份域、操作者、部门、应用及会话引用。
3. Java 或 Magic 完成授权，拒绝事件捕获可信范围和统一原因码。
4. 业务层生成动作、目标及脱敏变更；关键成功事件在业务事务中写入。
5. 最终响应适配器记录实际 HTTP 状态、业务码和耗时；不能仅由异常是否抛出来判断结果。
6. 过滤器 finally 使用已保存的不可变上下文完成访问记录，然后清理 MDC 和线程上下文。

不能在 afterCompletion 已清除 TenantContext 后重新推断数据归属。异步 Servlet、异常分派及线程切换需只完成一次记录；使用请求完成标记避免重复。MDC 是技术关联工具，不是数据库路由或授权来源。

#### 5.4 Magic 业务接入规范

新增 auditRuntime 上下文、脱敏和写入原语，替换到处拼接日志 SQL 的方式。Java 不负责猜业务字段含义；Magic 传入经授权得到的稳定对象 ID、展示快照和白名单差异。动态 API 元数据可提供资源 ID、中文标题及所属模块，不能只依赖前端菜单名称。

log_template_t 可改造为动作映射规则：HTTP 方法及路由模板映射到模块、功能、动作、记录等级和字段策略。规则只允许声明式白名单，不执行任意表达式。发布时按版本批量加载，不能在列表逐行查询模板，也不能每条 SQL 都查询字典。

Java 的 @AuditedOperation 等注解只覆盖真正经过代理的方法，不能假设能覆盖 Magic、内部自调用、返回业务失败信封的路径。框架默认采集请求事实，业务层显式声明语义，两者通过 operation_id 关联而不互相代替。

#### 5.5 避免多层重复记录

一个 HTTP 请求通常有一条访问记录，可有若干真正不同的业务动作事件。给每类语义动作明确唯一生产者。请求拦截器不再重复生成“修改用户”业务事件，Sa Token 监听器和登录脚本也不能各写一遍登录成功。

event_id 标识单条事实，request_id 标识一次服务端请求，operation_id 标识一次业务动作或幂等操作，trace_id 标识跨请求链路，batch_id 或 task_id 标识批次和任务。日志投递重试保留 event_id；用户再次发起一次有效业务操作则产生新的事件，并保留重试关联。

### 6 统一事件数据模型

#### 6.1 主要字段

下表为目标逻辑契约。行业各主表按本类别落适用字段；平台由 iam_audit_event_t 适配同样语义，不要求再新增一张中央等价审计主表。类型以 MySQL 表示，达梦映射见后文。

| 字段 | 类型及约束 | 含义 |
| --- | --- | --- |
| tid | varchar(32) 主键 | 稳定事件 ID，由服务端生成 |
| schema_version | int 非空 默认 1 | 事件契约版本 |
| tenant_id | varchar(32) 行业非空 | 可信租户 ID；平台主表继续不强加行业 tenantId |
| identity_domain | varchar(16) | 平台身份域；行业按现行认证域映射 |
| source_app | varchar(32) 非空 | 入口应用编码，不能作为租户归属依据 |
| source_service | varchar(64) 非空 | 后端服务编码 |
| instance_id | varchar(128) | 执行实例编码，不含部署凭据 |
| event_category | varchar(24) 非空 | AUTH OPERATION SECURITY ACCESS TASK 等 |
| action_code | varchar(100) 非空 | 稳定业务动作，平台兼容已有 action |
| module_code module_name | varchar(64) varchar(128) | 模块编码及当时展示名称 |
| function_code function_name | varchar(100) varchar(128) | 功能资源编码及名称快照 |
| actor_kind | varchar(24) 非空 | TENANT_USER OPERATOR AUTH_ACCOUNT CLIENT SYSTEM ANONYMOUS |
| actor_id | varchar(64) 可空 | 认证后的主体 ID，匿名失败不可填伪造用户 |
| actor_account actor_name | varchar(128) varchar(128) | 当时账号及姓名快照，按展示权限脱敏 |
| actor_org_id actor_org_name | varchar(64) varchar(256) | 当时所属部门 ID 与名称快照 |
| actor_org_path | varchar(1000) | 当时部门路径，仅展示，不据此授予新权限 |
| actor_role_snapshot | text 或 CLOB | 有界角色编码名称快照，非查询时逐行补查 |
| initiator_kind initiator_id | varchar(24) varchar(64) | 人发起、系统执行时保留原发起人 |
| target_type target_id | varchar(48) varchar(64) | 目标类型及稳定 ID |
| target_name | varchar(256) | 目标名称快照，不强依赖未来业务表 |
| target_app_id | varchar(32) | 平台动作的目标应用；不是浏览器自报归属 |
| result reason_code | varchar(16) varchar(100) | 业务结果及稳定原因码 |
| summary | varchar(1000) | 可直接阅读的脱敏业务说明 |
| event_time received_time | datetime(6) 非空 | UTC 事实时间与接收时间，分别用于展示和延迟分析 |
| duration_ms | bigint 可空 | 耗时毫秒，必须非负 |
| request_id operation_id | varchar(32) varchar(64) | 请求与业务关联标识 |
| trace_id span_id | char(32) char(16) 可空 | W3C 兼容的小写十六进制链路及跨度标识 |
| legacy_trace_id | varchar(64) 可空 | 兼容原有错误编号，不强行伪造标准 trace |
| session_ref | varchar(64) 可空 | 独立随机会话引用，不能保存 token 或密码摘要 |
| client_ip user_agent | varchar(64) varchar(512) | IPv4 IPv6 来源及有界终端描述 |
| http_method route_path | varchar(16) varchar(1000) | 实际方法与脱敏路由，不保存敏感查询串 |
| http_status business_code | int varchar(64) | 技术状态与业务错误码，分别保存 |
| batch_id task_id | varchar(64) 可空 | 关联现有批次和任务 |
| total_count success_count failure_count skipped_count | bigint 非负 | 批量摘要，允许未结束阶段为空 |
| detail_json | JSON 或 CLOB 可空 | 脱敏白名单详情，默认不超过 32KiB |
| origin_key | varchar(160) 非空 | 原始生产者幂等键，不依赖请求次数猜测 |

新增事件时间按 UTC 存储，页面按明确时区显示，默认 Asia/Shanghai。历史 created_time 若为本地时间，需按原环境和切换点转换，不能直接追加 Z 当作 UTC。无法确定原时区的历史记录标记来源口径。

#### 6.2 人员与部门快照

认证信息从可信会话取得，部门等展示数据优先复用本次业务已读取的主体信息。需要补齐时按本次请求全部主体或对象 ID 一次批量读取；写入前冻结快照，异步消费者不再通过“当前用户”重建历史。

同名账号在不同租户、不同身份域是不同主体。人员删除、改名、调岗不改写历史事件。前端显示“姓名 账号 所属部门”，可展开角色和会话引用；没有资料时明确显示“历史未记录”，不从同名账号推断。

#### 6.3 变更内容

只记录有业务意义且被允许的字段差异，例如名称、状态、授权范围和逻辑数据源类型。字段差异包括 field_code、field_label、change_type、before_masked、after_masked。新增、删除、清空与未修改分开表达；值被隐藏时显示“已变更 内容隐藏”。

密码、盐、密码摘要、连接口令、token、Cookie、Authorization、私钥、签名原文、一次性口令均不记录。凭据类操作只记录“已重置”“已轮换”“已撤销”及受控对象 ID。文件只记录标识、受控名称、数量和大小，不记录附件正文。

### 7 数据库结构设计

#### 7.1 表的演进方式

| 表 | 所在库 | 演进方案 |
| --- | --- | --- |
| log_user_login_t | 四行业 | 扩展认证会话事件字段；停止写入 password token，兼容后退役敏感列 |
| log_system_t | 四行业 | 扩展统一业务审计字段；原 log_desc detail_json 等按兼容映射读取 |
| log_request_t | 四行业 | 扩展 request_id trace_id 业务结果与采样标记；大字段只在授权详情中读取 |
| log_template_t | 四行业 | 扩展声明式动作及字段策略，保留原路由映射兼容 |
| iam_audit_event_t | baseline | 保持中央权威表，补充主体部门快照、关联号、稳定原始幂等键与可信目标范围 |
| 五种新增辅助表 | baseline 及四行业 | 同一逻辑契约，按所属库限定 scope_kind 与 scope_id；行业保留 tenant_id |
| 各模块原有日志 | 原所在库 | 继续存执行事实，通用日志只保存关联引用 |

行业三张日志主表不再使用普通业务逻辑删除作为清理入口；历史 is_del 兼容读取，新增事件写后不可由业务接口修改或删除。平台表已有 version、updated_time 等列可保留兼容，但新审计正文在最终组装后一次写入，不继续“先插入后补充改写”事件。

#### 7.2 各主表的专用字段与索引

| 表 | 除统一契约外的专用字段 | 目标查询索引 |
| --- | --- | --- |
| log_user_login_t | auth_realm varchar(32)、auth_step varchar(24)、termination_reason varchar(48)、attempt_account_masked varchar(128)、account_fingerprint char(64)、fingerprint_key_version int、expires_at datetime(6)、observed_at datetime(6) | tenant_id event_time tid；tenant_id actor_id event_time tid；tenant_id actor_org_id event_time tid；tenant_id account_fingerprint event_time |
| log_system_t | change_json JSON 或 CLOB；关联原领域 origin_record_type varchar(64) 与 origin_record_id varchar(64) | tenant_id event_time tid；tenant_id actor_org_id event_time tid；tenant_id actor_id event_time tid；tenant_id module_code action_code event_time tid；tenant_id trace_id |
| log_request_t | request_bytes response_bytes bigint、sampled tinyint、sample_rate decimal(6,5)、downstream_code varchar(64)、error_ref varchar(64) | tenant_id event_time tid；tenant_id request_id；tenant_id trace_id event_time；tenant_id result event_time tid |
| iam_audit_event_t | 复用现有 actor_kind actor_id app_id action snapshot_json；补充统一契约中尚缺字段；event_type 继续兼容现有枚举，安全类可用 action 及 category 扩展 | identity_domain event_time tid；identity_domain app_id event_time tid；identity_domain actor_org_id event_time tid；request_id；trace_id；origin_key 唯一 |
| log_template_t | action_code varchar(100)、module_code varchar(64)、function_code varchar(100)、audit_level varchar(16)、field_policy_json JSON 或 CLOB、enabled tinyint、version bigint | tenant_id request_method request_uri 唯一有效规则；tenant_id action_code；规则版本索引 |

主表 origin_key 在各自归属内设唯一约束；行业建议唯一键 tenant_id origin_key。历史数据先以稳定的 legacy 前缀与旧主键回填，再建立非空约束。新增 IP 列长度统一为 64，历史日志中 16 或 20 位 IPv4 假设必须移除。

统一契约是接口与领域模型，不要求把含义相同的历史列再复制一遍。行业表已有 user_id、org_id、org_name、full_name、user_account 等字段时，先通过适配器映射到 actor_id、actor_org_id、actor_org_name、actor_name、actor_account；字段容量不足时原位扩展。旧 is_success 映射有限结果集，新增 result 承担部分成功、待确认等完整语义。平台复用 action、app_id、snapshot_json，不再新增同义列。历史发生时间无法确认时保留在兼容查询中并标明未知，不伪造 event_time 通过非空校验。

热路径默认按 event_time 与 tid 稳定排序。操作者、时间、耗时和统计列提供后端排序；每个接口建立明确白名单。不是为每个可排序字段都盲目新建索引，按真实组合筛选与执行计划选择索引，宽文本不作为排序字段。

#### 7.3 辅助表共同约束

五张辅助表共同包含 tid varchar(32) 主键、scope_kind varchar(16) 非空、scope_id varchar(32) 非空、tenant_id varchar(32)、created_time datetime(6) 非空 UTC。下表所列字段为额外字段。所有表和列均需中文注释，表注释使用“日志管理 业务用途”的模块用途语义，最终 DDL 按仓库要求用连字符分隔模块与用途。

tid、scope、created_time 均由可信写入器显式赋值，不能使用固定行业租户默认值。辅助表中未注明默认值的字段不设置隐式默认；未明确非空的展示及诊断字段允许为空。version 默认 1 且非空；可更新辅助表的 updated_time 由服务端显式写 UTC，数据库方言不一致时不依赖隐式自动更新时间。JSON 或 CLOB 中的内容由版本化 schema 校验。

行业记录要求 scope_kind=TENANT、scope_id=tenant_id 且 tenant_id 非空；控制面记录要求 scope_kind=PLATFORM、scope_id=PLATFORM，tenant_id 为空。scope 由服务端路由器生成。平台事件的目标租户只作为目标关系，不借此把平台记录改成行业会话。

UUID 主键不等于授权。所有详情及批量输入同时校验 scope 与当前角色范围。辅助表与多种主表之间不建立伪造的跨库外键；event_store 仅允许 AUTH、OPERATION、REQUEST、IAM，父记录须在当前库、当前 scope 中批量验证。

#### 7.4 批量操作明细表

拟建 log_batch_item_t，表注释“日志管理-批量操作结果明细”。对单项操作不强制插入此表；对于批量部分失败、需要逐目标追溯的操作批量写入。

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| event_store event_id | varchar(16) varchar(32) 非空 | 关联当前库的权威事件 |
| batch_id | varchar(64) 非空 | 稳定批次 ID |
| item_no | int 非空 大于 0 | 批次内序号 |
| target_type target_id | varchar(48) varchar(64) 非空 | 已授权目标 |
| target_name | varchar(256) 可空 | 目标名称快照 |
| target_app_id target_org_id | varchar(32) varchar(64) 可空 | 详情权限需要的稳定范围引用 |
| action_code result | varchar(100) varchar(16) 非空 | 单项动作和结果 |
| reason_code summary | varchar(100) varchar(1000) 可空 | 脱敏原因及说明 |
| detail_json | JSON 或 CLOB 可空 | 必要字段差异，默认每项不超过 8KiB |
| event_time | datetime(6) 非空 | 子项事实时间 |

唯一键为 scope_kind scope_id event_store event_id item_no。索引为 scope_kind scope_id event_id item_no，以及 tenant_id target_type target_id event_time。列表只读父事件；用户主动打开批次后，子项通过独立分页接口读取，不能自动逐行补查。

#### 7.5 可靠投递表

拟建 log_audit_outbox_t，表注释“日志管理-审计事件可靠投递”。用于投递检索投影、受控集中分析或外部归档通知，不代替主表的同事务审计。未启用这些能力时无需启动消费者。

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| event_store event_id | varchar(16) varchar(32) 非空 | 本库已持久化事件引用 |
| destination_code | varchar(64) 非空 | 服务端白名单目标编码，禁止任意 URL |
| payload_version | int 非空 默认 1 | 投递消息版本 |
| payload_json | JSON 或 CLOB 非空 | 已脱敏事件信封，默认不超过 64KiB |
| status | varchar(16) 非空 默认 PENDING | PENDING PROCESSING RETRY DONE DEAD |
| attempt_count | int 非空 默认 0 | 实际尝试次数 |
| next_retry_at | datetime(6) 非空 | 下次可取时间 |
| lease_owner | varchar(128) 可空 | 当前工作实例 |
| lease_until | datetime(6) 可空 | 租约截止时间 |
| last_error_code last_error_summary | varchar(100) varchar(512) 可空 | 脱敏投递错误 |
| updated_time version | datetime(6) bigint 非空 | 状态更新时间及并发版本 |

唯一键为 scope_kind scope_id event_store event_id destination_code。取件索引为 status next_retry_at lease_until tid；范围核查索引为 scope_kind scope_id event_id。消费者先按真实数据源分组，再每组批量读取和抢租约；不得遍历事件逐项查主体资料。接收端以同一事件键幂等写入，采用至少一次投递，不宣称跨库恰好一次。

#### 7.6 策略表

拟建 log_policy_t，表注释“日志管理-采集保留与脱敏策略”。建议默认值经容量和管理需求确认后启用，不能把示例天数当作法律规定。

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| category | varchar(24) 非空 | AUTH OPERATION REQUEST SECURITY RUNTIME 等 |
| enabled | tinyint 非空 默认 1 | 可选类别开关；强制审计类别不允许关闭 |
| hot_days | int 非空 大于 0 | 在线保留天数 |
| total_retention_days | int 非空 且不小于 hot_days | 含在线期的总保留期 |
| sample_rate | decimal(6,5) 非空 默认 1 | 仅普通成功请求可小于 1 |
| detail_limit_bytes | int 非空 默认 32768 | 脱敏后详情最大字节数 |
| field_policy_json | JSON 或 CLOB 可空 | 字段允许项、隐藏项及版本 |
| archive_enabled | tinyint 非空 默认 1 | 是否执行冷归档 |
| updated_by updated_time version | varchar(64) datetime(6) bigint | 变更操作者、时间与版本 |

唯一键为 scope_kind scope_id category。策略受系统强制下限约束：租户不能允许记录密码、禁用关键审计或绕过保留冻结。策略保存本身产生独立审计，更新采用乐观锁，不能通过改策略使既有记录立即被删除。

#### 7.7 导出任务表

拟建 log_export_job_t，表注释“日志管理-受控日志导出任务”。默认异步导出至多十万条，可按容量配置；当前页导出仍需服务端导出权限及访问审计。

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| owner_kind owner_id | varchar(24) varchar(64) 非空 | 发起主体，不能保存其 bearer token |
| query_json | JSON 或 CLOB 非空 | 规范化条件与时间区间，禁止任意 SQL |
| scope_snapshot_json | JSON 或 CLOB 非空 | 创建时授权范围的有界快照 |
| permission_version | varchar(64) 可空 | 范围变更检测依据 |
| cutoff_time | datetime(6) 非空 | 数据采集截止时间及查询口径 |
| status | varchar(16) 非空 | PENDING RUNNING SUCCESS FAILED CANCELLED EXPIRED |
| format_code | varchar(16) 非空 | CSV 或 JSONL；按明确能力支持其他格式 |
| row_limit row_count | bigint 非空 | 允许上限及实际行数 |
| object_key | varchar(512) 可空 | 受控存储键，禁止公开 URL 和本地任意路径 |
| sha256 | char(64) 可空 | 导出文件完整性摘要，不是凭据摘要 |
| expires_at finished_at | datetime(6) 可空 | 下载失效与任务完成时间 |
| last_error_code updated_time version | varchar(100) datetime(6) bigint | 结果与并发控制 |

索引为 scope_kind scope_id owner_id created_time tid，以及 status created_time tid。后台执行范围取创建时范围与当前有效权限的交集；下载时再次校验权限、归属和有效期。权限回收后不允许凭旧下载地址继续取文件。

cutoff_time 不自动等于数据库事务快照。默认导出采用追加日志的截止时间、稳定游标及执行时可见数据，回执说明实际范围和迟到记录口径。需要严格某一提交时刻一致性证据时，使用数据库一致性快照或已封存归档批次另行实现，不能给普通分批查询贴上“强一致快照”标签。

#### 7.8 归档批次表

拟建 log_archive_batch_t，表注释“日志管理-日志归档校验与保留记录”。

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| event_store | varchar(16) 非空 | 被归档的权威事件类型 |
| range_start range_end | datetime(6) 非空 | UTC 半开时间范围 |
| generation | int 非空 默认 1 | 同一范围归档或迟到补充代次 |
| status | varchar(20) 非空 | PREPARING WRITTEN VERIFIED PURGING COMPLETE FAILED |
| record_count byte_count | bigint 非空 默认 0 | 实际记录数与归档字节数 |
| object_key manifest_key | varchar(512) 可空 | 受控文件和清单键 |
| sha256 | char(64) 可空 | 不可变归档文件校验摘要 |
| retention_until | datetime(6) 非空 | 允许保留的截止时间 |
| hold_status hold_reason | varchar(16) varchar(512) | 保留冻结状态及必要原因 |
| verified_at purge_completed_at | datetime(6) 可空 | 校验与热库清理的完成时间 |
| operator_id updated_time version | varchar(64) datetime(6) bigint | 发起人和状态控制 |

唯一键为 scope_kind scope_id event_store range_start range_end generation。索引为 status updated_time、retention_until hold_status。校验、清理可推进状态，但归档内容与范围清单不可静默覆盖。迟到事件生成增补代次，不修改已封存文件。

#### 7.9 数据库方言与约束

MySQL 使用 datetime(6)、JSON 或 text 类型；达梦使用等价 TIMESTAMP(6)、CLOB 或经版本确认的原生 JSON 能力。枚举尽量使用应用白名单及实际方言支持的 CHECK，避免将 MySQL ENUM、JSON 函数、LIMIT、ON DUPLICATE KEY 直接复制到达梦。

所有计数字段非负；duration_ms 可空且非负；range_end 大于 range_start；状态、scope 组合、JSON 字节上限在写入器强校验，并在数据库支持时增加约束。字符串长度按真实字符与字节语义确认，UTF8 中文快照不可截断到损坏编码。

不为审计主体或已删除业务对象建立会导致历史事件级联删除的外键。只对同库生命周期严格一致的辅助结构考虑外键；多态事件引用由可信写入器批量校验。归档、历史主表和索引迁移要同时适配 MySQL 与达梦，不能通过放宽租户条件解决兼容问题。

### 8 采集流程与事务设计

#### 8.1 登录与退出

登录入口解析并核实目标租户，冻结匿名尝试信息，复用认证过程的主体查询完成部门快照。密码错误、停用、锁定、MFA 失败等均产生明确阶段与结果；未知账号只保存受控掩码及账户 HMAC 指纹，不记录输入密码或其摘要。HMAC 密钥保存在受限运行配置，记录密钥版本支持轮换。

全部认证通过并建立有效会话后，写最终登录事件，再返回会话凭据。若关键审计持久化失败，按明确补偿撤销本次新会话，不把失败凭据返回客户端。Redis 会话与关系库不是同一事务；必须测试进程崩溃窗口、补偿失败及遗留会话 TTL，不能宣称天然原子。必要时增加会话准备状态和对账机制，不改变原有身份域。

退出先获取可信主体、部门及独立 session_ref，再撤销会话并记录最终结果。即使审计存储不可用，也应继续撤销会话，将事件写入受限应急缓冲并告警，不能让日志故障阻止用户退出。退出日志不得为了获得操作者而在撤销后再次读取“当前登录人”。

主动退出、管理员踢出、凭据变更导致会话撤销分别适配各个 Sa Token 命名空间。同一监听回调和业务脚本使用同一操作关联键去重；session_ref 是独立随机标识，不可由真实 token 直接截取。

#### 8.2 关键业务成功与失败

关键写业务的推荐顺序为：一次批量授权读取前置数据，执行业务更新，生成脱敏差异，在同一数据库事务内写入权威业务审计，必要时追加 outbox，然后提交。审计写入失败时高风险写操作回滚；这项策略要在功能说明中明确，不能在所有读接口上不加区分地阻断业务。

业务事务回滚后，失败事件使用独立短事务或受限应急缓冲记录，不能放进已回滚的事务又声称失败日志可靠。原始业务错误优先返回，日志异常不得覆盖它。拒绝事件也必须有独立记录路径，且不写敏感请求原文。

Magic 的 db.normal().transaction 与 Spring 事务不能只凭线程相同就假定共享连接。实现时须核查 Magic 2.2.2 的实际连接参与方式：权威审计可由同一个 Magic sql 对象在同一事务中写入；Java 提供校验和格式化原语。必须以真实 Magic 脚本执行的回滚测试证明事务一致性，不能仅测试 Java mock。

#### 8.3 请求访问与业务结果

过滤器记录请求开始和响应结束，Java 与 Magic 的结果适配器传递标准业务码、业务结果和错误引用。操作成功但序列化失败时，业务审计保留已提交事实，请求日志记录返回失败；两者通过 operation_id 和 request_id 关联，页面不把业务回滚作为默认解释。

流式响应只记录可确认的传输状态与服务端处理结果，不能为了读取响应正文而缓存大文件。普通成功读取可采样，失败、权限拒绝、认证、关键写和敏感读取按强制策略记录。健康检查及高频轮询独立降噪，但不能因此抹掉安全拒绝事件。

#### 8.4 批量与异步业务

一个父操作记录整体结果，子项按批次写入 log_batch_item_t。批量授权和名称快照一次预取，批量插入结果，禁止循环内补读对象、机构或角色。全事务回滚时不能保留子项 SUCCESS；独立提交的子项则真实展示 PARTIAL。

后台工作线程从受控任务记录恢复租户与发起人，使用显式作用域进入正确数据源，结束后 finally 清理。不能把调用线程的 TenantContext 或 StpUtil 当作异步线程的默认身份。任务重试生成 attempt_id，业务 operation_id 或稳定投递 ID 保持关联。

#### 8.5 平台保存与租户下发

平台保存人员或授权成功，记录平台事件；用户明确勾选并确认下发后，才产生下发受理与每次尝试事件。租户接收端在自己的库中记录接收、账号建立、角色绑定、密码设置等结果。中央与租户之间不做伪装的单库事务。

页面分别展示“平台资料保存结果”和“目标应用接收结果”。下发失败或回执不明不改写原保存成功事实。关联使用稳定操作号、任务号及签名信封中的受控元数据，不能把目标应用返回的任意 tenantId 当作路由。日志投递后台任务只传日志，不获得重新下发人员权限的能力，继续遵守 IDAAS 禁止自动扫描下发的规则。

### 9 查询接口与分页契约

#### 9.1 拟建接口

| 接口 | 主要用途 | 权限与限制 |
| --- | --- | --- |
| GET /sym/logs/auth | 行业登录退出分页 | 当前租户与认证日志权限 |
| GET /sym/logs/operations | 行业操作和安全分类分页 | 当前角色及部门数据范围 |
| GET /sym/logs/requests | 接口请求分页 | 接口日志权限，默认不含正文 |
| GET /sym/logs/events/{kind}/{id} | 单条授权详情 | 重新校验事件范围及详情权限 |
| GET /sym/logs/events/{kind}/{id}/items | 批量子项分页 | 父事件与每个可见子项范围 |
| GET /sym/logs/statistics | 分类、结果及趋势聚合 | 与列表同一筛选和权限条件 |
| POST /sym/logs/exports | 创建受控导出 | 导出权限，条件和数量上限 |
| GET /sym/logs/exports/{id} | 导出进度或授权下载入口 | 所有者或显式管理权限 |
| GET PUT /sym/logs/policies | 读取和修改日志策略 | 配置权限、乐观锁与强制下限 |
| GET POST /sym/logs/archives | 归档列表或受控启动 | 归档权限，不提供一键清空日志 |
| GET /idaas/audit/list | 平台原查询兼容增强 | identity_domain 及角色应用组织范围 |
| /idaas/audit 下的详情导出策略接口 | 平台管理能力扩展 | 平台独立会话，不套行业 token |

上表是目标契约，开发前核对现行路由，保持已有调用兼容。通用查询可由 Java 服务实现，IDAAS 使用现有 Magic guard 接入相应框架原语。不得增加一个匿名“前端提交任意审计事件”的权威写接口。

#### 9.2 查询参数与响应

查询支持 startTime、endTime、actorId、orgId、moduleCode、actionCode、result、sourceApp、targetType、targetId、requestId、traceId、keyword、page、size、sortField、sortDir。用户传入的部门只是筛选条件，必须与服务端授权范围求交；普通行业接口不接收用于切换归属的 tenantId。

默认最近七天，在线查询建议最大九十天区间；page 从 1 开始，size 建议 20、50、100，最大 100。深分页设定可配置上限，超出后使用有界游标或异步导出。where、count、统计和分页排序在服务端完成，不把当前页浏览器排序伪装成全量排序。

响应包含 list、total、page、size、queryTime、timeZone、appliedScopeSummary 和可选的统计摘要。列表一次返回操作者、部门、功能和目标的展示快照、结果及必要数量；不返回 detail_json、change_json、请求头或整段错误栈。未知历史字段明确为 null，页面显示“历史未记录”。

#### 9.3 固定查询数量与逐 ID 授权

列表默认一次页面请求。后端在身份与角色范围解析的固定开销之外，执行一次 count 与一次分页 select；统计可由单独一次聚合查询取得。展示姓名部门来自日志快照，不随每页 20 行或 100 行追加读取。

如必须查询一组事件 ID，一次最多 100 个，先在相同 tenant_id、身份域及角色范围条件中批量取回，并逐 ID 比较请求集合与授权结果。不存在、无权或混入其他租户的 ID 按统一契约拒绝，不能只返回有权子集却报告整批成功。详情内业务对象另作同样的有界批量校验。

跨租户仅用于已获授权的后台运维聚合，先按实际数据库分组，每个允许的库固定查询次数并设置并发上限；行业用户不获得跨租户选择器。例外调用必须列出允许库数、总查询数上限和失败策略。

### 10 各系统的页面设计

#### 10.1 菜单与入口

行业系统在现有系统管理下增加“日志管理”，包含登录日志、操作日志、接口日志、安全日志。具有相应权限时显示导出日志、归档日志页签，以及页签栏右侧的日志策略配置按钮。任务执行、审批流转、数据交换保持已有主入口，日志详情提供授权后的跳转。

澄天使用现有 Vue 和 Element Plus 风格；海通、万象、统一身份平台沿用各自现有 React 与 Ant Design 风格。复用后端契约和业务语义，不强制引入跨应用前端构建依赖。低代码页面若采用共享实现，仍只在根 lowcode 维护一个真实组件资源。

#### 10.2 列表布局

页面顶部为时间区间、操作者、部门、模块、结果和关键词筛选，查询嵌入列表工具栏；右侧放刷新与导出。结果统计显示“当前筛选范围”的总数、成功、失败、拒绝或部分成功，不能使用本页行数代替整体统计。

操作表主要列为发生时间、操作人员、所属部门、功能、操作内容、目标、结果、来源 IP、耗时及详情。用户提出的时间列、首个业务名称列、统计列均提供后端排序，切换排序返回第一页。长名称省略并可查看完整内容，状态同时显示文字和颜色。

登录退出页显示事件名称、账号、姓名、部门、登录方式、来源、结果、原因及会话引用；登录失败的匿名主体不渲染成已登录用户。接口页另外显示 HTTP 状态与业务状态，避免一个绿色 200 掩盖业务失败。

#### 10.3 详情与关联链路

详情使用分组区域展示人员部门与时间、业务动作与目标、变更字段、请求与链路、批量结果及任务关联。页面先展示“谁对什么做了什么、结果如何”，技术请求与错误栈放在后续授权页签。可以复制请求号和链路号，不提供复制 token 或连接字符串的能力。

日志记录保存当时快照，业务跳转按当前权限处理。批量项按需分页加载；不在父表每行自动查询目标资料。技术异常详情只对有权限人员提供受限、脱敏内容，不能直接暴露服务器文件路径或整份跨租户日志文件。

#### 10.4 交互状态

所有保存、导出受理、失败和权限提示使用页面顶部可见通知；表格错误与重试入口保留。空结果显示筛选范围，接口失败不能显示为零条成功。策略修改、归档启动等表单的必填项与后端校验一致标星。

默认不提供编辑日志、单条删除日志或清空日志按钮。经权限控制的生命周期清理通过策略与归档批次执行，并有自身审计。异步导出展示受理、运行、完成、失败、失效状态，下载记录属于敏感访问事件。

### 11 脱敏与安全设计

#### 11.1 数据白名单

允许记录人员及部门的必要快照、稳定资源 ID、动作、状态、数量、文件大小和脱敏错误。手机号、邮箱、身份证件等按岗位需要掩码；授权详情可见性由字段级策略决定。账号本身可包含中文和特殊字符，不用日志设计重新引入账号格式限制，但必须做长度限制、参数绑定和安全输出。

禁止记录密码及任何可复用密码摘要、盐、token、Cookie、Authorization、私钥、keytab、数据库口令、验证码和签名原文。登录失败账户指纹仅对账号做带密钥 HMAC，不对输入密码计算可猜测摘要。禁止“先记录原文再依赖前端遮盖”。

#### 11.2 IP 终端与注入防护

只有来自配置过的可信反向代理，才接受 Forwarded 或 X Forwarded For 等头部。否则使用直接连接地址，并区分代理地址与确认的客户端地址。User Agent、模块标题、账号和业务摘要均为潜在不可信字符串，须限制长度并转义 CR LF 和控制字符。

日志查询使用 SQL 参数绑定，sortField 映射固定 SQL 表达式。CSV 导出防止以等号、加号、减号、@ 及控制字符开头的内容形成公式；同时保留原始业务语义的受控 JSONL 导出选项。前端以文本渲染详情，不执行记录中的 HTML 或脚本。

#### 11.3 保密与防篡改

日志查询账户只获得相应范围的读取权限；应用审计写入路径仅允许追加必要数据；归档清理由独立受控服务身份执行。普通业务接口不提供任意 UPDATE DELETE 审计事件的能力。权限隔离、数据库审计及受控备份共同实现追溯。

归档文件采用校验清单和可选服务端签名；签名密钥与数据库权限分离，必要时使用对象存储保留锁。单纯在同一数据库保存 SHA256 或哈希链，不能证明拥有数据库写权限的人未同时篡改内容和摘要。是否需要不可变存储由审计要求与部署能力决定。

查询日志管理自身的访问归入访问日志，敏感详情和导出下载另记业务审计。被写入的日志事件本身不再次触发同类日志生成，避免递归写入和流量放大。

### 12 性能可靠性与生命周期

#### 12.1 写入等级与故障策略

| 事件等级 | 权威写入路径 | 存储不可用时 |
| --- | --- | --- |
| 关键业务成功 | 与业务同库同事务追加 | 高风险写回滚并给出可见错误，不伪报成功 |
| 认证失败及拒绝 | 独立短事务或持久应急缓冲 | 保留原认证拒绝结果，告警并记录缺口指标 |
| 主动退出及会话撤销 | 先确保撤销，记录最终事实 | 继续撤销并缓冲告警，不能阻止退出 |
| 普通请求访问 | 有界内存批次及受限磁盘缓冲 | 按策略降采样或明确丢弃，记录数量，不宣称零丢失 |
| 检索及外部投递 | 主表加同事务 outbox | 保留主表事实，退避重试与人工处理 DEAD |
| 系统运行日志 | 结构化滚动文件 | 磁盘压力告警，优先限制调试噪声 |

内存队列不能保证进程崩溃不丢失；afterCommit 回调也不是持久队列。关键事实先入同事务数据库，再异步扩展。消费者每批建议 100 至 500 项，重试采用有上限的指数退避及抖动，租约超时可恢复，人工重放仍使用原事件幂等键。

#### 12.2 运行日志改造

Logback 目标输出结构化 JSONL，包含 UTC 时间、级别、服务、实例、tenant_id 或平台 scope、actor_ref、request_id、trace_id、error_code、异常摘要。既有按级别文件要明确是否允许重叠；可采用一个主文件加独立错误文件并说明容量，不能把三个文件容量上限误认为整体上限。

生产默认 INFO，临时 DEBUG 带超时恢复；禁止 SQL 参数原文输出敏感数据。共享进程不为每个租户无限创建独立 appender，避免租户数导致文件句柄和内存膨胀。采用统一结构化文件与采集过滤，产品页面通过服务端权限查询，不开放任意路径读文件。

#### 12.3 默认保留建议

| 类别 | 在线建议 | 总保留建议 | 说明 |
| --- | --- | --- | --- |
| 登录退出与安全审计 | 180 天 | 365 天 | 默认全量，需按管理与合规要求确认 |
| 关键操作与敏感访问 | 180 天 | 365 天 | 与业务档案要求协调 |
| 普通接口访问 | 30 天 | 90 天 | 成功读取可采样，错误按独立策略 |
| 系统运行文件 | 7 至 14 天 | 按容量归档 | 不替代审计表保留策略 |
| 导出临时文件 | 完成后 24 小时 | 到期清理 | 下载鉴权及任务元数据另保留 |
| outbox 成功记录 | 7 天运行窗口 | 按运维需要 | 仅清理投递副本，不删权威审计 |

以上为设计起点，不是法律最低保留期限。未明确合规要求时不能据此自动删除现有日志。保留冻结优先于到期策略，策略缩短需要影响预览、权限确认及审计。

#### 12.4 归档顺序

选择封闭时间区间并复核 scope，分批读取、脱敏校验、压缩写受控存储，生成文件摘要与记录清单，回读校验成功后将批次置为 VERIFIED。确认保留冻结与在线窗口后，才分批清理热库，断点续作并记录进度。失败不得直接进入清理阶段。

优先按日期索引与有界清理实现；数据量确有需要再引入分区。MySQL 分区对唯一键存在约束，达梦策略也不同，不能未经设计把所有表立即改为按月分区。档案查询使用授权的异步检索任务，迟到事件形成增补批次。

#### 12.5 容量与监控

容量按每日事件数乘以平均行及索引占用估算。例如每租户每日 5 万认证及操作事件，按每条含索引平均 3KiB 估算，180 天约 25.7GiB；每日 100 万请求即使平均每条含索引 1KiB，30 天也约 28.6GiB。四租户相加再乘副本、备份、空间碎片和安全余量，不能只计算正文大小。

实际行大小、索引大小、事件速率及保留期都必须压测或测量，上述仅用于说明请求日志可能超过业务审计的容量。避免每个请求保留完整 JSON 请求响应，将大字段限长、按需详情及访问采样作为容量控制的一部分。

监控至少覆盖采集异常数、应急缓冲大小、显式丢弃数、主表写入延迟、outbox 积压及最老年龄、DEAD 数、跨租户拒绝数、查询 P95、归档校验失败、磁盘剩余和导出任务耗时。框架自身故障要进入独立健康告警，不能仅写入已经故障的同一个日志通道。

### 13 开发改造清单与实施顺序

#### 13.1 按当前工程落点改造

| 改造位置 | 主要工作 |
| --- | --- |
| platform.audit 现有包 | 保留审计列填充，新增事件与上下文、脱敏、可靠写入及身份适配 |
| WebConfiguration 与租户拦截链 | 安排 Trace Filter 和可信上下文捕获，补齐白名单接口边界 |
| MagicApiResultProvider 与 GlobalExceptionHandler | 统一最终业务结果、请求号、链路号与错误编号，不泄露凭据 |
| ApiErrorResolver | 保留旧错误查询兼容，使用当前请求 trace 关联，禁止每层另造链路 |
| 租户本地登录函数 | 覆盖失败分支、停用来源、登录成功，停止记录 token |
| /portal/logout | 完成可信退出主体捕获和最终事件，不恢复旧 SSO 自动分支 |
| IDAAS 记录审计与接口包装函数 | 升级为一次组装追加、真实结果、完整快照和关联号 |
| IDAAS 登录及 MFA 接口 | 将密码通过与最终成功分离，覆盖补偿和会话撤销 |
| 现有写操作与任务服务 | 在明确事务边界接入动作、批量结果与阶段事件 |
| 四个前端 | 按原 UI 技术栈提供日志入口、分页筛选、详情与导出进度 |
| 独立海通服务 | 保留 ht_exchange_log，按已核验绑定关联交换及原发起操作 |
| logback.xml 与部署配置 | 结构化日志、生产级别、容量、滚动和采集权限 |

#### 13.2 建议交付批次

第一批完成敏感字段停止写入、历史清理方案、统一 request 和 trace 上下文、租户及身份域隔离、登录退出及最重要的权限变更审计。交付标准是关键事件能完整落库且无凭据泄漏。

第二批覆盖核心业务动作、批量结果与最终状态，建设各租户列表、详情、统计及受控导出。交付标准是能从人员部门追到具体业务对象，列表 20 与 100 行无逐行补查。

第三批完善策略、归档校验、热库清理、运行日志采集、outbox 和可重建集中检索。只有达到容量或运维需要时才增加独立检索基础设施；第一批不强制新增 Kafka、Elasticsearch 或日志微服务。

实施前按环境生成受限备份和逐表升级计划。先审阅四行业及控制库完整结构定义，再按真实方言应用、运行 tools/check-db.py，并使用 tools/export-db.py 同步完整结构与清单。Magic 和低代码先修改唯一源码，逐资源比较发布与回读；本设计文档不替代这些发布步骤。

#### 13.3 历史兼容与敏感数据处理

新旧读取适配层标明来源与契约版本，不把缺失字段补成虚构历史事实。历史登录记录只有成功标志时，不伪造退出类型、部门或真实会话时长。已有主键和引用保持稳定。

token、password 等历史列应先确认写入点、读取点、备份与外部复制，再受控清理并处理相关备份保留。若发现仍有效的会话凭据暴露，应按安全流程撤销相应会话，不能只把界面隐藏。完成兼容后再决定删除敏感列，删除结构须同步所有行业方言和初始化定义。

迁移只导入可确认的历史事实；以旧主键建立幂等映射，保留原发生时间，不把迁移时间当作登录时间。旧表停止新增写入后才安排退役，不能双写后长期保留两个互相矛盾的权威源。

### 14 验收与测试设计

#### 14.1 必须通过的业务场景

| 测试场景 | 验收结果 |
| --- | --- |
| 本地正常登录 | 一个最终成功事件，有可信租户、账号、部门、会话引用，无 token |
| 账号不存在或密码错误 | 有失败事件，不泄露账号是否存在，不记录密码及摘要 |
| 密码通过后 MFA 未完成 | 只有阶段事件，不进入成功登录次数 |
| MFA 成功及签发异常 | 成功口径准确；签发或审计失败有补偿与明确结果 |
| 主动退出与重复退出 | 服务端会话撤销；重复请求不生成伪造的新会话退出 |
| 被踢出与会话到期 | 发起人、目标人及到期或发现时间分开 |
| 修改姓名或调岗后查旧日志 | 仍显示当时姓名部门，不逐行读取现行组织表 |
| 批量一百项部分失败 | 父事件 PARTIAL，数量正确，子项结果真实 |
| HTTP 200 内业务失败 | 请求日志业务结果 FAILED，HTTP 状态仍为 200 |
| 业务提交后响应失败 | 操作事实成功，请求返回失败，两者可关联 |
| 平台保存成功而下发失败 | 两阶段分开记录，不能将保存描述为已回滚 |
| 无权或跨租户事件 ID | 列表、详情、批量、统计和导出全部拒绝越界 |
| 无日志详情权限 | 只显示授权摘要，不返回大字段、技术栈或隐藏对象 |
| 导出提交后权限收回 | 执行或下载重新校验，不按旧权限扩大输出 |
| 关键审计写入失败 | 高风险业务回滚，失败事件另路记录，原错误不被掩盖 |
| 审计不可用时退出 | 退出仍完成，有应急缓冲和健康告警 |
| outbox 重复投递和进程崩溃 | 原事件幂等不重复，租约到期可继续 |
| 多实例异步线程复用 | 不串租户、不串人员，MDC 和连接作用域清理正确 |
| 中文账号及特殊字符 | 正常记录安全输出，不注入日志或导出公式 |
| 归档校验失败和保留冻结 | 热库不提前删除，冻结范围不可清理 |
| MySQL 与达梦 | 字段、索引、时间、分页、批次和事务行为逐环境核查 |

#### 14.2 请求数和 SQL 数的目标证据

| 路径 | 20 行或项的目标 | 100 行或项的目标 | 范围校验 |
| --- | --- | --- | --- |
| 日志列表 | 1 个页面请求，2 条业务查询 | 1 个页面请求，2 条业务查询 | 同一租户、角色及部门谓词用于 count 和 select |
| 列表与独立统计同时加载 | 2 个页面请求，共 3 条业务查询 | 2 个页面请求，共 3 条业务查询 | 聚合与列表相同范围 |
| 事件批量授权读取 | 1 个请求，1 条批量读取 | 1 个请求，1 条批量读取 | 对请求 ID 集合逐 ID 验证，无权整批拒绝 |
| 批量操作前置快照 | 1 次授权对象查询加 1 次必要名称批查 | 同样固定查询数 | 每个目标在当前业务授权范围 |
| 批次详情 | 用户主动 1 次分页请求 | 用户主动 1 次分页请求 | 父事件及明细范围一致 |

上表是设计验收目标，身份和角色范围解析的固定开销另行列出，不能隐藏在缓存里后只展示热缓存结果。实施时分别记录冷启动与热运行的实际浏览器请求和 SQL 数，并核查 helper、数据库触发器及远程调用。写入次数可随真实子项数量增长，但读前置条件不得逐项查询。

#### 14.3 性能与可靠性目标

建议在代表性数据量、索引和并发条件下设定日志列表 P95 小于 1 秒，关键审计增加的事务耗时 P95 不超过 20 毫秒，普通访问日志采集不阻塞响应。上述数值均是压测目标，不是当前测得结果。

验证必须包含日志库故障、磁盘满、队列满、Redis 故障、线程池拒绝、应用退出、连接池关闭、多实例竞争、时钟偏差和重放。任何明确丢弃都产生计数，不能通过吞异常让系统看起来一切正常。

### 15 待确认配置与建议默认值

| 项目 | 建议起点 | 实施前要确认的内容 |
| --- | --- | --- |
| 各租户日志查看范围 | 通过现有角色显式授权 | 哪些岗位能看全租户、部门或本人 |
| 平台事件查看范围 | 身份域加应用组织授权 | 哪些角色可看全局平台动作 |
| 在线与总保留时间 | 关键日志 180 天在线 365 天总期 | 审计要求、容量、备份及冻结流程 |
| 请求采样 | 失败拒绝慢请求全量，普通成功读取可配置 | 实际调用量与需要完整记录的敏感读取清单 |
| 导出限制 | 异步十万条上限，文件 24 小时过期 | 数据量、格式、审批及下载权限 |
| 不可变归档 | 校验清单必需，保留锁按需 | 对象存储能力与签名密钥管理 |
| 集中运维视图 | 先按租户查，跨租户只给授权运维 | 是否确有跨租户汇总需求，允许哪些数据源 |
| 关键审计故障策略 | 高风险写回滚，退出始终可完成 | 高风险动作清单与应急负责人 |

这些参数可以在开始实施时进一步确认；不影响先完成凭据去除、可信上下文、统一事件模型和租户隔离等基础工作。

### 16 依据与代码核查入口

#### 16.1 当前工程依据

| 核查入口 | 本设计使用的事实 |
| --- | --- |
| 根 AGENTS.md 与 docs/data-elements.md | 当前租户、资源版本源、分页与授权约束 |
| data-elements-idaas/AGENTS.md | 平台操作员、人员主档、统一认证账号及应用本地账号分离；禁止自动下发 |
| db/baseline/init.sql | iam_audit_event_t 的真实列、索引及中央审计归属 |
| db/baseline_ga/init.sql | 行业四种日志表及领域日志的当前结构 |
| db/schema-manifest.json | 当前结构清单及方言，不能代替运行环境验证 |
| platform/audit/infrastructure/magic | AuditNamedTableInterceptor 和 AuditTableRegistry 的审计列填充 |
| platform/tenant/web/TenantWebInterceptor.java | 请求租户绑定和完成时清理 |
| platform/tenant/api/TenantAccountsMagicModule.java | 本地登录的会话属性、独立 token 及租户绑定 |
| platform/tenant/api/PlatformSessionMagicModule.java | 平台独立 Sa Token 命名空间 |
| magic/function/09.租户账号/01.租户本地登录.ms | 当前成功登录写表及 token 字段写入 |
| magic/api/01.系统门户/04.系统登出.ms | 当前退出实际逻辑与历史分支 |
| magic/function/13.统一身份管理/03.记录脱敏审计.ms | 中央审计字段与当前 trace 生成方式 |
| magic/function/13.统一身份管理/09.接口访问与审计封装.ms | API 包装、最终结果及补充更新方式 |
| magic/api/13.统一身份管理/10.审计查询/01.分页查询审计记录.ms | 当前查询、人员关联与分页排序 |
| magic/api/13.统一身份管理/01.认证会话/01.登录管理控制台.ms | 当前密码认证、MFA 与成功事件时序 |
| shared/error 与 platform/web/error | 当前错误编号、错误返回及诊断接入 |
| feature/diagnostics/DiagnosticStore.java | Redis 用户及租户隔离的临时排错会话 |
| src/main/resources/logback.xml | 文件阈值、滚动、容量与保留配置 |
| data-elements-idaas/src/pages/Audit.tsx | 现有平台日志列表、详情和当前页导出 |
| db/haitong/init.sql | 独立 ht_exchange_log 的协议与尝试关联模型 |

#### 16.2 外部技术依据

Spring 官方的事务事件说明定义了 BEFORE_COMMIT、AFTER_COMMIT、AFTER_ROLLBACK 等阶段，并指出无事务时默认不触发事务监听。本设计据此将事务回调与持久化队列分开，不把提交后内存回调当作可靠投递。

参考链接 https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html

OpenTelemetry 官方上下文传播说明解释了跨服务传播 trace 与 span 并将其关联到日志的方式。本设计将链路关联与身份授权分开，保留当前错误编号的兼容映射。

参考链接 https://opentelemetry.io/docs/concepts/context-propagation/

OWASP Logging Cheat Sheet 建议记录认证、授权及重要动作，并排除访问 token、密码和会话等敏感值。本设计采用服务端白名单、限长与安全输出，并要求覆盖采集故障测试。

参考链接 https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
