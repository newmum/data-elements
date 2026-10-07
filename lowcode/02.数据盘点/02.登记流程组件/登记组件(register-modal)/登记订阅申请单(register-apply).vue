<template>
  <div class="px-5 py-2">
    <JsonForm
      ref="formRef"
      bordered
      :rules="formRules"
      :options="{
        form: {
          labelWidth: '250px',
        },
      }"
    >
      <template #type-apply-table>
        <data-table
          flex-type="flex-[0_1_200px]"
          border
          class="rounded-lg"
          :show-page="false"
          :data="tableState.list"
          :columns="tableState.cols"
        >
          <template #column-radio="{ row }">
            <el-radio
              :model-value="tableState.check"
              :value="row.tid"
              @click="chooseRes(row)"
            ></el-radio>
          </template>
          <template #column-assetName="{ row }">
            <el-text>
              <Icon :icon="row.assetClass" />
              {{ row.assetName }}
            </el-text>
          </template>
          <template #column-categoryName="{ row }">
            <el-text>
              <Icon icon="category" />
              {{ row.categoryName }}
            </el-text>
          </template>
        </data-table>
      </template>
    </JsonForm>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, reactive } from "vue";
import { storeToRefs } from "pinia";
import { FormUtils } from "@/utils/form";
import { useRegisterStore } from "@/store";
import { get, set } from "lodash-es";
import dayjs from "dayjs";

// refs
const formRef = ref();
const formRules = ref([]);
const store = useRegisterStore();
const { state } = storeToRefs(store);
// 当前申请单信息
const apply = computed({
  get: () => {
    if (!get(store.data, "apply")) {
      set(store.data, "apply", {});
    }
    return get(store.data, "apply");
  },
  set: (val) => set(store.data, "apply", val),
});

onMounted(() => {
  // @TODO 第一步初始化信息，也可调接口更新数据
  formRules.value = [...rule1, ...rule3];
  nextTick(() => {
    formRef.value?.setValue({
      ...mock,
      ...apply.value,
    });
    chooseRes(tableState.list[0]);
  });
});

const chooseRes = (row) => {
  tableState.check = row.tid;
  tableState.resName = row.assetName;
  tableState.assetClass = row.assetClass;
  tableState.resId = row.tid;
  tableState.row = row;
  if (row.assetClass === "api") {
    formRules.value = [...rule1, ...rule2, ...rule3];
    nextTick(() => {
      formRef.value?.setValue({
        ...mock,
        ...apply.value,
      });
    });
  } else {
    formRules.value = [...rule1, ...rule3];
    nextTick(() => {
      formRef.value?.setValue({
        ...mock,
        ...apply.value,
      });
    });
  }
};

// 验证表单
const validate = async () => {
  if (!tableState.check) {
    throw "请选择订阅资源";
  }
  if (!formRef.value) {
    throw new Error("表单未初始化");
  }
  await formRef.value.validate();
};

const saveApi = (data: Record<string, any>) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({ tid: $common.uuid(), ...data });
    }, 1000);
  });
};

const save = async (): Promise<any> => {
  try {
    state.value.loadStatus["main"] = true;
    await validate();
    // 保存逻辑
    const formData = formRef.value?.getSaveData() || {};
    const data: any = await saveApi(formData); // 调用保存接口

    set(store.data, "apply", Object.assign({}, formData, data, get(store.data, "apply", {})));
    return {
      success: true,
      msg: "保存成功",
    };
  } catch (err: any) {
    return {
      success: false,
      msg: (err.message || err) as string,
    };
  } finally {
    state.value.loadStatus["main"] = false;
  }
};
const next = () => {
  if (!apply.value?.tid) {
    throw "请保存";
  }
};
const finish = () => {
  next();
  const list = [];
  list.push({
    ...tableState.row,
    assetClass: tableState.row.assetClass,
    assetName: tableState.resName,
    applyTime: dayjs(new Date()).format("YYYY-MM-DD HH:mm:ss"),
  });
  return list;
};

// 暴露方法给父组件
defineExpose({
  save,
  next,
  finish,
});
// 初始化表单规则
const rule1 = FormUtils.fixJson(
  [
    {
      type: "UTitle",
      title: "基本信息",
    },
    {
      type: "form-text",
      title: "订阅部门",
      field: "orgId",
      props: { options: "org" },
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "form-text",
      title: "统一社会信用代码",
      field: "usci",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "申请类型",
      field: "applyType",
      props: { options: "applyType" },
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "form-text",
      title: "申请日期",
      field: "applyTime",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "使用人姓名",
      field: "userName",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "申请人姓名",
      field: "applicantName",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "使用人联系方式",
      field: "userPhone",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "申请人联系方式",
      field: "applicantPhone",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "使用人邮箱",
      field: "userEmail",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "input",
      title: "申请人邮箱",
      field: "applicantEmail",
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "apply-table",
      title: "订阅资源",
      field: "subscribeResources",
      col: {
        span: 24,
      },
      $required: true,
    },
  ],
  "250px"
);
const rule2 = FormUtils.fixJson(
  [
    {
      type: "UTitle",
      title: "使用信息",
    },
    {
      type: "input",
      field: "averageFrequency",
      title: "服务接口每天使用频次(次)",
      props: {
        placeholder: "请输入服务接口调用频次(平均)次/天",
      },
      $required: true,
      col: {
        span: 12,
      },
    },
    {
      type: "input",
      field: "peakFrequency",
      title: "服务接口使用峰值（次）",
      props: {
        placeholder: "请输入服务接口调用频次(峰值)次/天",
      },
      col: {
        span: 12,
      },
      $required: true,
    },
    {
      type: "ElDatePicker",
      title: "使用时间",
      field: "useTime",
      props: {
        type: "datetimerange",
        showTime: true,
      },
      col: {
        span: 12,
      },
      style: "width: 100%",
    },
    {
      type: "input",
      field: "useDays",
      title: "接口使用期限（天）",
      props: {
        placeholder: "请输入接口使用期限，单位：天",
      },
      col: {
        span: 12,
      },
    },
    {
      type: "input",
      field: "otherRequests",
      title: "其他技术请求",
      props: {
        placeholder: "请输入其他技术请求",
        allowClear: true,
        maxlength: 64,
      },
      col: {
        span: 24,
      },
    },
    {
      type: "input",
      field: "applyBasis",
      title: "申请依据",
      props: {
        type: "textarea",
        placeholder:
          "填写规范说明：建议填写国家、上级机构下发的政策文件或本级单位政策文件名称、发文机关、文号、具体条目内容等信息，便于提高审核通过率。具体如下内容：依据某个部门印发《文件名称》（文号），引用具体内容。",
        maxlength: 255,
        rows: 4,
      },
      col: {
        span: 24,
      },
      $required: true,
    },
  ],
  "250px"
);
const rule3 = FormUtils.fixJson(
  [
    {
      type: "UTitle",
      title: "数据用途",
    },
    {
      type: "select",
      title: "应用系统",
      field: "appSystem",
      col: {
        span: 24,
      },
      $required: true,
      placeholder: "请选择应用系统",
      options: [
        { label: "广电信号传输调度系统", value: "broadcast_signal_dispatch" },
        { label: "广电用户收视分析平台", value: "user_view_analysis" },
        { label: "广电内容审核管理系统", value: "content_review_system" },
      ],
    },
    {
      type: "select",
      title: "使用范围说明",
      field: "useScope",
      col: {
        span: 12,
      },
      $required: true,
      placeholder: "请选择使用范围说明",
      options: [
        { label: "内部业务支撑", value: "internal_business" },
        { label: "跨部门数据共享", value: "cross_dept_share" },
        { label: "对外服务接口", value: "external_service" },
      ],
    },
    {
      type: "select",
      title: "应用场景类型",
      field: "sceneType",
      col: {
        span: 12,
      },
      $required: true,
      placeholder: "应用场景类型",
      options: [
        { label: "数据查询", value: "data_query" },
        { label: "统计分析", value: "stat_analysis" },
        { label: "业务流程支撑", value: "business_support" },
      ],
    },
    {
      type: "textarea",
      title: "办事场景",
      field: "businessScene",
      props: { rows: 3 },
      col: {
        span: 24,
      },
      $required: true,
      placeholder:
        "填写规范说明：订阅使用某项数据资源，用于某个业务场景，实现某种程度的业务成效。示例：使用单位xx，用于xx系统办理xx等业务时，调用xx接口，实现xx，从而减免提交纸质材料。",
    },
    {
      type: "file-upload",
      title: "附件信息",
      field: "attachment",
      col: {
        span: 24,
      },
      $required: false,
      props: {
        accept: ".png,.jpg,.jpeg,.svg,.xlsx,.docx,.pdf,.txt",
        placeholder: "请上传.png,.jpg,.jpeg,.svg,.xlsx,.docx,.pdf,.txt格式文件。",
      },
    },
  ],
  "250px"
);

// 完整表单模拟数据（与表单配置的 field 完全对应，广电场景适配）
const mock = {
  // 基本信息模块
  orgId: "10400",
  usci: "9111000071780321XQ", // 真实格式的统一社会信用代码（模拟广电相关单位）
  applyType: "政务服务", // 对应 DictSelect 选项（新增申请，符合 applyType 字典规范）
  applyTime: dayjs(new Date()).format("YYYY-MM-DD HH:mm:ss"), // 申请日期，符合 DatePicker 格式
  userName: "叶华",
  applicantName: "李强",
  userPhone: "13800138000",
  applicantPhone: "13900139000",
  userEmail: "zhangming@nrta.gov.cn",
  applicantEmail: "liqiang@nrta.gov.cn",
  subscribeResources: [
    // 订阅资源表格数据（延续之前广电场景，匹配 apply-table 字段）
    {
      radio: "",
      assetName: "广电全国卫视节目信号传输API",
      categoryName: "一般题材备案数据目录",
      assetClass: "API服务",
      shareType: "内部共享",
      orgName: "国家广播电视总局信息传输中心",
      applyStatus: "已订阅",
    },
    {
      radio: "",
      assetName: "广电家庭用户收视行为统计数据表",
      categoryName: "一般题材备案数据目录",
      assetClass: "数据表",
      shareType: "仅限广电系统内共享",
      orgName: "广电网络股份有限公司大数据研究院",
      applyStatus: "审批中",
    },
  ],

  // 使用信息模块
  averageFrequency: "5000", // 日均使用频次
  peakFrequency: "10000", // 峰值使用频次
  useTime: ["2026-02-01 08:00:00", "2027-01-31 18:00:00"], // 时间范围，符合 ElDatePicker datetimerange 格式
  useDays: "365", // 接口使用期限（天）
  otherRequests: "希望接口提供超高并发支持，保障节假日信号传输稳定", // 其他技术请求（不超过64字）
  applyBasis:
    "依据国家广播电视总局印发《全国广播电视信号传输保障管理办法》（广电发〔2025〕12号），其中第三章第十条明确要求：各级广电单位应保障全国卫视节目信号的稳定传输与数据统计，需调用统一信号传输API与用户收视统计数据。", // 申请依据（符合填写规范，不超过255字）

  // 数据用途模块
  appSystem: "broadcast_signal_dispatch", // 对应 select 选项（广电信号传输调度系统）
  useScope: "internal_business", // 对应 select 选项（内部业务支撑）
  sceneType: "business_support", // 对应 select 选项（业务流程支撑）
  businessScene:
    "使用单位为国家广播电视总局广播电视传输中心，用于广电信号传输调度系统办理全国卫视节目信号传输与监控业务时，调用广电全国卫视节目信号传输API与收视行为统计数据表，实现信号传输状态的实时监控与用户收视数据的自动统计，从而减免人工填报传输报表与收视数据的纸质材料，提升业务办理效率80%以上。", // 办事场景（符合填写规范）
  attachment: [
    // 附件信息（模拟已上传文件，符合 file-upload 格式）
    {
      name: "《广电信号传输API使用申请说明.docx》",
      url: "https://example.com/attachment/1.docx",
      size: 204800,
      type: "docx",
    },
  ],
};
const tableState = reactive({
  check: "",
  list: [
    {
      tid: "1",
      assetClass: "api",
      assetName: "广电全国卫视节目信号传输API",
      categoryName: "一般题材备案数据目录",
      shareType: "有条件共享",
      orgName: "电视剧司",
      applyStatus: "待订阅",
    },
    {
      tid: "2",
      assetClass: "table",
      assetName: "广电家庭用户收视行为统计数据表",
      categoryName: "一般题材备案数据目录",
      shareType: "无条件共享",
      orgName: "电视剧司",
      applyStatus: "待订阅",
    },
  ],
  cols: [
    {
      label: "请选择",
      prop: "radio",
      align: "center",
      width: 80,
    },
    {
      label: "资源名称",
      prop: "assetName",
      minWidth: 180,
    },
    {
      label: "所属目录",
      prop: "categoryName",
      ellipsis: true,
      minWidth: 120,
    },
    {
      label: "资源类型",
      prop: "assetClass",
      width: 120,
      options: "assetClass",
    },
    {
      label: "共享属性",
      prop: "shareType",
      width: 120,
    },
    {
      label: "来源部门",
      prop: "orgName",
      width: 200,
    },
    {
      label: "订阅状态",
      prop: "applyStatus",
    },
  ],
});
</script>
