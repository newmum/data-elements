# 海通统一字段转换 NiFi 扩展

此模块与 Java 后端编译同一份 `../src/main/java/com/linewell/dataelement/mapping/` 源码。字段函数和条件在 JVM 执行；外部码表只执行经登记授权后的参数化批量 SELECT。

Hive 作为目标库时的原始写入发布规则见[详细设计第27章](../../docs/海通字段映射统一执行方案.md)：Hive 分支旁路字段对标，HDFS 写入不再生成 MergeRecord，上游查询复用根作用域已有的 `PUBLIC-WRITER-JSON-TIME_FORMAT`，ConvertRecord 复用根作用域已有的 `PUBLIC-READER-JSON`，不生成任务级同名服务。Hive 上游 GenerateTableFetch / QueryDatabaseTableRecord 的 Columns to Return 显式清空。缺少全局 JSON 服务时在清理旧流程前拒绝发布。第27章的 Hive 发布规则本身只涉及共享后端和 Hive 组件清单；字段引擎升级仍须使用下文与目标NiFi匹配的 NAR。已有原生流程须受控完整发布后才改变拓扑；HDFS CSV 时间文本还须验证来源/JVM/目标时区，不能按普通 JDBC 的恢复语义推断。

## 当前版本（1.1.19）

共享内核、函数库、NiFi 处理器和字典服务使用 **1.1.19**。未声明精度的 `DECIMAL` / `NUMERIC` / `NUMBER` 不再隐式转换为 `DECIMAL(38,10)`；以 `BigDecimal` 保留每条记录自己的小数位数，NiFi 在每个有界窗口生成足够的 Decimal Schema，支持 JSON 和 Avro 输出。规则输出的 `precision=0, scale=0` 表示自动保留精度；如目标物理列有明确精度，仍继承并校验物理容量，不能通过设为 0 绕过容量限制。显式 CAST / toDecimal 的精度和舍入设置继续有效。

Oracle 无参数 `NUMBER` 的元数据不使用存储字节数充当十进制精度，JDBC 的 scale=-127 哨兵归一化为自动精度。显式 `NUMBER(p,s)` 支持负 scale 和 scale 大于 precision，输出端使用能准确承载结果的 NiFi/Avro Decimal 定义。真正超限时给出目标 `DECIMAL(p,s)`、所需精度或小数位数及调整建议，不静默截断数值。

匹配 SDK 的干净构建包为 `logs/field-mapping-extension/clean-final-1.1.19-nifi-2.9.0/` 和 `logs/field-mapping-extension/clean-final-1.1.19-nifi-2.11.0/` 下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.19.nar`。先安装匹配目标 NiFi 的 NAR，再更新共享后端与海通静态资源；已部署流程使用原有 Bundle，须通过受控发布或字段节点升级选用新版本。

## 上一版（1.1.17）

共享转换内核、函数库、NiFi 处理器与字典服务同步升级为 **1.1.17**，编辑和执行协议版本不变。此版修复数据库比较规则与 Java 严格键比较不一致导致的 `DICTIONARY_RESULT_MISMATCH` 误报：批量参数关系附带请求序号，结果按该序号回填，保留数据库自身的大小写、补空格及类型比较语义。重复键、无结果、参数绑定和批次边界检查仍然生效。

平台发布字段治理节点时，`retry` 单独自环；`failure` 与 `invalid` 各连接独立保留 Funnel，便于查看异常，不自动重新执行不可重试输入。failure 的属性和日志增加受控说明及异常类型；成功恢复时清理这些错误属性。

匹配 SDK 的干净构建包如下，不能跨 NiFi 版本互换：

- NiFi 2.9.0：`logs/field-mapping-extension/clean-1.1.17-nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.17.nar`。
- NiFi 2.11.0：`logs/field-mapping-extension/clean-1.1.17-nifi-2.11.0/haitong-mapping-nar/haitong-mapping-nar-1.1.17.nar`。

两个包均通过组件测试及重复类 / SPI 注册检查。开发 NiFi 2.9.0 已通过 NAR Manager 安装 1.1.17，使用合成输入实际执行 79 条规则，20 / 100 行均通过。没有替换已有业务流程的 Bundle、队列、水位和计划。生产未部署。

后端同步修复只读字段探查的完整展示、宽表 Schema 校验及旧字典引用绑定，见[详细设计第33章](../../docs/海通字段映射统一执行方案.md)。发布顺序为：先安装与目标 NiFi 匹配的新 NAR，再更新后端和前端 / 选定低代码组件。已有原生流程的连接不会因替换 NAR 自动改变，需在保留队列和来源状态的前提下受控发布或调整拓扑。

## 上一版全功能验收（1.1.16）

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

本轮未新增表/字段，不需要数据库结构迁移。Hive 实库验证按用户要求排除；本轮不是所有数据库驱动、Hive落库或大规模集群性能认证。具体覆盖、批量 SQL 数和部署边界见[详细设计第30章](../../docs/海通字段映射统一执行方案.md)。

## 构建与产物检查

以下为先前演示版本的构建及部署记录；本次1.1.16的当前基线、产物与验收结果以上一节为准。该历史运行基线为 Java 21、NiFi 2.11.0、扩展/共享内核/函数库 1.1.13。编辑协议为 schemaVersion=2.0，执行计划协议为 planVersion=1.0，不随包版本机械改号。1.0.4 的历史实施记录保留在 [详细设计第 21 章](../../docs/海通字段映射统一执行方案.md)，1.1.1 历史契约见该文档第 22 章，当前混合日期设计与验证边界见第 26 章。其他 NiFi/Java 组合须单独验证，截图中的 NiFi 1.15 不属于此包的运行基线。

```powershell
cd data-elements-parent/mapping-extension
mvn clean package
cd ../..
python tools/check-mapping-nar.py logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar
```

必须清洁构建。NAR 插件会收集构建目录中的 bundled-dependencies；在同一目录仅执行 package 切换版本会混入旧内核，导致同名类被旧版本加载。检查工具必须确认只有一份 core、一份 runtime、无重复类且 Processor/Controller Service 均已注册。产物和测试报告在忽略的 logs 中。

## 部署到其他环境

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

此例仅适用于确认容器名、路径和 `nifi` 用户一致且尚未安装该版本的目标。演示已存在 1.1.13，直接使用即可。不能覆盖同版本已加载文件；内容改变应发布新版本并重新构建、核验。自动加载完成后，确认 `HaitongTransformRecord` 和 `JdbcHaitongDictionaryService` 的 Bundle 版本均为 1.1.13。旧版本仍被流程引用时保留。当前平台会阻止未经所有节点版本核验的多节点升级。

**5. 更新后端和海通页面。** 先将已审阅 JAR 放入暂存路径，比较目标版本并保存备份，再原子替换实际运行 JAR。当前演示服务的检查命令是：

```bash
systemctl restart data-element.service
systemctl is-active data-element.service
curl -fsS http://127.0.0.1:8088/actuator/health
```

最后一个命令必须返回 `status=UP`。海通将本次 `dist/` 整体暂存并发布到实际 `/haitong/` 站点，保留前一版已打开页面可能仍要加载的哈希资源。当前演示检查和刷新命令为 `/opt/nginx/sbin/nginx -t`、`/opt/nginx/sbin/nginx -s reload`。新环境以自身服务和 Nginx 路径为准。生产构建 API 基址为 `/prod-api/nifi/api`，网关须代理到后端 `/nifi/api`；本地开发使用 `http://localhost:8088` 代理。共享 Magic/低代码如有本次所选变更，另外完成 `baseline` 发布、刷新和回读。本次后续扩展交付没有额外 Magic/低代码改动。

**6. 用真实会话验收。** 从数据中台正常登录并核对 `/sym/tenant/current`。后端 `/nifi/api/field-mapping/capabilities` 应返回内核 1.1.13 与 56 项能力；海通的“编译计划”返回 `ENGINE_PLAN`，转换 `query` 为 NULL。使用受控测试表验证字段转换、字典读取和目标写入，确认 failure/invalid 有受控保留队列、retry 有次数上限。其他数据库要分别验证实际驱动的 Schema、标识符、精度、日期及 INSERT/UPDATE/UPSERT/DELETE 能力；目标不支持的操作须明确拒绝。未获得其测试环境前不宣称全部数据库已认证。

**7. 回退。** 后端或页面发布异常时恢复受限备份并重新验证健康和入口。字段规则从“模板 / 计划历史”选取旧版本，仅先恢复草稿，再校验、预览、保存及升级运行节点。出现 `MAPPING_UPGRADE_RECOVERY_REQUIRED` 时保持停止，核对原生边界、处理器和发布引用后处理；不清空来源 state 或业务队列。数据库回退先保持新增历史表和数据，不能通过删表丢弃计划与回退材料。

## 业务使用步骤

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

**复用与异常处理。** “模板 / 计划历史”可以保存租户模板、导入并重绑字段、查看已发布节点的计划历史；导入后继续校验、预览和发布。运行结果 success 去目标，failure、invalid、retry 合并到本组件的一条自环；异常内容保留在队列中，失败/无效记录按30秒起步、最高5分钟的指数退避重试。invalid 再次进入时恢复原始记录、类型和行号，避免重复包装。使用运行日志和队列统计定位错误，不把错误出口自动终止当作正常成功。固定快照用于字典结果稳定的场景，LIVE/TTL 的历史重放可能读到新字典值；来源身份和时钟稳定不能代替目标写入幂等设计。

## 组件契约

### Hive 来源的日期时间 JSON Writer

发布 Hive 来源时，后端为当前流程组创建一份名为 `PUBLIC-WRITER-JSON-TIME_FORMAT` 的 `JsonRecordSetWriter`，将 Hive `ExecuteSQLRecord` 的 `Record Writer` 绑定到其真实 NiFi ID。直接在 Hive 来源执行的 SOURCE_DB SQL 节点也使用此 Writer。判断依据为 `source.hive`、来源配置 `dbType=HIVE` 或 Hive JDBC URL；仅目标为 Hive 不改变普通数据库来源的 Writer。

默认格式为 Date `yyyy-MM-dd`、Time `HH:mm:ss`、Timestamp `yyyy-MM-dd HH:mm:ss.SSSSSSSSS`，并写出完整 `avro.schema`。Hive 流程的 JSON Reader 和统一映射类型化 Reader 配置相同的解析格式，避免日期字符串与类型化 Schema 不匹配。名称相同的祖先共享服务不会被修改；此 Writer 的配置和生命周期属于本次发布的流程组，每个流程只创建一次。普通数据库 `ExecuteSQLRecord` 保持原有 JsonWriter 绑定。

这些设置用于消除时间序列化/解析格式不一致。Hive JDBC 已返回的 NULL、SQL 本身的转换失败和目标类型不兼容，需要分别定位，不能靠 Writer 名称恢复。NiFi 2.11.0 已用合成 DATE/TIME/TIMESTAMP 与 NULL 做实际读写核对，覆盖本次格式和配套 Reader；没有据此宣称真实 Hive JDBC、所有时间精度或 Hive SerDe 矩阵已经验收。变更作用于后续发布；有增量水位的既有流程沿用组件 ID 保护，不能通过清空 state 或强制整图重建应用此默认值。

- `HaitongTransformRecord`：Record Reader、可选的类型化 Reader、Record Writer、执行计划和 SHA-256 摘要；可设置 `plan-id` 和稳定的 `context-node-id`。需要外部字典时绑定批量字典服务。输入具有 avro.schema 时使用类型化 Reader；计划绑定来源 schemaContract 时检查实际 Reader Schema，输出保留 Writer 的 Schema 属性。
- `JdbcHaitongDictionaryService`：发布时的已授权字典定义，以及 datasourceId 对应的 DBCP Controller Service。运行时再次比对表、schema、键、键类型、取值列、过滤条件和重复键策略，之后才执行读取、缓存或快照查找。连接凭据只进入敏感 DBCP 属性；完整固定快照仅进入敏感的 `snapshot-payloads` 属性。
- relationships：success、invalid、failure、retry。failure、invalid、retry 在平台发布时合并为本组件的一条自环，不能自动终止。error-loop-delay 默认30秒，重复异常指数退避至最多5分钟；瞬时字典故障的 retry 次数上限仍为5，之后进入保留内容的 failure 回环。永久脏数据仍需处理原因，自环本身不会修复数据。
- 每个窗口最多 512 行、约 8 MiB；单行 1 MiB、单字段结果 256 KiB；最多 32 个字典、8 个数据源、16 个依赖阶段、20,000 个查询键。两个窗口许可限制单处理器内并发。不同 FlowFile 分别拥有执行上下文。
- 一份输入的全部窗口在同一个 ProcessSession 中提交。FAIL_FLOWFILE 在任何窗口失败时删除全部候选输出、保留原输入；ROUTE_INVALID 在同一 Session 内提交成功记录及错误信封。
- `ht.input.identity`、`ht.execution.clock` 固定重放身份与时钟。编译器将稳定画布 node.id 写入 `context-node-id`，计划摘要保存在 `ht.plan.hash.<context-node-id>`，生成器使用“输入身份 + 稳定节点身份”；更换原生 Processor UUID 后仍能保持该上下文。手工组件没有此属性时回退到处理器 ID，不能宣称跨替换确定性。同节点计划摘要冲突拒绝重放，多映射节点串联分别校验。
- `ht.dictionary.sql.count` 为当前输入的字典读取 SQL 数，不含目标写入、授权预取或快照预先构建。输入身份、时钟稳定不等于 LIVE/TTL 字典值稳定，也不等于目标 exactly-once。

## 画布规则配置入口

画布复用原有字段映射组件，不显示旧草稿提示或提供另一套编辑器。表头合并映射与字段数量，“推荐”改为“自动映射”，顶部“预览”右侧为“映射规则”。自动映射只补充未配置的目标字段。

表格“转换 / 配置”列与顶部“映射规则”共用一个弹框，分别编辑当前行和新增映射。基础区只显示源/目标字段与转换方式，按所选方式展开函数、日期、身份证、码表、条件、表达式等具体参数。空值/失败、输出约束、组合步骤和高级设置默认折叠；完整 JSON 为高级入口。后续转换、字段元数据及原有约束在简化编辑过程中保留。取消不应用，应用规则后须保存画布并发布到 NiFi 才改变运行版本。

流程设置集中配置源字段缺失、整批失败或错误记录隔离、时区、语言地区、字段大小写及未映射字段直通。模板按钮打开模板/计划历史，更多提供校验、执行计划、Schema 锁定、仅升级字段节点与已发布版本启动。预览继续调用与 NiFi 相同的执行内核。新节点使用2.0协议；打开现有配置时服务端适配后供同一表单使用，不在打开时自动改写保存内容。

码表选择先加载当前权限内的数据源，再按用户查询动作请求登记表的服务端分页（默认20、上限100），选表后一次读取字段。20/100项均为一次整组授权校验加 COUNT 和分页查询，禁止逐表补查；登记表、字段与后续执行计划按当前租户和角色范围再次校验。复合键按键列顺序配置来源，过滤只使用登记列和参数化类型值。重复键仅提供内核支持的 `ERROR` 与旧规则兼容的 `LEGACY_MAX_LABEL`，不提供依赖无排序结果的第一条/最后一条。

Hive 来源流程的新发布及“仅升级运行字段节点”均将 `HaitongTransformRecord.record-writer` 绑定到名为 **PUBLIC-WRITER-JSON-TIME_FORMAT** 的时间格式 Writer，参数与前文 Hive Reader/Writer 配对策略一致；普通数据库保留普通 Writer。NiFi 属性实际保存 Controller Service UUID，不能把显示名称硬编码为 NAR 属性默认 ID。此前默认绑定适配沿用1.1.1；本轮UUID与日期内核升级为1.1.13，需要安装新扩展。仅升级时保留源/目标组件 ID 和水位；既有来源 Writer 的修改仍应走受控发布，不能通过整图重建重置水位。

## 字典新鲜度与资源版本

`freshnessMode` 由计划固定，支持以下模式；缓存未命中仍须合并成有界批次，不允许逐记录补查。

| 模式 | 运行契约 |
| --- | --- |
| `LIVE_BATCH` | 每次执行按本批去重键读取当前字典值，不承诺历史重放结果相同 |
| `TTL_BATCH_CACHE` | `ttlSeconds` 为 1～600；按服务/租户命名空间、定义摘要和类型化键隔离；未命中及过期键合并批取。最多 5,000 项，单项值超过 4,096 字节不缓存；命中与未命中结果都可缓存 |
| `VERSIONED_SNAPSHOT` | 固定 snapshotId、snapshotHash 和 definitionHash，缺失或摘要不一致拒绝执行；运行业务字典 SQL 为零，不静默回退为实时读取 |

完整快照通过已授权定义构建，最多 5,000 行、累计序列化条目 1,000,000 字节、查询超时 10 秒；只有完整成功才保存版本。Service 启用/停用时清理缓存与快照索引。当前没有自动刷新快照、跨节点缓存一致性或自动版本淘汰的验收声明。

租户业务库的 `data_access_mapping_plan_t` 保存不可变计划，`data_access_mapping_resource_t` 保存不可变 TEMPLATE/DICTIONARY_SNAPSHOT，`data_access_mapping_publication_t` 保存可变的当前发布引用。publication 不代表完整发布事件历史。`KEEP_ALL` 是当前保留策略：不自动删除计划、模板、快照历史，也不在节点升级中清理旧 disabled 处理器。公开计划只带快照引用与摘要，不能把完整快照、凭据或样本放入 Git、普通日志或模板。

后端能力目录当前有 56 项，53 项可直接作为值函数；uuid、codeTableLookup、expression 分别映射到 SYSTEM_VALUE、LOOKUP、EXPRESSION。参数按 FunctionArgumentSchema 返回的类型/位置/默认值/枚举生成表单，实际调用还需统一绑定器校验，不能只凭函数名称判断支持范围。

## Schema 和预览边界

`/nifi/api/field-mapping/schema-contract` 按登记引用锁定来源/目标物理结构，编译与发布复核，NiFi 运行检查 Reader 输入契约。结构漂移不能触发隐式建表或重建来源。Schema 先批量授权，再按数据源分组，每个数据源一次连接、一次批量物理读取：MySQL/MariaDB 用参数化 information_schema 查询覆盖所请求 catalog/table，并保留完整 COLUMN_TYPE；其他 JDBC 在当前 catalog 的一个明确 schema 调用一次 getColumns 扫描，跨 schema 通用回退拒绝。每批最多 100 引用、8 数据源、单表 2,000 列、总响应/扫描 20,000 列。12 条选定 Schema 批读测试已通过；其他实际驱动仍需真实环境验证。

`/nifi/api/field-mapping/sample` 只从已登记表取 1～100 行、最多 2 MiB，先在服务端对原始样本执行，再对回传 sampleRows/resultRows 的全部非 NULL 值遮盖；结果标记 REGISTERED_SOURCE_MASKED。手工样本预览仍保护敏感字段及其派生 alias。来源全遮盖响应不用于肉眼断言真实值正确性，精度和转换结果由受控样例单独验证。

## 发布与回退

先检查目标 NiFi 的版本、全部节点的 bundle 能力、磁盘余量、驱动位置、Reader/Writer 的实际属性名，再安装经过检查的新版本 NAR。演示环境允许使用已配置的 nar_extensions 自动加载目录；不能用同一版本文件覆盖已加载类。

画布旧 1.0 配置继续保留；在编译面板明确升级为 schemaVersion=2.0，或由新接入任务生成 JAVA_RECORD 标记。草稿升级、版本保存和实际运行升级是不同动作。模板/历史计划导入只改草稿，不代表已回退运行；所有引用在保存、编译和发布时重新授权。

整图重建仍受非空队列和来源 state 保护。`/nifi/api/field-mapping/upgrade-runtime` 专门替换选定字段节点，保持来源/目标原生组件 ID 及来源 state，不复制、不清空来源状态，不顺带发布画布其他修改。当前真实旧 QueryRecord → 新 Processor 替换、再次升级至 1.1.1 均已验证，来源/目标 ID 及水位逐项不变，原 STOPPED 来源未被误启动。选定图验证不能外推所有历史拓扑和故障组合。

调用必须带 pipelineId、nodeId、expectedUpdatedAt、expectedPlanHash；服务端按已保存草稿重新编译，并检查流程归属、边界连接、Reader/Writer、Bundle 与有限图范围。先确认队列明确为零，停组等处理器停止后再次确认；非空或未知队列、并发修改、未保存计划、无法核验的旧链都拒绝升级。不得删除业务队列或清空 state 来通过检查。

切换只替换字段边界和当前计划引用，旧 mapping 处理器保留为 DISABLED。`start=true` 按升级前每个 Processor 的实际运行状态恢复，不是整组全启动；原来停止/禁用的组件不能被误启动，旧字段链原来运行时才相应启动新字段处理器。`start=false` 保持停止。提交前失败尝试恢复旧边界，提交后恢复运行失败保持停止并报告；恢复不完整必须报告需人工处理，不删除来源/目标来掩盖失败。旧 1.0 单值/拆分 SQL 字典规则转换已修复并测试，选定升级失败边界 10 条通过。

普通 `/start` 保留 DSL hash 保护，有 pending 草稿时仍拒绝。UI 的“启动已发布运行版本”显式调用 `POST /nifi/api/field-mapping/start-published`，请求带 pipelineId/expectedUpdatedAt。服务端一次批量读取并重新授权当前 published 计划，核对原生来源/目标/辅助 ID、计划引用及边界；发现未登记的额外 success 候选连接阻止启动。普通未禁用组件按原生范围启动，一次性来源只 RUN_ONCE；返回 draftPublished=false，不把尚未发布的草稿标记为整图部署成功。此显式启动动作与升级后按原状态恢复的语义不同。

restore-draft、upgrade-runtime、start-published 共用 SerializedNifiMutation 保护。PipelineRepository.update 的 SQL UPDATE 同时限制 ID、tenant_id、is_del=0、旧 updated_at，影响行数不是 1 即冲突；版本时间单调递增。计划归档 20/100 节点的当前测试均为 4 条本服务 SQL，另加固定授权解析，不能沿用早期未包含新角色核验的“2 条 SQL”口径。

插件升级、Java 后端更新、海通静态页面更新及 baseline Magic 发布是不同步骤。共享资源必须逐文件比较、发布、刷新和回读；不要把安装 NAR 当成整个系统已升级。使用 tools/package-backend-overlay.py 可以只替换选定类，校验旧内容并验证未选中类与资源未变化。

数据库兼容验证分两层：纯转换不依赖数据库方言；字典 JDBC 与目标数据库操作仍需验证对应驱动、标识符大小写、Schema、权限、参数绑定和数据库原生语义。不能用 H2 或 MySQL 的结果宣称所有实际数据库均已验收。

## 早期1.1.1已确认与待完成验证

- 演示四行业库 143 张统一业务表已完成 check/export；所选后端 95 个运行类、海通前端及 NiFi NAR 1.1.1 已部署，服务 UP。本地 8088 已更新同 95 类并健康 UP，函数/内核版本一致。
- 20/100 行正式预览的精确 38 位小数、敏感 alias，以及实际来源取样输入/输出双端全遮盖已通过；同模板两版本保存与导入已通过。
- 实际 100 行同键集合连续执行两次，LIVE_BATCH 字典 SQL 为 `[1,1]`，TTL_BATCH_CACHE 为 `[1,0]`，VERSIONED_SNAPSHOT 为 `[0,0]`；不包含快照构建、授权/元数据和目标写入。
- 当前选定自动验收汇总为 22 套、120 项，失败/错误/跳过均为 0（包含 20 套后端选定测试及内核性能、原生 Processor 契约）。其中升级失败边界 10 条、Schema 批读 12 条、角色及 batchPlans 13 条、publishedLifecycle 3 条；这些子集不另行累加。
- 真实原位升级及平台 `/stop → /field-mapping/start-published → /stop` 已通过，来源/目标 ID、水位和原停止状态保留，pending 草稿未误标为整图部署成功。真实 1.1.1 MySQL INSERT/UPDATE/UPSERT/DELETE 已通过，小数与日期时间无偏移。
- 纯内核 10,000 行 × 100 字段的本机一次测量为 0.246 秒、约 40,655 行/秒，仅限内核。真实 NiFi 同规模为 1 输入 → 20 窗口 → 1 输出，21,920,001 字节全量核对通过；连同数据准备、API 等待及约 22 MB 下载的墙钟 164.937 秒不能作为 ETL 吞吐或 SLA，未经核定的 NiFi task 统计未纳入结论。
- 用户确认暂时没有其他数据库测试环境；当前为单 NiFi 节点，不宣称多节点集群、全部真实驱动/复杂类型、完整历史迁移矩阵、端到端 exactly-once 或 SLA 压测已通过。


## 1.1.4 交互与UUID变更（历史）

字段映射继续复用原组件。列表去掉映射类型、校验和全部通过标签，按当前页顺序显示连续序号；恢复原三栏连线可视化、候选勾选自动映射、三个快捷模板及自适应高级编辑器。表格“转换 / 配置”和顶部“映射规则”使用同一个配置弹框。

ODS_UUID默认使用32位UUID，保留完整有效位并绑定流程、节点、输入与记录身份；分窗口继续累计下标，拆分子输入使用不同身份，同输入重试保持相同ID。已知旧ODS生成模板编译为UUID32，人工常量及其它自定义规则保留。当前目标VARCHAR(32)无需修改结构。

登记“时间”对应DATETIME并保留时分秒，“日期”对应DATE。已选择时间目标时，日期解析默认使用日期时间；仅日期解析隐式补零点会被编译拒绝。DATE转换支持JDBC时间对象和JSON日期时间文本。来源逻辑类型、完整Schema、时间Writer和类型化Reader配套使用；Hive仍显示PUBLIC-WRITER-JSON-TIME_FORMAT。中间TIMESTAMP使用时间点语义，按计划时区还原，不能仅比较UTC中间字符串和本地数据库时间。

构建使用：

```powershell
./tools/build-mapping-extension.ps1 -MavenCommand 'mvn' -PythonCommand 'python'
```

工具执行clean package并显式检查退出码及NAR重复类、服务注册。构建产物路径和SHA-256由工具返回；校验失败不得安装，也不能覆盖已加载的同版本文件。发布参数和最新规则见 [设计第24章](../../docs/海通字段映射统一执行方案.md)。

在已部署环境重新打开字段映射，编辑、预览并保存；首次流程正常发布即可。已有运行流程通过“更多”的字段升级入口采用新内核，并核对旧来源的时间编解码配置，保留来源ID和水位。尚未更新的运行图仍引用其原NAR/计划，不能把安装新包当作所有任务都已自动切换。

当前1.1.4已安装至演示NiFi 2.11.0并回读验证。本地与演示后端版本一致，海通静态页面和所选接入生成脚本已发布并刷新。最终包SHA-256为 `35281d08a15687af8fa92bce5bf949bb93e1abe9f2f73eab931229b6bdcd3a4b`，重复类0、服务注册通过。独立原生JDBC往返写入保留 `2026-10-14 14:27:49` 和32位ODS_UUID；两份各2,049条输入跨5个窗口得到4,098个不同ID，转换与ID生成不发SQL。正式平台停止状态发布、20/100项请求及权限拒绝也已通过，验收流程和样例已清理。前端92项验证通过；后端各测试集合及实际环境边界见 [设计验收记录](../../docs/海通字段映射统一执行方案.md#245-本轮实际验收与交付状态)。这些结果不是全部实际数据库、集群吞吐或端到端SLA认证。


## 1.1.6 宽表、ODS时间及数据库日期语义

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

不传NiFi版本时采用POM的当前默认2.11.0及默认输出目录；指定版本时使用独立输出目录，避免清理另一版本产物。SHA-256和父NAR版本随包核验，后端须与扩展同为1.1.6。生产尚未直接连接，本轮其它数据库为类型/发布契约和Oracle兼容SQL验证，未宣称所有真实驱动、时区、写入模式或生产数据修复已完成。完整设计见 [第25章](../../docs/海通字段映射统一执行方案.md#25-宽表字典容量与数据库时间语义v26--116)。


## 1.1.10 同一字段混合日期格式

复用现有字段映射的日期处理表单，输入默认AUTO逐值识别；同一列可以同时出现横线、斜线、点号、紧凑、中文、英文、ISO偏移等文本，不按首行固定格式。年月、月日、纯时间转完整日期需要显式defaultDate，数字时间戳显式选单位，非法值走现有失败策略。原有指定格式规则保留。

使用：打开“转换和规则” → 日期处理 → 自动识别 → 日期时间（保留时分秒），先预览混合样本，再保存并重新发布/受控升级字段节点。需要仅日期才选择DATE。前端预览保留小数秒，不改全局JSON时间格式。

此节为1.1.10历史记录；当前正式版本为1.1.13，升级见第28章。历史包：NiFi 2.11默认包 `logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.10.nar`，2.9匹配包 `logs/field-mapping-extension/nifi-2.9.0/haitong-mapping-nar/haitong-mapping-nar-1.1.10.nar`。不要采用构建中的中间包，不覆盖已加载的同版本NAR，不把两种NiFi构建装入同一实例。

保留NiFi/JDBC已有的时间点传输约定。流程时区与NiFi JVM时区不同，中间JSON显示可能不同于业务本地时间；须核对实际目标表、时间点和串联结果，不能只修正中间文本。不同目标驱动及其连接时区仍需真实验收。本轮支持目录、补全策略、精度和端到端验证见 [详细设计第26章](../../docs/海通字段映射统一执行方案.md#26-多格式日期逐值识别v27--1110)。


## 1.1.13 生产驱动与历史日期兼容

完整原因、日期与夏令时边界、画布编辑权限、20/100批量证据以及部署使用步骤见[详细设计第28章](../../docs/海通字段映射统一执行方案.md)。当前共享内核、函数库、运行NAR均为1.1.13；2.11与2.9使用各自SDK构建的同名NAR，不混装。已有原生节点须受控升级Bundle和执行计划，不会随NAR上传自动切换。

输出精度以数据源登记保存的统一格式为准：DATE为日期，DATETIME为保留时分秒的时间，不按字段名或业务含义猜测。服务端在既有批量读取中核对登记快照，编译时设置输出及日期解析精度，运行过程不逐行读取登记库。修改登记后重新发布或受控升级字段节点；实际目标类型无法承载时在发布前拒绝，不自动执行DDL。

## 1.1.13 类型别名、异常回环与 Hive 画布

`CHARACTER VARYING`（含长度、大小写和多空格）、CHAR/NCHAR VARYING、NATIONAL CHARACTER VARYING、NCLOB、LONGNVARCHAR、BPCHAR 等统一为 STRING。显式 CAST 与函数 cast 在编译时归一化，未知 CAST 类型提前拒绝；不为字段转换生成 SQL。

Hive 作为目标时，平台加载、合成展示、保存及 NiFi 发布统一旁路该分支紧邻的字段映射链。共享 JDBC 分支保留必要映射；Hive 作为来源不触发旁路。不完整草稿可继续保存编辑，发布仍严格检查断链和循环。全局 JSON 服务不存在时仍明确拒绝发布，不创建同名任务级替代服务。

NiFi 2.9 的匹配构建：`mvn -Dnifi.version=2.9.0 -Dmapping.output.root=D:/Sourcecode/data-elements/logs/field-mapping-extension/nifi-2.9.0 clean package`。产物位于该目录下的 `haitong-mapping-nar/haitong-mapping-nar-1.1.13.nar`。2.9 完成匹配 SDK 构建和组件测试，真实原生运行验证在演示2.11完成；使用时须按现场 NiFi SDK选择对应包。不能同时安装同一扩展版本的两个 SDK 构建。

已安装新 NAR 不会自动改变既有1.1.10等处理器的 Bundle、执行计划或连接。新流程正常发布会生成合并自环；已有字段节点使用保留来源ID升级入口，先停采集并让旧队列自然处理/按原计划保留，核对零队列后升级。字段升级不会改变 Hive 既有图的拓扑，Hive 旧任务须在来源ID/水位保护允许的受控发布流程中更新，不能清空业务队列或重置来源水位来绕过保护。
