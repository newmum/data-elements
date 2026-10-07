<template>
  <div class="data-standard-container">
    <div class="data-standard-content card-container px-5 pt-5 pb-2">
      <el-splitter style="width: 100%; height: 100%">
        <el-splitter-panel size="300px" min="10%">
          <div class="left-panel">
            <div class="panel-header flex-x-between">
              <h3>标准分类目录</h3>
              <el-button
                type="text"
                class="left-header-btn"
                @click="
                  categoryDrawerRef?.openAdd(currentNode?.tid !== '0' ? currentNode?.tid : '')
                "
              >
                <Icon icon="el-icon-plus" />
              </el-button>
            </div>

            <div class="tree-container">
              <UTree
                ref="treeRef"
                :data="treeData"
                :search="true"
                :expand="false"
                :highlight-current="true"
                :expand-on-click-node="false"
                :search-placeholder="'请输入搜索关键词'"
                :default-expand-all="true"
                :default-props="{
                  children: 'children',
                  label: 'label',
                }"
                class="tree-container-custom"
                @node-click="handleNodeClick"
              >
                <template #node="{ node }">
                  <div
                    class="custom-tree-node"
                    @mouseenter="hoveredNodeTid = node?.data?.tid"
                    @mouseleave="openDropdownTid !== node?.data?.tid && (hoveredNodeTid = null)"
                  >
                    <span class="node-label">{{ node?.label }}</span>
                    <div class="node-actions">
                      <el-dropdown
                        trigger="hover"
                        :hide-on-click="true"
                        @visible-change="
                          (visible) => {
                            openDropdownTid = visible ? node?.data?.tid : null;
                            if (!visible) hoveredNodeTid = null;
                          }
                        "
                        @command="(cmd) => handleDropdownCommand(cmd, node)"
                      >
                        <Icon
                          class="more-icon"
                          :class="{
                            visible:
                              hoveredNodeTid === node?.data?.tid ||
                              openDropdownTid === node?.data?.tid,
                          }"
                          icon="el-icon-MoreFilled"
                        />
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="edit">
                              <Icon icon="el-icon-Edit" class="mr-1" />
                              编辑
                            </el-dropdown-item>
                            <el-dropdown-item command="delete" class="delete-item">
                              <Icon icon="el-icon-Delete" class="mr-1" style="color: #f56c6c" />
                              <span style="color: #f56c6c">删除</span>
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                </template>
              </UTree>
            </div>
          </div>
        </el-splitter-panel>

        <el-splitter-panel>
          <div class="right-panel pl-4">
            <DataTable
              ref="tableRef"
              class="standard-main-table"
              :columns="tableColumns"
              :data="fetchTableData"
              :immediate="false"
              :show-index="true"
              style="height: 100%"
            >
              <template #toolbar>
                <div class="standard-list-toolbar">
                  <div class="standard-list-toolbar__status">
                    <el-segmented
                      v-model="state.activeStatus"
                      :options="state.statusOptions"
                      @change="refreshData"
                    ></el-segmented>
                  </div>
                  <div class="standard-list-toolbar__filters">
                    <el-input
                      v-model="state.keyword"
                      class="standard-keyword-input"
                      placeholder="请输入关键字"
                      clearable
                      @clear="refreshData"
                      @keyup.enter="refreshData"
                    >
                      <template #suffix>
                        <Icon icon="search" class="standard-search-icon" @click="refreshData" />
                      </template>
                    </el-input>
                    <div class="standard-list-toolbar__primary">
                      <el-button plain icon="Upload" @click="() => handleAction('数据元导入')">
                        导入
                      </el-button>
                      <el-button type="primary" icon="Plus" @click="() => handleAction('新增')">
                        新增
                      </el-button>
                    </div>
                  </div>
                </div>
              </template>
              <template #column-metaName="{ row }">
                <button type="button" class="standard-name-link" @click="handleAction('详情', row)">
                  {{ row.metaName }}
                </button>
              </template>
              <template #column-dictionaryCount="{ row }">
                <button
                  type="button"
                  class="dictionary-count-link"
                  :class="{ 'is-empty': !Number(row.dictionaryCount) }"
                  @mouseenter="dictionaryHoverTid = row.tid"
                  @mouseleave="dictionaryHoverTid = null"
                  @click="openDictionaryDrawer(row)"
                >
                  {{ Number(row.dictionaryCount) ? Number(row.dictionaryCount).toLocaleString("zh-CN") : dictionaryHoverTid === row.tid ? "新增" : "-" }}
                </button>
              </template>
            </DataTable>
          </div>
        </el-splitter-panel>
      </el-splitter>
    </div>

    <el-drawer
      v-model="dictionaryDrawer.visible"
      direction="rtl"
      size="680px"
      destroy-on-close
      :close-on-click-modal="true"
      class="dictionary-drawer"
    >
      <template #header>
        <div class="dictionary-drawer__header">
          <strong>{{ dictionaryDrawer.meta?.metaName || "数据元" }}的数据字典</strong>
          <span>共{{ dictionaryDrawer.values.length }}项</span>
        </div>
      </template>

      <DataTable
        ref="dictionaryTableRef"
        :columns="dictionaryColumns"
        :data="fetchDictionaryData"
        :immediate="false"
        :show-index="true"
        style="height: 100%"
      >
        <template #toolbar>
          <div class="flex justify-end">
            <el-button type="primary" icon="Plus" @click="openDictionaryForm()">新增代码</el-button>
          </div>
        </template>
      </DataTable>
    </el-drawer>

    <el-dialog
      v-model="dictionaryForm.visible"
      :title="dictionaryForm.originalCode ? '编辑代码' : '新增代码'"
      width="460px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="代码值" required>
          <el-input v-model.trim="dictionaryForm.code" maxlength="128" placeholder="请输入代码值" />
        </el-form-item>
        <el-form-item label="代码名称" required>
          <el-input v-model.trim="dictionaryForm.name" maxlength="255" placeholder="请输入代码名称" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model.trim="dictionaryForm.description" maxlength="500" placeholder="请输入说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictionaryForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="dictionaryForm.saving" @click="saveDictionaryItem">保存</el-button>
      </template>
    </el-dialog>

    <!-- 标准分类目录 增加/编辑 抽屉（独立组件） -->
    <standard-category-drawer
      ref="categoryDrawerRef"
      :tree-data="treeData"
      @saved="handleCategorySaved"
    />

    <!-- 数据元详情抽屉（独立组件） -->
    <MetaDetailDrawer ref="metaDetailDrawerRef" :tree-data="treeData" @saved="refreshData" />

    <!-- 数据元批量导入抽屉（独立组件） -->
    <MetaImportDrawer
      ref="importDrawerRef"
      @download-template="downloadImportTemplate"
      @parse="parseImportFiles"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, nextTick } from "vue";
import * as ExcelJS from "exceljs";
import { ElMessage, ElMessageBox } from "element-plus";
// import standardCategoryDrawer from "./standard-category-drawer.vue";
// import MetaImportDrawer from "./meta-import-drawer.vue";
// import MetaDetailDrawer from "./meta-detail-drawer.vue";

const state = reactive({
  keyword: undefined,
  activeStatus: undefined,
  statusOptions: [
    { label: "全部", value: undefined },
    { label: "待发布", value: 0 },
    { label: "已发布", value: 1 },
  ],
});
const currentNode = ref();
const tableRef = ref();
const treeRef = ref();
const treeData = ref<any[]>([]);

// The root "all" category is represented by tid/treePath "0".  It is a UI
// scope, not a real data category, so it must never be sent as an API filter.
const getSelectedCategoryPath = () => {
  const node = currentNode.value;
  return node && node.tid !== "0" && node.treePath !== "0" ? node.treePath || null : null;
};

// 树节点 hover 状态
const hoveredNodeTid = ref<string | null>(null);
// 下拉菜单当前打开的节点 tid（打开时不允许因 mouseleave 隐藏图标）
const openDropdownTid = ref<string | null>(null);

// 分类抽屉组件 ref
const categoryDrawerRef = ref<any>();

// 删除分类节点
const deleteCategoryNode = (node: any) => {
  const data = node.data || node;
  const label = data.label || data.dictName || "该节点";
  ElMessageBox.confirm(`确定要删除分类【${label}】吗？删除后不可恢复。`, "删除确认", {
    confirmButtonText: "确定删除",
    cancelButtonText: "取消",
    type: "warning",
    confirmButtonClass: "el-button--danger",
  })
    .then(async () => {
      await $common.post("/sym/dictSaveOrUpdate", {
        action: "delete",
        tid: data.tid,
      });
      ElMessage.success("删除成功");
      await fetchTreeData();
    })
    .catch(() => {});
};

// 树节点下拉菜单命令处理
const handleDropdownCommand = (command: string, node: any) => {
  if (command === "edit") {
    categoryDrawerRef.value?.openEdit(node.data || node);
  } else if (command === "delete") {
    deleteCategoryNode(node);
  }
};

// 根据分类ID获取完整路径
const getCategoryPath = (dataCategoryId: string): string => {
  if (!dataCategoryId || !treeData.value || treeData.value.length === 0) {
    return "";
  }

  const findNodePath = (nodes: any[], id: string, path: string[] = []): string[] | null => {
    for (const node of nodes) {
      // 跳过"全部"节点
      if (node.label === "全部") {
        if (node.children && node.children.length > 0) {
          const foundPath = findNodePath(node.children, id, path);
          if (foundPath) {
            return foundPath;
          }
        }
        continue;
      }

      if (node.tid === id) {
        return [...path, node.label];
      }
      if (node.children && node.children.length > 0) {
        const foundPath = findNodePath(node.children, id, [...path, node.label]);
        if (foundPath) {
          return foundPath;
        }
      }
    }
    return null;
  };

  const path = findNodePath(treeData.value, dataCategoryId);
  return path ? path.join(" / ") : "";
};
const tableColumns = [
  { prop: "standardEncode", label: "标准编码", showOverflowTooltip: true },
  { prop: "metaName", label: "数据元名称", showOverflowTooltip: true },
  { prop: "metaCode", label: "数据元英文名", showOverflowTooltip: true },
  { prop: "dictionaryCount", label: "数据字典（项）", width: 120, type: "num", precision: 0 },
  {
    prop: "fieldType",
    label: "类型/长度",
    showOverflowTooltip: true,
    formatter: (row: any) => {
      const type = row.fieldType || row.dataTypeId || "-";
      return row.fieldLength ? `${type} (${row.fieldLength})` : type;
    },
  },
  {
    prop: "publishStatus",
    label: "状态",
    width: 100,
    showOverflowTooltip: true,
    options: [
      { label: "待发布", value: 0, tagType: "error" },
      { label: "已发布", value: 1, tagType: "success" },
    ],
    props: { dot: true },
    align: "center",
  },
  {
    prop: "action",
    label: "操作",
    width: 100,
    align: "center",
    headerAlign: "center",
    buttons: [
      {
        label: "编辑",
        type: "primary",
        link: true,
        click: (row: any) => handleAction("详情", row),
        if(row: any) {
          return row.publishStatus === 0;
        },
      },
      {
        label: "下线",
        type: "danger",
        link: true,
        click: (row: any) => updatePublishStatus(row),
        if(row: any) {
          return row.publishStatus === 1;
        },
      },
      {
        label: "发布",
        type: "primary",
        link: true,
        click: (row: any) => updatePublishStatus(row),
        if(row: any) {
          return row.publishStatus === 0;
        },
      },
    ],
  },
];
// 批量导入抽屉组件 ref
const importDrawerRef = ref<any>();
// 数据元详情抽屉组件 ref
const metaDetailDrawerRef = ref<any>();
const dictionaryTableRef = ref<any>();
const dictionaryHoverTid = ref<string | null>(null);
const dictionaryDrawer = reactive({
  visible: false,
  meta: null as any,
  domain: null as any,
  values: [] as any[],
});
const dictionaryForm = reactive({
  visible: false,
  saving: false,
  originalCode: "",
  code: "",
  name: "",
  description: "",
});

const getApiData = (response: any) => response?.data || response || {};

const parseDictionaryValues = (raw: any) => {
  try {
    const values = typeof raw === "string" ? JSON.parse(raw || "[]") : raw;
    return Array.isArray(values)
      ? values.map((item) => ({
          code: String(item?.code ?? ""),
          name: String(item?.name ?? ""),
          description: item?.description || "",
        }))
      : [];
  } catch (error) {
    console.warn("数据字典格式不正确", error);
    return [];
  }
};

const loadDictionaryDomain = async (meta: any) => {
  dictionaryDrawer.domain = null;
  dictionaryDrawer.values = [];
  if (!meta?.valueDomainId) return;

  const response = await $common.post("/dwm/standard/code/queryById", { tid: meta.valueDomainId });
  const domain = getApiData(response);
  if (domain?.tid) {
    dictionaryDrawer.domain = domain;
    dictionaryDrawer.values = parseDictionaryValues(domain.dictItemValue);
  }
};

const openDictionaryDrawer = async (meta: any) => {
  dictionaryDrawer.meta = meta;
  dictionaryDrawer.visible = true;
  try {
    await loadDictionaryDomain(meta);
    await nextTick();
    dictionaryTableRef.value?.refresh(true);
  } catch (error: any) {
    ElMessage.error(error?.message || "读取数据字典失败");
  }
};

const fetchDictionaryData = async ({ pageNo, pageSize }: { pageNo: number; pageSize: number }) => {
  const values = dictionaryDrawer.values || [];
  const start = (pageNo - 1) * pageSize;
  return {
    list: values.slice(start, start + pageSize),
    total: values.length,
  };
};

const openDictionaryForm = (item?: any) => {
  dictionaryForm.originalCode = item?.code || "";
  dictionaryForm.code = item?.code || "";
  dictionaryForm.name = item?.name || "";
  dictionaryForm.description = item?.description || "";
  dictionaryForm.visible = true;
};

const dictionaryColumns = [
  { prop: "code", label: "代码值", minWidth: 140, showOverflowTooltip: true },
  { prop: "name", label: "代码名称", minWidth: 180, showOverflowTooltip: true },
  { prop: "description", label: "说明", minWidth: 180, showOverflowTooltip: true },
  {
    prop: "action",
    align: "right",
    label: "操作",
    width: 140,
    buttons: [
      { label: "编辑", type: "primary", link: true, click: (row: any) => openDictionaryForm(row) },
      { label: "删除", type: "danger", link: true, click: (row: any) => deleteDictionaryItem(row) },
    ],
  },
];

const persistDictionaryValues = async (values: any[]) => {
  const meta = dictionaryDrawer.meta;
  const dictItemValue = JSON.stringify(values);
  let domain = dictionaryDrawer.domain;

  if (domain?.tid) {
    const response = await $common.post("/dwm/standard/code/saveOrUpdate", {
      ...domain,
      dictItemValue,
      codeSet: domain.codeSet || domain.dictCode,
      codeValue: "__HEADER__",
      codeName: domain.codeName || domain.dictName,
    });
    domain = getApiData(response);
  } else {
    const codeSet = meta.standardCodeSet || meta.standardEncode || meta.metaCode;
    const response = await $common.post("/dwm/standard/code/saveOrUpdate", {
      dictName: `${meta.metaName}数据字典`,
      dictCode: codeSet,
      codeSet,
      codeValue: "__HEADER__",
      codeName: `${meta.metaName}数据字典`,
      dataCategoryId: meta.dataCategoryId,
      dataCategoryName: meta.dataCategoryName,
      dataCategoryPath: meta.dataCategoryPath,
      description: `${meta.metaName}的数据字典`,
      dictItemValue,
    });
    domain = getApiData(response);
    const savedMeta = await $common.post("/dwm/standard/element/metaSaveOrUpdate", {
      ...meta,
      valueDomainId: domain.tid,
      standardCodeSet: codeSet,
    });
    dictionaryDrawer.meta = getApiData(savedMeta);
  }

  dictionaryDrawer.domain = domain;
  dictionaryDrawer.values = values;
  await nextTick();
  dictionaryTableRef.value?.refresh(true);
  refreshData();
};

const saveDictionaryItem = async () => {
  const code = dictionaryForm.code.trim();
  const name = dictionaryForm.name.trim();
  if (!code || !name) {
    ElMessage.warning("请填写代码值和代码名称");
    return;
  }
  const duplicated = dictionaryDrawer.values.some(
    (item) => item.code === code && item.code !== dictionaryForm.originalCode
  );
  if (duplicated) {
    ElMessage.warning("代码值不能重复");
    return;
  }

  dictionaryForm.saving = true;
  try {
    const item = { code, name, description: dictionaryForm.description.trim() };
    const values = dictionaryForm.originalCode
      ? dictionaryDrawer.values.map((value) => (value.code === dictionaryForm.originalCode ? item : value))
      : [...dictionaryDrawer.values, item];
    await persistDictionaryValues(values);
    dictionaryForm.visible = false;
    ElMessage.success("保存成功");
  } catch (error: any) {
    ElMessage.error(error?.message || "保存数据字典失败");
  } finally {
    dictionaryForm.saving = false;
  }
};

const deleteDictionaryItem = (item: any) => {
  ElMessageBox.confirm(`确定删除代码值【${item.code}】吗？`, "删除确认", { type: "warning" })
    .then(async () => {
      await persistDictionaryValues(dictionaryDrawer.values.filter((value) => value.code !== item.code));
      ElMessage.success("删除成功");
    })
    .catch(() => {});
};

// 请求表格数据
const fetchTableData = async ({
  pageNo: pageNum,
  pageSize,
}: {
  pageNo: number;
  pageSize: number;
}) => {
  const response = await $common.post("/dwm/standard/element/page", {
    metaName: state.keyword || null,
    publishStatus: state.activeStatus || null,
    categoryPath: getSelectedCategoryPath(),
    pageNum,
    pageSize,
  });
  const data = getApiData(response);
  const list = Array.isArray(data.list) ? data.list : [];
  const total = data.total || 0;
  return {
    list,
    total,
  };
};

// 表格数据请求
const refreshData = () => {
  tableRef.value.refresh(false);
};

const importMetaHeaders = ["数据元名称*", "数据元英文名*", "标准编码*", "字段类型*", "长度*", "数据分类", "数据分级*", "可为空*", "业务定义", "格式规则", "示例值"];
const importDictionaryHeaders = ["数据元英文名*", "代码值*", "代码名称*", "说明"];
const supportedImportTypes = new Set(["varchar", "char", "integer", "bigint", "decimal", "date", "datetime", "boolean", "text"]);

const cellText = (value: any) => {
  if (value === undefined || value === null) return "";
  if (typeof value === "object") return String(value.text ?? value.result ?? "").trim();
  return String(value).trim();
};

const sheetRows = (sheet: any) => {
  const headers: string[] = [];
  sheet.getRow(1).eachCell({ includeEmpty: true }, (cell: any, index: number) => {
    headers[index] = cellText(cell.value).replace(/^\uFEFF/, "");
  });
  const rows: any[] = [];
  sheet.eachRow((row: any, rowNumber: number) => {
    if (rowNumber === 1) return;
    const item: Record<string, any> = { __rowNumber: rowNumber };
    let hasValue = false;
    headers.forEach((header, index) => {
      if (!header) return;
      const value = cellText(row.getCell(index).value);
      item[header] = value;
      hasValue ||= !!value;
    });
    if (hasValue) rows.push(item);
  });
  return rows;
};

const validateImportHeaders = (sheet: any, sheetName: string, requiredHeaders: string[]) => {
  if (!sheet) throw new Error(`缺少“${sheetName}”工作表`);
  const headers: string[] = [];
  sheet.getRow(1).eachCell({ includeEmpty: true }, (cell: any) => {
    const value = cellText(cell.value).replace(/^\uFEFF/, "");
    if (value) headers.push(value);
  });
  const missing = requiredHeaders.filter((header) => !headers.includes(header));
  if (missing.length) throw new Error(`“${sheetName}”缺少列：${missing.join("、")}`);
};

const downloadImportTemplate = async () => {
  try {
    const WorkbookClass = (ExcelJS as any).Workbook || (ExcelJS as any).default?.Workbook;
    if (!WorkbookClass) throw new Error("Excel 模板组件未加载");
    const workbook = new WorkbookClass();
    workbook.creator = "data-elements";
    const guide = workbook.addWorksheet("填写说明");
    guide.columns = [{ width: 20 }, { width: 100 }];
    guide.addRows([
      ["项目", "说明"],
      ["填写方式", "先填写“数据元”，再在“数据字典”中以数据元英文名关联代码值。带 * 的列为必填项。"],
      ["字段类型", "仅支持 varchar、char、integer、bigint、decimal、date、datetime、boolean、text。"],
      ["长度", "填写正整数；date、datetime 也需填写规范长度，例如 10、19。"],
      ["数据分级", "填写 1-4，或 L1-L4。可为空填写 是/否。"],
      ["数据分类", "可选；填写左侧标准分类目录中的分类名称。未填写时不绑定分类。"],
      ["数据字典", "代码值和代码名称成对填写；同一数据元下代码值不可重复。代码值建议设置为文本以保留前导零。"],
    ]);
    const metaSheet = workbook.addWorksheet("数据元");
    metaSheet.columns = importMetaHeaders.map((header: string) => ({ header, key: header, width: header.includes("定义") || header.includes("规则") ? 32 : 18 }));
    metaSheet.addRows([
      ["民族", "ethnic_group", "GB/T 3304", "varchar", "2", "", "2", "否", "公民民族类别", "", "汉族"],
      ["性别", "gender", "GB/T 2261.1", "varchar", "1", "", "2", "否", "公民性别", "", "男"],
    ]);
    const dictionarySheet = workbook.addWorksheet("数据字典");
    dictionarySheet.columns = importDictionaryHeaders.map((header: string) => ({ header, key: header, width: header === "说明" ? 36 : 22 }));
    dictionarySheet.addRows([["ethnic_group", "01", "汉族", ""], ["ethnic_group", "02", "蒙古族", ""], ["gender", "1", "男", ""], ["gender", "2", "女", ""]]);
    [guide, metaSheet, dictionarySheet].forEach((sheet: any) => {
      sheet.getRow(1).font = { bold: true, color: { argb: "FFFFFFFF" } };
      sheet.getRow(1).fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FF2674FF" } };
      sheet.views = [{ state: "frozen", ySplit: 1 }];
    });
    dictionarySheet.getColumn(2).numFmt = "@";
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "数据元及数据字典导入模板.xlsx";
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success("导入模板已下载");
  } catch (error: any) {
    console.error("下载数据元导入模板失败", error);
    ElMessage.error(error?.message || "下载导入模板失败");
  }
};

const findCategoryByName = (name: string, nodes = treeData.value): any => {
  for (const node of nodes || []) {
    if (node.label === name) return node;
    const found = findCategoryByName(name, node.children || []);
    if (found) return found;
  }
  return null;
};

const parseImportFiles = async (files: any[]) => {
  const file = files?.[0]?.raw || files?.[0];
  if (!file) {
    ElMessage.warning("请选择填写完成的 Excel 文件");
    return;
  }
  try {
    const WorkbookClass = (ExcelJS as any).Workbook || (ExcelJS as any).default?.Workbook;
    if (!WorkbookClass) throw new Error("Excel 导入组件未加载");
    const workbook = new WorkbookClass();
    await workbook.xlsx.load(await file.arrayBuffer());
    const metaSheet = workbook.getWorksheet("数据元");
    const dictionarySheet = workbook.getWorksheet("数据字典");
    validateImportHeaders(metaSheet, "数据元", importMetaHeaders);
    validateImportHeaders(dictionarySheet, "数据字典", importDictionaryHeaders);
    const metaRows = sheetRows(metaSheet);
    const dictionaryRows = sheetRows(dictionarySheet);
    if (!metaRows.length) throw new Error("“数据元”至少需要填写一行数据");
    if (metaRows.length > 200) throw new Error("单次最多导入 200 个数据元");

    const errors: string[] = [];
    const importedCodes = new Set<string>();
    const dictionaries = new Map<string, any[]>();
    const levelMap: Record<string, string> = { "1": "1", "2": "2", "3": "3", "4": "4", L1: "1", L2: "2", L3: "3", L4: "4" };
    const nullableMap: Record<string, number> = { "是": 1, "否": 0, "1": 1, "0": 0, TRUE: 1, FALSE: 0 };
    const normalizedMetas = metaRows.map((raw: any) => {
      const rowNo = raw.__rowNumber;
      const metaName = cellText(raw["数据元名称*"]);
      const metaCode = cellText(raw["数据元英文名*"]).toLowerCase();
      const standardEncode = cellText(raw["标准编码*"]);
      const fieldType = cellText(raw["字段类型*"]).toLowerCase();
      const fieldLength = Number(cellText(raw["长度*"]));
      const dataLevel = levelMap[cellText(raw["数据分级*"]).toUpperCase()];
      const isNullable = nullableMap[cellText(raw["可为空*"]).toUpperCase()];
      if (!metaName || !metaCode || !standardEncode || !fieldType || !fieldLength || !dataLevel || isNullable === undefined) errors.push(`数据元第 ${rowNo} 行存在未填写的必填项`);
      if (metaCode && !/^[a-z][a-z0-9_]{0,99}$/.test(metaCode)) errors.push(`数据元第 ${rowNo} 行英文名仅支持小写字母、数字和下划线，且须以字母开头`);
      if (fieldType && !supportedImportTypes.has(fieldType)) errors.push(`数据元第 ${rowNo} 行字段类型不受支持：${fieldType}`);
      if (fieldLength && (!Number.isInteger(fieldLength) || fieldLength < 1 || fieldLength > 65535)) errors.push(`数据元第 ${rowNo} 行长度应为 1-65535 的整数`);
      if (importedCodes.has(metaCode)) errors.push(`数据元英文名重复：${metaCode}`);
      importedCodes.add(metaCode);
      const categoryName = cellText(raw["数据分类"]);
      const category = categoryName ? findCategoryByName(categoryName) : null;
      if (categoryName && !category) errors.push(`数据元第 ${rowNo} 行不存在数据分类：${categoryName}`);
      return { metaName, metaCode, standardEncode, fieldType, fieldLength, dataLevel, isNullable, bizDef: cellText(raw["业务定义"]), formatPattern: cellText(raw["格式规则"]), exampleValue: cellText(raw["示例值"]), dataCategoryId: category?.tid || null, dataCategoryName: category?.label || null, dataCategoryPath: category?.treePath || null };
    });
    dictionaryRows.forEach((raw: any) => {
      const rowNo = raw.__rowNumber;
      const metaCode = cellText(raw["数据元英文名*"]).toLowerCase();
      const code = cellText(raw["代码值*"]);
      const name = cellText(raw["代码名称*"]);
      if (!metaCode || !code || !name) {
        errors.push(`数据字典第 ${rowNo} 行存在未填写的必填项`);
        return;
      }
      if (!importedCodes.has(metaCode)) errors.push(`数据字典第 ${rowNo} 行引用的数据元不存在：${metaCode}`);
      const values = dictionaries.get(metaCode) || [];
      if (values.some((item) => item.code === code)) errors.push(`数据字典第 ${rowNo} 行代码值重复：${metaCode}.${code}`);
      values.push({ code, name, description: cellText(raw["说明"]) });
      dictionaries.set(metaCode, values);
    });
    const page = getApiData(await $common.post("/dwm/standard/element/page", { pageNum: 1, pageSize: 1000 }));
    const existingCodes = new Set((page.list || []).map((item: any) => String(item.metaCode || "").toLowerCase()));
    normalizedMetas.forEach((item) => {
      if (existingCodes.has(item.metaCode)) errors.push(`数据元英文名已存在：${item.metaCode}`);
    });
    if (errors.length) throw new Error(errors.slice(0, 10).join("；") + (errors.length > 10 ? `；另有 ${errors.length - 10} 项错误` : ""));

    for (const meta of normalizedMetas) {
      const values = dictionaries.get(meta.metaCode) || [];
      let valueDomainId: string | null = null;
      if (values.length) {
        const domain = getApiData(await $common.post("/dwm/standard/code/saveOrUpdate", {
          dictName: `${meta.metaName}数据字典`, dictCode: meta.standardEncode, codeSet: meta.standardEncode,
          codeValue: "__HEADER__", codeName: `${meta.metaName}数据字典`, dataCategoryId: meta.dataCategoryId,
          dataCategoryName: meta.dataCategoryName, dataCategoryPath: meta.dataCategoryPath,
          description: `${meta.metaName}的数据字典`, dictItemValue: JSON.stringify(values),
        }));
        valueDomainId = domain?.tid || null;
      }
      await $common.post("/dwm/standard/element/metaSaveOrUpdate", {
        ...meta, dataTypeId: meta.fieldType, valueDomainId, standardCodeSet: meta.standardEncode, publishStatus: 1,
      });
    }
    importDrawerRef.value?.close?.();
    refreshData();
    ElMessage.success(`成功导入 ${normalizedMetas.length} 个数据元和 ${dictionaryRows.length} 项数据字典`);
  } catch (error: any) {
    console.error("导入数据元失败", error);
    ElMessage.error(error?.message || "导入失败，请确认模板内容");
  }
};

// 获取树形数据
const fetchTreeData = async () => {
  try {
    const response = await $common.post("/dwm/standard/standard-class-list");

    // 确保 treeData 是数组格式
    if (Array.isArray(response)) {
      treeData.value = response;
    } else if (response && typeof response === "object") {
      // 如果是对象，尝试转换为数组
      treeData.value = [response];
    } else {
      treeData.value = [];
    }
  } catch (error) {
    console.error("Error fetching tree data:", error);
    treeData.value = [];
  }
};

const handleCategorySaved = async () => {
  // The child closes itself after persistence; close through its exposed API as
  // well so a tree refresh cannot leave an Element Plus drawer mounted open.
  categoryDrawerRef.value?.close?.();
  await fetchTreeData();
};

// 树节点点击
const handleNodeClick = (node: any) => {
  currentNode.value = node;
  refreshData();
};

const handleAction = (type: string, row?: any) => {
  if (type === "详情" && row) {
    // 已发布状态只读查看，待发布可编辑
    const readonly = row.publishStatus === 1;
    metaDetailDrawerRef.value?.openDetail(row, readonly);
    return;
  }
  if (type === "新增") {
    const defaultCategoryId =
      currentNode.value && currentNode.value.tid !== "0" ? currentNode.value.tid : undefined;
    metaDetailDrawerRef.value?.openAdd(defaultCategoryId);
    return;
  }
  if (type === "数据元导入") {
    importDrawerRef.value?.open();
    return;
  }
  if (type === "选择分类" && row?.tid) {
    return;
  }
  if (type === "下载数据元导入模板" && row) {
    return;
  }
};

// 更新发布状态
const updatePublishStatus = async (row: any) => {
  $common.handle({
    url: "/dwm/standard/element/updatePublishStatus",
    action: row.publishStatus === 0 ? "发布" : "下线",
    info: `是否${row.publishStatus === 0 ? "发布" : "下线"}该数据元【${row.metaName}】`,
    data: { tid: row.tid, publishStatus: row.publishStatus === 0 ? 1 : 0 },
    done: () => {
      refreshData();
    },
  });
};

// 初始化数据
onMounted(async () => {
  await fetchTreeData();
  // DataTable is configured for manual loading; load the full list on first entry.
  // Otherwise records are shown only after the user clicks a category node.
  await nextTick();
  refreshData();
});
</script>

<style scoped lang="scss">
.data-standard-container {
  width: 100%;
  height: 100%;
  display: block;
  min-width: 0;
  min-height: 0;
  overflow: hidden;

  .data-standard-content {
    height: 100%;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  .left-panel {
    width: 100%;
    height: 100%;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 10px;
      margin-right: 14px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: #464c64;
      }
      .left-header-btn {
        padding: 4px 8px;
        color: var(--el-color-primary);

        &:hover {
          background-color: #1890ff1a;
        }
      }
    }

    .tree-container {
      height: calc(100% - 53px);

      :deep(.el-tree) {
        /*节点高度自适应*/
        --el-tree-node-content-height: 100%;
      }
      .tree-container-custom {
        height: 100%;
        :deep(.u-tree-search) {
          margin-right: 14px;
        }

        :deep(.el-tree) {
          padding-right: 14px;
        }

        :deep(.el-tree-node__content) {
          &:hover {
            .more-icon {
              opacity: 1;
            }
          }
        }
      }
    }
  }

  .right-panel {
    width: 100%;
    height: 100%;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
    :deep(.el-button .el-icon + span) {
      margin-left: 3px;
    }
  }
}

.data-standard-container :deep(.el-splitter-panel) {
  min-width: 0 !important;
  min-height: 0 !important;
  overflow: hidden !important;
}

.standard-list-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  margin-bottom: 0;
}

// 仅压缩本页主列表工具栏，避免与 DataTable 默认 mb-4 叠加。
.standard-main-table :deep(> .mb-4) {
  margin-bottom: 8px;
}

.standard-main-table :deep(.el-table__body td:last-child .cell) {
  padding-left: 12px;
  padding-right: 12px;
}

.standard-main-table :deep(.operation-buttons),
.standard-main-table :deep(.operation-buttons > .flex) {
  width: 100%;
  justify-content: center;
}

.standard-main-table :deep(.operation-buttons > .flex) { gap: 12px; }
.standard-main-table :deep(.operation-buttons .el-button) { margin-left: 0; }

.standard-list-toolbar__status { flex: 0 0 auto; }

.standard-list-toolbar__primary,
.standard-list-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.standard-list-toolbar__primary {
  flex: 0 0 auto;
  gap: 6px;

  :deep(.el-button + .el-button) { margin-left: 0; }
}

.standard-list-toolbar__filters {
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
  justify-content: flex-end;
}

.standard-keyword-input {
  flex: 0 1 200px;
  width: 200px;
  min-width: 160px;
  max-width: 200px;
}

.standard-search-icon {
  cursor: pointer;
  color: var(--el-color-primary);
}

@media (max-width: 760px) {
  .standard-list-toolbar {
    flex-wrap: wrap;
  }

  .standard-list-toolbar__filters {
    width: 100%;
    margin-left: 0;
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .standard-keyword-input {
    flex: 1 1 160px;
    width: auto;
    max-width: none;
  }
}

// 树节点自定义样式
.custom-tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 4px;

  .node-label {
    flex: 1;
    word-break: break-all;
    white-space: normal;
  }

  .node-actions {
    display: flex;
    align-items: center;
    flex-shrink: 0;
    min-width: 24px;
  }

  .more-icon {
    opacity: 0;
    cursor: pointer;
    transition: opacity 0.2s;
    font-size: 16px;
    color: #909399;
    padding: 3px;
    border-radius: 4px;
    outline: none;

    &:hover {
      color: var(--el-color-primary);
      background-color: var(--el-color-primary-light-9);
    }

    &.visible {
      opacity: 1;
    }
  }
}

// 删除选项红色样式
:deep(.el-dropdown-menu__item.delete-item) {
  color: #f56c6c;

  &:hover {
    background-color: #fef0f0;
    color: #f56c6c;
  }
}

.standard-search-icon {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 16px;
}

.standard-name-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font: inherit;
}

.standard-name-link:hover {
  text-decoration: underline;
}

.dictionary-count-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font: inherit;
}

.dictionary-count-link:hover {
  text-decoration: underline;
}

.dictionary-count-link.is-empty {
  color: #909399;
}

.dictionary-count-link.is-empty:hover {
  color: var(--el-color-primary);
}

:global(.dictionary-drawer .el-drawer__body) {
  min-height: 0;
  padding: 14px 20px 20px;
  display: flex;
  flex-direction: column;
}

:global(.dictionary-drawer .mb-4) {
  margin-bottom: 8px;
}

.dictionary-drawer__header {
  display: flex;
  align-items: baseline;
  gap: 10px;

  strong {
    color: #1f2937;
    font-size: 17px;
  }

  span {
    color: #909399;
    font-size: 13px;
    font-weight: normal;
  }
}

:deep(.u-tree-search .el-input__suffix) {
  color: var(--el-color-primary);
  cursor: pointer;
}
</style>
