<template>
  <div class="kb-preview-pdf">
    <VueOfficePdf
      v-if="props.src"
      :src="props.src"
      style="width: 100%; height: 100%"
      @error="handleError"
    />
  </div>
</template>

<script setup lang="ts">
import VueOfficePdf from "@vue-office/pdf";

const props = defineProps<{
  src: string;
}>();

const handleError = (err: any) => {
  console.error("PDF 预览失败:", err);
};
</script>

<style scoped lang="scss">
.kb-preview-pdf {
  width: 100%;
  height: 100%;
  margin: 0 auto;
}

:deep(.vue-office-pdf) {
  min-width: 800px;
}
</style>
