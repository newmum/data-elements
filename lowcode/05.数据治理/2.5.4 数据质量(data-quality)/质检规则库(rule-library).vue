<template>
  <div class="rule-library-container card-container flex flex-col pl-5">
    <div class="h-full grid" style="grid-template-columns: 20% 50% 30%">
      <!-- 左栏：质检维度 -->
      <div class="dimension-sidebar">
        <div class="sidebar-header flex items-center">
          <h3 class="header-title">质检维度</h3>
        </div>
        <nav class="dimension-nav">
          <div v-for="dim in dimensions" :key="dim.value" class="pr-3 mb-2">
            <div
              :class="{ active: String(dim.value) === activeDimension }"
              class="flex items-center justify-between p-3 cursor-pointer gap-2 dimension-item"
              @click="setActiveDimension(dim.value, dim.count)"
            >
              <div class="flex gap-3 flex-1 items-center">
                <div
                  class="dimension-item-icon flex items-center justify-between"
                  :style="{ backgroundColor: `${dim.tagType || '#0190f9'}20` }"
                >
                  <Icon
                    :icon="dim.icon || 'el-icon-timer'"
                    :color="dim.tagType || '#0190f9'"
                    size="26"
                  />
                </div>
                <div class="flex-1">
                  <p class="dimension-item-name">
                    {{ dim.dictName }}
                    <span class="dimension-item-count">
                      {{ dim.count }}
                    </span>
                  </p>
                  <p class="dimension-item-desc">{{ dim.desc }}</p>
                </div>
              </div>
              <!-- <span class="dimension-item-count">
                {{ dim.count }}
              </span> -->
            </div>
          </div>
        </nav>
      </div>

      <!-- 中栏：规则列表 -->
      <div class="rule-list-container">
        <div class="rule-list-header">
          <el-input
            v-model="searchQuery"
            placeholder="搜索预设规则..."
            prefix-icon="Search"
            style="width: 200px"
            @keyup.enter="getRuleData"
          />
          <el-button type="primary" @click="handleAddRule">
            <icon :icon="'el-icon-Plus'" class="mr-2" />
            自定义规则
          </el-button>
        </div>

        <div class="rule-list-content">
          <!-- 加载中状态 -->
          <div
            v-if="loading"
            v-loading="loading"
            :element-loading-text="'加载中...'"
            class="loading-container"
          ></div>

          <!-- 错误状态 -->
          <div v-else-if="error" class="loading-container">
            <Empty description="获取数据失败，请重试" />
          </div>

          <!-- 空数据状态 -->
          <div v-else-if="rules.length === 0" class="loading-container">
            <Empty description="暂无规则数据" />
          </div>

          <!-- 规则列表 -->
          <template v-else>
            <div
              v-for="rule in rules"
              :key="rule.tid"
              :class="['rule-card', { active: selectedRuleId === rule.tid }]"
              @click="setSelectedRule(rule)"
            >
              <div class="rule-card-header">
                <h4 class="rule-card-title">{{ rule.ruleName }}</h4>
                <div class="flex items-center">
                  <dict-label v-model="rule.ruleLevel" :options="ruleLevelOptions"></dict-label>
                  <div class="divider-vertical"></div>
                  <el-dropdown trigger="hover" @command="handleRuleCommand" @click.stop>
                    <Icon icon="el-icon-MoreFilled" class="more-icon" :size="13" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item :command="{ type: 'edit', rule }">
                          <Icon icon="el-icon-Edit" class="mr-2" />
                          编辑规则
                        </el-dropdown-item>
                        <el-dropdown-item
                          :command="{ type: 'delete', rule }"
                          style="color: #ff4d4f"
                        >
                          <Icon icon="el-icon-Delete" class="mr-2" />
                          删除规则
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
              <p class="rule-card-desc">{{ rule.ruleDescription }}</p>
              <div class="rule-card-footer">
                <span class="rule-card-code">{{ rule.ruleCode }}</span>
                <Icon
                  icon="el-icon-ArrowRight"
                  :color="selectedRuleId === rule.tid ? '#1b67f8' : '#94a3b8'"
                  class="rule-card-arrow"
                />
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 右栏：规则详情展示 -->
      <div class="rule-detail-container">
        <div class="detail-header flex items-center justify-between">
          <h3 class="header-title">{{ formType === "add" ? "规则定义" : "规则定义详情" }}</h3>
          <div v-if="formType === 'add'">
            <el-button type="primary" :disabled="isSaving" @click="handleSaveRule">
              <icon :icon="'save'" class="mr-2" />
              保存
            </el-button>
            <el-button @click="handleCancelRule">
              <icon :icon="'el-icon-Close'" class="mr-2" />
              取消
            </el-button>
          </div>
        </div>
        <div class="detail-form">
          <json-form ref="formRef" :rules="formRules" :options="formOptions"></json-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h, nextTick, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { FormUtils } from "@/utils/form";
import { useDictStore } from "@/store";
import Empty from "@/components/base/empty.vue";

const ruleLevelOptions = useDictStore().getDictItems("ruleLevel") || [];
const qualityDimension = useDictStore().getDictItems("qualityDimension") || [];

interface Rule {
  tid: string;
  ruleName: string;
  ruleCode: string;
  checkDimension: string;
  ruleLevel: string;
  ruleDescription: string;
  checkLogic: string;
  scopeOfApplication: string;
  configParameters: string;
  // exception: string;
}

interface DimensionItem {
  dictName: string;
  value: string;
  label: string;
  tagType?: string;
  name?: string;
  color?: string;
  icon?: string;
  desc?: string;
  count?: number;
}

const activeDimension = ref("");
const selectedRuleId = ref("");
const searchQuery = ref("");
const formType = ref("view"); // add 或 view
const formRef = ref();
const currentEditRule = ref<Rule | null>(null);
const formOptions = {
  form: {
    labelPosition: "top",
  },
};
const formRules = computed(() => {
  return FormUtils.fixJson([
    {
      type: "UTitle",
      props: {
        name: "基础信息",
      },
    },
    {
      type: "input",
      title: "规则名称",
      field: "ruleName",
      $required: true,
      props: {
        disabled: formType.value === "view",
      },
    },
    {
      type: "input",
      title: "规则编码",
      field: "ruleCode",
      $required: true,
      props: {
        disabled: formType.value === "view",
      },
      renderSlots: {
        append() {
          return h("div", {
            class: "i-svg:idea cursor-pointer",
            style: "font-size: 18px;",
            title: "点击生成随机编码",
            onClick: async () => {
              if (formType.value === "view") return;
              formRef.value.setValue({
                ruleCode: `code_${Math.random().toString(36).substring(2, 10)}`,
              });
            },
          });
        },
      },
    },
    {
      type: "select",
      title: "质检维度",
      field: "checkDimension",
      props: {
        options: qualityDimension,
        disabled: formType.value === "view",
      },
      $required: true,
    },
    {
      type: "select",
      title: "规则层级",
      field: "ruleLevel",
      props: {
        options: ruleLevelOptions,
        disabled: formType.value === "view",
      },
      $required: true,
    },
    {
      type: "input",
      field: "ruleDescription",
      title: "规则描述",
      props: {
        type: "textarea",
        placeholder: "请简要描述规则的功能和作用",
        maxlength: 500,
        rows: 4,
        showWordLimit: true,
        disabled: formType.value === "view",
      },
      $required: true,
    },
    {
      type: "input",
      title: "适用范围",
      field: "scopeOfApplication",
      $required: true,
      props: {
        disabled: formType.value === "view",
      },
    },
    {
      type: "input",
      title: "配置参数",
      field: "configParameters",
      $required: true,
      props: {
        disabled: formType.value === "view",
      },
    },
    {
      type: "code-editor",
      title: "校验逻辑",
      field: "checkLogic",
      $required: true,
      props: {
        readOnly: formType.value === "view",
      },
    },
  ]);
});
const rules = ref<Rule[]>([]);
const dimensions = ref<DimensionItem[]>([]);
const loading = ref(false);
const error = ref(false);
const isSaving = ref(false);

const handleAddRule = async () => {
  formType.value = "add";
  selectedRuleId.value = "";
  currentEditRule.value = null;

  // 等待DOM更新后再操作表单
  await nextTick();

  if (formRef.value) {
    formRef.value.resetFields();
    formRef.value.setValue({
      ruleName: "",
      ruleCode: "",
      checkDimension: activeDimension.value,
      ruleLevel: "",
      ruleDescription: "",
      checkLogic: "",
      scopeOfApplication: "",
      configParameters: "",
    });
  }
};

const handleSaveRule = async () => {
  await formRef.value?.validate();
  isSaving.value = true;
  try {
    const formData = await formRef.value.getFormData();
    // 如果是编辑模式，添加tid
    if (currentEditRule.value) {
      formData.tid = currentEditRule.value.tid;
    }
    const data = await $common.post("/dwm/quality/saveOrUpdate", formData);
    console.log("handleSaveRule", data);
    if (data) {
      ElMessage.success(currentEditRule.value ? "更新成功" : "保存成功");
      // 保存成功后切换到view模式并刷新数据
      formType.value = "view";
      currentEditRule.value = null;
      // 先刷新维度数据
      await getDimensionsData();
      // 确保规则列表数据已更新，并选中最新添加的规则
      await nextTick();
      // 如果有返回的规则ID，选中该规则
      const newRule: Rule | undefined = rules.value.find((r) => r.tid == data);
      if (newRule) {
        setSelectedRule(newRule);
      } else {
        if (rules.value.length > 0) {
          setSelectedRule(rules.value[0]);
        }
      }
    } else {
      ElMessage.error("保存失败");
    }
  } catch (error) {
    console.error("Save rule error:", error);
    ElMessage.error("保存失败");
  } finally {
    isSaving.value = false;
  }
};
const handleCancelRule = async () => {
  // 切换回view模式
  formType.value = "view";

  // 等待DOM更新后再操作表单
  await nextTick();

  // 如果当前维度有规则，选中第一个规则
  if (rules.value.length > 0) {
    setSelectedRule(rules.value[0]);
  } else {
    clearSelectedRule();
  }
};

const clearSelectedRule = () => {
  selectedRuleId.value = "";
  if (formRef.value) {
    formRef.value.resetFields();
    formRef.value.setValue({
      ruleName: "",
      ruleCode: "",
      checkDimension: activeDimension.value,
      ruleLevel: "",
      ruleDescription: "",
      checkLogic: "",
      scopeOfApplication: "",
      configParameters: "",
    });
  }
};

const setActiveDimension = async (value: string, count?: number) => {
  activeDimension.value = value;
  // 重置选中的规则ID为当前维度的第一个规则
  if (count && count > 0) {
    await getRuleData();
  } else {
    rules.value = [];
    clearSelectedRule();
  }
};

const setSelectedRule = (rule: Rule) => {
  formType.value = "view";
  selectedRuleId.value = rule.tid;
  currentEditRule.value = null;
  formRef.value?.setValue(rule);
};

const getRuleData = async () => {
  loading.value = true;
  error.value = false;

  try {
    const res: any = await $common.post("/dwm/quality/list", {
      checkDimension: activeDimension.value,
      ruleName: searchQuery.value,
    });

    if (res) {
      rules.value = res || [];
      setSelectedRule(rules.value[0]);
    } else {
      rules.value = [];
      clearSelectedRule();
    }
  } catch (err) {
    console.error("Failed to get rule data:", err);
    error.value = true;
    rules.value = [];
    ElMessage.error("获取规则数据失败");
  } finally {
    loading.value = false;
  }
};

const getDimensionsData = async () => {
  const data: any =
    (await $common.get("/dwm/quality/getQualityDimension")) || [];
  dimensions.value = data || [];
  // 初始化默认值
  if (dimensions.value.length > 0) {
    const dim =
      activeDimension.value === ""
        ? dimensions.value[0]
        : dimensions.value.find((item) => item.value === activeDimension.value);
    setActiveDimension(dim?.value || "", dim?.count || 0);
  }
};

const handleRuleCommand = async (command: { type: string; rule: Rule }) => {
  const { type, rule } = command;

  if (type === "edit") {
    // 编辑规则
    await handleEditRule(rule);
  } else if (type === "delete") {
    // 删除规则
    await handleDeleteRule(rule);
  }
};

const handleEditRule = async (rule: Rule) => {
  formType.value = "add";
  currentEditRule.value = rule;
  selectedRuleId.value = rule.tid;

  // 等待DOM更新后再操作表单
  await nextTick();

  if (formRef.value) {
    formRef.value.resetFields();
    formRef.value.setValue(rule);
  }
};

const handleDeleteRule = async (rule: Rule) => {
  $common.handle({
    url: "/dwm/quality/deleteById",
    info: `确定要删除规则【${rule.ruleName}】吗？`,
    data: { tid: rule.tid },
    done: async () => {
      // 删除成功后刷新数据
      await getDimensionsData();
      // 如果删除的是当前选中的规则，清空表单
      if (selectedRuleId.value === rule.tid) {
        clearSelectedRule();
      }
    },
  });
};

onMounted(async () => {
  await getDimensionsData();
});
</script>

<style scoped lang="scss">
.rule-library-container {
  height: 100%;
  .dimension-sidebar {
    display: flex;
    flex-direction: column;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    // padding-right: 5px;
    overflow-y: auto;

    .sidebar-header {
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 15px;
      margin-top: 20px;
      height: 43px;
    }

    .dimension-nav {
      flex: 1;
      overflow-y: auto;
      .active {
        background-color: #eff6ff;
        .dimension-item-name {
          font-size: 15px;
          color: #1d4ed8;
        }
        // .dimension-item-count {
        //   color: #ffffff;
        //   font-size: 13px;
        //   background-color: #2563eb;
        // }
      }
      .dimension-item {
        border-radius: 8px;
        &:hover {
          background-color: #f8fafc;
        }
        .dimension-item-icon {
          padding: 9px;
          border-radius: 8px;
          height: 44px;
          width: 44px;
        }
      }
      .dimension-item-name {
        margin: 0;
        font-size: 15px;
        font-weight: 500;
        color: #0f172a;
        line-height: 25px;
      }
      .dimension-item-desc {
        margin: 0;
        font-size: 13px;
        line-height: 1.5;
        color: #94a3b8;
      }
      .dimension-item-count {
        font-size: 14px;
        color: #64748b;
      }
    }
  }
  .rule-list-container {
    display: flex;
    flex-direction: column;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow: auto;
    background-color: #ffffff;

    .rule-list-header {
      padding: 15px;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      display: flex;
      align-items: center;
      justify-content: space-between;
      background-color: #ffffff;
      flex-shrink: 0;
    }

    .rule-list-content {
      flex: 1;
      overflow-y: auto;
      padding: 15px;
      gap: 0.75rem;
      display: flex;
      flex-direction: column;
      background-color: #fafbff;

      .loading-container {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 400px;
      }

      .rule-card {
        padding: 1rem;
        border-radius: 4px;
        background: #fff;
        border: 1px solid #d8dee6;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          border: 1px solid #bfdbfe;
          box-shadow: 0 0 12px #bfdbfe26;
          :deep(.rule-card-arrow) {
            transform: translateX(4px);
          }
        }

        &.active {
          border: 1px solid #1b67f8;
          box-shadow: 0 0 12px #1b67f826;
        }

        .rule-card-header {
          display: flex;
          align-items: center;
          justify-content: space-between;

          .rule-card-title {
            font-weight: 500;
            font-size: 15px;
            text-align: left;
            color: #0f172a;
            margin: 0;
          }

          .el-tag {
            font-size: 0.625rem;
            font-weight: bold;
          }

          .divider-vertical {
            width: 1px;
            height: 16px;
            background-color: #d8dee6;
            margin: 0 8px 0 12px;
          }

          .more-icon {
            cursor: pointer;
            color: #94a3b8;
            transition: all 0.2s ease;
            transform: rotate(90deg);
            outline: none;

            &:hover {
              color: #1b67f8;
            }
          }
        }

        .rule-card-desc {
          font-family: "Source Han Sans SC";
          font-weight: 400;
          font-size: 13px;
          color: #94a3b8;
          padding: 8px 0;
          margin: 0;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .rule-card-footer {
          display: flex;
          align-items: center;
          justify-content: space-between;

          .rule-card-code {
            font-weight: 400;
            font-size: 13px;
            color: #94a3b8;
          }

          .rule-card-arrow {
            transition: transform 0.2s ease;
          }
        }
      }
    }
  }
  .rule-detail-container {
    display: flex;
    flex-direction: column;
    overflow: auto;
    .detail-header {
      padding: 0 15px;
      height: 32px;
      margin-top: 23px;
      margin-bottom: 15px;
    }
    .detail-title {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #464c64;
      line-height: 32px;
    }
    .detail-form {
      padding: 0 15px;
      overflow: auto;
    }
  }
  .header-title {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #464c64;
  }
}
</style>
