<template>
  <div :class="{ hidden: hidden }" class="pagination">
    <span v-if="showTotal">
      当前第 {{ currentPage }} 页，共
      <span class="total">{{ total }}</span>
      条记录
    </span>
    <span v-else></span>
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      size="small"
      :background="background"
      :layout="layout"
      :page-sizes="pageSizes"
      :total="numericTotal"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup lang="ts">
const props = defineProps({
  total: {
    type: Number as PropType<number>,
    default: 0,
  },
  pageSizes: {
    type: Array as PropType<number[]>,
    default() {
      return [10, 20, 50, 100];
    },
  },
  layout: {
    type: String,
    default: " prev, pager, next, sizes, jumper",
  },
  background: {
    type: Boolean,
    default: false,
  },
  autoScroll: {
    type: Boolean,
    default: true,
  },
  hidden: {
    type: Boolean,
    default: false,
  },
  showTotal: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["pagination"]);

// 防御性转换：el-pagination 的 total 必须为 Number，但 API 可能返回字符串
const numericTotal = computed(() => Number(props.total) || 0);

const currentPage = defineModel("page", {
  type: Number,
  required: true,
  default: 1,
});

const pageSize = defineModel("limit", {
  type: Number,
  required: true,
  default: 20,
});

watch(
  () => props.total,
  (newVal: number) => {
    const lastPage = Math.ceil(newVal / pageSize.value);
    if (newVal > 0 && currentPage.value > lastPage) {
      currentPage.value = lastPage;
      emit("pagination", { page: currentPage.value, limit: pageSize.value });
    }
  }
);

function handleSizeChange(val: number) {
  currentPage.value = 1;
  emit("pagination", { page: currentPage.value, limit: val });
}

function handleCurrentChange(val: number) {
  emit("pagination", { page: val, limit: pageSize.value });
}
</script>

<style lang="scss" scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px 12px;
  min-width: 0;
  padding: 4px 0 0;
  font-size: var(--el-font-size-base);

  .total {
    color: var(--el-color-primary);
  }

  &.hidden {
    display: none;
  }

  :deep(.el-pagination) {
    min-width: 0;
    flex-wrap: wrap;
    justify-content: flex-end;
    --el-pagination-font-size-small: var(--el-font-size-base);
    --el-border-radius-base: 4px;
    .el-pager {
      li.is-active {
        border: 1px solid var(--el-color-primary);
        border-radius: var(--el-border-radius-base);
      }
      .number {
        line-height: var(--el-font-size-base);
      }
    }

    .el-select {
      width: 100px;
    }
  }
}
</style>
