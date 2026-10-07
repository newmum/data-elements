<template>
  <div class="card-container">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <!-- 顶部标题 -->
      <div class="sidebar-header">
        <div class="sidebar-title">
          <span class="sidebar-title__icon" aria-hidden="true"><Icon icon="document" /></span>
          <span>表单配置</span>
        </div>
          <el-button
            class="add-form-button"
            type="text"
            title="新增表单"
            aria-label="新增表单"
            @click="handleAddForm"
          >
            <Icon icon="el-icon-plus" />
          </el-button>
      </div>

      <!-- 表单列表内容 -->
      <div class="form-list-section">
        <div class="form-list">
          <div
            v-for="form in formList"
            :key="form.tid"
            :class="['form-item', currentFormName === form.formName ? 'form-item--active' : '']"
          >
            <!-- 表单信息和点击区域 -->
            <div class="form-item__body" @click="handleFormChange(form)">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
              <div class="form-item__info">
                <span class="form-item__name" :title="form.formName">
                  {{ form.formName }}
                </span>
                <span class="form-item__count">{{ form.fieldCount }} 个字段</span>
              </div>
            </div>

            <!-- 编辑删除操作按钮 -->
            <div class="form-item__actions">
              <button
                class="action-btn"
                :title="`重命名 ${form.formName}`"
                @click.stop="handleRenameForm(form)"
              >
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
                  />
                </svg>
              </button>
              <button
                class="action-btn action-btn--delete"
                :title="`删除 ${form.formName}`"
                @click.stop="handleDeleteForm(form)"
              >
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                  />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧主内容区 -->
    <main v-if="currentFormName" class="main-content">
      <!-- 顶部操作栏 -->
      <header class="main-header">
        <div class="breadcrumb">
          <span class="breadcrumb__title">
            {{ currentFormName }}
          </span>
          <span v-if="isModified" class="unsaved-dot" title="有未保存的修改" />
          <el-button text @click="handleRenameForm(currentFormObj)">
            <Icon icon="el-icon-edit" />
          </el-button>
        </div>
        <el-button :type="'primary'" :disabled="!isModified" @click="handleSave">
          <template #icon><Icon icon="save" /></template>
          保存
        </el-button>
      </header>

      <!-- 内容区 -->
      <el-splitter class="splitter-content">
        <!-- 代码视图 -->
        <el-splitter-panel class="code-panel" :size="30">
          <CodeEditor
            v-model="currentJson"
            lang="json"
            theme="monokai"
            height="100%"
            @change="handleEditorChange"
          />
        </el-splitter-panel>

        <!-- 预览视图 -->
        <el-splitter-panel class="preview-panel" :size="70">
          <el-scrollbar>
            <div v-if="previewError" class="preview-error">
              {{ previewError }}
            </div>
            <JsonForm v-else ref="previewFormRef" bordered :rules="currentRules" />
          </el-scrollbar>
        </el-splitter-panel>
      </el-splitter>
    </main>

    <div v-else class="empty-wrapper">
      <empty />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from "vue";
  import { cloneDeep } from "lodash-es";

interface FormListItem {
  tid: string;
  formName: string;
  formJson: string;
  env: string;
  fieldCount: number;
}

// ─── 状态 ────────────────────────────────────────────────────────────────────

const configStore = reactive<Record<string, FormListItem[]>>({});
const currentEnv = ref<string>($setting.systemCode);
const currentForm = ref<FormListItem>();
const currentFormName = ref<string>("");
const currentJson = ref<string>("");
const isModified = ref<boolean>(false);
const formList = ref<FormListItem[]>([]);
const previewError = ref<string>("");
const previewFormRef = ref<any>(null);

const currentFormObj = computed(() =>
  formList.value.find((f) => f.formName === currentFormName.value)
);

const currentRules = computed(() => getRulesArray(currentJson.value));

// ─── 工具函数 ────────────────────────────────────────────────────────────────

const getRulesArray = (value: any): any[] => {
  previewError.value = "";
  if (!value) {
    previewError.value = "请编辑左侧JSON配置以预览表单";
    return [];
  }
  if (Array.isArray(value)) return value;
  if (typeof value === "string") {
    try {
      const parsed = eval(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch (e: any) {
      previewError.value = `配置格式错误，无法预览：${e?.message}`;
      return [];
    }
  }
  return [];
};

// ─── 表单加载与切换 ─────────────────────────────────────────────────────────

const loadForms = async (env = currentEnv.value) => {
  try {
    const data = await $common.post("/sym/form?action=get", { env });
    configStore[env] = data.map((el) => {
      const rules = getRulesArray(el.formJson);
      return { ...el, fieldCount: Array.isArray(rules) ? rules.length : 0 };
    });
    formList.value = configStore[env];
    if (!currentFormName.value) {
      handleFormChange(formList.value[0]);
    }
    isModified.value = false;
  } catch (error) {
    $message.error("加载表单列表失败");
    console.error(error);
  }
};

const handleFormChange = async (form?: FormListItem) => {
  if (isModified.value) {
    try {
      await $dialog.confirm("当前配置未保存，是否放弃修改并切换表单？", "提示", {
        type: "warning",
      });
    } catch {
      return;
    }
  }
  currentForm.value = form;
  currentFormName.value = form?.formName ?? "";
  currentJson.value = cloneDeep(form?.formJson);
  isModified.value = false;
  previewError.value = "";
};

// ─── 编辑器 ──────────────────────────────────────────────────────────────────

const handleEditorChange = (value: string) => {
  isModified.value = currentForm.value?.formJson !== currentJson.value;
  // 强制更新，否则表单渲染不会更新
  currentRules.value = currentRules.value;
};

// ─── 保存 ────────────────────────────────────────────────────────────────────

const handleSave = async () => {
  if (!currentFormName.value || !isModified.value) return;

  getRulesArray(currentJson.value);
  if (previewError.value) return;

  try {
    await $common.post("/sym/form?action=save", {
      tid: currentFormObj.value?.tid,
      env: currentEnv.value,
      formName: currentFormName.value,
      formJson: currentJson.value,
    });

    // 同步本地缓存
    const envForms = configStore[currentEnv.value] || [];
    const idx = envForms.findIndex((item) => item.formName === currentFormName.value);
    if (idx > -1) {
      envForms[idx].formJson = currentJson.value;
    }

    isModified.value = false;
    $message.success("配置已保存");
  } catch (error) {
    $message.error("配置格式错误，无法保存");
    console.error(error);
  }
};

// ─── 新增表单 ────────────────────────────────────────────────────────────────

const DEFAULT_FORM_JSON = `[
  {
    type: "UTitle",
    props: {
      name: "基本信息",
    },
  },
  {
    type: "input",
    title: "名称",
    field: "name",
    col: {
      span: 24,
    },
    $required: true,
  },
]`;

const handleAddForm = () => {
  $dialog
    .prompt("", "新增表单", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputValue: "新表单",
    })
    .then(async ({ value }) => {
      if (!value?.trim()) {
        $message.error("表单名称不能为空");
        return;
      }
      const isDuplicate = (configStore[currentEnv.value] || []).some(
        (item) => item.formName === value
      );
      if (isDuplicate) {
        $message.error(`"${value}" 表单已存在`);
        return;
      }
      try {
        await $common.post("/sym/form?action=save", {
          env: currentEnv.value,
          formName: value,
          formJson: DEFAULT_FORM_JSON,
        });
        currentFormName.value = value;
        currentJson.value = DEFAULT_FORM_JSON;
        isModified.value = false;
        loadForms();
      } catch (error) {
        console.error(error);
      }
    });
};

// ─── 重命名表单 ──────────────────────────────────────────────────────────────

const handleRenameForm = (form: FormListItem | undefined) => {
  if (!form) return;
  $dialog
    .prompt("", "重命名表单", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputValue: form.formName,
    })
    .then(async ({ value }) => {
      if (!value?.trim() || value === form.formName) return;
      const isDuplicate = (configStore[currentEnv.value] || []).some(
        (item) => item.formName === value && item.tid !== form.tid
      );
      if (isDuplicate) {
        $message.error(`"${value}" 表单已存在`);
        return;
      }
      try {
        await $common.post("/sym/form?action=save", {
          env: currentEnv.value,
          formName: value,
          tid: form.tid,
        });
        loadForms();
      } catch (error) {
        $message.error("重命名失败，请重试");
        console.error(error);
      }
    })
    .catch(() => {});
};

// ─── 删除表单 ────────────────────────────────────────────────────────────────

const handleDeleteForm = async (form: FormListItem) => {
  try {
    await $dialog.confirm(`确定要删除表单 【${form.formName}】 吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      dangerMode: true,
    });
    await $common.post("/sym/form?action=delete", { tid: form.tid });

    const newForms = (configStore[currentEnv.value] || []).filter((item) => item.tid !== form.tid);
    configStore[currentEnv.value] = newForms;

    if (currentFormName.value === form.formName) {
      handleFormChange(newForms[0]);
    }

    formList.value = newForms;
    $message.success(`表单 "${form.formName}" 已删除`);
  } catch (error) {
    if (error !== "cancel") {
      $message.error("删除失败，请重试");
      console.error(error);
    }
  }
};

// ─── 生命周期 ────────────────────────────────────────────────────────────────

const onKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key?.toLowerCase() === "s") {
    e.preventDefault();
    handleSave();
  }
};

onMounted(() => {
  loadForms();
  window.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped lang="scss">
// ─── 根容器 ──────────────────────────────────────────────────────────────────
.card-container {
  display: flex;
  flex: 1;
  overflow: hidden;
}

// ─── 侧边栏 ──────────────────────────────────────────────────────────────────
.sidebar {
  width: 16rem;
  background-color: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;

  &-header {
    padding: 0.625rem 1rem;
    border-bottom: 1px solid #e5e7eb;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 1rem;
    font-weight: 500;
    color: #1f2937;

    &__icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2rem;
      height: 2rem;
      border-radius: 0.5rem;
      color: #fff;
      background: #2563eb;
    }
  }

  .add-form-button {
    padding: 4px 8px;
    color: var(--el-color-primary);

    &:hover {
      background-color: #1890ff1a;
    }
  }

  }

// ─── 表单列表区域 ────────────────────────────────────────────────────────────
.form-list-section {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.form-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

// ─── 表单条目 ────────────────────────────────────────────────────────────────
.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.375rem;
  cursor: pointer;
  transition:
    color 0.15s ease-in-out,
    background-color 0.15s ease-in-out;
  position: relative;
  color: #4b5563;

  &:hover {
    background-color: #f3f4f6;

    .form-item__actions {
      opacity: 1;
    }
  }

  &--active {
    background-color: #2563eb !important;
    color: #fff !important;
  }

  &__body {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    flex: 1;
    width: 100%;

    svg {
      width: 1rem;
      height: 1rem;
      flex-shrink: 0;
    }
  }

  &__info {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__name {
    font-size: 0.875rem;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__count {
    font-size: 0.75rem;
    opacity: 0.75;
    margin-top: 4px;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    opacity: 0;
    transition: opacity 0.15s ease-in-out;
    position: absolute;
    right: 0.5rem;
  }
}

// ─── 操作按钮 ────────────────────────────────────────────────────────────────
.action-btn {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    background-color 0.15s ease-in-out,
    color 0.15s ease-in-out;

  svg {
    width: 0.75rem;
    height: 0.75rem;
  }

  &:hover {
    background-color: #e5e7eb;
  }

  &--delete {
    color: #ef4444;

    &:hover {
      background-color: #fee2e2;
    }
  }
}

// ─── 主内容区 ────────────────────────────────────────────────────────────────
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-header {
  background-color: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 1.5rem;
}

// ─── 面包屑 ──────────────────────────────────────────────────────────────────
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1rem;
  color: #6b7280;

  svg {
    width: 1rem;
    height: 1rem;
  }

  &__title {
    color: #1f2937;
    font-weight: 500;
  }
}

// ─── 分割面板 ────────────────────────────────────────────────────────────────
.splitter-content {
  flex: 1;
  display: flex;
  padding: 1.5rem;
  overflow: hidden;
}

.code-panel {
  height: 100%;
  background-color: #1e1e1e;
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow:
    0 10px 15px -3px rgba(0, 0, 0, 0.1),
    0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

:deep(.preview-panel) {
  height: 100%;
  background-color: #fff;
  border-radius: 0.75rem;
  margin-left: 20px;
}

.preview-error {
  color: #ef4444;
  padding: 1rem;
  background-color: #fef2f2;
  border-radius: 0.5rem;
}

.empty-wrapper {
  margin-left: auto;
  margin-right: auto;
}

// ─── 未保存圆点动画 ──────────────────────────────────────────────────────────
.unsaved-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #f97316;
  box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.6);
  animation: pulse-dot 1.5s ease-in-out infinite;
  margin-right: 2px;
  flex-shrink: 0;
}

@keyframes pulse-dot {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.6);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(249, 115, 22, 0);
  }
}
</style>
