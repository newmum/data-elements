# 海通数据集成中心操作手册

## 登录和新建流程

打开站点根地址，以 `admin` 和部署时设置的密码登录。进入「NiFi 流程」后点击「新建流程」，为流程命名，选择已登记的 NiFi 节点，再把需要的组件添加到画布。按数据方向连接节点，填写各节点的配置并保存。部署成功后点击启动；测试结束后点击停止。

先部署到测试节点并使用独立的测试表和集合。运行期间从画布查看队列、组件状态和告警。出现部署错误时先处理所提示的连接、驱动或公共服务问题；出现运行错误时先检查具体节点的告警，再到目标库确认是否已写入。

## 样例一：把数据写入 MongoDB

在测试 MongoDB 创建 `haitong_validation.flow_source`，以 `mongosh` 写入四条样例记录：

```javascript
db.getSiblingDB('haitong_validation').flow_source.insertMany([
  {record_id: 'validation-01', amount: 12.50, status: 'active'},
  {record_id: 'validation-02', amount: 7.00, status: 'pending'},
  {record_id: 'validation-03', amount: -2.00, status: 'active'},
  {record_id: 'validation-04', amount: 3.00, status: 'unknown'}
]);
```

1. 在海通画布选择 MongoDB 来源和 MongoDB 目标，将来源连接到目标。
2. 分别填写主机、端口、数据库、认证配置和集合名。来源选 `flow_source`，目标选同库的空集合 `flow_mongo_target`。填写的是 NiFi 执行节点能访问的地址。
3. 保存、部署、启动。看到目标集合有四条记录后停止流程。重复验证前请更换测试集合，避免再次插入相同记录。

## 样例二：从 MongoDB 读取，经 Java 质检后写入关系库

1. 在独立的测试 MySQL 或 MariaDB 上执行 `examples/example-mariadb.sql`。用有相应表写入权限的应用账号连接，JDBC 目标表填写 `haitong_validation.flow_target`（界面若分别有数据库和表字段，分别填写）。MariaDB 的 JDBC 驱动在 `examples/drivers/`，复制到 NiFi 执行机并按部署说明登记实际路径。若用 MySQL，选择对应数据库类型和驱动。
2. 将 `examples/quality-check.jar` 复制到每个可能执行该节点的 NiFi 主机。确保 NiFi 进程能执行 Java 21 并读取该 JAR。可以参照 `examples/QualityCheck.java` 编写自己的检查规则。
3. 在海通画布连接「MongoDB 来源 → JAVA 加工 → JDBC 目标」。MongoDB 来源集合选 `flow_mongo_target`。在 JAVA 加工中填写 NiFi 主机上的 Java 可执行路径和 JAR 路径。JDBC 目标选择原生 `PutDatabaseRecord`、`INSERT`，填写连接信息、目标表与对应驱动。
4. 保存、部署、启动。通过质检的两条记录应出现在目标表，状态分别为 `ACTIVE`、`PENDING`，`quality_status` 为 `PASS`。另外两条因负金额或不支持的状态被过滤。确认结果后停止流程。

样例 JAR 从标准输入读取 JSON，在标准输出写合格记录并向标准错误记录通过和拒绝数量。如果整批都未通过，样例用非零退出码交给流程的失败路径。上线前请按业务要求决定是否隔离、告警或重试不合格记录。
