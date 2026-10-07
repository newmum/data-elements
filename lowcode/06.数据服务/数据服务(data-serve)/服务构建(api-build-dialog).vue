<template>
  <el-dialog
    v-model="open"
    destroy-on-close
    fullscreen
    class="api-modal__container"
    header-class="p-0!"
    @close="onCancel"
  >
    <div class="flex flex-col h-full">
      <div class="api-modal__header">
        <div class="api-modal__title">服务构建</div>
      </div>
      <template v-if="!isFinish">
        <div class="api-modal__body" style="grid-template-columns: 100%">
          <el-scrollbar>
            <div class="pr-5 flex flex-col h-full">
              <!-- 基本信息区域 -->
              <u-title name="服务信息" class="mt-2" />
              <json-form ref="formRef" bordered :rules="formRules">
                <template #field-requestMethod="scope">
                  <el-radio-group v-model="scope.model.value">
                    <el-radio
                      v-for="item in scope.rule.props.options"
                      :key="item.value"
                      :label="item.value"
                      border
                    >
                      {{ item.label }}
                    </el-radio>
                  </el-radio-group>
                </template>
              </json-form>

              <!-- 请求参数 -->
              <u-title name="请求参数" class="mt-4" />
              <api-params ref="paramsRef" :params="params"></api-params>

              <!-- 数据源信息 -->
              <u-title name="数据源配置" class="mt-4" />
              <div class="data-sources-table">
                <vxe-table
                  ref="dataSourceRef"
                  :data="dataSources"
                  :edit-config="{ trigger: 'click', mode: 'row' }"
                  :row-config="{ isHover: true }"
                  border
                  size="small"
                  class="mb-4"
                >
                  <vxe-column field="data_source_alias" title="数据源别名" :edit-render="{ name: 'VxeInput' }" width="140" />
                  <vxe-column field="data_source_name" title="数据源名称" :edit-render="{ name: 'VxeInput' }" width="140" />
                  <vxe-column field="data_source_type" title="类型" :edit-render="{ name: 'VxeSelect', options: dataSourceTypeOptions }" width="100" />
                  <vxe-column field="host_name" title="地址" :edit-render="{ name: 'VxeInput' }" width="140" />
                  <vxe-column field="port" title="端口" :edit-render="{ name: 'VxeInput' }" width="80" />
                  <vxe-column field="user_name" title="用户名" :edit-render="{ name: 'VxeInput' }" width="120" />
                  <vxe-column field="password" title="密码" :edit-render="{ name: 'VxeInput' }" width="120" />
                  <vxe-column field="database_instance_name" title="库名" :edit-render="{ name: 'VxeInput' }" width="140" />
                  <vxe-column title="操作" width="60">
                    <template #default="{ row }">
                      <el-button type="danger" text size="small" @click="removeDataSource(row)">删除</el-button>
                    </template>
                  </vxe-column>
                </vxe-table>
                <el-button type="primary" plain size="small" @click="addDataSource">
                  <Icon :icon="'el-icon-Plus'" class="mr-1" />新增数据源
                </el-button>
              </div>

              <!-- 响应示例 -->
              <u-title name="响应示例" class="mt-4" />
              <code-editor
                v-model="responseExample"
                :lang="'json'"
                style="height: 200px"
              ></code-editor>

              <!-- 状态码 -->
              <u-title name="状态码" class="mt-4" />
              <v-table
                ref="statusRef"
                class="api-status"
                :options="statusTableOptions"
                show-operation
              ></v-table>
            </div>
          </el-scrollbar>
        </div>
        <div class="api-modal__footer">
          <el-button @click="onCancel">取消</el-button>
          <el-button type="primary" plain :disabled="saveLoading" @click="handleSave">
            <icon :icon="'save'" class="mr-2" />
            保存
          </el-button>
          <el-button type="primary" :disabled="taskLoading" @click="handleRegister">
            <icon :icon="'check-circle'" class="mr-2" />
            提交构建
          </el-button>
        </div>
      </template>
      <task-finish
        v-else
        :name="buildFormData.serviceName || ''"
        :time="new Date().toISOString()"
        :code="'TSK_BUILD_' + Date.now()"
        @to-see="handleToSee"
      ></task-finish>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: boolean): void;
}>();

const props = defineProps({
  modelValue: Boolean,
});

const formRules = ref<any[]>([]);
const formRef = ref();
const paramsRef = ref();
const statusRef = ref();
const dataSourceRef = ref();
const saveLoading = ref(false);
const taskLoading = ref(false);
const isFinish = ref(false);
const isSaved = ref(false);
const responseExample = ref('');
const params = ref<any[]>([]);
const catalogOptions = ref<any[]>([]);

const dataSources = ref<any[]>([
  {
    data_source_alias: 'gateway_44',
    data_source_name: 'gateway_44',
    data_source_type: 'mysql',
    host_name: '10.231.176.39',
    port: '3306',
    user_name: '',
    password: '',
    database_instance_name: 'gateway_44',
  },
]);

const dataSourceTypeOptions = [
  { label: 'MySQL', value: 'mysql' },
  { label: 'Oracle', value: 'oracle' },
  { label: 'PostgreSQL', value: 'postgresql' },
  { label: 'SQL Server', value: 'sqlserver' },
];

const buildFormData = ref<any>({});

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

formRules.value = FormUtils.fixJson([
  {
    type: "UTitle",
    props: { name: "基本要素" },
  },
  {
    type: "input",
    title: "服务名称",
    field: "serviceName",
    col: { span: 12 },
    $required: true,
  },
  {
    type: "select",
    title: "服务目录名称",
    field: "catalogId",
    col: {
      span: 12,
    },
    props: {
      filterable: true,
      clearable: true,
      options: catalogOptions.value,
    },
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
    type: "input",
    title: "表名",
    field: "tableName",
    col: { span: 12 },
    $required: true,
  },
  {
    type: "select",
    title: "数据模式",
    field: "model",
    col: { span: 12 },
    props: {
      options: [
        { label: "标准模式", value: "0" },
      ],
    },
    $required: true,
  },
  {
    type: "UTitle",
    props: { name: "其他信息" },
  },
  {
    type: "el-input",
    field: "description",
    title: "服务描述",
    props: {
      type: "textarea",
      maxlength: 500,
      rows: 3,
    },
    col: { span: 24 },
  },
]);

const statusTableOptions = reactive({
  rowConfig: { drag: true },
  toolbarConfig: {
    buttons: [
      { code: "append_edit", icon: "vxe-icon-add", name: "新增参数", status: "primary", mode: "text" },
    ],
    tools: [],
  },
  columns: [
    { field: "statusCode", title: "状态码", editRender: { name: "VxeInput" } },
    { field: "statusDesc", title: "状态描述", editRender: { name: "VxeInput" } },
  ],
  editRules: {
    statusCode: [{ required: true, content: "状态码不能为空", trigger: "blur" }],
    statusDesc: [{ required: true, content: "状态描述不能为空", trigger: "blur" }],
  },
});

const defaultStatusCodes = [
  { statusCode: "200", statusDesc: "请求成功" },
  { statusCode: "400", statusDesc: "非法请求" },
  { statusCode: "500", statusDesc: "服务器错误" },
];

const defaultParams = [
  { paramName: "id", paramDesc: "主键ID", paramType: "string", paramPosition: "query", instance: "", req: true, res: false, status: "required", paramComment: "" },
  // { paramName: "pageNum", paramDesc: "页码", paramType: "integer", paramPosition: "query", instance: "", req: false, res: false, status: "optional", paramComment: "" },
  // { paramName: "pageSize", paramDesc: "每页条数", paramType: "integer", paramPosition: "query", instance: "", req: false, res: false, status: "optional", paramComment: "" },
  // { paramName: "total", paramDesc: "总记录数", paramType: "integer", paramPosition: "", instance: "", req: false, res: true, status: "optional", paramComment: "" },
  // { paramName: "list", paramDesc: "数据列表", paramType: "array", paramPosition: "", instance: "", req: false, res: true, status: "optional", paramComment: "" },
];

const addDataSource = () => {
  dataSources.value.push({
    id: $common.uuid(),
    data_source_alias: '',
    data_source_name: '',
    data_source_type: 'mysql',
    host_name: '',
    port: '3306',
    user_name: '',
    password: '',
    database_instance_name: '',
    is_default: 0,
    description: '',
    status: 0,
  });
};

const removeDataSource = (row: any) => {
  const idx = dataSources.value.indexOf(row);
  if (idx > -1) {
    dataSources.value.splice(idx, 1);
  }
};

const handleSave = async () => {
  try {
    saveLoading.value = true;
    const paramsValid = await paramsRef.value?.validate();
    const statusValid = await statusRef.value?.validate();
    const formValid = await formRef.value?.validate();

    if (paramsValid && statusValid && formValid) {
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const paramsData = paramsRef.value?.getData();
      const statusData = statusRef.value?.getData();
      const formData = await formRef.value?.getFormData();

      buildFormData.value = formData;
      console.log("保存参数", paramsData);
      console.log("保存状态码", statusData);
      console.log("保存表单数据", formData);

      isSaved.value = true;
      ElMessage.success("保存成功");
    }
  } catch (error) {
    console.error("保存失败:", error);
    ElMessage.error("保存失败，请重试");
  } finally {
    saveLoading.value = false;
  }
};

const handleRegister = async () => {
  if (!isSaved.value) {
    ElMessage.warning("请先保存配置信息");
    return;
  }

  try {
    taskLoading.value = true;

    const formData = await formRef.value?.getFormData();
    const paramList = paramsRef.value?.getData() || params.value;
    const statusList = statusRef.value?.getData() || defaultStatusCodes;

    const res = await $request({
      url: '/v1/gateway/service/buildApi',
      method: 'post',
      data: {
        // 直接将所有表单信息平铺提交，由后端 assembleBuildApiRequest 封装
        serviceName: formData.serviceName || '',
        dataSourceId: formData.dataSourceId || '',
        catalogId: formData.catalogId || '',
        operationType: formData.operationType || '0',
        tableName: formData.tableName || '',
        model: formData.model || '0',
        description: formData.description || '',
        requestMethod: formData.requestMethod || '',
        _paramList: paramList,
        _statusList: statusList,
        data_sources_all: dataSources.value.map((ds: any) => ({
          ...ds,
          id: ds.id || $common.uuid(),
        })),
        responseExample: responseExample.value || '',
      },
    });

    console.log('服务构建成功，响应:', res);
    isFinish.value = true;
    ElMessage.success('服务构建成功');
  } catch (error) {
    console.error('构建服务失败:', error);
    ElMessage.error('构建服务失败，请重试');
  } finally {
    taskLoading.value = false;
  }
};

const onCancel = () => {
  emit("close", false);
  open.value = false;
};

const handleToSee = () => {
  open.value = false;
  emit("close", false);
};

// 初始化 mock 数据
params.value = defaultParams;

// 获取服务目录列表（下拉框数据）
const fetchCatalogs = async () => {
  try {
    const res = await $common.post("/dataassets/manage/assets/page", {
      sortField: "updatedTime",
      sortDir: "desc",
      pageNum: 1,
      pageSize: 1000,
      conditions: [{ field: "assetType", value: "catalog", type: "match" }],
    });
    const data = res.data || res;
    const options = (data.list || []).map((item: any) => ({
      label: item.catalogName,
      value: item.tid,
    }));
    catalogOptions.value = options;
    // 替换 formRules 触发重新渲染，使 select 的 options 生效
    formRules.value = formRules.value.map((rule: any) =>
      rule.field === "catalogId"
        ? { ...rule, props: { ...rule.props, options } }
        : rule
    );
  } catch (error) {
    console.error("获取服务目录列表失败:", error);
  }
};

// 弹窗打开时加载服务目录列表
watch(
  open,
  (val: boolean) => {
    catalogOptions.value = [];
    if (val) {
      fetchCatalogs();
      isSaved.value = false;
    }
  },
  { immediate: true }
);
</script>

<style lang="scss">
.api-modal__container {
  padding: 0;
  .el-dialog__body {
    height: 100%;
    padding: 0;
  }
  .el-dialog__header {
    padding: 0;
  }
  .api-modal__header {
    display: grid;
    flex-shrink: 0;
    grid-template-columns: 15% 1fr 15%;
    align-items: center;
    height: 45px;
    padding: 0 15px 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #ebeef5;
  }
  .api-modal__title {
    font-family: Arial, sans-serif;
    font-size: 16px;
    font-weight: 700;
    color: #000000e0;
  }
  .api-modal__footer {
    display: flex;
    justify-content: center;
    padding: 10px 24px;
    text-align: right;
    background-color: #fff;
    border-top: 1px solid #ebeef5;
  }
  .api-modal__body {
    height: calc(100% - 45px - 53px);
    display: grid;
    .el-radio__label {
      font-size: 12px;
    }
  }
  .api-status {
    position: relative;
    .vxe-grid--toolbar-wrapper {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }
  .data-sources-table {
    padding: 10px 0;
  }
}
</style>
