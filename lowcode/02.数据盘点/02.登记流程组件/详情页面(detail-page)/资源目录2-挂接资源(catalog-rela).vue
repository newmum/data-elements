<template>
  <div class="card-container h-auto px-5 pt-4 flex-1 w-full flex flex-col">
    <!-- 挂接表 -->
    <div>
      <!-- 数据表信息和字段映射 -->
      <div
        v-if="state.catalogInfo.targetTableId"
        class="gap-5 w-full"
        style="display: grid; grid-template-columns: minmax(200px, 440px) minmax(200px, 1fr)"
      >
        <div>
          <!-- 操作按钮区域 -->
          <!--          <div v-if="props.editable" class="flex gap-3 mb-2">-->
          <!--            <el-button-->
          <!--              type="primary"-->
          <!--              class="flex-1"-->
          <!--              icon="Connection"-->
          <!--              @click="handleAction('挂接数据表')"-->
          <!--            >-->
          <!--              重新挂接-->
          <!--            </el-button>-->
          <!--            <el-button-->
          <!--              type="danger"-->
          <!--              plain-->
          <!--              class="flex-1"-->
          <!--              icon="Delete"-->
          <!--              @click="handleRemove('table', state.tableInfo?.tid)"-->
          <!--            >-->
          <!--              移除资源-->
          <!--            </el-button>-->
          <!--          </div>-->

          <!-- 资产详细信息卡片 -->
          <div class="asset-info-card">
            <div class="card-header">
              <div class="header-title">
                <span>资源信息</span>
              </div>
            </div>
            <div class="info-list-container">
              <div class="info-item-row">
                <div class="item-label">数据表名：</div>
                <el-text
                  v-if="state.tableInfo.tid"
                  type="primary"
                  class="item-value cursor-pointer"
                  truncated
                  @click="
                    handleAction('detail', {
                      id: state.tableInfo.tid,
                      type: 'table',
                      title: state.tableInfo.tableName,
                    })
                  "
                >
                  <Icon icon="table" />
                  {{ state.tableInfo.tableName }}
                </el-text>
              </div>
              <div class="info-item-row">
                <div class="item-label">数据库名：</div>
                <el-text
                  v-if="state.tableInfo.dbId"
                  type="primary"
                  truncated
                  class="cursor-pointer"
                  @click="
                    handleAction('detail', {
                      id: state.tableInfo.dbId,
                      type: 'db',
                      title: state.dbInfo.dbName,
                    })
                  "
                >
                  <Icon :icon="state.dbInfo.dbType" />
                  {{ state.dbInfo.dbName }}
                </el-text>
              </div>
              <div class="info-item-row">
                <div class="item-label">存储大小：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.storageSize"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">字段数量：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.fieldCount" suffix=" 个"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">记录数量：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.recordCount" suffix=" 条"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">更新周期：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.updateCycle"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">数据更新：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.dataUpdateCycle"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">DDL创建时间：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.ddlCreatedTime"></dict-label>
                </div>
              </div>
              <div class="info-item-row">
                <div class="item-label">DDL更新时间：</div>
                <div class="item-value">
                  <dict-label :model-value="state.tableInfo.ddlUpdatedTime"></dict-label>
                </div>
              </div>
            </div>
          </div>

          <!-- 删除建表按钮 -->
          <el-button
            v-if="props.editable && state.tableInfo.tid && props.dataSourceType === 'ods'"
            type="danger"
            plain
            style="width: 100%; margin-top: 12px"
            icon="Delete"
            @click="handleDeleteTable"
          >
            移除资源
          </el-button>
        </div>

        <div>
          <u-title name="数据项映射" class="pt-2! pb-4!">
            <template v-if="props.dataSourceType === 'ods'" #right>
              <el-button
                type="primary"
                :icon="'el-icon-CopyDocument'"
                link
                plain
                @click="state.ddlDialogVisible = true"
              >
                查看DDL创建语句
              </el-button>
            </template>
          </u-title>
          <data-table
            flex-type="flex-[1_1_auto]"
            show-index
            :show-page="false"
            :operation-width="80"
            :loading="state.loading.mapping"
            :columns="state.mappingCols"
            :data="state.tableFields"
          >
            <template #column-nullable="{ row }">
              <Icon v-if="row.nullable == 0" icon="yes" />
            </template>
            <template #column-primaryKey="{ row }">
              <Icon v-if="row.primaryKey == 1" icon="yes" />
            </template>
            <!-- <template #column-targetTableColumnId="{ row }">
              <el-select
                v-model="row.targetTableColumnId"
                placeholder="请选择"
                filterable
                style="width: 100%"
                @change="handleMappingChange"
              >
                <el-option
                  v-for="field in state.tableFields"
                  :key="field.tid"
                  :label="field.columnName"
                  :value="field.tid"
                  :disabled="
                    state.mappingData.some(
                      (m: any) => m.targetTableColumnId === field.tid && m.tid !== row.tid
                    )
                  "
                />
              </el-select>
            </template>
            <template #operation="scope">
              <el-button type="primary" link @click="handleResetMapping(scope.row)">重置</el-button>
            </template> -->
          </data-table>
        </div>
      </div>

      <!-- 未挂接表时的提示 -->
      <div v-else class="no-table">
        <empty description="暂无关联数据表">
          <el-link
            v-if="props.dataSourceType !== 'ods'"
            :disabled="!props.editable"
            @click="handleAction('挂接数据表')"
          >
            挂接数据表
            <Icon icon="el-icon-ArrowRight" />
          </el-link>
        </empty>
      </div>
    </div>

    <!-- 挂接API -->
    <div>
      <u-title name="API服务">
        <template v-if="state.api.data.length" #right>
          <el-link :disabled="!props.editable" @click="handleAction('挂接API')">
            <Icon icon="el-icon-Connection" />
            API服务
          </el-link>
        </template>
      </u-title>
      <data-table
        flex-type="flex-[1_1_auto]"
        show-index
        :show-page="false"
        :loading="state.loading.api"
        :columns="state.api.cols"
        :data="state.api.data"
      >
        <template #empty>
          <empty description="暂无关联API服务">
            <el-link :disabled="!props.editable" @click="handleAction('挂接API')">
              挂接API服务
              <Icon icon="el-icon-ArrowRight" />
            </el-link>
          </empty>
        </template>
      </data-table>
    </div>

    <!-- 挂接文件 -->
    <div>
      <u-title name="文件">
        <template v-if="state.file.data.length" #right>
          <el-link :disabled="!props.editable" @click="handleAction('挂接文件')">
            <Icon icon="el-icon-Connection" />
            文件
          </el-link>
        </template>
      </u-title>
      <data-table
        flex-type="flex-[1_1_auto]"
        show-index
        :show-page="false"
        :loading="state.loading.file"
        :columns="state.file.cols"
        :data="state.file.data"
      >
        <template #empty>
          <empty description="暂无关联文件">
            <el-link :disabled="!props.editable" @click="handleAction('挂接文件')">
              挂接文件
              <Icon icon="el-icon-ArrowRight" />
            </el-link>
          </empty>
        </template>
      </data-table>
    </div>

    <!-- 抽屉容器 -->
    <el-drawer
      v-model="state.open"
      :title="drawerTitle"
      size="40%"
      :show-close="false"
      body-class="p-0!"
    >
      <template #header>
        <h4>{{ drawerTitle }}</h4>
        <el-input
          v-if="state.currentType !== 'mapping'"
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
                <empty v-if="!state.leftList.length && !state.loading[state.currentType]" />
                <div
                  v-for="item in state.leftList"
                  :key="item.id"
                  class="list-item"
                  :class="{ active: state.activeId === item.id }"
                  @click="handleAction('click-left', item)"
                >
                  <div class="list-item-icon">
                    <Icon :icon="item.assetType === 'db' ? item.dbType : item.assetType" />
                  </div>
                  <div class="list-item-content">
                    <div class="list-item-title">{{ item[`${item.assetType}Name`] }}</div>
                    <el-text v-if="item.assetType === 'db'" type="info">
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
                v-infinite-scroll="() => handleAction('load-more-right')"
                :infinite-scroll-disabled="
                  !state.hasMoreRight || !state.activeId || state.loading.rightLoadMore
                "
                :infinite-scroll-distance="50"
                class="h-full"
              >
                <div
                  v-for="item in state.rightList"
                  :key="item.id"
                  class="list-item"
                  :class="{
                    active:
                      item.tid === state.catalogInfo.targetTableId ||
                      state.api.data.includes(item.tid) ||
                      state.file.data.includes(item.tid),
                  }"
                  @click="handleAction('click-right', item)"
                >
                  <div class="list-item-icon">
                    <Icon :icon="item.assetType" />
                  </div>
                  <div class="list-item-content">
                    <div class="list-item-title">{{ item[`${item.assetType}Name`] }}</div>
                    <el-text v-if="item.assetType === 'table'" type="info">
                      {{ item.tableNameCn }}
                    </el-text>
                  </div>
                </div>
                <div v-if="state.loading.rightLoadMore" class="loading-more">
                  <el-skeleton :rows="3" animated />
                </div>
                <empty v-else-if="!state.rightList.length && !state.loading[state.currentType]" />
                <div v-else-if="!state.hasMoreRight && state.rightList.length > 0" class="no-more">
                  -- 没有更多数据了 --
                </div>
              </div>
            </el-scrollbar>
          </el-splitter-panel>
        </el-splitter>
      </div>
    </el-drawer>
    <!-- DDL创建语句弹窗 -->
    <ddl-dialog
      v-model="state.ddlDialogVisible"
      :table-info="state.tableInfo"
      :db-info="state.dbInfo"
      :table-fields="state.tableFields"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from "vue";
import { useRouter } from "vue-router";
import { useDetailStore } from "@/store";
import { useDetailDialog } from "@/composables";

const { openDetailDialog } = useDetailDialog();

const router = useRouter();
const props = defineProps({
  catalogId: {
    type: String,
  },
  dataSourceType: {
    type: String,
    default: "",
  },
  editable: {
    type: Boolean,
    default: true,
  },
});

const state = reactive<any>({
  open: false,
  loading: {
    table: false,
    api: false,
    file: false,
    mapping: false,
    leftLoadMore: false,
    rightLoadMore: false,
  },
  leftPage: 1,
  rightPage: 1,
  pageSize: 20,
  hasMoreLeft: true,
  hasMoreRight: false,
  table: {
    cols: [
      { label: "表名称", prop: "assetName", minWidth: 150 },
      { label: "数据库", prop: "dbName" },
      { label: "更新时间", prop: "updateTime" },
      {
        label: "操作",
        prop: "action",
        buttons: [
          {
            type: "danger",
            link: true,
            label: "移除",
            click(row: any) {
              handleRemove("table", row.id);
            },
          },
        ],
      },
    ],
    data: [],
  },
  api: {
    cols: [
      { label: "API服务名称", prop: "assetName", minWidth: 150 },
      { label: "参数数量", prop: "count" },
      { label: "更新时间", prop: "updateTime" },
      {
        label: "操作",
        prop: "action",
        buttons: [
          {
            type: "danger",
            link: true,
            label: "移除",
            click(row: any) {
              handleRemove("api", row.id);
            },
          },
        ],
      },
    ],
    data: [],
  },
  file: {
    cols: [
      { label: "文件名称", prop: "assetName", minWidth: 150 },
      { label: "文件大小", prop: "fileSize" },
      { label: "更新时间", prop: "updateTime" },
      {
        label: "操作",
        prop: "action",
        buttons: [
          {
            type: "danger",
            link: true,
            label: "移除",
            click(row: any) {
              handleRemove("file", row.id);
            },
          },
        ],
      },
    ],
    data: [],
  },
  tableInfo: {}, // 数据表详细信息
  dbInfo: {}, // 数据库详细信息
  catalogInfo: {}, // 目录信息
  targetTableId: "", // 挂接的表ID
  mappingCols: [
    { label: "数据项名称", prop: "columnName" },
    { label: "数据项英文名", prop: "columnComment" },
    { label: "数据类型", prop: "dataType" },
    { label: "长度", prop: "length" },
    { label: "精度", prop: "scale" },
    {
      label: "主键",
      prop: "primaryKey",
    },
    {
      label: "不为空",
      prop: "nullable",
    },
    // {
    //   label: "字段映射",
    //   prop: "targetTableColumnId",
    //   width: 200,
    // },
  ], // 映射关系数据
  mappingViewMode: "list" as "list" | "grid", // 映射表格视图模式：list-列表，grid-网格
  tableFields: [], // 表字段列表
  rightList: [],
  leftList: [],
  keyword: "",
  activeId: "",
  currentType: "", // 当前操作类型：table、api、file
  ddlDialogVisible: false, // DDL弹窗可见性
});

onMounted(() => {
  handleAction("init");
});

// 计算抽屉标题
const drawerTitle = computed(() => {
  switch (state.currentType) {
    case "table":
      return "挂接数据表";
    case "api":
      return "挂接API服务";
    case "mapping":
      return "设置数据项映射";
    case "file":
      return "挂接文件";
    default:
      return "";
  }
});

// 打开挂接抽屉
const openAttachDrawer = (type: string) => {
  state.currentType = type;
  state.open = true;
  state.keyword = "";
  loadLeftList(type);
};

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init":
      // 初始化获取挂接表、关联API、关联文件资源
      initTable();
      initApi();
      initFile();
      break;
    case "detail":
      if (props.dataSourceType === "dwm") {
        openDetailDialog({ ...item, dataSourceType: props.dataSourceType });
      } else {
        router.push({ path: "detail", query: item });
      }
      break;
    case "挂接数据表":
      openAttachDrawer("table");
      break;
    case "挂接API":
      openAttachDrawer("api");
      break;
    case "挂接文件":
      openAttachDrawer("file");
      break;
    case "click-left":
      state.activeId = item.id;
      // 根据左侧查询右侧
      loadRightList(state.currentType, item.id, state.keyword);
      break;
    case "click-right":
      // 触发挂接操作
      handleAttach(state.currentType, item);
      break;
    case "search":
      // 根据关键字搜索右侧列表
      if (state.activeId) {
        loadRightList(state.currentType, state.activeId, state.keyword);
      }
      break;
    case "load-more-left":
      loadLeftList(state.currentType, state.keyword, true);
      break;
    case "load-more-right":
      loadRightList(state.currentType, state.activeId, state.keyword, true);
      break;
  }
};

// 初始化获取挂接表
const initTable = async () => {
  if (!props.catalogId) return;

  state.loading.table = true;
  try {
    // 调用获取目录详情的接口，获取已挂接的表
    const res = await $common.post("/dst/catalog/detail", {
      tid: props.catalogId,
    });

    // 保存目录信息
    state.catalogInfo = res || {};

    // 同时获取目录数据项信息
    await $common
      .post("/dst/catalog/catalog-items/list", {
        tid: props.catalogId,
      })
      .then((itemRes) => {
        // 将目录数据项信息合并到目录信息中
        state.catalogInfo.catalogItems = itemRes || [];
      });

    if (res?.targetTableId) {
      // 如果有挂接表，获取表的详情
      const tableRes = await $common.post("/dst/catalog/detail", {
        tid: res.targetTableId,
      });
      // 保存表的详细信息
      state.tableInfo = tableRes;

      if (tableRes.dbId) {
        // 获取库详情
        $common
          .post("/dst/catalog/detail", {
            tid: tableRes.dbId,
          })
          .then((data) => {
            state.dbInfo = data;
          });

        // 获取数据项映射关系
        await initMapping();
      }
    } else {
      state.tableInfo = {};
      state.mappingData = [];
    }
  } catch (error) {
    console.error("获取挂接表失败", error);
    $message.error("获取挂接表失败");
  } finally {
    state.loading.table = false;
  }
};

// 初始化获取数据项映射关系
const initMapping = async () => {
  if (!props.catalogId) return;

  state.loading.mapping = true;
  try {
    // 调用获取目录数据项的接口
    state.mappingData = state.catalogInfo.catalogItems?.map((item: any) => ({
      ...item,
      targetTableColumnId: item.targetTableColumnId || "",
    }));

    // 加载表字段列表
    await loadTableFields();
  } catch (error) {
    console.error("获取映射关系失败", error);
    $message.error("获取映射关系失败");
  } finally {
    state.loading.mapping = false;
  }
};

// 加载表字段信息
const loadTableFields = async () => {
  if (!state.tableInfo.tid) return;

  try {
    // 调用获取表字段的接口
    const fieldRes = await $common.get("/dst/database/metadata/columns", {
      tid: state.tableInfo.tid,
    });
    // 将字段信息存储到状态中
    state.tableFields = fieldRes || {};
  } catch (error) {
    console.error("获取表字段失败", error);
    $message.error("获取表字段失败");
  }
};

// 初始化获取关联API
const initApi = async () => {
  if (!props.catalogId) return;

  state.loading.api = true;
  try {
    // 调用获取API服务列表的接口，根据catalogId匹配
    const apiRes = await $common.post("/dst/catalog/list", {
      assetType: "api",
      catalogId: props.catalogId,
    });

    if (apiRes && apiRes.length > 0) {
      state.api.data = apiRes.map((api: any) => ({
        id: api.tid,
        assetName: api.assetName,
        count: api.paramCount || 0,
        updateTime: api.updateTime,
      }));
    } else {
      state.api.data = [];
    }
  } catch (error) {
    console.error("获取关联API失败", error);
    $message.error("获取关联API失败");
  } finally {
    state.loading.api = false;
  }
};

// 初始化获取关联文件
const initFile = async () => {
  if (!props.catalogId) return;

  state.loading.file = true;
  try {
    // 调用获取文件列表的接口，根据catalogId匹配
    const fileRes = await $common.post("/dst/catalog/list", {
      assetType: "file",
      catalogId: props.catalogId,
    });

    if (fileRes && fileRes.length > 0) {
      state.file.data = fileRes.map((file: any) => ({
        id: file.tid,
        assetName: file.assetName,
        fileSize: file.fileSize || "",
        updateTime: file.updateTime,
      }));
    } else {
      state.file.data = [];
    }
  } catch (error) {
    console.error("获取关联文件失败", error);
    $message.error("获取关联文件失败");
  } finally {
    state.loading.file = false;
  }
};

// 处理映射关系变更
const handleMappingChange = () => {
  saveMapping();
};

// 重置映射
const handleResetMapping = (row: any) => {
  row.targetTableColumnId = "";
  handleMappingChange();
};

// 保存映射关系
const saveMapping = async (closeDrawer = false) => {
  try {
    // 调用保存映射的接口
    await $common.post("/dst/catalog/catalog-items/save", {
      tid: props.catalogId,
      catalogId: props.catalogId,
      catalogItems: state.mappingData.map((item) => ({
        ...item,
        targetTableColumnId: item.targetTableColumnId || "",
      })),
    });
    $message.success("操作成功");
    if (closeDrawer) {
      state.open = false;
    }
    // 重新初始化映射数据
    // await initMapping();
  } catch (err) {
    $message.error("映射失败");
    console.error(err);
  }
};

// 加载左侧列表（使用资产列表接口）
const loadLeftList = async (type: string, keyword: string = "") => {
  state.loading[type] = true;
  try {
    let data = [];
    const orgId = $user.orgId || "";

    switch (type) {
      case "table":
        // 调用获取数据库列表的接口（使用列表接口）
        data = await $common.post("/dst/catalog/list", {
          assetType: "db",
          ...(keyword ? { dbName: keyword } : {}),
        })
        data = data?.filter((item: any) => item.orgId === orgId);
        break;
      case "api":
        // 调用获取API服务列表的接口（使用列表接口）
        data = await $common.post("/dst/catalog/list", {
          assetType: "app",
          ...(keyword ? { appName: keyword } : {}),
        });
        break;
      case "file":
        // 调用获取文件列表的接口（使用列表接口）
        data = await $common.post("/dst/catalog/list", {
          assetType: "file",
          ...(keyword ? { fileCatalogName: keyword } : {}),
        });
        break;
    }
    state.leftList = data || [];
    state.rightList = [];
    state.activeId = "";
    // 默认选中左侧第一个列表项并加载右侧列表
    if (state.leftList.length > 0) {
      state.activeId = state.leftList[0].id;
      loadRightList(type, state.activeId, state.keyword);
    }
  } catch (error) {
    console.error("加载列表失败", error);
    $message.error("加载列表失败");
  } finally {
    state.loading[type] = false;
  }
};

// 加载右侧列表
const loadRightList = async (
  type: string,
  parentId: string,
  keyword: string,
  loadMore: boolean = false
) => {
  if (!loadMore) {
    state.loading[type] = true;
    state.rightPage = 1;
    state.hasMoreRight = true;
  } else {
    if (!state.hasMoreRight || state.loading.rightLoadMore || !state.activeId) return;
    state.loading.rightLoadMore = true;
  }
  try {
    let data = [];
    const pageNum = loadMore ? state.rightPage + 1 : 1;

    switch (type) {
      case "table":
        // 调用获取数据库下表的列表接口
        data = await $common.post("/dst/catalog/page", {
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
              value: parentId,
              type: "match",
            },
            ...(keyword ? [{ field: "tableName", value: keyword, type: "like" }] : []),
          ],
        });
        break;
      case "api":
        // 调用获取API列表的接口
        data = await $common.post("/dst/catalog/page", {
          sortField: "updatedTime",
          sortDir: "desc",
          pageNum,
          pageSize: state.pageSize,
          conditions: [
            {
              field: "assetType",
              value: "api",
              type: "match",
            },
            {
              field: "catalogId",
              value: props.catalogId,
              type: "match",
            },
            ...(keyword ? [{ field: "apiName", value: keyword, type: "like" }] : []),
          ],
        });
        break;
      case "file":
        // 调用获取文件列表的接口
        data = await $common.post("/dst/catalog/page", {
          sortField: "updatedTime",
          sortDir: "desc",
          pageNum,
          pageSize: state.pageSize,
          conditions: [
            {
              field: "assetType",
              value: "file",
              type: "match",
            },
            {
              field: "catalogId",
              value: props.catalogId,
              type: "match",
            },
            ...(keyword ? [{ field: "fileName", value: keyword, type: "like" }] : []),
          ],
        });
        break;
    }
    const newData = data?.list || [];
    if (loadMore) {
      state.rightList = [...state.rightList, ...newData];
      state.rightPage++;
    } else {
      state.rightList = newData;
    }
    state.hasMoreRight = newData.length === state.pageSize;
  } catch (error) {
    console.error("加载列表失败", error);
    $message.error("加载列表失败");
  } finally {
    state.loading[type] = false;
    state.loading.rightLoadMore = false;
  }
};

// 处理挂接操作
const handleAttach = async (type: string, item: any) => {
  if (!props.catalogId) return;

  if (type === "table" && state.catalogInfo.targetTableId) {
    // 数据表挂接时，检查是否已存在挂接表
    const confirmResult = await $dialog.confirm(
      "当前目录已挂接数据表，重新挂接表可能会造成流程异常，请谨慎操作！",
      "是否替换为新表？",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    if (confirmResult !== "confirm") {
      return;
    }
  }

  try {
    // 调用挂接接口
    await $common.post("/dst/catalog/saveOrUpdate", {
      tid: props.catalogId,
      assetType: "catalog",
      propList: {
        [type === "table" ? "targetTableId" : type === "api" ? "apiId" : "fileId"]: item.id,
      },
    });

    $message.success("挂接成功");

    // 更新目录信息
    $common.post("/dst/catalog/detail", { tid: props.catalogId }).then((data) => {
      nextTick(() => {
        state.catalogInfo = { ...data };
        useDetailStore().setData(data);
      });
    });

    state.open = false;
    // 重新初始化数据
    if (type === "table") {
      initTable();
    } else if (type === "api") {
      initApi();
    } else if (type === "file") {
      initFile();
    }
  } catch (error) {
    console.error("挂接失败", error);
    $message.error("挂接失败");
  }
};

// 处理移除操作
const handleRemove = async (type: string, id: string) => {
  if (!props.catalogId) return;

  try {
    // 显示确认提示
    await $dialog.confirm(
      `确定要移除该${type === "table" ? "数据表" : type === "api" ? "API服务" : "文件"}吗？`,
      "确认移除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    // 调用移除接口
    await $common.post("/dst/catalog/saveOrUpdate", {
      tid: props.catalogId,
      assetType: "catalog",
      propList: {
        [type === "table" ? "targetTableId" : type === "api" ? "apiId" : "fileId"]: "",
      },
    });
    $message.success("移除成功");
    // 重新初始化数据
    if (type === "table") {
      initTable();
    } else if (type === "api") {
      initApi();
    } else if (type === "file") {
      initFile();
    }
  } catch (error) {
    console.error("移除失败", error);
  }
};

// 删除建表
const handleDeleteTable = () => {
  $common.handle({
    url: "/ods/cleanup-rebuild",
    info: "删除挂接资源，任务同步删除，是否继续？",
    action: "移除",
    data: {
      tid: props.catalogId,
    },
    done: () => {
      state.catalogInfo.targetTableId = "";
    },
  });
};
</script>

<style scoped lang="scss">
.card-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 24px;
  height: auto !important;
}

.custom-btn {
  height: 36px;
  border-radius: 6px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.asset-info-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;

  .card-header {
    padding: 14px 20px;
    background: linear-gradient(to right, #f8faff, #ffffff);
    border-bottom: 1px solid #f0f2f5;

    .header-title {
      display: flex;
      align-items: center;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }
  }

  .info-list-container {
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .info-item-row {
    display: flex;
    align-items: center;
    line-height: 1.6;

    .item-label {
      flex-shrink: 0;
    }
  }

  .divider {
    height: 1px;
    background: #f0f2f5;
    margin: 4px 0;
  }
}

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

    &-title {
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &-desc {
      font-size: 12px;
      color: #909399;
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

.mapping-table-card {
  flex: 1;
}

/* 映射设置容器样式 */
.mapping-container {
  height: 100%;

  &-content {
    padding: 20px;
  }

  &-item {
    margin-bottom: 16px;

    &-label {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 14px;
    }
  }
}

.data-type {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mapping-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 视图切换按钮样式 */
.active {
  background-color: var(--el-color-primary);
  color: #fff;
}

/* 挂接操作容器样式 */
.attach-container {
  height: 100%; /* 设置固定高度 */

  .el-splitter {
    height: 100%;

    .el-splitter-panel {
      height: 100%;
      overflow: hidden;
    }
  }
}
</style>
