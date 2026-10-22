# 工程文档

每个工程只维护下表对应的一份主文档。功能、接口、配置、部署及验证边界在既有章节原位更新，通过 Git 查看历史版本；不再新增日期、阶段、版本或单次变更说明文件。工程内的 README 仅作入口，`AGENTS.md` 保留协作规则。

| 工程 | 持续维护文档 |
| --- | --- |
| 仓库公共约定与资源维护 | [data-elements.md](data-elements.md) |
| 澄天数据中台 | [data-elements-chengtian.md](data-elements-chengtian.md) |
| 海通数据集成中心 | [data-elements-haitong.md](data-elements-haitong.md) |
| 统一身份管理平台 | [data-elements-idaas.md](data-elements-idaas.md) |
| 共享 Java 后端及 NiFi 扩展 | [data-elements-parent.md](data-elements-parent.md) |
| 万象数据治理中心 | [data-elements-wanxiang.md](data-elements-wanxiang.md) |
| 云梯应用设计器 | [data-elements-yunti.md](data-elements-yunti.md) |
| 海通数据推送服务 | [haitong-data-pull.md](haitong-data-pull.md) |

数据库完整结构统一在 [db/](../db/README.md) 维护，各实际数据库保留一份完整初始化结构，不在子工程继续累积增量 SQL。现有库升级所需的临时执行语句、测试报告、截图和导出文档放根目录被忽略的 `logs/`；含凭据的恢复材料放仓库外受限目录。

`assets/` 只保存主文档引用且仍需维护的附属资源，例如海通字段映射交互说明。历史验收截图、JSON 报告和旧版本评审材料不属于文档源；原有材料已归档到本机 `logs/schema-doc-consolidation/archive/`，已提交版本也可以从 Git 历史查阅。
