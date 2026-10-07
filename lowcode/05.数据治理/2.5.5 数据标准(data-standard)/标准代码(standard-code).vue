<template>
  <div class="standard-code card-container pl-5">
    <div class="h-full grid" style="grid-template-columns: 20% 50% 30%">
      <div class="standard-code-left h-full">
        <div class="sidebar-header panel-header flex-x-between">
          <h3>标准分类目录</h3>
          <el-button type="text" class="left-header-btn" @click="handleAction('add')">
            <Icon icon="el-icon-plus" />
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
            :search-placeholder="'请输入搜索关键词'"
            :default-expand-all="true"
            :default-props="{
              children: 'children',
              label: 'label',
            }"
            class="tree-container-custom"
            @node-click="handleNodeClick"
          ></UTree>
        </div>
      </div>

      <div class="standard-code-center flex flex-col">
        <div class="center-header flex-x-between">
          <h3 style="padding-top: 11px">标准代码</h3>
          <div>
            <el-input
              v-model="searchQuery"
              placeholder="请输入标准代码名称"
              prefix-icon="Search"
              style="width: 200px"
              @keyup.enter="refreshData"
            />
            <el-button type="primary" class="ml-3" @click="handleAddCode">
              <icon :icon="'el-icon-Plus'" class="mr-2" />
              创建标准代码
            </el-button>
          </div>
        </div>

        <div class="center-content">
          <DataTable
            ref="tableRef"
            :columns="tableColumns"
            :data="fetchTableData"
            :show-index="true"
            :stripe="false"
            :immediate="false"
            style="height: 100%"
            @row-click="handleRowClick"
          ></DataTable>
        </div>
      </div>

      <div class="standard-code-right">
        <div class="detail-header flex items-center justify-between">
          <h3 class="header-title">标准代码详情</h3>
          <div>
            <el-button type="primary" :disabled="isLoading" @click="handleSaveCode">
              <icon :icon="'save'" class="mr-2" />
              保存
            </el-button>
          </div>
        </div>
        <div class="detail-form">
          <json-form ref="formRef" :rules="formRules" :options="formOptions"></json-form>
          <u-title name="代码值定义">
            <template #right>
              <el-button type="primary" link @click="handleAddValue">
                <icon :icon="'el-icon-Plus'" class="mr-2" />
                新增代码值
              </el-button>
            </template>
          </u-title>
          <v-table
            ref="vtableRef"
            :options="vtableColumns"
            :editable="true"
            :show-toolbar="false"
            :show-operation="true"
          ></v-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h, nextTick, onMounted } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
import { findTreeNodeByTid } from "@/utils";

const props = defineProps<{
  treeData: any[];
}>();

interface InfoItem {
  tid: string;
  dictName: string;
  dictCode: string;
  dataCategoryId: string;
  dataCategoryName: string;
  dataCategoryPath: string;
  description: string;
  dictItemValue: string;
  createdBy: string;
  createdTime: string;
  updatedBy: string;
  updatedTime: string;
}
const isLoading = ref(false);
const currentNode = ref();
const selectedCodeId = ref("");
const searchQuery = ref("");
const tableRef = ref(); // DataTable 组件引用

// The root "all" category uses the sentinel path "0" and must query all
// standard code sets instead of becoming a category filter.
const getSelectedCategoryPath = () => {
  const node = currentNode.value;
  return node && node.tid !== "0" && node.treePath !== "0" ? node.treePath || null : null;
};
const vtableRef = ref();
const formRef = ref();
const formOptions = {
  form: {
    labelPosition: "top",
  },
};
const formRules = computed(() => {
  return FormUtils.fixJson([
    {
      type: "UTitle",
      props: {
        name: "基础信息",
      },
    },
    {
      type: "input",
      title: "代码表名称",
      field: "dictName",
      props: { maxLength: 255 },
      $required: true,
    },
    {
      type: "input",
      title: "代码表编码",
      field: "dictCode",
      $required: true,
      props: { maxLength: 255 },
      renderSlots: {
        append() {
          return h("div", {
            class: "i-svg:idea cursor-pointer",
            style: "font-size: 18px;",
            title: "点击生成随机编码",
            onClick: async () => {
              formRef.value.setValue({
                dictCode: `code_${Math.random().toString(36).substring(2, 10)}`,
              });
            },
          });
        },
      },
    },
    {
      type: "elTreeSelect",
      title: "标准分类",
      field: "dataCategoryId",
      props: {
        data: props.treeData?.length ? props.treeData[0].children || [] : [],
        valueKey: "tid",
        placeholder: "请选择标准分类",
        clearable: true,
        filterable: true,
        "check-strictly": true,
        "default-expand-all": true,
      },
      $required: true,
    },
    {
      type: "input",
      field: "description",
      title: "描述",
      props: {
        type: "textarea",
        placeholder: "请简要描述",
        maxlength: 500,
        rows: 4,
        showWordLimit: true,
      },
      $required: true,
    },
  ]);
});
const vtableColumns = ref({
  columns: [
    { type: "seq", width: 70 },
    {
      title: "代码值",
      field: "code",
      editRender: { name: "VxeInput" },
    },
    {
      title: "含义",
      field: "name",
      editRender: { name: "VxeInput" },
    },
  ],
  editRules: {
    code: [{ required: true, message: "代码值不能为空", trigger: "blur" }],
    name: [{ required: true, message: "含义不能为空", trigger: "blur" }],
  },
  toolbarConfig: {
    buttons: [],
    tools: [],
  },
});
const tableColumns = [
  { prop: "dictName", label: "标准代码名称" },
  { prop: "dictCode", label: "编码" },
  { prop: "updatedTime", label: "更新时间" },
];
const tableData = ref<InfoItem[]>([]);

// 树节点点击
const handleNodeClick = (node: any) => {
  if (!node) return;

  currentNode.value = node;
  searchQuery.value = "";
  clearSelectedRule();

  // 立即刷新表格数据
  refreshData();
};

const handleAction = (type: string) => {
  // TODO: 实现操作处理逻辑
  console.log("handleAction", type);
  // if (type === "add") {
  //   handleAddCode();
  // }
};

const handleAddCode = async () => {
  await nextTick();
  clearSelectedRule();

  // 如果当前有选中的树节点，自动设置分类
  if (currentNode.value && currentNode.value.tid !== "0") {
    formRef.value?.setValue({ dataCategoryId: currentNode.value.tid });
  }
};

const handleSaveCode = async () => {
  try {
    // 验证表单
    await formRef.value?.validate();
    // 验证表格
    await vtableRef.value?.validate();
    isLoading.value = true;

    // 获取表单数据
    const info = await formRef.value?.getFormData();
    // 获取表格数据
    const vData = vtableRef.value?.getData();
    const categoryNode = findTreeNodeByTid(props.treeData, info.dataCategoryId);
    if (categoryNode) {
      info.dataCategoryName = categoryNode.label;
      info.dataCategoryPath = categoryNode.treePath;
    }

    // 保存数据
    const response = await $common.post(
      "/dwm/standard/code/saveOrUpdate",
      {
        ...info,
        dictItemValue: JSON.stringify(vData),
        tid: selectedCodeId.value,
      }
    );

    // 保存成功后处理
    ElMessage.success("保存成功");
    isLoading.value = false;

    // 刷新表格数据，传递新添加行的tid
    refreshData(!selectedCodeId.value ? response?.tid : undefined);
  } catch (error) {
    console.error("验证失败:", error);
    ElMessage.error("保存失败，请重试");
    isLoading.value = false;
  }
};

const clearSelectedRule = () => {
  selectedCodeId.value = "";
  if (formRef.value) {
    formRef.value.resetFields();
  }
  vtableRef.value?.setData([]);
  tableRef.value?.setCurIndex(null);
  // 异步设置表单数据，避免阻塞主线程
  nextTick(() => {
    if (currentNode.value && currentNode.value.tid !== "0") {
      formRef.value?.setValue({ dataCategoryId: currentNode.value.tid });
    }
  });
};

const handleRowClick = (item: InfoItem) => {
  if (!item) return;

  selectedCodeId.value = item.tid;

  // 异步更新表单数据，避免阻塞主线程
  nextTick(() => {
    formRef.value?.setValue(item);
    const vtableData = JSON.parse(item.dictItemValue || "[]");
    if (Array.isArray(vtableData)) {
      vtableRef.value?.setData(vtableData);
    }
  });

  // 找到点击行的索引并设置高亮
  const index = tableData.value.findIndex((row: InfoItem) => row.tid === item.tid);
  if (index !== -1) {
    tableRef.value?.setCurIndex(index);
  }
};

// 选中表格行数据
const selectRow = () => {
  if (!selectedCodeId.value && tableData.value.length > 0) {
    const firstItem = tableData.value[0];
    handleRowClick(firstItem);
  }
};

const handleAddValue = () => {
  vtableRef.value?.gridEvents.toolbarButtonClick({ code: "append_edit" });
};

// 表格数据请求
const fetchTableData = async ({
  pageNo: pageNum,
  pageSize,
  newCodeId,
}: {
  pageNo: number;
  pageSize: number;
  newCodeId?: string;
}) => {
  try {
    const response = await $common.post("/dwm/standard/code/page", {
      dictName: searchQuery.value || null,
      categoryPath: getSelectedCategoryPath(),
      pageNum,
      pageSize,
    });
    const data = response.data || response;
    const list = data.list || [];
    const total = data.total || 0;
    tableData.value = list;

    if (tableData.value?.length) {
      // 如果有新添加的行，优先选中该行
      if (newCodeId) {
        const newRowIndex = list.findIndex((row: InfoItem) => row.tid === newCodeId);
        if (newRowIndex !== -1) {
          const newRow = list[newRowIndex];
          handleRowClick(newRow);
          return {
            list,
            total,
          };
        }
      }
      // 否则，执行原有的自动选中逻辑
      selectRow();
    } else {
      clearSelectedRule();
    }
    return {
      list,
      total,
    };
  } catch (error) {
    console.error("获取表格数据失败:", error);
    return {
      list: [],
      total: 0,
    };
  }
};

// 表格数据请求
const refreshData = (newCodeId?: string) => {
  // 传递新添加行的tid，用于后续高亮
  tableRef.value.refresh(false, { newCodeId });
};

onMounted(async () => {
  // The table uses manual loading; fetch standard code sets on first entry.
  await nextTick();
  refreshData();
});
</script>

<style scoped lang="scss">
.standard-code {
  height: 100%;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #464c64;
  }
  .standard-code-left {
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;

    .sidebar-header {
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 10px;
      margin-top: 20px;
      margin-right: 14px;
    }

    .left-header-btn {
      padding: 4px 8px;
      color: var(--el-color-primary);

      &:hover {
        background-color: #1890ff1a;
      }
    }
    .tree-container {
      height: calc(100% - 73px);
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
      }
    }
  }
  .standard-code-center {
    border-right: 1px solid rgba(5, 5, 5, 0.06);

    .center-header {
      padding: 15px;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
    }

    .center-content {
      flex: 1;
      padding: 15px;
    }
  }
  .standard-code-right {
    display: flex;
    flex-direction: column;
    overflow: auto;
    .detail-header {
      padding: 0 15px;
      height: 32px;
      margin-top: 16px;
      margin-bottom: 15px;
    }
    .detail-title {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #464c64;
      line-height: 32px;
    }
    .detail-form {
      padding: 0 15px;
      overflow: auto;
    }
  }
  .header-title {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #464c64;
    margin-top: 11px;
  }
}
</style>
