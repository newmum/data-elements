# 数据要素系统工程

本仓库集中维护各应用源码、数据库完整结构和工程文档。各前端拥有自己的依赖、锁文件、构建配置和发布目录；主平台的真实业务功能通过配置的地址访问 `data-elements-parent` 后端，独立服务按各自配置运行。全部工程说明见[文档索引](#documentation)。

| 应用 | 工程目录 | 技术栈 | 本地端口 | 生产静态资源前缀 | 安装与启动 |
| --- | --- | --- | ---: | --- | --- |
| 澄天数据中台 | [`data-elements-chengtian/`](docs/data-elements-chengtian.md#overview) | Vue 3 / Vite | 3000 | `/wanxiang/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 海通数据集成中心 | [`data-elements-haitong/`](docs/data-elements-haitong.md#overview) | React 18 / Vite | 3002 | `/haitong/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 统一身份管理平台 | [`data-elements-idaas/`](docs/data-elements-idaas.md#identity-boundaries) | React 19 / Vite | 3005 | `/idaas/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 万象数据治理中心 | [`data-elements-wanxiang/`](docs/data-elements-wanxiang.md#overview) | React 19 / Vite | 3010 | `/wanxiang-governance/` | `npm ci`、`npm run dev` |
| 共享后端 | [`data-elements-parent/`](docs/data-elements-parent.md#overview) | Java 21 / Spring Boot / Maven | 8088 | API 网关配置决定 | `mvn -f data-elements-parent/pom.xml spring-boot:run` |

前端共同建议使用 Node.js 22.12 或更高版本。每个应用请在自己的目录执行安装和构建命令；本仓库没有根目录的 Node workspace，也没有一份供所有前端共用的锁文件。前端的页面与静态资源可独立启动；登录、数据读取和写入需要后端及其 Nacos、数据库等运行服务可用。

海通的 NiFi 画布和[数据上报工作台](docs/data-elements-haitong.md#data-report)都属于海通前端路由。数据上报的页面、业务逻辑和测试统一在海通工程维护。仓库还包含[云梯设计器](docs/data-elements-yunti.md)和[海通数据推送服务](docs/haitong-data-pull.md)，其运行方式和边界分别在各自主文档维护。

仓库级规则位于根目录的 `.gitignore` 和 `.editorconfig`。应用专用的 `package.json`、锁文件、Vite/TypeScript 配置和环境文件保留在各自目录；各工程使用本机进程、JAR 或静态资源进行原生部署，不维护 Docker 部署文件。跨应用导航和会话目前由各应用分别维护，以保证应用可独立发布；变更生产路径或登录协议时，应逐个核对各应用的配置和跨系统跳转。

共享资源采用本地修改、审查差异、再发布的工作方式，详见 [本地资源版本管理](docs/data-elements.md#resource-versioning)。根 `magic/` 对应 Magic 在线目录，`lowcode/` 对应数据库低代码组件目录，`db/` 保存控制库、四个当前租户库和独立 `haitong` 库的完整结构初始化文件。普通前端 `src/` 中的 Vue 文件继续属于各自应用。

运行日志、测试报告、编译与发布临时产物统一放在根 `logs/`，该目录不提交 Git。历史增量 SQL、脚本备份与导出发布包不再作为工程的版本源。长期有用的说明保留在 `docs/`。运行凭据只应从环境或受限配置注入，不要写入前端 `VITE_*` 变量或提交到 Git。

<a id="documentation"></a>

## 工程文档

本文件是仓库唯一 README 和总入口。各工程的功能、接口、配置、部署及验证说明在以下主文档原位维护，协作规则继续保存在 AGENTS.md；子目录不另设 README。

| 工程或范围 | 持续维护文档 |
| --- | --- |
| 仓库公共约定与资源维护 | [data-elements.md](docs/data-elements.md) |
| 澄天数据中台 | [data-elements-chengtian.md](docs/data-elements-chengtian.md) |
| 海通数据集成中心 | [data-elements-haitong.md](docs/data-elements-haitong.md) |
| 统一身份管理平台 | [data-elements-idaas.md](docs/data-elements-idaas.md) |
| 共享 Java 后端及 NiFi 扩展 | [data-elements-parent.md](docs/data-elements-parent.md) |
| 万象数据治理中心 | [data-elements-wanxiang.md](docs/data-elements-wanxiang.md) |
| 云梯应用设计器 | [data-elements-yunti.md](docs/data-elements-yunti.md) |
| 海通数据推送服务 | [haitong-data-pull.md](docs/haitong-data-pull.md) |
| 数据库完整结构、方言与环境基线 | [数据库结构基线](docs/data-elements.md#database-schema) |
| 跨工程维护工具 | [统一维护工具](docs/data-elements.md#maintenance-tools) |

海通数据推送服务采用 Java 21 / Maven，默认端口 18083，实际端口以部署配置为准。在 `haitong-data-pull/` 执行 `scripts\build.cmd` / `scripts\run.cmd`（Windows）或 `bash scripts/build.sh` / `bash scripts/run.sh`（Linux）；生产服务管理使用 `scripts/install-systemd.sh`。外置 `config/` 用于部署参数，`src/main/resources/` 用于随 JAR 打包的默认资源；两者职责不同。`data/` 保存运行数据库、密钥、待处理文件，不能作为无用目录清理。有效测试统一在 `src/test/`，脚本统一在 `scripts/`；运行产物和测试报告不提交 Git。

本机已配置的海通环境从根目录执行 `start-haitong-data-pull.cmd`：它明确加载仓库外私有 YAML，等待健康检查成功后才报告启动完成，日志写入根 `logs/haitong-data-pull/local-runtime/`。从 IDE 直接运行 main 时，工作目录和私有配置参数见[本地启动配置](docs/haitong-data-pull.md#local-startup)。
