<template>
  <el-drawer
    class="access-apply-drawer"
    v-model="open"
    :title="drawerTitle"
    direction="rtl"
    size="min(1040px, 100vw)"
    :body-class="ddlMode ? 'access-apply access-apply-ddl' : 'access-apply'"
    :close-on-click-modal="!isSaveing && !recoveryDeleting"
    :close-on-press-escape="!isSaveing && !recoveryDeleting"
    :show-close="!isSaveing && !recoveryDeleting"
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
            :disabled="isSaveing"
            @click="showFormView"
          >物化建表</button>
          <button
            type="button"
            class="access-apply-view-tab"
            :class="{ 'is-active': ddlMode }"
            role="tab"
            :aria-selected="ddlMode"
            :disabled="isSaveing || ddlGenerating || templateLoading || !!loadError"
            @click="showDdlView"
          >查看DDL语句</button>
        </div>
        <span v-if="sourcePartitionDetected" class="access-apply-partition-notice" role="status"
          title="默认不创建分区。如需创建，请在查看DDL语句中手工勾选“创建分区”并确认配置。">
          来源表是分区表，是否创建分区？
        </span>
      </div>
    </template>
    <div
      v-loading="templateLoading"
      :element-loading-text="loadingText"
      element-loading-background="rgba(255, 255, 255, 0.88)"
      class="access-apply-content"
      :aria-busy="isSaveing"
      :class="{ 'is-ddl-mode': ddlMode }"
    >
      <div v-if="isSaveing && !materializationAwaitingDecision" class="materialization-progress-overlay">
        <div class="materialization-progress-card" role="status" aria-live="polite">
          <div class="materialization-progress-symbol" aria-hidden="true"><span></span><Icon icon="database" /></div>
          <h3>正在物化建表</h3>
          <p class="materialization-progress-detail">{{ materializationProgress.detail }}</p>
          <div class="materialization-progress-meta">
            <span v-if="materializationProgress.total">已完成 {{ materializationProgress.completed }} / {{ materializationProgress.total }} 张目标表</span>
            <span aria-hidden="true">已用时 {{ materializationElapsed }} 秒</span>
          </div>
          <ol class="materialization-progress-steps">
            <li v-for="(label, index) in ['校验字段和配置', '预检目标表与建表语句', '执行建表并登记']" :key="label"
              :class="{ 'is-complete': index < materializationProgress.phase, 'is-current': index === materializationProgress.phase }">
              <span class="materialization-step-mark" aria-hidden="true">{{ index < materializationProgress.phase ? '✓' : index + 1 }}</span>
              <span>{{ label }}</span>
              <small>{{ index < materializationProgress.phase ? '已完成' : index === materializationProgress.phase ? '处理中' : '待处理' }}</small>
            </li>
          </ol>
          <p class="materialization-progress-note">建表期间请勿关闭窗口或重复提交<span v-if="materializationElapsed >= 30">。目标库响应较慢，仍在等待处理结果</span>。</p>
        </div>
      </div>
      <div v-if="loadError && !templateLoading" class="template-load-error" role="alert">
        <el-alert :title="loadError" type="error" :closable="false" show-icon />
        <el-button type="primary" plain @click="init">重试加载</el-button>
      </div>
      <el-alert v-if="sourcePreflightError" class="source-preflight-error" :title="sourcePreflightError"
        type="error" :closable="false" show-icon />
      <el-alert v-if="reuseTypeNotice" :title="reuseTypeNotice" type="info" :closable="false" show-icon />
      <div v-show="!ddlMode && !loadError" class="materialization-form-view">
      <u-title name="建表信息" />
      <el-alert v-for="message in templateWarnings" :key="message" :title="message" type="warning" :closable="false" show-icon class="mb-2" />
      <div v-if="knownTargets.length" class="existing-target-panel">
        <strong>已有目标表</strong>
        <span>以下目标表已与当前来源表关联，可保留并创建流程。Hive 移除登记和流程后仍保留物理表及数据，后续可校验并复用已有表。</span>
        <div v-for="target in knownTargets" :key="target.targetTableId" class="existing-target-row">
          <span :title="target.targetTableName">{{ target.targetDbName || target.targetDbId }} / {{ target.targetTableName }}</span>
          <el-button link type="danger" :disabled="isSaveing || recoveryDeleting" @click="handleDeleteExistingTarget(target)">{{ isHiveTarget(target) ? "移除登记和流程" : "删除目标表" }}</el-button>
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
        <div class="ddl-target-toolbar">
          <el-tabs v-model="activeDdlKey" class="ddl-target-tabs">
            <el-tab-pane v-for="plan in ddlPlans" :key="plan.key" :name="plan.key" :label="plan.dbName" />
          </el-tabs>
          <el-checkbox v-if="hasPartitionSupport" :model-value="partitionEnabled"
            :disabled="!editable || ddlGenerating || isSaveing" @change="togglePartition">创建分区（所有支持的目标库）</el-checkbox>
        </div>
        <el-alert v-if="sourceDefinitionMessage" :title="sourceDefinitionMessage" type="info" :closable="false" show-icon />
        <span v-if="partitionEnabled && !activePartitionSupported">当前目标库使用普通表，其他支持分区的目标库继续创建分区。</span>
        <div v-if="activePartitionSupported && activePartition.enabled" class="partition-settings">
          <span>{{ partitionProbeMessage }}</span>
          <div class="partition-settings-fields">
            <el-select v-model="activePartition.field" placeholder="分区字段" :disabled="!editable" @change="updatePartitionDdl">
              <el-option v-for="field in templateTableItems" :key="field.columnName" :value="field.columnName"
                :label="`${field.columnComment || field.columnName}（${field.columnName}）`" />
            </el-select>
            <el-select v-model="activePartitionChoice" :disabled="!editable" @change="changePartitionStrategy">
              <el-option label="按年分区" value="YEAR" />
              <el-option label="按月分区" value="MONTH" />
              <el-option label="区域分区" value="REGION" />
              <el-option label="枚举分区" value="LIST" />
              <el-option v-if="activePartitionChoice === 'TIME'" label="时间分区（来源实际边界）" value="TIME" />
              <el-option v-if="activePartitionChoice === 'RANGE'" label="数值范围分区" value="RANGE" />
            </el-select>
            <template v-if="activePartition.strategy === 'YEAR'">
              <el-date-picker v-model="activePartition.startYear" type="year" value-format="YYYY" placeholder="起始年份" @change="updatePartitionDdl" />
              <el-input-number v-model="activePartition.years" :min="1" :max="240" controls-position="right" @change="updatePartitionDdl" />
              <span>年；包含历史与未来兜底分区</span>
            </template>
            <template v-if="activePartition.strategy === 'MONTH'">
              <el-date-picker v-model="activePartition.startMonth" type="month" value-format="YYYY-MM" placeholder="起始月份" @change="updatePartitionDdl" />
              <el-input-number v-model="activePartition.months" :min="1" :max="240" controls-position="right" @change="updatePartitionDdl" />
              <span>个月；包含历史与未来兜底分区</span>
            </template>
            <span v-if="activePartition.sourceBoundaries">已保留来源 {{ activePartition.partitions?.length || 0 }} 个实际边界</span>
            <el-button v-if="activePartition.sourceBoundaries && ['YEAR', 'MONTH'].includes(activePartitionChoice)"
              :disabled="!editable" @click="repartitionSource">重新划分</el-button>
            <el-input v-if="['YEAR', 'MONTH'].includes(activePartition.strategy) || activePartition.kind === 'TIME'"
              v-model="activePartition.timezone" placeholder="分区时区，默认Asia/Shanghai" @change="updatePartitionDdl" />
            <el-input v-if="activePartition.strategy === 'LIST'" v-model="activeListPartitionText"
              class="partition-values-input" :placeholder="activePartitionChoice === 'REGION' ? '区域编码，用逗号分隔，如350100,350200' : '枚举值，用逗号分隔，如0,1,2'"
              @change="updatePartitionDdl" @keyup.enter="updatePartitionDdl" />
          </div>
          <el-alert v-if="partitionError" :title="partitionError" :type="partitionErrorType" :closable="false" show-icon />
        </div>
        <el-alert v-for="issue in activeDdlWarnings" :key="`${issue.field}:${issue.message}`"
          :title="`${issue.field}：${issue.message}`" type="warning" :closable="false" show-icon class="mb-2" />
        <div ref="ddlEditorHost" class="ddl-editor-host">
          <CodeEditor
            :key="activeDdlKey"
            v-model="activeDdlSql"
            lang="sql"
            theme="chrome"
            :height="ddlEditorHeight"
            :show-option="false"
            :read-only="!editable"
          />
        </div>
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
          {{ isHiveTarget(recoveryTarget) ? "移除已有登记和流程" : "删除已有目标表" }}
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
      当前没有可复用的目标物理表。请重新物化，或取消并调整目标配置。
    </p>
    <p v-if="existingDecisionHasUnlinked" class="existing-target-warning">
      其中有目标表尚未关联当前来源表。继续后会校验真实字段并补齐关联登记；已有表及其数据会保留。
    </p>
    <template #footer>
      <el-button @click="resolveExistingDecision('cancel')">取消</el-button>
      <el-button type="primary" :disabled="!existingDecision.canReuse" @click="resolveExistingDecision('flow')">继续建表并创建流程</el-button>
      <el-button v-if="!existingDecisionHasHive" type="danger" @click="resolveExistingDecision('delete')">删除目标表并重新物化</el-button>
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
  // 分发使用部门数据源；接入默认使用 ODS 分域。
  taskCategory?: "ingestion" | "distribution";
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "save", payload: any): void;
  (e: "target-deleted", payload: any): void;
  (e: "update:modelValue", value: boolean): void;
}>();

const dictStore = useDictStore();
const registeredColTypeOptions = dictStore.getDictItems("tableColType");
// 原生类型映射可返回这些通用类型，旧租户字典没有时仍须正确回显和编辑。
const colTypeOptions = [
  ...registeredColTypeOptions,
  ...["varbinary", "float", "double"].filter((value) =>
    !registeredColTypeOptions.some((item: any) => String(item.value).toLowerCase() === value)
  ).map((value) => ({ label: value.toUpperCase(), value })),
];
const formData = ref<any>({});
const jsonFormRef = ref<any>(null);
const drawerTitle = ref("物化建表");
const ddlMode = ref(false);
const ddlEditorHost = ref<HTMLElement | null>(null);
const ddlEditorHeight = ref("360px");
let ddlSizeObserver: ResizeObserver | null = null;
const syncDdlEditorHeight = () => {
  const host = ddlEditorHost.value;
  const body = host?.closest(".el-drawer__body");
  if (!host || !body) return;
  const bottomPadding = Number.parseFloat(window.getComputedStyle(body).paddingBottom) || 0;
  const availableHeight = body.getBoundingClientRect().bottom - host.getBoundingClientRect().top - bottomPadding;
  // 使用明确像素高度，避免旧浏览器在多层 flex/百分比高度中回退为 200px。
  ddlEditorHeight.value = `${Math.max(200, Math.floor(availableHeight))}px`;
};
const stopDdlEditorSizing = () => {
  ddlSizeObserver?.disconnect();
  ddlSizeObserver = null;
  window.removeEventListener("resize", syncDdlEditorHeight);
};
const ddlGenerating = ref(false);
const ddlPlans = ref<any[]>([]);
const ddlSqlByTarget = ref<Record<string, string>>({});
const ddlWarningsByTarget = ref<Record<string, any[]>>({});
const ddlTableItemsByTarget = ref<Record<string, any[]>>({});
const ddlGeneratedSqlByTarget = ref<Record<string, string>>({});
const ddlInputFingerprintsByTarget = ref<Record<string, string>>({});
const ddlPartitionInputsByTarget = ref<Record<string, any>>({});
const activeDdlWarnings = computed(() => ddlWarningsByTarget.value[activeDdlKey.value] || []);
const activeDdlKey = ref("");
const partitionPlans = ref<Record<string, any>>({});
const ddlPartitionPlans = ref<Record<string, any>>({});
const partitionSupport = ref<Record<string, boolean>>({});
const partitionEnabled = ref(false);
const hasPartitionSupport = computed(() => Object.values(partitionSupport.value).some(Boolean));
const partitionProbeMessage = ref("");
const sourcePartitionDetected = ref(false);
const sourceDefinitionMessage = ref("");
const sourceConstraints = ref<any[]>([]);
const partitionError = ref("");
const partitionErrorType = ref<"warning" | "error">("error");
let ddlGenerationVersion = 0;
let partitionProbeIdentity = "";
const activePartitionSupported = computed(() => partitionSupport.value[activeDdlKey.value] === true);
const activePartition = computed(() => partitionPlans.value[activeDdlKey.value] || {});
const defaultRegionCodes = ['350100', '350200', '350300', '350400', '350500', '350600', '350700', '350800', '350900', '351000'];
const calendarPartitionDefaults = (strategy: string, now = new Date()) => {
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  return strategy === 'YEAR'
    ? { startYear: String(year), years: Math.max(2035, year + 4) - year + 1 }
    : { startMonth: `${year}-${String(month).padStart(2, '0')}`, months: (Math.max(2035, year + 1) - year) * 12 + 13 - month };
};
const activeListPartitionText = computed({
  get: () => activePartition.value.enumText ?? (activePartition.value.partitions || []).flatMap((row: any) => row.values || []).join(','),
  set: (value: string) => { activePartition.value.enumText = value; },
});
const activePartitionChoice = computed({
  get: () => activePartition.value.strategy === 'RANGE'
    ? activePartition.value.displayStrategy || (activePartition.value.kind === 'TIME' ? 'TIME' : 'RANGE')
    : activePartition.value.strategy === 'LIST' && activePartition.value.displayStrategy === 'REGION'
      ? 'REGION' : activePartition.value.strategy || 'MONTH',
  set: (value: string) => {
    const plan = activePartition.value;
    if (plan.strategy === 'LIST' && plan.enumText !== undefined) {
      plan[plan.displayStrategy === 'REGION' ? 'regionText' : 'enumerationText'] = plan.enumText;
    }
    plan.strategy = value === 'REGION' ? 'LIST' : value;
    delete plan.sourceBoundaries;
    delete plan.sourceInterval;
    delete plan.displayStrategy;
    if (value === 'REGION') Object.assign(plan, { displayStrategy: 'REGION', enumText: plan.regionText ?? defaultRegionCodes.join(',') });
    if (value === 'LIST') plan.enumText = plan.enumerationText ?? '';
    if (['YEAR', 'MONTH'].includes(value)) Object.assign(plan, calendarPartitionDefaults(value));
  },
});
const repartitionSource = async () => {
  activePartitionChoice.value = activePartitionChoice.value;
  await updatePartitionDdl();
};
const invalidateDdl = () => {
  ddlGenerationVersion += 1;
  ddlGenerating.value = false;
  ddlSqlByTarget.value = {};
  ddlPartitionPlans.value = {};
  ddlWarningsByTarget.value = {};
  ddlTableItemsByTarget.value = {};
  ddlGeneratedSqlByTarget.value = {};
  ddlInputFingerprintsByTarget.value = {};
  ddlPartitionInputsByTarget.value = {};
  ddlSourceFingerprint.value = "";
  ddlWasEdited.value = false;
};
const partitionInputError = (key: string, message: string): never => {
  const targetName = ddlPlans.value.find((target: any) => target.key === key)?.dbName
    || datasourceMap.value[key]?.dbName || "所选目标数据源";
  throw Object.assign(new Error(`目标数据源“${targetName}”：${message}`), {
    partitionValidation: true, partitionTargetKey: key, handled: true,
  });
};
const partitionPayload = (key: string) => {
  const plan = partitionPlans.value[key] || { enabled: false };
  if (!plan.enabled) return { enabled: false };
  const fields = tableRef.value?.getData() || templateTableItems.value;
  const field = fields.find((row: any) => row.columnName === plan.field);
  const fieldLabel = field ? `${field.columnComment || field.columnName}（${field.columnName}）` : "分区字段";
  const fieldType = String(field?.columnType || field?.dataType || "").toLowerCase();
  if (!field) partitionInputError(key, "请先选择用于分区的字段。");
  if (["MONTH", "YEAR"].includes(plan.strategy) && !/date|time/.test(fieldType)) {
    partitionInputError(key, `“${fieldLabel}”不是日期或时间字段。按年或按月分区请选择日期或时间字段；按区域编码分区请选择“区域分区”。`);
  }
  if (plan.strategy === "LIST" && plan.enumText !== undefined) {
    const valueLabel = plan.displayStrategy === 'REGION' ? '区域编码' : '枚举值';
    const example = plan.displayStrategy === 'REGION' ? '350100,350200' : '0,1,2';
    const values = String(plan.enumText).split(/[,，\n]/).map((value: string) => value.trim()).filter(Boolean);
    if (!values.length) {
      partitionInputError(key, /date|time/.test(fieldType)
        ? `请先填写区域编码或枚举值。当前选择的“${fieldLabel}”是时间字段，按区域分区请改选区域编码字段；按时间分区请选择“按月分区”。`
        : `请填写至少一个${valueLabel}，用逗号分隔，例如${example}；每个值会生成一个分区。`);
    }
    if (values.length > 2000) partitionInputError(key, `填写的${valueLabel}超过2000项，请减少分区数量。`);
    if (new Set(values).size !== values.length) partitionInputError(key, `${valueLabel}有重复，请删除重复项后再生成建表语句。`);
    if (/date|time/.test(fieldType) && values.some((value: string) => !/^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}:\d{2})?$/.test(value))) {
      partitionInputError(key, `“${fieldLabel}”是时间字段，不能填写区域编码。请选择区域编码字段，或填写YYYY-MM-DD格式的日期；按月划分请选择“按月分区”。`);
    }
    if (/^(number|numeric|decimal|int|bigint|smallint|tinyint|float|double|real)/.test(fieldType)
      && values.some((value: string) => !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value))) {
      partitionInputError(key, `“${fieldLabel}”是数字字段，请填写数字枚举值，不要填写中文名称或引号。`);
    }
    return { ...plan, partitions: values.map((value: string, index: number) => ({
      name: plan.displayStrategy === 'REGION' && /^\d{6}$/.test(value) ? `p_${value}` : `p_${index + 1}`,
      bucket: plan.displayStrategy === 'REGION' && /^\d{6}$/.test(value) ? value : `p_${index + 1}`, values: [value],
    })) };
  }
  if (plan.strategy === "LIST" && !plan.partitions?.length) {
    partitionInputError(key, "请填写至少一个区域编码或枚举值，用逗号分隔，例如350100,350200。");
  }
  return plan;
};
const probePartitions = async (targets: any[]) => {
  const identity = JSON.stringify([String(props.id), targets.map((target: any) => target.dbId)]);
  if (partitionProbeIdentity === identity) return;
  const response = await $common.post("/dst/database/metadata/getCreateTableDDL", {
    canvas: true, partitionProbe: true, sourceTableId: formData.value.sourceTableId || props.id,
    targetDbIds: targets.map((target: any) => target.dbId),
  }, { _hiddenErrorMsg: true });
  const result = response?.data || response || {};
  partitionSupport.value = result.targetSupport || {};
  sourceConstraints.value = Array.isArray(result.source?.constraints) ? result.source.constraints : [];
  sourceDefinitionMessage.value = result.source?.ddlReadStatus && result.source.ddlReadStatus !== 'unsupported'
    ? String(result.source.ddlReadMessage || '') : '';
  partitionProbeMessage.value = String(result.source?.message || "未识别到来源分区，可手动配置");
  sourcePartitionDetected.value = result.source?.status === "detected";
  for (const target of targets) {
    if (partitionPlans.value[target.key]) continue;
    const existing = Object.values(partitionPlans.value).find((plan: any) => plan.enabled);
    partitionPlans.value[target.key] = partitionSupport.value[target.key] && result.source?.status === "detected"
      ? { ...JSON.parse(JSON.stringify(result.source.plan)), enabled: partitionEnabled.value }
      : { ...JSON.parse(JSON.stringify(existing || {})), enabled: partitionEnabled.value && partitionSupport.value[target.key] === true };
  }
  partitionProbeIdentity = identity;
};
const updatePartitionDdl = async (targetKeys?: any) => {
  partitionError.value = "";
  partitionErrorType.value = "error";
  const keys = Array.isArray(targetKeys) ? targetKeys : [activeDdlKey.value];
  try { await regenerateDdl(await currentDdlInput(), keys); }
  catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      for (const key of keys) if (ddlPartitionInputsByTarget.value[key]) {
        partitionPlans.value[key] = JSON.parse(JSON.stringify(ddlPartitionInputsByTarget.value[key]));
      }
      return;
    }
    if (error?.partitionTargetKey) activeDdlKey.value = error.partitionTargetKey;
    partitionErrorType.value = error?.partitionValidation ? "warning" : "error";
    partitionError.value = String(error?.message || "无法生成建表语句，请检查分区字段和分区值后重试。");
  }
};
const changePartitionStrategy = async () => {
  const plan = partitionPlans.value[activeDdlKey.value];
  if (plan?.strategy === "LIST" && plan.enumText === undefined) {
    plan.enumText = Array.isArray(plan.partitions) && plan.partitions.every((row: any) => Array.isArray(row.values))
      ? plan.partitions.flatMap((row: any) => row.values).join(",") : "";
  }
  await updatePartitionDdl();
};
const togglePartition = async (enabled: any) => {
  if (partitionEnabled.value === !!enabled) return;
  const previous = JSON.parse(JSON.stringify(partitionPlans.value));
  const keys = Object.keys(partitionSupport.value).filter(key => partitionSupport.value[key]);
  const fields = tableRef.value?.getData() || templateTableItems.value;
  const temporal = fields.find((field: any) => /date|time/i.test(String(field.columnType || field.dataType))) || fields[0];
  for (const key of keys) {
    const plan = partitionPlans.value[key] || {};
    if (enabled && !plan.field) Object.assign(plan, { field: temporal?.columnName || "", strategy: "MONTH", ...calendarPartitionDefaults('MONTH') });
    partitionPlans.value[key] = { ...plan, enabled: !!enabled };
  }
  const before = partitionEnabled.value;
  partitionEnabled.value = !!enabled;
  try { await regenerateDdl(await currentDdlInput(), keys); }
  catch (error: any) {
    partitionPlans.value = previous;
    partitionEnabled.value = before;
    if (error === 'cancel' || error === 'close') return;
    partitionError.value = String(error?.message || "无法更新分区配置，请重试。");
  }
};
const ddlSourceFingerprint = ref("");
const ddlWasEdited = ref(false);
const defaultFieldsVisible = ref(false);
const activeDdlSql = computed({
  get: () => ddlSqlByTarget.value[activeDdlKey.value] || "",
  set: (value: string) => {
    ddlSqlByTarget.value = { ...ddlSqlByTarget.value, [activeDdlKey.value]: value };
    ddlWasEdited.value = true;
    partitionError.value = "";
  },
});
const isSaveing = ref(false);
const materializationProgress = ref({ phase: 0, detail: "", total: 0, completed: 0 });
const materializationElapsed = ref(0);
const materializationAwaitingDecision = ref(false);
let materializationStartedAt = 0;
let materializationTimer: ReturnType<typeof setInterval> | undefined;
const stopMaterializationProgress = () => {
  if (materializationTimer !== undefined) clearInterval(materializationTimer);
  materializationTimer = undefined;
  materializationAwaitingDecision.value = false;
};
const startMaterializationProgress = () => {
  stopMaterializationProgress();
  materializationProgress.value = { phase: 0, detail: "正在校验表名、字段和目标配置", total: 0, completed: 0 };
  materializationElapsed.value = 0;
  materializationStartedAt = Date.now();
  // 只更新本地耗时，不轮询接口、不模拟完成百分比。
  materializationTimer = setInterval(() => {
    materializationElapsed.value = Math.max(0, Math.floor((Date.now() - materializationStartedAt) / 1000));
  }, 1000);
};
const confirmMaterialization = async (...args: Parameters<typeof ElMessageBox.confirm>) => {
  materializationAwaitingDecision.value = true;
  try { return await ElMessageBox.confirm(...args); }
  finally { materializationAwaitingDecision.value = false; }
};

const recoveryTarget = ref<any>(null);
const knownTargets = ref<any[]>([]);
const existingDecisionVisible = ref(false);
const existingDecision = ref<{ targets: any[]; canReuse: boolean }>({ targets: [], canReuse: false });
let existingDecisionResolve: ((choice: string) => void) | null = null;
const existingDecisionHasUnlinked = computed(() =>
  existingDecision.value.targets.some((target: any) => target.probe?.targetUnlinked === true),
);
const existingDecisionHasHive = computed(() => existingDecision.value.targets.some((target: any) =>
  isHiveTarget({ ...target, targetDatabaseType: target.probe?.targetDatabaseType })));
const existingDecisionSummary = computed(() => {
  const rows = existingDecision.value.targets;
  return `目标表已存在：${rows.map((row: any) => `${row.targetDbName || row.targetDbId} / ${row.targetTableName}`).join('、')}。选择继续后，已有表会直接复用并保留数据，其余尚不存在的目标表正常创建，然后生成任务流程。`;
});
const resolveExistingDecision = (choice: string) => {
  materializationAwaitingDecision.value = false;
  existingDecisionVisible.value = false;
  const resolve = existingDecisionResolve;
  existingDecisionResolve = null;
  resolve?.(choice);
};
const askExistingDecision = (targets: any[], canReuse: boolean): Promise<string> => {
  materializationAwaitingDecision.value = true;
  existingDecision.value = { targets, canReuse };
  existingDecisionVisible.value = true;
  return new Promise((resolve) => { existingDecisionResolve = resolve; });
};
const recoveryDeleting = ref(false);
const templateLoading = ref(false);
const loadError = ref("");
const sourcePreflightError = ref("");
const reuseTypeNotice = ref("");
const loadErrorCode = ref("");
const loadPending = ref({ datasource: false, node: false, source: false });
let initRun = 0;
const datasourceOptions = ref<any[]>([]);
const datasourceMap = ref<Record<string, any>>({});
const datasourcePathMap = ref<Record<string, any[]>>({});
const datasourceLeafIds = ref<string[]>([]);
// 保留接口返回的字段模板。JsonForm 与 VxeGrid 都会在抽屉打开时完成一次
// 异步初始化；只向尚未就绪的表格实例 setData 会被其初始化选项覆盖。
const templateTableItems = ref<any[]>([]);
const templateWarnings = ref<string[]>([]);
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
const sourceFileConfig = ref<any>(null);
const loadingText = computed(() => {
  const { datasource, node, source } = loadPending.value;
  if (source && (datasource || node)) return "正在读取登记字段快照和建表选项...";
  if (source) return "正在读取登记字段快照...";
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
      minWidth: 176,
      editRender: {
        name: "VxeInput",
        props: {
          maxlength: 255,
        },
      },
      showOverflow: "tooltip",
    },
    {
      title: "字段说明",
      field: "columnComment",
      minWidth: 176,
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
      minWidth: 108,
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
      width: 80,
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

// 接入目标按接口返回的 ODS 分域配置顺序排列，不采用复选框点击顺序。
// 分发目标来自部门数据源树，保留其选择顺序。
const orderTargetDatasourceIds = (value: any) => {
  const ids = normalizeDbIds(value).map((id: any) => String(id));
  if (props.taskCategory === "distribution") return ids;
  const selected = new Set(ids);
  const ordered = [...new Set(datasourceLeafIds.value.filter((id) => selected.has(id)))];
  return [...ordered, ...ids.filter((id) => !ordered.includes(id))];
};

const getDefaultDatasourcePaths = () => {
  if (props.taskCategory === "distribution") return [];
  const paths: any[][] = [];
  const selectedIds = new Set<string>();
  // 只勾选 huiju / yuanshi，排列遵循管理员保存的分域顺序。
  for (const dbId of datasourceLeafIds.value) {
    const datasource = datasourceMap.value[dbId] || {};
    const path = datasourcePathMap.value[dbId];
    if (normalizeCode(datasource.layerCode) !== "ods"
      || !["huiju", "yuanshi"].includes(normalizeCode(datasource.domainCode ?? datasource.domain_code))
      || !path?.length || selectedIds.has(dbId)) continue;
    selectedIds.add(dbId);
    paths.push([...path]);
  }
  return paths;
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
  if (["oceanbaseoracle", "oceanbase_oracle", "oracle", "dameng", "dm"].includes(code)) {
    return "date";
  }
  if ([
    "postgresql", "postgres", "kingbase", "kingbase8", "gaussdb", "opengauss", "gauss",
    "highgo", "vastbase", "hailiang", "tdsql_pg", "tdsql-pg", "vertica", "hetu", "trino", "presto", "hive",
  ].includes(code)) {
    return "timestamp";
  }
  if (code === "sqlserver") {
    return "datetime2";
  }
  return "datetime";
};

// 预览与提交共同使用每个目标库的类型，避免第二个目标沿用第一个库的 DATE。
const targetTableItems = (items: any[], dbType: any) => items.map((row: any) => {
  // The editor changes dataType/length; synchronize the hidden native definition
  // for both DDL preview and metadata submission, retaining origin fields.
  row = { ...row, defaultValue: null };
  const selected = String(row?.dataType || '').trim();
  const base = selected.toLowerCase().replace(/\s*\(.*$/, '').trim();
  const original = String(row?.columnType || '').toLowerCase().replace(/\s*\(.*$/, '').trim();
  const length = Number(row?.length);
  if (['varchar', 'varchar2', 'nvarchar', 'nvarchar2', 'char', 'nchar'].includes(base) && length > 0) {
    row = { ...row, columnType: `${selected.replace(/\s*\(.*$/, '')}(${length})` };
  } else if (['text', 'tinytext', 'mediumtext', 'longtext', 'clob', 'nclob'].includes(base)) {
    row = { ...row, columnType: base };
  } else if (original && original !== base
    && ['varchar', 'char', 'blob', 'date', 'datetime', 'timestamp', 'int', 'bigint', 'decimal', 'float', 'double', 'tinyint'].includes(base)) {
    row = { ...row, columnType: selected };
  }
  const name = String(row?.columnName || "").trim().toUpperCase();
  const format = String(row?.standardField || row?.dataStandardId || "").trim().toUpperCase();
  const targetType = format === "DATE" ? "date"
    : format === "DATETIME" || name === "ODS_RKSJ" || name === "ODS_GXSJ"
      ? materializationTimeType(dbType) : "";
  if (!targetType) return row;
  return { ...row, dataType: targetType, columnType: targetType, length: 0,
    precisionLength: 0, scale: 0, defaultValue: null };
});

const syncOdsSystemTimeTypes = (dbType: any) => {
  const rows = tableRef.value?.getData?.() || [];
  if (!rows.length) return;
  const normalizedRows = targetTableItems(rows, dbType);
  if (JSON.stringify(normalizedRows) !== JSON.stringify(rows)) {
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
  const dbId = orderTargetDatasourceIds(value)[0];
  const dbType = String(datasourceMap.value[dbId]?.dbType || "").trim();
  if (!dbType) return;
  const currentDbType = String(jsonFormRef.value?.getValue?.("dbType") || "").trim();
  if (currentDbType !== dbType) jsonFormRef.value?.setValue({ dbType });
  syncOdsSystemTimeTypes(dbType);
};

const buildTargetTableName = () => {
  // Keep the qualified source name for reading; only the default target uses its leaf name.
  const parts = String(sourceTableName.value || "").trim()
    .match(/"(?:""|[^"])*"|`(?:``|[^`])*`|\[(?:\]\]|[^\]])*\]|[^.]+/g) || [];
  const leaf = String(parts.at(-1) || "").trim();
  const rawName = leaf.replace(/^"(.*)"$/, "$1").replace(/^`(.*)`$/, "$1")
    .replace(/^\[(.*)\]$/, "$1");
  if (!rawName) return "";
  return /^ODS_/i.test(rawName) ? rawName : `ODS_${rawName}`;
};

const sameStringList = (left: string[] = [], right: string[] = []) =>
  left.length === right.length && left.every((value, index) => value === right[index]);

const normalizeTargetTableName = (value: any, dbType: any) => {
  const name = String(value || "").trim();
  const type = String(dbType || "").trim().toLowerCase();
  return type === "hive" || type === "mrshive" ? name.toLowerCase() : name;
};

// JsonForm/form-create is the sole owner of editable form values. These refs only
// project the selected target data sources into the drawer header; they never write
// a changed dbId back to formData, so a form change cannot trigger itself again.
const targetDatasourceIds = ref<string[]>([]);
const targetTableNameInput = ref("");

const syncTargetDatasourceSelection = (value: any) => {
  const nextIds = orderTargetDatasourceIds(value);
  if (!sameStringList(targetDatasourceIds.value, nextIds)) targetDatasourceIds.value = nextIds;
  syncDbTypeByDatasource(value);
  syncTargetTableNameInput(jsonFormRef.value?.getValue?.("tableName") || targetTableNameInput.value);
};

const syncTargetTableNameInput = (value: any) => {
  const rawName = String(value || "").trim();
  const onlyHive = targetDatasourceIds.value.length > 0 && targetDatasourceIds.value.every((dbId) =>
    ["hive", "mrshive"].includes(String(datasourceMap.value[dbId]?.dbType || "").trim().toLowerCase()));
  const nextName = onlyHive ? normalizeTargetTableName(rawName, "hive") : rawName;
  if (targetTableNameInput.value !== nextName) targetTableNameInput.value = nextName;
  if (props.type === "add" && nextName !== rawName
    && jsonFormRef.value?.getValue?.("tableName") !== nextName) {
    jsonFormRef.value?.setValue({ tableName: nextName });
  }
};

const normalizeFormPayload = (data: any) => {
  const targetDbIds = orderTargetDatasourceIds(data?.dbId);
  const dbId = targetDbIds[0];
  const selected = datasourceMap.value[dbId];
  const selectedPath = Array.isArray(data?.nifiNodePath) ? data.nifiNodePath : [];
  const selectedNodeId = selectedPath[1] || data?.nifiNodeId;
  const selectedNode = nifiNodeOptions.value.find((item: any) => item.value === selectedNodeId);
  const nifiNetworkCode = String(selectedPath[0] || data?.nifiNetworkCode || selectedNode?.networkCode || "").trim();
  return {
    ...data,
    tableName: String(data?.tableName || "").trim(),
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
  partitionPlans.value = {};
  ddlPartitionPlans.value = {};
  partitionSupport.value = {};
  partitionEnabled.value = false;
  ddlGeneratedSqlByTarget.value = {};
  ddlInputFingerprintsByTarget.value = {};
  ddlPartitionInputsByTarget.value = {};
  partitionProbeIdentity = "";
  sourcePartitionDetected.value = false;
  sourceConstraints.value = [];
  sourceDefinitionMessage.value = "";
  partitionError.value = "";
  loadError.value = "";
  sourcePreflightError.value = "";
  reuseTypeNotice.value = "";
  loadErrorCode.value = "";
  sourceFileConfig.value = null;
  knownTargets.value = (Array.isArray(props.existingTargets) ? props.existingTargets : [])
    .filter((target: any) => target?.targetTableId && target?.targetDbId && target?.targetTableName)
    .map((target: any) => ({ ...target }));
  recoveryTarget.value = knownTargets.value[0] || null;
  const sourceTableId = String(props.id || "").trim();
  // 运行时会预加载该组件；没有实际打开抽屉时不允许以空 tid 请求来源表模板。
  if (!sourceTableId) {
    templateLoading.value = false;
    return false;
  }
  templateLoading.value = true;
  loadPending.value = { datasource: true, node: true, source: true };
  try {
    const addUrl = "/ods/getTableTempalte";
    const viewUrl = "/ods/queryTargetTableInfo";
    // 三个请求互不依赖；收到全部结果后再设置表单默认目标库和接入节点。
    const [datasourceResult, nodeResult, sourceResult] = await Promise.allSettled([
      trackLoad("datasource", $common.post("/ods/targetDatasource/options", {
        taskCategory: props.taskCategory || "ingestion",
      }), runId),
      trackLoad("node", $common.post("/ods/nifi-node/access-options", {}), runId),
      trackLoad("source", $common.post(props.type === "add" ? addUrl : viewUrl, {
        tid: sourceTableId,
      }, { _hiddenErrorMsg: true }, 120 * 1000), runId),
    ]);
    if (!isCurrentInit(runId)) return false;
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
    if (!isCurrentInit(runId)) return false;
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
    sourceFileConfig.value = rawPropList.sourceFileConfig ? { ...rawPropList.sourceFileConfig } : null;
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
      propList.tableName = buildTargetTableName();
      propList.dbId = getDefaultDatasourcePaths();
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
    syncTargetTableNameInput(propList.tableName);
    formData.value = { ...propList };
    await nextTick();
    if (!isCurrentInit(runId)) return false;
    jsonFormRef.value?.setValue(formData.value);
    syncTargetDatasourceSelection(propList.dbId);
    // 设置表格数据(字段列表)。即使字段为空也明确清空上一张来源表的数据，
    // 避免复用抽屉时残留旧内容；非空时由 loadTemplateTableItems 等待 Grid
    // 就绪后再写入，避免首次打开偶发的空表竞态。
    templateWarnings.value = Array.isArray(payload.warnings) ? payload.warnings : [];
    await loadTemplateTableItems(payload.tableItems);
    if (!isCurrentInit(runId)) return false;
    if (templateTableItems.value.length) {
      const selected = datasourceMap.value[normalizeDbId(propList.dbId)];
      syncOdsSystemTimeTypes(selected?.dbType || propList.dbType);
    }
    return true;
  } catch (error: any) {
    if (!isCurrentInit(runId)) return false;
    console.error("物化建表模板加载失败", error);
    // 保留错误与重试入口；全局请求层可能已提示，避免再弹重复消息。
    loadError.value = String(error?.message || "物化建表信息加载失败，请重试");
    loadErrorCode.value = String(error?.errorCode || "");
    return false;
  } finally {
    if (isCurrentInit(runId)) templateLoading.value = false;
  }
};

const handleCancel = () => {
  if (isSaveing.value || recoveryDeleting.value) return;
  cancelPendingInit();
  emit("close");
  open.value = false;
};

const buildDdlTargets = (data: any) => data.targetDbIds.map((dbId: string) => {
  const datasource = datasourceMap.value[dbId] || {};
  return {
    key: dbId,
    dbId,
    dbName: datasource.datasourceName || datasource.dbName || datasource.label || dbId,
    propList: {
      ...data,
      dbId,
      dbType: datasource.dbType || data.dbType,
      tableName: normalizeTargetTableName(data.tableName, datasource.dbType || data.dbType),
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
  await probePartitions(targets);
  if (targets.some((target: any) => !target.propList.tableName)) throw new Error("请填写目标表名");
  if (data.tableName.includes(".")) throw new Error("目标表名只填写表名，不能包含用户名或 Schema 前缀");
  syncTargetTableNameInput(data.tableName);
  const fingerprint = JSON.stringify({
    targets: targets.map((target: any) => ({ dbId: target.dbId, dbType: target.propList.dbType, tableName: target.propList.tableName, tableNameCn: target.propList.tableNameCn, partitionPlan: partitionPlans.value[target.key] || { enabled: false } })),
    tableItems,
    sourceConstraints: sourceConstraints.value,
  });
  return { targets, tableItems, fingerprint };
};

const ddlTargetFingerprint = (input: any, target: any) => JSON.stringify([
  target.propList, input.tableItems, partitionPlans.value[target.key] || { enabled: false }, sourceConstraints.value,
]);
const regenerateDdl = async (input: any, targetKeys?: string[]) => {
  // Only changed target inputs need regeneration. Tab switching and changes in
  // another target must never replace an independently edited SQL draft.
  const changed = input.targets.filter((target: any) => (!targetKeys || targetKeys.includes(target.key))
    && ddlInputFingerprintsByTarget.value[target.key] !== ddlTargetFingerprint(input, target));
  const edited = changed.filter((target: any) => ddlSqlByTarget.value[target.key]
    && ddlSqlByTarget.value[target.key] !== ddlGeneratedSqlByTarget.value[target.key]);
  if (targetKeys && edited.length) {
    await ElMessageBox.confirm(`本次分区调整会重新生成“${edited.map((target: any) => target.dbName).join('、')}”的手工 SQL。是否继续？`,
      "更新分区", { type: "warning", confirmButtonText: "重新生成", cancelButtonText: "取消" });
  }
  const generationVersion = ++ddlGenerationVersion;
  ddlGenerating.value = true;
  ddlPlans.value = input.targets;
  if (!input.targets.some((target: any) => target.key === activeDdlKey.value)) activeDdlKey.value = input.targets[0]?.key || "";
  try {
    const statements: Record<string, string> = {};
    const warnings: Record<string, any[]> = {};
    const convertedItems: Record<string, any[]> = {};
    const normalizedPlans: Record<string, any> = {};
    const inputFingerprints = Object.fromEntries(changed.map((target: any) => [target.key, ddlTargetFingerprint(input, target)]));
    const partitionInputs = Object.fromEntries(changed.map((target: any) => [target.key,
      JSON.parse(JSON.stringify(partitionPlans.value[target.key] || { enabled: false }))]));
    const targets = changed.map((target: any) => {
      return { propList: target.propList, tableItems: targetTableItems(input.tableItems, target.propList.dbType), partitionPlan: partitionPayload(target.key), sourceConstraints: sourceConstraints.value };
    });
    const response = targets.length ? await $common.post("/dst/database/metadata/getCreateTableDDL", {
      batchGenerate: true,
      canvas: true,
      targets,
    }, { _hiddenErrorMsg: true }) : { data: { batchGenerate: true, results: [] } };
    const generated = response?.data || response || {};
    if (generationVersion !== ddlGenerationVersion) return;
    if (generated.batchGenerate !== true || !Array.isArray(generated.results)
      || generated.results.length !== changed.length) {
      throw new Error("目标表批量建表语句生成结果不完整，请重试");
    }
    for (const [index, target] of changed.entries()) {
      const result = generated.results[index];
      normalizedPlans[target.key] = result.partitionPlan || { enabled: false };
      if (String(result?.dbId) !== String(target.dbId)) {
        throw new Error("目标表批量建表语句生成结果与所选数据源不一致，请重试");
      }
      if (String(result?.requestedTableName || "") !== target.propList.tableName
        || String(result?.tableName || "") !== target.propList.tableName) {
        throw new Error(`目标数据源“${target.dbName}”生成的表名与输入框不一致，请刷新后重试`);
      }
      const statement = String(result?.ddl || "").trim();
      if (!statement) throw new Error(`目标数据源“${target.dbName}”未生成建表语句`);
      statements[target.key] = statement;
      warnings[target.key] = Array.isArray(result.warnings) ? result.warnings : [];
      const original = targetTableItems(input.tableItems, target.propList.dbType);
      const byName = new Map(original.map((row:any) => [String(row.columnName).toLowerCase(),row]));
      convertedItems[target.key] = Array.isArray(result.tableItems) ? result.tableItems.map((row:any) => ({
        ...(byName.get(String(row.columnName).toLowerCase()) || {}), ...row,
      })) : original;
    }
    ddlPlans.value = input.targets;
    const retain = (previous: Record<string, any>, next: Record<string, any>) => Object.fromEntries(
      input.targets.filter((target: any) => target.key in next || target.key in previous)
        .map((target: any) => [target.key, target.key in next ? next[target.key] : previous[target.key]]),
    );
    ddlPartitionPlans.value = retain(ddlPartitionPlans.value, normalizedPlans);
    ddlSqlByTarget.value = retain(ddlSqlByTarget.value, statements);
    ddlWarningsByTarget.value = retain(ddlWarningsByTarget.value, warnings);
    ddlTableItemsByTarget.value = retain(ddlTableItemsByTarget.value, convertedItems);
    ddlGeneratedSqlByTarget.value = retain(ddlGeneratedSqlByTarget.value, statements);
    ddlInputFingerprintsByTarget.value = retain(ddlInputFingerprintsByTarget.value, inputFingerprints);
    ddlPartitionInputsByTarget.value = retain(ddlPartitionInputsByTarget.value, partitionInputs);
    if (!input.targets.some((target: any) => target.key === activeDdlKey.value)) activeDdlKey.value = input.targets[0]?.key || "";
    ddlSourceFingerprint.value = input.targets.every((target: any) =>
      ddlInputFingerprintsByTarget.value[target.key] === ddlTargetFingerprint(input, target)) ? input.fingerprint : "";
    ddlWasEdited.value = input.targets.some((target: any) =>
      ddlSqlByTarget.value[target.key] !== ddlGeneratedSqlByTarget.value[target.key]);
  } catch (error: any) {
    if (generationVersion !== ddlGenerationVersion) return;
    ddlSourceFingerprint.value = "";
    if (error && typeof error === "object") error.ddlGeneration = true;
    throw error;
  } finally {
    if (generationVersion === ddlGenerationVersion) ddlGenerating.value = false;
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
    if (partitionPlans.value[activeDdlKey.value]?.enabled) {
      ddlMode.value = true;
      ddlSourceFingerprint.value = "";
      partitionErrorType.value = error?.partitionValidation ? "warning" : "error";
      partitionError.value = String(error?.message || "分区配置无效，请调整字段或取消创建分区");
    }
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
  startMaterializationProgress();
  let lastTargetForm: any = null;
  const reuseTargetDbIds = new Set<string>();
  sourcePreflightError.value = '';
  reuseTypeNotice.value = '';
  try {
    const ddlInput = await currentDdlInput();
    materializationProgress.value.detail = '正在预检来源类型及文件定位信息';
    try {
      const sourcePreflightResponse = await $common.post('/ods/dataAggTaskEnsure', {
        tableId: props.id, dryRun: true, validateSourceOnly: true,
      }, { _hiddenErrorMsg: true }, 60 * 1000);
      const sourcePreflight = sourcePreflightResponse?.data || sourcePreflightResponse || {};
      if (sourcePreflight.sourcePreflight !== true || sourcePreflight.valid !== true
        || sourcePreflight.sideEffectsApplied !== false || String(sourcePreflight.sourceTableId) !== String(props.id)) {
        throw new Error('来源预检结果不完整，请重试');
      }
    } catch (error: any) {
      sourcePreflightError.value = String(error?.message || '来源预检失败，请检查数据源登记配置后重试');
      throw error;
    }
    materializationProgress.value.total = ddlInput.targets.length;
    if (ddlWasEdited.value) {
      // Manual SQL is authoritative; do not rerun the generator or its partition/key rules before execution.
      for (const target of ddlInput.targets) {
        if (!String(ddlSqlByTarget.value[target.key] || "").trim()) throw new Error(`请填写“${target.dbName}”的建表语句`);
        ddlPartitionPlans.value[target.key] = partitionPlans.value[target.key]?.enabled
          ? (ddlPartitionPlans.value[target.key]?.enabled ? ddlPartitionPlans.value[target.key] : partitionPlans.value[target.key])
          : { enabled: false };
      }
      ddlSourceFingerprint.value = ddlInput.fingerprint;
    }
    if (!ddlSourceFingerprint.value) await regenerateDdl(ddlInput);
    if (ddlSourceFingerprint.value && ddlSourceFingerprint.value !== ddlInput.fingerprint) {
      if (ddlWasEdited.value) {
        await confirmMaterialization(
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
      // Batch-check selected target authorization; SQL syntax and constraints are decided by the database.
      const targets = ddlInput.targets.map((target: any) => {
        const ddl = String(ddlSqlByTarget.value[target.key] || "").trim();
        if (!ddl) throw new Error(`请填写“${target.dbName}”的建表语句`);
        return {
          dbId: target.dbId,
          tableName: target.propList.tableName,
          ddl,
          partitionPlan: ddlPartitionPlans.value[target.key],
          tableItems: ddlTableItemsByTarget.value[target.key],
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
        throw new Error("目标数据源预检结果不完整，请重试");
      }
      for (const [index, target] of targets.entries()) {
        const result = validation.results[index];
        if (String(result?.dbId) !== String(target.dbId)
          || String(result?.requestedTableName).trim().toUpperCase() !== target.tableName.toUpperCase()
          || result?.valid !== true || result?.sideEffectsApplied !== false) {
          throw new Error("目标数据源预检结果与所选目标不一致，请重试");
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
    materializationProgress.value.phase = 1;
    materializationProgress.value.detail = "正在检查目标表是否存在，并核对建表语句";
    const preflightResponse = await $common.post('/ods/dataAggReset', {
        batchDryRun: true,
        checkPhysicalExistence: true,
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
    if (preflight.results.some((probe:any) => probe.physicalCheckWarning)) {
      ElMessage.warning("部分目标库暂不能读取表目录，将继续执行建表；数据库已有表时会提示处理");
    }
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
      const canReuse = conflicts.some((item: any) => item.probe.targetPhysicalExists === true);
      const choice = await askExistingDecision(conflicts, canReuse);
      if (choice === 'cancel') return;
      if (choice === 'flow') {
        if (!canReuse) throw new Error('没有可复用的目标物理表');
        const reusable = conflicts.filter((item: any) => item.probe.targetPhysicalExists === true);
        const targets = reusable.map((item: any) => {
          const target = ddlInput.targets.find((plan: any) => String(plan.dbId) === String(item.targetDbId));
          return { dbId:target.dbId, tableName:target.propList.tableName,
            ddl:ddlSqlByTarget.value[target.key], partitionPlan:ddlPartitionPlans.value[target.key], tableItems:targetTableItems(ddlInput.tableItems,target.propList.dbType) };
        });
        const response = await $common.post('/dst/database/metadata/createTable', {
          canvas:true, validateOnly:true, batchReuseValidation:true, targets,
        }, { _hiddenErrorMsg:true }, 120 * 1000);
        const validation = response?.data || response || {};
        if (validation.batchReuseValidation !== true || validation.sideEffectsApplied !== false
          || !Array.isArray(validation.results) || validation.results.length !== targets.length) {
          throw new Error('已有目标表批量结构校验结果不完整');
        }
        for (const [index, target] of targets.entries()) {
          const result = validation.results[index];
          if (String(result?.dbId) !== String(target.dbId) || result?.requestedTableName !== target.tableName
            || result?.valid !== true || result?.physicalTableReused !== true || result?.sideEffectsApplied !== false) {
            throw new Error('已有目标表结构校验与所选目标不一致');
          }
          reuseTargetDbIds.add(String(target.dbId));
        }
        const compatibilityWarnings = validation.results.flatMap((result: any) =>
          Array.isArray(result.compatibilityWarnings) ? result.compatibilityWarnings : []);
        if (compatibilityWarnings.length) {
          reuseTypeNotice.value = `已有表可复用：${compatibilityWarnings.length}项字段沿用现有文本类型，不修改已有表结构，可继续创建任务。`;
          ElMessage.info(reuseTypeNotice.value);
        }
      } else {
        await validateEditedDdl();
        await confirmMaterialization(
          `将删除 ${conflicts.length} 个同名目标物理表及其关联元数据，然后重新物化。删除不可恢复，确认继续吗？`,
          '删除目标表并重新物化',
          { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
        );
        materializationProgress.value.detail = "正在清理已确认重新物化的目标表";
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
          if (outcome.targetCleanupPending || outcome.processGroupCleanupPendingCount > 0
            || (item.probe.targetPhysicalExists && outcome.targetPhysicalDeleted !== true)) {
            throw new Error(`目标物理表“${item.targetTableName}”未确认删除，已停止重新物化`);
          }
          if (item.known) {
            knownTargets.value = knownTargets.value.filter((target: any) => target.targetTableId !== item.known.targetTableId);
            emit('target-deleted', item.known);
          }
        }
        recoveryTarget.value = null;
      }
    }
    if (!conflicts.length || reuseTargetDbIds.size) await validateEditedDdl();
    materializationProgress.value.phase = 2;
    const targetTables: any[] = [];
    for (const target of ddlInput.targets) {
      const targetDbId = target.dbId;
      const targetDatasource = datasourceMap.value[targetDbId] || {};
      // Reuse the exact target configuration used by DDL preview and preflight validation.
      const targetForm = { ...target.propList };
      lastTargetForm = targetForm;
      const reuseExistingTable = reuseTargetDbIds.has(String(targetDbId));
      materializationProgress.value.detail = `正在${reuseExistingTable ? '复用' : '创建'}第 ${targetTables.length + 1} / ${ddlInput.targets.length} 张：${target.dbName} · ${targetForm.tableName}`;
      const result = await $common.post("/ods/createTapleApply", {
        propList: targetForm,
        partitionPlan: ddlPartitionPlans.value[target.key],
        tableItems: ddlTableItemsByTarget.value[target.key] || targetTableItems(ddlInput.tableItems, targetForm.dbType),
        tid: props.id,
        ddl: ddlSourceFingerprint.value ? ddlSqlByTarget.value[targetDbId] : undefined,
        reuseExistingTable,
        recreateMissingTable: !reuseExistingTable,
      }, { _hiddenErrorMsg: true }, 120 * 1000);
      const payload = result?.data || result || {};
      const targetTableId = payload.targetTableId || payload.tid;
      if (!targetTableId) {
        throw new Error(`目标数据源“${targetDatasource.dbName || targetDbId}”的目标表未创建成功`);
      }
      materializationProgress.value.completed = targetTables.length + 1;
      targetTables.push({
        ...payload,
        targetTableId,
        targetDbId,
        targetDbName: targetDatasource.dbName || targetDatasource.label || "",
        targetDbType: targetDatasource.dbType || "",
        targetTableName: targetForm.tableName,
      });
    }
    ElMessage.success(`已处理 ${targetTables.length} 个目标表（复用 ${reuseTargetDbIds.size} 个），正在准备任务画布`);
    emit("save", {
      ...targetTables[0],
      targetTableId: targetTables[0]?.targetTableId || "",
      targetTables,
      sourceFileConfig: sourceFileConfig.value,
    });
  } catch (error: any) {
    const message = String(error?.message || error || "");
    if (error?.partitionValidation || error?.ddlGeneration) {
      ddlSourceFingerprint.value = "";
      if (error?.partitionTargetKey) activeDdlKey.value = error.partitionTargetKey;
      ddlMode.value = true;
      partitionErrorType.value = error?.partitionValidation ? "warning" : "error";
      partitionError.value = message;
    }
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
    if (message.includes('目标物理表已存在')) {
      // 检查与建表之间有其他请求创建了同名表；重新提交走完整预检及明确复用，不能绕过结构校验直接生成流程。
      ElMessage.warning('目标库状态已变化，同名物理表已存在。请再次提交，在提示中确认复用已有表后继续创建流程。');
    }
  } finally {
    stopMaterializationProgress();
    isSaveing.value = false;
  }
};

// A pre-existing physical target is adopted only after explicit confirmation
// and server-side validation of its real fields.
// 页面只负责说明策略，删除类型与权限由服务端登记记录决定。
const isHiveTarget = (target) => ['hive', 'mrshive', 'huaweihive', 'huaweimrshive'].includes(
  String(target?.targetDatabaseType || target?.targetDbType || target?.dbType || datasourceMap.value[target?.targetDbId]?.dbType || '')
    .trim().toLowerCase().replace(/[_-]/g, ''),
);
const targetCleanupConfirmText = (target, preview = {}) => {
  const name = target?.targetTableName || target?.tableName || '当前目标表';
  if (preview.targetHivePhysicalRetained === true || isHiveTarget(target)) {
    return `将移除“${name}”的接入流程和目标登记，Hive 物理表及数据保留。后续可在物化建表时确认复用已有表并重新创建流程。确认继续吗？`;
  }
  if (preview.targetCleanupPending === true) {
    return `目标库暂时无法检查，将取消可清理的接入流程，并保留“${name}”的目标登记供重试。确认继续吗？`;
  }
  return `将删除目标物理表“${name}”及其元数据，并移除接入流程。该操作不可恢复；若目标库删除失败会保留登记供重试。确认继续吗？`;
};
const targetCleanupSummary = (outcomes) => {
  const pending = outcomes.filter((item) => item.targetCleanupPending === true || Number(item.processGroupCleanupPendingCount || 0) > 0).length;
  const hive = outcomes.filter((item) => item.targetHivePhysicalRetained === true).length;
  const message = outcomes.length === 1 && outcomes[0].message
    ? outcomes[0].message
    : `已处理 ${outcomes.length} 个目标的接入清理${hive ? `；${hive} 个 Hive 物理表及数据保留` : ''}${pending ? `；${pending} 个目标有清理待重试，相关登记或流程记录已保留` : ''}`;
  return { pending, message };
};

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
      `${preview.targetUnlinked ? '该目标表未关联当前来源表，请先核对数据用途。' : ''}${targetCleanupConfirmText(target, preview)}`,
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
    const summary = targetCleanupSummary([outcome]);
    if (outcome.targetMetadataRetained !== true && outcome.targetCleanupPending !== true) {
      knownTargets.value = knownTargets.value.filter((item: any) => item.targetTableId !== target.targetTableId);
      recoveryTarget.value = null;
      emit('target-deleted', target);
    }
    ElMessage[summary.pending ? 'warning' : 'success'](summary.message);
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
onBeforeUnmount(stopMaterializationProgress);
watch([open, ddlMode, activeDdlKey], ([visible, isDdl]) => {
  stopDdlEditorSizing();
  if (!visible || !isDdl) return;
  const body = ddlEditorHost.value?.closest(".el-drawer__body");
  if (!body) return;
  syncDdlEditorHeight();
  if (typeof ResizeObserver !== "undefined") {
    ddlSizeObserver = new ResizeObserver(syncDdlEditorHeight);
    ddlSizeObserver.observe(body);
  }
  window.addEventListener("resize", syncDdlEditorHeight);
}, { flush: "post" });
onBeforeUnmount(stopDdlEditorSizing);
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
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

:global(.el-drawer__body.access-apply-ddl) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-bottom: 12px;
}

:global(.el-drawer__body.access-apply .u-title) {
  padding: 8px 0 !important;
}

:global(.access-apply-drawer .el-drawer__header) {
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
.access-apply-partition-notice {
  flex: 0 1 auto;
  padding: 4px 8px;
  border-radius: 4px;
  background: #fff7e6;
  color: #a66408;
  font-size: 12px;
  line-height: 1.5;
}
.template-load-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
}
.template-load-error .el-alert { width: 100%; }
.source-preflight-error { flex: 0 0 auto; margin-bottom: 12px; }
// 内容在弹框可见高度内滚动，遮罩始终覆盖这个可见区域，不跟随字段总高度。
.access-apply-content { position: relative; display: flex; flex: 1; flex-direction: column; min-height: 0; overflow: hidden; }
.materialization-form-view { flex: 1; min-width: 0; min-height: 0; overflow: auto; }
.materialization-progress-overlay { position: absolute; inset: 0; z-index: 20; display: flex; align-items: center; justify-content: center; padding: 18px; background: rgba(248, 250, 255, .94); }
.materialization-progress-card { width: min(100%, 410px); max-height: 100%; overflow: auto; padding: 26px 28px 22px; box-sizing: border-box; border: 1px solid #e2eaf8; border-radius: 14px; background: #fff; box-shadow: 0 10px 40px rgba(33, 78, 145, .1); text-align: center; }
.materialization-progress-symbol { position: relative; display: flex; align-items: center; justify-content: center; width: 60px; height: 60px; margin: 0 auto 16px; color: #2269ed; background: #eef4ff; border-radius: 50%; font-size: 25px; span { position: absolute; inset: -4px; border: 2px solid #dce8ff; border-top-color: #2269ed; border-radius: 50%; animation: materialization-spin 1.4s linear infinite; } }
.materialization-progress-card h3 { margin: 0 0 10px; color: #24364e; font-size: 18px; font-weight: 600; }
.materialization-progress-detail { margin: 0; color: #5b6d87; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.materialization-progress-meta { display: flex; justify-content: center; flex-wrap: wrap; gap: 6px 16px; margin: 10px 0 20px; color: #7c8ba2; font-size: 12px; }
.materialization-progress-steps { display: flex; flex-direction: column; gap: 9px; margin: 0; padding: 0; list-style: none; text-align: left; li { display: flex; align-items: center; gap: 10px; padding: 9px 11px; border-radius: 7px; color: #8591a4; font-size: 13px; background: #f8f9fc; small { margin-left: auto; font-size: 11px; white-space: nowrap; } } li.is-current { color: #2269ed; background: #eef4ff; } li.is-complete { color: #429068; } }
.materialization-step-mark { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; flex: 0 0 20px; border-radius: 50%; border: 1px solid currentColor; font-size: 11px; }
.materialization-progress-note { margin: 18px 0 0; color: #8a96a8; font-size: 12px; line-height: 1.7; }
@keyframes materialization-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .materialization-progress-symbol span { animation: none; } }
@media (max-height: 550px) { .materialization-progress-card { padding: 14px 18px; } .materialization-progress-symbol { display: none; } .materialization-progress-meta { margin-bottom: 10px; } .materialization-progress-note { margin-top: 10px; } }

.access-apply-content.is-ddl-mode {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
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
.ddl-target-toolbar { display: flex; align-items: center; gap: 20px; padding-right: 16px; flex: 0 0 auto; }
.ddl-target-tabs { padding: 0 12px; flex: 1; min-width: 0; }
.ddl-target-toolbar > .el-checkbox { flex: 0 0 auto; margin-right: 0; }
.partition-settings { padding: 8px 12px; background: var(--el-fill-color-light); flex: 0 0 auto; max-height: 180px; overflow: auto; }
.partition-settings-fields { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 8px 0; }
.partition-settings-fields :deep(.el-select), .partition-settings-fields :deep(.el-input) { width: 200px; }
.partition-settings-fields :deep(.partition-values-input.el-input) { width: min(100%, 420px); flex: 1 1 280px; }
.ddl-editor-host {
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
.ddl-inline-panel :deep(.el-scrollbar) {
  height: 100%;
  width: 100%;
}
.ddl-inline-panel :deep(.ace_editor) { min-height: 200px !important; }

@media (max-height: 430px) {
  .ddl-editor-host { overflow: auto; }
}
</style>
