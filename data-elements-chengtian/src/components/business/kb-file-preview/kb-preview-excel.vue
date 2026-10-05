<template>
  <div ref="containerRef" class="kb-preview-excel">
    <VueOfficeExcel
      v-if="props.src"
      :key="renderKey"
      :src="props.src"
      :options="excelOptions"
      style="width: 100%; height: 100%"
      @error="handleError"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import VueOfficeExcel from "@vue-office/excel";
import "@vue-office/excel/lib/index.css";

const props = defineProps<{
  src: string;
}>();

const containerRef = ref<HTMLElement | null>(null);
const renderKey = ref(0);
let resizeObserver: ResizeObserver | null = null;
let resizeTimeout: number | null = null;

const excelOptions = {
  fit: false,
  scrollX: true,
  scrollY: true,
};

const handleError = (err: any) => {
  console.error("Excel 预览失败:", err);
};

const handleResize = () => {
  if (resizeTimeout) {
    clearTimeout(resizeTimeout);
  }
  resizeTimeout = window.setTimeout(() => {
    renderKey.value++;
  }, 200);
};

onMounted(() => {
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(handleResize);
    resizeObserver.observe(containerRef.value);
  }
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (resizeTimeout) {
    clearTimeout(resizeTimeout);
    resizeTimeout = null;
  }
});
</script>

<style scoped lang="scss">
.kb-preview-excel {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

:deep(.vue-office-excel) {
  width: 100% !important;
  height: 100% !important;
}

:deep(.vue-office-excel > div),
:deep(.x-spreadsheet) {
  width: 100% !important;
  height: 100% !important;
}

:deep(.x-spreadsheet-container) {
  width: 100% !important;
  height: 100% !important;
}
</style>
