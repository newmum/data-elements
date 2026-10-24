<template>
  <section class="table-field-detail">
    <register-dbTable-catalog v-if="tableId" :key="tableId" :detail-table-id="tableId" />
  </section>
</template>

<script setup>
defineProps({ tableId: { type: String, required: true } });
</script>

<style scoped>
.table-field-detail {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 16px 28px 16px;
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
}

@media (max-width: 1280px) {
  .table-field-detail {
    padding: 12px 20px 16px;
  }
}
</style>
