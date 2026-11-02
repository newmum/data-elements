<template>
  <div
    v-loading="initializing && context !== 'detail'"
    :aria-busy="initializing"
    class="api-register-content"
    :class="{ 'is-readonly': currentMode === 'view' }"
  >
    <LoadingState v-if="initializing && context === 'detail'" type="form" />
    <div v-show="!initializing || context !== 'detail'">
    <div v-if="context === 'detail'" class="registration-heading-actions">
      <el-button v-if="!isEditing" type="primary" plain @click="startEdit">编辑服务</el-button>
      <template v-else>
        <el-button :disabled="saveLoading" @click="handleCancel">取消编辑</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">保存修改</el-button>
      </template>
    </div>
    <el-alert
      v-if="initializationError"
      type="error"
      :title="initializationError"
      show-icon
      :closable="false"
    >
      <el-button type="primary" link @click="setFormData(data)">重新加载登记信息</el-button>
    </el-alert>
    <json-form
      v-if="definitionLoaded"
      ref="formRef"
      bordered
      responsive
      label-width="144px"
      :rules="formRules"
      :options="formOptions"
      @change="onFormChange"
    />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from "vue";
import type { FormRule } from "@form-create/element-ui";
import { cloneDeep } from "lodash-es";
import { ElMessage, ElMessageBox } from "element-plus";

type RecordValue = Record<string, unknown>;
interface FormApi {
  validate(): Promise<boolean>;
  getFormData(): Promise<RecordValue>;
  setValue(value: RecordValue): void;
}
interface ServiceParameter {
  paramName: string;
  description: string;
  paramType: string;
  paramPosition: string;
  required: string;
  defaultValue: string;
  showcaseValue: string;
  paramScope: string;
}
type ExampleScope = "request" | "response";
interface Snapshot {
  form: RecordValue;
  params: ServiceParameter[];
  request: string;
  response: string;
  exampleInitialization: Record<ExampleScope, boolean>;
}
const props = withDefaults(defineProps<{ context?: "modal" | "detail"; data?: RecordValue }>(), {
  context: "modal",
  data: () => ({}),
});
const emit = defineEmits<{
  (event: "saved", data: RecordValue): void;
  (event: "ready", ready: boolean): void;
  (event: "change"): void;
}>();
const record = (value: unknown): RecordValue => {
  if (typeof value === "string") {
    try {
      return record(JSON.parse(value));
    } catch {
      return {};
    }
  }
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as RecordValue)
    : {};
};
const text = (value: unknown): string => (value == null ? "" : String(value));
const payloadOf = (response: unknown): RecordValue => {
  const value = record(response);
  return record(value.data ?? value);
};
const messageOf = (error: unknown, fallback: string): string =>
  error instanceof Error ? error.message : text(record(error).message) || fallback;
const formRef = ref<FormApi>();
const isEditing = ref(props.context === "modal");
const currentMode = computed(() =>
  props.context === "modal" || isEditing.value ? "edit" : "view"
);
const saveLoading = ref(false),
  initializing = ref(true),
  initializationError = ref("");
const formRules = ref<FormRule[]>([]);
const activeExample = ref<ExampleScope>("request");
const definitionLoaded = ref(false);
const formOptions = computed(() => ({
  form: {
    disabled: currentMode.value === "view" || !!initializationError.value,
    showMessage: true,
    labelWidth: "144px",
    labelPosition: "right",
    colon: false,
  },
  global: {
    "*": {
      props: {
        disabled: currentMode.value === "view",
        readOnly: currentMode.value === "view",
      },
    },
  },
}));
let definitionRequest: Promise<void> | undefined;
const configuredDefaults: RecordValue = {};
const collectDefaults = (rules: unknown[]) => {
  for (const item of rules) {
    const rule = record(item);
    if (typeof rule.field === "string" && rule.value !== undefined)
      configuredDefaults[rule.field] = cloneDeep(rule.value);
    if (Array.isArray(rule.children)) collectDefaults(rule.children);
  }
};
const loadDefinition = () =>
  (definitionRequest ??= $form
    .loadDefinition("登记API服务")
    .then((definition) => {
      if (!definition.rules.length)
        throw new Error("登记API服务的表单规则为空，请在表单配置中完善");
      collectDefaults(definition.rules);
      const rules = cloneDeep(definition.rules);
      bindExampleActions(rules);
      formRules.value = rules;
      definitionLoaded.value = true;
    })
    .catch((error: unknown) => {
      definitionRequest = undefined;
      throw error;
    }));
let exampleInitialized: Record<ExampleScope, boolean> = { request: false, response: false };
const generating = new Set<ExampleScope>();
let snapshot: Snapshot = {
  form: {}, params: [], request: "", response: "",
  exampleInitialization: { request: false, response: false },
};
let initializationVersion = 0;
let userRequest: Promise<RecordValue> | undefined;
const currentUser = (): Promise<RecordValue> =>
  (userRequest ??= $common
    .post("/sym/user/me", {})
    .then(payloadOf)
    .catch((error: unknown) => {
      userRequest = undefined;
      throw error;
    }));

const normalizeParams = (values: unknown): ServiceParameter[] => {
  let rows: unknown = values;
  if (typeof rows === "string") {
    try {
      rows = JSON.parse(rows);
    } catch {
      rows = [];
    }
  }
  return (Array.isArray(rows) ? rows : []).map((value) => {
    const row = record(value);
    const scope = text(row.paramScope || row.param_scope).trim();
    return {
      paramName: text(row.paramName || row.param_name),
      description: text(row.description || row.paramDesc),
      paramType: text(row.paramType || row.param_type),
      paramPosition: text(row.paramPosition || row.param_position),
      required: ["1", "true", "是", "必填"].includes(text(row.required).trim().toLowerCase()) ? "1" : "0",
      defaultValue: text(row.defaultValue),
      showcaseValue: text(row.showcaseValue),
      paramScope: scope === "出参" ? "response" : scope === "入参" ? "request" :
        scope || (text(row.res) === "1" ? "response" : "request"),
    };
  });
};
const formValues = async (): Promise<RecordValue> => ({
  ...snapshot.form,
  ...(await formRef.value?.getFormData()),
  // 服务标识以保存结果为准，避免未重新挂载的表单模型恢复首次登记时的空值。
  id: snapshot.form.id,
  orgId: snapshot.form.orgId,
});
const exampleValue = (row: ServiceParameter): unknown => {
  const supplied = row.showcaseValue.trim() || row.defaultValue.trim();
  const type = row.paramType.toLowerCase();
  const numeric = ["number", "int", "integer", "long", "float", "double", "decimal", "short"];
  if (supplied) {
    if (numeric.includes(type) && Number.isFinite(Number(supplied))) return Number(supplied);
    if (type === "boolean" || type === "bool") {
      if (["true", "1"].includes(supplied.toLowerCase())) return true;
      if (["false", "0"].includes(supplied.toLowerCase())) return false;
    }
    if (["object", "array", "json"].includes(type)) {
      try { return JSON.parse(supplied); } catch { /* 保留用户提供的示例或占位值。 */ }
    }
    return supplied;
  }
  if (numeric.includes(type)) return 0;
  if (type === "boolean" || type === "bool") return false;
  if (type === "array") return [];
  if (type === "object" || type === "json") return {};
  if (type === "date") return "2026-01-01";
  if (type === "datetime") return "2026-01-01 00:00:00";
  return "";
};
const createExample = (rows: ServiceParameter[], scope: ExampleScope): string =>
  JSON.stringify(Object.fromEntries(rows.filter((row) => row.paramScope === scope && row.paramName.trim())
    .map((row) => [row.paramName.trim(), exampleValue(row)])), null, 2);
const canEditExamples = () => currentMode.value === "edit";
const generateExample = async (scope: ExampleScope, explicit = true): Promise<boolean> => {
  if (initializing.value || !canEditExamples() || saveLoading.value || generating.has(scope)) return false;
  if (!explicit && exampleInitialized[scope]) return false;
  const version = initializationVersion;
  const field = `${scope}Example`;
  generating.add(scope);
  try {
    const form = await formValues();
    const previous = text(form[field]);
    if (!explicit && previous.trim()) { exampleInitialized[scope] = true; return false; }
    const generated = createExample(normalizeParams(form.publishParams), scope);
    if (explicit && previous.trim() && previous !== generated) {
      try {
        await ElMessageBox.confirm(
          `将根据当前${scope === "request" ? "入参" : "出参"}重新生成${scope === "request" ? "请求" : "响应"}示例，并替换现有内容。`,
          "重新生成示例", { confirmButtonText: "生成并替换", cancelButtonText: "保留现有内容", type: "warning" }
        );
      } catch { return false; }
    }
    if (version !== initializationVersion) return false;
    const latest = await formValues();
    // 异步确认期间切换服务、进入只读或继续编辑时，不覆盖新的内容。
    if (version !== initializationVersion || !canEditExamples() || text(latest[field]) !== previous) return false;
    exampleInitialized[scope] = true;
    formRef.value?.setValue({ ...latest, [field]: generated });
    emit("change");
    return true;
  } catch (error) {
    ElMessage.error(messageOf(error, "示例未生成，请重新打开服务后重试"));
    return false;
  } finally { generating.delete(scope); }
};
const onFormChange = (field: string) => {
  if (!initializing.value && field === "requestExample") exampleInitialized.request = true;
  if (!initializing.value && field === "responseExample") exampleInitialized.response = true;
  emit("change");
};
// 标题、按钮及位置由租户表单配置持有，登记组件只连接对应业务行为。
const bindExampleActions = (rules: unknown[]) => {
  for (const value of rules) {
    const rule = record(value);
    if (rule.name === "generateActiveExample")
      rule.on = { ...record(rule.on), click: () => generateExample(activeExample.value) };
    for (const scope of ["request", "response"] as const) {
      if (rule.name === `generate${scope === "request" ? "Request" : "Response"}Example`)
        rule.on = { ...record(rule.on), click: () => { void generateExample(scope); } };
      if (rule.field === `${scope}Example`)
        rule.on = { ...record(rule.on), focus: () => { void generateExample(scope, false); } };
    }
    if (rule.name === "serviceExamples")
      rule.on = { ...record(rule.on), tabChange: (name: string) => {
        if (name === "request" || name === "response") {
          activeExample.value = name;
          return generateExample(name, false);
        }
      } };
    if (Array.isArray(rule.children)) bindExampleActions(rule.children);
  }
};
const buildPayload = (form: RecordValue, rows: ServiceParameter[]): RecordValue => ({
  id: text(snapshot.form.id),
  name: text(form.name).trim(),
  serviceCode: text(form.serviceCode).trim(),
  version: text(form.version) || "1.0",
  description: text(form.description),
  labels: text(snapshot.form.labels),
  groupId: props.data.groupId || "api_service_group_default",
  status: props.data.status || "draft",
  serviceCreateType: "api",
  resourceId: props.data.resourceId,
  datasourceId: props.data.datasourceId,
  providerConfig: {
    ...record(props.data.providerConfig),
    protocol: form.protocol,
    address: text(form.address).trim(),
    httpMethod: form.httpMethod,
  },
  publishConfig: {
    ...record(props.data.publishConfig),
    publishProtocol: form.protocol,
    // 新登记沿用服务地址；已有服务保留其历史发布路径。
    publishAddress: text(form.publishAddress || form.address).trim(),
    publishMethod: form.httpMethod,
  },
  publishParams: rows.map((row, index) => ({
    ...row,
    ordering: String(index + 1),
    res: row.paramScope === "response" ? 1 : 0,
  })),
  extendInfo: {
    ...record(props.data.extendInfo),
    orgId: form.orgId,
    dataDomain: form.dataDomain,
    dataCategory: form.dataCategory,
    dataLevel: form.dataLevel,
    returnType: form.returnType,
    dataUpdateFreq: form.dataUpdateFreq,
    shareType: form.shareType,
    apiDescription: text(form.apiDescription),
    requestExample: text(form.requestExample),
    responseExample: text(form.responseExample),
    exampleInitialization: { ...exampleInitialized },
  },
});
const restore = async () => {
  exampleInitialized = { ...snapshot.exampleInitialization };
  await nextTick();
  formRef.value?.setValue({
    ...snapshot.form,
    publishParams: snapshot.params.map((row) => ({ ...row })),
    requestExample: snapshot.request,
    responseExample: snapshot.response,
  });
  await nextTick();
};
const setFormData = async (data: RecordValue = {}) => {
  const version = ++initializationVersion;
  initializing.value = true;
  emit("ready", false);
  initializationError.value = "";
  try {
    const [user] = await Promise.all([currentUser(), loadDefinition()]);
    if (version !== initializationVersion) return;
    const ext = record(data.extendInfo),
      provider = record(data.providerConfig),
      publish = record(data.publishConfig);
    const orgId = ext.orgId || data.orgId || user.orgId;
    const form: RecordValue = {
      id: text(data.id || data.tid),
      name: text(data.serviceName || data.name),
      serviceCode: text(data.serviceCode || data.code),
      version: text(data.version) || text(configuredDefaults.version) || "1.0",
      description: text(data.description || data.serviceDesc),
      labels: text(data.labels),
      orgId,
      orgName:
        text(data.orgName) || (text(orgId) === text(user.orgId) ? text(user.orgName) : text(orgId)),
      protocol: provider.protocol || data.protocol || configuredDefaults.protocol || "HTTP",
      address: text(provider.address || data.address || data.uri),
      httpMethod: text(
        provider.httpMethod || data.method || configuredDefaults.httpMethod || "GET"
      ).toUpperCase(),
      publishAddress: text(publish.publishAddress || data.publishAddress || data.uri),
      dataDomain: ext.dataDomain || data.dataDomain || "",
      dataCategory: ext.dataCategory || data.dataCategory || "",
      dataLevel: ext.dataLevel || data.dataLevel || "",
      returnType: ext.returnType || data.returnType || "",
      dataUpdateFreq: ext.dataUpdateFreq || data.dataUpdateFreq || "",
      shareType: ext.shareType || data.shareType || "",
      apiDescription: text(ext.apiDescription),
    };
    if (!form.id && !data.providerConfig) {
      for (const [field, value] of Object.entries(configuredDefaults)) {
        if (field in form && !["id", "orgId", "orgName"].includes(field) && form[field] === "")
          form[field] = cloneDeep(value);
      }
    }
    const params = normalizeParams(data.publishParams || data.paramList);
    const previousInitialization = record(ext.exampleInitialization);
    const examples = { request: text(ext.requestExample ?? data.requestExample), response: text(ext.responseExample ?? data.responseExample) };
    const initialized = { request: previousInitialization.request === true || !!examples.request.trim(),
      response: previousInitialization.response === true || !!examples.response.trim() };
    for (const scope of ["request", "response"] as const) {
      if (!initialized[scope] && params.some((row) => row.paramScope === scope && row.paramName.trim())) {
        examples[scope] = createExample(params, scope);
        initialized[scope] = true;
      }
    }
    snapshot = { form, params, ...examples, exampleInitialization: initialized };
    await restore();
  } catch (error) {
    if (version === initializationVersion)
      initializationError.value = messageOf(error, "登记信息加载失败，请关闭后重试");
  } finally {
    if (version === initializationVersion) {
      initializing.value = false;
      emit("ready", !initializationError.value);
    }
  }
};
const validate = async (): Promise<boolean> => {
  if (initializing.value || initializationError.value) return false;
  try {
    await nextTick();
    if (!definitionLoaded.value || !formRef.value) return false;
    const valid = await formRef.value.validate();
    if (valid === false) return false;
    const form = await formValues();
    if (!text(form.orgId)) {
      ElMessage.warning("当前账号未关联部门，请先完善部门信息");
      return false;
    }
    const address = text(form.address).trim();
    if (!/^https?:\/\/[^\s]+$/i.test(address) && !address.startsWith("/")) {
      ElMessage.warning("服务地址须为HTTP(S)地址或已有服务相对路径");
      return false;
    }
    return true;
  } catch {
    return false;
  }
};
const handleSave = async (): Promise<boolean> => {
  if (saveLoading.value) return false;
  saveLoading.value = true;
  try {
    if (!(await validate())) {
      ElMessage.warning("请完善服务登记中的必填项后再提交");
      return false;
    }
    const form = await formValues(),
      rows = normalizeParams(form.publishParams);
    const payload = buildPayload(form, rows),
      saved = payloadOf(await $common.post("/dws/flowserve/gateway/save", payload));
    const id = text(saved.id || saved.tid);
    if (!id) throw new Error("保存请求未返回服务标识，请刷新列表确认结果后再操作");
    snapshot = {
      form: { ...form, id },
      params: rows,
      request: text(form.requestExample),
      response: text(form.responseExample),
      exampleInitialization: { ...exampleInitialized },
    };
    if (props.context === "detail") {
      isEditing.value = false;
      await restore();
    }
    emit("saved", { ...payload, id });
    ElMessage.success(props.context === "detail" ? "服务修改已保存" : "服务登记已保存");
    return true;
  } catch (error) {
    if (record(error).handled !== true)
      ElMessage.error(messageOf(error, "服务登记未保存，请检查填写内容后重试"));
    return false;
  } finally {
    saveLoading.value = false;
  }
};
const startEdit = async () => {
  isEditing.value = true;
  await restore();
};
const handleCancel = async () => {
  isEditing.value = false;
  await restore();
};
watch(
  () => props.data,
  (data) => {
    void setFormData(data);
  },
  { immediate: true, deep: true }
);
defineExpose({
  handleSave,
  generateExample,
  validate,
  setFormData,
  reset: () => setFormData(props.data),
  getFormData: formValues,
  getParamsData: async () => normalizeParams((await formValues()).publishParams),
  getRequestExample: async () => text((await formValues()).requestExample),
  getResponseExample: async () => text((await formValues()).responseExample),
  getPayload: async () => {
    const form = await formValues();
    return buildPayload(form, normalizeParams(form.publishParams));
  },
});
onBeforeUnmount(() => { initializationVersion++; });
</script>

<style scoped>
.api-register-content {
  width: 100%;
  max-width: 1160px;
  min-width: 0;
  margin: 0 auto;
  padding-bottom: 12px;
  color: #25344b;
}
.registration-heading-actions {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
}
.api-register-content.is-readonly {
  --el-disabled-text-color: var(--el-text-color-regular);
  --el-disabled-bg-color: #fafbfc;
}
.api-register-content.is-readonly :deep(.registration-parameters-heading) {
  position: static !important;
}
.api-register-content :deep(.el-form-item__content) {
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}
.api-register-content :deep(.el-form-item__error) {
  position: static;
  align-self: flex-start;
  padding: 4px 0 0;
  line-height: 18px;
}
.api-register-content :deep(.registration-parameter-field) {
  border: 0;
  margin: 0;
}
.api-register-content :deep(.registration-parameter-field > .el-form-item > .el-form-item__content) {
  padding: 0;
}
.api-register-content :deep(.registration-parameters-section .vxe-button--item-wrapper) {
  justify-content: flex-end;
}
.api-register-content :deep(.registration-parameters-section .vxe-toolbar) {
  min-height: 40px;
  padding: 0;
}
@container (max-width: 480px) {
  .api-register-content :deep(.registration-parameters-heading) {
    position: static !important;
  }
}
@media (max-width: 760px) {
  .api-register-content :deep(.el-col-12) {
    max-width: 100%;
    flex-basis: 100%;
  }
}
</style>
