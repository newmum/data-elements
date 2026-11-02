<template>
  <section class="access-onboarding" :aria-label="title">
    <div class="access-onboarding-panel">
      <header class="access-onboarding-heading">
        <h3>{{ title }}</h3>
        <span v-if="badge" class="access-onboarding-badge">{{ badge }}</span>
        <div v-if="$slots.default" class="access-onboarding-toolbar"><slot /></div>
      </header>
      <p v-if="description" class="access-onboarding-description">{{ description }}</p>
      <ol class="access-onboarding-steps">
        <li v-for="(step, index) in steps" :key="step.title" :class="`step-tone-${index % 3}`">
          <div class="access-onboarding-illustration" aria-hidden="true">
            <span class="access-onboarding-number">{{ String(index + 1).padStart(2, "0") }}</span>
            <span class="access-onboarding-symbol">
              <svg v-if="step.paths?.length" viewBox="0 0 64 64">
                <path v-for="(path, pathIndex) in step.paths" :key="pathIndex" :d="path" />
              </svg>
              <Icon v-else :icon="step.icon || 'el-icon-Document'" :size="34" />
            </span>
            <span v-if="index < steps.length - 1" class="access-onboarding-arrow">→</span>
          </div>
          <span class="access-onboarding-step">第 {{ index + 1 }} 步</span>
          <h4>{{ step.title }}</h4>
          <p>{{ step.description }}</p>
        </li>
      </ol>
      <footer v-if="hint || $slots.status" class="access-onboarding-footer">
        <p v-if="hint" class="access-onboarding-hint">
          <Icon icon="el-icon-Right" :size="15" />
          {{ hint }}
        </p>
        <div v-if="$slots.status" class="access-onboarding-status"><slot name="status" /></div>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  badge?: string;
  description?: string;
  hint?: string;
  steps: readonly {
    title: string;
    description: string;
    icon?: string;
    paths?: readonly string[];
  }[];
}>();
</script>

<style scoped>
.access-onboarding {
  width: 100%;
  min-width: 0;
  color: #233b5f;
  text-align: left;
}
.access-onboarding-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  max-width: 100%;
  margin-left: auto;
}
.access-onboarding-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.access-onboarding-panel {
  container-type: inline-size;
  padding: 24px;
  border: 1px solid #e9eff8;
  border-radius: 10px;
  background:
    radial-gradient(ellipse at 9% 0%, #e5efff 0%, transparent 48%),
    radial-gradient(ellipse at 100% 0%, #e5f5f4 0%, transparent 42%), #f8fbff;
}
.access-onboarding-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin-bottom: 18px;
}
.access-onboarding-heading h3 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.45;
}
.access-onboarding-badge {
  flex-shrink: 0;
  padding: 4px 10px;
  border: 1px solid #dce8fb;
  border-radius: 5px;
  background: #eef5ff;
  color: #47638a;
  font-size: 13px;
  line-height: 1.4;
}
.access-onboarding-description {
  margin: -8px 0 18px;
  color: #62738b;
  font-size: 14px;
  line-height: 1.7;
}
.access-onboarding-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.access-onboarding-steps li {
  min-width: 0;
  padding: 14px 16px 18px;
  border: 1px solid #e2eaf5;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 3px 12px #496a9b04;
  color: #2868d9;
}
.access-onboarding-steps .step-tone-1 {
  color: #7353cf;
}
.access-onboarding-steps .step-tone-2 {
  color: #087f70;
}
.access-onboarding-illustration {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  height: 68px;
}
.access-onboarding-number {
  color: #e5ecf8;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -1px;
  font-variant-numeric: tabular-nums;
}
.access-onboarding-symbol {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66px;
  height: 66px;
  border-radius: 15px;
  background: #f5f8ff;
}
.access-onboarding-symbol svg {
  width: 60px;
  height: 60px;
  padding: 3px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.access-onboarding-arrow {
  position: absolute;
  top: 18px;
  right: -29px;
  z-index: 1;
  color: #a4bce0;
  font-size: 26px;
  line-height: 1;
}
.access-onboarding-step {
  display: block;
  margin: 11px 0 5px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}
.access-onboarding-steps h4 {
  margin: 0;
  color: #233b5f;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.6;
}
.access-onboarding-steps p {
  margin: 6px 0 0;
  color: #62738b;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.access-onboarding-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 18px;
}
.access-onboarding-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1 1 260px;
  margin: 0;
  color: #62738b;
  font-size: 14px;
  line-height: 1.7;
}
.access-onboarding-hint :deep(.iconify) {
  flex-shrink: 0;
  margin-top: 0;
}
.access-onboarding-status {
  flex: 1 1 100%;
  min-width: 0;
}
.access-onboarding-status :slotted(.guide-import-result) {
  margin-top: 0;
}
@container (max-width: 660px) {
  .access-onboarding-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .access-onboarding-steps {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
  .access-onboarding-steps li {
    position: relative;
    min-height: 110px;
    padding: 14px 16px 14px 96px;
  }
  .access-onboarding-illustration {
    position: absolute;
    top: 20px;
    left: 14px;
    width: 66px;
  }
  .access-onboarding-number,
  .access-onboarding-arrow {
    display: none;
  }
  .access-onboarding-step {
    margin-top: 0;
  }
}
</style>
