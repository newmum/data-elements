<template>
  <div class="case-header-card">
    <div class="title" :class="titleClass">
      <slot name="title">{{ title }}</slot>
    </div>

    <!--  操作  -->
    <div class="action">
      <slot name="action"></slot>
    </div>

    <!--  信息字段  -->
    <div class="info-row">
      <slot name="info">
        <template v-for="(item, index) in items" :key="index">
          <div class="info-item">
            <div class="label flex-y-center">
              <Icon v-if="item.icon" :icon="item.icon" size="28px" />
              {{ item.label }}:
            </div>
            <span>
              {{ item.value }}
            </span>
          </div>
        </template>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; items: any[]; titleClass: string }>();
</script>

<style lang="scss" scoped>
.case-header-card {
  position: relative;
  padding: 16px 16px 20px 16px;
  background: linear-gradient(357.37deg, #ffffff 63.91%, #e3e9f9 90.95%);
  border-radius: 8px;
  .title {
    position: absolute;
    top: -24px;
    left: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 287px;
    height: 40px;
    padding: 0 40px;
    font-size: 22px;
    color: #fff;
    text-align: center;
    background-image: url("@/assets/images/19/card-header2.svg");
    background-repeat: no-repeat;
    background-size: 100% auto;
  }

  .action {
    display: flex;
    justify-content: flex-end;
  }
}

.info-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px 8px;
  width: 80%;
  margin-top: 12px;
  margin-left: 24px;
}

.info-item {
  display: flex;
  align-items: center;
}

.label {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  margin-right: 8px;
  color: #606266;
}
</style>
