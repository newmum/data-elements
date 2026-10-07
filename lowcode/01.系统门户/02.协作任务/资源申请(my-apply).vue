<template>
  <div class="card-container flex-1">
    <page-list
      :loading="state.loading"
      :list="state.data"
      :total="state.total"
      :columns="state.fields"
      :page-no="state.pageNo"
      :page-size="state.pageSize"
      @title-click="handleAction('detail', $event)"
    >
      <template #toolbar>
        <div class="flex-x-between">
          <el-segmented
            v-model="state.seg"
            :options="['审批通过', '审批中', '不通过', '被驳回', '撤回', '待提交']"
            @change="handleAction('refresh')"
          />
          <el-space>
            <el-input
              v-model="state.keyword"
              clearable
              placeholder="请输入搜索关键词"
              suffix-icon="search"
              @keydown.enter="handleAction('refresh')"
            />
          </el-space>
        </div>
      </template>

      <template #icon>
        <Icon icon="catalog" size="18" />
      </template>

      <template #title="{ row }">
        {{ row.applyName }}
      </template>

      <template #status="{ row }">
        <dict-label
          v-if="row.flowStatus"
          round
          :model-value="row.flowStatus"
          options="flowStatus"
          tag-class="text-sm border-0"
        >
          {{ row.status }}
        </dict-label>
      </template>

      <template #actions="{ row }">
        <template v-if="state.seg === '待提交'">
          <el-button type="primary" link icon="EditPen" @click="handleAction('继续申请', row)">
            继续申请
          </el-button>
          <el-button type="danger" link icon="delete" @click="handleAction('删除', row)">
            删除
          </el-button>
        </template>

        <el-button
          v-if="['审批中', '被驳回', '不通过', '撤回'].includes(state.seg)"
          type="primary"
          link
          icon="view"
          @click="handleAction('查看进度', row)"
        >
          查看进度
        </el-button>

        <el-button
          v-if="['不通过'].includes(state.seg)"
          type="warning"
          link
          icon="EditPen"
          @click="handleAction('再次申请', row)"
        >
          再次申请
        </el-button>

        <el-button
          v-if="['被驳回', '撤回'].includes(state.seg)"
          type="warning"
          link
          icon="EditPen"
          @click="handleAction('修改申请', row)"
        >
          修改申请
        </el-button>

        <!--        <el-button-->
        <!--          v-if="state.seg === '审批通过'"-->
        <!--          type="primary"-->
        <!--          link-->
        <!--          icon="download"-->
        <!--          @click="handleAction('文件下载', row)"-->
        <!--        >-->
        <!--          文件下载-->
        <!--        </el-button>-->
        <!--        <el-button-->
        <!--          v-if="state.seg === '审批通过'"-->
        <!--          type="primary"-->
        <!--          link-->
        <!--          icon="view"-->
        <!--          @click="handleAction('查看调用信息', row)"-->
        <!--        >-->
        <!--          查看调用信息-->
        <!--        </el-button>-->
        <el-button
          v-if="state.seg === '审批中'"
          type="danger"
          link
          @click="handleAction('申请撤回', row)"
        >
          申请撤回
        </el-button>
        <el-button type="primary" link @click="handleAction('查看申请单', row)">
          查看申请单
        </el-button>
      </template>
    </page-list>

    <el-drawer v-model="state.open" size="400" title="查看进度">
      <time-line
        :list="state.nodes?.map((el) => ({ ...el, nodeType: el.skipType, org: el.orgName }))"
      />
    </el-drawer>

    <el-dialog v-model="state.view" :title="`${state.applyData.applyName}申请单`" width="60%">
      <apply-json-form :init-data="state.applyData" :editable="false" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted, watch, ref, h } from "vue";
import { useRouter } from "vue-router";
import { useRegisterModal } from "@/composables";
import { ElCheckbox } from "element-plus";

// 登记弹框
const { openRegisterModal, open } = useRegisterModal();
const router = useRouter();
const state = reactive<any>({
  loading: false,
  seg: "审批通过",
  keyword: "",
  data: [],
  fields: [],
  total: 0,
  pageNo: 1,
  pageSize: 20,
  // 查看进度
  open: false,
  nodes: [],

  // 查看申请单
  view: false,
  applyData: "",
});

// 监听关闭登记弹框
watch(open, (newVal, oldVal) => {
  if (newVal === false && oldVal) {
    handleAction("refresh");
  }
});

onMounted(() => {
  fetchList();
});

const handleAction = (type: string, row?: any) => {
  switch (type) {
    case "refresh":
      fetchList();
      break;
    case "detail":
      router.push({
        path: "detail",
        query: { type: "catalog", id: row.detail.tid, title: row.detail.catalogName },
      });
      break;
    case "再次申请":
      openRegisterModal({
        registerClass: "apply",
        registerData: {
          editType: "再次申请", // 复制一份申请单
          apply: { tid: row.tid },
        },
      });
      break;
    case "继续申请":
      openRegisterModal({
        registerClass: "apply",
        registerData: {
          apply: { tid: row.tid },
        },
      });
      break;
    case "修改申请":
      // 被驳回、撤回的申请单，处于节点1的审批中
      openRegisterModal({
        registerClass: "apply",
        registerData: {
          editType: "修改申请", // 修改同一份申请单
          apply: { tid: row.tid },
        },
      });
      break;
    case "删除":
      handleDraft(row);
      break;
    case "查看进度":
      state.open = true;
      $common
        .get("/sym/approval/info", {
          businessId: row.businessId,
        })
        .then(({ flowNodes }) => {
          state.nodes = flowNodes;
        });
      break;
    case "申请撤回":
      $common.handle({
        url: "/sym/approval/operate/handle",
        info: `是否撤回该申请【${row.applyName}】`,
        action: "撤回",
        data: {
          taskId: row.taskId,
          businessId: row.businessId,
          handleType: "REVOKE",
          message: "",
          approveType: row.approveType,
        },
        done: () => {
          handleAction("refresh");
        },
      });
      break;

    case "查看申请单":
      state.applyData = row;
      state.view = true;
      break;
  }
};

const fetchList = async () => {
  state.loading = true;
  try {
    state.data = [];
    const segMap: Record<string, number | null> = {
      审批通过: "2",
      审批中: "1",
      不通过: "3",
      被驳回: "5",
      撤回: "4",
      待提交: "0",
    };
    const targetStatus = segMap[state.seg] ?? undefined;

    // 接口返回的数据
    const { total, list } = await $common.post("/sym/approval/query/applyPage", {
      pageNum: state.pageNo,
      pageSize: state.pageSize,
      sortField: "updatedTime",
      sortDir: "desc",
      conditions: [
        {
          field: "flowStatus",
          value: targetStatus === undefined ? null : targetStatus,
          type: "match",
        },
        {
          field: "applyName",
          value: state.keyword,
          type: "like",
        },
      ],
    });
    state.data = list;
    state.total = total;
  } finally {
    state.loading = false;
  }
};
const handleDraft = async (item: any) => {
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

  // 刷新
  handleAction("refresh");
};

state.fields = [
  { title: "数源单位", field: "detail.orgId", options: "org" },
  { title: "资源类型", field: "detail.resType", options: "assetType" },
  { title: "共享类型", field: "detail.shareType", options: "shareType" },
  { title: "更新周期", field: "detail.updateCycle", options: "updateCycle" },
  { title: "应用系统", field: "appId", options: "app" },
  { title: "申请时间", field: "applyTime" },
];
</script>

<style scoped lang="scss">
:deep(.pl-container) {
  padding-top: 26px;
  .pl-toolbar {
    margin: 0 26px;
  }
  .pl-row {
    padding-left: 26px;
    padding-right: 26px;
  }
}
</style>
