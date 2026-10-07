# 统一维护工具

本目录的工具源码和测试提交 Git，供五个工程共用。Magic、低代码和数据库的统一维护入口集中在这里；一次性执行脚本、日志、编译产物、发布包和恢复备份不放在本目录。

| 文件 | 用途 |
| --- | --- |
| `configure-resources.py` | 从当前 Nacos API 配置定位控制库和租户库，把私有连接配置保存到仓库外 |
| `resource-sync.py` | 导出、比较与规划根 `magic/`、`lowcode/` 的资源；在明确目标后发布单个已登记资源 |
| `compile-lowcode.mjs` | 使用澄天现有编译器编译选定 Vue 组件，校验生成代码语法，结果写入 `logs/lowcode/compiled/` |
| `export-db.py` | 只读导出实际五个数据库的完整结构，更新根 `db/`；不执行数据库变更 |
| `check-db.py` | 只读校验所有行业租户与 `baseline_ga` 的表集合、列、主键、索引和外键列；按达梦原生语义识别类型、生成列和自动更新时间，差异返回失败 |
| `audit-api.py` | 只读整理 Magic HTTP 接口的静态、有限模板及内部依赖引用；可加入受限的当前编译代码、菜单、表单、服务绑定和运行 Java 上下文，报告只写根 `logs/`，不自动删除 |
| `tests/test_resource_sync.py` | 检查资源身份、路径、并发修改、凭据脱敏和发布保护 |
| `tests/test_schema_gate.py` | 检查同数量不同表、额外唯一索引、主键变化、错误租户默认值与 Magic 断链/路径漂移的拦截 |
| `tests/test_api_audit.py` | 检查注释请求、代理/环境前缀、模板条件分支、有限表映射、绝对服务地址及路径参数的引用识别 |

从仓库根目录执行。参数、依赖、私有配置和完整流程见 [本地资源版本管理](../docs/本地资源版本管理.md)，命令参数以各工具 `--help` 为准。

```powershell
python tools/resource-sync.py status
python tools/resource-sync.py plan
python tools/check-db.py
python tools/audit-api.py
python tools/export-db.py
python -m unittest discover -s tools/tests
```

工具默认只读；`pull` 只写本地副本并保护本地修改。发布需要指定资源、环境和已认证会话，不能因本地缺少文件自动删除数据库资源。含凭据配置和恢复材料保持在仓库外的受限目录，其他运行产物放根 `logs/`。

接口引用清单不能直接当删除计划。菜单导航并非接口请求；动态表达式、资源 ID 调度、外部回调和服务登记需要人工核对。当前运行上下文通过 `audit-api.py --context <受限上下文文件>` 传入，原始内容不写入报告；工具不会读取用户会话凭据或探测生产接口。目录建议见 [资源目录组织建议](../docs/资源目录组织建议.md)。
