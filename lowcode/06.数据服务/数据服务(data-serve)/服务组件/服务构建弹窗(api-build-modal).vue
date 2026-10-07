<template>
  <ServiceDialogWrapper
    v-model="open"
    title="服务构建"
    :is-finish="isFinish"
    :finish-name="buildFormData.serviceName || ''"
    :finish-time="buildFormData.buildTime || ''"
    @close="onCancel"
    @to-see="handleToSee"
  >
    <ApiBuildContent ref="contentRef" context="modal" :data="apiDetail" />

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :disabled="taskLoading" @click="handleSubmit">
        <icon :icon="'check-circle'" class="mr-2" />
        提交构建
      </el-button>
    </template>
  </ServiceDialogWrapper>
</template>

<script setup lang="ts">
import { ref, watch, computed } from "vue";
// import ApiBuildContent from "./components/ApiBuildContent.vue";
// import ServiceDialogWrapper from "./components/ServiceDialogWrapper.vue";

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: "cancel" | "success"): void;
}>();

const props = defineProps({
  modelValue: Boolean,
  apiDetail: Object as () => any,
});

const contentRef = ref<any>(null);
const taskLoading = ref(false);
const isFinish = ref(false);
const buildFormData = ref<any>({});

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

const onCancel = () => {
  emit("close", "cancel");
  open.value = false;
};

const handleToSee = () => {
  open.value = false;
  emit("close", "success");
};

const handleSubmit = async () => {
  taskLoading.value = true;
  try {
    const ok = await contentRef.value?.handleSave();
    if (ok) {
      buildFormData.value = (await contentRef.value?.getFormData()) || {};
      isFinish.value = true;
    }
  } finally {
    taskLoading.value = false;
  }
};

watch(
  open,
  (val: boolean) => {
    if (val) {
      isFinish.value = false;
      buildFormData.value = {};
      contentRef.value?.reset();
    }
  },
  { immediate: true }
);
</script>
