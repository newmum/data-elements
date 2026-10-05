<template>
  <!-- 顶部导航栏 -->
  <BaseLayout>
    <div class="layout__header">
      <div class="layout__header-content">
        <!-- Logo区域 -->
        <div v-if="isShowLogo" class="layout__header-logo">
          <AppLogo type="blue" :collapse="false" theme="light" />
        </div>

        <!-- 顶部菜单区域 -->
        <div class="layout__header-menu">
          <WorkTopMenu />
        </div>

        <!-- 右侧操作区域 -->
        <div class="layout__header-actions">
          <NavbarActions theme="light" />
        </div>
      </div>
    </div>

    <div
      v-if="showLeft"
      class="layout__container workplace__container"
      :class="{ 'workplace-detail__container': isWorkDetail }"
    >
      <div class="workplace__content">
        <!-- 左侧菜单栏 -->
        <div v-if="!isWorkDetail" class="layout__sidebar--left workplace__left">
          <div class="workplace-info">
            <div class="info-avatar" @click="openInfo">
              <img src="../../assets/touxiang.png" />
              <div class="mask">编辑</div>
            </div>
            <div class="info-content">
              <div class="info-content-name">{{ userInfo.realName }}</div>
              <div class="info-content-info">xx部门</div>
            </div>
          </div>
          <div class="workplace-menu">
            <template v-for="(item, index) in sideMenuRoutes">
              <div
                v-if="!item.meta?.hidden"
                :key="index"
                class="menu-item"
                :class="{ active: activePaths.includes(item.path) }"
                @click="toPath(item.path)"
              >
                <div class="menu-item-left">
                  <icon :icon="item.meta?.icon" />
                  {{ item.meta?.title }}
                </div>
                <!--                <div class="menu-item-right">{{ getMenuCount(item.name as string) }}</div>-->
              </div>
            </template>
          </div>
        </div>
        <!-- 主内容区 -->
        <div class="layout__main workplace__main">
          <AppMain height="calc(100vh - var(--navbar-height) - 24px)" />
        </div>
      </div>
    </div>

    <div v-else class="layout__container">
      <div class="layout__main">
        <AppMain height="100vh" />
      </div>
    </div>
  </BaseLayout>
</template>
<script setup lang="ts">
import { useRouter } from "vue-router";
import { useUserStore } from "@/store";
import { useLayout } from "@/layouts/composables/useLayout";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";
import BaseLayout from "@/layouts/views/BaseLayout.vue";
import NavbarActions from "@/layouts/components/NavBar/components/NavbarActions.vue";
import AppLogo from "@/layouts/components/AppLogo/index.vue";

const route = useRoute();
const router = useRouter();

// 布局相关参数
const { isShowLogo } = useLayout();

const { sideMenuRoutes, activePaths } = useLayoutMenu();

const { userInfo } = useUserStore();

const showLeft = computed(() => sideMenuRoutes.value?.some((el) => !el.meta?.hidden));

// 是否个人中心详情页
const isWorkDetail = ref(false);
const isWorkDetailValue = computed(
  () => route.path.startsWith("/workplace") && route.path.endsWith("/detail")
);
// 延迟更新，防止切换详情页面过渡时错位
watch(
  isWorkDetailValue,
  (newVal) => {
    setTimeout(() => {
      isWorkDetail.value = newVal;
    }, 50);
  },
  { immediate: true }
);

// 响应式窗口尺寸
const openInfo = () => {
  router.push({ path: "myInfo" });
};

const toPath = (path: string) => {
  router.push({ path });
};
// const menuCounts = ref();
const initMenuCount = async () => {};
// const getMenuCount = (name: string) => {
//   return menuCounts.value ? menuCounts.value[name] || 0 : 0;
// };

const initData = async () => {};

onMounted(() => {
  initMenuCount();
  initData();
});
</script>

<!-- 顶部菜单样式 -->
<style lang="scss" scoped>
.layout {
  //overflow: hidden;

  &__header {
    position: fixed;
    top: 0;
    z-index: 999;
    width: 100%;
    height: $navbar-height;
    background-color: transparent;
    backdrop-filter: blur(5px);

    &:before {
      position: absolute;
      top: 0;
      left: -20px;
      z-index: -1;
      width: 250px;
      content: "";
    }

    &-content {
      display: flex;
      align-items: center;
      height: 100%;
      padding: 0;
    }

    &-menu {
      display: flex;
      flex: 1;
      align-items: center;
      min-width: 0;
      height: 100%;
      overflow: hidden;
    }

    &-actions {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      height: 100%;
      padding: 0 1px 0 24px;
    }
  }

  &__container {
    display: flex;
    flex-direction: column;
    // 不能设置，否则会出现两个滚动条
    //overflow-y: auto;

    .app-main {
      flex: 1;
      background-color: transparent;
    }
    .layout__main {
      flex: 1;
    }
  }
}
</style>

<style lang="scss" scoped>
.workplace {
  &__container {
    display: flex;
    flex-direction: column;
    min-height: 100%;
    padding-top: $navbar-height;
    overflow-y: auto;
    background-color: #fafafa;
    background-image: url("@/assets/images/workplace/bg.png");
    background-repeat: no-repeat;
    background-size: 100% auto;
  }

  /* 左右内容容器 */
  &__content {
    display: flex;
    flex: 1;
    gap: 1rem;
    padding: 24px 24px 0;

    .workplace__main {
      display: flex;
      flex: 1;
      flex-direction: column;
      width: 100%;
      border-radius: 8px;
    }
  }

  &__left {
    flex-shrink: 0;
    width: 350px;
    height: fit-content;
    padding-bottom: 20px;
    background-color: #fff;
    border-radius: 8px;

    .workplace-info {
      position: relative;
      display: flex;
      align-items: center;
      padding: 20px 16px;
      background: url(@/assets/images/workplace/info-bg.png) center no-repeat;
      background-size: 100% 120px;
      border-radius: 8px;

      .info-avatar {
        position: relative;
        width: 60px;
        height: 60px;
        margin-top: 10px;
        overflow: hidden;
        border-radius: 50%;
        transition: all 0.3s ease;

        &:hover {
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        }

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .mask {
          position: absolute;
          top: 0;
          left: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          width: 100%;
          height: 100%;
          font-size: 14px;
          color: #fff;
          cursor: pointer;
          background: rgba(0, 0, 0, 0.5);
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        &:hover .mask {
          opacity: 1;
        }
      }

      .info-content {
        margin-top: 10px;
        margin-left: 16px;
        color: #fff;
        text-align: left;

        .info-content-name {
          font-size: 18px;
          font-weight: 700;
        }

        .info-content-info {
          font-size: 14px;
        }

        .info-content-extend {
          display: flex;
          gap: 4px;
          align-items: center;
          font-size: 14px;
          font-weight: 400;
          color: var(--el-color-primary);
          cursor: pointer;
          svg {
            font-size: 16px;
          }
        }
      }
    }

    .workplace-menu {
      width: 100%;
      padding: 0px 16px;
      .menu-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px;
        margin: 8px 0;
        font-size: 16px;
        font-weight: 400;
        color: #323643;
        cursor: pointer;
        border-radius: 8px;
        &:hover {
          background: var(--el-bg-color-page);
        }
        &.active {
          color: var(--el-color-primary);
          background: rgba(27, 119, 255, 0.1);
        }
        .menu-item-left {
          display: flex;
          gap: 5px;
          align-items: center;
          :deep(.svg-icon) {
            font-size: 24px;
          }
        }
        .menu-item-right {
          width: 28px;
          height: 19px;
          font-size: 12px;
          color: #686c80;
          text-align: center;
          background: #edf0f4;
          border-radius: 9.5px;
        }
      }
    }
  }
}

.workplace-detail__container {
  .workplace__content {
    padding-top: 0;
  }
}
</style>
