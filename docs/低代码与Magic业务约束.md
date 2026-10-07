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
- `POST /dst/database/metadata/tables/preview`
  - Lightweight table exploration/list preview.
- `POST /dst/database/metadata/test-connection`
  - Data source connection test.
- `POST /dst/application/save`
  - Save an application system directly to `sym_application_t` and `data_prop_t`.
- `POST /dst/database/saveOrUpdate`
  - Save a data source directly to `db_datasource_t` and `da_app_db_rela`; all extensible connection and metadata fields belong in `db_datasource_t.pool_cfg`, not `data_prop_t`.
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
- On the database detail form, the user-facing field `是否已注册` binds to the authoritative `assetStatus` / `db_datasource_t.asset_status` column—not the auxiliary `registerStatus` / `register_status` workflow marker. Configure values `0` as `未注册` and `2` as `已注册`; render legacy value `1` as `审核中` without permitting a manual transition into it. The save API may synchronize derived workflow fields after an allowed `assetStatus` change, but the form payload and detail response must use `assetStatus` as the source for this control.
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
  - Save application systems, data sources, tables, and catalogs as directly usable (`asset_status=2`, `flow_status=2`, no `flow_order_id`).
  - Do not create `da_order_asset_rela` rows for `app`, `db`, `table`, or `catalog`.
  - Registration steps after the first data-source step show only Previous/Next; they must not auto-save or auto-submit.
- The standalone approval center remains available independently of asset registration:
  - Keep the Warm-Flow dependencies, `com.linewell.dataelement.flow` runtime package, approval controller/service, flow suggestions, and flow relation infrastructure.
  - Its Magic tree is `/magic-api/api/09.系统管理/02.审批中心/`; public routes start with `/sym/approval`.
  - Warm-Flow callbacks and permission handlers call the `/sym/approval` Magic routes.
  - Do not reconnect `register-db`, `register-dbTable`, catalog registration, or registration completion to automatic approval submission.
- Application systems use `sym_application_t` as their only master table. Data sources use `db_datasource_t` as their only master table. Preserve migrated IDs in each table's `tid`; do not recreate app/db rows in `da_asset_t`.
- Data tables use `db_table_t` as their only master table and `db_table_column_t.table_id` for field metadata. Preserve migrated table IDs in `db_table_t.tid`; do not create table rows in `da_asset_t`, `da_asset_table_column_t`, or `data_prop_t`.
- Every data-table property used by registration, governance, preview, resource-center, or indexing logic must be represented by a physical `db_table_t` / `db_table_column_t` column. Do not add `data_type='table'` dynamic properties.
- Data-source connection options, metadata summaries, and migration extensions belong in the valid JSON object stored in `db_datasource_t.pool_cfg`. Data-source interfaces must not read or write `data_prop_t`; direct columns remain denormalized query fields where present.
- Application systems and data sources keep `sym_application_t` and `db_datasource_t` as their authoritative master tables. Their high-frequency list/search interfaces read the denormalized Elasticsearch index for speed; in this environment the configured physical index is `elements_dataassets` (`configData().es_dataassets`), while `EsCommonService` receives the logical name `dataassets` and adds the `elements_` prefix. Detail interfaces may fall back to MySQL when an index document is absent.
- Ordinary application-system and data-source save/delete interfaces update MySQL first and then update or remove the matching Elasticsearch document. Dynamic-detail saves that explicitly request `deferIndexRefresh` follow the validated best-effort asynchronous projection contract above and return `indexRefreshPending: true`; do not make a completed authoritative save appear failed because indexing is slow. Keep `POST /dataassets/manage/assets/refresh` for one asset and `POST /dataassets/manage/assets/batchRefresh` for reconciliation or full index rebuilds.
- Implement the legacy refresh routes above as thin Magic API compatibility aliases that delegate to `/dst/maintenance/refresh` and `/dst/maintenance/batchRefresh`; keep the indexing logic in the `/dst/maintenance` implementations.
- Never treat Elasticsearch as the source of truth for application systems or data sources. Index refresh must rebuild app documents from `sym_application_t`, db documents from `db_datasource_t` plus `pool_cfg`, and remove stale app/db documents that no longer exist in the master tables.
- Elasticsearch documents are search projections, not storage for full configuration. Keep datasource credentials and complex `pool_cfg` objects out of ES, convert Hutool `JSONNull` values to real nulls or strings before indexing, and exclude large governance payloads such as `fieldGovernanceConfig`, dictionary profiles, and column arrays. Full values remain in MySQL.
- For `showConnect=0`, persist the data-source master record but clear direct connection columns and connection keys in `pool_cfg` so credentials cannot reappear in detail views.
- Low-code pages should use existing project table components where practical; avoid inventing unrelated table systems.
- Organization hierarchy edits use the tenant database's `rm_org_t`, not the control database. When a user may change `parent_id` from an organization form, enforce the move in the save API rather than only in the low-code page: reject modification of the virtual `ROOT` row, self-parenting, missing parents, and descendant-parent cycles; then update the moved row's `parent_id` and `org_path` and rewrite the old path prefix for every active descendant. Keep the hierarchy's `serial_number` prefix consistent for the moved subtree as well, because organization-scope list queries use it. Do not split a parent change into a UI-only update or two independently successful save requests.
- Keep UI language consistent:
  - `字典对标` is now `字典翻译`.
  - `业务对标` is now `统一格式`.
  - `表数据查看` should be `表数据预览`.
- For `register-confirm`, dictionary tables do not need timestamp validation. Only `业务表` and `日志表` require a timestamp field, and each table only needs one timestamp field.
- For `db-table` preview:
  - Row data preview should not apply added/modified/deleted row coloring.
  - Field structure view should show added fields in green, modified fields in yellow, deleted fields in gray/strikethrough.
- For `db-table` deleted table rows:
  - Show deleted physical tables as gray/strikethrough table names.
  - Operation must be a real Delete action with confirmation, then call `/dst/database/metadata/tables/deleteAsset`.
