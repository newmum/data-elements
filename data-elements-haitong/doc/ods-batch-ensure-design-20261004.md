# 接入任务批量确保：合规改造边界

状态：**设计与审计记录，尚未实现或发布批量确保接口**。现有 `POST /ods/batchCreateJob` 可以创建持久作业并按页预览来源表；`POST /ods/dataAggTaskEnsure` 仍是单来源表写接口。页面不能以循环、并发池或服务端代理逐项调用它来宣称“批量创建”。单项由用户主动点击执行仍可保留。

本记录按仓库的[前后端批量查询与分页规范](../../docs/前后端批量查询与分页规范.md)制定。审计依据为 `data/working/panshi-integration-20261002/existing/ods_data_agg_task_ensure_01.ms` 的 2026-10-02 导出快照，并只读确认本地活动 `baseline.api_file_t` 仍有 `ods_data_agg_task_ensure_01`（路径 `/magic-api/api/03.数据接入/13.按来源表补齐接入任务.ms`，本地记录更新时间 2026-10-02 23:19:49）。正式实施前仍须再次导出、备份并逐项比对活动资源，不能用此快照覆盖共享 Magic 资源。

## 为什么不能包装现有单表接口

单表脚本同时处理已有任务复用、自动目录及历史回退、多目标表、字典关联、强制对标、FTP/API 来源、缺失翻译列的物理建表修补、不同目标库及 Hive 草稿生成。它在以下路径读取数据库，且若直接在批量循环里调用，查询量随来源表、目标表和字典关联字段数增长：

| 调用链 | 当前读取点 | 批量化要求 |
| --- | --- | --- |
| `governanceConfigOf`、`findCatalogBySource` | 每个表、每条目录关联读取 `da_prop_t`/`da_catalog_t` | 按本批来源、目标和目录 ID 一次预取，内存归组 |
| `registeredDictionaryRelations`、`dictionaryLookupSource/Fallback` | 每个关联字段读取字典表、字段、治理配置和数据源 | 先汇总所有字典 ID 和受控回退名称，再有界批量读取 |
| `expectedMappingCount`、已有任务检查 | 每个目标表 `count`，每个任务查字段映射数和流程 | 目标字段按 `table_id IN (...)` 预取并计数；已有任务、映射聚合和流程各批量查询 |
| 目标节点生成 | 每个目标表读取数据源、连接配置和字段 | 去重的目标库与字段批量预取；连接配置提供批量读取契约 |
| `datasourceConnectionConfig('read', id)`、`apiPullConfig.resolvePipelineSource` | 当前活动 `/common/datasourceConnectionConfig` 每次 `read` 都按数据源 ID 对 `db_datasource_t` 执行 `selectOne`；Java 的 `resolvePipelineSource` 每次执行模式检查、来源库校验与 `pool_cfg` 查询 | 增加 `readMany(ids)` 和批量 API 来源解析，或改成接收已预取数据的纯函数，并用 SQL 计数证明 |
| `targetTableSchema.ensureTranslationColumns` | Java 每次调用都连接外部目标 JDBC、读取目标表列元数据，缺失时执行 DDL | 批量确保仅接受已完整物化的目标；缺列进入明确的先行修补流程，不在批次循环内探测/DDL |

`dataAggTaskEnsure` 默认不重建已有用户流程，只有显式 `repair/forceRebuild` 才会替换。新批量接口也必须禁止隐式重建，并保持已有任务及画布原样；不能用简化 DSL 替代现有字段增强和目标配置。

连接配置的直接证据是活动控制库函数 `/magic-api/function/01.通用函数/08.读取或保存数据源连接配置.ms`（id `datapd_datasource_connection_config_01`）的 `read/json` 分支；它每次仅按一个 `tid` 查 `db_datasource_t`。API 来源的直接证据在 `data-elements/src/main/java/com/linewell/dataelement/dataservice/pull/ApiPullConfigurationMagicModule.java` 的 `resolvePipelineSource`（约 188–229 行）：每次按一个数据源和表名执行 `ensureSchema`（其中有 5 条 `CREATE IF NOT EXISTS`）、来源库校验查询和 `pool_cfg` 查询。物理列探测在 `data-elements/src/main/java/com/linewell/dataelement/integration/nifi/canvas/schema/TargetTableSchemaMagicModule.java` 的 `ensureTranslationColumns`（约 28–75 行）：一次仅处理一个物理目标表，连接 JDBC 并读取列元数据。三个依赖均没有现成的批量配置读取或纯任务生成入口；即使被新批量路由包裹，也仍是逐项读取。

## 建议的实现拆分

1. **预取层**：新增服务端内部批量读取器，一次处理最多 20 个作业项。输入仅为作业 ID 和受限批大小；服务端从 `tenantRuntime.id()` 取得租户并读取作业及待处理项，不信任客户端的租户、来源库、目标库或目标表快照。用当前会话的 `dataScope.visibleDataSourcesFor` 重验来源和目标数据源，再把每个来源表及唯一物化目标表与提交快照逐 ID 对齐；缺失、跨租户、越权、重复、关联变化均明确失败。读取器分批查来源/目标表、全部字段、目录及属性、字典注册表/字段、去重数据源、已有接入任务、映射数和流程。所有 `IN` 和结果集有显式上限，不能全量加载。
2. **配置解析层**：为 `datasourceConnectionConfig` 增加有界 `readMany(ids)`，或在批量预取 `db_datasource_t` 后以纯函数进行与单项相同的解密、归一化；为 Java `ApiPullConfigurationMagicModule` 增加 `resolvePipelineSources`，一次按当前租户批量读取去重数据源，内存解析所需表的 API 定义，并让旧单项方法复用同一纯解析器。`TargetTableSchemaMagicModule` 若要支持批量修补，需按目标连接/模式分组、按组受限读取列元数据并确保每个目标表授权；本期更安全的是批次预检发现缺列即转先行修补，不在确保环节执行 DDL。没有可审计的批量入口之前，不能让生成器调用现有单 ID helper。FTP 域绑定在每批只查一次。
3. **纯生成层**：从已备份的当前单表脚本抽出不执行数据库或远端读取的 `generateTaskDraft(context)`，复用原有的目录、字典、强制对标、字段映射、源节点、Hive/JDBC 目标节点语义。单表接口先作为适配器调用该生成器处理一个 context；对典型任务比较新旧 DSL 的规范化结构、字段映射及目标配置，再开放批量路径。历史单项调用与 `forceRebuild` 语义不变。
4. **批量写入层**：建议新增 `POST /ods/dataAggTaskEnsureBatch`，请求 `{jobId, limit}`，`limit` 为 1–20。服务端持锁领取该作业最多 20 项，经上述预取和纯生成后，在租户业务库事务中写入 `nifi_pipeline_t`、`data_access_agg_task_t`、`data_access_field_mapping` 与现有 `ods_batch_create_item_t` 结果；同批字段映射用批量插入。失败和失联采用现有 item 的租约/重试字段记录，不静默标成功。已经成功的来源表不重建；重复请求返回已保存的 taskId。
5. **页面切换**：待端点通过下面的验收后，页面按最多 20 项一次调用 `dataAggTaskEnsureBatch`，20/100 项分别至多 1/5 个执行请求；保留现有作业、单项结果和用户主动单项处理入口。旧单表接口继续服务原数据中台和手工创建，直到新旧结果回归验证完成。

现有 `ods_batch_create_job_t` 和 `ods_batch_create_item_t` 已含租户、幂等键、项顺序、状态、租约、尝试次数、taskId 与错误信息；执行状态本身目前不需要新增业务表。**本地只读核对发现**，公安租户快照库 `baseline_ga_old.data_access_agg_task_t` 以及另三个本地 MySQL 副本只有 `tid` 唯一键，没有活跃 `(tenant_id,source_table_id)` 唯一约束；当前公安库 22 条未删除任务中，13 条有不同的来源表 ID、9 条来源表 ID 为空，目前虽无重复，却不能防止并发双建。`source_table_id`/`is_del` 可空，直接加 `(tenant_id,source_table_id,is_del)` 唯一键也无法正确约束空值和多条软删除历史。该检查没有重新解析实时 Nacos 租户绑定，部署前须重新确认当前业务库及生产方言。

批量路径与现有单表路径必须使用**同一个**并发防重机制：一种方案是在同一事务中按来源表 ID 升序批量锁住 `db_table_t` 注册行，再批量查询已有任务，并让单表路径也锁同一来源行；另一种方案是增加只约束活跃非空来源的数据库唯一机制，并分别提供 MySQL/达梦迁移、历史数据核查及回滚。只给新批量端点加锁而旧单表端点不加锁，仍会跨路径竞态；不可仅靠前端禁用按钮或进程内锁。

## 查询预算与验收门槛

每批最多 20 个来源、已物化目标与字典/字段总量须有独立上限；超限报出可操作的错误，不能退回逐项查询。预取的读 SQL 应为按实体类型固定的若干批量查询，及少量按显式批次/关联数量分组的查询；写 SQL 可以随创建项增长。`dataScope`、连接配置和 API 拉取 helper 的间接 SQL 也计入预算。以目标环境 SQL 计数器或日志验证 20 项与 100 项：页面执行请求分别不超过 1/5 个，读 SQL 随批次数增加而不接近 20/100；数据库写入数另列。准备普通、字典、强制对标、FTP、API、Huawei Hive、多目标、历史目录和已有任务样本，逐项比较单表与批量的 DSL/字段映射/目标配置。

下表是**待实现的验收条件，不是现有接口的测试通过记录**。`Q` 表示在真实运行环境为一个 20 项批次测得的固定读查询预算，包含所有 helper 的内部 SQL；在开发期间必须列出每条查询及其上限，不能用任意大的 `Q` 掩盖 N+1。现有自动 `claim → ensure → complete` 的 20/100 项约需 60/300 个 HTTP 请求，读 SQL 尚无计数证据。

| 样本 | 新执行 API 请求 | 读 SQL 验收 | 关键结果 |
| --- | ---: | --- | --- |
| 20 项普通、字典及强制对标混合 | 1 | 不超过固定 `Q`，且逐项/逐字段无额外 SELECT | 20 项全部返回对应 taskId；新旧 DSL 与映射逐项一致 |
| 100 项同类混合 | 最多 5 | 不超过 `5 × Q`；每批 SQL 数与该批 1 项、20 项基本相同 | 无遗漏、重复或隐式流程重建；仅写 SQL 随项数增长 |
| 20/100 项均已存在 | 1/最多 5 | 同一 `Q`/`5 × Q` 上界 | 返回原 taskId、流程和映射不变，新增任务数为 0 |
| 混入一个无权、跨租户或失效表 ID | 最多 1 个受限批次请求 | 仅本批预取与范围校验 | 整个受影响批次拒绝；不返回无权详情，不写任务 |
| 重复 ID、21 项直传、超过字段/字典上限 | 1 | 在重读或写入前拒绝，或仅做受限的预检读取 | 明确 4xx 与可操作错误，不降级逐项请求 |
| 同一作业并发双启动及租约过期重试 | 每批各 1 | 并发不造成逐项补查 | 同一租户/来源至多一条活跃任务，成功项绝不重建 |

还要分别记录浏览器请求、Magic/Java 直接 SQL、导入函数间接 SQL、外部目标库元数据请求和写 SQL。若运行时没有这些计数能力，验收不通过，不能仅以静态扫描或模拟数据宣称完成。

权限回归必须覆盖当前租户、另一个租户、同租户但角色数据范围不同、混入一个无权或不存在 ID、目标库授权被撤销、目标关联在作业创建后变化、重复 ID、超过批次上限、重试和并发双启动。每个输入 ID 必须有当前会话权限及来源/目标关系证据；不能把无权项过滤掉后继续创建。若未来增加物理表/列，遵守中文表注释和每列中文注释约定。

在上述端点和真实 20/100 项 SQL、请求数证据齐备前，批量页只能是**持久匹配作业与用户主动逐项创建**，不能启用自动逐项执行或宣称批量确保已完成。
