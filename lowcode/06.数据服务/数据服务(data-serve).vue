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
          <el-button type="primary" icon="plus" @click="openRegister">登记数据服务</el-button>
        </div>
        <div class="list-filters toolbar-filters">
          <el-select class="filter-select filter-select--status" v-model="filters.status" clearable placeholder="服务状态" @change="queryFirstPage">
            <el-option label="草稿" value="draft" />
            <el-option label="已发布" value="published" />
            <el-option label="已停用" value="offline" />
          </el-select>
          <el-input
            v-model.trim="filters.keyword"
            placeholder="搜索服务名称、编码或地址"
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
        :columns="actionColumns"
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
        <el-table-column prop="serviceName" sortable="custom" label="服务名称" min-width="220" align="left" header-align="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="service-identity">
              <el-link class="serve-link" type="primary" :underline="false" @click="handleDetailClick(row)">{{ row.serviceName || "—" }}</el-link>
              <span v-if="row.serviceCode" class="service-code" :title="row.serviceCode">{{ row.serviceCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="请求方式" width="100" align="left" header-align="left">
          <template #default="{ row }">{{ upper(row.method) || "—" }}</template>
        </el-table-column>
        <el-table-column prop="publishAddressFull" label="服务地址" min-width="240" align="left" header-align="left" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center" header-align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="updatedTime" sortable="custom" label="更新时间" width="170" align="left" header-align="left" show-overflow-tooltip />
      </data-table>
    </main>
      </el-splitter-panel>
    </el-splitter>

    <api-register-modal v-model="registerModalVisible" :api-detail="apiDetail" @close="onModalClose" />
    <api-build-modal v-model="buildModalVisible" :api-detail="apiDetail" @close="onModalClose" />
    <teleport to="body">
      <div v-if="orchestrationVisible" class="orchestration-designer">
      <div class="orchestration-frame-wrap">
        <section v-if="orchestrationLoading || orchestrationError" class="flow-entry" :class="{'flow-entry--error': orchestrationError}"
          :role="orchestrationError ? 'alert' : 'status'" aria-live="polite" :aria-busy="!orchestrationError">
          <div class="flow-entry__card">
            <div class="flow-entry__eyebrow"><i />云梯 · 服务编排</div>
      <div class="flow-entry__visual" aria-hidden="true">
        <div class="flow-entry__guide" />
        <div class="flow-entry__wire flow-entry__wire--first"><i /></div>
        <div class="flow-entry__wire flow-entry__wire--second"><i /></div>
        <div class="flow-entry__node flow-entry__node--input"><svg viewBox="0 0 24 24"><path d="M5 5h14v14H5zM9 9l3 3-3 3m5-3h2" /></svg><span>请求</span></div>
        <div class="flow-entry__node flow-entry__node--core"><svg viewBox="0 0 24 24"><path d="M5 7h14M5 17h14M8 4v6m8 4v6M5 12h14m-3-3v6" /></svg><span>编排</span></div>
        <div class="flow-entry__node flow-entry__node--output"><svg viewBox="0 0 24 24"><path d="M5 5h14v14H5zM8 12l3 3 5-6" /></svg><span>响应</span></div>
        <span v-if="orchestrationError" class="flow-entry__notice">!</span>
      </div>
            <h1>{{ orchestrationError ? entryFailure().title : '正在打开服务编排' }}</h1>
            <p class="flow-entry__description">{{ orchestrationError ? entryFailure().description : '连接服务节点，准备你的编排画布' }}</p>
            <template v-if="orchestrationError">
              <div class="flow-entry__actions">
                <button class="flow-entry__button flow-entry__button--primary" @click="openOrchestration(orchestrationRow)">重新加载</button>
                <button class="flow-entry__button" @click="closeOrchestration">返回数据服务</button>
              </div>
              <details class="flow-entry__details"><summary>查看错误详情</summary><pre>{{ orchestrationError }}</pre></details>
            </template>
            <div v-else class="flow-entry__progress" aria-hidden="true"><i /><i /><i /></div>
            <div class="flow-entry__footer">连接数据，让服务有序流转</div>
          </div>
        </section>
        <iframe
          v-if="orchestrationUrl && !orchestrationError"
          ref="orchestrationIframeRef"
          :src="orchestrationUrl"
          class="orchestration-iframe"
          :class="{'orchestration-iframe--pending': orchestrationLoading}"
          :aria-hidden="orchestrationLoading"
          title="云梯服务编排画布"
          frameborder="0"
          allowfullscreen
          @load="onOrchestrationLoad"
          @error="onOrchestrationError"
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
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();
const groupTreeRef = ref();
const tableRef = ref();
const groupLoading = ref(false);
const registerModalVisible = ref(false);
const buildModalVisible = ref(false);
const groupDialogVisible = ref(false);
const orchestrationVisible = ref(false);
const orchestrationLoading = ref(false);
const orchestrationError = ref("");
const orchestrationErrorCode = ref(0);
const orchestrationUrl = ref("");
const orchestrationTitle = ref("");
const orchestrationRow = ref(null);
const orchestrationIframeRef = ref();
let orchestrationAttempt = 0;
let orchestrationProbeController = null;
let orchestrationLoadTimer = null;
const currentGroup = ref({ id: "", name: "全部" });
const apiDetail = ref({});
const groupTree = ref([{ id: "", name: "全部", count: 0, children: [] }]);
const groupForm = reactive({ id: "", name: "", code: "", sortNo: 1 });

const filters = reactive({
  keyword: "",
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
    const res = await $common.post("/dws/flowserve/gateway/group/list", {departmentOnly:true});
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

const fetchServices = async ({ pageNo = 1, pageSize = 20, sortField = undefined, sortDir = undefined } = {}) => {
  const res = await $common.post("/dws/flowserve/gateway/api/list", {
    departmentOnly: true,
    pageNum: pageNo,
    pageSize,sortField,sortDir,
    keyword: filters.keyword,
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
  apiDetail.value = {groupId: currentGroup.value?.id || "api_service_group_default"};
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
const getToken = () => {
  // Follow the platform's remember-me choice: ordinary logins use sessionStorage.
  const rememberMe = localStorage.getItem("remember_me") === "true";
  const stored = (rememberMe ? localStorage : sessionStorage).getItem("token");
  if (!stored) return "";
  try {
    const parsed = JSON.parse(stored);
    return typeof parsed === "string" ? parsed : "";
  } catch {
    return stored;
  }
};
const entryFailure = () => {
  const code = Number(orchestrationErrorCode.value);
  if (code === 404) return {title: '暂时无法访问这个流程', description: '流程可能已移除，或不在当前账号的数据范围内。请返回数据服务重新选择。'};
  if ([401, 403, 100120].includes(code)) return {title: '请重新确认登录状态', description: '当前登录已失效或没有访问权限，请返回数据服务重新登录后打开。'};
  if (code === 409) return {title: '编排内容需要检查', description: '当前服务的流程关联或编排内容存在冲突，请返回数据服务检查后重试。'};
  return {title: '服务编排暂时无法打开', description: '连接暂时中断或服务未能及时响应。请稍后重试，或返回数据服务。'};
};
const entryErrorText = (value) => String(value || '服务暂时无法响应').slice(0, 2000)
  .replace(/(Bearer\s+)[\w.\-]+/gi, '$1[已隐藏]')
  .replace(/((?:token|password|secret|api[_-]?key)\s*[:=]\s*)[^\s&,;]+/gi, '$1[已隐藏]');
const normalizeBaseUrl = (url) => String(url || "").replace(/\/+$/, "");
const getYuntiCandidates = () => {
  const { protocol, hostname, origin } = window.location;
  const isLocalHost = ["localhost", "127.0.0.1"].includes(hostname);
  if (isLocalHost) {
    // Match Yunti's current Vite port, with bounded fallback for occupied ports and older setups.
    return [3030, 3031, 3032, 3033, 3034, 6174, 6175, 6176, 6177, 6178]
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
  const timeout = window.setTimeout(abortProbe, 5000);
  parentSignal?.addEventListener("abort", abortProbe, { once: true });
  try {
    const response = await fetch(`${normalizeBaseUrl(baseUrl)}/?_health=${Date.now()}`, {
      method: "GET",
      cache: "no-store",
      mode: isSameOrigin(baseUrl) ? "same-origin" : "cors",
      signal: controller.signal,
    });
    // A listening port alone is not proof that it is the Yunti application.
    if (response.type === "opaque") return false;
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
  const flowId = row?.flowId || row?.flow_id;
  if (!flowId) throw new Error("服务尚未关联有效流程");
  const params = new URLSearchParams({
    communication: "postMessage",
    entryId: String(orchestrationAttempt),
    parentOrigin: window.location.origin,
    apiId: row?.id || row?.tid || "",
    flowId,
    orgId: $user?.orgId || "",
    returnTo: window.location.href,
  });
  return `${normalizeBaseUrl(baseUrl)}/editor/${encodeURIComponent(flowId)}?${params.toString()}`;
};
const sendOrchestrationInitMessage = () => {
  if (!orchestrationUrl.value) return;
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
    new URL(orchestrationUrl.value).origin
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
  sendOrchestrationInitMessage();
  detectYuntiUnexpectedLogin();
};
const onOrchestrationError = () => {
  window.clearTimeout(orchestrationLoadTimer);
  orchestrationLoading.value = false;
  orchestrationError.value = "云梯画布连接中断，请确认 data-elements-yunti 服务仍在运行后重新检测。";
};
const closeOrchestration = () => {
  window.clearTimeout(orchestrationLoadTimer);
  orchestrationAttempt += 1;
  orchestrationProbeController?.abort();
  orchestrationProbeController = null;
  orchestrationVisible.value = false;
  orchestrationUrl.value = "";
  orchestrationError.value = "";
  orchestrationErrorCode.value = 0;
  orchestrationLoading.value = false;
  orchestrationRow.value = null;
};
const openOrchestration = async (row) => {
  window.clearTimeout(orchestrationLoadTimer);
  const target = row || orchestrationRow.value;
  if (!target) return;
  const apiId = target.id || target.tid;
  if (!apiId) {
    ElMessage.warning("请选择有效的数据服务");
    return;
  }
  orchestrationRow.value = target;
  orchestrationTitle.value = target.serviceName || target.name || "服务编排";
  orchestrationVisible.value = true;
  orchestrationLoading.value = true;
  orchestrationError.value = "";
  orchestrationErrorCode.value = 0;
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
      "未检测到云梯画布应用。本地开发默认地址为 http://localhost:3030，端口占用时会顺延；请检查服务启动终端中的地址。生产环境请确认 /yunti 静态资源已发布。";
    return;
  }
  // The explicit user action creates at most one authorized draft; listing rows never does.
  try {
    const response = await $common.postSilently('/dws/flowserve/gateway/orchestration/open', {apiId}, {}, 20000);
    if (currentAttempt !== orchestrationAttempt || !orchestrationVisible.value) return;
    const opened = extractPayload(response);
    if (!opened.flowId) throw new Error('未能读取当前服务的编排流程，请重新加载');
    orchestrationRow.value = {...target, flowId: opened.flowId};
    orchestrationUrl.value = buildOrchestrationUrl(baseUrl, orchestrationRow.value);
  } catch (error) {
    if (currentAttempt !== orchestrationAttempt || !orchestrationVisible.value) return;
    orchestrationLoading.value = false;
    orchestrationErrorCode.value = Number(error?.info?.status || String(error?.info?.code || '').match(/(?:^|-)(401|403|404|409|100120)$/)?.[1] || error?.response?.status || error?.code) || 0;
    orchestrationError.value = entryErrorText(error?.info?.message || error?.message);
    return;
  }
  orchestrationLoadTimer = window.setTimeout(() => {
    if (currentAttempt !== orchestrationAttempt || !orchestrationVisible.value) return;
    orchestrationLoading.value = false;
    orchestrationError.value = "已连接云梯服务，但画布初始化超时。请重新检测；若仍无法打开，请检查云梯页面是否完整加载。";
  }, 45000);
};
const handleOrchestrationMessage = (event) => {
  if (event.source !== orchestrationIframeRef.value?.contentWindow) return;
  if (!orchestrationUrl.value || event.origin !== new URL(orchestrationUrl.value).origin) return;
  if (event.data?.type === "YUNTI_APP_CLOSE" || event.data?.type === "FLOWSERVE_APP_CLOSE") {
    closeOrchestration();
    return;
  }
  if (event.data?.type === "YUNTI_APP_READY" || event.data?.type === "FLOWSERVE_APP_READY") {
    sendOrchestrationInitMessage();
  }
  // Session readiness is only an intermediate step. Keep the same surface until the graph is fitted.
  if (["YUNTI_EDITOR_READY", "YUNTI_EDITOR_ERROR"].includes(event.data?.type)) {
    if (event.data?.payload?.entryId !== String(orchestrationAttempt)) return;
    window.clearTimeout(orchestrationLoadTimer);
    orchestrationLoading.value = false;
    if (event.data.type === "YUNTI_EDITOR_ERROR") {
      orchestrationErrorCode.value = Number(event.data.payload?.code) || 0;
      orchestrationError.value = entryErrorText(event.data.payload?.message);
    }
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
const actionColumns = [
  {
    type: "buttons",
    prop: "action",
    label: "操作",
    align: "right",
    buttons: [
      { type: "primary", label: "编排", click: openOrchestration },
      { type: "primary", label: "编辑", click: handleEdit },
      { type: "danger", label: "删除", click: handleDelete },
    ],
  },
];
const onModalClose = async (result) => {
  // 当前路由以完整地址为页面标识；打开时清理参数会重建页面并关闭登记框。
  if (route.query.register === '1') {
    const query = { ...route.query }; delete query.register;
    await router.replace({ path: route.path, query });
    return;
  }
  if (result === "success") {
    Promise.all([loadGroups(), tableRef.value?.refresh?.()]);
  }
};

// 代理登记返回的页面可能仍在缓存中，路由意图不能仅在首次挂载时处理。
watch(() => [route.path, route.query.register], ([path, register]) => {
  if (path !== '/deal/data-server/list' || register !== '1') return;
  openRegister();
}, { immediate: true });

onMounted(async () => {
  window.addEventListener("message", handleOrchestrationMessage);
  await loadGroups();
});
onUnmounted(() => {
  closeOrchestration();
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
  background: transparent;
}

.group-node:hover .group-actions,
.group-node:focus-within .group-actions {
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

.service-identity { min-width: 0; }
.service-code {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.orchestration-iframe--pending { visibility: hidden; pointer-events: none; }

.flow-entry {
  position: absolute; inset: 0; z-index: 8; min-height: 100%; display: grid; place-items: center;
  overflow: auto; padding: 28px 20px; color: #263c58; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  background: radial-gradient(ellipse at 50% 40%, #fff 0%, #f4f8fd 50%, #edf3fb 100%);
  background-color: #f4f8fd; box-sizing: border-box;
}
.flow-entry::before { content: ''; position: absolute; inset: 0; pointer-events: none; opacity: .5; background-image: radial-gradient(#c1d2e5 .7px, transparent .7px); background-size: 22px 22px; mask-image: radial-gradient(ellipse at center, transparent 20%, #000 90%); }
.flow-entry__card { position: relative; width: min(520px, 100%); box-sizing: border-box; padding: 28px 38px 22px; text-align: center; border: 1px solid #fff; border-radius: 22px; background: #ffffffdb; box-shadow: 0 22px 70px #234e7c09, 0 2px 6px #234e7c03; }
.flow-entry__eyebrow { display: flex; justify-content: center; align-items: center; gap: 8px; color: #71849c; font-size: 12px; letter-spacing: 1px; }
.flow-entry__eyebrow i { width: 6px; height: 6px; border-radius: 2px; background: #5a89d5; transform: rotate(45deg); }
.flow-entry__visual { position: relative; width: 310px; max-width: 100%; height: 132px; margin: 20px auto 12px; }
.flow-entry__guide { position: absolute; inset: 0 25%; border-radius: 50%; background: radial-gradient(ellipse, #e9f2ff 0%, transparent 70%); }
.flow-entry__node { position: absolute; top: 39px; width: 52px; height: 52px; display: grid; place-items: center; box-sizing: border-box; background: #fff; border: 1px solid #dce6f2; border-radius: 13px; box-shadow: 0 6px 18px #355d880b; }
.flow-entry__node svg { width: 26px; height: 26px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.flow-entry__node span { position: absolute; top: 61px; width: 80px; color: #8391a4; font-size: 11px; letter-spacing: 2px; }
.flow-entry__node--input { left: 4px; color: #5b9f95; }
.flow-entry__node--core { left: calc(50% - 31px); top: 34px; width: 62px; height: 62px; color: #4f7fc9; border-color: #bed2ef; background: #f5f9ff; box-shadow: 0 0 0 6px #edf4ff, 0 8px 22px #3c76c414; animation: flow-entry-breathe 2.4s ease-in-out infinite; }
.flow-entry__node--core span { top: 66px; }
.flow-entry__node--output { right: 4px; color: #8c95bd; }
.flow-entry__wire { position: absolute; top: 65px; height: 1px; width: calc(50% - 87px); overflow: hidden; background: #d6e3f4; }
.flow-entry__wire--first { left: 56px; }
.flow-entry__wire--second { left: calc(50% + 31px); }
.flow-entry__wire i { position: absolute; top: -1px; width: 20px; height: 3px; border-radius: 3px; background: #6e9bdb; animation: flow-entry-travel 2.4s linear infinite; }
.flow-entry__wire--second i { animation-delay: 1.2s; }
.flow-entry h1 { margin: 0; color: #263c58; font-size: 21px; font-weight: 600; line-height: 1.5; letter-spacing: .2px; }
.flow-entry__description { max-width: 390px; margin: 10px auto 0; color: #8390a3; font-size: 13px; line-height: 1.85; text-wrap: pretty; }
.flow-entry__progress { display: flex; justify-content: center; gap: 6px; margin: 22px 0; }
.flow-entry__progress i { width: 4px; height: 4px; border-radius: 50%; background: #80a4d7; animation: flow-entry-dot 1.4s ease-in-out infinite; }
.flow-entry__progress i:nth-child(2) { animation-delay: .18s; }.flow-entry__progress i:nth-child(3) { animation-delay: .36s; }
.flow-entry__footer { margin-top: 22px; padding-top: 17px; border-top: 1px solid #edf1f7; color: #a0abbb; font-size: 11px; letter-spacing: 1px; }
.flow-entry__notice { position: absolute; top: 23px; left: calc(50% + 18px); width: 22px; height: 22px; border: 3px solid #fff; border-radius: 50%; color: #b17b37; background: #fff0d9; font-size: 13px; font-weight: 600; line-height: 22px; }
.flow-entry--error .flow-entry__node--core { color: #bd9155; border-color: #e5d8c6; background: #fffbf5; box-shadow: 0 0 0 6px #fcf6ee; animation: none; }
.flow-entry--error .flow-entry__wire { background: repeating-linear-gradient(90deg, #d6dfeb 0 4px, transparent 4px 8px); }.flow-entry--error .flow-entry__wire i { display: none; }
.flow-entry__actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 10px; margin-top: 23px; }
.flow-entry__button { min-height: 36px; padding: 7px 18px; border: 1px solid #dbe4ef; border-radius: 7px; background: #fff; color: #57718f; font: inherit; font-size: 13px; cursor: pointer; transition: background .15s, box-shadow .15s; }
.flow-entry__button:hover { background: #f4f8fd; }.flow-entry__button:focus-visible { outline: 3px solid #b4cef5; outline-offset: 3px; }
.flow-entry__button--primary { color: #fff; border-color: #4a7ed0; background: #4a7ed0; box-shadow: 0 3px 8px #4a7ed025; }.flow-entry__button--primary:hover { background: #3d70c2; }
.flow-entry__details { margin-top: 17px; text-align: center; color: #94a0b1; font-size: 11px; }.flow-entry__details summary { cursor: pointer; }.flow-entry__details pre { max-height: 110px; padding: 10px; overflow: auto; text-align: left; white-space: pre-wrap; word-break: break-word; background: #f6f8fb; color: #748297; border-radius: 6px; }
@keyframes flow-entry-travel { from { left: -20px; } to { left: 100%; } }
@keyframes flow-entry-breathe { 50% { box-shadow: 0 0 0 9px #edf4ff70, 0 8px 22px #3c76c414; } }
@keyframes flow-entry-dot { 0%, 80%, 100% { opacity: .3; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
@media (max-width: 480px) { .flow-entry { padding: 18px 14px; }.flow-entry__card { padding: 24px 22px 20px; border-radius: 18px; }.flow-entry h1 { font-size: 19px; }.flow-entry__visual { width: 280px; margin-top: 15px; } }
@media (max-height: 480px) { .flow-entry { padding: 16px; }.flow-entry__card { padding-top: 18px; padding-bottom: 14px; }.flow-entry__visual { height: 114px; margin-top: 8px; margin-bottom: 6px; }.flow-entry__footer { margin-top: 16px; padding-top: 12px; } }
@media (prefers-reduced-motion: reduce) { .flow-entry *, .flow-entry *::before { animation: none !important; transition: none !important; } }

</style>
