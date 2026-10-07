<template>
  <div class="auth-perm-tree h-full flex flex-col">
    <DataTable
      ref="tableRef"
      row-key="id"
      :columns="tableColumns"
      :data="permTreeData"
      :loading="loading"
      :show-selection="true"
      :show-page="false"
      :immediate="true"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren', checkStrictly: true }"
      style="height: 100%"
      @selection-change="handleSelectionChange"
    >
      <template #column-sno="{ row }">
        <el-tag v-if="row.sno" type="info" effect="plain">
          {{ row.sno }}
        </el-tag>
      </template>
      <template #column-resourceType="{ row }">
        <dict-label :model-value="row.resourceType" :options="resourceTypeOptions" />
      </template>
      <template #column-name="{ row }">
        <span :class="{ 'is-disabled': row.status === 1 }">
          {{ row.name }}
        </span>
      </template>
      <template #column-status="{ row }">
        <el-switch
          v-model="row.status"
          :active-value="0"
          :inactive-value="1"
          :loading="toggleLoadingId === row.id"
          :disabled="toggleLoadingId !== null"
          @change="(val) => handleStatusToggle(row, val)"
        />
      </template>
    </DataTable>

    <!-- 编辑 / 新增子级 抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="500"
      :close-on-click-modal="true"
      :destroy-on-close="true"
    >
      <div class="px-4">
        <JsonForm ref="formRef" :rules="formRules" :options="formOptions" />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saveLoading" @click="handleDrawerSave">
            确定
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 排序弹框 -->
    <el-dialog v-model="sortDialogVisible" title="调整排序" width="480px" destroy-on-close>
      <div class="sort-dialog-content">
        <div class="sort-tip mb-3 text-sm text-gray-500">拖拽调整顺序，排序号将自动更新</div>
        <VueDraggable v-model="sortItems" :animation="200" handle=".sort-handle" class="sort-list">
          <div v-for="(item, index) in sortItems" :key="item.id" class="sort-item">
            <Icon class="sort-handle cursor-grab" icon="el-icon-rank" />
            <span class="sort-index">{{ index + 1 }}</span>
            <span class="sort-name">{{ item.name }}</span>
            <span class="sort-current text-gray-400 text-xs ml-auto">
              当前排序: {{ item.sortNum ?? "-" }}
            </span>
          </div>
        </VueDraggable>
      </div>
      <template #footer>
        <el-button @click="sortDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="sortSaving" @click="handleSortSave">
          <template #icon><Icon icon="save" /></template>
          保存排序
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, reactive } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { FormUtils } from "@/utils/form";
import type { UColumnProps } from "@/components/data/data-table.vue";
import { isExternal } from "@/utils";
import { VueDraggable } from "vue-draggable-plus";

// 接口地址
const API = {
  MENU_LIST: "/sym/menu/all",
  SAVE_OR_UPDATE: "/sym/menu/saveOrUpdate",
  DELETE: "/sym/menu/delete",
  TOGGLE_STATUS: "/sym/menu/toggleStatus",
  SORT: "/sym/menu/sort",
  GET_ROLE_SCOPE: "/sym/auth/getRoleDataScope",
  SAVE_ROLE_SCOPE: "/sym/auth/saveRoleDataScope",
};

// 菜单项接口（匹配 API 返回）
interface MenuItem {
  id: string;
  name: string;
  parentId: string;
  staticIcon: string | null;
  dynamicImg: string | null;
  status: number;
  resourceType: number;
  sno: string;
  urlType: number;
  url: string | null;
  methodName: string | null;
  createId: string;
  updateId: string;
  deleted: number;
  createTime: string;
  updateTime: string;
  sortNum: number;
  code: string | null;
  openType: number;
  topId: string;
  appId: string;
}

// 树节点接口
interface PermItem extends MenuItem {
  children?: PermItem[];
  hasChildren?: boolean;
}

interface SubjectInfo {
  type: "role" | "org" | "user";
  id: string;
  name: string;
}

const props = defineProps<{
  subject: SubjectInfo | null;
}>();

const emit = defineEmits<{
  (e: "ready"): void;
}>();

// Mock: 各授权对象已有权限 ID
const mockAssignedPerms: Record<string, string[]> = {
  "role-1995679968881147906": ["1995679026924355585", "1995780880303783938"],
};
const resourceTypeOptions = [
  { label: "菜单", value: 0, tagType: "primary" },
  { label: "按钮", value: 1, tagType: "warning" },
  { label: "分组菜单", value: 2, tagType: "info" },
  { label: "外链", value: 3, tagType: "success" },
];

// ===== 状态 =====
const permTreeData = ref<PermItem[]>([]);
const tableRef = ref<any>();
const selectedPerms = ref<PermItem[]>([]);
const loading = ref(true);
const checkedIds = ref<Set<string>>(new Set());
const syncingSelection = ref(false);

// 排序弹框相关
const sortDialogVisible = ref(false);
const sortItems = ref<any[]>([]);
const sortSaving = ref(false);
const sortParentId = ref<string>("");
const deleteLoading = ref(false);
const toggleLoadingId = ref<string | null>(null);

// ===== 工具函数 =====
// 将扁平列表转换为树结构
const buildTree = (list: MenuItem[]): PermItem[] => {
  const map = new Map<string, PermItem>();
  const roots: PermItem[] = [];

  // 先创建所有节点的映射
  list.forEach((item) => {
    map.set(item.id, { ...item, children: [] });
  });

  // 构建树结构
  list.forEach((item) => {
    const node = map.get(item.id)!;
    if (item.parentId === "ROOT" || !map.has(item.parentId)) {
      // 顶级节点
      roots.push(node);
    } else {
      // 子节点，挂到父节点下
      const parent = map.get(item.parentId);
      if (parent) {
        if (!parent.children) parent.children = [];
        parent.children.push(node);
      }
    }
  });

  // 设置 hasChildren 标志
  const setHasChildren = (nodes: PermItem[]) => {
    nodes.forEach((node) => {
      node.hasChildren = (node.children?.length ?? 0) > 0;
      if (node.children?.length) setHasChildren(node.children);
    });
  };
  setHasChildren(roots);

  return roots;
};

// 加载菜单树
const loadMenuTree = async () => {
  // 在替换数据前保存当前勾选状态，避免 el-table 清空选择时丢失
  const savedIds = new Set(checkedIds.value);
  syncingSelection.value = true;
  loading.value = true;
  try {
    const res: any = await $common.post(API.MENU_LIST);

    if (Array.isArray(res)) {
      permTreeData.value = buildTree(res);
    } else if (res?.list && Array.isArray(res.list)) {
      permTreeData.value = buildTree(res.list);
    } else {
      console.warn("菜单接口返回格式异常：", res);
      permTreeData.value = [];
    }
  } catch (error) {
    console.error("加载菜单树失败：", error);
    ElMessage.error("加载菜单树失败");
    permTreeData.value = [];
  } finally {
    loading.value = false;
    await nextTick();
    applyCheckedIds(savedIds);
    emit("ready");
  }
};

// 切换状态
const handleStatusToggle = async (row: any, val: number) => {
  if (!row.id) return;
  const newStatus = val;

  toggleLoadingId.value = row.id;
  try {
    await $common.post(API.TOGGLE_STATUS, {
      id: row.id,
      status: newStatus,
    });

    ElMessage.success(newStatus === 0 ? "启用成功" : "禁用成功");
    await loadMenuTree();
  } catch (error) {
    console.error("状态切换失败：", error);
    ElMessage.error("操作失败");
    // 恢复开关状态
    row.status = newStatus === 0 ? 1 : 0;
  } finally {
    toggleLoadingId.value = null;
  }
};

// 打开排序弹框：查找当前节点的所有同级节点
const handleOpenSortDialog = (row: any) => {
  const parentId = row.parentId || "ROOT";
  sortParentId.value = parentId;

  // 从 permTreeData 中查找同级节点
  const findSiblings = (nodes: any[], targetParentId: string): any[] => {
    // 如果是根级，直接返回顶层节点
    if (targetParentId === "ROOT") {
      return nodes.map((n) => ({ id: n.id, name: n.name, sortNum: n.sortNum }));
    }
    // 在子节点中查找
    for (const node of nodes) {
      if (node.id === targetParentId) {
        return (node.children || []).map((n: any) => ({
          id: n.id,
          name: n.name,
          sortNum: n.sortNum,
        }));
      }
      if (node.children?.length) {
        const result = findSiblings(node.children, targetParentId);
        if (result.length) return result;
      }
    }
    return [];
  };

  sortItems.value = findSiblings(permTreeData.value, parentId);
  if (sortItems.value.length === 0) {
    ElMessage.warning("未找到同级节点");
    return;
  }
  sortDialogVisible.value = true;
};

// 保存排序
const handleSortSave = async () => {
  try {
    sortSaving.value = true;
    await $common.post(API.SORT, { ids: sortItems.value.map((item) => item.id) });
    ElMessage.success("排序保存成功");
    sortDialogVisible.value = false;
    await loadMenuTree();
  } catch (error) {
    console.error("排序保存失败:", error);
    ElMessage.error("排序保存失败");
  } finally {
    sortSaving.value = false;
  }
};

const flattenTree = (items: PermItem[]): PermItem[] => {
  const result: PermItem[] = [];
  const traverse = (list: PermItem[]) => {
    for (const item of list) {
      result.push(item);
      if (item.children?.length) traverse(item.children);
    }
  };
  traverse(items);
  return result;
};

const findById = (tree: PermItem[], id: string): PermItem | null => {
  for (const item of tree) {
    if (item.id === id) return item;
    if (item.children?.length) {
      const found = findById(item.children, id);
      if (found) return found;
    }
  }
  return null;
};

const deleteById = (tree: PermItem[], id: string): boolean => {
  for (let i = 0; i < tree.length; i++) {
    if (tree[i].id === id) {
      tree.splice(i, 1);
      return true;
    }
    if (tree[i].children?.length && deleteById(tree[i].children!, id)) return true;
  }
  return false;
};

// 从树中取出（摘除）指定节点并返回
const extractById = (tree: PermItem[], id: string): PermItem | null => {
  for (let i = 0; i < tree.length; i++) {
    if (tree[i].id === id) return tree.splice(i, 1)[0];
    if (tree[i].children?.length) {
      const found = extractById(tree[i].children!, id);
      if (found) return found;
    }
  }
  return null;
};

// ===== 监听授权对象变化，自动预选权限 =====
watch(
  () => props.subject,
  async (subject) => {
    tableRef.value?.$table?.value?.clearSelection?.();
    if (!subject) return;
    const key = `${subject.type}-${subject.id}`;
    const assignedIds = mockAssignedPerms[key] || [];
    if (!assignedIds.length) return;
    await nextTick();
    const allRows = flattenTree(permTreeData.value);
    allRows
      .filter((row) => assignedIds.includes(row.id))
      .forEach((row) => tableRef.value?.$table?.value?.toggleRowSelection?.(row, true));
  }
);

// ===== 初始化 =====
onMounted(() => {
  loadMenuTree();
});

// ===== 表格列配置 =====
const tableColumns = ref<UColumnProps[]>([
  {
    prop: "name",
    label: "菜单名称",
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: "sno",
    label: "页面组件/权限编码",
  },
  {
    prop: "resourceType",
    label: "类型",
    width: 120,
    align: "center",
  },
  {
    prop: "url",
    label: "页面路径",
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: "status",
    label: "显示",
    align: "center",
  },
  {
    prop: "operation",
    label: "操作",
    width: 80,
    fixed: "right",
    buttons: [
      {
        label: "操作",
        type: "primary",
        link: true,
        isLabel: true,
        dropdown: true,
        trigger: "hover",
        dropdownItems: [
          { label: "编辑菜单", icon: "el-icon-Edit", click: (row: any) => handleEdit(row) },
          { label: "新增子级", icon: "el-icon-Plus", click: (row: any) => handleAddChild(row) },
          {
            label: "调整排序",
            icon: "el-icon-Sort",
            click: (row: any) => handleOpenSortDialog(row),
          },
          {
            label: "删除菜单",
            icon: "el-icon-Delete",
            color: "#f56c6c",
            divider: true,
            click: (row: any) => handleDelete(row),
          },
        ],
      } as any,
    ],
  },
]);

const getDescendants = (row: PermItem): PermItem[] => {
  const result: PermItem[] = [];
  const visit = (nodes: PermItem[] = []) => nodes.forEach((node) => {
    result.push(node);
    visit(node.children || []);
  });
  visit(row.children || []);
  return result;
};

const getAncestors = (row: PermItem): PermItem[] => {
  const result: PermItem[] = [];
  let parentId = row.parentId;
  while (parentId && parentId !== "ROOT") {
    const parent = findById(permTreeData.value, parentId);
    if (!parent) break;
    result.push(parent);
    parentId = parent.parentId;
  }
  return result;
};

const applyCheckedIds = (ids: Set<string>) => {
  const table = tableRef.value?.$table;
  if (!table) return;
  syncingSelection.value = true;
  table.clearSelection();
  flattenTree(permTreeData.value).forEach((row) => {
    if (ids.has(row.id)) table.toggleRowSelection(row, true);
  });
  checkedIds.value = new Set(ids);
  selectedPerms.value = flattenTree(permTreeData.value).filter((row) => ids.has(row.id));
  nextTick(() => { syncingSelection.value = false; });
};

const handleSelectionChange = (selection: PermItem[]) => {
  if (syncingSelection.value) return;
  const nextIds = new Set(selection.map((row) => row.id));
  const newlyChecked = selection.filter((row) => !checkedIds.value.has(row.id));
  const newlyUnchecked = flattenTree(permTreeData.value).filter(
    (row) => checkedIds.value.has(row.id) && !nextIds.has(row.id)
  );

  newlyChecked.forEach((row) => {
    nextIds.add(row.id);
    getDescendants(row).forEach((node) => nextIds.add(node.id));
    getAncestors(row).forEach((node) => nextIds.add(node.id));
  });
  newlyUnchecked.forEach((row) => {
    nextIds.delete(row.id);
    getDescendants(row).forEach((node) => nextIds.delete(node.id));
  });
  applyCheckedIds(nextIds);
};

// ===== 抽屉（编辑 / 新增子级） =====
const drawerVisible = ref(false);
const drawerMode = ref<"edit" | "addChild">("edit");
const currentRow = ref<PermItem | null>(null);
const formRef = ref<any>();
const saveLoading = ref(false);
const roleScopeLoading = ref(false);
const roleDataScope = ref<"ALL" | "ORG" | "OWNER">("OWNER");

const drawerTitle = computed(() => (drawerMode.value === "edit" ? "编辑菜单" : "新增子级"));
const menuScopeCode = (row?: PermItem | null) => row?.id ? `MENU:${String(row.id).trim().toUpperCase()}` : "";
const showRoleDataScope = computed(() =>
  drawerMode.value === "edit" && props.subject?.type === "role" && currentRow.value?.resourceType === 0
);

const loadRoleDataScope = async (row: PermItem) => {
  if (props.subject?.type !== "role" || row.resourceType !== 0) return;
  roleScopeLoading.value = true;
  roleDataScope.value = "OWNER";
  try {
    const data: any = await $common.post(API.GET_ROLE_SCOPE, { roleId: props.subject.id, resourceCode: menuScopeCode(row) });
    roleDataScope.value = data?.scope || "OWNER";
    formRef.value?.setValue({ roleDataScope: roleDataScope.value });
  } catch (error) {
    console.error("获取菜单数据范围失败：", error);
    ElMessage.warning("获取菜单数据范围失败，请稍后重试");
  } finally {
    roleScopeLoading.value = false;
  }
};

const formOptions = { form: { labelWidth: "90px", colon: true } };

// 父级菜单树：只保留 resourceType===0（菜单）的节点
const parentMenuTree = computed(() => {
  const filterMenus = (items: PermItem[]): any[] => {
    const result: any[] = [];
    for (const item of items) {
      if (item.resourceType !== 0) continue;
      const node: any = { id: item.id, name: item.name };
      const filteredChildren = filterMenus(item.children ?? []);
      if (filteredChildren.length) node.children = filteredChildren;
      result.push(node);
    }
    return result;
  };

  return [{ id: "ROOT", name: "顶级菜单", children: filterMenus(permTreeData.value) }];
});

const formRules = computed(() =>
  FormUtils.fixJson([
    {
      type: "form-text",
      field: "id",
      hidden: true,
    },
    {
      field: "parentId",
      title: "父级菜单",
      type: "el-tree-select",
      $required: true,
      props: {
        data: parentMenuTree.value,
        props: { label: "name", value: "id", children: "children" },
        placeholder: "请选择父级菜单",
        checkStrictly: true,
        renderAfterExpand: false,
        defaultExpandAll: true,
        style: "width: 100%",
      },
    },
    { field: "name", title: "菜单名称", type: "input", $required: true, props: { maxlength: 50 } },
    {
      field: "resourceType",
      title: "类型",
      type: "radio",
      $required: true,
      value: 0,
      options: resourceTypeOptions,
      control: [
        // 菜单
        {
          value: 0,
          condition: "==",
          rule: ["url", "sno", "sortNum", "staticIcon"],
        },
        // 按钮
        {
          value: 1,
          condition: "==",
          rule: ["sno", "sortNum"],
        },
        // 分组菜单, url的默认值为 __group__
        {
          value: 2,
          condition: "==",
          rule: ["sortNum"],
        },
        // 外链
        {
          value: 3,
          condition: "==",
          rule: ["url", "openType", "sortNum", "staticIcon"],
        },
      ],
    },
    {
      field: "openType",
      title: "打开方式",
      type: "dict-select",
      value: 0,
      props: {
        type: "radio",
        options: [
          { label: "内嵌打开", value: 0 },
          { label: "iframe打开", value: 1 },
          { label: "打开新页面", value: 2 },
        ],
      },
      link: ["resourceType"],
      update: (value: any, rule: any, api: any, { origin }: any) => {
        if (origin == "link") {
          // 新增时，类型为外链时，打开方式默认填打开新页面
          if (!api.getValue("id") && api.getValue("resourceType") == 3) {
            rule.value = 2;
          }
        }
      },
      control: [
        // 外链
        {
          value: 1,
          condition: "==",
          rule: ["code"],
        },
      ],
    },
    {
      field: "url",
      title: "页面路径",
      type: "input",
      props: { placeholder: "如：/users或外部链接，按钮类型为空", maxlength: 200 },
      $required: true,
    },
    {
      field: "code",
      // 作为路由的name，iframe打开时需要，且要唯一
      title: "页面标识",
      type: "input",
      props: { placeholder: "页面标识唯一，如：Path", maxlength: 200 },
      $required: true,
    },
    {
      field: "sno",
      title: "页面组件",
      type: "el-input",
      $required: false,
      props: { maxlength: 100, placeholder: "页面组件名(my-app)或按钮权限名(sym:asset:add)" },
      link: ["resourceType"],
      update: (value: any, rule: any, api: any, { origin }: any) => {
        if (origin === "link") {
          const resourceType = api.getRule("resourceType");
          if (resourceType.value == 0) {
            rule.props.placeholder = "my-app";
            rule.title = "页面组件";
          }
          if (resourceType.value == 1) {
            rule.props.placeholder = "sym:asset:add";
            rule.title = "权限编码";
          }
        }
      },
    },
    {
      field: "dataScopeEnabled",
      title: "启用数据范围",
      type: "radio",
      value: 0,
      options: [
        { label: "启用", value: 1 },
        { label: "不启用", value: 0 },
      ],
    },
    ...(showRoleDataScope.value ? [{
      field: "roleDataScope",
      title: "当前角色的数据范围",
      type: "radio",
      value: roleDataScope.value,
      props: {
        disabled: roleScopeLoading.value,
        options: [
          { label: "全部数据", value: "ALL" },
          { label: "本部门数据", value: "ORG" },
          { label: "本人登记数据", value: "OWNER" },
        ],
      },
    }] : []),
    {
      field: "status",
      title: "显示状态",
      type: "radio",
      $required: true,
      value: 0,
      options: [
        { label: "显示", value: 0 },
        { label: "隐藏", value: 1 },
      ],
    },
    {
      field: "sortNum",
      title: "排序",
      type: "number",
      props: { placeholder: "请输入排序", min: 1 },
      style: "width: 100%",
    },
    {
      field: "staticIcon",
      title: "图标",
      type: "icon-select",
    },
  ])
);

const handleEdit = async (row: PermItem) => {
  currentRow.value = row;
  drawerMode.value = "edit";
  drawerVisible.value = true;
  await nextTick();
  await nextTick();
  formRef.value?.setValue({ ...row });
  await loadRoleDataScope(row);
};

const handleAddChild = (row: PermItem) => {
  currentRow.value = row;
  drawerMode.value = "addChild";
  drawerVisible.value = true;
  nextTick(() => {
    formRef.value?.resetFields();
    // 默认父级为当前行
    formRef.value?.setValue({ parentId: row.id });
  });
};

const handleInsert = () => {
  handleAddChild({ id: "ROOT" } as PermItem);
};

const handleDrawerSave = async () => {
  try {
    saveLoading.value = true;
    await formRef.value?.validate();
    const data = await formRef.value?.getSaveData();
    if (!data) return;
    const selectedScope = data.roleDataScope || roleDataScope.value;
    delete data.roleDataScope;
    const tasks: Promise<any>[] = [$common.post(API.SAVE_OR_UPDATE, data)];
    if (showRoleDataScope.value && props.subject?.id && currentRow.value) {
      tasks.push($common.post(API.SAVE_ROLE_SCOPE, {
        roleId: props.subject.id,
        resourceCode: menuScopeCode(currentRow.value),
        scope: selectedScope,
      }));
    }
    await Promise.all(tasks);
    ElMessage.success("保存成功");
    await loadMenuTree(); // 重新加载树

    drawerVisible.value = false;
  } finally {
    saveLoading.value = false;
  }
};

const handleDelete = (row: PermItem) => {
  ElMessageBox.confirm(`确定要删除 "${row.name}" 吗？`, "删除确认", {
    type: "warning",
    confirmButtonText: "确定",
    cancelButtonText: "取消",
  })
    .then(async () => {
      deleteLoading.value = true;
      try {
        await $common.post(API.DELETE, { id: row.id });
        ElMessage.success("删除成功");
        await loadMenuTree(); // 重新加载树
      } catch (error) {
        console.error("删除失败：", error);
        ElMessage.error("删除失败");
      } finally {
        deleteLoading.value = false;
      }
    })
    .catch(() => {});
};

// ===== 暴露方法给父组件 =====

// 设置权限勾选状态
const setCheckedPermissions = (resourceIds: string[]) => {
  const ids = new Set(resourceIds);
  checkedIds.value = ids;
  nextTick(() => applyCheckedIds(ids));
};

defineExpose({
  getCheckedPermissions: (): string[] => selectedPerms.value.map((p) => p.id),
  setCheckedPermissions,
  handleInsert,
});
</script>

<style scoped lang="scss">
.auth-perm-tree {
  width: 100%;
  height: 100%;
}

:deep(.is-disabled) {
  color: var(--el-text-color-disabled);
}

.sort-list {
  max-height: 400px;
  overflow-y: auto;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
  background: var(--el-bg-color);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  .sort-handle {
    color: var(--el-text-color-placeholder);
    font-size: 18px;
  }

  .sort-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 600;
  }

  .sort-name {
    flex: 1;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }
}
</style>
