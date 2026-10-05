<template>
  <div class="login-container">
    <div class="visual-area">
      <div class="brand-card">
        <div class="brand-logo" @click="handleLogoClick">
          <el-image v-if="settingsStore.logo" :src="settingsStore.logo" fit="contain" />
          <span v-else>数</span>
        </div>
        <div>
          <h1>{{ settingsStore.systemName || "数据要素操作平台" }}</h1>
          <p>数据资源登记、自动接入数据、快速构建服务的一体化平台</p>
        </div>
      </div>

      <div class="metrics-panel">
        <div class="metric-item is-inventory">
          <div class="metric-icon">
            <DataAnalysis />
          </div>
          <div class="metric-copy">
            <strong>数据盘点</strong>
            <span>资源统一登记</span>
          </div>
        </div>
        <div class="metric-item is-access">
          <div class="metric-icon">
            <Connection />
          </div>
          <div class="metric-copy">
            <strong>数据接入</strong>
            <span>流程全程管控</span>
          </div>
        </div>
        <div class="metric-item is-service">
          <div class="metric-icon">
            <Promotion />
          </div>
          <div class="metric-copy">
            <strong>数据服务</strong>
            <span>服务快速构建</span>
          </div>
        </div>
      </div>
    </div>

    <section class="login-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Welcome back</span>
          <h2>登录工作台</h2>
        </div>
        <div class="status-dot" :class="{ warning: settingsStore.settingsError }"></div>
      </div>

      <Login :show-tenant-selector="showTenantSelector" />

      <transition name="fade-slide">
        <div v-if="settingsStore.settingsError" class="service-alert">
          <div class="alert-icon">!</div>
          <div class="alert-content">
            <strong>后端服务暂时不可用</strong>
            <p>{{ settingsStore.settingsError }}</p>
          </div>
          <el-button
            :loading="settingsStore.settingsLoading"
            size="small"
            class="retry-button"
            @click="retrySettings"
          >
            重新连接
          </el-button>
        </div>
      </transition>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Connection, DataAnalysis, Promotion } from "@element-plus/icons-vue";
import { useSettingStore } from "@/store";
import Login from "./components/Login.vue";

const settingsStore = useSettingStore();
const showTenantSelector = ref(false);
const logoClickCount = ref(0);
let logoClickTimer: ReturnType<typeof setTimeout> | undefined;

function handleLogoClick() {
  if (showTenantSelector.value) return;
  logoClickCount.value += 1;
  if (logoClickTimer) clearTimeout(logoClickTimer);
  if (logoClickCount.value >= 5) {
    showTenantSelector.value = true;
    logoClickCount.value = 0;
    return;
  }
  logoClickTimer = setTimeout(() => {
    logoClickCount.value = 0;
  }, 1200);
}

function retrySettings() {
  settingsStore.loadingSettings(true);
}
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  display: grid;
  grid-template-columns: minmax(360px, 680px) minmax(360px, 460px);
  gap: clamp(36px, 4vw, 64px);
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100%;
  padding: 56px clamp(24px, 6vw, 96px);
  overflow: hidden;
  color: #162033;
  background:
    radial-gradient(circle at 18% 18%, rgba(37, 99, 235, 0.18), transparent 30%),
    radial-gradient(circle at 82% 16%, rgba(79, 70, 229, 0.14), transparent 26%),
    linear-gradient(135deg, #f8fbff 0%, #eef4ff 46%, #f7faff 100%);
}

.login-container::before {
  position: absolute;
  inset: 8%;
  content: "";
  background-image:
    linear-gradient(rgba(37, 99, 235, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(37, 99, 235, 0.08) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: radial-gradient(circle, #000 0%, transparent 72%);
}

.visual-area,
.login-panel {
  position: relative;
  z-index: 1;
}

.visual-area {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 680px;
}

.brand-card {
  display: flex;
  gap: 22px;
  align-items: center;
  padding: 28px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 18px;
  box-shadow: 0 26px 70px rgba(31, 76, 148, 0.16);
  backdrop-filter: blur(18px);
}

.brand-logo {
  display: grid;
  flex: 0 0 76px;
  place-items: center;
  overflow: hidden;
  font-size: 30px;
  font-weight: 800;
  color: #fff;
  background: transparent;
  border-radius: 20px;
  cursor: pointer;
  user-select: none;
}

.brand-logo :deep(.el-image) {
  width: 76px;
  height: 76px;
}

.brand-card h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 48px);
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: 0;
}

.brand-card p {
  margin: 14px 0 0;
  font-size: 15px;
  line-height: 1.8;
  color: #5f6f86;
}

.metrics-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-item {
  --metric-color: #2563eb;
  --metric-soft: #eaf2ff;

  position: relative;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 13px;
  align-items: center;
  min-height: 84px;
  padding: 16px 18px;
  overflow: hidden;
  color: var(--metric-color);
  background: rgba(255, 255, 255, 0.74);
  border: 1px solid rgba(31, 76, 148, 0.1);
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(31, 76, 148, 0.06);
  transition:
    transform 0.24s ease,
    background-color 0.24s ease,
    border-color 0.24s ease,
    box-shadow 0.24s ease;
  backdrop-filter: blur(16px);
}

.metric-item::after {
  position: absolute;
  right: -42px;
  bottom: -54px;
  width: 118px;
  height: 118px;
  content: "";
  background: var(--metric-soft);
  border-radius: 50%;
  opacity: 0.72;
}

.metric-item:hover {
  border-color: var(--metric-color);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 32px rgba(31, 76, 148, 0.12);
  transform: translateY(-3px);
}

.metric-item.is-inventory {
  --metric-color: #2563eb;
  --metric-soft: #eaf2ff;
}

.metric-item.is-access {
  --metric-color: #7c3aed;
  --metric-soft: #f2edff;
}

.metric-item.is-service {
  --metric-color: #0891b2;
  --metric-soft: #e8f8fb;
}

.metric-icon {
  position: relative;
  z-index: 1;
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  color: var(--metric-color);
  background: var(--metric-soft);
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.84);
}

.metric-icon svg {
  width: 20px;
  height: 20px;
}

.metric-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.metric-copy span {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.35;
  color: #6b7890;
}

.metric-copy strong {
  display: block;
  font-size: 17px;
  line-height: 1.25;
  color: #172033;
}

.login-panel {
  width: 100%;
  padding: 34px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 18px;
  box-shadow: 0 26px 70px rgba(31, 76, 148, 0.18);
  backdrop-filter: blur(18px);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.eyebrow {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0;
}

.panel-head h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 750;
  letter-spacing: 0;
}

.status-dot {
  width: 12px;
  height: 12px;
  margin-top: 8px;
  background: #2563eb;
  border-radius: 50%;
  box-shadow: 0 0 0 6px rgba(37, 99, 235, 0.12);
}

.status-dot.warning {
  background: #f59e0b;
  box-shadow: 0 0 0 6px rgba(245, 158, 11, 0.14);
}

.service-alert {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  margin-top: 4px;
  background: linear-gradient(135deg, #fff8ed, #fffdf8);
  border: 1px solid rgba(245, 158, 11, 0.22);
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(146, 92, 9, 0.08);
}

.alert-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  font-weight: 800;
  color: #b45309;
  background: #ffedd5;
  border-radius: 10px;
}

.alert-content {
  min-width: 0;
}

.service-alert strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: #9a5b00;
}

.service-alert p {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 1.6;
  color: #87612a;
  white-space: nowrap;
}

.retry-button {
  min-width: 88px;
  height: 32px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  border: none;
  border-radius: 10px;
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.2);
}

.retry-button:hover,
.retry-button:focus {
  color: #fff;
  background: linear-gradient(135deg, #1d4ed8, #4338ca);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.24s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 980px) {
  .login-container {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 28px 18px;
  }

  .visual-area {
    max-width: none;
  }

  .brand-card {
    padding: 22px;
  }
}

@media (max-width: 640px) {
  .brand-card,
  .metrics-panel {
    display: none;
  }

  .login-panel {
    padding: 24px;
  }

  .service-alert {
    grid-template-columns: 34px minmax(0, 1fr);
    align-items: flex-start;
  }

  .retry-button {
    grid-column: 1 / -1;
    width: 100%;
  }

  .service-alert p {
    max-width: 100%;
    white-space: normal;
  }
}
</style>
