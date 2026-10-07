<template>
  <div class="api-detail card-container px-5 pt-5 flex flex-col">
    <el-skeleton v-if="loading" :rows="8" animated class="detail-loading" />
    <el-result
      v-else-if="loadError"
      icon="error"
      title="服务详情加载失败"
      :sub-title="loadError"
    >
      <template #extra>
        <el-button type="primary" @click="loadServiceData">重新加载</el-button>
      </template>
    </el-result>
    <component
      v-else
      :is="ContentComponent"
      :key="`${serviceType}-${serviceId}`"
      ref="contentRef"
      context="detail"
      :data="serviceData"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";

const props = defineProps({
  tid: {
    type: String,
    default: "",
  },
});

const route = useRoute();
const serviceData = ref({});
const serviceType = ref("register");
const loading = ref(false);
const loadError = ref("");
const serviceId = computed(() => String(props.tid || route.query.id || ""));

const ContentComponent = computed(
  () => (serviceType.value === "build" ? "ApiBuildContent" : "ApiRegisterContent")
);

const parseMaybeJson = (value) => {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch (error) {
    return value;
  }
};

const extractPayload = (response) => {
  const parsed = parseMaybeJson(response);
  const payload = parseMaybeJson(parsed?.data);
  if (payload && typeof payload === "object") return payload;
  return parsed || {};
};

const objectValue = (value) => {
  const parsed = parseMaybeJson(value);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
};

// 同时保留新接口的扁平字段和旧服务构建组件依赖的嵌套结构。
const normalizeDetail = (data) => {
  const providerConfig = objectValue(data?.providerConfig);
  const publishConfig = objectValue(data?.publishConfig);
  const extendInfo = objectValue(data?.extendInfo);
  const serviceInfo = objectValue(data?.serviceInfo);
  const publishParams = Array.isArray(data?.publishParams)
    ? data.publishParams
    : Array.isArray(data?.paramList)
      ? data.paramList
      : [];
  const name = data?.serviceName || data?.name || serviceInfo.name || "";

  return {
    ...data,
    name,
    serviceName: name,
    description: data?.description || data?.serviceDesc || "",
    providerConfig,
    publishConfig,
    extendInfo,
    publishParams,
    service_parameters: Array.isArray(data?.service_parameters)
      ? data.service_parameters
      : publishParams,
    serviceInfo: {
      ...serviceInfo,
      id: data?.id || data?.tid || serviceInfo.id || "",
      name,
      dataSourceId:
        serviceInfo.dataSourceId || data?.dataSourceId || providerConfig.dataSourceId || "",
      tableName: serviceInfo.tableName || data?.tableName || providerConfig.tableName || "",
      operationType:
        serviceInfo.operationType || data?.operationType || providerConfig.operationType || "",
      model: serviceInfo.model ?? data?.model ?? providerConfig.model ?? "0",
    },
  };
};

const loadServiceData = async () => {
  const id = serviceId.value;
  if (!id) {
    serviceData.value = {};
    loadError.value = "缺少服务标识，请从数据服务列表重新进入";
    return;
  }

  loading.value = true;
  loadError.value = "";
  try {
    const response = await $common.post("/dws/flowserve/gateway/api/detail", {
      apiId: id,
    });
    const detail = normalizeDetail(extractPayload(response) || {});
    serviceData.value = detail;
    const requestedType = String(route.query.apiType || "").toLowerCase();
    const createType = String(detail.serviceCreateType || "").toLowerCase();
    serviceType.value =
      requestedType === "build" || createType === "apibuild" ? "build" : "register";
  } catch (error) {
    console.error("服务详情加载失败", error);
    serviceData.value = {};
    loadError.value = error?.message || "服务详情接口请求失败，请稍后重试";
    ElMessage.error("服务详情加载失败");
  } finally {
    loading.value = false;
  }
};

watch(
  () => [serviceId.value, route.query.apiType],
  () => loadServiceData(),
  { immediate: true }
);
</script>

<style lang="scss" scoped>
.api-detail {
  min-height: 320px;
  overflow: auto;
}

.detail-loading {
  padding: 12px 4px;
}
</style>
