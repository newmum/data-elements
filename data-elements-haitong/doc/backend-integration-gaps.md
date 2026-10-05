# 海通集成中心后端对接与剩余契约

本清单按当前已登录租户的现有数据中台接口与库表核对。前端入口是 `http://localhost:3002/#/integration/overview`；生产入口沿用平台网关 `/haitong/`。本次没有修改生产表结构，也没有把浏览器本地样例写入租户库。

源数据管理平台的画布入口已统一指向 `/haitong/`。本地审计检查了前端活动源码、配置和构建产物，以及控制库 `ui_component_t` 全部 232 行的源码与编译脚本，均无仍在使用的 `/nifi-canvas/`。现用 `global-nav`、`schedule-setting` 的源码和编译脚本都使用 `/haitong/`；接入监控、数据汇聚及 Hive 汇聚共用该 `schedule-setting`。旧路径只留在历史版本表与迁移前备份，未改写历史。这个结论不等于生产网关路由和远端部署已完成，发布时仍须验证 `/haitong/` 的静态资源、会话桥和直接画布链接。

## 已复用

| 页面能力 | 已接入的接口或实体 |
| --- | --- |
| 登录、租户、NiFi 画布 | 平台 `capability-session.html` 会话桥、`/sym/user/me`、`/sym/tenant/current`、原 NiFi 流程接口；进入画布不再手填 Token |
| 集成任务与创建 | `/ods/task/page`、`/ods/dataAggPage`、`/ods/dataAggTaskEnsure`，对应 `data_access_agg_task_t`、登记数据源和表 |
| 接入监控 | NiFi `/nifi/api/pipelines/runtime-list`、`/ods/task/monitorSnapPage` 和已有 `data_access_task_monitor_snap_t` |
| NiFi 节点 | `/ods/nifi-node/page`、可用节点选项及原节点维护/健康接口，对应 `nifi_node_t` |
| 盘点策略、执行和差异 | `/ods/reconciliation/policy/*`、`/ods/reconciliation/run/*`、`/ods/reconciliation/summary`，对应现有对账策略、执行、差异和字段规则表 |
| 批量匹配与持久队列 | 新增租户内 `ods_batch_create_job_t`、`ods_batch_create_item_t` 和 Magic `POST /ods/batchCreateJob`，按当前角色范围分页预览、批量复核并保存作业；用户主动点击一次只调用一次既有 `/ods/dataAggTaskEnsure`，结果和失败可跨页面查看与续跑。**自动批量创建尚未实现。** |
| 多表任务编组 | 新增租户内 `data_access_multi_group_t`、`data_access_multi_group_item_t` 和 `/ods/task/multi/*`；只把已有的独立单表任务编组、排序和展示逐表状态，不创建一个合并的 NiFi 流程。 |
| 分发任务只读总览 | 新增 Magic `POST /ods/distribution/task/page`，按当前登录租户分页读取既有 `data_distribution_task_t`；当前租户确实为 0 条，不虚构执行量 |
| 小时监控趋势 | 新增 Magic `POST /ods/task/monitor/trend`，按当前登录租户与时间窗口读取有界快照，在脚本内逐任务取小时峰值再求和；无 MySQL 时间分组函数依赖，明确不是目标库确认写入 |
| 脱敏差异完整导出 | 新增 Magic `POST /ods/reconciliation/run/diff/export`，只允许当前租户已完成实例；最多 5000 条，超限显式拒绝，结果使用既有仓储脱敏值 |
| 外部 NiFi 流程登记 | 新增 Magic `POST /ods/task/registry/validate|save|page` 与租户内 `nifi_pipeline_external_ref_t`；仅将外部 ID 绑定已保存的 NiFi Canvas DSL v1 流程，预检、防重复和真实登记历史已接入。原海通 Flow JSON 保留独立本地演示模式 |
| 外部登记选择已有流程 | 新增 Magic `POST /ods/task/registry/pipeline-options`；按当前登录租户分页搜索已保存的 Canvas DSL v1 流程，选择框每次只加载当前页，不再调用无界 `/nifi/api/pipelines` 全量列表 |
| NiFi 节点资源 | Java `POST /sym/node-config/service/diagnostics` 读取 NiFi 系统诊断并按当前租户节点范围返回安全汇总；集群页展示堆内存、线程等真实指标，不能用它推断目标表写入成功。 |

`data_distribution_task_t` 和 `data_access_task_monitor_snap_t` 在当前租户库中已经存在。不能把“缺少分发监控接口”或“缺少趋势汇总接口”理解为缺这两张表。

## 尚需补充的后端契约

| 能力 | 现有基础与确切缺口 | 建议的补充 |
| --- | --- | --- |
| 多表统一执行 | 已有两张租户内编组表、五个 Magic 接口，可对已有单表任务编组并查看各自状态。编组成员仍各有独立 NiFi 流程，没有组级统一启动、调度或一次事务的执行语义。 | 若业务确需统一执行，先定义组级调度、逐表运行记录、失败恢复和权限模型，再补真实编排服务；不能把管理编组当成单个多表 NiFi 流程。 |
| 分发监控 | `data_distribution_task_t` 已有任务状态和进度；租户级只读分页 `/ods/distribution/task/page` 和任务详情 `/ods/distribution/task/detail` 已补，当前租户没有分发记录。现有 `/v1/dataassets/delivery/detail` 只按申请单返回本人明细，尚无完整 NiFi 执行关联或独立状态历史。 | 若分发由 NiFi 执行，为分发任务补充 `pipeline_id` 或独立的任务—流程关联结构，再增加真实运行记录/状态历史；不能从任务名称猜测 NiFi 关联。 |
| 跨网传输 | 节点 `network_code` 与接入任务 `nifiNetworkCode` 只表达单侧网络，现有任务没有目标网络、发送批次、签收及目标写入确认。用户已确认目前**尚无**实际发送与回执服务；统一身份的 `idaas_receiver_receipt_t` 用途不同，不能复用为数据传输回执。 | 先建设或指定实际传输与回执服务，再定义租户内任务/批次/回执持久化契约，至少保存来源与目标网络、批次、发送状态、接收签收、目标写入确认及关联流程；之后用 Magic API 提供任务分页、批次详情和回执查询。 |
| 即时账单核销 | 现有对账实例和脱敏差异可查询，但没有“预期交付—确认接收”的双边账单、人工核销及复核证据接口。 | 先确定账单与证据的持久化模型及不可覆盖的核销记录，再提供 Magic API 的账单分页/详情/核销/复核接口。当前页不允许伪核销。 |
| 真正的自动批量建任务 | 持久作业、逐项结果、停止和重试已落地；页面不会自动循环调用单表接口。现有 `/ods/dataAggTaskEnsure` 及其数据源配置、API 拉取和目标表结构帮助入口仍按单项读库或访问外部库，包装调用会造成 20/100 项请求及读查询线性增长。现有单表任务表也没有按活跃来源防并发重复创建的唯一约束。 | 按[批量确保改造设计](ods-batch-ensure-design-20261004.md)实现有界预取、共用纯生成器、新旧入口共享的并发防重和服务端批次执行，并用 20/100 项真实 SQL 计数与新旧 DSL 等价验收后才能开启自动执行。 |
| 原海通 Flow JSON 转换 | 原海通本地 Flow JSON 只有 `kind/read/transform/write/send/receive` 与字符串配置；NiFi 画布 DSL 需要 `manifestKey/category` 和组件专属配置。真实登记现已支持已有 NiFi 流程或原 NiFi Canvas DSL v1 JSON，绑定到租户内 `nifi_pipeline_t`；本地 Flow 仍只在浏览器演示，未冒充真实流程。 | 若必须直接导入旧 Flow JSON，先逐组件定义数据源凭据引用、字段映射、目标写入、跨网接收等转换规则，再提供服务端验证与迁移；不能做仅改字段名的假转换。 |
| 总览确认写入 | 历史快照分页、小时峰值趋势和 NiFi 节点系统诊断已接入；快照流出不等于目标库事务提交成功，因此“今日确认写入”仍没有可靠统计。 | 先取得目标写入成功证据并确定去重与时间口径，再提供确认写入汇总。 |
| 大规模差异附件 | 脱敏完整导出 `/ods/reconciliation/run/diff/export` 已提供 5000 条以内的有界全量 JSON 行，超限不截断；当前租户尚无对账执行数据，未完成有数据实例的正向验证。 | 对超过 5000 条的实例增加后台分片/流式文件导出；前端应明确提示上限与接口错误。 |

分发和跨网页面明确显示接口缺口；多表页面明确标为已有单表任务的管理编组。接入任务数据仅作为参考时也会标明，不能把参考统计认作真实分发或跨网结果。新增业务接口优先写入现有 Magic API；NiFi 资源指标采集由服务层实现。

### 建议的落库与 Magic API 清单

下表区分**本地开发租户已执行**与**尚待实施**；所有正式环境仍须按真实租户—业务库映射单独备份、迁移和验证。

| 模块 | 建议表/字段 | 建议接口 |
| --- | --- | --- |
| 多表管理编组 | **本地三个 MySQL 租户库已新增** `data_access_multi_group_t`、`data_access_multi_group_item_t`；仅保存现有独立任务关联与顺序，不保存伪造的组级执行结果。远端广电达梦迁移未执行。 | `/ods/task/multi/candidates|page|save|delete|relink`；真实发布验证状态见[编组资源说明](../../data-elements/db/migrations/resources/haitong-multi-table-20261004/README.md) |
| 分发 | 继续使用 `data_distribution_task_t`；一任务一流程时加 `pipeline_id`，一任务多流程时另建关联表，不能凭名称关联。 | `/ods/distribution/task/page` 与 `/ods/distribution/task/detail` 已完成；仍需有真实流程关联后的 `/runtime` |
| 跨网 | 新增租户内批次/回执结构，例如 `data_cross_network_transfer_t`，含 `access_task_id`、`pipeline_id`、`source_network_code`、`target_network_code`、`batch_no`、发送/签收/目标写入状态及时间。 | `/ods/cross-network/task/page`、`/ods/cross-network/batch/detail`、`/ods/cross-network/receipt/page` |
| 即时账单与核销 | 如需双边账单和人工核销，新增账单/核销证据结构，例如 `data_reconcile_delivery_bill_t` 与 `data_reconcile_writeoff_t`，记录 `run_id`、预期交付、确认接收、复核证据、操作人与理由。 | `/ods/reconciliation/bill/page`、`/detail`、`/writeoff`、`/review` |
| 批量匹配作业 | **本地三个 MySQL 租户库已新增** `ods_batch_create_job_t`、`ods_batch_create_item_t`，保存租户、来源/目标、幂等键、租约、逐项状态与错误。远端广电达梦迁移未执行。 | `/ods/batchCreateJob` 的 `sourceOptions/targetOptions/preview/create/list/detail/claim/complete/stop/retry`；自动批次执行 `/ods/dataAggTaskEnsureBatch` **尚未实现** |
| 外部流程登记 | **已新增**租户表 `nifi_pipeline_external_ref_t`，外部 ID 与 pipeline 均按租户唯一，记录登记时 DSL 摘要；本地 GA、海渔、澄天 MySQL 租户库已执行 `V20261004_02__external_nifi_flow_registry.tenant.mysql.sql`。NiFi DSL 仍由原 `/nifi/api/pipelines` 保存。 | `/ods/task/registry/validate`、`/save`、`/page` 已完成并在 GA 租户验收；其他租户尚待业务会话验证 |
| 汇总/导出 | 无需新监控快照表；小时快照峰值与 5000 条以内脱敏差异导出已完成，确认写入仍需落地证据。 | `/ods/task/monitor/trend` 与 `/ods/reconciliation/run/diff/export` 已完成；仍需 `/ods/task/confirmed-write/today` 和超限流式导出 |

### 本轮新增 Magic API 的真实验证

- `POST /ods/distribution/task/page`：请求 `{pageNum,pageSize,keyword?,status?}`，返回 `{pageNum,pageSize,total,list}`。当前开发租户实测 `code=0`、`total=0`；这与业务表当前无有效记录一致。
- `POST /ods/distribution/task/detail`：请求 `{tid}`，按当前租户读取任务元数据与持久化状态；未知/非本租户 ID 返回 404。当前开发租户无分发记录，已验证不存在分支。
- `POST /ods/task/monitor/trend`：请求 `{beginTime,endTime,interval:"hour"}`，时间采用本地 `YYYY-MM-DD HH:mm:ss`，跨度至多七天，结束时间不含。返回 `{metric:"SUM_OF_TASK_HOURLY_SNAPSHOT_PEAKS",points:[{bucketTime,sampleCount,flowFilesIn,flowFilesOut,bytesIn,bytesOut,queuedCountMax}]}`。单次最多处理 50000 条快照，超限返回 413，要求缩短窗口，绝不截断结果。脚本已移除 MySQL `DATE_FORMAT`，改为按数据库时间范围分页查询并在接口内聚合；GA MySQL 的 24 小时窗口实测 `code=0`、22 个有样本的小时，逐字段与原 SQL 小时汇总一致。各任务在同一小时的快照峰值相加，不能标成小时写入成功总数；远端达梦仍须发布后正向验证。
- `POST /ods/reconciliation/run/diff/export`：请求 `{runId,type?}`，当前租户且状态为 `CONSISTENT` 或 `INCONSISTENT` 的实例返回 `{runId,runStatus,type,total,limit:5000,filename,rows:[{businessKey,diffType,fieldName,sourceValueMasked,targetValueMasked}]}`。超过上限直接返回 413，未知或非本租户实例返回 404。当前开发租户所有对账执行表均无记录，已验证路由和不存在分支，尚无可用于验证全量结果的真实实例。

上述四个接口的六个新资源记录（含数据分发目录和分组）已写入当前开发后端所使用的控制库 `api_file_t`，并调用现有 Magic 资源刷新接口生效。发布内容已另存于 `data/working/haitong-integration-20261004/magic-api-published.json`，源文件在 `data/working/ga-self-loop-20260928/api/03.数据接入/` 下。此后外部流程登记单独增加了租户表，见下一节。

### 外部流程登记的实际契约和验证

- `POST /ods/task/registry/validate`：`{externalFlowId,pipelineId?}`；返回外部 ID 是否已绑定、目标流程是否存在、是否同一绑定、目标流程是否被另一外部 ID 占用。所有查询以服务端当前租户为边界。
- `POST /ods/task/registry/save`：`{externalFlowId,pipelineId,sourceFormat:"CANVAS_DSL_V1"}`；只绑定当前租户已保存且 `dsl_version=1` 的 NiFi 流程。相同绑定重复提交返回 `created:false`，同一外部 ID 绑定不同流程或一条流程绑定不同外部 ID 返回冲突。不会部署流程。
- `POST /ods/task/registry/page`：`{pageNum,pageSize,keyword?}`；只列出当前租户仍有效的真实 NiFi 流程登记记录，不混入浏览器 IndexedDB 样例。
- `POST /ods/task/registry/pipeline-options`：`{pageNum:1..1000,pageSize:1..100,keyword?:最多100字}`；返回 `{pageNum,pageSize,total,list:[{id,name,status,processGroupId}]}`，只包含当前会话租户未删除、DSL v1 且已有 DSL 摘要的流程，不读取 DSL 大字段。`TaskRegistryPage` 输入搜索并显式翻页，选择后仍由 `validate`/`save` 对该单个 ID 再做当前租户校验。
- 已用临时 NiFi 草稿实测创建、首次登记、幂等重复提交、预检和分页返回 1 条；临时登记与草稿在测试结束后均已清理。缺失流程返回 404。没有把任何既有业务流程当作测试对象。
- 分页规模验收：在 GA 本地租户临时补入 40 条可选流程和 1 条伪外租户记录时，总可见 102 条。请求 `pageSize=20` 为 **1 次 HTTP、20 项**，请求 `pageSize=100` 为 **1 次 HTTP、100 项**；Magic 源码只有一次 `db.page`，固定为总数查询与当前页查询，无循环/逐行 SQL。搜索临时 40 条得到 40 项；伪造请求体 `tenantId` 并搜索外租户记录得到 0 项，`pageSize=101` 返回 400。41 条测试记录均已按唯一 ID 清理，余量 0。
- 权限口径：现有 NiFi `GET /nifi/api/pipelines` 的仓储仅以租户业务库隔离，`nifi_pipeline_t` 无组织/角色归属列，也无现成逐角色列表谓词；新选择接口沿用这个租户级可见范围，并额外在 SQL 强制 `tenant_id=tenantRuntime.id()`，不使用请求体中的租户 ID。若将来对 NiFi 流程增加更细角色/组织范围，列表、`validate`、`save` 和按 ID 打开画布须同时增加相同判定，不能只约束选择列表。
- 本次增加的 Magic 资源已备份于 `data/working/haitong-integration-20261004/external-registry-published.json`；源文件位于 `data/working/ga-self-loop-20260928/api/03.数据接入/02.接入任务/外部流程登记/`。最新脚本已同步到本地控制库 `api_file_t` 并刷新；登记预检/分页及分发分页刷新后均实测 `code=0`。

| 当前 Nacos 租户路由 | 实际业务库 | 登记表迁移状态 | 本轮验证范围 |
| --- | --- | --- | --- |
| GA 开发租户 | 本地 MySQL `baseline_ga_old` | 已执行 | 8 个字段注释、4 个索引、0 条遗留记录；NiFi 临时草稿创建/首次绑定/分页/重复幂等与四请求并发实测，通过后清理临时数据 |
| 海渔 | 本地 MySQL `baseline_sea_fishery` | 已执行 | 表注释、8 个字段注释、4 个索引、0 条记录；未用该租户会话做真实登记 |
| 澄天 | 本地 MySQL `chengtian` | 已执行 | 表注释、8 个字段注释、4 个索引、0 条记录；未用该租户会话做真实登记 |
| 北京广播 | 远端达梦 `BASELINE_BEIJING_GD` | **未执行**；已准备达梦 DDL | 未连接或修改远端正式库，达梦方言的接口行为仍需在发布时验证 |

本地 MySQL 中与北京广播同名的历史副本不代表其正式租户业务库，不应对该副本执行迁移后声称北京广播已完成发布。

### 外部流程登记的正式环境发布顺序

1. 核对目标环境的租户—业务库映射及现有 `nifi_pipeline_t` 结构，备份目标租户库和控制库 `api_file_t`。开发验证租户是 `2084109831682699264`，映射 `baseline_ga_old`；不能把这组值硬编码到正式环境。
2. 在每个需要启用登记的**租户业务库**选择对应方言执行 `data-elements/db/migrations/V20261004_02__external_nifi_flow_registry.tenant.mysql.sql` 或 `V20261004_02__external_nifi_flow_registry.tenant.dm.sql`。该迁移只新增 `nifi_pipeline_external_ref_t`，外部 ID 与流程 ID 各有租户内唯一键，含中文表/字段注释。上表三个本地 MySQL 库已执行；北京广播远端达梦及其他正式库尚未执行，不能声称已上线。
3. 将 `magic-api-published.json` 与 `external-registry-published.json` 中的资源按 `tid`/`file_path` 核对后发布到**控制库** `api_file_t`，保留原有同路径资源，避免重复路由；然后调用现有 `/sym/node-config/magic-resource/refresh`。两个备份保存的是精确文件内容，源 `.ms` 与备份应一致。
4. 部署海通前端及需要的 NiFi Java 服务版本。注册页导入的必须是 NiFi Canvas DSL v1；原海通本地 Flow JSON 仍在独立演示 Tab，不会自动变成真实流程。
5. 用正式租户会话验证 `/ods/task/registry/validate|page`、已有流程绑定、重复提交幂等、跨租户拒绝；分发分页应返回 `taskName/taskStatus/...` 驼峰字段，详情与分页同源。再验证一个有真实记录的分发任务；开发库目前本来是 0 条，已用临时测试行正向验证后清理。达梦 SQL 方言与运行结果仍需在目标达梦库单独验证；不要以 MySQL 开发结果替代。

并发同一外部 ID 的登记最终由业务库唯一键约束，`save` 捕获唯一冲突后回读：同一流程返回幂等 `created:false`，不同流程返回 409。开发环境四个同时提交的请求实测均返回成功，恰有一个 `created:true`。这只是接口与数据库契约验证，尚未在正式环境发布。

### 批量队列与多表编组的发布边界

批量队列和多表编组在本地三个 MySQL 租户业务库（`baseline_ga_old`、`baseline_sea_fishery`、`chengtian`）已建新表。发布到其他租户时，先以 Nacos 的实际租户—业务库映射确认目标、备份业务库，再分别执行 `data-elements/db/migrations/V20261004_01__ods_batch_create_job.tenant.mysql.sql` 和 `data-elements/db/migrations/resources/haitong-multi-table-20261004/01-create.mysql.sql`。北京广播使用对应达梦脚本，**目前未执行，亦未验证运行方言**；该租户目前会因缺表而报错，不能将前端部署视为可用。本地同名 MySQL 历史副本不代表正式北京广播租户库。

海渔与澄天的现用 `rm_role_t` 原来没有 Java 数据范围服务所需的 `data_scope` 列，导致批量和多表接口在业务查询前返回 500。本地两库已分别备份角色表结构及全部角色行，再按 [`05-role-data-scope-compat.mysql.sql`](../../data-elements/db/migrations/resources/haitong-multi-table-20261004/05-role-data-scope-compat.mysql.sql) 增加可空列；原有 12/2 个角色的值均保持 `NULL`，Java 按本人范围解释，未自动授予组织或全部范围。迁移和安全回滚条件见[多表资源说明](../../data-elements/db/migrations/resources/haitong-multi-table-20261004/README.md)。用两租户真实会话复测，多表 `page/candidates` 及批量作业 `sourceOptions/list` 均正常返回 0 项；不代表有权限的业务数据已经接入。

两项 Magic 资源都写入控制库 `api_file_t`，而不是租户业务库。批量作业的控制库插入迁移只处理资源尚不存在的情况；已有同 `tid` 的资源必须先备份，再按精确资源 ID 更新为 `resources/ods-batch-create-20261004/job.ms` 的最终内容，通过 Magic 编辑器保存/刷新后回读内容校验，不能把 `WHERE NOT EXISTS` 的空操作当成升级成功。多表五个接口按其 `manifest.json` 与资源说明同样做备份、精确更新和刷新。两组前端页面都依赖这些接口及租户表，不能只部署静态资源。

这些接口按服务端当前租户和角色的**全局**数据源范围（本人、组织或全部）做校验。现有 `DataScopeAuthorizationService.currentScope(resourceCode)` 尚未提供菜单级独立数据范围，传入菜单编号不代表额外的菜单授权。运行中的 `/ods/*` 路由要求登录并绑定租户，但没有独立的海通菜单/API 门禁；`MENU:2081000000000000002` 实际是“数据源管理”菜单，不能直接拿来保护海通接口。当前检查的租户尚无专属海通菜单资源，若要按菜单限制调用，须先确定资源、配置角色关系，再让新接口共同校验并覆盖不同角色回归，不能只在前端隐藏入口。公安租户的多表接口已用真实会话验收并发同键、幂等、内容冲突、跨租户拒绝、软删保留单表及 NiFi 关联；验收组已软删，0 条启用。20/100 项 SQL 数量是独立 SQL 模拟，当前真实库没有足够的可见候选做生产规模验证。批量页的结果由用户主动逐项触发旧单表接口；自动批量确保和跨网传输、签收、账单核销仍缺后端执行与证据契约，不应在生产界面标成已完成。
