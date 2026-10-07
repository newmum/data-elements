<template>
  <el-drawer
    v-model="visible"
    :title="processName"
    :size="900"
    :direction="'rtl'"
    :close-on-press-escape="false"
    :close-on-click-modal="false"
    custom-class="process-detail-drawer"
    @close="onClose"
  >
    <!-- 抽屉内容区 -->
    <div class="drawer-content">
      <!-- 1. 图例栏 -->
      <div class="legend-bar">
        <div class="legend-item">
          <div class="legend-item-icon network">
            <Icon :icon="'user2'" />
          </div>
          <span class="legend-item-text">业务对象</span>
        </div>
        <div class="legend-item">
          <div class="legend-item-icon check-square">
            <Icon :icon="'process'" />
          </div>
          <span class="legend-item-text">业务事项</span>
        </div>
        <div class="legend-item">
          <div class="legend-item-icon layers">
            <Icon :icon="'transaction'" />
          </div>
          <span class="legend-item-text">业务事件</span>
        </div>
      </div>

      <!-- 2. 关系拓扑图画布 -->
      <div class="topology-canvas" :class="{ loading: topologyLoading }">
        <!-- 加载中 -->
        <div v-if="topologyLoading" class="topology-placeholder">
          <div class="topology-spinner"></div>
          <span>加载拓扑数据…</span>
        </div>

        <!-- 空状态 -->
        <div v-else-if="topObjects.length === 0 && steps.length === 0" class="topology-placeholder">
          <empty :title="'暂无关联目录'"></empty>
        </div>

        <template v-else>
          <!-- A. 顶层：普通对象 (并排) -->
          <div
            class="top-objects"
            :class="{ single: topObjects.length === 1 }"
            :style="topObjectsStyle"
          >
            <div v-for="(obj, i) in topObjects" :key="i" class="object-item">
              <div class="object-card" @click="handleClick(obj)">
                <div class="object-icon">
                  <Icon :icon="'user2'" />
                </div>
                <div class="object-info">
                  <p class="object-name" :title="obj.catalogName">{{ obj.catalogName }}</p>
                </div>
                <div class="object-dots">
                  <div class="dot"></div>
                  <div class="dot"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 顶层到中层的垂直实线（仅当有顶层对象时） -->
          <div v-if="topObjects.length > 0" class="vertical-line">
            <div class="horizontal-line" :style="horizontalLineStyle"></div>
            <div class="line-corner"></div>
          </div>

          <!-- B. 中层：核心业务事项 (主体) -->
          <div class="core-process">
            <div class="process-card">
              <div class="process-content">
                <div class="process-icon">
                  <Icon :icon="'process'" :size="24" />
                </div>
                <div class="process-info">
                  <p class="process-name">{{ processName }}</p>
                  <p class="process-code">{{ processEn }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- C. 中层到底层的虚线分支（仅当有业务事件时） -->
          <div v-if="steps.length > 0" class="dashed-branches">
            <svg :viewBox="`0 0 ${dashedSvgW} 64`" class="branches-svg">
              <path
                :d="dashedPathD"
                fill="none"
                stroke="#3B48CC"
                stroke-width="1.5"
                stroke-dasharray="4,4"
                opacity="0.3"
              />
            </svg>
          </div>

          <!-- D. 底层：业务事件流 (横向流转) -->
          <div v-if="steps.length > 0" class="event-flow" :style="eventFlowStyle">
            <div v-for="(step, idx) in steps" :key="idx" class="event-item">
              <div class="event-card" @click="handleClick(step)">
                <div class="event-header">
                  <div class="event-icon">
                    <Icon :icon="'transaction'" />
                  </div>
                  <div class="event-dots">
                    <div class="dot"></div>
                    <div class="dot"></div>
                  </div>
                </div>
                <p class="event-name" :title="step.catalogName">{{ step.catalogName }}</p>
              </div>
              <div v-if="idx < steps.length - 1" class="event-arrow">
                <i class="icon-arrow-right"></i>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 3. 详细描述面板 -->
      <div class="detail-panels">
        <div class="panel">
          <h4 class="panel-title">关联业务逻辑</h4>
          <div class="panel-content">
            <div v-if="businessLogic.length === 0" class="panel-empty">暂无描述</div>
            <div v-for="(logic, idx) in businessLogic" :key="idx" class="logic-item">
              <div class="logic-number">{{ idx + 1 }}</div>
              <p class="logic-text">{{ logic }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex gap-3" style="justify-content: end">
        <el-button type="primary" @click="onAdd">
          <Icon :icon="'el-icon-Plus'" class="mr-2" />
          创建主题目录
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { useRegisterModal, useDetailDialog, useProcessDrawer } from "@/composables";

const { openRegisterModal, open } = useRegisterModal();
const { openDetailDialog } = useDetailDialog();
const { processDrawerData } = useProcessDrawer();

const emit = defineEmits(["close"]);

const visible = ref(true);
const topologyLoading = ref(false);

// 从全局数据中取 props（组件渲染在 BaseLayout，通过 provide/inject 传递数据）
const processName = computed(() => processDrawerData.value?.name || "");
const processEn = computed(() => processDrawerData.value?.enName || "");
const processDescription = computed(() => processDrawerData.value?.description || "");

/** API 返回的 catalog 条目 */
interface CatalogItem {
  id: string;
  catalogName: string;
  catalogNameEn?: string;
  tableType?: string;
  [key: string]: any;
}

/** 顶层"普通对象"卡片（tableType = "wdb"） */
const topObjects = ref<CatalogItem[]>([]);

/** 底层"业务事件流"卡片（tableType = "business"） */
const steps = ref<CatalogItem[]>([]);

/** 动态计算顶层对象 gap 与连接线宽度，避免卡片溢出被遮挡 */
const topObjectsStyle = computed(() => {
  const count = topObjects.value.length;
  if (count <= 1) return {};
  const availableWidth = 836;
  const cardWidth = 176;
  const rawGap = Math.floor((availableWidth - cardWidth * count) / (count - 1));
  // 卡片 ≤ 4 时单行排列，gap 上限 192、下限 24
  if (rawGap >= 24) {
    const gap = Math.min(192, rawGap);
    return { gap: `${gap}px` } as const;
  }
  // 卡片 ≥ 5 时换行，使用固定间距
  return {
    gap: "24px",
    flexWrap: "wrap",
    justifyContent: "center",
  } as const;
});

/** 动态计算垂直连接线中水平线的宽度，与顶层卡片组对齐 */
const horizontalLineStyle = computed(() => {
  const count = topObjects.value.length;
  if (count === 0) return { display: "none" };
  const cardWidth = 176;
  const availableWidth = 836;
  const rawGap = count > 1 ? Math.floor((availableWidth - cardWidth * count) / (count - 1)) : 0;
  if (rawGap < 24) {
    // 换行模式：水平线撑满可用宽度
    return {
      width: `${availableWidth}px`,
      left: `${-(availableWidth / 2)}px`,
    };
  }
  const gap = Math.min(192, rawGap);
  const totalWidth = cardWidth * count + gap * (count - 1);
  return {
    width: `${totalWidth}px`,
    left: `${-(totalWidth / 2)}px`,
  };
});

const EVENT_CARD_W = 128;
const EVENT_ARROW_W = 14;
const EVENTS_GAP = 16;
const EVENTS_PADDING = 32; // event-flow 左右 padding 总和

/** 动态计算虚线分支 SVG viewBox 宽度，始终与计算坐标系一致 */
const dashedSvgW = computed(() => {
  // SVG 始终撑满容器宽度；viewBox 宽度固定为 836，
  // 与 dashedPathD 计算时使用的 availableW 保持一致，避免坐标缩放导致偏移
  return 836;
});

const dashedPathD = computed(() => {
  const count = steps.value.length;
  if (count === 0) return "";
  const availableW = 836;
  const contentW = availableW - EVENTS_PADDING;
  const itemW = EVENT_CARD_W + EVENT_ARROW_W;
  const totalRowW = count * itemW - EVENT_ARROW_W + (count - 1) * EVENTS_GAP;
  const isWrap = totalRowW > contentW;

  // 换行模式：简化为全宽竖线
  if (isWrap) {
    const cx = availableW / 2;
    const left = EVENTS_PADDING / 2; // 扣除 padding
    const right = availableW - EVENTS_PADDING / 2;
    return `M ${cx} 0 L ${cx} 32 L ${left} 32 L ${left} 64 M ${cx} 32 L ${right} 32 L ${right} 64`;
  }

  // 单行模式：精确对齐每张卡片中心
  const cx = availableW / 2;
  const leftOffset = (availableW - totalRowW) / 2;
  const stepW = itemW + EVENTS_GAP; // 每步占宽（卡片+箭头+间距）
  const segments: string[] = [];
  for (let i = 0; i < count; i++) {
    // 第 i 张卡片中心的 x 坐标
    const x = leftOffset + stepW * i + EVENT_CARD_W / 2;
    segments.push(`M ${cx} 0 L ${cx} 32 Q ${cx} 32 ${x} 32 L ${x} 64`);
  }
  return segments.join(" ");
});

/** 底层事件流动态样式：卡片数 ≤ 5 单行，≥ 6 换行 */
const eventFlowStyle = computed(() => {
  const count = steps.value.length;
  const contentW = 836 - EVENTS_PADDING;
  const itemW = EVENT_CARD_W + EVENT_ARROW_W;
  const totalW = count * itemW - EVENT_ARROW_W + (count - 1) * EVENTS_GAP;
  if (totalW <= contentW) return {};
  return {
    flexWrap: "wrap",
    justifyContent: "center",
  } as const;
});

/** 业务逻辑文案：来自 processDescription */
const businessLogic = ref<string[]>([]);

/** 核心资产数据 */
const coreAssets = ref<string[]>([]);

const onClose = () => {
  visible.value = false;
  emit("close");
};

const onAdd = () => {
  openRegisterModal({
    registerClass: "catalog",
    pdateFormRule: [
      {
        field: "tableType",
        value: "business",
        props: {
          picks: ["业务表", "维度表"],
        },
      },
      {
        field: "dataSourceType",
        value: processEn.value,
        props: {
          picks: ["dwm"],
        },
      },
    ],
  });
};

/** 加载拓扑数据 */
const fetchTopologyData = async () => {
  if (!processEn.value) return;
  topologyLoading.value = true;
  try {
    const raw: CatalogItem[] = await $common.post("/dst/catalog/list", {
      assetType: "catalog",
      dataSourceType: processEn.value,
    });

    const wdbList: CatalogItem[] = [];
    const bizList: CatalogItem[] = [];

    for (const item of raw) {
      if (item.tableType === "wdb") {
        wdbList.push(item);
      } else if (item.tableType === "business") {
        bizList.push(item);
      }
    }

    topObjects.value = wdbList;
    steps.value = bizList;
    coreAssets.value = raw
      .filter((item) => !!item.catalogName)
      .map((item) => item.catalogNameEn || item.catalogName);
  } catch {
    ElMessage.error("获取拓扑数据失败，请稍后重试");
  } finally {
    topologyLoading.value = false;
  }
};

const handleClick = (item: CatalogItem) => {
  openDetailDialog(
    {
      id: item.id,
      title: item.catalogName,
      type: "catalog",
      dataSourceType: "dwm",
    },
    () => {
      // 该弹窗关闭后恢复抽屉并刷新
      nextTick(() => {
        visible.value = true;
        refresh();
      });
    }
  );
};

/** 解析 processDescription 为业务逻辑列表 */
const parseBusinessLogic = () => {
  const desc = processDescription.value;
  if (!desc) {
    businessLogic.value = [];
    return;
  }
  // 仅按换行符拆分为多条
  const parts = desc
    .split(/[\n\r]+/)
    .map((s: string) => s.trim())
    .filter(Boolean);
  businessLogic.value = parts.length > 0 ? parts : [desc];
};

// 刷新数据
const refresh = () => {
  fetchTopologyData();
  parseBusinessLogic();
};

onMounted(() => {
  fetchTopologyData();
  parseBusinessLogic();
});

// 监听关闭登记弹框
watch(open, (newVal, oldVal) => {
  if (newVal === false && oldVal) {
    refresh();
  }
});
</script>

<style lang="scss" scoped>
/* 抽屉容器 */
.process-detail-drawer {
  .el-drawer__header {
    display: none;
  }

  .el-drawer__body {
    padding: 0;
    height: 100%;
    display: flex;
    flex-direction: column;
  }
}

/* 抽屉头部 */
.drawer-header {
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background-color: #f8fafc;

  .header-content {
    display: flex;
    align-items: center;
    gap: 12px;

    .header-icon {
      width: 32px;
      height: 32px;
      background-color: #3b48cc;
      color: white;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .header-info {
      .header-title {
        font-size: 16px;
        font-weight: bold;
        color: #1f2937;
        margin: 0;
      }

      .header-subtitle {
        font-size: 11px;
        color: #9ca3af;
        font-weight: 500;
        margin: 0;
      }
    }
  }

  .header-close {
    padding: 8px;
    border-radius: 50%;
    color: #9ca3af;
    transition: all 0.3s ease;

    &:hover {
      background-color: #f3f4f6;
    }
  }
}

/* 抽屉内容区 */
.drawer-content {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;

  /* 图例栏 */
  .legend-bar {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 16px;
    background-color: #f9fafb;
    border: 1px solid #f3f4f6;
    border-radius: 8px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .legend-item-icon {
        width: 24px;
        height: 24px;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;

        &.network {
          background: linear-gradient(180deg, #42c7fb 0%, #2cb4f7 100%);
        }

        &.check-square {
          background: linear-gradient(223.35deg, #bb90ff 0%, #6e66d1 100%);
        }

        &.layers {
          background: linear-gradient(220.43deg, #80de8f 0%, #40bf76 100%);
        }
      }

      .legend-item-text {
        font-size: 12px;
        font-weight: bold;
        color: #4b5563;
      }
    }

    .legend-divider {
      width: 1px;
      height: 16px;
      background-color: #e5e7eb;
      margin: 0 8px;
    }

    .legend-relations {
      display: flex;
      align-items: center;
      gap: 16px;

      .legend-relation-item {
        font-size: 12px;
        color: #9ca3af;
        font-weight: 500;
        display: flex;
        align-items: center;
        gap: 4px;

        &.dashed {
          border-bottom: 1px dashed #e5e7eb;
        }
      }
    }
  }

  /* 关系拓扑图画布 */
  .topology-canvas {
    min-height: 500px;
    border: 1px solid #f3f4f6;
    border-radius: 12px;
    background-color: white;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 64px 0;
    position: relative;

    /* 加载/空状态占位 */
    .topology-placeholder {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      color: #9ca3af;
      font-size: 14px;
      min-height: 400px;

      .topology-spinner {
        width: 28px;
        height: 28px;
        border: 3px solid #e5e7eb;
        border-top-color: #2d81e5;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
      }
    }

    &.loading {
      justify-content: center;
    }

    /* 顶层对象 */
    .top-objects {
      display: flex;
      /* gap 由 JS 动态计算（单行自适应 / 多行换行） */
      position: relative;
      z-index: 10;

      .object-item {
        position: relative;

        .object-card {
          width: 176px;
          padding: 12px;
          background-color: white;
          border: 1px solid #e5e7eb;
          border-radius: 8px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
          display: flex;
          align-items: center;
          gap: 12px;
          cursor: pointer;

          &:hover {
            border-color: #34d2e7;
          }

          .object-icon {
            width: 32px;
            height: 32px;
            background: linear-gradient(218.19deg, #34d2e7 0%, #25b2dd 100%);
            color: white;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
          }

          .object-info {
            min-width: 0;

            .object-name {
              font-size: 13px;
              font-weight: bold;
              color: #1f2937;
              line-height: 1;
              margin: 0;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
          }

          .object-dots {
            margin-left: auto;
            display: flex;
            gap: 2px;

            .dot {
              width: 4px;
              height: 4px;
              background-color: #e5e7eb;
              border-radius: 50%;
            }
          }
        }
      }
    }

    /* 顶层到中层的垂直实线 */
    .vertical-line {
      width: 1.5px;
      height: 48px;
      background-color: rgba(59, 72, 204, 0.2);
      position: relative;

      .horizontal-line {
        position: absolute;
        top: 0;
        /* left + width 通过 JS 动态计算，确保与顶层卡片组对齐 */
        height: 1.5px;
        background-color: rgba(59, 72, 204, 0.2);
      }

      .line-corner {
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 12px;
        height: 12px;
        border-bottom: 1.5px solid rgba(59, 72, 204, 0.2);
        border-right: 1.5px solid rgba(59, 72, 204, 0.2);
        transform: translateX(-50%) rotate(45deg);
      }
    }

    /* 核心业务事项 */
    .core-process {
      position: relative;
      z-index: 20;

      .process-card {
        width: 320px;
        padding: 20px;
        background: linear-gradient(223.35deg, #bb90ff 0%, #6e66d1 70%);
        color: white;
        border-radius: 12px;
        box-shadow:
          0 10px 15px -3px rgba(0, 0, 0, 0.1),
          0 4px 6px -2px rgba(0, 0, 0, 0.05);
        position: relative;
        overflow: hidden;
        transition: transform 0.3s ease;

        &:hover {
          transform: scale(1.05);
        }

        .process-content {
          display: flex;
          align-items: center;
          gap: 16px;

          .process-icon {
            width: 40px;
            height: 40px;
            background-color: rgba(255, 255, 255, 0.2);
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
          }

          .process-info {
            .process-name {
              font-size: 15px;
              font-weight: bold;
              letter-spacing: 0.05em;
              margin: 0;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .process-code {
              font-size: 11px;
              opacity: 0.6;
              font-family: monospace;
              text-transform: uppercase;
              margin: 4px 0 0;
            }
          }
        }
      }
    }

    /* 中层到底层的虚线分支 */
    .dashed-branches {
      width: 100%;
      height: 64px;
      position: relative;

      .branches-svg {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
      }
    }

    /* 底层：业务事件流 */
    .event-flow {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 0 16px;
      width: 100%;
      justify-content: center;

      .event-item {
        display: flex;
        align-items: center;

        .event-card {
          width: 128px;
          padding: 12px;
          background-color: white;
          border: 1px solid #f3f4f6;
          border-radius: 8px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
          transition: all 0.3s ease;
          cursor: pointer;

          &:hover {
            border-color: #40bf76;
          }

          .event-header {
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 8px;
            position: relative;

            .event-icon {
              width: 24px;
              height: 24px;
              color: white;
              background: linear-gradient(220.43deg, #80de8f 0%, #40bf76 100%);
              border-radius: 6px;
              display: flex;
              align-items: center;
              justify-content: center;
            }

            .event-dots {
              position: absolute;
              top: 12px;
              right: 5px;
              display: flex;
              gap: 2px;

              .dot {
                width: 2px;
                height: 2px;
                background-color: #d1d5db;
                border-radius: 50%;
              }
            }
          }

          .event-name {
            font-size: 11px;
            font-weight: bold;
            color: #4b5563;
            text-align: center;
            line-height: 1.2;
            margin: 0;
            transition: color 0.3s ease;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;

            .event-card:hover & {
              color: #722ed1;
            }
          }
        }

        .event-arrow {
          color: #e5e7eb;
        }
      }
    }
  }

  /* 详细描述面板 */
  .detail-panels {
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    gap: 24px;

    .panel {
      background-color: white;
      padding: 20px;
      border: 1px solid #f3f4f6;
      border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      display: flex;
      flex-direction: column;
      gap: 16px;

      .panel-title {
        font-size: 14px;
        font-weight: bold;
        color: #1f2937;
        margin: 0;
        display: flex;
        align-items: center;
        gap: 8px;

        i {
          color: #3b48cc;
        }
      }

      .panel-content {
        display: flex;
        flex-direction: column;
        gap: 12px;
        max-height: 320px;
        overflow-y: auto;

        .panel-empty {
          color: #9ca3af;
          font-size: 13px;
          text-align: center;
          padding: 16px 0;
        }

        /* 业务逻辑项 */
        .logic-item {
          display: flex;
          align-items: flex-start;
          gap: 12px;

          .logic-number {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background-color: #eff6ff;
            color: #3b82f6;
            font-size: 10px;
            font-weight: 900;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            margin-top: 2px;
          }

          .logic-text {
            font-size: 12px;
            color: #6b7280;
            line-height: 1.5;
            margin: 0;
          }
        }
      }
    }
  }
}

/* 抽屉底部操作 */
.drawer-footer {
  height: 64px;
  padding: 0 32px;
  border-top: 1px solid #f3f4f6;
  background-color: white;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;

  .footer-button {
    padding: 0 24px;
    height: 36px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: bold;
    cursor: pointer;
    transition: all 0.3s ease;

    &.secondary {
      border: 1px solid #e5e7eb;
      background-color: white;
      color: #6b7280;

      &:hover {
        background-color: #f9fafb;
      }
    }

    &.primary {
      background-color: #003399;
      color: white;
      box-shadow:
        0 4px 6px -1px rgba(0, 0, 0, 0.1),
        0 2px 4px -1px rgba(0, 0, 0, 0.06);

      &:hover {
        background-color: #1e40af;
      }
    }
  }
}

/* 图标样式 */
.icon-check-square::before {
  content: "✓";
  font-weight: bold;
  font-size: 16px;
}

.icon-x::before {
  content: "×";
  font-weight: bold;
  font-size: 20px;
}

.icon-arrow-right::before {
  content: "→";
  font-size: 14px;
}

.icon-arrow-up-right::before {
  content: "↗";
  font-size: 14px;
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes slideInRight {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
</style>
