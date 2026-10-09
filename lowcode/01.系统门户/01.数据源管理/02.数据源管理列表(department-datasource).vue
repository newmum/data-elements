<template>
  <div class="department-source-page">
    <section class="topbar">
      <div class="topbar-left">
        <h2>数据源管理</h2>
        <span class="topbar-scope">{{ currentScopeLabel }}</span>
      </div>
      <div class="topbar-actions">
        <el-input
          v-model.trim="state.keywordInput"
          class="search-input"
          clearable
          placeholder="搜索数据源 / 业务系统 / 库名"
          @keyup.enter="commitKeyword"
          @clear="clearKeyword"
        >
          <template #suffix>
            <button class="search-icon-button" type="button" title="search" @click.stop="commitKeyword">
              <Icon icon="el-icon-Search" />
            </button>
          </template>
        </el-input>
        <el-button icon="Refresh" :loading="state.loading" @click="loadData">刷新</el-button>
        <el-button type="primary" icon="Plus" @click="openDatasourceRegister">登记数据源</el-button>
      </div>
    </section>

    <section class="overview-grid">
      <article
        class="overview-card is-blue"
        :class="{ 'is-active': state.metricFilter === 'sourceIssues' }"
        role="button"
        tabindex="0"
        @click="applyMetricFilter('sourceIssues')"
        @keyup.enter="applyMetricFilter('sourceIssues')"
      >
        <span class="card-icon"><Icon icon="el-icon-Monitor" /></span>
        <div class="card-main">
          <small>数据源</small>
          <strong>
            {{ stats.datasourceTotal }}
            <em>个</em>
          </strong>
        </div>
        <div class="card-breakdown">
          <span :class="{ 'attention-danger': stats.unreachableDatasourceTotal > 0 }">无法连通 {{ stats.unreachableDatasourceTotal }} 个</span>
          <span :class="{ 'attention-warning': stats.pendingDatasourceTotal > 0 }">
            待登记 {{ stats.draftDatasourceTotal }} 个 / 未登记完成 {{ stats.incompleteDatasourceTotal }} 个
          </span>
        </div>
      </article>
      <article
        class="overview-card is-green"
        :class="{ 'is-active': state.metricFilter === 'unannotated' }"
        role="button"
        tabindex="0"
        @click="applyMetricFilter('unannotated')"
        @keyup.enter="applyMetricFilter('unannotated')"
      >
        <span class="card-icon"><Icon icon="complete" /></span>
        <div class="card-main">
          <small>表标注</small>
          <strong>
            {{ stats.annotatedTableTotal }}
            <em>/ {{ stats.tableTotal }} 张</em>
          </strong>
        </div>
        <div class="card-breakdown">
          <span :class="{ 'attention-warning': stats.unannotatedTotal > 0 }">
            未完成标注 {{ stats.unannotatedTotal }}
          </span>
        </div>
      </article>
      <article
        class="overview-card is-purple"
        :class="{ 'is-active': state.metricFilter === 'tableIssues' }"
        role="button"
        tabindex="0"
        @click="applyMetricFilter('tableIssues')"
        @keyup.enter="applyMetricFilter('tableIssues')"
      >
        <span class="card-icon"><Icon icon="table" /></span>
        <div class="card-main">
          <small>表登记</small>
          <strong>{{ stats.registeredTableTotal }}<em>/ {{ stats.tableTotal }} 张</em></strong>
        </div>
        <div class="card-breakdown">
          <span :class="{ 'attention-warning': stats.problemTableTotal > 0 }">
            未完成登记 {{ stats.problemTableTotal }}
          </span>
        </div>
      </article>
      <article
        class="overview-card is-orange"
        :class="{ 'is-active': state.metricFilter === 'changed' }"
        role="button"
        tabindex="0"
        @click="applyMetricFilter('changed')"
        @keyup.enter="applyMetricFilter('changed')"
      >
        <span class="card-icon"><Icon icon="todo" /></span>
        <div class="card-main">
          <small>表变更</small>
          <strong :class="{ 'is-text-value': stats.changedTotal === 0 }">
            {{ stats.changedTotal > 0 ? stats.changedTotal : "未发现变更" }}
            <em v-if="stats.changedTotal > 0">张</em>
          </strong>
        </div>
        <div v-if="stats.changedTotal > 0" class="card-breakdown is-change">
          <span v-if="stats.addedTableTotal > 0" class="is-added">新增 {{ stats.addedTableTotal }}</span>
          <span v-if="stats.modifiedTableTotal > 0" class="is-modified">修改 {{ stats.modifiedTableTotal }}</span>
          <span v-if="stats.deletedTableTotal > 0" class="is-deleted">删除 {{ stats.deletedTableTotal }}</span>
        </div>
      </article>
    </section>

    <section class="work-card management-workspace">
      <aside class="organization-tree-panel" aria-label="组织机构">
        <el-alert v-if="state.treeError" :title="state.treeError" type="error" :closable="false" />
        <div class="organization-tree-panel__head">
          <div>
            <strong>组织机构</strong>
          </div>
          <el-tag size="small" effect="light">{{ stats.datasourceTotal }} 个</el-tag>
        </div>
        <UTree
          v-if="treeData.length"
          class="organization-tree"
          :class="{ 'is-loading': state.treeLoading }"
          ref="treeRef"
          :loading="state.treeLoading"
          :data="treeData"
          node-key="value"
          :search="true"
          :expand="false"
          :default-expand-all="false"
          :auto-expand-first-node="false"
          :expand-on-click-node="false"
          search-placeholder="请输入组织机构名称"
          @node-click="selectTreeNode"
        >
          <template #node="{ node }">
            <u-tree-node
              show-icon
              style="--gap: 0; --node-count-color: #323643"
              :keyword="treeRef?.searchValue"
              :label="node.label"
            >
              <template #count>
                <span v-if="getTreeNodeCount(node.data) > 0">({{ getTreeNodeCount(node.data) }})</span>
                <span v-else></span>
              </template>
              <template #icon>
                <Icon
                  :icon="treeNodeIcon(node)"
                  :color="treeNodeColor(node)"
                  class="mr-1"
                  :size="treeNodeSize(node)"
                />
              </template>
            </u-tree-node>
          </template>
        </UTree>
        <el-empty v-else :image-size="58" description="暂无可管理部门" />
      </aside>

      <div class="application-workspace">
      <el-alert v-if="state.listError" :title="state.listError" type="error" :closable="false" />
      <div class="work-toolbar">
        <el-segmented v-model="state.filter" :options="filterOptions" @change="clearMetricFilter" />
      </div>

      <el-skeleton v-if="state.loading && !state.list.length" class="source-skeleton" :rows="8" animated />

      <div v-else-if="visibleList.length === 0" class="source-empty-state">
        <div class="source-empty-illustration" aria-hidden="true">
          <span class="empty-db empty-db-main">
            <Icon icon="database" />
          </span>
          <span class="empty-db empty-db-sub">
            <Icon icon="connection" />
          </span>
          <span class="empty-search">
            <Icon icon="search" />
          </span>
        </div>
        <strong>暂无匹配的数据源</strong>
        <p>当前筛选条件下没有数据源，可切换应用系统、调整关键词或查看其他状态。</p>
      </div>

      <div v-else class="source-list">
        <article
          v-for="item in displayList"
          :key="item.tid"
          class="source-card"
          @click="handleCardClick(item)"
          :class="{
            'is-expanded': isExpanded(item),
            'can-expand': canExpand(item),
            'has-todo': sourceTodoCount(item) > 0,
          }"
        >
          <div class="source-head">
            <div class="source-title">
              <el-tooltip :content="sourceIconTip(item)" placement="top" :show-after="300">
                <span
                  class="source-icon"
                  :class="{
                    'is-elasticsearch': sourceTypeIcon(item) === 'elasticsearch',
                    'is-data-report': isDataReportSource(item),
                    'is-data-push': isDataPushSource(item)
                  }"
                >
                  <Icon :icon="sourceTypeIcon(item)" />
                  <i class="source-connection-status" :class="connectionStatusClass(item)">
                    <Icon :icon="connectionStatusIcon(item)" />
                  </i>
                </span>
              </el-tooltip>
              <div class="source-name">
                <div class="source-name-line">
                  <button
                    class="source-name-button"
                    type="button"
                    :title="item.dbName || '未命名数据源'"
                    @click.stop="openDatasourceDetail(item)"
                  >
                    {{ item.dbName || "未命名数据源" }}
                  </button>
                  <el-tag class="status-title-tag" :type="statusTagType(item)" effect="light" size="small">
                    {{ statusText(item) }}
                  </el-tag>
                </div>
                <p>{{ sourceMetaText(item) }}</p>
                <div class="source-audit-meta" :title="`创建人：${sourceCreatorText(item)}；最后修改时间：${sourceUpdatedTimeText(item)}`">
                  <span>创建人：{{ sourceCreatorText(item) }}</span>
                  <span>最后修改：{{ sourceUpdatedTimeText(item) }}</span>
                </div>
              </div>
              <button
                v-if="canExpand(item)"
                class="source-expand-arrow"
                type="button"
                :title="isExpanded(item) ? '收起数据表' : '展开数据表'"
                @click.stop="toggleExpand(item)"
              >
                <Icon :icon="isExpanded(item) ? 'up' : 'down'" />
              </button>
            </div>

            <div class="source-side">
              <div class="source-actions">
                <el-button
                  class="register-button"
                  type="primary"
                  :loading="isOpeningRegistration(item)"
                  @click.stop="handlePrimaryAction(item)"
                >
                  <Icon :icon="isRegistrationComplete(item) ? 'el-icon-View' : 'el-icon-Tickets'" />
                  {{ isRegistrationComplete(item) ? "查看数据源" : "登记数据源" }}
                </el-button>
                <el-button class="edit-button" @click.stop="openDatasourceEdit(item)">
                  <Icon icon="el-icon-EditPen" />
                  编辑
                </el-button>
                <el-button
                  class="delete-button"
                  :loading="state.deletedIds.includes(item.tid)"
                  @click.stop="deleteDatasource(item)"
                >
                  <Icon icon="el-icon-Delete" />
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <div class="source-metrics">
            <span>数据表 {{ item.tableNum || 0 }} 张</span>
            <span>已标注 {{ item.annotatedTableNum || 0 }} 张</span>
            <span :class="{ danger: normalizeNumber(item.unannotatedTableNum) > 0 }">待标注 {{ item.unannotatedTableNum || 0 }} 张</span>
            <span>字典表 {{ item.dictionaryTableNum || 0 }} 张</span>
            <span>业务表 {{ item.businessTableNum || 0 }} 张</span>
            <span>日志表 {{ item.logTableNum || 0 }} 张</span>
            <span :class="{ warning: normalizeNumber(item.changedTableNum) > 0 }">发现变更 {{ item.changedTableNum || 0 }} 张</span>
          </div>

          <transition name="table-panel">
            <div v-if="isExpanded(item)" class="table-panel" @click.stop>
              <div class="table-panel-head">
                <strong>已登记数据表</strong>
                <span>{{ tableTotal(item) }} 张</span>
              </div>

              <el-skeleton v-if="tableLoadState(item).loading" :rows="4" animated />
              <div v-else-if="tableLoadState(item).error" class="table-load-error" role="alert">
                <span>{{ tableLoadState(item).error }}</span>
                <el-button size="small" @click.stop="loadTablePage(item, tableLoadState(item).requestedPage || 1)">重试</el-button>
              </div>
              <div v-else-if="pagedTables(item).length > 0" class="table-preview-scroll">
              <el-table
                :data="pagedTables(item)"
                border
                stripe
                size="small"
                class="table-preview"
              >
                <el-table-column label="数据表名称" min-width="260">
                  <template #default="{ row }">
                    <div class="table-name-cell is-link" @click.stop="openTableDetail(row, item)">
                      <span class="table-icon" :class="{ 'is-view': isView(row) }">
                        <Icon :icon="isView(row) ? 'view' : 'table'" />
                      </span>
                      <div>
                        <div class="table-cn-line">
                          <button class="table-name-button" type="button" :title="tableChineseName(row)">{{ tableChineseName(row) }}</button>
                          <el-tag
                            v-if="hasChange(row)"
                            :type="changeTagType(row.changeStatus)"
                            effect="plain"
                            size="small"
                          >
                            {{ changeText(row.changeStatus) }}
                          </el-tag>
                        </div>
                        <small>{{ row.tableName || "-" }}</small>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="业务类型" width="120">
                  <template #default="{ row }">{{ businessTypeText(row.businessType) }}</template>
                </el-table-column>
                <el-table-column prop="fieldCount" label="字段数" width="90" align="center" />
                <el-table-column label="创建时间" width="190">
                  <template #default="{ row }">
                    <div class="table-created-cell">
                      <span>{{ tableCreatedTimeText(row) }}</span>
                      <el-tag v-if="tableCreatedDayTag(row)" :type="tableCreatedDayTag(row) === '今日' ? 'danger' : 'warning'" effect="light" size="small">
                        {{ tableCreatedDayTag(row) }}
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="最后修改时间" width="190">
                  <template #default="{ row }">
                    <div class="table-created-cell">
                      <span>{{ tableUpdatedTimeText(row) }}</span>
                      <el-tag v-if="tableUpdatedDayTag(row)" :type="tableUpdatedDayTag(row) === '今日' ? 'danger' : 'warning'" effect="light" size="small">
                        {{ tableUpdatedDayTag(row) }}
                      </el-tag>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              </div>

              <div v-if="tableTotalPages(item) > 1" class="table-panel-pager">
                <span>每页 10 条，当前第 {{ tablePage(item) }} / {{ tableTotalPages(item) }} 页</span>
                <div class="table-panel-pager-actions">
                  <el-button size="small" plain :disabled="tableLoadState(item).loading || tablePage(item) <= 1" @click.stop="changeTablePage(item, -1)">
                    上一页
                  </el-button>
                  <el-button size="small" plain :disabled="tableLoadState(item).loading || tablePage(item) >= tableTotalPages(item)" @click.stop="changeTablePage(item, 1)">
                    下一页
                  </el-button>
                </div>
              </div>

              <el-empty v-if="!tableLoadState(item).loading && !tableLoadState(item).error && tableTotal(item) === 0" description="暂无已登记数据表" :image-size="72" />
            </div>
          </transition>
        </article>
        <div v-if="canLoadMoreSources" class="source-load-more">
          <span>已展示 {{ displayList.length }} / {{ visibleList.length }} 个数据源</span>
          <el-button plain @click.stop="loadMoreSources">
            加载更多（+{{ nextSourceLoadCount }}）
          </el-button>
        </div>
      </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, computed, nextTick, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { useRegisterModal } from "@/composables";

const { openRegisterModal, completedVersion } = useRegisterModal();
const router = useRouter();
const treeRef = ref();
const orgTreeSource = ref([]);
const treeData = ref([
  { name: "全部部门", label: "全部部门", value: "", type: "org", children: [] },
]);
const treeStatistics = ref({ total: 0, orgCounts: {}, orgApps: {} });
const countMap = ref({});
const SOURCE_BATCH_SIZE = 24;

const state = reactive({
  loading: false,
  treeLoading: false,
  listError: "",
  treeError: "",
  tableLoads: {},
  keyword: "",
  keywordInput: "",
  filter: "all",
  metricFilter: "",
  sourceDisplayLimit: SOURCE_BATCH_SIZE,
  leftSelectNode: treeData.value[0],
  expanded: [],
  deletedIds: [],
  openingRegistrationIds: [],
  list: [],
});

const filterOptions = [
  { label: "全部", value: "all" },
  { label: "待登记", value: "pending" },
  { label: "未登记完成", value: "incomplete" },
  { label: "已完成", value: "completed" },
  { label: "待标注", value: "unannotated" },
  { label: "有变更", value: "changed" },
];

const userOrgName = computed(() => $user?.orgName || "当前部门");

const normalizeNumber = (value) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const firstPositive = (...values) => {
  for (const value of values) {
    const n = normalizeNumber(value);
    if (n > 0) return n;
  }
  return 0;
};

const firstNumber = (...values) => {
  for (const value of values) {
    if (value === null || value === undefined || value === "") continue;
    const n = Number(value);
    if (Number.isFinite(n)) return n;
  }
  return 0;
};

const firstText = (...values) => {
  for (const value of values) {
    if (value !== null && value !== undefined && String(value).trim() !== "") {
      return String(value).trim();
    }
  }
  return "";
};

// 抓取类数据源的连接模式等配置会保存在 poolCfg 中。列表接口为了轻量展示
// 不返回完整配置，直接用列表行进入登记流程时必须先补齐，才能与登记第一步的
// “下一步”保持完全一致的探查/快照读取方式。
const parsePoolCfg = (value) => {
  if (!value) return {};
  if (typeof value === "object" && !Array.isArray(value)) return value;
  if (typeof value !== "string") return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

const firstPresent = (...values) => {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== "") return value;
  }
  return undefined;
};

const mergeDatasourceDetail = (summary = {}, detail = {}) => {
  const merged = { ...summary };
  Object.entries(detail || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") merged[key] = value;
  });
  const poolCfg = parsePoolCfg(merged.poolCfg ?? merged.pool_cfg);
  return {
    ...merged,
    tid: firstText(merged.tid, merged.id, summary.tid, summary.id),
    id: firstText(merged.id, merged.tid, summary.id, summary.tid),
    dbType: firstText(
      merged.dbType,
      merged.db_type,
      merged.databaseType,
      poolCfg.dbType,
      poolCfg.db_type,
      poolCfg.databaseType
    ),
    accessMode: firstText(
      merged.accessMode,
      merged.access_mode,
      merged.dataAccessMode,
      merged.data_access_mode,
      poolCfg.accessMode,
      poolCfg.access_mode,
      poolCfg.dataAccessMode,
      poolCfg.data_access_mode
    ),
    // 0 / false 均是有效配置，不能用 truthy 判断，否则会把抓取型数据源误当作直连数据源。
    showConnect: firstPresent(
      merged.showConnect,
      merged.show_connect,
      poolCfg.showConnect,
      poolCfg.show_connect
    ),
  };
};

const isTableAnnotated = (table) =>
  Number(table.annotated ?? table.isAnnotated ?? table.is_annotated ?? 0) === 1 ||
  ["1", "true", "yes", "completed"].includes(
    String(table.annotatedStatus || table.annotated_status || "").toLowerCase()
  );

const isTableRegistered = (table) =>
  normalizeNumber(table.assetStatus ?? table.asset_status) === 2;

const tableHasIssue = (table) => !isTableRegistered(table);

const tableChangeStatus = (table) => {
  const status = String(table.changeStatus || table.change_status || "").toLowerCase();
  if (["added", "add", "new"].includes(status)) return "added";
  if (["deleted", "delete", "removed"].includes(status)) return "deleted";
  if (["modified", "modify", "changed", "change", "reset"].includes(status)) return "modified";
  return "unchanged";
};

const tableArrayStats = (tables) => {
  const result = {
    tableNum: tables.length,
    annotatedTableNum: 0,
    unannotatedTableNum: 0,
    registeredTableNum: 0,
    unregisteredTableNum: 0,
    businessTableNum: 0,
    dictionaryTableNum: 0,
    logTableNum: 0,
    coreTableNum: 0,
    registeredCoreTableNum: 0,
    deferredTableNum: 0,
    issueTableNum: 0,
    problemTableNum: 0,
    changedTableNum: 0,
    addedTableNum: 0,
    modifiedTableNum: 0,
    deletedTableNum: 0,
  };
  tables.forEach((table) => {
    if (isTableAnnotated(table)) result.annotatedTableNum += 1;
    else result.unannotatedTableNum += 1;

    if (isTableRegistered(table)) result.registeredTableNum += 1;
    else result.unregisteredTableNum += 1;

    const businessType = String(table.businessType || table.business_type || "").toLowerCase();
    const isCoreTable = ["business", "业务表", "dict", "dictionary", "字典表", "log", "日志表"].includes(businessType);
    const isDeferredTable = ["暂不处理", "暂不登记", "不确定"].includes(businessType);
    if (isCoreTable) {
      result.coreTableNum += 1;
      if (isTableRegistered(table)) result.registeredCoreTableNum += 1;
    }
    if (isDeferredTable) result.deferredTableNum += 1;
    if (["business", "业务表"].includes(businessType)) result.businessTableNum += 1;
    else if (["dict", "dictionary", "字典表"].includes(businessType)) result.dictionaryTableNum += 1;
    else if (["log", "日志表"].includes(businessType)) result.logTableNum += 1;

    if (tableHasIssue(table)) result.issueTableNum += 1;
    if (!isTableRegistered(table) || tableHasIssue(table)) result.problemTableNum += 1;

    const changeStatus = tableChangeStatus(table);
    if (changeStatus === "added") {
      result.addedTableNum += 1;
      result.changedTableNum += 1;
    } else if (changeStatus === "deleted") {
      result.deletedTableNum += 1;
      result.changedTableNum += 1;
    } else if (changeStatus === "modified") {
      result.modifiedTableNum += 1;
      result.changedTableNum += 1;
    }
  });
  return result;
};

const normalizeItem = (item) => {
  const tables = Array.isArray(item.tables) ? item.tables : [];
  const fallbackStats = tableArrayStats(tables);
  const tableNum = firstNumber(item.tableNum, item.table_num, fallbackStats.tableNum);
  const annotatedTableNum = firstNumber(item.annotatedTableNum, item.annotated_table_num, fallbackStats.annotatedTableNum);
  const unannotatedTableNum = firstNumber(
    item.unannotatedTableNum,
    item.unannotated_table_num,
    Math.max(tableNum - annotatedTableNum, 0),
    fallbackStats.unannotatedTableNum
  );
  return {
    ...item,
    tid: item.tid || item.id,
    id: item.id || item.tid,
    dbName: item.dbName || item.db_name,
    appName: item.appName || item.app_name,
    appId: item.appId || item.app_id,
    dbType: item.dbType || item.db_type || item.databaseType,
    accessMode: firstText(
      item.accessMode,
      item.access_mode,
      item.dataAccessMode,
      item.data_access_mode
    ),
    database: item.database || item.schema || item.dbNameEn,
    schema: item.schema,
    description: firstText(
      item.description,
      item.dbDescription,
      item.db_description,
      item.datasourceDescription,
      item.datasource_description,
      item.dataSourceDescription,
      item.data_source_description,
      item.remark,
      item.remarks,
      item.comment
    ),
    orgId: item.orgId || item.org_id,
    orgName: firstText(item.orgName, item.org_name, item.sourceOrgName, item.source_org_name, userOrgName.value),
    orgPath: firstText(item.orgPath, item.org_path, item.organizationPath, item.organization_path),
    tableNum,
    annotatedTableNum,
    unannotatedTableNum,
    registeredTableNum: firstNumber(item.registeredTableNum, item.registered_table_num, fallbackStats.registeredTableNum),
    unregisteredTableNum: firstNumber(item.unregisteredTableNum, item.unregistered_table_num, Math.max(tableNum - firstNumber(item.registeredTableNum, item.registered_table_num, fallbackStats.registeredTableNum), 0), fallbackStats.unregisteredTableNum),
    businessTableNum: firstNumber(item.businessTableNum, item.business_table_num, fallbackStats.businessTableNum),
    dictionaryTableNum: firstNumber(item.dictionaryTableNum, item.dictionary_table_num, fallbackStats.dictionaryTableNum),
    logTableNum: firstNumber(item.logTableNum, item.log_table_num, fallbackStats.logTableNum),
    coreTableNum: firstNumber(item.coreTableNum, item.core_table_num, fallbackStats.coreTableNum),
    registeredCoreTableNum: firstNumber(
      item.registeredCoreTableNum,
      item.registered_core_table_num,
      fallbackStats.registeredCoreTableNum
    ),
    deferredTableNum: firstNumber(
      item.deferredTableNum,
      item.deferred_table_num,
      item.pendingRegistrationTableNum,
      item.pending_registration_table_num,
      fallbackStats.deferredTableNum
    ),
    issueTableNum: firstNumber(item.issueTableNum, item.issue_table_num, fallbackStats.issueTableNum),
    problemTableNum: firstNumber(item.problemTableNum, item.problem_table_num, fallbackStats.problemTableNum),
    changedTableNum: firstNumber(item.changedTableNum, item.changed_table_num, fallbackStats.changedTableNum),
    addedTableNum: firstNumber(item.addedTableNum, item.added_table_num, fallbackStats.addedTableNum),
    modifiedTableNum: firstNumber(item.modifiedTableNum, item.modified_table_num, fallbackStats.modifiedTableNum),
    deletedTableNum: firstNumber(item.deletedTableNum, item.deleted_table_num, fallbackStats.deletedTableNum),
    showConnect: item.showConnect ?? item.show_connect,
    connectionStatus:
      item.connectionStatus || item.connection_status || item.testStatus || item.test_status || "",
    assetStatus: item.assetStatus ?? item.asset_status,
    createdBy: firstText(item.createdBy, item.created_by, item.creatorId, item.creator_id),
    createdByName: firstText(
      item.createdByName,
      item.created_by_name,
      item.creatorName,
      item.creator_name,
      item.createdUserName,
      item.created_user_name
    ),
    updatedTime: item.updatedTime || item.updated_time || item.updateTime || item.update_time || "",
    createdTime: item.createdTime || item.created_time || item.createTime || item.create_time || "",
    tables,
  };
};

// 数据源管理列表以最近一次修改时间为唯一的主排序依据。接口虽已按时间返回，
// 但页面会按应用系统分组，必须在分组前后再次统一排序，避免应用名称打乱顺序。
const datasourceUpdatedAt = (item) => {
  const value = String(item?.updatedTime || item?.updated_time || item?.createdTime || item?.created_time || "").trim();
  if (!value) return 0;
  const parsed = Date.parse(value.replace(" ", "T"));
  return Number.isFinite(parsed) ? parsed : 0;
};
const compareDatasourceUpdated = (left, right) => {
  const difference = datasourceUpdatedAt(right) - datasourceUpdatedAt(left);
  if (difference !== 0) return difference;
  return String(left?.dbName || "").localeCompare(String(right?.dbName || ""), "zh-Hans-CN");
};

// 列表和组织树独立落地；一次失败不丢弃另一项成功结果。
let loadGeneration = 0;
const loadSourceOverview = async (generation) => {
  state.loading = true;
  state.listError = "";
  try {
    const result = await $common.post("/dst/database/departmentDataSources", { mode: "summary" });
    if (generation !== loadGeneration) return;
    const list = Array.isArray(result?.list) ? result.list : Array.isArray(result) ? result : [];
    state.list = list.map(normalizeItem).sort(compareDatasourceUpdated);
    state.expanded = [];
    state.tableLoads = {};
    state.sourceDisplayLimit = SOURCE_BATCH_SIZE;
  } catch (error) {
    if (generation !== loadGeneration) return;
    console.error("加载数据源概要失败:", error);
    state.listError = "数据源加载失败，请点击刷新重试。";
  } finally {
    if (generation === loadGeneration) state.loading = false;
  }
};
const loadOrganizationTree = async (generation) => {
  state.treeLoading = true;
  state.treeError = "";
  try {
    const [orgTree, statistics] = await Promise.all([
      $common.post("/sym/org/getOrgTree", { parentId: $user.orgRootId }),
      $common.post("/dst/database/treeStatistics", { keyword: "", dbType: "", registrationState: "", orgIds: [] }),
    ]);
    if (generation !== loadGeneration) return;
    orgTreeSource.value = Array.isArray(orgTree) ? orgTree : [];
    treeStatistics.value = { total: normalizeNumber(statistics?.total), orgCounts: statistics?.orgCounts || {}, orgApps: statistics?.orgApps || {} };
    countMap.value = { org: treeStatistics.value.orgCounts };
    rebuildTree();
    state.leftSelectNode = treeData.value[0];
  } catch (error) {
    if (generation !== loadGeneration) return;
    console.error("加载组织机构失败:", error);
    state.treeError = "组织机构加载失败，数据源列表仍可使用。";
  } finally {
    if (generation === loadGeneration) state.treeLoading = false;
  }
};
const loadData = () => {
  const generation = ++loadGeneration;
  // 即使概要刷新失败，也不能让已作废的展开请求永久停在 loading。
  for (const [key, cache] of Object.entries(state.tableLoads)) {
    if (cache.loading) state.tableLoads[key] = { ...cache, loading: false, requestId: undefined, error: "加载已取消，请重试。" };
  }
  return Promise.allSettled([loadSourceOverview(generation), loadOrganizationTree(generation)]);
};

const sourceTodoCount = (item) =>
  normalizeNumber(item.unannotatedTableNum) + normalizeNumber(item.changedTableNum);

const matchedKeyword = (item) => {
  const keyword = (state.keyword || "").toLowerCase();
  if (!keyword) return true;
  return [item.dbName, item.appName, item.database, item.schema, item.dbType]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(keyword));
};

const commitKeyword = () => {
  state.keyword = String(state.keywordInput || "").trim();
};

const clearKeyword = () => {
  state.keywordInput = "";
  state.keyword = "";
};

const matchedFilter = (item) => {
  if (state.filter === "pending") {
    return normalizeNumber(item.assetStatus ?? item.asset_status) === 0;
  }
  if (state.filter === "incomplete") return normalizeNumber(item.assetStatus ?? item.asset_status) === 1;
  if (state.filter === "completed") return isRegistrationComplete(item);
  if (state.filter === "unannotated") return normalizeNumber(item.unannotatedTableNum) > 0;
  if (state.filter === "changed") return normalizeNumber(item.changedTableNum) > 0;
  return true;
};

const matchedMetricFilter = (item) => {
  if (state.metricFilter === "sourceIssues") return sourceHasPrimaryIssue(item);
  if (state.metricFilter === "unannotated") return normalizeNumber(item.unannotatedTableNum) > 0;
  if (state.metricFilter === "tableIssues") return normalizeNumber(item.problemTableNum) > 0;
  if (state.metricFilter === "changed") return normalizeNumber(item.changedTableNum) > 0;
  return true;
};

const filteredBeforeOrganization = computed(() =>
  state.list.filter((item) => matchedKeyword(item) && matchedFilter(item) && matchedMetricFilter(item))
);

const getTreeNodeCount = (node) => {
  if (!node) return 0;
  if (node.type === "app") return normalizeNumber(node.count);
  if (!node.value) return normalizeNumber(treeStatistics.value.total);
  const ownCount = normalizeNumber(countMap.value.org?.[node.value]);
  const orgChildren = (node.children || []).filter((child) => child.type !== "app");
  return ownCount + orgChildren.reduce((total, child) => total + getTreeNodeCount(child), 0);
};

const filterZeroNodes = (nodes) =>
  (nodes || [])
    .filter((node) => getTreeNodeCount(node) > 0)
    .map((node) => ({ ...node, children: filterZeroNodes(node.children || []) }));

const appendApplicationNodes = (nodes) =>
  (nodes || []).map((node) => {
    const orgChildren = appendApplicationNodes(node.children || []);
    const apps = orgChildren.length === 0
      ? (treeStatistics.value.orgApps?.[node.value] || []).map((app) => ({
          ...app, type: "app", name: app.label, serialNumber: app.value, children: [],
        }))
      : [];
    return { ...node, type: "org", children: [...orgChildren, ...apps] };
  });

const applyDefaultTreeState = () => {
  nextTick(() => nextTick(() => {
    const tree = treeRef.value?.getTree?.();
    if (!tree) return;
    const expandOrganizations = (nodes) => {
      (nodes || []).forEach((item) => {
        const orgChildren = (item.children || []).filter((child) => child.type !== "app");
        const treeNode = tree.getNode(item.value);
        if (treeNode) {
          treeNode.expanded = isSystemAdministratorScope.value
            ? orgChildren.length > 0
            : (item.children || []).length > 0;
        }
        expandOrganizations(orgChildren);
      });
    };
    expandOrganizations(treeData.value);
    tree.setCurrentNode?.(treeData.value[0]);
  }));
};

const rebuildTree = () => {
  treeData.value[0].children = appendApplicationNodes(filterZeroNodes(orgTreeSource.value));
  applyDefaultTreeState();
};

const collectTreeNodeOrgIds = (node) => {
  if (!node || node.type === "app") return [];
  return [node.value, ...(node.children || []).flatMap((child) => collectTreeNodeOrgIds(child))].filter(Boolean);
};

const matchesSelectedTreeNode = (item) => {
  const node = state.leftSelectNode;
  if (!node || !node.value) return true;
  if (node.type === "app") return String(item.appId || "") === String(node.value);
  return collectTreeNodeOrgIds(node).map(String).includes(String(item.orgId || ""));
};

const selectTreeNode = (node) => {
  state.leftSelectNode = node || treeData.value[0];
  state.expanded = [];
  state.sourceDisplayLimit = SOURCE_BATCH_SIZE;
};

const treeNodeIcon = (node) => (node?.data?.type === "app" ? "app" : node?.expanded ? "folder-open" : "folder");
const treeNodeColor = (node) => (node?.data?.type === "app" ? "#7a5af8" : "#0b8bf9");
const treeNodeSize = (node) => (node?.data?.type === "app" ? "19" : "23");
const isSystemAdministratorScope = computed(() => Object.keys(treeStatistics.value.orgCounts || {}).length > 1);
const currentScopeLabel = computed(() => {
  const selected = state.leftSelectNode;
  if (selected?.value && selected?.label) return selected.label;
  if (isSystemAdministratorScope.value) return "全部部门";
  return String($user.orgName || $user.org_name || treeData.value[0]?.children?.[0]?.label || "当前部门");
});
const visibleList = computed(() => filteredBeforeOrganization.value.filter(matchesSelectedTreeNode));
const displayList = computed(() => visibleList.value.slice(0, state.sourceDisplayLimit));
const canLoadMoreSources = computed(() => displayList.value.length < visibleList.value.length);
const nextSourceLoadCount = computed(() => Math.min(SOURCE_BATCH_SIZE, visibleList.value.length - displayList.value.length));
const loadMoreSources = () => {
  state.sourceDisplayLimit = Math.min(state.sourceDisplayLimit + SOURCE_BATCH_SIZE, visibleList.value.length);
};

const stats = computed(() => {
  const appKeys = new Set();
  const result = state.list.reduce(
    (acc, item) => {
      acc.datasourceTotal += 1;
      if (isConnectionUnavailable(item)) {
        acc.unreachableDatasourceTotal += 1;
      }
      if (hasAppSystem(item)) {
        appKeys.add(appSystemKey(item));
        acc.appDatasourceTotal += 1;
      } else {
        acc.unboundAppDatasourceTotal += 1;
      }
      acc.tableTotal += normalizeNumber(item.tableNum);
      acc.annotatedTableTotal += normalizeNumber(item.annotatedTableNum);
      acc.unannotatedTotal += normalizeNumber(item.unannotatedTableNum);
      acc.registeredTableTotal += normalizeNumber(item.registeredTableNum);
      acc.problemTableTotal += normalizeNumber(item.problemTableNum);
      acc.changedTotal += normalizeNumber(item.changedTableNum);
      acc.addedTableTotal += normalizeNumber(item.addedTableNum);
      acc.modifiedTableTotal += normalizeNumber(item.modifiedTableNum);
      acc.deletedTableTotal += normalizeNumber(item.deletedTableNum);
      if (isRegistrationComplete(item)) {
        acc.completedTotal += 1;
      } else {
        acc.pendingDatasourceTotal += 1;
        if (normalizeNumber(item.assetStatus ?? item.asset_status) === 1) acc.incompleteDatasourceTotal += 1;
        else acc.draftDatasourceTotal += 1;
      }
      return acc;
    },
    {
      appSystemTotal: 0,
      appDatasourceTotal: 0,
      unboundAppDatasourceTotal: 0,
      datasourceTotal: 0,
      completedTotal: 0,
      pendingDatasourceTotal: 0,
      draftDatasourceTotal: 0,
      incompleteDatasourceTotal: 0,
      unreachableDatasourceTotal: 0,
      tableTotal: 0,
      annotatedTableTotal: 0,
      unannotatedTotal: 0,
      registeredTableTotal: 0,
      problemTableTotal: 0,
      changedTotal: 0,
      addedTableTotal: 0,
      modifiedTableTotal: 0,
      deletedTableTotal: 0,
    }
  );
  result.appSystemTotal = appKeys.size;
  return result;
});

const isExpanded = (item) => state.expanded.includes(item.tid);
const canExpand = (item) => normalizeNumber(item.tableNum) > 0;

const toggleExpand = (item) => {
  if (!canExpand(item)) return;
  if (isExpanded(item)) {
    state.expanded = state.expanded.filter((id) => id !== item.tid);
  } else {
    state.expanded.push(item.tid);
    if (!tableLoadState(item).loaded && !tableLoadState(item).loading) loadTablePage(item, 1);
  }
};

const handleCardClick = (item) => {
  if (!canExpand(item)) return;
  toggleExpand(item);
};

const tablePageSize = 10;
const tablePageKey = (item) => String(item.tid || item.id || "");
const tableLoadState = (item) => state.tableLoads[tablePageKey(item)] || {};
const tableTotal = (item) => normalizeNumber(tableLoadState(item).total ?? item.registeredTableNum);
const tableTotalPages = (item) => Math.max(1, Math.ceil(tableTotal(item) / tablePageSize));
const tablePage = (item) => tableLoadState(item).pageNum || 1;
const pagedTables = (item) => tableLoadState(item).list || [];

// 只在展开或翻页时读取当前数据源的当前页；刷新后的旧响应不得覆盖新状态。
const loadTablePage = async (item, pageNum) => {
  const key = tablePageKey(item);
  const cached = tableLoadState(item);
  if (cached.loading) return;
  const request = { ...cached, loading: true, error: "", requestedPage: pageNum, generation: loadGeneration, requestId: Symbol() };
  state.tableLoads[key] = request;
  try {
    const result = await $common.post("/dst/database/departmentDataSources", {
      mode: "tables", datasourceId: key, pageNum, pageSize: tablePageSize,
    });
    if (state.tableLoads[key]?.requestId !== request.requestId || request.generation !== loadGeneration) return;
    state.tableLoads[key] = {
      loading: false, loaded: true, error: "", requestedPage: result?.pageNum || pageNum,
      pageNum: result?.pageNum || pageNum, total: normalizeNumber(result?.total),
      list: Array.isArray(result?.list) ? result.list : [],
    };
  } catch (error) {
    if (state.tableLoads[key]?.requestId !== request.requestId || request.generation !== loadGeneration) return;
    state.tableLoads[key] = { ...cached, loading: false, error: "数据表加载失败，请重试。", requestedPage: pageNum };
  }
};
const changeTablePage = (item, delta) => {
  if (tableLoadState(item).loading) return;
  const nextPage = Math.min(Math.max(tablePage(item) + delta, 1), tableTotalPages(item));
  loadTablePage(item, nextPage);
};

const appSystemKey = (item) =>
  String(item.appId || item.appName || "").trim();

const hasAppSystem = (item) => appSystemKey(item).length > 0;

// 状态由服务端聚合有效表完成情况；0 待登记，1 未登记完成，2 已完成。
const hasCompletedRegistrationStatus = (item) =>
  normalizeNumber(item.assetStatus ?? item.asset_status) === 2;

const isRegistrationComplete = (item) => hasCompletedRegistrationStatus(item);

const isConnectionUnavailable = (item) => connectionStatusOf(item) !== "success";

const sourceHasPrimaryIssue = (item) => isConnectionUnavailable(item) || !isRegistrationComplete(item);

const statusText = (item) => ["待登记", "未登记完成", "已完成"][normalizeNumber(item.assetStatus ?? item.asset_status)] || "待登记";

const statusTagType = (item) => {
  return isRegistrationComplete(item) ? "success" : "warning";
};

const schemaText = (item) => {
  if (!hasConnection(item)) return "";
  return item.database || item.schema || "";
};

const hasConnection = (item) =>
  !(item.showConnect === 0 || item.showConnect === "0" || item.showConnect === false);

const connectionStatusOf = (item) => {
  if (!hasConnection(item)) return "unprovided";
  const status = String(item.connectionStatus || "").toLowerCase();
  if (["success", "connected", "ok", "true", "1"].includes(status)) return "success";
  if (["failed", "fail", "error", "disconnect", "disconnected", "false", "0"].includes(status)) {
    return "failed";
  }
  return "unknown";
};

const connectionStatusText = (item) => {
  const status = connectionStatusOf(item);
  if (status === "success") return "连接测试通过";
  if (status === "failed") return "数据库无法连通";
  if (status === "unprovided") return "暂不提供连接信息";
  return "已提供连接信息，尚未记录连通性测试结果";
};

const connectionStatusIcon = (item) => {
  const status = connectionStatusOf(item);
  if (status === "success") return "el-icon-SuccessFilled";
  if (status === "failed") return "el-icon-CircleCloseFilled";
  if (status === "unprovided") return "el-icon-RemoveFilled";
  return "el-icon-WarningFilled";
};

const connectionStatusClass = (item) => `is-${connectionStatusOf(item)}`;

const supportedDatasourceTypes = [
  "mysql",
  "oracle",
  "oceanbasemysql",
  "oceanbaseoracle",
  "gaussdb",
  "gbase8a",
  "sqlserver",
  "hive",
  "vertica",
  "dameng",
  "postgresql",
  "kingbase8",
  "maxcompute",
  "minio",
  "ftp",
  "api",
  "kafka",
  "elasticsearch",
];

const normalizeDatasourceType = (value) => {
  const normalized = String(value || "").toLowerCase().replace(/[\s_-]/g, "");
  const alias = {
    pg: "postgresql",
    postgres: "postgresql",
    postgresql: "postgresql",
    mssql: "sqlserver",
    sqlserver: "sqlserver",
    dm: "dameng",
    dameng: "dameng",
    kingbase: "kingbase8",
    kingbasees: "kingbase8",
    kingbase8: "kingbase8",
    oceanbasemysql: "oceanbasemysql",
    oceanbaseoracle: "oceanbaseoracle",
    es: "elasticsearch",
    elastic: "elasticsearch",
    elasticsearch: "elasticsearch",
  };
  return alias[normalized] || normalized;
};

const isSupportedDatasourceType = (value) => supportedDatasourceTypes.includes(normalizeDatasourceType(value));

// 第一步“数据上报方式”没有真实的接口连接，dbType 可能仍会保留为 api。
// 因此优先用登记时持久化的接入方式判断卡片图标，避免把上报数据源误画成 API。
const normalizeAccessMode = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  const aliases = {
    extract: "explore",
    push: "receive",
    pull: "capture",
    report: "upload",
  };
  return aliases[normalized] || normalized;
};

const isDataReportSource = (item) => {
  if (normalizeAccessMode(item?.accessMode) === "upload") return true;
  // 历史数据源创建时尚未持久化 accessMode。上报方式不会保存接口连接，
  // 因而以“无连接的 API”作为仅对历史记录生效的兼容判定。
  const noConnection = ["0", "false", "no", "off"].includes(String(item?.showConnect ?? "").trim().toLowerCase());
  return normalizeDatasourceType(item?.dbType) === "api" && noConnection;
};

// 与登记第一步“数据推送方式”保持一致：Download 表示平台接收外部推送数据。
const isDataPushSource = (item) => normalizeAccessMode(item?.accessMode) === "receive";

const sourceTypeIcon = (item) => {
  if (isDataReportSource(item)) return "el-icon-UploadFilled";
  if (isDataPushSource(item)) return "el-icon-Download";
  const type = normalizeDatasourceType(item.dbType);
  return supportedDatasourceTypes.includes(type) ? type : "database-network";
};

const sourceTypeName = (item) => {
  if (isDataReportSource(item)) return "数据上报方式";
  if (isDataPushSource(item)) return "数据推送方式";
  const type = String(item.dbType || "").toLowerCase().replace(/[\s_-]/g, "");
  const names = {
    mysql: "MySQL",
    oracle: "Oracle",
    oceanbasemysql: "OceanBase MySQL",
    oceanbaseoracle: "OceanBase Oracle",
    gaussdb: "GaussDB",
    gbase8a: "GBase 8a",
    sqlserver: "SQL Server",
    hive: "Hive",
    vertica: "Vertica",
    dameng: "达梦数据库",
    postgresql: "PostgreSQL",
    kingbase8: "人大金仓 KingbaseES",
    maxcompute: "MaxCompute",
    minio: "MinIO",
    ftp: "FTP",
    api: "API",
    kafka: "Kafka",
    elasticsearch: "Elasticsearch 搜索引擎",
    other: "其他类型数据源",
  };
  return names[type] || item.dbType || "其他类型数据源";
};

const sourceIconTip = (item) => {
  const connectionText = connectionStatusText(item);
  const schema = schemaText(item);
  return [sourceTypeName(item), connectionText, schema ? `库名/Schema：${schema}` : ""]
    .filter(Boolean)
    .join(" · ");
};

// 分组标题已经展示了应用系统；数据源卡片的次级信息展示数据源描述。
const sourceMetaText = (item) => {
  return String(item.description || "").trim() || "暂无描述";
};

const sourceCreatorText = (item) => firstText(
  item.createdByName,
  item.created_by_name,
  item.creatorName,
  item.creator_name,
  item.createdBy,
  item.created_by,
  "未留痕"
);

const applyMetricFilter = (filter) => {
  state.metricFilter = state.metricFilter === filter ? "" : filter;
  state.filter = "all";
};

const clearMetricFilter = () => {
  state.metricFilter = "";
};

const businessTypeText = (value) => {
  const map = {
    business: "业务表",
    log: "日志表",
    dict: "字典表",
    dictionary: "字典表",
    process: "过程表",
    backup: "备份表",
  };
  return map[value] || value || "-";
};

const isView = (row) => {
  const type = String(row.tableType || row.table_type || "").toLowerCase();
  return type.includes("view") || type.includes("视图");
};

const tableChineseName = (row) => row.tableNameCn || row.tableComment || "未填写中文注释";

const tableCreatedTime = (row) => firstText(
  row.createdTime, row.created_time, row.createTime, row.create_time,
  row.registeredTime, row.registered_time, row.registerTime, row.register_time
);
const tableUpdatedTime = (row) => firstText(
  row.updatedTime, row.updated_time, row.updateTime, row.update_time,
  row.modifiedTime, row.modified_time, row.lastModifiedTime, row.last_modified_time
);
const tableTimeDate = (value) => {
  if (!value) return null;
  const date = new Date(typeof value === "string" ? value.replace(" ", "T") : value);
  return Number.isNaN(date.getTime()) ? null : date;
};
const tableCreatedDate = (row) => tableTimeDate(tableCreatedTime(row));
const tableUpdatedDate = (row) => tableTimeDate(tableUpdatedTime(row));
const tableTimeText = (date) => {
  if (!date) return "-";
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const tableCreatedTimeText = (row) => {
  return tableTimeText(tableCreatedDate(row));
};
const sourceUpdatedTimeText = (item) => tableTimeText(tableTimeDate(firstText(
  item.updatedTime,
  item.updated_time,
  item.updateTime,
  item.update_time
)));
const tableDayTag = (date) => {
  if (!date) return "";
  const today = new Date();
  const dayStart = (value) => new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
  const days = Math.round((dayStart(today) - dayStart(date)) / 86400000);
  if (days === 0) return "今日";
  if (days === 1) return "昨日";
  return "";
};
const tableCreatedDayTag = (row) => tableDayTag(tableCreatedDate(row));
const tableUpdatedTimeText = (row) => tableTimeText(tableUpdatedDate(row));
const tableUpdatedDayTag = (row) => tableDayTag(tableUpdatedDate(row));

const hasChange = (row) => {
  const status = String(row.changeStatus || "").toLowerCase();
  return ["added", "deleted", "modified"].includes(status);
};

const changeText = (value) => {
  const map = { added: "新增", deleted: "删除", modified: "修改", unchanged: "无变化" };
  return map[value] || "无变化";
};

const changeTagType = (value) => {
  if (value === "added") return "success";
  if (value === "deleted") return "danger";
  if (value === "modified") return "warning";
  return "info";
};

const openDatasourceRegister = () => {
  const selectedNode = state.leftSelectNode || {};
  const selectedApp = selectedNode.type === "app"
    ? { value: selectedNode.value, label: selectedNode.label }
    : null;
  const newDb = {
    did: $common.uuid(),
    dbName: "待命名",
    appId: selectedApp?.value || "",
    appName: selectedApp?.label || "",
    orgId: $user?.orgId || "",
    orgName: $user?.orgName || "",
    sourceOrgId: $user?.orgId || "",
    sourceOrgName: $user?.orgName || "",
  };
  const registerData = {
    db: [newDb],
    departmentOnly: true,
    defaultOrgId: $user?.orgId,
    defaultOrgName: $user?.orgName,
  };
  if (selectedApp) {
    registerData.app = {
      tid: selectedApp.value,
      appName: selectedApp.label,
      title: selectedApp.label,
    };
  }
  openRegisterModal({
    registerClass: "db",
    registerData,
  });
};

const openDatasourceEdit = (row) => {
  const typeSupported = isSupportedDatasourceType(row.dbType || row.db_type || row.databaseType);
  const editRow = typeSupported
    ? row
    : {
        ...row,
        dbType: "",
        db_type: "",
        databaseType: "",
        showConnect: "",
        show_connect: "",
        forceChooseType: true,
      };
  openRegisterModal({
    registerClass: "db",
    registerData: { db: [editRow], departmentOnly: true, forceChooseType: !typeSupported },
  });
};

const openDatasourceDetail = (row) => {
  router.push({
    path: "/datasource-manage/detail",
    query: {
      id: row.tid || row.id,
      type: "db",
      title: row.dbName || "数据源详情",
    },
  });
};

const openTableDetail = (row) => {
  const tableId = row?.tid || row?.tableId || row?.id;
  if (!tableId) {
    ElMessage.warning("暂未找到数据表标识，无法进入详情");
    return;
  }
  router.push({
    path: "/datasource-manage/detail",
    query: {
      id: tableId,
      type: "table",
      title: tableChineseName(row) || row.tableName || "数据表详情",
    },
  });
};

const datasourceKey = (row) => String(row?.tid || row?.id || "").trim();
const isOpeningRegistration = (row) => state.openingRegistrationIds.includes(datasourceKey(row));

const openTableRegister = async (row, currentStep = 1) => {
  const tid = datasourceKey(row);
  if (!tid) {
    ElMessage.warning("暂未找到数据源标识，无法进入登记流程");
    return;
  }
  if (isOpeningRegistration(row)) return;

  state.openingRegistrationIds.push(tid);
  try {
    // 与 register-db 在“下一步”时的处理保持一致：先读主记录详情，再携带
    // poolCfg 内的 showConnect 等配置进入数据表页。这样 API、FTP 等抓取型
    // 数据源会读取已保存的表快照，而不会被错误地重新当作直连库探查。
    const detail = await $common.post("/dst/database/detail", { tid, silent: true });
    if (detail?.found === false) {
      ElMessage.warning("该数据源已不存在或当前部门无权访问，请刷新后重试");
      return;
    }
    const datasource = mergeDatasourceDetail(row, detail || {});
    openRegisterModal({
      registerClass: "dbTable",
      registerData: { db: [datasource], currentStep, departmentOnly: true },
    });
  } catch (error) {
    console.error("读取数据源登记配置失败:", error);
    ElMessage.error("读取数据源完整配置失败，暂无法可靠加载数据表，请稍后重试");
  } finally {
    state.openingRegistrationIds = state.openingRegistrationIds.filter((id) => id !== tid);
  }
};

const handlePrimaryAction = (row) => {
  if (isRegistrationComplete(row)) {
    openTableRegister(row, 4);
    return;
  }
  openTableRegister(row);
};

const deleteDatasource = (row) => {
  $common.handle({
    url: "/dst/database/delete",
    info: `是否删除该数据源【${row.dbName || "未命名数据源"}】`,
    data: { tid: row.tid },
    done: () => {
      state.deletedIds.push(row.tid);
      loadData();
    },
  });
};

watch(completedVersion, () => loadData());
watch(
  [() => state.keyword, () => state.filter, () => state.metricFilter],
  () => {
    state.sourceDisplayLimit = SOURCE_BATCH_SIZE;
    state.expanded = [];
  }
);

onMounted(loadData);
</script>

<style scoped lang="scss">
.department-source-page {
  min-height: 100%;
  padding: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-color: #cbd5e1 transparent;
  scrollbar-width: thin;
  background: transparent;

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    border: 2px solid transparent;
    border-radius: 999px;
    background: #cbd5e1;
    background-clip: content-box;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: #94a3b8;
    background-clip: content-box;
  }
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #e8edf5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.04);
  margin-bottom: 14px;
}

.topbar-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;

  h2 {
    margin: 0;
    color: #101828;
    font-size: 20px;
    font-weight: 760;
  }
}

.topbar-scope {
  display: inline-flex;
  align-items: center;
  max-width: 260px;
  min-height: 26px;
  padding: 0 10px;
  overflow: hidden;
  border-radius: 6px;
  background: #f2f6fc;
  color: #52657d;
  font-size: 13px;
  line-height: 26px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;

  > .el-button + .el-button {
    margin-left: 0;
  }
}

.search-input {
  width: 320px;
}

.search-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
  transition: color 0.16s ease;

  &:hover,
  &:focus {
    color: #1768db;
    outline: none;
  }
}

.app-filter {
  width: 220px;
}

.app-option {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.app-option-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  background: #eff6ff;
  color: #1677ff;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 14px;
}

.overview-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 88px;
  padding: 14px 16px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #e8edf5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 18px rgba(16, 24, 40, 0.035);
  outline: none;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;

  &::after {
    position: absolute;
    right: 0;
    bottom: 0;
    width: 54px;
    height: 4px;
    content: "";
    background: currentColor;
    border-radius: 999px 0 16px;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover {
    border-color: currentColor;
    box-shadow: 0 13px 28px rgba(16, 24, 40, 0.09);
    transform: translateY(-3px);
  }

  &.is-active {
    border-color: currentColor;
    background: linear-gradient(135deg, #fff 0%, #f8fbff 100%);
    box-shadow:
      0 14px 30px rgba(16, 24, 40, 0.1),
      inset 0 0 0 1px currentColor;

    &::after {
      opacity: 1;
    }
  }

  small {
    display: block;
    color: #667085;
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 4px;
    color: #101828;
    font-size: 26px;
    line-height: 1;

    em {
      margin-left: 2px;
      color: #667085;
      font-size: 13px;
      font-style: normal;
      font-weight: 600;
    }

    &.is-text-value {
      font-size: 20px;
      line-height: 1.2;
    }
  }
}

.card-main {
  flex: 0 0 auto;
}

.card-breakdown {
  display: grid;
  gap: 6px;
  min-width: 0;
  margin-left: auto;
  text-align: right;

  span {
    color: #667085;
    font-size: 12px;
    white-space: nowrap;
  }

  .attention-danger {
    color: #d92d20;
    font-weight: 400;
  }

  .attention-warning {
    color: #f79009;
    font-weight: 400;
  }

  &.is-change {
    gap: 4px;

    .is-added {
      color: #039855;
    }

    .is-modified {
      color: #f79009;
    }

    .is-deleted {
      color: #d92d20;
    }
  }
}

.card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  font-size: 22px;
}

.overview-card.is-blue .card-icon {
  background: #eff6ff;
  color: #1677ff;
}

.overview-card.is-blue {
  color: #1677ff;
}

.overview-card.is-green .card-icon {
  background: #ecfdf3;
  color: #12b76a;
}

.overview-card.is-green {
  color: #12b76a;
}

.overview-card.is-purple .card-icon {
  background: #f4f3ff;
  color: #7a5af8;
}

.overview-card.is-purple {
  color: #7a5af8;
}

.overview-card.is-orange .card-icon {
  background: #fff7ed;
  color: #f79009;
}

.overview-card.is-orange {
  color: #f79009;
}

.work-card {
  padding: 14px;
  border: 1px solid #e8edf5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.04);
}

.management-workspace {
  position: relative;
  display: grid;
  grid-template-columns: minmax(226px, 278px) minmax(0, 1fr);
  align-items: stretch;
  min-width: 0;
  padding: 0;
  overflow: hidden;
}

.workspace-loading-mask {
  position: absolute;
  z-index: 10;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-direction: column;
  min-height: 260px;
  padding-top: 54px;
  background: rgba(255, 255, 255, 0.94);
  color: #344054;
  text-align: center;

  strong {
    margin-top: 12px;
    color: #1d2939;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    margin: 7px 0 0;
    color: #667085;
    font-size: 13px;
  }
}

.workspace-loading-spinner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #eef5ff;
  color: #1677ff;
  font-size: 20px;

  :deep(svg) {
    animation: workspace-loading-spin 0.9s linear infinite;
  }
}

@keyframes workspace-loading-spin {
  to {
    transform: rotate(360deg);
  }
}

.organization-tree-panel {
  min-width: 0;
  padding: 16px 12px;
  border-right: 1px solid #e8edf5;
  background: linear-gradient(180deg, #fbfdff 0%, #f6f9fd 100%);
}

.organization-tree-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 8px 12px;
  border-bottom: 1px solid #e8edf5;

  strong {
    color: #1d2939;
    font-size: 16px;
    font-weight: 700;
  }

}

.organization-tree {
  margin-top: 10px;
  background: transparent;

  /* 首屏时 UTree 仅有占位节点，必须预留骨架与加载遮罩的完整高度。 */
  &.is-loading {
    min-height: 300px;

    :deep(.u-tree-body) {
      min-height: 260px;
    }
  }
}

.application-workspace {
  min-width: 0;
  padding: 14px;
  overflow: hidden;
}

.work-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bulk-button {
  min-width: 92px;

  :deep(svg) {
    width: 14px;
    height: 14px;
    margin-right: 5px;
  }
}

.source-skeleton {
  padding: 8px 2px;
}

.source-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  min-height: 280px;
  padding: 42px 24px 48px;
  border: 1px dashed #d7e3f5;
  border-radius: 18px;
  background:
    radial-gradient(circle at 50% 36%, rgba(64, 128, 255, 0.08), transparent 32%),
    linear-gradient(180deg, #fbfdff 0%, #fff 100%);
  color: #667085;
  text-align: center;

  strong {
    margin-top: 14px;
    color: #1f2a44;
    font-size: 16px;
    font-weight: 650;
  }

  p {
    max-width: 420px;
    margin: 8px 0 0;
    color: #7a8699;
    font-size: 13px;
    line-height: 22px;
  }
}

.source-empty-illustration {
  position: relative;
  width: 128px;
  height: 92px;
}

.empty-db,
.empty-search {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px rgba(59, 130, 246, 0.14);
}

.empty-db {
  border: 1px solid #cfe0ff;
  background: linear-gradient(180deg, #f4f8ff 0%, #eaf2ff 100%);
  color: #2f6df6;
}

.empty-db-main {
  left: 26px;
  top: 10px;
  width: 72px;
  height: 58px;
  border-radius: 18px;

  :deep(svg) {
    width: 34px;
    height: 34px;
  }
}

.empty-db-sub {
  right: 18px;
  bottom: 8px;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  color: #18a058;
}

.empty-search {
  left: 18px;
  bottom: 2px;
  width: 40px;
  height: 40px;
  border: 1px solid #d8e3f3;
  border-radius: 999px;
  background: #fff;
  color: #94a3b8;
}

.application-source-list {
  display: grid;
  gap: 18px;
}

.application-source-group {
  background: transparent;
}

.application-source-group__head {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 52px;
  padding: 8px 2px;
  gap: 12px;
  border: 0;
  background: transparent;
  color: #1d2939;
  cursor: pointer;
  text-align: left;

  &:hover,
  &:focus-visible {
    background: #f8fbff;
    outline: none;
  }
}

.application-source-group.is-expanded .application-source-group__head {
  border-bottom: 1px solid #edf1f6;
}

.application-source-group__title {
  display: grid;
  min-width: 0;

  strong {
    overflow: hidden;
    color: #1d2939;
    font-size: 16px;
    font-weight: 650;
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.application-source-group__summary {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  color: #667085;
  font-size: 12px;

  span {
    padding: 4px 9px;
    border-radius: 999px;
    background: #f2f5f9;
  }

  .is-warning {
    background: #fff7ed;
    color: #dc6803;
  }
}

.application-source-group__arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 8px;
  background: #edf4ff;
  color: #3576db;
}

.application-source-group__body {
  padding: 10px 0 2px;
  background: transparent;
}

.source-list {
  display: grid;
  gap: 12px;
  padding-top: 0;
}

.source-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0 2px;
  color: #667085;
  font-size: 13px;

  :deep(.el-button) {
    min-width: 148px;
  }
}

.application-group-panel-enter-active,
.application-group-panel-leave-active {
  overflow: hidden;
  transition: max-height 0.2s ease, opacity 0.18s ease;
}

.application-group-panel-enter-from,
.application-group-panel-leave-to {
  max-height: 0;
  opacity: 0;
}

.application-group-panel-enter-to,
.application-group-panel-leave-from {
  max-height: 5000px;
  opacity: 1;
}

.source-card {
  position: relative;
  min-width: 0;
  max-width: 100%;
  border: 1px solid #dfe7f1;
  border-radius: 12px;
  background: #fff;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;

  &:hover {
    border-color: #b7cef0;
    box-shadow: 0 5px 14px rgba(37, 99, 235, 0.06);
  }

  &.is-expanded {
    border-color: #b7cef0;
    box-shadow: 0 5px 14px rgba(37, 99, 235, 0.06);
  }
}

.source-card.can-expand {
  cursor: pointer;
}

.source-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px 10px;
  cursor: default;
}

.source-card.can-expand .source-head {
  cursor: pointer;
}

.source-title {
  display: flex;
  flex: 1 1 0;
  align-items: center;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  gap: 12px;
}

.source-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  margin: 0 5px 5px 0;
  overflow: visible;
  border-radius: 13px;
  background: linear-gradient(145deg, #eff6ff, #e7f1ff);
  color: #1677ff;
  font-size: 22px;
  box-shadow: inset 0 0 0 1px rgba(22, 119, 255, 0.08);
}

.source-icon.is-elasticsearch {
  :deep(svg) {
    width: 28px;
    height: 28px;
  }
}

.source-icon.is-data-report {
  background: linear-gradient(145deg, #f5f0ff, #ece5ff);
  color: #7c3aed;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.12);
}

.source-icon.is-data-push {
  background: linear-gradient(145deg, #effaf4, #e2f6eb);
  color: #079669;
  box-shadow: inset 0 0 0 1px rgba(7, 150, 105, 0.14);
}

.source-connection-status {
  position: absolute;
  right: -5px;
  bottom: -5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #f2f4f7;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.14);
  color: #98a2b3;
  font-size: 12px;
  font-style: normal;
}

.source-connection-status.is-success {
  background: #ecfdf3;
  color: #12b76a;
}

.source-connection-status.is-failed {
  background: #fff1f3;
  color: #f04438;
}

.source-connection-status.is-unprovided {
  background: #f2f4f7;
  color: #98a2b3;
}

.source-connection-status.is-unknown {
  background: #fffaeb;
  color: #f79009;
}

.source-name {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  max-width: 100%;

  h4,
  .source-name-button {
    margin: 0;
    overflow: hidden;
    font-size: 16px;
    font-weight: 720;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  h4 {
    color: #101828;
  }

  .source-name-button {
    max-width: 100%;
    padding: 0;
    border: 0;
    background: transparent;
    color: #101828;
    cursor: pointer;
    text-align: left;

    &:hover,
    &:focus {
      color: #095de0;
      outline: none;
    }
  }

  p {
    display: -webkit-box;
    width: 100%;
    max-width: 100%;
    margin: 4px 0 0;
    overflow: hidden;
    color: #667085;
    font-size: 13px;
    line-height: 1.45;
    text-overflow: ellipsis;
    white-space: normal;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    line-clamp: 3;
  }
}

.source-audit-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 5px;
  overflow: hidden;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.45;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.source-name-line {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;

  h4,
  .source-name-button {
    min-width: 0;
  }
}

.status-title-tag {
  flex: 0 0 auto;
}

.connection-tag {
  flex: 0 0 auto;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.source-side {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  flex: 0 1 auto;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.source-tags {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.expand-icon {
  flex-shrink: 0;
  color: #98a2b3;
}

.source-metrics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 16px 12px 68px;

  span {
    padding: 4px 10px;
    border-radius: 999px;
    background: #f8fafc;
    color: #475467;
    font-size: 12px;
  }

  .danger {
    background: #fff1f3;
    color: #d92d20;
  }

  .warning {
    background: #fff7ed;
    color: #dc6803;
  }
}

.source-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  flex: 0 0 auto;
  transform: translateX(-4px);
  padding: 3px;
  background: #f8fbff;
  border: 1px solid #e5edf8;
  border-radius: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);

  :deep(.el-button) {
    height: 30px;
    margin-left: 0;
    padding: 0 10px;
    font-size: 13px;
    font-weight: 400;
    border-radius: 8px;
    box-shadow: none;
  }

  :deep(svg) {
    width: 14px;
    height: 14px;
    margin-right: 4px;
  }

  :deep(.register-button.el-button--primary) {
    color: #fff;
    background: linear-gradient(135deg, #1a7dff 0%, #176bff 100%);
    border-color: #176bff;
  }

  :deep(.register-button.el-button--primary:hover),
  :deep(.register-button.el-button--primary:focus) {
    background: linear-gradient(135deg, #0f6fea 0%, #095de0 100%);
    border-color: #095de0;
  }

  :deep(.edit-button),
  :deep(.delete-button) {
    background: #fff;
    border-color: transparent;
  }

  :deep(.edit-button) {
    color: #35506b;
  }

  :deep(.edit-button:hover),
  :deep(.edit-button:focus) {
    color: #1768db;
    background: #f3f8ff;
    border-color: #cfe0f5;
  }

  :deep(.delete-button) {
    color: #d92d20;
  }

  :deep(.delete-button:hover),
  :deep(.delete-button:focus) {
    color: #b42318;
    background: #fff5f6;
    border-color: #fecdd3;
  }
}

.register-button {
  min-width: 118px;
}

.edit-button {
  min-width: 58px;
}

.delete-button {
  min-width: 58px;
}

.source-expand-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  margin-left: auto;
  border: 1px solid transparent;
  border-radius: 9px;
  background: #f3f8ff;
  color: #1768db;
  cursor: pointer;
  opacity: 0;
  transform: translateX(6px);
  transition:
    opacity 0.16s ease,
    transform 0.16s ease,
    background 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}

.source-card.can-expand:hover .source-expand-arrow,
.source-card.is-expanded .source-expand-arrow {
  opacity: 1;
  transform: translateX(0);
}

.source-expand-arrow:hover,
.source-expand-arrow:focus {
  color: #095de0;
  background: #eaf3ff;
  border-color: #cfe0f5;
  outline: none;
}

.table-panel {
  padding: 0 16px 16px;
  cursor: default;
}

.table-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ec;

  strong {
    color: #101828;
  }

  span {
    color: #667085;
    font-size: 13px;
  }
}

.table-preview-scroll {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border-radius: 12px;

  :deep(.el-scrollbar__bar) {
    opacity: 1;
  }
}

.table-preview {
  min-width: 770px;
  border-radius: 12px;
}

.table-panel-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding: 8px 10px;
  color: #667085;
  font-size: 13px;
  border: 1px solid #edf2f7;
  border-radius: 10px;
  background: #fbfdff;
}

.table-panel-pager-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.table-name-cell {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;

  strong {
    display: block;
    color: #0f172a;
    font-weight: 680;
    line-height: 1.35;
  }

  small {
    display: block;
    margin-top: 2px;
    color: #667085;
  }
}

.table-name-cell.is-link {
  cursor: pointer;
}

.table-name-button {
  display: block;
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: #101828;
  font-weight: 680;
  line-height: 1.35;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.table-name-cell.is-link:hover .table-name-button,
.table-name-button:hover,
.table-name-button:focus {
  color: #1768db;
  outline: none;
}

.table-cn-line {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.table-created-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #475467;
  font-variant-numeric: tabular-nums;

  :deep(.el-tag) {
    flex: 0 0 auto;
  }
}

.table-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 9px;
  background: #eef6ff;
  color: #1677ff;

  &.is-view {
    background: #fff7ed;
    color: #f97316;
  }
}

.table-panel-enter-active,
.table-panel-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.table-panel-enter-from,
.table-panel-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 1080px) {
  .topbar,
  .work-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  /* 窄屏时卡片身份信息必须优先展示，操作按钮固定换到第二行。
     不能把 source-title 作为可压缩的同级内容，否则长按钮会把图标、名称和
     状态整体挤出可视区域，只剩下“查看/编辑/删除”。 */
  .source-head {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    align-items: stretch;
    gap: 10px;
  }

  .source-title {
    display: flex !important;
    width: 100%;
    min-width: 0;
    overflow: visible;
  }

  .source-name {
    width: auto;
  }

  .source-side {
    display: flex;
    width: 100%;
    min-width: 0;
  }

  .topbar-actions,
  .source-actions,
  .source-side,
  .source-tags {
    justify-content: flex-start;
  }

  .source-side {
    min-width: 0;
  }

  .source-actions {
    max-width: 100%;
    transform: none;
  }

  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-input {
    width: 100%;
  }

  .app-filter {
    width: 100%;
  }

  .source-metrics {
    padding-left: 16px;
  }

  .management-workspace {
    grid-template-columns: 1fr;
  }

  .organization-tree-panel {
    max-height: 220px;
    overflow: auto;
    border-right: 0;
    border-bottom: 1px solid #e8edf5;
  }
}

@media (max-width: 640px) {
  .source-head {
    padding: 12px 12px 8px;
  }

  .source-title {
    gap: 10px;
  }

  .source-icon {
    width: 36px;
    height: 36px;
    font-size: 20px;
  }

  .source-audit-meta {
    gap: 2px 10px;
  }

  .source-actions {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .source-actions :deep(.el-button) {
    flex: 0 0 auto;
  }
}
</style>
