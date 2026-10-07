<template>
  <div class="flow-config card-container flex h-full flex-col px-5 pt-5 pb-2">
    <div class="mb-5 flex-x-between">
      <div>
        <el-button type="primary" icon="plus" @click="openCreate">新建流程</el-button>
      </div>
      <div class="filters">
        <el-select
          v-model="state.publishStatus"
          clearable
          placeholder="发布状态"
          class="status-filter"
          @change="refresh"
        >
          <el-option label="已发布" :value="1" />
          <el-option label="未发布" :value="0" />
        </el-select>
        <el-input
          v-model.trim="state.keyword"
          clearable
          class="keyword-input"
          placeholder="搜索流程名称或编码"
          @clear="refresh"
          @keydown.enter="refresh"
        >
          <template #suffix>
            <span class="search-trigger" title="查询" @click="refresh"><Icon icon="search" /></span>
          </template>
        </el-input>
        <el-button circle plain icon="refresh" title="刷新" @click="refresh" />
      </div>
    </div>

    <data-table ref="tableRef" show-index :columns="columns" :data="fetchData">
      <template #column-flowName="{ row }">
        <el-link type="primary" :underline="false" @click="openDesigner(row)">
          <Icon icon="connection" class="mr-1" />{{ row.flowName }}
        </el-link>
      </template>
      <template #column-isPublish="{ row }">
        <el-tag :type="Number(row.isPublish) === 1 ? 'success' : 'info'" effect="light" round>
          {{ Number(row.isPublish) === 1 ? "已发布" : "未发布" }}
        </el-tag>
      </template>
      <template #column-activityStatus="{ row }">
        <span class="state-dot" :class="{ enabled: Number(row.activityStatus) !== 0 }"></span>
        {{ Number(row.activityStatus) === 0 ? "已停用" : "启用中" }}
      </template>
    </data-table>

    <el-dialog v-model="state.dialogVisible" title="新建审批流程" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="state.form" :rules="rules" label-width="88px">
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model.trim="state.form.flowName" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="流程编码" prop="flowCode">
          <el-input v-model.trim="state.form.flowCode" maxlength="64" placeholder="例如 assetRegister" />
        </el-form-item>
        <el-form-item label="流程分类" prop="category">
          <el-select v-model="state.form.category" allow-create filterable class="w-full">
            <el-option label="数据资产" value="数据资产" />
            <el-option label="数据接入" value="数据接入" />
            <el-option label="数据服务" value="数据服务" />
            <el-option label="系统管理" value="系统管理" />
          </el-select>
        </el-form-item>
        <el-form-item label="设计模式">
          <el-segmented v-model="state.form.modelValue" :options="modelOptions" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="state.dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="state.saving" @click="createFlow">创建并设计</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const tableRef = ref();
const formRef = ref();
const state = reactive<any>({
  keyword: "",
  publishStatus: "",
  dialogVisible: false,
  saving: false,
  form: {},
});
const modelOptions = [
  { label: "经典模式", value: "CLASSICS" },
  { label: "简洁模式", value: "SIMPLICITY" },
];
const rules = {
  flowName: [{ required: true, message: "请输入流程名称", trigger: "blur" }],
  flowCode: [
    { required: true, message: "请输入流程编码", trigger: "blur" },
    { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/, message: "以字母开头，仅支持字母、数字、下划线和横线", trigger: "blur" },
  ],
  category: [{ required: true, message: "请选择流程分类", trigger: "change" }],
};

const columns = [
  { label: "流程名称", prop: "flowName", minWidth: 210 },
  { label: "流程编码", prop: "flowCode", minWidth: 180 },
  { label: "分类", prop: "category", width: 130, props: { emptyText: "通用审批" } },
  { label: "版本", prop: "version", width: 90, align: "center" },
  { label: "发布状态", prop: "isPublish", width: 110, align: "center" },
  { label: "运行状态", prop: "activityStatus", width: 110, align: "center" },
  { label: "更新时间", prop: "updateTime", width: 180, align: "center" },
  {
    type: "buttons",
    label: "操作",
    prop: "action",
    width: 260,
    buttons: [
      { type: "primary", label: "设计", click: (row) => openDesigner(row) },
      {
        type: "success",
        label: (row) => Number(row.isPublish) === 1 ? "取消发布" : "发布",
        click: (row) => execute(row, Number(row.isPublish) === 1 ? "unPublish" : "publish"),
      },
      { type: "primary", label: "复制", click: (row) => execute(row, "copy") },
      { type: "danger", label: "删除", click: (row) => execute(row, "delete") },
    ],
  },
];

const fetchData = async ({ pageNo: pageNum, pageSize }) => {
  const result = await $common.post("/sym/approval/config/list", {
    pageNum,
    pageSize,
    keyword: state.keyword,
    publishStatus: state.publishStatus,
  });
  return { list: result?.list || [], total: result?.total || 0 };
};
const refresh = () => tableRef.value?.refresh(true);
const openCreate = () => {
  state.form = { flowName: "", flowCode: "", category: "数据资产", modelValue: "CLASSICS" };
  state.dialogVisible = true;
};
const openDesigner = (row) => {
  router.push({ path: "/setting/flow-config/flow-designer", query: { id: row.id, title: row.flowName } });
};
const createFlow = async () => {
  await formRef.value?.validate();
  state.saving = true;
  try {
    const result = await $common.post("/sym/approval/config/saveOrUpdate", state.form);
    state.dialogVisible = false;
    openDesigner({ id: result.id, flowName: state.form.flowName });
  } finally {
    state.saving = false;
  }
};
const execute = async (row, action) => {
  const actionName = { publish: "发布", unPublish: "取消发布", copy: "复制", delete: "删除" }[action];
  try {
    await $dialog({
      title: `${actionName}流程`,
      message: `确定${actionName}流程【${row.flowName}】？`,
      showCancelButton: true,
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: action === "delete" ? "warning" : "info",
    });
  } catch {
    return;
  }
  await $common.post("/sym/approval/config/exec", { id: row.id, action });
  $message.success(`${actionName}成功`);
  refresh();
};
</script>

<style scoped lang="scss">
.flow-config { min-width: 0; }
.filters { display: flex; align-items: center; gap: 12px; }
.status-filter { width: 130px; }
.keyword-input { width: 280px; }
.search-trigger { display: inline-flex; cursor: pointer; color: #1677ff; }
.state-dot { display: inline-block; width: 7px; height: 7px; margin-right: 6px; border-radius: 50%; background: #c0c4cc; }
.state-dot.enabled { background: #00a870; box-shadow: 0 0 0 3px #e8f7f1; }
@media (max-width: 800px) { .filters { width: 100%; } .keyword-input { flex: 1; width: auto; } }
</style>
