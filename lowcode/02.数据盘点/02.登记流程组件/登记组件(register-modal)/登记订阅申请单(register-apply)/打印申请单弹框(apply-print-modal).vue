<template>
  <el-dialog v-model="open" width="1050">
    <!-- 要打印的DOM区域 -->
    <div ref="printRef" class="print-content">
      <h2>订阅目录申请单</h2>
      <JsonForm
        ref="formRef"
        bordered
        :rules="formRules"
        :options="{
          form: {
            labelWidth: '160px',
          },
        }"
        :data="data"
      >
        <template #field-applyResource>
          <table style="width: 100%; table-layout: fixed">
            <tr>
              <td
                v-for="col in tableCols"
                :key="col.prop"
                style="font-weight: 500; text-align: center"
              >
                {{ col.label }}
              </td>
            </tr>
            <tr v-for="(row, i) in data['applyResource']" :key="i">
              <td v-for="col in tableCols" :key="i + col.prop" style="text-align: center">
                <dict-label :model-value="row[col.prop]" :options="col.options" :tag="false" />
              </td>
            </tr>
          </table>
        </template>
      </JsonForm>
      <div class="promise">
        <p class="promise-info">
          我单位承诺填写信息全部属实，依法依规使用服务接口，确保数据使用安全，
          对违规使用数据、泄露数据等情况承担相关责任。
        </p>
        <div class="prove">
          <p>经办人：</p>
          <p>申请单位（盖章）：</p>
          <p>申请日期：</p>
        </div>
      </div>
    </div>

    <!-- 打印按钮 -->
    <template #footer>
      <el-button type="primary" @click="handleDownload">下载</el-button>
      <el-button @click="handlePrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from "vue";
import { usePrint } from "@/composables";
import { cloneDeep, set } from "lodash-es";

const props = withDefaults(
  defineProps<{
    data?: Record<string, any>;
    rules?: [];
    tableCols?: [];
  }>(),
  {
    data: () => ({}),
    rules: () => [],
    tableCols: [
      {
        label: "数据目录",
        prop: "catalogName",
        ellipsis: true,
        minWidth: 120,
      },
      {
        label: "资源类型",
        prop: "resType",
        options: "assetType",
      },
      {
        label: "共享属性",
        prop: "shareType",
        options: "shareType",
        width: 120,
      },
      {
        label: "来源部门",
        prop: "orgId",
        options: "org",
        width: 200,
      },
    ],
  }
);
const formRef = ref();
const open = defineModel("modelValue", {
  type: Boolean,
  required: true,
});

const formRules = computed(() => {
  return props.rules.map((el) => {
    set(el, "props.tag", false);
    return el;
  });
});

const tableState = reactive<{ selectedRows: any[]; list: any[]; cols: any[] }>({
  selectedRows: [],
  list: [],
  cols: [],
});

const printRef = ref(null); // 绑定要打印的DOM
const { printDOM, downloadPdf } = usePrint();

// 打印处理函数
const handlePrint = async () => {
  await printDOM(printRef, {
    title: "申请单",
    // 打印前回调（可修改数据/DOM）
    beforePrint: () => {
      $message.primary("正在准备打印...");
    },
  });
};

onMounted(() => {
  tableState.list = [];
});
const handleDownload = async () => {
  await downloadPdf(printRef, {
    filename: "申请单.pdf",
    fitToA4: true, // 强制适配A4
    orientation: "portrait", // 纵向（可选landscape横向）
    // 下载前后回调
    beforeDownload: () => {
      $message.primary("正在下载，请稍后...");
    },
    afterDownload: () => {},
  });
};
defineExpose({
  print: handlePrint,
  download: handleDownload,
});
</script>

<style scoped lang="scss">
.print-content {
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 20px 0 20px;

  table,
  tr,
  td,
  th {
    border: 0.5px solid #000000e0;
    border-collapse: collapse;
  }

  :deep(.el-tag) {
    background-color: transparent;
    border: 0;
    margin-left: 0;
  }

  :deep(.jf-border) {
    width: calc(100% + 1px);
    .u-title .title:before {
      display: none;
      font-weight: bolder;
    }

    .fc-form-col {
      border: 1px solid #000000e0;
      margin: -1px 1px 0 -1px;

      /*防止表单右侧边框凹陷*/
      &:nth-child(even) {
        // border-right: 0;
      }
      &:nth-child(odd) {
        border-right: 1px;
      }
      &.el-col-24 {
        border: 1px solid #000000e0;
      }

      .el-form-item__content {
        * {
          color: #000000e0 !important;
        }
      }

      .el-form-item .el-form-item__label {
        background: #fff;
        border-right: 1px solid #000000e0;
        & > .fc-form-title {
          overflow: hidden;
          text-overflow: ellipsis;
          font-weight: 400;
          color: #000000e0 !important;
          white-space: wrap;
        }
      }
    }
  }
}

.promise {
  border: 1px solid #000000e0;
  border-top: 0;
  height: 250px;
  text-indent: 32px;
  font-size: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 100%;

  .promise-info {
    font-weight: 700;
  }
  .prove {
    p {
      margin-bottom: 32px;
    }
  }
}
</style>
