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
    <div v-if="definitionLoaded && !hasDebugHeading" class="api-debug-fallback">
      <el-button type="primary" plain @click="openDebug">调试 API</el-button>
    </div>
    </div>
    <el-drawer v-model="debugVisible" title="调试 API" append-to-body :size="'min(640px, 100vw)'" @closed="resetDebug">
      <el-form label-position="top" class="api-debug-form" @submit.prevent="sendDebug">
        <el-form-item label="上游请求">
          <el-input v-model="debugRequest.address" :disabled="debugLoading" aria-label="上游地址">
            <template #prepend>{{ debugRequest.method }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="Query 参数（JSON 对象）">
          <el-input v-model="debugRequest.query" type="textarea" :rows="3" :disabled="debugLoading" />
        </el-form-item>
        <el-form-item label="请求头（JSON 对象）">
          <el-input v-model="debugRequest.headers" type="textarea" :rows="3" :disabled="debugLoading" />
        </el-form-item>
        <el-form-item label="临时认证">
          <div class="api-debug-auth">
            <el-input v-model="debugRequest.authHeader" placeholder="请求头名，如 token" :disabled="debugLoading" />
            <el-input v-model="debugRequest.authValue" type="password" show-password placeholder="仅用于本次调试" :disabled="debugLoading" />
          </div>
        </el-form-item>
        <el-form-item v-if="!['GET', 'HEAD'].includes(debugRequest.method)" label="请求体">
          <el-input v-model="debugRequest.payload" type="textarea" :rows="5" :disabled="debugLoading" />
        </el-form-item>
        <el-alert v-if="debugError" :title="debugError" type="error" :closable="false" show-icon />
        <section v-if="debugResult" class="api-debug-result" aria-live="polite">
          <div class="api-debug-status">
            <el-tag :type="debugResult.ok ? 'success' : 'danger'">{{ debugStatus }}</el-tag>
            <span v-if="debugResult.status">HTTP {{ debugResult.status }}</span>
            <span v-if="debugResult.businessCode != null">业务码 {{ debugResult.businessCode }}</span>
            <span>{{ debugResult.durationMs ?? 0 }} ms</span>
          </div>
          <details v-if="debugResult.headers">
            <summary>响应头</summary>
            <pre>{{ JSON.stringify(debugResult.headers, null, 2) }}</pre>
          </details>
          <pre v-if="debugResult.body != null" aria-label="响应正文">{{ debugResponseText }}</pre>
          <el-button v-if="debugResponseText && currentMode === 'edit'" link type="primary" @click="useDebugExample">设为响应示例</el-button>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="debugVisible = false">关闭</el-button>
        <el-button type="primary" :loading="debugLoading" @click="sendDebug">发送请求</el-button>
      </template>
    </el-drawer>
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
const hasDebugHeading = ref(false);
const debugVisible = ref(false), debugLoading = ref(false), debugError = ref("");
const debugResult = ref<RecordValue>();
const debugRequest = ref({ address: "", method: "GET", query: "{}", headers: "{}", payload: "", authHeader: "", authValue: "" });
let debugAttempt = 0;
const debugResponseText = computed(() => {
  const body = debugResult.value?.body;
  return body == null ? "" : typeof body === "string" ? body : JSON.stringify(body, null, 2);
});
const debugStatus = computed(() => {
  const result = debugResult.value;
  if (!result) return "";
  if (result.kind === "timeout") return "请求超时";
  if (result.kind === "network") return "连接失败";
  if (result.ok) return "调用成功";
  const status = Number(result.status);
  if ([401, 403].includes(status) || [401, 403, 100120].includes(Number(result.businessCode))) return "上游认证或授权失败";
  if (status >= 500) return "上游服务异常";
  if (status >= 400) return "上游拒绝请求";
  if (status >= 300) return "上游返回重定向";
  return "上游业务返回失败";
});
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
      hasDebugHeading.value = bindDebugAction(rules);
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
const resetDebug = () => {
  debugAttempt++;
  debugLoading.value = false;
  debugError.value = "";
  debugResult.value = undefined;
  debugRequest.value = { address: "", method: "GET", query: "{}", headers: "{}", payload: "", authHeader: "", authValue: "" };
};
const bindDebugAction = (rules: unknown[]): boolean => {
  for (const value of rules) {
    const rule = record(value);
    if (rule.type === "UTitle" && record(rule.props).name === "接口信息") {
      rule.children = [...(Array.isArray(rule.children) ? rule.children : []), {
        type: "el-button", slot: "right", native: true, children: ["调试 API"],
        props: { type: "primary", plain: true, size: "small", disabled: false },
        on: { click: () => { void openDebug(); } },
      }];
      return true;
    }
    if (Array.isArray(rule.children) && bindDebugAction(rule.children)) return true;
  }
  return false;
};
const openDebug = async () => {
  if (!definitionLoaded.value || initializing.value || debugVisible.value) return;
  const version = initializationVersion;
  const form = await formValues();
  if (version !== initializationVersion) return;
  resetDebug();
  const request = debugRequest.value;
  request.address = text(form.address).trim();
  request.method = text(form.httpMethod || "GET").toUpperCase();
  const query: RecordValue = {}, headers: RecordValue = {}, body: RecordValue = {};
  for (const row of normalizeParams(form.publishParams)) {
    const name = row.paramName.trim();
    if (row.paramScope !== "request" || !name || /authorization|token|password|passwd|secret|api.?key|credential|cookie/i.test(name)) continue;
    if (!row.showcaseValue.trim() && !row.defaultValue.trim()) continue;
    const value = exampleValue(row);
    const position = row.paramPosition.toLowerCase();
    if (position === "path") request.address = request.address.replaceAll("{" + name + "}", encodeURIComponent(text(value)));
    else if (position === "header") headers[name] = text(value);
    else if (position === "body") body[name] = value;
    else query[name] = value;
  }
  request.query = JSON.stringify(query, null, 2);
  request.headers = JSON.stringify(headers, null, 2);
  request.payload = Object.keys(body).length ? JSON.stringify(body, null, 2) : "";
  debugVisible.value = true;
};
const debugObject = (value: string, label: string): RecordValue => {
  try {
    const parsed = JSON.parse(value.trim() || "{}");
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) return parsed;
  } catch { /* 统一显示当前编辑区的格式错误。 */ }
  throw new Error(label + "必须是 JSON 对象");
};
const sendDebug = async () => {
  if (debugLoading.value) return;
  const attempt = ++debugAttempt;
  const input = { ...debugRequest.value };
  debugLoading.value = true;
  debugError.value = "";
  debugResult.value = undefined;
  try {
    if (!input.address.trim() || /\{[^{}]+\}/.test(input.address)) throw new Error("请填写服务地址并补齐路径参数");
    const query = debugObject(input.query, "Query 参数");
    const headers = debugObject(input.headers, "请求头");
    if (input.authHeader.trim() || input.authValue) {
      if (!input.authHeader.trim() || !input.authValue) throw new Error("请同时填写认证请求头和值");
    }
    const payload = ["GET", "HEAD"].includes(input.method) || !input.payload.trim() ? undefined : input.payload;
    if (payload && !Object.keys(headers).some((name) => name.toLowerCase() === "content-type")) {
      try { JSON.parse(payload); } catch { throw new Error("请填写合法 JSON 请求体，或指定对应的 Content-Type"); }
    }
    if (!["GET", "HEAD", "OPTIONS"].includes(input.method)) {
      try {
        await ElMessageBox.confirm("此请求可能修改上游数据，确定发送？", "确认发送 " + input.method + " 请求", { type: "warning" });
      } catch { return; }
    }
    if (attempt !== debugAttempt || !debugVisible.value) return;
    const result = await $common.post("/dws/flowserve/gateway/api/debug", {
      apiId: text(snapshot.form.id), datasourceId: props.data.datasourceId,
      address: input.address.trim(), method: input.method, query, headers, payload,
      credentialHeader: input.authHeader.trim(), credential: input.authValue,
    });
    if (attempt === debugAttempt && debugVisible.value) debugResult.value = payloadOf(result);
  } catch (error) {
    if (attempt === debugAttempt && debugVisible.value) debugError.value = messageOf(error, "API 调试失败，请检查配置后重试");
  } finally {
    if (attempt === debugAttempt) {
      debugLoading.value = false;
      debugRequest.value.authValue = "";
    }
  }
};
const useDebugExample = async () => {
  if (!canEditExamples() || !debugResponseText.value) return;
  const attempt = debugAttempt;
  const value = debugResponseText.value;
  const form = await formValues();
  if (text(form.responseExample).trim()) {
    try { await ElMessageBox.confirm("使用本次脱敏响应替换当前响应示例？", "替换响应示例"); }
    catch { return; }
  }
  if (attempt !== debugAttempt || !debugVisible.value) return;
  formRef.value?.setValue({ responseExample: value });
  exampleInitialized.response = true;
  emit("change");
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
    // 平台发布地址由服务发布记录写入，编辑登记信息时保留已有值。
    publishAddress: text(form.publishAddress).trim(),
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
    debugVisible.value = false;
    resetDebug();
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
onBeforeUnmount(() => { initializationVersion++; debugAttempt++; });
</script>

<style scoped>
.api-debug-fallback { display: flex; justify-content: flex-end; margin-top: 8px; }
.api-debug-auth { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.api-debug-auth > * { flex: 1 1 180px; }
.api-debug-result { margin-top: 16px; }
.api-debug-status { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 12px; }
.api-debug-result pre {
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
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
