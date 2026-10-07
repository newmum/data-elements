<template>
  <el-drawer
    :model-value="modelValue"
    title="查看DDL创建语句"
    size="700"
    class="ddl-preview-drawer"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div class="ddl-preview-content">
      <div class="ddl-preview-editor">
        <CodeEditor
        v-model="ddlSql"
        lang="sql"
        theme="chrome"
        :height="'100%'"
        :show-option="false"
        :read-only="true"
        />
      </div>
    </div>
    <template #footer>
      <div class="flex" style="justify-content: flex-end">
        <el-button :icon="'el-icon-CopyDocument'" type="primary" @click="handleCopy">
          一键复制
        </el-button>
        <el-button :icon="'el-icon-Close'" @click="handleClose(false)">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  /** 数据表信息（tid, dbId, tableName, tableNameCn） */
  tableInfo: {
    type: Object,
    default: () => ({}),
  },
  /** 数据库信息（dbType） */
  dbInfo: {
    type: Object,
    default: () => ({}),
  },
  /** 表字段列表 */
  tableFields: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["update:modelValue"]);

const ddlSql = ref("");

// 关闭弹窗
const handleClose = (val: boolean = false) => {
  emit("update:modelValue", val);
};

// 打开时自动加载 DDL
watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return;

    if (!props.tableInfo?.tid) {
      $message.warning("暂无关联数据表");
      handleClose(false);
      return;
    }

    ddlSql.value = "";

    try {
      const propList = {
        dbType: props.dbInfo?.dbType,
        dbId: props.tableInfo?.dbId,
        tableName: props.tableInfo?.tableName,
        tableNameCn: props.tableInfo?.tableNameCn,
      };

      const tableItems = Array.isArray(props.tableFields) ? props.tableFields : [];

      const res = await $common.post("/dst/database/metadata/getCreateTableDDL", {
        propList,
        tableItems,
      });

      ddlSql.value = res || "";
    } catch (error) {
      console.error("获取DDL失败", error);
      $message.error("获取DDL创建语句失败");
    }
  }
);

// 复制 DDL 语句
const handleCopy = () => {
  if (ddlSql.value) {
    $common.copyText(ddlSql.value);
  } else {
    $message.warning("没有可复制的内容");
  }
};
</script>

<style lang="scss" scoped>
// DDL drawer uses the remaining body height all the way down to the Ace editor.
:global(.ddl-preview-drawer .el-drawer__body) {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-content) {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor) {
  min-height: 0;
  width: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor > .el-scrollbar) {
  min-height: 0;
  height: 100%;
  width: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__wrap),
:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__view) {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
}

:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__view) {
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor .ace_editor) {
  min-height: 0 !important;
  height: 100% !important;
  flex: 1 1 auto;
}
</style>
