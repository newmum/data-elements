<template>
  <div class="approval-center card-container flex h-full flex-col px-5 pt-5 pb-2">
    <div class="summary-strip">
      <button
        v-for="item in summaryItems"
        :key="item.value"
        type="button"
        class="summary-item"
        :class="{ active: state.view === item.value }"
        @click="changeView(item.value)"
      >
        <span class="summary-icon" :class="item.value"><Icon :icon="item.icon" /></span>
        <span class="summary-copy">
          <strong>{{ state.summary[item.value] || 0 }}</strong>
          <span>{{ item.label }}</span>
        </span>
      </button>
    </div>

    <div class="toolbar mb-5">
      <el-segmented v-model="state.view" :options="viewOptions" @change="changeView" />
      <div class="filters">
        <el-select
          v-model="state.flowCode"
          clearable
          placeholder="流程类型"
          class="flow-filter"
          @change="refresh"
        >
          <el-option
            v-for="item in state.definitions"
            :key="item.flowCode"
            :label="item.flowName"
            :value="item.flowCode"
          />
        </el-select>
        <el-input
          v-model.trim="state.keyword"
          clearable
          class="keyword-input"
          placeholder="搜索流程名称或业务标题"
          @clear="refresh"
          @keydown.enter="refresh"
        >
          <template #suffix>
            <span class="search-trigger" title="查询" @click="refresh"><Icon icon="search" /></span>
          </template>
        </el-input>
        <el-button circle plain icon="refresh" title="刷新" @click="refresh" />
        <el-button plain icon="user" @click="openDelegation">委托设置</el-button>
      </div>
    </div>

    <data-table ref="tableRef" show-index :columns="columns" :data="fetchData">
      <template #column-businessName="{ row }">
        <div class="business-cell">
          <span class="business-icon"><Icon icon="document" /></span>
          <div>
            <el-text class="business-title" truncated>{{ row.businessName || row.flowName }}</el-text>
            <span class="business-code">{{ row.businessId || '-' }}</span>
          </div>
        </div>
      </template>
      <template #column-flowStatus="{ row }">
        <el-tag :type="statusMeta(row.flowStatus).type" effect="light" round>
          {{ statusMeta(row.flowStatus).label }}
        </el-tag>
      </template>
      <template #column-nodeName="{ row }">
        <span>{{ row.nodeName || (state.view === 'archive' ? '流程结束' : '-') }}</span>
      </template>
    </data-table>

    <approval-modal
      v-model="state.approvalVisible"
      :task-id="state.current?.id"
      :flow-status-type="state.view"
      :info="state.current"
      @close="state.approvalVisible = false"
      @submit-success="afterOperation"
    />
    <el-drawer v-model="state.detailVisible" title="审批进度" size="520px" destroy-on-close>
      <el-skeleton v-if="state.detailLoading" animated :rows="8" />
      <time-line
        v-else-if="state.nodes.length"
        :list="state.nodes.map((item) => ({ ...item, nodeType: item.skipType, org: item.orgName }))"
      />
      <el-empty v-else description="暂无审批记录" />
    </el-drawer>
    <el-dialog v-model="state.delegationVisible" title="审批委托" width="680px" destroy-on-close>
      <div class="delegation-form">
        <el-select
          v-model="state.delegation.delegateUserId"
          filterable
          remote
          :remote-method="searchUsers"
          :loading="state.userLoading"
          placeholder="选择受托人"
        >
          <el-option
            v-for="user in state.users"
            :key="user.storageId"
            :label="`${user.handlerName}（${user.handlerCode || '-'}）`"
            :value="user.storageId"
          />
        </el-select>
        <el-date-picker
          v-model="state.delegation.range"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-button type="primary" :loading="state.delegationSaving" @click="saveDelegation">新增委托</el-button>
      </div>
      <el-table :data="state.delegations" height="300" border>
        <el-table-column prop="delegateUserName" label="受托人" min-width="120" />
        <el-table-column prop="flowCode" label="适用流程" min-width="120">
          <template #default="{ row }">{{ row.flowCode || "全部流程" }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="165" />
        <el-table-column prop="endTime" label="结束时间" width="165" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="danger" link @click="deleteDelegation(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";

const tableRef = ref();
const state = reactive<any>({
  view: "todo",
  keyword: "",
  flowCode: "",
  summary: { todo: 0, process: 0, done: 0, archive: 0 },
  definitions: [],
  current: null,
  approvalVisible: false,
  detailVisible: false,
  detailLoading: false,
  nodes: [],
  delegationVisible: false,
  delegationSaving: false,
  userLoading: false,
  users: [],
  delegations: [],
  delegation: { delegateUserId: "", range: [] },
});

const summaryItems = [
  { label: "待我审批", value: "todo", icon: "clock" },
  { label: "我发起的", value: "process", icon: "promotion" },
  { label: "我已办理", value: "done", icon: "circle-check" },
  { label: "已归档", value: "archive", icon: "folder-checked" },
];
const viewOptions = summaryItems.map((item) => ({ label: item.label, value: item.value }));

const columns = computed(() => [
  { label: "审批事项", prop: "businessName", minWidth: 280 },
  { label: "流程类型", prop: "flowName", minWidth: 170 },
  { label: "当前节点", prop: "nodeName", minWidth: 150 },
  { label: "发起人", prop: "createName", width: 120, props: { emptyText: "-" } },
  { label: "状态", prop: "flowStatus", width: 110, align: "center" },
  { label: state.view === "done" ? "办理时间" : "更新时间", prop: "updateTime", width: 180, align: "center" },
  {
    type: "buttons",
    label: "操作",
    prop: "action",
    width: 150,
    buttons: [
      {
        if: () => state.view === "todo",
        type: "primary",
        label: "审批",
        click: (row) => approve(row),
      },
      {
        if: () => state.view === "process",
        type: "warning",
        label: "催办",
        click: (row) => remind(row),
      },
      { type: "primary", label: "进度", click: (row) => showProgress(row) },
    ],
  },
]);

const fetchData = async ({ pageNo: pageNum, pageSize }) => {
  const result = await $common.post("/sym/approval/query/page", {
    pageNum,
    pageSize,
    flowSatusType: state.view,
    flowCode: state.flowCode,
    key: state.keyword,
  });
  return { list: result?.list || [], total: result?.total || 0 };
};

const statusMeta = (status) => {
  const map = {
    "1": { label: "审批中", type: "primary" },
    "2": { label: "已通过", type: "success" },
    "4": { label: "已终止", type: "danger" },
    "6": { label: "已撤回", type: "info" },
    "8": { label: "已完成", type: "success" },
    "9": { label: "已退回", type: "warning" },
    "11": { label: "已拿回", type: "warning" },
  };
  return map[String(status)] || { label: "待处理", type: "info" };
};

const refreshSummary = async () => {
  state.summary = { ...state.summary, ...(await $common.get("/sym/approval/query/summary")) };
};
const loadDefinitions = async () => {
  state.definitions = (await $common.get("/sym/approval/query/definitionList")) || [];
};
const refresh = () => tableRef.value?.refresh(true);
const changeView = (view) => {
  state.view = view;
  refresh();
};
const approve = (row) => {
  state.current = row;
  state.approvalVisible = true;
};
const afterOperation = () => {
  state.approvalVisible = false;
  refreshSummary();
  refresh();
};
const remind = async (row) => {
  await $common.post("/sym/approval/reminder/send", {
    instanceId: row.instanceId,
    message: `请及时处理【${row.businessName || row.flowName}】`,
  });
  $message.success("催办已发送");
};
const openDelegation = async () => {
  state.delegationVisible = true;
  state.delegations = (await $common.get("/sym/approval/delegation/list")) || [];
  searchUsers("");
};
const searchUsers = async (keyword) => {
  state.userLoading = true;
  try {
    const response = await $common.get("/warm-flow/handler-result", {
      handlerType: "用户", handlerName: keyword, pageNum: 1, pageSize: 50,
    });
    const data = response?.data || response;
    state.users = data?.handlerAuths?.list || data?.handlerAuths?.records || [];
  } finally {
    state.userLoading = false;
  }
};
const saveDelegation = async () => {
  if (!state.delegation.delegateUserId || state.delegation.range?.length !== 2) {
    $message.warning("请选择受托人和有效期");
    return;
  }
  state.delegationSaving = true;
  try {
    await $common.post("/sym/approval/delegation/save", {
      delegateUserId: state.delegation.delegateUserId,
      startTime: state.delegation.range[0],
      endTime: state.delegation.range[1],
    });
    $message.success("委托设置成功");
    state.delegation = { delegateUserId: "", range: [] };
    state.delegations = (await $common.get("/sym/approval/delegation/list")) || [];
  } finally {
    state.delegationSaving = false;
  }
};
const deleteDelegation = async (row) => {
  await $common.post("/sym/approval/delegation/delete", { tid: row.tid });
  $message.success("委托已取消");
  state.delegations = (await $common.get("/sym/approval/delegation/list")) || [];
};
const showProgress = async (row) => {
  state.detailVisible = true;
  state.detailLoading = true;
  state.nodes = [];
  try {
    const result = await $common.get("/sym/approval/info", { businessId: row.businessId });
    state.nodes = result?.flowNodes || [];
  } finally {
    state.detailLoading = false;
  }
};

onMounted(() => Promise.all([refreshSummary(), loadDefinitions()]));
</script>

<style scoped lang="scss">
.approval-center { min-width: 0; }
.summary-strip { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 18px; overflow: hidden; }
.summary-item { display: flex; align-items: center; gap: 12px; min-height: 76px; padding: 12px 18px; border: 0; border-right: 1px solid #e4e7ed; background: #fff; color: #303133; text-align: left; cursor: pointer; transition: background-color .18s, box-shadow .18s; }
.summary-item:last-child { border-right: 0; }
.summary-item:hover { background: #f7f9fc; }
.summary-item.active { background: #f2f7ff; box-shadow: inset 0 -3px 0 #1677ff; }
.summary-icon { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 6px; color: #1677ff; background: #eaf3ff; font-size: 19px; }
.summary-icon.done { color: #00a870; background: #e9f8f2; }
.summary-icon.archive { color: #667085; background: #f1f3f5; }
.summary-icon.process { color: #7a5af8; background: #f1edff; }
.summary-copy { display: flex; flex-direction: column; gap: 2px; }
.summary-copy strong { font-size: 22px; line-height: 28px; }
.summary-copy span { color: #667085; font-size: 13px; }
.toolbar, .filters { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.flow-filter { width: 170px; }
.keyword-input { width: 280px; }
.search-trigger { display: inline-flex; cursor: pointer; color: #1677ff; }
.business-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.business-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 5px; color: #1677ff; background: #eef5ff; }
.business-cell > div { min-width: 0; display: flex; flex-direction: column; }
.business-title { max-width: 240px; font-weight: 600; }
.business-code { color: #98a2b3; font-size: 12px; line-height: 18px; }
.delegation-form { display: grid; grid-template-columns: 180px 1fr auto; gap: 10px; margin-bottom: 14px; }
@media (max-width: 980px) { .summary-strip { grid-template-columns: repeat(2, 1fr); } .summary-item:nth-child(2) { border-right: 0; } .toolbar { align-items: stretch; flex-direction: column; } .filters { width: 100%; } .keyword-input { flex: 1; width: auto; } }
</style>
