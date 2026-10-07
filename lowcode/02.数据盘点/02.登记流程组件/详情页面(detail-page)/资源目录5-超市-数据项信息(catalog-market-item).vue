<template>
  <div class="card-container pt-5 px-5 pb-2">
    <data-table
      show-index
      flex-type="flex-[0_1_auto]"
      :show-page="false"
      :columns="state.cols"
      :data="state.data"
      :loading="state.loading"
      size="large"
    >
      <template #column-colName="{ row }">
        <Icon
          v-if="row.isPk === '1'"
          style="color: var(--el-color-warning); transform: rotate(35deg)"
          icon="el-icon-key"
        />
        {{ row.colName }}
      </template>
      <template #column-isPk="{ row }">
        <Icon v-if="row.isPk === '1'" icon="yes" />
      </template>
      <template #column-isNullable="{ row }">
        <Icon v-if="row.isNullable !== '1'" icon="yes" />
      </template>
    </data-table>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from "vue";

const props = defineProps({
  catalogId: {
    type: String,
    required: true,
  },
});

const state = reactive<any>({ loading: false, cols: [], data: [] });

state.cols = [
  { label: "数据项名称", prop: "colName" },
  { label: "数据项英文名称", prop: "colEn" },
  { label: "数据项类型", prop: "colType" },
  { label: "长度", prop: "colLength", maxWidth: 200 },
  { label: "主键", prop: "isPk", maxWidth: 120 },
  { label: "非空", prop: "isNullable", maxWidth: 120 },
];

onMounted(() => {
  init();
});

const init = async () => {
  if (!props.catalogId) return;
  state.loading = true;
  try {
    const data = await $common.post("/dst/catalog/catalog-items/list", {
      tid: props.catalogId,
    });
    state.data = data || [];
  } catch (error) {
    console.error("获取catalog数据项失败", error);
  } finally {
    state.loading = false;
  }
};
</script>

<style scoped lang="scss"></style>
