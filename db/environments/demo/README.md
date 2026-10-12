# 演示环境数据库结构

本目录保存演示环境当前实际运行的完整结构。五份 `init.sql` 和 `schema-manifest.json` 由 `tools/export-db.py` 从实际库导出，只用于新空库初始化，不能在已有演示库直接执行。

| 用途 | 实际数据库 | 方言 | 表数 |
| --- | --- | --- | ---: |
| 控制库 | `baseline` | MySQL | 53 |
| 公安 | `baseline_demo_ga` | MySQL | 140 |
| 广电 | `baseline_demo_broadcasting` | MySQL | 140 |
| 海渔 | `baseline_demo_sea_fishery` | MySQL | 140 |
| 澄天 | `baseline_demo_chengtian` | MySQL | 140 |

控制库另有两张部署视图。保留的三个旧身份表属于原环境恢复与历史依赖范围，不作为新的共享账号来源。当前行业账号在所属租户的 `rm_user_t` 中维护，四个租户物理分库并同时校验 `tenant_id`。

本机的统一结构权威仍是 `baseline_ga`；演示环境校验使用实际绑定的公安库 `baseline_demo_ga`。升级时先审阅本机统一结构，再按演示租户路由应用、核对和导出。本目录与本机结构都需要同步维护，不能只让表数量一致。

连接配置保存于仓库外受限目录。对已定位的演示配置使用：

演示环境另有两个独立接入验证库 `demo_ingest_source` 和 `demo_ingest_target`，结构与用途见 [演示接入验证库](ingestion-fixtures/README.md)。它们不加入五个控制/租户数据库的统一结构校验。

```powershell
python tools/check-db.py --config <受限演示配置路径> --authority baseline_demo_ga
python tools/export-db.py --config <受限演示配置路径> --output db/environments/demo
```

旧演示库 `baseline_ga` 和 `baseline_chengtian` 保留作为升级前恢复材料，当前业务路由不再使用。目录中不保存旧业务数据备份、账号授权或连接凭据。部署与恢复流程见 [演示环境部署](../../../docs/演示环境部署.md)。
