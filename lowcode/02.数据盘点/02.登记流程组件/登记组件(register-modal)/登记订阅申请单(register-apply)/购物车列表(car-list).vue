<template>
  <div class="pt-5 px-5">
    <u-title class="py-0! flex-center" name="待申请数据目录">
      <el-tag round class="ml-2">{{ filteredList?.length }}</el-tag>
      <template #right>
        <el-space :size="16">
          <el-input
            v-model="state.keyword"
            style="width: 300px"
            clearable
            placeholder="请输入搜索关键词"
            suffix-icon="search"
          />

          <el-badge :value="state.draftCount" :hidden="state.draftCount === 0" :max="99">
            <el-tooltip
              :content="state.draftCount === 0 ? '暂无待提交申请单' : ''"
              :disabled="state.draftCount > 0"
            >
              <el-button
                type="primary"
                plain
                :disabled="state.draftCount === 0"
                @click="handleAction('草稿箱')"
              >
                草稿箱
              </el-button>
            </el-tooltip>
          </el-badge>
        </el-space>
      </template>
    </u-title>
    <page-list
      v-model="state.selectList"
      row-key="tid"
      :list="filteredList"
      :columns="state.fields"
      selectable
      @title-click="handleAction('detail', $event)"
    >
      <template #toolbar></template>

      <template #icon>
        <Icon icon="category" size="18" />
      </template>

      <template #title="{ row }">
        {{ row.catalogName }}
      </template>

      <template #status="{ row }">
        <span></span>
      </template>

      <template #actions="{ row }">
        <el-button type="danger" link icon="delete" @click="handleAction('删除', row)">
          删除
        </el-button>
      </template>

      <template #pagination></template>
    </page-list>

    <el-drawer
      v-model="state.open"
      size="385"
      resizable
      title="待提交申请单"
      header-class="draft-drawer__header"
      body-class="draft-drawer__body"
    >
      <div class="draft-list">
        <div v-for="it in state.drafts" :key="it.id" class="draft-card">
          <div class="card-header">
            <div class="card-title">
              <Icon icon="todo2" size="18" class="mr-1" />
              <span :title="it.applyName">{{ it.applyName }}</span>
            </div>
            <el-dropdown
              trigger="hover"
              placement="bottom-end"
              :hide-on-click="true"
              @command="(cmd) => handleDraftCommand(it, cmd)"
            >
              <el-button type="info" link style="rotate: 90deg; margin-right: -8px">
                <Icon icon="el-icon-MoreFilled" size="14" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete" style="color: var(--el-color-danger)">
                    <Icon icon="el-icon-delete" />
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="card-meta">
            <div class="meta-row">
              <span>资源数量：</span>

              <span>{{ it.resCount }}个</span>
            </div>
            <div class="meta-row">
              <span>更新时间：</span>
              <span>{{ it.updatedTime }}</span>
            </div>
          </div>
          <el-button type="primary" plain class="card-btn" @click="handleAction('继续申请', it)">
            <template #icon>
              <Icon icon="apply" />
            </template>
            继续申请
          </el-button>
        </div>
        <Empty v-if="!state.drafts?.length" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, getCurrentInstance, ref, h } from "vue";
import { ElMessageBox, ElCheckbox } from "element-plus";
import { useRegisterStore } from "@/store";
import { useCartCount } from "@/composables";

const { decrement } = useCartCount();

const store = useRegisterStore();
const { proxy } = getCurrentInstance()!;
const $common = (proxy as any).$common;

const state = reactive<any>({
  selectList: [],
  open: false,
  keyword: "",
  data: [],
  drafts: [],
  draftCount: 0,
  fields: [],
});

const fetchList = async () => {
  const data = await $common.post("/dws/market/shoppingCardList");
  state.data = data || [];
};

onMounted(() => {
  fetchList();
  fetchDrafts();
});

const fetchDrafts = async () => {
  const data = await $common.post("/dws/market/applyForm/list", {
    type: "catalogSubscribe",
    flowStatus: 0,
  });
  state.drafts = (data || []).map((el: any) => ({
    ...el,
    id: el.tid,
    title: el.applyTitle || el.title || el.formName,
    resCount: el.resCount ?? el.catalogIds?.split(",")?.length, // 暂时先使用目录个数，因为一般一个目录一个资源
  }));
  state.draftCount = state.drafts.length;
};

const handleAction = async (type: string, row?: any) => {
  switch (type) {
    case "草稿箱":
      state.open = true;
      fetchDrafts();
      break;
    case "删除":
      await $common
        .post("/dws/market/shoppingCardRemove", {
          tid: row.shoppingCarId,
        })
        .then((res) => {
          $message.success("删除成功");
          decrement(1);
        });
      await fetchList();
      break;
    case "继续申请":
      store.data = {
        apply: { tid: row.tid },
      };
      store.handleAction("next");
      break;
    case "next": {
      if (state.open) {
        // 继续申请触发的next
        state.open = false;
        break;
      }
      // 申请单信息
      const data = state.data.filter((el) => state.selectList.includes(el.tid));
      if (data.length === 0) {
        throw "请至少勾选一个数据目录";
      }
      store.data = {
        apply: { applyResource: data },
      };
      break;
    }
  }
};

const handleDraftCommand = async (item: any, cmd: string) => {
  if (cmd === "delete") {
    const addToCart = ref(false);

    try {
      await $dialog({
        title: "删除申请",
        message: () =>
          h("div", null, [
            h("p", { style: "margin-bottom: 12px" }, `确定删除申请单【${item.applyName}】？`),
            h(
              ElCheckbox,
              {
                modelValue: addToCart.value,
                "onUpdate:modelValue": (v: boolean) => (addToCart.value = v),
              },
              () => `将申请目录重新加入购物车`
            ),
          ]),
        showCancelButton: true,
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });
    } catch {
      return;
    }

    await $common.post("/dws/market/applyForm/deleteById", {
      tid: item.tid,
      addToCart: addToCart.value,
    });

    $message.success(addToCart.value ? "已删除，目录已重新加入购物车" : "删除成功");
    state.drafts = state.drafts.filter((d: any) => d.id !== item.id);
    state.draftCount = state.drafts.length;

    if (addToCart.value) {
      fetchList();
    }
  }
};

defineExpose({
  next: () => handleAction("next"),
});

state.fields = [
  { title: "数源单位", field: "orgId", options: "org" },
  { title: "资源类型", field: "resType", options: "assetType" },
  { title: "共享类型", field: "shareType", options: "shareType" },
  { title: "更新周期", field: "updateCycle", options: "updateCycle" },
  { title: "业务系统", field: "appId", options: "app" },
  { title: "选购时间", field: "addTime" },
];

const filteredList = computed(() => {
  const kw = (state.keyword || "").trim().toLowerCase();
  return state.data.filter((row: any) => {
    const text = [row.catalogName, row.appName, row.orgName, row.shareType, row.belongField]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();
    return text.includes(kw);
  });
});
</script>

<style scoped lang="scss">
:deep(.pl-container) {
  padding-top: 0px;
  .pl-toolbar {
    margin: 0 0;
  }
  .pl-row {
    padding-left: 12px;
    padding-right: 20px;
  }
  .pl-title:hover {
    color: var(--title-color) !important;
  }

  .pl-actions {
    visibility: hidden;
  }
  .pl-row:hover {
    .pl-actions {
      visibility: visible;
    }
  }
}

:deep(.el-drawer) {
  border-radius: 12px 0 0 12px !important;
}

:deep(.draft-drawer__header) {
  padding: 24px 28px 0 32px;
  border-width: 0;
}

:deep(.draft-drawer__body) {
  padding: 24px 32px;
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.draft-card {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: #fff;
  padding: 16px 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: "PingFang SC Medium";
  font-weight: 500;
  font-size: 16px;
  color: #323643;
  min-width: 0;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.card-meta {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  font-family: "PingFang SC", sans-serif;
  color: var(--el-text-color-secondary);
}
.card-btn {
  width: 100%;
}
</style>
