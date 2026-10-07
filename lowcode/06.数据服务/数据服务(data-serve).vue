<template>
  <div class="data-serve-page card-container px-5 pt-5 pb-2">
    <el-splitter class="serve-splitter">
      <el-splitter-panel size="300px" min="10%">
    <aside class="serve-sidebar left-panel">
      <div class="panel-header sidebar-head">
        <h3>服务分组</h3>
        <el-button
          class="left-header-btn"
          type="text"
          title="新增服务分组"
          aria-label="新增服务分组"
          @click="openGroupCreate"
        >
          <Icon icon="el-icon-plus" />
        </el-button>
      </div>

      <div class="tree-container">
        <UTree
          :loading="groupLoading"
          ref="groupTreeRef"
          :data="groupTree"
          node-key="id"
          :search="true"
          :expand="false"
          :default-expand-all="true"
          :auto-expand-first-node="true"
          :expand-on-click-node="false"
          :highlight-key="currentGroup.id"
          class="tree-container-custom"
          search-placeholder="请输入分组名称"
          @node-click="handleGroupClick"
        >
          <template #node="{ node }">
            <div class="group-node">
              <u-tree-node
                show-icon
                class="group-node-label"
                style="--gap: 0; --node-count-color: #323643"
                :keyword="groupTreeRef?.searchValue"
                :label="node.label"
                :node="node"
              >
                <template #count>
                  <span>({{ Number(node.data?.count || 0) }})</span>
                </template>
                <template #icon>
                  <Icon :icon="node.expanded ? 'folder-open' : 'folder'" color="#0b8bf9" class="mr-1" size="23" />
                </template>
              </u-tree-node>
              <span v-if="node.data?.id" class="group-actions" @click.stop>
                <el-button link type="primary" @click.stop="openGroupEdit(node.data)">编辑</el-button>
                <el-button link type="danger" @click.stop="deleteGroup(node.data)">删除</el-button>
              </span>
            </div>
          </template>
        </UTree>
      </div>
    </aside>
      </el-splitter-panel>

      <el-splitter-panel>
    <main class="serve-main right-panel pl-4">
      <div class="list-toolbar source-toolbar">
        <div class="list-toolbar__primary">
          <el-button type="primary" icon="plus" @click="openRegister">创建服务</el-button>
        </div>
        <div class="list-filters toolbar-filters">
          <el-select class="filter-select" v-model="filters.method" clearable placeholder="请求方式" @change="queryFirstPage">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <el-select class="filter-select filter-select--status" v-model="filters.status" clearable placeholder="服务状态" @change="queryFirstPage">
            <el-option label="草稿" value="draft" />
            <el-option label="已发布" value="published" />
            <el-option label="已停用" value="offline" />
          </el-select>
          <el-input
            v-model.trim="filters.keyword"
            placeholder="请输入服务名称/服务编码/请求地址"
            clearable
            class="keyword-input"
            @clear="queryFirstPage"
            @keydown.enter="queryFirstPage"
          >
            <template #suffix>
              <span class="keyword-search-trigger" title="查询" @mousedown.prevent @click.stop="queryFirstPage">
                <Icon icon="search" />
              </span>
            </template>
          </el-input>
        </div>
      </div>

      <data-table
        ref="tableRef"
        show-index
        :columns="[]"
        :data="fetchServices"
        row-key="tid"
        height="100%"
        flex-type="flex-[1_1_200px]"
        :show-search="false"
        :show-reset="false"
        :page-sizes="[10, 20, 50, 100]"
        stripe
        class="serve-table"
      >
        <el-table-column label="服务名称" min-width="190" align="left" header-align="left" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link class="serve-link" type="primary" :underline="false" @click="handleDetailClick(row)">
              {{ row.serviceName || "-" }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="serviceCode" label="服务编码" min-width="150" align="left" header-align="left" show-overflow-tooltip />
        <el-table-column v-if="!isCompactTable" label="请求方式" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <span class="method-tag" :class="'is-' + upper(row.method || 'GET')">
              {{ upper(row.method || "GET") }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactTable" label="接口地址" min-width="240" align="left" header-align="left" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.publishAddressFull || row.publishAddress || row.uri || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactTable" prop="groupName" label="服务分组" width="130" align="left" header-align="left" show-overflow-tooltip />
        <el-table-column v-if="!isCompactTable" label="服务类型" width="120" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.serviceCreateType === "apiBuild" ? "服务构建" : "服务登记" }}
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactTable" label="状态" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactTable" prop="updatedTime" label="更新时间" width="170" align="left" header-align="left" show-overflow-tooltip />
        <el-table-column
          label="操作"
          width="158"
          align="right"
          header-align="center"
          fixed="right"
          class-name="operation-column"
          :show-overflow-tooltip="false"
        >
          <template #default="{ row }">
            <div class="operation-buttons table-actions">
              <el-button link type="primary" @click="openOrchestration(row)">编排</el-button>
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </data-table>
    </main>
      </el-splitter-panel>
    </el-splitter>

    <api-register-modal v-model="registerModalVisible" :api-detail="apiDetail" @close="onModalClose" />
    <api-build-modal v-model="buildModalVisible" :api-detail="apiDetail" @close="onModalClose" />
    <teleport to="body">
      <div v-if="orchestrationVisible" class="orchestration-designer">
      <div class="orchestration-frame-wrap">
        <div v-if="orchestrationError" class="orchestration-error">
          <div class="orchestration-error-card">
            <div class="orchestration-error-icon">!</div>
            <h3>服务编排画布暂时无法打开</h3>
            <p>{{ orchestrationError }}</p>
            <div class="orchestration-error-actions">
              <el-button type="primary" @click="openOrchestration(orchestrationRow)">重新检测</el-button>
              <el-button @click="closeOrchestration">关闭</el-button>
            </div>
          </div>
        </div>
        <div v-else-if="orchestrationLoading" class="orchestration-loading">
          <el-icon class="is-loading" :size="30" color="#1677ff"><el-icon-loading /></el-icon>
          <span>正在打开服务编排画布...</span>
        </div>
        <iframe
          v-if="orchestrationUrl && !orchestrationError"
          ref="orchestrationIframeRef"
          :src="orchestrationUrl"
          class="orchestration-iframe"
          frameborder="0"
          allowfullscreen
          @load="onOrchestrationLoad"
        ></iframe>
      </div>
      </div>
    </teleport>
    <el-dialog
      v-model="groupDialogVisible"
      :title="groupForm.id ? '编辑服务分组' : '新增服务分组'"
      width="420px"
      destroy-on-close
    >
      <el-form :model="groupForm" label-width="90px">
        <el-form-item label="分组名称" required>
          <el-input v-model.trim="groupForm.name" placeholder="请输入分组名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="分组编码">
          <el-input v-model.trim="groupForm.code" placeholder="可不填，系统自动生成" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="groupForm.sortNo" :min="1" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRouter } from "vue-router";

const router = useRouter();
const groupTreeRef = ref();
const tableRef = ref();
const groupLoading = ref(false);
const registerModalVisible = ref(false);
const buildModalVisible = ref(false);
const groupDialogVisible = ref(false);
const orchestrationVisible = ref(false);
const orchestrationLoading = ref(false);
const orchestrationError = ref("");
const orchestrationUrl = ref("");
const orchestrationTitle = ref("");
const orchestrationRow = ref(null);
const orchestrationIframeRef = ref();
let orchestrationAttempt = 0;
let orchestrationProbeController = null;
const currentGroup = ref({ id: "", name: "全部" });
const apiDetail = ref({});
const groupTree = ref([{ id: "", name: "全部", count: 0, children: [] }]);
const groupForm = reactive({ id: "", name: "", code: "", sortNo: 1 });
const isCompactTable = ref(false);

const filters = reactive({
  keyword: "",
  method: "",
  status: "",
});

const upper = (value) => String(value || "").toUpperCase();
const parseMaybeJson = (value) => {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch (error) {
    return value;
  }
};
const extractPayload = (response) => {
  const parsed = parseMaybeJson(response);
  const payload = parseMaybeJson(parsed?.data);
  if (payload && typeof payload === "object") return payload;
  if (parsed && typeof parsed === "object" && parsed.data !== undefined) return parseMaybeJson(parsed.data) || {};
  return parsed || {};
};
const normalizeServiceRow = (row) => {
  const normalized = { ...row };
  normalized.id = row.id || row.tid;
  normalized.tid = row.tid || row.id;
  normalized.serviceName = row.serviceName || row.servicename || row.service_name || row.name || "";
  normalized.serviceCode = row.serviceCode || row.servicecode || row.service_code || row.code || "";
  normalized.groupName = row.groupName || row.groupname || row.group_name || "默认分组";
  normalized.serviceCreateType = row.serviceCreateType || row.servicecreatetype || row.service_create_type || "api";
  normalized.publishAddressFull =
    row.publishAddressFull ||
    row.publishaddressfull ||
    row.publish_address_full ||
    row.publishAddress ||
    row.publishaddress ||
    row.publish_address ||
    row.uri ||
    "";
  normalized.publishAddress = row.publishAddress || row.publishaddress || row.publish_address || normalized.publishAddressFull;
  normalized.updatedTime = row.updatedTime || row.updatedtime || row.updated_time || "";
  normalized.flowId = row.flowId || row.flowid || row.flow_id || "";
  return normalized;
};
const statusText = (status) => {
  const key = String(status || "draft").toLowerCase();
  return {
    published: "已发布",
    online: "已发布",
    draft: "草稿",
    offline: "已停用",
    disabled: "已停用",
  }[key] || "草稿";
};
const statusTagType = (status) => {
  const key = String(status || "draft").toLowerCase();
  if (key === "published" || key === "online") return "success";
  if (key === "offline" || key === "disabled") return "info";
  return "warning";
};

const loadGroups = async () => {
  groupLoading.value = true;
  try {
    const res = await $common.post("/dws/flowserve/gateway/group/list", {});
    const data = extractPayload(res);
    const children = data.children || data.list || [];
    const rootCount = Number(data.total || children.reduce((sum, item) => sum + Number(item.count || 0), 0));
    groupTree.value = [{ id: "", name: "全部", count: rootCount, children }];
    await nextTick();
  } catch (error) {
    console.error("服务分组加载失败", error);
    ElMessage.error("服务分组加载失败");
  } finally {
    groupLoading.value = false;
  }
};

const fetchServices = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const res = await $common.post("/dws/flowserve/gateway/api/list", {
    pageNum: pageNo,
    pageSize,
    keyword: filters.keyword,
    method: filters.method,
    status: filters.status,
    groupId: currentGroup.value?.id || "",
  });
  const data = extractPayload(res);
  return {
    list: (data.list || []).map(normalizeServiceRow),
    total: Number(data.total || 0),
  };
};

const queryFirstPage = () => {
  tableRef.value?.refresh?.(true);
};
const handleGroupClick = (node) => {
  currentGroup.value = node || { id: "", name: "全部" };
  queryFirstPage();
};
const openRegister = () => {
  apiDetail.value = {};
  registerModalVisible.value = true;
};
const loadApiDetail = async (row) => {
  const apiId = row?.id || row?.tid;
  if (!apiId) return { ...(row || {}) };
  const response = await $common.post("/dws/flowserve/gateway/api/detail", { apiId });
  return normalizeServiceRow({ ...row, ...extractPayload(response) });
};
const openGroupCreate = () => {
  Object.assign(groupForm, { id: "", name: "", code: "", sortNo: groupTree.value?.[0]?.children?.length + 1 || 1 });
  groupDialogVisible.value = true;
};
const openGroupEdit = (row) => {
  Object.assign(groupForm, {
    id: row.id || "",
    name: row.name || "",
    code: row.code || "",
    sortNo: Number(row.sortNo || 1),
  });
  groupDialogVisible.value = true;
};
const saveGroup = async () => {
  if (!groupForm.name) {
    ElMessage.warning("请填写分组名称");
    return;
  }
  await $common.post("/dws/flowserve/gateway/group/save", { ...groupForm });
  ElMessage.success("保存成功");
  groupDialogVisible.value = false;
  await loadGroups();
};
const deleteGroup = async (row) => {
  await ElMessageBox.confirm(`确认删除服务分组“${row.name || "-"}”吗？`, "删除确认", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消",
  });
  await $common.post("/dws/flowserve/gateway/group/delete", { id: row.id });
  ElMessage.success("删除成功");
  if (currentGroup.value?.id === row.id) {
    currentGroup.value = { id: "", name: "全部" };
  }
  await loadGroups();
  queryFirstPage();
};
const handleDetailClick = async (row) => {
  const id = row?.id || row?.tid;
  if (!id) {
    ElMessage.warning("暂未找到服务标识，无法查看详情");
    return;
  }
  try {
    await router.push({
      name: "deal-data-server-detail",
      query: {
        type: "api",
        title: row.serviceName || row.name || "服务详情",
        id,
        apiType: row.serviceCreateType === "apiBuild" ? "build" : "register",
      },
    });
  } catch (error) {
    console.error("打开服务详情失败", error);
    ElMessage.error("服务详情暂时无法打开，请稍后重试");
  }
};
const handleEdit = async (row) => {
  try {
    const detail = await loadApiDetail(row);
    apiDetail.value = { ...detail };
    if (detail.serviceCreateType === "apiBuild") {
      buildModalVisible.value = true;
    } else {
      registerModalVisible.value = true;
    }
  } catch (error) {
    console.error("服务详情加载失败", error);
    ElMessage.error("服务详情加载失败，暂不能编辑");
  }
};
const getToken = () => localStorage.getItem("token") || "";
const normalizeBaseUrl = (url) => String(url || "").replace(/\/+$/, "");
const getYuntiCandidates = () => {
  const { protocol, hostname, port, origin } = window.location;
  const isLocalHost = ["localhost", "127.0.0.1"].includes(hostname);
  if (isLocalHost && port === "3000") {
    return [6174, 6175, 6176, 6177, 6178]
      .map((candidatePort) => `${protocol}//${hostname}:${candidatePort}`)
      .concat(`${origin}/yunti`);
  }
  return [`${origin}/yunti`];
};
const isSameOrigin = (baseUrl) => {
  try {
    return new URL(baseUrl, window.location.href).origin === window.location.origin;
  } catch (error) {
    return false;
  }
};
const isYuntiDocument = (text) =>
  /<meta[^>]+name=["']application-name["'][^>]+content=["']data-elements-yunti["']/i.test(text) ||
  text.includes("云梯服务编排平台") ||
  text.includes("FlowServe Studio");
const isYuntiReachable = async (baseUrl, parentSignal) => {
  const controller = new AbortController();
  const abortProbe = () => controller.abort();
  const timeout = window.setTimeout(abortProbe, 3000);
  parentSignal?.addEventListener("abort", abortProbe, { once: true });
  try {
    const response = await fetch(`${normalizeBaseUrl(baseUrl)}/?_health=${Date.now()}`, {
      method: "GET",
      cache: "no-store",
      mode: isSameOrigin(baseUrl) ? "same-origin" : "no-cors",
      signal: controller.signal,
    });
    if (response.type === "opaque") return true;
    if (!response.ok) return false;
    const text = await response.text().catch(() => "");
    return isYuntiDocument(text);
  } catch (error) {
    return false;
  } finally {
    window.clearTimeout(timeout);
    parentSignal?.removeEventListener("abort", abortProbe);
  }
};
const buildOrchestrationUrl = (baseUrl, row) => {
  const flowId = row?.flowId || row?.flow_id || row?.id || row?.tid;
  const token = getToken();
  const params = new URLSearchParams({
    communication: "postMessage",
    apiId: row?.id || row?.tid || "",
    flowId,
    orgId: $user?.orgId || "",
    returnTo: window.location.href,
  });
  if (token) params.set("token", token);
  return `${normalizeBaseUrl(baseUrl)}/editor/${encodeURIComponent(flowId)}?${params.toString()}`;
};
const sendOrchestrationInitMessage = () => {
  const row = orchestrationRow.value || {};
  orchestrationIframeRef.value?.contentWindow?.postMessage(
    {
      type: "INIT",
      payload: {
        token: getToken(),
        apiId: row.id || row.tid || "",
        flowId: row.flowId || row.flow_id || row.id || row.tid || "",
        orgId: $user?.orgId || "",
        returnTo: window.location.href,
      },
    },
    "*"
  );
};
const detectYuntiUnexpectedLogin = () => {
  try {
    const href = orchestrationIframeRef.value?.contentWindow?.location.href || "";
    if (href.includes("/login") || href.includes("#/login")) {
      orchestrationError.value =
        "当前地址打开到了主系统登录页，说明 data-elements-yunti 画布应用没有正确挂载。请先启动 data-elements-yunti，或检查生产环境 /yunti 静态资源映射。";
    }
  } catch (error) {
    // 跨域 iframe 无法读取 location，属于正常情况。
  }
};
const onOrchestrationLoad = () => {
  if (!orchestrationVisible.value) return;
  orchestrationLoading.value = false;
  sendOrchestrationInitMessage();
  detectYuntiUnexpectedLogin();
};
const closeOrchestration = () => {
  orchestrationAttempt += 1;
  orchestrationProbeController?.abort();
  orchestrationProbeController = null;
  orchestrationVisible.value = false;
  orchestrationUrl.value = "";
  orchestrationError.value = "";
  orchestrationLoading.value = false;
  orchestrationRow.value = null;
};
const openOrchestration = async (row) => {
  const target = row || orchestrationRow.value;
  if (!target) return;
  const flowId = target.flowId || target.flow_id || target.id || target.tid;
  if (!flowId) {
    ElMessage.warning("当前服务没有关联编排流程");
    return;
  }
  orchestrationRow.value = target;
  orchestrationTitle.value = target.serviceName || target.name || "服务编排";
  orchestrationVisible.value = true;
  orchestrationLoading.value = true;
  orchestrationError.value = "";
  orchestrationUrl.value = "";
  orchestrationProbeController?.abort();
  orchestrationProbeController = new AbortController();
  const currentAttempt = ++orchestrationAttempt;

  let baseUrl = "";
  for (const candidate of getYuntiCandidates()) {
    if (await isYuntiReachable(candidate, orchestrationProbeController.signal)) {
      baseUrl = normalizeBaseUrl(candidate);
      break;
    }
    if (currentAttempt !== orchestrationAttempt || !orchestrationVisible.value) return;
  }
  if (currentAttempt !== orchestrationAttempt || !orchestrationVisible.value) return;
  orchestrationProbeController = null;
  if (!baseUrl) {
    orchestrationLoading.value = false;
    orchestrationError.value =
      "未检测到 data-elements-yunti 画布应用。本地开发请从 6174 端口启动；端口占用时会自动顺延。生产环境请确认 /yunti 静态资源已发布。";
    return;
  }
  orchestrationUrl.value = buildOrchestrationUrl(baseUrl, target);
};
const handleOrchestrationMessage = (event) => {
  if (event.source !== orchestrationIframeRef.value?.contentWindow) return;
  if (event.data?.type === "YUNTI_APP_CLOSE" || event.data?.type === "FLOWSERVE_APP_CLOSE") {
    closeOrchestration();
    return;
  }
  if (event.data?.type === "YUNTI_APP_READY" || event.data?.type === "FLOWSERVE_APP_READY") {
    sendOrchestrationInitMessage();
  }
};
const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除服务“${row.serviceName || row.name || "-"}”吗？`, "删除确认", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消",
  });
  await $common.post("/dws/flowserve/gateway/delete", { apiId: row.id || row.tid });
  ElMessage.success("删除成功");
  await Promise.all([loadGroups(), tableRef.value?.refresh?.()]);
};
const onModalClose = (result) => {
  if (result === "success") {
    Promise.all([loadGroups(), tableRef.value?.refresh?.()]);
  }
};
const refreshTableDensity = () => {
  // 侧栏占用空间后，窄工作区只保留定位服务所需的核心列，避免主区域产生横向滚动条。
  isCompactTable.value = window.innerWidth < 1480;
};

onMounted(async () => {
  refreshTableDensity();
  window.addEventListener("resize", refreshTableDensity);
  window.addEventListener("message", handleOrchestrationMessage);
  await loadGroups();
});
onUnmounted(() => {
  closeOrchestration();
  window.removeEventListener("resize", refreshTableDensity);
  window.removeEventListener("message", handleOrchestrationMessage);
});
</script>

<style scoped lang="scss">
.data-serve-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.serve-splitter {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  height: 100%;
  overflow: hidden;

  // Element Plus uses .el-splitter-panel in the runtime currently in use.
  // Keep the former selector for version compatibility, but contain scrolling
  // inside the table rather than letting a child toolbar widen the whole pane.
  :deep(.el-splitter-panel),
  :deep(.el-splitter__panel) {
    min-width: 0 !important;
    overflow: hidden !important;
  }
}

.serve-sidebar {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(5, 5, 5, 0.06);
  overflow-y: auto;

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    margin: 0 14px 10px 0;
    border-bottom: 1px solid rgba(5, 5, 5, 0.06);

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
    flex: 1;
    min-height: 0;
    height: calc(100% - 43px);

    .tree-container-custom {
      height: 100%;

      :deep(.u-tree-search) {
        margin-right: 14px;
      }

      :deep(.el-tree) {
        padding-right: 14px;
      }
    }

    :deep(.el-tree) {
      --el-tree-node-content-height: 100%;
    }

    :deep(.el-tree-node__content.is-current) {
      background-color: var(--el-color-primary-light-9) !important;
      color: var(--el-color-primary);
    }

    :deep(.el-tree-node__content) {
      padding-bottom: 2px;
      overflow: hidden;
    }
  }
}

.group-node {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.group-node-label {
  flex: 1;
  min-width: 0;
}

.group-actions {
  display: none;
  align-items: center;
  flex-shrink: 0;
  gap: 2px;
  padding-left: 4px;
  background: #fff;
}

.group-node:hover .group-actions {
  display: inline-flex;
}

.serve-main {
  width: 100%;
  height: 100%;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  container-type: inline-size;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.list-toolbar__primary {
  flex: 0 0 auto;
}

.list-filters {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 0 1 auto;
  min-width: 0;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: auto;
}

.filter-select {
  flex: 0 1 clamp(120px, 18cqw, 160px);
  width: clamp(120px, 18cqw, 160px);
}

.filter-select--status {
  flex-basis: clamp(108px, 16cqw, 144px);
  width: clamp(108px, 16cqw, 144px);
}

.keyword-input {
  flex: 0 1 clamp(210px, 32cqw, 360px);
  width: clamp(210px, 32cqw, 360px);
  max-width: 360px;
  min-width: 210px;
}

@media (max-width: 760px) {
  .list-toolbar {
    align-items: stretch;
  }

  .list-filters {
    width: 100%;
    margin-left: 0;
  }

  .filter-select {
    flex: 1 1 calc(50% - 5px);
    width: auto;
  }

  .keyword-input {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
  }
}

.keyword-search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  width: 22px;
  height: 22px;
  color: #7d8ca5;
  cursor: pointer;
  border-radius: 6px;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.serve-link {
  display: inline-flex;
  max-width: 100%;
  vertical-align: middle;

  :deep(.el-link__inner) {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;

  :deep(.el-button) {
    margin-left: 0;
    font-weight: 400;
    height: auto;
    min-height: 28px;
    padding: 0 2px;
    line-height: 28px;
  }
}

.serve-table {
  flex: 1;
  min-width: 0;
  min-height: 0;

  :deep(.pagination) {
    container-type: inline-size;
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 10px;
    min-width: 0;
    white-space: nowrap;
  }

  :deep(.pagination > span:first-child) {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.pagination .el-pagination) {
    flex: 0 1 auto;
    flex-wrap: nowrap;
    min-width: 0;
    margin-left: auto;
    white-space: nowrap;
  }

  :deep(.operation-column .cell) {
    overflow: visible;
  }

  :deep(.el-scrollbar__bar.is-horizontal) {
    display: none;
  }

  :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
    min-width: 48px;
    border-radius: 5px;
    background-color: #a8b1bf;
    opacity: 0.78;
  }

  :deep(.el-scrollbar__bar.is-horizontal:hover .el-scrollbar__thumb) {
    background-color: #8d98a8;
    opacity: 0.9;
  }

  :deep(.el-table__inner-wrapper::before) {
    height: 1px;
    background-color: #dcdfe6;
  }
}

@container (max-width: 680px) {
  .serve-table :deep(.pagination > span:first-child),
  .serve-table :deep(.pagination .el-pagination__jump) {
    display: none;
  }
}

@container (max-width: 480px) {
  .serve-table :deep(.pagination .el-pagination__sizes) {
    display: none;
  }
}

.method-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  height: 22px;
  line-height: 22px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #1677ff;
}

.method-tag.is-GET {
  background: #18a058;
}

.method-tag.is-POST {
  background: #1677ff;
}

.method-tag.is-PUT {
  background: #fa8c16;
}

.method-tag.is-DELETE {
  background: #f56c6c;
}

.orchestration-designer {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: block;
  width: 100vw;
  height: 100vh;
  background: #fff;
}

.orchestration-frame-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #f5f7fb;
}

.orchestration-iframe {
  width: 100%;
  height: 100%;
  display: block;
  background: #fff;
}

.orchestration-loading,
.orchestration-error {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f6f9ff;
}

.orchestration-loading {
  gap: 10px;
  font-size: 14px;
  color: #4e5d78;
}

.orchestration-error-card {
  width: 420px;
  padding: 28px 30px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 48px rgba(22, 55, 120, 0.14);
  text-align: center;

  h3 {
    margin: 12px 0 8px;
    font-size: 18px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    line-height: 1.7;
    color: #5e6c84;
  }
}

.orchestration-error-icon {
  width: 42px;
  height: 42px;
  margin: 0 auto;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff1f0;
  color: #ff4d4f;
  font-size: 24px;
  font-weight: 700;
}

.orchestration-error-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 18px;
}
</style>
