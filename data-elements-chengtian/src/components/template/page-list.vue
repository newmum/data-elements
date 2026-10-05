<template>
  <div v-loading="loading" class="pl-container">
    <div v-if="$slots['toolbar'] || $slots['toolbar-extra']" class="pl-toolbar">
      <slot name="toolbar" />
    </div>

    <div class="pl-list">
      <div
        v-for="(row, index) in list"
        :key="row[rowKey] || index"
        :class="['pl-row', { 'pl-row--no-divider': !showDivider }, 'animated', 'fadeInLeft']"
        :style="{ animationDelay: `${index * 60}ms` }"
        @click="handleItemClick(row)"
      >
        <!-- 头部区域 -->
        <div class="pl-header">
          <div v-if="selectable" class="pl-select" @click.stop>
            <el-checkbox
              :model-value="isSelected(row)"
              @change="(val: boolean) => onSelectChange(row, val)"
            />
          </div>
          <div class="pl-icon">
            <slot name="icon" :row="row" :index="index">
              <div :class="`i-svg:menu`" />
            </slot>
          </div>

          <div class="pl-title" @click.stop="handleTitleClick(row)">
            <slot name="title" :row="row" :index="index">
              {{ row.title }}
            </slot>
          </div>

          <div class="pl-status">
            <slot name="status" :row="row" :index="index">
              <el-tag v-if="row.status" round class="text-sm border-0">
                {{ row.status }}
              </el-tag>
            </slot>
          </div>

          <!-- 操作按钮 -->
          <div class="pl-actions">
            <slot name="actions" :row="row" :index="index"></slot>
          </div>
        </div>

        <!-- 详情区域 -->
        <div class="pl-columns">
          <slot name="columns">
            <template v-for="col in columns" :key="col.title">
              <div class="pl-field-col">
                <span class="pl-field-label">{{ col.title }}:</span>
                <el-text truncated class="pl-field-value">
                  <slot :name="'column-' + col.field" v-bind="{ row }">
                    <dict-label
                      :model-value="get(row, col.field)"
                      v-bind="{
                        ...col,
                      }"
                      :tag="false"
                    />
                  </slot>
                </el-text>
              </div>
            </template>
          </slot>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!list?.length" class="pl-empty">
      <slot name="empty">
        <Empty type="list" :description="emptyText" />
      </slot>
    </div>

    <slot name="pagination">
      <Pagination
        v-show="!!total && !hiddenOnePage"
        :total="total"
        :page="pageNo ?? 0"
        :limit="pageSize ?? 20"
        class="pl-pagination"
        @pagination="handlePageChange"
      />
    </slot>
  </div>
</template>

<script lang="ts" setup>
import { get } from "lodash-es";

// 列表项数据结构
export interface ListItem {
  id?: string | number;
  title: string;
  status?: string;
  [key: string]: any; // 允许其他自定义字段
}

// 属性字段类型
export interface DisplayField {
  title: string;
  field: string;
  suffix?: string;
  emptyText?: string;
}

// 组件Props类型
export interface PageListProps {
  /** 行key */
  rowKey?: string;
  /** 列表数据 */
  list?: ListItem[];
  /** 是否显示加载状态 */
  loading?: boolean;
  /** 是否可点击 */
  clickable?: boolean;
  /** 是否显示分割线 */
  showDivider?: boolean;
  /** 空状态文本 */
  emptyText?: string;
  /** 自定义空状态组件 */
  emptyComponent?: any;
  /** 属性字段配置 */
  columns?: DisplayField[];
  pageNo?: number;
  pageSize?: number;
  total?: number;
  /** 只有一页时隐藏分页 */
  hiddenOnePage?: boolean;
  /** 是否开启行选择复选框 */
  selectable?: boolean;
}

// 定义组件属性
const props = withDefaults(defineProps<PageListProps>(), {
  rowKey: "tid",
  list: () => [],
  pageNo: 1,
  pageSize: 20,
  total: 0,
  loading: false,
  showDivider: true,
  clickable: true,
  emptyText: "暂无数据",
  columns: () => [
    { title: "来源部门", field: "orgaName" },
    { title: "目录类型", field: "catalogType" },
    { title: "注册时间", field: "registerTime" },
    { title: "来源表名", field: "sourceName" },
    { title: "数据条数", field: "rowTotal", suffix: "条", emptyText: "0条" },
    { title: "字段数", field: "itemNum", suffix: "个", emptyText: "0个" },
  ],
});

const selectIds = defineModel("modelValue", {
  type: Array,
  required: false,
  default: [],
});

// 定义事件
const emit = defineEmits<{
  /** 行数据点击事件 */
  (e: "row-click", item: ListItem): void;
  /** 标题点击事件 */
  (e: "title-click", item: ListItem): void;
  /** 分页变化事件 */
  (e: "page-change", page: { pageNo: number; pageSize: number }): void;
}>();

// 处理项目点击
const handleItemClick = (row: ListItem): void => {
  if (props.clickable) {
    emit("row-click", row);
  }
};

// 处理标题点击
const handleTitleClick = (row: ListItem): void => {
  if (props.clickable) {
    emit("title-click", row);
  }
};

// 处理操作按钮点击
const handlePageChange = (param: any): void => {
  const { page, limit } = param;
  emit("page-change", { pageNo: page, pageSize: limit });
};
// 选择相关
const isSelected = (row: ListItem): boolean => {
  const id = row[props.rowKey] as any;
  return Array.isArray(selectIds.value) ? selectIds.value.includes(id) : false;
};

const onSelectChange = (row: ListItem, checked: boolean) => {
  const id = row[props.rowKey] as any;
  const has = selectIds.value.includes(id);
  if (checked && !has) selectIds.value.push(id);
  else if (!checked && has) selectIds.value = selectIds.value.filter((x) => x !== id);
};
</script>

<style lang="scss" scoped>
.pl-container {
  --title-color: #454e72;
  --desc-color: #636981;
  --pm: 20px;
  --ps: 16px;

  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding-top: 20px;
  font-size: 14px;
  line-height: 20px;
}

.pl-toolbar {
  position: relative;
  z-index: 1;
  margin: 0 20px;
}

.pl-list {
  padding-top: 16px;

  .pl-row {
    padding: 18px 20px;
    border-bottom: 1px solid #f0f0f0;
    transition: all 0.3s ease;

    &--no-divider {
      border-bottom: none;
    }

    &:hover {
      box-shadow: 0 -4px 18px rgb(64 110 145 / 11%);
    }
  }

  .pl-select {
    display: flex;
    align-items: center;
    margin-right: 12px;
  }

  .pl-header {
    display: flex;
    align-items: center;
    margin-bottom: 24px;

    .pl-icon {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      justify-content: center;
      margin-right: 10px;

      :deep(.iconsvg) {
        color: var(--el-color-primary);
      }
    }

    .pl-title {
      max-width: 400px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 14px;
      font-weight: 600;
      color: var(--title-color);
      white-space: nowrap;
      cursor: pointer;
      transition: color 0.3s ease;

      &:hover {
        color: var(--el-color-primary);
      }
    }

    .pl-status {
      flex-grow: 1;
      flex-shrink: 0;
      margin-left: 20px;
    }

    .pl-actions {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      margin-left: 16px;

      .pl-btn {
        height: auto;
        padding: 0;
      }
    }
  }

  .pl-columns {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px 8px;
    margin-left: 28px;

    .pl-field-col {
      display: flex;
      flex: 1;
      align-items: center;
      min-width: 0;
      color: #686c80;
      .pl-field-label {
        flex-shrink: 0;
        margin-right: 8px;
      }
      .pl-field-value {
        color: #686c80;
      }
    }
  }
}

@keyframes fadeInLeft {
  0% {
    opacity: 0;
    transform: translateX(-20px);
  }
  100% {
    opacity: 1;
    transform: translateX(0);
  }
}
.fadeInLeft {
  animation-name: fadeInLeft;
}
.animated {
  animation-duration: 1s;
  animation-fill-mode: both;
}

.pl-loading {
  padding: 40px 20px;
  text-align: center;
}

.pl-empty {
  padding: 40px 20px;
  text-align: center;
}

.pl-pagination {
  margin: 16px 20px;
  :deep(.pagination) {
    padding: 0;
  }
}
</style>
