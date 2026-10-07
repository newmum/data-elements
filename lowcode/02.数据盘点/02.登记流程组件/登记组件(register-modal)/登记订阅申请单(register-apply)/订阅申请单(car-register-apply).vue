<template>
  <div class="px-5 py-2">
    <apply-json-form
      ref="applyFormRef"
      :init-data="applyData"
      :copy="store.data.editType === '再次申请'"
    />

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useRegisterStore } from "@/store";
import { useCartCount } from "@/composables";
import { get, set } from "lodash-es";

const { refresh: refreshCartCount } = useCartCount();

// refs
const applyFormRef = ref();
const store = useRegisterStore();
const { state } = storeToRefs(store);

// 当前申请单信息
const applyData = computed({
  get: () => {
    if (!get(store.data, "apply")) {
      set(store.data, "apply", {});
    }
    return get(store.data, "apply", {});
  },
  set: (val) => set(store.data, "apply", val),
});

// 验证表单
const validate = async () => {
  await applyFormRef.value?.validate();
};

// 保存单个申请单
const saveApi = (data: Record<string, any>) => {
  // applyResource 保存为目录 tid 数组
  return ($common as any).post("/dws/market/applyForm/saveOrUpdate", {
    tid: data.tid,
    type: "catalogSubscribe",
    propList: {
      ...data,
    },
  });
};

const save = async (): Promise<any> => {
  try {
    state.value.loadStatus["main"] = true;
    await validate();

    const isUpdate = !!applyData.value.tid;
    const saveData = await applyFormRef.value?.getFormData();

    // 保持一个目录一条申请记录，便于后续分别维护和撤销。
    let saveBatchList = [];
    const catalogIds = saveData.applyResource?.map((el) => el.tid);

    if (!isUpdate) {
      // 新增：每个目录都是新申请单
      saveBatchList = catalogIds.map((id: any) => ({
        ...saveData,
        applyResource: [id],
        applyName: saveData.applyResource.find((el) => el.tid === id)?.catalogName,
      }));
    } else {
      // 编辑：用逗号分隔的 tid 匹配
      const tids = applyData.value.tid.split(",");
      saveBatchList = catalogIds.map((id: any, ind: number) => ({
        ...saveData,
        applyResource: [id],
        tid: tids[ind],
        applyName: saveData.applyResource.find((el) => el.tid === id)?.catalogName,
      }));
    }

    // 并行保存所有申请单
    const res = await Promise.all(saveBatchList.map((el) => saveApi(el)));

    // 新增申请单则更新购物车目录数
    if (!isUpdate) {
      refreshCartCount();
    }

    // 合并所有 tid 用逗号连接
    applyData.value = Object.assign(applyData.value, saveData, {
      tid: res.map((el: any) => el.tid).join(","),
    });

    return { success: true, msg: "保存成功" };
  } catch (err: any) {
    return { success: false, msg: (err.message || err) as string };
  } finally {
    state.value.loadStatus["main"] = false;
  }
};

const next = () => {
  if (!applyData.value?.tid) {
    throw "请保存";
  }
};
const commit = async () => {
  next();

  const saveData = await applyFormRef.value?.getFormData();
  const finishData = saveData.applyResource?.map((el) => ({
    ...el,
    assetType: "catalog",
    assetName: el.catalogName,
    applyTime: saveData.applyTime,
  }));
  store.data["finish"] = finishData;
  await finish();
};

const finish = async () => {
  next();

  const tids = applyData.value.tid.split(",");
  const apis = tids.map((tid) =>
    $common.post("/dws/market/submit", {
      flowOrderId: tid,
    })
  );
  await Promise.all(apis);
  $message.success("申请已直接生效");
  store.handleAction("next");
};
const print = () => {
  applyFormRef.value?.openPrint();
};

// 暴露方法给父组件
defineExpose({ save, next, commit, print });

</script>
