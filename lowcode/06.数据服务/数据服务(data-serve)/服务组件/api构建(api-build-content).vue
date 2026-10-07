<template>
  <div class="api-build-content">
    <!-- 表单 -->
    <json-form ref="formRef" bordered :rules="formRules">
      <!-- 基本要素标题栏：detail 场景下渲染编辑/取消/保存按钮 -->
      <template #type-u-title="{ rule }">
        <u-title :name="rule.props?.name" class="gap-4" style="height: 50px">
          <div v-if="context === 'detail' && rule.props.name === '基本要素'">
            <template v-if="!isEditing">
              <el-button type="primary" plain icon="edit" @click="startEdit">编辑修改</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link @click="handleCancel">取消</el-button>
              <el-button type="primary" link :loading="saveLoading" @click="handleSave">
                <Icon icon="save" class="mr-1" />
                保存
              </el-button>
            </template>
          </div>
        </u-title>
      </template>
    </json-form>

    <!-- 参数信息 -->
    <u-title name="参数信息" />
    <v-table
      ref="paramsRef"
      :options="paramsTableOptions"
      :editable="currentMode === 'edit'"
      :show-operation="false"
      class="api-params-table"
      @data-change="handleParamsDataChange"
    >
      <template #req="{ row }">
        <el-checkbox
          :model-value="row.req === 1"
          :disabled="currentMode === 'view'"
          @update:model-value="(val: CheckboxValueType) => handleReqChange(row, val)"
        />
      </template>
      <template #res="{ row }">
        <el-checkbox
          :model-value="row.res === 1"
          :disabled="currentMode === 'view'"
          @update:model-value="(val: CheckboxValueType) => handleResChange(row, val)"
        />
      </template>
      <template #req_header="{ column }">
        <el-checkbox
          :model-value="allReq"
          :indeterminate="indeterminateReq"
          :disabled="currentMode === 'view'"
          class="header-checkbox"
          @change="handleAllReqChange"
        >
          {{ column.title }}
        </el-checkbox>
      </template>
      <template #res_header="{ column }">
        <el-checkbox
          :model-value="allRes"
          :indeterminate="indeterminateRes"
          :disabled="currentMode === 'view'"
          class="header-checkbox"
          @change="handleAllResChange"
        >
          {{ column.title }}
        </el-checkbox>
      </template>
      <template #required="{ row }">
        <el-checkbox
          v-if="currentMode === 'edit'"
          :model-value="row.required === 1"
          @update:model-value="(val) => (row.required = val ? 1 : 0)"
        />
        <el-checkbox v-else :model-value="row.required === 1" disabled />
      </template>
      <template #columnOperator="{ row }">
        <el-select
          v-if="row.req === 1 && currentMode === 'edit'"
          v-model="row.columnOperator"
          placeholder="请选择运算符"
          clearable
          size="small"
        >
          <el-option label="and" value="and" />
          <el-option label="or" value="or" />
        </el-select>
        <span v-else class="text-gray-400">
          {{ row.req === 1 ? row.columnOperator || "--" : "--" }}
        </span>
      </template>
      <template #requestOperator="{ row }">
        <el-select
          v-if="row.req === 1 && currentMode === 'edit'"
          v-model="row.requestOperator"
          placeholder="请选择操作符"
          clearable
          size="small"
        >
          <el-option label="=" value="=" />
          <el-option label=">" value=">" />
          <el-option label="<" value="<" />
          <el-option label="in" value="in" />
          <el-option label="like" value="like" />
          <el-option label="between" value="between" />
        </el-select>
        <span v-else class="text-gray-400">
          {{ row.req === 1 ? row.requestOperator || "--" : "--" }}
        </span>
      </template>
    </v-table>

    <!-- 响应示例（已注释） -->
    <!-- <u-title name="响应示例" />
    <div style="height: 300px">
      <code-editor v-model="responseExample" :lang="'json'" :read-only="currentMode === 'view'" />
    </div> -->

    <!-- 状态码（已注释） -->
    <!-- <u-title name="状态码" />
    <ServiceStatusTable
      ref="statusRef"
      :enable-drag="currentMode === 'edit'"
      :readonly="currentMode === 'view'"
    /> -->
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
import type { CheckboxValueType } from "element-plus";
import { useDictStore } from "@/store";
// import ServiceStatusTable from "./ServiceStatusTable.vue";

const props = withDefaults(
  defineProps<{
    /** 使用场景：modal=弹窗创建, detail=详情页查看/编辑 */
    context: "modal" | "detail";
    /** 详情页传入的已有数据（含 id 时为更新，无 id 时为新建） */
    data?: Record<string, any>;
  }>(),
  {
    context: "detail",
    data: () => ({}),
  }
);

const emit = defineEmits<{
  (e: "saved", data: Record<string, any>): void;
}>();

// ==================== 编辑状态（仅 detail 场景） ====================
const isEditing = ref(props.context === "modal");
const saveLoading = ref(false);
const currentMode = computed(() => {
  if (props.context === "modal") return "edit";
  return isEditing.value ? "edit" : "view";
});

// ==================== Refs ====================
const formRef = ref();
const paramsRef = ref();
// const statusRef = ref();
// const responseExample = ref("");
const tableOptions = ref<
  { label: string; value: string; tableName: string; tableNameCn: string; tid: string }[]
>([]);

// ==================== 表单配置（双规则集：fixJson 预处理 → view=transFormText, edit=rawRules） ====================
const rawRules = FormUtils.fixJson([
  { type: "UTitle", props: { name: "基本要素" } },
  { type: "input", title: "服务名称", field: "serviceName", col: { span: 12 }, $required: true },
  {
    type: "dict-select",
    title: "服务目录",
    field: "catalogId",
    props: {
      placeholder: "请选择服务目录",
      options: "catalog",
      filterable: true,
      filters: {
        assetStatus: "2",
      },
    },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "dict-select",
    title: "数据源",
    field: "dataSourceId",
    props: {
      options: "db",
      placeholder: "请选择所属数据源",
      clearable: true,
      filters: { orgId: "$user.orgId" },
    },
    on: {
      change: (value: string) => {
        fetchTables(value);
      },
    },
    col: { span: 12 },
    $required: true,
  },
  {
    type: "select",
    title: "表名",
    field: "tableName",
    col: { span: 12 },
    props: {
      filterable: true,
      clearable: true,
      placeholder: "请先选择数据源",
      options: tableOptions.value,
    },
    on: {
      change: (value: string) => {
        fetchColumns(value);
      },
    },
    $required: true,
  },
  {
    type: "select",
    title: "操作类型",
    field: "operationType",
    col: { span: 12 },
    props: {
      options: [
        { label: "查询(select)", value: "0" },
        { label: "新增(insert)", value: "1" },
        { label: "修改(update)", value: "2" },
        { label: "删除(delete)", value: "3" },
      ],
    },
    $required: true,
  },
  {
    type: "select",
    title: "数据模式",
    field: "model",
    col: { span: 12 },
    props: { options: [{ label: "标准模式", value: "0" }] },
    $required: true,
  },
  {
    type: "cascader",
    field: "orgId",
    title: "来源部门",
    $dict: "org",
    props: {
      props: {
        checkStrictly: false,
        emitPath: false,
      },
      placeholder: "请选择来源部门",
      clearable: true,
    },
    class: "w-full",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "el-input",
    field: "description",
    title: "服务描述",
    props: { type: "textarea", maxlength: 500, rows: 4, showWordLimit: true },
    col: { span: 24 },
  },
]);

const formRules = computed(() => {
  if (currentMode.value === "view") return FormUtils.transFormText(rawRules);
  return rawRules;
});

// ==================== 参数信息 v-table 配置 ====================
const paramsTableOptions = reactive({
  importConfig: { mode: "covering" },
  toolbarConfig: {
    buttons: [],
    tools: [],
  },
  columns: [
    { type: "seq", width: 60, title: "序号" },
    { field: "req", title: "入参", width: 80, slots: { default: "req", header: "req_header" } },
    { field: "res", title: "出参", width: 80, slots: { default: "res", header: "res_header" } },
    {
      field: "columnName",
      title: "参数名称",
      editRender: { name: "VxeInput", props: { placeholder: "请输入参数名称" } },
    },
    {
      field: "columnComment",
      title: "参数说明",
      editRender: { name: "VxeInput", props: { placeholder: "请输入参数说明", maxlength: 500 } },
    },
    {
      field: "dataType",
      title: "参数类型",
      width: 150,
      editRender: {
        name: "ElSelect",
        options: useDictStore().getDictItems("tableColType"),
        props: { placeholder: "请选择参数类型" },
      },
    },
    {
      field: "columnOperator",
      title: "请求运算符",
      width: 150,
      slots: { default: "columnOperator" },
    },
    {
      field: "requestOperator",
      title: "请求操作符",
      width: 150,
      slots: { default: "requestOperator" },
    },
    { field: "required", title: "必填属性", width: 150, slots: { default: "required" } },
  ],
});

// ==================== 入参/出参 Checkbox 逻辑 ====================
const allReq = ref(false);
const allRes = ref(false);
const indeterminateReq = ref(false);
const indeterminateRes = ref(false);

const updateCheckAllState = () => {
  const data = paramsRef.value?.getData() || [];
  allReq.value = data.length > 0 && data.every((i: any) => i.req === 1);
  allRes.value = data.length > 0 && data.every((i: any) => i.res === 1);
  indeterminateReq.value = data.some((i: any) => i.req === 1) && !allReq.value;
  indeterminateRes.value = data.some((i: any) => i.res === 1) && !allRes.value;
};

const handleReqChange = (row: any, val: CheckboxValueType) => {
  row.req = val ? 1 : 0;
  if (row.req === 1) {
    row.columnOperator = row.columnOperator || "and";
    row.requestOperator = row.requestOperator || "=";
    row.paramPosition = row.paramPosition || "body";
    row.status = row.status || "required";
  } else {
    row.columnOperator = "";
    row.requestOperator = "";
    if (row.res !== 1) {
      row.paramPosition = "";
      row.status = undefined;
    }
  }
  updateCheckAllState();
};

const handleResChange = (row: any, val: CheckboxValueType) => {
  row.res = val ? 1 : 0;
  if (row.req !== 1 && row.res === 1) {
    row.paramPosition = "";
    row.status = undefined;
  } else if (row.req === 1) {
    row.paramPosition = row.paramPosition || "body";
    row.status = row.status || "required";
  }
  updateCheckAllState();
};

const handleAllReqChange = (val: CheckboxValueType) => {
  allReq.value = !!val;
  const data = paramsRef.value?.getData() || [];
  const updated = data.map((row: any) => ({
    ...row,
    req: val ? 1 : 0,
    columnOperator: val ? row.columnOperator || "and" : "",
    requestOperator: val ? row.requestOperator || "=" : "",
    paramPosition: !val && row.res === 1 ? "" : row.paramPosition || "body",
    status: val ? row.status || "required" : row.status,
  }));
  paramsRef.value?.setData(updated);
  updateCheckAllState();
};

const handleAllResChange = (val: CheckboxValueType) => {
  allRes.value = !!val;
  const data = paramsRef.value?.getData() || [];
  const updated = data.map((row: any) => ({
    ...row,
    res: val ? 1 : 0,
    paramPosition: !val ? row.paramPosition || "body" : "",
  }));
  paramsRef.value?.setData(updated);
  updateCheckAllState();
};

let _normalizing = false;
const handleParamsDataChange = () => {
  if (_normalizing) return;
  const data = paramsRef.value?.getData() || [];
  if (data.length === 0) {
    allReq.value = false;
    allRes.value = false;
    indeterminateReq.value = false;
    indeterminateRes.value = false;
    return;
  }
  const needsFix = data.some(
    (row: any) => typeof row.req === "boolean" || typeof row.res === "boolean"
  );
  if (needsFix) {
    _normalizing = true;
    paramsRef.value?.setData(
      data.map((row: any) => ({
        ...row,
        req: row.req === true || row.req === 1 ? 1 : 0,
        res: row.res === true || row.res === 1 ? 1 : 0,
      }))
    );
    _normalizing = false;
  }
  updateCheckAllState();
};

// ==================== 数据源联动 ====================
const fetchTables = async (id: string) => {
  formRef.value?.setValue({ tableName: "" });
  if (!id) {
    tableOptions.value = [];
    updateTableOptions();
    return;
  }
  try {
    const res = await $common.get(`/dataassets/metadata/tables?dbId=${id}`);
    const data = Array.isArray(res) ? res : res?.data || [];
    tableOptions.value = data.map((item: any) => ({
      label: item.tableName,
      value: item.tableName,
      ...item,
    }));
    updateTableOptions();
  } catch {
    tableOptions.value = [];
    ElMessage.error("获取表列表失败");
  }
};

const updateTableOptions = () => {
  formRef.value?.updateFieldOptions("tableName", tableOptions.value);
};

const fetchColumns = async (tableName: string) => {
  if (!tableName) {
    paramsRef.value?.setData([]);
    updateCheckAllState();
    return;
  }
  try {
    const id = tableOptions.value.find((item: any) => item.tableName === tableName)?.tid || "";
    const res = await $common.get(`/dataassets/metadata/columns?tid=${id}`);
    const data = Array.isArray(res) ? res : res?.data || [];
    paramsRef.value?.setData(data);
    updateCheckAllState();
  } catch {
    paramsRef.value?.setData([]);
    updateCheckAllState();
    ElMessage.error("获取列列表失败");
  }
};

// ==================== 快照（取消时恢复） ====================
let _dataSnapshot: Record<string, any> = {};

// ==================== 编辑状态控制（仅 detail 场景） ====================
const startEdit = () => {
  // 更新非表单数据的快照（params、status、responseExample、tableOptions）
  _dataSnapshot.paramsData = paramsRef.value?.getData() || [];
  // _dataSnapshot.statusData = statusRef.value?.getData() || [];
  // _dataSnapshot.responseExample = responseExample.value;
  _dataSnapshot.tableOptions = [...tableOptions.value];
  // formData 保持 setFormData 初始化时的原始数据，不从 view 模式 getFormData 获取
  // （view 模式 form-text 组件返回的数据可能不完整）

  isEditing.value = true;
  nextTick(() => {
    if (_dataSnapshot.formData && Object.keys(_dataSnapshot.formData).length > 0) {
      formRef.value?.setValue(_dataSnapshot.formData);
    }
    updateTableOptions();
  });
};

const handleCancel = () => {
  isEditing.value = false;
  nextTick(() => {
    // 恢复表选项
    tableOptions.value = _dataSnapshot.tableOptions || [];
    updateTableOptions();
    // 恢复表单
    if (_dataSnapshot.formData) formRef.value?.setValue(_dataSnapshot.formData);
    paramsRef.value?.setData(_dataSnapshot.paramsData || []);
    // statusRef.value?.setData(_dataSnapshot.statusData || []);
    // responseExample.value = _dataSnapshot.responseExample || "";
    updateCheckAllState();
  });
};

// ==================== 保存（modal 和 detail 共用） ====================

/** 生成 3-10 位随机字母+数字作为 publishAddress */
const generatePublishAddress = (): string => {
  const len = Math.floor(Math.random() * 8) + 3; // 3~10
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  return Array.from({ length: len }, () => chars[Math.floor(Math.random() * chars.length)]).join(
    ""
  );
};

/** req/res 组合值 → parameters 字符串 */
const toParameters = (req: number, res: number): string => {
  if (req === 1 && res === 0) return "0";
  if (req === 0 && res === 1) return "1";
  if (req === 1 && res === 1) return "1,0";
  return "";
};

/** parameters 字符串 → req/res */
const fromParameters = (params: string | undefined): { req: number; res: number } => {
  if (!params || params.trim() === "") return { req: 0, res: 0 };
  const set = params.split(",").map((s) => s.trim());
  return {
    req: set.includes("0") ? 1 : 0,
    res: set.includes("1") ? 1 : 0,
  };
};

const handleSave = async (): Promise<boolean> => {
  try {
    saveLoading.value = true;
    const valid = await validate();
    if (!valid) {
      ElMessage.warning("请检查表单填写是否完整");
      return false;
    }
    const formData = (await formRef.value?.getFormData()) || {};
    const paramList = paramsRef.value?.getData() || [];
    // const statusList = statusRef.value?.getData() || [];
    // const responseExampleStr = responseExample.value || "";

    // 获取选中表的注释信息
    const selectedTable = tableOptions.value.find(
      (item: any) => item.tableName === formData.tableName
    );

    // 构建数据源信息（保持原逻辑）
    const sourceList =
      useDictStore()
        .getDictItems("db")
        .filter((item: any) => item.tid === formData.dataSourceId) || [];

    // 按新 API 结构组装请求体
    await $common.post("/v1/gateway/service/buildApi", {
      extendInfo: {
        catalogId: formData.catalogId,
        orgId: formData.orgId,
        description: formData.description || "",
      },
      serviceInfo: {
        name: formData.serviceName,
        dataSourceId: formData.dataSourceId,
        operationType: formData.operationType,
        tableComment: "",
        publishAddress: generatePublishAddress(),
        tableName: formData.tableName,
        tableNames: [formData.tableName].filter(Boolean),
        model: formData.model || "0",
      },
      serviceParameters: paramList.map((row: any) => ({
        tableName: formData.tableName,
        // paramType: row.paramType ?? row.dataType ?? "0",
        paramType: 0,
        aliasName: row.columnComment || "",
        paramName: row.columnName || "",
        operator: row.requestOperator || "=",
        joiner: row.columnOperator || "and",
        position: "0",
        parameters: toParameters(row.req, row.res),
        required: row.required === 1,
        description: row.columnComment || "",
      })),
      dataSourcesAll:
        sourceList.map((item: any) => ({
          id: item.tid,
          dataSourceAlias: item.label,
          dataSourceName: item.dbName,
          dataSourceType: item.dbType,
          hostName: item.host,
          port: item.port,
          userName: item.username,
          password: item.password,
          databaseInstanceName: item.dbName,
          isDefault: 0,
          description: item.description || "",
          status: 0,
        })) || [],
      serviceTableMappings: [],
      serviceTables: selectedTable
        ? [
            {
              tableName: selectedTable.tableName,
              tableComment: "",
              tableDetail: "",
              tableRows: "",
            },
          ]
        : [],
      dataSourceConnectAttribute: [],
      serviceRule: {},
      // _api_build_statusList: statusList,
      // _api_responseExample: responseExampleStr,
    });

    if (props.context === "detail") {
      isEditing.value = false;
      _dataSnapshot = {
        formData: { ...formData },
        paramsData: [...paramList],
        // statusData: [...statusList],
        // responseExample: responseExampleStr,
        tableOptions: [...tableOptions.value],
      };
    }

    emit("saved", formData);
    ElMessage.success(props.context === "detail" ? "保存成功" : "服务构建成功");
    return true;
  } catch (error) {
    console.error("保存失败:", error);
    ElMessage.error("保存失败，请重试");
    return false;
  } finally {
    saveLoading.value = false;
  }
};

// ==================== 数据填充（适配新 API 详情出参结构） ====================
const setFormData = (data: Record<string, any>) => {
  console.log("setFormData", data);
  if (!data || !Object.keys(data).length) return;
  nextTick(async () => {
    // ---- 表单字段回填：从嵌套结构中提取 ----
    const serviceInfo: any = data.serviceInfo || {};
    const extendInfo: any = data.extendInfo || {};

    formRef.value?.setValue({
      serviceName: serviceInfo.name || "",
      catalogId: extendInfo.catalogId || "",
      dataSourceId: serviceInfo.dataSourceId || "",
      tableName: serviceInfo.tableName || "",
      operationType: serviceInfo.operationType || "",
      model: serviceInfo.model ?? "0",
      description: data.description || "",
    });

    // ---- 响应示例（已注释）----
    // if (data.responseExample !== undefined) {
    //   responseExample.value = data.responseExample;
    // } else if (data._api_responseExample !== undefined) {
    //   responseExample.value = data._api_responseExample;
    // }

    // ---- 状态码（已注释）----
    // if (data.statusList) statusRef.value?.setData(data.statusList);
    // else if (data._api_build_statusList) statusRef.value?.setData(data._api_build_statusList);

    // ---- 参数列表：service_parameters → paramList（反向映射） ----
    const rawParams: any[] = data.service_parameters || data.paramList || [];
    if (rawParams.length > 0) {
      const convertedParams = rawParams.map((p: any) => {
        const { req, res } = fromParameters(p.parameters);
        return {
          columnName: p.paramName || "",
          columnComment: p.aliasName || p.description || "",
          dataType: p.paramType ?? "0",
          req,
          res,
          requestOperator: p.operator || "=",
          columnOperator: p.joiner || "and",
          required: p.required ? 1 : 0,
          // 保留原始数据以便后续使用
          ...p,
        };
      });
      paramsRef.value?.setData(convertedParams);
    }

    // ---- 数据源→表名联动 ----
    if (serviceInfo.dataSourceId) await fetchTables(serviceInfo.dataSourceId);
    updateCheckAllState();

    _dataSnapshot = {
      formData: { ...data },
      paramsData: rawParams.length > 0 ? rawParams : [],
      // statusData: data.statusList || data._api_build_statusList || [],
      // responseExample: data.responseExample || data._api_responseExample || responseExample.value,
      tableOptions: [...tableOptions.value],
    };
  });
};

watch(
  () => props.data,
  () => {
    nextTick(() => setFormData(props.data));
  },
  { immediate: true, deep: true }
);

// ==================== Validate ====================
const validate = async (): Promise<boolean> => {
  try {
    const paramsValid = await paramsRef.value?.validate();
    // const statusValid = await statusRef.value?.validate();
    const formValid = await formRef.value?.validate();
    // return paramsValid !== false && statusValid !== false && formValid !== false;
    return paramsValid !== false && formValid !== false;
  } catch {
    return false;
  }
};

// ==================== Expose ====================
defineExpose({
  getFormData: async () => (await formRef.value?.getFormData()) || {},
  getParamsData: () => paramsRef.value?.getData() || [],
  // getStatusData: () => statusRef.value?.getData() || [],
  // getResponseExample: () => responseExample.value || "",
  validate,
  setFormData,
  handleSave,
  reset: () => {
    formRef.value?.resetFields();
    tableOptions.value = [];
    // responseExample.value = "";
    // statusRef.value?.reset();
    paramsRef.value?.setData([]);
    updateCheckAllState();
  },
});
</script>

<style scoped lang="scss">
.api-build-content {
  .data-sources-table {
    padding: 10px 0;
  }
  .api-status {
    position: relative;
    .vxe-grid--toolbar-wrapper {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }
  .api-params-table {
    min-height: 200px;
    position: relative;
    .vxe-grid--toolbar-wrapper {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }
  .header-checkbox {
    height: 40px;
    color: #587aa8;
  }
}
</style>
