<template>
  <el-select
    ref="selectRef"
    v-model="selectedValue"
    :placeholder="placeholder"
    :filterable="filterable"
    :clearable="clearable"
    :disabled="disabled"
    :remote="remote"
    :remote-method="remoteMethod"
    :style="style"
    :popper-class="popperClass"
    v-bind="$attrs"
    @change="handleChange"
    @visible-change="handleVisibleChange"
  >
    <el-option
      v-for="option in mergedOptions"
      :key="option[valueKey]"
      :label="option[labelKey]"
      :value="option[valueKey]"
    >
      <slot name="option" :option="option">
        <span :title="option[labelKey]" class="flex-y-center">
          <Icon v-if="option.icon" :icon="option.icon" class="mr-2" />
          {{ option[labelKey] }}
        </span>
      </slot>
    </el-option>
    <template #footer>
      <div
        v-if="loading && mergedOptions.length > 0"
        class="select-footer-loading flex items-center justify-center py-2"
      >
        <Icon icon="loading" class="mr-2 animate-spin" />
        <span class="text-xs text-gray-400">加载中...</span>
      </div>
      <div v-else-if="!hasMore && mergedOptions.length > 0" class="select-footer-divider py-1 px-4">
        <span class="select-footer-divider-text">已加载全部</span>
      </div>
    </template>
    <template #label="{ label, value, ...scoped }">
      <slot name="label" :label="label" :value="value" v-bind="scoped">
        <div class="flex-y-center w-full">
          <Icon
            v-if="mergedOptions.find((el) => el[valueKey] === value)?.icon"
            class="mr-2"
            :icon="mergedOptions.find((el) => el[valueKey] === value)?.icon"
          />
          {{ label }}
        </div>
      </slot>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from "vue";

interface PaginationResponse {
  list: any[];
  total: number;
}

interface FetchOptions {
  pageNum?: number;
  pageSize?: number;
  [key: string]: any;
}

const props = defineProps({
  modelValue: {
    type: [String, Number, Array],
    default: undefined,
  },
  // 数据加载函数，返回 Promise<{ list: any[], total: number }>
  fetchFn: {
    type: Function as PropType<(options: FetchOptions) => Promise<PaginationResponse>>,
    required: true,
  },
  // 初始查询参数
  queryParams: {
    type: Object,
    default: () => ({}),
  },
  // 选项的 label 字段名
  labelKey: {
    type: String,
    default: "label",
  },
  // 选项的 value 字段名
  valueKey: {
    type: String,
    default: "value",
  },
  // 是否开启远程搜索
  remote: {
    type: Boolean,
    default: false,
  },
  // 是否可过滤
  filterable: {
    type: Boolean,
    default: false,
  },
  // 是否可清空
  clearable: {
    type: Boolean,
    default: true,
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false,
  },
  placeholder: {
    type: String,
    default: "请选择",
  },
  style: {
    type: Object,
    default: () => ({}),
  },
  // 分页大小
  pageSize: {
    type: Number,
    default: 15,
  },
  // 触发加载的阈值（距离底部多少像素）
  threshold: {
    type: Number,
    default: 20,
  },
  // 是否立即加载
  immediate: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["update:modelValue", "change", "load-success", "load-error"]);

const selectRef = ref();
const selectedValue = ref(props.modelValue);
const options = ref<any[]>([]);
const loading = ref(false);
const hasMore = ref(true);
const currentPage = ref(1);
const totalCount = ref(0);
const isDropdownOpen = ref(false);
const isInitialized = ref(false);
const popperClass = `pagination-select-popper-${Math.random().toString(36).slice(2, 8)}`;

// 合并后的选项（当开启远程搜索时，options会被替换）
const mergedOptions = computed(() => options.value);

// 远程搜索方法
const remoteMethod = async (query: string) => {
  if (!query) {
    currentPage.value = 1;
    options.value = [];
    hasMore.value = true;
    await loadOptions({ keyword: query });
    return;
  }
  // 远程搜索时，重置并重新加载
  currentPage.value = 1;
  options.value = [];
  hasMore.value = true;
  await loadOptions({ keyword: query });
};

// 加载选项
const loadOptions = async (extraParams: Record<string, any> = {}) => {
  if (loading.value || !hasMore.value) return;

  try {
    loading.value = true;

    const params = {
      pageNum: currentPage.value,
      pageSize: props.pageSize,
      ...props.queryParams,
      ...extraParams,
    };

    const response = await props.fetchFn(params);

    const list = response?.list || [];
    const total = response?.total || 0;

    totalCount.value = total;

    if (currentPage.value === 1) {
      // 第一页，直接替换
      options.value = list;
    } else {
      // 后续页，追加数据
      options.value = [...options.value, ...list];
    }

    // 判断是否还有更多数据
    hasMore.value = options.value.length < total;

    emit("load-success", {
      list: options.value,
      total,
      currentPage: currentPage.value,
    });
  } catch (error) {
    console.error("加载选项失败:", error);
    emit("load-error", error);
  } finally {
    loading.value = false;
  }
};

// 处理下拉框显示/隐藏
const handleVisibleChange = (visible: boolean) => {
  isDropdownOpen.value = visible;

  if (visible) {
    // 等待下拉面板渲染完成后，绑定滚动事件
    nextTick(() => {
      // 直接在body中查找下拉框元素，因为el-select的下拉框默认添加到body末尾
      const dropdown = document.querySelector(`.${popperClass}`);
      const scrollContainer = dropdown?.querySelector(".el-select-dropdown__wrap");
      if (scrollContainer) {
        scrollContainer.addEventListener("scroll", handleScroll);
      }
    });
  } else {
    // 移除滚动事件监听
    nextTick(() => {
      // 直接在body中查找下拉框元素
      const dropdown = document.querySelector(`.${popperClass}`);
      const scrollContainer = dropdown?.querySelector(".el-select-dropdown__wrap");
      if (scrollContainer) {
        scrollContainer.removeEventListener("scroll", handleScroll);
      }
    });
  }

  if (visible && props.immediate && !isInitialized.value) {
    // 首次打开时初始化
    isInitialized.value = true;
    currentPage.value = 1;
    options.value = [];
    hasMore.value = true;
    loadOptions();
  } else if (visible && options.value.length === 0) {
    // 打开且无数据时加载
    currentPage.value = 1;
    hasMore.value = true;
    loadOptions();
  }
};

// 处理滚动事件
const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement;
  if (!target || !isDropdownOpen.value) return;

  // 计算是否滚动到底部
  const { scrollTop, scrollHeight, clientHeight } = target;
  const distanceToBottom = scrollHeight - scrollTop - clientHeight;

  if (distanceToBottom <= props.threshold && hasMore.value && !loading.value) {
    // 加载下一页
    currentPage.value++;
    loadOptions();
  }
};

// 处理选项变化
const handleChange = (value: any) => {
  emit("update:modelValue", value);
  emit("change", value);
};

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (val) => {
    selectedValue.value = val;
  }
);

// 组件卸载时移除事件监听
onUnmounted(() => {
  const dropdown = document.querySelector(`.${popperClass}`);
  const scrollContainer = dropdown?.querySelector(".el-select-dropdown__wrap");
  if (scrollContainer) {
    scrollContainer.removeEventListener("scroll", handleScroll);
  }
});

// 暴露方法
defineExpose({
  reload: async () => {
    currentPage.value = 1;
    options.value = [];
    hasMore.value = true;
    await loadOptions();
  },
  reset: async () => {
    currentPage.value = 1;
    options.value = [];
    hasMore.value = true;
    selectedValue.value = undefined;
    emit("update:modelValue", undefined);
    await loadOptions();
  },
});
</script>

<style scoped lang="scss">
.select-footer-loading {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.select-footer-divider {
  display: flex;
  gap: 12px;
  align-items: center;

  &::before,
  &::after {
    flex: 1;
    height: 1px;
    content: "";
    background: linear-gradient(
      to right,
      transparent,
      var(--el-border-color-lighter) 30%,
      var(--el-border-color-lighter) 70%,
      transparent
    );
  }
}

.select-footer-divider-text {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  letter-spacing: 0.5px;
  white-space: nowrap;
}
</style>

<!-- 非 scoped 样式：覆盖 Element Plus footer 容器默认边框，避免空内容时也显示横线 -->
<style lang="scss">
[class*="pagination-select-popper-"] {
  .el-select-dropdown__footer {
    padding: 0;
    border-top: none;
  }
}
</style>
