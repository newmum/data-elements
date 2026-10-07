<template>
  <ServiceDialogWrapper
    v-model="open"
    title="服务登记"
    :is-finish="isFinish"
    :finish-name="registeredFormData.serviceName || registeredFormData.name || ''"
    :finish-time="registeredFormData.latestUpdateTime || ''"
    @close="onCancel"
    @to-see="handleToSee"
  >
    <ApiRegisterContent ref="contentRef" :data="apiDetail" context="modal" />

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :disabled="taskLoading" @click="handleSubmit">
        <icon :icon="'check-circle'" class="mr-2" />
        提交登记
      </el-button>
    </template>
  </ServiceDialogWrapper>
</template>

<script setup lang="ts">
import { ref, watch, computed } from "vue";
// import ApiRegisterContent from "./components/ApiRegisterContent.vue";
// import ServiceDialogWrapper from "./components/ServiceDialogWrapper.vue";

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: "cancel" | "success"): void;
}>();

const props = defineProps({
  modelValue: Boolean,
  apiDetail: Object as () => any,
});

const contentRef = ref();
const taskLoading = ref(false);
const isFinish = ref(false);
const registeredFormData = ref<any>({});

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

const onCancel = (): void => {
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
      registeredFormData.value = (await contentRef.value?.getFormData()) || {};
      isFinish.value = true;
    }
  } finally {
    taskLoading.value = false;
  }
};

const fetchFormData = async () => {
  if (props.apiDetail?.serviceBuildId) {
    $common
      .post("/dataassets/manage/assets/queryById", {
        tid: props.apiDetail.serviceBuildId,
      })
      .then((res) => {
        registeredFormData.value = res || {};
      });
  }
};

watch(
  open,
  (val: boolean) => {
    if (val) {
      isFinish.value = false;
      registeredFormData.value = {};
      contentRef.value?.reset();
      fetchFormData();
    }
  },
  { immediate: true }
);
</script>
