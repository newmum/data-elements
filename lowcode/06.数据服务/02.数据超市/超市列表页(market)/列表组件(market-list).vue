<template>
  <div v-loading="loading" class="list-wrapper">
    <div v-if="dataList.length === 0" class="empty-list">
      <Empty />
    </div>
    <template v-for="(item, ind) in dataList" :key="ind">
      <div class="asset-item">
        <div class="asset-header">
          <div class="header-left">
            <span
              class="title"
              :class="{ new: isNewItem(item.publishTime) }"
              @click="$emit('detail', item)"
            >
              <Icon v-if="item.assetType" :icon="item.assetType" size="18" />
              {{ item[`${item.assetType}Name`] }}
            </span>
            <el-tag
              v-if="item.applyStatus != null"
              size="small"
              :type="getApplyTagConfig(item).type"
              :effect="getApplyTagConfig(item).effect"
              class="apply-status-inline-tag"
              round
            >
              {{ getApplyTagConfig(item).text }}
            </el-tag>
          </div>
          <div class="header-right">
            <Icon icon="eye" style="color: var(--el-color-primary)"></Icon>
            {{ item.viewCount }}
          </div>
        </div>

        <div class="asset-info">
          <div class="info-row">
            <div
              v-for="(field, index) in displayFields?.slice(0, 3)"
              :key="index"
              class="info-item"
            >
              <span class="field-label">{{ field.title }}:</span>
              <span class="field-value" :title="getFieldValue(item, field.field)">
                <dict-label
                  :model-value="item[field.field]"
                  :options="field.options"
                  :tag="false"
                />
              </span>
            </div>
          </div>
          <div class="info-row">
            <div
              v-for="(field, index) in displayFields?.slice(3, 6)"
              :key="index"
              class="info-item"
            >
              <span class="field-label">{{ field.title }}:</span>
              <span class="field-value" :title="getFieldValue(item, field.field)">
                <dict-label
                  :model-value="item[field.field]"
                  :options="field.options"
                  :show-tag="false"
                />
              </span>
            </div>
          </div>
        </div>

        <div class="asset-actions">
          <div class="action-left">
            <el-text
              class="asset-type"
              :class="{ 'resource-active': hasResourceType(item, 'table') }"
            >
              <icon icon="db1" size="14" />
              库表
            </el-text>
            <el-text
              class="asset-type"
              :class="{ 'resource-active': hasResourceType(item, 'api') }"
            >
              <icon icon="api1" size="14" />
              API
            </el-text>
            <el-text
              class="asset-type"
              :class="{ 'resource-active': hasResourceType(item, 'file') }"
            >
              <Icon icon="file1" size="14" />
              文件
            </el-text>
            <el-text
              class="asset-type"
              :class="{ 'resource-active': hasResourceType(item, 'dir') }"
            >
              <Icon icon="dir" size="14" />
              文件夹
            </el-text>
          </div>
          <div class="action-right">
            <ElButton
              v-if="showCollect"
              :class="{ collected: item.collected === '1' }"
              @click="$emit('collect', item)"
            >
              <template #icon>
                <Icon v-if="item.collected" icon="star-filled" size="14" />
                <Icon v-else icon="star" size="14" />
              </template>
              <span>{{ item.collected ? "已收藏" : "收藏" }}</span>
            </ElButton>
            <template v-if="item.applyStatus === 2">
              <ElButton type="primary" plain @click="$emit('detail', item)">
                <template #icon><Icon icon="eye" /></template>
                查看详情
              </ElButton>
            </template>
            <template
              v-else-if="
                item.applyStatus === 0 || item.applyStatus === 1 || item.applyStatus === 'cart'
              "
            >
              <ElButton type="primary" plain disabled>
                <template #icon><Icon icon="apply1" /></template>
                申请
              </ElButton>
            </template>
            <template v-else>
              <ElButton type="primary" plain @click="$emit('apply', item, $event)">
                <template #icon><Icon icon="apply" /></template>
                {{ showApply || "申请" }}
              </ElButton>
            </template>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";

const props = withDefaults(
  defineProps<{
    dataList: Array;
    loading: boolean;
    item: Record<string, any>;
    displayFields: { title: string; field: string }[];
    showApply?: string;
    showCollect?: boolean;
  }>(),
  {
    showApply: "申请",
    showCollect: true,
  }
);

defineEmits(["collect", "apply", "page-change", "detail"]);

// 获取字段值
const getFieldValue = (item: any, field: string) => {
  return item[field];
};

// 判断是否为新上架项目（7天内）
const isNewItem = (dateStr: string) => {
  if (!dateStr) return false;
  const publishDate = new Date(dateStr);
  const now = new Date();
  const diffTime = now.getTime() - publishDate.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24));
  return diffDays <= 7;
};

// 判断是否包含特定资源类型
const hasResourceType = (item: any, typeName: string) => {
  return item.resType && item.resType.includes(typeName);
};

// 申请状态 Tag 配置：根据 applyStatus 返回文字、类型、效果、图标
const getApplyTagConfig = (item: any) => {
  const s = item.applyStatus;
  if (s === 2) return { text: "已申请", type: "success", effect: "dark", icon: "check-circle" };
  if (s === 1) return { text: "审核中", type: "warning", effect: "dark", icon: "el-icon-Timer" };
  if (s === 0) return { text: "待提交", type: "primary", effect: "dark", icon: "el-icon-Edit" };
  if (s === "cart")
    return { text: "已加购", type: "warning", effect: "dark", icon: "el-icon-ShoppingCartFull" };
  return { text: "申请", type: "primary", effect: "dark", icon: "apply" };
};
</script>

<style scoped lang="scss">
.list-wrapper {
  position: relative;
}

.empty-list {
  background: #fff;
  padding: 40px 0;
  border-radius: 8px;
  margin-bottom: 16px;
}

.asset-item {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 0 16px 8px;

  //box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);

  .asset-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .apply-status-inline-tag {
        display: inline-flex;
        align-items: center;
        gap: 2px;
        flex-shrink: 0;
      }

      .title {
        display: flex;
        align-items: center;
        gap: 4px;
        font-family: PingFang SC Medium;
        font-size: 16px;
        margin-right: 8px;
        cursor: pointer;
        line-height: 20px;
        font-weight: 600;
        color: #1b1e26;
        &:hover {
          color: var(--el-color-primary);
        }
      }
    }

    .header-right {
      color: #999;
      display: flex;
      align-items: center;

      .el-icon {
        margin-right: 4px;
      }
    }
  }

  .asset-info {
    padding-left: 25px;
    line-height: 20px;
    color: #686c80;
    .info-row {
      display: flex;
      justify-content: space-between;
      gap: 6px;
      margin-bottom: 6px;

      .info-item {
        flex: 1;
        display: flex;
        width: 33%;

        .field-label {
          min-width: 70px;
          flex-shrink: 0;
        }

        .field-value {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          flex: 1;
        }
      }
    }
  }

  .asset-actions {
    display: flex;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 25px;

    .action-left {
      display: flex;
      gap: 16px;

      .asset-type {
        background-color: #f9f9fc;
        cursor: pointer;
        padding: 8px 12px;
        border-radius: 8px;
        font-family: "Source Han Sans CN";
        font-weight: 400;
        font-size: 14px;
        color: #323643;

        &-active {
          color: var(--el-color-primary);
          background-color: var(--el-color-primary-light-9);
        }
      }

      .resource-active {
        color: var(--el-color-primary);
        background-color: var(--el-color-primary-light-9);
      }
    }

    .action-right {
      display: flex;
      gap: 8px;
      align-items: center;

      button {
        min-width: 100px;
      }

      .apply-status-tag {
        height: 32px;
        padding: 0 12px;
        font-size: 13px;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: default;
        border-radius: 4px;
      }
    }
  }
}
</style>
