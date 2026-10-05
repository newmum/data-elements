<template>
  <div :class="['navbar-actions', navbarActionsClass]">
    <!-- 桌面端工具项 -->
    <!--    <template>-->
    <!-- 搜索 -->
    <div class="navbar-actions__item">
      <MenuSearch />
    </div>

    <!-- 超市购物车 -->
    <div
      v-if="settingStore.showShoppingCar || route.path.startsWith('/market')"
      class="navbar-actions__item mr-4"
    >
      <shopping-car />
    </div>

    <!-- 全屏 -->
    <!-- <div class="navbar-actions__item">
        <Fullscreen />
      </div> -->

    <!-- 通知 -->
    <!-- <div class="navbar-actions__item">
        <Notification />
      </div> -->
    <!--    </template>-->

    <!-- 用户菜单 -->
    <div class="navbar-actions__item">
      <el-dropdown trigger="hover" popper-class="-mt-2 w-137px" :show-arrow="false">
        <div class="user-profile">
          <img class="user-profile__avatar mr-4" src="@/assets/touxiang.png" />
          <div class="flex flex-col items-start gap-1 mr-2">
            <span class="user-profile__name">
              {{ userStore.userInfo.realName }}
            </span>
            <span class="user-profile__org text-12px" style="opacity: 0.8">
              {{ userStore.userInfo.orgName }}
            </span>
          </div>
          <icon icon="el-icon-ArrowDown" size="14px" class="ml-1" />
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleProfileClick">
              <div class="flex-center gap-1">
                <Icon icon="el-icon-user" size="14" />
                <span>个人中心</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleSettingClick">
              <div class="flex-center gap-1">
                <Icon icon="setting" size="14" />
                <span>系统管理</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided @click="logout">
              <div class="flex-center gap-1">
                <Icon icon="logout" class="mr-2" size="14" />
                <span>退出登录</span>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <PersonalCenterDialog v-model="profileVisible" />
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { useSettingStore, useUserStore } from "@/store";
import PersonalCenterDialog from "./PersonalCenterDialog.vue";

// 导入子组件
// import MenuSearch from "@/components/base/MenuSearch/index.vue";
// import Fullscreen from "@/components/base/Fullscreen/index.vue";
// import Notification from "@/components/template/Notification/index.vue";

const props = defineProps<{
  theme?: "light" | "dark";
}>();

const userStore = useUserStore();
const settingStore = useSettingStore();

const route = useRoute();
const router = useRouter();
const profileVisible = ref(false);
/**
 * 打开当前用户资料弹窗
 */
function handleProfileClick() {
  profileVisible.value = true;
}

/**
 * 打开系统管理页面
 */
function handleSettingClick() {
  router.push({ path: "/setting" });
}

// 根据主题和侧边栏配色方案选择样式类
const navbarActionsClass = computed(() => {
  // 暗黑主题下，所有布局都使用白色文字
  if (props.theme === "dark") {
    return "navbar-actions--white-text";
  }

  // 明亮主题下
  if (props.theme === "light") {
    return "navbar-actions--dark-text";
  }

  return "navbar-actions--white-text";
});

/**
 * 退出登录
 */
function logout() {
  ElMessageBox.confirm("确定注销并退出系统吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    lockScroll: false,
  }).then(() => {
    userStore.logout().then((url: string) => {
      if (url === "/login") {
        router.push(`/login?redirect=${encodeURIComponent(route.fullPath)}`);
      } else {
        window.location.href = url;
      }
    });
  });
}
</script>

<style scoped lang="scss">
.navbar-actions {
  display: flex;
  align-items: center;
  height: 100%;

  &__item {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 44px; /* 增加最小点击区域到44px，符合人机交互标准 */
    height: 100%;
    min-height: 44px;
    padding: 0 8px;
    text-align: center;
    cursor: pointer;
    transition: all 0.3s;

    // 确保子元素居中
    > * {
      display: flex;
      align-items: center;
      justify-content: center;
      color: #000000e0;
    }

    // 确保 Element Plus 组件可以正常工作
    :deep(.el-dropdown),
    :deep(.el-tooltip) {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }

    // 图标样式
    :deep([class^="i-svg:"]) {
      font-size: 18px;
      line-height: 1;
      color: var(--el-text-color-regular);
      transition: color 0.3s;
    }

    &:hover {
      :deep([class^="i-svg:"]) {
        color: var(--el-color-primary);
      }
    }
  }

  .user-profile {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    padding: 0 8px;

    &__avatar {
      flex-shrink: 0;
      width: 28px;
      height: 28px;
      border-radius: 50%;
    }

    &__name {
      color: var(--el-text-color-regular);
      white-space: nowrap;
      transition: color 0.3s;
    }
  }
}

// 白色文字样式（用于深色背景：暗黑主题、顶部布局、混合布局）
.navbar-actions--white-text {
  .navbar-actions__item {
    :deep([class^="i-svg:"]),
    :deep(.el-icon) {
      color: rgba(255, 255, 255, 0.85);
    }

    &:hover {
      :deep([class^="i-svg:"]) {
        color: #fff;
      }
    }
  }
  .user-profile {
    color: rgba(255, 255, 255, 1);
  }

  .user-profile__name {
    font-weight: bold;
    color: rgba(255, 255, 255, 1);
  }
}

// 深色文字样式（用于浅色背景：明亮主题下的左侧布局）
.navbar-actions--dark-text {
  .navbar-actions__item {
    :deep([class^="i-svg:"]) {
      color: var(--el-text-color-regular) !important;
    }

    &:hover {
      :deep([class^="i-svg:"]) {
        color: var(--el-color-primary) !important;
      }
    }
  }
  .user-profile {
    color: var(--el-text-color-regular) !important;
  }
  .user-profile__name {
    color: var(--el-text-color-regular) !important;
  }
}

// 确保下拉菜单中的图标不受影响
:deep(.el-dropdown-menu) {
  [class^="i-svg:"] {
    color: var(--el-text-color-regular) !important;

    &:hover {
      color: var(--el-color-primary) !important;
    }
  }
}
</style>
