# 统一身份管理平台（data-elements-idaas）

`data-elements-idaas` 是工程和包标识；“卓鉴”是内部产品名称。登录、控制台、公众入口、浏览器标题与跨系统切换入口对用户显示“统一身份管理平台”。政企与公众是同一系统的两个身份域。

运行期名称由 `src/domain/branding.ts` 统一维护。后端已有品牌配置若仍含旧产品名或工程名，页面展示时使用系统名称；其他自定义标题保留，原始配置及审计资料不改写。下文包含历次实施记录，历史阶段说明以当时的范围为准。

## 当前运行状态（2026-10-05）

前端沿用 V2.2 可读紧凑版。控制库 `baseline` 已部署 40 张目标 IAM 业务表。平台操作账号 `iam_operator_t`、人员主档 `iam_subject_t`、统一认证账号 `iam_auth_account_t` 分属独立身份域；平台会话不附带租户 ID，平台权限与角色数据范围由 Magic 即时校验。运行期使用共享 `data-elements` 服务及其“13.统一身份管理” Magic 资源，不读取浏览器旧 mock 数据充当真实身份资料。

公安、广电、海渔、澄天四个行业应用目前都以本地账号 `accountSource=LOCAL` 登录，中央下发保持 `OFF`。人员、机构、角色、资源和授权资料在控制库管理，公安已完成正式初始化导入；租户新资料仅由操作人员在应用详情人工比较、确认后导入。目录或授权保存不会自动下发。只有在“同步对象”明确点击下发，或在“同步任务”明确点击继续执行／重试，才调用下发接口。配置接收端并测试连接不等于开启下发或完成端到端验收，详见 [手动同步实施记录](docs/同步改为手动执行20261001.md) 与 [本轮隔离验收](docs/手动下发隔离验收20261005.md)。

运行绑定分别为公安 `baseline_ga_old`（MySQL）、广电 `BASELINE_BEIJING_GD`（达梦）、海渔 `baseline_sea_fishery`（MySQL）、澄天 `chengtian`（MySQL）。四个行业应用的机构、角色、菜单及本地授权继续由各自租户系统维护；不得通过历史安装器或完整发布器恢复已退役模型。当前开发边界和增量发布方式见 [AGENTS.md](AGENTS.md)。

### 历史方案与阶段记录

[整体设计 V3](docs/整体设计20260928/总体方案.md)、[完整数据库表结构](docs/整体设计20260928/数据库表结构设计.md)、[功能及关联设计](docs/整体设计20260928/功能与关联设计.md)、[第一阶段实施与验收](docs/第一阶段实施20260928/实施与验收说明.md)、[公安租户账号自闭环实施与验收](docs/公安租户账号自闭环实施与验收.md) 和 [后端架构与数据库设计](docs/后端架构与数据库设计.md) 保留实施过程与当时的验收范围；其中“15 张表”“其他应用使用 CONTROL”“中央目录待实施”等说法均是历史状态。

左上角系统切换入口显示“统一身份管理”与下箭头；展开菜单沿用万象、启智的五组分类，卓鉴位于“平台支撑”，列表项显示“统一身份管理平台”。桌面折叠侧栏与手机导航抽屉均可切换。万象（含磐石、皓月）、启智承载的能力中心及海通同步提供卓鉴入口；数据中台的“全部导航”也提供“平台支撑 → 统一身份管理平台”。折叠侧栏的二级菜单支持悬停、点击和键盘选择功能，手机抽屉保留内联导航。

折叠菜单交互、完整系统名称及最新验收见 [折叠菜单与系统名称调整](docs/折叠菜单与系统名称调整.md)。

跨系统导航使用当前标签页。卓鉴默认开发入口为 `http://localhost:3005/#/console/workforce/overview`，生产部署默认前缀 `/idaas/`，可通过 `VITE_IDAAS_CENTER_URL` 覆盖。卓鉴访问其他系统分别通过 `VITE_WANXIANG_GOVERNANCE_URL`、`VITE_QIZHI_CENTER_URL`、`VITE_HAITONG_CENTER_URL` 配置；开发默认端口为 3010、3001、3002，生产默认前缀为 `/wanxiang-governance/`、`/qizhi/`、`/haitong/`。

这里完成的是系统入口互通。卓鉴通过身份接口建立自身可信会话，各系统按自身会话校验访问；跨系统协议单点登录属于后续阶段。具体修改与验收见 `docs/系统切换接入说明.md`。

在 V2.1 工程上修正全站文字层级。**管理工作区保留紧凑间距与控件，不再压缩标题和主导航的可读字号；认证表单采用舒展尺寸。** 保留深色导航、蓝紫主题、白色卡片、原业务页面及关联数据。

## 启动

需要 Node.js 22.12 或以上版本。仓库提交的锁文件是 `pnpm-lock.yaml`，安装时使用 pnpm：

```bash
pnpm install --frozen-lockfile
npm run dev
```

默认地址：`http://localhost:3005/#/login`。`npm run dev`、`npm run preview` 与 Playwright 测试统一使用根目录 `dev-server.config.ts` 中的 localhost / 3005 配置。端口占用时直接报错，不自动递增。Windows 可使用 `启动前端.cmd`；其他系统使用 `bash 启动前端.sh`。不要直接双击根目录 `index.html`，它是 Vite 入口。

同目录其他前端目前分配数据中台 3000、启智 3001、海通 3002、设计器 3003、报表 3004；卓鉴使用 3005。旧地址 `127.0.0.1:5173` 的浏览器本地数据仍保存在旧来源，不会被删除；新地址拥有独立存储，需要重新登录。

沿用固定版本 React/React DOM 19.3.0、Ant Design 6.6.5，完整依赖见 `package.json`。不要以 `npm install` 生成另一份锁文件，也不要在只有 `pnpm-lock.yaml` 的当前仓库运行 `npm ci`。

## 本轮修订

| 区域 | 规则 |
| --- | --- |
| 页面标题 | 24/34px、600字重 |
| 抽屉/弹窗标题 | 18/28px |
| 卡片、分组、应用卡片名称 | 16/24px；small 卡片标题仍是标题 |
| 一级/二级菜单、顶部主导航 | 14/22px，不小于正文 |
| 表格主值、表单、按钮、页签 | 14/22px |
| 描述与帮助 | 13/20px；不能比标题大 |
| 标签、时间和辅助标注 | 12/18px，标签行高可20px |
| 主要指标 / 次要数字 | 28/36px / 24/32px |

同时修正了嵌套 Text 重置应用名称字号、原生文本回退到浏览器字号、SVG 图表小字随 viewBox 缩小等问题。窄屏采用换行、收起导航和局部表格滚动，不重新缩字号。设置页保存区改为文档流，避免遮住输入。

管理工作区保持紧凑：56px顶栏、216/64px侧栏、28px常用控件、8/12/16px主要间距。小卡片标题区最小40px，为16px标题保留合理行高，不硬裁切多行标题。认证页面使用独立标准主题：44px 输入框、至少 48px 主按钮、24px 字段间距，桌面与移动端保持相同操作尺寸。

## 设计规范与关键代码

```text
design.md                        唯一设计规范，小写，不是产品页面
AGENTS.md                        Codex及其他AI必须先读的约束
src/app/typography.ts             唯一文字角色数值源
src/styles/typography-tokens.css  自动生成，不手改
src/app/theme.ts                 Ant Design算法及全局/组件Token
src/domain/branding.ts           工程名、产品名、系统名与完整品牌名称
src/styles/typography.css         最后加载的业务语义文字样式
src/styles/app.css                品牌、布局与紧凑间距
src/components/common.tsx        标题、列表、指标、编辑与详情
src/components/useElementWidth.ts 响应式图表实测宽度
src/components/chart-layout.ts  图表刻度疏密规则
src/pages/                       10个页面实现文件，复用到所有业务路由
```

修改字号后运行 `npm run tokens:generate`，再运行 `npm run tokens:check`。禁止恢复设计规范菜单、路由、密度切换或用户界面的研发提示。

## 第一阶段后端连接（历史记录）

默认运行时使用真实身份服务，不再使用本地固定账号登录。登录页从 `/sym/tenant/login-options` 读取可选租户，显式提交所属租户；只有 `/idaas/auth/login` 与工作区加载均成功后才进入控制台。

- `src/services/http.ts`：Magic API 响应与 `token` 请求头适配，业务失败不当作成功数据。
- `src/services/workspace.ts`：服务端会话恢复、工作区加载、实体写入与重新加载；相同写请求失败重试沿用内存中的请求号。
- `src/mock/store.ts`：保留旧导入路径，React 绑定现已使用上述真实服务。
- `src/mock/database.ts` 与种子只供隔离单元测试；旧浏览器本地数据保留，不上传、不删除，也不作为服务器失败时的替代结果。

本地 `/api` 由 Vite 代理到 `http://localhost:8088`；可用 `VITE_IDAAS_API_TARGET` 修改开发代理目标。生产反向代理需要将 `/api/idaas/**` 和 `/api/sym/tenant/login-options` 路由到同一 `data-elements` 服务，并移除 `/api` 前缀。只有不透明会话凭据存于当前标签页的 `iam.frontend.backend.token`，重新加载后仍需由服务端验证。

已接入前端流程包括政企登录、当前会话、组织人员与多任职、应用登记、角色资源与审计读取。新建人员需明确选择“创建新账号”或“关联已有账号”；关联已有账号不提交初始密码，也不修改共享账号姓名。界面的可编辑范围使用服务端 `editableTables`、`allowedAppIds`、`orgIds` 和显式 `scopeMode`。人员页在存在未完成开户或移除操作时显示恢复入口，继续处理只发送原请求号；读取错误保留原人员列表。

授权扩展、认证客户端、OIDC、MFA、公众注册、找回密码和同步任务仍显示“尚未开通”的业务状态，不再由浏览器定时器或固定验证码产生成功结果。应用登记不代表认证协议已开通。

## 数据延续与工程边界

原来的 `localStorage['iam.frontend.mock.v2']` 与 `sessionStorage['iam.frontend.session']` 均保留在浏览器内，运行期不读取其中的身份或权限。历史测试记录保留其原始数据与验收范围，不改写为服务器验收。

## 检查与验收

```bash
npm run tokens:check
npm run syntaxcheck
npm run audit:ui
npm run audit:typography
npm test
npm run typecheck
npm run build
npx playwright install chromium
npx playwright test tests/backend-p1.spec.ts
npm run test:typography
npm run test:compact
npm run test:e2e
```

默认 Playwright 套件使用测试层 HTTP 契约拦截请求，开发代理也固定指向不可用的本机端口；若有接口漏拦截，测试会失败而不会落到共享后端。`tests/e2e.spec.ts` 只做当前平台会话、身份域、注册提示及页面显示检查，不提交注册、授权、同步或其他业务写入。`IDAAS_LIVE_TEST=1` 才会收集真实后端用例；其中部分用例可能写入资料或触发下发，必须在明确的隔离环境中单独执行。默认契约测试与手动下发链路的真实端到端验收分别记录，不互相替代。同步对象页的 20/100 行请求数结果及既有任务创建 SQL 性能缺口见[本轮隔离验收](docs/手动下发隔离验收20261005.md)。

Windows可运行 `检查页面排版.cmd`，按顺序执行检查；任何一步失败即停止，不把未执行项目算作通过。

`test:typography` 对真实 React 工作区、全部12个应用页签、8种认证入口和重点浮层测量字号，输出每页截图和测量JSON。`test:compact` 另覆盖7种宽度、长字段及删除入口。最终仍需逐页人工视觉确认，不能只看“没有水平溢出”。

V2.2 排版修订时的实际结果、未执行项见 `docs/v2.2/验证与交付说明.md`；该记录中的依赖下载失败及未执行状态是历史交付结果。命名统一的范围与实际验证见 `docs/命名统一说明.md`；认证页舒展布局及 localhost / 3005 调整见 `docs/认证页面与本地启动调整.md`。源码语法、纯逻辑类型检查及独立HTML测量不能替代实际React验收。

## 可选视觉辅助

`preview/index.html` 是更新过字号的独立 HTML 辅助，可用浏览器直接打开；代表总览、用户、应用、授权、审计、设置、登录与3种浮层。它不加载React/Ant Design，不具有完整业务功能。

该辅助文件复用实际工程样式并单独桥接原生DOM；需要更新样式时运行 `node scripts/build-companion.mjs`。其Chromium结果在 `docs/v2.2/companion-browser-results.json`，截图在 `companion-screenshots/`，均明确属于独立HTML，不是实际React截图。

`docs/v1`、`docs/v2`、`docs/v2.1` 及 V2.2 原始验收记录是历史资料，不代表本次命名统一后的验证状态；不得把旧字号、旧截图和旧测试结论覆盖当前工程。历史日志中的旧 npm 包名、截图中的旧平台标题、原始方案文件名及清单路径用于追溯原交付，保留原始值。


## 历史交付与回退记录（不代表当前运行状态）

第一阶段新增 `tests/backend-adapter.test.ts` 和 `tests/backend-p1.spec.ts`，并将排版、紧凑浏览器检查适配为测试层 HTTP 契约数据；这些检查不替代实时数据库验收。真实环境检查使用 `scripts/verify-backend-live.mjs`，在已安装 Edge 的 Windows 环境执行，不重置用户的浏览器。

第一阶段共享后端已经在当前开发环境发布，保留 `baseline` / `baseline_ga_old` 绑定，实际新增 16 个 Magic HTTP 接口及 13 个函数。Java 框架已编译重启，本地后台为 `http://localhost:8088`，卓鉴入口为 `http://localhost:3005/#/login`。登录使用现有共享账号及有效公安成员资格，应用登记不等于已经实现跨系统单点登录。

数据库新增字段、11 张扩展表、Java 框架改动、资源刷新方法和 105 项真实 HTTP 验收见 [第一阶段后端实施说明](docs/backend-phase1-implementation.md)；界面及契约检查范围见 [第一阶段前端接入验证](docs/第一阶段前端接入验证.md)。

2026-09-28 曾实施**平台与租户两套登录域及 HTTP 下发**，历史实现见 [第二阶段接口下发实施说明](docs/第二阶段接口下发实施说明.md)。同日按用户新的安排撤回租户端改造：保留中央目录和独立平台会话，恢复原业务系统共享账号模型。平台专用操作账号实体的进一步拆分应仅在控制库中开发，其他租户适配暂缓。

### 2026-09-28 租户改造回退时的运行方式

卓鉴继续通过独立平台会话登录并维护控制库中央人员、机构目录。源数据管理平台恢复使用原控制库账号密码、原租户成员关系及各租户自己的机构、角色和菜单。前一轮生成的公安本地维护密码不再使用；卓鉴平台 token 与原业务 token 不能互换。

中央入口仍为“13.统一身份管理”。租户“09.系统管理 → 10.身份下发”和本地登录/管理接口已经撤下；公安前两阶段新增的 10 张表、8 个原表扩展字段及接收授权配置已备份后移除。实际下发、测试连接、任务创建和接入配置保存已暂停，旧安装及完整发布脚本禁止重新应用租户资源。

- [当前回退范围、实际验证与后续开发顺序](docs/租户改造回退与平台优先开发说明.md)
- [历史第二阶段实现](docs/第二阶段接口下发实施说明.md)
- [待后续接入采用的 IDAAS/1 标准](docs/IDAAS-1用户机构下发接口标准.md)
- [历史第二阶段验收](docs/第二阶段接口下发验证记录.md)

当前回退的真实接口检查使用 `data-elements/scripts/verify_idaas_tenant_rollback.py`。第一、第二阶段的浏览器契约和实时下发检查保留为历史资料，不作为当前回退验收，也不应为运行旧测试重新安装租户资源。
