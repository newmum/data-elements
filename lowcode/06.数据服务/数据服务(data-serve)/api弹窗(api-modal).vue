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
        <div class="api-modal__title">创建服务</div>
      </div>
      <template v-if="!isFinish">
        <div class="api-modal__body" style="grid-template-columns: 20% 80%">
          <div class="left-panel h-full">
            <left-select
              v-model="currentCategoryDid"
              :list="categoryList"
              :title="'服务目录列表'"
              :icon="'api'"
              :field-name="{ title: 'catalogName' }"
              @select="handleSelect"
              @delete="handleDelete"
              @add="handleAdd"
            />
          </div>
          <el-scrollbar>
            <div class="pr-5 flex flex-col h-full">
              <json-form ref="formRef" bordered :rules="formRules">
                <template #field-requestMethod="scope">
                  <el-radio-group
                    v-model="scope.model.value"
                    @change="
                      (val: string | number | boolean | undefined) => handleChange(String(val))
                    "
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
              <u-title v-if="apiType === 'build'" name="SQL脚本" class="mt-4" />
              <code-editor
                v-if="apiType === 'build'"
                v-model="scriptCode"
                :lang="'sql'"
                style="height: 300px"
              ></code-editor>
              <api-params ref="paramsRef" :params="params"></api-params>
              <u-title name="响应示例" class="mt-4" />
              <code-editor
                v-model="responseExample"
                :lang="'sql'"
                style="height: 300px"
              ></code-editor>
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
            提交注册
          </el-button>
        </div>
      </template>
      <task-finish
        v-else
        :name="(currentCategoryInfo as any).catalogName"
        :time="(currentCategoryInfo as any).latestUpdateTime"
        :code="'TSK_INGEST_20250102_001'"
        @to-see="handleToSee"
      ></task-finish>
    </div>
    <category-drawer
      v-model:visible="drawerVisible"
      :selected-data="selectedData"
      @confirm="handleDrawerConfirm"
      @close="handleDrawerClose"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, reactive } from 'vue'
import { FormUtils } from "@/utils/form";
import { cloneDeep } from "lodash-es";
import { ElMessage } from "element-plus";
import { useDictStore } from "@/store";
// import apiParams from "./api-params.vue";
// import categoryDrawer from "./category-drawer.vue";

const shareTypeOptions = useDictStore().getDictItems("shareType");
const levelOptions = useDictStore().getDictItems("dataSecurityLevel");

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: boolean): void;
}>();

const props = defineProps({
  modelValue: Boolean,
  categoryData: Array,
  apiType: String, // register:登记API build:服务构建
});
const formRules = ref<any[]>([]);
const formRef = ref();
const paramsRef = ref();
const categoryList = ref<any[]>([]);
const currentCategoryDid = ref("");
const currentCategoryInfo = ref<any>({});
const scriptCode = ref("");
const responseExample = ref("");
const params = ref<any[]>([]);
const catalogOptions = ref<any[]>([]);
const drawerVisible = ref(false);
const selectedData = ref<any[]>([]); // 选中的目录数据
const saveLoading = ref(false);
const taskLoading = ref(false);
const isFinish = ref(false);
const isSaved = ref(false);
const dictStore = useDictStore();
const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

formRules.value = FormUtils.fixJson([
  {
    type: "UTitle",
    props: {
      name: "基本要素",
    },
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
      options: [],
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "数据所属领域",
    field: "dataDomain",
    props: {
      options: "dataDomain",
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "cascader",
    title: "数据所属分类",
    field: "dataCategory",
    props: {
      options: dictStore.getDictItems("dataCategory"),
    },
    class: "w-full",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "来源事项名称",
    field: "sourceBusinessName",
    props: {
      options: "sourceBusinessName",
      fitInputWidth: true,
    },
    col: {
      span: 12,
    },
  },
  {
    type: "input",
    title: "来源事项业务项",
    field: "sourceBusinessItem",
    col: {
      span: 12,
    },
  },
  {
    type: "DictSelect",
    title: "数据所在层级",
    field: "dataLevel",
    props: {
      options: "dataLevel",
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "el-input",
    field: "assetDesc",
    title: "服务摘要",
    props: {
      type: "textarea",
      placeholder: "请简要描述数据服务的核心内容、覆盖范围及核心用途",
      maxlength: 500,
      rows: 4,
      showWordLimit: true,
    },
    col: {
      span: 24,
    },
    $required: true,
  },
  {
    type: "UTitle",
    props: {
      name: "技术要素",
    },
  },
  {
    type: "input",
    title: "服务名",
    field: "serviceName",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "select",
    title: "返回类型",
    field: "returnType",
    col: {
      span: 12,
    },
    props: {
      options: [
        {
          label: "JSON",
          value: "JSON",
        },
        {
          label: "XML",
          value: "XML",
        },
      ],
    },
    $required: true,
  },
  {
    type: "select",
    title: "接入协议",
    field: "accessProtocol",
    col: {
      span: 12,
    },
    props: {
      type: "select",
      options: [
        {
          label: "HTTP",
          value: "HTTP",
        },
        {
          label: "HTTPS",
          value: "HTTPS",
        },
      ],
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "更新周期",
    field: "dataUpdateFreq",
    props: {
      options: "dataUpdateFreq",
      placeholder: "请选择更新周期",
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "input",
    title: "接口地址",
    field: "apiUrl",
    col: {
      span: 12,
    },
    props: {
      placeholder: "请输入接口地址，例如 http://172.16.45.4/s/",
    },
    $required: true,
  },
  {
    type: "input",
    title: "请求地址",
    field: "requestUrl",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "fieldComponent",
    title: "请求方式",
    field: "requestMethod",
    props: {
      type: "radio",
      options: [
        {
          label: "GET",
          value: "GET",
          color: "#fa8c16",
        },
        {
          label: "POST",
          value: "POST",
          color: "#1677ff",
        },
        {
          label: "PUT",
          value: "PUT",
          color: "#389e0d",
        },
        {
          label: "DELETE",
          value: "DELETE",
          color: "#9254de",
        },
      ],
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "select",
    title: "共享类型",
    field: "shareType",
    col: {
      span: 12,
    },
    props: {
      options: shareTypeOptions,
    },
    $required: true,
  },
  {
    type: "select",
    title: "数据分级",
    field: "dataLevel",
    props: {
      type: "select",
      options: levelOptions,
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "input",
    field: "description",
    title: "服务描述",
    props: {
      type: "textarea",
      maxlength: 255,
      rows: 4,
    },
    col: {
      span: 24,
    },
  },
]);
const statusRef = ref();
const statusTableOptions = reactive({
  rowConfig: {
    drag: true,
  },
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增参数",
        status: "primary",
        mode: "text",
      },
    ],
    tools: [],
  },
  columns: [
    {
      field: "statusCode", // 可编辑-插槽渲染
      title: "状态码",
      editRender: { name: "VxeInput" },
    },
    {
      field: "statusDesc", // 可编辑-插槽渲染
      title: "状态描述",
      editRender: { name: "VxeInput" },
    },
  ],
  editRules: {
    statusCode: [{ required: true, content: "状态码不能为空", trigger: "blur" }],
    statusDesc: [{ required: true, content: "状态描述不能为空", trigger: "blur" }],
  },
});
const statusCodeData = ref([
  {
    statusCode: "500",
    statusDesc: "服务器错误",
  },
  {
    statusCode: "400",
    statusDesc: "非法请求",
  },
  {
    statusCode: "401",
    statusDesc: "认证失败",
  },
  {
    statusCode: "403",
    statusDesc: "禁止，没有权限操作",
  },
  {
    statusCode: "404",
    statusDesc: "找不到",
  },
  {
    statusCode: "200",
    statusDesc: "请求成功",
  },
]); // mock
const paramsData = ref([
  {
    paramName: "ID",
    paramDesc: "ID",
    paramType: "string",
    paramPosition: "query",
    instance: "",
    req: true,
    res: false,
    status: "required",
    paramComment: "",
  },
  {
    paramName: "userId",
    paramDesc: "用户ID",
    paramType: "string",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "required",
    paramComment: "广电用户唯一标识",
  },
  {
    paramName: "deviceId",
    paramDesc: "设备ID",
    paramType: "string",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "required",
    paramComment: "机顶盒或智能终端设备ID",
  },
  {
    paramName: "channelId",
    paramDesc: "频道ID",
    paramType: "string",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "optional",
    paramComment: "电视频道ID",
  },
  {
    paramName: "startTime",
    paramDesc: "开始时间",
    paramType: "datetime",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "required",
    paramComment: "查询开始时间",
  },
  {
    paramName: "endTime",
    paramDesc: "结束时间",
    paramType: "datetime",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "required",
    paramComment: "查询结束时间",
  },
  {
    paramName: "watchDuration",
    paramDesc: "观看时长",
    paramType: "integer",
    paramPosition: "",
    instance: "",
    req: false,
    res: true,
    status: "optional",
    paramComment: "用户观看时长(分钟)",
  },
]);

const handleChange = (val: string) => {
  formRef.value?.setValue({
    requestMethod: val,
  });
};

const updateCategoryList = (data: any[]) => {
  if (!data.length) return;
  currentCategoryDid.value = data[0]?.id;
  currentCategoryInfo.value = data[0];
  categoryList.value = data.map((item) => ({
    ...item,
    did: item.id,
  }));
  nextTick(() => {
    updateRightData(data[0]);
  });
};

const defaultFormData = {
  catalogId: '',
  catalogName: '广电用户收视行为分析服务',
  apiUrl: 'http://172.16.45.4/s/',
  dataDomain: 'TVOD',
  dataCategory: [],
  sourceBusinessName: '广电运营支撑系统',
  sourceBusinessItem: '用户收视行为分析',
  dataLevel: 'province',
  assetDesc: '提供广电用户收视行为数据的查询与分析服务，覆盖用户观看记录、频道偏好、观看时长等多维度数据，支持运营决策与用户画像构建。',
  serviceName: 'user-watch-behavior-service',
  returnType: 'JSON',
  accessProtocol: 'HTTP',
  dataUpdateFreq: 'daily',
  requestUrl: '/api/v1/user/watch/behavior',
  requestMethod: 'GET',
  shareType: '有条件共享',
  description: '本服务提供广电用户收视行为数据的标准化查询接口，支持按用户ID、设备ID、时间范围等维度进行数据检索，返回用户观看频道、时长及设备类型等详细信息。',
};

const updateRightData = (item: any) => {
  // 合并默认 mock 数据与目录项数据，确保表单始终有展示内容
  formRef.value?.setValue({
    ...defaultFormData,
    ...item,
  });
  statusRef.value?.setData(statusCodeData.value);
  params.value = paramsData.value;

  const fields = paramsData.value.map((param) => param.paramName).join(", ");
  const tableName = item?.resources || 'user_watch_behavior';
  scriptCode.value = `select ${fields}\nfrom ${tableName}\nwhere userId = :userId\n  and startTime >= :startTime\n  and endTime <= :endTime\norder by startTime desc;`;
  responseExample.value = JSON.stringify(
    {
      code: 200,
      message: "success",
      data: [
        {
          userId: "U20240001",
          deviceId: "STB20240001",
          channelId: "CCTV-1",
          startTime: "2024-01-15 19:30:00",
          endTime: "2024-01-15 20:15:00",
          watchDuration: 45,
        },
        {
          userId: "U20240001",
          deviceId: "STB20240002",
          channelId: "CCTV-13",
          startTime: "2024-01-15 20:20:00",
          endTime: "2024-01-15 21:00:00",
          watchDuration: 40,
        },
      ],
    },
    null,
    2
  );
};
const resetRinghtData = () => {
  formRef.value?.resetFields();
  // paramsRef.value?.clear();
  // statusRef.value?.clear();
  params.value = [];
  scriptCode.value = "";
  responseExample.value = "";
};
const onCancel = (): void => {
  emit("close", false);
  open.value = false;
};

const handleSelect = (item: any) => {
  currentCategoryDid.value = item.did;
  currentCategoryInfo.value = item;
  resetRinghtData();
  updateRightData(item);
};

const handleAdd = () => {
  if (props.apiType === "build") {
    drawerVisible.value = true;
    selectedData.value = cloneDeep(categoryList.value);
  } else {
    categoryList.value.push({ did: $common.uuid() });
  }
};

const handleDelete = (item: any) => {
  // 检查是否是最后一个目录
  if (categoryList.value.length <= 1) {
    ElMessage.warning("不能删除最后一个目录");
    return;
  }

  // 执行删除操作
  const index = categoryList.value.findIndex((category) => category.did === item.did);
  if (index > -1) {
    categoryList.value.splice(index, 1);
    // 如果删除的是当前选中的目录，切换到第一个目录
    if (currentCategoryDid.value === item.did && categoryList.value.length > 0) {
      handleSelect(categoryList.value[0]);
    }
    ElMessage.success("目录删除成功");
  }
};

const handleDrawerConfirm = (data: any) => {
  updateCategoryList(data);
  drawerVisible.value = false;
};

const handleDrawerClose = () => {
  drawerVisible.value = false;
};

watch(
  open,
  (val: boolean) => {
    categoryList.value = [];
    catalogOptions.value = [];
    if (val) {
      fetchCatalogs();
      isSaved.value = false;
      if (props.apiType === "build") {
        updateCategoryList(props.categoryData || []);
      } else {
        handleAdd();
      }
    }
  },
  { immediate: true }
);

watch(
  () => props.categoryData,
  (newData) => {
    if (newData?.length) {
      updateCategoryList(newData);
    }
  },
  {
    deep: true,
  }
);

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

// 保存表单数据
const handleSave = async () => {
  try {
    // 设置加载状态
    saveLoading.value = true;

    // 验证所有表单
    const paramsValid = await paramsRef.value?.validate();
    const statusValid = await statusRef.value?.validate();
    const formValid = await formRef.value?.validate();

    if (paramsValid && statusValid && formValid) {
      // 模拟接口加载过程
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // 获取数据
      const paramsData = paramsRef.value?.getData();
      const statusData = statusRef.value?.getData();
      const formData = await formRef.value?.getFormData();
      categoryList.value.forEach((item) => {
        if (item.did === currentCategoryDid.value) {
          item.tid = currentCategoryDid.value;
        }
      });
      console.log("保存参数", paramsData);
      console.log("保存状态码", statusData);
      console.log("保存表单数据", formData);

      // 保存成功后设置保存状态为 true
      isSaved.value = true;
      // 显示成功提示
      ElMessage.success("保存成功");
    }
  } catch (error) {
    console.error("保存失败:", error);
    ElMessage.error("保存失败，请重试");
  } finally {
    // 关闭加载状态
    saveLoading.value = false;
  }
};

const handleRegister = async () => {
  // 检查是否已经保存
  if (!isSaved.value) {
    ElMessage.warning("请先保存配置信息");
    return;
  }

  try {
    taskLoading.value = true;

    // 获取表单、参数及状态码数据（原样传递到后端，由后端组装最终JSON）
    const formData = await formRef.value?.getFormData();
    const paramList = paramsRef.value?.getData() || params.value;
    const statusList = statusRef.value?.getData() || statusCodeData.value;

    const res = await $request({
      url: '/v1/gateway/service/save',
      method: 'post',
      data: {
        // 表单字段作为独立 KV 传递
        ...formData,
        // 额外参数
        groupId: currentCategoryDid.value || '',
        _paramList: JSON.stringify(paramList),
        _statusList: JSON.stringify(statusList),
        _responseExample: responseExample.value || '',
        _scriptCode: scriptCode.value || '',
      },
    });
    console.log('创建服务成功，响应:', res);
    isFinish.value = true;
    ElMessage.success('服务注册成功');
  } catch (error) {
    console.error('注册服务失败:', error);
    ElMessage.error('注册服务失败，请重试');
  } finally {
    taskLoading.value = false;
  }
};

// 处理前往查看
const handleToSee = () => {
  // 关闭模态框
  open.value = false;
  emit("close", false);
};
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
    grid-template-columns: 20% 80%;
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
}
</style>
