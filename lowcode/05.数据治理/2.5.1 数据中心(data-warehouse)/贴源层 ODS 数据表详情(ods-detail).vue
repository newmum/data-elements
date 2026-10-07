<template>
  <div class="ods-detail card-container flex flex-col px-5 pt-5 pb-2">
    <div class="h-full grid" style="grid-template-columns: 20% 80%">
      <!-- ========== 左侧树 ========== -->
      <div class="left-panel h-full">
        <div class="left-header flex items-center justify-between">
          <h3>贴源层分类</h3>
          <el-button type="primary" link icon="plus" @click="handleRootAdd" />
        </div>
        <div class="tree-container">
          <u-tree
            :loading="treeLoading"
            ref="taskTreeRef"
            class="task-tree-container"
            :data="treeData"
            :search="true"
            :highlight-current="true"
            :expand="false"
            :default-expand-all="false"
            :auto-expand-first-node="false"
            :default-expanded-keys="defaultExpandedKeys"
            :expand-on-click-node="false"
            :default-props="{ label: 'label', children: 'children' }"
            :node-key="'tid'"
            :current-node-key="currentNodeKey"
            @node-click="(node) => handleAction('node-click', node)"
          >
            <template #node="{ node }">
              <div class="tree-node-item">
                <u-tree-node
                  :label="node.label"
                  :node="node"
                  :show-icon="true"
                  :wrap="false"
                  class="tree-node-main"
                >
                  <template #count>
                    <span v-if="get(countMap, `dataSourceType.${node?.data.value}`, 0) > 0">
                      ({{ get(countMap, `dataSourceType.${node?.data.value}`, 0) }})
                    </span>
                    <span v-else></span>
                  </template>
                </u-tree-node>
                <el-dropdown
                  trigger="click"
                  @command="(cmd: string) => handleNodeAction(cmd, node.data)"
                >
                  <span class="tree-node-more" @click.stop>
                    <Icon icon="el-icon-MoreFilled" size="14px" />
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="add" icon="plus">新增</el-dropdown-item>
                      <el-dropdown-item command="edit" icon="edit">编辑</el-dropdown-item>
                      <el-dropdown-item
                        command="delete"
                        icon="delete"
                        style="color: var(--el-color-danger)"
                      >
                        删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </u-tree>
        </div>
      </div>

      <!-- ========== 右侧表格 ========== -->
      <div class="pl-4 flex flex-col h-full w-full">
        <DataTable
          ref="tableRef"
          :columns="tableColumns"
          :data="fetchData"
          :immediate="false"
          show-index
          style="height: 100%"
        >
          <template #toolbar>
            <div class="flex items-center justify-between">
              <div class="right-header">
                <span class="header-name">{{ currentNodeName }}</span>
              </div>
              <div class="flex items-center gap-3">
                <el-input
                  v-model.trim="state.keyword"
                  placeholder="搜索目录名称/表名"
                  clearable
                  prefix-icon="Search"
                  style="width: 200px"
                  @keydown.enter="handleAction('refresh')"
                  @clear="handleAction('refresh')"
                />
                <el-button type="primary" @click="handleAction('add')">
                  <Icon :icon="'el-icon-Plus'" class="mr-2" />
                  新建基础目录
                </el-button>
              </div>
            </div>
          </template>
          <template #column-catalogName="{ row }">
            <el-text
              type="primary"
              cursor-pointer
              @click="
                handleAction('详情', { id: row.tid, type: 'table', title: row.catalogName })
              "
            >
              <Icon icon="catalog" />
              {{ row.catalogName }}
            </el-text>
          </template>
          <template #column-targetTableName="{ row }">
            <el-text
              v-if="row.targetTableName"
              type="primary"
              cursor-pointer
              @click="
                handleAction('详情', {
                  id: row.targetTableId,
                  type: 'table',
                  title: row.targetTableName,
                })
              "
            >
              <Icon icon="table" />
              {{ row.targetTableName }}
            </el-text>
            <el-text v-else type="info">暂无挂接表</el-text>
          </template>
          <template #column-tableName="{ row }">
            <el-text
              type="primary"
              cursor-pointer
              @click="
                handleAction('详情', { id: row.sourceTableId, type: 'table', title: row.tableName })
              "
            >
              <Icon icon="hive" />
              {{ row.tableName }}
            </el-text>
          </template>
          <template #column-sourceDir="{ row }">
            <el-text
              type="primary"
              cursor-pointer
              @click="
                handleAction('详情', {
                  id: row.sourceCatalogId,
                  type: 'category',
                  title: row.sourceDir,
                })
              "
            >
              {{ row.sourceDir }}
            </el-text>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- ========== 新增/编辑弹窗 ========== -->
    <u-modal
      v-model="dialogVisible"
      :title="dialogTitle"
      :confirm-loading="dialogLoading"
      @confirm="handleDialogConfirm"
    >
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="90px">
        <el-form-item label="父级节点">
          <dict-select
            v-model="dialogForm.parentId"
            type="cascader"
            options="dataSourceType"
            model-key="tid"
            :props="{
              checkStrictly: true,
              emitPath: false,
              value: 'tid',
            }"
            :filters="{ value: 'ods' }"
            style="width: 100%"
            placeholder="请选择父级节点"
          />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="dialogForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典Code" prop="dictCode">
          <el-input
            v-model="dialogForm.dictCode"
            placeholder="请输入字典Code"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="dialogForm.sortNo"
            :min="0"
            :step="1"
            :precision="0"
            controls-position="right"
            placeholder="请输入排序值"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="dialogForm.dictDesc"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>
    </u-modal>
  </div>
</template>

<script setup lang="ts">
import { useRegisterModal } from "@/composables";
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { get } from "lodash-es";

// 登记弹框
const { openRegisterModal, open } = useRegisterModal();
const router = useRouter();
const taskTreeRef = ref();
const tableRef = ref();
const dialogFormRef = ref();

// ─── 树 ──────────────────────────────────────
const treeData = ref<any[]>([]);
const treeLoading = ref(false);
const currentNodeKey = ref<string | null>(null);
const currentNodeName = ref("");
const defaultExpandedKeys = ref<string[]>([]);
const rootDictNode = ref<any>(null);
const countMap = ref<Record<string, Record<string, number>>>({});

// ─── 表格筛选状态 ────────────────────────────
const state = reactive({
  keyword: "",
  leftSelectNode: {} as any,
});

// ─── 表格列配置 ──────────────────────────────
const tableColumns = ref([
  { prop: "catalogName", label: "贴源表名称" },
  { prop: "targetTableName", label: "挂接表名" },
  { prop: "sourceDir", label: "关联源目录" },
  { prop: "processLogic", label: "加工逻辑概要" },
  { prop: "shareType", label: "共享属性", options: "shareType", width: 120 },
  { prop: "dataLevel", label: "数据范围", options: "dataLevel" },
  { prop: "fieldCount", label: "字段数", props: { suffix: "个" }, align: "right" },
  { prop: "recordCount", label: "记录数", props: { suffix: "条" }, align: "right" },
  { prop: "updatedTime", label: "最后更新时间" },
  { prop: "status", label: "对标状态", options: "alignmentStatus" },
  {
    prop: "operation",
    label: "操作",
    buttons: [
      {
        label: "数据开发",
        type: "primary",
        link: true,
        if: (row: any) => row.develop == "0",
        click: (row: any) => handleAction("develop", row),
      },
      {
        label: "开发任务",
        type: "primary",
        link: true,
        if: (row: any) => row.develop == "1",
        click: (row: any) => handleAction("task", row),
      },
      {
        label: "数据质检",
        type: "primary",
        link: true,
        if: (row: any) => row.quality == "0",
        click: (row: any) => handleAction("check", row),
      },
      {
        label: "质检结果",
        type: "primary",
        link: true,
        if: (row: any) => row.quality == "1",
        click: (row: any) => handleAction("result", row),
      },
      {
        label: "删除",
        type: "danger",
        link: true,
        click: (row: any) => handleAction("delete", row),
      },
    ],
  },
]);

// ─── 弹窗 ────────────────────────────────────
const dialogVisible = ref(false);
const dialogLoading = ref(false);
const dialogMode = ref<"add" | "edit">("add");
const dialogEditTid = ref<string | null>(null);
const dialogForm = ref<Record<string, any>>({});

const dialogRules = {
  dictName: [{ required: true, message: "请输入字典名称", trigger: "blur" }],
  dictCode: [{ required: true, message: "请输入字典Code", trigger: "blur" }],
};

const dialogTitle = computed(() => (dialogMode.value === "edit" ? "编辑" : "新增"));

// ========================================================
//  handleAction — 统一操作入口
// ========================================================
const handleAction = (type: string, row?: any) => {
  switch (type) {
    case "node-click":
      state.leftSelectNode = row;
      currentNodeKey.value = row.tid || row.value;
      currentNodeName.value = row.label;
      tableRef.value?.refresh(false);
      break;
    case "refresh":
      tableRef.value?.refresh(true);
      break;
    case "详情":
      router.push({
        path: "detail",
        query: { id: row.id, type: row.type, title: row.title },
      });
      break;
    // 表格操作按钮（占位，后续实现）
    case "add":
      openRegisterModal({
        registerClass: "table",
        updateFormRule: [
          {
            field: "dataSourceType",
            value: state.leftSelectNode.value,
          },
        ],
      });
    case "develop":
    case "task":
    case "check":
    case "result":
    case "delete":
      console.log("[ods-detail] handleAction:", type, row);
      break;
  }
};

// ─── 收集树中所有叶子节点的 value ───
const collectLeafValues = (nodes: any[]): string[] => {
  const values: string[] = [];
  const traverse = (list: any[]) => {
    for (const node of list) {
      if (node.value) values.push(node.value);
      if (node.children?.length) traverse(node.children);
    }
  };
  traverse(nodes);
  return values;
};

// ─── 加载左侧树统计 ───
const loadCountMap = () => {
  const leafValues = collectLeafValues(treeData.value);
  if (!leafValues.length) return;
  $common
    .post("/dst/statistics/common", {
      conditions: [{ field: "dataSourceType", type: "in", value: leafValues }],
      countFields: ["dataSourceType"],
    })
    .then((res) => {
      countMap.value = res;
    });
};

// ─── 加载树数据 ───
const fetchTreeData = async () => {
  treeLoading.value = true;
  try {
    const dictTreeList = await $common.get("/sym/dict", { code: "dataSourceType" });
    treeData.value = dictTreeList.filter((el: any) => el.value === "ods");
    rootDictNode.value = treeData.value[0] || null;
    if (rootDictNode.value?.tid) {
      defaultExpandedKeys.value = [rootDictNode.value.tid];
    }
    // 加载统计
    loadCountMap();
  } catch (e) {
    console.error("[ods-detail] 加载树数据失败", e);
    treeData.value = [];
  } finally {
    treeLoading.value = false;
  }
};

// ─── 刷新树并恢复选中 ───
const refreshTree = async () => {
  const prevKey = currentNodeKey.value;
  await fetchTreeData();

  const findNodeByTid = (nodes: any[], tid: string): any => {
    for (const n of nodes) {
      if (n.tid === tid) return n;
      if (n.children) {
        const found = findNodeByTid(n.children, tid);
        if (found) return found;
      }
    }
    return null;
  };

  if (prevKey && treeData.value.length) {
    const node = findNodeByTid(treeData.value, prevKey);
    if (node) {
      handleAction("node-click", node);
    } else if (treeData.value.length) {
      handleAction("node-click", treeData.value[0]);
    }
  } else if (treeData.value.length) {
    handleAction("node-click", treeData.value[0]);
  }
};

// ─── 根节点 + 按钮 → 新增子级 ───
const handleRootAdd = () => {
  dialogMode.value = "add";
  dialogForm.value = {
    parentId: rootDictNode.value?.tid || null,
    sortNo: 0,
    dictName: "",
    dictCode: "",
    dictDesc: "",
  };
  dialogVisible.value = true;
};

// ─── 树节点 ... 菜单 ───
const handleNodeAction = (command: string, data: any) => {
  switch (command) {
    case "add":
      dialogMode.value = "add";
      dialogForm.value = {
        parentId: data.tid || null,
        sortNo: 0,
        dictName: "",
        dictCode: "",
        dictDesc: "",
      };
      dialogVisible.value = true;
      break;
    case "edit":
      dialogMode.value = "edit";
      dialogEditTid.value = data.tid;
      dialogForm.value = {
        parentId: data.parentId || null,
        dictName: data.label,
        dictCode: data.value || data.dictCode,
        dictDesc: data.dictDesc || "",
        sortNo: data.sortNo || 0,
      };
      dialogVisible.value = true;
      break;
    case "delete":
      handleDeleteNode(data);
      break;
  }
};

// ─── 删除节点 ───
const handleDeleteNode = (data: any) => {
  if (data.children?.length) {
    $message.warning("该节点下存在子级数据，请先删除子级");
    return;
  }
  $common.handle({
    url: "/sym/dictSaveOrUpdate",
    info: `确认删除"${data.label}"？`,
    action: "删除",
    data: { tid: data.tid, action: "delete" },
    done: () => {
      refreshTree();
    },
  });
};

// ─── 弹窗确认 → 保存 ───
const handleDialogConfirm = async () => {
  await dialogFormRef.value?.validate();
  dialogLoading.value = true;
  try {
    const payload: Record<string, any> = {
      parentId: dialogForm.value.parentId || null,
      dictCode: dialogForm.value.dictCode,
      dictName: dialogForm.value.dictName,
      dictDesc: dialogForm.value.dictDesc || "",
      sortNo: dialogForm.value.sortNo || 0,
    };
    if (dialogMode.value === "edit") {
      payload.tid = dialogEditTid.value;
    }
    await $common.post("/sym/dictSaveOrUpdate", payload);
    $message.success(dialogMode.value === "add" ? "新增成功" : "保存成功");
    dialogVisible.value = false;
    refreshTree();
  } finally {
    dialogLoading.value = false;
  }
};

// ========================================================
//  fetchData — 表格数据接口（由 DataTable 组件调用）
// ========================================================
const fetchData = async ({ pageNo: pageNum, pageSize }: { pageNo: number; pageSize: number }) => {
  const conditions: any[] = [];

  // 树节点筛选：收集叶子节点 value 作为 category 过滤条件
  if (state.leftSelectNode?.value) {
    const leafValues: string[] = [];
    $common.deepTree(state.leftSelectNode, (node) => {
      if (node.value) leafValues.push(node.value);
    });
    if (leafValues.length > 0) {
      conditions.push({ field: "dataSourceType", value: leafValues, type: "in" });
    }
  }
  // 从 ODS 数据源卡片进入时，仅展示该数据源已登记的表。
  const datasourceId = String(router.currentRoute.value.query.datasourceId || "").trim();
  if (datasourceId) {
    conditions.push({ field: "datasourceId", value: datasourceId, type: "match" });
  }


  // 关键字搜索
  if (state.keyword) {
    conditions.push({ field: "catalogName", value: state.keyword, type: "like" });
  }

  const { list = [], total } = await $common.post("/dwm/center/ods-list", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions,
  });
  return { list, total };
};

// ─── 初始化 ───
onMounted(async () => {
  await fetchTreeData();
  const rootNode = treeData.value[0];
  if (router.currentRoute.value.query.layerCode) {
    handleAction(
      "node-click",
      rootNode.children.find((el) => el.value == router.currentRoute.value.query.layerCode) ||
        rootNode
    );
  } else if (rootNode) {
    handleAction("node-click", rootNode);
  }
});
</script>

<style scoped lang="scss">
.ods-detail {
  .left-panel {
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;
  }
  .left-header {
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
  .right-header {
    display: flex;
    align-items: end;
    height: 16px;
    gap: 10px;
    .header-name {
      line-height: 1;
      font-size: 16px;
      font-weight: 700;
      color: #464c64;
    }
  }
  .tree-container {
    height: calc(100% - 53px);
    .task-tree-container {
      height: 100%;
      :deep(.u-tree-search) {
        margin-right: 14px;
      }
      :deep(.el-tree) {
        padding-right: 14px;
      }
      :deep(.el-tree-node__content) {
        flex: 1;
        overflow: hidden;
      }
    }
  }
}

// ─── 树节点自定义 ───
.tree-node-item {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;

  .tree-node-more {
    flex-shrink: 0;
    opacity: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 4px;
    color: #909399;
    cursor: pointer;
    transition:
      opacity 0.15s,
      background-color 0.15s;
  }
}

:deep(.el-tree-node__content:hover) {
  .tree-node-more {
    opacity: 1;
  }
}
</style>
