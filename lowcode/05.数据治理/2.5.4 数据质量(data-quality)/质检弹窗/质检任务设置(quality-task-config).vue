<template>
  <div class="quality-task-config px-5 py-2">
    <JsonForm ref="formRef" bordered :rules="formRules" />
    <u-title name="执行调度策略" class="mt-4" />
    <schedule-config ref="scheduleConfigRef" :info="basicInfo"></schedule-config>
  </div>
</template>

<script setup lang="ts">
import { ref, inject } from "vue";
import { FormUtils } from "@/utils/form";
// import scheduleConfig from "./scheduleConfig.vue";

const formRef = ref();
const formRules = ref([]);
const basicInfo = ref({
  scheduleMode: "realtime",
});
const scheduleConfigRef = ref();
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
    field: "name",
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "select",
    title: "运行资源组",
    field: "resourceGroup",
    options: [
      { label: "默认Spark集群", value: "spark" },
      { label: "流式计算引擎", value: "flink" },
      { label: "轻量Python节点", value: "py" },
    ],
    props: {
      type: "select",
    },
    col: {
      span: 12,
    },
  },
  {
    type: "input",
    field: "description",
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

// 保存表单数据
const save = async () => {
  try {
    await formRef.value?.validate();
    const formData = await formRef.value?.getFormData();
    const scheduleConfig = await scheduleConfigRef.value?.getConfig();
    $message.success("保存成功");
    console.log("执行调度策略:", scheduleConfig);
    console.log("表单数据:", formData);
  } catch (error) {
    console.error("表单校验失败:", error);
  }
};

const getActionRef = inject("getActionRef", ref(undefined));
const commit = async () => {
  const isValidate = await formRef.value?.validate();
  const isValidateSchedule = await scheduleConfigRef.value?.getConfig();
  if (!isValidateSchedule || !isValidate) {
    throw "执行调度策略验证不通过";
  }
  $dialog
    .confirm("是否提交配置?", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    })
    .then(() => {
      $message.success("操作成功");
      getActionRef.value.action("next");
    })
    .catch(() => {});
};
defineExpose({
  save,
  commit,
});
</script>

<style scoped lang="scss">
.quality-task-config {
  background-color: #fff;
}
</style>
