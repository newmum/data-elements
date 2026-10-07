<template>
  <el-drawer
    v-model="visible"
    :title="isBatch ? '批量分类' : '目录分类'"
    :size="600"
    :destroy-on-close="true"
    @close="handleClose"
  >
    <div class="category-drawer-content">
      <!-- 已选资产列表（批量时显示） -->
      <div v-if="isBatch" class="selected-assets">
        <div v-for="(item, index) in selectedAssets" :key="index" class="asset-item">
          <div class="asset-info">
            <Icon icon="catalog" class="mr-2" />
            <span class="data-asset-name">{{ item.catalogName }}</span>
          </div>
          <Icon icon="el-icon-Close" class="close-icon" @click="handleRemoveAsset(index)" />
        </div>
      </div>

      <!-- 非批量时的资产名称 -->
      <div v-else class="single-asset-name">
        <Icon icon="catalog" class="mr-2" />
        <span>{{ selectedAssets[0]?.catalogName }}</span>
      </div>

      <el-divider />

      <!-- 已选类目标签 -->
      <div v-if="choosedCategoryList.length > 0" class="selected-categories">
        <div class="category-label">已选:</div>
        <div class="category-tags">
          <template v-for="item in choosedCategoryList" :key="item.field">
            <div v-for="(label, index) in item.label" :key="index" class="category-item">
              <Icon icon="folder" :color="'#0b8bf9'" class="mr-1" />
              <span>{{ label }}</span>
            </div>
          </template>
        </div>
      </div>

      <el-divider v-if="choosedCategoryList.length > 0" />

      <!-- 类目选择区域 -->
      <div class="category-section">
        <div class="category-title">所属类目:</div>
        <div class="category-tree-container">
          <el-tabs
            v-if="props.categoryConfig.length > 0"
            v-model="activeTab"
            tab-position="left"
            @tab-change="(name: any) => handleTabChange(name as string)"
          >
            <el-tab-pane
              v-for="item in props.categoryConfig"
              :key="item.field"
              :label="item.title"
              :name="item.field"
            >
              <div
                v-if="!categoryTreeData[item.field] || categoryTreeData[item.field].length === 0"
                class="empty-tree"
              >
                <el-empty description="暂无类目数据" :image-size="60" />
              </div>
              <el-tree
                v-else
                :ref="(el) => setTreeRef(item.field, el)"
                :data="categoryTreeData[item.field]"
                :props="treeProps"
                :expand-on-click-node="false"
                :check-strictly="true"
                show-checkbox
                node-key="value"
                :default-expand-all="true"
                :default-checked-keys="getCheckedKeys(item.field)"
                @check-change="(_node: any, check: boolean) => handleTreeCheckChange(_node, check)"
                @check="(_node: any, checkedStatus: any) => handleTreeCheck(checkedStatus, item)"
              >
                <template #default="{ node, data }">
                  <div class="tree-node-label">
                    <Icon v-if="!data.children?.length" :icon="'file'" class="mr-1" />
                    <Icon
                      v-else
                      :icon="node.expanded ? 'folder-open' : 'folder'"
                      :color="'#0b8bf9'"
                      class="mr-1"
                    />
                    <span>{{ node.label }}</span>
                  </div>
                </template>
              </el-tree>
            </el-tab-pane>
          </el-tabs>
          <el-empty v-else description="暂无类目配置" :image-size="60" />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :disabled="btnLoading" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { findTreeNodeByTid } from "@/utils";

interface CategoryItem {
  field: string;
  title: string;
  label?: string;
  $dict?: string;
  fieldShow?: boolean;
  fieldSortNo?: number;
  props?: {
    options?: any[];
    multiple?: boolean;
    filters?: {
      [key: string]: string;
    };
    [key: string]: any;
  };
}

interface AssetItem {
  tid: string;
  catalogName: string;
  [key: string]: any;
}

const props = defineProps<{
  modelValue: boolean;
  selectedAssets: AssetItem[];
  isBatch?: boolean;
  categoryConfig: CategoryItem[];
  categoryOptionsMap?: Record<string, any[]>;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "update:selectedAssets", value: AssetItem[]): void;
  (e: "success"): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

// 类目树数据
const categoryTreeData = ref<Record<string, OptionType[]>>({});
// 已选类目列表
const choosedCategoryList = ref<{ field: string; label: string[]; value: string[] }[]>([]);
// 当前选中的 tab
const activeTab = ref("");
// 按钮 loading
const btnLoading = ref(false);
// 树组件引用
const treeRefs = ref<Record<string, any>>({});

// 设置树组件引用
const setTreeRef = (field: string, el: any) => {
  if (el) {
    treeRefs.value[field] = el;
  }
};

// 树组件配置
const treeProps = {
  children: "children",
  label: "label",
  value: "value",
};

// 获取类目配置和树数据
const fetchCategoryData = async () => {
  if (!props.selectedAssets || props.selectedAssets.length === 0) return;

  // 直接使用父组件传入的已解析配置，不再重复请求
  activeTab.value = props.categoryConfig[0]?.field || "";

  // 从 categoryOptionsMap 中取已解析的 options
  if (props.categoryOptionsMap) {
    categoryTreeData.value = { ...props.categoryOptionsMap };
  }

  // 当不是批量分类时，才获取已选类目数据
  if (!props.isBatch && props.selectedAssets.length > 0) {
    await fetchCurrentCategories();
  }
};

// 获取当前选中资产的类目数据
const fetchCurrentCategories = async () => {
  const firstAsset = props.selectedAssets[0];
  if (!firstAsset?.tid) return;

  try {
    const response = await $common.post("/dst/catalog/detail", {
      tid: firstAsset.tid,
    });
    const data = response.data || response;

    // 根据配置处理每个类目字段
    choosedCategoryList.value = [];
    props.categoryConfig.forEach((config) => {
      const value = data[config.field];
      if (value !== undefined && value !== null && value !== "") {
        const values = value.includes(",") ? value.split(",") : [value];

        choosedCategoryList.value.push({
          field: config.field,
          label: getCheckLabels(config.field, values),
          value: values,
        });
      }
    });
  } catch (error) {
    console.error("获取资产类目失败:", error);
  }
};

const getCheckLabels = (field: string, value: string[]) => {
  const treeData = categoryTreeData.value[field];
  if (!treeData || !treeData.length) return [];

  const labels = value.map((v) => {
    const node = findTreeNodeByTid(treeData, v, "value");
    return node ? node.label : v;
  });

  return labels;
};

// 获取已选中的 key
const getCheckedKeys = (field: string): string[] => {
  const category = choosedCategoryList.value.find((item) => item.field === field);
  if (!category) return [];
  return category.value;
};

let currentCheckNode: any = {};
const handleTreeCheckChange = (node: any, check: boolean) => {
  currentCheckNode = check ? node : {};
};
// 处理树节点选择
const handleTreeCheck = (checkedInfo: any, config: CategoryItem) => {
  let checkedKeys = checkedInfo.checkedKeys;
  let checkedNodes = checkedInfo.checkedNodes;
  const isMultiple = config.props?.multiple;
  if (!isMultiple) {
    if (checkedKeys.length > 1 && currentCheckNode.value) {
      // 处理单选逻辑
      const treeRef = treeRefs.value[config.field];
      treeRef.setCheckedKeys([currentCheckNode.value]);
      checkedNodes = [currentCheckNode];
      checkedKeys = [currentCheckNode.value];
    }
  }
  const labels = checkedNodes.map((n: any) => n.label) || [];
  const newCategory = {
    field: config.field,
    label: labels,
    value: checkedKeys,
  };

  const categoryIndex = choosedCategoryList.value.findIndex((item) => item.field === config.field);
  if (categoryIndex > -1) {
    choosedCategoryList.value[categoryIndex] = newCategory;
  } else {
    choosedCategoryList.value.push(newCategory);
  }
};

// 移除已选资产
const handleRemoveAsset = (index: number) => {
  const newAssets = [...props.selectedAssets];
  newAssets.splice(index, 1);
  emit("update:selectedAssets", newAssets);

  // 如果没有资产了，关闭抽屉
  if (newAssets.length === 0) {
    emit("update:modelValue", false);
  }
};

// Tab 切换
const handleTabChange = (name: string) => {
  activeTab.value = name;
};

// 关闭抽屉
const handleClose = () => {
  visible.value = false;
  choosedCategoryList.value = [];
  categoryTreeData.value = {};
  treeRefs.value = {};
};

// 保存
const handleSave = async () => {
  btnLoading.value = true;

  try {
    // 构建类目 props
    const categoryProps: Record<string, any> = {};
    choosedCategoryList.value.forEach((item) => {
      categoryProps[item.field] = item.value.join(",");
    });

    // 获取所有选中资产的 tid
    const tids = props.selectedAssets.map((asset: AssetItem) => asset.tid);
    await $common.post("/dst/catalog/properties/batch-save", {
      tids,
      props: categoryProps,
    });

    emit("success");
    handleClose();
  } catch (error) {
    console.error("保存类目失败:", error);
  } finally {
    btnLoading.value = false;
  }
};

// 监听抽屉显示
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      fetchCategoryData();
    }
  }
);
</script>

<style scoped lang="scss">
.category-drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  :deep(.el-divider--horizontal) {
    margin: 15px 0;
  }
}

.selected-assets {
  max-height: 150px;
  overflow-y: auto;

  .asset-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    margin-bottom: 8px;
    background: #f4f7fc;
    border-radius: 4px;

    .asset-info {
      display: flex;
      align-items: center;
      overflow: hidden;

      .data-asset-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .close-icon {
      cursor: pointer;
      color: #909399;

      &:hover {
        color: #f56c6c;
      }
    }
  }
}

.single-asset-name {
  display: flex;
  align-items: center;
  padding: 12px;
  background: #f4f7fc;
  border-radius: 4px;
  font-weight: 500;
}

.selected-categories {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;

  .category-label {
    margin-right: 8px;
    margin-top: 6px;
    font-weight: 500;
  }

  .category-tags {
    display: flex;
    flex-wrap: wrap;
    flex: 1;

    .category-item {
      display: flex;
      align-items: center;
      margin: 0 8px 8px 0;
      padding: 5px 10px;
      background: #f4f7fc;
      border-radius: 4px;
      gap: 4px;
    }
  }
}

.category-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .category-title {
    margin-bottom: 12px;
    font-weight: 500;
  }

  .category-tree-container {
    flex: 1;
    overflow: hidden;

    :deep(.el-tabs) {
      height: 100%;
    }
    :deep(.el-tabs__content) {
      height: 100%;
      overflow-y: auto;
    }

    .empty-tree {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 200px;
    }

    .tree-node-label {
      display: flex;
      align-items: center;
    }
  }
}

.drawer-footer {
  text-align: right;
}
</style>
