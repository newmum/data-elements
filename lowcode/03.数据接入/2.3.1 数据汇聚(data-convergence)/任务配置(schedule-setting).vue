<template>
  <div class="schedule-setting-designer">
    <div v-if="designerError" class="designer-error">
      <div class="designer-error-card">
        <div class="designer-error-icon">!</div>
        <h3>流程画布暂时无法打开</h3>
        <p>{{ designerError }}</p>
        <div class="designer-error-actions">
          <el-button type="primary" @click="openDesigner">重新检测</el-button>
          <el-button @click="closeDesigner">关闭</el-button>
        </div>
      </div>
    </div>
    <!-- 主应用探测并初始化子画布时，展示与数据接入业务一致的三阶段进度。 -->
    <div v-else-if="iframeLoading" class="loading-mask">
      <div class="canvas-loading-shell" aria-label="正在加载流程画布">
        <div class="canvas-loading-toolbar">
          <i></i><span></span><span></span><b></b>
        </div>
        <div class="canvas-loading-workspace">
          <div class="canvas-loading-copy">
            <span class="canvas-loading-main-icon"><Icon icon="el-icon-Connection" /></span>
            <div>
              <strong>正在准备数据接入画布</strong>
              <small>正在读取任务配置并装配流程节点</small>
            </div>
          </div>
          <div class="canvas-loading-pipeline" aria-hidden="true">
            <div class="canvas-loading-step is-active">
              <span><Icon icon="db" /></span>
              <b>读取任务配置</b>
            </div>
            <div class="canvas-loading-track"><i></i></div>
            <div class="canvas-loading-step">
              <span><Icon icon="el-icon-SetUp" /></span>
              <b>装配处理节点</b>
            </div>
            <div class="canvas-loading-track is-later"><i></i></div>
            <div class="canvas-loading-step">
              <span><Icon icon="el-icon-Connection" /></span>
              <b>生成流程画布</b>
            </div>
          </div>
        </div>
      </div>
    </div>
    <iframe
      v-if="designerUrl && !designerError"
      ref="iframeRef"
      :src="designerUrl"
      class="schedule-setting-iframe"
      frameborder="0"
      allowfullscreen
      @load="onIframeLoad"
    ></iframe>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";

const emit = defineEmits(["close"]);

const props = defineProps({
  id: {
    type: String,
    default: "",
  },
  accessTaskId: {
    type: String,
    default: "",
  },
});

const designerUrl = ref("");
const iframeLoading = ref(true);
const designerError = ref("");
const iframeRef = ref<HTMLIFrameElement | null>(null);
let designerAttempt = 0;
let designerProbeController: AbortController | null = null;

const onIframeLoad = () => {
  sendInitMessage();
  detectUnexpectedLogin();
};

const storedToken = (value: string | null) => {
  if (!value) return "";
  try {
    const parsed = JSON.parse(value);
    return typeof parsed === "string" ? parsed : "";
  } catch {
    return value;
  }
};

// 与主平台 AuthStorage 保持一致：未勾选“记住密码”时 token 在 sessionStorage。
const getToken = () => {
  const remembered = localStorage.getItem("remember_me") === "true";
  return storedToken(remembered ? localStorage.getItem("token") : sessionStorage.getItem("token"));
};

const normalizeBaseUrl = (url: string) => url.replace(/\/+$/, "");

const isSameOrigin = (baseUrl: string) => {
  try {
    return new URL(baseUrl, window.location.href).origin === window.location.origin;
  } catch (error) {
    return false;
  }
};

const isHaitongDocument = (text: string) =>
  /<title>\s*海通数据集成中心\s*<\/title>/i.test(text) &&
  /<div[^>]+id=["']root["']/i.test(text);

const getDesignerCandidates = () => {
  // 低代码组件运行时不能读取 Vite 环境变量，复用主应用公布的能力中心地址。
  const configured = String(window["__DATA_ELEMENTS_HAITONG_BASE_URL__"] || "").trim()
    || String(document.querySelector('meta[name="data-elements-haitong-base-url"]')?.getAttribute("content") || "").trim()
    || (["localhost", "127.0.0.1"].includes(window.location.hostname) && window.location.port === "3000"
      ? "http://localhost:3002/" : "/haitong/");
  try {
    const base = new URL(configured, window.location.href);
    base.search = "";
    base.hash = "";
    if (!base.pathname.endsWith("/")) base.pathname += "/";
    return [base.href];
  } catch {
    return [];
  }
};

const isDesignerReachable = async (baseUrl: string, parentSignal?: AbortSignal) => {
  const controller = new AbortController();
  const abortProbe = () => controller.abort();
  const timeout = window.setTimeout(abortProbe, 3000);
  parentSignal?.addEventListener("abort", abortProbe, { once: true });
  try {
    const response = await fetch(`${normalizeBaseUrl(baseUrl)}/?_health=${Date.now()}`, {
      method: "GET",
      cache: "no-store",
      mode: isSameOrigin(baseUrl) ? "same-origin" : "no-cors",
      signal: controller.signal,
    });
    if (response.type === "opaque") return true;
    if (!response.ok) return false;
    const text = await response.text().catch(() => "");
    return isHaitongDocument(text);
  } catch (error) {
    return false;
  } finally {
    window.clearTimeout(timeout);
    parentSignal?.removeEventListener("abort", abortProbe);
  }
};

const sendInitMessage = () => {
  const token = getToken();
  if (!token || !designerUrl.value) return;
  const targetOrigin = new URL(designerUrl.value, window.location.href).origin;
  iframeRef.value?.contentWindow?.postMessage(
    {
      type: "INIT",
      payload: {
        token,
        tid: props.id,
        accessTaskId: props.accessTaskId,
        orgId: $user.orgId || "",
      },
    },
    targetOrigin
  );
};

const detectUnexpectedLogin = () => {
  try {
    const href = iframeRef.value?.contentWindow?.location.href || "";
    if (href.includes("/login") || href.includes("#/login")) {
      designerError.value =
        "当前海通地址打开到了主系统登录页。请检查海通能力中心地址和部署路径。";
    }
  } catch (error) {
    // 跨域 iframe 无法读取 location，属于正常情况。
  }
};

const closeDesigner = () => {
  designerAttempt += 1;
  designerProbeController?.abort();
  designerProbeController = null;
  designerUrl.value = "";
  designerError.value = "";
  iframeLoading.value = false;
  emit("close");
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: "NIFI_APP_CLOSE", timestamp: Date.now() }, "*");
  }
};

const handleDesignerMessage = (event: MessageEvent) => {
  if (event.source !== iframeRef.value?.contentWindow) return;
  if (!designerUrl.value || event.origin !== new URL(designerUrl.value, window.location.href).origin) return;
  if (event.data?.type === "NIFI_APP_READY") {
    sendInitMessage();
  } else if (event.data?.type === "NIFI_CANVAS_CONTENT_READY") {
    iframeLoading.value = false;
  } else if (event.data?.type === "NIFI_CANVAS_CONTENT_ERROR") {
    iframeLoading.value = false;
    designerError.value = event.data?.message || "流程画布数据加载失败，请重新检测";
  }
};

const openDesigner = async () => {
  designerError.value = "";
  iframeLoading.value = true;
  designerUrl.value = "";
  designerProbeController?.abort();
  designerProbeController = new AbortController();
  const currentAttempt = ++designerAttempt;

  if (!getToken()) {
    iframeLoading.value = false;
    designerError.value = "当前登录会话已失效，请重新登录数据中台后打开画布。";
    return;
  }

  const candidates = getDesignerCandidates();
  let designerBaseUrl = "";
  for (const candidate of candidates) {
    if (await isDesignerReachable(candidate, designerProbeController.signal)) {
      designerBaseUrl = normalizeBaseUrl(candidate);
      break;
    }
    if (currentAttempt !== designerAttempt) return;
  }

  if (currentAttempt !== designerAttempt) return;
  designerProbeController = null;

  if (!designerBaseUrl) {
    iframeLoading.value = false;
    designerError.value =
      "未检测到海通数据集成中心。本地开发请启动海通前端；生产环境请确认 /haitong/ 已发布并可访问。";
    return;
  }

  const params = new URLSearchParams({
    communication: "postMessage",
    tid: props.id,
    accessTaskId: props.accessTaskId,
    orgId: $user.orgId || "",
  });

  designerUrl.value = `${designerBaseUrl}/?_t=${new Date().getTime()}#/development/canvas?${params.toString()}`;
};

onMounted(() => {
  window.addEventListener("message", handleDesignerMessage);
  openDesigner();
});

onUnmounted(() => {
  designerAttempt += 1;
  designerProbeController?.abort();
  window.removeEventListener("message", handleDesignerMessage);
});
</script>

<style lang="scss" scoped>
.schedule-setting-designer {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #fff;
  z-index: 9999;
  .designer-error {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, #f7fbff 0%, #ffffff 100%);
    z-index: 2;
  }

  .designer-error-card {
    width: 460px;
    padding: 34px 36px;
    text-align: center;
    background: #fff;
    border: 1px solid #e6efff;
    border-radius: 16px;
    box-shadow: 0 18px 46px rgba(23, 68, 160, 0.12);
  }

  .designer-error-icon {
    width: 52px;
    height: 52px;
    margin: 0 auto 16px;
    color: #f56c6c;
    font-size: 28px;
    font-weight: 700;
    line-height: 52px;
    background: #fff1f0;
    border-radius: 50%;
  }

  .designer-error-card h3 {
    margin: 0 0 10px;
    color: #1f2937;
    font-size: 18px;
    font-weight: 700;
  }

  .designer-error-card p {
    margin: 0;
    color: #667085;
    font-size: 14px;
    line-height: 1.7;
  }

  .designer-error-actions {
    display: flex;
    justify-content: center;
    gap: 12px;
    margin-top: 24px;
  }

  .loading-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    z-index: 1;
  }

  .canvas-loading-shell {
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: #f5f7fa;
  }

  .canvas-loading-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    height: 56px;
    padding: 0 24px;
    background: #fff;
    border-bottom: 1px solid #ebeef5;

    i,
    span,
    b {
      display: block;
      height: 16px;
      border-radius: 4px;
      background: linear-gradient(90deg, #eef1f5 25%, #f8fafc 50%, #eef1f5 75%);
      background-size: 200% 100%;
      animation: canvas-skeleton 1.4s ease-in-out infinite;
    }

    i { width: 32px; height: 32px; border-radius: 8px; }
    span { width: 120px; }
    span + span { width: 76px; }
    b { width: 180px; margin-left: auto; }
  }

  .canvas-loading-stage {
    position: relative;
    height: calc(100% - 56px);
  }

  .canvas-loading-workspace {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: calc(100% - 56px);
    padding: 24px;
  }

  .canvas-loading-copy {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 38px;

    strong,
    small { display: block; letter-spacing: 0; }
    strong { color: #1f2937; font-size: 18px; line-height: 26px; }
    small { margin-top: 3px; color: #7b8798; font-size: 13px; line-height: 20px; }
  }

  .canvas-loading-main-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    color: #1677ff;
    font-size: 22px;
    border: 1px solid #b9d7ff;
    border-radius: 8px;
    background: #eef6ff;
    animation: canvas-icon-pulse 1.6s ease-in-out infinite;
  }

  .canvas-loading-pipeline {
    display: grid;
    grid-template-columns: 112px minmax(72px, 150px) 112px minmax(72px, 150px) 112px;
    align-items: start;
    width: min(680px, 86vw);
  }

  .canvas-loading-step {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 9px;
    color: #637083;

    span {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 46px;
      height: 46px;
      color: #5f6b7a;
      font-size: 21px;
      border: 1px solid #dbe2ea;
      border-radius: 8px;
      background: #fff;
    }

    b { font-size: 13px; font-weight: 500; letter-spacing: 0; white-space: nowrap; }
  }

  .canvas-loading-step.is-active span {
    color: #1677ff;
    border-color: #8fc0ff;
    background: #eef6ff;
  }

  .canvas-loading-track {
    position: relative;
    height: 46px;

    &::before {
      content: "";
      position: absolute;
      top: 22px;
      left: 7px;
      right: 7px;
      height: 2px;
      background: #d9e2ec;
    }

    i {
      position: absolute;
      top: 18px;
      left: 5px;
      width: 10px;
      height: 10px;
      border: 2px solid #fff;
      border-radius: 50%;
      background: #1677ff;
      box-shadow: 0 0 0 1px #8fc0ff;
      animation: canvas-data-flow 1.7s ease-in-out infinite;
    }
  }

  .canvas-loading-track.is-later i { animation-delay: 0.62s; }

  @keyframes canvas-skeleton {
    from { background-position: 200% 0; }
    to { background-position: -200% 0; }
  }

  @keyframes canvas-data-flow {
    0% { left: 5px; opacity: 0; }
    14% { opacity: 1; }
    84% { opacity: 1; }
    100% { left: calc(100% - 15px); opacity: 0; }
  }

  @keyframes canvas-icon-pulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(22, 119, 255, 0); }
    50% { box-shadow: 0 0 0 7px rgba(22, 119, 255, 0.08); }
  }

  @media (max-width: 720px) {
    .canvas-loading-pipeline {
      grid-template-columns: 82px minmax(34px, 1fr) 82px minmax(34px, 1fr) 82px;
      width: 100%;
    }

    .canvas-loading-step b { font-size: 12px; }
  }

  .schedule-setting-iframe {
    display: block;
    width: 100%;
    height: 100%;
    border: none;
    background: #f5f7fa;
  }
}
</style>

