# 海通统一字段转换 NiFi 扩展

此模块与 Java 后端编译同一份 `../src/main/java/com/linewell/dataelement/mapping/` 源码。字段函数和条件在 JVM 执行；外部码表只执行经登记授权后的参数化批量 SELECT。

## 构建与产物检查

当前运行基线为 Java 21、NiFi 2.11.0、扩展/共享内核/函数库 1.1.1。编辑协议为 schemaVersion=2.0，执行计划协议为 planVersion=1.0，不随包版本机械改号。1.0.4 的历史实施记录保留在 [详细设计第 21 章](../../docs/海通字段映射统一执行方案.md)，1.1.1 契约和当前验收边界见该文档第 22 章。其他 NiFi/Java 组合须单独验证，截图中的 NiFi 1.15 不属于此包的运行基线。

```powershell
cd data-elements-parent/mapping-extension
mvn clean package
cd ../..
python tools/check-mapping-nar.py logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-1.1.1.nar
```

必须清洁构建。NAR 插件会收集构建目录中的 bundled-dependencies；在同一目录仅执行 package 切换版本会混入旧内核，导致同名类被旧版本加载。检查工具必须确认只有一份 core、一份 runtime、无重复类且 Processor/Controller Service 均已注册。产物和测试报告在忽略的 logs 中。

## 组件契约

- `HaitongTransformRecord`：Record Reader、可选的类型化 Reader、Record Writer、执行计划和 SHA-256 摘要；可设置 `plan-id` 和稳定的 `context-node-id`。需要外部字典时绑定批量字典服务。输入具有 avro.schema 时使用类型化 Reader；计划绑定来源 schemaContract 时检查实际 Reader Schema，输出保留 Writer 的 Schema 属性。
- `JdbcHaitongDictionaryService`：发布时的已授权字典定义，以及 datasourceId 对应的 DBCP Controller Service。运行时再次比对表、schema、键、键类型、取值列、过滤条件和重复键策略，之后才执行读取、缓存或快照查找。连接凭据只进入敏感 DBCP 属性；完整固定快照仅进入敏感的 `snapshot-payloads` 属性。
- relationships：success、invalid、failure、retry。failure 和 invalid 必须连接受控保留队列，不能自动终止；retry 使用有界退避和次数上限。
- 每个窗口最多 512 行、约 8 MiB；单行 1 MiB、单字段结果 256 KiB；最多 32 个字典、8 个数据源、16 个依赖阶段、20,000 个查询键。两个窗口许可限制单处理器内并发。不同 FlowFile 分别拥有执行上下文。
- 一份输入的全部窗口在同一个 ProcessSession 中提交。FAIL_FLOWFILE 在任何窗口失败时删除全部候选输出、保留原输入；ROUTE_INVALID 在同一 Session 内提交成功记录及错误信封。
- `ht.input.identity`、`ht.execution.clock` 固定重放身份与时钟。编译器将稳定画布 node.id 写入 `context-node-id`，计划摘要保存在 `ht.plan.hash.<context-node-id>`，生成器使用“输入身份 + 稳定节点身份”；更换原生 Processor UUID 后仍能保持该上下文。手工组件没有此属性时回退到处理器 ID，不能宣称跨替换确定性。同节点计划摘要冲突拒绝重放，多映射节点串联分别校验。
- `ht.dictionary.sql.count` 为当前输入的字典读取 SQL 数，不含目标写入、授权预取或快照预先构建。输入身份、时钟稳定不等于 LIVE/TTL 字典值稳定，也不等于目标 exactly-once。

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

## 当前已确认与待完成验证

- 演示四行业库 143 张统一业务表已完成 check/export；所选后端 95 个运行类、海通前端及 NiFi NAR 1.1.1 已部署，服务 UP。本地 8088 已更新同 95 类并健康 UP，函数/内核版本一致。
- 20/100 行正式预览的精确 38 位小数、敏感 alias，以及实际来源取样输入/输出双端全遮盖已通过；同模板两版本保存与导入已通过。
- 实际 100 行同键集合连续执行两次，LIVE_BATCH 字典 SQL 为 `[1,1]`，TTL_BATCH_CACHE 为 `[1,0]`，VERSIONED_SNAPSHOT 为 `[0,0]`；不包含快照构建、授权/元数据和目标写入。
- 统一运行的 20 套选定测试全部通过；其中升级失败边界 10 条、Schema 批读 12 条、角色及 batchPlans 13 条、publishedLifecycle 3 条。这里列的是选定子集，不累加推断总测试条数。
- 真实原位升级及平台 `/stop → /field-mapping/start-published → /stop` 已通过，来源/目标 ID、水位和原停止状态保留，pending 草稿未误标为整图部署成功。真实 1.1.1 MySQL INSERT/UPDATE/UPSERT/DELETE 已通过，小数与日期时间无偏移。
- 纯内核 10,000 行 × 100 字段的本机一次测量为 0.246 秒、约 40,655 行/秒，仅限内核。真实 NiFi 同规模为 1 输入 → 20 窗口 → 1 输出，21,920,001 字节全量核对通过；连同数据准备、API 等待及约 22 MB 下载的墙钟 164.937 秒不能作为 ETL 吞吐或 SLA，未经核定的 NiFi task 统计未纳入结论。
- 用户确认暂时没有其他数据库测试环境；当前为单 NiFi 节点，不宣称多节点集群、全部真实驱动/复杂类型、完整历史迁移矩阵、端到端 exactly-once 或 SLA 压测已通过。
