# 万象数据治理中心（data-elements-wanxiang）

本文件是本工程唯一的持续维护说明。功能、接口、配置、部署和验证边界在对应章节原位更新；版本差异通过 Git 查看，不再新增按日期、版本或阶段命名的增量文档。工程 README 仅保留入口。

仓库规则以 [AGENTS.md](../AGENTS.md) 为准；数据库现行完整结构见 [db/](../db/README.md)。文中保留的设计背景与既往验证只说明当时范围，不代表新环境已验收，也不能覆盖现行租户与身份边界。历史截图、JSON 报告和旧版本评审材料不作为当前文档依赖。

## 目录

- [工程概览与使用](#overview)
- [万象数据治理中心 3.2 · 页面与视觉实现规范](#design-system)
- [万象数据质量模板与执行](#quality-templates)
- [万象数据治理 3.2.1 · 总览还原与数据库品牌图标](#guide-ed6a5583)

<a id="overview"></a>

## 工程概览与使用

<a id="overview--当前运行状态"></a>

### 当前运行状态

新版界面的主题、插画、导航和画布设计保留；治理总览、元数据管理、数据标准、数据质量的实际菜单已使用原平台共享后端。认证由元数据管理平台统一完成，接口异常或未登录不会回退成演示业务数据。

模型名称、业务域、选表范围和画布布局沿用旧 `wanxiang2` 的浏览器视图方案，按账号和租户隔离。真实数据源、表、字段、逻辑关系、标准引用、质检记录均来自共享服务；本地模型视图不是服务端数据模型库。

- 元数据、质量及中心功能的现行说明统一维护在本文，历史联调与验收记录归档到根 `logs/`。
- 原始 UI 评审材料属于历史版本，不适用于当前已接入菜单；设计规范在本文持续维护。


<a id="overview--本地启动"></a>

### 本地启动

使用 Node.js 22.12 或更高版本，在本应用目录安装依赖并启动：

```powershell
npm ci
npm run dev
```

依赖由万象自己的 `package.json` 和 `package-lock.json` 管理；维护与验收脚本也从本应用依赖中加载所需模块，无需安装其他前端工程。每次依赖声明变化后重新执行 `npm ci`。

开发入口为 `http://localhost:3010/`，元数据管理平台为 `http://localhost:3000/`，共享后端为 `http://localhost:8088/`。先在平台登录，再进入治理中心。不要直接双击源码 `index.html`。开发服务使用固定端口，不会静默换成其他端口。

配置项：

| 配置 | 用途 / 默认值 |
| --- | --- |
| `VITE_DATA_ELEMENTS_API_TARGET` | 开发代理的共享后端地址，默认 `http://localhost:8088` |
| `VITE_DATA_ELEMENTS_API_BASE_URL` | 浏览器接口前缀，开发默认 `/dev-api`，生产默认 `/prod-api` |
| `VITE_DATA_ELEMENTS_PLATFORM_URL` | 统一登录及返回平台地址，开发默认 `http://localhost:3000/#/`，生产默认 `/wanxiang/#/` |
| `VITE_WANXIANG_GOVERNANCE_URL` | 系统切换中的治理中心地址，生产默认 `/wanxiang-governance/` |

不要将账号、数据库密码、token 或 keytab 放入源码、构建环境文件或 URL。不同端口通过平台既有的 session bridge 同步登录态，生产同源部署复用平台存储。


<a id="overview--验证与构建"></a>

### 验证与构建

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

当前用户已确认账号体系仍在更新。不要通过修改认证或回退旧运行包恢复页面。历史验收曾记录前端自动化 300 项通过、后端针对性测试 13 项通过；这些数字不代表当前检出的测试状态。2026-10-05 补齐现有测试和发布脚本直接引用的 Magic API 源文件及数据库图标许可证后，生产构建通过；`npm run check` 的 317 项测试中 316 项通过、1 项失败。剩余失败是 `src/design/theme.ts` 与历史固定哈希不一致；原工程中的同一文件也是当前哈希，需核对视觉基线后处理。

生产带路径前缀构建示例（不等于部署）：

```powershell
$env:VITE_DATA_ELEMENTS_API_BASE_URL='/prod-api'
$env:VITE_DATA_ELEMENTS_PLATFORM_URL='/wanxiang/#/'
$env:VITE_WANXIANG_GOVERNANCE_URL='/governance/'
npx vite build --base=/governance/ --outDir dist-prefix-check
```

反向代理需配置相应静态目录、API 前缀和平台页面。现有大体积构建分片警告不影响构建成功，但正式发布前可继续进行按模块拆包优化。


<a id="overview--工程结构"></a>

### 工程结构

```text
src/app/                   统一认证、中心切换、个人信息、导航及全屏壳
src/features/platform/     已接入的管理页面、表单与操作
src/features/er/           真实元数据驱动的 ER 画布与关系操作
src/services/              共享接口 DTO、缓存、查询及本地视图适配
src/shared/                共享认证、请求、跨系统导航
src/assets/                用户已确认的本地插画、数据库原图
src/mocks/                 保留的历史预览/测试实现，不作为已接入菜单的数据源
backend/                   测试及发布脚本直接引用的 Magic API 源文件
scripts/                   检查、构建、真实接口验证及发布工具
tests/                     逻辑/契约测试和图形资产原样检查
../docs/data-elements-wanxiang.md  持续维护的工程说明
third-party/               数据库图标的来源与许可证
```

数据库图标来源和固定文件哈希见 `src/assets/databases/sources.json`，许可证见 `third-party/`。界面只使用本地图像，不请求外网图床。

<a id="design-system"></a>

## 万象数据治理中心 3.2 · 页面与视觉实现规范

> **3.2.1 定向覆盖：**「治理总览」页头使用3.1.0的简洁 `page-atmosphere` 结构，不显示三项亮点和右侧宣传短句；其他模块保留以下3.2规范。数据库品牌图标必须取自 `src/assets/databases/sources.json` 记录的上游原文件，不得再描摹类似标志。


目标：紧凑但清晰，紫色品牌贯穿，业务内容优先，装饰有差异但不喧宾夺主。


<a id="design-system--组件职责"></a>

### 组件职责

React/Ant Design用于业务控件。侧栏用原生ProductNavigation和19套ModuleIcon，避免inline-collapsed隐藏标题影响中心。React Flow仍管理全屏ER；不另外引入图引擎。每个Page按标题查询moduleVisuals，显式映射本地浅深SVG，不用字符串拼接构建未知资源。


<a id="design-system--层级"></a>

### 层级

页标题24/22px；区块16px；正文/菜单/表单14px；表格13—14px；辅助12px；微型徽标11px。字号不能用全局scale压缩。菜单图标22px、收起点击区44×42px。页头约170px，正文、三项亮点和主要按钮均为可操作DOM；插画无pointer事件。


<a id="design-system--资产"></a>

### 资产

总览为山形/环轨；来源为数据库汇聚；采集为结构传递；目录为卡片检索；模型/ER为结构关系；对标为字段对应；血缘为路径；标准/审核/落地为规则文档；代码/编码为标识；质量域使用探查、校验、编排、报告和闭环图形。浅深主题各自着色，不使用整页反色滤镜。

SourceIcon统一服务数据源页、目录、ER对象库与节点。8个来源使用不同的可辨识图形，未知来源回退通用数据库图形；不是只换背景色。


<a id="design-system--页面内容"></a>

### 页面内容

列表按“业务名称优先，技术代码次之”组织；UUID保留于详情或Tooltip，不作为主要阅读内容。业务状态、来源状态、采集状态不能混成同一种“正常”。有副作用动作需确认，主体操作突出，低频与危险动作进入更多菜单。

Mock只发生于业务服务层，页面不生成随机数字或假增长率。已有属性与本地业务记录优先；装饰图片不能隐含未实现的远程连接、数据扫描、认证或大模型能力。


<a id="design-system--主题与兼容"></a>

### 主题与兼容

默认浅色，记住主动选择。深色覆盖表头、固定列、抽屉、选择器和画布。小屏优先文字/动作，插画退为低透明背景；长表格内部滚动。ER始终全屏，不受外层治理框架挤压。

新样式位于src/styles/studio-polish.css，最后导入。不靠覆盖所有Ant组件的!important解决布局。数据存储键继续wanxiang:local-workspace:3.1；不得因为视觉升级清空用户本地记录。


<a id="design-system--验证边界"></a>

### 验证边界

当前165项逻辑/契约、23个纯TS入口、74个源码语法和384组源码DOM样式检查通过。完整组件运行与生产构建未完成，详见doc/02-测试报告.md。新增页面应同时扩展moduleVisuals、图形映射、真实运行验收与现有回归，不能只补一个漂亮截图。

<a id="quality-templates"></a>

## 万象数据质量模板与执行

<a id="quality-templates--菜单与能力"></a>

### 菜单与能力

| 需求 | 实现 |
| --- | --- |
| 完整性 | 非空、非空白内置模板；SQL条件可表达有条件必填。 |
| 唯一性 | 单字段及联合唯一内置模板，全表统计重复，不在分片内误判唯一。 |
| 一致性 | 同一登记数据源的跨表引用，支持联合键；SQL条件支持当前表两列比较。 |
| 准确性 | 数值范围、业务阈值与参数化条件；代表符合配置规则，不能替代外部权威事实核验。 |
| 规范性 | 枚举、长度、正则和邮箱/电话等预设；SQL条件补充文本规范。 |
| 模板管理 | 数据质量规则菜单提供内置模板及SQL模板分页、维度筛选、创建、修改、软删除。 |
| 任务执行 | 质检方案与任务选择模板及字段、保存、预检、手动/定时执行、取消、启停、历史报告与工单。 |

字段探查及探查报告沿用独立统计口径，不将字段统计自动当成质检评分。质量报告展示真实实例、规则指标与有限问题样例；样例数量不代表全部异常。


<a id="quality-templates--sql条件模板"></a>

### SQL条件模板

模板填写当前任务表上的异常条件，条件为真时计入异常。字段使用`${column}`、可选`${relatedColumn}`，值参数使用`#{名称}`，例如：

```sql
${column} < #{min} OR ${column} > #{max}
```

默认参数是JSON对象，如`{"min":0,"max":100}`。参数区分未配置、null、空字符串、0和false。字段引用由已授权登记字段构造，值由JDBC绑定。NULL比较结果不是true，必填要求应另配完整性规则。

支持比较、算术、AND/OR、IN、BETWEEN、CASE及COALESCE、NULLIF、TRIM、UPPER、LOWER、ABS、ROUND。不接受独立SELECT、子查询、任意表访问、DDL/DML、注释、语句分隔符或任意数据库函数。联合唯一和跨表关联使用内置规则。实际来源库的字段类型兼容由任务预检确认，不宣称任意原生SQL均能跨方言转换。


<a id="quality-templates--所有权与快照"></a>

### 所有权与快照

模板复用行业库`data_gov_quality_rule`，以`scope_of_application=QUALITY_SQL_TEMPLATE_V1`区别历史规则。`check_logic`保存版本、条件及默认参数JSON；名称、编码、维度、说明及审计沿用已有列，本次不变更物理结构。模板在当前认证租户内共享，归属和操作者由服务端解析。

任务保存一次读取全部去重模板ID，逐ID核对当前租户及有效模板标识。客户端只提交templateId、字段及值参数；正文、名称及维度从现用模板复制到`dq_quality_task_rule_t.parameters_json`。执行实例继续使用既有`task_snapshot`。模板修改、软删除不改变已有任务及历史报告；重新保存任务时须选择现用模板并重新预检。

管理和快照编排保留在Magic。Java新增`QualitySqlTemplate`只提供表达式解析、占位符处理和绑定值输出，现有JDBC内核增加适配；沿用聚合、样例限额、分片、超时与错误传播，不新增业务SQL语句或第二套执行引擎。


<a id="quality-templates--批量与发布"></a>

### 批量与发布

模板服务端分页排序，20/100行均为一次计数及一次分页查询，正文随有界页返回，不逐行补查。任务最多64条规则，模板先去重后一次读取。

引用字段由父表单汇总，每批最多50张表；每批一次归属读取并按角色逐ID校验，再一次字段读取。20/100项为1/2个请求及2/4次业务SQL，角色配置读取是每请求固定成本。空字段表仍须通过归属校验，不能用空列表掩盖越权或缺失ID。

先部署表达式技术模块和执行内核，再发布所选Magic资源。万象前端独立构建部署，不涉及共享低代码发布或数据库结构升级。

共享同一任务库的所有质量Worker必须同步部署支持SQL_TEMPLATE的后端版本；只升级Web入口不足以保证执行兼容。旧Worker仍可能领取新类型任务并在初始化时报告不支持的规则类型。部署验收应核对实例实际领取节点及其规则能力，并在全部Worker升级后验证任务执行。

<a id="guide-ed6a5583"></a>

## 万象数据治理 3.2.1 · 总览还原与数据库品牌图标

本次在 **3.2.0 纯前端工程**上完成两个定向修改。保留 Mock 服务、本地工作区、紧凑排版、默认浅色、深色切换、全屏 ER 和现有菜单。


<a id="guide-ed6a5583--1-只还原治理总览的顶部"></a>

### 1. 只还原「治理总览」的顶部

恢复 3.1.0 已确认的 `page-atmosphere` 头图结构与 CSS 渲染方式，直接使用上一版的浅色/深色 SVG 原文件；不是重新生成一张相似背景。两张原图与旧版的 SHA-256 一致，既有背景位置、缩放、透明度和高度均沿用旧样式。

顶部保留品牌小字、标题、一句说明和原主按钮：

> 治理总览
>
> 连接多源数据，建立可信标准，让每一项治理结果清晰可见。

取消总览顶部新加的三项亮点标签和右侧宣传短句。总览下方的指标、任务、图表、模型及交互保持 3.2.0。其他模块的特色插画、业务亮点和描述保持 3.2.0，不连带回退。


<a id="guide-ed6a5583--2-使用真实上游数据库图标"></a>

### 2. 使用真实上游数据库图标

| 数据库 | 采用的资源 |
|---|---|
| MySQL | Devicon 海豚 SVG 原文件 |
| PostgreSQL | DBeaver 大象图标 PNG 原文件 |
| 达梦 DM8 | DBeaver 达梦红蓝标识 PNG 原文件 |
| Oracle | DBeaver Oracle 红底白色 O 图标 PNG 原文件 |
| SQL Server | DBeaver SQL Server 网格标识 PNG 原文件 |
| MongoDB | Devicon 绿叶 SVG 原文件 |
| Elasticsearch | Devicon 彩色标识 SVG 原文件 |
| MariaDB | Devicon 海狮 SVG 原文件 |

8 个资源均校验 Git blob SHA-1，与对应上游返回的文件字节完全一致。没有描摹形状、重绘路径、改色或用 AI 生成类似图案；只是通过现有容器等比显示。上游固定为 Devicon `v2.17.0` 与 DBeaver `bd19f03ac85885192f793812de8bae85ede20bd5`。

通过统一 `SourceIcon` 覆盖数据源列表/筛选分布/详情/登记类型选择、元数据目录、ER 左侧来源库、ER 表节点和旧来源能力页。未知来源继续使用通用图标，不伪装成已有厂商。深色来源树保留浅色底板，防止原色深色图案融入背景；没有对品牌图片加滤镜。

资源存放于 `src/assets/databases/`，来源/哈希见 `sources.json`，许可说明见 `third-party/`。品牌商标归各自权利人所有；“主流图标”不代表厂商对本产品的背书。


<a id="guide-ed6a5583--3-没有改动的部分"></a>

### 3. 没有改动的部分

菜单及其居中/展开选中态、所有非总览模块文案和头图、数据源表格字段与操作、业务逻辑、Mock 初始化、存储键、备份格式、路由、全屏画布交互均保持最新 3.2.0。119 个非目标源文件已通过输入包 SHA-256 对比，36 个非总览隔离页面头部 DOM 与改造前相同。

只有业务运行源的 `common.tsx`（总览特例）、`Visuals.tsx`（图标资源）、`Capabilities.tsx`（统一来源图标）、`studio-polish.css`（ER 来源树深色底板）及数据库品牌资产发生定向修改。


<a id="guide-ed6a5583--4-使用与升级"></a>

### 4. 使用与升级

解压后工程根目录仍是 `data-elements-wanxiang`。只需 Node.js 22.12+，无需后端、Java 或数据库。

```bash
npm install
npm run dev
```

先导出旧工作区 JSON 备份。在同一浏览器、协议、主机和端口下继续读取 `wanxiang:local-workspace:3.1`，不会因本次更新重置业务数据。不要清除浏览器站点存储来升级。


<a id="guide-ed6a5583--5-验证与预览"></a>

### 5. 验证与预览

实际执行结果见 `02-测试报告.md`。`visual/3.2.1/` 为项目 TSX/CSS 与本地数据生成的隔离 DOM 浏览器截图，**不是完整 React/Ant Design 应用运行截图**；Hooks 与 Ant 控件采用明确的测试适配器。当前环境无法取得完整 npm 依赖，生产构建仍需在联网环境补验。
