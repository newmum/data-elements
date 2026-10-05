<template>
  <div class="kb-preview-word">
    <VueOfficeDocx
      v-if="props.src"
      :src="props.src"
      style="width: 100%; height: 100%"
      @error="handleError"
    />
  </div>
</template>

<script setup lang="ts">
import VueOfficeDocx from "@vue-office/docx";
import "@vue-office/docx/lib/index.css";

const props = defineProps<{
  src: string;
}>();

const handleError = (err: any) => {
  console.error("Word 预览失败:", err);
};
</script>

<style scoped lang="scss">
.kb-preview-word {
  width: 100%;
  height: 100%;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
}
:deep(.vue-office-docx) {
  min-width: 880px;
  overflow: visible !important;
}

:deep(.docx-wrapper) {
  min-width: 880px;
}
</style>
