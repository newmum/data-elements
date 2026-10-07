<template>
  <el-drawer
    v-model="settingsStore.navVisible"
    direction="ltr"
    :modal="true"
    style="margin-top: 55px; height: calc(100% - 55px)"
    :z-index="998"
    size="500px"
    :with-header="false"
    modal-class="nav-modal"
    body-class="images-img bg-cover"
  >
    <h4 class="m-0 font-bold mb-8 mt-4">全部导航</h4>
    <div class="menus">
      <div class="grid">
        <el-text class="font-bold">
          <Icon icon="menu-home" size="18" class="mr-1" />
          大数据能力产品
        </el-text>
        <el-divider class="my-2" />
        <div class="menu-items">
          <h5 @click="open('/integration/overview')">数据集成中心</h5>
          <h5 @click="open('/resource/overview')">数据资源中心</h5>
          <h5 @click="open('/compute/overview')">数据计算中心</h5>
          <h5 @click="open('/governance/overview')">数据治理中心</h5>
          <h5 @click="openAssetCenter">数据资产中心</h5>
          <h5 @click="open('/services/catalog')">数据服务中心</h5>
        </div>
      </div>
      <div class="grid">
        <el-text class="font-bold">
          <Icon icon="menu-exam" size="18" class="mr-2" />
          数据应用产品
        </el-text>
        <el-divider class="my-2" />
        <div class="menu-items">
          <h5 @click="openSearchCenter">智能搜索中心</h5>
          <h5 @click="open('/graph/datasets')">知识服务中心</h5>
          <h5 @click="open('/visualization/screens')">数据可视化中心</h5>
          <h5 @click="open('/ai/robots')">人工智能中心</h5>
        </div>
      </div>
      <div class="grid">
        <el-text class="font-bold">
          <Icon icon="safe" size="18" class="mr-2" />
          数据安全产品
        </el-text>
        <el-divider class="my-2" />
        <div class="menu-items">
          <h5
            role="button"
            tabindex="0"
            @click="openIdentityCenter"
            @keydown.enter="openIdentityCenter"
            @keydown.space.prevent="openIdentityCenter"
          >统一身份管理中心</h5>
          <h5 @click="open('/security/discovery/models')">数据安全中心</h5>
          <h5 @click="open('/ops/health')">数据运行监控中心</h5>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { useSettingStore } from "@/store";

const settingsStore = useSettingStore();
const DEFAULT_QIZHI_BASE_URL = "/qizhi/";
const LOCAL_QIZHI_BASE_URL = "http://localhost:3001/";
const DEFAULT_WANXIANG_BASE_URL = "/wanxiang-governance/";
const LOCAL_WANXIANG_BASE_URL = "http://localhost:3010/";
const DEFAULT_HAITONG_BASE_URL = "/haitong/";
const LOCAL_HAITONG_BASE_URL = "http://localhost:3002/";
const DEFAULT_SEARCH_BASE_URL = "/search/";
const LOCAL_SEARCH_BASE_URL = "http://localhost:3300/";

/**
 * Read the application address registry first, with meta-tag compatibility
 * for static deployments. Local fallbacks are only used on the platform dev
 * server; production uses the corresponding same-origin deployment prefix.
 */
function resolveCenterBaseUrl(windowKey, metaName, localUrl, productionUrl) {
  const configuredByWindow = String(window[windowKey] || "").trim();
  const configuredByMeta = String(
    document.querySelector(`meta[name="${metaName}"]`)?.getAttribute("content") || "",
  ).trim();
  const isLocalFrontend = ["localhost", "127.0.0.1", "[::1]"].includes(window.location.hostname);
  const localFallback = isLocalFrontend && window.location.port === "3000" ? localUrl : "";
  const configured = configuredByWindow || configuredByMeta || localFallback || productionUrl;
  const baseUrl = new URL(configured, window.location.origin);

  baseUrl.search = "";
  baseUrl.hash = "";
  if (!baseUrl.pathname.endsWith("/")) {
    baseUrl.pathname += "/";
  }
  return baseUrl;
}

function resolveQizhiBaseUrl() {
  return resolveCenterBaseUrl("__DATA_ELEMENTS_QIZHI_BASE_URL__", "data-elements-qizhi-base-url", LOCAL_QIZHI_BASE_URL, DEFAULT_QIZHI_BASE_URL);
}

function resolveWanxiangBaseUrl() {
  return resolveCenterBaseUrl("__DATA_ELEMENTS_WANXIANG_BASE_URL__", "data-elements-wanxiang-base-url", LOCAL_WANXIANG_BASE_URL, DEFAULT_WANXIANG_BASE_URL);
}

function resolveHaitongBaseUrl() {
  return resolveCenterBaseUrl("__DATA_ELEMENTS_HAITONG_BASE_URL__", "data-elements-haitong-base-url", LOCAL_HAITONG_BASE_URL, DEFAULT_HAITONG_BASE_URL);
}

function resolveSearchBaseUrl() {
  return resolveCenterBaseUrl("__DATA_ELEMENTS_SEARCH_BASE_URL__", "data-elements-search-base-url", LOCAL_SEARCH_BASE_URL, DEFAULT_SEARCH_BASE_URL);
}

function openAt(destination, route) {
  destination.hash = `#${route.startsWith("/") ? route : `/${route}`}`;
  window.location.assign(destination.toString());
}

function open(route) {
  // Governance, resource and asset views moved into Wanxiang. Integration
  // lives in Haitong; other capabilities remain in Qizhi. Do not send every
  // product to the old Qizhi port just because its hash route looks alike.
  const destination = /^\/(governance|resource|assets)(\/|$)/.test(route)
    ? resolveWanxiangBaseUrl()
    : /^\/integration(\/|$)/.test(route)
      ? resolveHaitongBaseUrl()
      : resolveQizhiBaseUrl();
  openAt(destination, route);
}

function openIdentityCenter() {
  const destination = resolveCenterBaseUrl("__DATA_ELEMENTS_IDAAS_BASE_URL__", "data-elements-idaas-base-url", "http://localhost:3005/", "/idaas/");
  openAt(destination, "/console/workforce/overview");
}

function openSearchCenter() {
  window.location.assign(resolveSearchBaseUrl().toString());
}

function openAssetCenter() {
  // 资产中心已经合并进万象；入口应指向中心主页，而不是已移除的启智 /assets/market 路由。
  openAt(resolveWanxiangBaseUrl(), "/assets/my");
}
</script>

<style scoped lang="scss">
.menus {
  display: grid;
  gap: 24px;
  padding-left: 12px;

  h5 {
    margin: 0;
    padding: 8px 0;
    cursor: pointer;
    border-radius: 8px;
    font-weight: normal;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  .menu-items {
    display: grid;
    grid-template-columns: 1fr 1fr;
    padding-left: 24px;
  }
}
</style>

<style lang="scss">
.nav-modal {
  --el-overlay-color-lighter: transparent;

  :deep(.el-drawer) {
    background-size: cover;
    box-shadow: 0 20px 20px 0 rgba(0, 0, 0, 0.19);
  }
}
</style>
