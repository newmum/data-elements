<!--
表格组件：分布的几种形态
1：flex-[0_0_200px] 固定表格高度200，即不伸长 不缩短
2：flex-[1_1_200px] 表格在flex容器中自适应伸长剩余空间，最短为200
3：flex-[0_1_auto] 表格高度与内容高度一致
-->
<template>
  <div class="flex flex-col flex-1">
    <!-- 工具栏 -->
    <div v-if="$slots['toolbar']" class="mb-4">
      <slot name="toolbar" />
    </div>

    <!--  支持行排序  -->
    <VueDraggable
      v-model="tableData"
      :class="[flexType]"
      class="flex flex-col overflow-y-auto"
      target="tbody"
      :animation="150"
      :disabled="!rowSort"
    >
      <!-- 表格 -->
      <el-table
        ref="elTableRef"
        v-loading="overlayLoading"
        :class="{ 'data-table--initial-loading': effectiveLoading && tableData.length === 0 }"
        element-loading-text="正在更新表格数据"
        element-loading-custom-class="app-data-loading-mask"
        :data="tableData"
        :row-class-name="tableRowClassName"
        v-bind="{
          border,
          stripe,
          height,
          maxHeight,
          rowKey,
          showOverflowTooltip,
          ...$attrs,
        }"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        @row-click="(row) => emit('row-click', row)"
      >
        <template #empty>
          <slot v-if="effectiveLoading" name="loading">
            <LoadingState type="table" :title="loadingText" compact />
          </slot>
          <slot v-else name="empty">
            <Empty :type="emptyType" :title="emptyTitle" :description="emptyDescription" compact />
          </slot>
        </template>
        <!-- 多选列 -->
        <el-table-column
          v-if="showSelection"
          type="selection"
          width="40"
          :selectable="props.selectable"
        />

        <!-- 序号列 -->
        <el-table-column
          v-if="showIndex"
          label="序号"
          :width="indexWidth"
          type="index"
          :fixed="indexFixed"
        >
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>

        <!-- 自定义业务列位于选择列和序号列之后 -->
        <slot></slot>

        <!-- 数据列 -->
        <template v-for="column in dataColumns" :key="column.prop">
          <template v-if="!column.hidden">
            <!--    操作按钮列，自适应按钮个数       -->
            <OperationColumn
              v-if="Array.isArray(column.buttons)"
              v-bind="{
                prop: column.prop,
                label: column.label || '操作',
                fixed: column.fixed || 'right',
                listDataLength: tableData.length,
                minWidth: 60,
                width: column.width,
                align: column.align,
                headerAlign: column.headerAlign,
                showOverflowTooltip: false,
              }"
            >
              <template #default="{ row }">
                <div class="flex items-center">
                  <template v-for="(it, itInd) in column.buttons" :key="itInd">
                    <template v-if="it.if != undefined ? it.if(row) : true">
                      <!-- 下拉菜单按钮组 -->
                      <el-dropdown v-if="it.dropdown" :trigger="it.trigger || 'click'">
                        <el-button
                          :type="it.type"
                          :link="it.link"
                          :style="{ marginLeft: column.buttons.length === 1 ? '0' : '12px' }"
                        >
                          <template v-if="it.isLabel">
                            {{ it.label }}
                          </template>
                          <template v-else>
                            <Icon icon="el-icon-MoreFilled" size="12" />
                          </template>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <template
                              v-for="(dropdownItem, dropInd) in it.dropdownItems"
                              :key="dropInd"
                            >
                              <el-dropdown-item
                                v-if="dropdownItem.if != undefined ? dropdownItem.if(row) : true"
                                :disabled="
                                  isFunction(dropdownItem.disabled)
                                    ? dropdownItem.disabled(row)
                                    : dropdownItem.disabled
                                "
                                :divided="dropdownItem.divider"
                                :style="
                                  dropdownItem.color ? { color: dropdownItem.color } : undefined
                                "
                                @click="dropdownItem.click ? dropdownItem.click(row) : undefined"
                              >
                                <Icon
                                  v-if="dropdownItem.icon"
                                  :icon="dropdownItem.icon"
                                  class="mr-1"
                                  :style="
                                    dropdownItem.color ? { color: dropdownItem.color } : undefined
                                  "
                                />
                                {{
                                  isFunction(dropdownItem.label)
                                    ? dropdownItem.label(row)
                                    : dropdownItem.label
                                }}
                              </el-dropdown-item>
                            </template>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>

                      <template v-else>
                        <!-- 普通按钮 -->
                        <span v-if="!!it.divider" class="button-divider"></span>
                        <el-button
                          :type="it.type"
                          :link="it.link ?? true"
                          :disabled="isFunction(it.disabled) ? it.disabled(row) : it.disabled"
                          :dashed="it.dashed"
                          :href="it.href"
                          :color="it.color"
                          :target="it.target"
                          :text-color="it.textColor"
                          @click="it.click ? it.click(row) : undefined"
                        >
                          <template v-if="it.icon" #icon>
                            <Icon :icon="it.icon" />
                          </template>
                          {{ isFunction(it.label) ? it.label(row) : it.label }}
                        </el-button>
                      </template>
                      <!-- 分隔线 -->
                    </template>
                  </template>
                </div>
              </template>
            </OperationColumn>

            <el-table-column
              v-else
              v-bind="column"
              :formatter="
                column.render
                  ? (a, b, c, d) =>
                      column.render!({ row: a, column: b, cellValue: c, index: d, text: c })
                  : column.formatter
              "
            >
              <!-- 自定义列内容 -->
              <template v-if="$slots[`column-${column.prop}`]" #default="scope">
                <slot
                  :name="`column-${column.prop}`"
                  :text="scope.row[column.prop]"
                  :row="scope.row"
                  :column="column"
                  :index="scope.$index"
                ></slot>
              </template>

              <template
                v-else-if="!column.renderCell && !column.formatter && !column.render"
                #default="{ row }"
              >
                <!-- 根据column.type判断 -->
                <span v-if="column.type === 'num'" class="table-number-cell">
                  {{ formatNumberValue(row[column.prop], column) }}
                </span>

                <el-image
                  v-else-if="column.type === 'image'"
                  style="z-index: 100; width: 100px; height: 100px"
                  :src="row[column.prop]"
                  :preview-src-list="[row[column.prop]]"
                  show-progress
                  fit="cover"
                  preview-teleported
                  hide-on-click-modal
                />

                <el-link
                  v-else-if="column.type === 'link'"
                  type="primary"
                  :underline="false"
                  @click.prevent="column.onClick && column.onClick(row)"
                >
                  <dict-label v-bind="column.props" :model-value="row[column.prop]" />
                </el-link>
                <dict-label
                  v-else-if="column.options"
                  v-bind="column.props"
                  :options="column.options"
                  :model-value="row[column.prop]"
                />
                <dict-label
                  v-else
                  v-auto-width
                  v-bind="column.props"
                  :model-value="row[column.prop]"
                />
              </template>

              <!-- 表头筛选 -->
              <template #header>
                <span class="title" :title="column.label">{{ column.label }}</span>
                <el-popover
                  v-if="column.filterable"
                  placement="bottom"
                  :width="column.filterPopoverWidth || 200"
                  trigger="click"
                >
                  <template #reference>
                    <el-icon :class="{ active: !!columnFilters[column.prop] }">
                      <Icon v-if="column.filterOptions" icon="filter" />
                      <Icon v-else icon="el-icon-search" />
                    </el-icon>
                  </template>
                  <div class="column-filter">
                    <el-input
                      v-if="!column.filterOptions"
                      v-model="columnFilters[column.prop]"
                      :placeholder="`筛选${column.label}`"
                      clearable
                      suffix-icon="Search"
                      @input="handleColumnFilter()"
                    />
                    <dict-select
                      v-else
                      v-model="columnFilters[column.prop]"
                      :options="unref(column.filterOptions)"
                      :placeholder="`筛选${column.label}`"
                      clearable
                      @change="handleColumnFilter()"
                    ></dict-select>
                  </div>
                </el-popover>
              </template>
            </el-table-column>
          </template>
        </template>

        <!-- 操作插槽 #operation -->
        <OperationColumn
          v-if="$slots.operation"
          label="操作"
          :width="operationWidth"
          :align="operationAlign"
          fixed="right"
          :list-data-length="tableData.length"
          :show-overflow-tooltip="false"
        >
          <template #default="{ row, $index }">
            <slot name="operation" :row="row" :index="$index"></slot>
          </template>
        </OperationColumn>

        <!-- 兼容表格插槽 -->
        <template v-for="name in tableSlotNames" :key="name" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps"></slot>
        </template>
      </el-table>
    </VueDraggable>

    <!-- 分页区域 -->
    <div v-if="shouldShowPagination" class="flex-[0_0_auto] w-full">
      <slot name="pagination">
        <Pagination
          v-model:page="currentPage"
          v-model:limit="pageSize"
          :total="total"
          :page-sizes="pageSizes"
          :show-total="showPaginationTotal"
          @pagination="handlePagination"
        />
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getTextWidth } from "@/utils";
import { TableInstance } from "element-plus";
import { VueDraggable } from "vue-draggable-plus";
import type { TableColumnCtx } from "element-plus";
import { computed, ref, shallowRef, useSlots, watch, unref } from "vue";
import { cloneDeep, isFunction } from "lodash-es";

// 按钮项属性
export interface ButtonItem {
  label: string | ((row: any) => string);
  type?: "" | "text" | "default" | "primary" | "success" | "warning" | "info" | "danger";
  link?: boolean;
  icon?: string;
  color?: string;
  textColor?: string;
  dashed?: boolean;
  href?: string;
  target?: string;
  disabled?: boolean | ((row: any) => boolean);
  if?: (row: any) => boolean;
  divider?: boolean;
  on?: any;
  dropdown?: boolean;
  dropdownItems?: DropdownButtonItem[];
  click?: (row: any) => void;
}

// 下拉菜单项属性
export interface DropdownButtonItem {
  label: string | ((row: any) => string);
  icon?: string;
  color?: string;
  disabled?: boolean | ((row: any) => boolean);
  if?: (row: any) => boolean;
  divider?: boolean;
  click: (row: any) => void;
}

// 列属性
export interface UColumnProps {
  type?: "num" | "image" | "link" | "date" | "buttons" | string; // 字段类型
  props?: object; // 列属性
  prop: string; // 字段名
  label: string; // 列标题
  width?: number; // 列宽
  minWidth?: number; // 最小列宽
  maxWidth?: number; // 最大列宽
  align?: "left" | "center" | "right"; // 对齐方式
  headerAlign?: "left" | "center" | "right"; // 表头对齐方式
  precision?: number; // 数值列固定小数位；未设置时按整数展示
  useGrouping?: boolean; // 数值列是否使用千分位，默认使用
  sortable?: boolean | "custom"; // 是否支持排序
  fixed?: "left" | "right"; // 固定列
  showOverflowTooltip?: boolean; // 当内容过长被隐藏时显示为 tooltip
  slotName?: string; // 自定义列模板的插槽名
  formatter?: (
    row: any,
    column: TableColumnCtx<any>,
    cellValue: any,
    index: number
  ) => string | VNode; // 自定义列模板
  filterable?: boolean; // 是否开启过滤选项
  filterPopoverWidth?: string | number; // 过滤下拉列表的宽度
  filterOptions?: any[]; // 自定义过滤选项
  options?: any; // 自定义列选项
  autoWidth?: boolean; // 是否动态设置列宽
  hidden?: boolean; // 隐藏列
  onClick?: (arg: any) => void; // link的点击方法
  buttons?: any[];
  render?: (scope: {
    row: any;
    column: TableColumnCtx;
    cellValue: string;
    text: any;
    index: number;
  }) => string | VNode;
}

// 获取数据参数
interface fetchDataParams {
  pageNo: number; // 当前页码
  pageSize: number; // 每页显示条数
  search?: string; // 搜索内容
}

// 表格属性
interface UTableProps {
  columns: UColumnProps[]; // 列配置
  data: any[] | ((params: fetchDataParams) => Promise<{ list: any[]; total: number }>); // 获取数据的方法
  showSearch?: boolean; // 是否显示搜索
  showReset?: boolean; // 是否显示重置按钮
  showPage?: boolean; // 是否显示分页
  showSelection?: boolean; // 是否显示多选框
  selectable?: (row: any, index: number) => boolean; // 多选框是否可选
  showIndex?: boolean; // 是否显示序号列
  showOverflowTooltip?: boolean; // 当内容过长被隐藏时显示为 tooltip
  indexWidth?: string | number; // 序号列宽度
  indexFixed?: boolean | "left" | "right"; // 序号列固定位置，false 表示不固定
  showOperation?: boolean; // 是否显示操作列
  operationWidth?: number; // 操作列宽度
  operationAlign?: string; // 操作列对齐方式
  border?: boolean; // 是否显示边框
  stripe?: boolean; // 是否显示斑马纹
  height?: string | number; // 表格高度, 当flexType 为flex-[0_1_auto]时生效
  maxHeight?: string | number; // 表格最大高度
  rowKey?: string; // 行数据的Key
  initPageSize?: number; // 初始分页值
  pageSizes?: number[]; // 每页显示个数选择器的选项设置
  paginationLayout?: string; // 分页布局
  showPaginationTotal?: boolean; // 是否显示分页左侧总记录数
  hidePaginationOnSinglePage?: boolean; // 单页数据时是否隐藏分页区域
  immediate?: boolean; // 是否立即加载数据
  rowSort?: boolean; // 是否行排序
  flexType?: string | "flex-[0_0_200px]" | "flex-[1_1_200px]" | "flex-[0_1_auto]"; // 展示形态，确定高度等
  loading?: boolean; // 加载与loading共同控制
  loadingText?: string;
  emptyType?: string;
  emptyTitle?: string;
  emptyDescription?: string;
}

interface UTableEmits {
  (e: "selection-change", selection: any[]): void;
  (
    e: "sort-change",
    sortInfo: { column: TableColumnCtx<any>; prop: string; order: "ascending" | "descending" }
  ): void;
  (e: "filter-change", filters: any): void;
  (e: "row-click", row: any): void;
}

// 定义props
const props = withDefaults(defineProps<UTableProps>(), {
  flexType: "flex-[1_1_200px]",
  showSearch: true,
  showReset: true,
  showPage: true,
  showSelection: false,
  selectable: undefined,
  showIndex: false,
  showOverflowTooltip: true,
  indexWidth: "60",
  indexFixed: "left",
  operationWidth: 180,
  border: false,
  stripe: true,
  height: "100%",
  maxHeight: undefined,
  rowKey: "id",
  initPageSize: 20,
  pageSizes: () => [10, 20, 50, 100],
  paginationLayout: "total, sizes, prev, pager, next, jumper",
  showPaginationTotal: true,
  hidePaginationOnSinglePage: true,
  immediate: true,
  loading: false,
  loadingText: "正在加载表格数据",
  emptyType: "table",
  emptyTitle: undefined,
  emptyDescription: undefined,
});

// 定义emit事件
const emit = defineEmits<UTableEmits>();
const slots = useSlots();
const tableSlotNames = computed(() =>
  Object.keys(slots).filter(
    (name) => !["default", "toolbar", "pagination", "operation", "empty"].includes(name)
  )
);

// 响应式数据
const elTableRef = shallowRef<TableInstance>();
const localLoading = ref(false);
const tableData = ref<any[]>([]);
const effectiveLoading = computed(() => localLoading.value || props.loading);
const overlayLoading = computed(() => effectiveLoading.value && tableData.value.length > 0);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(props.initPageSize);
const shouldShowPagination = computed(
  () => props.showPage && (!props.hidePaginationOnSinglePage || total.value > pageSize.value)
);
// 筛选过滤
const columnFilters = ref<Record<string, any>>({});
const curIndex = ref(-1); // 当前操作的行索引

// 展示数据列
const dataColumns = ref<UColumnProps[]>(props.columns);

/**
 * 数值列统一由列配置决定精度，避免同一列中出现混用小数位、无千分位的展示。
 * 非数值或空值不强行转换，以免将业务状态文本误当成数字。
 */
const formatNumberValue = (value: unknown, column: UColumnProps) => {
  if (value === null || value === undefined || value === "") return "--";
  const normalized = typeof value === "string" ? value.replace(/,/g, "").trim() : value;
  if (typeof normalized === "string" && !/^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(normalized)) {
    return normalized;
  }
  const numericValue = Number(normalized);
  if (!Number.isFinite(numericValue)) return String(value);

  const precision =
    Number.isInteger(column.precision) && (column.precision as number) >= 0
      ? (column.precision as number)
      : 0;
  return new Intl.NumberFormat("zh-CN", {
    useGrouping: column.useGrouping !== false,
    minimumFractionDigits: precision,
    maximumFractionDigits: precision,
  }).format(numericValue);
};

const freshColumns = () => {
  dataColumns.value = props.columns?.map((col) => {
    const column = cloneDeep(col);
    // 默认内容溢出时显示省略提示；如果页面列配置显式关闭，则尊重页面配置。
    column["showOverflowTooltip"] = col.showOverflowTooltip ?? true;

    const { type } = column;
    if (Array.isArray(column.buttons) || type === "buttons") {
      column.align = column.align || "right";
      column.headerAlign = column.headerAlign || "center";
    } else if (type === "num") {
      column.align = column.align || "right";
      column.headerAlign = column.headerAlign || "right";
      column.precision = column.precision ?? 0;
    } else {
      column.align = column.align || "left";
      column.headerAlign = column.headerAlign || column.align;
    }
    if (type === "date") {
      column.width = 180;
    }
    // 未设置任何宽度则自动计算
    if (!column.width && !column.maxWidth) {
      // 遍历tableData，获取最大宽度
      let maxWidth = 0;
      for (let i = 0; i < tableData.value.length && i < 20; i++) {
        const item = tableData.value[i];
        const width = getTextWidth(item[column.prop]);
        if (width > maxWidth) {
          maxWidth = width;
        }
      }
      maxWidth += 50; // padding 24 再加3个确保不会出现省略号
      column.minWidth = maxWidth < 300 ? maxWidth : 300;
      if (maxWidth < 120) {
        column.minWidth = 120;
      }
    }

    column.minWidth = column.minWidth || 120;

    // 设置了最大宽度 和 最小宽度
    if (column.maxWidth) {
      // 遍历tableData，获取最大宽度
      let maxWidth = 0;
      const minWidth = column.minWidth ? column.minWidth : 120;
      for (let i = 0; i < tableData.value.length && i < 20; i++) {
        const item = tableData.value[i];
        const width = getTextWidth(item[column.prop]);
        if (width > maxWidth) {
          maxWidth = width;
        }
      }
      maxWidth += 24; // padding 24 再加3个确保不会出现省略号

      if (maxWidth > minWidth && maxWidth < column.maxWidth) {
        /* empty */
      } else if (maxWidth < minWidth) {
        /* empty */
      } else if (maxWidth > column.maxWidth) {
        column.width = column.maxWidth;
      }
    }

    // // 设置了最大宽度 和 最小宽度
    // if (column.maxWidth && !column.minWidth) {
    //   // 遍历tableData，获取最大宽度
    //   let maxWidth = 0;
    //   for (let i = 0; i < tableData.value.length && i < 20; i++) {
    //     const item = tableData.value[i];
    //     const width = getTextWidth(item[column.prop]);
    //     if (width > maxWidth) {
    //       maxWidth = width;
    //     }
    //   }
    //   maxWidth += 50; // padding 24 再加3个确保不会出现省略号
    //
    //   if (maxWidth < column.maxWidth) {
    //     /* empty */
    //     column.minWidth = maxWidth;
    //   } else if (maxWidth < column.minWidth) {
    //     /* empty */
    //   } else if (maxWidth > column.maxWidth) {
    //     column.width = column.maxWidth;
    //     column["showOverflowTooltip"] = true;
    //   }
    // }

    return {
      ...column,
    };
  });
};

// 自适应宽度指令
const vAutoWidth = {
  mounted() {
    // 初次挂载的时候计算一次
    // freshColumns();
  },
  updated() {
    // 数据更新时重新计算一次
    // freshColumns();
  },
};
// 行样式
const tableRowClassName = ({ rowIndex }: { rowIndex: number }) => {
  return rowIndex === curIndex.value ? "current-row" : "";
};

// 获取表格数据
// Several refresh triggers can overlap during page and tab initialization.
// A stale response must not overwrite the result of a newer request.
let latestFetchRequestId = 0;

const fetchTableData = async () => {
  const requestId = ++latestFetchRequestId;
  const fetchData = props.data;
  try {
    localLoading.value = true;
    if (Array.isArray(fetchData)) {
      // 分页
      if (props.showPage) {
        tableData.value = fetchData.slice(
          (currentPage.value - 1) * pageSize.value,
          currentPage.value * pageSize.value
        );
      } else {
        tableData.value = fetchData;
      }
      total.value = fetchData.length;
    } else if (typeof fetchData === "function") {
      const params = {
        pageNo: currentPage.value,
        pageSize: pageSize.value,
      };
      const { list, total: totalCount } = await fetchData(params);

      // 强制创建新的数组，避免引用问题
      const newList = Array.isArray(list) ? [...list] : [];

      /*
      // 先清空
      tableData.value = [];
      await nextTick(); // 等待清空完成*/

      if (requestId !== latestFetchRequestId) return;

      tableData.value = newList;
      total.value = Number(totalCount) || 0;
    }

    // 刷新列配置
    if (requestId !== latestFetchRequestId) return;

    freshColumns();
  } catch (error) {
    console.error("获取表格数据失败:", error);
  } finally {
    if (requestId === latestFetchRequestId) {
      localLoading.value = false;
    }
  }
};

const handlePagination = (params: { page: number; limit: number }) => {
  currentPage.value = params.page;
  pageSize.value = params.limit;
  fetchTableData();
};

// 多选变化
const handleSelectionChange = (val: any[]) => {
  emit("selection-change", val);
};

// 排序变化
const handleSortChange = ({
  column,
  prop,
  order,
}: {
  column: any;
  prop: string;
  order: "ascending" | "descending";
}) => {
  emit("sort-change", { column, prop, order });
};

// 刷新表格数据, isInit 是否初始化分页
const refresh = (isInit = false) => {
  if (isInit) {
    currentPage.value = 1;
  }
  fetchTableData();
};

// 筛选变化
const handleColumnFilter = () => {
  emit("filter-change", { ...columnFilters.value });
};

const setCurIndex = (index: number = -1) => {
  curIndex.value = index;
};

// 监听props.immediate
watch(
  () => props.immediate,
  (val) => {
    if (val) {
      fetchTableData();
    }
  },
  { immediate: true }
);

watch(
  () => props.data,
  (val) => {
    if (val) {
      fetchTableData();
    }
  }
);

watch(
  () => props.columns,
  (val) => {
    if (val) {
      freshColumns();
    }
  }
);

// 暴露方法给父组件
defineExpose({
  $table: elTableRef, // 引用
  tableData,
  refresh,
  setCurIndex, // 设置当前操作的行索引
  curIndex, // 当前操作的行索引
  pageSize, // 当前页大小
  total, // 总条数
});
</script>

<style scoped lang="scss">
/*表头样式*/
:deep(.el-table__header-wrapper .el-table__header .el-table__cell) {
  .cell {
    display: flex;
    gap: 8px;
    align-items: center;
    text-align: left;
    & > span.title {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .el-tooltip__trigger {
      color: #909399;
      cursor: pointer;

      &:hover {
        color: var(--el-color-primary);
      }

      &.active {
        color: var(--el-color-primary) !important;
      }
    }
  }

  /*防止失效*/
  &.is-left .cell {
    justify-content: flex-start;
    text-align: left;
  }
  &.is-center .cell {
    justify-content: center;
    text-align: center;
  }
  &.is-right .cell {
    justify-content: right;
    text-align: right;
  }
}

:deep(.table-number-cell) {
  display: block;
  width: 100%;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
:deep(.operation-buttons) {
  display: flex;
  align-items: center;
  .button-divider {
    display: inline-block;
    width: 1px;
    height: 16px;
    margin: 0 8px;
    background-color: #e4e7ed;
  }
}

.pagination {
  flex-shrink: 0;
  width: 100%;
  margin-top: 4px;
}

/* Initial skeletons start below the header instead of floating in the table center. */
:deep(.data-table--initial-loading .el-table__empty-block) {
  align-items: flex-start;
}

:deep(.data-table--initial-loading .el-table__empty-text) {
  align-self: flex-start;
  width: 100%;
}

/*使link文字在ell下显示省略号*/
:deep(.el-link) {
  .el-link__inner {
    display: inline !important;
    .el-icon {
      top: 2px;
    }
  }
  display: inline !important;
}
</style>
