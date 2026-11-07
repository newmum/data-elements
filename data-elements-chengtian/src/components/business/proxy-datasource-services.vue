<template>
  <section
    class="proxy-datasource-services"
    :class="`proxy-datasource-services--${mode}`"
    :aria-label="title"
  >
    <div class="proxy-datasource-services__toolbar">
      <UTitle v-if="mode === 'step1'" :name="title" class="proxy-datasource-services__title" />
      <strong v-else>{{ title }}</strong>
      <div class="proxy-datasource-services__actions">
        <el-input
          v-model.trim="keywordInput"
          class="proxy-datasource-services__search"
          placeholder="搜索服务名称、编码或地址"
          aria-label="搜索服务名称、编码或地址"
          :disabled="!datasourceId || sourceBlocked"
          clearable
          @clear="applyKeyword"
          @keyup.enter="applyKeyword"
        >
          <template #suffix>
            <el-button
              link
              type="primary"
              aria-label="查询服务"
              :disabled="!datasourceId || sourceBlocked"
              @click="applyKeyword"
            >
              <Icon icon="search" />
            </el-button>
          </template>
        </el-input>
        <slot name="actions" />
      </div>
    </div>

    <LoadingState v-if="sourceLoading" type="table" compact />
    <div v-else-if="sourceError" class="proxy-datasource-services__empty" role="alert">
      {{ sourceError }}
      <el-button link type="primary" @click="emit('retry-source')">重新读取</el-button>
    </div>
    <DataTable
      v-else
      :key="datasourceId"
      ref="table"
      class="proxy-datasource-services__table"
      height="auto"
      flex-type="flex-[0_1_auto]"
      :data="fetchServices"
      :columns="columns"
      :show-search="false"
      :show-reset="false"
      :page-sizes="pageSizes"
      :immediate="Boolean(datasourceId)"
      row-key="tid"
      show-index
    >
      <el-table-column
        prop="serviceName"
        label="服务名称"
        min-width="180"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column prop="method" label="请求方式" width="110" />
      <el-table-column
        prop="publishAddressFull"
        label="服务地址"
        min-width="230"
        show-overflow-tooltip
      />
      <el-table-column prop="status" label="状态" width="105">
        <template #default="{ row }">
          <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedTime" label="更新时间" width="170" sortable="custom" />
      <template #empty>
        <div class="proxy-datasource-services__empty" :role="listError ? 'alert' : 'status'">
          {{ listError || emptyLabel }}
          <el-button v-if="listError" link type="primary" @click="refresh()">重新读取</el-button>
        </div>
      </template>
    </DataTable>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import requestPolicy from "@/utils/request-policy";

interface ServiceRow {
  tid: string;
  serviceName: string;
  method: string;
  publishAddressFull: string;
  status?: string;
  updatedTime: string;
}

interface ServicePage {
  list: ServiceRow[];
  total: number | string;
}

interface PageQuery {
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortDir?: "asc" | "desc";
}

const props = withDefaults(
  defineProps<{
    sourceId: string;
    mode?: "step1" | "review";
    sourceLoading?: boolean;
    sourceError?: string;
  }>(),
  { mode: "step1", sourceLoading: false, sourceError: "" }
);

const emit = defineEmits<{ "retry-source": [] }>();
const datasourceId = computed(() => props.sourceId.trim());
const sourceBlocked = computed(() => props.sourceLoading || Boolean(props.sourceError));
const title = computed(() => (props.mode === "review" ? "已登记数据服务" : "当前数据源已登记服务"));
const pageSizes = computed(() => (props.mode === "review" ? [20, 50, 100] : [10, 20, 50, 100]));
const columns: [] = [];
const emptyPage = { list: [], total: 0 };
const table = ref<{ refresh: (resetPage?: boolean) => void }>();
const keywordInput = ref("");
const keyword = ref("");
const listError = ref("");
let activeRequest: AbortController | undefined;

const emptyLabel = computed(() => {
  if (props.mode === "review") {
    return keyword.value ? "没有匹配的数据服务" : "尚未登记服务，请返回第一步登记服务。";
  }
  if (keyword.value) return "没有匹配的服务，请调整搜索关键词。";
  return datasourceId.value ? "暂无已登记服务" : "保存数据源后，可登记服务。";
});

function statusLabel(status?: string) {
  if (status === "published") return "已发布";
  if (status === "offline") return "已停用";
  if (props.mode === "review" && status && status !== "draft") return status;
  return "草稿";
}

function refresh(resetPage = false) {
  table.value?.refresh(resetPage);
}

function applyKeyword() {
  keyword.value = keywordInput.value.trim();
  refresh(true);
}

async function fetchServices({ pageNo, pageSize, sortField, sortDir }: PageQuery) {
  const id = datasourceId.value;
  if (!id || sourceBlocked.value) return emptyPage;

  // 切源、重新搜索或翻页时取消旧请求，只有当前请求可以更新错误和结果。
  activeRequest?.abort();
  const requestController = new AbortController();
  activeRequest = requestController;
  listError.value = "";
  try {
    const result = await requestPolicy.quiet<ServicePage | Error>({
      url: "/dws/flowserve/gateway/api/list",
      method: "post",
      data: {
        datasourceId: id,
        pageNum: pageNo,
        pageSize,
        sortField,
        sortDir,
        keyword: keyword.value,
      },
      signal: requestController.signal,
    });
    if (requestController.signal.aborted) return emptyPage;
    if (result instanceof Error) throw result;
    const rawTotal = result?.total;
    const total = typeof rawTotal === "string" && rawTotal.trim() ? Number(rawTotal) : rawTotal;
    if (
      !Array.isArray(result?.list) ||
      typeof total !== "number" ||
      !Number.isSafeInteger(total) ||
      total < 0
    ) {
      throw new Error("服务清单响应不完整，请重新读取");
    }
    return { list: result.list, total };
  } catch (error) {
    if (!requestController.signal.aborted) {
      listError.value =
        error instanceof Error && error.message ? error.message : "服务清单读取失败，请重新读取";
    }
    return emptyPage;
  } finally {
    if (activeRequest === requestController) activeRequest = undefined;
  }
}

watch(
  datasourceId,
  () => {
    activeRequest?.abort();
    keywordInput.value = "";
    keyword.value = "";
    listError.value = "";
  },
  { flush: "sync" }
);
watch(
  sourceBlocked,
  (blocked) => {
    if (blocked) activeRequest?.abort();
  },
  { flush: "sync" }
);
onBeforeUnmount(() => activeRequest?.abort());
defineExpose({ refresh });
</script>

<style scoped>
.proxy-datasource-services {
  min-width: 0;
}

.proxy-datasource-services__toolbar,
.proxy-datasource-services__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.proxy-datasource-services__toolbar {
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.proxy-datasource-services__actions {
  justify-content: flex-end;
  margin-left: auto;
}

.proxy-datasource-services__title {
  flex: 0 0 auto;
  width: auto;
  height: 38px;
}

.proxy-datasource-services__search {
  width: 320px;
  max-width: 100%;
}

.proxy-datasource-services--review .proxy-datasource-services__search {
  width: 300px;
}

.proxy-datasource-services__table {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.proxy-datasource-services--step1 .proxy-datasource-services__table {
  min-height: 280px;
}

.proxy-datasource-services--review .proxy-datasource-services__table {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 0 0 7px 7px;
}

.proxy-datasource-services--review .proxy-datasource-services__table :deep(.el-table .cell) {
  white-space: nowrap;
}

.proxy-datasource-services__table :deep(.el-table__empty-text) {
  width: 100%;
  line-height: normal;
}

.proxy-datasource-services__table :deep(.el-table__empty-block) {
  padding: 0;
}

.proxy-datasource-services__empty {
  padding: 40px 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.proxy-datasource-services__empty .el-button {
  margin-left: 8px;
}

@media (max-width: 600px) {
  .proxy-datasource-services__actions {
    width: 100%;
  }

  .proxy-datasource-services__search,
  .proxy-datasource-services--review .proxy-datasource-services__search {
    flex: 1;
    width: auto;
    min-width: 0;
  }
}
</style>
