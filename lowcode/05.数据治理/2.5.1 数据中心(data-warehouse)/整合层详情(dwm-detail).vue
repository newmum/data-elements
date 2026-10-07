<template>
  <div class="dwm-detail card-container px-5 pt-5">
    <div class="dwm-detail__header flex items-center justify-between">
      <div class="header-left flex items-center gap-3">
        <h3 class="header-title">整合层（DWM）业务主题规划全景图</h3>
        <div class="header-stats">
          <el-tag type="info" size="small" class="stat-tag">
            {{ stats.warehouseCount }} 个业务域
          </el-tag>
          <el-tag type="warning" size="small" class="stat-tag">
            {{ stats.domainCount }} 个业务线
          </el-tag>
          <el-tag size="small" class="stat-tag stat-tag--purple">
            {{ stats.processCount }} 个业务事项
          </el-tag>
        </div>
      </div>
      <div class="header-actions flex items-center gap-2">
        <!-- 展开/收起工具栏 -->
        <el-button-group>
          <el-tooltip content="展开所有业务线" placement="top" :show-after="400">
            <el-button size="small" @click="handleExpandAll">
              <i class="icon-expand-all"></i>
              展开全部
            </el-button>
          </el-tooltip>
          <el-tooltip content="收起所有业务线" placement="top" :show-after="400">
            <el-button size="small" @click="handleCollapseAll">
              <i class="icon-collapse-all"></i>
              收起全部
            </el-button>
          </el-tooltip>
        </el-button-group>

        <el-divider direction="vertical" />

        <el-button type="primary" @click="handleCreateWarehouse">
          <Icon :icon="'el-icon-Plus'" class="mr-1" />
          新建业务域
        </el-button>
      </div>
    </div>
    <div class="dwm-detail__content">
      <dwm-visual-tree ref="visualTreeRef" :name="visualName" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute } from "vue-router";
const route = useRoute();
// import dwmVisualTree from "./dwm-visual-tree.vue";

const visualTreeRef = ref();
const visualName = ref("");

/** 动态统计数据，从子组件 expose 中读取 */
const stats = computed(() => {
  return (
    visualTreeRef.value?.stats ?? {
      warehouseCount: 0,
      domainCount: 0,
      processCount: 0,
    }
  );
});

/** 新建业务域：调用子组件暴露的方法 */
const handleCreateWarehouse = () => {
  visualTreeRef.value?.openCreateWarehouse();
};

/** 展开全部 */
const handleExpandAll = () => {
  visualTreeRef.value?.expandAll();
};

/** 收起全部 */
const handleCollapseAll = () => {
  visualTreeRef.value?.collapseAll();
};

onMounted(async () => {
  visualName.value = (route.query.name as string) || "";
});
</script>

<style scoped lang="scss">
.dwm-detail {
  width: 100%;
  height: 100%;

  .dwm-detail__header {
    border-bottom: 1px solid rgba(5, 5, 5, 0.06);
    padding-bottom: 10px;
    margin-bottom: 10px;

    .header-left {
      flex-wrap: wrap;
      gap: 8px;

      .header-title {
        font-size: 15px;
        font-weight: 600;
        color: #1f2937;
        flex-shrink: 0;
      }

      .header-stats {
        display: flex;
        align-items: center;
        gap: 6px;

        .stat-tag {
          font-size: 11px;
        }

        :deep(.stat-tag--purple) {
          background-color: #f5f3ff;
          border-color: #e9d5ff;
          color: #6d28d9;
        }
      }
    }

    .header-actions {
      .icon-expand-all::before {
        content: "⊞";
        margin-right: 4px;
        font-size: 13px;
      }

      .icon-collapse-all::before {
        content: "⊟";
        margin-right: 4px;
        font-size: 13px;
      }
    }
  }

  .dwm-detail__content {
    height: calc(100% - 62px);
    width: 100%;
    position: relative;
  }
}
</style>
