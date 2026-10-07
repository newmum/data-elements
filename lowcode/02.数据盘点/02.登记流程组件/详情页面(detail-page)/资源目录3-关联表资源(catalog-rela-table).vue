<template>
  <div class="card-container p-5">
    <data-table
      ref="tableRef"
      flex-type="flex-[0_1_auto]"
      show-index
      :loading="state.loading.data"
      :columns="state.cols"
      :data="fetchData"
      :show-page="false"
      show-overflow-tooltip
    >
      <template #column-tableName="{ row }">
        <el-text
          type="primary"
          cursor-pointer
          @click="handleAction('detail', { id: row.tableId, type: 'table', title: row.tableName })"
        >
          <Icon icon="table" />
          {{ row.tableName }}
        </el-text>
      </template>
      <template #column-dbName="{ row }">
        <el-text
          type="primary"
          cursor-pointer
          @click="handleAction('detail', { id: row.dbId, type: 'db', title: row.dbName })"
        >
          <Icon icon="db" />
          {{ row.dbName }}
        </el-text>
      </template>
      <template #column-appName="{ row }">
        <el-text
          type="primary"
          cursor-pointer
          @click="handleAction('detail', { id: row.appId, type: 'app', title: row.appName })"
        >
          <Icon icon="app" />
          {{ row.appName }}
        </el-text>
      </template>
      <template #column-relaType="{ row, column }">
        <el-select
          v-model="row.relaType"
          placeholder="请选择"
          filterable
          clearable
          style="width: 100%"
          @change="(val) => handleAction('关联关系', val)"
        >
          <el-option
            v-for="field in column.options"
            :key="field.value"
            :label="field.label"
            :value="field.value"
            :disabled="
              state.data.some(
                (d: any) => d.relaType === '0' && field.label === '业务表' && d.tid !== row.tid
              )
            "
          />
        </el-select>
      </template>
    </data-table>
    <div class="mt-4">
      <el-link v-if="props.editable" @click="handleAction('选择数据表')">选择数据表</el-link>
    </div>

    <!-- 选择数据表抽屉 -->
    <el-drawer
      v-model="state.drawerOpen"
      title="选择数据表"
      size="40%"
      :show-close="false"
      body-class="p-0!"
    >
      <template #header>
        <div class="flex gap-4">
          <h4>选择数据表</h4>
          <el-button type="primary" @click="handleAction('save')">保存</el-button>
        </div>

        <el-input
          v-model="state.keyword"
          placeholder="请输入关键字"
          suffix-icon="Search"
          style="width: 250px"
          @input="handleAction('search')"
        />
      </template>

      <!-- 挂接操作内容 -->
      <div class="attach-container">
        <!-- 左右分栏容器 -->
        <el-splitter class="h-full">
          <el-splitter-panel>
            <el-scrollbar class="h-full">
              <div class="h-full">
                <empty v-if="!state.dbList.length && !state.loading.db" />
                <div
                  v-for="item in state.dbList"
                  :key="item.id"
                  class="list-item"
                  :class="{ active: state.activeDbId === item.id }"
                  @click="handleAction('click-db', item)"
                >
                  <div class="list-item-icon">
                    <Icon :icon="item.dbType" />
                  </div>
                  <div class="list-item-content">
                    <div class="list-item-title">{{ item.dbName }}</div>
                    <el-text type="info">
                      {{ item.database }}{{ item.schema ? ` (${item.schema})` : "" }}
                    </el-text>
                  </div>
                </div>
              </div>
            </el-scrollbar>
          </el-splitter-panel>
          <el-splitter-panel>
            <el-scrollbar class="h-full">
              <div
                v-infinite-scroll="() => handleAction('load-more-table')"
                :infinite-scroll-disabled="
                  !state.hasMoreTable || !state.activeDbId || state.loading.tableLoadMore
                "
                :infinite-scroll-distance="50"
                class="h-full"
              >
                <div v-for="item in state.tableList" :key="item.id" class="list-item">
                  <div class="list-item-icon">
                    <el-checkbox
                      :model-value="isTableSelected(item)"
                      @change="handleAction('toggle-select', item)"
                    />
                  </div>
                  <div class="list-item-content">
                    <div class="list-item-title">
                      <Icon icon="table" />
                      {{ item.tableName }}
                    </div>
                    <el-text type="info">
                      {{ item.tableNameCn }}
                    </el-text>
                  </div>
                </div>
                <div v-if="state.loading.tableLoadMore" class="loading-more">
                  <el-skeleton :rows="3" animated />
                </div>
                <empty v-else-if="!state.tableList.length && !state.loading.table" />
                <div v-else-if="!state.hasMoreTable && state.tableList.length > 0" class="no-more">
                  -- 没有更多数据了 --
                </div>
              </div>
            </el-scrollbar>
          </el-splitter-panel>
        </el-splitter>
      </div>
    </el-drawer>

    <!-- 数据项映射弹框 -->
    <el-dialog v-model="state.mappingDialogOpen" title="数据项映射" width="900px">
      <data-table
        flex-type="flex-[1_1_auto]"
        show-index
        :show-page="false"
        :operation-width="80"
        :loading="state.loading.mapping"
        :columns="state.mappingCols"
        :data="state.mappingData"
      >
        <template #column-sourceTableColumnId="{ row }">
          <el-select
            v-model="row.sourceTableColumnId"
            placeholder="请选择"
            filterable
            clearable
            style="width: 100%"
            @change="handleMappingChange"
          >
            <el-option
              v-for="field in state.sourceTableFields"
              :key="field.tid"
              :label="field.columnName"
              :value="field.tid"
              :disabled="
                state.mappingData.some(
                  (m: any) => m.sourceTableColumnId === field.tid && m.tid !== row.tid
                )
              "
            />
          </el-select>
        </template>
        <template #operation="scope">
          <el-button type="primary" link @click="handleResetMapping(scope.row)">重置</el-button>
        </template>
      </data-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="state.mappingDialogOpen = false">取消</el-button>
          <el-button type="primary" @click="saveMapping">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { prop } from "@antv/x6/lib/common/dom/prop";
import { ref, onMounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { useDetailDialog } from "@/composables";

const { openDetailDialog } = useDetailDialog();
const router = useRouter();
const props = defineProps({
  catalogId: String,
  editable: {
    type: Boolean,
    default: false,
  },
  dataSourceType: {
    type: String,
    default: "",
  },
});

// refs
const tableRef = ref();
const state = reactive<any>({
  cols: [],
  data: [],
  editing: false,
  // 抽屉相关状态
  drawerOpen: false,
  keyword: "",
  activeDbId: "",
  dbList: [],
  tableList: [],
  tablePage: 1,
  pageSize: 20,
  hasMoreTable: false,
  loading: {
    data: false,
    db: false,
    table: false,
    tableLoadMore: false,
    mapping: false,
  },
  // 已选中的表ID集合，用于多选
  selectedTableIds: new Set<string>(),
  // 数据项映射弹框状态
  mappingDialogOpen: false,
  mappingData: [],
  mappingCols: [
    { label: "数据项名称", prop: "colName", minWidth: 120 },
    { label: "数据项英文名", prop: "colEn", minWidth: 150 },
    { label: "数据类型", prop: "colType", width: 120 },
    {
      label: "字段映射",
      prop: "sourceTableColumnId",
      width: 200,
    },
  ],
  sourceTableFields: [],
  currentMappingTable: null,
});

// 判断表是否已选中
const isTableSelected = (item: any) => {
  const tableId = item.tid;
  return state.selectedTableIds.has(tableId);
};

// 切换表的选中状态
const toggleTableSelection = (item: any) => {
  const tableId = item.tableId || item.tid;
  if (state.selectedTableIds.has(tableId)) {
    state.selectedTableIds.delete(tableId);
  } else {
    state.selectedTableIds.add(tableId);
  }
};

const handleAction = (type: string, row?: any) => {
  switch (type) {
    case "refresh":
      tableRef.value.refresh();
      break;
    case "detail":
      if (props.dataSourceType === "dwm") {
        openDetailDialog({ ...row, dataSourceType: props.dataSourceType });
      } else {
        router.push({ path: "detail", query: row });
      }
      break;
    case "选择数据表":
      openSelectTableDrawer();
      break;
    case "click-db":
      state.activeDbId = row.id;
      // 根据左侧库查询右侧表列表
      loadTableList(row.id, state.keyword);
      break;
    case "toggle-select":
      // 切换选中状态
      toggleTableSelection(row);
      break;
    case "search":
      // 根据关键字搜索右侧表列表
      if (state.activeDbId) {
        loadTableList(state.activeDbId, state.keyword);
      }
      break;
    case "load-more-table":
      loadTableList(state.activeDbId, state.keyword, true);
      break;
    case "save":
      // 保存选中的表
      saveSelectedTables();
      break;
    case "字段映射":
      openMappingDialog(row);
      break;
    case "移除":
      handleRemove(row);
      break;
    case "关联关系":
      saveRelaType();
      break;
  }
};

// 打开选择数据表抽屉
const openSelectTableDrawer = async () => {
  state.drawerOpen = true;
  state.keyword = "";
  // 重置选中状态
  state.selectedTableIds = new Set<string>();
  // 加载已关联的表数据
  state.data.forEach((item: any) => {
    state.selectedTableIds.add(item.tableId);
  });
  loadDbList();
};

// 打开数据项映射弹框
const openMappingDialog = async (row: any) => {
  state.currentMappingTable = row;
  state.mappingDialogOpen = true;
  state.loading.mapping = true;
  try {
    // 获取目录数据项列表
    const catalogItems = await $common.post("/dst/catalog/catalog-items/list", {
      tid: props.catalogId,
    });

    // 获取来源表字段列表
    const tableFields = await $common.get("/dst/database/metadata/columns", {
      tid: row.tableId,
    });

    state.sourceTableFields = tableFields || [];

    // 整理映射数据
    state.mappingData = (catalogItems || []).map((item: any) => ({
      ...item,
      sourceTableColumnId: item.sourceTableColumnId || "",
    }));
  } catch (error) {
    console.error("加载数据项映射失败", error);
    $message.error("加载数据项映射失败");
  } finally {
    state.loading.mapping = false;
  }
};

// 处理映射关系变更
const handleMappingChange = () => {
  // 实时保存或标记为已修改
};

// 重置映射
const handleResetMapping = (row: any) => {
  row.sourceTableColumnId = "";
};

// 保存映射关系
const saveMapping = async () => {
  try {
    await $common.post("/dst/catalog/saveOrUpdate", {
      tid: props.catalogId,
      catalogItems: state.mappingData.map((item: any) => ({
        ...item,
        sourceTableColumnId: item.sourceTableColumnId || "",
      })),
    });
    $message.success("保存成功");
    state.mappingDialogOpen = false;
  } catch (error) {
    console.error("保存映射失败", error);
    $message.error("保存映射失败");
  }
};

// 加载数据库列表
const loadDbList = async () => {
  state.loading.db = true;
  try {
    const data = await $common.post("/dst/catalog/list", {
      assetType: "db",
    });
    state.dbList = data || [];
    state.tableList = [];
    state.activeDbId = "";
    // 默认选中左侧第一个库并加载右侧表列表
    if (state.dbList.length > 0) {
      state.activeDbId = state.dbList[0].id;
      loadTableList(state.dbList[0].id, state.keyword);
    }
  } catch (error) {
    console.error("加载数据库列表失败", error);
    $message.error("加载数据库列表失败");
  } finally {
    state.loading.db = false;
  }
};

// 加载表列表
const loadTableList = async (dbId: string, keyword: string, loadMore: boolean = false) => {
  if (!dbId) return;

  if (!loadMore) {
    state.loading.table = true;
    state.tablePage = 1;
    state.hasMoreTable = true;
  } else {
    if (!state.hasMoreTable || state.loading.tableLoadMore || !state.activeDbId) return;
    state.loading.tableLoadMore = true;
  }

  try {
    const pageNum = loadMore ? state.tablePage + 1 : 1;
    const data = await $common.post("/dst/catalog/page", {
      sortField: "updatedTime",
      sortDir: "desc",
      pageNum,
      pageSize: state.pageSize,
      conditions: [
        {
          field: "assetType",
          value: "table",
          type: "match",
        },
        {
          field: "dbId",
          value: dbId,
          type: "match",
        },
        ...(keyword ? [{ field: "tableName", value: keyword, type: "like" }] : []),
      ],
    });

    const newData = data?.list || [];
    if (loadMore) {
      state.tableList = [...state.tableList, ...newData];
      state.tablePage++;
    } else {
      state.tableList = newData;
    }
    state.hasMoreTable = newData.length === state.pageSize;
  } catch (error) {
    console.error("加载表列表失败", error);
    $message.error("加载表列表失败");
  } finally {
    state.loading.table = false;
    state.loading.tableLoadMore = false;
  }
};

// 保存选中的表
const saveSelectedTables = async () => {
  if (!props.catalogId) return;

  try {
    // 整理数据：比对已勾选表和原始列表中的数据
    const mapping: { tableId: string; relaType: string }[] = [];

    // 遍历所有选中的表ID
    state.selectedTableIds.forEach((tableId: string) => {
      // 查找原始数据中是否已存在该表
      const originalItem = state.data.find((item: any) => item.tableId === tableId);

      if (originalItem) {
        // 已存在的表，保留原有的relaType
        mapping.push({
          tableId,
          relaType: originalItem.relaType || "",
        });
      } else {
        // 新增的表，relaType为空字符串
        mapping.push({
          tableId,
          relaType: "",
        });
      }
    });

    // 调用保存接口
    await $common.post("/dst/catalog/relations/save", {
      catalogId: props.catalogId,
      relaTableList: mapping,
    });

    $message.success("保存成功");
    state.drawerOpen = false;
    // 刷新列表数据
    tableRef.value?.refresh();
  } catch (error) {
    console.error("保存失败", error);
    $message.error("保存失败");
  }
};

// 移除关联表 - 复用保存接口
const handleRemove = async (row: any) => {
  try {
    await $dialog.confirm("确定要移除该关联表吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    // 获取当前所有关联表（排除要移除的）
    const remainingTables = state.data
      .filter((item: any) => item.tableId !== row.tableId)
      .map((item: any) => ({
        tableId: item.tableId,
        relaType: item.relaType || "",
      }));
    // 复用保存接口，传入剩余的表列表
    await $common.post("/dst/catalog/relations/save", {
      catalogId: props.catalogId,
      relaTableList: remainingTables,
    });

    $message.success("移除成功");
    tableRef.value?.refresh();
  } catch (error) {
    // 用户取消或操作失败
  }
};

// 保存关联关系
const saveRelaType = async () => {
  try {
    await $common.post("/dst/catalog/relations/save", {
      catalogId: props.catalogId,
      relaTableList: state.data.map((item: any) => ({
        tableId: item.tableId,
        relaType: item.relaType || "",
      })),
    });
    $message.success("操作成功");
  } catch (err) {
    console.error(err);
  }
};

// 列表接口
const fetchData = async () => {
  const list = await $common.post("/dst/catalog/relations/list", {
    catalogId: props.catalogId,
  });
  // 保存列表数据到state.data，用于关联关系选择
  state.data = list || [];
  return {
    list,
  };
};

// 表单规则
state.cols = [
  { label: "数据表名称", prop: "tableName", minWidth: 150 },
  { label: "表注释", prop: "tableNameCn" },
  {
    label: "所属库",
    prop: "dbName",
  },
  {
    label: "所属业务系统",
    prop: "appName",
  },
  {
    label: "关联关系",
    prop: "relaType",
    options: $dict.getDictItems("relaType"),
    width: 150,
  },
  {
    label: "操作",
    prop: "action",
    buttons: [
      {
        if(row: any) {
          return row.relaType === "0";
        },
        type: "primary",
        link: true,
        label: "字段映射",
        click: (row: any) => handleAction("字段映射", row),
      },
      {
        type: "danger",
        link: true,
        label: "移除",
        click: (row: any) => handleAction("移除", row),
      },
    ],
  },
];
</script>

<style scoped lang="scss">
.list-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.3s;
  color: #303133;
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--el-border-color);

  &:last-child {
    border-bottom: none;
  }

  &.active {
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &:hover {
    background-color: #f5f7fa;
  }

  &-icon {
    font-size: 20px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  &-content {
    flex: 1;
    min-width: 0;

    .list-item-title {
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}

.loading-more,
.no-more {
  padding: 16px;
  text-align: center;

  &.no-more {
    color: #909399;
    font-size: 14px;
  }
}

/* 挂接操作容器样式 */
.attach-container {
  height: 100%;

  .el-splitter {
    height: 100%;

    .el-splitter-panel {
      height: 100%;
      overflow: hidden;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
