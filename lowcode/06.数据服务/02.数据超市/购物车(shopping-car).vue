<template>
  <div class="cart-animation-demo">
    <el-badge
      ref="cartTarget"
      :value="cartCount"
      :offset="[2, 0]"
      style="line-height: 1"
      @click="open"
    >
      <Icon icon="el-icon-ShoppingCart" size="20" />
    </el-badge>

    <!-- 动画容器（承载小圆球，纯 CSS 样式，保证小球可见） -->
    <div ref="animateContainer" class="animate-container"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, unref, onMounted } from "vue";
import { useElementBounding } from "@vueuse/core";
import { throttle } from "lodash-es";
import { useRegisterModal, useCartCount } from "@/composables";

// 申请单登记弹框
const { openRegisterModal } = useRegisterModal();

const open = () => {
  openRegisterModal({
    registerClass: "car-apply",
    registerData: {},
  });
};

function useCartAnimation(options: Record<string, any> = {}) {
  // 默认配置
  const defaultOptions = {
    duration: 500,
    throttleTime: 500,
  };
  const finalOptions = { ...defaultOptions, ...options };

  // 动画容器 Ref（需在视图中绑定）
  const animateContainer = ref(null);
  // 购物车数量（使用外部传入的共享 ref，若未传则降级为局部 ref）
  const cartCount = options.cartCount || ref(0);

  // 校验配置项
  if (!options.cartTarget) {
    console.warn("useCartAnimation: 必须传入 cartTarget（购物车元素 Ref）");
  }

  /**
   * 生成动画小圆球并执行滑动动画
   * @param {MouseEvent} event 点击事件对象（用于获取点击位置）
   * @param {string} [color='#ff4d4f'] 小圆球颜色（可选）
   */
  const triggerCartAnimation = (event, color = "#ff4d4f") => {
    const cartTarget = unref(options.cartTarget);
    const container = unref(animateContainer);

    // 校验必要元素
    if (!event || !cartTarget || !container) return;

    // 1. 获取点击位置（相对于视口）和购物车元素位置（使用 VueUse useElementBounding）
    const { clientX: clickX, clientY: clickY } = event;
    const bounds = useElementBounding(cartTarget);
    const cartX = unref(bounds.x);
    const cartY = unref(bounds.y);
    const cartW = unref(bounds.width);
    const cartH = unref(bounds.height);

    // 2. 计算动画目标位置（购物车中心）
    const targetX = cartX + cartW / 2 - 8; // 8 = 小圆球直径 16px / 2，居中对齐
    const targetY = cartY + cartH / 2 - 8;

    // 3. 创建小圆球动画元素（纯 CSS 样式，无 UnoCSS 依赖）
    const ball = document.createElement("div");
    ball.style.width = "16px"; // 小圆球直径 16px
    ball.style.height = "16px";
    ball.style.borderRadius = "50%"; // 圆形（小圆球核心样式）
    ball.style.position = "absolute";
    ball.style.pointerEvents = "none"; // 不遮挡交互
    ball.style.zIndex = "9999"; // 提高层级，避免被其他元素遮挡
    ball.style.willChange = "transform, opacity"; // 开启硬件加速
    ball.style.left = "0px";
    ball.style.top = "0px";

    // 设置小圆球颜色和初始样式
    ball.style.backgroundColor = color; // 小球颜色，默认红色，可自定义
    ball.style.transform = `translate(${clickX - 8}px, ${clickY - 8}px) scale(1)`;
    ball.style.opacity = "1";

    // 4. 添加小圆球到动画容器
    container.appendChild(ball);

    const travelDuration = Math.max(0, Math.floor(finalOptions.duration * 0.84));
    const bounceDuration = finalOptions.duration - travelDuration;

    const duration = travelDuration;
    const startX = clickX;
    const startY = clickY;
    const endX = targetX + 8; // 回到中心坐标再减去 8 的偏移，前面 transform 已经做了 -8
    const endY = targetY + 8;
    const midX = (startX + endX) / 2;
    const marginTop = 24;
    const marginBottom = 24;
    const viewportH = window.innerHeight || document.documentElement.clientHeight || 800;
    const clampY = (yy: number) => Math.max(marginTop, Math.min(yy, viewportH - marginBottom));
    const desiredArc = Math.max(80, Math.abs(endX - startX) * 0.3);
    const maxArc = Math.max(10, Math.min(desiredArc, Math.min(startY, endY) - marginTop));
    const controlX = midX;
    const controlY = clampY(Math.min(startY, endY) - maxArc);
    const startTime = performance.now();
    const ease = (t: number) => {
      const p1y = 0.67,
        p2y = 0.67;
      const u = 1 - t;
      const y = u * u * u * 0 + 3 * u * u * t * p1y + 3 * u * t * t * p2y + t * t * t * 1;
      return y;
    };

    const step = (now: number) => {
      const rawT = Math.min((now - startTime) / duration, 1);
      const t = ease(rawT);
      const invT = 1 - t;
      const x = invT * invT * startX + 2 * invT * t * controlX + t * t * endX;
      let y = invT * invT * startY + 2 * invT * t * controlY + t * t * endY;
      y = clampY(y);
      const scale = 1 - 0.3 * t;
      const opacity = 1 - 0.6 * t;
      ball.style.transform = `translate(${x - 8}px, ${y - 8}px) scale(${scale})`;
      ball.style.opacity = String(opacity);

      if (rawT < 1) {
        requestAnimationFrame(step);
      } else {
        const overshoot = 10;
        const dirX = endX - startX;
        const dirY = endY - startY;
        const len = Math.max(1, Math.hypot(dirX, dirY));
        const ox = (dirX / len) * overshoot;
        let oy = (dirY / len) * overshoot;
        if (oy < 0) {
          const availableTop = endY - marginTop;
          oy = -Math.min(Math.abs(oy), Math.max(0, availableTop));
        }
        const bounceStart = performance.now();
        const bounceStep = (now2: number) => {
          const tb = Math.min((now2 - bounceStart) / bounceDuration, 1);
          let bx, by;
          if (tb < 0.5) {
            const p = ease(tb * 2);
            bx = endX + ox * p;
            by = clampY(endY + oy * p);
          } else {
            const p = ease((tb - 0.5) * 2);
            bx = endX + ox * (1 - p);
            by = clampY(endY + oy * (1 - p));
          }
          const bScale = 0.7 + 0.3 * (1 - tb);
          const bOpacity = 0.4 + 0.6 * (1 - tb);
          ball.style.transform = `translate(${bx - 8}px, ${by - 8}px) scale(${bScale})`;
          ball.style.opacity = String(bOpacity);
          if (tb < 1) {
            requestAnimationFrame(bounceStep);
          } else {
            if (container.contains(ball)) container.removeChild(ball);
            cartCount.value++;
          }
        };
        requestAnimationFrame(bounceStep);
      }
    };
    requestAnimationFrame(step);
  };

  // 8. 节流包装动画方法，避免重复触发
  const throttledTriggerCartAnimation = throttle(triggerCartAnimation, finalOptions.throttleTime);

  // 9. 暴露对外方法和状态
  return {
    animateContainer,
    cartCount,
    throttledTriggerCartAnimation,
    triggerCartAnimation,
  };
}

// 1. 定义购物车目标元素 Ref（绑定到视图中的购物车）
const cartTarget = ref(null);

// 共享购物车数量
const { cartCount, refresh } = useCartCount();

// 2. 引入并使用封装的购物车动画 Composables
const {
  animateContainer, // 动画容器 Ref
  throttledTriggerCartAnimation, // 节流后的动画触发方法
} = useCartAnimation({
  cartTarget, // 传入购物车目标元素
  cartCount, // 传入共享 ref，动画完成时自动 +1
  duration: 800, // 动画时长（0.5s）
  throttleTime: 500, // 节流时间（可选，默认 500ms）
});

onMounted(() => {
  refresh();
});

// 3. 点击按钮触发动画
const addToCart = (event) => {
  // 调用 Composables 提供的方法，传入点击事件和小圆球颜色（可选）
  throttledTriggerCartAnimation(event, "var(--el-color-primary)"); // 蓝色小圆球
};

$common.addToCart = addToCart;
</script>

<style scoped>
/* 关键：动画容器纯 CSS 样式，保证小球不被遮挡、正常渲染 */
.animate-container {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0; /* 替换 UnoCSS 的 inset-0，纯 CSS 实现全屏固定 */
  pointer-events: none; /* 不遮挡其他交互 */
  z-index: 9998; /* 低于小球的 9999，保证小球在最上层 */
}
.el-badge__content {
  transition: all 0.3s ease;
}
</style>
