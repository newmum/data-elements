<template>
  <el-drawer
    v-model="visible"
    :title="props.title"
    :size="1000"
    :direction="'rtl'"
    :before-close="onClose"
    custom-class="table-detail-drawer"
  >
    <!-- 抽屉内容区 -->
    <detail-page :type="detailType" :id="tid" :is-breadcrumbs="false" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  tid: {
    type: String,
  },
  detailType: {
    type: String,
  }
});

const emit = defineEmits(["close"]);

const visible = ref(true);

const onClose = () => {
  visible.value = false;
  emit("close");
};
</script>

<style lang="scss"></style>
