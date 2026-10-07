<template>
  <div class="user-manage-content card-container px-5 pt-5 pb-2">
    <el-splitter style="width: 100%; height: 100%">
      <!-- 左侧组织结构树 -->
      <el-splitter-panel size="280px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>组织架构</h3>
          </div>
          <div class="tree-container">
            <UTree
              ref="treeRef"
              :data="treeData"
              :search="true"
              :expand="false"
              :highlight-current="true"
              :expand-on-click-node="false"
              :search-placeholder="'搜索部门'"
              node-key="value"
              class="tree-container-custom"
              @node-click="handleNodeClick"
            ></UTree>
          </div>
        </div>
      </el-splitter-panel>

      <!-- 右侧用户列表 -->
      <el-splitter-panel>
        <div class="right-panel pl-4">
          <DataTable
            ref="tableRef"
            :columns="tableColumns"
            :data="fetchUserData"
            :show-selection="false"
            :show-index="true"
            :immediate="true"
            style="height: 100%"
          >
            <template #toolbar>
              <div class="flex justify-between items-center w-full">
                <div class="flex">
                  <el-button type="primary" :icon="'el-icon-Plus'" @click="handleAdd">
                    新增用户
                  </el-button>
                </div>
                <div class="flex items-center gap-2">
                  <el-input
                    v-model="searchKeyword"
                    placeholder="搜索用户名/姓名/手机号"
                    clearable
                    class="user-keyword-search"
                    style="width: 220px"
                    @keyup.enter="refreshTableData"
                    @clear="refreshTableData"
                  >
                    <template #suffix>
                      <el-icon class="toolbar-search-icon" title="搜索" @click="refreshTableData"><Search /></el-icon>
                    </template>
                  </el-input>
                  <el-select
                    v-model="filterOnline"
                    placeholder="是否在线"
                    clearable
                    style="width: 110px"
                    @change="refreshTableData"
                  >
                    <el-option label="在线" :value="true" />
                    <el-option label="离线" :value="false" />
                  </el-select>
                </div>
              </div>
            </template>

            <template #column-userName="{ row }">
              <span class="user-account-cell">
                <i class="online-status-dot" :class="row.online ? 'online-status-dot--online' : 'online-status-dot--offline'" />
                <span>{{ row.userName || '-' }}</span>
              </span>
            </template>

            <template #column-realName="{ row }">
              <button type="button" class="user-name-link" @click="handleDetail(row)">{{ row.realName || '-' }}</button>
            </template>

            <!-- 角色列 —— 支持多个角色标签展示 -->
            <template #column-roleNames="{ row }">
              <div v-if="row.roleNames" class="flex flex-wrap gap-1">
                <el-tag
                  v-for="(role, idx) in row.roleNames.split(',').filter((r: string) => r)"
                  :key="idx"
                  :type="role === '超级管理员' ? undefined : 'info'"
                  :effect="role === '超级管理员' ? 'dark' : 'plain'"
                  size="small"
                >
                  {{ role }}
                </el-tag>
              </div>
              <span v-else class="text-gray-400">-</span>
            </template>

            <!-- 状态开关列 —— 参考 role-manage.vue 模式 -->
            <template #column-status="{ row }">
              <ElSwitch
                :model-value="row.status"
                :active-value="0"
                :inactive-value="1"
                :loading="row._statusLoading"
                @click.prevent="handleStatusClick(row)"
              ></ElSwitch>
            </template>
          </DataTable>
        </div>
      </el-splitter-panel>
    </el-splitter>

    <!-- 用户新增/详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      size="700"
      :close-on-click-modal="true"
      @close="handleDrawerClose"
    >
      <template #header>
        <el-tabs v-model="drawerTab" class="drawer-tabs" @tab-change="handleDrawerTabChange">
          <el-tab-pane label="用户详情" name="detail" />
          <el-tab-pane label="用户角色" name="roles" :disabled="!currentUserId" />
        </el-tabs>
      </template>
      <div class="px-4">
        <!--
          Keep the form mounted while browsing roles. JsonForm owns the entered
          values internally, so a v-if here used to destroy the detail state
          and recreate an empty form when the user returned to this tab.
        -->
        <JsonForm v-show="drawerTab === 'detail'" ref="jsonFormRef" :rules="formRules" :options="formOptions" />

        <section v-if="drawerTab === 'roles'" class="user-roles-panel" v-loading="roleLoading">
          <div class="role-panel-tip">勾选后保存即可更新该用户关联的角色。</div>
          <el-checkbox-group v-if="roleOptions.length" v-model="selectedRoleIds" class="role-checkbox-list">
            <el-checkbox v-for="role in roleOptions" :key="role.id" :label="role.id" border class="role-checkbox">
              <span class="role-checkbox__name">{{ role.name }}</span>
              <span v-if="role.codeNum" class="role-checkbox__code">{{ role.codeNum }}</span>
              <el-tag v-if="role.status === 1" type="info" size="small">已禁用</el-tag>
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-else-if="!roleLoading" description="暂无可关联角色" :image-size="96" />
        </section>
      </div>
      <template #footer>
        <div class="flex justify-end">
          <el-button :icon="'el-icon-Close'" @click="drawerVisible = false">取消</el-button>
          <el-button v-if="drawerTab === 'detail'" type="primary" :loading="saveLoading" @click="handleSave">
            <Icon icon="save" class="mr-2" />
            保存
          </el-button>
          <el-button v-else type="primary" :loading="roleSaving" @click="handleSaveRoles">
            <Icon icon="save" class="mr-2" />
            保存角色
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, h, onMounted } from "vue";
import { ElMessage, ElSwitch } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import type { UploadRequestOptions } from "element-plus";
import { FormUtils } from "@/utils/form";
import type { UColumnProps } from "@/components/data/data-table.vue";
import request from "@/utils/request";
import { default as pinyin } from "js-pinyin";

const currentNode = ref<any>(null);

/** 上传成功的头像文件 id，保存时直接读取此值，避免从表单 upload 字段解析 */
const uploadedAvatarId = ref<string>("");

/** 根据 fileId 通过 axios（自动携带 token 头）获取图片，返回 Data URL */
const fetchAvatarUrl = async (fileId: string): Promise<string> => {
  try {
    // 后端返回标准 JSON：{ content: "base64...", contentType: "image/png", fileName: "xxx.png" }
    const res = await request.get("/sym/file/preview", {
      params: { id: fileId },
    });
    if (!res || !res.content) {
      console.warn("[fetchAvatarUrl] 返回数据为空");
      return "";
    }
    // 直接拼接 Data URL
    return `data:${res.contentType || "image/png"};base64,${res.content}`;
  } catch (e: any) {
    console.error("获取头像失败:", e);
    return "";
  }
};

const treeData = ref<any[]>([
  {
    name: "全部",
    value: "",
    children: [],
  },
]);

/** 获取组织机构树 */
const fetchOrgTree = async () => {
  try {
    const res = await $common.post("/sym/org/getOrgTree", {
      parentId: $user.orgRootId,
    });
    treeData.value[0].children = res?.data ?? res ?? [];
  } catch (e) {
    console.error("获取组织机构树失败:", e);
    treeData.value[0].children = [];
  }
};

onMounted(() => {
  fetchOrgTree();
});

const handleNodeClick = (node: any) => {
  currentNode.value = node;
  refreshTableData();
};

// ===================== 右侧表格 =====================
const tableRef = ref();
const searchKeyword = ref("");
const filterOnline = ref<boolean | "">("");
const currentUserId = ref("");

type OnlineUsers = {
  ids: Set<string>;
  userNames: Set<string>;
};

let onlineUsersCache: OnlineUsers | null = null;
let onlineUsersCacheTime = 0;
const onlineUsersCacheTtl = 15 * 1000;

/** 查询当前租户全部有效在线会话，并汇总为用户状态。 */
const fetchOnlineUsers = async (): Promise<OnlineUsers> => {
  if (onlineUsersCache && Date.now() - onlineUsersCacheTime < onlineUsersCacheTtl) {
    return onlineUsersCache;
  }

  const ids = new Set<string>();
  const userNames = new Set<string>();
  const pageSize = 200;
  let pageNum = 1;
  let total = 0;

  do {
    const response = await $common.post("/sym/user/online/page", { pageNum, pageSize });
    const data = response?.data || response || {};
    const rows = data.list || [];
    total = Number(data.total || 0);
    rows.forEach((session: any) => {
      if (session.loginId != null) ids.add(`${session.loginId}`);
      if (session.userName) userNames.add(`${session.userName}`);
    });
    if (!rows.length) break;
    pageNum += 1;
  } while ((pageNum - 1) * pageSize < total);

  onlineUsersCache = { ids, userNames };
  onlineUsersCacheTime = Date.now();
  return onlineUsersCache;
};

const isUserOnline = (user: any, onlineUsers: OnlineUsers) =>
  onlineUsers.ids.has(`${user.id}`) || onlineUsers.userNames.has(`${user.userName || ""}`);

const buildUserParams = (pageNum: number, pageSize: number): Record<string, any> => {
  const params: Record<string, any> = { pageNum, pageSize };
  if (currentNode.value?.id) params.orgId = currentNode.value.id;
  if (searchKeyword.value) params.realName = searchKeyword.value;
  return params;
};

const requestUserPage = async (pageNum: number, pageSize: number) => {
  const response = await $common.post("/sym/user/page", buildUserParams(pageNum, pageSize));
  return response.data || response || {};
};

/** 分页查询用户 */
const fetchUserData = async ({
  pageNo: pageNum,
  pageSize,
}: {
  pageNo: number;
  pageSize: number;
}) => {
  try {
    const onlineUsers = await fetchOnlineUsers();

    if (filterOnline.value !== "") {
      const allUsers: any[] = [];
      const queryPageSize = 200;
      let queryPageNum = 1;
      let total = 0;
      do {
        const data = await requestUserPage(queryPageNum, queryPageSize);
        const rows = data.list || [];
        total = Number(data.total || 0);
        allUsers.push(...rows);
        if (!rows.length) break;
        queryPageNum += 1;
      } while ((queryPageNum - 1) * queryPageSize < total);

      const rows = allUsers
        .map((user) => ({ ...user, online: isUserOnline(user, onlineUsers) }))
        .filter((user) => user.online === filterOnline.value);
      const start = (pageNum - 1) * pageSize;
      return { list: rows.slice(start, start + pageSize), total: rows.length };
    }

    const data = await requestUserPage(pageNum, pageSize);
    return {
      list: (data.list || []).map((user: any) => ({ ...user, online: isUserOnline(user, onlineUsers) })),
      total: data.total || 0,
    };
  } catch (e) {
    console.error("获取用户列表失败:", e);
    return { list: [], total: 0 };
  }
};

const tableColumns = ref<UColumnProps[]>([
  {
    prop: "userName",
    label: "用户账号",
    showOverflowTooltip: true,
  },
  {
    prop: "realName",
    label: "姓名",
  },
  {
    prop: "phone",
    label: "手机号",
    width: 130,
  },
  {
    prop: "roleNames",
    label: "角色",
    minWidth: 130,
  },
  {
    prop: "status",
    label: "状态",
    width: 80,
  },
  {
    prop: "createTime",
    label: "创建时间",
    width: 170,
  },
  {
    prop: "operation",
    label: "操作",
    width: 80,
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

const refreshTableData = () => {
  tableRef.value?.refresh(false);
};

/** 状态切换 */
const handleStatusClick = async (row: any) => {
  // @click.prevent 阻止了开关本身切换，手动计算目标状态：0↔1 互切
  const targetStatus = row.status == 0 ? 1 : 0;
  row._statusLoading = true;
  try {
    await $common.post("/sym/user/saveOrUpdate", {
      ...row,
      status: targetStatus,
    });
    row.status = targetStatus;
    ElMessage.success(`已${targetStatus == 0 ? "启用" : "禁用"}`);
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message);
  } finally {
    row._statusLoading = false;
  }
};

/** 删除用户 */
const handleDelete = (row: any) => {
  $common.handle({
    url: "/sym/user/delete",
    info: `确定要删除用户 "${row.realName || row.userName}" 吗？`,
    data: { id: row.id },
    done: () => refreshTableData(),
  });
};

// ===================== 抽屉表单 =====================
const drawerVisible = ref(false);
const drawerType = ref<"add" | "detail">("add");
const drawerTab = ref<"detail" | "roles">("detail");
const jsonFormRef = ref<any>(null);
const saveLoading = ref(false);
const roleLoading = ref(false);
const roleSaving = ref(false);
const roleOptions = ref<any[]>([]);
const selectedRoleIds = ref<string[]>([]);

const loadUserRoles = async () => {
  if (!currentUserId.value) {
    roleOptions.value = [];
    selectedRoleIds.value = [];
    return;
  }
  try {
    roleLoading.value = true;
    const [optionsResponse, selectedResponse] = await Promise.all([
      $common.post("/sym/user/role-options", {}),
      $common.post("/sym/user/roles", { userId: currentUserId.value }),
    ]);
    const optionsData = optionsResponse?.data || optionsResponse || {};
    const selectedData = selectedResponse?.data || selectedResponse || {};
    roleOptions.value = optionsData.list || [];
    selectedRoleIds.value = (selectedData.roleIds || []).map((id: any) => `${id}`);
  } catch (e: any) {
    console.error("获取用户角色失败:", e);
    ElMessage.error("获取用户角色失败");
    roleOptions.value = [];
    selectedRoleIds.value = [];
  } finally {
    roleLoading.value = false;
  }
};

const handleDrawerTabChange = (tab: string | number) => {
  if (tab === "roles") loadUserRoles();
};

const handleSaveRoles = async () => {
  if (!currentUserId.value) return;
  try {
    roleSaving.value = true;
    await $common.post("/sym/user/saveRoles", {
      userId: currentUserId.value,
      roleIds: selectedRoleIds.value,
    });
    ElMessage.success("用户角色已保存");
    refreshTableData();
  } catch (e: any) {
    console.error("保存用户角色失败:", e);
    ElMessage.error(e?.message || "保存用户角色失败");
  } finally {
    roleSaving.value = false;
  }
};

const handleAdd = () => {
  drawerType.value = "add";
  drawerTab.value = "detail";
  currentUserId.value = "";
  roleOptions.value = [];
  selectedRoleIds.value = [];
  passwordValue.value = "";
  uploadedAvatarId.value = "";
  uploadedDataUrl.value = "";
  drawerVisible.value = true;
  nextTick(() => {
    jsonFormRef.value?.clearValue?.();
    jsonFormRef.value?.setValue?.({ status: 0 });
  });
};

/** 详情回填 */
const handleDetail = async (row: any) => {
  drawerType.value = "detail";
  drawerTab.value = "detail";
  currentUserId.value = row.id || "";
  roleOptions.value = [];
  selectedRoleIds.value = [];
  drawerVisible.value = true;
  await nextTick();
  jsonFormRef.value?.clearValue?.();

  // 编辑模式：用已有头像文件 id 初始化 ref
  uploadedAvatarId.value = row.appFileId || "";

  // 头像回显：通过 axios 携带 token 头获取 Data URL，传给 el-upload 文件列表
  let appFileId: any[] = [];
  if (row.appFileId) {
    const url = await fetchAvatarUrl(row.appFileId);
    if (url) {
      appFileId = [
        {
          url,
          name: row.realName || "avatar",
          id: row.appFileId,
        },
      ];
    }
  }
  passwordValue.value = "";
  jsonFormRef.value?.setValue?.({ ...row, password: "", appFileId, confirmPassword: "" });
};

const handleDrawerClose = () => {
  uploadedAvatarId.value = "";
  uploadedDataUrl.value = "";
  drawerTab.value = "detail";
  roleOptions.value = [];
  selectedRoleIds.value = [];
  drawerVisible.value = false;
};

/** 保存用户 —— 表单字段与 API 字段一致，直接透传 */
const handleSave = async () => {
  try {
    saveLoading.value = true;
    const formData = await jsonFormRef.value?.getFormData(true);
    if (!formData) return;

    const payload: Record<string, any> = { ...formData };

    delete payload.confirmPassword;

    payload.appFileId = uploadedAvatarId.value || undefined;
    if (!payload.orgId) {
      ElMessage.error("请选择所属机构");
      return;
    }

    // 编辑模式传 id
    if (currentUserId.value) {
      payload.id = currentUserId.value;
    }

    await $common.post("/sym/user/saveOrUpdate", payload);
    ElMessage.success(drawerType.value === "add" ? "创建成功" : "保存成功");
    drawerVisible.value = false;
    refreshTableData();
  } catch (e: any) {
    console.error(e);
  } finally {
    saveLoading.value = false;
  }
};

// 用于确认密码校验的响应式密码引用
const passwordValue = ref("");

// 正则表达式缓存
const phoneRegex = /^1[3-9]\d{9}$/;
const idCardRegex = /^\d{17}[\dXx]$/;

// 姓名转拼音缓存
const pinyinCache = new Map<string, string>();

// 姓名转拼音账号
const nameToPinyin = (name: string): string => {
  if (!name) return "";

  if (pinyinCache.has(name)) {
    return pinyinCache.get(name)!;
  }

  try {
    const result = pinyin.getCamelChars?.(name);
    const pinyinResult = (result || "").toLowerCase();
    pinyinCache.set(name, pinyinResult);
    return pinyinResult;
  } catch {
    return "";
  }
};

const formOptions = {
  form: {
    labelWidth: "100px",
    colon: true,
  },
};

/** 上传成功后的 data URL 缓存 */
const uploadedDataUrl = ref<string>("");

/** 自定义头像上传 */
const customUpload = async (options: UploadRequestOptions) => {
  try {
    // FileReader 读取完整 data URL（含 "data:image/xxx;base64," 前缀）
    // 同时用于：1) 提取 base64 上传  2) 作为缩略图预览地址（自包含，无需认证，无生命周期问题）
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(options.file);
    });

    const base64 = dataUrl.split(",")[1];

    // 直接 JSON POST，无需 FormData / Content-Type 特殊处理
    const res = await request.post("/sym/file/upload", {
      file_name: options.file.name,
      content: base64,
    });

    // 如果之前有头像文件（编辑模式替换头像），删除旧文件避免产生孤儿记录
    const oldFileId = uploadedAvatarId.value;
    if (oldFileId) {
      try {
        await $common.post("/sym/file/delete", { id: oldFileId });
      } catch (e) {
        console.error("删除旧头像文件失败:", e);
      }
    }

    // 存文件 id 到 ref（保存时直接读取）
    uploadedAvatarId.value = res.id;

    // 缓存 data URL，供 onSuccess 回调修复 file.url 使用
    uploadedDataUrl.value = dataUrl;
    options.onSuccess({ ...res, url: dataUrl });
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || "上传失败";
    options.onError(new Error(msg));
  }
};

const formRules = FormUtils.fixJson([
  // ---- 基本信息标题 ----
  {
    type: "UTitle",
    props: { name: "基本信息" },
  },
  {
    field: "orgId",
    title: "所属机构",
    type: "cascader",
    $dict: "org",
    $required: true,
    props: {
      props: {
        // 机构树既可选择叶子部门，也允许把任意上级机构作为用户所属机构。
        // emitPath 保持为 false，后端继续接收单个 orgId，不改变保存接口的数据格式。
        checkStrictly: true,
        emitPath: false,
      },
      placeholder: "请选择所属机构",
      clearable: true,
    },
    class: "w-full",
  },
  {
    field: "realName",
    title: "用户姓名",
    type: "input",
    $required: true,
    props: { placeholder: "请输入用户姓名" },
    inject: true,
    on: {
      change: (inject: any, val: string) => {
        const account = nameToPinyin(val);
        if (account) {
          inject.api.setValue("userName", account);
        }
      },
    },
  },
  {
    field: "phone",
    title: "手机号码",
    type: "input",
    $required: true,
    props: { placeholder: "请输入手机号码" },
    validate: [
      {
          "type": "string",
          "message": "请输入正确的手机号码",
          "pattern": $common.regexps.phone
      }
    ],
  },
  {
    field: "userName",
    title: "用户账号",
    type: "input",
    $required: true,
    props: { placeholder: "请输入用户账号" },
    renderSlots: {
      append() {
        return h("div", {
          class: "i-svg:idea cursor-pointer",
          style: "font-size: 18px;",
          title: "根据姓名自动生成",
        });
      },
    },
  },
  {
    field: "password",
    title: "密码",
    type: "input",
    props: { placeholder: "新增必填，详情留空不修改", type: "password", showPassword: true },
    inject: true,
    on: {
      change: (_inject: any, val: string) => {
        passwordValue.value = val;
      },
    },
    validate: [
      {
        validator: (_rule: any, value: string, callback: any) => {
          const password = value == null ? "" : `${value}`;
          if (drawerType.value === "add" && !password) {
            return callback(new Error("请输入密码"));
          }
          callback();
        },
        trigger: "blur",
      },
    ],
    renderSlots: {
      append() {
        return h("div", {
          class: "i-svg:copy cursor-pointer",
          style: "font-size: 18px;",
          title: "复制密码",
          onClick: () => {
            $common.copyText(passwordValue.value);
          },
        });
      },
    },
  },
  {
    field: "confirmPassword",
    title: "确认密码",
    type: "input",
    props: { placeholder: "请再次输入新密码", type: "password", showPassword: true },
    validate: [
      {
        validator: (_rule: any, value: string, callback: any) => {
          const password = passwordValue.value || "";
          if (!password && drawerType.value !== "add") return callback();
          if (!value) return callback(new Error("请输入确认密码"));
          if (value !== password) return callback(new Error("两次密码不一致"));
          callback();
        },
        trigger: "blur",
      },
    ],
  },
  {
    field: "status",
    title: "账号状态",
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

  // ---- 其他信息标题 ----
  {
    type: "UTitle",
    props: { name: "其他信息" },
  },
  // 编制 + 职级
  {
    field: "authorizedStrength",
    title: "编制",
    type: "switch",
    value: "0",
    props: {
      activeValue: "0",
      inactiveValue: "1",
    },
    options: [
      { label: "是", value: "0" },
      { label: "否", value: "1" },
    ],
  },
  {
    field: "originPosition",
    title: "职级",
    type: "dictSelect",
    props: { placeholder: "请选择职级", options: "jobLevel", clearable: true },
  },
  // 职称 + 办公地址
  {
    field: "originRank",
    title: "职称",
    type: "dictSelect",
    props: { placeholder: "请选择职称", options: "jobTitle", clearable: true },
  },
  {
    field: "officeAddress",
    title: "办公地址",
    type: "input",
    props: { placeholder: "请输入办公地址" },
  },
  {
    field: "idCard",
    title: "身份证号",
    type: "input",
    props: { placeholder: "请输入身份证号" },
    validate: [
      {
        validator: (_rule: any, value: string, callback: any) => {
          if (!value) return callback();
          if (!idCardRegex.test(value)) return callback(new Error("身份证号格式不正确"));
          callback();
        },
        trigger: "blur",
      },
    ],
  },
  {
    field: "gender",
    title: "性别",
    type: "dictSelect",
    props: { placeholder: "请选择性别", options: "gender", clearable: true },
  },
  {
    field: "nation",
    title: "民族",
    type: "dictSelect",
    props: { placeholder: "请选择民族", filterable: true, options: "ethnicity", clearable: true },
  },
  {
    field: "dateBirth",
    title: "出生日期",
    type: "DatePicker",
    props: {
      placeholder: "选择日期",
      type: "date",
      format: "YYYY-MM-DD",
      valueFormat: "YYYY-MM-DD",
      style: "width: 100%",
    },
  },
  {
    field: "appFileId",
    title: "头像",
    type: "upload",
    props: {
      httpRequest: customUpload,
      onRemove: async () => {
        // 删除头像时调用后端软删除接口，清理 t_file 记录
        const fileId = uploadedAvatarId.value;
        if (fileId) {
          try {
            await $common.post("/sym/file/delete", { id: fileId });
          } catch (e) {
            console.error("删除头像文件失败:", e);
          }
        }
        uploadedAvatarId.value = "";
        uploadedDataUrl.value = "";
      },
      onSuccess: (_response: any, uploadFile: any) => {
        const dataUrl = uploadedDataUrl.value;
        if (dataUrl && uploadFile) {
          uploadFile.url = dataUrl;
        }
      },
      name: "file",
      listType: "picture-card",
      limit: 1,
      accept: "image/*",
    },
  },
]);
</script>

<style scoped lang="scss">
.drawer-tabs {
  height: 56px;
  width: 100%;

  :deep(.el-tabs__header) {
    height: 100%;
    margin: 0;
  }

  :deep(.el-tabs__nav-wrap),
  :deep(.el-tabs__nav-scroll),
  :deep(.el-tabs__nav) {
    height: 100%;
  }

  :deep(.el-tabs__item) {
    height: 100%;
    line-height: 56px;
  }

  :deep(.el-tabs__active-bar) {
    height: 2px;
    bottom: 0;
    z-index: 2;
  }

  :deep(.el-tabs__nav-wrap::after) {
    z-index: 0;
  }

  :deep(.el-tabs__content) {
    display: none;
  }
}

:deep(.el-drawer__header) {
  box-sizing: border-box;
  height: 56px;
  min-height: 56px;
  margin-bottom: 0;
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.user-roles-panel {
  min-height: 300px;
}

.role-panel-tip {
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.role-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.role-checkbox {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  min-height: 44px;
  margin-right: 0;
  padding: 8px 12px;

  :deep(.el-checkbox__label) {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    padding-left: 8px;
  }
}

.role-checkbox__name {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.role-checkbox__code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.user-manage-content {
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
      height: 43px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: #464c64;
      }
    }

    .tree-container {
      height: calc(100% - 53px);

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

        .node-label {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
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

    .toolbar-search-icon {
      color: var(--el-color-primary);
      cursor: pointer;
      font-size: 17px;
      transition: color 0.2s ease;

      &:hover {
        color: var(--el-color-primary-light-3);
      }
    }

    .user-account-cell {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }

    .online-status-dot {
      flex: 0 0 auto;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.85);
    }

    .online-status-dot--online {
      background: #22c55e;
    }

    .online-status-dot--offline {
      background: #f04438;
    }

    .user-name-link {
      max-width: 100%;
      padding: 0;
      overflow: hidden;
      color: var(--el-color-primary);
      font: inherit;
      line-height: inherit;
      text-align: left;
      text-overflow: ellipsis;
      white-space: nowrap;
      vertical-align: top;
      cursor: pointer;
      background: transparent;
      border: 0;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
