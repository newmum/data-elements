<template>
  <el-dialog
    v-model="open"
    destroy-on-close
    fullscreen
    class="access-modal__container"
    body-class="px-20"
    header-class="p-0!"
    @close="onCancel"
  >
    <div class="flex flex-col h-full">
      <div class="access-modal__header">
        <div class="access-modal__title">数据接入</div>
      </div>
      <template v-if="!isFinish">
        <el-scrollbar>
          <div class="access-modal__body">
            <el-skeleton :loading="loading" animated>
              <JsonForm ref="formRef" bordered :rules="formRules">
                <template #field-taskTypeId="scope">
                  <el-tree-select
                    v-model="scope.model.value"
                    :data="eltCategoryOptions"
                    :default-expand-all="true"
                    placeholder="请选择数据库"
                    @change="handleEltChange"
                  ></el-tree-select>
                </template>
              </JsonForm>
              <u-title name="数据库表配置" class="mt-4" />
              <database-config
                ref="databaseConfigRef"
                :info="basicInfo"
                :edit="!props.id"
              ></database-config>
              <u-title name="字段映射" class="mt-4 mapping-title">
                <el-button type="text" class="ml-2" @click="handleAutoMapping">
                  <Icon icon="el-icon-Refresh"></Icon>
                  自动映射
                </el-button>
              </u-title>
              <field-mapping
                ref="fieldMappingRef"
                :data="basicInfo.columnMapping"
                :target-field-options="targetFieldOptions"
              ></field-mapping>
              <u-title name="执行调度策略" class="mt-4" />
              <schedule-config ref="scheduleConfigRef" :info="basicInfo"></schedule-config>
            </el-skeleton>
          </div>
        </el-scrollbar>
        <div class="access-modal__footer">
          <el-button @click="onCancel">取消</el-button>
          <el-button type="primary" plain :disabled="isSaveing" @click="handleSave">
            <icon :icon="'save'" class="mr-2" />
            保存
          </el-button>
          <el-button type="primary" :disabled="taskLoading" @click="handleCreateTask">
            <icon :icon="'check-circle'" class="mr-2" />
            创建任务
          </el-button>
        </div>
      </template>
      <task-finish
        v-else
        :name="basicInfo.taskName"
        :code="basicInfo.dataCatalogId"
        :time="basicInfo.lastRunning"
        :type="'access'"
        title="创建成功"
        @to-see="onToSee"
      ></task-finish>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
// import databaseConfig from "./databaseConfig.vue";
// import fieldMapping from "./fieldMapping.vue";
// import scheduleConfig from "./scheduleConfig.vue";
// import taskFinish from "./task-finish.vue";

interface BasicInfo {
  dataCatalogId?: string;
  taskName?: string;
  taskTypeName?: string;
  taskTypeId?: string;
  taskDesc?: string;
  sourceDbId?: string;
  sourceTableId?: string;
  sourceTablePrimaryKey?: string;
  sourceTableIncrementKey?: string;
  targetDbId?: string;
  targetTableId?: string;
  targetTablePrimaryKey?: string;
  scheduleStrategy?: number;
  scheduleCycle?: number;
  scheduleRunning?: string;
  scheduleRunningStart?: string;
  scheduleFaildRetry?: number;
  cronExpress?: string;
  taskStatus?: number;
  lastRunning?: string;
  lastStatusValue?: string;
  endRunning?: string;
  tenantId?: string;
  columnMapping?: any[];
}

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: boolean): void;
}>();

const props = defineProps({
  modelValue: Boolean,
  id: String, // 目录id
  accessTaskId: String, // 任务id
});
const formRules = ref([]);
const formRef = ref();
const databaseConfigRef = ref();
const fieldMappingRef = ref();
const scheduleConfigRef = ref();
const loading = ref(false);
const taskLoading = ref(false);
const basicInfo = ref<BasicInfo>({});
const isFinish = ref(false);
const isSaved = ref(false);
const isSaveing = ref(false);
const eltCategoryOptions = ref<any[]>([]);
// 目标字段选项 - 从 databaseConfig 获取后传递给 fieldMapping
const targetFieldOptions = ref<any[]>([]);

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

formRules.value = FormUtils.fixJson([
  {
    type: "UTitle",
    props: {
      name: "任务基本要素",
    },
  },
  {
    type: "input",
    title: "任务名称",
    field: "taskName",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "fieldComponent",
    title: "ETL任务分类",
    field: "taskTypeId",
    props: {
      data: [],
      placeholder: "请选择ETL任务分类",
      clearable: true,
      filterable: true,
      "check-strictly": false,
      "default-expand-all": true,
    },
    col: {
      span: 12,
    },
  },
  {
    type: "input",
    field: "taskDesc",
    title: "任务描述",
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

const handleEltChange = (value: any) => {
  formRef.value?.setValue({
    taskTypeId: value,
  });
};

// 获取ETL任务分类数据
const fetchEltCategoryOptions = async () => {
  try {
    const res = await $common.get("/ods/getEtlGroup");
    const data = Array.isArray(res) ? res : [];
    eltCategoryOptions.value = data;
    // 强制更新表单的 field 数据
    await nextTick();
    formRef.value?.updateFieldOptions("taskTypeId", data);
  } catch (error) {
    console.error("获取ETL任务分类失败:", error);
    ElMessage.error("获取ETL任务分类失败");
  }
};

const onCancel = (): void => {
  emit("close", false);
  open.value = false;
};

const onToSee = () => {
  emit("close", true);
  open.value = false;
};

// 获取建表后去接入时的信息
const fetchAccessInfo = async (id?: string) => {
  if (!id) return;
  try {
    loading.value = true;
    $common
      .post("/ods/task/autoMappingTemplate", {
        tid: id,
      })
      .then((res: any) => {
        basicInfo.value = res;
      });
    loading.value = false;
  } catch (error) {
    console.error("获取接入信息失败:", error);
    loading.value = false;
  }
};

// 处理自动映射
const handleAutoMapping = async () => {
  try {
    // 检查数据库表配置是否完成
    await databaseConfigRef.value?.getConfig();
    const sourceInfo = await databaseConfigRef.value?.getSourceFormData();
    const targetInfo = await databaseConfigRef.value?.getTargetFormData();
    const info = (await $common.post("/ods/task/columnAutoMapping", {
      sourceTableId: sourceInfo.sourceTableId,
      targetTableId: targetInfo.targetTableId,
    })) as any;
    if (!info || !Array.isArray(info)) {
      ElMessage.error("自动映射失败");
      return;
    }
    basicInfo.value.columnMapping = info;
    ElMessage.success("字段自动映射完成");
  } catch (error) {
    console.error("自动映射失败:", error);
    ElMessage.error("自动映射失败");
  }
};

// 保存表单数据
const handleSave = async () => {
  try {
    await formRef.value?.validate();
    isSaveing.value = true;
    const databaseConfig = await databaseConfigRef.value?.getConfig();
    const fieldMapping = await fieldMappingRef.value?.fieldMappingData;
    const scheduleConfig = await scheduleConfigRef.value?.getConfig();
    const formData = await formRef.value.getFormData();
    await $common.post("/ods/task/saveOrUpdate", {
      ...formData,
      ...databaseConfig,
      ...scheduleConfig,
      columnMapping: fieldMapping,
    });

    // 保存成功后设置保存状态为 true
    isSaved.value = true;
    isSaveing.value = false;
    ElMessage.success("保存成功");
  } catch (error) {
    isSaved.value = false;
    isSaveing.value = false;
    console.error("表单校验失败:", error);
    ElMessage.error("保存失败，请检查表单");
  }
};

// 创建任务
const handleCreateTask = async () => {
  // 检查是否已经保存
  if (!isSaved.value) {
    ElMessage.warning("请先保存配置信息");
    return;
  }

  try {
    // 模拟创建任务的过程
    taskLoading.value = true;
    await new Promise((resolve) => setTimeout(resolve, 1500));
    isFinish.value = true;
    ElMessage.success("任务创建成功");
  } catch (error) {
    console.error("创建任务失败:", error);
    ElMessage.error("创建任务失败，请重试");
  } finally {
    taskLoading.value = false;
  }
};

watch(
  open,
  (val: boolean) => {
    if (val) {
      // 模态框打开时重置保存状态
      isSaved.value = false;
      // 打开弹窗时加载ETL任务分类数据
      fetchEltCategoryOptions();
      fetchAccessInfo(props.id);
    }
  },
  { immediate: true }
);

// 监听 databaseConfig 的目标字段选项变化，实时更新给 fieldMapping
watch(
  () => databaseConfigRef.value?.targetFieldOptions,
  (newOptions) => {
    if (Array.isArray(newOptions) && newOptions.length > 0) {
      targetFieldOptions.value = newOptions;
    }
  },
  { immediate: true, deep: true }
);
</script>

<style lang="scss">
.access-modal__container {
  padding: 0;
  .el-dialog__body {
    height: 100%;
    padding: 0;
  }
  .el-dialog__header {
    padding: 0;
  }
  .access-modal__body {
    padding: 0.5rem 1.25rem;
  }
  .access-modal__header {
    display: grid;
    flex-shrink: 0;
    grid-template-columns: 15% 1fr 15%;
    align-items: center;
    height: 45px;
    padding: 0 15px 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #ebeef5;
  }
  .access-modal__title {
    font-family: Arial, sans-serif;
    font-size: 16px;
    font-weight: 700;
    color: #000000e0;
  }
  .access-modal__footer {
    display: flex;
    justify-content: center;
    padding: 10px 24px;
    text-align: right;
    background-color: #fff;
    border-top: 1px solid #ebeef5;
  }
  .mapping-title {
    & .title {
      line-height: 32px !important;
    }
  }
}
</style>
