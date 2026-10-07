<template>
  <div>
    <u-title name="参数信息" class="mt-4">
      <template #right>
        <button
          class="vxe-button type--text size--small theme--primary"
          name="新增参数"
          type="button"
          code="append_edit"
          @click="addParams"
        >
          <i class="vxe-button--item vxe-button--prefix-icon vxe-icon-add"></i>
          <span class="vxe-button--item vxe-button--content">新增参数</span>
        </button>
      </template>
    </u-title>

    <div style="min-height: 220px">
      <vxe-table
        ref="xTable"
        size="small"
        show-overflow
        :border="'inner'"
        :edit-rules="validRules"
        :edit-config="{ trigger: 'click', mode: 'row' }"
        :data="state.initData"
      >
        <template #empty>
          <empty description="暂无数据" style="margin: 20px 0" />
        </template>
        <vxe-column type="seq" width="60" title="序号" fixed="left"></vxe-column>

        <vxe-column field="req" width="80" title="入参" fixed="left">
          <template #header>
            <ElCheckbox v-model="allReq" :indeterminate="indeterminateParams('req')" />
            入参
          </template>
          <template #edit="scope">
            <ElCheckbox v-model="scope.row.req" @change="changeCheck(scope)" />
          </template>
          <template #default="scope">
            <ElCheckbox v-model="scope.row.req" @change="changeCheck(scope)" />
          </template>
        </vxe-column>

        <vxe-column field="res" width="80" title="出参" fixed="left">
          <template #header>
            <ElCheckbox v-model="allRes" :indeterminate="indeterminateParams('res')" />
            出参
          </template>
          <template #edit="scope">
            <ElCheckbox v-model="scope.row.res" @change="changeCheck(scope)" />
          </template>
          <template #default="scope">
            <ElCheckbox v-model="scope.row.res" @change="changeCheck(scope)" />
          </template>
        </vxe-column>

        <template v-for="col in columnConfig" :key="col.field">
          <vxe-column :field="col.field" :title="col.title" v-bind="col.props" :edit-render="{}">
            <!-- 编辑状态插槽 -->
            <template #edit="scope">
              <component
                :is="editComponents[col.editType]"
                v-model="scope.row[col.field]"
                :config="col"
                style="width: 100%"
                v-bind="col.props"
                allow-clear
                :disabled="col.disabled ? col.disabled(scope.row) : false"
              />
            </template>

            <!-- 默认显示插槽 -->
            <template #default="{ row }">
              <span class="vxe-cell--label">
                {{ col.formatter ? col.formatter(row[col.field]) : row[col.field] }}
              </span>
            </template>
          </vxe-column>
        </template>

        <vxe-column title="操作" width="60">
          <template #default="{ row }">
            <a style="color: #ff7875" @click="removeParams(row)">删除</a>
          </template>
        </vxe-column>
      </vxe-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElCheckbox, ElInput, ElInputNumber, ElSelect } from "element-plus";

const props = defineProps<{ params: Record<string, any>[] }>();

// refs
const state = reactive({
  paramTypeList: [
    { value: "string", label: "string" },
    { value: "boolean", label: "boolean" },
    { value: "number", label: "number" },
  ],
  paramPositionList: [
    { value: "body", label: "body" },
    { value: "query", label: "query" },
    { value: "header", label: "header" },
  ],
  statusList: [
    { value: "optional", label: "可选" },
    { value: "required", label: "必填" },
    { value: "constant", label: "常量" },
    { value: "template", label: "模板" },
  ],
  initData: [],
});
const xTable = ref();
const validRules = {
  paramName: [{ required: true, message: "参数名称必须填写" }],
  paramDesc: [{ required: true, message: "参数中文名必须填写" }],
  paramType: [{ required: true, message: "参数类型必须填写" }],
  paramPosition: [{ required: false, message: "参数位置必须填写" }],
  status: [{ required: true, message: "必填属性必须填写" }],
};

const validate = async (isFWGJ = false) => {
  const f = await xTable.value?.validate(true);
  if (f) throw new Error("请完善数据项");
  if (isFWGJ) {
    const data = xTable.value?.getTableData().fullData;
    data.forEach((i) => {
      if (!i.req && !i.res) {
        throw new Error("请选择参数类型");
      }
      if (/[\u4e00-\u9fa5]/.test(i.paramName) && isFWGJ) {
        throw new Error("服务构建时，包含中文的参数名称，会导致构建失败。");
      }
    });
  }
  return true;
};

const addParams = async (row) => {
  const $table = xTable.value;

  // 添加空值检查
  if (!$table) {
    console.error("Table reference is null or undefined");
    return;
  }

  let record;
  if (row && Object.keys(row).length) {
    record = JSON.parse(
      JSON.stringify({
        ...row,
        paramType: row.paramType ? row.paramType : "string",
        paramPosition: !row.res && row.req ? "" : row.paramPosition ? row.paramPosition : "body",
        status: "required",
        req: row.req !== undefined ? row.req : false,
        res: row.res !== undefined ? row.res : false,
        paramComment: row.paramComment || "",
      })
    );
  } else {
    record = {
      paramName: "",
      paramDesc: "",
      paramType: "string",
      paramPosition: "body",
      instance: "",
      req: false,
      res: false,
      status: "required",
      paramComment: "",
    };
  }

  try {
    // 尝试使用insert方法代替insertAt
    await $table.insert(record);
  } catch (error) {
    console.error("Error inserting record:", error);
    // 如果insert方法失败，尝试直接修改数据数组
    state.initData.unshift(record);
  }
};
const removeParams = async (row) => {
  xTable.value?.remove(row);
};

const editComponents = {
  input: ElInput,
  select: ElSelect,
  checkbox: ElCheckbox,
  inputNumber: ElInputNumber,
};

const columnConfig = [
  {
    field: "paramName",
    title: "参数名称",
    editType: "input",
    props: {
      fixed: "left",
      minWidth: 120,
      placeholder: "请输入参数名称",
    },
  },
  {
    field: "paramDesc",
    title: "参数中文名",
    editType: "input",
    props: {
      fixed: "left",
      minWidth: 120,
      placeholder: "请输入参数中文名",
    },
  },
  {
    field: "paramComment",
    title: "参数说明",
    editType: "input",
    props: {
      minWidth: 120,
      placeholder: "请输入参数说明",
      maxlength: 500,
    },
  },
  {
    field: "paramType",
    title: "参数类型",
    editType: "select",
    props: {
      width: 120,
      placeholder: "请选择参数类型",
      options: state.paramTypeList,
    },
    formatter: (value) => getLabel(state.paramTypeList, value),
  },
  {
    field: "paramPosition",
    title: "参数位置",
    editType: "select",
    props: {
      width: 120,
      placeholder: "请选择参数位置",
      options: state.paramPositionList,
    },
    formatter: (value) => getLabel(state.paramPositionList, value),
    disabled: (row) => row.res && !row.req,
  },
  {
    field: "status",
    title: "必填属性",
    editType: "select",
    props: {
      width: 120,
      placeholder: "请选择必填属性",
      options: state.statusList,
    },
    formatter: (value) => getLabel(state.statusList, value),
  },
  {
    field: "instance",
    title: "默认值",
    editType: "input",
    props: {
      width: 120,
      placeholder: "请输入默认值",
    },
  },
];

const allReq = computed({
  get: () => {
    const d = xTable.value?.getTableData().fullData;
    return d.length !== 0 && d.every((i) => i.req);
  },
  set: (val) => {
    const list = [];
    xTable.value?.getTableData().fullData.forEach((row) => {
      list.push({
        ...row,
        req: val,
        // 当不是入参且是出参时, 参数位置置空
        paramPosition: !val && row.res ? "" : row.paramPosition || "body",
        // 当是入参时, 必填属性置为必填
        status: val ? row.status || "required" : undefined,
      });
    });
    xTable.value?.reloadData(list);
  },
});

const allRes = computed({
  get: () => {
    const d = xTable.value?.getTableData().fullData;
    return d.length !== 0 && d.every((i) => i.res);
  },
  set: (val) => {
    const list = [];
    xTable.value?.getTableData().fullData.forEach((row) => {
      list.push({
        ...row,
        res: val,
        paramPosition: !val ? row.paramPosition || "body" : "",
      });
    });
    xTable.value?.reloadData(list);
  },
});

const indeterminateParams = (type) => {
  const $table = xTable.value;
  const data = $table.getTableData().fullData;
  const a = data.some((i) => i[type]);
  const b = data.every((i) => i[type]);
  return a && !b;
};

const changeCheck = (scope) => {
  const $table = xTable.value;
  $table.updateStatus(scope);
  const row = scope.row;
  if (!row.req && row.res) {
    row.paramPosition = "";
    row.status = undefined;
  } else {
    row.paramPosition = row.paramPosition || "body";
    row.status = row.status || "required";
  }
  // 刷新表格数据, 强制更新勾选状态 allRes allReq
  $table.reloadData(xTable.value?.getTableData().fullData);
};

const getLabel = (list, val) => {
  return list.find((i) => i.value === val)?.label || "";
};

const getData = () => {
  const params: any = [];
  xTable.value?.getTableData().fullData.forEach((i) => {
    if (i.req || i.res) {
      params.push({
        paramName: i.paramName,
        paramDesc: i.paramDesc,
        paramComment: i.paramComment,
        paramType: i.paramType,
        paramPosition: i.paramPosition,
        status: i.status,
        instance: i.instance,
        req: i.req || false,
        res: i.res || false,
      });
    }
  });
  return params;
};

const reload = (list) => {
  const params = [];
  list?.forEach((p) => {
    const ind = params.findIndex((param) => param.paramName === p.paramName);
    if (ind > -1) {
      const existingReq = params[ind].req;
      const existingRes = params[ind].res;
      const self = params[ind];
      params[ind] = {
        ...params[ind],
        instance: self.instance || p.reqExample || p.respExample,
        paramPosition: self.paramPosition || p.paramPosition,
        paramType: self.paramType || p.paramType,
        status: self.status || p.status,
        res: existingRes,
        req: existingReq,
      };
      return;
    }
    if (p.req !== undefined && p.res !== undefined) {
      const el = {
        ...p,
        instance: p.type == "request" ? p.reqExample : p.respExample,
        req: p.req,
        res: p.res,
      };
      params.push(el);
    } else {
      // 否则，根据 type 字段计算
      const el = {
        ...p,
        instance: p.type == "request" ? p.reqExample : p.respExample,
        req: p.type === "request",
        res: p.type === "respone",
      };
      params.push(el);
    }
  });
  state.initData = params || [];
};
watch(
  () => props.params,
  (val) => {
    reload(val);
  },
  { deep: true, immediate: true }
);
defineExpose({
  validate,
  getData,
});
</script>
