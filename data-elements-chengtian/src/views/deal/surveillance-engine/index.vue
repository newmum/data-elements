<template>
  <div class="surveillance-engine-page">
    <section class="engine-hero">
      <div class="hero-copy">
        <div class="eyebrow"><span class="eyebrow-dot" />数据加工开发 / 布控引擎</div>
        <h1>结构化数据布控引擎</h1>
        <p>
          将人员、手机、车辆等结构化 Kafka 事件与 surveillance_control_item_t 中的布控数据批量匹配，命中进入各通道结果 Topic，异常进入系统死信。
        </p>
        <div class="hero-meta">
          <span><el-icon><CircleCheckFilled /></el-icon>平台匹配服务已接入</span>
          <span><el-icon><Connection /></el-icon>支持多通道流组</span>
          <span><el-icon><Lock /></el-icon>凭据引用不落 DSL</span>
        </div>
      </div>
      <div class="hero-actions">
        <el-button type="primary" size="large" disabled>
          <el-icon><VideoPlay /></el-icon>
          流组画布暂不可用
        </el-button>
        <span>布控专用画布未迁入海通；当前页面仍可查看已登记资源与统计。</span>
      </div>
    </section>
    <el-alert title="布控专用画布暂未迁入海通，配置入口已停用。普通 NiFi 画布不能代替布控引擎配置。" type="warning" show-icon :closable="false" class="surveillance-canvas-notice" />

    <section class="summary-grid">
      <div v-for="item in summaries" :key="item.label" class="summary-card">
        <div class="summary-icon" :class="`summary-icon--${item.tone}`">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-label">{{ item.label }}</div>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>资源配置</h2>
          <p>每个布控资源 Topic 独立匹配并输出对应结果 Topic；人员、手机、车辆仅作为底层匹配类型。</p>
        </div>
        <div class="section-actions">
          <el-tag type="info" effect="plain">结构化数据流组</el-tag>
        </div>
      </div>

      <div class="channel-grid">
        <article v-for="resource in resourceCards" :key="resource.resourceCode" class="channel-card">
          <div class="channel-card__top">
            <div class="channel-title">
              <div class="channel-icon" :class="`channel-icon--${resource.tone}`">
                <el-icon><component :is="resource.icon" /></el-icon>
              </div>
              <div>
                <h3>{{ resource.displayName }}</h3>
                <span>{{ resource.title }} · {{ resource.resourceCode }}</span>
              </div>
            </div>
            <el-tag type="warning" effect="light">固定通道</el-tag>
          </div>

          <div class="channel-flow">
            <div class="flow-node">
              <span class="flow-node__label">输入</span>
              <code>{{ resource.inputTopic }}</code>
              <span class="flow-node__source">{{ topicSources([resource.inputTopic]) }}</span>
            </div>
            <span class="flow-arrow">→</span>
            <div class="flow-node flow-node--match">
              <span class="flow-node__label">匹配</span>
              <code>布控数据表</code>
            </div>
            <span class="flow-arrow">→</span>
            <div class="flow-node">
              <span class="flow-node__label">命中</span>
              <code>{{ resource.resultTopic }}</code>
            </div>
          </div>

          <div class="channel-footer">
            <span><el-icon><Timer /></el-icon>今日输入 {{ resource.todayInputCount }} · 今日命中 {{ resource.todayMatchedCount }}</span>
          <el-button link type="primary" disabled>引擎配置暂不可用 <el-icon><ArrowRight /></el-icon></el-button>
          </div>
        </article>
      </div>
    </section>

    <section class="section-block architecture-block">
      <div class="section-heading">
        <div>
          <h2>处理链路</h2>
          <p>匹配决策在平台服务完成，NiFi 负责消息编排和结果分流。</p>
        </div>
      </div>
      <div class="architecture-flow">
        <div v-for="(step, index) in architecture" :key="step.title" class="architecture-step">
          <div class="architecture-step__icon" :class="`architecture-step__icon--${step.tone}`">
            <el-icon><component :is="step.icon" /></el-icon>
          </div>
          <div>
            <strong>{{ step.title }}</strong>
            <span>{{ step.description }}</span>
          </div>
          <el-icon v-if="index < architecture.length - 1" class="architecture-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="result-tags">
        <span class="result-tag result-tag--success"><i />命中结果 Topic</span>
        <span class="result-tag result-tag--muted"><i />未命中仅内部统计</span>
        <span class="result-tag result-tag--danger"><i />异常进入系统死信</span>
      </div>
    </section>

    <el-dialog
      v-model="canvasVisible"
      title="结构化数据布控引擎流组"
      fullscreen
      append-to-body
      destroy-on-close
      class="surveillance-canvas-dialog"
      @closed="canvasFrame = null"
    >
      <iframe
        ref="canvasFrame"
        :src="canvasUrl.toString()"
        class="surveillance-canvas-frame"
        title="结构化数据布控引擎流组画布"
        @load="postCanvasInit"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  ArrowRight,
  CircleCheckFilled,
  Connection,
  DataAnalysis,
  Iphone,
  Lock,
  Timer,
  User,
  Van,
  VideoPlay,
} from "@element-plus/icons-vue";
import { computed, onMounted, onUnmounted, ref, type Component } from "vue";
import { AuthStorage } from "@/utils/auth";
import { capabilityCenterBases } from "@/utils/capabilityCenters";
import { createSurveillanceResourceTopic, getSurveillanceKafkaConnection, listSurveillanceDailyStats, listSurveillanceResourceTopics, type SurveillanceDailyStat, type SurveillanceKafkaConnection, type SurveillanceResourceTopic } from "@/api/surveillance-api";
import {
  loadSurveillanceChannels,
  saveSurveillanceChannels,
  loadSurveillanceTopics,
  topicByValue,
  resourceCodeForTopic,
  type SurveillanceChannelConfig,
} from "./config";

const canvasVisible = ref(false);
const canvasFrame = ref<HTMLIFrameElement | null>(null);
const selectedResource = ref<SurveillanceResourceTopic | null>(null);
const canvasUrl = computed(() => {
  const url = new URL(capabilityCenterBases.haitong, window.location.href);
  url.search = "";
  url.hash = "/development/canvas";
  url.searchParams.set("engine", "structured-surveillance");
  if (selectedResource.value) {
    url.searchParams.set("resourceCode", selectedResource.value.resourceCode);
    url.searchParams.set("resourceName", resourceDisplayName(selectedResource.value, selectedResource.value.channelCode));
    url.searchParams.set("channelCode", selectedResource.value.channelCode);
    url.searchParams.set("inputTopic", selectedResource.value.inputTopic);
    url.searchParams.set("resultTopic", selectedResource.value.resultTopic);
  }
  return url;
});

type SurveillanceChannel = {
  code: SurveillanceChannelConfig["code"];
  title: SurveillanceChannelConfig["title"];
  subtitle: SurveillanceChannelConfig["subtitle"];
  icon: Component;
  tone: string;
  inputTopics: string[];
  outputTopic: SurveillanceChannelConfig["outputTopic"];
  builtIn: SurveillanceChannelConfig["builtIn"];
};

const topicOptions = loadSurveillanceTopics();
const channelVisuals: Record<string, { icon: Component; tone: string }> = {
  person: { icon: User, tone: "blue" },
  mobile: { icon: Iphone, tone: "orange" },
  vehicle: { icon: Van, tone: "green" },
};
const toViewChannel = (channel: SurveillanceChannelConfig): SurveillanceChannel => ({
  ...channel,
  inputTopics: [...channel.inputTopics],
  ...(channelVisuals[channel.code] || { icon: Connection, tone: "violet" }),
});
const channels = ref<SurveillanceChannel[]>(loadSurveillanceChannels().map(toViewChannel));
const persistChannels = () => saveSurveillanceChannels(channels.value);

const resourceTopics = ref<SurveillanceResourceTopic[]>([]);
const dailyStats = ref<SurveillanceDailyStat[]>([]);
const kafkaConnection = ref<SurveillanceKafkaConnection | null>(null);
const outputKafkaConnection = ref<SurveillanceKafkaConnection | null>(null);
const resourceCards = computed(() => {
  const registered = resourceTopics.value.length
    ? resourceTopics.value
    : channels.value.flatMap(channel => channel.inputTopics.map(inputTopic => ({
      engineCode: "structured-surveillance",
      channelCode: channel.code as "person" | "mobile" | "vehicle",
      resourceCode: resourceCodeForTopic(channel.code, inputTopic),
      sourceTable: topicByValue(inputTopic, topicOptions)?.tableName || inputTopic,
      inputTopic,
      // 中文 Topic 经过 slug 化可能为空，使用资源编码保证每个资源都有唯一结果 Topic。
      resultTopic: `${channel.code}.result.${resourceCodeForTopic(channel.code, inputTopic).replace(`${channel.code}_`, "")}`,
      identifierType: channel.code === "person" ? "ID_CARD_NO" : channel.code === "mobile" ? "PHONE_NO" : "PLATE_NO",
      keyField: channel.code === "person" ? "idCardNo" : channel.code === "mobile" ? "phoneNo" : "plateNo",
      status: "ACTIVE",
    } satisfies SurveillanceResourceTopic)));
  return registered.map(resource => {
    const visual = channelVisuals[resource.channelCode] || { icon: Connection, tone: "violet" };
    const channel = channels.value.find(item => item.code === resource.channelCode);
    const stat = dailyStats.value.find(item => item.resourceCode === resource.resourceCode);
    return {
      ...resource,
      ...visual,
      title: channel?.title || resource.channelCode,
      subtitle: channel?.subtitle || "布控资源",
      // 资源注册时的来源表就是业务侧看到的布控资源名称；没有来源表时再回退到 Topic 和固定通道名称。
      displayName: resourceDisplayName(resource, channel?.title || resource.channelCode),
      todayInputCount: stat?.inputCount || 0,
      todayMatchedCount: stat?.matchedCount || 0,
    };
  });
});

const resourceDisplayName = (resource: SurveillanceResourceTopic, fallback: string) => {
  const sourceTable = resource.sourceTable?.trim();
  if (sourceTable && sourceTable !== resource.inputTopic) return sourceTable;
  const topic = topicByValue(resource.inputTopic, topicOptions);
  if (topic?.tableName?.trim()) return topic.tableName.trim();
  return fallback;
};

const refreshResourceData = async () => {
  try {
    [kafkaConnection.value, outputKafkaConnection.value] = await Promise.all([
      getSurveillanceKafkaConnection("INPUT"), getSurveillanceKafkaConnection("OUTPUT"),
    ]);
    resourceTopics.value = await listSurveillanceResourceTopics({ engineCode: "structured-surveillance", status: "ACTIVE" }) || [];
    if (!resourceTopics.value.length) {
      await Promise.allSettled(channels.value.flatMap(channel => channel.inputTopics.map(inputTopic => {
        const topic = topicByValue(inputTopic, topicOptions);
        const resourceCode = resourceCodeForTopic(channel.code, inputTopic);
        const keyField = channel.code === "person" ? "idCardNo" : channel.code === "mobile" ? "phoneNo" : "plateNo";
        const identifierType = channel.code === "person" ? "ID_CARD_NO" : channel.code === "mobile" ? "PHONE_NO" : "PLATE_NO";
        return createSurveillanceResourceTopic({
          engineCode: "structured-surveillance", channelCode: channel.code as "person" | "mobile" | "vehicle", resourceCode,
          sourceTable: topic?.tableName || inputTopic, inputTopic,
          resultTopic: `${channel.code}.result.${resourceCode.replace(`${channel.code}_`, "")}`,
          identifierType, keyField, schemaJson: JSON.stringify({ fields: topic?.fields || [] }), status: "ACTIVE",
        });
      })));
      resourceTopics.value = await listSurveillanceResourceTopics({ engineCode: "structured-surveillance", status: "ACTIVE" }) || [];
    }
    dailyStats.value = await listSurveillanceDailyStats({ engineCode: "structured-surveillance" }) || [];
    window.setTimeout(() => postCanvasInit(), 0);
  } catch {
    resourceTopics.value = [];
    dailyStats.value = [];
  }
};

const summaries = computed(() => [
  { value: String(channels.value.length), label: "业务通道", icon: Connection, tone: "blue" },
  { value: String(resourceCards.value.length), label: "结果 Topic / 资源", icon: DataAnalysis, tone: "violet" },
  { value: "1", label: "平台匹配服务", icon: CircleCheckFilled, tone: "green" },
]);

const topicSource = (topic: string) => topicByValue(topic, topicOptions)?.source ?? "已配置路由 Topic";
const topicSources = (topics: string[]) => Array.from(new Set(topics.map(topicSource))).join("、") || "未绑定输入 Topic";

const architecture = [
  { title: "Kafka 输入", description: "消费结构化事件", icon: Connection, tone: "blue" },
  { title: "批量匹配", description: "读取 control_item 布控数据", icon: DataAnalysis, tone: "violet" },
  { title: "结果分流", description: "命中结果 / 系统死信", icon: VideoPlay, tone: "green" },
];

const openCanvas = (resource: SurveillanceResourceTopic | null = null) => {
  selectedResource.value = resource;
  canvasVisible.value = true;
  // iframe 可能已经完成过 READY 握手，重新进入画布时仍需把最新的多 Topic 配置重发。
  [0, 150, 500, 1200].forEach(delay => window.setTimeout(() => postCanvasInit(), delay));
};

const postCanvasInit = () => {
  const message = {
    type: "INIT",
    payload: {
      token: AuthStorage.getAccessToken(),
      engine: "structured-surveillance",
      kafkaConnection: kafkaConnection.value?.status === "ACTIVE" ? kafkaConnection.value : null,
      outputKafkaConnection: outputKafkaConnection.value?.status === "ACTIVE" ? outputKafkaConnection.value : null,
      channels: channels.value.map(({ code, title, subtitle, inputTopics, outputTopic }) => ({
        code,
        title,
        subtitle,
        inputTopics,
        inputTopic: inputTopics[0],
        outputTopic,
      })),
        resources: resourceCards.value
          .filter(resource => !selectedResource.value || resource.resourceCode === selectedResource.value.resourceCode)
          .map(({ engineCode, channelCode, resourceCode, inputTopic, resultTopic, keyField }) => ({
          engineCode,
          channelCode,
          resourceCode,
        inputTopic,
        resultTopic,
        keyField,
          })),
    },
  };
  canvasFrame.value?.contentWindow?.postMessage(message, canvasUrl.value.origin);
  // The canvas bridge starts before React finishes mounting. Re-send a few times
  // so a fast iframe load cannot lose the configuration handshake.
  [150, 500, 1200].forEach(delay => window.setTimeout(() => {
    canvasFrame.value?.contentWindow?.postMessage(message, canvasUrl.value.origin);
  }, delay));
};

const handleCanvasMessage = (event: MessageEvent) => {
  if (event.source !== canvasFrame.value?.contentWindow || event.origin !== canvasUrl.value.origin) return;
  if (event.data?.type === "NIFI_APP_READY") postCanvasInit();
  if (event.data?.type === "NIFI_APP_CLOSE" || event.data?.type === "NIFI_APP_BACK") {
    canvasVisible.value = false;
    canvasFrame.value = null;
  }
};

onMounted(() => {
  window.addEventListener("message", handleCanvasMessage);
  void refreshResourceData();
});
onUnmounted(() => window.removeEventListener("message", handleCanvasMessage));
</script>

<style lang="scss" scoped>
.surveillance-canvas-notice { margin: 16px 0; }
.surveillance-engine-page {
  min-height: 100%;
  padding: 4px 8px 32px;
  color: #1f2a44;
  background: #f3f5f8;
}

.engine-hero {
  display: flex;
  justify-content: space-between;
  min-height: 218px;
  padding: 34px 40px;
  overflow: hidden;
  color: #233451;
  background: linear-gradient(118deg, #e7eef7 0%, #eef3f8 58%, #f4f6f9 100%);
  border: 1px solid #dfe7f0;
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(61, 79, 106, 0.08);
}

.hero-copy { max-width: 720px; }
.eyebrow { display: flex; gap: 8px; align-items: center; font-size: 13px; color: #71839c; }
.eyebrow-dot { width: 7px; height: 7px; background: #72b5a2; border-radius: 50%; }
h1 { margin: 18px 0 10px; font-size: 30px; font-weight: 700; letter-spacing: 1px; }
.hero-copy p { max-width: 680px; margin: 0; font-size: 14px; line-height: 1.8; color: #65758d; }
.hero-meta { display: flex; gap: 22px; margin-top: 22px; font-size: 12px; color: #72829a; }
.hero-meta span { display: flex; gap: 5px; align-items: center; }
.hero-actions { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; min-width: 210px; }
.hero-actions :deep(.el-button) { color: #fff; background: #5d7fa8; border: 1px solid #5d7fa8; box-shadow: 0 8px 18px rgba(72, 101, 137, 0.16); }
.hero-actions :deep(.el-button:hover) { background: #527394; border-color: #527394; }
.hero-actions > span { margin-top: 12px; font-size: 12px; color: #8492a6; }

.summary-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin: 18px 0 26px; }
.summary-card { display: flex; gap: 14px; align-items: center; padding: 18px 22px; background: #fff; border: 1px solid #edf0f6; border-radius: 12px; }
.summary-icon, .channel-icon, .architecture-step__icon { display: grid; flex: none; place-items: center; border-radius: 10px; }
.summary-icon { width: 44px; height: 44px; font-size: 22px; }
.summary-icon--blue, .channel-icon--blue, .architecture-step__icon--blue { color: #3478e5; background: #eaf2ff; }
.summary-icon--violet, .architecture-step__icon--violet { color: #7c61dc; background: #f0edff; }
.channel-icon--violet { color: #7964bb; background: #f0edff; }
.summary-icon--green, .channel-icon--green, .architecture-step__icon--green { color: #21a67a; background: #e6f8f1; }
.channel-icon--orange { color: #e8963d; background: #fff3e4; }
.summary-value { font-size: 24px; font-weight: 700; line-height: 1.1; }
.summary-label { margin-top: 5px; font-size: 12px; color: #8b94a7; }

.section-block { padding: 24px; margin-bottom: 18px; background: #fff; border: 1px solid #edf0f6; border-radius: 12px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.section-actions { display: flex; gap: 12px; align-items: center; }
h2 { margin: 0; font-size: 18px; }
.section-heading p { margin: 7px 0 0; font-size: 13px; color: #8992a5; }
.channel-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.channel-card { padding: 18px; border: 1px solid #e9edf5; border-radius: 10px; transition: border-color 0.2s, box-shadow 0.2s; }
.channel-card:hover { border-color: #9ebcf3; box-shadow: 0 8px 20px rgba(42, 83, 148, 0.08); }
.channel-card__top, .channel-title, .channel-footer { display: flex; align-items: center; }
.channel-card__top, .channel-footer { justify-content: space-between; }
.channel-card__actions { display: flex; gap: 5px; align-items: center; }
.channel-title { gap: 10px; }
.channel-icon { width: 38px; height: 38px; font-size: 20px; }
h3 { margin: 0 0 5px; font-size: 15px; }
.channel-title span { font-size: 12px; color: #919aae; }
.channel-flow { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 0.8fr) auto minmax(0, 0.8fr); gap: 7px; align-items: center; margin: 22px 0 18px; }
.flow-node { min-width: 0; padding: 8px 9px; background: #f7f9fc; border-radius: 7px; }
.flow-node--match { background: #f1f0ff; }
.flow-node__label { display: block; margin-bottom: 3px; font-size: 11px; color: #9aa2b2; }
.flow-node__source { display: block; margin-top: 4px; overflow: hidden; font-size: 10px; color: #8c99aa; text-overflow: ellipsis; white-space: nowrap; }
.flow-topic-select { width: 100%; }
.flow-topic-select :deep(.el-input__wrapper) { padding: 0; background: transparent; box-shadow: none; }
.flow-topic-select :deep(.el-input__inner) { min-width: 0; font-size: 11px; color: #46536b; text-overflow: ellipsis; }
code { display: block; max-width: 92px; overflow: hidden; font-size: 11px; color: #46536b; text-overflow: ellipsis; white-space: nowrap; }
.flow-arrow { color: #b4bdcc; }
.channel-footer { padding-top: 12px; font-size: 12px; color: #8b94a7; border-top: 1px solid #f0f2f6; }
.channel-footer > span { display: flex; gap: 5px; align-items: center; }
.channel-form__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-topic-select { width: 100%; }
.form-help { margin-top: 6px; font-size: 12px; line-height: 1.6; color: #8b94a7; }

.architecture-block { padding-bottom: 20px; }
.architecture-flow { display: grid; grid-template-columns: repeat(3, 1fr); gap: 26px; }
.architecture-step { position: relative; display: flex; gap: 12px; align-items: center; }
.architecture-step__icon { width: 42px; height: 42px; font-size: 20px; }
.architecture-step strong, .architecture-step span { display: block; }
.architecture-step strong { margin-bottom: 5px; font-size: 14px; }
.architecture-step span { font-size: 12px; color: #939bad; }
.architecture-arrow { position: absolute; top: 14px; right: 3px; color: #bdc5d2; }
.result-tags { display: flex; gap: 12px; padding-top: 20px; margin-top: 20px; border-top: 1px dashed #e5e9f1; }
.result-tag { display: flex; gap: 7px; align-items: center; padding: 6px 12px; font-size: 12px; border-radius: 99px; }
.result-tag i { width: 6px; height: 6px; border-radius: 50%; }
.result-tag--success { color: #1b9c73; background: #eaf9f3; }
.result-tag--success i { background: #1bba82; }
.result-tag--muted { color: #7e899b; background: #f3f5f8; }
.result-tag--muted i { background: #aab3c0; }
.result-tag--danger { color: #db6a6a; background: #fff0f0; }
.result-tag--danger i { background: #eb7c7c; }

.surveillance-canvas-dialog :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  height: 100%;
  margin: 0;
}
.surveillance-canvas-dialog :deep(.el-dialog__header) {
  position: relative;
  z-index: 2;
  flex: 0 0 54px;
  height: 54px;
  margin: 0;
  padding: 18px 20px;
  background: #fff;
}
.surveillance-canvas-dialog :deep(.el-dialog__body) {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  height: calc(100vh - 54px) !important;
  padding: 0;
  overflow: hidden;
}
.surveillance-canvas-frame {
  display: block;
  position: absolute;
  inset: 0;
  flex: none;
  width: 100%;
  min-height: 0;
  height: 100%;
  border: 0;
}

@media (max-width: 1100px) {
  .engine-hero { padding: 28px; }
  .channel-grid, .summary-grid { grid-template-columns: 1fr; }
  .architecture-flow { grid-template-columns: 1fr; gap: 16px; }
  .architecture-arrow { display: none; }
}
</style>
