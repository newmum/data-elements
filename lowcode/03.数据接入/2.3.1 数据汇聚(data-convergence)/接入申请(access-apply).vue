<template>
  <el-drawer
    v-model="open"
    :title="drawerTitle"
    direction="rtl"
    size="760px"
    :body-class="'access-apply'"
    :close-on-click-modal="true"
    @close="handleCancel"
  >
    <template #header>
      <div class="access-apply-drawer-header">
        <div class="access-apply-view-tabs" role="tablist" aria-label="建表配置视图">
          <button
            type="button"
            class="access-apply-view-tab"
            :class="{ 'is-active': !ddlMode }"
            role="tab"
            :aria-selected="!ddlMode"
            @click="showFormView"
          >物化建表</button>
          <button
            type="button"
            class="access-apply-view-tab"
            :class="{ 'is-active': ddlMode }"
            role="tab"
            :aria-selected="ddlMode"
            :disabled="ddlGenerating || templateLoading || !!loadError"
            @click="showDdlView"
          >查看DDL语句</button>
        </div>
        <span
          v-if="editable && targetTablePlans.length"
          class="access-apply-target-summary"
          :title="targetTablePlanSummary"
        >
          {{ targetTablePlanSummary }}
        </span>
      </div>
    </template>
    <div
      v-loading="templateLoading || isSaveing"
      :element-loading-text="loadingText"
      element-loading-background="rgba(255, 255, 255, 0.88)"
      class="access-apply-content"
    >
      <div v-if="loadError && !templateLoading" class="template-load-error" role="alert">
        <el-alert :title="loadError" type="error" :closable="false" show-icon />
        <el-button type="primary" plain @click="init">重试加载</el-button>
      </div>
      <div v-show="!ddlMode && !loadError" class="materialization-form-view">
      <u-title name="建表信息" />
      <div v-if="knownTargets.length" class="existing-target-panel">
        <strong>已有目标表</strong>
        <span>以下目标表已与当前来源表关联，可保留并只创建流程，或单独删除后重新物化。</span>
        <div v-for="target in knownTargets" :key="target.targetTableId" class="existing-target-row">
          <span :title="target.targetTableName">{{ target.targetDbName || target.targetDbId }} / {{ target.targetTableName }}</span>
          <el-button link type="danger" :disabled="isSaveing || recoveryDeleting" @click="handleDeleteExistingTarget(target)">删除目标表</el-button>
        </div>
      </div>
      <JsonForm
        ref="jsonFormRef"
        :data="formData"
        :rules="formRules"
        label-width="140px"
        bordered
        class="mb-2"
      ></JsonForm>
      <div class="materialization-field-toolbar">
        <el-button type="primary" :disabled="!editable || ddlMode" @click="handleAddField">＋ 新增数据项</el-button>
        <el-button type="primary" plain :disabled="!editable || ddlMode" @click="handleOpenDefaultFieldsDrawer">默认字段</el-button>
      </div>
      <v-table
        ref="tableRef"
        :options="tableColumns"
        :editable="editable"
        :show-operation="editable"
        :show-toolbar="false"
        class="api-param-table"
        @btn-click="handleTableBtnClick"
      >
        <template #primaryKey="{ row }">
          <el-checkbox
            :model-value="row.primaryKey === 1"
            :disabled="!editable"
            @update:model-value="(val) => (row.primaryKey = val ? 1 : 0)"
          />
        </template>
        <template #nullable="{ row }">
          <el-checkbox
            :model-value="row.nullable === 0"
            :disabled="!editable"
            @update:model-value="(val) => (row.nullable = val ? 0 : 1)"
          />
        </template>
      </v-table>
      </div>
      <div v-if="ddlMode && !loadError" class="ddl-inline-panel">
        <el-tabs v-if="ddlPlans.length > 1" v-model="activeDdlKey" class="ddl-target-tabs">
          <el-tab-pane v-for="plan in ddlPlans" :key="plan.key" :name="plan.key" :label="plan.dbName" />
        </el-tabs>
        <CodeEditor
          :key="activeDdlKey"
          v-model="activeDdlSql"
          lang="sql"
          theme="chrome"
          :height="'100%'"
          :show-option="false"
          :read-only="!editable"
        />
      </div>
    </div>

    <template #footer>
      <div class="flex" style="justify-content: flex-end">
        <el-button
          v-if="recoveryTarget && !knownTargets.some((target) => target.targetTableId === recoveryTarget.targetTableId)"
          type="danger"
          plain
          :loading="recoveryDeleting"
          :disabled="isSaveing || recoveryDeleting"
          @click="handleDeleteExistingTarget()"
        >
          删除已有目标表
        </el-button>
        <el-button
          v-if="editable"
          type="primary"
          :loading="isSaveing"
          :disabled="templateLoading || isSaveing || !!loadError"
          @click="handleSave"
        >
          <Icon v-if="!isSaveing" icon="save" class="mr-1" />
          {{ isSaveing ? "正在物化" : "提交" }}
        </el-button>

        <el-button :icon="'el-icon-Close'" :disabled="isSaveing" @click="handleCancel">关闭</el-button>
      </div>
    </template>
  </el-drawer>

  <el-dialog
    v-model="existingDecisionVisible"
    title="目标表已存在"
    width="560px"
    :close-on-click-modal="false"
    @closed="resolveExistingDecision('cancel')"
  >
    <p>{{ existingDecisionSummary }}</p>
    <p v-if="!existingDecision.canReuse" class="existing-target-warning">
      当前选择的目标表缺少完整、有效的本来源表登记关系，不能直接复用建流程。请删除后重新物化，或取消并修改目标表名。
    </p>
    <p v-if="existingDecisionHasUnlinked" class="existing-target-warning">
      其中有目标表已单独登记、但未关联当前来源表。删除前请核对其数据用途；确认删除后只清理所选目标表，不影响当前来源表的其他任务。
    </p>
    <template #footer>
      <el-button @click="resolveExistingDecision('cancel')">取消</el-button>
      <el-button type="primary" :disabled="!existingDecision.canReuse" @click="resolveExistingDecision('flow')">只创建流程</el-button>
      <el-button type="danger" @click="resolveExistingDecision('delete')">删除目标表并重新物化</el-button>
    </template>
  </el-dialog>

  <!-- 默认字段弹窗 -->
  <el-drawer
    v-model="defaultFieldsVisible"
    title="默认字段添加"
    size="700"
    :close-on-click-modal="true"
  >
    <DataTable
      ref="defaultFieldsTableRef"
      :columns="defaultFieldsColumns"
      :data="defaultFieldsData"
      :show-selection="true"
      :show-page="false"
      :flex-type="'flex-[0_1_auto]'"
      :rowKey="'columnName'"
      @selection-change="handleSelectionChange"
    />
    <template #footer>
      <div class="flex justify-end">
        <el-button type="primary" @click="handleConfirmDefaultFields">确定</el-button>
        <el-button @click="defaultFieldsVisible = false">取消</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage, ElMessageBox } from "element-plus";
import { useDictStore } from "@/store";

// Props
const props = defineProps<{
  modelValue: boolean;
  type: string; // add/view
  id: string;
  existingTargets?: any[];
  // 可由入口页面指定目标域编码；未指定时保持原有的首项默认行为。
  defaultTargetDomainCode?: string;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "save", payload: any): void;
  (e: "target-deleted", payload: any): void;
  (e: "update:modelValue", value: boolean): void;
}>();

const dictStore = useDictStore();
const colTypeOptions = dictStore.getDictItems("tableColType");
const formData = ref<any>({});
const jsonFormRef = ref<any>(null);
const drawerTitle = ref("物化建表");
const ddlMode = ref(false);
const ddlGenerating = ref(false);
const ddlPlans = ref<any[]>([]);
const ddlSqlByTarget = ref<Record<string, string>>({});
const activeDdlKey = ref("");
const ddlSourceFingerprint = ref("");
const ddlWasEdited = ref(false);
const defaultFieldsVisible = ref(false);
const activeDdlSql = computed({
  get: () => ddlSqlByTarget.value[activeDdlKey.value] || "",
  set: (value: string) => {
    ddlSqlByTarget.value = { ...ddlSqlByTarget.value, [activeDdlKey.value]: value };
    ddlWasEdited.value = true;
  },
});
const isSaveing = ref(false);
const recoveryTarget = ref<any>(null);
const knownTargets = ref<any[]>([]);
const existingDecisionVisible = ref(false);
const existingDecision = ref<{ targets: any[]; canReuse: boolean }>({ targets: [], canReuse: false });
let existingDecisionResolve: ((choice: string) => void) | null = null;
const existingDecisionHasUnlinked = computed(() =>
  existingDecision.value.targets.some((target: any) => target.probe?.targetUnlinked === true),
);
const existingDecisionSummary = computed(() => {
  const rows = existingDecision.value.targets;
  return `目标表已存在：${rows.map((row: any) => `${row.targetDbName || row.targetDbId} / ${row.targetTableName}`).join('、')}。请选择保留目标表只创建流程，或删除目标表后重新物化。`;
});
const resolveExistingDecision = (choice: string) => {
  existingDecisionVisible.value = false;
  const resolve = existingDecisionResolve;
  existingDecisionResolve = null;
  resolve?.(choice);
};
const askExistingDecision = (targets: any[], canReuse: boolean): Promise<string> => {
  existingDecision.value = { targets, canReuse };
  existingDecisionVisible.value = true;
  return new Promise((resolve) => { existingDecisionResolve = resolve; });
};
const recoveryDeleting = ref(false);
const templateLoading = ref(false);
const loadError = ref("");
const loadPending = ref({ datasource: false, node: false, source: false });
let initRun = 0;
const datasourceOptions = ref<any[]>([]);
const datasourceMap = ref<Record<string, any>>({});
const datasourcePathMap = ref<Record<string, any[]>>({});
const datasourceLeafIds = ref<string[]>([]);
// 保留接口返回的字段模板。JsonForm 与 VxeGrid 都会在抽屉打开时完成一次
// 异步初始化；只向尚未就绪的表格实例 setData 会被其初始化选项覆盖。
const templateTableItems = ref<any[]>([]);
const nifiNetworkOptions = ref<any[]>([]);
const nifiNodeOptions = ref<any[]>([]);
const nifiNodeTreeOptions = computed(() =>
  nifiNetworkOptions.value
    .map((network: any) => ({
      ...network,
      children: getNifiNodesByNetwork(network.value).map((node: any) => ({
        ...node,
        label: node.nodeName || node.label,
      })),
    }))
    .filter((network: any) => network.children.length)
);
const sourceTableName = ref("");
const loadingText = computed(() => {
  if (isSaveing.value) return "正在物化目标表并登记接入信息...";
  const { datasource, node, source } = loadPending.value;
  if (source && (datasource || node)) return "正在读取来源表结构和建表选项...";
  if (source) return "正在读取来源表结构...";
  if (datasource && node) return "正在读取目标数据源和接入节点...";
  if (datasource) return "正在读取目标数据源...";
  if (node) return "正在读取接入节点...";
  return "正在准备建表信息...";
});

// 默认字段数据
const defaultFieldsData = ref([
  {
    columnComment: "创建时间",
    columnName: "create_time",
    length: 20,
    dataType: "datetime",
    columnType: "datetime",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "更新时间",
    columnName: "update_time",
    length: 20,
    dataType: "datetime",
    columnType: "datetime",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "创建人",
    columnName: "created_by",
    length: 64,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "更新人",
    columnName: "updated_by",
    length: 64,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "备注",
    columnName: "remark",
    length: 500,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "数据状态",
    columnName: "data_status",
    length: 32,
    dataType: "int",
    columnType: "int",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "删除标志",
    columnName: "is_del",
    length: 1,
    dataType: "int",
    columnType: "int",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
]);
const editable = computed(() => props.type === "add");
const selectedDefaultFields = ref<any[]>([]);
const defaultFieldsTableRef = ref();
const createFormRules = () => FormUtils.fixJson([
  {
    field: "sourceDatasourceName",
    title: "来源数据源名称",
    type: "form-text",
    ignore: true,
    col: {
      span: 12,
    },
    props: {
      originType: "input",
      emptyText: "--",
    },
  },
  {
    field: "sourceAccessMode",
    title: "数据接入方式",
    type: "form-text",
    ignore: true,
    col: {
      span: 12,
    },
    props: {
      originType: "input",
      emptyText: "--",
    },
  },
  {
    field: "dbId",
    title: "目标数据源名称",
    type: "dict-select",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      disabled: !editable.value,
      // 保留原生多选交互；已选择路径在输入框中汇总为普通文本，不显示标签。
      filterable: false,
      type: "cascader",
      options: datasourceOptions.value,
      placeholder: "请选择目标数据源",
      plainMultipleText: true,
      showAllLevels: true,
      props: {
        emitPath: true,
        checkStrictly: false,
        multiple: true,
      },
    },
    on: {
      // JsonForm owns the live form state. Mirror the selected paths into
      // formData so the drawer header recalculates before the form is saved.
      change: (value: any) => syncTargetDatasourceSelection(value),
    },
  },
  {
    field: "nifiNodePath",
    title: "数据接入节点",
    type: "dict-select",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      disabled: !editable.value,
      filterable: true,
      type: "cascader",
      options: nifiNodeTreeOptions.value,
      placeholder: "请选择所属网络和数据接入节点",
      props: {
        emitPath: true,
        checkStrictly: false,
        expandTrigger: "click",
      },
    },
    on: {
      change: (value: any) => syncNifiNodeSelection(value),
    },
  },
  {
    field: "tableNameCn",
    title: "数据目录名称",
    type: "input",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      maxlength: 255,
      disabled: !editable.value,
    },
  },
  {
    field: "tableName",
    title: "目标数据表名",
    type: "input",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      maxlength: 255,
      disabled: !editable.value,
    },
    on: {
      // A manually adjusted name must be reflected in the target-table plan
      // shown at the top of the drawer as well.
      change: (value: any) => syncTargetTableNameInput(value),
    },
  },
]);
const tableRef = ref();
const formRules = ref<any[]>(createFormRules());
const tableColumns = ref({
  // 字段名、表注释等可编辑单元格在省略时必须能读取完整值；showAll
  // 同时覆盖 VXE 编辑单元格未触发列级 tooltip 的场景。
  tooltipConfig: {
    showAll: true,
    enterable: true,
  },
  editConfig: {
    showIcon: false,
  },
  columns: [
    { type: "seq", width: 48, align: "center" },
    {
      title: "字段名",
      field: "columnName",
      editRender: {
        name: "VxeInput",
        props: {
          maxlength: 255,
        },
      },
      showOverflow: "tooltip",
    },
    {
      title: "表注释",
      field: "columnComment",
      editRender: {
        name: "VxeInput",
        props: {
          maxlength: 255,
        },
      },
      showOverflow: "tooltip",
    },
    {
      title: "数据类型",
      field: "dataType",
      editRender: { name: "ElSelect", options: colTypeOptions },
    },
    {
      title: "主键",
      field: "primaryKey",
      width: 52,
      align: "center",
      slots: { default: "primaryKey" },
    },
    {
      title: "不为空",
      field: "nullable",
      width: 64,
      align: "center",
      slots: { default: "nullable" },
    },
    {
      title: "长度",
      field: "length",
      width: 100,
      editRender: {
        name: "VxeInput",
        props: {
          type: "number",
          min: 0,
        },
      },
    },
    {
      title: "操作",
      width: 60,
      align: "center",
      fixed: "right",
      slots: { default: "operation" },
    },
  ],
  editRules: {
    columnName: [{ required: true, message: "字段名不能为空", trigger: "blur" }],
    dataType: [{ required: true, message: "数据类型不能为空", trigger: "change" }],
    length: [
      { required: true, message: "长度不能为空", trigger: "blur" },
      {
        validator: ({ cellValue }: any) => {
          if (cellValue === "" || cellValue === null || cellValue === undefined) {
            return true;
          }
          const num = Number(cellValue);
          return !isNaN(num) && num >= 0;
        },
        message: "长度必须为正数",
        trigger: "blur",
      },
    ],
  },
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增数据项",
        status: "primary",
        className: "btn-primary",
      },
      {
        code: "default_field",
        icon: "vxe-icon-setting",
        name: "默认字段",
        status: "primary",
        className: "btn-plain",
      },
    ],
    tools: [],
  },
});
// 默认字段表格列配置
const defaultFieldsColumns = ref([
  { prop: "columnName", label: "字段名", minWidth: 180 },
  { prop: "columnComment", label: "字段注释", minWidth: 150 },
  { prop: "length", label: "长度", width: 100 },
  { prop: "dataType", label: "数据类型", options: "colTypeOptions" },
]);
const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

const normalizeDbId = (value: any) => {
  if (Array.isArray(value)) {
    if (value.length && Array.isArray(value[0])) {
      return normalizeDbId(value[0]);
    }
    return value[value.length - 1];
  }
  return value;
};

const normalizeDbIds = (value: any) => {
  if (value == null || value === "") return [];
  const paths = Array.isArray(value) && Array.isArray(value[0]) ? value : [value];
  return [...new Set(paths.map((item: any) => normalizeDbId(item)).filter(Boolean))];
};

const readList = (payload: any) => {
  if (Array.isArray(payload)) return payload;
  return payload?.list || payload?.records || payload?.rows || payload?.data?.list || [];
};

const getOptionValue = (item: any) => item?.value || item?.tid || item?.id;

const normalizeDatasourcePath = (dbId: any, groupId?: any) => {
  const realDbId = normalizeDbId(dbId);
  if (!realDbId) return [];
  const resolvedPath = datasourcePathMap.value[String(realDbId)];
  if (resolvedPath?.length) return [...resolvedPath];
  const selected = datasourceMap.value[realDbId] || {};
  return [groupId || selected.parentValue || selected.layerCode || "ODS", realDbId];
};

const normalizeDatasourcePaths = (dbIds: any) =>
  normalizeDbIds(dbIds).map((dbId) => normalizeDatasourcePath(dbId));

const normalizeCode = (value: any) => String(value ?? "").trim().toLowerCase();

const getDefaultDatasourcePathByDomainCode = () => {
  const targetDomainCode = normalizeCode(props.defaultTargetDomainCode);
  if (!targetDomainCode) return [];

  const targetDbId = datasourceLeafIds.value.find((dbId) => {
    const datasource = datasourceMap.value[dbId] || {};
    return normalizeCode(
      datasource.domainCode
      ?? datasource.domain_code
      ?? datasource.targetDomainCode
      ?? datasource.target_domain_code,
    ) === targetDomainCode;
  });

  const path = targetDbId ? datasourcePathMap.value[targetDbId] : [];
  return path?.length ? [...path] : [];
};

const getDefaultDatasourcePath = () => {
  const configuredPath = getDefaultDatasourcePathByDomainCode();
  if (configuredPath.length) return configuredPath;

  const firstLeafId = datasourceLeafIds.value[0];
  if (firstLeafId && datasourcePathMap.value[firstLeafId]?.length) {
    return [...datasourcePathMap.value[firstLeafId]];
  }
  const groups = datasourceOptions.value || [];
  const firstGroup = groups.find((group: any) => group?.children?.length);
  const firstDatasource = firstGroup?.children?.[0];
  const dbId = getOptionValue(firstDatasource);
  return firstGroup && dbId ? [firstGroup.value, dbId] : [];
};

const getDefaultDatasourcePaths = () => {
  const path = getDefaultDatasourcePath();
  return path.length ? [path] : [];
};

const nodeEnabled = (item: any) => {
  const rawValue = item?.enabled ?? item?.isEnabled ?? item?.status ?? 1;
  if (rawValue === false) return false;
  const textValue = String(rawValue).toLowerCase();
  return !["0", "false", "disabled", "stop", "\u505c\u7528"].includes(textValue);
};

const nodeDefault = (item: any) => Number(item?.isDefault ?? item?.is_default ?? 0) === 1;

const getNifiNodesByNetwork = (networkCode: any) =>
  nifiNodeOptions.value.filter((item: any) => item.networkCode === String(networkCode || "").trim());

const getDefaultNifiNodeId = (networkCode?: any) => {
  const candidates = networkCode ? getNifiNodesByNetwork(networkCode) : nifiNodeOptions.value;
  return candidates.find((item: any) => item.isDefault)?.value || candidates[0]?.value || "";
};

const resolveNifiNodeId = (value: any, networkCode?: any) => {
  const candidates = networkCode ? getNifiNodesByNetwork(networkCode) : nifiNodeOptions.value;
  if (value && candidates.some((item: any) => item.value === value)) {
    return value;
  }
  return getDefaultNifiNodeId(networkCode);
};

/** Keep the persisted API payload flat while presenting a true two-level selector in the drawer. */
const syncNifiNodeSelection = (value: any) => {
  const path = Array.isArray(value) ? value : [];
  const networkCode = String(path[0] || "").trim();
  const nodeId = resolveNifiNodeId(path[1], networkCode);
  jsonFormRef.value?.setValue?.({
    nifiNodePath: networkCode && nodeId ? [networkCode, nodeId] : [],
    nifiNetworkCode: networkCode,
    nifiNodeId: nodeId,
  });
};

const buildDatasourceOptions = (response: any) => {
  if (response instanceof Error) throw response;
  const payload = response?.data || response || {};
  if (!Array.isArray(payload?.options) || !Array.isArray(payload?.list)) {
    throw new Error("目标数据源选项返回不完整，请重试加载");
  }
  const options = payload?.options || [];
  const list = payload?.list || [];
  const map: Record<string, any> = {};
  const pathMap: Record<string, any[]> = {};
  const leafIds: string[] = [];
  list.forEach((item: any) => {
    const dbId = getOptionValue(item);
    if (!dbId) return;
    map[String(dbId)] = item;
  });
  const collectDatasourceLeaves = (nodes: any[], parentPath: any[] = []) => {
    (nodes || []).forEach((item: any) => {
      const value = getOptionValue(item);
      const children = item?.children || [];
      if (!value) {
        collectDatasourceLeaves(children, parentPath);
        return;
      }
      const path = [...parentPath, value];
      if (children.length) {
        collectDatasourceLeaves(children, path);
        return;
      }
      const dbId = String(value);
      map[dbId] = {
        ...map[dbId],
        ...item,
        parentValue: item.parentValue || parentPath[parentPath.length - 1] || "",
      };
      pathMap[dbId] = path;
      leafIds.push(dbId);
    });
  };
  collectDatasourceLeaves(options);
  datasourceOptions.value = options;
  datasourceMap.value = map;
  datasourcePathMap.value = pathMap;
  datasourceLeafIds.value = leafIds;
};

const loadManagedNifiNodeOptions = (response: any) => {
  if (response instanceof Error) throw response;
  const payload = response?.data || response || {};
  if (!Array.isArray(payload?.networks) || !Array.isArray(payload?.list)) {
    throw new Error("接入节点选项返回不完整，请重试加载");
  }
  const networks = payload?.networks || [];
  nifiNetworkOptions.value = networks
    .map((item: any) => ({
      label: item.label || item.dictName || item.name,
      value: item.value || item.dictCode || item.code,
    }))
    .filter((item: any) => item.label && item.value);
  const rows = payload?.list || readList(payload);
  nifiNodeOptions.value = rows
    .filter(nodeEnabled)
    .sort((a: any, b: any) => Number(nodeDefault(b)) - Number(nodeDefault(a)))
    .map((item: any) => ({
      label: item.label || (item.networkName && (item.nodeName || item.node_name || item.name)
        ? `${item.networkName} · ${item.nodeName || item.node_name || item.name}`
        : item.nodeName || item.node_name || item.name),
      value: item.nodeId || item.tid || item.id || item.value,
      nodeCode: item.nodeCode || item.node_code || "",
      networkCode: item.networkCode || item.network_code || "",
      networkName: item.networkName || item.network_name || "",
      nodeName: item.nodeName || item.node_name || item.name || "",
      baseUrl: item.baseUrl || item.base_url || "",
      isDefault: nodeDefault(item),
    }))
    .filter((item: any) => item.label && item.value);
};

const materializationTimeType = (dbType: any) => {
  const code = String(dbType || "").trim().toLowerCase();
  if (code.includes("oceanbase") || code === "oracle" || code === "dameng" || code === "dm") {
    return "date";
  }
  if ([
    "postgresql", "postgres", "kingbase", "kingbase8", "gaussdb", "opengauss", "gauss",
    "vertica", "hetu", "trino", "presto", "hive",
  ].includes(code)) {
    return "timestamp";
  }
  if (code === "sqlserver") {
    return "datetime2";
  }
  return "datetime";
};

const syncOdsSystemTimeTypes = (dbType: any) => {
  const rows = tableRef.value?.getData?.() || [];
  if (!rows.length) return;
  const targetType = materializationTimeType(dbType);
  let changed = false;
  const normalizedRows = rows.map((row: any) => {
    const name = String(row?.columnName || "").trim().toUpperCase();
    if (name !== "ODS_RKSJ" && name !== "ODS_GXSJ") return row;
    if (String(row.dataType || "").toLowerCase() === targetType
      && String(row.columnType || "").toLowerCase() === targetType) return row;
    changed = true;
    return { ...row, dataType: targetType, columnType: targetType, length: 0 };
  });
  if (changed) {
    tableRef.value?.setData?.(normalizedRows);
  }
};

/** 等待 Grid 就绪后一次写入来源字段，避免大字段表重复渲染。 */
const loadTemplateTableItems = async (items: any) => {
  const rows = Array.isArray(items) ? items.map((item: any) => ({ ...item })) : [];
  templateTableItems.value = rows;
  await nextTick();

  const table = tableRef.value;
  if (!table) throw new Error("字段表格尚未就绪，请重新打开物化建表窗口");
  const grid = table.$grid?.value || table.$grid;
  if (grid?.loadData) {
    await table.loadData?.(rows);
  } else {
    // v-table 尚未挂好内部 Grid 时，响应式 options 是单次写入的兜底。
    table.setData?.(rows);
  }
};

const syncDbTypeByDatasource = (value: any) => {
  const dbId = normalizeDbIds(value)[0];
  const dbType = String(datasourceMap.value[dbId]?.dbType || "").trim();
  if (!dbType) return;
  const currentDbType = String(jsonFormRef.value?.getValue?.("dbType") || "").trim();
  if (currentDbType !== dbType) jsonFormRef.value?.setValue({ dbType });
  syncOdsSystemTimeTypes(dbType);
};

const buildTargetTableName = (value: any) => {
  const selected = datasourceMap.value[normalizeDbId(value)] || {};
  const prefix = String(selected.tablePrefix || selected.layerCode || "ODS").trim().toUpperCase();
  const rawName = String(sourceTableName.value || "").trim();
  if (!prefix || !rawName) return "";
  const prefixPattern = new RegExp(`^${prefix}_`, "i");
  return prefixPattern.test(rawName) ? rawName : `${prefix}_${rawName}`;
};

const sameStringList = (left: string[] = [], right: string[] = []) =>
  left.length === right.length && left.every((value, index) => value === right[index]);

// JsonForm/form-create is the sole owner of editable form values. These refs only
// project the selected target data sources into the drawer header; they never write
// a changed dbId back to formData, so a form change cannot trigger itself again.
const targetDatasourceIds = ref<string[]>([]);
const targetPrimaryTableName = ref("");

const targetTablePlans = computed(() =>
  targetDatasourceIds.value
    .map((dbId, index) => {
      const selected = datasourceMap.value[dbId];
      const tableName = index === 0 && targetPrimaryTableName.value
        ? targetPrimaryTableName.value
        : buildTargetTableName(dbId);
      return { dbId, dbName: selected?.datasourceName || selected?.dbName || selected?.label || "所选目标库", tableName };
    })
    .filter((item) => Boolean(item.dbId && item.tableName)),
);

const targetTablePlanSummary = computed(() => {
  const plans = targetTablePlans.value;
  if (!plans.length) return "请选择目标数据源";
  return plans.map((item) => `在“${item.dbName}”创建“${item.tableName}”`).join("；");
});

const syncTargetDatasourceSelection = (value: any) => {
  const nextIds = normalizeDbIds(value);
  if (!sameStringList(targetDatasourceIds.value, nextIds)) targetDatasourceIds.value = nextIds;
  syncDbTypeByDatasource(value);
  syncTargetTableName(value);
};

const syncTargetTableNameInput = (value: any) => {
  const nextName = String(value || "").trim();
  if (targetPrimaryTableName.value !== nextName) targetPrimaryTableName.value = nextName;
};

const syncTargetTableName = (value: any) => {
  const tableName = buildTargetTableName(normalizeDbIds(value)[0]);
  if (!tableName) return;
  if (targetPrimaryTableName.value !== tableName) targetPrimaryTableName.value = tableName;
  const currentTableName = String(jsonFormRef.value?.getValue?.("tableName") || "").trim();
  if (currentTableName !== tableName) jsonFormRef.value?.setValue({ tableName });
};

const normalizeFormPayload = (data: any) => {
  const targetDbIds = normalizeDbIds(data?.dbId);
  const dbId = targetDbIds[0];
  const selected = datasourceMap.value[dbId];
  const selectedPath = Array.isArray(data?.nifiNodePath) ? data.nifiNodePath : [];
  const selectedNodeId = selectedPath[1] || data?.nifiNodeId;
  const selectedNode = nifiNodeOptions.value.find((item: any) => item.value === selectedNodeId);
  const nifiNetworkCode = String(selectedPath[0] || data?.nifiNetworkCode || selectedNode?.networkCode || "").trim();
  return {
    ...data,
    dbId,
    targetDbIds,
    dbType: selected?.dbType || data?.dbType || "",
    nifiNetworkCode,
    nifiNodeId: resolveNifiNodeId(selectedNodeId, nifiNetworkCode),
  };
};

const isCurrentInit = (runId: number) => runId === initRun && open.value;
const trackLoad = (key: "datasource" | "node" | "source", request: Promise<any>, runId: number) =>
  request.finally(() => {
    if (isCurrentInit(runId)) loadPending.value = { ...loadPending.value, [key]: false };
  });
const cancelPendingInit = () => {
  initRun += 1;
  templateLoading.value = false;
  loadPending.value = { datasource: false, node: false, source: false };
};

const init = async () => {
  const runId = ++initRun;
  loadError.value = "";
  knownTargets.value = (Array.isArray(props.existingTargets) ? props.existingTargets : [])
    .filter((target: any) => target?.targetTableId && target?.targetDbId && target?.targetTableName)
    .map((target: any) => ({ ...target }));
  recoveryTarget.value = knownTargets.value[0] || null;
  const sourceTableId = String(props.id || "").trim();
  // 运行时会预加载该组件；没有实际打开抽屉时不允许以空 tid 请求来源表模板。
  if (!sourceTableId) {
    templateLoading.value = false;
    return;
  }
  templateLoading.value = true;
  loadPending.value = { datasource: true, node: true, source: true };
  try {
    const addUrl = "/ods/getTableTempalte";
    const viewUrl = "/ods/queryTargetTableInfo";
    // 三个请求互不依赖；收到全部结果后再设置表单默认目标库和接入节点。
    const [datasourceResult, nodeResult, sourceResult] = await Promise.allSettled([
      trackLoad("datasource", $common.post("/ods/targetDatasource/options", {}), runId),
      trackLoad("node", $common.post("/ods/nifi-node/access-options", {}), runId),
      trackLoad("source", $common.post(props.type === "add" ? addUrl : viewUrl, {
        tid: sourceTableId,
      }), runId),
    ]);
    if (!isCurrentInit(runId)) return;
    if (datasourceResult.status === "rejected") throw datasourceResult.reason;
    if (nodeResult.status === "rejected") throw nodeResult.reason;
    if (sourceResult.status === "rejected") throw sourceResult.reason;
    const datasourceResponse = datasourceResult.value;
    const nodeResponse = nodeResult.value;
    const res = sourceResult.value;
    buildDatasourceOptions(datasourceResponse);
    loadManagedNifiNodeOptions(nodeResponse);
    formRules.value = createFormRules();
    await nextTick();
    if (!isCurrentInit(runId)) return;
    jsonFormRef.value?.updateFieldOptions?.("dbId", datasourceOptions.value);
    jsonFormRef.value?.updateFieldOptions?.("nifiNodePath", nifiNodeTreeOptions.value);
    if (res instanceof Error) throw res;
    const payload = res?.data || res || {};
    if (!payload?.propList || !Array.isArray(payload?.tableItems)) {
      throw new Error("来源表模板返回不完整，请重试加载");
    }
    // 设置表单数据（表信息）。JsonForm 的受控入参是 data，不是 v-model；
    // 仅调用实例 setValue 时，遇到动态规则重建会丢失只读字段，导致来源信息
    // 和接入方式回显为“--”。先写入响应式 data，再同步给已创建的表单实例。
    const rawPropList = { ...(payload.propList || {}) };
    const propList = {
      ...rawPropList,
      // 兼容历史任务/不同接入方式的字段命名，优先使用来源表模板的标准字段。
      sourceDatasourceName: String(
        rawPropList.sourceDatasourceName
        || rawPropList.sourceDbName
        || rawPropList.datasourceName
        || rawPropList.dbName
        || ""
      ).trim(),
      sourceAccessMode: String(
        rawPropList.sourceAccessMode
        || rawPropList.sourceAccessModeName
        || rawPropList.accessModeName
        || rawPropList.accessMode
        || ""
      ).trim(),
    };
    sourceTableName.value = String(propList.sourceTableName || propList.tableName || "").trim();
    if (props.type === "add") {
      const defaultDbPaths = getDefaultDatasourcePaths();
      propList.dbId = defaultDbPaths.length
        ? defaultDbPaths
        : normalizeDatasourcePaths(propList.dbId);
    } else if (propList.dbId) {
      propList.dbId = normalizeDatasourcePaths(propList.dbId);
    }
    const existingNode = nifiNodeOptions.value.find((item: any) => item.value === propList.nifiNodeId);
    if (props.type === "add") {
      // The source datasource registration owns the network selection.  On a
      // new materialization, retain that network and preselect its managed
      // default node so the cascader is immediately usable without guessing.
      const registeredNetworkCode = String(
        propList.sourceNetworkCode
        || propList.nifiNetworkCode
        || propList.storageDomain
        || propList.storage_domain
        || propList.SSWL
        || ""
      ).trim();
      propList.nifiNetworkCode = registeredNetworkCode;
      propList.nifiNodeId = resolveNifiNodeId("", registeredNetworkCode);
    } else {
      propList.nifiNetworkCode = String(propList.nifiNetworkCode || existingNode?.networkCode || "").trim();
      propList.nifiNodeId = resolveNifiNodeId(propList.nifiNodeId, propList.nifiNetworkCode);
    }
    propList.nifiNodePath = propList.nifiNetworkCode && propList.nifiNodeId
      ? [propList.nifiNetworkCode, propList.nifiNodeId]
      : [];
    formData.value = { ...propList };
    await nextTick();
    if (!isCurrentInit(runId)) return;
    jsonFormRef.value?.setValue(formData.value);
    syncTargetDatasourceSelection(propList.dbId);
    // 设置表格数据(字段列表)。即使字段为空也明确清空上一张来源表的数据，
    // 避免复用抽屉时残留旧内容；非空时由 loadTemplateTableItems 等待 Grid
    // 就绪后再写入，避免首次打开偶发的空表竞态。
    await loadTemplateTableItems(payload.tableItems);
    if (!isCurrentInit(runId)) return;
    if (templateTableItems.value.length) {
      const selected = datasourceMap.value[normalizeDbId(propList.dbId)];
      syncOdsSystemTimeTypes(selected?.dbType || propList.dbType);
    }
  } catch (error: any) {
    if (!isCurrentInit(runId)) return;
    console.error("物化建表模板加载失败", error);
    // 保留错误与重试入口；全局请求层可能已提示，避免再弹重复消息。
    loadError.value = String(error?.message || "物化建表信息加载失败，请重试");
  } finally {
    if (isCurrentInit(runId)) templateLoading.value = false;
  }
};

const handleCancel = () => {
  cancelPendingInit();
  emit("close");
  open.value = false;
};

const buildDdlTargets = (data: any) => data.targetDbIds.map((dbId: string, index: number) => {
  const datasource = datasourceMap.value[dbId] || {};
  return {
    key: dbId,
    dbId,
    dbName: datasource.datasourceName || datasource.dbName || datasource.label || dbId,
    propList: {
      ...data,
      dbId,
      dbType: datasource.dbType || data.dbType,
      tableName: index === 0 ? String(data.tableName || "").trim() : buildTargetTableName(dbId),
    },
  };
});

const currentDdlInput = async () => {
  await jsonFormRef.value?.validate();
  await tableRef.value?.validate();
  const data = normalizeFormPayload(await jsonFormRef.value?.getFormData());
  if (!data.targetDbIds.length) throw new Error("请至少选择一个目标数据源");
  if (data.targetDbIds.length > 20) throw new Error("一次最多选择 20 个目标数据源");
  syncOdsSystemTimeTypes(data.dbType);
  const tableItems = tableRef.value?.getData() || [];
  if (!tableItems.length) throw new Error("请至少添加一个字段");
  const targets = buildDdlTargets(data);
  if (targets.some((target: any) => !target.propList.tableName)) throw new Error("请填写目标表名");
  const fingerprint = JSON.stringify({
    targets: targets.map((target: any) => ({ dbId: target.dbId, dbType: target.propList.dbType, tableName: target.propList.tableName, tableNameCn: target.propList.tableNameCn })),
    tableItems,
  });
  return { targets, tableItems, fingerprint };
};

const regenerateDdl = async (input: any) => {
  ddlGenerating.value = true;
  try {
    const statements: Record<string, string> = {};
    const targets = input.targets.map((target: any) => {
      const targetTimeType = materializationTimeType(target.propList.dbType);
      const targetItems = input.tableItems.map((item: any) => {
        const name = String(item.columnName || "").trim().toUpperCase();
        return name === "ODS_RKSJ" || name === "ODS_GXSJ"
          ? { ...item, dataType: targetTimeType, columnType: targetTimeType, length: 0 }
          : item;
      });
      return { propList: target.propList, tableItems: targetItems };
    });
    const response = await $common.post("/dst/database/metadata/getCreateTableDDL", {
      batchGenerate: true,
      canvas: true,
      targets,
    }, { _hiddenErrorMsg: true });
    const generated = response?.data || response || {};
    if (generated.batchGenerate !== true || !Array.isArray(generated.results)
      || generated.results.length !== input.targets.length) {
      throw new Error("目标表批量建表语句生成结果不完整，请重试");
    }
    for (const [index, target] of input.targets.entries()) {
      const result = generated.results[index];
      if (String(result?.dbId) !== String(target.dbId)) {
        throw new Error("目标表批量建表语句生成结果与所选数据源不一致，请重试");
      }
      const statement = String(result?.ddl || "").trim();
      if (!statement) throw new Error(`目标数据源“${target.dbName}”未生成建表语句`);
      statements[target.key] = statement;
    }
    ddlPlans.value = input.targets;
    ddlSqlByTarget.value = statements;
    activeDdlKey.value = input.targets[0]?.key || "";
    ddlSourceFingerprint.value = input.fingerprint;
    ddlWasEdited.value = false;
  } finally {
    ddlGenerating.value = false;
  }
};

const showFormView = () => {
  ddlMode.value = false;
};

const showDdlView = async () => {
  if (ddlMode.value) return;
  try {
    const input = await currentDdlInput();
    if (ddlSourceFingerprint.value !== input.fingerprint) {
      if (ddlWasEdited.value) {
        await ElMessageBox.confirm(
          "字段表格或目标配置已有变化，重新生成建表语句会覆盖手工修改的 SQL。是否继续？",
          "更新建表语句",
          { type: "warning", confirmButtonText: "重新生成", cancelButtonText: "取消" },
        );
      }
      await regenerateDdl(input);
    }
    ddlMode.value = true;
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    if (!error?.handled) ElMessage.error(error?.message || error || "生成建表语句失败");
  }
};

const handleAddField = async () => {
  const grid = tableRef.value?.$grid;
  if (!grid || !editable.value) return;
  const result = await grid.insertAt({}, -1);
  if (result?.row) await grid.setEditRow(result.row);
};

const handleSave = async () => {
  if (isSaveing.value) return;
  isSaveing.value = true;
  let lastTargetForm: any = null;
  try {
    const ddlInput = await currentDdlInput();
    const formData = normalizeFormPayload(await jsonFormRef.value?.getFormData());
    const tableFields = ddlInput.tableItems;
    if (!ddlSourceFingerprint.value) await regenerateDdl(ddlInput);
    if (ddlSourceFingerprint.value && ddlSourceFingerprint.value !== ddlInput.fingerprint) {
      if (ddlWasEdited.value) {
        await ElMessageBox.confirm(
          "字段或目标配置已变化，重新生成建表语句会覆盖手工修改的 SQL。是否继续？",
          "更新建表语句",
          { type: "warning", confirmButtonText: "重新生成", cancelButtonText: "取消" },
        );
      }
      await regenerateDdl(ddlInput);
      ddlMode.value = true;
      ElMessage.warning("字段或目标配置已变化，建表语句已同步更新，请确认 SQL 后再次提交");
      return;
    }
    const validateEditedDdl = async () => {
      if (!ddlSourceFingerprint.value) return;
      // Validate every edited statement before any existing target can be removed.
      const targets = ddlInput.targets.map((target: any) => {
        const ddl = String(ddlSqlByTarget.value[target.key] || "").trim();
        if (!ddl) throw new Error(`请填写“${target.dbName}”的建表语句`);
        return {
          dbId: target.dbId,
          tableName: target.propList.tableName,
          ddl,
        };
      });
      const response = await $common.post("/dst/database/metadata/createTable", {
        canvas: true,
        validateOnly: true,
        batchValidateOnly: true,
        targets,
      }, { _hiddenErrorMsg: true });
      const validation = response?.data || response || {};
      if (validation.batchValidateOnly !== true || validation.sideEffectsApplied !== false
        || !Array.isArray(validation.results) || validation.results.length !== targets.length) {
        throw new Error("批量建表语句校验结果不完整，请重试");
      }
      for (const [index, target] of targets.entries()) {
        const result = validation.results[index];
        if (String(result?.dbId) !== String(target.dbId)
          || String(result?.requestedTableName).trim().toUpperCase() !== target.tableName.toUpperCase()
          || result?.valid !== true || result?.sideEffectsApplied !== false) {
          throw new Error("批量建表语句校验结果与所选目标不一致，请重试");
        }
      }
    };
    const plannedTargets = ddlInput.targets.map((target: any) => ({
      targetDbId: target.dbId,
      targetTableName: target.propList.tableName,
      targetDbName: target.dbName,
    }));
    const preflightTargets = plannedTargets.map((plan: any) => {
      const known = knownTargets.value.find((target: any) =>
        String(target.targetDbId) === String(plan.targetDbId)
        && String(target.targetTableName).trim().toUpperCase() === plan.targetTableName.toUpperCase(),
      );
      return {
        targetDbId: plan.targetDbId,
        targetTableName: plan.targetTableName,
        targetTableId: known?.targetTableId || '',
        confirmUnmanagedTarget: true,
      };
    });
    const preflightResponse = await $common.post('/ods/dataAggReset', {
        batchDryRun: true,
        sourceTableId: props.id,
        deleteTargetTable: true,
        dryRun: true,
        targets: preflightTargets,
      }, { _hiddenErrorMsg: true }, 120 * 1000);
    const preflight = preflightResponse?.data || preflightResponse || {};
    if (preflight.batchDryRun !== true || preflight.sideEffectsApplied !== false
      || !Array.isArray(preflight.results) || preflight.results.length !== plannedTargets.length) {
      throw new Error('目标表批量预检结果不完整，请重试');
    }
    const conflicts: any[] = [];
    for (const [index, plan] of plannedTargets.entries()) {
      const probe = preflight.results[index];
      if (String(probe?.targetDbId) !== String(plan.targetDbId)
        || String(probe?.targetTableName).trim().toUpperCase() !== plan.targetTableName.toUpperCase()
        || probe?.sideEffectsApplied !== false) {
        throw new Error('目标表批量预检结果与所选目标不一致，请重试');
      }
      const known = knownTargets.value.find((target: any) =>
        String(target.targetDbId) === String(plan.targetDbId)
        && String(target.targetTableName).trim().toUpperCase() === plan.targetTableName.toUpperCase(),
      );
      if (probe.targetPhysicalExists || probe.targetUnlinked || known) conflicts.push({ ...plan, known, probe });
    }
    if (conflicts.length) {
      const recoveryConflict = conflicts.find((item: any) => !item.known);
      if (recoveryConflict) {
        recoveryTarget.value = {
          sourceTableId: props.id,
          targetTableId: recoveryConflict.probe?.targetTableId || '',
          targetDbId: recoveryConflict.targetDbId,
          targetTableName: recoveryConflict.targetTableName,
        };
      }
      const canReuse = conflicts.length === plannedTargets.length
        && conflicts.every((item: any) => item.known?.targetTableId
          && item.probe.targetPhysicalExists === true && item.probe.targetMetadataMissing !== true);
      const choice = await askExistingDecision(conflicts, canReuse);
      if (choice === 'cancel') return;
      if (choice === 'flow') {
        if (!canReuse) throw new Error('目标表未完整登记，不能直接创建流程');
        const targetTables = conflicts.map((item: any) => item.known);
        emit('save', { ...targetTables[0], targetTables, reusedExisting: true });
        return;
      }
      await validateEditedDdl();
      await ElMessageBox.confirm(
        `将删除 ${conflicts.length} 个同名目标物理表及其关联元数据，然后重新物化。删除不可恢复，确认继续吗？`,
        '删除目标表并重新物化',
        { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
      );
      for (const item of conflicts) {
        const result = await $common.post('/ods/dataAggReset', {
          sourceTableId: props.id,
          targetTableId: item.probe?.targetTableId || item.known?.targetTableId || '',
          targetDbId: item.targetDbId,
          targetTableName: item.targetTableName,
          deleteTargetTable: true,
          confirmUnmanagedTarget: true,
          confirmUnlinkedTarget: item.probe?.targetUnlinked === true,
        }, { _hiddenErrorMsg: true }, 120 * 1000);
        const outcome = result?.data || result || {};
        if (item.probe.targetPhysicalExists && outcome.targetPhysicalDeleted !== true) {
          throw new Error(`目标物理表“${item.targetTableName}”未确认删除，已停止重新物化`);
        }
        if (item.known) {
          knownTargets.value = knownTargets.value.filter((target: any) => target.targetTableId !== item.known.targetTableId);
          emit('target-deleted', item.known);
        }
      }
      recoveryTarget.value = null;
    }
    if (!conflicts.length) await validateEditedDdl();
    const targetTables: any[] = [];
    for (const [targetIndex, targetDbId] of formData.targetDbIds.entries()) {
      const targetDatasource = datasourceMap.value[targetDbId] || {};
      const targetForm = {
        ...formData,
        dbId: targetDbId,
        dbType: targetDatasource.dbType || formData.dbType,
        // 首个（单目标时即唯一）目标库使用用户在表单中确认的名称；
        // 只有附加的目标库才自动带 ODS 前缀，避免多库写入重名。
        tableName: targetIndex === 0
          ? String(formData.tableName || "").trim()
          : buildTargetTableName(targetDbId),
      };
      lastTargetForm = targetForm;
      const result = await $common.post("/ods/createTapleApply", {
        propList: targetForm,
        tableItems: tableFields,
        tid: props.id,
        ddl: ddlSourceFingerprint.value ? ddlSqlByTarget.value[targetDbId] : undefined,
      }, { _hiddenErrorMsg: true }, 120 * 1000);
      const payload = result?.data || result || {};
      const targetTableId = payload.targetTableId || payload.tid;
      if (!targetTableId) {
        throw new Error(`目标数据源“${targetDatasource.dbName || targetDbId}”的目标表未创建成功`);
      }
      targetTables.push({
        ...payload,
        targetTableId,
        targetDbId,
        targetDbName: targetDatasource.dbName || targetDatasource.label || "",
        targetDbType: targetDatasource.dbType || "",
        targetTableName: targetForm.tableName,
      });
    }
    ElMessage.success(`已完成 ${targetTables.length} 个目标表物化，将创建包含全部目标写入节点的接入任务`);
    emit("save", {
      ...targetTables[0],
      targetTableId: targetTables[0]?.targetTableId || "",
      targetTables,
    });
  } catch (error: any) {
    const message = String(error?.message || error || "");
    if (message.includes("目标物理表已存在")) {
      recoveryTarget.value = {
        sourceTableId: props.id,
        targetDbId: lastTargetForm?.dbId,
        targetTableName: lastTargetForm?.tableName,
      };
    }
    if (error !== 'cancel' && error !== 'close' && !message.includes('目标物理表已存在') && !error?.handled) {
      ElMessage.error(error?.message || error || '物化建表失败');
    }
    if (message.includes('目标物理表已存在') && recoveryTarget.value) {
      const known = knownTargets.value.find((target: any) =>
        String(target.targetDbId) === String(recoveryTarget.value.targetDbId)
        && String(target.targetTableName).toUpperCase() === String(recoveryTarget.value.targetTableName).toUpperCase(),
      );
      const choice = await askExistingDecision([{ ...recoveryTarget.value, known }], Boolean(known));
      if (choice === 'flow' && known) emit('save', { ...known, targetTables: [known], reusedExisting: true });
      if (choice === 'delete') ElMessage.info('请使用“删除已有目标表”操作，删除后重新提交');
    }
  } finally {
    isSaveing.value = false;
  }
};

// A pre-existing physical target is never adopted implicitly. Operators can
// remove it explicitly and then retry the same materialization request.
const handleDeleteExistingTarget = async (selectedTarget: any = null) => {
  const target = selectedTarget || recoveryTarget.value;
  if (!target || recoveryDeleting.value) return;
  try {
    const request = {
      sourceTableId: props.id,
      targetTableId: target.targetTableId || '',
      targetDbId: target.targetDbId,
      targetTableName: target.targetTableName,
      deleteTargetTable: true,
      confirmUnmanagedTarget: true,
    };
    const previewResult = await $common.post('/ods/dataAggReset',
      { ...request, dryRun: true }, { _hiddenErrorMsg: true });
    const preview = previewResult?.data || previewResult || {};
    if (target.targetTableId && String(preview.targetTableId || '') !== String(target.targetTableId)) {
      throw new Error('目标表归属校验失败，已停止删除');
    }
    if (preview.targetUnlinked && !preview.targetTableId) {
      throw new Error('未关联目标表缺少可核验的元数据 ID，已停止删除');
    }
    await ElMessageBox.confirm(
      `${preview.targetUnlinked ? '该目标表未关联当前来源表，请先核对数据用途。' : ''}将删除目标数据源中的物理表“${target.targetTableName}”及其元数据，该操作不可恢复。确认继续吗？`,
      '删除已有目标表',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    );
    recoveryDeleting.value = true;
    const result = await $common.post('/ods/dataAggReset', {
      ...request,
      targetTableId: preview.targetTableId || request.targetTableId,
      confirmUnlinkedTarget: preview.targetUnlinked === true,
    },
      { _hiddenErrorMsg: true }, 120 * 1000);
    const outcome = result?.data || result || {};
    if (preview.targetPhysicalExists && outcome.targetPhysicalDeleted !== true) {
      throw new Error('目标物理表未确认删除，请检查目标库连接与删除结果后重试');
    }
    knownTargets.value = knownTargets.value.filter((item: any) => item.targetTableId !== target.targetTableId);
    recoveryTarget.value = null;
    emit('target-deleted', target);
    ElMessage.success(preview.targetPhysicalExists
      ? '目标表已删除，请重新提交创建任务' : '已清理失效的目标表登记，请重新提交创建任务');
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    if (!error?.handled) ElMessage.error(error?.message || error || '删除目标表失败');
  } finally {
    recoveryDeleting.value = false;
  }
};

// Handle table button click
const handleTableBtnClick = async (params: any) => {
  const { code } = params;

  switch (code) {
    case "default_field":
      // 打开默认字段弹窗
      handleOpenDefaultFieldsDrawer();
      break;
  }
};

// 打开默认字段弹窗，同步选中状态
const handleOpenDefaultFieldsDrawer = () => {
  // 获取当前表格中的所有字段
  const currentFields = tableRef.value?.getData() || [];

  // 提取已存在的字段名
  const existingFieldNames = new Set(currentFields.map((field: any) => field.columnName));

  // 根据现有字段同步选中默认字段
  const fieldsToSelect = defaultFieldsData.value.filter((item) =>
    existingFieldNames.has(item.columnName)
  );

  // 打开弹窗
  defaultFieldsVisible.value = true;

  // 等待弹窗打开后，设置表格的选中行
  setTimeout(() => {
    if (defaultFieldsTableRef.value) {
      const table = defaultFieldsTableRef.value.$table;
      if (table) {
        // 先清除所有选中
        table.clearSelection();
        // 设置选中行
        fieldsToSelect.forEach((row) => {
          table.toggleRowSelection(row, true);
        });
      }
    }
  }, 100);
};

// 处理选中变化
const handleSelectionChange = (selection: any[]) => {
  selectedDefaultFields.value = selection;
};

// 确认添加默认字段
const handleConfirmDefaultFields = () => {
  if (selectedDefaultFields.value.length === 0) {
    ElMessage.warning("请至少选择一个字段");
    return;
  }

  // 获取当前表格中的所有字段
  const currentFields = tableRef.value?.getData() || [];
  const existingFieldNames = new Set(currentFields.map((field: any) => field.columnName));

  // 将选中的默认字段转换为表格所需的格式
  const newFields = selectedDefaultFields.value.filter(
    (item) => !existingFieldNames.has(item.columnName)
  );

  if (newFields.length === 0) {
    ElMessage.info("所选字段已全部存在于表格中");
    defaultFieldsVisible.value = false;
    return;
  }

  // 将字段添加到表格末尾
  newFields.forEach((field) => {
    tableRef.value?.$grid?.insertAt(field, -1);
  });

  ElMessage.success(
    `已添加 ${newFields.length} 个默认字段${
      selectedDefaultFields.value.length > newFields.length
        ? `（${selectedDefaultFields.value.length - newFields.length} 个已存在）`
        : ""
    }`
  );
  defaultFieldsVisible.value = false;
};

onMounted(() => {
  if (props.modelValue && props.id) {
    init();
  }
});
watch(() => props.modelValue, (visible, previous) => {
  if (visible && !previous && props.id) init();
  if (!visible && previous) cancelPendingInit();
});
onBeforeUnmount(cancelPendingInit);
</script>

<style lang="scss" scoped>
.existing-target-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  margin: 0 0 12px;
  border: 1px solid #d9e8ff;
  border-radius: 8px;
  background: #f6f9ff;
  color: #37547d;
  font-size: 13px;
}
.existing-target-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.existing-target-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.existing-target-warning { color: #b54708; line-height: 1.6; }
.access-apply {
  .access-apply-content {
    :deep(.vxe-toolbar .vxe-button--item-wrapper) {
      justify-content: end;
    }
    :deep(.btn-plain) {
      background-color: #fff !important;
      color: #1b67f8;
      border: 1px solid #8db3fc;
      &:hover {
        color: #4985f9 !important;
        border-color: #4985f9 !important;
        outline: none;
      }
    }
    :deep(.btn-primary) {
      &:hover {
        background-color: #5f95fa !important;
        border-color: #5f95fa !important;
        outline: none;
      }
    }

  }
}

// 抽屉 body 由 Teleport 渲染，不带本组件的 scoped 属性；使用 global 才能
// 确保 body-class 真正命中。保留紧凑但不贴边的首区块间距。
:global(.el-drawer__body.access-apply) {
  padding-top: 8px !important;
}

:global(.el-drawer__body.access-apply:has(.ddl-inline-panel)) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-bottom: 12px;
}

:global(.el-drawer__body.access-apply .u-title) {
  padding: 8px 0 !important;
}

:global(.el-drawer__header:has(.access-apply-drawer-header)) {
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-bottom: 1px solid #e8edf4;
}

:global(.el-drawer__header .access-apply-drawer-header) {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  min-height: 48px;
  gap: 18px;
}

.access-apply-view-tabs {
  display: flex;
  align-self: stretch;
  align-items: stretch;
  flex: 0 0 auto;
  gap: 30px;
}
.access-apply-view-tab {
  appearance: none;
  padding: 0 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #9099aa;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}
.access-apply-view-tab.is-active {
  border-bottom-color: #1b67f8;
  color: #1b67f8;
}
.access-apply-view-tab:hover:not(:disabled) { color: #1b67f8; }
.access-apply-view-tab:disabled { cursor: wait; opacity: .65; }
.template-load-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
}
.template-load-error .el-alert { width: 100%; }
.materialization-form-view { min-width: 0; }
.access-apply-content:has(.ddl-inline-panel) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

:global(.el-drawer__header .access-apply-target-summary) {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: #5b77a5;
  font-size: 13px;
  font-weight: 400;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.materialization-field-toolbar {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 8px;
}
.materialization-field-toolbar .el-button + .el-button { margin-left: 0; }
.ddl-inline-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #e3e9f2;
  border-radius: 6px;
  background: #fff;
}
.ddl-target-tabs { padding: 0 12px; }
.ddl-inline-panel :deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
  height: auto;
  width: 100%;
}
.ddl-inline-panel :deep(.ace_editor) { min-height: 200px !important; }

@media (max-height: 430px) {
  :global(.el-drawer__body.access-apply:has(.ddl-inline-panel)) { overflow: auto; }
  .access-apply-content:has(.ddl-inline-panel) { min-height: 220px; }
}
</style>
