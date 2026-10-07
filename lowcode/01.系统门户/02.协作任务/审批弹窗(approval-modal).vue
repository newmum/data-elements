<template>
  <u-modal
    v-model="visible"
    title="资产审批"
    width="600"
    class="approval-modal"
    :draggable="false"
    @confirm="handleSubmit"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
      <!-- 审批结果 -->
      <el-form-item label="审批结果" prop="decision">
        <el-radio-group v-model="formData.decision" @change="changeDecision">
          <el-radio v-for="item in approveOptions" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 审批意见 -->
      <el-form-item label="审批意见" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="4"
          :maxlength="500"
          show-word-limit
          placeholder="请填写审批意见,最多可输入500个字符"
        />
        <el-checkbox v-model="formData.remarkSave" class="mt-2">设为常用意见</el-checkbox>
      </el-form-item>

      <!-- 常用意见 -->
      <el-form-item label="常用意见">
        <el-select
          v-model="formData.commonSuggest"
          :popper-class="'approval-suggest'"
          placeholder="请选择常用意见"
          style="width: 100%"
          clearable
          @change="changeSuggest"
        >
          <el-option
            v-for="item in suggestList"
            :key="item.tid"
            :label="item.content"
            :value="item.tid"
          >
            <div class="flex justify-between items-center">
              <span>{{ item.content }}</span>
              <Icon
                :color="'#ff4757'"
                icon="el-icon--delete"
                class="cucursor-pointerr"
                @click.stop="delSuggest(item.tid)"
              ></Icon>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 选择驳回节点 -->
      <el-form-item
        v-if="formData.decision === 'REJECT'"
        label="选择驳回节点"
        prop="rejectNodeCode"
      >
        <el-select
          v-model="formData.rejectNodeCode"
          placeholder="请选择驳回节点"
          style="width: 100%"
        >
          <el-option
            v-for="item in rejectNodes"
            :key="item.nodeCode"
            :label="item.nodeName"
            :value="item.nodeCode"
          />
        </el-select>
      </el-form-item>
    </el-form>
  </u-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

const props = defineProps({
  modelValue: Boolean,
  taskId: {
    type: String,
    required: true,
  },
  info: {
    type: Object,
    required: true,
  },
  flowStatusType: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(["update:modelValue", "close", "submit-success"]);
const formRef = ref();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit("update:modelValue", value);
  },
});

// 表单数据
const formData = reactive({
  decision: "PASS",
  remark: "",
  remarkSave: false,
  commonSuggest: "",
  rejectNodeCode: "",
});

// 常用意见列表
const suggestList = ref<any[]>([]);

// 获取常用意见列表
const fetchSuggestList = async () => {
  try {
      const response = await $common.get("/sym/approval/query/suggestionList");
    const data = response.data || response;
    suggestList.value = data || [];
  } catch (error) {
    console.error("获取常用意见列表失败:", error);
    suggestList.value = [];
  }
};

// 驳回节点列表
const rejectNodes = ref<any[]>([]);

// 审批选项
const approveOptions = ref<{ label: string; value: string }[]>([]);
const todoOptions = ref([
  { label: "通过", value: "PASS" },
  { label: "不通过", value: "UNPASS" },
  { label: "驳回", value: "REJECT" },
]);
const revokeOptions = ref([{ label: "撤回", value: "REVOKE" }]);

// 根据 flowStatusType 动态调整审批选项
watch(
  () => props.flowStatusType,
  (newValue) => {
    if (newValue === "process") {
      // 当 flowStatusType 是 process 时，只有一个撤回选项
      approveOptions.value = revokeOptions.value;

      // 默认选中撤回
      formData.decision = "REVOKE";
    } else {
      approveOptions.value = todoOptions.value;
      // 默认选中通过
      formData.decision = "PASS";
    }
  },
  { immediate: true }
);

// 表单验证规则
const rules = reactive({
  decision: [{ required: true, message: "请选择审批结果", trigger: "change" }],
  remark: [{ required: true, message: "请填写审批意见", trigger: "blur" }],
  rejectNodeCode: [
    {
      required: true,
      message: "请选择驳回节点",
      trigger: "change",
      validator: (rule: any, value: any, callback: any) => {
        if (formData.decision === "REJECT" && !value) {
          callback(new Error("请选择驳回节点"));
        } else {
          callback();
        }
      },
    },
  ],
});

watch(
  () => visible.value,
  (newValue) => {
    if (newValue) {
      // 弹窗打开时，重新初始化 formData
      Object.assign(formData, {
        decision: props.flowStatusType === "process" ? "REVOKE" : "PASS",
        remark: "",
        remarkSave: false,
        commonSuggest: "",
        rejectNodeCode: "",
      });
      // 获取常用意见列表
      fetchSuggestList();
    }
  }
);

// 获取驳回节点列表
const fetchRejectNodes = async () => {
  try {
      const response = await $common.post("/sym/approval/operate/previousNodeList", {
      definitionId: props.info.definitionId,
      nodeCode: props.info.nodeCode,
    });
    const data = response.data || response || [];
    rejectNodes.value = data.filter((item: any) => item.nodeCode !== "start");
  } catch (error) {
    console.error("获取驳回节点失败:", error);
    rejectNodes.value = [];
  }
};

// 审批结果变更
const changeDecision = () => {
  // 如果不是驳回，清空驳回节点
  if (formData.decision !== "REJECT") {
    formData.rejectNodeCode = "";
  } else {
    // 当选择驳回时，获取驳回节点列表
    fetchRejectNodes();
  }
};

// 常用意见变更
const changeSuggest = (value: string) => {
  console.log("常用意见变更:", value);
  // 根据选择的常用意见设置审批结果
  const suggest = suggestList.value.find((item) => item.tid === value);
  if (suggest) {
    // 回填到审批意见输入框
    formData.remark = suggest.content;
    // 这里可以根据常用意见设置对应的审批结果
  }
};

// 删除常用意见
const delSuggest = (tid: string) => {
  ElMessageBox.confirm("确定要删除此常用意见吗？", "删除确认", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(async () => {
      try {
        // 调用删除接口
          await $common.post("/sym/approval/query/deleteSuggestion", {
          tid,
        });
        const index = suggestList.value.findIndex((item) => item.tid === tid);
        if (index > -1) {
          suggestList.value.splice(index, 1);
          // 如果删除的是当前选中的常用意见，清空选择
          if (formData.commonSuggest === tid) {
            formData.commonSuggest = "";
          }
          ElMessage.success("删除成功");
        }
      } catch (error) {
        console.error("删除常用意见失败:", error);
        ElMessage.error("删除失败");
      }
    })
    .catch(() => {
      // 取消删除
      console.log("取消删除常用意见");
    });
};

// 提交表单
const handleSubmit = async () => {
  try {
    // 验证表单
    if (formRef.value) {
      await formRef.value.validate();
    }
    // 构建请求参数
    const requestData: any = {
      taskId: props.taskId,
      businessId: props.info.businessId,
      handleType: formData.decision,
      message: formData.remark,
      nodeCode: formData.rejectNodeCode,
      approveType: props.info.ext?.flowCode,
    };

    // 如果选择了常用意见，添加 suggestionId 字段
    if (formData.commonSuggest) {
      requestData.suggestionId = formData.commonSuggest;
    }

    // 如果勾选了设为常用意见，添加 storageSuggestion 字段
    if (formData.remarkSave) {
      requestData.storageSuggestion = "1";
    }

    // 发送审批请求
      await $common.post("/sym/approval/operate/handle", requestData);
    ElMessage.success("审批提交成功");
    visible.value = false;
    // 触发成功事件，通知父组件刷新数据
    emit("submit-success");
  } catch (error) {
    console.error("表单验证失败:", error);
  }
};

// 关闭模态框
const handleClose = () => {
  // 重置表单
  formRef.value?.resetFields();
  visible.value = false;
  emit("close");
};
</script>

<style lang="scss">
.approval-modal {
  padding: 20px 24px !important;
  .el-dialog__footer {
    border-top: 0;
  }
}
.approval-suggest {
  .el-select-dropdown__item {
    padding: 0 20px !important;
  }
}
</style>
