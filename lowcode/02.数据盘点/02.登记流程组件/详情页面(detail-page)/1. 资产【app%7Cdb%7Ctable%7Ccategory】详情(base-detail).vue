<template>
  <div v-loading="state.loading" class="px-5 py-2 bg-white flex-1" :aria-busy="detailLoading">
    <LoadingState v-if="detailLoading" type="form" />
    <el-result v-else-if="loadError" icon="error" title="详情加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="loadDetail">重新加载</el-button>
      </template>
    </el-result>
    <div v-show="!detailLoading && !loadError">
    <JsonForm ref="formRef" bordered :rules="formRules">
      <template #type-u-title="{ rule }">
        <u-title :name="rule.props?.name" class="gap-4" style="height: 50px">
          <div v-if="titleActions(rule).length" class="detail-title-actions">
            <el-button
              v-for="action in titleActions(rule)"
              :key="action.key"
              :disabled="isTitleActionDisabled(action)"
              :icon="action.icon"
              :link="action.link === true"
              :loading="state.actionLoading[action.key] === true"
              :plain="action.plain === true"
              :type="action.type || 'primary'"
              @click="handleTitleAction(action)"
            >
              {{ action.label }}
            </el-button>
          </div>
        </u-title>
      </template>
    </JsonForm>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, watch, onBeforeUnmount } from "vue";
import { FormUtils } from "@/utils/form";
import { useDetailStore } from "@/store";
import { get, set, omit } from "lodash-es";

const props = defineProps<{
  /*资产id*/
  tid: string;
  editable?: boolean;
}>();
// refs
const formRef = ref();
const formRules = ref([]);
const detailLoading = ref(true);
const loadError = ref("");
let loadVersion = 0;
const store = useDetailStore();
const state = reactive<any>({
  rules: [],
  formData: {},
  loading: false,
  editing: false,
  actionLoading: {},
});
// 详情类型
const currentData = computed(() => state.formData || {});
const detailClass = computed(() => store.state.detailClass);
const txtRules = computed(() => FormUtils.transFormText(state.rules.map(metadataDisplayRule)));

const metadataDisplayRule = (rule: any) => {
  if (detailClass.value === "table" && rule.field === "dataUpdateTime") {
    return { ...rule, title: "元数据更新时间" };
  }
  if (detailClass.value === "db" && rule.field === "procCount") {
    return { ...rule, field: "procedureCount" };
  }
  if (detailClass.value === "db" && rule.field === "totalData") {
    // The overview provides total bytes, not an exact sum of every table's row count.
    return { ...rule, field: "totalSize", title: "总占用空间", props: { ...rule.props, suffix: "" } };
  }
  return rule;
};

// 配置和详情并行读取；两者都完成且表单回填后才结束骨架屏。
const loadDetail = async () => {
  const version = ++loadVersion;
  const tid = props.tid;
  const type = detailClass.value;
  detailLoading.value = true;
  loadError.value = "";
  state.editing = false;
  try {
    const formName = {
      app: "系统详情", db: "库详情", catalog: "目录详情", table: "表详情", fileCatalog: "文件目录详情",
    }[type];
    if (!tid) throw new Error("缺少资源标识，请从资源列表重新进入");
    if (!formName) throw new Error("暂不支持此资源的详情表单，请返回列表重试");
    const [rules, data] = await Promise.all([$form.get(formName), detailApi(tid, type)]);
    if (version !== loadVersion) return;
    if (!Array.isArray(rules) || !rules.length) throw new Error("详情表单配置为空，请在表单配置中完善后重新加载");
    state.rules = rules;
    state.formData = { ...data };
    formRules.value = txtRules.value;
    await nextTick();
    if (version !== loadVersion) return;
    formRef.value?.setValue(displayDataForDetail(state.formData, type));
    await nextTick();
    if (version === loadVersion) store.setData(data);
  } catch (error: unknown) {
    if (version === loadVersion)
      loadError.value = error instanceof Error ? error.message : "详情数据暂时无法加载，请稍后重试";
  } finally {
    if (version === loadVersion) detailLoading.value = false;
  }
};

const handleAction = (action: string) => {
  if (action === "init") return loadDetail();
};

const titleActions = (rule: any) => {
  const actions = Array.isArray(rule?.props?.actions) ? [...rule.props.actions] : [];
  if (detailClass.value === "table" && String(rule?.props?.name || "").includes("元数据")) {
    if (!actions.some((action) => action.request?.url === "/dst/database/metadata/refreshDbInfo")) {
      actions.push({
        key: "refreshMetadata", label: "刷新元数据", icon: "Refresh", visibleWhen: "view",
        reloadDetail: true, successMessage: "元数据已刷新",
        request: { url: "/dst/database/metadata/refreshDbInfo", method: "get",
          payload: { tid: "$datasourceId", tableId: "$tid" } },
      });
    }
  }
  return actions.filter((action) => {
    if (!action?.key || !action?.label) return false;
    const visibleWhen = action.visibleWhen || "always";
    return (
      visibleWhen === "always" ||
      (visibleWhen === "view" && !state.editing) ||
      (visibleWhen === "editing" && state.editing)
    );
  });
};

const isTitleActionDisabled = (action: any) =>
  detailLoading.value || !!loadError.value || state.loading || state.actionLoading[action.key] === true || (action.requiresEditable === true && !props.editable);

const resolveActionValue = (value: any): any => {
  if (value === "$tid") return props.tid;
  if (value === "$datasourceId") return state.formData.datasourceId || state.formData.dbId;
  if (value === "$detailClass") return detailClass.value;
  if (Array.isArray(value)) return value.map(resolveActionValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveActionValue(item)]));
  }
  return value;
};

const handleTitleAction = async (action: any) => {
  if (isTitleActionDisabled(action)) return;
  if (action.key === "edit") {
    state.editing = true;
    formRules.value = state.rules;
    await nextTick();
    formRef.value?.setValue(state.formData);
    return;
  }
  if (action.key === "cancel") {
    state.editing = false;
    handleAction("init");
    return;
  }
  if (action.key === "save") {
    await save();
    return;
  }

  const request = action.request;
  if (!request?.url) {
    $message.error(`操作“${action.label}”缺少请求配置`);
    return;
  }
  state.actionLoading[action.key] = true;
  try {
    const method = String(request.method || "post").toLowerCase();
    const payload = resolveActionValue(request.payload || {});
    const data = method === "get" ? await $common.get(request.url, payload) : await $common.post(request.url, payload);
    const refreshed = action.reloadDetail || request.url === "/dst/database/metadata/refreshDbInfo"
      ? await detailApi(props.tid) : data;
    if (refreshed && typeof refreshed === "object") {
      state.formData = { ...state.formData, ...refreshed };
      formRef.value?.setValue(displayDataForDetail(state.formData));
      store.setData(state.formData);
    }
    $message.success(action.successMessage || "操作成功");
  } catch (error: any) {
    console.error(`动态表单操作“${action.label}”失败：`, error);
    $message.error(error?.message || `${action.label}失败`);
  } finally {
    state.actionLoading[action.key] = false;
  }
};

const detailApi = (tid: string, type = detailClass.value) => {
  if (type === "app") {
    return $common.post("/dst/application/detail", { tid });
  }
  if (type === "db") {
    return $common.post("/dst/database/detail", { tid });
  }
  if (type === "table") {
    return $common.post("/ods/dataAggPage", { viewLevel: "tableDetail", tableId: tid }).then((res: any) => {
      const payload = res?.data || res || {};
      return payload.detail || payload;
    });
  }
  return $common.post("/dst/catalog/detail", { tid });
};

const displayDataForDetail = (data: Record<string, any>, type = detailClass.value) => {
  if (type !== "db") {
    return data;
  }
  return {
    ...data,
    appId: data.appName || data.applicationName || data.systemName || data.appId,
    nodeId: data.nodeName || data.nodeTypeName || data.nodeId,
    dbType: data.dbTypeName || data.dbType,
  };
};

const saveApi = (data: Record<string, any>) => {
  const url =
    detailClass.value === "app"
      ? "/dst/application/save"
      : detailClass.value === "db"
        ? "/dst/database/saveOrUpdate"
        : "/dst/catalog/saveOrUpdate";
  return $common.post(url, {
    tid: currentData.value.tid,
    assetType: detailClass.value,
    propList: data,
    // The server commits MySQL first; the search projection may be refreshed after the user-visible save.
    deferIndexRefresh: detailClass.value === "db",
  });
};

const refreshDbIndexInBackground = (tid: string) => {
  if (!tid) return;
  window.setTimeout(() => {
    $common.post("/dst/maintenance/refresh", { tid, assetType: "db" }).catch((error: any) => {
      console.warn("数据源已保存，搜索索引将在盘点维护中重试：", error);
    });
  }, 0);
};

const save = async (): Promise<any> => {
  try {
    state.loading = true;
    await formRef.value.validate();
    // 保存逻辑
    const formData = formRef.value?.getSaveData() || {};
    // 判断是否有变更
    const isUpdate = Object.keys(formData).some((k) => {
      if (!state.formData[k] && !formData[k]) {
        // 均为空时，也是匹配
        return false;
      }
      return state.formData[k] !== formData[k];
    });
    if (!isUpdate) {
      $message.warning("数据无变动，无需保存");
      return;
    }
    const saveResult = await saveApi(omit(formData, ["updatedTime", "regTime", "createdTime", "publishTime"]));
    let metadataWarning = false;
    if (detailClass.value === "db") {
      // Keep a committed save successful if the remote datasource is unavailable.
      if (String(formData.showConnect ?? state.formData.showConnect ?? "1") === "1") {
        try {
          await $common.getSilently("/dst/database/metadata/refreshDbInfo", { tid: saveResult?.tid || currentData.value.tid });
        } catch {
          metadataWarning = true;
        }
      }
      refreshDbIndexInBackground(saveResult?.tid || currentData.value.tid);
    }

    set(store, "data", Object.assign({}, formData, get(store, "data", {})));
    if (metadataWarning) $message.warning("数据源已保存，但元数据刷新失败，可在详情中重试");
    else $message.success("保存成功");
    state.editing = false;
    await handleAction("init");
  } catch (err: any) {
    console.log(err.message);
    $message.error(err);
  } finally {
    state.loading = false;
  }
};
watch(() => [props.tid, detailClass.value], loadDetail, { immediate: true });
onBeforeUnmount(() => { loadVersion++; });
</script>

<style scoped lang="scss">
.none {
  border: 0;
}

.detail-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
