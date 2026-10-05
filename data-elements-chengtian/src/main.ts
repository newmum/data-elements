import { createApp } from "vue";
import App from "./App.vue";
import setupPlugins from "@/plugins";
import './utils/platformSessionChannel';
import { useUserStoreHook } from './store/modules/user-store';
import { capabilityCenterBases, identityCenterBase } from './utils/capabilityCenters';

// Element Plus 基础样式（先加载，后用自定义主题覆盖）
import "element-plus/theme-chalk/index.css";
// 主题样式（自定义覆盖）
import "@/styles/index.scss";
import "uno.css";

// 过渡动画
import "animate.css";

import ElementPlus from "element-plus";

declare global {
  interface Window {
    /** Low-code global navigation reads this value when opening the 祺智 suite. */
    __DATA_ELEMENTS_QIZHI_BASE_URL__?: string;
    /** Low-code registration pages use this configured report-workbench URL. */
    __DATA_ELEMENTS_REPORT_URL__?: string;
    __DATA_ELEMENTS_WANXIANG_BASE_URL__?: string;
    __DATA_ELEMENTS_HAITONG_BASE_URL__?: string;
    __DATA_ELEMENTS_SEARCH_BASE_URL__?: string;
    __DATA_ELEMENTS_IDAAS_BASE_URL__?: string;
  }
}

// Low-code bundles cannot read Vite's build-time environment directly. Publish
// the same addresses used by login recovery and the exact-origin session bridge.
window.__DATA_ELEMENTS_QIZHI_BASE_URL__ = capabilityCenterBases.qizhi;
window.__DATA_ELEMENTS_REPORT_URL__ = capabilityCenterBases.report;
window.__DATA_ELEMENTS_WANXIANG_BASE_URL__ = capabilityCenterBases.wanxiang;
window.__DATA_ELEMENTS_HAITONG_BASE_URL__ = capabilityCenterBases.haitong;
window.__DATA_ELEMENTS_SEARCH_BASE_URL__ = capabilityCenterBases.search;
window.__DATA_ELEMENTS_IDAAS_BASE_URL__ = identityCenterBase;

const app = createApp(App);

// 注册 Element Plus
app.use(ElementPlus);
// 注册插件
app.use(setupPlugins);
app.mount("#app");

// Capability-center logout is a framework event, not a feature-page action.
window.addEventListener('data-elements:platform-logout', () => {
  void useUserStoreHook().resetAllState().then(() => {
    window.location.replace(`${import.meta.env.BASE_URL}#/login`);
  });
});
