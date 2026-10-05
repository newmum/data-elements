export type SurveillanceTopic = {
  value: string;
  label: string;
  source: string;
  schema: string;
  tableName?: string;
  fields?: string[];
};

export type SurveillanceResourceTopic = {
  resourceCode: string;
  channelCode: "person" | "mobile" | "vehicle";
  sourceTable: string;
  inputTopic: string;
  resultTopic: string;
  identifierType: string;
  keyField: string;
  schemaJson?: string;
  status?: string;
};

export type SurveillanceChannelConfig = {
  code: string;
  title: string;
  subtitle: string;
  inputTopics: string[];
  outputTopic: string;
  builtIn: boolean;
};

export const SURVEILLANCE_TOPICS: SurveillanceTopic[] = [
  { value: "topic_大巴购票", label: "topic_大巴购票", source: "大巴购票路由", schema: "人员购票字段 JSON", tableName: "大巴购票", fields: ["idCardNo", "name", "phoneNo", "ticketTime"] },
  { value: "topic_人脸识别", label: "topic_人脸识别", source: "人脸识别路由", schema: "人员识别字段 JSON", tableName: "人脸识别", fields: ["idCardNo", "name", "faceId", "captureTime"] },
  { value: "topic_车辆卡口", label: "topic_车辆卡口", source: "车辆卡口路由", schema: "车辆通行字段 JSON", tableName: "车辆卡口", fields: ["plateNo", "vehicleType", "vehicleColor", "passTime"] },
  { value: "topic_酒店住宿", label: "topic_酒店住宿", source: "酒店住宿路由", schema: "住宿登记字段 JSON", tableName: "酒店住宿", fields: ["idCardNo", "name", "hotelName", "checkInTime"] },
  { value: "topic_网吧上网", label: "topic_网吧上网", source: "网吧上网路由", schema: "上网登记字段 JSON", tableName: "网吧上网", fields: ["idCardNo", "name", "internetBar", "loginTime"] },
  { value: "topic_航班订票", label: "topic_航班订票", source: "航班订票路由", schema: "航班订票字段 JSON", tableName: "航班订票", fields: ["idCardNo", "name", "flightNo", "ticketTime"] },
  { value: "topic_航班出入港", label: "topic_航班出入港", source: "航班出入港路由", schema: "航班出入港字段 JSON", tableName: "航班出入港", fields: ["idCardNo", "name", "flightNo", "direction", "passTime"] },
  { value: "topic_动车订票", label: "topic_动车订票", source: "动车订票路由", schema: "动车订票字段 JSON", tableName: "动车订票", fields: ["idCardNo", "name", "trainNo", "ticketTime"] },
];

export const DEFAULT_SURVEILLANCE_CHANNELS: SurveillanceChannelConfig[] = [
  { code: "person", title: "人员通道", subtitle: "身份证信息布控", inputTopics: ["topic_大巴购票", "topic_人脸识别", "topic_酒店住宿", "topic_网吧上网", "topic_航班订票", "topic_航班出入港", "topic_动车订票"], outputTopic: "person.result", builtIn: true },
  { code: "mobile", title: "手机通道", subtitle: "手机号信息布控", inputTopics: [], outputTopic: "mobile.result", builtIn: true },
  { code: "vehicle", title: "车辆通道", subtitle: "车牌信息布控", inputTopics: ["topic_车辆卡口"], outputTopic: "vehicle.result", builtIn: true },
];

const STORAGE_KEY = "structured-surveillance:channels";
const CONFIG_VERSION_KEY = "structured-surveillance:channels-version";
const CONFIG_VERSION = "resource-channels-v1";
const TOPICS_STORAGE_KEY = "structured-surveillance:topics";
const TOPIC_ALIASES: Record<string, string> = {
  "topic_民航表": "topic_身份证表",
  "topic_手机规则表": "topic_手机表",
  "topic_车辆": "topic_车牌表",
};
const LEGACY_DEFAULT_TOPIC_VALUES = new Set([
  "topic_身份证表",
  "topic_手机表",
  "topic_车牌表",
  "topic_民航表",
  "topic_手机规则表",
  "topic_车辆",
]);

const normalizeChannel = (channel: Partial<SurveillanceChannelConfig> & { inputTopic?: string }): SurveillanceChannelConfig => {
  const code = channel.code || "custom";
  const defaultChannel = DEFAULT_SURVEILLANCE_CHANNELS.find(item => item.code === code);
  const rawTopics = channel.inputTopics?.length
    ? channel.inputTopics
    : channel.inputTopic
      ? [channel.inputTopic]
      : defaultChannel?.inputTopics || [];
  return {
    code,
    title: defaultChannel?.title || channel.title || "自定义通道",
    subtitle: defaultChannel?.subtitle || channel.subtitle || "自定义布控通道",
    inputTopics: Array.from(new Set(rawTopics.map(topic => TOPIC_ALIASES[topic] || topic))),
    outputTopic: defaultChannel?.outputTopic || channel.outputTopic || `${code}.result`,
    builtIn: defaultChannel?.builtIn ?? Boolean(channel.builtIn),
  };
};

export const loadSurveillanceTopics = (): SurveillanceTopic[] => {
  if (typeof window === "undefined") return SURVEILLANCE_TOPICS.map(topic => ({ ...topic, fields: [...(topic.fields || [])] }));
  try {
    const stored = JSON.parse(window.localStorage.getItem(TOPICS_STORAGE_KEY) || "null");
    if (Array.isArray(stored)) {
      const customTopics = stored
        .filter((topic: unknown): topic is SurveillanceTopic => Boolean(topic && typeof topic === "object" && typeof (topic as SurveillanceTopic).value === "string"))
        .map(topic => ({ ...topic, fields: [...(topic.fields || [])] }));
      const topics = [...SURVEILLANCE_TOPICS, ...customTopics.filter(topic => !SURVEILLANCE_TOPICS.some(base => base.value === topic.value))];
      return topics;
    }
  } catch {
    // Ignore malformed topic state and use the built-in routes.
  }
  return SURVEILLANCE_TOPICS.map(topic => ({ ...topic, fields: [...(topic.fields || [])] }));
};

export const saveSurveillanceTopic = (topic: SurveillanceTopic) => {
  if (typeof window === "undefined") return;
  const customTopics = loadSurveillanceTopics().filter(item => !SURVEILLANCE_TOPICS.some(base => base.value === item.value));
  const next = [...customTopics.filter(item => item.value !== topic.value), { ...topic, fields: [...(topic.fields || [])] }];
  window.localStorage.setItem(TOPICS_STORAGE_KEY, JSON.stringify(next));
};

export const loadSurveillanceChannels = (): SurveillanceChannelConfig[] => {
  if (typeof window === "undefined") return DEFAULT_SURVEILLANCE_CHANNELS.map(item => ({ ...item, inputTopics: [...item.inputTopics] }));
  try {
    if (window.localStorage.getItem(CONFIG_VERSION_KEY) !== CONFIG_VERSION) {
      const defaults = DEFAULT_SURVEILLANCE_CHANNELS.map(item => ({ ...item, inputTopics: [...item.inputTopics] }));
      try {
        const storedTopics = JSON.parse(window.localStorage.getItem(TOPICS_STORAGE_KEY) || "[]");
        if (Array.isArray(storedTopics)) {
          window.localStorage.setItem(
            TOPICS_STORAGE_KEY,
            JSON.stringify(storedTopics.filter((topic: unknown) => {
              const value = topic && typeof topic === "object" ? (topic as { value?: unknown }).value : undefined;
              return typeof value !== "string" || !LEGACY_DEFAULT_TOPIC_VALUES.has(value);
            }))
          );
        }
      } catch {
        window.localStorage.removeItem(TOPICS_STORAGE_KEY);
      }
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(defaults));
      window.localStorage.setItem(CONFIG_VERSION_KEY, CONFIG_VERSION);
      return defaults;
    }
    const stored = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || "null");
    if (Array.isArray(stored) && stored.length) {
      const normalized = stored.map(normalizeChannel);
      const migrated = DEFAULT_SURVEILLANCE_CHANNELS.map(defaultChannel => {
        const configured = normalized.find(channel => channel.code === defaultChannel.code);
        return configured
          ? { ...defaultChannel, ...configured, inputTopics: [...configured.inputTopics], builtIn: true }
          : { ...defaultChannel, inputTopics: [...defaultChannel.inputTopics] };
      });
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(migrated));
      return migrated;
    }
  } catch {
    // Ignore malformed browser state and fall back to the built-in template.
  }
  return DEFAULT_SURVEILLANCE_CHANNELS.map(item => ({ ...item, inputTopics: [...item.inputTopics] }));
};

export const saveSurveillanceChannels = (channels: SurveillanceChannelConfig[]) => {
  if (typeof window === "undefined") return;
  const normalized = channels.map(normalizeChannel);
  const fixedChannels = DEFAULT_SURVEILLANCE_CHANNELS.map(defaultChannel => {
    const configured = normalized.find(channel => channel.code === defaultChannel.code);
    return configured
      ? { ...defaultChannel, ...configured, inputTopics: [...configured.inputTopics], builtIn: true }
      : { ...defaultChannel, inputTopics: [...defaultChannel.inputTopics] };
  });
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(fixedChannels));
  window.localStorage.setItem(CONFIG_VERSION_KEY, CONFIG_VERSION);
};

export const topicByValue = (value: string, topics: SurveillanceTopic[] = loadSurveillanceTopics()) => topics.find(topic => topic.value === value);

export const resourceCodeForTopic = (channelCode: SurveillanceChannelConfig["code"], topic: string) => {
  const slug = topic
    .replace(/^topic[_-]?/i, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
  if (slug) return `${channelCode}_${slug}`;
  const fingerprint = Array.from(topic).reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0, 7).toString(36);
  return `${channelCode}_resource_${fingerprint}`;
};
