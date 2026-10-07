<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    destroy-on-close
    :close-on-click-modal="false"
    class="create-node-dialog"
    @close="handleClose"
  >
    <!-- 类型标识条 -->
    <div class="node-type-badge" :class="`badge-${props.nodeType}`">
      <span class="badge-dot"></span>
      <span class="badge-text">{{ nodeTypeLabel }}</span>
      <span v-if="props.parentName && !props.editData" class="badge-parent">
        &nbsp;·&nbsp;{{ props.parentName }}
      </span>
    </div>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="90px"
      class="create-node-form"
    >
      <el-form-item label="名称" prop="dictName">
        <el-input
          v-model="formData.dictName"
          :placeholder="`请输入${nodeTypeLabel}名称`"
          maxlength="50"
          show-word-limit
          clearable
        />
      </el-form-item>

      <el-form-item label="编码" prop="dictCode">
        <el-input
          v-model="formData.dictCode"
          :placeholder="`字母开头，仅含字母/数字/下划线`"
          maxlength="80"
          show-word-limit
          clearable
          @input="handleDictCodeInput"
        />
      </el-form-item>

      <el-form-item label="描述" prop="dictDesc">
        <el-input
          v-model="formData.dictDesc"
          type="textarea"
          :rows="5"
          :placeholder="`请输入${nodeTypeLabel}描述（选填）`"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="排序" prop="sortNo">
        <el-input-number
          v-model="formData.sortNo"
          :min="0"
          :max="9999"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ props.editData ? "保存" : "确认" }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
// import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";

export type NodeType = "warehouse" | "domain" | "process";

export interface NodeFormData {
  dictName: string;
  dictCode: string;
  dictDesc: string;
  sortNo: number;
}

interface Props {
  modelValue: boolean;
  nodeType: NodeType;
  /** 编辑时传入原始数据 */
  editData?: NodeFormData | null;
  /** 父节点名称（用于提示，如"在xxx下创建业务线"） */
  parentName?: string;
  /** 异步确认处理器，返回 Promise 表示 API 调用完成。传入时取代 confirm 事件 */
  onConfirm?: (data: NodeFormData) => Promise<void>;
}

const props = withDefaults(defineProps<Props>(), {
  editData: null,
  parentName: "",
});

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirm: [data: NodeFormData];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const formRef = ref<FormInstance>();
const loading = ref(false);

const nodeTypeLabel = computed(() => {
  const map: Record<NodeType, string> = {
    warehouse: "业务域",
    domain: "业务线",
    process: "业务事项",
  };
  return map[props.nodeType];
});

const dialogTitle = computed(() => {
  return props.editData ? `编辑${nodeTypeLabel.value}` : `新建${nodeTypeLabel.value}`;
});

const formData = ref<NodeFormData>({
  dictName: "",
  dictCode: "",
  dictDesc: "",
  sortNo: 1,
});

const formRules: FormRules = {
  dictName: [
    { required: true, message: "请输入名称", trigger: "blur" },
    { min: 1, max: 50, message: "名称长度为1-50个字符", trigger: "blur" },
  ],
  dictCode: [
    { required: true, message: "请输入编码", trigger: "blur" },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
      message: "须以字母开头，仅包含字母、数字、下划线",
      trigger: "blur",
    },
  ],
};

/** 编码自动转小写，过滤非法字符 */
const handleDictCodeInput = (val: string) => {
  // 转小写，去除非法字符（保留字母/数字/下划线）
  formData.value.dictCode = val.toLowerCase().replace(/[^a-z0-9_]/g, "");
};

// 编辑时回填数据
watch(
  () => props.editData,
  (val) => {
    if (val) {
      formData.value = { ...val };
    } else {
      formData.value = { dictName: "", dictCode: "", dictDesc: "", sortNo: 1 };
    }
  },
  { immediate: true }
);

const handleClose = () => {
  visible.value = false;
  formRef.value?.resetFields();
  formData.value = { dictName: "", dictCode: "", dictDesc: "", sortNo: 1 };
};

const handleConfirm = async () => {
  try {
    await formRef.value?.validate();
  } catch {
    // 校验未通过，ElForm 会自动高亮错误项，不需要额外提示
    return;
  }
  loading.value = true;
  try {
    if (props.onConfirm) {
      await props.onConfirm({ ...formData.value });
    } else {
      emit("confirm", { ...formData.value });
    }
    handleClose();
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss">
.create-node-dialog {
  .el-dialog__header {
    padding-bottom: 0;
  }

  .el-dialog__body {
    padding: 12px 24px 8px;
  }

  /* 类型标识条 */
  .node-type-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
    margin-bottom: 16px;

    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .badge-parent {
      color: inherit;
      opacity: 0.7;
      font-weight: 400;
    }

    &.badge-warehouse {
      background-color: #eff6ff;
      color: #1d4ed8;
      .badge-dot {
        background-color: #2d81e5;
      }
    }

    &.badge-domain {
      background-color: #fff7ed;
      color: #c2570a;
      .badge-dot {
        background-color: #f5a74b;
      }
    }

    &.badge-process {
      background-color: #f5f3ff;
      color: #6d28d9;
      .badge-dot {
        background-color: #6e66d1;
      }
    }
  }

  .create-node-form {
    .el-form-item__label {
      font-weight: 500;
    }

    .el-textarea__inner {
      font-size: 13px;
    }
  }
}
</style>
