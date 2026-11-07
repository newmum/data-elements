<template>
  <div class="pt-5 px-5">
    <u-title class="py-0! flex-center" name="待申请数据目录">
      <el-tag round class="ml-2">{{ total }}</el-tag>
      <template #right>
        <el-space :size="16" wrap>
          <el-input
            v-model="keyword"
            class="cart-search"
            clearable
            :maxlength="100"
            placeholder="请输入搜索关键词"
            aria-label="搜索购物车目录"
            suffix-icon="search"
            @keydown.enter="applySearch"
          />

          <el-badge :value="drafts.length" :hidden="drafts.length === 0" :max="99">
            <el-button type="primary" plain @click="openDrafts">草稿箱</el-button>
          </el-badge>
        </el-space>
      </template>
    </u-title>
    <div v-if="selectedRows.size" class="cart-selection">
      <span role="status">已选 {{ selectedRows.size }} 项，最多可选 {{ selectionLimit }} 项</span>
      <el-button type="primary" link @click="selectedRows.clear()">清空选择</el-button>
    </div>
    <div v-if="hasUnconfirmedRows && !listError" class="cart-eligibility-notice">
      <span class="cart-eligibility-message" role="status">
        <Icon icon="el-icon-Warning" size="16" aria-hidden="true" />
        当前页有目录暂时无法确认资格，可重试校验。
      </span>
      <el-button type="primary" link :loading="loading" :disabled="Boolean(removingId)" @click="retryEligibility">
        重试资格校验
      </el-button>
    </div>
    <el-result v-if="listError" icon="error" title="购物车加载失败" :sub-title="listError">
      <template #extra>
        <el-button type="primary" plain @click="fetchList">重新加载</el-button>
      </template>
    </el-result>
    <page-list
      v-else
      row-key="shoppingCarId"
      :list="rows"
      :columns="fields"
      :loading="loading"
      :clickable="false"
      :total="total"
      :page-no="pageNo"
      :page-size="pageSize"
      @page-change="changePage"
    >
      <template #icon="{ row }">
        <el-checkbox
          class="cart-checkbox"
          :aria-label="`选择目录 ${row.catalogName || row.tid || '目录不可用'}`"
          :model-value="Boolean(isSelectable(row) && row.tid && selectedRows.has(row.tid))"
          :disabled="loading || !isSelectable(row)"
          @change="toggleSelection(row, $event === true)"
        />
        <Icon icon="category" size="18" />
      </template>

      <template #title="{ row }">
        <span :title="row.catalogName">{{ row.catalogName || "未命名目录" }}</span>
      </template>

      <template #status="{ row }">
        <div v-if="!isSelectable(row)" class="cart-row-status">
          <el-tag v-if="row.availabilityStatus === 'unconfirmed'" type="warning" size="small" disable-transitions>
            暂时无法确认资格
          </el-tag>
          <template v-else>
            <el-tag type="info" size="small" disable-transitions>暂不可申请</el-tag>
            <span class="cart-row-reason">{{ row.availabilityReason || '目录已不符合申请条件' }}</span>
          </template>
        </div>
      </template>

      <template #actions="{ row }">
        <el-button
          :class="{ 'cart-remove-visible': !isSelectable(row) }"
          type="danger"
          link
          icon="delete"
          :loading="removingId === row.shoppingCarId"
          :disabled="loading || Boolean(removingId)"
          @click="removeCartRow(row)"
        >
          移除
        </el-button>
      </template>

      <template #empty>
        <Empty
          v-if="!loading"
          type="list"
          :description="appliedKeyword ? '没有匹配的目录，请调整搜索关键词' : '购物车暂无目录'"
        />
      </template>
    </page-list>

    <el-drawer
      v-model="draftDrawerOpen"
      size="385"
      resizable
      title="待提交申请单"
      header-class="draft-drawer__header"
      body-class="draft-drawer__body"
    >
      <div v-loading="draftsLoading" class="draft-list">
        <el-result v-if="draftError" icon="error" title="草稿加载失败" :sub-title="draftError">
          <template #extra>
            <el-button type="primary" plain @click="fetchDrafts">重新加载</el-button>
          </template>
        </el-result>
        <div v-for="it in drafts" :key="it.tid" class="draft-card">
          <div class="card-header">
            <div class="card-title">
              <Icon icon="todo2" size="18" class="mr-1" />
              <span :title="it.applyName">{{ it.applyName }}</span>
            </div>
            <el-dropdown
              trigger="hover"
              placement="bottom-end"
              :hide-on-click="true"
              @command="(cmd) => handleDraftCommand(it, cmd)"
            >
              <el-button type="info" link style="rotate: 90deg; margin-right: -8px">
                <Icon icon="el-icon-MoreFilled" size="14" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete" :disabled="deletingDraft" style="color: var(--el-color-danger)">
                    <Icon icon="el-icon-delete" />
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="card-meta">
            <div class="meta-row">
              <span>资源数量：</span>

              <span>{{ it.resCount }}个</span>
            </div>
            <div class="meta-row">
              <span>更新时间：</span>
              <span>{{ it.updatedTime }}</span>
            </div>
          </div>
          <el-button type="primary" plain class="card-btn" :disabled="deletingDraft" @click="continueDraft(it)">
            <template #icon>
              <Icon icon="apply" />
            </template>
            继续申请
          </el-button>
        </div>
        <Empty v-if="!draftsLoading && !draftError && !drafts.length" description="暂无待提交申请单" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { ElCheckbox } from "element-plus";
import { useRegisterStore } from "@/store";
import { useCartCount } from "@/composables";

interface CartRow {
  tid?: string | null;
  shoppingCarId: string;
  catalogName?: string;
  available: boolean;
  availabilityStatus?: "available" | "unavailable" | "unconfirmed";
  availabilityReason?: string;
  [key: string]: unknown;
}

interface CartPage {
  list: CartRow[];
  total: number;
  cartTotal: number;
  pageNum: number;
  pageSize: number;
}

interface Draft {
  tid: string;
  applyName?: string;
  updatedTime?: string;
  catalogIds?: string;
  resCount?: number;
}

const selectionLimit = 100;
const fields = [
  { title: "数源单位", field: "orgId", options: "org" },
  { title: "资源类型", field: "resType", options: "assetType" },
  { title: "共享类型", field: "shareType", options: "shareType" },
  { title: "更新周期", field: "updateCycle", options: "updateCycle" },
  { title: "业务系统", field: "appId", options: "app" },
  { title: "选购时间", field: "addTime" },
];
const store = useRegisterStore();
const { beginRead, sessionRevision } = useCartCount();
const rows = ref<CartRow[]>([]);
const hasUnconfirmedRows = computed(() => rows.value.some((row) => row.availabilityStatus === "unconfirmed"));
const selectedRows = reactive(new Map<string, CartRow>());
const keyword = ref("");
const appliedKeyword = ref("");
const pageNo = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(true);
const listError = ref("");
const removingId = ref("");

const draftDrawerOpen = ref(false);
const drafts = ref<Draft[]>([]);
const draftsLoaded = ref(false);
const draftsLoading = ref(false);
const draftError = ref("");
const deletingDraft = ref(false);
let requestSequence = 0;
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let mounted = true;

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function showActionError(error: unknown, fallback: string) {
  if (!error || typeof error !== "object" || !("handled" in error && error.handled)) {
    $message.error(errorMessage(error, fallback));
  }
}

function isCartPage(value: unknown): value is CartPage {
  if (!value || typeof value !== "object") return false;
  const result = value as Partial<CartPage>;
  return (
    Array.isArray(result.list) &&
    result.list.every(
      (row) =>
        row &&
        typeof row.shoppingCarId === "string" &&
        Boolean(row.shoppingCarId.trim()) &&
        typeof row.available === "boolean" &&
        (!row.available || (typeof row.tid === "string" && Boolean(row.tid.trim()))),
    ) &&
    typeof result.total === "number" &&
    Number.isSafeInteger(result.total) &&
    result.total >= 0 &&
    typeof result.cartTotal === "number" &&
    Number.isSafeInteger(result.cartTotal) &&
    result.cartTotal >= 0 &&
    typeof result.pageNum === "number" &&
    Number.isSafeInteger(result.pageNum) &&
    result.pageNum >= 1 &&
    typeof result.pageSize === "number" &&
    Number.isSafeInteger(result.pageSize) &&
    result.pageSize >= 1 &&
    result.pageSize <= selectionLimit
  );
}

function isSelectable(row: CartRow) {
  return row.available && (row.availabilityStatus == null || row.availabilityStatus === "available");
}

function reconcilePageSelections(pageRows: CartRow[]) {
  // 选择按目录去重。遇到重复购物车记录时，优先保留原选中的可用记录。
  const pageSelections = new Map<string, CartRow | null>();
  for (const row of pageRows) {
    if (!row.tid) continue;
    const previous = selectedRows.get(row.tid);
    if (!previous) continue;
    if (isSelectable(row)) {
      if (!pageSelections.get(row.tid) || row.shoppingCarId === previous.shoppingCarId) {
        pageSelections.set(row.tid, row);
      }
    } else if (!pageSelections.has(row.tid)) {
      pageSelections.set(row.tid, null);
    }
  }
  for (const [tid, row] of pageSelections) {
    if (row) selectedRows.set(tid, row);
    else selectedRows.delete(tid);
  }
}

async function fetchList() {
  const sequence = ++requestSequence;
  const revision = sessionRevision.value;
  const commitCount = beginRead();
  loading.value = true;
  listError.value = "";
  try {
    const result: unknown = await $common.post("/dws/market/shoppingCardList", {
      mode: "page",
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      keyword: appliedKeyword.value,
    });
    if (sequence !== requestSequence || revision !== sessionRevision.value) return;
    if (!isCartPage(result)) throw new Error("购物车返回的数据格式不正确，请重新加载。");
    rows.value = result.list;
    total.value = result.total;
    pageNo.value = result.pageNum;
    pageSize.value = result.pageSize;
    commitCount(result.cartTotal);
    reconcilePageSelections(result.list);
  } catch (error: unknown) {
    if (sequence === requestSequence && revision === sessionRevision.value) {
      listError.value = errorMessage(error, "暂时无法读取目录，请稍后重试。");
    }
  } finally {
    if (sequence === requestSequence && revision === sessionRevision.value) loading.value = false;
  }
}

function applySearch() {
  clearTimeout(searchTimer);
  const search = keyword.value.trim();
  if (search === appliedKeyword.value) return;
  appliedKeyword.value = search;
  pageNo.value = 1;
  void fetchList();
}

function retryEligibility() {
  if (!hasUnconfirmedRows.value || loading.value || removingId.value) return;
  void fetchList();
}

function changePage({ pageNo: nextPage, pageSize: nextSize }: { pageNo: number; pageSize: number }) {
  const effectivePage = nextSize === pageSize.value ? nextPage : 1;
  if (effectivePage === pageNo.value && nextSize === pageSize.value) return;
  pageNo.value = effectivePage;
  pageSize.value = nextSize;
  void fetchList();
}

function toggleSelection(row: CartRow, checked: boolean) {
  if (loading.value || !isSelectable(row) || !row.tid) return;
  if (!checked) {
    selectedRows.delete(row.tid);
    return;
  }
  if (!selectedRows.has(row.tid) && selectedRows.size >= selectionLimit) {
    $message.warning("一次最多申请 100 个数据目录，请先取消部分选择。");
    return;
  }
  selectedRows.set(row.tid, row);
}

async function removeCartRow(row: CartRow) {
  if (removingId.value || loading.value) return;
  const revision = sessionRevision.value;
  removingId.value = row.shoppingCarId;
  try {
    const removed: unknown = await $common.post("/dws/market/shoppingCardRemove", {
      tid: row.shoppingCarId,
    });
    if (!mounted || revision !== sessionRevision.value) return;
    if (removed !== true) throw new Error("暂未确认目录已移除，请重新加载购物车。");
    if (row.tid && selectedRows.get(row.tid)?.shoppingCarId === row.shoppingCarId) {
      const replacement = rows.value.find(
        (item) => item.tid === row.tid && isSelectable(item) && item.shoppingCarId !== row.shoppingCarId,
      );
      if (replacement) selectedRows.set(row.tid, replacement);
      else selectedRows.delete(row.tid);
    }
    $message.success("已从购物车移除");
    await fetchList();
  } catch (error: unknown) {
    if (mounted && revision === sessionRevision.value) showActionError(error, "移除目录失败，请重试。");
  } finally {
    if (mounted && revision === sessionRevision.value) removingId.value = "";
  }
}

async function fetchDrafts() {
  if (draftsLoading.value) return;
  const revision = sessionRevision.value;
  draftsLoading.value = true;
  draftError.value = "";
  try {
    const data: unknown = await $common.post("/dws/market/applyForm/list", {
      type: "catalogSubscribe",
      flowStatus: 0,
    });
    if (!mounted || revision !== sessionRevision.value) return;
    if (!Array.isArray(data)) throw new Error("草稿箱返回的数据格式不正确，请重新加载。");
    drafts.value = data.map((draft: Draft) => ({
      ...draft,
      resCount: draft.resCount ?? draft.catalogIds?.split(",").filter(Boolean).length ?? 0,
    }));
    draftsLoaded.value = true;
  } catch (error: unknown) {
    if (mounted && revision === sessionRevision.value) {
      draftError.value = errorMessage(error, "暂时无法读取草稿，请稍后重试。");
    }
  } finally {
    if (mounted && revision === sessionRevision.value) draftsLoading.value = false;
  }
}

function openDrafts() {
  draftDrawerOpen.value = true;
  if (!draftsLoaded.value) void fetchDrafts();
}

function continueDraft(draft: Draft) {
  store.data = { apply: { tid: draft.tid } };
  store.handleAction("next");
}

function next() {
  if (draftDrawerOpen.value) {
    draftDrawerOpen.value = false;
    return;
  }
  if (loading.value || listError.value || removingId.value) throw new Error("请先完成购物车列表操作。");
  if (selectedRows.size === 0) throw new Error("请至少勾选一个可申请的数据目录。");
  store.data = { apply: { applyResource: [...selectedRows.values()] } };
}

async function handleDraftCommand(draft: Draft, command: string) {
  if (command !== "delete" || deletingDraft.value) return;
  const revision = sessionRevision.value;
  deletingDraft.value = true;
  const addToCart = ref(false);
  try {
    try {
      await $dialog({
        title: "删除申请",
        message: () =>
          h("div", null, [
            h("p", { style: "margin-bottom: 12px" }, `确定删除申请单【${draft.applyName}】？`),
            h(
              ElCheckbox,
              {
                modelValue: addToCart.value,
                "onUpdate:modelValue": (value: boolean) => (addToCart.value = value),
              },
              () => "将申请目录重新加入购物车",
            ),
          ]),
        showCancelButton: true,
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });
    } catch {
      return;
    }

    if (!mounted || revision !== sessionRevision.value) return;
    const removed: unknown = await $common.post("/dws/market/applyForm/deleteById", {
      tid: draft.tid,
      addToCart: addToCart.value,
    });
    if (!mounted || revision !== sessionRevision.value) return;
    if (removed !== true) throw new Error("暂未确认草稿已删除，请重新加载草稿箱。");
    $message.success("草稿已删除");
    drafts.value = drafts.value.filter((item) => item.tid !== draft.tid);
    if (addToCart.value) await fetchList();
  } catch (error: unknown) {
    if (mounted && revision === sessionRevision.value) showActionError(error, "删除草稿失败，请重试。");
  } finally {
    if (mounted && revision === sessionRevision.value) deletingDraft.value = false;
  }
}

watch(keyword, () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(applySearch, 250);
});

watch(
  sessionRevision,
  () => {
    requestSequence++;
    clearTimeout(searchTimer);
    rows.value = [];
    selectedRows.clear();
    keyword.value = "";
    appliedKeyword.value = "";
    pageNo.value = 1;
    total.value = 0;
    loading.value = false;
    listError.value = "";
    removingId.value = "";
    draftDrawerOpen.value = false;
    drafts.value = [];
    draftsLoaded.value = false;
    draftsLoading.value = false;
    draftError.value = "";
    deletingDraft.value = false;
  },
  { flush: "sync" },
);

onMounted(() => {
  void fetchList();
  void fetchDrafts();
});

onBeforeUnmount(() => {
  mounted = false;
  requestSequence++;
  clearTimeout(searchTimer);
});

defineExpose({ next });
</script>

<style scoped lang="scss">
.cart-search {
  width: min(300px, 55vw);
}

.cart-selection {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.cart-eligibility-notice {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 16px;
  margin-top: 12px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-warning-light-9);
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 20px;
}

.cart-eligibility-message,
.cart-row-status {
  display: flex;
  align-items: center;
  gap: 6px 8px;
}

.cart-eligibility-message :deep(.iconsvg) {
  flex: none;
  color: var(--el-text-color-regular);
}

.cart-row-status {
  flex-wrap: wrap;

  :deep(.el-tag) {
    --el-tag-text-color: var(--el-text-color-regular);
  }
}

.cart-row-reason {
  color: var(--el-text-color-regular);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.cart-checkbox {
  margin-right: 10px;
}

.cart-eligibility-notice :deep(.el-button:focus-visible),
:deep(.pl-actions .el-button:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}

:deep(.pl-container) {
  padding-top: 0px;
  .pl-toolbar {
    margin: 0 0;
  }
  .pl-row {
    padding: 14px 20px 14px 12px;
    animation: none !important;
  }
  .pl-header {
    margin-bottom: 12px;
  }
  .pl-columns {
    row-gap: 8px;
  }
  .pl-pagination {
    position: sticky;
    bottom: 0;
    z-index: 2;
    margin: 0;
    padding: 8px 12px;
    border-top: 1px solid var(--el-border-color-lighter);
    background: var(--el-bg-color);
  }
  .pl-title {
    cursor: default;
    &:hover {
      color: var(--title-color);
    }
  }
  .pl-header .pl-status {
    flex: 1 1 auto;
    min-width: 0;
    margin-left: 12px;
  }

  @media (max-width: 760px) {
    .pl-header {
      flex-wrap: wrap;
      gap: 6px 0;
    }
    .pl-header .pl-status:not(:empty) {
      flex: 1 0 100%;
      order: 1;
      margin-left: 0;
    }
    .pl-header .pl-title {
      max-width: none;
      flex: 1 1 0;
      min-width: 0;
    }
  }

  @media (hover: hover) {
    .pl-actions .el-button:not(.cart-remove-visible) {
      opacity: 0;
    }
    .pl-row:hover .pl-actions .el-button,
    .pl-row:focus-within .pl-actions .el-button {
      opacity: 1;
    }
  }
}

:deep(.el-drawer) {
  border-radius: 12px 0 0 12px !important;
}

:deep(.draft-drawer__header) {
  padding: 24px 28px 0 32px;
  border-width: 0;
}

:deep(.draft-drawer__body) {
  padding: 24px 32px;
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.draft-card {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: #fff;
  padding: 16px 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: "PingFang SC Medium";
  font-weight: 500;
  font-size: 16px;
  color: #323643;
  min-width: 0;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.card-meta {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  font-family: "PingFang SC", sans-serif;
  color: var(--el-text-color-secondary);
}
.card-btn {
  width: 100%;
}
</style>
