<template>
  <div v-loading="state.loading" class="px-5 py-2 bg-white flex-1">
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
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed, h, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { useDetailStore } from "@/store";
import { get, set, omit, pick } from "lodash-es";

const props = defineProps<{
  /*资产id*/
  tid: string;
  editable?: boolean;
}>();
// refs
const formRef = ref();
const formRules = ref([]);
const store = useDetailStore();
const state = reactive<any>({
  ruleMap: {},
  rules: [],
  formData: {},
  loading: false,
  editing: false,
  actionLoading: {},
});
// 详情类型
const currentData = computed(() => state.formData || {});
const detailClass = computed(() => store.state.detailClass);
const txtRules = computed(() => {
  // eslint-disable-next-line vue/no-side-effects-in-computed-properties
  state.rules = state.ruleMap[detailClass.value] ? state.ruleMap[detailClass.value] : [];

  return FormUtils.transFormText(state.rules);
});

onMounted(() => {
  // 表单规则
  switch (detailClass.value) {
    case "app":
      $form.get("系统详情").then((data) => {
        state.ruleMap["app"] = data;
        handleAction("init");
      });
      break;
    case "db":
      $form.get("库详情").then((data) => {
        state.ruleMap["db"] = data;
        handleAction("init");
      });
      break;
    case "catalog":
      $form.get("目录详情").then((data) => {
        state.ruleMap["catalog"] = data;
        handleAction("init");
      });
      break;
    case "table":
      $form.get("表详情").then((data) => {
        state.ruleMap["table"] = data;
        handleAction("init");
      });
      break;
    case "fileCatalog":
      $form.get("文件目录详情").then((data) => {
        state.ruleMap["fileCatalog"] = data;
        handleAction("init");
      });
      break;
  }
});

const handleAction = (type: string) => {
  switch (type) {
    case "init":
      formRules.value = txtRules.value;
      if (!props.tid) return;
      detailApi(props.tid).then((data) => {
        nextTick(() => {
          state.formData = { ...data };
          formRef.value?.setValue(displayDataForDetail(state.formData));
          // 更新详情
          store.setData(data);
        });
      });
      break;
  }
};

const titleActions = (rule: any) => {
  const actions = rule?.props?.actions;
  if (!Array.isArray(actions)) return [];
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
  state.loading || state.actionLoading[action.key] === true || (action.requiresEditable === true && !props.editable);

const resolveActionValue = (value: any): any => {
  if (value === "$tid") return props.tid;
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
    if (data && typeof data === "object") {
      state.formData = { ...state.formData, ...data };
      formRef.value?.setValue(state.formData);
      store.setData({ ...get(store, "data", {}), ...data });
    }
    $message.success(action.successMessage || "操作成功");
  } catch (error: any) {
    console.error(`动态表单操作“${action.label}”失败：`, error);
    $message.error(error?.message || `${action.label}失败`);
  } finally {
    state.actionLoading[action.key] = false;
  }
};

const detailApi = (tid: string) => {
  if (detailClass.value === "app") {
    return $common.post("/dst/application/detail", { tid });
  }
  if (detailClass.value === "db") {
    return $common.post("/dst/database/detail", { tid });
  }
  if (detailClass.value === "table") {
    return $common.post("/ods/dataAggPage", { viewLevel: "tableDetail", tableId: tid }).then((res: any) => {
      const payload = res?.data || res || {};
      return payload.detail || payload;
    });
  }
  return $common.post("/dst/catalog/detail", { tid });
};

const displayDataForDetail = (data: Record<string, any>) => {
  if (detailClass.value !== "db") {
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
    if (detailClass.value === "db") {
      refreshDbIndexInBackground(saveResult?.tid || currentData.value.tid);
    }

    set(store, "data", Object.assign({}, formData, get(store, "data", {})));
    $message.success("保存成功");
    state.editing = false;
    handleAction("init");
  } catch (err: any) {
    console.log(err.message);
    $message.error(err);
  } finally {
    state.loading = false;
  }
};
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
