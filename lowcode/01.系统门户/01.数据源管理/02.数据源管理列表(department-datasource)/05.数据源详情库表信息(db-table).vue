<template>
  <div class="db-table-page">
    <u-title>
      <template #name>数据表信息</template>
      <div class="db-table-title-extra">
        <div class="segmented-like business-segment" role="radiogroup">
          <button
            v-for="item in businessSegmentOptions"
            :key="item.value"
            :class="['segment-option', state.businessTypeFilter === item.value ? 'is-selected' : '']"
            type="button"
            role="radio"
            :aria-checked="state.businessTypeFilter === item.value"
            @click="setBusinessTypeFilter(item.value)"
          >
            <span class="segment-label">{{ item.label }}</span>
            <span class="segment-count">{{ item.count }}</span>
          </button>
        </div>
        <div class="toolbar-actions">
          <el-button
            class="refresh-probe-btn"
            plain
            :loading="state.refreshLoading"
            @click="handleAction('刷新数据表')"
          >
            <template #icon>
              <Icon icon="el-icon-Refresh" />
            </template>
            {{ state.refreshLoading ? "正在探查" : "重新探查" }}
          </el-button>
          <div v-if="state.diffReady" class="segmented-like diff-filter-tabs" role="radiogroup">
            <button
              v-for="item in diffSegmentOptions"
              :key="item.value"
              type="button"
              :class="['segment-option', 'diff-filter-tab', state.diffFilter === item.value ? 'is-selected' : '', `is-${item.className}`]"
              role="radio"
              :aria-checked="state.diffFilter === item.value"
              @click="setDiffFilter(item.value)"
            >
              <span class="segment-label">{{ item.label }}</span>
              <span class="segment-count">{{ item.count }}</span>
            </button>
          </div>
          <el-button
            class="register-resource-btn"
            type="primary"
            :loading="state.dbAssetLoading"
            @click="openDbTableRegistration"
          >
            <template #icon>
              <Icon icon="el-icon-Plus" />
            </template>
            登记数据资源
          </el-button>
        </div>
      </div>
    </u-title>

    <transition name="probe-reveal">
      <section v-if="state.refreshLoading" class="metadata-probe" aria-live="polite">
        <div class="probe-visual" aria-hidden="true">
          <span class="probe-node probe-database"><Icon icon="db" /></span>
          <span class="probe-path">
            <i></i>
            <i></i>
            <i></i>
          </span>
          <span class="probe-node probe-table"><Icon icon="table" /></span>
          <span class="probe-scan-beam"></span>
        </div>
        <div class="probe-content">
          <div class="probe-heading">
            <strong>正在探查数据库结构</strong>
            <span>正在连接数据源，读取库表与字段，并与已登记元数据进行差异比对</span>
          </div>
          <div class="probe-progress"><i></i></div>
          <div class="probe-steps">
            <span><Icon icon="el-icon-Link" />连接数据源</span>
            <span><Icon icon="el-icon-Search" />扫描库表字段</span>
            <span><Icon icon="el-icon-DataAnalysis" />分析结构差异</span>
          </div>
        </div>
      </section>
    </transition>

    <data-table
      ref="tableRef"
      :class="['metadata-table', { 'is-probing': state.refreshLoading }]"
      row-key="tableName"
      :loading="state.loading && !state.refreshLoading"
      :columns="state.cols"
      :data="fetchData"
      :init-page-size="20"
      :page-sizes="[10, 20, 50, 100]"
      :operation-width="72"
      @filter-change="handleAction('filter-change', $event)"
    >
      <template #column-tableName="{ row }">
        <div :class="['table-name-cell', row.changeStatus === '删除' ? 'is-deleted' : '']">
          <span
            :class="['table-type-icon', row.tableType === '视图' ? 'is-view' : 'is-table']"
            :title="tableTypeTip(row)"
          >
            <Icon :icon="row.tableType === '视图' ? 'el-icon-View' : 'table'" />
          </span>
          <el-text
            v-if="row.changeStatus !== '删除'"
            class="table-name-link"
            type="primary"
            truncated
            @click.stop="openFields(row, 'rows')"
          >
            {{ row.tableName }}
          </el-text>
          <span v-else class="table-name-link is-disabled" :title="changeStatusTip(row)">
            {{ row.tableName }}
          </span>
          <el-tag
            v-if="state.diffReady && row.changeStatus && row.changeStatus !== '无变化'"
            :class="['name-change-tag', changeStatusClass(row.changeStatus)]"
            size="small"
            effect="light"
            :title="changeStatusTip(row)"
          >
            {{ row.changeStatus }}
          </el-tag>
        </div>
      </template>

      <template #column-tableComment="{ row }">
        <el-input
          v-model="row.tableComment"
          class="comment-input"
          size="small"
          clearable
          placeholder="请规范填写数据表中文名"
          title="请规范填写数据表中文名"
          @change="(value) => updateTableComment(row, value)"
        />
      </template>

      <template #column-businessType="{ row }">
        <span :class="['business-select', businessClass(row.businessType)]" :title="businessTypeReason(row)">
          <select
            v-model="row.businessType"
            class="business-select__control"
            aria-label="业务类型"
            @change="updateBusinessType(row, row.businessType)"
          >
            <option
            v-for="item in businessTypeOptions"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</option>
          </select>
          <Icon class="business-select__arrow" icon="el-icon-ArrowDown" />
        </span>
      </template>

      <template #column-fieldCount="{ row }">
        <el-button class="field-count-link" type="primary" link @click.stop="openFields(row, 'fields')">
          {{ row.fieldCount ?? 0 }}
        </el-button>
      </template>

      <template #operation="{ row }">
        <el-button
          v-if="row.changeStatus === '删除'"
          class="delete-link"
          type="danger"
          link
          :loading="row._deleteLoading"
          @click.stop="deleteRemovedTable(row)"
        >
          删除
        </el-button>
        <el-tag v-else-if="row.registered" class="operation-status-tag" size="small" type="success" effect="light">
          已登记
        </el-tag>
        <el-button v-else class="save-link" type="primary" link @click.stop="registerRow(row)">
          保存
        </el-button>
      </template>
    </data-table>

    <el-dialog
      v-model="state.previewDialogVisible"
      class="preview-dialog"
      width="1280px"
      append-to-body
      :title="`${state.currentTableName || ''} 表数据预览`"
    >
      <div v-loading="state.previewLoading" class="preview-layout">
        <main class="preview-data">
          <div class="preview-section-head">
            <div class="preview-mode-switch">
              <button :class="{ 'is-active': state.previewMode === 'rows' }" type="button" @click="state.previewMode = 'rows'">行数据预览</button>
              <button :class="{ 'is-active': state.previewMode === 'fields' }" type="button" @click="state.previewMode = 'fields'">字段结构</button>
            </div>
            <div class="preview-topbar-meta">
              {{ state.previewColumns.length }} 个字段
              <span v-if="state.previewMode === 'rows'">
                {{ state.previewRows.length ? ` · 展示 ${state.previewRows.length} 条数据` : ` · ${state.previewMessage || "暂无样例数据"}` }}
              </span>
              <span v-else> · 右侧展示首条数据</span>
            </div>
          </div>
          <DataTable
            v-if="state.previewMode === 'rows'"
            :columns="[]"
            :data="state.previewRows"
            :show-page="false"
            border
            stripe
            height="520"
          >
            <el-table-column
              v-for="field in state.previewColumns"
              :key="field.columnName"
              :prop="field.columnName"
              min-width="180"
              show-overflow-tooltip
            >
              <template #header>
                <div class="preview-column-title" :title="field.columnName">
                  <strong>
                    {{ field.columnName }}
                  </strong>
                  <small>
                    {{ displayColumnType(field) }}
                    <i v-if="displayColumnLength(field)">({{ displayColumnLength(field) }})</i>
                    · {{ field.columnComment || "未填中文名" }}
                    <b v-if="isPrimaryKey(field.primaryKey)">主键</b>
                  </small>
                </div>
              </template>
              <template #default="{ row }">{{ formatPreviewCell(row[field.columnName]) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty :description="state.previewMessage || '暂无样例数据'" :image-size="72" />
            </template>
          </DataTable>
          <DataTable v-else :columns="[]" :data="state.fieldRows" :show-page="false" border stripe height="520" :row-class-name="fieldRowClassName">
            <el-table-column prop="serialNo" label="序号" width="70" align="center" />
            <el-table-column prop="columnName" label="字段名" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="field-name-with-status">
                  <span>{{ row.columnName }}</span>
                  <el-tag
                    v-if="row._diffStatus"
                    :class="['field-diff-tag', fieldDiffClass(row)]"
                    size="small"
                    effect="light"
                    :title="row._diffText"
                  >
                    {{ row._diffStatus }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="columnComment" label="字段注释" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="field-attr-cell">
                  <span>{{ row.columnComment || "-" }}</span>
                  <small v-if="fieldChangeNote(row, 'columnComment')">{{ fieldChangeNote(row, 'columnComment') }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="columnType" label="字段类型" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="field-attr-cell">
                  <span>{{ displayColumnType(row) }}</span>
                  <small v-if="fieldChangeNote(row, 'columnType')">{{ fieldChangeNote(row, 'columnType') }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="primaryKey" label="主键" width="96" align="center">
              <template #default="{ row }">
                <div class="field-attr-cell is-center">
                  <span><el-tag v-if="isPrimaryKey(row.primaryKey)" size="small" type="warning" effect="light">是</el-tag><template v-else>-</template></span>
                  <small v-if="fieldChangeNote(row, 'primaryKey')">{{ fieldChangeNote(row, 'primaryKey') }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="nullable" label="可空" width="96" align="center">
              <template #default="{ row }">
                <div class="field-attr-cell is-center">
                  <span>{{ nullableLabel(row.nullable) }}</span>
                  <small v-if="fieldChangeNote(row, 'nullable')">{{ fieldChangeNote(row, 'nullable') }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="defaultValue" label="默认值" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="field-attr-cell">
                  <span>{{ row.defaultValue || "-" }}</span>
                  <small v-if="fieldChangeNote(row, 'defaultValue')">{{ fieldChangeNote(row, 'defaultValue') }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="首条数据" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ firstPreviewValue(row) }}</template></el-table-column>
            <template #empty>
              <el-empty :description="state.previewMessage || '暂无字段信息'" :image-size="72" />
            </template>
          </DataTable>
        </main>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from "vue";
import { useRegisterModal } from "@/composables";

const props = defineProps({
  dbId: String,
  dataSourceType: String,
});

const tableRef = ref();
const { openRegisterModal } = useRegisterModal();

const businessTypeOptions = [
  { label: "业务表", value: "业务表" },
  { label: "日志表", value: "日志表" },
  { label: "字典表", value: "字典表" },
  { label: "过程表", value: "过程表" },
  { label: "备份表", value: "备份表" },
];

const businessFilterOptions = [
  { label: "全部表", value: "全部" },
  ...businessTypeOptions,
];

const businessSegmentOptions = computed(() =>
  businessFilterOptions.map((item) => ({
    ...item,
    count: businessTypeCount(item.value),
  }))
);

const diffFilterOptions = [
  { label: "全部", value: "全部", className: "all" },
  { label: "新增", value: "新增", className: "added" },
  { label: "删除", value: "删除", className: "deleted" },
  { label: "变更", value: "变更", className: "modified" },
  { label: "无变化", value: "无变化", className: "unchanged" },
];

const diffSegmentOptions = computed(() =>
  diffFilterOptions.map((item) => ({
    ...item,
    count: diffStatusCount(item.value),
  }))
);

const state = reactive({
  cols: [],
  loading: false,
  refreshLoading: false,
  businessTypeFilter: "全部",
  metaCache: {},
  metaCacheDbId: "",
  dbAsset: null,
  dbAssetLoading: false,
  registeredTableMap: {},
  registeredLoadDbId: "",
  registeredLoading: false,
  tableRows: [],
  filteredRows: [],
  summary: {
    tableCount: 0,
    viewCount: 0,
    fieldCount: 0,
  },
  businessStats: {},
  diffReady: false,
  diffFilter: "全部",
  diffRows: [],
  diffStats: {
    total: 0,
    added: 0,
    deleted: 0,
    modified: 0,
    unchanged: 0,
  },
  previewDialogVisible: false,
  previewLoading: false,
  previewMode: "rows",
  previewColumns: [],
  previewRows: [],
  previewMessage: "",
  fieldRows: [],
  currentTableName: "",
});

/**
 * 详情路由会复用同一个 db-table 组件；数据库切换后必须清空上一个库的探查结果和元数据缓存。
 * 否则 diffReady 仍为 true，fetchData 会直接展示旧缓存，当前数据库的表不会重新探查。
 */
watch(
  () => props.dbId,
  (nextDbId, previousDbId) => {
    if (!nextDbId || nextDbId === previousDbId) return;

    state.metaCache = {};
    state.metaCacheDbId = "";
    state.dbAsset = null;
    state.dbAssetLoading = false;
    state.registeredTableMap = {};
    state.registeredLoadDbId = "";
    state.registeredLoading = false;
    state.tableRows = [];
    state.filteredRows = [];
    state.businessStats = {};
    state.diffReady = false;
    state.diffFilter = "全部";
    state.diffRows = [];
    state.summary = { tableCount: 0, viewCount: 0, fieldCount: 0 };
    state.diffStats = { total: 0, added: 0, deleted: 0, modified: 0, unchanged: 0 };
    state.previewDialogVisible = false;
    state.previewColumns = [];
    state.previewRows = [];
    state.previewMessage = "";
    state.fieldRows = [];
    state.currentTableName = "";

    tableRef.value?.refresh(true);
  },
  { flush: "post" }
);

const handleAction = (type, row) => {
  switch (type) {
    case "filter-change":
      refreshTable(false);
      break;
    case "刷新数据表":
      runReExplore();
      break;
  }
};

const refreshTable = (resetPage = true) => {
  tableRef.value?.refresh(resetPage);
};

const setBusinessTypeFilter = (value) => {
  state.businessTypeFilter = value;
  refreshTable(true);
};

const setDiffFilter = (value) => {
  state.diffFilter = value;
  refreshTable(true);
};

const fetchData = async ({ pageNo = 1, pageSize = 20 }) => {
  if (!props.dbId) {
    resetRows();
    return { list: [], total: 0 };
  }

  ensureMetaCache();
  loadDbContextInBackground();
  state.loading = true;
  const autoExplore = !state.diffReady;
  if (autoExplore) {
    state.refreshLoading = true;
  }
  try {
    if (!state.diffReady) {
      await loadReExploreData();
    }

    state.filteredRows = applyToolbarFilters(state.diffRows);
    updateStats(state.diffRows, state.filteredRows);

    const start = Math.max(pageNo - 1, 0) * pageSize;
    return {
      list: state.filteredRows.slice(start, start + pageSize),
      total: state.filteredRows.length,
    };
  } catch (error) {
    console.error("探查数据表列表失败:", error);
    resetRows();
    return { list: [], total: 0 };
  } finally {
    state.loading = false;
    if (autoExplore) {
      state.refreshLoading = false;
    }
  }
};

const runReExplore = async () => {
  if (!props.dbId) return;
  state.refreshLoading = true;
  state.loading = true;
  try {
    const result = await loadReExploreData();
    state.filteredRows = applyToolbarFilters(state.tableRows);
    updateStats(state.tableRows, state.filteredRows);
    refreshTable(true);
    const summary = result?.summary || {};
    $message.success(
      `重新探查完成：新增 ${summary.addedCount || 0}，删除 ${summary.deletedCount || 0}，变更 ${summary.modifiedCount || 0}`
    );
  } catch (error) {
    console.error("重新探查数据库表差异失败:", error);
    $message.error(error?.message || "重新探查失败");
  } finally {
    state.loading = false;
    state.refreshLoading = false;
  }
};

const loadReExploreData = async () => {
  await ensureRegisteredTablesLoaded();
  const result = await $common.post(
    "/dst/database/metadata/tables/reExplore",
    {
      dbId: props.dbId,
      limit: 0,
    },
    {},
    300 * 1000
  );
  state.diffReady = true;
  state.diffFilter = "全部";
  state.diffRows = normalizeDiffRows(result || {});
  state.tableRows = state.diffRows;
  return result || {};
};

const resetRows = () => {
  state.tableRows = [];
  state.filteredRows = [];
  updateStats([], []);
};

const applyToolbarFilters = (rows) => {
  return rows.filter((row) => {
    if (state.businessTypeFilter !== "全部" && row.businessType !== state.businessTypeFilter) {
      return false;
    }
    if (state.diffReady && state.diffFilter !== "全部" && (row.changeStatus || "无变化") !== state.diffFilter) {
      return false;
    }
    return true;
  });
};

const normalizeRows = (rows) => {
  return rows.map((row, index) => {
    const normalized = {
      serialNo: row.serialNo || index + 1,
      ...row,
    };
    const key = getRowKey(normalized);
    const cached = state.metaCache[key] || {};
    const registeredAsset = state.registeredTableMap[normalizeTableKey(normalized.tableName)];
    return {
      ...normalized,
      businessType: cached.businessType || normalized.businessType || "业务表",
      tableComment: cached.tableComment ?? normalized.tableComment ?? normalized.tableNameCn ?? "",
      registered: !!registeredAsset,
      tableAssetId: registeredAsset?.tid || "",
      changeStatus: state.diffReady ? normalized.changeStatus || "无变化" : "",
      changes: normalized.changes || null,
      before: normalized.before || null,
      businessTypeReason: cached.businessTypeReason || normalizeBusinessReason(normalized),
    };
  });
};

const normalizeDiffRows = (result) => {
  const rows = [];
  const pushRow = (row, status, extra = {}) => {
    if (!row) return;
    rows.push({
      serialNo: rows.length + 1,
      ...row,
      ...extra,
      tableComment: row.tableComment || row.tableNameCn || row.tableName || "",
      registered: status !== "新增",
      changeStatus: status,
      tableAssetId: row.tid || row.id || extra.tableAssetId || "",
      businessType: row.businessType || extra.businessType || "业务表",
    });
  };

  for (const row of result.added || []) {
    pushRow(row, "新增", { registered: false });
  }
  for (const item of result.modified || []) {
    const before = item.before || {};
    const after = item.after || {};
    pushRow(after, "变更", {
      before,
      changes: item.changes || {},
      registered: true,
      tableAssetId: before.tid || before.id || "",
      businessType: before.businessType || after.businessType || "业务表",
    });
  }
  for (const row of result.deleted || []) {
    pushRow(row, "删除", {
      registered: true,
      physicallyDeleted: true,
      tableAssetId: row.tid || row.id || "",
      tableComment: row.tableNameCn || row.tableComment || row.tableName || "",
    });
  }
  for (const row of result.unchanged || []) {
    pushRow(row, "无变化", { registered: true });
  }

  state.diffStats = {
    total: rows.length,
    added: (result.added || []).length,
    deleted: (result.deleted || []).length,
    modified: (result.modified || []).length,
    unchanged: (result.unchanged || []).length,
  };
  return normalizeRows(rows).map((row, index) => ({ ...row, serialNo: index + 1 }));
};

const getRowKey = (row) => row.tableName || row.name || String(row.serialNo || "");

const updateBusinessType = (row, value) => {
  const key = getRowKey(row);
  state.metaCache[key] = {
    ...(state.metaCache[key] || {}),
    businessType: value,
    businessTypeReason: "人工调整业务类型",
  };
  row.businessType = value;
  row.businessTypeReason = "人工调整业务类型";
  state.filteredRows = applyToolbarFilters(state.tableRows);
  updateStats(state.tableRows, state.filteredRows);
};

const updateTableComment = (row, value) => {
  const key = getRowKey(row);
  state.metaCache[key] = {
    ...(state.metaCache[key] || {}),
    tableComment: value,
  };
  row.tableComment = value;
};

const deleteRemovedTable = async (row) => {
  const tableId = row.tableAssetId || row.tid || row.id;
  if (!props.dbId || !tableId) {
    $message.error("缺少表资产ID，无法删除");
    return;
  }
  try {
    await $dialog.confirm(
      `确认删除已失效的表资产“${row.tableName || ""}”？删除后会递归清理字段、目录、关系、接入任务及历史脏数据。`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
      }
    );
  } catch (error) {
    return;
  }
  row._deleteLoading = true;
  try {
    await $common.post("/dst/database/metadata/tables/deleteAsset", {
      dbId: props.dbId,
      tableId,
      tableName: row.tableName,
    });
    $message.success("删除成功");
    state.diffReady = false;
    refreshTable(true);
  } catch (error) {
    console.error("删除表资产失败:", error);
    $message.error(error?.message || "删除失败");
  } finally {
    row._deleteLoading = false;
  }
};

const ensureMetaCache = () => {
  if (state.metaCacheDbId === props.dbId) return;
  state.metaCacheDbId = props.dbId || "";
  state.metaCache = {};
};

const normalizeTableKey = (value) => String(value || "").trim().toLowerCase();

const loadDbContextInBackground = () => {
  if (!props.dbId) return;
  if (!state.dbAsset && !state.dbAssetLoading) {
    state.dbAssetLoading = true;
    loadDbAsset()
      .then((dbAsset) => {
        state.dbAsset = dbAsset;
      })
      .finally(() => {
        state.dbAssetLoading = false;
      });
  }
  if (state.registeredLoadDbId !== props.dbId && !state.registeredLoading) {
    state.registeredLoading = true;
    state.registeredLoadDbId = props.dbId;
    state.registeredTableMap = {};
    loadRegisteredTables()
      .then((tableAssets) => {
        state.registeredTableMap = buildRegisteredTableMap(tableAssets);
        state.tableRows = normalizeRows(state.tableRows);
        state.filteredRows = applyToolbarFilters(state.tableRows);
        updateStats(state.tableRows, state.filteredRows);
      })
      .finally(() => {
        state.registeredLoading = false;
      });
  }
};

const ensureDbAsset = async () => {
  if (state.dbAsset) return state.dbAsset;
  state.dbAssetLoading = true;
  try {
    state.dbAsset = await loadDbAsset();
    return state.dbAsset;
  } finally {
    state.dbAssetLoading = false;
  }
};

const loadDbAsset = async () => {
  try {
    return await $common.post("/dst/database/detail", { tid: props.dbId });
  } catch (error) {
    console.warn("读取数据库连接信息失败:", error);
    return null;
  }
};

const loadRegisteredTables = async () => {
  try {
    const result = await $common.get("/dst/database/metadata/tables", { dbId: props.dbId });
    return Array.isArray(result) ? result : [];
  } catch (error) {
    console.warn("读取已登记表列表失败:", error);
    return [];
  }
};

const ensureRegisteredTablesLoaded = async () => {
  if (state.registeredLoadDbId === props.dbId && Object.keys(state.registeredTableMap || {}).length) return;
  state.registeredLoading = true;
  state.registeredLoadDbId = props.dbId;
  try {
    const tableAssets = await loadRegisteredTables();
    state.registeredTableMap = buildRegisteredTableMap(tableAssets);
  } finally {
    state.registeredLoading = false;
  }
};

const buildRegisteredTableMap = (rows) => {
  const map = {};
  for (const row of rows || []) {
    const name = row.tableName || row.tableNameEn || row.sourceTableName || row.name;
    const key = normalizeTableKey(name);
    if (key) {
      map[key] = row;
    }
  }
  return map;
};

const updateStats = (allRows, filteredRows) => {
  const rows = filteredRows || allRows || [];
  const businessStats = {};
  for (const item of businessTypeOptions) {
    businessStats[item.value] = 0;
  }
  for (const row of allRows || []) {
    const type = row.businessType || "业务表";
    businessStats[type] = (businessStats[type] || 0) + 1;
  }
  state.businessStats = businessStats;
  state.summary = {
    tableCount: rows.filter((row) => (row.tableType || "数据表") !== "视图").length,
    viewCount: rows.filter((row) => row.tableType === "视图").length,
    fieldCount: rows.reduce((sum, row) => sum + Number(row.fieldCount || 0), 0),
  };
};

const businessTypeCount = (value) => {
  if (value === "全部") return state.tableRows.length;
  return state.businessStats[value] || 0;
};

const diffStatusCount = (value) => {
  if (value === "新增") return state.diffStats.added;
  if (value === "删除") return state.diffStats.deleted;
  if (value === "变更") return state.diffStats.modified;
  if (value === "无变化") return state.diffStats.unchanged;
  return state.diffStats.total;
};

const normalizeBusinessReason = (row) => {
  const reason = row.businessTypeReason || "";
  if (!reason || reason.includes("未命中")) {
    return "表名和注释未呈现日志、字典、流程或备份特征，更符合承载核心业务对象与业务过程数据的业务表特征";
  }
  return reason;
};

const businessTypeReason = (row) => normalizeBusinessReason(row);

const tableTypeTip = (row) => (row.tableType === "视图" ? "视图" : "数据表");

const businessClass = (type) => {
  const mapping = {
    业务表: "is-business",
    日志表: "is-log",
    字典表: "is-dict",
    过程表: "is-process",
    备份表: "is-backup",
  };
  return mapping[type] || "is-business";
};

const changeStatusClass = (status) => {
  const mapping = {
    新增: "is-added",
    删除: "is-deleted",
    变更: "is-modified",
    无变化: "is-unchanged",
  };
  return mapping[status] || "is-unchanged";
};

const changeStatusTip = (row) => {
  if (!state.diffReady) return "";
  if (row.changeStatus === "新增") return "当前数据库存在，但尚未登记为表资产";
  if (row.changeStatus === "删除") return "已登记为表资产，但当前数据库中没有探查到这张表";
  if (row.changeStatus === "变更") {
    const changes = row.changes || {};
    const messages = [];
    if (changes.columnsAdded?.length) messages.push(`新增字段：${changes.columnsAdded.join("、")}`);
    if (changes.columnsDeleted?.length) messages.push(`删除字段：${changes.columnsDeleted.join("、")}`);
    if (changes.columnsModified?.length) {
      messages.push(`字段属性变更：${changes.columnsModified.map((item) => item.columnName).join("、")}`);
    }
    if (changes.tableCommentChanged) messages.push("表注释变化");
    if (changes.tableTypeChanged) messages.push("表类型变化");
    if (changes.fieldCountChanged) messages.push("字段数量变化");
    if (changes.recordCountChanged) messages.push("记录数变化");
    return messages.join("；") || "表结构或元数据发生变化";
  }
  return "已登记表与当前探查结果一致";
};

const openFields = async (row, mode = "rows") => {
  if (!props.dbId || !row?.tableName) return;
  state.currentTableName = row.tableName;
  state.previewMode = mode;
  state.previewDialogVisible = true;
  state.previewLoading = true;
  state.fieldRows = [];
  state.previewColumns = [];
  state.previewRows = [];
  state.previewMessage = "";
  try {
    const fields = await loadFieldsWithDiff(row);
    state.fieldRows = fields;
    const previewFields = fields.filter((field) => field._diffStatus !== "删除");
    state.previewColumns = previewFields.length ? previewFields.slice(0, 24) : fields.slice(0, 24);
    await loadPreviewRows(row, previewFields);
  } catch (error) {
    console.error("查询字段列表失败:", error);
    state.previewMessage = error?.message || "字段列表查询失败";
    $message.error(state.previewMessage);
  } finally {
    state.previewLoading = false;
  }
};

const loadFieldsWithDiff = async (row) => {
  const beforeFields = row.tableAssetId ? await loadRegisteredColumns(row.tableAssetId) : [];

  if (row.changeStatus === "删除") {
    return beforeFields.map((field) => ({
      ...field,
      _diffStatus: "删除",
      _diffText: "物理库中已不存在该字段，仅保留登记快照",
    }));
  }

  await ensureDbAsset();
  const probe = await probeColumns(row.tableName);
  if (!probe.success) {
    throw new Error(probe.error || "未探查到字段");
  }
  const currentFields = normalizeFieldRows(probe.columns || []);

  if (row.changeStatus === "新增") {
    return currentFields.map((field) => ({
      ...field,
      _diffStatus: "新增",
      _diffText: "新增数据表中的字段",
    }));
  }

  if (row.changeStatus === "变更" && beforeFields.length) {
    return decorateFieldDiffs(currentFields, beforeFields);
  }

  return currentFields;
};

const loadRegisteredColumns = async (tableAssetId) => {
  if (!tableAssetId) return [];
  const result = await $common.get("/dst/database/metadata/columns", { tid: tableAssetId });
  return normalizeFieldRows(Array.isArray(result) ? result : result?.list || result?.data || []);
};

const loadPreviewRows = async (row, fields = []) => {
  if (row.changeStatus === "删除") {
    state.previewRows = [];
    state.previewMessage = "物理表已删除，仅展示登记快照字段";
    return;
  }
  if (!row.tableAssetId) {
    state.previewRows = [];
    state.previewMessage = "新增表尚未登记，当前仅展示字段结构";
    return;
  }
  try {
    const result = await $common.post("/dst/database/metadata/table/sample-data", {
      tableId: row.tableAssetId,
      sampleSize: 10,
    });
    const preview = normalizePreviewResult(result, fields);
    state.previewColumns = preview.columns.length ? preview.columns : state.previewColumns;
    state.previewRows = preview.rows;
    state.previewMessage = preview.rows.length ? "" : "暂无样例数据，仅展示字段结构";
  } catch (error) {
    state.previewRows = [];
    state.previewMessage = error?.message || "样例数据读取失败，仅展示字段结构";
  }
};

const normalizePreviewResult = (result, fields = []) => {
  const data = result?.data && !Array.isArray(result.data) ? result.data : result || {};
  const rawColumns = Array.isArray(data.columns)
    ? data.columns
    : Array.isArray(data.fields)
      ? data.fields
      : Array.isArray(data.columnNames)
        ? data.columnNames
        : [];
  const rawRows = Array.isArray(data.rows)
    ? data.rows
    : Array.isArray(data.list)
      ? data.list
      : Array.isArray(data.records)
        ? data.records
        : Array.isArray(data.data)
          ? data.data
          : Array.isArray(data)
            ? data
            : [];
  const columnNames = rawColumns.map((column) =>
    typeof column === "string" ? column : column.columnName || column.name || column.prop
  ).filter(Boolean);
  const rows = rawRows.map((row) => {
    if (!Array.isArray(row)) return row;
    const item = {};
    columnNames.forEach((column, index) => { item[column] = row[index]; });
    return item;
  });
  const rowColumns = columnNames.length ? columnNames : Array.from(rows.reduce((set, row) => { Object.keys(row || {}).forEach((key) => set.add(key)); return set; }, new Set()));
  const fieldMapByName = new Map((fields || []).map((field) => [field.columnName, field]));
  const resolvedColumns = (rowColumns.length ? rowColumns : (fields || []).map((field) => field.columnName))
    .filter(Boolean)
    .slice(0, 24)
    .map((column) => {
      const field = fieldMapByName.get(column);
      if (field) return field;
      const index = columnNames.indexOf(column);
      return { columnName: column, columnComment: "", columnType: Array.isArray(data.columnTypes) && index >= 0 ? data.columnTypes[index] : "", dataType: Array.isArray(data.columnTypes) && index >= 0 ? data.columnTypes[index] : "", columnLength: "", primaryKey: false };
    });
  return { columns: resolvedColumns, rows };
};

const decorateFieldDiffs = (currentFields, beforeFields) => {
  const beforeMap = new Map(beforeFields.map((field) => [normalizeTableKey(field.columnName), field]));
  const currentMap = new Map(currentFields.map((field) => [normalizeTableKey(field.columnName), field]));
  const rows = currentFields.map((field) => {
    const before = beforeMap.get(normalizeTableKey(field.columnName));
    if (!before) {
      return { ...field, _diffStatus: "新增", _diffText: "字段为本次重新探查新增" };
    }
    const diffInfo = describeFieldChange(before, field);
    if (!diffInfo.text) return field;
    return { ...field, _diffStatus: "变更", _diffText: diffInfo.text, _diffMap: diffInfo.map };
  });
  beforeFields.forEach((field) => {
    if (!currentMap.has(normalizeTableKey(field.columnName))) {
      rows.push({
        ...field,
        serialNo: rows.length + 1,
        _diffStatus: "删除",
        _diffText: "字段已从物理库删除",
      });
    }
  });
  return rows.map((field, index) => ({ ...field, serialNo: index + 1 }));
};

const describeFieldChange = (before, current) => {
  const changes = [];
  const map = {};
  if (normalizeColumnTypeForDiff(before.columnType || before.dataType) !== normalizeColumnTypeForDiff(current.columnType || current.dataType)) {
    const beforeValue = before.columnType || before.dataType || "-";
    const currentValue = current.columnType || current.dataType || "-";
    map.columnType = `曾为 ${beforeValue}`;
    changes.push(`类型：${beforeValue} -> ${currentValue}`);
  }
  if (normalizeCompareValue(before.columnComment) !== normalizeCompareValue(current.columnComment)) {
    const beforeValue = before.columnComment || "-";
    const currentValue = current.columnComment || "-";
    map.columnComment = `曾为 ${beforeValue}`;
    changes.push(`注释：${beforeValue} -> ${currentValue}`);
  }
  if (isPrimaryKey(before.primaryKey) !== isPrimaryKey(current.primaryKey)) {
    const beforeValue = isPrimaryKey(before.primaryKey) ? "是" : "否";
    const currentValue = isPrimaryKey(current.primaryKey) ? "是" : "否";
    map.primaryKey = `曾为 ${beforeValue}`;
    changes.push(`主键：${beforeValue} -> ${currentValue}`);
  }
  if (normalizeNullable(before.nullable) !== normalizeNullable(current.nullable)) {
    const beforeValue = nullableLabel(before.nullable);
    const currentValue = nullableLabel(current.nullable);
    map.nullable = `曾为 ${beforeValue}`;
    changes.push(`可空：${beforeValue} -> ${currentValue}`);
  }
  if (normalizeCompareValue(before.defaultValue) !== normalizeCompareValue(current.defaultValue)) {
    const beforeValue = before.defaultValue || "-";
    const currentValue = current.defaultValue || "-";
    map.defaultValue = `曾为 ${beforeValue}`;
    changes.push(`默认值：${beforeValue} -> ${currentValue}`);
  }
  return { text: changes.join("；"), map };
};

const normalizeCompareValue = (value) => String(value ?? "").trim().toLowerCase();

const TEMPORAL_COLUMN_TYPES = new Set(["date", "time", "datetime", "timestamp", "year"]);
const MYSQL_DISPLAY_WIDTH_TYPES = new Set(["int", "integer", "bigint", "smallint", "tinyint", "mediumint"]);

const columnTypeText = (field) => String(field?.columnType || field?.dataType || "").trim();

const columnTypeBase = (value) => {
  const text = String(value ?? "").trim().toLowerCase().replace(/\s+/g, " ");
  const bracketIndex = text.indexOf("(");
  return (bracketIndex >= 0 ? text.slice(0, bracketIndex) : text).trim();
};

const normalizeColumnTypeForDiff = (value) => {
  const text = String(value ?? "").trim().toLowerCase().replace(/\s+/g, " ");
  if (!text) return "";
  const base = columnTypeBase(text);
  if (TEMPORAL_COLUMN_TYPES.has(base)) return base;
  if (MYSQL_DISPLAY_WIDTH_TYPES.has(base)) return base;
  return text;
};

const displayColumnType = (field) => {
  const text = columnTypeText(field);
  if (!text) return "-";
  const base = columnTypeBase(text);
  if (TEMPORAL_COLUMN_TYPES.has(base)) return base.toUpperCase();
  return text;
};

const displayColumnLength = (field) => {
  const length = String(field?.columnLength || "").trim();
  if (!length) return "";
  const text = columnTypeText(field).replace(/\s+/g, "").toLowerCase();
  const base = columnTypeBase(text);
  if (TEMPORAL_COLUMN_TYPES.has(base)) return "";
  if (text.endsWith(`(${length.toLowerCase()})`)) return "";
  return length;
};

const normalizeBoolish = (value, defaultValue = false) => {
  if (value === true || value === false) return value;
  if (value === 1 || value === "1") return true;
  if (value === 0 || value === "0") return false;
  const text = String(value ?? "").trim().toLowerCase();
  if (!text) return defaultValue;
  if (["true", "yes", "y", "是", "主键", "pk"].includes(text)) return true;
  if (["false", "no", "n", "否", "非主键"].includes(text)) return false;
  return defaultValue;
};

const isPrimaryKey = (value) => normalizeBoolish(value, false);

const normalizeNullable = (value) => {
  if (value === null || value === undefined || value === "") return true;
  const text = String(value).trim().toLowerCase();
  if (["false", "0", "no", "n", "否", "not null", "no null"].includes(text)) return false;
  if (["true", "1", "yes", "y", "是", "nullable", "null"].includes(text)) return true;
  return normalizeBoolish(value, true);
};

const nullableLabel = (value) => (normalizeNullable(value) ? "是" : "否");

const formatPreviewCell = (value) => {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
};

const firstPreviewValue = (field) => formatPreviewCell(state.previewRows[0]?.[field.columnName]);

const fieldChangeNote = (row, prop) => row?._diffMap?.[prop] || "";

const fieldDiffClass = (field) => {
  const mapping = {
    新增: "is-added",
    删除: "is-deleted",
    变更: "is-modified",
  };
  return mapping[field?._diffStatus] || "";
};

const fieldRowClassName = ({ row }) => {
  const className = fieldDiffClass(row);
  return className ? `field-row-${className}` : "";
};

const probeColumns = async (tableName) => {
  const dbAsset = state.dbAsset;
  if (!dbAsset) {
    throw new Error("数据库连接信息读取失败");
  }
  const response = await fetch("/nifi/api/columns-probe", {
    method: "POST",
    headers: buildFetchHeaders(),
    body: JSON.stringify({
      manifestKey: resolveManifestKey(dbAsset),
      config: buildProbeConfig(dbAsset),
      table: tableName,
    }),
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data?.error || "字段探查请求失败");
  }
  return data;
};

const buildFetchHeaders = () => {
  const token = readStorageValue("token");
  const accessToken = readStorageValue("Access-Token");
  const headers = {
    "Content-Type": "application/json",
  };
  if (token) headers.token = token;
  if (accessToken) headers["Access-Token"] = accessToken;
  return headers;
};

const readStorageValue = (key) => {
  const raw = localStorage.getItem(key) || sessionStorage.getItem(key) || "";
  if (!raw) return "";
  try {
    return JSON.parse(raw) || "";
  } catch (error) {
    return raw;
  }
};

const buildProbeConfig = (asset) => {
  const dbType = firstValue(asset, ["dbType", "databaseType", "dataSourceType"]) || props.dataSourceType || "mysql";
  return {
    host: firstValue(asset, ["host", "dbIp", "ip"]),
    port: firstValue(asset, ["port", "dbPort"]),
    database: firstValue(asset, ["database", "dbName", "dbMetaDbName", "serviceName"]),
    username: firstValue(asset, ["username", "dbUser", "dbMetaUser", "user"]),
    password: firstValue(asset, ["password", "dbPassword", "dbMetaPassword"]),
    currentSchema: firstValue(asset, ["schema", "defaultSchema"]),
    defaultSchema: firstValue(asset, ["schema", "defaultSchema", "dbName"]),
    sid: firstValue(asset, ["sid", "serviceName", "database", "dbName"]),
    serverName: firstValue(asset, ["serverName"]),
    dbType,
  };
};

const resolveManifestKey = (asset) => {
  const type = String(
    firstValue(asset, ["dbType", "databaseType", "dataSourceType"]) || props.dataSourceType || "mysql"
  )
    .trim()
    .toLowerCase()
    .replace(/_/g, "-");
  const mapping = {
    mysql: "source.mysql",
    mariadb: "source.mariadb",
    oracle: "source.oracle",
    postgresql: "source.postgresql",
    postgres: "source.postgresql",
    "tdsql-pg": "source.tdsql-pg",
    gaussdb: "source.gaussdb",
    opengauss: "source.gaussdb",
    sqlserver: "source.sqlserver",
    "sql-server": "source.sqlserver",
    db2: "source.db2",
    dm: "source.dameng",
    dameng: "source.dameng",
    kingbase: "source.kingbase",
    gbase8a: "source.gbase8a",
    gbase8s: "source.gbase8s",
    oscar: "source.oscar",
    highgo: "source.highgo",
    "tdsql-mysql": "source.tdsql-mysql",
    oceanbase: "source.oceanbase",
    clickhouse: "source.clickhouse",
  };
  return mapping[type] || "source.mysql";
};

const firstValue = (source, keys) => {
  for (const key of keys) {
    const value = source?.[key];
    if (value !== undefined && value !== null && value !== "") {
      return value;
    }
  }
  return "";
};

const normalizeFieldRows = (rows) => {
  return (rows || []).map((row, index) => ({
    serialNo: row.serialNo || index + 1,
    columnName: row.columnName || row.name || row.fieldName || row.column_name,
    columnComment: row.columnComment || row.comment || row.remarks || row.column_comment,
    columnType: row.columnType || buildColumnType(row),
    dataType: row.dataType || row.typeName,
    nullable: row.nullable,
    primaryKey: row.primaryKey,
    defaultValue: row.defaultValue || row.columnDefault || row.default_value,
    columnLength: row.columnLength || row.columnSize || row.length || "",
  }));
};

const buildColumnType = (row) => {
  const type = row.typeName || row.dataType || "";
  const size = row.columnSize;
  const scale = row.decimalDigits;
  if (!type) return "";
  if (size && scale) return `${type}(${size},${scale})`;
  if (size) return `${type}(${size})`;
  return type;
};

const openDbTableRegistration = async () => {
  if (!props.dbId) {
    $message.error("缺少数据源ID，无法进入登记流程");
    return;
  }
  const dbAsset = (await ensureDbAsset()) || { tid: props.dbId, id: props.dbId };
  openRegisterModal({
    registerClass: "dbTable",
    registerData: {
      db: [
        {
          ...dbAsset,
          tid: dbAsset.tid || dbAsset.id || props.dbId,
          id: dbAsset.id || dbAsset.tid || props.dbId,
        },
      ],
      currentStep: 1,
    },
  });
};

const registerRow = (row) => {
  const tableName = row.tableName || row.name || "";
  const tableNameCn = row.tableNameCn || row.tableComment || tableName;
  openRegisterModal({
    registerClass: "catalog",
    registerData: {
      catalog: [
        {
          tid: row.catalogId,
          catalogName: `${String(tableNameCn || tableName).replace("表", "目录")}(${tableName})`,
          catalogNameEn: tableName,
          assetDesc: `${String(tableNameCn || tableName).replace("表", "目录")}(${tableName})`,
          sourceTableId: row.tid,
          dbId: props.dbId,
          businessType: row.businessType,
        },
      ],
      table: [
        {
          ...row,
          tableName,
          tableNameCn,
        },
      ],
    },
  });
};

state.cols = [
  { label: "序号", prop: "serialNo", width: 72, align: "center", fixed: "left" },
  { label: "数据表名", prop: "tableName", minWidth: 290, fixed: "left" },
  { label: "表注释", prop: "tableComment", minWidth: 260 },
  { label: "字段数", prop: "fieldCount", width: 86, align: "center" },
  {
    label: "业务类型",
    prop: "businessType",
    width: 144,
    align: "center",
    showOverflowTooltip: false,
    className: "business-type-column",
  },
];
</script>

<style scoped lang="scss">
.db-table-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 10px 18px 14px;
  overflow: hidden;
  background: #fff;
}

.db-table-title-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1 1 auto;
  gap: 10px;
  margin-left: 16px;
  min-width: 0;
  overflow: visible;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 0 1 auto;
  gap: 8px;
  min-width: 0;
}

.register-resource-btn {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border-radius: 7px;
  font-weight: 400;
  white-space: nowrap;
}

.diff-filter-tabs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.diff-filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #4b5563;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.diff-filter-tab b {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  color: #475467;
  background: #f2f4f7;
  font-size: 12px;
  line-height: 18px;
}

.diff-filter-tab.is-selected {
  border-color: #1677ff;
  color: #0958d9;
  background: #eaf3ff;
}

.diff-filter-tab.is-added.is-selected {
  border-color: #52c41a;
  color: #237804;
  background: #f0fdf4;
}

.diff-filter-tab.is-deleted.is-selected {
  border-color: #ff7875;
  color: #a8071a;
  background: #fff1f0;
}

.diff-filter-tab.is-modified.is-selected {
  border-color: #faad14;
  color: #ad6800;
  background: #fffbe6;
}

.segmented-like {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: 32px;
  padding: 2px;
  border-radius: 8px;
  background: #f5f5f5;
  gap: 2px;
}

.business-segment {
  max-width: 468px;
}

.segment-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 28px;
  min-width: 58px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  transition: background-color 0.16s ease, box-shadow 0.16s ease, color 0.16s ease;

  &:hover {
    color: #1f2937;
  }

  &.is-selected {
    background: #fff;
    color: #111827;
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
  }
}

.segment-label,
.segment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.segment-count {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #e5e7eb;
  color: #374151;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
}

.segment-option.is-selected .segment-count {
  background: #f1f5f9;
  color: #0f172a;
}

.metadata-table {
  min-height: 0;
  margin-top: 8px;
}

.table-name-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.table-name-link {
  min-width: 0;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }

  &.is-disabled {
    color: #98a2b3;
    cursor: not-allowed;
    text-decoration: line-through;
  }
}

.table-name-cell.is-deleted .table-type-icon {
  color: #98a2b3;
  background: #f2f4f7;
}

.name-change-tag {
  flex: 0 0 auto;
  height: 20px;
  padding: 0 6px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
}

.name-change-tag.is-added {
  border-color: #b7eb8f;
  color: #237804;
  background: #f6ffed;
}

.name-change-tag.is-modified {
  border-color: #ffd666;
  color: #ad6800;
  background: #fffbe6;
}

.name-change-tag.is-deleted {
  border-color: #d0d5dd;
  color: #667085;
  background: #f2f4f7;
}

.table-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 5px;

  &.is-table {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &.is-view {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }
}

.comment-input {
  width: 100%;

  :deep(.el-input__wrapper) {
    min-height: 28px;
    box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
  }
}

.business-select {
  position: relative;
  width: 104px;
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  color: #0958d9;

  &.is-business {
    color: #0958d9;

    .business-select__control {
      background: #e6f4ff;
      border-color: #bae0ff;
    }
  }

  &.is-log {
    color: #ad6800;

    .business-select__control {
      background: #fff1b8;
      border-color: #ffe58f;
    }
  }

  &.is-dict {
    color: #237804;

    .business-select__control {
      background: #d9f7be;
      border-color: #b7eb8f;
    }
  }

  &.is-process {
    color: #531dab;

    .business-select__control {
      background: #efdbff;
      border-color: #d3adf7;
    }
  }

  &.is-backup {
    color: #434343;

    .business-select__control {
      background: #f0f0f0;
      border-color: #d9d9d9;
    }
  }
}

.business-select__control {
  width: 100%;
  height: 26px;
  padding: 0 28px 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  outline: none;
  appearance: none;
  color: inherit;
  font: inherit;
  line-height: 24px;
  cursor: pointer;
  transition: border-color 0.16s ease, filter 0.16s ease, box-shadow 0.16s ease;

  &:hover {
    filter: brightness(0.98);
  }

  &:focus-visible {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  }
}

.business-select__arrow {
  position: absolute;
  right: 8px;
  width: 14px;
  height: 14px;
  pointer-events: none;
}

:deep(.business-type-column .cell) {
  overflow: visible;
  text-overflow: clip;
}

.field-count-link {
  min-width: 34px;
  padding: 0;
  font-weight: 700;
}

.save-link {
  padding: 0;
}

.operation-status-tag.is-deleted {
  border-color: #ffa39e;
  color: #a8071a;
  background: #fff1f0;
}

.operation-status-tag {
  height: 22px;
  padding: 0 8px;
  border-radius: 5px;
  font-weight: 500;
}

:deep(.el-table__cell) {
  padding-top: 8px;
  padding-bottom: 8px;
}

:deep(.el-table__fixed-right) {
  box-shadow: var(--el-box-shadow-light);
}

.preview-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

.preview-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 14px 18px;
  border-bottom: 1px solid #edf0f3;
}

.preview-dialog :deep(.el-dialog__body) {
  padding: 14px 16px 16px;
  background: #f6f7f9;
}

.preview-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 612px;
}

.preview-topbar-meta {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-mode-switch {
  display: inline-flex;
  gap: 4px;
  padding: 2px;
  border: 0;
  border-radius: 7px;
  background: #edf2f7;

  button {
    min-width: 86px;
    height: 28px;
    border: 0;
    border-radius: 6px;
    color: #475467;
    font-size: 12px;
    font-weight: 650;
    background: transparent;
    cursor: pointer;

    &.is-active {
      color: #fff;
      background: #1677ff;
      box-shadow: 0 4px 10px rgba(22, 119, 255, 0.18);
    }
  }
}

.preview-data {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  border: 1px solid #e1e5ea;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.preview-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 48px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: linear-gradient(180deg, #fff 0%, #fafcff 100%);
}

.preview-section-head strong {
  color: #111827;
  font-size: 13px;
}

.preview-section-head span {
  margin-left: 8px;
  color: #667085;
  font-size: 12px;
}

.preview-data :deep(.el-table) {
  border-right: 0;
  border-left: 0;
}

.preview-data :deep(.el-table__header th) {
  background: #f8fafc;
}

.preview-column-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  line-height: 16px;
}

.preview-column-title strong,
.preview-column-title small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-column-title strong {
  color: #111827;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
}

.preview-column-title small {
  color: #667085;
  font-size: 11px;
  font-weight: 400;
}

.preview-column-title i {
  font-style: normal;
}

.preview-column-title b {
  display: inline-flex;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 4px;
  color: #b54708;
  background: #fffaeb;
  font-size: 10px;
  font-weight: 700;
}

.field-name-with-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.field-attr-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  line-height: 18px;

  &.is-center {
    align-items: center;
  }

  span {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    display: inline-flex;
    align-items: center;
    max-width: 100%;
    height: 16px;
    padding: 0;
    border: 0;
    color: #b7791f;
    background: transparent;
    font-size: 11px;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.field-diff-tag {
  border-radius: 5px;
  font-weight: 600;
}

.field-diff-tag.is-added {
  border-color: #b7eb8f;
  color: #237804;
  background: #f6ffed;
}

.field-diff-tag.is-modified {
  border-color: #ffd666;
  color: #ad6800;
  background: #fffbe6;
}

.field-diff-tag.is-deleted {
  border-color: #d0d5dd;
  color: #667085;
  background: #f2f4f7;
}

.preview-data :deep(.field-row-is-deleted td) {
  color: #98a2b3;
  background: #f2f4f7 !important;
  text-decoration: line-through;
}

.preview-data :deep(.field-row-is-added td) {
  background: #f6ffed !important;
}

.preview-data :deep(.field-row-is-modified td) {
  background: #fffbe6 !important;
}

.metadata-probe {
  position: relative;
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 18px;
  min-height: 102px;
  margin: 4px 0 12px;
  padding: 16px 22px;
  overflow: hidden;
  border: 1px solid #b7d6ff;
  border-radius: 12px;
  background: linear-gradient(105deg, #f4f9ff 0%, #edf6ff 54%, #f7fbff 100%);
  box-shadow: 0 8px 24px rgb(22 119 255 / 8%);
}

.metadata-probe::after {
  position: absolute;
  top: -48px;
  right: -34px;
  width: 180px;
  height: 180px;
  border: 28px solid rgb(22 119 255 / 5%);
  border-radius: 50%;
  content: "";
}

.probe-visual {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 190px;
  height: 64px;
}

.probe-node {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: 1px solid #91caff;
  border-radius: 14px;
  color: #1677ff;
  background: #fff;
  box-shadow: 0 6px 18px rgb(22 119 255 / 16%);
  font-size: 24px;
  animation: probe-node-pulse 1.8s ease-in-out infinite;
}

.probe-table {
  animation-delay: 0.7s;
}

.probe-path {
  display: flex;
  align-items: center;
  justify-content: space-around;
  width: 82px;
  height: 2px;
  margin: 0 6px;
  background: #b7d6ff;
}

.probe-path i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #409eff;
  box-shadow: 0 0 0 4px rgb(64 158 255 / 12%);
  animation: probe-data-flow 1.35s ease-in-out infinite;
}

.probe-path i:nth-child(2) { animation-delay: 0.24s; }
.probe-path i:nth-child(3) { animation-delay: 0.48s; }

.probe-scan-beam {
  position: absolute;
  z-index: 3;
  top: 7px;
  left: 8px;
  width: 28px;
  height: 50px;
  border-radius: 14px;
  background: linear-gradient(90deg, transparent, rgb(22 119 255 / 16%), transparent);
  animation: probe-scan 2.1s ease-in-out infinite;
}

.probe-content {
  position: relative;
  z-index: 2;
  flex: 1 1 auto;
  min-width: 0;
}

.probe-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.probe-heading strong {
  flex: 0 0 auto;
  color: #1d4f91;
  font-size: 16px;
}

.probe-heading span {
  overflow: hidden;
  color: #667085;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-progress {
  height: 5px;
  margin: 12px 0 10px;
  overflow: hidden;
  border-radius: 3px;
  background: #dcecff;
}

.probe-progress i {
  display: block;
  width: 42%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #69b1ff, #1677ff, #69b1ff);
  animation: probe-progress-move 1.65s ease-in-out infinite;
}

.probe-steps {
  display: flex;
  align-items: center;
  gap: 24px;
  color: #5f6b7a;
  font-size: 12px;
}

.probe-steps span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  animation: probe-step-highlight 3s ease-in-out infinite;
}

.probe-steps span:nth-child(2) { animation-delay: 1s; }
.probe-steps span:nth-child(3) { animation-delay: 2s; }

.metadata-table {
  transition: opacity 0.25s ease, filter 0.25s ease;
}

.metadata-table.is-probing {
  opacity: 0.56;
  filter: saturate(0.65);
  pointer-events: none;
}

.probe-reveal-enter-active,
.probe-reveal-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.probe-reveal-enter-from,
.probe-reveal-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@keyframes probe-node-pulse {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-3px) scale(1.04); }
}

@keyframes probe-data-flow {
  0%, 100% { opacity: 0.28; transform: scale(0.7); }
  50% { opacity: 1; transform: scale(1.15); }
}

@keyframes probe-scan {
  0% { opacity: 0; transform: translateX(0); }
  15% { opacity: 1; }
  80% { opacity: 0.85; }
  100% { opacity: 0; transform: translateX(150px); }
}

@keyframes probe-progress-move {
  0% { transform: translateX(-105%); }
  100% { transform: translateX(345%); }
}

@keyframes probe-step-highlight {
  0%, 22%, 100% { color: #7b8794; }
  10% { color: #1677ff; }
}

@media (max-width: 980px) {
  .probe-visual { flex-basis: 150px; }
  .probe-heading span { display: none; }
  .probe-steps { gap: 12px; }
}
</style>
