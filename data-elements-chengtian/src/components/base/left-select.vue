<template>
  <div class="left-title">
    <h4 class="list-title">{{ title }}</h4>
    <div class="asset-list" @scroll.passive="loadMoreOnScroll">
      <template v-for="item in visibleList" :key="item.did">
        <div
          class="asset-list-item"
          :class="{ 'asset-list-item-selected': currentDid === item.did }"
          @click="handleSelect(item)"
        >
          <div class="asset-list-item-title flex-y-center gap-2">
            <Icon :icon="icon" size="18px" style="color: var(--el-color-primary)" />
            <div v-if="showEdit" class="editable-title-wrapper flex-1 group min-w-0">
              <input
                :value="item[fieldName.title]"
                class="editable-title-input"
                placeholder="待命名"
                maxlength="255"
                @click.stop
                @change="(e) => handleRename(item, (e.target as HTMLInputElement).value)"
              />
              <Icon icon="edit" size="14" class="editable-title-icon flex-shrink-0" />
            </div>
            <el-text v-else class="flex-1" truncated>
              {{ item[fieldName.title] || "待命名" }}
            </el-text>
          </div>
          <template v-if="true">
            <el-dropdown trigger="hover">
              <div class="flex items-center" @click.stop>
                <template v-if="showTip">
                  <ElIcon v-if="!item.tid" class="mr-2 text-red-6" :size="16">
                    <WarningFilled />
                  </ElIcon>
                  <ElIcon v-else class="mr-2 text-green-6" :size="16"><CircleCheckFilled /></ElIcon>
                </template>
                <ElIcon v-if="showDelete" :size="12"><MoreFilled rotate-90 /></ElIcon>
              </div>
              <template #dropdown>
                <el-dropdown-menu v-if="showDelete">
                  <el-dropdown-item style="color: #ff7875" @click="onMenuClick('delete', item)">
                    <ElIcon><Delete /></ElIcon>
                    移除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </template>
      <div v-if="loadingMore" class="asset-list-loading" aria-label="正在加载更多数据源">
        <el-skeleton animated>
          <template #template>
            <div v-for="index in 3" :key="index" class="asset-list-loading-item">
              <el-skeleton-item variant="circle" class="asset-list-loading-icon" />
              <el-skeleton-item variant="text" class="asset-list-loading-text" />
              <el-skeleton-item variant="circle" class="asset-list-loading-status" />
            </div>
          </template>
        </el-skeleton>
      </div>
      <template v-if="!list?.length && !loadingMore">
        <empty
          :type="icon"
          compact
          :title="`暂无${title || '数据'}`"
          description="当前还没有可选择的业务数据，可点击下方按钮新增。"
        />
      </template>
      <div v-if="hasMore" class="asset-list-more">继续向下滚动加载更多（已显示 {{ visibleList.length }} / {{ list.length }}）</div>
    </div>
    <div v-if="showAdd" class="list-btn">
      <el-button type="primary" plain icon="Plus" @click="add">添加</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, PropType, ref, watch } from "vue";

const emit = defineEmits(["update:modelValue", "select", "delete", "add", "rename"]);
const props = defineProps({
  modelValue: {
    type: String,
    required: true,
  },
  title: String,
  icon: {
    type: String,
    default: "category",
  },
  currentId: String,
  list: {
    type: [] as PropType<Record<string, any>[]>,
    default: [],
  },
  fieldName: {
    type: Object as PropType<Record<string, string>>,
    default: () => ({
      title: "assetName",
      type: "assetClass",
    }),
  },
  showTip: {
    type: Boolean,
    default: true,
  },
  showAdd: {
    type: Boolean,
    default: true,
  },
  showDelete: {
    type: Boolean,
    default: true,
  },
  showEdit: {
    type: Boolean,
    default: false,
  },
  // 已存在的列表项无需等待远程补充列表；在尾部保留骨架，避免数据突然插入造成跳变。
  loadingMore: {
    type: Boolean,
    default: false,
  },
});

const currentDid = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

const PAGE_SIZE = 60;
const renderLimit = ref(PAGE_SIZE);
const visibleList = computed(() => (props.list || []).slice(0, renderLimit.value));
const hasMore = computed(() => (props.list || []).length > visibleList.value.length);

const loadMoreOnScroll = (event: Event) => {
  const target = event.target as HTMLElement;
  if (!hasMore.value || target.scrollTop + target.clientHeight < target.scrollHeight - 48) return;
  renderLimit.value = Math.min(renderLimit.value + PAGE_SIZE, props.list.length);
};

// methods
const handleSelect = (item: any) => {
  currentDid.value = item.did;
  emit("select", item);
};
const onMenuClick = (key: string, item: object) => {
  switch (key) {
    case "delete":
      emit("delete", item);
      break;
  }
};
const handleRename = (item: any, value: string) => {
  if (!value || !value.trim()) return;
  emit("rename", item, value);
};
const add = () => {
  emit("add");
};

watch(
  () => props.list,
  (newList) => {
    renderLimit.value = Math.max(PAGE_SIZE, Math.min(renderLimit.value, newList?.length || PAGE_SIZE));
    if (newList?.length > 0 && !newList.some((el) => el.did === currentDid.value)) {
      handleSelect(newList[0]);
    }
    if (!newList?.length) {
      currentDid.value = "";
    }
  },
  { immediate: true }
);

const currentItem = computed(() => props.list.find((el) => el.did === props.modelValue));

defineExpose({
  currentItem,
});
</script>

<style scoped lang="scss">
.left-title {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 0 10px 0;
  margin-right: 16px;
  overflow: hidden;
  border-right: 1px solid #ebeef5;

  &:empty {
    display: none;
  }

  .list-title {
    padding: 0 20px;
  }

  .asset-list {
    padding: 8px 0;
    overflow-y: auto;

    .asset-list-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 55px;
      padding: 0 16px 0 20px;
      margin-bottom: 0;
      line-height: 55px;
      cursor: pointer;
      border-bottom: 1px solid #f0f0f0;
      border-radius: 0;
      transition: all 0.3s;

      &:hover {
        background-color: #f8f8f8;
      }

      .asset-list-item-title {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        font-family: Source Han Sans SC;
        font-size: 14px;
        font-weight: 400;
        color: #323643;
        white-space: nowrap;
      }

      .editable-title-wrapper {
        position: relative;
        display: flex;
        align-items: center;

        .editable-title-input {
          width: 100%;
          padding: 2px 4px;
          overflow: hidden;
          text-overflow: ellipsis;
          font-family: "Source Han Sans SC", sans-serif;
          font-size: 14px;
          font-weight: 400;
          color: #323643;
          white-space: nowrap;
          outline: none;
          background: transparent;
          border: none;
          border-bottom: 1px solid transparent;
          transition: all 0.2s;

          &::placeholder {
            font-style: italic;
            color: #b0b0b0;
          }

          &:hover {
            border-bottom-color: #e5e5e5;
          }

          &:focus {
            background: #f4f7fc;
            border-bottom-color: var(--el-color-primary);
            border-radius: 4px 4px 0 0;
          }
        }

        .editable-title-icon {
          position: absolute;
          right: 4px;
          color: #c0c4cc;
          opacity: 0;
          transition: opacity 0.2s;
        }

        &:hover .editable-title-icon {
          opacity: 1;
        }

        .editable-title-input:focus + .editable-title-icon {
          opacity: 0;
        }
      }
    }

    .asset-list-item-selected {
      color: #409eff;
      background-color: #eef6fb;
      &:hover {
        background-color: #eef6fb;
      }
    }

    .asset-list-loading {
      padding: 4px 0;

      .asset-list-loading-item {
        display: flex;
        align-items: center;
        height: 55px;
        padding: 0 16px 0 20px;
        border-bottom: 1px solid #f0f0f0;
      }

      .asset-list-loading-icon {
        width: 18px;
        height: 18px;
        margin-right: 10px;
      }

      .asset-list-loading-text {
        flex: 1;
        max-width: 180px;
        height: 16px;
      }

      .asset-list-loading-status {
        width: 16px;
        height: 16px;
        margin-left: auto;
      }
    }

    .asset-list-more {
      padding: 10px 16px;
      color: #94a3b8;
      font-size: 12px;
      line-height: 18px;
      text-align: center;
    }
  }

  .list-btn {
    padding: 12px 16px;
    text-align: center;
    button {
      width: 110px;
    }
    button:hover {
      background-color: #f4f8ff !important;
    }
  }
}
</style>
