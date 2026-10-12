# 低代码与 Magic 业务约束

本文件由根目录 `AGENTS.md` 按领域引用，保存现行业务契约与通用页面规则；运行环境、编译和发布步骤由个人 `data-elements-lowcode` skill 提供。Magic / 共享低代码唯一版本源分别为根目录 `magic/` 与 `lowcode/`，资源映射以各自 `.manifest.json` 为准。结构唯一版本源是 `db/`；执行输出只写 `logs/`。首次导出、差异核对及工具用法见 [本地资源版本管理](本地资源版本管理.md)。不能依赖旧工程路径、过程快照或历史发布器。

## 共享能力与数仓规划

四个前端独立安装和发布。涉及相同业务能力时复用当前后端、公共 Magic 契约、权威表及租户边界，不能复制业务主表或等价后端；不因这个要求强制抽取会话桥、导航、图标等共同前端包。运行配置中的能力入口若跳转到其他独立应用，应保持对象 ID 和当前认证租户上下文；同页跳转，URL 不携带 token/密码，生产地址从环境配置取得。认证或 API 失败不得默默退回 mock 数据。

数仓分层/分域共用低代码组件 `data-warehouse` 和 `/register/zl-sjzz/list`，契约为 `GET /dwm/center/layer-list`、`POST /dwm/center/datasource-options`、`POST /dwm/center/node/save`、`GET /sym/dict?code=dataSourceType`、`POST /sym/dictSaveOrUpdate` 和 `/sym/tenant/current`。层与逻辑域使用租户 `sym_dict_t`，绑定使用 `dwm_center_layer_source_t`，数据源主表为 `db_datasource_t`。这里的“分库”指逻辑分域，不能执行物理 CREATE DATABASE；无字典 ID 的 DST 组织汇总不作为可编辑分域。绑定将节点类型标为数据中心，不搬移物理数据，解绑不自动恢复历史节点类型。绑定成功不能充当真实连接测试，构建成功不能充当部署完成。

## Important Pages

Low-code component names:

- `register-db`: step 1, register data source.
- `register-dbTable`: step 2, explore/mark tables.
- `register-dbTable-catalog`: step 3 dictionary table registration and step 4 business/log table registration.
- `register-confirm`: step 5 registration review.
- `register-finish`: old completion page; the current flow generally stops on step 5.
- `db-table`: registered database detail, data table information tab.
- `my-db`: database list page.
- `my-app`: application-system list page.

Useful URLs:

- Database list: `http://localhost:3000/#/register/register-sjkb/list`

## Interface Map

Common metadata interfaces:

- `POST /dst/database/metadata/tables/reExplore`
  - Re-explore registered database tables and return differences: added, deleted, modified, unchanged.
  - Used by `db-table`.
  - Should not automatically write ES or asset tables.
- `POST /dst/database/metadata/tables/deleteAsset`
  - Soft-delete a registered table asset snapshot after re-explore finds the physical table was removed.
  - Deletes metadata snapshots only, not the physical database table.
- `POST /dst/database/metadata/table/sample-data`
  - Magic API sample-data endpoint used by preview dialogs; requires `tableId`.
- `POST /dst/database/metadata/test-connection`
  - Data source connection test.
- `POST /dst/application/save`
  - Save an application system directly to `sym_application_t` and `data_prop_t`.
- `POST /dst/database/saveOrUpdate`
  - Save a data source directly to `db_datasource_t` and `da_app_db_rela`; connection extensions belong in `db_datasource_t.pool_cfg`. Metadata summaries use the physical summary columns in `db_datasource_t`, not `pool_cfg` or `data_prop_t`.
- `POST /dst/database/assets/saveOrUpdate`
  - Compatibility endpoint for catalog assets. Data tables must use `/dst/database/table/*` and persist to `db_table_t` / `db_table_column_t`.

When testing locally, try the frontend proxy path first if the page uses `dev-api`; direct `8088` calls may require a fresh `token`.

## Low-Code Runtime Publishing and Registration Modal Rules

- Treat `baseline.ui_component_t.compile_js` and `compile_css` as the released runtime artifacts. Saving only `source_code` does not fix a browser page.
- Before accepting a low-code fix, inspect the target component through `POST /sym/component?action=list`. Its compiled JS must be non-empty and, for an SFC compiled by this project, contain a factory that ends by returning the component (normally `return __sfc__`).
- A registration dialog whose header, steps, and bottom actions render while one step is blank usually means that the dynamic step component's compiled artifact is stale or invalid. Inspect that named component directly; do not diagnose it only from the parent wrapper or static frontend source.
- First reconcile the local `lowcode/` source with the current control-database source; compile the confirmed local source and save source + compiled JS + compiled CSS together through `action=saveCode`, then call `/sym/component/cache/refresh`. Verify the refreshed component again through the list endpoint. Do not overwrite newer database source with an old local copy; compare source hashes before publication.
- The component-cache refresh must be usable by ordinary authorized users; do not add a separate platform-administrator gate to `/sym/component/cache/refresh` unless a future explicit authorization model requires it.
- `register-modal` is a static shell around dynamically registered low-code steps (`register-db`, `register-dbTable`, and subsequent registration pages). When it opens, ensure the runtime SFC bundle has been loaded with `loadAllRuntimeSfc(app)` before rendering the step, and surface a visible error if loading fails. Route guards alone are insufficient after a cache refresh, direct portal entry, or hot update.
- Keep the static registration components (`RegisterModal`, `AssetRegister`, `RegisterBody`, `RegisterAction`) registered in the application component entry and directly imported by `BaseLayout`; a Vue warning such as `Failed to resolve component: register-modal` is a blocking integration defect, not cosmetic logging.
- `useRegisterModal` must have a safe shared/fallback context for low-code pages that are evaluated outside the normal Vue injection tree. Opening registration must never silently create an empty dialog.
- Cache refresh normally removes the need for a backend restart. A restart is a recovery step only after verifying the saved runtime artifact and the two cache levels.

## Dynamic Forms (`form-create`) and Form Configuration

The frontend uses `@form-create/element-ui` to render configurable business forms. Treat this as a tenant-owned dynamic-form subsystem, distinct from the shared low-code component subsystem.

### Ownership, storage, and tenant boundary (mandatory)

- The form-configuration page is the released low-code component named `form-config`, available at `/setting/form-config`. It lists, edits, previews, creates, renames, and soft-deletes form definitions for the active environment.
- Dynamic form definitions are stored in **each selected tenant's business database**, in `sym_form`; they are not stored in `baseline.ui_component_t`, `baseline.api_file_t`, or another control-plane table. Resolve and record the active tenant/database before inspecting or changing a form.
- `sym_form` fields are: `tid`, `form_name`, `form_json`, `form_config`, `env`, `created_time`, `updated_time`, `is_del`, and `tenant_id`. `form_json` and `env` are required; deletion is logical (`is_del=1`). The delivered schema has no physical uniqueness key for `(form_name, env)`, so preserve the application's duplicate check and audit for duplicates when directly repairing data.
- All form read/write/delete requests use `POST /sym/form?action=get|save|delete`. The Magic resource is `/magic-api/api/09.系统管理/12.动态表单/01.动态表单配置.ms`; it delegates to the backend `tenantForm` Magic module.
- The backend module `TenantFormMagicModule` obtains the database only from `TenantContext.requireTenantId()` and `TenantDataSourceRegistry.dataSourceForTenant(...)`. Never add a request `tenantId`, route/query tenant value, form record's historic `tenant_id`, or browser-selected value as an alternate source of business-data ownership. A missing tenant context must fail closed.
- `tenant_id` is recorded from the active server-side tenant when a row is inserted. Do not let the client choose it. Preserve the current tenant's data-source routing even if the physical schema also contains `tenant_id`.

### Configuration contract

- The authoritative editable payload is `sym_form.form_json`. `form-config` currently stores an array-like JavaScript/form-create rule expression (for example `[ { type: "input", title: "名称", field: "name", ... } ]`) and renders it with `<form-create :rule="...">`. The administrative editor presently evaluates this expression in order to support the existing configuration format; do not silently change existing records to a different schema without a migration and backward-compatible reader.
- The visual `FcDesigner` screen is `data-elements-chengtian/src/views/lowcode/designer.vue`. Its designer payload is a different wrapper format: the outer `formJson` is `JSON.stringify(data)`, while `data.rule` and `data.options` are themselves serialized strings. When consuming or modifying designer-created forms, parse the outer JSON first, then parse `rule` and `options`; when saving, preserve that same nesting.
- `form_config` is optional auxiliary configuration. Preserve it on read/update unless the feature deliberately owns it; do not move rendering rules out of `form_json` merely because the column exists.
- Every data-bearing rule needs a stable, non-empty `field`. Use a unique field name within the form. Layout/display-only rules (such as `UTitle`) may omit `field`; they must not be included in persistence payloads.
- Keep form-create rule data serializable. Use the project helpers `FormUtils.toJson` / `FormUtils.parseJson` where functions or form-create's special JSON encoding are involved; do not replace them with plain `JSON.stringify` for rules that contain callbacks. Retain the project's registered effects/components from `data-elements-chengtian/src/plugins/formCreate.ts` and the custom rules/components they depend on.
- The shared renderer is `data-elements-chengtian/src/components/form/json-form.vue`. It clones incoming rules, supports preview/read-only mode, and exposes validation/value methods. Reuse it for normal business-form rendering rather than reimplementing rule parsing, save-field filtering, or validation in each page.
- `json-form` considers fields with `model === "0"` or `0` to be dynamic fields. Its `get2SaveData()` separates ordinary `baseData` from `dynamicData`; use that split when the receiving interface distinguishes master columns from dynamic properties. `getSaveData()` excludes `ignore` rules and normalizes saveable blank values to empty strings by default.
- Retain the current presentation defaults unless a product requirement says otherwise: no built-in submit/reset buttons, right-aligned `180px` labels, no label colon, and `FormUtils.fixJson` defaults for placeholders, clearability, full-row label width, dates, and number controls.

### API and editing rules

- `get` accepts optional `tid`, `formName`, and `env` filters and returns rows from the current tenant only. Pass the correct `env`: ordinary runtime helpers use `$setting.systemCode`; the designer uses its route `pid`; the configuration page uses its selected current environment. Do not assume a same-named form in another environment is the intended definition.
- `save` requires `formName` and `formJson`, and creates a new row only when `tid` is absent. On create, the backend rejects an existing active row with the same `(form_name, env)` in the active tenant. On update, send the complete persisted payload—at minimum `tid`, `formName`, `formJson`, and `env`, plus `formConfig` when it is owned—because the backend replaces those values rather than performing a partial patch.
- In particular, do not implement a rename request that sends only `tid` and `formName`: the current save contract requires and writes `formJson`, so load/preserve the current configuration before renaming. Likewise, do not write an empty `formConfig` or change `env` incidentally during a rule-only update.
- `delete` requires `tid` and performs a soft delete. Before adding or using deletion in a business flow, find every form lookup/caller by its `formName` and environment; deleting an active form can make the target dynamic page unusable.
- Validate syntax before saving and display a clear configuration error instead of persisting an unparsable rule expression. Preserve unsaved-change confirmation when switching forms, changing environment, or navigating away from the configuration page.
- Do not use `eval` on new, arbitrary user-generated content outside the existing compatibility boundary. If the format is modernized, introduce an explicit safe parser/validator and a migration plan rather than broadening executable configuration privileges.

### Change and validation workflow

1. Identify the target form name, `env`, active tenant, and tenant database. Query only the tenant `sym_form` row(s), including `tid`, `form_json`, `form_config`, `is_del`, and timestamps; do not confuse it with the shared `form-config` page source in `baseline.ui_component_t`.
2. Back up the exact form record before a persistent tenant-database change. For page code changes, first compare the current `form-config` runtime source/artifacts against its canonical `lowcode/` file and reconcile any changes according to the local-first publishing workflow.
3. Keep the existing storage format for the selected form. Verify that every form-create `type` is registered/importable in the runtime, custom effect names exist in `formCreate.ts`, each persisted data field is unique, and rule references/slots still match the consuming page.
4. Test with a real authenticated session in the intended tenant: load `POST /sym/form?action=get` with the target `env`, render the actual consuming page, populate values, validate required fields, verify preview/read-only behavior when applicable, and inspect the API payload (including `baseData`/`dynamicData` where used). Repeat after switching to another tenant to prove definitions neither leak nor overwrite across tenant databases.
5. If the changed artifact is the shared `/setting/form-config` UI itself, publish `source_code`, `compile_js`, and `compile_css` together to `baseline.ui_component_t`, invoke `/sym/component/cache/refresh`, and re-open the page. A `sym_form` data-only update does not require component-cache refresh or a backend restart.

### Dynamic detail-page completeness and save performance (mandatory)

- For a page rendered through `base-detail` / `json-form` (for example `/register/register-sjkb/detail`), `sym_form.form_json` is the source of truth for every user-visible form element: data fields, display order, sections, titles, labels, required/visible/read-only rules, selector choices, icons, and title-toolbar actions. Do not add page-specific controls or labels in the consuming low-code component merely because a particular detail form currently needs them.
- Keep shared component code declarative and generic. It may render a `UTitle` rule's configured `props.actions`, dispatch standard editing actions, resolve documented placeholders, and render configured control props; it must not branch on a business title such as `基本信息`, form name, database type, or page route in order to inject business UI.
- Configure `UTitle` toolbar actions in `props.actions`. An action has at least `key`, `label`, `visibleWhen` (`view`, `editing`, or `always`), and optional `icon`, Element button props, `requiresEditable`, `successMessage`, and `request`. Standard keys are `edit`, `cancel`, and `save`; nonstandard actions supply `request: { method, url, payload }`. Supported request placeholders must be explicit and stable (currently `$tid` and `$detailClass`); add a generic resolver instead of hardcoding an action per page.
- A control whose options are business choices must obtain those choices from its form rule props. In particular, the `db-type-select` rule carries `props.options` with group label/name and option `{ label, value, icon }`; database-type names and icons are not to be maintained as a second hardcoded page list. A legacy fallback may remain only to render older records while their `sym_form` rows are migrated.
- 数据源登记状态以 `db_datasource_t.asset_status` 为准：`0` 为“待登记”（尚未结束登记），`1` 为“未登记完成”（已结束登记，但仍有有效表状态不为 `2`），`2` 为“已完成”（全部有效表状态为 `2`）。第五步允许结束部分登记，服务端汇总当前租户、当前数据源下 `is_del=0` 的全部表决定状态，不信任客户端传入的完成状态；空表清单保留为 `1`。暂不处理、过程表、备份表等如果仍处于表状态 `0`，也不能把数据源算为全部完成。数据源状态 `1` 不代表审批中，不复用目录审批状态字典。
- 数据源管理卡片的“字典表／业务表／日志表”数量仅统计当前租户、可见数据源下 `is_del=0` 且 `db_table_t.asset_status > 0` 的对应类型表，状态 `1`、`2` 均计入，`0`、NULL、负值不计入。主表批量聚合为权威来源，不能沿用 ES 残留数量；前端备用统计保持同一规则。“数据表总数／已标注／待标注”仍使用探查与标注范围，不因本规则排除未登记表；数据源本身是否结束登记不影响管理卡片这三类数量。
- 海通来源节点的 `incrementalColumn` 是持久化的流程配置，不是随启动或停止重置的运行水位。只有新建模板时根据来源表登记的抽取时间初始化一次，不按主键或字段名猜测。打开已有画布、来源配置面板、保存和启动均不异步重新读取登记配置或自动补齐历史任务；历史任务缺失字段由用户手工填写。保存及启动只校验当前配置，不能用登记值覆盖手工选择；缺少必填主增量字段时明确提示并拒绝增量操作，全量同步不依赖该字段。停止、监控刷新和保存回读必须保持已保存的字段及完整设计配置，服务端返回空值或不同字段时不能静默覆盖当前画布。
- 表保存、重新标注、驳回、删除快照、模板导入及探查新增表后同步数据源状态；未结束登记的 `0` 不因单表保存自动升为 `1/2`。数据接入树、列表、统计及任务创建采用相同范围：`db_datasource_t.asset_status > 0` 且 `db_table_t.asset_status > 0`，并保留有效记录、业务表/日志表/字典表、当前租户和角色数据范围条件。状态 `0` 或 NULL 不计入接入候选；状态 `1` 的表也可接入，但数据源“全部登记完成”的聚合仍要求所有有效表状态为 `2`。推送契约仍只发布状态 `2` 的表，其发布范围独立于接入候选。接入任务展开区按真实任务来源展示“来源库／来源表 → 目标库／目标表”的数据链路，级联任务不能用根来源替代上一跳，历史多目标任务须展示全部已授权目标；所需元数据随分页接口批量返回，不在展开时逐任务补查。状态聚合每批最多 `100` 个数据源，在集合外一次分组查询，不逐表/逐数据源补查。
- The `是否可连通` field binds to `connectionStatus` / `connection_status`, is display-only in the detail form, and its result may be updated only by an actual connection test or the deliberate removal of connection information. Enforce this in the save API as well as by disabling the form control.
- Before changing a detail page, audit the complete runtime path: the consuming component, `base-detail`, `json-form`, all custom rule components, and the selected tenant's `sym_form` row. Move any discovered page-specific visual element or behavior into the applicable form configuration, unless it is a truly generic renderer capability or an authorization/data-integrity rule that must stay server-side.
- Saving the authoritative tenant database record must complete before success is returned. Non-authoritative search/index projection is best effort and must not hold the edit dialog open: request `deferIndexRefresh` for database-detail saves, return `indexRefreshPending: true` from the save API when accepted, then schedule `/dst/maintenance/refresh` after the UI has returned to view mode. Preserve error logging and reconciliation capability; a background index failure must not make a successfully persisted form save appear to fail.
- Validate a dynamic-detail change in an authenticated intended tenant: confirm `/sym/form?action=get` returns the configured actions/options, verify no duplicate hardcoded buttons/options remain, open edit mode, check type icons in both selector and read-only display, save a representative record, and measure that user-visible completion no longer waits on search-index refresh. Repeat against a second tenant where that form exists to prove tenant isolation.

## Data-Access Page Interaction and Responsive Layout Rules

- On the data-convergence / task-management page, implement confirmation dialogs with imported `ElMessageBox.confirm`; do not assume that a dynamically evaluated low-code component has an injected global `$confirm`.
- A reject-for-modification action must send the real table identifier (`sourceTableId`, then `tid`, then `tableId`) and datasource identifier to `/ods/dataAggReject`. It returns the table to re-registration/rejected state; it must not delete the datasource.
- Magic API maintenance can leave duplicate rows for the same HTTP method/path. Before relying on or changing a route, test the active runtime route with a harmless missing-parameter request, inspect the duplicate rows, and retain exactly one clearly active implementation after a reference audit. Do not delete legacy rows blindly.
- Split-pane inventory and data-access pages must not add a page-level horizontal scrollbar. Give outer panes and right panels `min-width: 0` with controlled `overflow: hidden`; keep any necessary table scrolling inside the table only. Toolbars and filters must wrap, and keyword inputs should use responsive widths rather than forcing a fixed wide layout.

## Table Toolbar Conventions

- For management-list tables, place the primary creation action at the left of the top toolbar. Use a plus icon and a specific label such as `新增租户`, `新增角色`, or `新增用户`; do not leave the action as an ambiguous `新增`.
- Place keyword filters/search controls at the right of the same toolbar. The search input should contain a blue, clickable magnifier at its right edge, invoke the query on click and Enter, and retain a clear action that restores the full list.
- Do not add a separate refresh button by default. Add one only when the user explicitly needs an operation that cannot be represented by search, clear, pagination reload, or the normal post-save refresh.
- Keep the toolbar responsive: preserve the create action and search as distinct controls, allow them to wrap on narrow screens, and avoid forcing a page-level horizontal scrollbar.

## Standard Split-Tree Management List (`my-db` Template)

Use the released `my-db` page at `/register/register-sjkb/list` as the default visual and structural template for inventory lists that have an organization tree at left and a paged management table at right (for example, `my-catalog`). Keep domain fields, APIs, and operations specific to the target page; reuse the layout contract rather than copying data-source behavior.

- Use `el-splitter` with a `300px` default left `el-splitter-panel` for administrators. The left tree is `UTree`, with its built-in `search`, `search-placeholder`, and `loading` support; do not add a second manually managed tree-search input unless the target has a distinct filtering need. Expand only the initial organization path (`全部 → 第一层机构 → 内设机构`) on first load; leave all lower-level departments collapsed.
- A left-tree or left-classification header's add action is a compact icon action, separate from the main-list primary creation button. Match the data-standard classification reference: `<el-button type="text" class="left-header-btn"><Icon icon="el-icon-plus" /></el-button>`, with `padding: 4px 8px`, `color: var(--el-color-primary)`, and hover `background-color: #1890ff1a`. Do not substitute a filled/circular primary button or a neutral custom icon treatment. Keep its title and accessible name specific to the object being created.
- Use `card-container ... w-full h-full` plus `datasource-explorer-page` / `datasource-explorer-splitter` styles. The page root, splitter panels, and right panel need `min-width: 0`, `min-height: 0`, and controlled `overflow: hidden`; tables may scroll internally but the document must not gain a horizontal scrollbar.
- The left panel is a column: title/header first, then the custom tree fills the remaining height. Preserve tree label ellipsis and its right-side breathing space. Do not set an arbitrary persistent width on the tree itself.
- The right panel is a flex column. Its toolbar is a `list-toolbar`: primary creation action left-aligned; `list-filters` right-aligned and content-sized rather than stretched to fill the row. Give the right panel an inline-size container and let select/search controls grow within readable ranges rather than locking them to a small fixed width: ordinary selects `120–160px`, status selects `108–144px`, and keyword inputs `210–360px`. This preserves a compact row in a constrained panel while allowing long placeholders to become fully readable on wide screens. Below the narrow breakpoint filters may wrap, with selects sharing a row and the keyword field using a full row. Use concise product labels such as `登记目录` instead of `登记数据目录` when the page context already identifies the object.
- The keyword input is clearable, searches on Enter and on a blue clickable magnifier, and restores the full list on clear. Reuse the project `data-table`; do not introduce a standalone table/pager pair. Default to the shared page size unless the product requirement explicitly needs another size.
- Pagination comes only from `data-table` → shared `Pagination`. The shared component must not wrap pagination with `el-scrollbar`; use a compact `4px` top spacing in both the shared pagination and table wrapper. In a constrained right panel, use a container-query rule to hide the total-record text and jump control below `680px`, then page-size control below `480px`; keep previous/next, page numbers, and page size when space allows. Do not clip pagination or create a page-level horizontal scrollbar.
- Table alignment follows the Ant Design data-format convention: ordinary text headers and values (including English and Chinese names) are left-aligned; explicit state/tag columns may remain centered; operation values are right-aligned while the `操作` header is centered. Numeric columns declare `type: "num"`, have a fixed `precision` for the column (default `0`), use thousands separators by default, put any unit in the header, and align both header and values right. Use `--` for empty numeric values. Do not append a repeated unit such as `张` to every numeric table cell.
- Operation columns are fixed right, right-aligned, and compact. Do not reserve a large trailing gap after the final action; the right edge padding should match the table's normal cell padding. When every row has exactly one short action (for example, `创建任务` or `任务设置`), use a narrow column and center that one action so its left and right whitespace are visually symmetric; do not retain a wide right-aligned action column that leaves a large blank area on the left.
- Validate at the target desktop viewport: left panel is 300px, toolbar controls share one row, `document.documentElement.scrollWidth === clientWidth`, the page root has no horizontal overflow, and the pagination element has no horizontal overflow. Refresh the low-code component cache after publishing source, compiled JS, and compiled CSS.

## Huawei MRS Hive / Kerberos Configuration Rules

- Do not store a keytab in the repository, low-code source, Elasticsearch, or database configuration. Put it in a server-only restricted secrets directory; datasource configuration stores only paths and non-secret connection settings.
- The supplied `krb5.conf` and `user.keytab` identify the realm/KDC and client principal, but they do **not** supply the HiveServer2/HA ZooKeeper endpoint, Hive service principal, or target Hive database. Do not claim that two file paths alone are sufficient for a connection.
- Prefer a concise standard setup: `keytab` path, `krb5.conf` path, MRS client configuration directory or `hiveclient.properties` path, and Hive database (default allowed). Read endpoint/service-discovery values from the MRS client configuration; expose advanced manual overrides only when necessary.
- Validate Kerberos/Hive on the host that will run the service or NiFi process, because local desktop paths and credentials are not portable to a server.

## Magic API Organization

- Public route roots are fixed by domain:
  - `/dst`: data inventory. Its direct children are `/application`, `/database`, and `/catalog`.
  - `/ods`: data access, ETL, and NiFi orchestration.
  - `/dwm`: data governance.
  - `/dws`: data services and cloud-gateway operations.
  - `/das`: data applications, application repository, screen designer, and application-tab operations.
  - `/idaas`: unified identity management and its explicitly configured tenant-receiver interfaces; platform and tenant-local accounts remain separate.
  - `/sym` and `/portal`: the existing tenant system-management and portal routes. Preserve their deployed paths when improving Magic editor names or documentation.
- A Magic group metadata `path` must contain one URL segment only. Compose deeper URLs through nested groups; never put `dst/database` or another multi-segment value in one group.
- Number sibling API groups, APIs, functions, and tasks with two digits: `01.`, `02.`, `03.`. Do not use unnumbered names, `1.1`, `未定义名称`, `test`, or copied names in active resources.
- Physical `file_path` numbering is for readable ordering. Runtime imports and routes come from group/API metadata `path`; do not rewrite imports merely because a display/file name gained a numeric prefix.
- Direct database moves must preserve the complete resource tree:
  - Every group has one `group.json`.
  - Every physical folder has one active directory row ending in `/` with `file_content='this is directory'`.
  - This applies to `api`, `function`, and `task`. Missing function/task directory rows can leave scripts visible in the database while cold-start imports fail with `找不到函数`.
- After folder or numbering changes, restart the backend and inspect cold-start `MagicApiRuntimeProbe`: `rootFunctionDirs`, `rootTaskDirs`, and `missingFileGroups=[]`. A hot registry is not sufficient evidence.
- Before deleting an interface, cross-check low-code `source_code` and `compile_js`, the four current frontend `src` trees (`data-elements-chengtian`, `data-elements-haitong`, `data-elements-idaas`, `data-elements-wanxiang`) plus known external callbacks, Magic imports, Java callers, scheduled tasks, and known external callbacks. Referenced but empty/broken interfaces must be implemented, not deleted.
- NiFi clients and cloud-gateway signing/token/HTTP clients may remain Java runtime adapters. Parameter validation, orchestration, persistence, and response shaping belong in Magic API.
- Shared inventory aggregations such as `/dst/statistics/common` belong in the dedicated `02.数据盘点/04.盘点统计` group. Do not move a shared endpoint into one page domain merely because one caller is being debugged; verify every low-code caller first.
- Personal-center Magic resources belong under `01.平台门户/07.个人中心`; their nested public route is `/portal/account`.
- Data-application Magic resources belong under `07.数据应用` and use `/das` as the public root. Do not retain `/workshop` calls in low-code source/compiled JS or any frontend project.

### Magic API readability and documentation

- Keep public interface URLs and function import paths in their existing English form. Give each active interface and function a clear Chinese display name. Within each sibling group, use consecutive two-digit display numbers starting at `01`; renumber the remaining siblings after moving or retiring a resource. Keep the physical file name in sync with its display name while preserving the Magic metadata `id`, route `path`, method, and group identity.
- The Magic editor `description` is a business explanation, not a repeat of the name or an English route. State what the interface reads or changes, key input and validation rules, authentication and tenant/application scope, important persistence or downstream effects, and the returned result or meaningful error. Describe unfinished compatibility endpoints truthfully as unfinished. Give shared functions equally specific descriptions and readable parameter descriptions.
- Put an interface-level comment at the start of every script with its purpose, HTTP route, and business flow. Add concise Chinese comments beside decisions that are easy to misunderstand: scope checks, important branches, version/transaction handling, data synchronization, and writes. Format MagicScript consistently so the flow is readable in the visual editor. Do not move endpoint-specific business logic into a one-caller function merely to shorten the endpoint; retain functions for rules actually reused across interfaces or required shared framework boundaries.
- Format complete static SQL as MagicScript's SQL-aware multiline literal `"""..."""` when supported by the installed runtime/editor, with readable clauses and indentation. Preserve Magic binding expressions (`#{...}`), identifier/alias case, quoted values, and SQL semantics exactly; those can affect parameter binding and result keys. Keep dynamically assembled SQL fragments in their necessary string form and validate identifiers with a whitelist. Do not mechanically convert all strings or uppercase SQL tokens.
- Before bulk editing, inventory active `api_file_t` rows and back up the exact affected rows. Match resources by the Magic metadata `id` when unique; if legacy rows share an ID or have no row `tid`, distinguish them by their exact `file_path` and content, and report editor-invisible orphan resources separately. Check for duplicate paths and preserve the JSON/`================================`/script format plus directory and `group.json` records. After publication, refresh or cold-load the Magic registry, inspect representative scripts in the editor, and test real read and write routes for each affected business area. A database-only comparison or unrefreshed test does not prove the new script runs.

## Magic API Metadata Management

- The Magic API folder `/magic-api/api/02.数据盘点/02.数据库表/01.元数据管理/` is the target home for metadata operations that were formerly implemented in Java. Do not assume an interface is unused just because the current script is empty, `null`, or only calls `MetadataAssetService`.
- For 06 metadata interfaces, classify each file before deleting or rewriting:
  - **Useful and implemented**: keep, verify with an authenticated HTTP request, and preserve route compatibility.
  - **Useful but only a Java wrapper/empty shell**: implement the Magic script instead of deleting it.
  - **Truly unused**: delete only after checking frontend calls, Java controller/service calls, Magic resource tree, and related workflow usage.
- Prefer Magic scripts that make the data flow explicit: load assets from ES with database fallback, build `DataSourceConfig`, call low-level metadata utilities such as `MetadataExplorerService` / `MetadataDataQueryService`, then write asset tables and ES only where the endpoint semantics require persistence.
- Add a short header comment to each metadata Magic script explaining purpose, read/write behavior, core tables touched, and safe validation mode. For write-heavy interfaces, support `dryRun=true` when practical so validation can avoid dirtying asset data.
- Use explanatory comments for purpose and data flow, but do not leave commented-out legacy Java code, old test branches, disabled imports, or stale implementation blocks in scripts.
- Do not leave stale response samples that imply broken behavior, huge historical payloads, or `literal cannot be used alone null` failures after fixing an interface.
- Updating `baseline.api_file_t` directly is not enough if the backend is already running. Use Magic editor save endpoint `POST /api/web/resource/file/api/save` with the parsed JSON metadata plus `script` field, or restart the backend, then verify the business route.
- After moving or bulk-updating Magic files, query each active API with `GET /api/web/resource/file/{metadata.id}` and verify `data.script` is not null. A row may contain code after the `================================` separator while the running Magic registry still has a null script. Re-save those files through `/api/web/resource/file/api/save`, then verify again.
- The identifier used by Magic editor detail/save APIs is the JSON metadata `id`, which can differ from `api_file_t.tid`. Do not assume the table row ID is the runtime API ID.
- Every Magic directory needs one active directory row whose `file_path` ends with `/` and whose `file_content` is exactly `this is directory`. Keep one `group.json` and one script row per path; audit duplicates after direct database maintenance.
- When moving a Magic group, search all Magic scripts for internal imports such as `@post:/old/path` and update them together with frontend calls. A valid group tree can still fail at runtime if cross-script imports retain the old route.
- For destructive or persistent endpoints, verify non-destructively first: use missing-parameter tests, invalid IDs, or `dryRun=true`; do not create/drop/delete real assets unless the user explicitly asks or the workflow requires it.

## Project-Specific Rules

- Avoid automatic save/submit behavior unless the user explicitly asks for it; prefer explicit Save/Next/Delete clicks.
- Asset registration has no approval workflow:
  - Do not expose or call an asset `submit` endpoint.
  - 应用、已完成登记的数据表及无需审批的目录保存为直接可用（`asset_status=2`、`flow_status=2`、无 `flow_order_id`）。数据源使用上述 `0/1/2` 登记进度，结束登记后 `flow_status=2`，无需审批。
  - Do not create legacy order-asset relation rows for `app`, `db`, `table`, or `catalog`.
  - Registration steps after the first data-source step show only Previous/Next; they must not auto-save or auto-submit.
- The standalone approval center remains available independently of asset registration:
  - Keep the Warm-Flow dependencies, `com.linewell.dataelement.flow` runtime package, approval controller/service, flow suggestions, and flow relation infrastructure.
  - Its Magic tree is `/magic-api/api/09.系统管理/08.审批中心/`; public routes start with `/sym/approval`.
  - Warm-Flow completion uses the current `/sym/approval/callback/finshHandle` Magic contract. Permission resolution uses `ApprovalPrincipalService`; unused legacy permission/query wrappers are not restored.
  - Do not reconnect `register-db`, `register-dbTable`, catalog registration, or registration completion to automatic approval submission.
  - Associate current approval operations through the authoritative `data_apply_form_t` / `res_catalog_publish_request` business ID and Warm-Flow `flow_instance.business_id`, not a second asset-order relation table. Load the business record with an explicit current-tenant predicate, resolve applicant/organization and display information in one bounded query, and check initiator ownership or current-tenant organization membership. The subscription and publication approval types must match their actual business records; asset registration must not be reconnected to the retired `checkIn` / `assetUpdate` path. Keep current subscription, publication, task handling and callback contracts.
- Application systems use `sym_application_t` as their only master table. Data sources use `db_datasource_t` as their only master table. Preserve migrated IDs in each table's `tid`; do not recreate app/db rows in `da_asset_t`.
- Data tables use `db_table_t` as their only master table and `db_table_column_t.table_id` for field metadata. Preserve migrated table IDs in `db_table_t.tid`; do not create table rows in `da_asset_t`, `da_asset_table_column_t`, or `data_prop_t`.
- Every data-table property used by registration, governance, preview, resource-center, or indexing logic must be represented by a physical `db_table_t` / `db_table_column_t` column. Do not add `data_type='table'` dynamic properties.
- Data-source connection options and migration extensions belong in the valid JSON object stored in `db_datasource_t.pool_cfg`. Data-source metadata summaries belong in the physical `db_datasource_t` summary columns; data-source interfaces must not read or write `data_prop_t` for these values.
- 数据源登记的显式“保存”提交主记录后，对提供连接的数据库刷新一次概览，更新版本、表/视图/过程/用户数量和容量；不隐式全量登记表或逐表补查。远程采集失败保留已完成的保存并提示可重试；未提供连接的手工快照不执行物理探查。详情显式编辑保存遵循同一规则。
- 库详情旧表单的 `procCount` 显示字段映射到现行 `procedureCount`；旧 `totalData` 概览项显示现行 `totalSize`，标题为“总占用空间”，不能加“条”后缀或把字节容量当作全库记录总数。展示适配不改写租户原始动态表单。
- 数据表与数据源详情共用 `detail-page` / `base-detail`，分别使用当前租户 `sym_form` 的“表详情”与“库详情”。表详情的“刷新元数据”只探查当前已授权表；`GET /dst/database/metadata/refreshDbInfo` 的 `tid` 始终为数据源 ID，可选 `tableId` 必须属于该数据源、当前租户及角色范围，`dryRun=true` 不写入。表统计保存到 `db_table_t.record_count/field_count/storage_size/total_size_bytes/total_size_formatted/ddl_create_time/ddl_update_time`，不覆盖 `db_table_column_t` 字段治理设置；不支持的来源指标保持空值，零值不能回退到旧 ES 统计。详情“元数据更新时间”使用资产 `updated_time`，不能称为来源库最后数据变更时间；搜索索引只是展示投影，重新读取详情应以业务主表为准。
- Application systems and data sources keep `sym_application_t` and `db_datasource_t` as their authoritative master tables. Their high-frequency list/search interfaces read the denormalized Elasticsearch index for speed; in this environment the configured physical index is `elements_dataassets` (`configData().es_dataassets`), while `EsCommonService` receives the logical name `dataassets` and adds the `elements_` prefix. Detail interfaces may fall back to MySQL when an index document is absent.
- Ordinary application-system and data-source save/delete interfaces update MySQL first and then update or remove the matching Elasticsearch document. Dynamic-detail saves that explicitly request `deferIndexRefresh` follow the validated best-effort asynchronous projection contract above and return `indexRefreshPending: true`; do not make a completed authoritative save appear failed because indexing is slow. Use `POST /dst/maintenance/refresh` for one asset and `POST /dst/maintenance/batchRefresh` for reconciliation or full index rebuilds.
- Keep indexing logic in the `/dst/maintenance` implementations. The unused `/dataassets/manage/assets/refresh` and `/batchRefresh` aliases have been retired; do not recreate them without a confirmed current caller. The service builder still uses `/dataassets/manage/assets/page`.
- Never treat Elasticsearch as the source of truth for application systems or data sources. Index refresh must rebuild app documents from `sym_application_t`, db documents from `db_datasource_t` plus `pool_cfg`, and remove stale app/db documents that no longer exist in the master tables.
- Elasticsearch documents are search projections, not storage for full configuration. Keep datasource credentials and complex `pool_cfg` objects out of ES, convert Hutool `JSONNull` values to real nulls or strings before indexing, and exclude large governance payloads such as `fieldGovernanceConfig`, dictionary profiles, and column arrays. Full values remain in MySQL.
- For `showConnect=0`, persist the data-source master record but clear direct connection columns and connection keys in `pool_cfg` so credentials cannot reappear in detail views.
- Low-code pages should use existing project table components where practical; avoid inventing unrelated table systems.
- Organization hierarchy edits use the tenant database's `rm_org_t`, not the control database. When a user may change `parent_id` from an organization form, enforce the move in the save API rather than only in the low-code page: reject modification of the virtual `ROOT` row, self-parenting, missing parents, and descendant-parent cycles; then update the moved row's `parent_id` and `org_path` and rewrite the old path prefix for every active descendant. Keep the hierarchy's `serial_number` prefix consistent for the moved subtree as well, because organization-scope list queries use it. Do not split a parent change into a UI-only update or two independently successful save requests.
- Keep UI language consistent:
  - `字典对标` is now `字典翻译`.
  - `业务对标` is now `统一格式`.
  - `表数据查看` should be `表数据预览`.
- 字段登记的统一格式 `DATE`（日期）/`DATETIME`（时间）必须进入 ODS 物化字段、DDL 与接入映射：日期使用目标库的 `DATE`；时间按目标方言使用 Oracle/达梦/OceanBase Oracle 的 `DATE`、PostgreSQL/人大金仓/Hive 等的 `TIMESTAMP`、MySQL/OceanBase MySQL 的 `DATETIME`、SQL Server 的 `DATETIME2`。多选目标库时分别生成类型，DDL 预览与提交元数据使用同一份目标字段。来源物理字段类型保持不变，字符型日期/时间由生成的字段映射显式转换，空字符串转 NULL；转换规则同时保存在画布 DSL 与字段映射记录中。仅采用已保存的登记配置，尊重显式清空，不从字段名猜测时间格式，也不自动改造已经存在的目标物理表。
- 物化建表多选 ODS 目标库时，默认勾选、建表预览、物化提交和后台任务创建统一遵循资源中心 ODS 分域的配置顺序（`sym_dict_t.sort_no`，同序号按分域 ID 稳定排序），不能按复选框点击顺序、传入数组顺序或物化表创建时间决定链路。配置为 `huiju` 在 `yuanshi` 前时，生成原来源表→汇聚库、汇聚库→原始库的独立级联任务；管理员调整分域位置后，新建任务采用新的顺序。仅处理所选且已逐 ID 校验租户、角色范围与来源关联的目标，后台一次批量读取有效分域绑定；非 ODS 分发目标保留显式选择顺序。每个任务和画布只包含一个目标库，任务记录的来源表/来源库必须是该跳的真实来源；在原来源表的展开列表汇总全部任务。后续跳直接映射前一跳物化字段，保留 `ODS_UUID`、`ODS_RKSJ`、`ODS_GXSJ` 和翻译结果，不重复生成主键或重复治理。重复提交按真实来源与目标复用任务；历史多目标画布只在明确确认后重建，任一目标校验失败不得留下部分任务。删除任务只影响指定任务；物化表仍被下游任务使用时拒绝物理删除。
- 新建物化建表的目标数据源默认勾选当前租户、角色可见的 ODS 分域编码 `huiju`、`yuanshi` 对应数据源，并按上述分域配置顺序排列；只存在其中一个时选择该分域，均不存在时留空。不得用名称或选项首项猜测，也不覆盖已有目标表查看时的选择。接入任务外层列表的“数据表”和“接入任务”列不提供排序。
- 分发任务新建物化建表的目标选项采用接入左树同口径的当前租户、角色可见非 ODS 已登记数据源，按“部门→数据源”级联展示，不默认选择 ODS 分域或任意首项。未完成登记或未提供连接的候选不可选；已保存目标查看时保留原选择。接入与分发展开区使用同一套真实表链路展示，箭头及状态在数据库/表名块之间上下居中；分发列表“数据表”“分发任务”两列不提供排序。
- 接入与分发链路的当前运行状态采用本页一次批量读取的有效 `nifi_pipeline_t.status`，与画布生命周期保持一致；无有效绑定流程的历史任务才回退 `task_status`。`monitor_status` 保存最近一次执行结果，`SUCCESS` 不能解释为任务停止，也不能覆盖运行态。批量状态查询保留租户、有效记录与链路角色范围，不逐任务请求 NiFi。
- 接入任务与分发任务按当前租户资源中心 `dataSourceType` 根下有效 ODS 层及其直接子分域在 `dwm_center_layer_source_t` 的实际绑定分类。接入候选排除此数据源集合；分发候选只包含此集合，不能用库名、数据库类型或旧 Hive 标志分类。两类列表、左树与统计均要求来源数据源、来源表 `asset_status > 0`，有效记录、业务表/日志表/字典表及当前租户、角色范围一致；ODS 主目标绑定也须计入。菜单显示名为“接入任务”“分发任务”，保留 `/data-convergence`、`/hive-metastore` 路由、菜单 ID 和权限配置。
- 分页批量返回关联任务的实际来源和全部授权目标，展开区按表 ID 展示连续“业务来源 → 汇聚 → 原始”链路；同一历史任务的多目标是并列写入，不能伪画成串行。候选分类不裁剪已关联下游任务；既有任务的中间来源登记状态为 `0` 时仍可按租户、角色及有效记录只读查看，不能据此放宽新任务创建的登记要求。不得根据同名表推断链路。
- 海通组合画布通过只读批量接口显示各独立任务的 DSL，并用展示连线表达任务间真实表关系。当前操作任务须明确标识；编辑、保存、部署、启动、停止仍只作用于选中任务，切换保留未保存草稿，操作进行中不得切换任务。展示节点和展示连线不得写入其他任务 DSL、部署为运行连接或自动移动 NiFi 流程组。聚合接口有界限制链跳、任务、关联表及画布体积，在集合外批量查询并逐 ID 校验租户和角色权限；详情读取及操作任务切换不得修复或保存 DSL，组合画布使用批量快照而不逐任务自动补查。历史来源及字段映射的调整只在明确的写操作中执行，按该任务真实来源处理；主画布加载不依赖 NiFi 在线。
- NiFi 原生入口只在用户点击时进行一次有界组树读取。有直属共同父组且该父组直属子组恰好属于当前已授权链路时可提供“组总览”，该视图展示子流程组，不代表把各组内部合并；同时保留各任务流程组入口。无法核验共同父组或父组还有其他任务时选择要进入的已部署任务，不为展示合并迁移运行流程组。选择弹框不依赖预先打开空白窗口，实际跳转由用户点击已核验入口触发。
- Hive（含华为 MRS Hive）物化目标表名统一使用小写，包含 `ods_` 前缀。按每个目标数据源的真实类型规范表名，混选其他数据库时保留其确认名称；目标计划、DDL 预览、提交、元数据与后续画布必须使用同一规范名称。手工 DDL 中的大写 Hive 表名在实际建表前拒绝，不改写字段、注释、字符串或既有物理表。
- 物化建表模板按登记快照中的原生类型识别字段，保留来源字段 ID、原始类型、长度与精度；Oracle `RAW`/`NCLOB`/`BINARY_FLOAT`/`BINARY_DOUBLE`/`ROWID`、PostgreSQL `uuid`/`bytea`/`float4`/`float8` 等不能误报为快照缺失。通用二进制和浮点类型须继续按实际目标方言生成 DDL，画布映射使用同一类型族；超出目标主键类型能力时明确拒绝，不能静默截断。真正缺少名称/类型或仅有中文展示类型返回 409 并列出字段；完整但尚未支持的原生类型返回 422 并列出原始类型，不默认猜为 `varchar(255)`，也不提示反复采集。普通模板读取不探查物理库，字段采集必须由用户明确发起；采集后重载失败时保留错误，不提示建表信息已加载成功。
- FTP/SFTP 来源物化使用同一份登记字段快照。用户打开建表抽屉时显式请求 `resolveSourceFile=true`，在来源表、数据源的租户和角色范围校验后，按登记名称解析唯一的实际文件及其 Excel 工作表/JSON 数组路径；目录只列举一次，只抽样读取该文件，不按字段或目标库重复探查。不用规范化表名拼接文件名，也不放大为整个目录采集。同名歧义和来源字段变化必须拒绝并提示重新登记/采集。编码和分隔符默认取数据源登记，可在本次物化中确认；采集配置只保存在任务画布，不能另存重复连接或文件主档。客户端不能覆盖托管连接、目录、文件正则或数据集路径。CSV/分隔 TXT、JSON/JSON TXT、XLS/XLSX 分别按正确解析器处理；JSON 子数组与 Excel 工作表必须与登记的数据集一致。旧画布仅在明确确认后重建，不自动启动任务或删除来源文件。
- 接入目标清理按服务端已授权数据源的真实类型处理：Hive（含 MRS 和普通 JDBC Hive）仅移除接入登记及关联流程，保留物理表和数据，不依赖 Hive 连通或自动执行 DROP。物化重名检查仍须连接目标库；所有库发现已有表时提示对应数据源和表名，用户明确选择继续后复用已有表、仅创建缺少的目标表并生成任务，不能隐式删除或覆盖已有数据。Hive 不提供自动物理删表重建选项。非 Hive 的明确删除仍正常执行物理删除；物理表已不存在按幂等清理处理，连接、驱动、权限或删除后校验异常时保留目标登记并返回待重试状态。NiFi 404 按已清理处理，其余清理异常保留流程记录和相关目标登记供重试，页面须显示待重试，不能伪报物理表或外部流程已删除。租户、角色范围、来源表和下游引用校验失败仍拒绝操作，不以容错跳过。
- 复用已有物理表必须先批量校验本次所选目标的真实字段，核对所需字段、类型族、长度与数值精度，保存实际字段顺序和类型；缺少或不兼容字段须列出并停止，不能照抄新建模板伪造元数据。既有表登记缺失时补齐目标表、字段、目录与来源关系，已有登记更新时保留表和匹配字段 ID、原统计及数据。不得抢占关联其他来源或有独立活动引用的目标，也不能复用来源表自身。普通 JDBC 按已授权数据源与 Schema 分组批量读取字段；托管 MRS Hive 网关暂无批量 DESCRIBE 契约，单次限制 20 个目标，沿用查询超时并在任何物化写入前完成整批检查。来源、字典、治理属性和目标候选均按租户、角色范围批量预取，字段遍历及辅助函数不逐字段查询。
- 物化目标的 `db_table_t.source_table_id` 可保留初始业务来源，级联任务的 `data_access_agg_task_t.source_table_id` 表示真实上一跳。清理不能只比较两字段是否相等：非直接来源须由当前租户有效任务精确绑定来源 ID、目标 ID，并确认与实际来源元数据相同的初始来源在当前租户有效。重新登记的目标若把来源标记成自身，还须从有效同租户目录及目录属性证明原来源和明确目标关联。此兼容不修改业务数据或元数据来源，不放宽竞争来源任务、活动下游任务、来源表自身及角色数据范围的保护；证明记录一次批量预取，不在逐目标循环中查库。
- For `register-confirm`, dictionary tables do not need timestamp validation. Only `业务表` and `日志表` require a timestamp field, and each table only needs one timestamp field.
- 多值字典翻译与标准枚举的拆分、匹配、拼接在 NiFi 记录处理中执行，不按来源库、字典库或目标库类型限制能力，不使用 MySQL 专属的 `JSON_TABLE`、`GROUP_CONCAT` 查询。标准枚举使用已登记的编码映射，不借来源库执行查询；字典关联保留真实数据源、schema 和附加条件，只按有界编码批次执行参数化 `IN` 查询。保留原始顺序和重复值，忽略空编码，部分匹配时保留未匹配的原始编码，全部未匹配时遵守 NULL/KEEP_SOURCE/FAIL 策略。旧自动生成的多值规则在恢复画布时根据登记元数据修复，不能退化成单值匹配或丢弃字典过滤条件。已物化并关联的目标表可保留并只补建任务流程。
- 关联枚举的单值 SQL 匹配必须按登记元数据中的来源字段类型生成字面量：数字型字段使用不带引号的数字编码，字符型字段保留引号和前导零，不能仅凭编码外观判断类型。来源类型随自动画布 DSL 和字段映射记录保存；旧画布部署时从当前登记规则补齐。数字编码须校验为有效且有界的数值，中文显示值仍为字符串；预览匹配与实际 SQL 的数值比较保持一致。
- NiFi QueryRecord 的单值枚举翻译须统一字符串比较的字符集：字符字段用 `CONVERT(字段, UTF8, UTF8)` 保持 Java 字符串内容并声明 UTF-8，编码字面量统一 `_UTF8`；数字字段仍直接比较数字，不作字符转换。描述及字符串回退值显式使用无长度限制的 UTF-8 `VARCHAR`，避免中文与 ASCII 混用字符集或短描述被 CASE 补空格。不能将 Calcite 的字符集声明带入来源 JDBC SQL；来源库使用其原生字符串字面量。针对 NiFi 2.9.0 的 SQL 验证使用同版本 `nifi-calcite-utils` 和实际 Calcite 引擎，不能只以 H2 执行或 SQL 文本断言代替。升级字段映射编译规则须同步相应部署指纹，使受影响任务的下次明确部署生成新 SQL，不自动改写运行中的 NiFi 流程。
- For `db-table` preview:
  - Row data preview should not apply added/modified/deleted row coloring.
  - Field structure view should show added fields in green, modified fields in yellow, deleted fields in gray/strikethrough.
- For `db-table` deleted table rows:
  - Show deleted physical tables as gray/strikethrough table names.
  - Operation must be a real Delete action with confirmation, then call `/dst/database/metadata/tables/deleteAsset`.
