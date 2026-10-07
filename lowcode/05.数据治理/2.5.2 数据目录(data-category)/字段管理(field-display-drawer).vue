<template>
  <el-drawer
    v-model="visible"
    title="字段显示设置"
    :size="600"
    class="field-display-drawer"
    @open="handleOpen"
  >
    <template #header>
      <div class="drawer-header">
        <span class="drawer-title">字段显示设置</span>
        <span class="drawer-subtitle">拖拽调整顺序，勾选控制显示</span>
      </div>
    </template>

    <div class="drawer-body">
      <!-- 操作提示 -->
      <div class="tips-bar">
        <Icon class="tips-icon" icon="el-icon-InfoFilled" />
        <span>勾选字段将在左侧树中显示，拖拽标签可调整排列顺序</span>
      </div>

      <!-- 全选 / 反选操作栏 -->
      <div class="action-bar">
        <el-checkbox
          v-model="isAllChecked"
          :indeterminate="isIndeterminate"
          @change="handleCheckAll"
        >
          全选
        </el-checkbox>
        <span class="checked-count">已选 {{ checkedCount }} / {{ sortableItems.length }} 项</span>
      </div>

      <!-- 可拖拽标签列表 -->
      <div ref="dragContainerRef" class="tag-list">
        <div
          v-for="(item, index) in sortableItems"
          :key="item.field || item._id"
          :data-index="index"
          class="tag-item"
          :class="{ 'tag-item--checked': item.fieldShow, 'tag-item--dragging': item._dragging }"
          draggable="true"
          @dragstart="handleDragStart($event, index)"
          @dragover.prevent="handleDragOver($event, index)"
          @dragend="handleDragEnd"
          @drop.prevent="handleDrop($event)"
        >
          <!-- 拖拽手柄 -->
          <Icon class="drag-handle" icon="el-icon-Rank" />

          <!-- 复选框 -->
          <el-checkbox v-model="item.fieldShow" @change="handleItemCheck" @click.stop />

          <!-- 标签内容 -->
          <div class="tag-content">
            <span class="tag-title">{{ item.title }}</span>
            <span v-if="item.field" class="tag-field">{{ item.field }}</span>
          </div>

          <!-- 排序序号标识 -->
          <span class="sort-badge">{{ index + 1 }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="handleReset">重置</el-button>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { ElMessage } from "element-plus";

// ======================== Props & Emits ========================
interface FormItem {
  type: string;
  field?: string;
  title?: string;
  fieldShow?: boolean;
  fieldSortNo?: number;
  display?: boolean;
  props?: Record<string, any>;
  col?: Record<string, any>;
  [key: string]: any;
}

interface CategoryItem {
  field: string;
  title: string;
  label?: string;
  fieldShow?: boolean;
  fieldSortNo?: number;
  props?: Record<string, any>;
  [key: string]: any;
}

interface SortableItem extends CategoryItem {
  _id: string;
  _dragging: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 来自父组件 categoryConfig 的字段列表 */
  categoryConfig: CategoryItem[];
  /** 完整的 formJson（原始表单配置，用于保存时重建） */
  formJson?: FormItem[];
  /** 表单名称，用于调用保存接口 */
  formName?: string;
  /** 任务ID */
  tid?: string;
  /** 环境 */
  env?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
  (e: "saved", items: SortableItem[]): void;
}>();

// ======================== State ========================
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const saving = ref(false);
const sortableItems = ref<SortableItem[]>([]);
const dragSrcIndex = ref<number | null>(null);

// ======================== Computed ========================
const checkedCount = computed(() => sortableItems.value.filter((i) => i.fieldShow).length);

const isAllChecked = computed({
  get: () => sortableItems.value.length > 0 && sortableItems.value.every((i) => i.fieldShow),
  set: () => {},
});

const isIndeterminate = computed(() => {
  const c = checkedCount.value;
  return c > 0 && c < sortableItems.value.length;
});

// ======================== Methods ========================

/** 初始化列表 */
const initItems = () => {
  sortableItems.value = props.categoryConfig
    .filter((item) => item.field && item.title)
    .map((item, idx) => ({
      ...item,
      _id: `${item.field}_${idx}`,
      _dragging: false,
      fieldShow: item.fieldShow !== undefined ? item.fieldShow : true,
      fieldSortNo: item.fieldSortNo !== undefined ? item.fieldSortNo : idx,
    }))
    .sort((a, b) => (a.fieldSortNo ?? 0) - (b.fieldSortNo ?? 0));
};

const handleOpen = () => {
  initItems();
};

/** 全选/取消全选 */
const handleCheckAll = (val: any) => {
  sortableItems.value.forEach((item) => {
    item.fieldShow = val;
  });
};

/** 单个 checkbox 变化时更新全选状态 */
const handleItemCheck = () => {
  // 由 computed 自动更新
};

// ======================== Drag & Drop ========================
const handleDragStart = (e: DragEvent, index: number) => {
  dragSrcIndex.value = index;
  sortableItems.value[index]._dragging = true;
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", String(index));
  }
};

const handleDragOver = (e: DragEvent, index: number) => {
  e.preventDefault();
  if (dragSrcIndex.value === null || dragSrcIndex.value === index) return;

  // 实时交换位置，产生平滑效果
  const items = [...sortableItems.value];
  const [moved] = items.splice(dragSrcIndex.value, 1);
  items.splice(index, 0, moved);
  dragSrcIndex.value = index;
  sortableItems.value = items;
};

const handleDrop = (e: DragEvent) => {
  e.preventDefault();
};

const handleDragEnd = () => {
  if (dragSrcIndex.value !== null) {
    sortableItems.value[dragSrcIndex.value]._dragging = false;
  }
  sortableItems.value.forEach((item) => {
    item._dragging = false;
  });
  dragSrcIndex.value = null;
};

// ======================== Reset ========================
const handleReset = () => {
  initItems();
  ElMessage.info("已重置为初始状态");
};

// ======================== Save ========================
const handleSave = async () => {
  if (!props.formJson || props.formJson.length === 0) {
    ElMessage.warning("表单配置数据为空，无法保存");
    return;
  }

  saving.value = true;
  try {
    // 构建 field -> { fieldShow, fieldSortNo } 映射
    const fieldMap = new Map<string, { fieldShow: boolean; fieldSortNo: number }>();
    sortableItems.value.forEach((item, idx) => {
      if (item.field) {
        fieldMap.set(item.field, { fieldShow: !!item.fieldShow, fieldSortNo: idx + 1 });
      }
    });

    // 基于原始 formJson 重建，写入 fieldShow 和 fieldSortNo
    const newFormJson: FormItem[] = props.formJson.map((item) => {
      if (item.field && fieldMap.has(item.field)) {
        const meta = fieldMap.get(item.field)!;
        return {
          ...item,
          fieldShow: meta.fieldShow,
          fieldSortNo: meta.fieldSortNo,
        };
      }
      return { ...item };
    });

    // 对有 fieldSortNo 的项按排序号整体排序（保留 UTitle 等无字段项的原始位置）
    // 策略：只对有 field 的条目参与排序，无 field 条目保持原始穿插位置
    const serialized = JSON.stringify(newFormJson, null, 2);

    const params = {
      tid: props.tid || "",
      env: props.env || "",
      formName: props.formName || "",
      formJson: serialized,
    };

    await (window as any).$common.post("/sym/form?action=save", params);

    ElMessage.success("保存成功");
    emit("saved", sortableItems.value);
    visible.value = false;
  } catch (err: any) {
    console.error("保存字段显示配置失败", err);
    ElMessage.error(err?.message || "保存失败，请重试");
  } finally {
    saving.value = false;
  }
};

// ======================== Watch ========================
watch(
  () => props.categoryConfig,
  () => {
    if (visible.value) {
      initItems();
    }
  },
  { deep: true }
);
</script>

<style scoped lang="scss">
.field-display-drawer {
  :deep(.el-drawer__header) {
    padding: 16px 20px 12px;
    margin-bottom: 0;
    border-bottom: 1px solid #f0f0f0;
  }

  :deep(.el-drawer__body) {
    padding: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  :deep(.el-drawer__footer) {
    padding: 12px 20px;
    border-top: 1px solid #f0f0f0;
  }
}

.drawer-header {
  display: flex;
  flex-direction: column;

  .drawer-title {
    font-size: 16px;
    font-weight: 600;
    color: #1d2129;
    line-height: 1.4;
  }

  .drawer-subtitle {
    font-size: 12px;
    color: #86909c;
    margin-top: 2px;
  }
}

.drawer-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 16px;
}

.tips-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  margin: 12px 0 8px;
  background: #e8f4ff;
  border-radius: 6px;
  font-size: 12px;
  color: #1890ff;

  .tips-icon {
    flex-shrink: 0;
    font-size: 14px;
  }
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  margin-bottom: 8px;

  .checked-count {
    font-size: 12px;
    color: #86909c;
  }
}

.tag-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
  }
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 6px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fafafa;
  cursor: grab;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    background: #f0f7ff;
    border-color: #91caff;
    box-shadow: 0 2px 6px rgba(24, 144, 255, 0.1);
  }

  &--checked {
    background: #f0f7ff;
    border-color: #bae0ff;
  }

  &--dragging {
    opacity: 0.5;
    background: #e6f4ff;
    border-color: #1890ff;
    border-style: dashed;
    cursor: grabbing;
  }

  .drag-handle {
    color: #bfbfbf;
    font-size: 16px;
    cursor: grab;
    flex-shrink: 0;

    &:hover {
      color: #1890ff;
    }
  }

  :deep(.el-checkbox) {
    flex-shrink: 0;
    margin-right: 0;
    height: auto;

    .el-checkbox__label {
      display: none;
    }
  }

  .tag-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;

    .tag-title {
      font-size: 13px;
      font-weight: 500;
      color: #1d2129;
      line-height: 1.4;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .tag-field {
      font-size: 11px;
      color: #86909c;
      margin-top: 1px;
      font-family: monospace;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .sort-badge {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #f5f5f5;
    border: 1px solid #e8e8e8;
    font-size: 11px;
    color: #86909c;
    display: flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
