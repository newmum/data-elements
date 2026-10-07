# 数据要素系统工程

本仓库包含五个可分别安装、启动和部署的应用。各前端拥有自己的依赖、锁文件、构建配置和发布目录；仓库根目录集中维护共同规则、共享 Magic/低代码源码、数据库结构和开发工具。各前端可以独立打开，涉及真实业务数据的功能仍通过配置的地址访问同一个 `data-elements-parent` 后端。

| 应用 | 工程目录 | 技术栈 | 本地端口 | 生产静态资源前缀 | 安装与启动 |
| --- | --- | --- | ---: | --- | --- |
| 澄天数据中台 | [`data-elements-chengtian/`](data-elements-chengtian/README.md) | Vue 3 / Vite | 3000 | `/wanxiang/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 海通数据集成中心 | [`data-elements-haitong/`](data-elements-haitong/README.md) | React 18 / Vite | 3002 | `/haitong/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 统一身份管理平台 | [`data-elements-idaas/`](data-elements-idaas/README.md) | React 19 / Vite | 3005 | `/idaas/` | `pnpm install --frozen-lockfile`、`pnpm run dev` |
| 万象数据治理中心 | [`data-elements-wanxiang/`](data-elements-wanxiang/README.md) | React 19 / Vite | 3010 | `/wanxiang-governance/` | `npm ci`、`npm run dev` |
| 共享后端 | [`data-elements-parent/`](data-elements-parent/README.md) | Java 21 / Spring Boot / Maven | 8088 | API 网关配置决定 | `mvn -f data-elements-parent/pom.xml spring-boot:run` |

前端共同建议使用 Node.js 22.12 或更高版本。每个应用请在自己的目录执行安装和构建命令；本仓库没有根目录的 Node workspace，也没有一份供所有前端共用的锁文件。前端的页面与静态资源可独立启动；登录、数据读取和写入需要后端及其 Nacos、数据库等运行服务可用。

海通的 NiFi 画布属于海通前端的路由，不是第六个应用。系统导航还包含启智、报表、搜索、云图设计器等入口，它们目前没有对应的仓内工程，需由其他部署提供。澄天开发服务中的 `/yuntu` 可选地读取外部设计器构建产物；缺少设计器时不影响澄天主应用启动。

仓库级规则位于根目录的 `.gitignore` 和 `.editorconfig`。应用专用的 `package.json`、锁文件、Vite/TypeScript 配置和环境文件保留在各自目录；`.dockerignore` 仍按各应用的 Docker 构建上下文生效。跨应用导航和会话目前由各应用分别维护，以保证应用可独立发布；变更生产路径或登录协议时，应逐个核对各应用的配置和跨系统跳转。

共享资源采用本地修改、审查差异、再发布的工作方式，详见 [本地资源版本管理](docs/本地资源版本管理.md)。根 `magic/` 对应 Magic 在线目录，`lowcode/` 对应数据库低代码组件目录，`db/` 保存控制库及四个当前租户库的完整结构初始化文件。普通前端 `src/` 中的 Vue 文件继续属于各自应用。

运行日志、测试报告、编译与发布临时产物统一放在根 `logs/`，该目录不提交 Git。历史增量 SQL、脚本备份与导出发布包不再作为工程的版本源。长期有用的说明保留在 `docs/`。运行凭据只应从环境或受限配置注入，不要写入前端 `VITE_*` 变量或提交到 Git。
