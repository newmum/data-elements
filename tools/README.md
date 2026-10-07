# 统一维护工具

本目录的工具源码和测试提交 Git，供五个工程共用。Magic、低代码和数据库的统一维护入口集中在这里；一次性执行脚本、日志、编译产物、发布包和恢复备份不放在本目录。

| 文件 | 用途 |
| --- | --- |
| `configure-resources.py` | 从当前 Nacos API 配置定位控制库和租户库，把私有连接配置保存到仓库外 |
| `resource-sync.py` | 导出、比较与规划根 `magic/`、`lowcode/` 的资源；在明确目标后发布单个已登记资源 |
| `compile-lowcode.mjs` | 使用澄天现有编译器编译选定 Vue 组件，校验生成代码语法，结果写入 `logs/lowcode/compiled/` |
| `export-db.py` | 只读导出实际五个数据库的完整结构，更新根 `db/`；不执行数据库变更 |
| `check-db.py` | 只读校验所有行业租户与 `baseline_ga_old` 的表集合、列、主键、索引和外键列；按达梦原生语义识别类型、生成列和自动更新时间，差异返回失败 |
| `tests/test_resource_sync.py` | 检查资源身份、路径、并发修改、凭据脱敏和发布保护 |
| `tests/test_schema_gate.py` | 检查同数量不同表、额外唯一索引、主键变化、错误租户默认值与 Magic 断链/路径漂移的拦截 |

从仓库根目录执行。参数、依赖、私有配置和完整流程见 [本地资源版本管理](../docs/本地资源版本管理.md)，命令参数以各工具 `--help` 为准。

```powershell
python tools/resource-sync.py status
python tools/resource-sync.py plan
python tools/check-db.py
python tools/export-db.py
python -m unittest discover -s tools/tests
```

工具默认只读；`pull` 只写本地副本并保护本地修改。发布需要指定资源、环境和已认证会话，不能因本地缺少文件自动删除数据库资源。含凭据配置和恢复材料保持在仓库外的受限目录，其他运行产物放根 `logs/`。
