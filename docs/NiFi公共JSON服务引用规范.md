# NiFi 公共 JSON 服务引用规范

平台发布任务时，所有 JSON Record Reader / Writer 都引用当前 NiFi 节点的真实 `NiFi Flow` 根作用域服务。

| 用途 | 全局服务名称 | 类型 |
| --- | --- | --- |
| JSON 读取 | `PUBLIC-READER-JSON` | `org.apache.nifi.json.JsonTreeReader` |
| 非 Hive 库 JSON 写出 | `PUBLIC-WRITER-JSON` | `org.apache.nifi.json.JsonRecordSetWriter` |
| Hive 读写链路 JSON 写出 | `PUBLIC-WRITER-JSON-TIME_FORMAT` | `org.apache.nifi.json.JsonRecordSetWriter` |

## 发布约束

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

## 历史流程调整约束

历史调整应先批量读取原生流程与平台发布关联，逐流程核验租户、流程 ID 与 `data-elements:v1:<tenant>:pipeline:<id>` 归属标记。缺少关联或无法证明归属的原生流程不自动修改。

在保留处理器 ID、连接、队列和采集状态的前提下，只替换相关处理器／服务属性中的 JSON 服务 ID。需要停机修改的处理器应记住原状态，配置完成后只恢复原先处于运行状态的处理器。不得通过重建流程、清队列或清采集水位完成引用调整。

任务级重复服务必须在重新读取全节点引用并确认没有依赖后才能禁用和删除；仍有外部引用的服务应保留并报告。全局服务的原配置和运行状态须在调整后保持一致。配置备份保存在受限运行目录，不能把可能含连接信息的完整流程快照写入 Git。
