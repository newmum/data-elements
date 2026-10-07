<template>
  <div class="database-config">
    <div class="database-config-container">
      <!-- 左侧：源数据库 -->
      <div class="source-database images-workplace-bg2">
        <div class="header">
          <div class="header-left">
            <div class="icon-wrapper source-icon">
              <Icon icon="database-network" size="22" />
            </div>
            <h3>源数据信息 (Source)</h3>
          </div>
          <el-button type="text" class="detail-btn" @click="handleShowDrawer('source')">
            详情
          </el-button>
        </div>

        <JsonForm ref="sourceFormRef" :rules="sourceFormRules" :options="formOptions">
          <template #field-sourceDbId="scope">
            <div class="flex items-center w-full gap-2">
              <dict-select
                v-model="scope.model.value"
                :options="dbOptions"
                :disabled="!props.edit"
                style="flex: 1"
                placeholder="请选择数据库"
                @change="handleSourceDatabaseChange"
              ></dict-select>
              <el-button type="primary" plain @click="testConnection(scope.model.value)">
                <Icon icon="shandian" class="mr-2"></Icon>
                连通性
              </el-button>
            </div>
          </template>
        </JsonForm>
      </div>

      <!-- 中间：动效连线 -->
      <div class="connection-line">
        <div class="line-bg"></div>
        <div class="line-animation">
          <div class="arrow-container">
            <div class="gradient-line"></div>
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M9 18l6-6-6-6"
                stroke="#165DFF"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>
        </div>
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          class="chevron-icon"
        >
          <path
            d="M9 18l6-6-6-6"
            stroke="#165DFF"
            stroke-opacity="0.2"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>

      <!-- 右侧：目标数据库 -->
      <div class="target-database images-workplace-bg3">
        <div class="header">
          <div class="header-left">
            <div class="icon-wrapper target-icon">
              <Icon icon="data-switching" size="22" />
            </div>
            <h3>目标数据信息 (Target)</h3>
          </div>
          <el-button type="text" class="detail-btn" @click="handleShowDrawer('target')">
            详情
          </el-button>
        </div>

        <JsonForm ref="targetFormRef" :rules="targetFormRules" :options="formOptions">
          <template #field-targetDbId="scope">
            <div class="flex items-center w-full gap-2">
              <dict-select
                v-model="scope.model.value"
                :options="dbOptions"
                :disabled="!props.edit"
                style="flex: 1"
                placeholder="请选择数据库"
                @change="handleTargetDatabaseChange"
              />
              <el-button type="primary" plain @click="testConnection(scope.model.value)">
                <Icon icon="shandian" class="mr-2"></Icon>
                连通性
              </el-button>
            </div>
          </template>
        </JsonForm>
      </div>
    </div>
    <detail-drawer v-if="drawerVisible" :title="drawerTitle" :tid="tid" @close="closeDrawer" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
// import detailDrawer from "./detail-drawer.vue";

const props = defineProps({
  info: {
    type: Object,
    default: () => ({}),
  },
  edit: {
    type: Boolean,
    default: true,
  },
  // dbOptions: {
  //   type: Array,
  //   default: () => [],
  // },
});
const sourceFormRef = ref();
const targetFormRef = ref();
const drawerVisible = ref(false);
const drawerTitle = ref("");
const tid = ref("");
const dbOptions = ref<any>([]);
// 目标字段选项 - 用于传递给 fieldMapping 组件
const targetFieldOptions = ref<any[]>([]);
const formOptions = {
  form: {
    labelPosition: "top",
  },
};
// 源数据表options
const sourceFormRules = FormUtils.fixJson([
  {
    type: "fieldComponent",
    title: "选择数据库",
    field: "sourceDbId",
    $required: true,
  },
  {
    type: "select",
    field: "sourceTableId",
    title: "选择数据表",
    $required: true,
    props: {
      placeholder: "请选择数据表",
      filterable: true,
      options: [],
      disabled: !props.edit,
    },
    on: {
      change: (value: string) => {
        updateFieldsById(value, "source");
      },
    },
  },
  {
    type: "select",
    field: "sourceTablePrimaryKey",
    title: "主键字段",
    $required: true,
    props: {
      placeholder: "请选择主键字段",
      filterable: true,
      options: [],
    },
  },
  {
    type: "select",
    field: "sourceTableIncrementKey",
    title: "增量提取字段",
    $required: true,
    props: {
      placeholder: "请选择增量提取字段",
      filterable: true,
      options: [],
    },
  },
]);
// 目标数据库form rules
const targetFormRules = FormUtils.fixJson([
  {
    type: "fieldComponent",
    field: "targetDbId",
    title: "选择数据库",
    $required: true,
  },
  {
    type: "select",
    field: "targetTableId",
    title: "选择数据表",
    $required: true,
    props: {
      disabled: !props.edit,
    },
    on: {
      change: (value: string) => {
        updateFieldsById(value, "target");
      },
    },
  },
  {
    type: "select",
    field: "targetTablePrimaryKey",
    title: "主键字段",
    $required: true,
    props: {
      placeholder: "请选择主键",
      filterable: true,
      options: [],
    },
  },
]);

// 更新数据库下拉数据
const updateDbData = async () => {
  try {
    const res: any = await $common.post("/dst/catalog/list", {
      assetType: "db",
    });

    if (!res || !Array.isArray(res)) {
      console.warn("获取数据库列表返回数据格式不正确:", res);
      return;
    }

    const options = res.map((item: any) => ({
      label: item.dbName,
      value: item.id,
      icon: item.dbType?.toLowerCase(),
    }));

    // 等待表单初始化完成后再更新选项
    await nextTick();

    dbOptions.value = [...options];
    sourceFormRef.value?.updateFieldOptions("sourceDbId", options);
    targetFormRef.value?.updateFieldOptions("targetDbId", options);
  } catch (error) {
    console.error("更新数据库下拉数据失败:", error);
  }
};

// 更新表数据_by数据库ID
const updateTableDataById = (dbId: string, type: "source" | "target") => {
  $common.get("/dst/database/metadata/tables?dbId=" + dbId).then((res: any) => {
    const options = res.map((item: any) => ({
      label: item.tableName,
      value: item.id,
    }));
    if (type === "source") {
      sourceFormRef.value?.updateFieldOptions("sourceTableId", options);
    } else {
      targetFormRef.value?.updateFieldOptions("targetTableId", options);
    }
  });
};

// 更新字段数据_by表ID
const updateFieldsById = (tableId: string, type: "source" | "target") => {
  $common.get("/dst/database/metadata/columns?tid=" + tableId).then((res: any) => {
    // 字段选项 - 用于表单下拉框
    const fieldOptions = res || [];
    const options = fieldOptions.map((item: any) => ({
      label: item.columnName,
      value: item.tid,
    }));

    if (type === "source") {
      sourceFormRef.value?.updateFieldOptions("sourceTablePrimaryKey", options);
      sourceFormRef.value?.updateFieldOptions("sourceTableIncrementKey", options);
    } else {
      targetFormRef.value?.updateFieldOptions("targetTablePrimaryKey", options);
      // 存储目标表字段信息，用于传递给 fieldMapping 组件
      targetFieldOptions.value = fieldOptions;
    }
  });
};

const handleShowDrawer = async (type: string) => {
  if (type === "source") {
    const form = await sourceFormRef.value?.formData();
    if (!form.sourceDbId) {
      ElMessage.error("请选择数据库");
      return;
    }
    drawerVisible.value = true;
    drawerTitle.value = "源数据详情";
    tid.value = form.sourceDbId;
  }
  if (type === "target") {
    const form = await targetFormRef.value?.formData();
    if (!form.targetDbId) {
      ElMessage.error("请选择数据库");
      return;
    }
    drawerVisible.value = true;
    drawerTitle.value = "目标数据详情";
    tid.value = form.targetDbId;
  }
};

const closeDrawer = () => {
  drawerVisible.value = false;
  drawerTitle.value = "";
  tid.value = "";
};

// 处理数据源类型变化
const handleSourceDatabaseChange = async (value: string) => {
  try {
    sourceFormRef.value?.setValue({
      sourceDbId: value,
    });
    updateTableDataById(value, "source");
  } catch (error) {
    console.error("加载数据表信息失败:", error);
    ElMessage.error("加载数据表信息失败");
  }
};

// 处理目标数据库类型变化
const handleTargetDatabaseChange = async (value: string) => {
  try {
    targetFormRef.value?.setValue({
      targetDbId: value,
    });
    updateTableDataById(value, "target");
  } catch (error) {
    console.error("加载数据表信息失败:", error);
    ElMessage.error("加载数据表信息失败");
  }
};

// 测试连接
const testConnection = async (id: string) => {
  try {
    if (!id) {
      ElMessage.error("请选择数据库");
      return;
    }
    const res = await $common.post("/dst/database/metadata/test-connection", {
      tid: id,
    });
    const data = res.data || res;
    if (data.connected) {
      ElMessage.success("连通成功");
    } else {
      ElMessage.error("连通失败");
    }
  } catch (error) {
    console.error("连通失败:", error);
    ElMessage.error("连通失败");
  }
};

// Expose form methods
const validateSourceForm = async () => {
  return await sourceFormRef.value?.validate();
};

const validateTargetForm = async () => {
  return await targetFormRef.value?.validate();
};

const getSourceFormData = () => {
  return sourceFormRef.value?.formData();
};

const getTargetFormData = () => {
  return targetFormRef.value?.formData();
};

const updateFormData = (data: any) => {
  sourceFormRef.value?.setValue(data);
  targetFormRef.value?.setValue(data);
  if (props.info.sourceDbId && props.info.targetDbId) {
    updateTableDataById(props.info.sourceDbId, "source");
    updateTableDataById(props.info.targetDbId, "target");
  }
  if (props.info.sourceTableId) {
    updateFieldsById(props.info.sourceTableId, "source");
  }
  if (props.info.targetTableId) {
    updateFieldsById(props.info.targetTableId, "target");
  }
};

const getConfig = async () => {
  try {
    await Promise.all([validateSourceForm(), validateTargetForm()]);
    return {
      ...getSourceFormData(),
      ...getTargetFormData(),
    };
  } catch (error) {
    console.log("数据库配置校验失败:", error);
    throw new Error("数据库配置校验失败，请完善必填项");
  }
};

watch(
  () => props.info,
  (newInfo) => {
    if (newInfo) {
      updateFormData(newInfo);
    }
  },
  { immediate: true }
);

onMounted(() => {
  updateDbData();
  updateFormData(props.info);
});

defineExpose({
  validateSourceForm,
  validateTargetForm,
  getSourceFormData,
  getTargetFormData,
  getConfig,
  targetFieldOptions,
});
</script>

<style lang="scss" scoped>
.database-config {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  // animation: fadeIn 0.5s ease-in-out;

  .database-config-container {
    width: 100%;
    display: flex;
    align-items: stretch;
    position: relative;
    z-index: 10;
  }

  // Source and Target database common styles
  .source-database,
  .target-database {
    flex: 1;
    background-size: 100% 260px;
    border: 1px solid #e2e8f0;
    border-radius: 0.75rem;
    box-shadow:
      0 4px 6px -1px rgba(0, 0, 0, 0.1),
      0 2px 4px -1px rgba(0, 0, 0, 0.06);
    padding: 1.5rem;
    position: relative;
    overflow: hidden;
  }

  // Source database specific styles
  .source-database {
    &::before {
      content: "";
      position: absolute;
      top: 0;
      left: 0;
      width: 1.5px;
      height: 100%;
      background: rgba(22, 93, 255, 0.8);
    }
  }

  // Target database specific styles
  .target-database {
    &::before {
      content: "";
      position: absolute;
      top: 0;
      right: 0;
      width: 1.5px;
      height: 100%;
      background: #bb90ff;
    }
  }

  // Header styles
  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 0.75rem;
    margin-bottom: 1.5rem;

    .header-left {
      display: flex;
      align-items: center;
      gap: 0.75rem;

      .icon-wrapper {
        width: 2rem;
        height: 2rem;
        border-radius: 0.5rem;
        display: flex;
        align-items: center;
        justify-content: center;

        &.source-icon {
          background: linear-gradient(111.86deg, #4da0f4 0%, #376cfd 100%);
          color: #fff;
        }

        &.target-icon {
          background: linear-gradient(111.86deg, #bb90ff 0%, #6e66d1 100%);
          color: #fff;
        }
      }

      h3 {
        font-size: 0.9375rem;
        font-weight: 600;
        color: #1e293b;
      }
    }
  }

  // Connection line styles
  .connection-line {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 6rem;
    position: relative;

    .line-bg {
      position: absolute;
      top: 50%;
      left: 0;
      right: 0;
      height: 2px;
      background: #f1f5f9;
      transform: translateY(-50%);
      z-index: 0;
    }

    .line-animation {
      position: absolute;
      top: 50%;
      left: -0.25rem;
      right: -0.25rem;
      height: 20px;
      transform: translateY(-50%);
      z-index: 10;
      overflow: hidden;
      pointer-events: none;

      .arrow-container {
        position: absolute;
        top: 0;
        bottom: 0;
        left: -150px;
        width: 300px;
        display: flex;
        align-items: center;
        justify-content: center;
        animation: flowArrowTrack 2.5s linear infinite;

        .gradient-line {
          height: 2px;
          width: 24px;
          background: linear-gradient(to right, transparent, #165dff, #165dff);
          opacity: 0.4;
        }

        svg {
          margin-left: -0.5rem;
          filter: drop-shadow(0 0 5px rgba(22, 93, 255, 0.5));
        }
      }
    }

    .chevron-icon {
      position: absolute;
      top: 50%;
      right: -0.35rem;
      transform: translateY(-50%);
    }
  }
}

// Animations
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes flowArrowTrack {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(350px);
  }
}
</style>
