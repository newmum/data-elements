<template>
  <div class="db-table-page">
    <div class="table-toolbar">
      <u-title name="数据表信息" />
      <div class="business-segment" role="radiogroup" aria-label="数据表业务类型">
        <button
          v-for="type in businessTypes"
          :key="type"
          type="button"
          role="radio"
          :aria-checked="state.businessType === type"
          :class="{ 'is-selected': state.businessType === type }"
          @click="setBusinessType(type)"
        >
          {{ type === "全部" ? "全部表" : type }}
          <span>{{ type === "全部" ? state.snapshotTotal : state.businessStats[type] || 0 }}</span>
        </button>
      </div>
      <div class="toolbar-actions">
        <el-button
          v-if="canCollect"
          plain
          :loading="collecting"
          :disabled="!state.source"
          @click="startCollection"
        >
          <template #icon><Icon icon="el-icon-Refresh" /></template>
          重新探查
        </el-button>
        <el-button
          type="primary"
          :loading="state.openingRegistration"
          @click="openDbTableRegistration"
        >
          <template #icon><Icon icon="el-icon-Plus" /></template>
          登记数据资源
        </el-button>
      </div>
    </div>
    <el-alert
      v-if="state.listError"
      :title="state.listError"
      type="error"
      show-icon
      :closable="false"
    >
      <el-button link type="primary" @click="refreshTable">重新读取快照</el-button>
    </el-alert>
    <div
      v-if="collecting || state.collectionError"
      class="collection-status"
      :class="{ 'is-error': state.collectionError }"
      role="status"
    >
      <template v-if="collecting">
        <Icon icon="el-icon-Loading" />
        <span>
          {{ state.collectionPhase === "PERSISTING" ? "正在保存表快照" : "正在后台探查数据表" }}
        </span>
        <el-progress :percentage="state.collectionProgress" :stroke-width="5" :show-text="true" />
      </template>
      <template v-else>
        <Icon icon="el-icon-Warning" />
        <span>{{ state.collectionError }}</span>
        <el-button link type="primary" @click="startCollection">重新探查</el-button>
      </template>
    </div>
    <DataTable
      ref="tableRef"
      class="metadata-table"
      row-key="tid"
      :loading="state.loading"
      :columns="[]"
      :data="fetchData"
      :init-page-size="20"
      :page-sizes="[20, 50, 100]"
      :operation-width="74"
      :empty-description="
        state.listError
          ? '可点击重新读取快照后重试'
          : '尚无数据表快照，请手动探查或进入登记流程维护'
      "
    >
      <el-table-column prop="serialNo" label="序号" width="64" align="center" />
      <el-table-column
        prop="tableName"
        label="数据表名"
        min-width="250"
        sortable="custom"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div class="table-name-cell">
            <Icon :icon="isViewTable(row) ? 'el-icon-View' : 'table'" />
            <el-button link type="primary" class="table-name-link" @click="openTablePreview(row)">
              {{ row.tableName }}
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="tableComment" label="表注释" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.tableComment || row.tableNameCn || "" }}</template>
      </el-table-column>
      <el-table-column
        prop="fieldCount"
        label="字段数"
        width="100"
        align="center"
        sortable="custom"
      >
        <template #default="{ row }">
          <el-button type="primary" link @click="openTablePreview(row, 'fields')">
            {{ row.fieldCount ?? 0 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="annotated" label="是否标注" width="104" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="isAnnotated(row.annotated) ? 'success' : 'info'"
            effect="light"
          >
            {{ isAnnotated(row.annotated) ? "已标注" : "未标注" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="businessType" label="业务类型" width="104" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info" effect="plain">
            {{ row.businessType || "业务表" }}
          </el-tag>
        </template>
      </el-table-column>
      <template #operation="{ row }">
        <el-button
          type="danger"
          link
          :loading="state.deletingId === row.tid"
          @click="deleteTableSnapshot(row)"
        >
          删除
        </el-button>
      </template>
    </DataTable>
    <el-dialog
      v-model="state.previewDialogVisible"
      class="preview-dialog"
      width="min(1280px, calc(100vw - 40px))"
      top="4vh"
      append-to-body
      :title="`${state.currentTableName || ''} 表数据预览`"
    >
      <div v-loading="state.previewLoading" class="preview-layout">
        <main class="preview-data">
          <div class="preview-section-head">
            <div class="preview-mode-switch">
              <button
                :class="{ 'is-active': state.previewMode === 'rows' }"
                :disabled="state.previewLoading"
                type="button"
                @click="changePreviewMode('rows')"
              >
                行数据预览
              </button>
              <button
                :class="{ 'is-active': state.previewMode === 'fields' }"
                :disabled="state.previewLoading"
                type="button"
                @click="changePreviewMode('fields')"
              >
                字段结构
              </button>
            </div>
            <el-button
              v-if="state.previewMode === 'fields' && state.currentPreviewTableId && !isPushSource"
              :loading="state.syncingFields"
              @click="syncPreviewFields"
            >
              <template #icon><Icon icon="el-icon-Refresh" /></template>
              同步字段
            </el-button>
            <div class="preview-topbar-meta">
              {{ state.previewColumns.length }} 个字段
              <span v-if="state.previewMode === 'rows'">
                {{
                  state.previewRows.length
                    ? ` · 展示 ${state.previewRows.length} 条数据`
                    : ` · ${state.previewMessage || "暂无样例数据"}`
                }}
              </span>
              <span v-else>· 显示已读取的字段结构</span>
            </div>
          </div>
          <DataTable
            v-if="state.previewMode === 'rows'"
            class="sample-preview-table"
            :columns="[]"
            :data="state.previewRows"
            :show-page="false"
            border
            stripe
            height="min(520px, calc(100vh - 190px))"
          >
            <el-table-column
              v-for="field in state.previewColumns"
              :key="field.columnName"
              :prop="field.columnName"
              min-width="170"
              show-overflow-tooltip
            >
              <template #header>
                <div class="preview-column-title">
                  <el-tooltip
                    :content="field.columnComment || field.columnName"
                    placement="top"
                    :show-after="400"
                  >
                    <strong>{{ field.columnComment || field.columnName }}</strong>
                  </el-tooltip>
                  <el-tooltip :content="field.columnName" placement="top" :show-after="400">
                    <small>{{ field.columnName }}</small>
                  </el-tooltip>
                  <small v-if="false">
                    {{ field.columnType || field.dataType || "未知类型" }}
                    <em v-if="shouldShowColumnLength(field)">({{ field.columnLength }})</em>
                    · {{ field.columnComment || "未填中文名" }}
                    <b v-if="field.primaryKey">主键</b>
                  </small>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="preview-friendly-empty">
                <el-empty :description="state.previewMessage || '暂无样例数据'" :image-size="88">
                  <template #description>
                    <strong>{{ state.previewMessage || "暂无样例数据" }}</strong>
                    <span>
                      可以切换到字段结构查看字段说明，或确认数据源连接、表权限后重新预览。
                    </span>
                  </template>
                </el-empty>
              </div>
            </template>
          </DataTable>
          <DataTable
            v-else
            :columns="[]"
            :data="state.fieldRows"
            :show-page="false"
            border
            stripe
            height="min(520px, calc(100vh - 190px))"
          >
            <el-table-column prop="serialNo" label="序号" width="70" align="center" />
            <el-table-column
              prop="columnName"
              label="字段名"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="columnComment"
              label="字段注释"
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.columnComment || "-" }}</template>
            </el-table-column>
            <el-table-column
              prop="columnType"
              label="字段类型"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.columnType || row.dataType || "-" }}</template>
            </el-table-column>
            <el-table-column prop="primaryKey" label="主键" width="72" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.primaryKey" size="small" type="warning" effect="light">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="nullable" label="可空" width="72" align="center">
              <template #default="{ row }">{{ row.nullable === false ? "否" : "是" }}</template>
            </el-table-column>
            <el-table-column
              prop="defaultValue"
              label="默认值"
              min-width="100"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.defaultValue || "-" }}</template>
            </el-table-column>
            <el-table-column label="首条数据" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ firstPreviewValue(row) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty :description="state.previewMessage || '暂无字段信息'" :image-size="72" />
            </template>
          </DataTable>
        </main>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount } from "vue";
import { ElMessageBox } from "element-plus";
import { useRegisterModal } from "@/composables";

type Row = Record<string, unknown>;
interface TableRow extends Row {
  tid: string;
  tableName: string;
  tableNameCn?: string;
  tableComment?: string;
  fieldCount?: number;
  annotated?: unknown;
  businessType?: string;
}
interface FieldRow extends Row {
  serialNo: number;
  columnName: string;
  columnComment: string;
  columnType: string;
  dataType: string;
  primaryKey: boolean;
  nullable: boolean;
  columnLength: unknown;
  defaultValue: unknown;
}
const props = defineProps<{ dbId: string; dataSourceType?: string }>();
const tableRef = ref<{ refresh(reset?: boolean): void }>();
const { openRegisterModal, open: registrationOpen } = useRegisterModal();
const businessTypes = ["全部", "业务表", "日志表", "字典表", "过程表", "备份表"];
const state = reactive({
  loading: false,
  listError: "",
  businessType: "全部",
  businessStats: {} as Record<string, number>,
  snapshotTotal: 0,
  rows: [] as TableRow[],
  total: 0,
  source: null as Row | null,
  openingRegistration: false,
  deletingId: "",
  collectionStarting: false,
  collectionStatus: "",
  collectionProgress: 0,
  collectionPhase: "",
  collectionError: "",
  jobId: "",
  previewDialogVisible: false,
  previewLoading: false,
  previewMode: "rows",
  currentTableName: "",
  currentPreviewTableId: "",
  previewColumns: [] as FieldRow[],
  fieldRows: [] as FieldRow[],
  previewRows: [] as Row[],
  previewMessage: "",
  syncingFields: false,
});
let generation = 0,
  listVersion = 0,
  previewVersion = 0,
  disposed = false;
let collectionTimer: ReturnType<typeof setTimeout> | undefined;
let sourceRequest: Promise<Row> | undefined;
let previewTable: TableRow | undefined;
let previewHasSample = false;
const objectOf = (value: unknown): Row =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? (value as Row) : {};
const payloadOf = (value: unknown): Row => {
  const outer = objectOf(value);
  return Array.isArray(outer.data) ? outer : objectOf(outer.data ?? outer);
};
const text = (value: unknown): string => (value == null ? "" : String(value));
const messageOf = (value: unknown, fallback: string): string =>
  text(objectOf(value).message) || fallback;
const parseObject = (value: unknown): Row => {
  if (typeof value !== "string") return objectOf(value);
  try {
    return objectOf(JSON.parse(value));
  } catch {
    return {};
  }
};
const sourceType = computed(() =>
  text(state.source?.dbType || state.source?.databaseType || props.dataSourceType).toLowerCase()
);
const accessMode = computed(() => {
  const source = state.source || {};
  const pool = parseObject(source.poolCfg ?? source.pool_cfg);
  return text(
    source.accessMode ??
      source.access_mode ??
      source.dataAccessMode ??
      pool.accessMode ??
      pool.access_mode ??
      pool.dataAccessMode
  ).toLowerCase();
});
const isPushSource = computed(() => ["receive", "push"].includes(accessMode.value));
const isOfflineSource = computed(
  () =>
    isPushSource.value ||
    ["capture", "pull"].includes(accessMode.value) ||
    ["0", "false"].includes(text(state.source?.showConnect))
);
const canCollect = computed(
  () => !!state.source && (!isOfflineSource.value || sourceType.value === "ftp")
);
const collecting = computed(() => state.collectionStarting || state.collectionStatus === "RUNNING");
const structured = computed(() => ["api", "ftp", "kafka"].includes(sourceType.value));
const serverManagedMrs = computed(
  () =>
    text(state.source?.metadataAccessMode || state.source?.metadata_access_mode).toLowerCase() ===
      "server-managed-mrs" || text(state.source?.hiveConnectionMode).toLowerCase() === "huawei-mrs"
);
const isAnnotated = (value: unknown) => value === true || value === 1 || value === "1";
const isViewTable = (row: TableRow) => /view|视图/i.test(text(row.tableType));
const refreshTable = () => tableRef.value?.refresh(true);
const setBusinessType = (value: string) => {
  if (state.businessType !== value) {
    state.businessType = value;
    refreshTable();
  }
};
const getSource = (): Promise<Row> => {
  if (state.source) return Promise.resolve(state.source);
  if (sourceRequest) return sourceRequest;
  const version = generation,
    dbId = props.dbId;
  sourceRequest = $common
    .post("/dst/database/detail", { tid: dbId })
    .then((result: unknown) => {
      const source = payloadOf(result);
      if (disposed || version !== generation) return source;
      if (source.found === false || !source.tid)
        throw new Error("数据源不存在或无访问权限，请返回数据源列表重试");
      state.source = source;
      return source;
    })
    .catch((error: unknown) => {
      if (version === generation) sourceRequest = undefined;
      throw error;
    });
  return sourceRequest;
};
async function fetchData({ pageNo = 1, pageSize = 20, sortField = "", sortDir = "" } = {}) {
  const version = ++listVersion,
    current = generation,
    dbId = props.dbId;
  if (!dbId) return { list: [], total: 0 };
  state.loading = true;
  state.listError = "";
  try {
    // 与登记第二步共用快照接口；列表不读取来源表或逐行补查字段。
    const result = payloadOf(
      await $common.get("/dst/database/metadata/tables", {
        dbId,
        paged: true,
        allSnapshots: true,
        includeGovernance: false,
        pageNo,
        pageSize,
        sortField,
        sortDir,
        businessType: state.businessType === "全部" ? "" : state.businessType,
      })
    );
    if (disposed || current !== generation || version !== listVersion)
      return { list: state.rows, total: state.total };
    const raw = Array.isArray(result.rows)
      ? result.rows
      : Array.isArray(result.list)
        ? result.list
        : [];
    state.rows = raw.map((value, index) => {
      const row = objectOf(value);
      return {
        ...row,
        tid: text(row.tid || row.id),
        tableName: text(row.tableName),
        serialNo: (pageNo - 1) * pageSize + index + 1,
      } as TableRow;
    });
    state.businessStats = Object.fromEntries(
      Object.entries(objectOf(result.businessStats)).map(([key, value]) => [
        key,
        Number(value) || 0,
      ])
    );
    state.snapshotTotal =
      Number(result.snapshotTotal ?? result.annotatedTotal ?? result.total) || 0;
    state.total = Number(result.total) || 0;
    return { list: state.rows, total: state.total };
  } catch (error: unknown) {
    if (!disposed && current === generation && version === listVersion) {
      state.listError = messageOf(error, "表快照读取失败，请稍后重试");
      state.rows = [];
      state.total = 0;
    }
    return { list: state.rows, total: state.total };
  } finally {
    if (current === generation && version === listVersion) state.loading = false;
  }
}
function stopPolling() {
  if (collectionTimer) clearTimeout(collectionTimer);
  collectionTimer = undefined;
}
function applyCollectionStatus(value: unknown) {
  const result = payloadOf(value);
  state.collectionStatus = text(result.status);
  state.jobId = text(result.jobId);
  state.collectionPhase = text(result.phase);
  state.collectionProgress = Math.max(0, Math.min(100, Number(result.progressPercent) || 0));
  state.collectionError = text(result.error);
  return result;
}
async function readCollectionStatus(version: number, notify = false) {
  const dbId = props.dbId;
  try {
    const value = await $common.get("/dst/database/metadata/tables/collection/status", {
      dbId,
      jobId: state.jobId || undefined,
    });
    if (disposed || generation !== version) return;
    const result = applyCollectionStatus(value);
    if (result.status === "RUNNING") {
      stopPolling();
      collectionTimer = setTimeout(() => {
        void readCollectionStatus(version, notify);
      }, 1500);
    } else if (result.status === "SUCCEEDED" && notify) {
      refreshTable();
      $message.success(`数据表探查完成，新增 ${Number(result.addedCount) || 0} 张`);
    }
  } catch (error: unknown) {
    if (!disposed && generation === version) {
      state.collectionError = messageOf(error, "采集状态读取失败，可重新探查后重试");
      if (collecting.value)
        collectionTimer = setTimeout(() => {
          void readCollectionStatus(version, notify);
        }, 3000);
    }
  }
}
async function startCollection() {
  if (collecting.value || !props.dbId) return;
  const version = generation,
    dbId = props.dbId;
  state.collectionStarting = true;
  state.collectionError = "";
  try {
    await getSource();
    if (disposed || version !== generation) return;
    if (!canCollect.value) {
      $message.info("该来源使用登记快照，请进入登记流程维护其数据表定义");
      return;
    }
    const result = await $common.post("/dst/database/metadata/tables/collection/start", {
      dbId,
      mode: "REFRESH",
    });
    if (disposed || version !== generation) return;
    applyCollectionStatus(result);
    await readCollectionStatus(version, true);
  } catch (error: unknown) {
    if (version === generation)
      state.collectionError = messageOf(error, "无法启动探查，请检查数据源连接后重试");
  } finally {
    if (version === generation) state.collectionStarting = false;
  }
}
async function openDbTableRegistration() {
  if (state.openingRegistration || !props.dbId) return;
  const version = generation;
  state.openingRegistration = true;
  try {
    const source = await getSource();
    if (disposed || version !== generation) return;
    openRegisterModal({
      registerClass: "dbTable",
      registerData: { db: [{ ...source, tid: props.dbId, id: props.dbId }], currentStep: 1 },
    });
  } catch (error: unknown) {
    $message.error(messageOf(error, "无法读取数据源信息，请重试"));
  } finally {
    if (version === generation) state.openingRegistration = false;
  }
}
async function deleteTableSnapshot(row: TableRow) {
  if (state.deletingId || !row.tid) return;
  const dbId = props.dbId,
    version = generation;
  try {
    await ElMessageBox.confirm(
      `确认删除“${row.tableName}”的表快照吗？将清理平台内关联的字段、目录和接入配置，不会删除来源数据库中的物理表或记录。`,
      "删除表快照",
      { type: "warning", confirmButtonText: "删除快照", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  if (disposed || version !== generation) return;
  state.deletingId = row.tid;
  try {
    const result = payloadOf(
      await $common.post("/dst/database/metadata/tables/deleteAsset", { dbId, tableId: row.tid })
    );
    if (result.deleted !== true) throw new Error("快照未确认删除，请刷新后重试");
    if (!disposed && version === generation) {
      $message.success("表快照已删除");
      refreshTable();
    }
  } catch (error: unknown) {
    $message.error(messageOf(error, "删除失败，请重试"));
  } finally {
    if (version === generation) state.deletingId = "";
  }
}
const boolValue = (value: unknown) =>
  value === true || value === 1 || ["1", "true", "yes", "y"].includes(text(value).toLowerCase());
function normalizeFields(value: unknown): FieldRow[] {
  const payload = payloadOf(value),
    rows = Array.isArray(value)
      ? value
      : Array.isArray(payload.data)
        ? payload.data
        : Array.isArray(payload.list)
          ? payload.list
          : Array.isArray(payload.columns)
            ? payload.columns
            : [];
  return rows.map((item, index) => {
    const field = objectOf(item);
    return {
      ...field,
      serialNo: index + 1,
      columnName: text(field.columnName || field.name || field.fieldName),
      columnComment: text(field.columnComment || field.comment),
      columnType: text(field.columnType || field.dataType || field.typeName),
      dataType: text(field.dataType || field.typeName),
      columnLength: field.columnLength ?? field.length ?? field.columnSize ?? "",
      primaryKey: boolValue(field.primaryKey),
      nullable: field.nullable == null ? true : boolValue(field.nullable),
      defaultValue: field.defaultValue ?? field.columnDefault ?? "",
    };
  });
}
const firstPreviewValue = (field: FieldRow) => text(state.previewRows[0]?.[field.columnName]);
const shouldShowColumnLength = (field: FieldRow) => !!field.columnLength;
async function mrsMetadata(action: string, extra: Row) {
  const source = await getSource();
  const database = text(source.database || source.dbName || source.dbMetaDbName || source.schema);
  if (!database) throw new Error("数据源缺少已登记的数据库名，请在登记流程中补充");
  return $common.post("/dst/database/metadata/huaweiMrsHiveJdbcDebug", {
    action,
    contract: "registration",
    database,
    ...extra,
  });
}
async function fetchFields(row: TableRow) {
  if (serverManagedMrs.value) return mrsMetadata("columns", { table: row.tableName });
  if (structured.value && !isPushSource.value)
    return $common.post("/dst/database/metadata/structuredColumns", {
      dbId: props.dbId,
      tableName: row.tableName,
    });
  return $common.get("/dst/database/metadata/columns", {
    tid: row.tid,
    dbId: props.dbId,
    tableName: row.tableName,
  });
}
function normalizeSample(value: unknown, fields: FieldRow[]) {
  const data = payloadOf(value),
    rawColumns = Array.isArray(data.columns)
      ? data.columns
      : Array.isArray(data.columnNames)
        ? data.columnNames
        : [];
  const names = rawColumns.map((item) =>
    typeof item === "string" ? item : text(objectOf(item).columnName || objectOf(item).name)
  );
  const rawRows = Array.isArray(data.rows)
    ? data.rows
    : Array.isArray(data.list)
      ? data.list
      : Array.isArray(data.data)
        ? data.data
        : [];
  const rows = rawRows.map((item) =>
    Array.isArray(item)
      ? Object.fromEntries(names.map((name, index) => [name, item[index]]))
      : objectOf(item)
  );
  const keys = names.length
    ? names
    : fields.length
      ? fields.map((field) => field.columnName)
      : Array.from(new Set(rows.flatMap((row) => Object.keys(row))));
  const byName = new Map(fields.map((field) => [field.columnName, field]));
  const columns = keys.map(
    (name, index) =>
      byName.get(name) || {
        serialNo: index + 1,
        columnName: name,
        columnComment: "",
        columnType: "",
        dataType: "",
        columnLength: "",
        nullable: true,
        primaryKey: false,
        defaultValue: "",
      }
  );
  return { rows, columns };
}
async function openTablePreview(row: TableRow, mode = "rows") {
  const version = ++previewVersion,
    current = generation;
  previewTable = row;
  previewHasSample = false;
  state.previewDialogVisible = true;
  state.previewMode = mode;
  state.previewLoading = true;
  state.currentTableName = row.tableName;
  state.currentPreviewTableId = row.tid;
  state.previewRows = [];
  state.previewColumns = [];
  state.fieldRows = [];
  state.previewMessage = "";
  try {
    await getSource();
    if (disposed || current !== generation || version !== previewVersion) return;
    const fields = normalizeFields(await fetchFields(row));
    if (disposed || current !== generation || version !== previewVersion) return;
    state.fieldRows = fields;
    state.previewColumns = fields;
    if (mode === "rows") await readSample(row, version, current);
  } catch (error: unknown) {
    if (version === previewVersion) state.previewMessage = messageOf(error, "预览读取失败，请重试");
  } finally {
    if (current === generation && version === previewVersion) state.previewLoading = false;
  }
}
async function readSample(row: TableRow, version = previewVersion, current = generation) {
  if (isOfflineSource.value && sourceType.value !== "ftp") {
    state.previewMessage = "该来源使用已保存快照，仅展示字段结构";
    return;
  }
  const body = {
    tableId: row.tid,
    dbId: props.dbId,
    tableName: text(row.sourceTableName) || row.tableName,
    sampleSize: 10,
  };
  const result = serverManagedMrs.value
    ? await mrsMetadata("sample", { table: body.tableName, limit: 10 })
    : await $common.post(
        structured.value
          ? "/dst/database/metadata/structuredSampleData"
          : "/dst/database/metadata/table/sample-data",
        body
      );
  if (disposed || current !== generation || version !== previewVersion) return;
  const sample = normalizeSample(result, state.fieldRows);
  state.previewRows = sample.rows;
  state.previewColumns = sample.columns;
  previewHasSample = true;
  state.previewMessage = sample.rows.length ? "" : "暂无样例数据，可切换查看字段结构";
}
async function changePreviewMode(mode: string) {
  state.previewMode = mode;
  if (mode !== "rows" || previewHasSample || !previewTable || state.previewLoading) return;
  const version = previewVersion,
    current = generation;
  state.previewLoading = true;
  try {
    await readSample(previewTable, version, current);
  } catch (error: unknown) {
    if (version === previewVersion)
      state.previewMessage = messageOf(error, "样例数据读取失败，请重试");
  } finally {
    if (current === generation && version === previewVersion) state.previewLoading = false;
  }
}
async function syncPreviewFields() {
  if (state.syncingFields || !state.currentPreviewTableId) return;
  const tableId = state.currentPreviewTableId,
    version = previewVersion,
    current = generation;
  try {
    await ElMessageBox.confirm(
      "从来源重新同步当前表的字段快照？新增、类型和注释将更新，已删除字段将移出配置。",
      "同步字段",
      { type: "warning", confirmButtonText: "确认同步", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  if (disposed || version !== previewVersion || current !== generation) return;
  state.syncingFields = true;
  try {
    const fields = normalizeFields(
      await $common.post("/dst/database/metadata/collectColumns", { tableId, force: true })
    );
    if (version === previewVersion) {
      state.fieldRows = fields;
      state.previewColumns = fields;
      $message.success(`已同步 ${fields.length} 个字段`);
      refreshTable();
    }
  } catch (error: unknown) {
    $message.error(messageOf(error, "字段同步失败，请重试"));
  } finally {
    state.syncingFields = false;
  }
}
async function initialize() {
  generation++;
  listVersion++;
  previewVersion++;
  stopPolling();
  state.collectionStarting = false;
  state.loading = false;
  state.listError = "";
  state.openingRegistration = false;
  state.deletingId = "";
  state.previewLoading = false;
  sourceRequest = undefined;
  state.source = null;
  state.rows = [];
  state.total = 0;
  state.snapshotTotal = 0;
  state.businessStats = {};
  state.businessType = "全部";
  state.collectionStatus = "";
  state.collectionError = "";
  state.jobId = "";
  state.previewDialogVisible = false;
  const current = generation;
  try {
    await getSource();
    if (!disposed && current === generation && canCollect.value)
      await readCollectionStatus(current);
  } catch (error: unknown) {
    if (current === generation) state.listError = messageOf(error, "数据源读取失败，请重试");
  }
}
watch(
  () => props.dbId,
  () => {
    void initialize();
    refreshTable();
  },
  { immediate: true }
);
watch(registrationOpen, (open, previous) => {
  if (!disposed && previous && !open) {
    sourceRequest = undefined;
    state.source = null;
    void getSource().catch(() => {});
    refreshTable();
  }
});
watch(
  () => state.previewDialogVisible,
  (visible) => {
    if (!visible) {
      previewVersion++;
      state.previewLoading = false;
      previewTable = undefined;
    }
  }
);
onBeforeUnmount(() => {
  disposed = true;
  generation++;
  previewVersion++;
  listVersion++;
  stopPolling();
});
</script>

<style scoped lang="scss">
.db-table-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding: 10px 18px 14px;
  overflow: hidden;
  background: #fff;
  gap: 12px;
}
.table-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}
.table-toolbar > :deep(.u-title) {
  width: auto;
  height: 40px;
  padding: 0;
  flex: 0 0 auto;
}
.business-segment {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 3px;
  background: #f4f5f7;
  border-radius: 7px;
}
.business-segment button {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #526176;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    background-color 160ms,
    color 160ms;
}
.business-segment button:hover {
  color: #2563eb;
  background: #eaf0fb;
}
.business-segment button.is-selected {
  color: #14253d;
  background: #fff;
  box-shadow: 0 1px 4px #172b4d12;
  font-weight: 600;
}
.business-segment span {
  min-width: 17px;
  padding: 1px 4px;
  border-radius: 8px;
  color: #667085;
  background: #e9edf3;
  text-align: center;
  font-size: 11px;
}
.business-segment button:focus-visible,
.preview-mode-switch button:focus-visible {
  outline: 2px solid #8ab4ff;
  outline-offset: 2px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.toolbar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.metadata-table {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
.table-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  min-width: 0;
}
.table-name-link {
  min-width: 0;
  overflow: hidden;
}
.table-name-link :deep(span) {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.collection-status {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid #dbe6f6;
  background: #f5f8fd;
  border-radius: 6px;
  color: #52647c;
  font-size: 13px;
}
.collection-status .el-progress {
  flex: 1;
  max-width: 280px;
  margin-left: auto;
}
.collection-status.is-error {
  color: #b54708;
  background: #fffaeb;
  border-color: #fedf89;
}
.preview-layout,
.preview-data {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.preview-section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
}
.preview-mode-switch {
  display: flex;
  gap: 3px;
  padding: 3px;
  background: #f2f4f7;
  border-radius: 6px;
}
.preview-mode-switch button {
  padding: 6px 12px;
  border: 0;
  border-radius: 4px;
  color: #667085;
  background: transparent;
  cursor: pointer;
}
.preview-mode-switch button.is-active {
  background: #fff;
  color: #2563eb;
  box-shadow: 0 1px 3px #10182814;
}
.preview-topbar-meta {
  margin-left: auto;
  font-size: 12px;
  color: #667085;
}
.preview-column-title {
  display: grid;
  gap: 3px;
  min-width: 0;
  line-height: 18px;
}
.preview-column-title strong,
.preview-column-title small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-column-title strong {
  font-weight: 600;
  color: #344054;
}
.preview-column-title small {
  font-weight: 400;
  color: #667085;
  font-size: 11px;
}
.preview-friendly-empty strong,
.preview-friendly-empty span {
  display: block;
}
.preview-friendly-empty span {
  margin-top: 8px;
  font-size: 12px;
  color: #98a2b3;
}
@media (prefers-reduced-motion: reduce) {
  .business-segment button {
    transition: none;
  }
}
</style>
