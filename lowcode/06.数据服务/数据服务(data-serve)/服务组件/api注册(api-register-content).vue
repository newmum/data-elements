<template>
  <div class="api-register-content">
    <!-- ?? -->
    <json-form ref="formRef" bordered :rules="formRules" :disabled="currentMode === 'view'">
      <!-- ???????:detail ???????/??/???? -->
      <template #type-u-title="{ rule }">
        <u-title :name="rule.props?.name" class="gap-4" style="height: 50px">
          <div v-if="context === 'detail' && rule.props.name === '????'">
            <template v-if="!isEditing">
              <el-button type="primary" plain icon="edit" @click="startEdit">????</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link @click="handleCancel">??</el-button>
              <el-button type="primary" link :loading="saveLoading" @click="handleSave">
                <Icon icon="save" class="mr-1" />
                ??
              </el-button>
            </template>
          </div>
        </u-title>
      </template>
      <template #field-httpMethod="scope">
        <el-radio-group
          v-model="scope.model.value"
          :disabled="currentMode === 'view'"
          @change="(val: string | number | boolean | undefined) => handleChange(String(val))"
        >
          <el-radio
            v-for="item in scope.rule.props.options"
            :key="item.value"
            :label="item.value"
            :style="{
              borderColor: `${item.color}40`,
              backgroundColor: `${item.color}20`,
              width: '90px',
              height: '26px',
              marginRight: '10px',
              fontSize: '12px',
              borderRadius: '4px',
            }"
            border
          >
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </template>
    </json-form>

    <!-- ???? -->
    <u-title name="????" />
    <v-table
      ref="paramsRef"
      :options="paramsTableOptions"
      :show-operation="currentMode === 'edit'"
      :editable="currentMode === 'edit'"
      class="api-params-table"
    ></v-table>

    <u-title name="????" />
    <div style="height: 300px">
      <code-editor v-model="requestExample" :lang="'sql'" :read-only="currentMode === 'view'" />
    </div>

    <!-- ???? -->
    <u-title name="????" />
    <div style="height: 300px">
      <code-editor v-model="responseExample" :lang="'sql'" :read-only="currentMode === 'view'" />
    </div>

    <!-- ??? -->
    <!-- <u-title name="???" />
    <ServiceStatusTable ref="statusRef" :readonly="currentMode === 'view'" /> -->
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
import { useDictStore } from "@/store";
// import ServiceStatusTable from "./ServiceStatusTable.vue";

const props = withDefaults(
  defineProps<{
    /** ????:modal=????, detail=?????/?? */
    context: "modal" | "detail";
    /** ??????????(? id ????,? id ????) */
    data?: Record<string, any>;
  }>(),
  {
    context: "modal",
    data: () => ({}),
  }
);

const emit = defineEmits<{
  (e: "saved", data: Record<string, any>): void;
}>();

const dictStore = useDictStore();

// ==================== ????(? detail ??) ====================
const isEditing = ref(props.context === "modal");
const saveLoading = ref(false);
/** ??????:modal ?????,detail ?? isEditing ?? */
const currentMode = computed(() => {
  if (props.context === "modal") return "edit";
  return isEditing.value ? "edit" : "view";
});

// ==================== Refs ====================
const formRef = ref();
const paramsRef = ref();
const responseExample = ref("");
const requestExample = ref("");

// ==================== ????(????:view=transFormText, edit=rawRules) ====================
const rawRules: any[] = [
  { type: "UTitle", props: { name: "????" } },
  {
    type: "cascader",
    field: "orgId",
    title: "????",
    $dict: "org",
    props: {
      props: {
        checkStrictly: false,
        emitPath: false,
      },
      placeholder: "???????",
      clearable: true,
    },
    class: "w-full",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "??????",
    field: "dataDomain",
    props: { options: "dataDomain", placeholder: "?????????" },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "cascader",
    title: "??????",
    field: "dataCategory",
    $dict: "dataCategory",
    class: "w-full",
    col: { span: 12 },
    props: {
      props: {
        checkStrictly: false,
        emitPath: false,
      },
      placeholder: "?????????",
    },
    $required: true,
  },
  {
    type: "dict-select",
    title: "????",
    field: "dataLevel",
    props: { options: "dataLevel", placeholder: "???????" },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "input",
    field: "description",
    title: "????",
    props: {
      type: "textarea",
      placeholder: "????????????????????????",
      maxlength: 500,
      rows: 4,
      showWordLimit: true,
    },
    col: { span: 24 },
    $required: true,
  },
  { type: "UTitle", props: { name: "????" } },
  { type: "input", title: "????", field: "name", col: { span: 12 }, $required: true },
  {
    type: "DictSelect",
    title: "????",
    field: "returnType",
    col: { span: 12 },
    props: {
      placeholder: "???????",
      options: "returnType",
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "????",
    field: "protocol",
    col: { span: 12 },
    props: {
      placeholder: "???????",
      options: "accessProtocol",
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "????",
    field: "dataUpdateFreq",
    props: { options: "dataUpdateFreq", placeholder: "???????" },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "input",
    title: "????",
    field: "address",
    col: { span: 12 },
    props: { placeholder: "??: http://172.16.45.4:8000" },
    $required: true,
  },
  {
    type: "input",
    title: "????",
    field: "publishAddress",
    props: {
      placeholder: "??????????????,??:getUserInfo/{userId}",
    },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "fieldComponent",
    title: "????",
    field: "httpMethod",
    value: "get",
    props: {
      type: "radio",
      options: [
        { label: "GET", value: "get", color: "#fa8c16" },
        { label: "POST", value: "post", color: "#1677ff" },
        { label: "PUT", value: "put", color: "#389e0d" },
        { label: "DELETE", value: "delete", color: "#9254de" },
      ],
    },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "dict-select",
    title: "????",
    field: "shareType",
    col: { span: 12 },
    props: { options: "shareType", placeholder: "???????" },
    $required: true,
  },
  {
    type: "input",
    field: "apiDescription",
    title: "????",
    props: { type: "textarea", maxlength: 255, rows: 4 },
    col: { span: 24 },
  },
];

/** ????????? field ??,?? detail ???? */
const basicElementFields = new Set([
  "orgId",
  "dataDomain",
  "dataCategory",
  "dataLevel",
  "description",
]);

/** ?????????(detail ?????) */
const filterBasicElement = (rules: any[]) => {
  return rules.filter((rule) => {
    // ????????
    if (rule.type === "UTitle" && rule.props?.name === "????") return false;
    // ??????????
    if (rule.field && basicElementFields.has(rule.field)) return false;
    return true;
  });
};

const formRules = computed(() => {
  const rules = props.context === "detail" ? filterBasicElement(rawRules) : rawRules;
  if (currentMode.value === "view") return FormUtils.transFormText(rules);
  return rules;
});

// ==================== ???? v-table ?? ====================
const paramsTableOptions = reactive({
  importConfig: { mode: "covering" },
  toolbarConfig: {
    buttons: [
      {
        code: "import",
        icon: "vxe-icon-cloud-upload",
        mode: "text",
        status: "primary",
        name: "??",
      },
      {
        code: "open_export",
        icon: "vxe-icon-download",
        mode: "text",
        status: "primary",
        name: "??",
      },
      {
        code: "append_edit",
        icon: "vxe-icon-add",
        name: "????",
        status: "primary",
        mode: "text",
      },
    ],
    tools: [],
  },
  columns: [
    { type: "seq", width: 60, title: "??" },
    {
      field: "paramName",
      title: "????",
      editRender: { name: "VxeInput", props: { placeholder: "???????", maxlength: 256 } },
    },
    {
      field: "description",
      title: "?????",
      editRender: { name: "VxeInput", props: { placeholder: "????????", maxlength: 256 } },
    },
    {
      field: "showcaseValue",
      title: "???",
      editRender: { name: "VxeInput", props: { placeholder: "??????", maxlength: 256 } },
    },
    {
      field: "paramType",
      title: "????",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramType"),
        props: { placeholder: "???????" },
      },
    },
    {
      field: "paramPosition",
      title: "????",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramPosition"),
        props: { placeholder: "???????" },
      },
    },
    {
      field: "required",
      title: "????",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("requiredAttribute"),
        props: { placeholder: "???????" },
      },
    },
    {
      field: "defaultValue",
      title: "???",
      width: 120,
      editRender: { name: "VxeInput", props: { placeholder: "??????", maxlength: 256 } },
    },
  ],
  editRules: {
    paramName: [{ required: true, message: "????????", trigger: "blur" }],
    paramDesc: [{ required: true, message: "?????????", trigger: "blur" }],
    paramType: [{ required: true, message: "????????", trigger: "change" }],
    paramPosition: [
      {
        validator: ({ cellValue, row }: any) => row.req !== 1 || !!cellValue,
        message: "???????????",
        trigger: "change",
      },
    ],
    required: [
      {
        validator: ({ cellValue, row }: any) => row.req !== 1 || !!cellValue,
        message: "???????????",
        trigger: "change",
      },
    ],
  },
});

const handleChange = (val: string) => {
  formRef.value?.setValue({ httpMethod: val });
};

// ==================== ??(???? value,?? label ??) ====================
let _dataSnapshot: Record<string, any> = {};

// ==================== ??????(? detail ??) ====================
const startEdit = () => {
  // ??????????(params?responseExample)
  _dataSnapshot.paramsData = paramsRef.value?.getData() || [];
  _dataSnapshot.requestExample = requestExample.value;
  _dataSnapshot.responseExample = responseExample.value;
  // formData ?? setFormData ?????????,?? view ?? getFormData ??
  isEditing.value = true;
  // formRules ?? ? ???? ? nextTick ???? value ??
  nextTick(() => {
    if (_dataSnapshot.formData && Object.keys(_dataSnapshot.formData).length > 0) {
      formRef.value?.setValue(_dataSnapshot.formData);
    }
  });
};

const handleCancel = () => {
  isEditing.value = false;
  nextTick(() => {
    // ????:view ???? label ??????
    formRef.value?.setValue(_dataSnapshot.formData || {});
    paramsRef.value?.setData(_dataSnapshot.paramsData || []);
    requestExample.value = _dataSnapshot.requestExample || "";
    responseExample.value = _dataSnapshot.responseExample || "";
  });
};

// ==================== ???????? ====================
/**
 * ??????? + ??????????????????
 */
const buildPayload = (formData: Record<string, any>, paramList: any[]) => {
  // ????? ? publishParams,?? ordering ??
  const publishParams = paramList.map((row: any, index: number) => ({
    ordering: String(index + 1),
    paramPosition: row.paramPosition || "",
    paramType: row.paramType || "",
    paramName: row.paramName || "",
    required: row.required || "",
    description: row.description || "",
    defaultValue: row.defaultValue || "",
    showcaseValue: row.showcaseValue || "",
  }));

  const providerConfig = {
    protocol: formData.protocol || "",
    address: formData.address || "",
    httpMethod: formData.httpMethod || "",
  };

  const publishConfig = {
    publishProtocol: formData.protocol || "",
    publishAddress: formData.publishAddress || "",
    publishMethod: formData.httpMethod || "",
  };

  const extendInfo = {
    orgId: formData.orgId || "",
    dataDomain: formData.dataDomain || "",
    dataCategory: formData.dataCategory || "",
    dataLevel: formData.dataLevel || "",
    returnType: formData.returnType || "",
    dataUpdateFreq: formData.dataUpdateFreq || "",
    shareType: formData.shareType || "",
    apiDescription: formData.apiDescription || "",
    requestExample: requestExample.value || "",
    responseExample: responseExample.value || "",
  };

  return {
    name: formData.name || "",
    version: formData.version || "1.0",
    description: formData.description || "",
    labels: formData.labels || "",
    providerConfig,
    providerParams: [],
    constantParams: [],
    publishConfig,
    publishParams,
    paramsMapping: [],
    requestBodyShowcase: "",
    responseShowcase: "",
    failedResponseShowcase: "",
    errorCodeShowcase: "",
    extendInfo,
  };
};

/**
 * ??????????????????? + ????
 */
const parsePayload = (
  data: Record<string, any>
): { formData: Record<string, any>; paramsData: any[] } => {
  const ext =
    typeof data.extendInfo === "string" ? JSON.parse(data.extendInfo) : data.extendInfo || {};
  const provider =
    typeof data.providerConfig === "string"
      ? JSON.parse(data.providerConfig)
      : data.providerConfig || {};
  const publish =
    typeof data.publishConfig === "string"
      ? JSON.parse(data.publishConfig)
      : data.publishConfig || {};

  const formData: Record<string, any> = {
    id: data.id || "",
    name: data.name || "",
    version: data.version || "1.0",
    description: data.description || "",
    labels: data.labels || "",
    // providerConfig
    protocol: provider.protocol || "",
    address: provider.address || "",
    httpMethod: provider.httpMethod || "",
    // publishConfig
    publishAddress: publish.publishAddress || "",
    // extendInfo
    orgId: ext.orgId || "",
    dataDomain: ext.dataDomain || "",
    dataCategory: ext.dataCategory || "",
    dataLevel: ext.dataLevel || "",
    returnType: ext.returnType || "",
    dataUpdateFreq: ext.dataUpdateFreq || "",
    shareType: ext.shareType || "",
    apiDescription: ext.apiDescription || "",
  };

  // ??/??????? ref
  const reqExample = ext.requestExample || "";
  const resExample = ext.responseExample || "";

  // ????:publishParams ? ???
  const paramsData =
    typeof data.publishParams === "string"
      ? JSON.parse(data.publishParams)
      : data.publishParams || [];

  return { formData, paramsData, _requestExample: reqExample, _responseExample: resExample } as any;
};

// ==================== ??(modal ? detail ??) ====================
const handleSave = async (): Promise<boolean> => {
  try {
    saveLoading.value = true;
    const valid = await validate();
    if (!valid) {
      ElMessage.warning("???????????");
      return false;
    }
    const formData = (await formRef.value?.getFormData()) || {};
    const paramList = paramsRef.value?.getData() || [];

    const payload = buildPayload(formData, paramList);
    // ????? id
    const id = _dataSnapshot?.formData?.id || "";
    await $common.post("/dws/flowserve/gateway/save", { ...payload, id });

    if (props.context === "detail") {
      isEditing.value = false;
      // ?????????
      _dataSnapshot = {
        formData: { ...formData, id },
        paramsData: [...paramList],
        requestExample: requestExample.value,
        responseExample: responseExample.value,
      };
      nextTick(() => {
        formRef.value?.setValue(formData);
      });
    }

    emit("saved", { ...payload, id });
    ElMessage.success(props.context === "detail" ? "????" : "??????");
    return true;
  } catch (error) {
    console.error("????:", error);
    // ???????????????;???????,???????
    if (!error?.handled) {
      ElMessage.error("????,???");
    }
    return false;
  } finally {
    saveLoading.value = false;
  }
};

// ==================== ???? ====================
/** ???? Java ???? */
const generateDefaultReqExample = (path?: string) => {
  return `public static void main(String[] args) {
  String path = " ${path || ""} ";
  String query = "";
  CloseableHttpClient client = HttpClients.createDefault();
  HttpGet httpGet = new HttpGet(path + "?" + query);
  try {
      HttpResponse response = client.execute(httpGet);
      System.out.println(response.toString());
  } catch (IOException e) {
      e.printStackTrace();
  }
}`;
};

const defaultResExample = `{
  "code": 0,
  "msg": "success",
  "success": true,
  "data": {}
}`;

const defaultParamsData = [
  {
    paramName: "ID",
    description: "ID",
    paramType: "string",
    showcaseValue: "",
    paramPosition: "path",
    required: true,
  },
];

/** ??????/??????????? */
const setDefaultSelectValues = (formData: Record<string, any>) => {
  // DictSelect/dict-select ????
  const requiredSelectFields: Record<string, string> = {
    dataDomain: "dataDomain",
    dataLevel: "dataLevel",
    returnType: "returnType",
    protocol: "accessProtocol",
    dataUpdateFreq: "dataUpdateFreq",
    shareType: "shareType",
  };

  Object.entries(requiredSelectFields).forEach(([fieldName, dictCode]) => {
    if (!formData[fieldName]) {
      const dictItems = dictStore.getDictItems(dictCode);
      if (dictItems.length > 0) {
        formData[fieldName] = dictItems[0].value;
      }
    }
  });
};

const setFormData = (data: Record<string, any>) => {
  console.log("setFormData", data);
  if (!data || !Object.keys(data).length) return;
  nextTick(() => {
    const parsed = parsePayload(data) as any;
    const formData = parsed.formData || {};
    let paramsData = parsed.paramsData || [];
    let reqExample = parsed.requestExample || "";
    let resExample = parsed.responseExample || "";

    // ???????,?? publishAddressFull ??????
    if (!reqExample) {
      reqExample = generateDefaultReqExample(data?.publishAddressFull);
    }

    // ???????,??????
    if (!resExample) {
      resExample = defaultResExample;
    }

    // ???????,????????
    if (!paramsData || paramsData.length === 0) {
      paramsData = defaultParamsData;
    }

    // ??????/???????????
    setDefaultSelectValues(formData);

    // ????????(?????)
    _dataSnapshot = {
      formData: { ...formData },
      paramsData: [...paramsData],
      requestExample: reqExample,
      responseExample: resExample,
    };

    // ????/??/??
    formRef.value?.setValue(formData);
    requestExample.value = reqExample;
    responseExample.value = resExample;
    paramsRef.value?.setData(paramsData);
  });
};

// ?? data prop ??
watch(
  () => props.data,
  () => {
    nextTick(() => setFormData(props.data));
  },
  { immediate: true, deep: true }
);

// onMounted(async () => {
//   // catalog ??????,??????????(?? view ?? label ??)
//   if (props.data && Object.keys(props.data).length > 0 && currentMode.value === "view") {
//     nextTick(() => setFormData(props.data));
//   }
// });

// ==================== Validate ====================
const validate = async (): Promise<boolean> => {
  try {
    const paramsValid = await paramsRef.value?.validate();
    const formValid = await formRef.value?.validate();
    return paramsValid !== false && formValid !== false;
  } catch {
    return false;
  }
};

// ==================== Expose ====================
defineExpose({
  getFormData: async () => (await formRef.value?.getFormData()) || {},
  getParamsData: () => paramsRef.value?.getData() || [],
  getRequestExample: () => requestExample.value || "",
  getResponseExample: () => responseExample.value || "",
  /** ????????? payload */
  getPayload: async () => {
    const formData = (await formRef.value?.getFormData()) || {};
    const paramList = paramsRef.value?.getData() || [];
    return buildPayload(formData, paramList);
  },
  validate,
  setFormData,
  /** ??(modal ? detail ????,??????) */
  handleSave,
  reset: () => {
    formRef.value?.resetFields();
    requestExample.value = "";
    responseExample.value = "";
    paramsRef.value?.setData([]);
  },
});
</script>

<style scoped lang="scss">
.api-register-content {
  .el-radio__label {
    font-size: 12px;
  }
  .api-params-table {
    min-height: 200px;
    position: relative;
    :deep(.vxe-grid--toolbar-wrapper) {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }
}
</style>
