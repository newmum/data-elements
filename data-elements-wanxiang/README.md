# 数据治理中心 · data-elements-wanxiang

## 当前运行状态

新版界面的主题、插画、导航和画布设计保留；治理总览、元数据管理、数据标准、数据质量的实际菜单已使用原平台共享后端。认证由元数据管理平台统一完成，接口异常或未登录不会回退成演示业务数据。

模型名称、业务域、选表范围和画布布局沿用旧 `wanxiang2` 的浏览器视图方案，按账号和租户隔离。真实数据源、表、字段、逻辑关系、标准引用、质检记录均来自共享服务；本地模型视图不是服务端数据模型库。

- [元数据管理对接说明与差异](docs/shared-metadata-integration.md)
- [数据质量对接说明](docs/shared-quality-integration.md)
- [全中心功能完整性检查与本轮验收](docs/governance-center-audit-20260929.md)
- 原始 UI 设计记录位于 `doc/`，其中的离线 Mock 运行方式和旧测试数字属于历史版本，不适用于当前已接入菜单。

## 本地启动

使用 Node.js 22.12 或更高版本，现有依赖安装完成后：

```powershell
npm run dev
```

开发入口为 `http://localhost:3010/`，元数据管理平台为 `http://localhost:3000/`，共享后端为 `http://localhost:8088/`。先在平台登录，再进入治理中心。不要直接双击源码 `index.html`。开发服务使用固定端口，不会静默换成其他端口。

配置项：

| 配置 | 用途 / 默认值 |
| --- | --- |
| `VITE_DATA_ELEMENTS_API_TARGET` | 开发代理的共享后端地址，默认 `http://localhost:8088` |
| `VITE_DATA_ELEMENTS_API_BASE_URL` | 浏览器接口前缀，开发默认 `/dev-api`，生产默认 `/prod-api` |
| `VITE_DATA_ELEMENTS_PLATFORM_URL` | 统一登录及返回平台地址，开发默认 `http://localhost:3000/#/`，生产默认 `/wanxiang/#/` |
| `VITE_WANXIANG_GOVERNANCE_URL` | 系统切换中的治理中心地址，生产默认 `/wanxiang-governance/` |

不要将账号、数据库密码、token 或 keytab 放入源码、构建环境文件或 URL。不同端口通过平台既有的 session bridge 同步登录态，生产同源部署复用平台存储。

## 验证与构建

```powershell
npm run check
npm run typecheck
npm run build
```

`check` 包括源码语法、纯领域模块严格类型和逻辑/契约测试；`build` 包括完整 React/Ant Design 类型检查及 Vite 生产构建。逻辑测试中的历史预览状态机测试不等于真实接口回归。

真实接口验证脚本从调用环境读取 `METADATA_TEST_ACCOUNT`、`METADATA_TEST_PASSWORD`，不打印登录凭据或 token：

```powershell
node scripts/verify-shared-metadata.mjs
node scripts/verify-shared-metadata-writes.mjs
```

前者仅核对两套前端的共享读取；后者只新增并清理具有专用前缀的测试记录。真实物理采集验证另见 `scripts/verify-shared-metadata-collection.mjs`，连接目标固定为 MySQL `information_schema`，额外连接凭据仅在调用环境提供，完成后清除临时登记的连接信息。不要在生产环境随意运行写入测试。

2026-09-29 扩展已接入联合关系/条件/基数、实际业务键验证、明确确认的物理 FK、质量规则/技术字段引用及手工加工图/字段转换。真实扩展回归脚本为 `scripts/verify-metadata-extensions.mjs`，会创建和清理专用开发测试表与记录，运行前必须核对实际租户库并从调用环境提供连接配置。本轮 25 项通过，MySQL 实际 FK 约束已验证；其他数据库的实库验收和其他租户迁移尚未完成。

当前用户已确认账号体系仍在更新。不要通过修改认证或回退旧运行包恢复页面；认证稳定后的全菜单及加工图 UI 闭环复验见完整性检查清单。当前最新前端自动化为 300 项通过，完整构建通过，后端新增能力针对性测试 13 项通过。这些数字不等于整个中心所有页面已完成最终浏览器验收。

生产带路径前缀构建示例（不等于部署）：

```powershell
$env:VITE_DATA_ELEMENTS_API_BASE_URL='/prod-api'
$env:VITE_DATA_ELEMENTS_PLATFORM_URL='/wanxiang/#/'
$env:VITE_WANXIANG_GOVERNANCE_URL='/governance/'
npx vite build --base=/governance/ --outDir dist-prefix-check
```

反向代理需配置相应静态目录、API 前缀和平台页面。现有大体积构建分片警告不影响构建成功，但正式发布前可继续进行按模块拆包优化。

## 工程结构

```text
src/app/                   统一认证、中心切换、个人信息、导航及全屏壳
src/features/platform/     已接入的管理页面、表单与操作
src/features/er/           真实元数据驱动的 ER 画布与关系操作
src/services/              共享接口 DTO、缓存、查询及本地视图适配
src/shared/                共享认证、请求、跨系统导航
src/assets/                用户已确认的本地插画、数据库原图
src/mocks/                 保留的历史预览/测试实现，不作为已接入菜单的数据源
backend/                   需要保存到原 Magic API 的审阅后脚本
scripts/                   检查、构建、真实接口验证及发布工具
tests/                     逻辑/契约测试和图形资产原样检查
docs/                      当前共享后端对接说明
doc/                       新版原始设计与历史验收记录
```

数据库图标来源和固定文件哈希见 `src/assets/databases/sources.json`，许可证见 `third-party/`。界面只使用本地图像，不请求外网图床。
