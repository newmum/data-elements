<template>
  <div class="quality-empty">
    <div class="quality-content">
      <!-- 左侧：微型仪表盘 -->
      <div class="dashboard-container">
        <div class="dashboard-card">
          <div class="dashboard-circle">
            <svg width="128" height="128" viewBox="0 0 128 128">
              <circle
                cx="64"
                cy="64"
                r="56"
                stroke="#F1F5F9"
                stroke-width="10"
                fill="transparent"
              />
              <circle
                cx="64"
                cy="64"
                r="56"
                stroke="#1D61FF"
                stroke-width="10"
                stroke-dasharray="280"
                stroke-dashoffset="280"
                fill="transparent"
                class="circle-progress"
              />
            </svg>
            <div class="dashboard-content">
              <span class="dashboard-score">--</span>
              <span class="dashboard-label">Score</span>
            </div>
          </div>
        </div>

        <!-- 右侧：紧凑规则矩阵 -->
        <div class="rules-container">
          <div class="rules-header">
            <Icon icon="shandian" class="rules-icon"></Icon>
            <span>数据质检</span>
          </div>
          <div class="rules-grid">
            <div v-for="rule in miniRules" :key="rule.label" class="rule-card">
              <div class="rule-icon" :style="{ backgroundColor: `${rule.color || '#0190f9'}20` }">
                <Icon :icon="rule.icon" :color="rule.color" size="16"></Icon>
              </div>
              <div class="rule-content">
                <p class="rule-label">{{ rule.label }}</p>
                <div class="rule-progress">
                  <div class="rule-progress-bar"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 文案引导 -->
      <div class="guidance-section">
        <div class="guidance-title">
          暂无质检结果
        </div>

        <div class="guidance-button">
          <el-button type="primary" @click="handlerQuality">
            去质检
            <div class="ml-5">
              <Icon :icon="'arrow-right'"></Icon>
            </div>
          </el-button>
        </div>
      </div>
    </div>
    <register-modal
      v-if="registerModalVisible"
      v-model="registerModalVisible"
      :register-class="'quality'"
      :quality-data="qualityData"
      @close="closeRegisterModal"
    ></register-modal>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useDictStore } from "@/store";

const registerModalVisible = ref(false);
const qualityDimension = useDictStore().getDictItems("qualityDimension") || [];

// 微型规则数据
const miniRules = qualityDimension?.slice(0, 4) || [];

// 质检数据
const qualityData = ref({
  name: "qualitycheck",
  title: "创建成功",
  code: "TSK_QUALITY_001",
  time: "2025-08-01 16:11:00",
  type: "quality",
});

const closeRegisterModal = () => {
  registerModalVisible.value = false;
};

const handlerQuality = () => {
  registerModalVisible.value = true;
};
</script>

<style scoped lang="scss">
.quality-empty {
  width: 100%;
  height: 100%;
  background-color: #fff;
  .quality-content {
    width: 100%;
    max-width: 580px;
    margin: 0 auto;
    padding: 100px 0;
    /* 微型仪表盘和规则矩阵 */
    .dashboard-container {
      display: flex;
      align-items: center;
      gap: 48px;
      margin-bottom: 40px;
      width: 100%;

      .dashboard-card {
        width: 160px;
        height: 160px;
        background-color: #ffffff;
        border-radius: 24px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
        border: 1px solid #e0e7ff;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        position: relative;
        flex-shrink: 0;
        transition: all 0.3s ease;

        &:hover {
          transform: translateY(-5px);
          box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
        }

        .dashboard-circle {
          position: relative;
          display: flex;
          align-items: center;
          justify-content: center;

          svg {
            width: 128px;
            height: 128px;
            transform: rotate(-90deg);
          }

          .circle-progress {
            animation: dash 2s ease-out forwards;
          }

          .dashboard-content {
            position: absolute;
            inset: 0;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;

            .dashboard-score {
              font-size: 36px;
              font-weight: 900;
              color: #1f2937;
              line-height: 1;
            }

            .dashboard-label {
              font-size: 10px;
              color: #9ca3af;
              font-weight: 900;
              text-transform: uppercase;
              letter-spacing: 0.2em;
              margin-top: 4px;
            }
          }
        }
      }

      .rules-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 16px;

        .rules-header {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 8px;

          .rules-icon {
            color: #f97316;
          }

          span {
            font-size: 13px;
            font-weight: 900;
            color: #374151;
          }
        }

        .rules-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 12px;

          .rule-card {
            padding: 12px;
            background-color: rgba(255, 255, 255, 0.8);
            border-radius: 12px;
            border: 1px solid #f3f4f6;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
            display: flex;
            align-items: center;
            gap: 12px;
            transition: all 0.2s ease;

            &:hover {
              transform: translateY(-2px);
              box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
            }

            .rule-icon {
              padding: 8px;
              border-radius: 8px;
              display: flex;
              align-items: center;
              justify-content: center;
            }

            .rule-content {
              flex: 1;
              display: flex;
              flex-direction: column;
              gap: 4px;

              .rule-label {
                font-size: 11px;
                font-weight: 700;
                color: #4b5563;
                margin: 0;
              }

              .rule-progress {
                height: 4px;
                background-color: #f9fafb;
                border-radius: 9999px;
                overflow: hidden;
                width: 100%;

                .rule-progress-bar {
                  height: 100%;
                  background-color: #e5e7eb;
                  border-radius: 9999px;
                  width: 33.33%;
                }
              }
            }
          }
        }
      }
    }

    /* 文案引导 */
    .guidance-section {
      text-align: center;
      margin-top: 40px;
      width: 100%;

      .guidance-title {
        margin-bottom: 24px;
        color: #323643;
      }

      .guidance-button {
        // padding-top: 24px;
        .start-quality-btn {
          padding: 0 40px;
          height: 56px;
          background-color: #1d61ff;
          color: #ffffff;
          border-radius: 18px;
          font-size: 15px;
          font-weight: 900;
          box-shadow: 0 20px 40px -10px rgba(29, 97, 255, 0.4);
          transition: all 0.3s ease;
          display: flex;
          align-items: center;
          gap: 16px;
          margin: 0 auto;

          &:hover {
            background-color: #1e40af;
            transform: translateY(-4px);
            box-shadow: 0 24px 48px -12px rgba(29, 97, 255, 0.5);
          }

          &:active {
            transform: translateY(0) scale(0.95);
          }

          .button-icon {
            background-color: rgba(255, 255, 255, 0.2);
            padding: 6px;
            border-radius: 9999px;
            transition: transform 0.3s ease;

            .el-icon {
              font-size: 18px;
            }
          }

          &:hover .button-icon {
            transform: translateX(4px);
          }
        }
      }
    }

    /* 动画 */
    @keyframes dash {
      from {
        stroke-dashoffset: 351;
      }
      to {
        stroke-dashoffset: 0;
      }
    }
  }
}
</style>
