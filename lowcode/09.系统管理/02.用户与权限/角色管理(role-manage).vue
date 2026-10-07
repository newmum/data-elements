<template>
  <div class="role-manage-content card-container flex flex-col px-5 pt-5 pb-2">
    <DataTable
      ref="tableRef"
      :columns="tableColumns"
      :data="fetchRoleData"
      :show-index="true"
      :immediate="true"
      style="height: 100%"
    >
      <template #toolbar>
        <div class="flex justify-between items-center w-full">
          <div class="flex">
            <el-button type="primary" :icon="'el-icon-Plus'" @click="handleAdd">新增角色</el-button>
          </div>
          <div class="flex items-center gap-2">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索角色名称/编码"
              clearable
              style="width: 220px"
              @keyup.enter="refreshTableData"
              @clear="refreshTableData"
            >
              <template #suffix>
                <Icon class="role-search-icon" icon="el-icon-Search" @click.stop="refreshTableData" />
              </template>
            </el-input>
          </div>
        </div>
      </template>

      <!-- 角色名称：进入角色信息 -->
      <template #column-name="{ row }">
        <button class="role-link" type="button" @click="handleDetail(row)">{{ row.name }}</button>
      </template>

      <!-- 角色编码列 -->
      <template #column-codeNum="{ row }">
        <el-tag
          :type="row.codeNum === 'admin' ? undefined : 'info'"
          :effect="row.codeNum === 'admin' ? 'dark' : 'plain'"
        >
          {{ row.codeNum }}
        </el-tag>
      </template>

      <!-- 状态开关列 -->
      <template #column-status="{ row }">
        <ElSwitch
          :model-value="row.status"
          :active-value="0"
          :inactive-value="1"
          :loading="row._statusLoading"
          @click.prevent="handleStatusClick(row)"
        ></ElSwitch>
      </template>

      <!-- 权限数列 -->
      <template #column-permCount="{ row }">
        <button class="role-stat-link" type="button" @click="handleDetail(row, 'permissions')">
          <Icon icon="captcha" />
          {{ row.permCount }}
        </button>
      </template>

      <!-- 用户数列 -->
      <template #column-userCount="{ row }">
        <button class="role-stat-link" type="button" @click="handleDetail(row, 'users')">
          <Icon icon="el-icon-User" />
          {{ row.userCount }}
        </button>
      </template>
    </DataTable>

    <!-- 新增/详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      size="600"
      :with-header="false"
      :close-on-click-modal="true"
      :destroy-on-close="true"
      @close="handleDrawerClose"
    >
      <div class="drawer-body">
        <button class="drawer-close" type="button" aria-label="关闭" @click="drawerVisible = false">
          ×
        </button>
        <el-tabs v-model="activeTab" class="role-tabs" @tab-change="handleRoleTabChange">
          <!-- 角色信息 Tab -->
          <el-tab-pane label="角色信息" name="basic">
            <div class="tab-content">
              <JsonForm ref="jsonFormRef" :rules="formRules" :options="formOptions" />
            </div>
          </el-tab-pane>

          <!-- 角色用户 Tab -->
          <el-tab-pane label="角色用户" name="users">
            <div class="tab-content user-tab">
              <!-- 搜索框 -->
              <div class="user-search">
                <el-input
                  v-model="userSearchKeyword"
                  placeholder="请输入搜索内容"
                  :prefix-icon="'el-icon-Search'"
                  clearable
                  @input="onUserSearch"
                  @clear="onUserSearch"
                />
              </div>

              <!-- 用户列表（带复选框，滚动分页加载） -->
              <el-scrollbar ref="userScrollRef" class="user-scroll" @scroll="onUserScroll">
                <div v-loading="userLoading" class="user-list-body">
                  <div
                    v-for="user in userList"
                    :key="user.id"
                    class="user-item"
                    @click="toggleUserSelect(user)"
                  >
                    <el-checkbox
                      :model-value="selectedUserIds.includes(String(user.id))"
                      @change="toggleUserSelect(user)"
                      @click.stop
                    />
                    <span
                      class="user-name"
                      :class="{ 'text-primary': selectedUserIds.includes(String(user.id)) }"
                    >
                      {{ user.realName || user.userName }}
                    </span>
                  </div>
                  <div v-if="userNoMore && userList.length > 0" class="no-more">
                    —没有更多数据了—
                  </div>
                  <div v-if="!userLoading && userList.length === 0" class="empty-users">
                    <empty />
                  </div>
                </div>
              </el-scrollbar>
            </div>
          </el-tab-pane>

          <!-- 角色权限 Tab：完整菜单/按钮树，已关联资源自动勾选 -->
          <el-tab-pane label="角色权限" name="permissions">
            <div class="tab-content permission-tab" v-loading="permissionLoading">
              <div class="permission-tip">勾选后保存即可更新该角色的菜单和操作权限。</div>
              <el-tree
                v-if="permissionTreeData.length"
                ref="permissionTreeRef"
                :data="permissionTreeData"
                node-key="id"
                show-checkbox
                check-on-click-node
                default-expand-all
                :props="{ label: 'label', children: 'children' }"
                @check="handlePermissionCheck"
              >
                <template #default="{ data }">
                  <span class="permission-tree-node">
                    <span>{{ data.label }}</span>
                    <span v-if="data.resourceType === 1" class="permission-tree-node__type">操作</span>
                    <span v-else-if="data.resourceType === 2" class="permission-tree-node__type">分组</span>
                  </span>
                </template>
              </el-tree>
              <el-empty v-else-if="!permissionLoading" description="暂无权限资源" :image-size="96" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <div class="flex justify-end">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saveLoading" @click="handleSave">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { FormUtils } from "@/utils/form";
import type { UColumnProps } from "@/components/data/data-table.vue";

// ===================== 表格 =====================
const tableRef = ref();
const searchKeyword = ref("");

const tableColumns = ref<UColumnProps[]>([
  {
    prop: "name",
    label: "角色名称",
    showOverflowTooltip: true,
  },
  {
    prop: "codeNum",
    label: "角色编码",
    showOverflowTooltip: true,
  },
  {
    prop: "description",
    label: "描述",
    showOverflowTooltip: true,
  },
  {
    prop: "userCount",
    label: "用户数",
    width: 100,
  },
  {
    prop: "permCount",
    label: "权限数",
    width: 100,
  },
  {
    prop: "status",
    label: "状态",
    width: 100,
  },
  {
    prop: "createTime",
    label: "创建时间",
    width: 180,
  },
  {
    prop: "operation",
    label: "操作",
    fixed: "right",
    buttons: [
      {
        label: "删除",
        type: "danger",
        click: (row: any) => handleDelete(row),
      },
    ],
  },
]);

const fetchRoleData = async ({
  pageNo: pageNum,
  pageSize,
}: {
  pageNo: number;
  pageSize: number;
}) => {
  const response = await $common.post("/sym/role/page", {
    name: searchKeyword.value || undefined,
    pageNum,
    pageSize,
  });

  const data = response.data || response;
  const list = data.list || [];
  const total = data.total || 0;

  return {
    list,
    total,
  };
};

const refreshTableData = () => {
  tableRef.value?.refresh(false);
};

const handleDelete = (row: any) => {
  $common.handle({
    url: "/sym/role/delete",
    info: `是否删除角色 "${row.name}" 吗？`,
    data: {
      id: row.id,
    },
    done: () => {
      // 刷新表格数据
      refreshTableData();
    },
  });
};

const handleStatusClick = async (row: any) => {
  // @click.prevent 阻止了开关本身切换，手动计算目标状态：0↔1 互切
  const targetStatus = row.status === 0 ? 1 : 0;
  row._statusLoading = true;
  try {
    await $common.post("/sym/role/saveOrUpdate", {
      id: row.id,
      roleName: row.name,
      codeNum: row.codeNum,
      status: targetStatus,
      description: row.description || "",
      userIds: row.userIds || "",
    });
    row.status = targetStatus;
    ElMessage.success(`已${targetStatus === 0 ? "启用" : "禁用"}`);
  } catch (e: any) {
    // 失败时开关视觉状态不变（click 已被 prevent），无需回滚
    if (e?.message) ElMessage.error(e.message);
  } finally {
    row._statusLoading = false;
  }
};

// ===================== 抽屉 =====================
const drawerVisible = ref(false);
const drawerType = ref<"add" | "detail">("add");
const activeTab = ref("basic");
const saveLoading = ref(false);
const jsonFormRef = ref<any>(null);
const currentRoleId = ref<string>("");
const permissionTreeRef = ref<any>(null);
const permissionTreeData = ref<any[]>([]);
const selectedResourceIds = ref<string[]>([]);
const permissionLoading = ref(false);
// 仅当用户实际打开过权限页签后才提交资源关联，避免编辑基本信息时误清空原有权限。
const permissionsLoaded = ref(false);

const handleAdd = () => {
  drawerType.value = "add";
  activeTab.value = "basic";
  currentRoleId.value = "";
  selectedUserIds.value = [];
  selectedResourceIds.value = [];
  permissionTreeData.value = [];
  permissionsLoaded.value = false;
  drawerVisible.value = true;
  loadUsers(true);
  nextTick(() => {
    jsonFormRef.value?.clearValue?.();
    jsonFormRef.value?.setValue?.({ status: 0 });
  });
};

const handleDetail = (row: any, tab: "basic" | "users" | "permissions" = "basic") => {
  drawerType.value = "detail";
  activeTab.value = tab;
  currentRoleId.value = row.id || "";
  // 解析已关联的用户ID（接口返回逗号分割字符串）
  selectedUserIds.value = row.userIds
    ? row.userIds.split(",").filter((id: string) => id !== "")
    : [];
  selectedResourceIds.value = [];
  permissionTreeData.value = [];
  permissionsLoaded.value = false;
  drawerVisible.value = true;
  loadUsers(true);
  nextTick(() => {
    jsonFormRef.value?.setValue?.({
      name: row.name,
      codeNum: row.codeNum,
      status: row.status,
      description: row.description,
    });
    if (tab === "permissions") loadRolePermissions();
  });
};

const handleDrawerClose = () => {
  drawerVisible.value = false;
  userSearchKeyword.value = "";
  activeTab.value = "basic";
  permissionTreeData.value = [];
  selectedResourceIds.value = [];
  permissionsLoaded.value = false;
};

const handleSave = async () => {
  try {
    saveLoading.value = true;

    // 1. 获取表单数据
    const formData = await jsonFormRef.value?.getFormData(true);
    if (!formData) return;

    // 2. 构建请求体
    const payload: Record<string, any> = {
      roleName: formData.name,
      codeNum: formData.codeNum,
      status: formData.status,
      description: formData.description || "",
      userIds: selectedUserIds.value.join(","),
    };

    // 编辑模式传 id
    if (currentRoleId.value) {
      payload.id = currentRoleId.value;
    }

    // 3. 调用保存接口
    const savedRole = await $common.post("/sym/role/saveOrUpdate", payload);
    const savedRoleData = savedRole?.data || savedRole || {};
    const roleId = `${savedRoleData.id || currentRoleId.value || ""}`;
    if (!roleId) {
      throw new Error("角色保存后未返回角色ID");
    }

    // 角色信息和用户关联保存成功后，仅在用户查看过权限页签时保存勾选结果。
    // 未查看权限页签的编辑不应覆盖既有的角色权限。
    if (permissionsLoaded.value) {
      await $common.post("/sym/auth/saveRoleResource", {
        roleId,
        resourceIds: selectedResourceIds.value,
      });
    }
    currentRoleId.value = roleId;

    ElMessage.success(drawerType.value === "add" ? "创建成功" : "保存成功");
    drawerVisible.value = false;
    refreshTableData();
  } catch (e: any) {
    console.error(e);
  } finally {
    saveLoading.value = false;
  }
};

// ===================== 角色权限（菜单树） =====================
const buildPermissionTree = (resources: any[]) => {
  const nodeMap = new Map<string, any>();
  const roots: any[] = [];
  for (const resource of resources || []) {
    const id = `${resource.id}`;
    nodeMap.set(id, {
      ...resource,
      id,
      label: resource.name || resource.sno || id,
      children: [],
    });
  }
  for (const resource of resources || []) {
    const id = `${resource.id}`;
    const node = nodeMap.get(id);
    const parentId = resource.parentId == null ? "ROOT" : `${resource.parentId}`;
    const parent = nodeMap.get(parentId);
    if (parent && parentId !== id) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortTree = (nodes: any[]) => {
    nodes.sort((a, b) => Number(a.sortNum || 0) - Number(b.sortNum || 0));
    nodes.forEach((node) => sortTree(node.children));
  };
  sortTree(roots);
  return roots;
};

const loadRolePermissions = async () => {
  if (permissionLoading.value) return;
  try {
    permissionLoading.value = true;
    permissionsLoaded.value = false;
    const menuResponse = await $common.post("/sym/menu/all", {});
    const menuData = menuResponse?.data || menuResponse || [];
    permissionTreeData.value = buildPermissionTree(Array.isArray(menuData) ? menuData : menuData.list || []);

    if (currentRoleId.value) {
      const selectedResponse = await $common.post("/sym/auth/getRoleResource", {
        roleId: currentRoleId.value,
      });
      const selectedData = selectedResponse?.data || selectedResponse || {};
      selectedResourceIds.value = (selectedData.resourceIds || []).map((id: any) => `${id}`);
    } else {
      selectedResourceIds.value = [];
    }
    await nextTick();
    permissionTreeRef.value?.setCheckedKeys(selectedResourceIds.value);
    permissionsLoaded.value = true;
  } catch (e: any) {
    console.error("获取角色权限失败:", e);
    ElMessage.error("获取角色权限失败");
    permissionTreeData.value = [];
    selectedResourceIds.value = [];
  } finally {
    permissionLoading.value = false;
  }
};

const handlePermissionCheck = (_node: any, checked: any) => {
  selectedResourceIds.value = (checked?.checkedKeys || []).map((id: any) => `${id}`);
};

const handleRoleTabChange = (tab: string | number) => {
  if (tab === "permissions") loadRolePermissions();
};

// ===================== 表单配置 =====================
const formOptions = {
  form: {
    labelWidth: "90px",
    colon: true,
  },
};

const formRules = FormUtils.fixJson([
  {
    field: "name",
    title: "角色名称",
    type: "input",
    $required: true,
    props: {
      placeholder: "请输入角色名称",
      maxlength: 50,
      showWordLimit: true,
    },
  },
  {
    field: "codeNum",
    title: "角色编码",
    type: "input",
    $required: true,
    props: {
      placeholder: "请输入角色编码",
      maxlength: 50,
      showWordLimit: true,
    },
  },
  {
    field: "status",
    title: "启用状态",
    type: "switch",
    value: 0,
    props: {
      activeValue: 0,
      inactiveValue: 1,
    },
    options: [
      { label: "启用", value: 0 },
      { label: "禁用", value: 1 },
    ],
  },
  {
    field: "description",
    title: "角色描述",
    type: "input",
    props: {
      type: "textarea",
      placeholder: "请输入角色描述",
      maxlength: 200,
      rows: 4,
      showWordLimit: true,
    },
  },
]);

// ===================== 角色用户（滚动分页加载） =====================
const PAGE_SIZE = 20;
const userSearchKeyword = ref("");
const selectedUserIds = ref<string[]>([]);
const userLoading = ref(false);
const userList = ref<any[]>([]);
const userPage = ref(1);
const userNoMore = ref(false);
const userScrollRef = ref<any>();

// 分页加载用户列表
const loadUsers = async (reset = false) => {
  if (userLoading.value) return;
  if (!reset && userNoMore.value) return;
  if (reset) {
    userPage.value = 1;
    userList.value = [];
    userNoMore.value = false;
  }
  userLoading.value = true;
  try {
    const response = await $common.post("/sym/user/page", {
      realName: userSearchKeyword.value || undefined,
      pageNum: userPage.value,
      pageSize: PAGE_SIZE,
    });
    const data = response.data || response;
    const list = data.list || [];
    userList.value = reset ? list : [...userList.value, ...list];
    userNoMore.value = list.length < PAGE_SIZE;
    userPage.value++;
  } finally {
    userLoading.value = false;
  }
};

// 搜索时重置分页
const onUserSearch = () => loadUsers(true);

// 滚动触底加载下一页
const onUserScroll = ({ scrollTop }: { scrollTop: number }) => {
  const wrapEl = userScrollRef.value?.wrapRef;
  if (!wrapEl) return;
  if (wrapEl.scrollHeight - scrollTop - wrapEl.clientHeight < 40) {
    loadUsers();
  }
};

const toggleUserSelect = (user: { id: string | number; userName: string }) => {
  const uid = String(user.id);
  const idx = selectedUserIds.value.indexOf(uid);
  if (idx === -1) {
    selectedUserIds.value.push(uid);
  } else {
    selectedUserIds.value.splice(idx, 1);
  }
};
</script>

<style scoped lang="scss">
.role-manage-content {
  width: 100%;
  height: 100%;

  .role-link,
  .role-stat-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 0;
    background: transparent;
    color: #1677ff;
    cursor: pointer;
    font: inherit;
    padding: 0;

    &:hover,
    &:focus-visible {
      color: #0958d9;
      text-decoration: underline;
      text-underline-offset: 3px;
      outline: none;
    }
  }

  .role-search-icon {
    color: #1b67f8;
    cursor: pointer;

    &:hover {
      color: #0b54d8;
    }
  }
}

.drawer-body {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 4px;

  .drawer-close {
    position: absolute;
    z-index: 1;
    top: 2px;
    right: 0;
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 4px;
    background: transparent;
    color: #606266;
    cursor: pointer;
    font-size: 24px;
    line-height: 32px;

    &:hover {
      background: #f5f7fa;
      color: #1b67f8;
    }
  }

  .role-tabs {
    flex: 1;
    display: flex;
    flex-direction: column;
    height: 100%;

    :deep(.el-tabs__header) {
      padding-right: 44px;
    }

    :deep(.el-tabs__content) {
      flex: 1;
      overflow: hidden;
    }

    :deep(.el-tab-pane) {
      height: 100%;
    }
  }

  .tab-content {
    padding: 16px 0;
    height: 100%;
    overflow-y: auto;
  }

  .user-tab {
    padding: 8px 0;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .user-search {
      flex-shrink: 0;
    }

    .user-scroll {
      flex: 1;
    }

    .user-list-body {
      padding-bottom: 8px;

      .user-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 16px;
        cursor: pointer;
        border-bottom: 1px solid #f0f0f0;
        transition: background-color 0.2s;

        &:last-child {
          border-bottom: none;
        }

        &:hover {
          background-color: #f5f7fa;
        }

        .user-name {
          font-size: 14px;
          color: #303133;

          &.text-primary {
            color: #1b67f8;
          }
        }
      }

      .empty-users {
        height: 400px;
      }

      .no-more {
        text-align: center;
        color: #909399;
        font-size: 12px;
        padding: 10px 0 16px;
      }
    }
  }

  .permission-tab {
    padding: 8px 0;

    .permission-tip {
      margin: 0 0 10px;
      color: #909399;
      font-size: 13px;
      line-height: 20px;
    }

    :deep(.el-tree-node__content) {
      height: 34px;
    }

    .permission-tree-node {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex: 1;
      min-width: 0;

      &__type {
        margin-right: 12px;
        color: #909399;
        font-size: 12px;
      }
    }
  }
}
</style>
