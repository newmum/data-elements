<template>
  <div class="finish-page">
    <section v-if="loading" class="finish-loading" aria-live="polite">
      <span class="loading-emblem"><Icon icon="el-icon-Loading" /></span>
      <strong>{{ finishProfile.loadingTitle }}</strong>
      <p>{{ finishProfile.loadingMessage }}</p>
    </section>
    <main v-else class="finish-shell">
      <section class="success-card">
        <div class="success-emblem" aria-hidden="true">
          <span><Icon icon="el-icon-Check" /></span>
        </div>
        <div class="success-copy">
          <span class="success-eyebrow">REGISTRATION COMPLETED</span>
          <h1>{{ props.title || finishProfile.title }}</h1>
          <p>{{ finishProfile.successMessage }}</p>
        </div>
        <div class="success-actions">
          <el-button v-if="finishType !== 'catalog' && resultRows.length" plain @click="listVisible = !listVisible">
            <template #icon><Icon icon="el-icon-List" /></template>
            {{ listVisible ? `收起${finishProfile.itemLabel}` : `查看${finishProfile.itemLabel}` }}
          </el-button>
          <el-button type="primary" @click="toSee">
            <template #icon><Icon icon="el-icon-Back" /></template>
            {{ finishProfile.returnLabel }}
          </el-button>
        </div>
      </section>

      <section class="summary-grid">
        <article v-for="item in summaryItems" :key="item.label" class="summary-card">
          <span :class="['summary-icon', item.tone]"><Icon :icon="item.icon" /></span>
          <div><small>{{ item.label }}</small><strong :class="{ 'time-value': item.isTime }">{{ item.value }}</strong></div>
        </article>
      </section>

      <section class="result-card">
        <header class="result-head">
          <div>
            <span class="result-status"><Icon icon="el-icon-CircleCheckFilled" /> {{ finishProfile.statusLabel }}</span>
            <h2>{{ entityName }}</h2>
            <p>{{ finishProfile.resultMessage }}</p>
          </div>
          <div class="result-meta">
            <span>{{ finishProfile.metaLabel }}</span>
            <strong>{{ entityMeta }}</strong>
          </div>
        </header>

        <div v-if="finishType === 'catalog' || listVisible" class="asset-list">
          <div class="asset-list-head">
            <strong>{{ finishProfile.itemListTitle }}</strong>
            <span>{{ finishType === 'catalog' ? `共 ${resultRows.length} 个目录` : `共 ${resultRows.length} 项` }}</span>
          </div>
          <div v-if="resultRows.length" class="asset-grid">
            <article v-for="row in resultRows" :key="row.tid || row.id || row.tableName || row.colEn" class="asset-item">
              <span class="asset-icon">
                <Icon :icon="resultRowIcon(row)" />
              </span>
              <div>
                <strong :title="resultRowName(row)">
                  {{ resultRowName(row) }}
                </strong>
                <small :title="resultRowCode(row)">{{ resultRowCode(row) }}</small>
              </div>
              <el-tag size="small" :type="finishType === 'datasource' ? 'info' : 'success'" effect="light" class="asset-count-tag">
                {{ resultRowBadge(row) }}
              </el-tag>
            </article>
          </div>
          <el-empty v-else :description="finishType === 'catalog' ? '暂未读取到已登记目录清单' : '暂未读取到数据表清单'" :image-size="72" />
        </div>
      </section>

      <div v-if="loadError" class="load-tip">
        <Icon icon="el-icon-WarningFilled" />
        {{ loadError }}
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from "vue";
import { useRegisterStore } from "@/store";
import dayjs from "dayjs";

const props = defineProps({
  data: { type: Array, default: () => [] },
  title: String,
  finishType: { type: String, default: "datasource" },
});

const store = useRegisterStore();
store.state.showAction = false;
const getActionRef = inject("getActionRef", ref(undefined));
const loading = ref(true);
const loadError = ref("");
const listVisible = ref(true);
const entityDetail = ref(null);
const tableRows = ref([]);
const submittedAt = dayjs().format("YYYY-MM-DD HH:mm:ss");

const finishType = computed(() => {
  const value = String(props.finishType || "datasource").toLowerCase();
  return ["datasource", "application", "catalog"].includes(value) ? value : "datasource";
});

const finishProfiles = {
  datasource: {
    title: "数据资源登记完成，待管理员审查", loadingTitle: "正在整理数据源登记结果", loadingMessage: "正在读取数据源、数据表和治理配置，请稍候。",
    successMessage: "数据源、数据表与治理配置均已保存并立即生效。", returnLabel: "返回数据源列表", statusLabel: "数据源已注册",
    resultMessage: "登记信息现在可以在数据资源列表和详情页面中查询、维护和使用。", metaLabel: "数据源类型", itemLabel: "数据表清单", itemListTitle: "登记数据表清单",
  },
  application: {
    title: "业务系统登记完成", loadingTitle: "正在整理业务系统登记结果", loadingMessage: "正在读取业务系统基础信息及关联资源，请稍候。",
    successMessage: "业务系统基础信息已保存，可继续登记其下的数据源和数据目录。", returnLabel: "返回业务系统列表", statusLabel: "业务系统已登记",
    resultMessage: "该业务系统已纳入源目录管理，可关联数据源、数据目录并持续维护。", metaLabel: "当前状态", itemLabel: "关联数据源", itemListTitle: "已关联数据源",
  },
  catalog: {
    title: "数据目录登记完成", loadingTitle: "正在整理数据目录登记结果", loadingMessage: "正在读取本次已登记的目录，请稍候。",
    successMessage: "数据目录及数据项已保存，可继续发布和维护目录内容。", returnLabel: "返回数据目录列表", statusLabel: "数据目录已登记",
    resultMessage: "目录信息已纳入源头数据目录，可在目录列表中查看、发布和维护。", metaLabel: "目录类型", itemLabel: "数据目录", itemListTitle: "已登记数据目录",
  },
};
const finishProfile = computed(() => finishProfiles[finishType.value]);

const firstItem = (value) => (Array.isArray(value) ? value[0] || {} : value || {});

const dbContext = computed(() => {
  return firstItem(store.data?.db);
});
const appContext = computed(() => firstItem(store.data?.app));
const catalogContext = computed(() => firstItem(store.data?.catalog));
// 完成页按本次已保存的目录展示，不能把目录中的字段当作登记结果。
const savedCatalogs = computed(() => {
  const source = store.data?.catalog;
  const rows = Array.isArray(source) ? source : source ? [source] : props.data;
  const unique = new Map();
  rows.forEach((row) => {
    const id = row?.tid || row?.id || row?.catalogId;
    if (id && (!row.assetType || row.assetType === "catalog")) unique.set(String(id), row);
  });
  return Array.from(unique.values());
});
const registeredCatalogs = savedCatalogs;
const entityContext = computed(() => ({ datasource: dbContext.value, application: appContext.value, catalog: catalogContext.value }[finishType.value] || {}));
const entityTid = computed(() => entityContext.value?.tid || entityContext.value?.id || entityContext.value?.dbId || entityContext.value?.appId || entityContext.value?.catalogId || "");
const entityName = computed(() => {
  if (finishType.value === "catalog" && registeredCatalogs.value.length > 1) return `本次已登记 ${registeredCatalogs.value.length} 个数据目录`;
  const detail = entityDetail.value || {};
  const context = entityContext.value || {};
  return detail.dbName || detail.appName || detail.catalogName || context.dbName || context.appName || context.applicationName || context.catalogName || context.name || "当前登记对象";
});
const entityMeta = computed(() => {
  const detail = entityDetail.value || {};
  const context = entityContext.value || {};
  if (finishType.value === "application") return detail.statusName || detail.appStatusName || detail.assetStatusName || "已登记";
  if (finishType.value === "catalog") return detail.catalogTypeName || detail.catalogType || context.catalogTypeName || context.catalogType || "数据目录";
  return detail.dbType || context.dbType || "数据库";
});

const resultRows = computed(() => {
  if (finishType.value === "datasource") return tableRows.value;
  const detail = entityDetail.value || {};
  const context = entityContext.value || {};
  if (finishType.value === "catalog") return registeredCatalogs.value;
  return detail.datasourceList || detail.dataSources || context.datasourceList || context.dataSources || [];
});
const summaryItems = computed(() => {
  if (finishType.value === "datasource") return [
    { label: "登记数据源", value: "1", icon: "el-icon-Coin", tone: "is-source" },
    { label: "已登记数据表", value: tableRows.value.length, icon: "el-icon-Grid", tone: "is-table" },
    { label: "完成时间", value: submittedAt, icon: "el-icon-Clock", tone: "is-time", isTime: true },
  ];
  if (finishType.value === "application") return [
    { label: "登记业务系统", value: "1", icon: "el-icon-Monitor", tone: "is-source" },
    { label: "关联数据源", value: resultRows.value.length, icon: "el-icon-Connection", tone: "is-table" },
    { label: "完成时间", value: submittedAt, icon: "el-icon-Clock", tone: "is-time", isTime: true },
  ];
  return [
    { label: "登记数据目录", value: registeredCatalogs.value.length, icon: "el-icon-FolderOpened", tone: "is-source" },
    { label: "已登记数据项", value: registeredCatalogs.value.reduce((count, row) => count + (row.catalogItems || row.items || []).length, 0), icon: "el-icon-Collection", tone: "is-table" },
    { label: "完成时间", value: submittedAt, icon: "el-icon-Clock", tone: "is-time", isTime: true },
  ];
});

function resultRowName(table) {
  if (finishType.value === "catalog") return displayText(table?.catalogName || table?.name || "未填写目录名称");
  return displayText(
    table?.tableComment || table?.tableNameCn || table?.colName || table?.itemName || table?.dbName || table?.assetName || table?.catalogName ||
    table?.catalogName ||
    table?.assetDesc ||
    "未填写名称"
  );
}
function resultRowCode(row) {
  if (finishType.value === "catalog") return displayText(row?.catalogNameEn || row?.catalogCode || row?.sourceTableName || "-");
  return displayText(row?.tableName || row?.tableNameEn || row?.colEn || row?.fieldName || row?.dbType || row?.catalogCode || "-");
}
function resultRowBadge(row) {
  if (finishType.value !== "datasource") return "已登记";
  return `${fieldCountOf(row)} 个字段`;
}
function fieldCountOf(row) {
  const candidates = [
    row?.fieldCount,
    row?.field_count,
    row?.columnCount,
    row?.column_count,
    row?.fieldsCount,
    row?.fields_count,
    row?.colCount,
    row?.col_count,
    row?.columns?.length,
    row?.fields?.length,
    row?.children?.length,
  ];
  const count = candidates.map((item) => Number(item)).find((item) => Number.isFinite(item) && item >= 0);
  return count ?? 0;
}
function displayText(value) {
  const text = String(value ?? "").trim();
  if (!text) return "";
  return repairMojibake(text);
}
function repairMojibake(text) {
  if (!looksLikeMojibake(text)) return text;
  try {
    const bytes = Uint8Array.from(Array.from(text).map((char) => char.charCodeAt(0) & 0xff));
    const decoded = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
    return /[\u4e00-\u9fff]/.test(decoded) ? decoded : text;
  } catch (error) {
    try {
      const decoded = decodeURIComponent(escape(text));
      return /[\u4e00-\u9fff]/.test(decoded) ? decoded : text;
    } catch (innerError) {
      return text;
    }
  }
}
function looksLikeMojibake(text) {
  return !/[\u4e00-\u9fff]/.test(text) && /[\u0080-\u00ff]/.test(text);
}
function resultRowIcon(row) {
  if (finishType.value === "catalog") return "el-icon-FolderOpened";
  if (finishType.value === "application") return "el-icon-Coin";
  return row?.tableType === "视图" ? "el-icon-DataAnalysis" : "table";
}

async function loadResult() {
  if (finishType.value === "catalog") {
    loading.value = true;
    loadError.value = "";
    // 上一步保存时已将表单、数据项和接口返回值合并进 store.data.catalog。
    // 完成页直接展示本次提交的快照，避免对每个目录再次请求详情。
    entityDetail.value = savedCatalogs.value[0] || null;
    if (!savedCatalogs.value.length) loadError.value = "未获取到本次已登记的目录，请返回目录列表查看";
    loading.value = false;
    return;
  }
  if (!entityTid.value) {
    loadError.value = "未获取到登记对象标识，页面将展示已保存的本地结果";
    loading.value = false;
    return;
  }
  loading.value = true;
  loadError.value = "";
  const detailUrl = { datasource: "/dst/database/detail", application: "/dst/application/detail", catalog: "/dst/catalog/detail" }[finishType.value];
  const detailResult = await Promise.allSettled([$common.post(detailUrl, { tid: entityTid.value })]);
  if (detailResult[0].status === "fulfilled") entityDetail.value = detailResult[0].value || null;
  if (finishType.value === "datasource") {
    const tableResult = await Promise.allSettled([$common.get("/dst/database/metadata/tables", { dbId: entityTid.value })]);
    if (tableResult[0].status === "fulfilled") {
      const rows = Array.isArray(tableResult[0].value) ? tableResult[0].value : [];
    const unique = new Map();
    rows.forEach((row) => {
      const key = String(row?.tableName || row?.tableNameEn || "").trim().toLowerCase();
      if (key) unique.set(key, row);
    });
    tableRows.value = Array.from(unique.values());
    }
  }
  if (detailResult[0].status === "rejected") {
    loadError.value = "部分登记结果刷新失败，请返回列表后重新查看";
  }
  loading.value = false;
}

function toSee() {
  getActionRef.value?.action?.("cancel");
}

onMounted(loadResult);
</script>

<style scoped lang="scss">
.finish-page {
  box-sizing: border-box;
  min-height: 100%;
  padding: 24px;
  overflow-y: auto;
  color: #182230;
  background:
    radial-gradient(circle at 8% 4%, rgb(18 183 106 / 10%), transparent 30%),
    linear-gradient(180deg, #f7fafc 0%, #f2f5f8 100%);
}

.finish-shell {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.finish-loading {
  display: grid;
  place-items: center;
  align-content: center;
  min-height: 420px;
  gap: 12px;
  color: #344054;
  text-align: center;
}

.finish-loading .loading-emblem {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  color: #175cd3;
  font-size: 30px;
  background: #eff4ff;
}

.finish-loading strong {
  color: #101828;
  font-size: 18px;
}

.finish-loading p {
  margin: 0;
  color: #667085;
  font-size: 14px;
}

.success-card {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  padding: 28px 30px;
  border: 1px solid #a6f4c5;
  border-radius: 18px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 16px 40px rgb(16 24 40 / 8%);
}

.success-emblem {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: #ecfdf3;

  span {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    border-radius: 50%;
    color: #fff;
    font-size: 26px;
    background: linear-gradient(135deg, #12b76a, #079455);
    box-shadow: 0 8px 18px rgb(18 183 106 / 24%);
  }
}

.success-copy {
  min-width: 0;

  h1 {
    margin: 4px 0 0;
    color: #101828;
    font-size: 26px;
    line-height: 1.3;
  }

  p {
    margin: 8px 0 0;
    color: #667085;
    font-size: 14px;
  }
}

.success-eyebrow {
  color: #067647;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 1.4px;
}

.success-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 84px;
  padding: 16px 18px;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
  background: #fff;

  small,
  strong {
    display: block;
  }

  small {
    color: #667085;
    font-size: 12px;
  }

  strong {
    margin-top: 3px;
    color: #101828;
    font-size: 23px;
  }

  .time-value {
    font-size: 15px;
  }
}

.summary-icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  font-size: 21px;

  &.is-source {
    color: #175cd3;
    background: #eff4ff;
  }

  &.is-table {
    color: #6941c6;
    background: #f4f3ff;
  }

  &.is-time {
    color: #067647;
    background: #ecfdf3;
  }
}

.result-card {
  margin-top: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 24px;
  border-bottom: 1px solid #eaecf0;

  h2 {
    margin: 8px 0 0;
    color: #101828;
    font-size: 18px;
  }

  p {
    margin: 5px 0 0;
    color: #667085;
    font-size: 13px;
  }
}

.result-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #067647;
  font-size: 12px;
  font-weight: 700;
}

.result-meta {
  flex: 0 0 auto;
  min-width: 130px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f8fafc;

  span,
  strong {
    display: block;
  }

  span {
    color: #667085;
    font-size: 11px;
  }

  strong {
    margin-top: 3px;
    font-size: 13px;
  }
}

.asset-list {
  padding: 18px 24px 24px;
}

.asset-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  strong {
    font-size: 14px;
  }

  span {
    color: #667085;
    font-size: 12px;
  }
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.asset-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 74px;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid #eaecf0;
  border-radius: 11px;
  background: #fcfcfd;

  > div {
    min-width: 0;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #344054;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
    font-weight: 700;
  }

  small {
    margin-top: 3px;
    color: #667085;
    font-size: 11px;
  }
}

.asset-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: #175cd3;
  background: #eff4ff;
}

.asset-count-tag {
  justify-self: end;
  max-width: 74px;
  min-width: 62px;
  text-align: center;

  :deep(.el-tag__content) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.load-tip {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  padding: 11px 13px;
  border: 1px solid #fedf89;
  border-radius: 10px;
  color: #93370d;
  background: #fffaeb;
  font-size: 12px;
}

@media (max-width: 1120px) {
  .asset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .success-card {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .success-actions {
    grid-column: 1 / -1;
  }

  .asset-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .finish-page {
    padding: 14px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .result-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
