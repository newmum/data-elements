# 数据库结构基线

本目录保存五个实际运行数据库的最新完整结构。每个库只有一份 `init.sql`；`schema-manifest.json` 记录方言、对象数量和校验值。结构包括表、完整列定义、主键、索引、外键、注释、视图，以及实际存在的触发器、存储程序、事件或序列。不含业务记录、账号密码、创建用户、授权或 Magic/低代码资源 INSERT。

初始化文件用于新空库或新空 schema。现有库的修改先审查结构差异、备份，再执行本次受控变更，完成后重新导出完整结构。共享 Magic 与低代码分别在根 `magic/`、`lowcode/` 维护。

## 当前数据库与统一结构

| 角色 | 实际数据库 | 方言 | 表数量 |
| --- | --- | --- | ---: |
| 控制库 | `baseline` | MySQL | 50 |
| 公安 | `baseline_ga_old` | MySQL | 143 |
| 澄天 | `chengtian` | MySQL | 143 |
| 海渔 | `baseline_sea_fishery` | MySQL | 143 |
| 广电 | `baseline_beijing_gd`，运行配置绑定 `BASELINE_BEIJING_GD` | 达梦 | 143 |

四个行业租户维护同一套业务表、列、主键、索引和约束，以 `baseline_ga_old` 为统一结构权威。当前包含公安原 139 张表和以下四张有现用功能或资产依据的公共表；各库都有完整定义，新增空表不复制其他租户数据。

| 公共表 | 业务依据 |
| --- | --- |
| `service_node_t` | 数据服务节点列表、保存、删除接口及有效节点记录 |
| `da_order_asset_rela` | Java 订单资产实体/服务及资产删除关联清理 |
| `kg_relation` | 知识图谱关系清单接口 |
| `nrta_tv_drama_approval_t` | 广电当前租户已有电视剧审批数据表和资源目录登记 |

控制库保存租户路由、共享资源与身份控制结构，独立于行业表集合。`baseline.api_backup_t` 和 `baseline.ui_component_history_t` 是编辑器历史功能表，保留。各租户保留自己的租户 ID 默认值、部署设置和业务数据；NiFi 证书校验设置按部署维护。

## 每次结构变更的约束

1. 新增表、增删字段、索引、主键、外键或自动更新时间规则，先修改 `db/` 中所有行业租户的完整定义并审查。
2. 按各租户真实绑定和数据库方言应用同一业务结构；有效公共功能要补齐，清理旧表须核实调用、数据资产和依赖。
3. 运行 `python tools/check-db.py`。工具只读比较行业表集合、列定义/注释/默认值、主键、索引语义与外键列，并检查达梦生成列及自动更新时间触发器；发现差异返回非零状态。约束表达式、触发器具体逻辑及部署视图继续结合完整 DDL 审查。
4. 运行 `python tools/export-db.py`，同步五库完整初始化文件和清单，审查全部 `db/` 差异。不能只更新公安文件，也不能只让表数量相同。

跨库视图依赖的数据库须先存在；控制库与行业库及各自部署视图按实际环境维护。临时增量 SQL、日志、发布包放根 `logs/`；含凭据或业务数据的必要恢复材料保存在仓库外受限目录。Git 只保存最新完整结构与长期维护工具。

## 达梦原生表示

达梦初始化使用原生 DDL，保留实际引号标识符、大小写、MAIN 表空间及存储属性。由已有权限的 DBA 在兼容设置一致的环境初始化；本目录不创建数据库用户。JSON/长文本映射为原生长文本，无符号整数按范围表示，自动更新时间使用原生触发器；目前有 20 个自动更新时间触发器。

登录名字段为 `VARCHAR(50)`，有效登录名生成规则为 `CASE WHEN deleted=0 THEN lower(trim(user_name)) ELSE NULL END`，联合唯一约束为租户 ID 与有效登录名。达梦以虚拟列/函数索引表达；其系统索引元数据会展开依赖列，因此校验使用实际索引 DDL 核对逻辑列。

以下是明确的方言表示，初始化文件保存各库真实结构：

| 范围 | MySQL | 当前达梦 |
| --- | --- | --- |
| `res_logical_model_field.entity_id` | 非空、默认空字符串 | Oracle 兼容模式将空字符串视为 NULL，保留可空列，不填写虚假实体 ID |
| `rm_user_t.date_birth` | `DATE` | 原生日期元数据/导出为 `TIMESTAMP(0)` |
| 时间字段 | `DATETIME` / `DATETIME(6)` | `TIMESTAMP`，部分原生列保留更高小数秒精度，校验要求至少覆盖源精度 |

默认数值按数值语义比较，标识符按实际方言引用。上述表示不要求变更整库兼容模式。类型、生成列与修改限制可查阅 [达梦官方数据定义文档](https://eco.dameng.com/document/dm/zh-cn/pm/definition-statement.html)。

## 工具与私有连接配置

从仓库根目录运行：

```powershell
python tools/check-db.py
python tools/export-db.py
```

工具默认读取用户目录 `.codex/private/data-elements/resources.local.json`，也可用 `--config` 指定。私有配置含控制库连接及当前 Nacos API/运行配置的租户映射，不提交 Git。改变环境或租户路由后先重新定位，不能从过期 Nacos 数据库缓存猜测目标。环境参数优先取本机环境变量。

工具使用 `pymysql`；达梦还使用 `jaydebeapi`、`JPype1` 和现有 JDBC 驱动。可用 `DATA_ELEMENTS_DM_JDBC_JAR` 指定驱动，或从当前用户 Maven 仓库发现。导出移除源账号 `DEFINER` 与随业务变化的表级自增计数，保留列自增属性；五库全部成功读取后才写本地文件。
