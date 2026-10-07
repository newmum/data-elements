<template>
  <div v-loading="state.loading" class="card-container py-2 px-5">
    <div>
      <u-title name="库表资源信息"></u-title>
      <data-table
        show-index
        :show-page="false"
        :columns="state.cols"
        :data="state.data"
        size="large"
      >
        <template #column-tableNameCn="{ row }">
          <el-text>
            <Icon icon="table" />
            {{ row.tableNameCn }}
          </el-text>
        </template>
      </data-table>
    </div>

    <!--    <div>-->
    <!--      <u-title name="接口资源信息"></u-title>-->
    <!--    </div>-->
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from "vue";

const props = defineProps({
  catalogId: String,
});

const state = reactive<any>({
  loading: false,
  cols: [],
  data: [],
  api: {},
  reqCols: [],
  reqData: [],
  respCols: [],
  respData: [],
});

state.cols = [
  { label: "资源名称", prop: "tableNameCn" },
  { label: "库表名称", prop: "tableName" },
  { label: "数据库类型", prop: "dbType" },
  { label: "字段数", prop: "fieldCount", maxWidth: 200, props: { suffix: "个" } },
  { label: "数据量", prop: "recordCount", maxWidth: 120, props: { suffix: "条" } },
];

state.data = [
  {
    assetName: "广电家庭用户收视行为统计数据表",
    tableName: "statistical_radio_users_table",
    dbType: "oracle",
    columnTotal: "14",
    dataTotal: "18",
  },
];

onMounted(() => {
  init();
});

const init = async () => {
  if (!props.catalogId) return;
  state.loading = true;
  try {
    const catalog = await $common.post("/dst/catalog/detail", {
      tid: props.catalogId,
    });
    const table = await $common.post("/dst/catalog/detail", {
      tid: catalog.targetTableId,
    });
    const db = await $common.post("/dst/catalog/detail", {
      tid: table.dbId,
    });
    state.data = [
      {
        ...table,
        dbType: db.dbType,
      },
    ];
  } finally {
    state.loading = false;
  }
};

state.api = {
  name: "lyy服务api",
  endpoint: "http://192.168.173.89:10081/index/#/center/workplace/myAsset/myAssetList",
  method: "get",
  serviceType: "http",
};

state.reqCols = [
  { label: "序号", prop: "index", maxWidth: 120 },
  { label: "参数名称", prop: "name" },
  { label: "参数描述", prop: "desc" },
  { label: "参数类型", prop: "type", maxWidth: 160 },
  { label: "参数位置", prop: "in", maxWidth: 160 },
  { label: "必填属性", prop: "required", maxWidth: 160 },
  { label: "默认值", prop: "defaultValue" },
];

state.reqData = [
  {
    index: 1,
    name: "id",
    desc: "编号",
    type: "string",
    in: "body",
    required: "必填",
    defaultValue: "",
  },
];

state.respCols = [
  { label: "序号", prop: "index", maxWidth: 120 },
  { label: "响应状态码", prop: "code", maxWidth: 160 },
  { label: "状态码描述", prop: "desc" },
];

state.respData = [
  { index: 1, code: "500", desc: "服务器错误" },
  { index: 2, code: "400", desc: "非法请求" },
  { index: 3, code: "401", desc: "认证失败" },
  { index: 4, code: "403", desc: "禁止，没有权限操作" },
  { index: 5, code: "404", desc: "找不到" },
  { index: 6, code: "200", desc: "请求成功" },
];
</script>

<style scoped lang="scss">
:deep(.el-table) {
  .el-table__inner-wrapper::before {
    display: none;
  }
}
</style>
