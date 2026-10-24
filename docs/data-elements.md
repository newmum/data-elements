# 数据要素仓库协作与资源维护（data-elements）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。工程 README 仅保留入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](../db/README.md)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 维护方式

每个工程的说明统一保存在 `docs/<工程名>.md`，共用规范在本文件原位维护，索引见 [docs/README.md](README.md)。新增或变更时更新现有章节，不追加日期、版本、阶段文件。独立海通库使用 `python tools/export-db.py --project haitong-data-pull` 只读更新根 `db/haitong/init.sql` 与结构清单，私有连接配置使用 `project_databases.haitong-data-pull`。已有库的临时差异执行语句和验收产物放忽略的根 `logs/`，不替代完整结构基线。

## 目录

- [本地资源版本管理](#resource-versioning)
- [前后端批量查询与分页规范](#batch-pagination)
- [低代码与 Magic 业务约束](#lowcode-magic)
- [资源目录组织建议](#resource-layout)
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

新增资源、分组创建/移动、删除或路由变化需单独审查完整目录元数据和调用方。先在规范本地目录准备源文件与元数据，确认后通过已核验的编辑器/后端契约登记；当前工具不会自动新建、删库或全量覆盖。不得为了绕过这些限制另建一份本地缓存脚本。

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

四个行业租户统一为以 `baseline_ga` 为权威的 157 张业务表，控制库独立维护。结构变更（含新增表、字段、索引、约束）先同步修改所有行业租户的完整初始化定义并审阅，再按实际方言应用所有行业库；保留各自租户默认值和数据。临时 ALTER 语句与备份放在 `logs/` 或仓库外受限目录，不累计日期增量 SQL。服务器变更后运行 `python tools/check-db.py` 检查表集合、列、主键、索引、外键列及原生自动更新时间机制，再重新只读导出全部数据库，确保完整初始化结构与实际数据库一致。`init.sql` 仅用于新空库；它不包含应用启动所需的业务种子和资源数据，不能直接在现有库执行。更多方言与依赖条件见 [数据库结构基线](../db/README.md)。


<a id="resource-versioning--提交前"></a>

### 提交前

核对 `git status` 和差异，确认只有可维护源、结构、配置和必要说明。根 `logs/`、旧 `exports/`、整个后端 `data-elements-parent/data/`、依赖、构建产物、私有配置和日志文件均被忽略；不使用 `git add -f` 绕过。已被 Git 跟踪的旧产物还需提交删除，添加忽略规则不会自动移除历史跟踪。仓库内不得继续保存分散 `.ms`、低代码工作副本、旧发布包或数据库增量缓存。

后端错误快照默认写入根 `logs/backend/errors/`，跟随 `DATA_ELEMENTS_LOG_DIR`，可用服务端 `storage.error-root-dir` 明确配置。旧 `storage.root-dir` 仅用于显式启用的管道文件迁移输入，不用于生成新的错误日志。

项目约束放在根 `AGENTS.md` 和它引用的业务文档，由 Git 同步给团队。个人 skill 保存如何定位环境、编译、发布和验证；不会代替仓库规则，也不会自动赋予写服务器或重启权限。

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
  - Used by `db-table`.
  - Should not automatically write ES or asset tables.
- `POST /dst/database/metadata/tables/deleteAsset`
  - Soft-delete a registered table asset snapshot after re-explore finds the physical table was removed.
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

当前使用各应用本地账号，不恢复已退役的共享账号后端。控制库为 `baseline`，四个租户分别绑定 `baseline_demo_ga`、`baseline_demo_broadcasting`、`baseline_demo_sea_fishery`、`baseline_demo_chengtian`。完整结构见 [演示数据库基线](../db/environments/demo/README.md)。

租户路由由受限的 `/etc/data-element/tenant-runtime.env` 提供，通过 `/etc/systemd/system/data-element.service.d/tenant-runtime.conf` 载入。它补充当前 Nacos 配置，包含部署专用连接信息，不进入 Git、日志或发布包。维护时必须核对最终生效配置，不能只看 Nacos 中旧数据库名。

升级保留原账号 ID、密码校验值、成员状态、角色和组织归属，业务数据只从演示环境原租户范围迁移。原 `baseline_ga`、`baseline_chengtian` 不再用于现行业务路由，保留为恢复材料。原登记流程的旧辅助列不恢复到新公共结构；现用登记状态使用 `asset_status`。


<a id="demo-environment--发布与核验"></a>

### 发布与核验

实际数据接入验证使用演示服务器自己的 `demo_ingest_source` 和 `demo_ingest_target`，不依赖本机或旧内网数据源。结构与保留的测试任务说明见 [演示接入验证库](../db/environments/demo/ingestion-fixtures/README.md)。当前公安演示租户的默认 NiFi 节点连接 `https://14.103.233.56:8443`，使用服务器现有 NiFi 的认证信息；MySQL 驱动位于容器 `/opt/nifi/drivers/mysql-connector-j-8.0.33.jar`。凭据只保存在受限运行配置和对应节点记录中。

驱动的持久化副本位于宿主机 `/opt/data-element-data/nifi/drivers/`。重建 NiFi 容器时，须将该目录挂载或恢复到上述容器路径，并重新核对节点的 JDBC 驱动配置；只保留容器内文件会在重建时丢失。

不要仅更新画布静态包：登记、字段采集、物化模板及数据源查询的所选 Magic 资源也须与后端配套。新查询依赖的 Java 模块应在上传前检查完整 JAR，不能保留旧后端再发布新脚本。

跨应用导航由控制库 `baseline.ui_component_t` 中的 `global-nav` 组件提供，不包含在海通静态包中。调整应用入口时，须单独核对本地 `lowcode/10.通用组件/全局导航(global-nav).vue` 与目标运行版本，同步发布源码及编译后的 JS/CSS，刷新组件缓存并实际点击验证。数据集成中心使用同源 `/haitong/#/integration/overview`，海通会转到 `/haitong/#/overview`；不能继续使用旧导航统一指向 `/qizhi/` 的规则。独立应用的地址优先读取平台地址配置，演示构建不得注入本机开发地址。

1. 先确认当前运行版本和租户路由。对共享 Magic/低代码只生成所选文件的状态与发布计划，保存受限恢复材料。重复 ID 的不同内容必须先审阅，不能按时间猜运行版本。
2. 前端、完整 JAR 和所选低代码从当前审阅源码构建，核验上传摘要。前端归档必须含 `index.html`，后端包必须包含完整运行依赖。暂存后原子替换，保留原版本。
3. Magic 保留资源 ID、目录和完整内容，发布前再次核对远端版本；低代码的源码、JS 和 CSS 同步更新。刷新后回读热运行资源，目录变动按已获授权完成冷加载验证。
4. 验证 `/portal/login`、`/sym/user/me`、`/sym/tenant/current` 和菜单接口，再验证接入/分发列表、链路和画布。只更新登录接口而遗漏用户信息接口，会出现登录成功后再次返回登录页。
5. 比较列表 20/100 行请求和 SQL 数，并验证批量 ID 的租户、角色范围。用户点击详情可按需读取，展开链路不得逐任务补查。
6. 打包版验证覆盖画布来源配置、数据库选择、表选择、添加节点和监控浮层。既要确认控件响应，也要区分服务器读取数据库失败与浏览器点击失败。外部来源库或 NiFi 不可达时明确报告，不能用本地成功替代远端证据。


<a id="demo-environment--恢复边界"></a>

### 恢复边界

恢复材料存放服务器受限 `/opt/deploy-backups/` 及操作者仓库外受限目录。只有在明确恢复授权下执行恢复，先检查发布后是否已有新编辑或业务写入。

- 服务恢复需成套恢复 JAR、前端、nginx 配置及身份/租户路由。回到旧版本时也需处理新增的 systemd 路由覆盖配置，不能只换 JAR。
- 共享资源按发布计划中的原记录逐项恢复，先比较当前内容与当次部署摘要；不能从后来变化的本地源码推断远端已部署版本。遇到并发编辑先解决差异。
- 账号配置恢复按原租户记录和原成员关系核对。新租户库中的发布后写入需评估和保留，不能直接丢弃或全库覆写。
- 原演示业务库保留完整，数据库备份另存受限目录；恢复过程不得导入本机业务数据，也不得把初始化结构当成数据恢复脚本。

运行报告、截图、发布包和临时执行文件放根 `logs/`。结构文件只保留各环境当前完整版本；凭据和含业务数据的备份禁止提交 Git。
