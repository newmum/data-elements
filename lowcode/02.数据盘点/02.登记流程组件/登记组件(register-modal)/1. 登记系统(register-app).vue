<template>
  <div class="px-5 py-2">
    <el-skeleton v-if="formLoading" :rows="6" animated />
    <div v-else-if="formLoadError" role="alert">
      <el-alert title="业务系统表单加载失败" :description="formLoadError" type="error" :closable="false" show-icon />
      <el-button class="mt-3" type="primary" plain @click="loadForm">重新加载</el-button>
    </div>
    <JsonForm v-else ref="formRef" bordered :rules="formRules">
      <template v-if="showAppSync" #field-appName="scope">
        <div class="flex-center gap-2 w-full">
          <el-autocomplete
            :model-value="scope.model.value"
            :fetch-suggestions="querySearch"
            placeholder="请输入业务系统名称"
            @input="(value) => scope.model.callback(value)"
          />
          <el-button
            type="primary"
            plain
            :loading="store.state.loadStatus['sync-app']"
            icon="refresh"
            @click="syncApp()"
          >
            同步一本账
          </el-button>
        </div>
      </template>
    </JsonForm>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { get, set } from "lodash-es";
import { useRegisterStore } from "@/store";

const formRef = ref();
const formRules = ref([]);
const appList = ref<any[]>([]);
const formLoading = ref(true);
const formLoadError = ref("");
const showAppSync = computed(() => {
  const visit = (rules: any[]): boolean => rules.some((rule) =>
    (rule?.field === "appName" && rule?.props?.showSync === true) ||
    (Array.isArray(rule?.children) && visit(rule.children))
  );
  return visit(formRules.value);
});
const store = useRegisterStore();
const app = computed({
  get: () => {
    if (!get(store.data, "app")) set(store.data, "app", {});
    return get(store.data, "app", {});
  },
  set: (value) => set(store.data, "app", value),
});

const loadForm = async () => {
  formLoading.value = true;
  formLoadError.value = "";
  try {
    const { rules } = await $form.loadDefinition("登记系统");
    if (!rules.length) throw new Error("【登记系统】表单配置为空，请在当前租户的表单配置中维护");
    let values = { orgId: $user.orgId };
    if (app.value.tid) {
      const result = await $common.post("/dst/application/detail", { tid: app.value.tid });
      app.value = { ...app.value, ...result };
      values = result;
    }
    formRules.value = rules;
    formLoading.value = false;
    await nextTick();
    formRef.value?.setValue(values);
  } catch (error) {
    formLoadError.value = error?.message || "业务系统表单加载失败，请重新加载";
  } finally {
    formLoading.value = false;
  }
};
loadForm();

const save = async () => {
  try {
    if (formLoadError.value) throw new Error(formLoadError.value);
    if (formLoading.value || !formRef.value?.api) throw new Error("表单仍在加载，请稍后再保存");
    store.state.loadStatus.main = true;
    await formRef.value.validate();
    const formData = formRef.value.getSaveData();
    const result = await $common.post("/dst/application/save", {
      tid: app.value.tid,
      assetType: "app",
      propList: formData,
    });
    if (!result?.tid) throw new Error("保存接口未返回业务系统标识，无法确认登记已保存");
    app.value = { ...app.value, ...formData, ...result };
    return { success: true, msg: "保存成功", data: app.value };
  } finally {
    store.state.loadStatus.main = false;
  }
};

const next = () => {
  if (!app.value.tid) throw new Error("请先保存应用系统");
};
const finish = async () => {
  // 完成登记也是一次明确的保存操作；校验或保存失败时不得进入完成页。
  await save();
  return [{ ...app.value, assetType: "app" }];
};

const getAppList = async () => {
  appList.value = (await $common.post("/dst/application/list", {})) || [];
};
const querySearch = (query: string, callback: (items: { value: string }[]) => void) => {
  const keyword = query.toLowerCase();
  callback(
    appList.value
      .filter((item) => !keyword || item.appName?.toLowerCase().includes(keyword))
      .map((item) => ({ value: item.appName }))
  );
};
const syncApp = async () => {
  store.setLoadStatus("sync-app", true);
  try {
    await getAppList();
    $message.success("同步成功");
  } finally {
    store.setLoadStatus("sync-app", false);
    formRef.value?.api?.refresh();
  }
};

defineExpose({ save, next, finish });
</script>
