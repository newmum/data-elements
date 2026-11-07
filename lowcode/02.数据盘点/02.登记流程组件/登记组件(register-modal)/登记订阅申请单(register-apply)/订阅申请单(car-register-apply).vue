<template>
  <div class="px-5 py-2">
    <apply-json-form ref="applyFormRef" :init-data="applyData" :copy="isCopy" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { useRegisterStore } from "@/store";
import { useCartCount } from "@/composables";

interface ApplyForm {
  getFormData: () => Promise<Record<string, unknown>>;
  openPrint: () => void;
}

interface DraftOrder {
  tid?: string;
  draftKey?: string;
  type: "catalogSubscribe";
  propList: Record<string, unknown> & { applyResource: string[] };
}

const store = useRegisterStore();
const applyFormRef = ref<ApplyForm>();
const emptyRecord: Record<string, unknown> = {};
const applicationState = computed(() => asRecord(store.data));
const applyData = computed(() => asRecord(applicationState.value.apply));
const isCopy = computed(() => applicationState.value.editType === "再次申请");
const { refresh: refreshCartCount } = useCartCount();
const draftKeys = new Map<string, string>();
const savedByCatalog = new Map<string, string>();
let saved: { snapshot: string; ids: string[] } | undefined;
let active = true;
onBeforeUnmount(() => {
  active = false;
});

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object"
    ? (value as Record<string, unknown>)
    : emptyRecord;
}

function draftKey(catalogId: string) {
  const existing = draftKeys.get(catalogId);
  if (existing) return existing;
  const key = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
  draftKeys.set(catalogId, key);
  return key;
}

async function readForm() {
  if (!applyFormRef.value) throw new Error("申请单正在加载，请稍后重试");
  const { applyResource, ...fields } = await applyFormRef.value.getFormData();
  if (!Array.isArray(applyResource) || !applyResource.length)
    throw new Error("请选择申请资源");
  if (applyResource.length > 100)
    throw new Error("一次最多申请 100 个数据目录");

  const catalogIds = applyResource.map((resource: unknown) => {
    const row = asRecord(resource);
    if (
      row.unavailable === true ||
      typeof row.tid !== "string" ||
      !row.tid.trim()
    )
      throw new Error("申请包含不可用的数据目录，请重新选择");
    return row.tid;
  });
  if (new Set(catalogIds).size !== catalogIds.length)
    throw new Error("申请资源中有重复的数据目录");

  return { fields, catalogIds, snapshot: JSON.stringify([fields, catalogIds]) };
}

async function saveDraft(form: Awaited<ReturnType<typeof readForm>>) {
  if (!active) throw new Error("申请单已关闭");
  if (saved?.snapshot === form.snapshot) return saved.ids;
  const originalId = applyData.value.tid;
  // 历史申请单可能关联多个目录，继续编辑时保留原申请单；购物车新建仍为每个目录一单。
  const orders: DraftOrder[] =
    !isCopy.value && typeof originalId === "string" && originalId
      ? [
          {
            tid: originalId,
            type: "catalogSubscribe",
            propList: { ...form.fields, applyResource: form.catalogIds },
          },
        ]
      : form.catalogIds.map((catalogId) => {
          const tid = savedByCatalog.get(catalogId);
          return {
            ...(tid ? { tid } : { draftKey: draftKey(catalogId) }),
            type: "catalogSubscribe",
            propList: { ...form.fields, applyResource: [catalogId] },
          };
        });

  const response: unknown = await $common.post(
    "/dws/market/applyForm/saveOrUpdate",
    { orders },
  );
  const items = asRecord(response).items;
  if (!Array.isArray(items) || items.length !== orders.length)
    throw new Error("保存结果不完整，请重试");
  const ids = items.map((value: unknown, index: number) => {
    const item = asRecord(value);
    const order = orders[index];
    if (
      typeof item.tid !== "string" ||
      item.tid !== (order.tid ?? order.draftKey) ||
      JSON.stringify(item.catalogIds) !==
        JSON.stringify(order.propList.applyResource)
    )
      throw new Error("保存结果与当前申请不一致，请重试");
    return item.tid;
  });

  orders.forEach((order, index) => {
    for (const catalogId of order.propList.applyResource)
      savedByCatalog.set(catalogId, ids[index]);
  });
  saved = { snapshot: form.snapshot, ids };
  if (orders.some((order) => order.draftKey)) {
    void refreshCartCount().catch(() => {});
  }
  return ids;
}

const save = async () => {
  await saveDraft(await readForm());
  return { success: true, msg: "申请草稿已保存" };
};

const commit = async () => {
  const ids = await saveDraft(await readForm());
  // 用户离开表单后，已完成的草稿保存仍然有效，但不能继续发出提交。
  if (!active) return;
  const response: unknown = await $common.post("/dws/market/submit", {
    flowOrderIds: ids,
  });
  const items = asRecord(response).items;
  if (!Array.isArray(items) || items.length !== ids.length)
    throw new Error("提交结果不完整，请重试");
  const confirmed = new Set(
    items.map((value: unknown) => {
      const item = asRecord(value);
      return item.flowStatus === 2 && item.directGranted === true
        ? item.flowOrderId
        : undefined;
    }),
  );
  if (ids.some((id) => !confirmed.has(id)))
    throw new Error("申请尚未全部生效，请重试");

  return { completed: true, msg: "申请已直接生效" };
};

const print = () => applyFormRef.value?.openPrint();
defineExpose({ save, commit, print });
</script>
