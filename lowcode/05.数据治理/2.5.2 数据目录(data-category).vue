<template>
  <div class="data-catalog-content card-container px-5 pt-5 pb-2">
    <el-splitter style="width: 100%; height: 100%">
      <el-splitter-panel size="360px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>目录类目</h3>
            <el-button
              type="primary"
              text
              size="small"
              title="字段设置"
              class="left-header-btn"
              @click="fieldDrawerVisible = true"
            >
              <Icon icon="setting" />
            </el-button>
          </div>

          <div class="tree-container">
            <UTree
              ref="treeRef"
              :data="treeData"
              :search="true"
              :expand="false"
              :highlight-current="true"
              :expand-on-click-node="false"
              :search-placeholder="'请输入类目名称'"
              :default-props="{ children: 'children', label: 'label' }"
              node-key="id"
              class="tree-container-custom"
              @node-click="handleNodeClick"
            >
              <template #node="{ node }">
                <u-tree-node
                  show-icon
                  style="--gap: 0; --node-count-color: #323643"
                  :keyword="treeRef?.searchValue"
                  :label="node.label"
                  :node="node"
                >
                  <template #count>
                    <span v-if="get(countMap, `${node?.data.pKey}.${node.data.value}`, 0) > 0">
                      ({{ get(countMap, `${node?.data.pKey}.${node.data.value}`, 0) }})
                    </span>
                    <span v-else></span>
                  </template>
                </u-tree-node>
              </template>
            </UTree>
          </div>
        </div>
      </el-splitter-panel>

      <el-splitter-panel>
        <div class="right-panel pl-4">
          <DataTable
            ref="tableRef"
            :columns="tableColumns"
            :data="fetchTableData"
            :show-selection="true"
            :show-index="true"
            :immediate="false"
            style="height: 100%"
            @selection-change="handleSelectionChange"
          >
            <template #toolbar>
              <div class="flex justify-between items-center">
                <div>
                  <el-button
                    type="primary"
                    :disabled="selectedRows.length === 0"
                    @click="batchPublish"
                  >
                    批量发布
                  </el-button>
                  <el-button
                    type="primary"
                    plain
                    :disabled="selectedRows.length === 0"
                    @click="batchClassify"
                  >
                    批量分类
                  </el-button>
                </div>
                <div>
                  <DictSelect
                    v-model="publishStatus"
                    placeholder="发布状态"
                    :options="publishStatusOptions"
                    clearable
                    style="width: 150px; margin-right: 10px"
                    @change="refreshTaleData"
                  ></DictSelect>
                  <el-input
                    v-model="assetName"
                    placeholder="请输入目录名称"
                    :suffix-icon="'el-icon-Search'"
                    clearable
                    style="width: 150px"
                    @keyup.enter="refreshTaleData"
                  />
                </div>
              </div>
            </template>
            <template #column-catalogName="{ row }">
              <el-text
                type="primary"
                cursor-pointer
                @click="handleDetailClick({ id: row.id, type: 'catalog', title: row.catalogName })"
              >
                <Icon icon="catalog"></Icon>
                {{ row.catalogName }}
              </el-text>
            </template>
            <template #column-targetTableName="{ row }">
              <el-text
                v-if="row.targetTableId"
                type="primary"
                cursor-pointer
                @click="
                  handleDetailClick({
                    id: row.targetTableId,
                    type: 'table',
                    title: row.targetTableName,
                  })
                "
              >
                <Icon icon="table"></Icon>
                {{ row.targetTableName }}
              </el-text>
              <el-text v-else type="info">-</el-text>
            </template>
            <template #column-category="{ row }">
              <div class="category-info">
                <el-popover
                  :width="400"
                  title="所属类目"
                  :popper-options="{ boundariesElement: 'viewport' }"
                  trigger="click"
                  @show="handleCategoryClick(row)"
                  @hide="
                    categoryData = null;
                    isLoadingCategory = true;
                  "
                >
                  <template #reference>
                    <el-text type="primary" cursor-pointer>类目信息</el-text>
                  </template>
                  <template v-if="isLoadingCategory">
                    <div
                      v-loading="isLoadingCategory"
                      :element-loading-text="'加载中...'"
                      class="loading-container"
                    ></div>
                  </template>
                  <div v-else-if="categoryData" class="category-content">
                    <div v-for="(value, key) in categoryData" :key="key" class="category-item">
                      <span class="category-label">{{ getCategoryLabel(key) }}：</span>
                      <span class="category-value">
                        <dict-label :model-value="value" :options="getCategoryLabel(key, true)" />
                      </span>
                      <el-button
                        type="text"
                        size="small"
                        class="remove-btn"
                        @click.stop="removeCategoryField(key, row.tid)"
                      >
                        移除
                      </el-button>
                    </div>
                    <div v-if="Object.keys(categoryData).length === 0" class="empty-category">
                      暂无类目信息
                    </div>
                  </div>
                  <div v-else class="error-container">加载失败，请重试</div>
                </el-popover>
              </div>
            </template>
          </DataTable>
        </div>
      </el-splitter-panel>
    </el-splitter>

    <!-- 类目分类抽屉 -->
    <data-category-drawer
      v-model="categoryDrawerVisible"
      :selected-assets="categorySelectedAssets"
      :is-batch="isBatchCategory"
      :category-config="categoryConfig"
      :category-options-map="categoryOptionsMap"
      @update:selected-assets="categorySelectedAssets = $event"
      @success="handleCategorySuccess"
    />
    <!-- 字段显示设置抽屉 -->
    <field-display-drawer
      v-model="fieldDrawerVisible"
      :category-config="categoryConfig"
      :form-json="rawFormJson"
      :form-name="formName"
      :tid="formTid"
      :env="formEnv"
      @saved="handleFieldDisplaySaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useDictStore } from "@/store";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import type { UColumnProps } from "@/components/data/data-table.vue";
import { get } from "lodash-es";
// import dataCategoryDrawer from "./data-category-drawer.vue";
// import fieldDisplayDrawer from "./field-display-drawer.vue";

interface CategoryItem {
  field: string;
  title: string;
  label?: string;
  $dict?: string;
  fieldShow?: boolean; // 用于判断类目树是否显示该字段
  fieldSortNo?: number; // 用于排序类目树字段的顺序
  props?: {
    options?: any[];
    multiple?: boolean;
    filters?: {
      [key: string]: string;
    };
    [key: string]: any;
  };
  children?: any[];
}
const router = useRouter();

const publishStatusOptions = useDictStore().getDictItems("publishStatus");

const tableRef = ref();
const categoryConfig = ref<CategoryItem[]>([]);
// 已解析的类目选项数据，key 为 field，value 为经过 filters 过滤后的 options 数组
const categoryOptionsMap = ref<Record<string, any[]>>({});
const currentNodeId = ref();
const currentNode = ref();
const treeRef = ref();
const countMap = ref({}); // 左侧树统计
const treeData = ref<any>([
  {
    label: "全部",
    value: "",
    id: "tree_node_all",
    children: [],
  },
]);
const tableColumns = ref<UColumnProps[]>([
  {
    prop: "catalogName",
    label: "数据目录名称",
    showOverflowTooltip: true,
  },
  {
    prop: "targetTableName",
    label: "挂接资源",
    showOverflowTooltip: true,
  },
  {
    prop: "shareType",
    label: "共享属性",
    width: 120,
    options: "shareType",
  },
  {
    prop: "category",
    label: "类目",
    width: 120,
  },
  {
    prop: "orgName",
    label: "数据来源部门",
    showOverflowTooltip: true,
  },
  {
    prop: "recordCount",
    label: "记录数",
  },
  {
    prop: "updateCycle",
    label: "更新周期",
    options: "updateCycle",
  },
  {
    prop: "regTime",
    label: "目录注册时间",
    width: 180,
  },
  {
    prop: "updatedTime",
    label: "目录更新时间",
    width: 180,
  },
  {
    prop: "publishStatus",
    label: "目录状态",
    options: "publishStatus",
    props: { dot: true },
  },
  {
    prop: "operation",
    label: "操作",
    type: "buttons",
    fixed: "right",
    width: 150,
    buttons: [
      {
        label: "分类",
        type: "primary",
        link: true,
        icon: "cascader",
        click: (row: any) => handleClassify(row),
      },
      {
        label: "发布",
        type: "primary",
        link: true,
        icon: "el-icon-Position",
        if: (row: any) => row.publishStatus == 0,
        divider: true,
        click: (row: any) => handlePublish(row),
      },
      {
        label: "下架",
        type: "primary",
        link: true,
        icon: "box",
        divider: true,
        if: (row: any) => row.publishStatus == 1,
        click: (row: any) => handlePublish(row),
      },
    ],
  },
]);
const publishStatus = ref("");
const assetName = ref("");
const selectedRows = ref([]);
const categoryData = ref(null);
const isLoadingCategory = ref(true);
const categoryInfo = ref<any>({});
const categoryDrawerVisible = ref(false);
const categorySelectedAssets = ref<any[]>([]);
const isBatchCategory = ref(false);

// 字段显示设置抽屉相关
const fieldDrawerVisible = ref(false);
const rawFormJson = ref<any[]>([]); // 原始完整 formJson
const formName = ref("");
const formTid = ref("");
const formEnv = ref("");

const handleDetailClick = (info: any) => {
  router.push({
    path: "detail",
    query: info,
  });
};

// 获取表格数据
const fetchTableData = async ({
  pageNo: pageNum,
  pageSize,
}: {
  pageNo: number;
  pageSize: number;
}) => {
  const conditions = [
    // 资产类型
    {
      field: "assetType",
      value: "catalog",
      type: "match",
    },
    {
      field: "assetStatus",
      value: "2", // 已注册目录
      type: "match",
    },
  ];
  if (publishStatus.value) {
    // 状态筛选
    conditions.push({
      field: "publishStatus",
      value: publishStatus.value,
      type: "match",
    });
  }
  if (assetName.value) {
    // 关键字查询
    conditions.push({
      field: "catalogName",
      value: assetName.value,
      type: "like",
    });
  }
  if (currentNode.value) {
    // 左侧树查询
    conditions.push({
      field: currentNode.value.pKey || currentNode.value.field,
      value: currentNode.value.value,
      type: "match",
    });
  }

  const response = await $common.post("/dst/catalog/page", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions,
  });
  const data = response.data || response;
  const list = data.list.map((row: any) => ({
    ...row,
    publishStatus: row.publishStatus || "0",
  }));
  const total = data.total || 0;
  return {
    list,
    total,
  };
};

const refreshTaleData = () => {
  tableRef.value.refresh(false);
};

// 树节点点击
const handleNodeClick = (node: any) => {
  currentNode.value = node;
  currentNodeId.value = node.id;
  refreshTaleData();
};

// 选择行变化
const handleSelectionChange = (selection: any) => {
  selectedRows.value = selection;
};

// 批量分类
const batchClassify = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择要分类的数据");
    return;
  }
  categorySelectedAssets.value = selectedRows.value;
  isBatchCategory.value = true;
  categoryDrawerVisible.value = true;
};

// 批量发布
const batchPublish = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择要发布的数据");
    return;
  }

  const tids = selectedRows.value.map((row: any) => row.tid);
  $common.handle({
    url: "/dst/catalog/properties/batch-save",
    action: "批量发布",
    info: `是否批量发布选中的 ${selectedRows.value.length} 条数据？`,
    data: {
      tids,
      props: {
        publishStatus: "1",
        publishTime: dayjs().format("YYYY-MM-DD HH:mm:ss"),
      },
    },
    done: () => {
      // 刷新表格数据
      refreshTaleData();
    },
  });
};

// 分类
const handleClassify = (row: any) => {
  categorySelectedAssets.value = [row];
  isBatchCategory.value = false;
  categoryDrawerVisible.value = true;
};

// 发布
const handlePublish = (row: any) => {
  if (!row.tid) return;

  // 确定发布状态
  const publishStatus = row.publishStatus === "1" ? "0" : "1";
  const actionText = row.publishStatus === "1" ? "下架" : "发布";

  // 使用$common.handle方法
  $common.handle({
    url: "/dst/catalog/properties/batch-save",
    action: actionText,
    info: `是否${actionText}该数据？`,
    data: {
      tids: [row.tid],
      props: {
        publishStatus,
        publishTime: dayjs().format("YYYY-MM-DD HH:mm:ss"),
      },
    },
    done: () => {
      // 刷新表格数据
      refreshTaleData();
    },
  });
};
// 唯一ID计数器，用于给树节点生成全局唯一 id
let uniqueIdCounter = 0;
// 处理字典项的递归函数
const processItems = (items: any[], field: string) => {
  return items.map((item: any) => {
    const processedItem = {
      ...item,
      pKey: field,
      id: `tree_node_${uniqueIdCounter++}`,
    };
    if (item.children && Array.isArray(item.children)) {
      processedItem.children = processItems(item.children, field);
    }
    return processedItem;
  });
};

const getCategoryConfig = async () => {
  try {
    categoryOptionsMap.value = {};
    const formConfig = await $form.get("目录详情");
    // 保存原始完整 formJson，用于后续抽屉保存
    rawFormJson.value = formConfig;

    // 获取表单元数据（tid、env、formName），用于保存接口入参
    const formMeta = await $form.getMeta("目录详情");

    if (formMeta) {
      formName.value = formMeta.formName || "目录详情";
      formTid.value = formMeta.tid || "";
      formEnv.value = formMeta.env || "";
    }

    // 过滤并转换配置项：保留有 options 的字段
    categoryConfig.value = formConfig
      .filter((item: CategoryItem) => item.props?.options || item.$dict)
      .map((item: CategoryItem, idx: number) => ({
        ...item,
        id: `tree_node_${uniqueIdCounter++}`,
        label: item.title,
        fieldShow: item.fieldShow !== undefined ? item.fieldShow : true,
        fieldSortNo: item.fieldSortNo !== undefined ? item.fieldSortNo : idx,
      }));
    // 按 fieldSortNo 排序，决定左侧树的展示顺序
    categoryConfig.value.sort((a: any, b: any) => (a.fieldSortNo ?? 0) - (b.fieldSortNo ?? 0));

    // 处理每个配置项的 options
    for (const config of categoryConfig.value) {
      const options = config.props?.options || config.$dict;
      if (!options) {
        config.children = [];
        continue;
      }
      // 解析原始 options 列表
      let rawOptions: any[] = [];
      if (typeof options === "string") {
        try {
          await useDictStore().loadDictItems(options);
          rawOptions = useDictStore().getDictItems(options) || [];
        } catch (error) {
          console.error("加载字典数据失败:", error);
          config.children = [];
          continue;
        }
      } else if (Array.isArray(options)) {
        rawOptions = options;
      } else {
        config.children = [];
        continue;
      }

      // 如果 props.filters 有值，按 filters 条件过滤 options
      const filters = config.props?.filters;
      if (filters && Object.keys(filters).length > 0) {
        // 解析 filters 中 $xxx.yyy 格式的占位符为实际值
        const resolvedFilters: Record<string, any> = {};
        for (const [key, val] of Object.entries(filters)) {
          if (typeof val === "string" && val.startsWith("$")) {
            const path = val; // 保留 $, 如 "$user.orgId" → window.$user.orgId
            resolvedFilters[key] = path
              .split(".")
              .reduce((obj: any, k: string) => obj?.[k], window as any);
          } else {
            resolvedFilters[key] = val;
          }
        }
        rawOptions = rawOptions.filter((item: any) =>
          Object.entries(resolvedFilters).every(([key, value]) => item[key] === value)
        );
      }

      // 保存已解析的 options，供分类抽屉使用（所有字段都需要，不管显隐）
      categoryOptionsMap.value[config.field] = rawOptions;

      // 如果 fieldShow 为 false，则跳过不添加到左侧树
      if (config.fieldShow === false) {
        config.children = [];
        continue;
      }

      config.children = processItems(rawOptions, config.field);
    }

    // 只将 fieldShow 为 true 的字段挂到树上
    treeData.value[0].children = categoryConfig.value.filter((c: any) => c.fieldShow !== false);
  } catch (error) {
    console.error("获取目录配置失败:", error);

    if (!treeData.value[0]) {
      treeData.value[0] = { children: [] };
    } else {
      treeData.value[0].children = [];
    }
  }
};

// 处理类目信息点击
const handleCategoryClick = async (row: any) => {
  if (!row.tid) return;
  categoryData.value = null;
  isLoadingCategory.value = true;

  try {
    // 获取目录详情
    const response = await $common.post("/dst/catalog/detail", { tid: row.tid });
    const data = response.data || response;
    categoryInfo.value = data;

    // 处理类目数据，只保留有值的字段
    const categoryFields: any = {};
    if (categoryConfig.value?.length) {
      categoryConfig.value.forEach((field: any) => {
        if (field.props && field.props.options && data[field.field]) {
          categoryFields[field.field] = data[field.field];
        }
      });
    }

    categoryData.value = categoryFields;
  } catch (error) {
    console.error("获取目录详情失败:", error);
  } finally {
    isLoadingCategory.value = false;
  }
};

// 获取类目字段的标签
const getCategoryLabel = (field: string, getOptions: boolean = false): any | string => {
  if (!categoryConfig.value || !Array.isArray(categoryConfig.value)) {
    return field;
  }

  const fieldConfig = categoryConfig.value.find((item: any) => item.field === field);
  return getOptions ? fieldConfig?.props?.options || {} : fieldConfig?.title || field;
};

// 移除类目字段
const removeCategoryField = (field: string, tid: string) => {
  if (!tid || !categoryData.value) return;

  // 使用二次确认弹窗
  $common.handle({
    url: "/dst/catalog/properties/save",
    action: "移除",
    info: `是否移除该类目字段？`,
    data: {
      props: {
        [field]: null,
      },
      tid,
    },
    done: async () => {
      refreshTaleData();
    },
  });
};

// 类目保存成功回调
const handleCategorySuccess = () => {
  refreshTaleData();
};

// 字段显示设置保存成功回调
const handleFieldDisplaySaved = async () => {
  // 清空表单缓存，重新拉取最新配置
  $form.clearCache();
  await getCategoryConfig();
};

// 初始化数据
onMounted(async () => {
  await getCategoryConfig();

  // 加载左侧树统计
  $common
    .post("/dst/statistics/common", {
      conditions: [
        { field: "assetType", type: "match", value: "catalog" },
        { field: "assetStatus", type: "match", value: 2 },
      ],
      countFields: categoryConfig.value.filter((el) => el.fieldShow).map((el) => el.field),
    })
    .then((res) => {
      countMap.value = res;
    });
});
</script>

<style scoped lang="scss">
.data-catalog-content {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;

  .left-panel {
    width: 100%;
    height: 100%;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 10px;
      margin-right: 14px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: #464c64;
      }
    }

    .left-header-btn {
      padding: 4px 8px;
      color: var(--el-color-primary);
      &:hover {
        background-color: #1890ff1a;
      }
    }

    .tree-container {
      height: calc(100% - 53px);

      :deep(.el-tree) {
        /*节点高度自适应*/
        --el-tree-node-content-height: 100%;
      }

      .tree-container-custom {
        height: 100%;
        :deep(.u-tree-search) {
          margin-right: 14px;
        }
        :deep(.el-tree) {
          padding-right: 14px;
        }
      }
      .custom-tree-node {
        display: flex;
        align-items: center;
        width: 100%;
        padding-right: 10px;

        .node-label {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .node-icon-folder {
          color: #0b8bf9;
          margin-right: 6px;
        }
        .node-icon-folder :deep(.icon) {
          width: 23px !important;
          height: 23px !important;
        }

        .more-icon {
          opacity: 0;
          cursor: pointer;
          transition: opacity 0.2s;
          font-size: 16px;
          color: #909399;
          padding: 4px;
          border-radius: 4px;
          outline: none;

          &:hover {
            color: var(--el-color-primary);
            background-color: var(--el-color-primary-light-9);
            outline: none;
          }

          &:focus {
            outline: none;
          }

          &.visible {
            opacity: 1;
          }
        }
      }
      :deep(.el-tree-node__content) {
        &:hover {
          .more-icon {
            opacity: 1;
          }
        }

        &.is-current {
          background-color: var(--el-color-primary-light-9) !important;
          color: var(--el-color-primary);
        }
      }

      :deep(.el-dropdown-menu__item.delete-item) {
        &:hover {
          background-color: #fef0f0;
        }
      }
    }
  }

  .right-panel {
    width: 100%;
    height: 100%;
    :deep(.el-button .el-icon + span) {
      margin-left: 3px;
    }
  }
}
:deep(.dialog-footer-custom) {
  border-top: none;
}
.category-info {
  position: relative;
}

.category-content {
  max-height: 300px;
  overflow-y: auto;
  .category-item {
    display: flex;
    align-items: center;
    margin-bottom: 5px;
  }

  .category-label {
    font-weight: 500;
  }

  .category-value {
    flex: 2;
    margin-right: 10px;
    word-break: break-all;
    :deep(.dict-text) {
      margin-right: 5px;
    }
  }

  .remove-btn {
    flex-shrink: 0;
    color: #f56c6c;
  }
}
.loading-container,
.error-container,
.empty-category {
  padding: 20px;
  text-align: center;
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 150px;
}

.empty-category {
  color: #909399;
}
</style>
