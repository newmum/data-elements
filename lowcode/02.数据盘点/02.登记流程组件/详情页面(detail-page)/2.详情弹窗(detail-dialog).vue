<template>
  <el-dialog
    v-model="visible"
    :title="props.detailData?.name || props.detailData?.title"
    fullscreen
    destroy-on-close
    :close-on-click-modal="false"
    class="detail-dialog__container"
    @opened="onOpened"
    @close="handleClose"
  >
    <detail-page
      v-if="renderPage"
      :id="props.detailData.id"
      :type="props.detailData.type"
      :data-source-type="props.detailData.dataSourceType"
      :is-breadcrumbs="false"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useDetailStore } from "@/store";
// import DetailPage from "./detail-page.vue";

interface Props {
  modelValue: boolean;
  detailData?: Record<string, any>;
}

const props = withDefaults(defineProps<Props>(), {
  detailData: () => ({
    name: "",
    id: "",
    type: "catalog",
    dataSourceType: "",
  }),
});

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  close: [];
}>();

const detailStore = useDetailStore();

// 控制 detail-page 是否渲染（延迟到 opened 后，确保快照先于 setup 执行）
const renderPage = ref(false);

// 快照：保存打开 dialog 前的 detailStore 状态
let snapshot: {
  data: any;
  state: any;
  history: any;
  config: any;
  currentTab: string;
} | null = null;

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

// dialog 打开动画结束后触发，此时 detail-page 尚未渲染，可以安全快照
const onOpened = () => {
  snapshot = {
    data: JSON.parse(JSON.stringify(detailStore.data)),
    state: JSON.parse(JSON.stringify(detailStore.state)),
    history: JSON.parse(JSON.stringify(detailStore.history)),
    config: JSON.parse(JSON.stringify(detailStore.config)),
    currentTab: detailStore.currentTab,
  };
  renderPage.value = true;
};

const handleClose = () => {
  visible.value = false;
  renderPage.value = false;
  // 恢复快照，避免污染原页面的 detailStore
  if (snapshot) {
    detailStore.data = snapshot.data;
    detailStore.state = snapshot.state;
    detailStore.history = snapshot.history;
    detailStore.config = snapshot.config;
    detailStore.currentTab = snapshot.currentTab;
    snapshot = null;
  }
  emit("close");
};
</script>

<style lang="scss">
.detail-dialog__container {
  padding: 0;
  overflow: hidden !important;
  .el-dialog__header {
    height: 55px;
    padding: 15px 15px 0 20px;
    background-color: #fff;
    border-bottom: var(--border);
  }
  .el-dialog__body {
    height: calc(100% - 55px);
    padding: 15px 0 0 15px;
    overflow: hidden !important;
  }
}
</style>
