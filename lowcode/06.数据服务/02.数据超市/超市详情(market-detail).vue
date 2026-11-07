<template>
  <div
    class="market-detail__container images-market-bg"
    :class="{ 'market-detail__container-MixLayout': isLayoutDetail }"
  >
    <div class="market-detail__header">
      <detail-breadcrumb />
    </div>

    <div v-if="loading && !detail" class="market-detail__body" aria-busy="true">
      <LoadingState title="正在加载目录详情" compact />
    </div>
    <div v-else-if="!detail" class="market-detail__body">
      <el-result icon="info" title="暂时无法查看目录" :sub-title="loadError">
        <template v-if="id" #extra>
          <el-button type="primary" plain @click="loadDetail"
            >重新加载</el-button
          >
        </template>
      </el-result>
    </div>
    <div v-else class="market-detail__body">
      <div class="detail-info">
        <div class="detail-info__icon">
          <Icon :icon="leftIcon" size="83" />
        </div>
        <div class="detail-info-view">
          <div class="detail-info-view-title">
            <h2 :title="title">{{ title }}</h2>
            <div class="detail-info__actions">
              <el-tag
                v-if="statusLabel"
                :type="
                  detail.applicationState === 'approved' ? 'success' : 'info'
                "
              >
                {{ statusLabel }}
              </el-tag>
              <el-button
                v-if="detail.canApply"
                type="primary"
                :disabled="loading || modalOpen"
                @click="openApplication"
              >
                <template #icon>
                  <Icon icon="apply" />
                </template>
                申请
              </el-button>
              <el-button
                v-if="detail.canCollect"
                text
                :loading="collecting"
                :disabled="loading"
                @click="toggleCollection"
              >
                <template #icon>
                  <Icon :icon="detail.collected ? 'star-filled' : 'star'" />
                </template>
                {{ detail.collected ? "已收藏" : "收藏" }}
              </el-button>
            </div>
          </div>
          <div class="detail-info-view-fields">
            <div v-for="(item, index) in fields" :key="index" class="field">
              <div class="field-label">{{ item.title }}：</div>
              <div class="field-value">
                <template v-if="item.field === 'resources'">
                  {{ detail["resType"]?.length > 0 ? "有资源" : "无资源" }}
                </template>
                <dict-label
                  v-else
                  :model-value="detail[item.field]"
                  :options="item.options"
                  :suffix="item.suffix"
                  :tag="false"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <detail-page
        :key="detail.tid"
        class="detail-tabs"
        type="catalog"
        tab-type="market-catalog"
        :id="detail.tid"
        :title="title"
        :data-source-type="detail.dataSourceType || ''"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { usePermissionStore } from "@/store";
import { useCartCount, useRegisterModal } from "@/composables";

type ApplicationState =
  | "available"
  | "cart"
  | "draft"
  | "pending"
  | "approved"
  | "withdrawn"
  | "restricted"
  | "no-resources"
  | "no-department";

interface MarketDetail {
  tid: string;
  catalogName?: string;
  dataSourceType?: string;
  resType: string[];
  applicationState: ApplicationState;
  canApply: boolean;
  canCollect: boolean;
  collected: boolean;
  [field: string]: unknown;
}

const route = useRoute();
const permissions = usePermissionStore();
const { open: modalOpen, openRegisterModal } = useRegisterModal();
const { sessionRevision } = useCartCount();
const isLayoutDetail = computed(() => {
  const first = route.matched.find((item) => item.meta?.title);
  return (
    permissions.mixLayoutTopMenus.find((item) => item.path === first?.path)
      ?.component?.name === "Layout"
  );
});
const id = computed(() =>
  typeof route.query.id === "string" ? route.query.id.trim() : "",
);
const detail = ref<MarketDetail | null>(null);
const loading = ref(false);
const collecting = ref(false);
const loadError = ref("");
const applicationFor = ref("");
const title = computed(() => detail.value?.catalogName || "数据目录");
let requestSequence = 0;
let visitSequence = 0;
let viewRecorded = false;

const statusLabels: Partial<Record<ApplicationState, string>> = {
  cart: "已加购",
  draft: "待提交",
  pending: "申请处理中",
  approved: "已申请",
  withdrawn: "已撤回",
  restricted: "不予共享",
  "no-resources": "暂无可申请资源",
  "no-department": "当前账号未关联申请部门",
};
const statusLabel = computed(() =>
  detail.value ? statusLabels[detail.value.applicationState] : undefined,
);

function currentVisit() {
  return { id: id.value, path: route.path, revision: sessionRevision.value, sequence: visitSequence };
}

function isCurrentVisit(visit: ReturnType<typeof currentVisit>) {
  return (
    visit.sequence === visitSequence &&
    visit.id === id.value &&
    visit.path === route.path &&
    visit.revision === sessionRevision.value
  );
}

async function loadDetail() {
  const visit = currentVisit();
  const requested = visit.id;
  const sequence = ++requestSequence;
  loadError.value = "";
  if (!requested) {
    detail.value = null;
    loading.value = false;
    loadError.value = "缺少目录编号，请从数据超市重新打开。";
    return;
  }

  loading.value = true;
  try {
    const row = await $common.postSilently("/dws/market/detail", { tid: requested });
    if (sequence !== requestSequence || !isCurrentVisit(visit)) return;
    if (row instanceof Error) throw row;
    if (!row || row.tid !== requested || row.assetType !== "catalog") {
      throw new Error("目录详情与当前访问记录不一致，请重新加载。");
    }
    detail.value = row;
    // 一次详情访问只提交一次浏览计数，详情刷新沿用本次访问。
    if (!viewRecorded) {
      viewRecorded = true;
      void $common.postSilently("/dws/market/view", { tid: requested }).catch(() => {});
    }
  } catch (error: unknown) {
    if (sequence !== requestSequence || !isCurrentVisit(visit)) return;
    detail.value = null;
    loadError.value =
      error instanceof Error ? error.message : "请稍后重试，或返回数据超市。";
  } finally {
    if (sequence === requestSequence && isCurrentVisit(visit)) loading.value = false;
  }
}

function openApplication() {
  const row = detail.value;
  if (
    !row?.canApply ||
    row.tid !== id.value ||
    loading.value ||
    modalOpen.value
  )
    return;
  applicationFor.value = row.tid;
  openRegisterModal({
    registerClass: "apply",
    registerData: { apply: { applyResource: [{ ...row }] } },
  });
}

async function toggleCollection() {
  const row = detail.value;
  if (
    !row?.canCollect ||
    row.tid !== id.value ||
    collecting.value ||
    loading.value
  )
    return;
  const visit = currentVisit();
  const desired = !row.collected;
  collecting.value = true;
  try {
    const collected = await $common.postSilently("/dws/market/collect", {
      catalogId: row.tid,
      collected: desired,
    });
    if (!isCurrentVisit(visit)) return;
    if (collected instanceof Error) throw collected;
    if (collected !== desired)
      throw new Error("收藏状态暂未确认，请重新加载。");
    if (detail.value?.tid === row.tid) detail.value.collected = collected;
    if (loading.value) void loadDetail();
    $message.success(collected ? "已收藏" : "已取消收藏");
  } catch (error: unknown) {
    if (!isCurrentVisit(visit)) return;
    $message.error(error instanceof Error ? error.message : "收藏操作未完成，请重试。");
  } finally {
    if (isCurrentVisit(visit)) collecting.value = false;
  }
}

watch(
  [id, sessionRevision],
  () => {
    visitSequence++;
    detail.value = null;
    viewRecorded = false;
    collecting.value = false;
    applicationFor.value = "";
    void loadDetail();
  },
  { immediate: true },
);

watch(modalOpen, (open, wasOpen) => {
  if (open || !wasOpen || !applicationFor.value) return;
  const requested = applicationFor.value;
  applicationFor.value = "";
  if (requested === id.value) void loadDetail();
});
onBeforeUnmount(() => {
  visitSequence++;
});

const leftIcon = "market-catalog";
const fields = [
  { title: "提供部门", field: "orgId", options: "org" },
  { title: "资源情况", field: "resources" },
  { title: "数据条数", field: "recordCount", suffix: "条" },
  { title: "更新时间", field: "updatedTime" },
  { title: "资源类型", field: "resType", options: "assetType" },
  { title: "共享属性", field: "shareType", options: "shareType" },
  { title: "更新周期", field: "updateCycle", options: "updateCycle" },
  { title: "发布时间", field: "publishTime" },
];
</script>

<style scoped lang="scss">
.market-detail__container-MixLayout {
  margin: -16px -16px 0;
  padding-top: 0 !important;

  :deep(.detail-container .detail-container__tab) {
    padding: 0 24px !important;
    margin: 0 16px !important;
    border-radius: 8px 8px 0 0 !important;
    .el-tabs__nav-wrap:after {
      height: 1px;
      background-color: rgb(229, 231, 234) !important;
    }
  }

  :deep(.detail-container .detail-container__content) {
    margin: 0 16px 0 !important;
    border-radius: 0 0 8px 8px !important;
    .card-container {
      border-radius: 0;
    }
  }
}

.market-detail__container {
  min-height: 100%;
  padding-top: 55px;
  padding-bottom: 8px;
  padding-inline: clamp(12px, 2vw, 24px);

  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: var(--el-bg-color-page);
  background-size: 100% auto;
}

.market-detail__header {
  width: min(100%, 1345px);
  display: flex;
  margin-top: 9px;
  margin-bottom: 11px;
}

.market-detail__body {
  width: min(100%, 1345px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;

  .detail-info {
    display: flex;
    flex-direction: row;
    padding: 20px 24px;
    gap: 20px;
    background-color: #fff;
    border-radius: 8px;
    flex: 0;

    &__icon {
      flex: 0 0 auto;
      border: 1px solid #bfd4e3;
      border-radius: 8px;
      width: 85px;
      height: 85px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(180deg, #eef6ff 0%, #fbfdff 100%);
      color: #0b8bf9;
    }

    &__actions {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;

      .el-button + .el-button {
        margin-left: 0;
      }
    }

    &-view {
      flex: 1;
      min-width: 0;
      &-title {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: 8px 16px;
        margin-bottom: 11px;

        h2 {
          color: #383838;
          font-size: 16px;
          margin: 0;
          font-weight: 700;
          word-break: break-all;
          overflow: hidden;
          white-space: nowrap;
          text-overflow: ellipsis;
          min-width: 0;
          max-width: 100%;
          line-height: 32px;
        }
      }

      &-fields {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 14px 8px;
        width: 100%;
        font-family:
          -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
          "Helvetica Neue", Arial, "Noto Sans", sans-serif, "Apple Color Emoji",
          "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji";

        color: #636981;
        font-size: 14px;

        .field {
          display: flex;
          min-width: 0;

          .field-label {
            flex-shrink: 0;
          }

          .field-value {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }
      }
    }
  }

  :deep(.detail-tabs) {
    border-radius: 8px;
    height: 100%;
    flex: 1 1 200px;
    margin-top: 0;
    .el-tabs {
      --el-tabs-header-height: 46px;
    }
    .detail-container__breadcrumb {
      display: none;
    }
  }
}

@media (max-width: 1100px) {
  .market-detail__body .detail-info-view-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .market-detail__body .detail-info {
    padding: 16px;
    gap: 12px;

    &__icon {
      display: none;
    }

    &-view-fields {
      grid-template-columns: minmax(0, 1fr);
    }
  }
}
</style>
