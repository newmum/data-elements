<template>
  <el-card class="warehouse-card">
    <el-skeleton :loading="loading || !item" animated :rows="4">
      <template #template>
        <div class="flex gap-4">
          <div class="header-icon skeleton-icon"></div>
          <div class="flex flex-col" style="flex: 1">
            <el-skeleton-item
              variant="text"
              style="height: 24px; width: 120px; margin-bottom: 8px"
            />
            <el-skeleton-item variant="text" style="height: 16px; width: 80px" />
          </div>
        </div>
        <div style="margin: 12px 0 20px 0">
          <el-skeleton-item variant="text" style="height: 24px" />
          <el-skeleton-item variant="text" style="height: 24px" />
        </div>
        <div class="flex justify-between items-center">
          <el-skeleton-item variant="text" style="height: 32px; width: 60px" />
          <div class="flex" style="width: 135px">
            <el-skeleton-item variant="button" style="height: 32px; width: 100px" />
            <el-skeleton-item
              variant="button"
              style="height: 32px; width: 32px; margin-left: 8px; border-radius: 50%"
            />
          </div>
        </div>
      </template>
      <template #default>
        <div v-if="item">
          <el-skeleton :loading="loading" animated>
            <!-- 实际卡片内容 -->
            <template #default>
              <div class="card-header">
                <div
                  class="header-icon flex items-center justify-center"
                  :class="`images-warehouse-${item.appIcon}`"
                ></div>
                <div class="header-info">
                  <div class="header-title" :title="item.appName">{{ item.appName }}</div>
                  <div class="header-name flex items-center gap-1">
                    <Icon :icon="'role'" :size="14" />
                    {{ item.createdBy || "-" }}
                  </div>
                </div>
              </div>
              <div class="description" :title="item.description">{{ item.description || "-" }}</div>
              <div class="flex justify-between items-center">
                <el-tag :type="item.publishStatus === 1 ? 'success' : 'warning'">
                  {{ item.publishStatus === 1 ? "已发布" : "待发布" }}
                </el-tag>
                <div class="flex" style="width: 135px">
                  <el-button type="primary" plain @click="handleClick('edit')">
                    <Icon :icon="'setting'" class="mr-2" />
                    编辑配置
                  </el-button>
                  <el-dropdown @command="handleDropdownCommand">
                    <span class="footer-more">
                      <Icon icon="el-icon-MoreFilled" size="14" />
                    </span>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="i in contextmenu"
                          :key="i.key"
                          :command="i.key"
                          :class="{ 'delete-item': i.key === 'delete' }"
                          :style="i.key === 'delete' ? { color: '#f56c6c' } : {}"
                        >
                          <Icon :icon="i.icon" class="mr-1" />
                          {{ i.label }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </template>

            <!-- 骨架屏模板 -->
            <template #template>
              <div class="skeleton-content">
                <!-- 头部骨架 -->
                <div class="flex gap-4">
                  <el-skeleton-item
                    variant="rect"
                    style="width: 64px; height: 64px; border-radius: 16px"
                  />
                  <div class="flex flex-col gap-2">
                    <el-skeleton-item variant="text" style="width: 120px; height: 24px" />
                    <el-skeleton-item variant="text" style="width: 80px; height: 16px" />
                  </div>
                </div>

                <!-- 描述骨架 -->
                <div class="skeleton-description">
                  <el-skeleton-item
                    variant="text"
                    style="width: 100%; height: 16px; margin-bottom: 8px"
                  />
                  <el-skeleton-item variant="text" style="width: 80%; height: 16px" />
                </div>

                <!-- 底部骨架 -->
                <div class="flex justify-between items-center">
                  <el-skeleton-item variant="button" style="width: 70px; height: 28px" />
                  <div class="flex gap-2">
                    <el-skeleton-item variant="button" style="width: 90px; height: 32px" />
                    <el-skeleton-item variant="circle" style="width: 24px; height: 24px" />
                  </div>
                </div>
              </div>
            </template>
          </el-skeleton>
        </div>
      </template>
    </el-skeleton>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from "vue";

// 应用数据接口（匹配 API 返回字段）
interface AppItem {
  tid: string;
  tenantId?: string;
  appName: string;
  appType: number;
  appIcon: string;
  description: string;
  publishStatus: number; // 0-待发布 1-已发布
  createdBy?: string;
  createdTime?: string;
  updatedBy?: string;
  updatedTime?: string;
}

const props = defineProps<{
  item?: AppItem;
  loading?: boolean;
}>();

const emit = defineEmits(["operation-click"]);

const contextmenu = ref([
  {
    label: props.item?.publishStatus === 0 ? "发布应用" : "取消发布",
    key: "publish",
    icon: "el-icon-Position",
  },
  {
    label: "删除应用",
    key: "delete",
    icon: "el-icon-delete",
  },
]);

const handleClick = (action: string) => {
  emit("operation-click", action, props.item);
};

const handleDropdownCommand = (command: string) => {
  emit("operation-click", command, props.item);
};
</script>

<style lang="scss" scoped>
.warehouse-card {
  padding: 16px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid #eaedf7;
  // box-shadow: none !important;
  // border-radius: 6px;
  box-shadow: 0 0 12px #f3f4fa;
  &:hover {
    transform: translateY(-4px);
    border: 1px solid #5591ff;
  }
  .card-header {
    display: flex;
    gap: 16px;
    align-items: flex-start;
  }
  .header-icon {
    width: 64px;
    min-width: 64px;
    height: 64px;
    border-radius: 16px;
    background-size: cover;
  }
  .header-info {
    flex: 1;
    min-width: 0;
  }
  .header-title {
    font-family: "PingFang SC Medium";
    font-weight: 500;
    font-size: 16px;
    line-height: 40px;
    text-align: left;
    color: #323643;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .header-name {
    font-family: "PingFang SC";
    font-weight: 400;
    font-size: 14px;
    text-align: left;
    color: #828691;
  }
  .description {
    font-family: "PingFang SC";
    font-weight: 400;
    font-size: 14px;
    line-height: 24px;
    text-align: left;
    color: #828691;
    margin: 12px 0 20px 0;
    height: 48px;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  :deep(.el-card__body) {
    padding: 0 !important;
    overflow: hidden;
  }
  .footer-more {
    outline: none;
    cursor: pointer;
    transform: rotate(90deg);
  }
  .skeleton-icon {
    border-radius: 16px;
    background: linear-gradient(90deg, #f0f2f5 25%, #e6e8eb 37%, #f0f2f5 63%);
    background-size: 400% 100%;
    animation: skeleton-loading 1.4s ease infinite;
  }
  @keyframes skeleton-loading {
    0% {
      background-position: 100% 50%;
    }
    100% {
      background-position: 0 50%;
    }
  }

  .skeleton-content {
    .skeleton-description {
      margin: 12px 0 20px 0;
    }
  }
}
</style>
