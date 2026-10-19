# 数据库时间分片发布

数据库来源使用现有的 `com.linewell.microservice.processors.database.GenerateDateTableFetch`，完整扩展坐标为 `com.linewell.microservice:datadevs-database-nar:2.9.0`。后端发布前核对该类型及三个坐标字段，创建时显式选择 `2.9.0`；不要求安装其他后缀版本，也不修改已有 NAR 的名字或内容。

发布属性使用现有 2.9.0 的真实描述符：

- `gen-table-column-for-val-partitioning` 使用流程配置中初始化自登记表的时间戳。
- `gen-table-fetch-partition-size=10000`，按月（`fetch-partition-type=2`）、间隔 `1`、等待 `0 seconds`。
- `min-date`、`max-date` 默认不设置，不自动填入当前时间或数据库最大值。
- 普通增量及首次全量后增量的 `Maximum-value Columns` 使用相同时间戳；单次和定时全量不启用增量水位。
- 不向该自定义组件写入未声明的 `Initial Load Strategy`。既有“从当前最大值开始”模式保留原生 `QueryDatabaseTableRecord` 初始化路径，初始聚合和后续水位由 NiFi 管理；这一路径不逐表读取来源数据到后端，也不使用 Max Date 模拟跳过历史。
- 来源 Record Writer 继续使用全局服务；ExecuteSQLRecord 每个 FlowFile 最多 10000 行。

普通采集首节点仅在主节点执行。定时全量保留父组定时触发器与串行批次子组；有批次输入的采集节点使用所有节点执行策略，避免“有输入连线且仅主节点执行”的 NiFi 校验错误。

原生画布可见该类型但发布仍报错时，应核对平台实际连接的 NiFi 返回的完整坐标。发布前错误区分“类型未注册”和“类型已注册但版本/坐标不匹配”，列出要求坐标和实际坐标；同一类型同时存在其他版本时仍明确选择 2.9.0。响应中附加的非坐标元数据不影响匹配。旧流程在预检查失败时保留。

此前独立后缀版本的实验源码、构建工具和测试产物已退回忽略的运行材料目录，不属于当前发布依赖，不用于替换现有 2.9.0。
