<template>
  <div v-loading="initializing" class="api-register-content">
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
      ref="formRef"
      bordered
      label-width="144px"
      :rules="formRules"
      :disabled="currentMode === 'view' || !!initializationError"
    >
      <template #type-u-title="{ rule }">
        <u-title :name="rule.props?.name" class="registration-section-title" />
      </template>
      <template #field-httpMethod="scope">
        <el-radio-group
          :model-value="scope.model.value"
          :disabled="currentMode === 'view'"
          class="request-methods"
          @change="
            (value: string | number | boolean | undefined) =>
              formRef?.setValue({ httpMethod: value })
          "
        >
          <el-radio v-for="method in methods" :key="method" :value="method" border>
            {{ method }}
          </el-radio>
        </el-radio-group>
      </template>
    </json-form>
    <section class="registration-parameters">
      <u-title name="服务参数" class="registration-section-title" />
      <p class="section-hint">
        填写调用所需的参数；没有参数的服务可以留空。参数名称和参数位置须与实际接口一致。
      </p>
      <v-table
        ref="paramsRef"
        :options="paramsTableOptions"
        :show-operation="currentMode === 'edit'"
        :editable="currentMode === 'edit'"
        class="api-params-table"
      />
    </section>
    <section class="registration-examples">
      <u-title name="调用示例" class="registration-section-title" />
      <el-tabs v-model="exampleTab">
        <el-tab-pane label="请求示例" name="request">
          <div class="example-editor">
            <code-editor v-model="requestExample" lang="json" :read-only="currentMode === 'view'" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="响应示例" name="response" lazy>
          <div class="example-editor">
            <code-editor
              v-model="responseExample"
              lang="json"
              :read-only="currentMode === 'view'"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
import { useDictStore } from "@/store";

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
interface ParameterApi {
  validate(): Promise<boolean>;
  getData(): unknown[];
  setData(rows: ServiceParameter[]): void;
}
interface Snapshot {
  form: RecordValue;
  params: ServiceParameter[];
  request: string;
  response: string;
}
const props = withDefaults(defineProps<{ context?: "modal" | "detail"; data?: RecordValue }>(), {
  context: "modal",
  data: () => ({}),
});
const emit = defineEmits<{
  (event: "saved", data: RecordValue): void;
  (event: "ready", ready: boolean): void;
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
const dictStore = useDictStore();
const formRef = ref<FormApi>();
const paramsRef = ref<ParameterApi>();
const isEditing = ref(props.context === "modal");
const currentMode = computed(() =>
  props.context === "modal" || isEditing.value ? "edit" : "view"
);
const saveLoading = ref(false),
  initializing = ref(false),
  initializationError = ref("");
const requestExample = ref(""),
  responseExample = ref(""),
  exampleTab = ref("request");
const methods = ["GET", "POST", "PUT", "DELETE", "PATCH"];
let snapshot: Snapshot = { form: {}, params: [], request: "", response: "" };
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

const titleRule = (name: string) => ({ type: "UTitle", props: { name } });
const inputRule = (
  field: string,
  title: string,
  required = false,
  props: RecordValue = {},
  span = 12
) => ({
  type: "input",
  field,
  title,
  props,
  col: { span },
  $required: required,
});
const dictRule = (field: string, title: string, options: string, required = true) => ({
  type: "DictSelect",
  field,
  title,
  props: { options, placeholder: `请选择${title}`, clearable: true },
  col: { span: 12 },
  $required: required,
});
const rawRules = [
  titleRule("基本信息"),
  inputRule("name", "服务名称", true, {
    placeholder: "请输入清晰、完整的服务名称",
    maxlength: 255,
  }),
  inputRule("serviceCode", "服务编码", false, {
    placeholder: "留空时由系统生成",
    maxlength: 64,
  }),
  inputRule("orgName", "所属部门", false, {
    disabled: true,
    placeholder: "按当前登录部门登记",
  }),
  inputRule("version", "服务版本", true, {
    placeholder: "例如 1.0",
    maxlength: 64,
  }),
  inputRule(
    "description",
    "服务说明",
    true,
    {
      type: "textarea",
      rows: 2,
      maxlength: 500,
      showWordLimit: true,
      placeholder: "说明服务用途、适用场景和数据内容",
    },
    24
  ),
  titleRule("访问配置"),
  dictRule("protocol", "访问协议", "accessProtocol"),
  dictRule("returnType", "返回格式", "returnType"),
  {
    type: "fieldComponent",
    field: "httpMethod",
    title: "请求方式",
    value: "GET",
    props: { type: "radio" },
    col: { span: 24 },
    $required: true,
  },
  inputRule("address", "服务地址", true, {
    placeholder: "例如 https://api.example.com/services/query",
    maxlength: 1000,
  }),
  inputRule("publishAddress", "发布路径", true, {
    placeholder: "例如 /services/query",
    maxlength: 1000,
  }),
  dictRule("shareType", "共享类型", "shareType"),
  dictRule("dataUpdateFreq", "更新频率", "dataUpdateFreq"),
  titleRule("资源分类"),
  dictRule("dataDomain", "数据所属领域", "dataDomain"),
  {
    type: "cascader",
    field: "dataCategory",
    title: "数据资源分类",
    $dict: "dataCategory",
    props: {
      props: { checkStrictly: true, emitPath: false },
      placeholder: "请选择数据资源分类",
      clearable: true,
    },
    col: { span: 12 },
    $required: true,
  },
  dictRule("dataLevel", "数据分级", "dataLevel"),
  inputRule(
    "apiDescription",
    "补充说明",
    false,
    {
      type: "textarea",
      rows: 2,
      maxlength: 255,
      placeholder: "可补充接口约定或使用注意事项",
    },
    24
  ),
];
const formRules = computed(() =>
  currentMode.value === "view" ? FormUtils.transFormText(rawRules) : rawRules
);
const paramsTableOptions = reactive({
  importConfig: { mode: "covering" },
  toolbarConfig: {
    buttons: [
      {
        code: "import",
        icon: "vxe-icon-cloud-upload",
        mode: "text",
        status: "primary",
        name: "导入参数",
      },
      {
        code: "open_export",
        icon: "vxe-icon-download",
        mode: "text",
        status: "primary",
        name: "导出参数",
      },
      {
        code: "append_edit",
        icon: "vxe-icon-add",
        mode: "text",
        status: "primary",
        name: "添加参数",
      },
    ],
    tools: [],
  },
  columns: [
    { type: "seq", width: 58, title: "序号" },
    {
      field: "paramName",
      title: "参数名称",
      minWidth: 150,
      editRender: {
        name: "VxeInput",
        props: { placeholder: "例如 pageSize", maxlength: 255 },
      },
    },
    {
      field: "description",
      title: "参数说明",
      minWidth: 180,
      editRender: {
        name: "VxeInput",
        props: { placeholder: "说明参数含义", maxlength: 255 },
      },
    },
    {
      field: "paramType",
      title: "参数类型",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramType"),
        props: { placeholder: "请选择类型" },
      },
    },
    {
      field: "paramPosition",
      title: "参数位置",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramPosition"),
        props: { placeholder: "请选择位置" },
      },
    },
    {
      field: "required",
      title: "是否必填",
      width: 110,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("requiredAttribute"),
        props: { placeholder: "请选择" },
      },
    },
    {
      field: "defaultValue",
      title: "默认值",
      minWidth: 120,
      editRender: { name: "VxeInput", props: { maxlength: 255 } },
    },
    {
      field: "showcaseValue",
      title: "示例值",
      minWidth: 120,
      editRender: { name: "VxeInput", props: { maxlength: 255 } },
    },
  ],
  editRules: {
    paramName: [{ required: true, message: "请填写参数名称", trigger: "blur" }],
    paramType: [{ required: true, message: "请选择参数类型", trigger: "change" }],
  },
});
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
    return {
      paramName: text(row.paramName || row.param_name),
      description: text(row.description || row.paramDesc),
      paramType: text(row.paramType || row.param_type),
      paramPosition: text(row.paramPosition || row.param_position),
      required: row.required === true || text(row.required) === "1" ? "1" : "0",
      defaultValue: text(row.defaultValue),
      showcaseValue: text(row.showcaseValue),
      paramScope: row.paramScope === "response" || row.res === 1 ? "response" : "request",
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
  providerConfig: {
    ...record(props.data.providerConfig),
    protocol: form.protocol,
    address: text(form.address).trim(),
    httpMethod: form.httpMethod,
  },
  publishConfig: {
    ...record(props.data.publishConfig),
    publishProtocol: form.protocol,
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
    requestExample: requestExample.value,
    responseExample: responseExample.value,
  },
});
const restore = async () => {
  await nextTick();
  formRef.value?.setValue({ ...snapshot.form });
  paramsRef.value?.setData(snapshot.params.map((row) => ({ ...row })));
  requestExample.value = snapshot.request;
  responseExample.value = snapshot.response;
};
const setFormData = async (data: RecordValue = {}) => {
  const version = ++initializationVersion;
  initializing.value = true;
  emit("ready", false);
  initializationError.value = "";
  try {
    const user = await currentUser();
    if (version !== initializationVersion) return;
    const ext = record(data.extendInfo),
      provider = record(data.providerConfig),
      publish = record(data.publishConfig);
    const orgId = ext.orgId || data.orgId || user.orgId;
    const form: RecordValue = {
      id: text(data.id || data.tid),
      name: text(data.serviceName || data.name),
      serviceCode: text(data.serviceCode || data.code),
      version: text(data.version) || "1.0",
      description: text(data.description || data.serviceDesc),
      labels: text(data.labels),
      orgId,
      orgName:
        text(data.orgName) || (text(orgId) === text(user.orgId) ? text(user.orgName) : text(orgId)),
      protocol: provider.protocol || data.protocol || "HTTP",
      address: text(provider.address || data.address || data.uri),
      httpMethod: text(provider.httpMethod || data.method || "GET").toUpperCase(),
      publishAddress: text(publish.publishAddress || data.publishAddress || data.uri),
      dataDomain: ext.dataDomain || data.dataDomain || "",
      dataCategory: ext.dataCategory || data.dataCategory || "",
      dataLevel: ext.dataLevel || data.dataLevel || "",
      returnType: ext.returnType || data.returnType || "",
      dataUpdateFreq: ext.dataUpdateFreq || data.dataUpdateFreq || "",
      shareType: ext.shareType || data.shareType || "",
      apiDescription: text(ext.apiDescription),
    };
    snapshot = {
      form,
      params: normalizeParams(data.publishParams || data.paramList),
      request: text(ext.requestExample || data.requestExample),
      response: text(ext.responseExample || data.responseExample),
    };
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
    const valid = await formRef.value?.validate();
    if (valid === false) return false;
    if ((await paramsRef.value?.validate()) === false) return false;
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
      rows = normalizeParams(paramsRef.value?.getData() || []);
    const payload = buildPayload(form, rows),
      saved = payloadOf(await $common.post("/dws/flowserve/gateway/save", payload));
    const id = text(saved.id || saved.tid);
    if (!id) throw new Error("保存请求未返回服务标识，请刷新列表确认结果后再操作");
    snapshot = {
      form: { ...form, id },
      params: rows,
      request: requestExample.value,
      response: responseExample.value,
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
  validate,
  setFormData,
  reset: () => setFormData(props.data),
  getFormData: formValues,
  getParamsData: () => normalizeParams(paramsRef.value?.getData() || []),
  getRequestExample: () => requestExample.value,
  getResponseExample: () => responseExample.value,
  getPayload: async () =>
    buildPayload(await formValues(), normalizeParams(paramsRef.value?.getData() || [])),
});
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
.section-hint {
  margin: 6px 0 0;
  color: #758298;
  font-size: 12px;
  line-height: 20px;
}
.registration-heading-actions {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  padding: 8px 0;
}
.request-methods {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 2px 0;
}
.request-methods :deep(.el-radio) {
  min-width: 88px;
  height: 34px;
  margin: 0;
  padding: 0 14px;
  border-radius: 4px;
}
.registration-section-title {
  min-height: 38px;
  margin: 8px 0 12px;
}
.api-register-content :deep(.el-form-item) {
  margin-bottom: 14px;
}
.api-register-content :deep(.el-form-item__label) {
  font-size: 13px;
}
.api-register-content :deep(.el-form-item__content) {
  min-width: 0;
}
.registration-parameters,
.registration-examples {
  min-width: 0;
  margin-top: 16px;
}
.section-hint {
  margin: -6px 0 8px;
}
.api-params-table {
  min-height: 170px;
  min-width: 0;
  max-width: 100%;
}
.example-editor {
  height: 180px;
  border: 1px solid #e5eaf1;
  border-radius: 4px;
  overflow: hidden;
}
@media (max-width: 760px) {
  .api-register-content :deep(.el-col-12) {
    max-width: 100%;
    flex-basis: 100%;
  }
}
</style>
