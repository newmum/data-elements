# 共享 Java 后端（data-elements-parent）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。仓库仅保留根目录 README 总入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](data-elements.md#database-schema)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 目录

- [工程概览与使用](#overview)
- [日志框架设计](#logging-framework)
- [NiFi 公共 JSON 服务引用规范](#nifi-json-services)
- [任务运行状态](#task-runtime)
- [跨数据库物化字符串容量规则](#materialized-strings)
- [布控代码目录](#guide-4d710a30)
- [字段映射扩展](#mapping-extension)
- [时间窗口抓取扩展](#date-fetch-extension)

<a id="overview"></a>

## 工程概览与使用

`data-elements-parent` 是当前仓库中的单模块共享后端工程，基于 Spring Boot、MyBatis-Plus、Sa-Token、magic-api、Redis、Nacos、Warm Flow 等组件构建，为各前端提供接口、动态 API、认证、配置、流程和数据访问能力。目录名中的 `parent` 不表示 Maven 聚合父工程。

布控引擎位于标准 Java 源码目录的 `com.linewell.dataelement.bk` 包，随普通 Maven 构建打入后端 JAR。源码、测试和扩展位置见[布控代码目录](#guide-4d710a30)。


<a id="overview--技术栈"></a>

### 技术栈

- Java 21
- Spring Boot 3.x
- Spring Cloud Alibaba / Nacos
- MyBatis-Plus
- Sa-Token
- Redis / Redisson
- magic-api
- Warm Flow
- Maven


<a id="overview--环境要求"></a>

### 环境要求

- JDK：21+
- Maven：3.8+
- MySQL：建议 8.x
- Redis：按环境配置
- Nacos：当前配置默认从 Nacos 加载 `data-element.yml` 和 `data-element-dev.yml`


<a id="overview--本地启动"></a>

### 本地启动

在本机日常启动时，优先从仓库根目录运行：

```powershell
.\start-data-elements.cmd
```

该入口默认读取工程外 `%USERPROFILE%/.codex/private/data-elements/local-runtime.private.json` 的环境变量和 Java 路径，优先运行 `.runtime/data-element-runtime.jar`，保留该配置中的 `NACOS_CONFIG_IMPORTS` 与 `SPRING_APPLICATION_JSON`。当前本机已选择演示 Nacos `14.103.233.56:8848`、命名空间 `data-element`、配置 `data-elements.yml`；地址与凭据仍以受限配置为准。入口不再自动切回旧内网 Nacos，也不会用 `target/` 中的旧包覆盖已验证运行包。配置缺失时会明确报错，可用 `-ConfigPath` 指定另一份工程外配置。

`-Build` 明确要求重新打包并替换运行包；`-JarPath` 明确选择其他包。需要其他环境时可显式传入 `-NacosHost`，并在该进程中提供相应 Nacos 凭据、命名空间和配置导入。直接执行 `java -jar` 或以下 Maven 命令不会自动读取上述受限 JSON，需要先设置同一套环境变量；不要依赖 JAR 的历史环境默认值。

```bash
cd data-elements-parent
mvn spring-boot:run -DskipTests
```

如果从父目录启动，可使用：

```bash
mvn -f data-elements-parent/pom.xml spring-boot:run -DskipTests
```


<a id="overview--常用环境变量"></a>

### 常用环境变量

当前配置通过环境变量接收 Nacos 连接信息。实际凭据由运行环境或密钥管理提供，不写入仓库：

```env
NACOS_HOST=<已确认的配置中心地址>
NACOS_PORT=8848
NACOS_USER=<从运行环境提供>
NACOS_PASSWORD=<从密钥管理提供>
NACOS_NAMESPACE=data-element
NACOS_CONFIG_IMPORTS=nacos:data-elements.yml?group=DEFAULT_GROUP&refreshable=true
```

如果需要本地不依赖 Nacos 运行，需要在 `src/main/resources/application.yml` 或本地 profile 中补充数据库、Redis、服务端口等配置，例如：

```yaml
server:
	port: ${SERVER_PORT:8088}

spring:
	datasource:
		driver-class-name: com.mysql.cj.jdbc.Driver
		url: ${DB_URL:jdbc:mysql://localhost:3306/baseline?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
		username: ${DB_USERNAME:root}
		password: ${DB_PASSWORD:}
	redis:
		host: ${REDIS_HOST:localhost}
		port: ${REDIS_PORT:6379}
		database: ${REDIS_DB:0}
		password: ${REDIS_PASSWORD:}
```


<a id="overview--构建与打包"></a>

### 构建与打包

```bash
# 编译
mvn -DskipTests compile

# 完整测试、打包并安装到本地 Maven 仓库
mvn install

# 只需要打包时（同样执行测试）
mvn package
```

打包产物位于 `target/` 目录，最终文件名由 `pom.xml` 中的 `finalName` 控制。

从仓库根目录构建时使用 `mvn -f data-elements-parent/pom.xml install`。测试报告默认在根目录 `logs/backend/surefire/`。`logs/` 下的临时 POM 属于当次运行材料，不作为日常构建入口。

如果测试编译提示 `org.apache.nifi.sql`、`org.apache.calcite.sql` 不存在，先核对实际使用的 POM。当前正式 POM 已声明测试依赖 `nifi-calcite-utils:2.9.0`，由它引入 Calcite 1.41.0；旧临时 POM 可能读取当前测试源码，却仍使用过期的依赖配置。默认测试输出目录是 `target/test-classes`，若日志显示 `build/test-classes`，应检查临时 POM 或输出目录覆盖参数。从仓库根目录执行 `mvn -f data-elements-parent/pom.xml clean install`；IDE 中重新加载正式 POM 的 Maven 配置。不要通过删除回归测试或跳过测试编译解决依赖缺失。

源码架构测试使用 Maven 提供的 `backend.javaSourceDirectory`（取自实际 `project.build.sourceDirectory`），不依赖测试工作目录；直接在 IDE 运行时会按测试类所在工程定位源码。重定向编译输出目录不会改变架构检查范围，源码缺失会明确报错。数据源清单测试读取实际打包的 classpath 资源。


<a id="overview--magic-api-说明"></a>

### magic-api 说明

Magic 与普通接口共用 `ApiErrorResolver`。异常链或调用栈明确来自 Elasticsearch 客户端时，连接拒绝、连接重置、DNS/TLS/连接关闭分别归入全文检索连接错误，超时归入全文检索超时；服务返回 401/403、429/502/503/504、其他查询错误时分别显示认证授权、暂时不可用及查询配置提示。对应错误码为 `SEARCH-CONNECTION-001`、`SEARCH-TIMEOUT-002`、`SEARCH-AUTH-003`、`SEARCH-UNAVAILABLE-001`、`SEARCH-QUERY-004`。页面优先展示可执行的中文提示，详情保留原异常和跟踪号。不能只凭 `Connection reset` 字样把数据库、HTTP 或脚本参数错误判成全文检索故障，也不能用空结果冒充查询成功。

数据源树统计仍按租户索引检索候选，再一次性按 `tenant_id`、有效状态与角色数据范围核验所有候选 ID；20/100 个候选均为一次 ES 查询、一次 SQL 读取和一次批量权限过滤。验证资源统一使用 `validation` 前缀，演示数据表使用 `demo`；过滤条件与验证资源更名需要同时发布并同步检索投影。

项目依赖 magic-api 提供动态 API 能力。若前端访问 `/sym/menu`、`/portal/index` 等接口返回 `No static resource ...`，通常说明 magic-api 脚本没有加载。

排查方向：

- 检查 magic-api 工作目录是否存在接口脚本。
- 检查是否从数据库资源模式加载脚本。
- 检查 Nacos 中的 magic-api 配置是否已下发。

文件资源模式示例：

```yaml
magic-api:
	web: /magic/web
	show-sql: true
	sql-column-case: camel
	resource:
		type: file
		location: D:/data/magic-api
```


<a id="overview--前端联调"></a>

### 前端联调

各前端通常通过 Vite 代理访问同一个后端：

```env
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://localhost:8088
```

对应工程：

- `data-elements-chengtian`：澄天数据中台，默认端口 `3000`
- `data-elements-haitong`：海通数据集成中心，默认端口 `3002`
- `data-elements-idaas`：统一身份管理平台，默认端口 `3005`
- `data-elements-wanxiang`：万象数据治理中心，默认端口 `3010`

旧文档中出现的 `data-element-designer` 是另一个工程，不在当前 Git 仓库内。


<a id="overview--常见问题"></a>

### 常见问题


<a id="overview--maven-在父目录运行失败"></a>

#### Maven 在父目录运行失败

请进入 `data-elements-parent` 目录执行 Maven，或使用 `-f data-elements-parent/pom.xml` 指定 POM。


<a id="overview--端口被占用"></a>

#### 端口被占用

通过 `SERVER_PORT` 调整服务端口，或释放已有进程。


<a id="overview--redis-或数据库连接失败"></a>

#### Redis 或数据库连接失败

检查 Nacos 配置、本地 profile 或环境变量是否正确，尤其是密码、库名和主机地址。


<a id="overview--nacos-不可用"></a>

#### Nacos 不可用

当前 `application.yml` 使用 `optional:nacos:` 导入配置。Nacos 不可用时应用可能仍会继续启动，但缺少数据库、Redis、magic-api 等配置时业务接口会异常。

<a id="nifi-json-services"></a>

## NiFi 公共 JSON 服务引用规范

平台发布任务时，所有 JSON Record Reader / Writer 都引用当前 NiFi 节点的真实 `NiFi Flow` 根作用域服务。

| 用途 | 全局服务名称 | 类型 |
| --- | --- | --- |
| JSON 读取 | `PUBLIC-READER-JSON` | `org.apache.nifi.json.JsonTreeReader` |
| 非 Hive 库 JSON 写出 | `PUBLIC-WRITER-JSON` | `org.apache.nifi.json.JsonRecordSetWriter` |
| Hive 读写链路 JSON 写出 | `PUBLIC-WRITER-JSON-TIME_FORMAT` | `org.apache.nifi.json.JsonRecordSetWriter` |


<a id="nifi-json-services--发布约束"></a>

### 发布约束

- 完整发布、字段映射单节点升级、字段映射级联、JDBC 来源与目标、JSON 转换和 SQL Record 链路采用同一引用规则。Hive 来源读取、无其他库写入目标的 Hive 转换链、Hive 目标的上游原始采集链使用时间格式 Writer；其他数据库分支使用普通 JSON Writer。映射写入 Oracle 等其他目标时，即使来源是 Hive，映射及目标合并等处理也使用普通 Writer。
- 混合画布按发布后的实际连接关系分类，不能因为画布存在 Hive 就把其他库分支切换为时间格式 Writer。写入 Hive 的原始分支绕过映射后，共享映射仍供 Oracle 等目标消费时，该映射使用普通 Writer。
- 仅校验当前流程实际需要的 Writer；非 Hive 流程不依赖时间格式 Writer，也不能在普通 Writer 缺失时以时间格式 Writer 替代。
- 编译器读取实际 Flow 根组及其直接 Controller Services，不把配置的租户／应用子组视为全局根，不按同名任务级服务替代。
- 同一编译过程只做一次全局服务列表读取，在内存中分配给全部节点；20／100 个来源或映射节点不增加全局服务读取次数。
- 全局名称必须唯一、类型正确、处于 `ENABLED` 且无校验错误。缺失、重名、未启用或类型错误时，在清理旧流程、停止升级对象或创建新流程前给出明确错误。
- 不自动创建 `JsonReader`、`JsonWriter`、来源类型化输入、类型化输出、统一映射类型化输入或独立合并 JSON Reader 等任务级替代服务。
- `typed-record-reader` 兼容属性也引用同一个全局 Reader；遗留 `mergeSchemaText` 不再创建专用 JSON Reader，实际读取策略由全局服务配置决定。
- 日期、时间、Schema、输出分组等属性以选中的全局服务当前配置为准。平台不覆盖这些属性，不启停全局服务，也不额外创建 Schema 服务。
- CSV、Excel、Avro、数据库连接池及字典服务仍按自身类型与用途配置；JSON 统一引用不改变它们的服务类型。


<a id="nifi-json-services--历史流程调整约束"></a>

### 历史流程调整约束

历史调整应先批量读取原生流程与平台发布关联，逐流程核验租户、流程 ID 与 `data-elements:v1:<tenant>:pipeline:<id>` 归属标记。缺少关联或无法证明归属的原生流程不自动修改。

在保留处理器 ID、连接、队列和采集状态的前提下，只替换相关处理器／服务属性中的 JSON 服务 ID。需要停机修改的处理器应记住原状态，配置完成后只恢复原先处于运行状态的处理器。不得通过重建流程、清队列或清采集水位完成引用调整。

任务级重复服务必须在重新读取全节点引用并确认没有依赖后才能禁用和删除；仍有外部引用的服务应保留并报告。全局服务的原配置和运行状态须在调整后保持一致。配置备份保存在受限运行目录，不能把可能含连接信息的完整流程快照写入 Git。

<a id="task-runtime"></a>

## 任务运行状态

任务监控、接入任务列表（含 Hive 元数据库）与海通画布的当前状态来自 `TaskRuntimeStatusService`，以 NiFi 当前调度状态为准。任务表中的 `task_status` 和流水线生命周期状态保留给业务操作、历史兼容与部署记录；读取状态不再回写任务表，也不清除部署关联。


<a id="task-runtime--读取与推送"></a>

### 读取与推送

- `POST /ods/task/page` 在同一次分页查询中返回 `runtimeState`、`runtimeLabel`、`runtimeFresh`、`runtimeObservedAt`、`running` 等字段。按当前状态筛选在分页前完成，总数与当前页使用同一快照。
- `POST /ods/dataAggPage`、`POST /ods/hiveTargetPage` 的表级列表复用同一节点快照，为当前页已授权、可达的每一段任务返回上述状态。第一段来源库 → 汇聚库与第二段汇聚库 → Hive 各按自己的流程组识别；不把首段状态或整行历史执行结果套到下游。流程主记录存在时，以其 `nifi_process_group_id` 为准；仅旧任务没有有效流程主记录时使用任务上的流程组引用。
- `GET /nifi/api/pipelines/{id}/status` 使用相同快照，同时返回该流程的处理器/队列指标和原有部署信息。
- `GET /nifi/api/task-runtime/events` 是带原有认证请求头的 SSE。消息只有状态版本、采样时间与连接健康，不包含任务 ID、完整 NiFi 树或凭据。浏览器收到状态版本变化后重新读取当前授权页。
- 同一后端实例内，同一租户/节点共享一次递归状态读取；有页面订阅时正常约每 2 秒读取一次，无订阅时没有后台状态采集。页面首次查询也可按需刷新已过期快照。
- 平台启动、停止、部署完成后使对应租户缓存失效；NiFi 原生页面上的操作由下一次共享采集发现。实际延迟还取决于 NiFi 响应与调度切换耗时。
- 隐藏或离开页面时关闭流，回到页面时重新连接；断连使用退避重试，连续 20 秒没有收到数据时主动重连并标记状态待确认。服务端约 55 秒正常结束一次连接，避开 MVC 60 秒超时；续订重新经过现有会话校验。SSE 异步结束阶段补齐 SaToken 请求上下文，原登录与租户拦截器照常执行。请求头携带认证信息，URL 中没有 token。
- 接入任务列表收到状态版本变化后合并刷新当前页，保留分页与筛选。心跳和计数变化不触发列表读取；连接不可用时链路显示“状态待确认”，不会用历史字段猜测为“未启动”或“执行异常”。


<a id="task-runtime--状态语义"></a>

### 状态语义

| 状态 | 含义 |
| --- | --- |
| RUNNING | 至少一个处理器/端口处于运行调度；活动线程为零也可以是运行中 |
| RUN_ERROR | 流程仍有运行组件，同时存在无效组件；界面提供停止操作 |
| STOPPING | 已没有运行调度组件，但仍有活动线程 |
| STOPPED | 可读取的组件全部停止或禁用 |
| EMPTY | 流程存在，但没有可调度组件 |
| INVALID | 存在无效组件且未运行 |
| UNDEPLOYED | 没有部署引用，也没有仍运行的生命周期记录 |
| MISSING | 当前节点的完整快照中未找到已关联的流程，保留关联供排查 |
| UNKNOWN | 节点连接失败、状态不可读或尚无法确认；不会当作已停止 |

列表兼容值为 `0` 未启用、`1` 运行中、`2` 运行异常、`3` 待确认；具体文案由 `runtimeLabel` 决定。流程执行历史里的 DROP/EXPIRE 留在历史结果和快照中，不再将当前运行状态改成异常。


<a id="task-runtime--性能与部署"></a>

### 性能与部署

状态树只解析一次并按流程组 ID 建立索引；逐行展示不访问数据库或 NiFi。筛选中的流程组参数每组最多 500 个，仍由服务端排序和分页。现行任务尚无独立持久化节点绑定，沿用租户默认节点与现有节点树筛选约定。

NiFi 状态读取连接/响应超时分别为 3/5 秒；失效认证最多重试一次；连接失败退避至最多 30 秒。每个实例最多保留 16 个活动节点缓存、512 个订阅；状态响应上限 16 MB，状态树上限 10,000 个流程组和 100,000 个组件。超出限制或不可读会明确进入待确认状态，不能静默截断并返回正常状态。

首次上线运行状态通道需同时更新后端、海通前端及控制库中的 `access-monitoring` 低代码和 `/ods/task/page` Magic API。接入任务列表使用该通道还需发布 `data-convergence`、`data-convergence-hive` 两个低代码组件与 `/ods/dataAggPage`、`/ods/hiveTargetPage` 两个 Magic API；已有该后端通道的环境无需为这次列表接入重启后端。反向代理应允许 SSE 流式输出；接口已设置 `X-Accel-Buffering: no` 和禁止缓存的响应头。历史采样任务继续用于历史记录，不承担实时状态同步。

<a id="materialized-strings"></a>

## 跨数据库物化字符串容量规则

<a id="materialized-strings--适用入口"></a>

### 适用入口

物化申请模板、来源 DDL 导入转换、单库或批量 DDL 预览、Java 元数据建表语句生成共用 `PortableColumnType` / `PortableStringCapacity` 规则。画布的建表校验和实际建表共用 `CanvasTableDdl`，保留生成的长度单位，不依赖连接会话的隐式默认值。

登记模板保存来源数据库类型及可取得的原生类型、字符容量、字节容量、长度单位和字符集。Oracle / OceanBase Oracle 元数据在原有一次字段查询中同时读取 `DATA_LENGTH`、`CHAR_LENGTH`、`CHAR_USED`；字符容量与字节容量分开保存。编辑后的正数字段长度是本次目标定义的有效输入，不能被来源快照中的旧字节长度覆盖。

这些规则用于新生成的 DDL 和新建表，不自动修改已有物化表，不截断业务数据，也不推测字段用途。已有表的扩容、LOB 键设计调整及生产执行需要独立审查。


<a id="materialized-strings--字符数与字节数"></a>

### 字符数与字节数

PostgreSQL 的 `VARCHAR(n)` / `CHAR(n)` 用 n 个字符表示容量；Oracle 的 `VARCHAR2(n)` 可受默认字节语义影响。相同的 n 不能在两个长度单位之间直接复制。目标 Oracle 家族的普通字符串显式使用 `CHAR` 单位，如 `VARCHAR2(50 CHAR)`。

`VARCHAR2(50 CHAR)` 已表示 50 个字符，不应再无条件变成 `VARCHAR2(200 CHAR)`。UTF-8 最坏编码宽度用于检查独立的字节上限，必要时转换为大文本。即便写了 `CHAR`，Oracle `VARCHAR2` 仍受 `MAX_STRING_SIZE` 的字节限制；没有经过确认的目标配置时，按 STANDARD 的 4000 字节容量处理，不假设已开启 EXTENDED。

参考：[PostgreSQL 字符类型](https://www.postgresql.org/docs/current/datatype-character.html)、[Oracle Database 数据类型与长度上限](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/Data-Types.html)。


<a id="materialized-strings--目标规则"></a>

### 目标规则

| 目标库 | 有界字符串 | 不能完整容纳来源声明容量时 |
| --- | --- | --- |
| Oracle | `VARCHAR2(n CHAR)`；固定长最坏字节数不超过 2000 时可用 `CHAR(n CHAR)` | 普通字符串按 4000 字节保守边界转 `CLOB`，Unicode 大文本转 `NCLOB` |
| OceanBase Oracle | 显式字符单位，普通短字符串最坏字节数不超过 32767 | 转 `CLOB` / `NCLOB`；固定长超出 2000 字节改用可变长 |
| 达梦 | `VARCHAR2(n CHAR)`，规避 `LENGTH_IN_CHAR` 默认值差异 | 采用 4000 字节的跨部署保守边界转 LOB；该值不是达梦原生最大长度声明 |
| SQL Server | 使用 Unicode 类型；来源按 Unicode 字符计数时为最坏 UTF-16 容量预留 `NVARCHAR(2*n)` | 超出 4000 个 UTF-16 单元转 `NVARCHAR(MAX)` |
| DB2 | 显式 `VARCHAR(n OCTETS)` / `CHAR(n OCTETS)`；字符上限按目标最大编码宽度扩为字节容量 | 超过 32672 字节转 `CLOB`；固定长超过 254 字节改可变长 |
| MySQL / MariaDB / OceanBase MySQL | 字符数保留；常规建表默认明确 `utf8mb4`，已有登记字符集优先 | 单字段或整行容量不足时，大文本使用 `LONGTEXT`，见下节 |
| PostgreSQL / PG 家族 | 保留有界字符长度；无界 `VARCHAR` 保留无界 | `TEXT` 等长文本保持大文本 |
| Hive | 字符串使用 `STRING` | 不新增有限字符长度 |

参考：[OceanBase Oracle 模式限制](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001103406)、[SQL Server nchar / nvarchar](https://learn.microsoft.com/en-us/sql/t-sql/data-types/nchar-and-nvarchar-transact-sql?view=sql-server-ver17)、[DB2 字符串与长度单位](https://www.ibm.com/docs/en/db2/11.1.0?topic=list-character-strings)、[达梦迁移中的显式 CHAR 长度单位](https://eco.dameng.com/document/dm/zh-cn/start/mysql_dm)。

源库覆盖 PostgreSQL、MySQL/MariaDB、SQL Server、Oracle/OB Oracle、达梦及现有金仓、高斯等 PG 家族。兼容模式仍应按登记的真实类型配置；未知源长度语义按可容纳的字符上界保守处理，不只按相同类型名称替换。SQLite 来源标签按不强制长度处理，使用大文本；这不代表新增 SQLite 连接器或 DDL 导入方言。


<a id="materialized-strings--unicode-与字符集"></a>

#### Unicode 与字符集

- SQL Server `NVARCHAR(n)` 的 n 是 UTF-16 代码单元数，补充字符占两个单元。不能把已是 UTF-16 的 n 再机械翻倍。
- 目标字符集缺失时，普通 Unicode 字符按最大 4 字节估算。登记了 AL32UTF8、GBK、单字节编码等时，按对应最大宽度计算。
- Oracle 旧 `UTF8` 与 AL32UTF8 不同，补充字符以代理对表达；非 UTF-16 来源预留两个目标字符单元，再按每单元最多 3 字节检查边界。达梦的 UTF8 不套用 Oracle 旧 UTF8 规则。
- 目标 `NVARCHAR2` 结合国家字符集计算容量；已知 AL16UTF16 用每单元 2 字节，未知国家字符集按最多 3 字节保守处理。
- 已知目标为非完整 Unicode 编码而来源字符集不同或不明时，返回需编辑告警。扩大字段不能解决目标字符集无法表示某个字符的问题。
- 来源 `TEXT`、`NTEXT`、`LONGTEXT`、`MEDIUMTEXT`、`CLOB`、`NCLOB`、`NVARCHAR(MAX)` 等不降级为 `VARCHAR(255)`。无界或缺少有效上限的可变字符串使用目标无界 / 大文本。


<a id="materialized-strings--mysql-整行容量"></a>

#### MySQL 整行容量

MySQL 的 `VARCHAR(n)` 按字符数声明，同时受行字节上限约束。转换器按 utf8mb4 的最坏宽度估算整个字段集合，采用 60000 字节保守预算并预留固定列开销。从容量最大的普通非键字段开始转为 `LONGTEXT`；主键、唯一键、索引字段及带默认值字段不会为了凑行容量被静默改成大文本。受保护字段自身超过预算时返回需编辑告警。LOB 默认值的语法依 MySQL 版本而不同，存在默认值时也返回需编辑告警。

参考：[MySQL 字符类型](https://dev.mysql.com/doc/refman/8.4/en/char.html)、[MySQL 存储要求](https://dev.mysql.com/doc/refman/8.4/en/storage-requirements.html)。


<a id="materialized-strings--键默认值和校验"></a>

#### 键、默认值和校验

Oracle 家族的 LOB 不能直接充当主键或唯一键。预览保留字段和约束，并返回需编辑告警；画布校验及实际创建均在连接目标库前明确拒绝该组合，提示调整键设计或确认可用容量。不能静默删除约束或截短数据。

DB2 的长度单位使用 token 解析扩展兼容现有 Druid 版本，并在 AST 输出时保留 `OCTETS` / `CODEUNITS32` 等单位；不对 SQL 文本做全局替换，默认值字符串保持原值。不同目标的默认表达式继续经过已有转换器，不能解决的表达式保留告警。


<a id="materialized-strings--批量与验证边界"></a>

### 批量与验证边界

容量换算只处理已取得的字段模型，不逐字段查询、不自动扫描源表。批量 DDL 请求最多 20 个目标库、每目标最多 500 个字段；一次按当前租户读取整个目标集合，再按现有角色范围逐 ID 校验，之后仅做 CPU 转换。测试覆盖 20/100 字段、20 个目标库均为一次目标集合读取，拒绝无权限或其他租户 ID 时不生成任何 DDL。

目标真实字符集及实际最大数据长度如需画像，应独立批量执行并审查成本；当前规则不用一次全表扫描作为点击物化的前置条件。没有真实数据库实例的方言仅声明单元测试 / 语法验证覆盖，不能据此宣称所有目标实库均已验证。Hive 实库按用户要求不做联调。

开发 Oracle 实测 AL32UTF8 / AL16UTF16：旧 `VARCHAR2(50 BYTE)` 写入 50 个中文复现 ORA-12899；通过平台生成并创建的 `VARCHAR2(50 CHAR)` 完整保留 50 个中文及 50 个 Emoji。1000 个 Emoji 达到 4000 字节边界，1001 个 Emoji 自动使用 CLOB。20/100 行分别一次 JDBC 批处理写入、一次查询回读，逐值一致；测试对象均为独立临时测试表并已删除。

<a id="guide-4d710a30"></a>

## 布控代码目录

布控引擎使用标准 Maven 目录，运行普通 `mvn clean package` 即会编译并打入后端 JAR。

| 内容 | 后端工程内的路径 |
| --- | --- |
| 布控 Java 源码 | `src/main/java/com/linewell/dataelement/bk/` |
| 布控测试 | `src/test/java/com/linewell/dataelement/bk/` |
| 布控 NiFi 组件清单 | `src/main/resources/manifests/transforms/surveillance-match.json` |

布控根包保留 `api`、`application`、`domain`、`infrastructure` 分层。`SurveillancePipelineExtension` 通过通用扩展接口提供 NiFi 模板及流程分组策略，核心代码不直接依赖其实现类。目录调整不改变布控 HTTP 路由、数据库表名及 Kafka 业务配置；已有租户隔离、批量查询、分页和排序校验继续生效。

移除该模块时需一并审查前端入口、已部署的 NiFi 流程及业务数据引用；构建使用干净输出目录，避免旧 class 留在产物中。

<a id="mapping-extension"></a>

## 字段映射扩展

此模块与 Java 后端编译同一份 `../src/main/java/com/linewell/dataelement/mapping/` 源码。字段函数和条件在 JVM 执行；外部码表只执行经登记授权后的参数化批量 SELECT。

Hive 作为目标库时的原始写入发布规则见[详细设计第27章](data-elements-haitong.md#field-mapping)：Hive 分支旁路字段对标，HDFS 写入不再生成 MergeRecord，上游查询复用根作用域已有的 `PUBLIC-WRITER-JSON-TIME_FORMAT`，ConvertRecord 复用根作用域已有的 `PUBLIC-READER-JSON`，不生成任务级同名服务。Hive 上游 GenerateTableFetch / QueryDatabaseTableRecord 的 Columns to Return 显式清空。缺少全局 JSON 服务时在清理旧流程前拒绝发布。第27章的 Hive 发布规则本身只涉及共享后端和 Hive 组件清单；字段引擎升级仍须使用下文与目标NiFi匹配的 NAR。已有原生流程须受控完整发布后才改变拓扑；HDFS CSV 时间文本还须验证来源/JVM/目标时区，不能按普通 JDBC 的恢复语义推断。


<a id="mapping-extension--当前版本1126"></a>

<a id="mapping-extension--当前版本1127"></a>

### 当前版本（1.1.27）

推送 JSON 的 `eventNo`、`cameraIndexCode`、`picBase64` 等驼峰字段可以被登记的 `event_no`、`camera_index_code`、`pic_base64` 规则读取。输入 Schema 与逐记录取值使用同一套名称解析，精确名称优先且包含显式 NULL；没有精确名称时匹配标准驼峰/下划线形式。纯大小写差异仍遵循已有 `caseSensitive` 设置，关闭大小写敏感时才采用唯一的大小写/分隔符匹配；多义匹配返回 `AMBIGUOUS_FIELD`，缺字段、类型变化及非空约束继续拒绝。预览与 NiFi 共用该内核，不在 Reader 里复制、重写整批业务 JSON，也不根据字段名称猜测时间格式。

推送时间字段继续按已保存的 DATE/DATETIME 登记或显式日期步骤转换，AUTO 支持秒/毫秒数字字符串；没有日期规则的文本列保留文本。图片字段完整经过既有有界窗口，目标物理列仍须具备相应文本容量：Oracle/OceanBase Oracle 使用 CLOB，MySQL 使用 LONGTEXT 等；本次兼容不自动修改已有业务表。空字符串经过 Oracle 字符列时按数据库语义成为 NULL，可空字段保持可空。回归使用真实 JsonTreeReader，覆盖14字段、20/100条、2万字符图片、毫秒时间和隔离的 Oracle/MySQL 兼容模式两级 JDBC 往返；兼容模式验证不代表真实生产驱动已验收。

删除平台按“集群连接节点数大于1”拒绝发布的限制。NiFi 2.9.0 的 processor-types / controller-service-types 由协调节点复制请求并返回各节点共同支持的类型及 Bundle；平台每张图只读取一次这两份列表，缺扩展仍在修改运行图前给出具体坐标，不逐组件查询集群节点。

新流程仅发布 Reader、可选类型化 Reader、Writer、可选字典服务、执行计划及默认重试配置，不再单独生成 `plan-hash`、`plan-id`、`context-node-id`、`required-engine-version`。平台发布引用与稳定节点上下文自动写入执行计划的 `_deployment`，不参与字段规则计算。手工组件只需填写服务和计划；没有平台上下文时自动使用处理器ID。四个旧属性以可选动态兼容项保留，原位切换 Bundle 后可读取旧配置，但旧摘要和旧补丁版本不再阻止启动。

摘要自动计算，用于诊断，不作为队列重放门槛。升级或修正规则后，排队的原输入和有效错误信封按当前规则处理，保留原记录下标、类型、输入身份与时钟。实际计划格式、函数与字段规则、字典绑定权限仍校验；未支持的协议或无效输入不会被当成空规则执行。平台发布及启动继续校验当前租户、角色、流程归属与已发布引用。

匹配 NiFi 2.9.0 的构建包位于 `logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.27.nar`，生产各节点安装相同包。新后端与 NAR 同步更新；既有组件的 Bundle 使用 NiFi 原位切换或平台字段节点升级入口调整，保留队列、来源 ID 和增量水位。NiFi 自身及 GenerateDateTableFetch 继续使用2.9.0。多节点请求语义依据 [NiFi 2.9.0 FlowResource](https://github.com/apache/nifi/blob/rel/nifi-2.9.0/nifi-framework-bundle/nifi-framework/nifi-web/nifi-web-api/src/main/java/org/apache/nifi/web/api/FlowResource.java) 与 [DocumentedTypesMerger](https://github.com/apache/nifi/blob/rel/nifi-2.9.0/nifi-framework-bundle/nifi-framework-cluster/src/main/java/org/apache/nifi/cluster/manager/DocumentedTypesMerger.java)。生产尚未安装此包，不能把本地 SDK/隔离 JDBC 回归宣称为生产多节点或最终落库验收。


<a id="mapping-extension--上一版1125"></a>

### 上一版（1.1.25）

共享内核、函数库、处理器与字典服务统一使用 **1.1.25**。AUTO 支持10位秒、11至13位毫秒时间戳，修复早于2001年的日期因毫秒值不足13位被误报 `DATE_UNIT_REQUIRED`。数值 `197395200000`、普通数字字符串和一层双引号字符串均可识别；`Asia/Shanghai` 下对应 `1976-04-04 00:00:00`。日历8位/14位及显式输入格式的优先规则保持不变，不将非法日期改判成时间戳。

已登记的 Oracle / OceanBase Oracle 物理 DATE 通过 JSON 传递时可变成毫秒整数。来源 Schema 的 `dateContainsTime` / `physicalType=DATE`，或紧邻来源的字段快照，为日期规则携带 `numericTemporalUnit=EPOCH_MILLIS`；纯数字载体按明确单位解析，覆盖1970附近的0、短位及负值，不按位数误判为秒。格式化日期文本仍按原规则解析，显式指定的日期格式/时间戳单位优先。普通数值列、混合来源和经过中间变换的未知载体不猜来源单位。输入 Reader 对已声明的日期字段接受数字/字符串及其联合类型载体，实际值仍必须通过日期转换；布尔、数组或对象联合类型不放宽。

本次匹配 NiFi 2.9.0 的干净构建包位于 `logs/mapping-historical-date/final-nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.25.nar`。后端及 NAR 须同版发布；已有原生组件仍绑定旧 Bundle，需受控升级字段节点或重新发布后使用新内核。NiFi 主程序及来源采集 GenerateDateTableFetch 保持2.9.0。本次不新增表结构或按记录查询，不清空原队列、水位和已保存计划。


<a id="mapping-extension--上一版1123"></a>

### 上一版（1.1.23）

共享内核、函数库、处理器与字典服务统一使用 **1.1.23**。数值 `1496742868000`、普通数字字符串以及值本身带一对双引号的数字字符串（如 JSON 中的 `"\"1496742868000\""`）按相同日期契约转换；允许外部及引号内部空白，最多去除一层完整双引号，只对整数字符串生效。AUTO 保留10位秒、13位毫秒及严格日历日期识别；显式 EPOCH 单位也支持该输入。残缺/嵌套引号、小数文本、未知单位和非法日期仍报可定位错误，不默默转换为 NULL。日期识别在内存完成，不新增字典或数据库查询。

本次匹配 NiFi 2.9.0 的包位于 `logs/mapping-epoch-strings/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.23.nar`。后端和 NiFi 扩展须同版发布，可直接从已安装的旧版本升级，不要求先安装1.1.22；已有流程保留原 Bundle、队列和水位。NiFi 主程序及来源采集 `GenerateDateTableFetch` 仍使用2.9.0，其版本与字段治理扩展独立。发布前检查列出后端要求坐标、实际已注册处理器和字典服务坐标，两个注册项须同版匹配。


<a id="mapping-extension--上一版1122"></a>

### 上一版（1.1.22）

共享内核、函数库、处理器与字典服务使用 **1.1.22**。日期自动识别和目标日期校验现在支持10位秒、13位毫秒时间戳，接受整数值及对应数字字符串（含正负号），不要求额外添加转换步骤。`1496742868000` 在 `Asia/Shanghai` 对应 `2017-06-06 17:54:28`；输出 DATE 或 DATETIME 仍遵循登记格式及目标契约，不能按字段名推断或固定丢弃时分秒。

8位日历日期、14位数字日期时间、14至23位紧凑文本继续按日历严格解析；非法日历不会被改判为时间戳。AUTO 不为零、非整数数值、其他位数或微秒/纳秒数值猜单位，这些输入可选择 `EPOCH_SECONDS` / `EPOCH_MILLIS` / `EPOCH_MICROS` / `EPOCH_NANOS`。明确指定的输入格式和时间戳单位继续优先，解析使用规则时区而非 JVM 默认时区。该转换在内存中执行，不按记录查询数据库。

匹配 SDK 的新包位于 `logs/mapping-epoch-auto/nifi-2.9.0/` 和 `logs/mapping-epoch-auto/nifi-2.11.0/` 下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.22.nar`。先安装对应 NiFi 版本的扩展，再更新同版共享后端；发布器按运行内核版本精确核验并选择处理器及字典服务 Bundle，不能安装新包后仍固定发布旧版组件。已有组件仍绑定旧 Bundle，须通过受控发布或字段节点升级使用1.1.22。保留既有队列、水位和原计划引用，不能覆盖已加载的同版本 NAR 或以清空队列应用修复。


<a id="mapping-extension--上一版1121"></a>

### 上一版（1.1.21）

共享内核、函数库、处理器和字典服务升级为 **1.1.21**。运行错误保留原错误码及规则/步骤 ID，同时增加来源与目标字段、中文注释、原始输入、失败中间值、目标约束、原因与实际路由。字段注释由登记 Schema 批量进入计划，运行时不补查数据库。规则预览与原生执行采用同一定位内核；完整值仅进入受控错误正文，日志与属性使用不含值的定位说明。详见[字段治理错误定位说明](data-elements-haitong.md#field-errors)。

匹配 SDK 的包位于 `logs/mapping-error-context/final-nifi-2.9.0/` 和 `logs/mapping-error-context/final-nifi-2.11.0/` 下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.21.nar`。先装对应 NiFi 版本的 NAR，再更新后端。已有处理器仍绑定旧 Bundle，须受控升级/发布才会使用新诊断；旧错误内容不会被重写。


<a id="mapping-extension--上一版1119"></a>

### 上一版（1.1.19）

共享内核、函数库、NiFi 处理器和字典服务使用 **1.1.19**。未声明精度的 `DECIMAL` / `NUMERIC` / `NUMBER` 不再隐式转换为 `DECIMAL(38,10)`；以 `BigDecimal` 保留每条记录自己的小数位数，NiFi 在每个有界窗口生成足够的 Decimal Schema，支持 JSON 和 Avro 输出。规则输出的 `precision=0, scale=0` 表示自动保留精度；如目标物理列有明确精度，仍继承并校验物理容量，不能通过设为 0 绕过容量限制。显式 CAST / toDecimal 的精度和舍入设置继续有效。

Oracle 无参数 `NUMBER` 的元数据不使用存储字节数充当十进制精度，JDBC 的 scale=-127 哨兵归一化为自动精度。显式 `NUMBER(p,s)` 支持负 scale 和 scale 大于 precision，输出端使用能准确承载结果的 NiFi/Avro Decimal 定义。真正超限时给出目标 `DECIMAL(p,s)`、所需精度或小数位数及调整建议，不静默截断数值。

匹配 SDK 的干净构建包为 `logs/field-mapping-extension/clean-final-1.1.19-nifi-2.9.0/` 和 `logs/field-mapping-extension/clean-final-1.1.19-nifi-2.11.0/` 下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.19.nar`。先安装匹配目标 NiFi 的 NAR，再更新共享后端与海通静态资源；已部署流程使用原有 Bundle，须通过受控发布或字段节点升级选用新版本。


<a id="mapping-extension--上一版1117"></a>

### 上一版（1.1.17）

共享转换内核、函数库、NiFi 处理器与字典服务同步升级为 **1.1.17**，编辑和执行协议版本不变。此版修复数据库比较规则与 Java 严格键比较不一致导致的 `DICTIONARY_RESULT_MISMATCH` 误报：批量参数关系附带请求序号，结果按该序号回填，保留数据库自身的大小写、补空格及类型比较语义。重复键、无结果、参数绑定和批次边界检查仍然生效。

平台新发布字段治理节点或仅升级字段节点时，`failure`、`retry` 合并为返回本组件的自环，只有 `invalid` 单独连接保留 Funnel。failure 保留原始输入并按默认30秒、最高5分钟指数退避，瞬时字典错误先走有次数上限的 retry；达到上限后进入 failure 自环。invalid 保存错误信封供定位和修复，不自动回灌。failure 的属性和日志提供受控说明及异常类型；成功恢复时清理这些错误属性。该连线由后端编译器生成，不自动改变已运行图；部署指纹已更新，后续明确部署使用新连线，不能为应用此默认值清空已有队列或来源水位。

匹配 SDK 的干净构建包如下，不能跨 NiFi 版本互换：

- NiFi 2.9.0：`logs/field-mapping-extension/clean-1.1.17-nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.17.nar`。
- NiFi 2.11.0：`logs/field-mapping-extension/clean-1.1.17-nifi-2.11.0/haitong-mapping-nar/haitong-mapping-nar-1.1.17.nar`。

两个包均通过组件测试及重复类 / SPI 注册检查。开发 NiFi 2.9.0 已通过 NAR Manager 安装 1.1.17，使用合成输入实际执行 79 条规则，20 / 100 行均通过。没有替换已有业务流程的 Bundle、队列、水位和计划。生产未部署。

后端同步修复只读字段探查的完整展示、宽表 Schema 校验及旧字典引用绑定，见[详细设计第33章](data-elements-haitong.md#field-mapping)。发布顺序为：先安装与目标 NiFi 匹配的新 NAR，再更新后端和前端 / 选定低代码组件。已有原生流程的连接不会因替换 NAR 自动改变，需在保留队列和来源状态的前提下受控发布或调整拓扑。


<a id="mapping-extension--上一版全功能验收1116"></a>

### 上一版全功能验收（1.1.16）

当前共享内核、函数库、处理器及字典服务均为 **1.1.16**，执行协议仍为 `schemaVersion=2.0` / `planVersion=1.0`。本地后端使用开发数据库 `192.168.175.86`；开发 NiFi 为 `https://10.231.176.56:8443`（NiFi 2.9.0、Java 21）。该节点已通过 NAR Manager 安装本版，并以隔离合成流程验证；未替换已有业务任务的处理器 Bundle、队列、水位或执行计划。

本轮分别构建并检查以下两包，必须按目标 NiFi 版本选择，不互换部署：

- NiFi 2.9.0：`logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.16.nar`。
- NiFi 2.11.0：`logs/field-mapping-extension/nifi-2.11.0/haitong-mapping-nar/haitong-mapping-nar-1.1.16.nar`。该组合本轮完成匹配 SDK 的构建和组件测试；本轮真实原生验收运行在开发 2.9.0 上。

在仓库根目录重新构建与核验的 PowerShell 示例：

```powershell
mvn -f data-elements-parent/mapping-extension/pom.xml clean package '-Dnifi.version=2.9.0' '-Dmapping.output.root=D:/Sourcecode/data-elements/logs/field-mapping-extension/nifi-2.9.0'
python tools/check-mapping-nar.py logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.16.nar
mvn -f data-elements-parent/mapping-extension/pom.xml clean package '-Dnifi.version=2.11.0' '-Dmapping.output.root=D:/Sourcecode/data-elements/logs/field-mapping-extension/nifi-2.11.0'
python tools/check-mapping-nar.py logs/field-mapping-extension/nifi-2.11.0/haitong-mapping-nar/haitong-mapping-nar-1.1.16.nar
python tools/verify-mapping-features.py --token-file <本机受限开发会话路径>
```

`verify-mapping-features.py` 使用53个直接函数及26条操作/恢复组合生成79条规则，调用真实 capabilities、compile 和 preview；只输出合成数据到忽略的 `logs/mapping-all-features/`。前端 `tests/mapping-audit.html` 直接挂载正式编辑器和开发接口，仅在本地开发服务器使用，输入的会话只保存在页面内存，不包含业务写入节点，不进入生产构建入口。生产升级仍按下方备份、构建、核验 Bundle、更新后端和页面、受控升级已有节点的顺序执行；下文演示部署位置和旧版本记录是历史环境说明，不代表本版已部署到演示或生产。

1.1.16 修复失败恢复使用实际规则输入、附加步骤状态不更新、函数可变参数入口缺失、字典单值/拆分模式切换丢配置、手工字段日期转换输出类型未同步等问题。中文“时分秒”输入纳入逐值自动识别，登记统一格式继续决定 DATE / DATETIME，不按字段名推断，不将非法日期宽松修正。

动态 ANY / ARRAY 输出按512行/8MiB窗口推导实际结构。JSON writer 使用原始记录写出；Avro 保留完整的数组/Map元素可空定义以及数字文本与数值的联合类型，避免把对象转成 Java Map 文本。动态输出可能每个窗口产生一个 success FlowFile；同一输入的所有派生输出仍在同一 Session 提交，后续写出失败会移除已生成的派生输出。静态目标类型保持原单写出路径。

**已确认的 NiFi 原生边界：** 本版生成的 Avro 文件经独立 Avro 解码，混合 JSON 中 `"12"`、`12`、`true`、`null` 均保留原类型；NiFi 2.9 原生 AvroReader/ConvertRecord 再读取含这类混合联合类型的数据时，会将部分数字文本隐式转为数字。这发生在下游原生读取/转换阶段。需要保留异构 JSON 原始类型的规则使用 JSON Record Writer，或明确输出为单一标量类型；不能把“Avro文件正确”表述成“所有下游读取器均无损”。

本轮未新增表/字段，不需要数据库结构迁移。Hive 实库验证按用户要求排除；本轮不是所有数据库驱动、Hive落库或大规模集群性能认证。具体覆盖、批量 SQL 数和部署边界见[详细设计第30章](data-elements-haitong.md#field-mapping)。


<a id="mapping-extension--构建与产物检查"></a>

### 构建与产物检查

以下为先前演示版本的构建及部署记录；本次1.1.16的当前基线、产物与验收结果以上一节为准。该历史运行基线为 Java 21、NiFi 2.11.0、扩展/共享内核/函数库 1.1.13。编辑协议为 schemaVersion=2.0，执行计划协议为 planVersion=1.0，不随包版本机械改号。1.0.4 的历史实施记录保留在 [详细设计第 21 章](data-elements-haitong.md#field-mapping)，1.1.1 历史契约见该文档第 22 章，当前混合日期设计与验证边界见第 26 章。其他 NiFi/Java 组合须单独验证，截图中的 NiFi 1.15 不属于此包的运行基线。

```powershell
cd data-elements-parent/mapping-extension
mvn clean package
cd ../..
python tools/check-mapping-nar.py logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar
```

必须清洁构建。NAR 插件会收集构建目录中的 bundled-dependencies；在同一目录仅执行 package 切换版本会混入旧内核，导致同名类被旧版本加载。检查工具必须确认只有一份 core、一份 runtime、无重复类且 Processor/Controller Service 均已注册。产物和测试报告在忽略的 logs 中。


<a id="mapping-extension--部署到其他环境"></a>

### 部署到其他环境

演示环境已部署本版，可从数据中台登录后进入海通使用。以下顺序适用于另一个已配置租户、数据源和 NiFi 节点的 data-elements 环境；每次升级仍须核对该环境的实际地址、数据库绑定和已有运行版本。

| 部署项 | 本地产物 / 定义 | 当前演示位置 |
| --- | --- | --- |
| Java 后端 | `data-elements-parent/target/data-element.jar`，或经审阅的所选类补丁 | `/opt/data-element.jar`，`data-element.service` |
| NiFi 扩展 | `logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar` | 容器 `data-element-nifi` 内 `/opt/nifi/nifi-current/nar_extensions/` |
| 海通页面 | `data-elements-haitong/dist/` 的完整静态文件 | `/opt/nginx-1.26.2/html/haitong/`，入口 `/haitong/` |
| 行业业务结构 | 当前环境各实际租户库完整 `init.sql` 中的映射相关定义 | 演示 `baseline_demo_ga`、`baseline_demo_chengtian`、`baseline_demo_sea_fishery`、`baseline_demo_broadcasting` |

**1. 确认目标和备份。** 当前已验证运行组合是 Java 21、NiFi 2.11.0 单节点。后端共享内核、函数库和 NiFi 扩展均须为 1.1.13。保存后端 JAR、海通静态目录、当前节点配置和所触及数据库结构的受限备份，记录来源 Processor ID、来源 state / 增量水位及发布计划引用。核对磁盘余量和 JDBC 驱动；当前演示 MySQL 驱动为容器中的 `/opt/nifi/drivers/mysql-connector-j-8.0.33.jar`。其他驱动须部署到 NiFi 服务主机/容器能读取的位置，并填写已登记数据源对应的驱动类和连接配置。浏览器工作站上的驱动路径不能作为 NiFi 运行路径。

**2. 更新所有实际行业租户库。** 本功能新增 `data_access_mapping_plan_t`、`data_access_mapping_resource_t`、`data_access_mapping_publication_t` 三张表，分别保存不可变执行计划、模板/字典快照和当前发布引用。它们属于行业业务库。演示环境四行业库已完成，统一结构为 143 张表；控制库 `baseline` 继续保存共享资源和租户定义。

已有库先与本环境的完整结构定义比较，只生成并审阅缺失表/索引/约束的增量语句，在受限备份后按该库真实方言执行。完整 `init.sql` 用于空库初始化，不能在已有库整体执行。演示实际定义在 `db/environments/demo/`；根 `db/` 的历史本地库定义有明确的待实库核验标记，不能把演示验收外推到历史达梦连接。结构变更后使用工具当前帮助支持的命令核对和导出：

```powershell
Set-Location D:\Sourcecode\data-elements
python tools/check-db.py --config <受限数据库配置路径> --authority <本环境公安租户真实库名>
python tools/export-db.py --config <受限数据库配置路径> --output <本环境完整结构目录>
```

尖括号内容须替换成已确认的实际参数；命令行不填写密码。两项工具均只读，不能代替前面的结构应用。需要检查所有行业库的表、列、主键、索引与外键，不能只检查当前登录的一个租户。

**3. 从同一源码基线构建。** Windows PowerShell 示例：

```powershell
Set-Location D:\Sourcecode\data-elements\data-elements-parent
mvn clean package

Set-Location D:\Sourcecode\data-elements\data-elements-parent\mapping-extension
mvn clean package

Set-Location D:\Sourcecode\data-elements
python tools/check-mapping-nar.py logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar

Set-Location D:\Sourcecode\data-elements\data-elements-haitong
pnpm install --frozen-lockfile
pnpm run build
```

Java 构建使用 JDK 21。海通当前清单要求 Node.js 22.12+、pnpm 12.6.0，使用仓库 `pnpm-lock.yaml`。必须检查每个命令退出状态，构建失败不能继续发布。共享后端整包升级前先合并并审阅同环境其他已发布改动；本次演示使用所选类补丁，保留了未选中类和资源。补丁工具见 `tools/package-backend-overlay.py --help`，其基准摘要不匹配时先解决并发版本差异。

**4. 安装扩展并核验 Bundle。** 当前演示 `nifi.nar.library.autoload.directory` 明确指向 `/opt/nifi/nifi-current/nar_extensions`。新环境先检查自身配置。把通过检查的 NAR 上传到服务主机的暂存目录，再放入已确认的自动加载目录；上传时先使用 `.part`，完整到达并设置 NiFi 用户可读权限后再改为 `.nar`，避免加载未完成的文件。以下 Linux 示例中的工作目录须已经包含经过校验的 NAR：

```bash
docker cp haitong-mapping-nar-1.1.13.nar \
  data-element-nifi:/opt/nifi/nifi-current/nar_extensions/haitong-mapping-nar-1.1.13.nar.part
docker exec -u root data-element-nifi chown nifi:nifi \
  /opt/nifi/nifi-current/nar_extensions/haitong-mapping-nar-1.1.13.nar.part
docker exec -u root data-element-nifi chmod 644 \
  /opt/nifi/nifi-current/nar_extensions/haitong-mapping-nar-1.1.13.nar.part
docker exec -u root data-element-nifi mv \
  /opt/nifi/nifi-current/nar_extensions/haitong-mapping-nar-1.1.13.nar.part \
  /opt/nifi/nifi-current/nar_extensions/haitong-mapping-nar-1.1.13.nar
```

此例仅适用于确认容器名、路径和 `nifi` 用户一致且尚未安装该版本的目标。演示已存在 1.1.13，直接使用即可。不能覆盖同版本已加载文件；内容改变应发布新版本并重新构建、核验。自动加载完成后，确认 `HaitongTransformRecord` 和 `JdbcHaitongDictionaryService` 的 Bundle 版本均为 1.1.13。旧版本仍被流程引用时保留。1.1.26起平台使用NiFi协调节点返回的共同扩展列表，不再按节点数拒绝发布。

**5. 更新后端和海通页面。** 先将已审阅 JAR 放入暂存路径，比较目标版本并保存备份，再原子替换实际运行 JAR。当前演示服务的检查命令是：

```bash
systemctl restart data-element.service
systemctl is-active data-element.service
curl -fsS http://127.0.0.1:8088/actuator/health
```

最后一个命令必须返回 `status=UP`。海通将本次 `dist/` 整体暂存并发布到实际 `/haitong/` 站点，保留前一版已打开页面可能仍要加载的哈希资源。当前演示检查和刷新命令为 `/opt/nginx/sbin/nginx -t`、`/opt/nginx/sbin/nginx -s reload`。新环境以自身服务和 Nginx 路径为准。生产构建 API 基址为 `/prod-api/nifi/api`，网关须代理到后端 `/nifi/api`；本地开发使用 `http://localhost:8088` 代理。共享 Magic/低代码如有本次所选变更，另外完成 `baseline` 发布、刷新和回读。本次后续扩展交付没有额外 Magic/低代码改动。

**6. 用真实会话验收。** 从数据中台正常登录并核对 `/sym/tenant/current`。后端 `/nifi/api/field-mapping/capabilities` 应返回与目标已安装扩展相同的内核版本及56项能力；海通的“编译计划”返回 `ENGINE_PLAN`，转换 `query` 为 NULL。使用受控测试表验证字段转换、字典读取和目标写入，确认 failure/retry 返回本组件、invalid 单独保留，retry 有次数上限。其他数据库要分别验证实际驱动的 Schema、标识符、精度、日期及 INSERT/UPDATE/UPSERT/DELETE 能力；目标不支持的操作须明确拒绝。未获得其测试环境前不宣称全部数据库已认证。

**7. 回退。** 后端或页面发布异常时恢复受限备份并重新验证健康和入口。字段规则从“模板 / 计划历史”选取旧版本，仅先恢复草稿，再校验、预览、保存及升级运行节点。出现 `MAPPING_UPGRADE_RECOVERY_REQUIRED` 时保持停止，核对原生边界、处理器和发布引用后处理；不清空来源 state 或业务队列。数据库回退先保持新增历史表和数据，不能通过删表丢弃计划与回退材料。


<a id="mapping-extension--业务使用步骤"></a>

### 业务使用步骤

**进入功能。** 演示从数据中台登录后进入“海通数据集成中心 → NiFi 流程”，或从真实数据接入任务打开关联流程。海通入口为 `/haitong/#/integration/overview`，画布入口为 `/haitong/#/development/canvas?pipelineId=<真实流程ID>`。本地开发执行 `pnpm run dev` 后使用 `http://localhost:3002/#/integration/overview`。独立打开页面仍使用平台的现有登录态；会话失效时回到平台登录。

**新建流程。** 在编辑状态配置来源与目标数据源/登记表，打开字段映射节点。旧 1.0 编辑器中先“编译”并选择“将当前草稿升级为统一字段规则”，进入 2.0 统一规则；新数据接入任务使用统一执行配置。随后：

1. 点击“锁定物理Schema”，保存来源和目标实际列、类型、长度、精度及可空性。此操作当前用于已登记 JDBC 表；刷新元数据并不意味着允许自动修改目标物理表。
2. 自动映射或新增规则，明确源字段/常量/表达式/系统值与目标字段。设置目标逻辑类型、最大长度、DECIMAL 精度和小数位、是否允许 NULL。
3. 对一个字段“增加转换步骤”，按需要组合去空格、类型转换、日期解析/格式化、身份证 15 位转 18 位及校验、函数、单值/拆分码表、枚举对标和输出校验。步骤依次执行；身份证转换明确世纪策略，数值转换明确舍入策略，日期明确格式和时区。
4. 码表填写已登记数据源/表引用、键列、取值列和结构化条件，设置未命中、部分未命中、NULL/空标签策略。选“实时批量读取”、允许陈旧 1～600 秒的 TTL 缓存，或构建并绑定完整的不可变快照。权限、字典定义和快照摘要在发布时复核。
5. 点击“校验”与“编译计划”。点击“样本预览”，用手工受控样例断言结果，或“从登记来源取样并预览（脱敏）”检查实际接入链路。登记来源取样的返回输入与输出均遮盖，精度/值正确性用受控样例验证。
6. 保存组件配置及画布；新流程执行正常“保存并部署”，核对 NiFi 组件无校验错误后“启动”。目标写入策略、主键/唯一键、增量字段和同步方式在来源/目标节点配置，字段规则负责产生目标记录。

**升级正在使用的旧流程。** 先确保来源不会继续产生新记录并等待队列自然排空，保留来源 ID 和水位。把选定字段配置明确转为 2.0，校验、预览并保存画布草稿，然后点击“保留来源ID升级运行节点”。平台停组后再次检查队列为零，只替换所选字段节点，保留来源/目标 ID 与水位，旧映射保留为 DISABLED；不能以删除队列、清空水位或整图重建作为升级前置处理。UI 请求会恢复升级前各组件的实际运行状态，原本停止的来源仍保持停止。

**停止后再次启动。** 若字段节点已单独升级，而画布还保存其他未发布修改，使用统一映射面板的“启动已发布运行版本”。这个动作运行当前 NiFi 图，一次性来源只执行一次；其他修改继续保留为草稿。普通“启动”的完整草稿发布校验仍生效。要让其他修改生效，另行完成它们的正常部署。

**复用与异常处理。** “模板 / 计划历史”可以保存租户模板、导入并重绑字段、查看已发布节点的计划历史；导入后继续校验、预览和发布。运行结果 success 去目标，failure、retry 合并为本组件的一条自环；只有 invalid 输出到独立保留队列。failure 按30秒起步、最高5分钟指数退避重试，invalid 不自动重新处理。兼容历史自环的 invalid 重放仍可恢复原始记录、类型和行号，避免重复包装。使用运行日志和队列统计定位错误，不把错误出口自动终止当作正常成功。固定快照用于字典结果稳定的场景，LIVE/TTL 的历史重放可能读到新字典值；来源身份和时钟稳定不能代替目标写入幂等设计。


<a id="mapping-extension--组件契约"></a>

### 组件契约

推送文件的通用传输能力由 `FileConsumptionCompiler` 编译为 NiFi 原生处理器：Magic 指定 `DELETE_AFTER_DATABASE_COMMIT` 后，`ListFTP/ListSFTP` 仅发现 `.json.done`，`FetchFTP/FetchSFTP` 下载时不删除，字段处理按整份输入失败，唯一 JDBC 目标的 `success` 才连接数据文件和完成标记的清理步骤。SFTP 使用 `DeleteSFTP`；当前 NiFi 没有 `DeleteFTP`，FTP 在写库成功后以 `FetchFTP` 的 `Delete File` 完成策略清理。读取和删除中的通信失败保留重试队列，来源不存在的异常保留待核查；不会把 JDBC 失败出口连接到清理步骤。该能力只负责协议图编译，不查询业务表、不决定登记归属或目标库。普通文件抽取仍保留源文件。

该策略要求单一文件来源、单 JDBC 目标且无分叉；多个落地库由 Magic 拆为接入链路，首库提交后成为后续链路的可靠来源。文件列表组件在集群主节点运行并保留状态，运行图迁移须保留来源组件 ID、文件/水位状态及队列。后续 JDBC 节点继承已登记的时间戳字段，不能将日期精度不足的审计列当作同日增量水位。业务主键及按主键写入负责异常重试幂等；该机制不将数据库事务和远端文件删除描述为同一原子事务。


<a id="mapping-extension--hive-来源的日期时间-json-writer"></a>

#### Hive 来源的日期时间 JSON Writer

发布 Hive 来源时，后端为当前流程组创建一份名为 `PUBLIC-WRITER-JSON-TIME_FORMAT` 的 `JsonRecordSetWriter`，将 Hive `ExecuteSQLRecord` 的 `Record Writer` 绑定到其真实 NiFi ID。直接在 Hive 来源执行的 SOURCE_DB SQL 节点也使用此 Writer。判断依据为 `source.hive`、来源配置 `dbType=HIVE` 或 Hive JDBC URL；仅目标为 Hive 不改变普通数据库来源的 Writer。

默认格式为 Date `yyyy-MM-dd`、Time `HH:mm:ss`、Timestamp `yyyy-MM-dd HH:mm:ss.SSSSSSSSS`，并写出完整 `avro.schema`。Hive 流程的 JSON Reader 和统一映射类型化 Reader 配置相同的解析格式，避免日期字符串与类型化 Schema 不匹配。名称相同的祖先共享服务不会被修改；此 Writer 的配置和生命周期属于本次发布的流程组，每个流程只创建一次。普通数据库 `ExecuteSQLRecord` 保持原有 JsonWriter 绑定。

这些设置用于消除时间序列化/解析格式不一致。Hive JDBC 已返回的 NULL、SQL 本身的转换失败和目标类型不兼容，需要分别定位，不能靠 Writer 名称恢复。NiFi 2.11.0 已用合成 DATE/TIME/TIMESTAMP 与 NULL 做实际读写核对，覆盖本次格式和配套 Reader；没有据此宣称真实 Hive JDBC、所有时间精度或 Hive SerDe 矩阵已经验收。变更作用于后续发布；有增量水位的既有流程沿用组件 ID 保护，不能通过清空 state 或强制整图重建应用此默认值。

- `HaitongTransformRecord`：Record Reader、可选的类型化 Reader、Record Writer和执行计划；摘要自动计算，平台引用及稳定上下文自动嵌入计划，旧元数据属性可选兼容。需要外部字典时绑定批量字典服务。输入具有 avro.schema 时使用类型化 Reader；计划绑定来源 schemaContract 时检查实际 Reader Schema，输出保留 Writer 的 Schema 属性。
- `JdbcHaitongDictionaryService`：发布时的已授权字典定义，以及 datasourceId 对应的 DBCP Controller Service。运行时再次比对表、schema、键、键类型、取值列、过滤条件和重复键策略，之后才执行读取、缓存或快照查找。连接凭据只进入敏感 DBCP 属性；完整固定快照仅进入敏感的 `snapshot-payloads` 属性。
- relationships：success、invalid、failure、retry。平台将 failure、retry 合并为本组件的一条自环，invalid 单独连接保留 Funnel；三个异常出口均不自动终止。error-loop-delay 默认30秒，failure 重复异常指数退避至最多5分钟；瞬时字典故障的 retry 次数上限为5，之后进入保留原文的 failure 回环。永久脏数据仍需处理原因，自环本身不会修复数据。
- 每个窗口最多 512 行、约 8 MiB；单行 1 MiB、单字段结果 256 KiB；最多 32 个字典、8 个数据源、16 个依赖阶段、20,000 个查询键。两个窗口许可限制单处理器内并发。不同 FlowFile 分别拥有执行上下文。
- 一份输入的全部窗口在同一个 ProcessSession 中提交。FAIL_FLOWFILE 在任何窗口失败时删除全部候选输出、保留原输入；ROUTE_INVALID 在同一 Session 内提交成功记录及错误信封。
- `ht.input.identity`、`ht.execution.clock` 固定重放身份与时钟。编译器将稳定画布 node.id 写入 `context-node-id`，计划摘要保存在 `ht.plan.hash.<context-node-id>`，生成器使用“输入身份 + 稳定节点身份”；更换原生 Processor UUID 后仍能保持该上下文。手工组件没有此属性时回退到处理器 ID，不能宣称跨替换确定性。1.1.26起同节点历史摘要仅作诊断，不阻止按当前规则重放；输入身份与时钟继续保留。
- `ht.dictionary.sql.count` 为当前输入的字典读取 SQL 数，不含目标写入、授权预取或快照预先构建。输入身份、时钟稳定不等于 LIVE/TTL 字典值稳定，也不等于目标 exactly-once。


<a id="mapping-extension--画布规则配置入口"></a>

### 画布规则配置入口

画布复用原有字段映射组件，不显示旧草稿提示或提供另一套编辑器。表头合并映射与字段数量，“推荐”改为“自动映射”，顶部“预览”右侧为“映射规则”。自动映射只补充未配置的目标字段。

表格“转换 / 配置”列与顶部“映射规则”共用一个弹框，分别编辑当前行和新增映射。基础区只显示源/目标字段与转换方式，按所选方式展开函数、日期、身份证、码表、条件、表达式等具体参数。空值/失败、输出约束、组合步骤和高级设置默认折叠；完整 JSON 为高级入口。后续转换、字段元数据及原有约束在简化编辑过程中保留。取消不应用，应用规则后须保存画布并发布到 NiFi 才改变运行版本。

流程设置集中配置源字段缺失、整批失败或错误记录隔离、时区、语言地区、字段大小写及未映射字段直通。模板按钮打开模板/计划历史，更多提供校验、执行计划、Schema 锁定、仅升级字段节点与已发布版本启动。预览继续调用与 NiFi 相同的执行内核。新节点使用2.0协议；打开现有配置时服务端适配后供同一表单使用，不在打开时自动改写保存内容。

码表选择先加载当前权限内的数据源，再按用户查询动作请求登记表的服务端分页（默认20、上限100），选表后一次读取字段。20/100项均为一次整组授权校验加 COUNT 和分页查询，禁止逐表补查；登记表、字段与后续执行计划按当前租户和角色范围再次校验。复合键按键列顺序配置来源，过滤只使用登记列和参数化类型值。重复键仅提供内核支持的 `ERROR` 与旧规则兼容的 `LEGACY_MAX_LABEL`，不提供依赖无排序结果的第一条/最后一条。

Hive 来源流程的新发布及“仅升级运行字段节点”均将 `HaitongTransformRecord.record-writer` 绑定到名为 **PUBLIC-WRITER-JSON-TIME_FORMAT** 的时间格式 Writer，参数与前文 Hive Reader/Writer 配对策略一致；普通数据库保留普通 Writer。NiFi 属性实际保存 Controller Service UUID，不能把显示名称硬编码为 NAR 属性默认 ID。此前默认绑定适配沿用1.1.1；本轮UUID与日期内核升级为1.1.13，需要安装新扩展。仅升级时保留源/目标组件 ID 和水位；既有来源 Writer 的修改仍应走受控发布，不能通过整图重建重置水位。


<a id="mapping-extension--字典新鲜度与资源版本"></a>

### 字典新鲜度与资源版本

`freshnessMode` 由计划固定，支持以下模式；缓存未命中仍须合并成有界批次，不允许逐记录补查。

| 模式 | 运行契约 |
| --- | --- |
| `LIVE_BATCH` | 每次执行按本批去重键读取当前字典值，不承诺历史重放结果相同 |
| `TTL_BATCH_CACHE` | `ttlSeconds` 为 1～600；按服务/租户命名空间、定义摘要和类型化键隔离；未命中及过期键合并批取。最多 5,000 项，单项值超过 4,096 字节不缓存；命中与未命中结果都可缓存 |
| `VERSIONED_SNAPSHOT` | 固定 snapshotId、snapshotHash 和 definitionHash，缺失或摘要不一致拒绝执行；运行业务字典 SQL 为零，不静默回退为实时读取 |

完整快照通过已授权定义构建，最多 5,000 行、累计序列化条目 1,000,000 字节、查询超时 10 秒；只有完整成功才保存版本。Service 启用/停用时清理缓存与快照索引。当前没有自动刷新快照、跨节点缓存一致性或自动版本淘汰的验收声明。

租户业务库的 `data_access_mapping_plan_t` 保存不可变计划，`data_access_mapping_resource_t` 保存不可变 TEMPLATE/DICTIONARY_SNAPSHOT，`data_access_mapping_publication_t` 保存可变的当前发布引用。publication 不代表完整发布事件历史。`KEEP_ALL` 是当前保留策略：不自动删除计划、模板、快照历史，也不在节点升级中清理旧 disabled 处理器。公开计划只带快照引用与摘要，不能把完整快照、凭据或样本放入 Git、普通日志或模板。

后端能力目录当前有 56 项，53 项可直接作为值函数；uuid、codeTableLookup、expression 分别映射到 SYSTEM_VALUE、LOOKUP、EXPRESSION。参数按 FunctionArgumentSchema 返回的类型/位置/默认值/枚举生成表单，实际调用还需统一绑定器校验，不能只凭函数名称判断支持范围。


<a id="mapping-extension--schema-和预览边界"></a>

### Schema 和预览边界

`/nifi/api/field-mapping/schema-contract` 按登记引用锁定来源/目标物理结构，编译与发布复核，NiFi 运行检查 Reader 输入契约。结构漂移不能触发隐式建表或重建来源。Schema 先批量授权，再按数据源分组，每个数据源一次连接、一次批量物理读取：MySQL/MariaDB 用参数化 information_schema 查询覆盖所请求 catalog/table，并保留完整 COLUMN_TYPE；其他 JDBC 在当前 catalog 的一个明确 schema 调用一次 getColumns 扫描，跨 schema 通用回退拒绝。每批最多 100 引用、8 数据源、单表 2,000 列、总响应/扫描 20,000 列。12 条选定 Schema 批读测试已通过；其他实际驱动仍需真实环境验证。

`/nifi/api/field-mapping/sample` 只从已登记表取 1～100 行、最多 2 MiB，先在服务端对原始样本执行，再对回传 sampleRows/resultRows 的全部非 NULL 值遮盖；结果标记 REGISTERED_SOURCE_MASKED。手工样本预览仍保护敏感字段及其派生 alias。来源全遮盖响应不用于肉眼断言真实值正确性，精度和转换结果由受控样例单独验证。


<a id="mapping-extension--发布与回退"></a>

### 发布与回退

先检查目标 NiFi 的版本、全部节点的 bundle 能力、磁盘余量、驱动位置、Reader/Writer 的实际属性名，再安装经过检查的新版本 NAR。演示环境允许使用已配置的 nar_extensions 自动加载目录；不能用同一版本文件覆盖已加载类。

画布旧 1.0 配置继续保留；在编译面板明确升级为 schemaVersion=2.0，或由新接入任务生成 JAVA_RECORD 标记。草稿升级、版本保存和实际运行升级是不同动作。模板/历史计划导入只改草稿，不代表已回退运行；所有引用在保存、编译和发布时重新授权。

整图重建仍受非空队列和来源 state 保护。`/nifi/api/field-mapping/upgrade-runtime` 专门替换选定字段节点，保持来源/目标原生组件 ID 及来源 state，不复制、不清空来源状态，不顺带发布画布其他修改。当前真实旧 QueryRecord → 新 Processor 替换、再次升级至 1.1.1 均已验证，来源/目标 ID 及水位逐项不变，原 STOPPED 来源未被误启动。选定图验证不能外推所有历史拓扑和故障组合。

调用必须带 pipelineId、nodeId、expectedUpdatedAt、expectedPlanHash；服务端按已保存草稿重新编译，并检查流程归属、边界连接、Reader/Writer、Bundle 与有限图范围。先确认队列明确为零，停组等处理器停止后再次确认；非空或未知队列、并发修改、未保存计划、无法核验的旧链都拒绝升级。不得删除业务队列或清空 state 来通过检查。

切换只替换字段边界和当前计划引用，旧 mapping 处理器保留为 DISABLED。`start=true` 按升级前每个 Processor 的实际运行状态恢复，不是整组全启动；原来停止/禁用的组件不能被误启动，旧字段链原来运行时才相应启动新字段处理器。`start=false` 保持停止。提交前失败尝试恢复旧边界，提交后恢复运行失败保持停止并报告；恢复不完整必须报告需人工处理，不删除来源/目标来掩盖失败。旧 1.0 单值/拆分 SQL 字典规则转换已修复并测试，选定升级失败边界 10 条通过。

普通 `/start` 保留 DSL hash 保护，有 pending 草稿时仍拒绝。UI 的“启动已发布运行版本”显式调用 `POST /nifi/api/field-mapping/start-published`，请求带 pipelineId/expectedUpdatedAt。服务端一次批量读取并重新授权当前 published 计划，核对原生来源/目标/辅助 ID、计划引用及边界；发现未登记的额外 success 候选连接阻止启动。普通未禁用组件按原生范围启动，一次性来源只 RUN_ONCE；返回 draftPublished=false，不把尚未发布的草稿标记为整图部署成功。此显式启动动作与升级后按原状态恢复的语义不同。

restore-draft、upgrade-runtime、start-published 共用 SerializedNifiMutation 保护。PipelineRepository.update 的 SQL UPDATE 同时限制 ID、tenant_id、is_del=0、旧 updated_at，影响行数不是 1 即冲突；版本时间单调递增。计划归档 20/100 节点的当前测试均为 4 条本服务 SQL，另加固定授权解析，不能沿用早期未包含新角色核验的“2 条 SQL”口径。

插件升级、Java 后端更新、海通静态页面更新及 baseline Magic 发布是不同步骤。共享资源必须逐文件比较、发布、刷新和回读；不要把安装 NAR 当成整个系统已升级。使用 tools/package-backend-overlay.py 可以只替换选定类，校验旧内容并验证未选中类与资源未变化。

数据库兼容验证分两层：纯转换不依赖数据库方言；字典 JDBC 与目标数据库操作仍需验证对应驱动、标识符大小写、Schema、权限、参数绑定和数据库原生语义。不能用 H2 或 MySQL 的结果宣称所有实际数据库均已验收。


<a id="mapping-extension--早期111已确认与待完成验证"></a>

### 早期1.1.1已确认与待完成验证

- 演示四行业库 143 张统一业务表已完成 check/export；所选后端 95 个运行类、海通前端及 NiFi NAR 1.1.1 已部署，服务 UP。本地 8088 已更新同 95 类并健康 UP，函数/内核版本一致。
- 20/100 行正式预览的精确 38 位小数、敏感 alias，以及实际来源取样输入/输出双端全遮盖已通过；同模板两版本保存与导入已通过。
- 实际 100 行同键集合连续执行两次，LIVE_BATCH 字典 SQL 为 `[1,1]`，TTL_BATCH_CACHE 为 `[1,0]`，VERSIONED_SNAPSHOT 为 `[0,0]`；不包含快照构建、授权/元数据和目标写入。
- 当前选定自动验收汇总为 22 套、120 项，失败/错误/跳过均为 0（包含 20 套后端选定测试及内核性能、原生 Processor 契约）。其中升级失败边界 10 条、Schema 批读 12 条、角色及 batchPlans 13 条、publishedLifecycle 3 条；这些子集不另行累加。
- 真实原位升级及平台 `/stop → /field-mapping/start-published → /stop` 已通过，来源/目标 ID、水位和原停止状态保留，pending 草稿未误标为整图部署成功。真实 1.1.1 MySQL INSERT/UPDATE/UPSERT/DELETE 已通过，小数与日期时间无偏移。
- 纯内核 10,000 行 × 100 字段的本机一次测量为 0.246 秒、约 40,655 行/秒，仅限内核。真实 NiFi 同规模为 1 输入 → 20 窗口 → 1 输出，21,920,001 字节全量核对通过；连同数据准备、API 等待及约 22 MB 下载的墙钟 164.937 秒不能作为 ETL 吞吐或 SLA，未经核定的 NiFi task 统计未纳入结论。
- 用户确认暂时没有其他数据库测试环境；当前为单 NiFi 节点，不宣称多节点集群、全部真实驱动/复杂类型、完整历史迁移矩阵、端到端 exactly-once 或 SLA 压测已通过。



<a id="mapping-extension--114-交互与uuid变更历史"></a>

### 1.1.4 交互与UUID变更（历史）

字段映射继续复用原组件。列表去掉映射类型、校验和全部通过标签，按当前页顺序显示连续序号；恢复原三栏连线可视化、候选勾选自动映射、三个快捷模板及自适应高级编辑器。表格“转换 / 配置”和顶部“映射规则”使用同一个配置弹框。

ODS_UUID默认使用32位UUID，保留完整有效位并绑定流程、节点、输入与记录身份；分窗口继续累计下标，拆分子输入使用不同身份，同输入重试保持相同ID。已知旧ODS生成模板编译为UUID32，人工常量及其它自定义规则保留。当前目标VARCHAR(32)无需修改结构。

登记“时间”对应DATETIME并保留时分秒，“日期”对应DATE。已选择时间目标时，日期解析默认使用日期时间；仅日期解析隐式补零点会被编译拒绝。DATE转换支持JDBC时间对象和JSON日期时间文本。来源逻辑类型、完整Schema、时间Writer和类型化Reader配套使用；Hive仍显示PUBLIC-WRITER-JSON-TIME_FORMAT。中间TIMESTAMP使用时间点语义，按计划时区还原，不能仅比较UTC中间字符串和本地数据库时间。

构建使用：

```powershell
./tools/build-mapping-extension.ps1 -MavenCommand 'mvn' -PythonCommand 'python'
```

工具执行clean package并显式检查退出码及NAR重复类、服务注册。构建产物路径和SHA-256由工具返回；校验失败不得安装，也不能覆盖已加载的同版本文件。发布参数和最新规则见 [设计第24章](data-elements-haitong.md#field-mapping)。

在已部署环境重新打开字段映射，编辑、预览并保存；首次流程正常发布即可。已有运行流程通过“更多”的字段升级入口采用新内核，并核对旧来源的时间编解码配置，保留来源ID和水位。尚未更新的运行图仍引用其原NAR/计划，不能把安装新包当作所有任务都已自动切换。

当前1.1.4已安装至演示NiFi 2.11.0并回读验证。本地与演示后端版本一致，海通静态页面和所选接入生成脚本已发布并刷新。最终包SHA-256为 `35281d08a15687af8fa92bce5bf949bb93e1abe9f2f73eab931229b6bdcd3a4b`，重复类0、服务注册通过。独立原生JDBC往返写入保留 `2026-10-14 14:27:49` 和32位ODS_UUID；两份各2,049条输入跨5个窗口得到4,098个不同ID，转换与ID生成不发SQL。正式平台停止状态发布、20/100项请求及权限拒绝也已通过，验收流程和样例已清理。前端92项验证通过；后端各测试集合及实际环境边界见 [设计验收记录](data-elements-haitong.md#field-mapping--245-本轮实际验收与交付状态)。这些结果不是全部实际数据库、集群吞吐或端到端SLA认证。



<a id="mapping-extension--116-宽表ods时间及数据库日期语义"></a>

### 1.1.6 宽表、ODS时间及数据库日期语义

本地与演示采用1.1.6；演示NiFi为2.11.0。字典定义预算从32扩展到1,000，授权批次与内核一致，重复表条件合并，逐ID范围校验保留。规则最多1,000、Schema单表2,000列、独立字典数据源8个、计划2MiB及512条/8MiB窗口仍有界。190个独立字典定义已做原生20/100行验证，每种规模190条批量字典SQL；相同完整定义仍共享读取，不能把此结果说成所有字典只查询一次。

Oracle/OceanBase Oracle的DATE保留时分秒，在读取投影中转换为TIMESTAMP并保留列名；其它库的仅日期DATE保持原意。SQL Server timestamp/rowversion按二进制原型保留，不能当作时间。带偏移文本直接按偏移生成Instant，不依赖NiFi JVM默认时区。TIME按时分秒处理，当前Time Format不保证小数精度。规则、物理Schema、读取、Writer和Reader须配套；旧来源已截断的数据不能靠仅替换字段节点恢复。

新建Linewell写入流程，存在ODS_RKSJ/ODS_GXSJ时自动生成insert追加系统时间，update仅追加ODS_GXSJ；MERGE更新排除ODS_RKSJ及存在的ODS_UUID。Oracle/OB Oracle使用SYSDATE。通过创建响应的真实属性描述绑定API键，参数缺失则报错，不创建同名动态属性；NiFi原生PutDatabaseRecord不接收这些专属参数。

针对截图中2.9.0组件，同时提供匹配NiFi 2.9.0的独立包。必须先确认实际运行版本，选择一个匹配包，不把两个同版本Bundle一起装入一个实例：

| NiFi运行版本 | 当前扩展包 | 验证范围 |
| --- | --- | --- |
| 2.11.0 | logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.6.nar | SDK、重复类/注册检查、演示原生运行 |
| 2.9.0 | logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.6.nar | 对应API编译、SDK、重复类/注册检查；未进入生产实例验收 |

版本化构建示例（Java 21）：

```powershell
./tools/build-mapping-extension.ps1 -NifiVersion '2.9.0'
```

不传NiFi版本时采用POM的当前默认2.11.0及默认输出目录；指定版本时使用独立输出目录，避免清理另一版本产物。SHA-256和父NAR版本随包核验，后端须与扩展同为1.1.6。生产尚未直接连接，本轮其它数据库为类型/发布契约和Oracle兼容SQL验证，未宣称所有真实驱动、时区、写入模式或生产数据修复已完成。完整设计见 [第25章](data-elements-haitong.md#field-mapping--25-宽表字典容量与数据库时间语义v26--116)。



<a id="mapping-extension--1110-同一字段混合日期格式"></a>

### 1.1.10 同一字段混合日期格式

复用现有字段映射的日期处理表单，输入默认AUTO逐值识别；同一列可以同时出现横线、斜线、点号、紧凑、中文、英文、ISO偏移等文本，不按首行固定格式。年月、月日、纯时间转完整日期需要显式defaultDate，数字时间戳显式选单位，非法值走现有失败策略。原有指定格式规则保留。

使用：打开“转换和规则” → 日期处理 → 自动识别 → 日期时间（保留时分秒），先预览混合样本，再保存并重新发布/受控升级字段节点。需要仅日期才选择DATE。前端预览保留小数秒，不改全局JSON时间格式。

此节为1.1.10历史记录；当前正式版本为1.1.13，升级见第28章。历史包：NiFi 2.11默认包 `logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.10.nar`，2.9匹配包 `logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.10.nar`。不要采用构建中的中间包，不覆盖已加载的同版本NAR，不把两种NiFi构建装入同一实例。

保留NiFi/JDBC已有的时间点传输约定。流程时区与NiFi JVM时区不同，中间JSON显示可能不同于业务本地时间；须核对实际目标表、时间点和串联结果，不能只修正中间文本。不同目标驱动及其连接时区仍需真实验收。本轮支持目录、补全策略、精度和端到端验证见 [详细设计第26章](data-elements-haitong.md#field-mapping--26-多格式日期逐值识别v27--1110)。



<a id="mapping-extension--1113-生产驱动与历史日期兼容"></a>

### 1.1.13 生产驱动与历史日期兼容

完整原因、日期与夏令时边界、画布编辑权限、20/100批量证据以及部署使用步骤见[详细设计第28章](data-elements-haitong.md#field-mapping)。当前共享内核、函数库、运行NAR均为1.1.13；2.11与2.9使用各自SDK构建的同名NAR，不混装。已有原生节点须受控升级Bundle和执行计划，不会随NAR上传自动切换。

输出精度以数据源登记保存的统一格式为准：DATE为日期，DATETIME为保留时分秒的时间，不按字段名或业务含义猜测。服务端在既有批量读取中核对登记快照，编译时设置输出及日期解析精度，运行过程不逐行读取登记库。修改登记后重新发布或受控升级字段节点；实际目标类型无法承载时在发布前拒绝，不自动执行DDL。


<a id="mapping-extension--1113-类型别名异常回环与-hive-画布"></a>

### 1.1.13 类型别名、异常回环与 Hive 画布

`CHARACTER VARYING`（含长度、大小写和多空格）、CHAR/NCHAR VARYING、NATIONAL CHARACTER VARYING、NCLOB、LONGNVARCHAR、BPCHAR 等统一为 STRING。显式 CAST 与函数 cast 在编译时归一化，未知 CAST 类型提前拒绝；不为字段转换生成 SQL。

Hive 作为目标时，平台加载、合成展示、保存及 NiFi 发布统一旁路该分支紧邻的字段映射链。共享 JDBC 分支保留必要映射；Hive 作为来源不触发旁路。不完整草稿可继续保存编辑，发布仍严格检查断链和循环。全局 JSON 服务不存在时仍明确拒绝发布，不创建同名任务级替代服务。

NiFi 2.9 的匹配构建：`mvn -Dnifi.version=2.9.0 -Dmapping.output.root=D:/Sourcecode/data-elements/logs/field-mapping-extension/nifi-2.9.0 clean package`。产物位于该目录下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar`。2.9 完成匹配 SDK 构建和组件测试，真实原生运行验证在演示2.11完成；使用时须按现场 NiFi SDK选择对应包。不能同时安装同一扩展版本的两个 SDK 构建。

已安装新 NAR 不会自动改变既有1.1.10等处理器的 Bundle、执行计划或连接。新流程正常发布会生成合并自环；已有字段节点使用保留来源ID升级入口，先停采集并让旧队列自然处理/按原计划保留，核对零队列后升级。字段升级不会改变 Hive 既有图的拓扑，Hive 旧任务须在来源ID/水位保护允许的受控发布流程中更新，不能清空业务队列或重置来源水位来绕过保护。

<a id="date-fetch-extension"></a>

## 数据源产品与运行驱动

登记、连接测试、元数据、分页预览及接入任务传递同一份产品配置。GaussDB 的主类型仍为 `gaussdb`，具体产品保存为 `pool_cfg.gaussdbVariant`，不靠端口或显示名称猜测产品。Hive 使用 `hiveConnectionMode` 区分开源与华为 MRS，明确开源模式时忽略遗留华为配置集。

| 产品选项 | JDBC 驱动类 | 运行驱动文件约定 | 默认端口提示 |
| --- | --- | --- | --- |
| 华为 GaussDB 集中式／分布式 | `com.huawei.opengauss.jdbc.Driver` | `opengaussjdbc.jar` | 8000 |
| 华为 GaussDB 新版原生驱动 | `com.huawei.gaussdb.jdbc.Driver` | `gaussdbjdbc.jar` | 8000 |
| openGauss | `org.opengauss.Driver` | `opengauss-jdbc.jar` | 5432 |
| GaussDB(DWS) | `org.postgresql.Driver` | `postgresql-42.6.0.jar` | 8000 |
| GaussDB(for MySQL)／MySQL 兼容模式 | `com.mysql.cj.jdbc.Driver` | `mysql-connector-j-8.4.0.jar` | 3306 |
| 历史 PostgreSQL 兼容配置 | `org.postgresql.Driver` | 原 PostgreSQL 驱动 | 5432 |
| Vertica | `com.vertica.jdbc.Driver` | `vertica-jdbc.jar` | 5433 |

端口以真实实例为准。华为 GaussDB 应安装其数据库版本配套的官方驱动，不能因为类名或协议相似而替换为普通 PostgreSQL JAR；DWS 允许 PostgreSQL JDBC，具体支持范围见 [GaussDB 驱动说明](https://support.huaweicloud.com/centralized-devg-v10-gaussdb/gaussdb-42-0080.html)、[openGauss JDBC](https://docs.opengauss.org/en/docs/7.0.0/getting_started/java.html) 和 [DWS 驱动说明](https://support.huaweicloud.com/mgtg-dws/dws_01_0032.html)。文件名是部署约定，节点可配置其他版本的完整 JAR 路径。未保存产品的历史记录保留原协议，编辑时选择真实产品；不自动重建运行任务或清空水位。

后端以 `JDBC_DRIVER_DIRECTORY` 或 JVM 参数 `data-elements.jdbc.driver-directory` 定位厂商驱动，加载前核对 JAR 内的实际驱动类。不同 GaussDB 驱动直接绑定到各自连接池，避免多个驱动接受 `jdbc:opengauss` 时由全局 DriverManager 随机选择。开源 Hive 必须在该目录的 `open-source-hive/` 安装 Apache Hive JDBC standalone 客户端，连接测试、表/字段探查和连接池共用同一个隔离驱动实例；缺少客户端时明确报错，不能回退到同名的华为 HiveDriver。华为客户端继续放在隔离的 `huawei-mrs/`，二者不混用 Hadoop/ZooKeeper 依赖。开源连接测试使用与登记、接入相同的 URL 构造器，保留 HTTP、TLS 和 NoSASL 配置，无认证模式显式传入 `auth=none`。

NiFi 驱动独立于后端安装。节点驱动映射支持 `GAUSSDB_NATIVE`（opengaussjdbc 兼容包）、`GAUSSDB_JDBC`（新版 gaussdbjdbc）、`GAUSSDB_OPENGAUSS`、`GAUSSDB_DWS`、`GAUSSDB_MYSQL`、`HIVE_OPEN_SOURCE` 等键；没有独立 GaussDB 映射时兼容现有默认目录／旧 GaussDB 映射。华为新版原生驱动使用 `gaussdbVariant=gaussdb-jdbc` 与 `jdbc:gaussdb:`，原 `gaussdb` 选项保留 `jdbc:opengauss:`，两种驱动均按 JDK 和数据库实例版本选择官方发行包。openGauss Maven 客户端需选择包含 `org.opengauss.Driver` 的 `-og` 版本，不能用同版本号下包含 `org.postgresql.Driver` 的另一个包替代。Hive 开源配置填写 Binary/HTTP、认证方式和必要 TLS/Kerberos 参数；华为模式继续由服务端配置集解析，浏览器不保存华为 keytab 内容。

SQL Server、GBase 8a、Vertica 提供统一分页预览执行器，查询字段与排序字段仍按当前来源的真实字段白名单校验。开源 Hive 首屏使用 LIMIT、后续页使用窗口函数，避免使用不兼容的 MySQL OFFSET 语法。GaussDB 按当前会话解析默认 Schema，批量字段读取使用有界 IN 查询及主键关联，最多200张表，不逐表补查。

FTPS 使用 TLS 控制及数据通道，支持21端口常见的显式 TLS和990端口常见的隐式 TLS，也可显式指定模式。后端及 NiFi 都验证证书链和主机名。画布连接测试与登记探查使用同一 TLS 工厂，在 TLS 协商成功之前不发送账号密码；认证后启用 `PBSZ 0`、`PROT P` 并核对远端目录。私有 CA 可分别配置探查端 `trustStorePath` 与接入节点 `nifiTrustStorePath`，不以关闭验证代替信任配置。NiFi 需安装 ExecuteGroovyScript 及 Commons Net 客户端，节点驱动映射可配置 `FTP=/实际路径/commons-net.jar`；目录配置按该受管文件名解析，版本须支持 Duration 超时接口。

FTPS 密码保存在 NiFi 敏感动态属性中，脚本正文不含凭据。发现状态在 FlowFile 持久化提交后更新；状态竞争可能重放，但不得丢弃数据。普通抽取不删除来源。仅显式选择落库后删除策略时，经 JDBC 成功出口核对来源文件大小和修改时间，再确认删除数据及完成标记；失败保留队列和文件，不能在下载成功时提前删除。

登记页数据抽取方式的代码覆盖如下。表中的已有链路表示保留现有实现，不表示所有厂商版本已完成实库认证；接入节点驱动与后端探查驱动分别部署。

| 数据源 | 登记、探查与接入的代码路径 | 部署时的主要条件 |
| --- | --- | --- |
| MySQL | 沿用 JDBC 元数据、预览及接入 | 真实实例版本、字符集和增量字段 |
| Oracle | 沿用 Oracle 元数据、预览及接入 | Service Name／SID、Schema 与驱动版本 |
| OceanBase MySQL | 沿用 MySQL 兼容分支 | 租户兼容模式与连接账号 |
| OceanBase Oracle | 沿用 Oracle 兼容分支 | 真实 Oracle 模式与 Schema |
| GaussDB | 按四类产品选择驱动、协议、Schema 及抽取方言；保留旧配置 | 华为原生与 openGauss 须安装配套官方驱动 |
| Hive | 开源／华为 MRS 双路径；补齐开源认证、HTTP/TLS 与预览分页 | Hive 客户端依赖、Kerberos／MRS 配置集 |
| GBase 8a | 沿用元数据与接入，补齐独立分页预览 | 安装 GBase 8a 厂商驱动，不替换为 MySQL 驱动 |
| SQL Server | 沿用元数据与接入，补齐方括号引用及 OFFSET/FETCH 预览 | 支持 OFFSET/FETCH 的数据库版本 |
| Vertica | 补齐分页预览、接入来源清单及驱动映射 | 在接入节点安装 Vertica 驱动 |
| 达梦 | 沿用原生 JDBC 元数据、预览及接入 | 驱动、Schema 与兼容设置 |
| PostgreSQL | 沿用 JDBC 元数据、预览及接入 | Schema、扩展类型与实例版本 |
| 人大金仓 | 沿用金仓元数据、预览及接入 | 真实兼容模式与 KingbaseES 驱动 |
| Elasticsearch | 沿用索引、字段、预览与 Record 接入 | 集群版本、认证与字段映射 |
| Kafka | 沿用主题、样本字段与消息接入 | SASL/TLS、消费组、起始偏移与消息格式 |
| MinIO | 沿用对象目录、文件字段与对象接入 | Endpoint、授权、路径与文件格式 |
| FTP／SFTP／FTPS | 沿用文件链路，补齐 FTPS TLS、敏感配置及持久化水位 | 协议、证书信任库与 NiFi Groovy／Commons Net 客户端 |

新增产品的单元测试和模拟编译验证不能代替对应版本实库验收。首次接入应核对驱动、Schema、字段类型、分页、全量、增量边界和中断恢复；当前时间／水位抽取不等同于日志 CDC，不承诺自动捕获删除。

## 时间窗口抓取扩展

数据库来源使用现有的 `com.linewell.microservice.processors.database.GenerateDateTableFetch`，完整扩展坐标为 `com.linewell.microservice:datadevs-database-nar:2.9.0`。后端发布前核对该类型及三个坐标字段，创建时显式选择 `2.9.0`；不要求安装其他后缀版本，也不修改已有 NAR 的名字或内容。

发布属性使用现有 2.9.0 的真实描述符：

- `gen-table-column-for-val-partitioning` 使用流程配置中初始化自登记表的时间戳。
- `gen-table-fetch-partition-size=10000`，按月（`fetch-partition-type=2`）、间隔 `1`、等待 `0 seconds`。
- `min-date`、`max-date` 默认不设置，不自动填入当前时间或数据库最大值。
- 普通增量及首次全量后增量的 `Maximum-value Columns` 使用相同时间戳；单次和定时全量不启用增量水位。
- 不向该自定义组件写入未声明的 `Initial Load Strategy`。既有“从当前最大值开始”模式保留原生 `QueryDatabaseTableRecord` 初始化路径，初始聚合和后续水位由 NiFi 管理；这一路径不逐表读取来源数据到后端，也不使用 Max Date 模拟跳过历史。
- 来源 Record Writer 继续使用全局服务。采集首节点直接连接 `ExecuteSQLRecord`，Oracle/OceanBase 不再自动插入 SQL 时间转换 `ReplaceText`。
- `ExecuteSQLRecord` 的 `Max Rows Per FlowFile` 使用来源配置 `batchSize`（单批最大条数，默认 5000），`Fetch Size` 使用 `maxRowsPerRun`（单次最大行数，默认 20000），`Output Batch Size=1`。这些输出与抓取设置独立于首节点固定 10000 的分片大小。
- `Fetch Size` 是 JDBC 每次抓取的建议值，不限制查询总行数；0 表示使用驱动默认值。保留历史已配置值，仅新建配置或缺省值使用新默认。

普通采集首节点仅在主节点执行。定时全量保留父组定时触发器与串行批次子组；有批次输入的采集节点使用所有节点执行策略，避免“有输入连线且仅主节点执行”的 NiFi 校验错误。

原生画布可见该类型但发布仍报错时，应核对平台实际连接的 NiFi 返回的完整坐标。发布前错误区分“类型未注册”和“类型已注册但版本/坐标不匹配”，列出要求坐标和实际坐标；同一类型同时存在其他版本时仍明确选择 2.9.0。响应中附加的非坐标元数据不影响匹配。旧流程在预检查失败时保留。

此前独立后缀版本的实验源码、构建工具和测试产物已退回忽略的运行材料目录，不属于当前发布依赖，不用于替换现有 2.9.0。

<a id="logging-framework"></a>

## 日志框架设计

认证、操作、接口、安全及任务关联的完整设计见[系统日志管理详细设计](data-elements.md#logging-design)。该设计复用行业日志表与控制库 `iam_audit_event_t`，由 `platform.audit` 提供可信上下文、脱敏和持久化原语；平台业务规则继续由现用 Magic 维护。现已增加可信快照、统一结果适配、脱敏、有界缓冲、日志查询、导出、策略及归档管理实现。五个本机数据库的结构已升级；8088 已按用户授权换包并正常启动，日志 Magic 及唯一 `log-management` 低代码组件均通过运行回读；原分散入口与统计组件经核查后删除。菜单路径快照和具体 Magic 操作名已纳入采集，查询不按行补查。针对性后端测试、四行业真实会话隔离、登录退出及文件下载验证通过；具体边界及接口以公共文档“当前实现与发布状态”为准。
