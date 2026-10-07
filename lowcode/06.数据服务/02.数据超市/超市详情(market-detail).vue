<template>
  <div
    class="market-detail__container images-market-bg"
    :class="{ 'market-detail__container-MixLayout': isLayoutDetail }"
  >
    <div class="market-detail__header">
      <detail-breadcrumb />
    </div>

    <div class="market-detail__body">
      <div class="detail-info">
        <div class="detail-info__icon">
          <Icon :icon="leftIcon" size="83" />
        </div>
        <div class="detail-info-view">
          <div class="detail-info-view-title">
            <h2 :title="title">{{ title }}</h2>
            <div style="margin-top: -8px">
              <el-button type="primary" text @click="handleAction('apply')">
                <template #icon>
                  <Icon icon="apply" />
                </template>
                申请
              </el-button>
              <el-button style="margin-left: -8px" text @click="handleAction('collect')">
                <template #icon>
                  <Icon :icon="isCollected ? 'star-filled' : 'star'" style="margin-top: -2px" />
                </template>
                <span style="color: #000000e0">{{ isCollected ? "已收藏" : "收藏" }}</span>
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

      <detail-page class="detail-tabs" type="catalog" tab-type="market-catalog" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { usePermissionStore } from "@/store";

// 判断是否Layout布局下的详情
const permissionStore = usePermissionStore();
const isLayoutDetail = computed(() => {
  const matched = route.matched.filter((item) => item.meta && item.meta.title);
  return (
    permissionStore.mixLayoutTopMenus.find((el) => el.path === matched[0].path)?.component?.name ===
    "Layout"
  );
});

const route = useRoute();
const assetType = computed(() => {
  return route.query.type || "catalog";
});
const title = computed<string>(() => {
  return route.query.title as string; // 增加类型断言，避免TS警告
});
const id = computed<string>(() => {
  return route.query.id as string; // 增加类型断言
});
const detail = ref<any>({});
const isCollected = computed(() => detail.value?.collected);
const config = computed(() => (assetType.value ? configs[assetType.value] : undefined));

const fields = computed(() => {
  return config.value?.fields || []; // 改为数组默认值
});

const updateBrowse = async () => {
  if (!id.value) {
    return;
  }
  $common.post("/dws/market/view", { tid: id.value });
};

const leftIcon = computed<string | undefined>(() => (config.value as any)?.icon);
onMounted(() => {
  handleAction("init");
});

const handleAction = (type: string) => {
  switch (type) {
    case "init":
      updateBrowse();
      //@todo 获取详情
      $common.post("/dws/market/detail", { tid: id.value }).then((data) => {
        detail.value = data;
      });
      break;
    case "collect":
      $common
        .post("/dws/market/collect", {
          catalogId: detail.value.tid,
        })
        .then((res) => {
          detail.value.collected = !detail.value.collected;
          $message.success(res ? "收藏成功" : "取消收藏");
        });
      break;
    case "apply":
      // 可补充申请逻辑
      break;
  }
};

const configs = {
  catalog: {
    icon: "market-catalog",
    fields: [
      { title: "提供部门", field: "orgId", options: "org" },
      { title: "资源情况", field: "resources", options: "" },
      { title: "数据条数", field: "recordCount", suffix: "条" },
      { title: "更新时间", field: "updatedTime" },
      { title: "资源类型", field: "resType", options: "assetType" },
      { title: "共享属性", field: "shareType", options: "shareType" },
      { title: "更新周期", field: "updateCycle", options: "updateCycle" },
      { title: "发布时间", field: "publishTime", options: "" },
    ],
  },
  api: {
    icon: "market-api",
    fields: [
      { title: "数源单位", field: "orgId", options: "org" },
      { title: "共享属性", field: "shareType", options: "shareType" },
      { title: "所属网域", field: "storageDomian", options: "" },
      { title: "更新时间", field: "updatedTime", options: "" },
      { title: "请求参数", field: "reqCount", options: "" },
      { title: "响应参数", field: "resCount", options: "" },
      { title: "更新周期", field: "updateCycle", options: "updateCycle" },
      { title: "发布时间", field: "publishTime", options: "" },
    ],
  },
};
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

  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: var(--el-bg-color-page);
  background-size: 100% auto;
}

.market-detail__header {
  width: 1345px;
  display: flex;
  margin-top: 9px;
  margin-bottom: 11px;
}

.market-detail__body {
  width: 1345px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;

  .detail-info {
    display: flex;
    flex-direction: row;
    padding-right: 28px;
    background-color: #fff;
    border-radius: 8px;
    flex: 0;

    &__icon {
      flex: 0 0 auto;
      border: 1px solid #bfd4e3;
      border-radius: 8px;
      width: 85px;
      height: 85px;
      margin: 26px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(180deg, #eef6ff 0%, #fbfdff 100%);
      color: #0b8bf9;
    }

    &-view {
      flex: 1;
      margin-top: 21px;
      &-title {
        display: flex;
        justify-content: space-between;
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
          max-width: 500px;
          line-height: 32px;
        }
      }

      &-fields {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 14px 8px;
        width: 100%;
        font-family:
          -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial,
          "Noto Sans", sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol",
          "Noto Color Emoji";

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
</style>
