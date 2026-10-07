<template>
  <div class="auth-left-panel flex flex-col h-full">
    <!-- 标题 -->
    <div class="panel-header">
      <span class="panel-title">授权角色</span>
    </div>

    <!-- Tabs -->
    <div style="min-height: 300px">
      <el-tabs v-model="activeTab" class="auth-left-tabs flex-1">
        <!-- 角色 Tab -->
        <el-tab-pane label="角色" name="role">
          <div class="tab-content">
            <div class="search-wrap">
              <el-input
                v-model="roleSearch"
                placeholder="请输入搜索内容"
                clearable
                suffix-icon="search"
                class="w-full"
                @input="onRoleSearch"
                @clear="onRoleSearch"
              />
              <!--            <el-button type="primary" link icon="plus" class="btn-plus" />-->
            </div>
            <LoadingState
              v-if="loading && !roleList.length"
              type="tree"
              compact
              class="auth-list-loading"
            />
            <el-scrollbar v-else ref="roleScrollRef" class="list-scroll" @scroll="onRoleScroll">
              <div class="list-body">
                <div
                  v-for="item in roleList"
                  :key="item.id"
                  class="list-item"
                  :class="{ active: isSelected('role', item.id) }"
                  @click="handleSelect('role', item)"
                >
                  <span class="item-name">
                    {{ item.name }}
                  </span>
                  <el-tag :type="item.codeNum === 'admin' ? 'primary' : 'info'" effect="light">
                    {{ item.codeNum }}
                  </el-tag>
                  <!--                <span class="item-count">{{ item.count }}</span>-->
                </div>
                <div v-if="roleNoMore && roleList.length > 0" class="no-more">—没有更多数据了—</div>
                <Empty v-if="!loading && !roleList.length" type="list" description="暂无角色" />
              </div>
            </el-scrollbar>
          </div>
        </el-tab-pane>

        <!-- 组织 Tab -->
        <el-tab-pane label="组织" name="org">
          <div class="tab-content org-tab">
            <UTree
              :loading="loading"
              :data="orgTreeData"
              :search="true"
              :default-expand-all="true"
              :auto-expand-first-node="false"
              node-key="id"
              search-placeholder="请输入搜索内容"
              @node-click="handleOrgNodeClick"
            />
          </div>
        </el-tab-pane>

        <!-- 用户 Tab -->
        <el-tab-pane name="user">
          <template #label>
            <span class="flex items-center gap-1">
              用户
              <em class="tab-count">{{ userTotal }}</em>
            </span>
          </template>
          <div class="tab-content">
            <div class="search-wrap">
              <el-input
                v-model="userSearch"
                placeholder="请输入搜索内容"
                clearable
                suffix-icon="search"
                @input="onUserSearch"
                @clear="onUserSearch"
              />
            </div>
            <LoadingState
              v-if="userLoading && !userList.length"
              type="tree"
              compact
              class="auth-list-loading"
            />
            <el-scrollbar v-else ref="userScrollRef" class="list-scroll" @scroll="onUserScroll">
              <div class="list-body">
                <div
                  v-for="item in userList"
                  :key="item.id"
                  class="list-item"
                  :class="{ active: isSelected('user', item.id) }"
                  @click="handleSelect('user', item)"
                >
                  <span class="item-name">{{ item.name }}</span>
                  <el-tag size="small" type="info" class="shrink-0">{{ item.group }}</el-tag>
                </div>
                <div v-if="userNoMore && userList.length > 0" class="no-more">—没有更多数据了—</div>
                <Empty v-if="!userLoading && !userList.length" type="list" description="暂无用户" />
              </div>
            </el-scrollbar>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from "vue";

interface SubjectInfo {
  type: "role" | "org" | "user";
  id: number;
  name: string;
}

interface RoleItem {
  id: string;
  name: string;
  codeNum: string;
  description: string;
  appId: string;
  sortNum: number;
  status: number;
  createTime: string;
  updateTime: string;
  // 兼容旧字段
  count?: number;
}

interface UserItem {
  id: number;
  name: string;
  group: string;
}

const emit = defineEmits<{
  (e: "select", subject: SubjectInfo): void;
  (e: "ready"): void;
}>();

// ===== 选中状态 =====
const selectedType = ref<"role" | "org" | "user" | "">("");
const selectedId = ref<number>(-1);

const isSelected = (type: string, id: number): boolean =>
  selectedType.value === type && selectedId.value === id;

const handleSelect = (type: "role" | "org" | "user", item: RoleItem | UserItem) => {
  selectedType.value = type;
  selectedId.value = item.id;
  emit("select", { type, id: item.id, name: item.name });
};

// ===== Tabs =====
const activeTab = ref("role");

// ===== 接口配置 =====
const API = {
  ROLE_LIST: "/sym/role/pageRole",
};

const mockUsers: UserItem[] = [
  { id: 1, name: "张建国", group: "个人用户" },
  { id: 2, name: "李明", group: "资产应用者" },
  { id: 3, name: "王芳", group: "资产应用者" },
  { id: 4, name: "陈伟", group: "资产管理者" },
  { id: 5, name: "刘洋", group: "资产管理者" },
  { id: 6, name: "赵静", group: "超级管理员" },
  { id: 7, name: "孙鹏", group: "资产管理者" },
  { id: 8, name: "周强", group: "系统管理员" },
  { id: 9, name: "郑华", group: "资产管理者" },
  { id: 10, name: "李威", group: "系统管理员" },
  { id: 11, name: "张娜", group: "资产应用者" },
  { id: 12, name: "刘霞", group: "区县管理员" },
  { id: 13, name: "陈军", group: "市级管理员" },
  { id: 14, name: "王刚", group: "资产提供者" },
  { id: 15, name: "杨帆", group: "资产管理者" },
  { id: 16, name: "肖博", group: "资产审批员" },
  { id: 17, name: "花娟", group: "个人用户" },
  { id: 18, name: "马龙", group: "系统管理员" },
  { id: 19, name: "邓芳", group: "资产管理者" },
  { id: 20, name: "郭海清", group: "资产应用者" },
  { id: 21, name: "陈小佳", group: "超级管理员" },
  { id: 22, name: "余文达", group: "资产提供者" },
  { id: 23, name: "风盛文", group: "资产应用者" },
  { id: 24, name: "文小敏", group: "资产管理者" },
];

const orgTreeData = ref([
  {
    id: 1,
    name: "数据治理中心",
    children: [
      { id: 11, name: "数据标准部", children: [] },
      { id: 12, name: "数据质量部", children: [] },
      { id: 13, name: "数据服务部", children: [] },
    ],
  },
  {
    id: 2,
    name: "技术支撑中心",
    children: [
      { id: 21, name: "平台运维组", children: [] },
      { id: 22, name: "研发组", children: [] },
    ],
  },
  {
    id: 3,
    name: "业务应用中心",
    children: [
      { id: 31, name: "数据流通部", children: [] },
      { id: 32, name: "数据资产部", children: [] },
    ],
  },
]);

// ===== 用户总数 =====
const userTotal = computed(() => mockUsers.length);

// ===== 角色无限滚动 =====
const PAGE_SIZE = 20;

const roleSearch = ref("");
const loading = ref(false);
const roleList = ref<RoleItem[]>([]);
const rolePage = ref(1);
const roleNoMore = ref(false);
const roleScrollRef = ref<any>();

const loadRoles = async (reset = false, selectFirst?: boolean) => {
  if (loading.value) return;
  if (!reset && roleNoMore.value) return;
  if (reset) {
    rolePage.value = 1;
    roleList.value = [];
    roleNoMore.value = false;
  }
  loading.value = true;
  try {
    const res = await $common.get(API.ROLE_LIST, {
      page: rolePage.value,
      pageSize: PAGE_SIZE,
      name: roleSearch.value || undefined,
    });
    const { list = [], total = 0 } = res || {};
    roleList.value = reset ? list : [...roleList.value, ...list];
    roleNoMore.value = roleList.value.length >= total;
    rolePage.value++;
    if (selectFirst) {
      if (roleList.value.length > 0) {
        handleSelect("role", roleList.value[0]);
      }
    }
  } catch (error) {
    console.error("加载角色列表失败:", error);
  } finally {
    loading.value = false;
  }
};

const onRoleSearch = () => loadRoles(true);

const onRoleScroll = ({ scrollTop }: { scrollTop: number }) => {
  const wrapEl = roleScrollRef.value?.wrapRef;
  if (!wrapEl) return;
  if (wrapEl.scrollHeight - scrollTop - wrapEl.clientHeight < 40) {
    loadRoles();
  }
};

// ===== 用户无限滚动 =====
const userSearch = ref("");
const userLoading = ref(false);
const userList = ref<UserItem[]>([]);
const userPage = ref(1);
const userNoMore = ref(false);
const userScrollRef = ref<any>();

const filteredUsers = computed(() => {
  const kw = userSearch.value.toLowerCase();
  return kw ? mockUsers.filter((u) => u.name.toLowerCase().includes(kw)) : mockUsers;
});

const loadUsers = async (reset = false) => {
  if (userLoading.value) return;
  if (!reset && userNoMore.value) return;
  if (reset) {
    userPage.value = 1;
    userList.value = [];
    userNoMore.value = false;
  }
  userLoading.value = true;
  await new Promise((r) => setTimeout(r, 200));
  const start = (userPage.value - 1) * PAGE_SIZE;
  const chunk = filteredUsers.value.slice(start, start + PAGE_SIZE);
  userList.value = reset ? chunk : [...userList.value, ...chunk];
  userNoMore.value = userList.value.length >= filteredUsers.value.length;
  userPage.value++;
  userLoading.value = false;
};

const onUserSearch = () => loadUsers(true);

const onUserScroll = ({ scrollTop }: { scrollTop: number }) => {
  const wrapEl = userScrollRef.value?.wrapRef;
  if (!wrapEl) return;
  if (wrapEl.scrollHeight - scrollTop - wrapEl.clientHeight < 40) {
    loadUsers();
  }
};

// ===== 组织树节点点击 =====
const handleOrgNodeClick = (node: any) => {
  selectedType.value = "org";
  selectedId.value = node.id;
  emit("select", { type: "org", id: node.id, name: node.name });
};

onMounted(async () => {
  try {
    await loadRoles(false, true);
  } finally {
    emit("ready");
  }
});
</script>

<style scoped lang="scss">
.auth-left-panel {
  width: 100%;
  border-right: 1px solid rgba(5, 5, 5, 0.06);
  min-height: 300px;

  .panel-header {
    padding: 4px 16px 12px 0;
    border-bottom: 1px solid rgba(5, 5, 5, 0.06);

    .panel-title {
      font-size: 15px;
      font-weight: 600;
      color: #464c64;
    }
  }

  .auth-left-tabs {
    height: calc(100% - 41px);

    :deep(.el-tabs__header) {
      padding: 0 16px;
      margin-bottom: 0;
      display: none;
    }

    :deep(.el-tabs__content) {
      height: calc(100% - 40px);
      overflow: hidden;
    }

    :deep(.el-tab-pane) {
      height: 100%;
    }
  }

  .tab-content {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding-top: 8px;

    &.org-tab {
      padding: 8px 16px 0;
    }

    .search-wrap {
      flex-shrink: 0;
      padding: 4px 0 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      margin-right: 8px;
      .btn-plus {
        &:hover {
          background-color: var(--el-color-primary-light-9);
        }
        padding: 8px;
        margin-right: 9px;
      }
    }

    .list-scroll {
      flex: 1;
    }

    .auth-list-loading {
      flex: 1;
      min-height: 0;
      overflow: hidden;
      padding: 12px 8px 8px 0;
    }

    .list-body {
      padding-bottom: 8px;
      min-height: 300px;
    }
  }

  .list-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    cursor: pointer;
    transition: background-color 0.15s;

    &:hover {
      background-color: #f5f7fa;
    }

    &.active {
      background-color: #ecf5ff;

      .item-name {
        color: #1b67f8;
      }
    }

    .item-name {
      font-size: 14px;
      color: #303133;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-right: 8px;
    }

    .item-count {
      min-width: 20px;
      height: 20px;
      padding: 0 6px;
      border-radius: 10px;
      background-color: #e4e7ed;
      color: #606266;
      font-size: 11px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
  }

  .tab-count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    background-color: #e4e7ed;
    border-radius: 9px;
    font-size: 11px;
    color: #606266;
    font-style: normal;
  }

  .no-more {
    text-align: center;
    color: #909399;
    font-size: 12px;
    padding: 10px 0 16px;
  }
}
</style>
