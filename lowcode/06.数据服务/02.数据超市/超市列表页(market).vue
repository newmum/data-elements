<template>
  <div class="market images-market-bg">
    <div class="market__top">
      <h1 class="market__title">数据超市</h1>
      <div class="market__search">
        <div class="market__search-box">
          <el-input
            v-model="state.keyword"
            class="market__search-input"
            placeholder="请输入数据目录关键词"
            size="large"
            clearable
            @keyup.enter="search"
            @clear="search"
          ></el-input>
          <el-button type="primary" icon="search" @click="search">搜索</el-button>
        </div>
      </div>
    </div>

    <div class="market__body">
      <div class="market__sidebar" :class="{ 'is-open': categoriesOpen }">
        <button
          class="market__categories-toggle"
          type="button"
          :aria-expanded="categoriesOpen"
          aria-controls="market-category-tabs market-category-tree"
          @click="categoriesOpen = !categoriesOpen"
        >
          <Icon icon="el-icon-menu" size="16" />
          分类筛选
          <span v-if="state.selectedOrg" class="category-selected">已选分类</span>
          <Icon :icon="categoriesOpen ? 'el-icon-ArrowUp' : 'el-icon-ArrowDown'" size="14" />
        </button>
        <!--    侧边页签    -->
        <div id="market-category-tabs" class="sidebar-header">
          <template v-if="sidebarItems.length === 2">
            <div
              class="two-nav"
              :class="{ 'img-back': state.activeSidebar !== sidebarItems[0].title }"
            >
              <div
                v-for="(item, ind) in sidebarItems"
                :key="item.title"
                class="nav-title"
                :class="{
                  active: state.activeSidebar === item.title,
                  'left-nav': ind === 0,
                  'right-nav': ind === 1,
                }"
                @click="changeCategory(item.title)"
              >
                {{ item.title }}
              </div>
            </div>
          </template>

          <h3 v-else class="nav-title">
            <Icon icon="el-icon-menu" class="mr-2" color="var(--el-color-primary)" :size="22" />
            {{ sidebarItems.length > 0 ? sidebarItems[0].title : "分类导航" }}
          </h3>
        </div>

        <!--    侧边树    -->
        <div id="market-category-tree" class="sidebar-card__list">
          <div v-if="countError" class="market__filter-error" role="status">
            分类统计加载失败
            <el-button link type="primary" @click="loadCounts">重试</el-button>
          </div>
          <template v-for="(tree, ind) in sidebarItems" :key="tree.title">
            <u-tree
              v-show="state.activeSidebar === tree.title"
              ref="treeRef"
              search
              search-placeholder="请输入搜索关键词"
              :data="tree.options"
              :auto-expand-first-node="false"
              :expand-on-click-node="false"
              :default-props="{ label: 'label' }"
              node-key="value"
              :current-node-key="state.selectedOrg?.value"
              @node-click="selectOrganization"
            >
              <template #node="{ node }">
                <u-tree-node
                  :label="node.label"
                  :keyword="treeRef ? treeRef[ind]?.searchValue : ''"
                >
                  <template #count>
                    {{ countMap ? (countMap[node.data.value] ?? 0) : "—" }}
                  </template>
                </u-tree-node>
              </template>
            </u-tree>
          </template>
        </div>
      </div>

      <div class="market__content">
        <check-card
          v-model="state.valueRight"
          :filters="rightItems"
          @change="queueList(true)"
        />
        <div v-if="appError" class="market__filter-error" role="status">
          业务系统加载失败
          <el-button link type="primary" @click="loadApps">重试</el-button>
        </div>

        <div class="result-card">
          <div class="result-card__header">
            <div v-if="state.sTime !== null && !state.loading && !state.listError" class="result-count">
              搜索结果
              <span>{{ state.totalLimited ? `至少 ${state.total}` : state.total }}</span>
              项
              <i>
                (用时
                <span>{{ state.sTime }}</span>
                秒)
              </i>
            </div>
            <div v-else class="result-count">搜索结果</div>
            <div class="sort-options">
              <template v-for="item in sorts" :key="item.title">
                <button
                  class="sort-item"
                  type="button"
                  :aria-pressed="state.sortField === item.field"
                  :class="{ active: state.sortField === item.field }"
                  @click="changeSort(item)"
                >
                  {{ item.title }}
                  <Icon
                    class="sort-icon icon-top"
                    icon="el-icon-CaretTop"
                    :class="{
                      active: state.sortField === item.field && state.sortOrder === 'asc',
                    }"
                  />
                  <Icon
                    icon="el-icon-CaretBottom"
                    class="sort-icon icon-bottom"
                    :class="{
                      active: state.sortField === item.field && state.sortOrder === 'desc',
                    }"
                  />
                </button>
              </template>
            </div>
          </div>
          <div class="result-card__body">
            <div v-if="state.listError" class="market__list-error" role="status">
              数据目录加载失败
              <el-button link type="primary" @click="queueList()">重试</el-button>
            </div>
            <market-list
              v-else
              :loading="state.loading"
              :display-fields="displayFields"
              :data-list="state.list"
              :adding-catalogs="addingCatalogs"
              :collecting-catalogs="collectingCatalogs"
              @detail="openDetail"
              @collect="collectCatalog"
              @apply="addCatalogToCart"
            />

            <div v-if="!state.listError && state.total > 0" class="pagination">
              <ElPagination
                :current-page="state.pageNo"
                :page-size="state.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="state.total"
                :background="true"
                layout="prev, pager, next, sizes"
                @update:current-page="changePage"
                @update:page-size="changePageSize"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useCartCount, useRegisterModal } from "@/composables";
import { useDictStore } from "@/store";

type SortField = "createdTime" | "updatedTime" | "recordCount";
type SortOrder = "asc" | "desc";
type Category = "行政处室" | "事业单位";
type FilterName = "业务系统" | "共享类型" | "加工程度" | "发布时间";
type FilterValue = string | number;
type FilterOption = { label: string; value: FilterValue };
type Organization = {
  id: string;
  serialNumber: string;
  value: string;
  label: string;
  children?: Organization[];
};
type ApplicationOption = { label: string; value: string; orgId: string };
type MarketCatalog = {
  tid: string;
  assetType: string;
  catalogName: string;
  collected?: boolean | "1" | "0";
  applyStatus?: number | string;
  canApply?: boolean;
  canCollect?: boolean;
};
type ListResult = { list: MarketCatalog[]; total: number; totalLimited?: boolean };
type CountResult = { orgPath: Record<string, number> };

const { completedVersion } = useRegisterModal();
const { refresh: refreshCartCount, sessionRevision } = useCartCount();
const router = useRouter();
const dictStore = useDictStore();
const treeRef = ref<{ searchValue?: string }[]>([]);
const categoriesOpen = ref(false);
const addingCatalogs = reactive(new Set<string>());
const collectingCatalogs = reactive(new Set<string>());
const state = reactive({
  keyword: "",
  appliedKeyword: "",
  activeSidebar: "行政处室" as Category,
  selectedOrg: undefined as Organization | undefined,
  valueRight: {} as Partial<Record<FilterName, FilterValue[]>>,
  sortField: "createdTime" as SortField,
  sortOrder: "desc" as SortOrder,
  list: [] as MarketCatalog[],
  total: 0,
  totalLimited: false,
  loading: true,
  listError: false,
  sTime: null as number | null,
  pageNo: 1,
  pageSize: 20,
});
const countMap = ref<Record<string, number> | null>(null);
const countError = ref(false);
const apps = ref<ApplicationOption[]>([]);
const appError = ref(false);

const sorts: { title: string; field: SortField; order: SortOrder }[] = [
  { title: "准入时间", field: "createdTime", order: "desc" },
  { title: "更新时间", field: "updatedTime", order: "asc" },
  { title: "数据量", field: "recordCount", order: "asc" },
];
const publishPeriods: FilterOption[] = [
  { label: "最近十天", value: "10d" },
  { label: "近一个月", value: "1M" },
  { label: "近三个月", value: "3M" },
  { label: "近六个月", value: "6M" },
];

function organizationNodes(nodes: Organization[] = []): Organization[] {
  return nodes.map((node) => ({
    ...node,
    value: node.serialNumber,
    children: organizationNodes(node.children),
  }));
}

const sidebarItems = computed<{ title: Category; options: Organization[] }[]>(() => {
  const groups = dictStore.getDictItems("org") as unknown as Organization[];
  return [
    { title: "行政处室", options: organizationNodes(groups[0]?.children) },
    { title: "事业单位", options: organizationNodes(groups[1]?.children) },
  ];
});

const filteredApps = computed(() => {
  const organization = state.selectedOrg;
  return organization ? apps.value.filter((app) => app.orgId === organization.id) : apps.value;
});

const rightItems = computed(() => [
  { title: "业务系统", options: filteredApps.value },
  { title: "共享类型", options: dictStore.getDictItems("shareType") },
  { title: "加工程度", options: dictStore.getDictItems("dataProcessLevel") },
  { title: "发布时间", options: publishPeriods },
]);

const displayFields = computed(() => [
  { title: "数源单位", field: "orgId", options: "org" },
  { title: "共享属性", field: "shareType", options: "shareType" },
  { title: "访问量", field: "viewCount" },
  { title: "更新周期", field: "updateCycle", options: "updateCycle" },
  { title: "业务系统", field: "appId", options: apps.value },
  { title: "注册时间", field: "createdTime" },
]);

let active = false;
let activatedOnce = false;
let listQueued = false;
let listRequest = 0;
let countRequest = 0;
let appRequest = 0;
let countLoading = false;
let appsLoading = false;
let appsLoaded = false;

function isCurrentSession(revision: typeof sessionRevision.value) {
  return active && revision === sessionRevision.value;
}

function queueList(resetPage = false) {
  if (resetPage) state.pageNo = 1;
  if (!active || listQueued) return;
  listRequest += 1;
  state.loading = true;
  state.listError = false;
  listQueued = true;
  queueMicrotask(() => {
    listQueued = false;
    if (active) void loadList();
  });
}

async function loadList() {
  const request = ++listRequest;
  const revision = sessionRevision.value;
  const start = performance.now();
  const orgPath = state.selectedOrg?.value;
  const appId = state.valueRight["业务系统"]?.[0];
  const shareType = state.valueRight["共享类型"]?.[0];
  const dataProcessLevel = state.valueRight["加工程度"]?.[0];
  const publishedWithin = state.valueRight["发布时间"]?.[0];

  try {
    const result: ListResult = await $common.postSilently("/dws/market/list", {
      pageNum: state.pageNo,
      pageSize: state.pageSize,
      keyword: state.appliedKeyword,
      orgPath,
      appId,
      shareType,
      dataProcessLevel,
      publishedWithin,
      sortField: state.sortField,
      sortDir: state.sortOrder,
    });
    if (request !== listRequest || !isCurrentSession(revision)) return;
    if (!Array.isArray(result?.list) || !Number.isSafeInteger(result.total) || result.total < 0) {
      throw new Error("数据目录响应无效");
    }

    const lastPage = Math.max(1, Math.ceil(result.total / state.pageSize));
    if (state.pageNo > lastPage) {
      state.pageNo = lastPage;
      queueList();
      return;
    }
    state.list = result.list;
    state.total = result.total;
    state.totalLimited = result.totalLimited === true;
    state.sTime = Number(((performance.now() - start) / 1000).toFixed(3));
  } catch {
    if (request !== listRequest || !isCurrentSession(revision)) return;
    state.listError = true;
  } finally {
    if (request === listRequest && isCurrentSession(revision)) state.loading = false;
  }
}

async function loadCounts() {
  if (!active || countLoading || countMap.value) return;
  const request = ++countRequest;
  const revision = sessionRevision.value;
  countLoading = true;
  countError.value = false;
  try {
    const result: CountResult = await $common.postSilently("/dws/market/count", {});
    if (request !== countRequest || !isCurrentSession(revision)) return;
    if (!result?.orgPath || typeof result.orgPath !== "object" || Array.isArray(result.orgPath)) {
      throw new Error("分类统计响应无效");
    }
    countMap.value = result.orgPath;
  } catch {
    if (request === countRequest && isCurrentSession(revision)) countError.value = true;
  } finally {
    if (request === countRequest) countLoading = false;
  }
}

async function loadApps() {
  if (!active || appsLoading || appsLoaded) return;
  const request = ++appRequest;
  const revision = sessionRevision.value;
  appsLoading = true;
  appError.value = false;
  try {
    const result: ApplicationOption[] = await $common.getSilently("/sym/dict", { code: "app" });
    if (request !== appRequest || !isCurrentSession(revision)) return;
    if (!Array.isArray(result)) throw new Error("业务系统响应无效");
    apps.value = result;
    appsLoaded = true;
  } catch {
    if (request === appRequest && isCurrentSession(revision)) appError.value = true;
  } finally {
    if (request === appRequest) appsLoading = false;
  }
}

function search() {
  state.appliedKeyword = state.keyword.trim();
  queueList(true);
}

function changePage(page: number) {
  if (page === state.pageNo) return;
  state.pageNo = page;
  queueList();
}

function changePageSize(size: number) {
  if (size === state.pageSize) return;
  state.pageSize = size;
  queueList(true);
}

function changeSort(sort: (typeof sorts)[number]) {
  if (state.sortField === sort.field) {
    state.sortOrder = state.sortOrder === "asc" ? "desc" : "asc";
  } else {
    state.sortField = sort.field;
    state.sortOrder = sort.order;
  }
  queueList(true);
}

function changeCategory(category: Category) {
  if (state.activeSidebar === category) return;
  state.activeSidebar = category;
  if (!state.selectedOrg) return;
  state.selectedOrg = undefined;
  queueList(true);
}

function selectOrganization(organization: Organization) {
  if (!organization.value) return;
  state.selectedOrg = state.selectedOrg?.value === organization.value ? undefined : organization;
  const selectedApp = state.valueRight["业务系统"]?.[0];
  if (selectedApp && !filteredApps.value.some((app) => app.value === selectedApp)) {
    state.valueRight["业务系统"] = [];
  }
  queueList(true);
}

function openDetail(item: MarketCatalog) {
  void router.push({
    path: "detail",
    query: { id: item.tid, type: item.assetType, title: item.catalogName },
  });
}

async function addCatalogToCart(item: MarketCatalog) {
  const catalogId = item.tid;
  if (!catalogId || item.canApply === false || addingCatalogs.has(catalogId)) return;
  addingCatalogs.add(catalogId);
  const revision = sessionRevision.value;
  try {
    const receipt = await $common.post("/dws/market/shoppingCard", { catalogId });
    if (!isCurrentSession(revision)) return;
    if (typeof receipt !== "string" || !receipt.trim()) {
      $message.warning("加购结果暂未确认，请刷新后重试");
      return;
    }
    $message.success("已加入购物车");
    queueList();
    void refreshCartCount().catch(() => {});
  } catch (error: unknown) {
    if (!isCurrentSession(revision) || !error || typeof error !== "object") return;
    const code = "errorCode" in error ? error.errorCode : undefined;
    // 请求层展示拒绝原因；目录状态发生变化时重新读取当前页。
    if (typeof code === "string" && /^(?:MAGIC|API)-BUSINESS-(?:404|409)$/.test(code)) queueList();
  } finally {
    if (revision === sessionRevision.value) addingCatalogs.delete(catalogId);
  }
}

async function collectCatalog(item: MarketCatalog) {
  const catalogId = item.tid;
  if (!catalogId || item.canCollect === false || collectingCatalogs.has(catalogId)) return;
  collectingCatalogs.add(catalogId);
  const revision = sessionRevision.value;
  const collected = item.collected !== true && item.collected !== "1";
  try {
    const result = await $common.post("/dws/market/collect", { catalogId, collected });
    if (!isCurrentSession(revision)) return;
    if (result !== collected) {
      $message.warning("收藏状态暂未确认，请重试");
      return;
    }
    const current = state.list.find((entry) => entry.tid === catalogId);
    if (current) current.collected = result;
    if (state.loading) queueList();
    $message.success(result ? "已收藏" : "已取消收藏");
  } catch {
    // 请求层展示操作失败的具体原因。
  } finally {
    if (revision === sessionRevision.value) collectingCatalogs.delete(catalogId);
  }
}

function activate() {
  active = true;
  queueList();
  void loadCounts();
  void loadApps();
}

onMounted(activate);
onActivated(() => {
  if (activatedOnce || !active) activate();
  activatedOnce = true;
});
function deactivate() {
  active = false;
  listRequest += 1;
  countRequest += 1;
  appRequest += 1;
  countLoading = false;
  appsLoading = false;
}
onDeactivated(deactivate);
onBeforeUnmount(deactivate);

watch(completedVersion, () => queueList());
watch(
  sessionRevision,
  () => {
    listRequest += 1;
    countRequest += 1;
    appRequest += 1;
    countLoading = false;
    appsLoading = false;
    appsLoaded = false;
    countMap.value = null;
    countError.value = false;
    apps.value = [];
    appError.value = false;
    addingCatalogs.clear();
    collectingCatalogs.clear();
    state.keyword = "";
    state.appliedKeyword = "";
    state.selectedOrg = undefined;
    state.valueRight = {};
    state.list = [];
    state.total = 0;
    state.totalLimited = false;
    state.sTime = null;
    state.pageNo = 1;
    state.loading = true;
    const revision = sessionRevision.value;
    // 会话版本先更新，凭证在同一轮调用中随后写入。
    if (active) queueMicrotask(() => {
      if (isCurrentSession(revision)) activate();
    });
  },
  { flush: "sync" }
);
</script>

<style scoped lang="scss">
.market {
  --market-gutter: clamp(12px, 2vw, 32px);

  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  min-height: 100%;
  padding: calc(var(--navbar-height, 56px) + clamp(20px, 2.5vw, 32px)) var(--market-gutter) 16px;
  background-color: var(--el-bg-color-page);
  background-size: max(100%, 1000px) auto;
}

.market__top {
  display: flex;
  gap: 16px;
  align-items: center;
  width: min(100%, 900px);
  margin-bottom: clamp(20px, 3.6vw, 46px);

  .market__title {
    margin: 0;
    font-family:
      Source Han Sans CN,
      sans-serif !important;
    font-weight: 600;
    flex: none;
    font-size: clamp(24px, 2.5vw, 32px);
    line-height: 1.3;
    letter-spacing: 0.12em;
  }

  .market__search {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 16px;

    &-box {
      display: flex;
      align-items: center;

      .market__search-input {
        flex: 1;
        min-width: 0;
        height: 48px;

        :deep(.el-input__wrapper) {
          --el-input-border-radius: 8px 0 0 8px;
          border: none;
          box-shadow: none;
          font-size: 16px;
        }
      }

      :deep(.el-button) {
        flex: 0 0 clamp(76px, 10vw, 120px);
        height: 48px;
        font-size: 16px;
        border-radius: 0 8px 8px 0;
      }
    }
  }

}

.market__filter-error,
.market__list-error {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}

.market__list-error {
  justify-content: center;
  min-height: 160px;
}

.market__body {
  display: grid;
  grid-template-columns: clamp(210px, 23vw, 320px) minmax(0, 1fr);
  align-items: start;
  gap: clamp(12px, 1.8vw, 24px);
  width: min(100%, 1366px);
}

.market__categories-toggle {
  display: none;
}

.market__sidebar {
  display: flex;
  flex-direction: column;
  align-self: stretch;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px #00000014;

  .sidebar-header {
    flex-shrink: 0;
    color: #1b1e26;
    margin-bottom: 10px;

    h3 {
      margin-top: 20px !important;
      margin-left: 20px !important;
    }
    .nav-title {
      display: flex;
      align-items: center;
      margin-left: 8px;
      font-family: Source Han Sans SC Bold;
      font-size: 18px;
      font-weight: 700;
      text-align: left;
    }

    .img-back.two-nav::before {
      transform: scaleX(-1); /* 水平镜像 */
    }

    .two-nav {
      position: relative;
      height: 58px;
      &::before {
        position: absolute;
        top: 0;
        left: 0;
        z-index: 1;
        width: 100%;
        height: 100%;
        content: "";
        background-image: var(--images-market-tab);
        background-size: 100% 100%;
        transition: all 0.2s;
      }
    }

    .left-nav,
    .right-nav {
      position: absolute;
      z-index: 1;
      justify-content: center;
      width: 50%;
      margin-left: 0;
      height: 100%;
      cursor: pointer;
      color: #454750;
    }
    .left-nav {
      left: 0;
    }

    .right-nav {
      left: 50%;
    }

    .active {
      color: var(--el-color-primary);
    }
  }
  .sidebar-card__search {
    padding: 0 16px 16px;
  }
  .sidebar-card__list {
    flex: 1;
    min-height: 0;
    padding: 0 16px;
    overflow-y: auto;
    // 由右侧内容决定网格行高，树在剩余空间内滚动。
    contain: size;

    :deep(.el-tree) {
      --el-tree-node-content-height: 100%;
      --tree-node-checked-color: var(--el-color-primary);

      .el-tree-node__content > .el-tree-node__expand-icon {
        --el-tree-expand-icon-color: #000000e0;
        font-size: 16px;
        padding: 0;
        margin-top: 2px;
        margin-right: 4px;
        width: 24px;
      }

      .el-icon.is-leaf {
        width: 10px;
      }

      .el-tree-node__content {
        padding: 6px 0;
        margin-bottom: 5px;
        border-radius: 6px;
        align-items: flex-start;
        color: #000000e0;

        &:hover {
          color: var(--el-color-primary);
          .el-tree-node__expand-icon {
            color: var(--el-color-primary);
          }
        }
      }
    }

    :deep(.node-title) {
      gap: 16px;
      align-items: flex-start;
      justify-content: space-between;
    }
  }
}

.result-card {
  padding: 16px clamp(12px, 1.8vw, 24px) 24px;
  margin-top: clamp(12px, 1.8vw, 24px);
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px #00000014;

  .result-card__header {
    padding-bottom: 16px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px 16px;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #edf0f4;
    line-height: 20px;

    .result-count {
      font-size: 14px;
      color: #323643;
      & > span {
        color: #0090ff;
        padding: 0 3px;
      }
      i {
        font-style: normal;
        color: rgb(147, 153, 167);
        margin-left: 5px;
      }
    }

    .sort-options {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 12px;

      .sort-item {
        cursor: pointer;
        display: flex;
        align-items: center;
        position: relative;
        padding: 4px 18px 4px 0;
        white-space: nowrap;
        background: none;
        border: 0;
        font-size: 14px;
        font-weight: 400;
        color: #686c80;

        &.active {
          color: var(--el-color-primary);
        }

        .sort-icon {
          position: absolute;
          right: 0;
          cursor: pointer;
          color: #c1c5d0;
          &.active {
            color: var(--el-color-primary);
          }
        }
        .icon-top {
          top: 2px;
        }
        .icon-bottom {
          top: 9px;
        }
      }
    }
  }

  .result-card__header .left .time {
    margin-left: 8px;
    color: var(--el-text-color-secondary);
  }

  &__body {
    .pagination {
      text-align: center;
      margin-top: 24px;
      background: #fff;
      padding: 16px;
      border-radius: 8px;
      display: flex;
      justify-content: center;
      :deep(.el-pagination) {
        flex-wrap: wrap;
        justify-content: center;
        row-gap: 8px;
      }
      :deep(.el-pagination).is-background {
        .el-pager li.is-active {
          background-color: transparent;
          border-radius: 6px;
          border: 1px solid var(--el-color-primary);
          color: var(--el-color-primary);
        }
        .btn-prev:disabled {
          background-color: transparent;
        }
        .btn-next:disabled {
          background-color: transparent;
        }
        .el-select__wrapper {
          width: 100px;
        }
      }
    }
  }
}

@media (max-width: 760px) {
  .market__top {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .market__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .market__categories-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 44px;
    padding: 8px 14px;
    color: var(--el-text-color-primary);
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: inherit;
    cursor: pointer;

    > :last-child {
      margin-left: auto;
    }

    .category-selected {
      font-size: 12px;
      color: var(--el-color-primary);
    }
  }

  .market__sidebar:not(.is-open) {
    .sidebar-header,
    .sidebar-card__list {
      display: none;
    }
  }

  .market__sidebar .sidebar-card__list {
    flex: none;
    max-height: clamp(180px, calc(100dvh - 255px), 560px);
    contain: none;
  }

  .result-card__body .pagination {
    padding: 8px 0;
  }
}
</style>
