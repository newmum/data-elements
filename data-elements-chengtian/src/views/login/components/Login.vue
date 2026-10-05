<template>
  <div class="login-form-shell">
    <el-form
      ref="loginFormRef"
      :model="loginFormData"
      :rules="loginRules"
      size="large"
      :validate-on-rule-change="false"
    >
      <!-- 用户名 -->
      <el-form-item prop="username">
        <el-input
          v-model.trim="loginFormData.username"
          placeholder="用户名"
          autocomplete="username"
          @keyup.enter="handleLoginSubmit"
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <!-- 密码 -->
      <el-tooltip :visible="isCapsLock" content="大写锁定已打开" placement="right">
        <el-form-item prop="password">
          <el-input
            v-model.trim="loginFormData.password"
            placeholder="密码"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup="checkCapsLock"
            @keyup.enter="handleLoginSubmit"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-tooltip>

      <div class="login-preferences">
        <el-checkbox v-model="loginFormData.rememberMe">记住密码</el-checkbox>
      </div>

      <transition name="tenant-reveal">
        <el-form-item v-if="showTenantSelector" prop="tenantId" class="tenant-selector">
          <el-select
            v-model="loginFormData.tenantId"
            :loading="tenantLoading"
            placeholder="选择使用的行业场景"
          >
            <el-option
              v-for="tenant in tenantOptions"
              :key="tenant.tid"
              :label="tenant.name"
              :value="tenant.tid"
            >
              <div class="tenant-option">
                <span>{{ tenant.name }}</span>
                <small>{{ tenant.code }}</small>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </transition>

      <!-- 登录按钮 -->
      <el-form-item>
        <el-button
          :loading="loading"
          type="primary"
          class="login-button"
          @click="handleLoginSubmit"
        >
          进入平台
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
<script setup lang="ts">
import type { FormInstance } from "element-plus";
import sm from "sm-crypto";
import { type LoginFormData } from "@/api/auth-api";
import TenantAPI, { type TenantOption } from "@/api/tenant-api";
import { useSettingStore, useUserStore } from "@/store";
import { AUTH_KEYS } from "@/enums";
import { Storage } from "@/utils/storage";
import { capabilityCenterBases } from "@/utils/capabilityCenters";

const props = defineProps<{
  showTenantSelector?: boolean;
}>();

const userStore = useUserStore();
const settingsStore = useSettingStore();
const route = useRoute();
const router = useRouter();

const loginFormRef = ref<FormInstance>();
const loading = ref(false);
// 是否大写锁定
const isCapsLock = ref(false);
const tenantLoading = ref(false);
const tenantOptions = ref<TenantOption[]>([]);
const tenantOptionsInitialized = ref(false);
let tenantOptionsPromise: Promise<void> | undefined;

/**
 * Login recovery normally accepts an internal Vue route.  Capability centers
 * run on explicit local origins or first-party production prefixes, so their
 * full URLs need a controlled hand-off after login.  This remains an allowlist
 * rather than making `redirect` an arbitrary external redirect.
 */
const capabilityReturnBases = Object.values(capabilityCenterBases)
  .map((value) => new URL(value, window.location.href));

function approvedCapabilityReturn(value: string): string | undefined {
  try {
    const target = new URL(value, window.location.href);
    const approved = capabilityReturnBases.some((base) =>
      target.origin === base.origin && target.pathname.startsWith(base.pathname),
    );
    return approved ? target.href : undefined;
  } catch {
    return undefined;
  }
}

const credentialKey = sm.sm3(`${window.location.host}:data-elements-login`).slice(0, 32);

/**
 * Tenant IDs are 19-digit snowflake strings.  Do not read them through the
 * generic Storage helper: it JSON-parses a bare numeric string into a
 * JavaScript number and loses precision, which then prevents it matching a
 * tenant option and can make the login initialization throw on `.trim()`.
 */
function normalizeTenantId(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function readLastSelectedTenantId(): string {
  return normalizeTenantId(localStorage.getItem(AUTH_KEYS.LAST_SELECTED_TENANT));
}

function persistLastSelectedTenantId(value: unknown) {
  const tenantId = normalizeTenantId(value);
  if (tenantId) {
    localStorage.setItem(AUTH_KEYS.LAST_SELECTED_TENANT, tenantId);
  } else {
    localStorage.removeItem(AUTH_KEYS.LAST_SELECTED_TENANT);
  }
}

const loginFormData = ref<LoginFormData>({
  username: "",
  password: "",
  captchaKey: "",
  captchaCode: "",
  rememberMe: Storage.get<boolean>(AUTH_KEYS.REMEMBER_ME, false),
  // 登录始终显式携带租户。优先恢复上一次有效选择；仅在它不可用时回退到首个租户。
  tenantId: readLastSelectedTenantId(),
});

function restoreRememberedCredentials() {
  if (!loginFormData.value.rememberMe) return;
  const username = Storage.get<string>(AUTH_KEYS.REMEMBERED_ACCOUNT, "");
  const encryptedPassword = Storage.get<string>(AUTH_KEYS.REMEMBERED_PASSWORD, "");
  loginFormData.value.username = username;
  if (!encryptedPassword) return;
  try {
    loginFormData.value.password = sm.sm4.decrypt(encryptedPassword, credentialKey);
  } catch {
    Storage.remove(AUTH_KEYS.REMEMBERED_PASSWORD);
  }
}

function persistRememberedCredentials() {
  if (!loginFormData.value.rememberMe) {
    Storage.remove(AUTH_KEYS.REMEMBERED_ACCOUNT);
    Storage.remove(AUTH_KEYS.REMEMBERED_PASSWORD);
    return;
  }
  Storage.set(AUTH_KEYS.REMEMBERED_ACCOUNT, loginFormData.value.username);
  Storage.set(
    AUTH_KEYS.REMEMBERED_PASSWORD,
    sm.sm4.encrypt(loginFormData.value.password, credentialKey)
  );
}

async function ensureDefaultTenant() {
  if (tenantOptionsInitialized.value) return;
  if (tenantOptionsPromise) return tenantOptionsPromise;

  tenantOptionsPromise = (async () => {
    tenantLoading.value = true;
    try {
      tenantOptions.value = await TenantAPI.loginOptions();
      // 登录页重新打开（包括租户切换后回到登录页）时，保留最近一次有效选择。
      // 已删除、停用或无权限的历史值才回退到服务端排序的第一项。
      const rememberedTenantId = readLastSelectedTenantId();
      const selectedTenantId = normalizeTenantId(loginFormData.value.tenantId);
      const availableTenantIds = new Set(tenantOptions.value.map((tenant) => String(tenant.tid)));
      const tenantId = [selectedTenantId, rememberedTenantId, tenantOptions.value[0]?.tid]
        .map(normalizeTenantId)
        .find((candidate) => candidate && availableTenantIds.has(candidate));
      loginFormData.value.tenantId = tenantId || "";
      tenantOptionsInitialized.value = true;
    } finally {
      tenantLoading.value = false;
    }
  })();

  try {
    await tenantOptionsPromise;
  } finally {
    tenantOptionsPromise = undefined;
  }
}

onMounted(async () => {
  restoreRememberedCredentials();
  await ensureDefaultTenant();
});

watch(
  () => props.showTenantSelector,
  async (visible) => {
    if (visible) await ensureDefaultTenant();
  },
  { immediate: true }
);

watch(
  () => loginFormData.value.tenantId,
  async (tenantId) => {
    const normalizedTenantId = normalizeTenantId(tenantId);
    persistLastSelectedTenantId(normalizedTenantId);
    if (!normalizedTenantId || normalizedTenantId === settingsStore.brandTenantId) return;
    await settingsStore.loadingSettings(true, normalizedTenantId);
  }
);

const loginRules = computed(() => {
  return {
    username: [
      {
        required: true,
        trigger: "blur",
        message: "请输入用户名",
      },
    ],
    password: [
      {
        required: true,
        trigger: "blur",
        message: "请输入密码",
      },
      {
        min: 6,
        message: "密码不能少于6位",
        trigger: "blur",
      },
    ],
    // 当前登录页未展示验证码输入框，且后端登录接口也不要求验证码。
    // 不要保留隐藏 captchaCode 的必填校验，否则账号和密码均已填写时
    // 表单仍会静默拦截提交，表现为“进入平台”按钮无法点击。
  };
});

/**
 * 登录提交
 */
async function handleLoginSubmit() {
  if (loading.value) return;
  try {
    // 用户可在接口尚未返回时立即提交；此处再次确保已选择服务端排序第一项。
    await ensureDefaultTenant();
    // 1. 表单验证
    const valid = await loginFormRef.value?.validate();
    if (!valid) return;

    loading.value = true;

    // 2. 执行登录
    const data = await userStore.login(loginFormData.value);
    persistRememberedCredentials();
    const requestedPath = decodeURIComponent((route.query.redirect as string) || data || "/");
    const capabilityReturn = approvedCapabilityReturn(requestedPath);
    if (capabilityReturn) {
      // The platform session remains in storage/the bridge, never in the URL.
      window.location.assign(capabilityReturn);
      return;
    }
    // 401 是提示页而非业务菜单。它可能被浏览器恢复为上次页面，登录后统一交给
    // 路由守卫定位当前账号的第一个可访问菜单，不能再次进入权限提示页。
    const redirectPath =
      requestedPath === "/401" || requestedPath.startsWith("/401?") ? "/" : requestedPath;
    // 明确标记这次跳转是“登录后恢复上次页面”。路由守卫据此在目标菜单
    // 不属于当前账号时转到第一个可访问菜单；普通已登录用户主动输入无权
    // 地址时不会携带该标记，仍应展示权限提示页。
    const resolvedRedirect = router.resolve(redirectPath);
    await router.push({
      path: resolvedRedirect.path,
      query: { ...resolvedRedirect.query, __loginRecovery: "1" },
      hash: resolvedRedirect.hash,
    });
  } catch (error) {
    // 4. 统一错误处理
    console.error("登录失败:", error);
  } finally {
    loading.value = false;
  }
}

// 检查输入大小写
function checkCapsLock(event: KeyboardEvent) {
  // 防止浏览器密码自动填充时报错
  if (event instanceof KeyboardEvent) {
    isCapsLock.value = event.getModifierState("CapsLock");
  }
}
</script>

<style lang="scss" scoped>
.login-form-shell {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-input__wrapper) {
    min-height: 48px;
    padding: 0 15px;
    background: rgba(247, 250, 255, 0.92);
    border: 1px solid rgba(37, 99, 235, 0.1);
    border-radius: 12px;
    box-shadow: none;
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease,
      background 0.2s ease;
  }

  :deep(.el-input__wrapper.is-focus) {
    background: #fff;
    border-color: rgba(37, 99, 235, 0.52);
    box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.1);
  }

  :deep(.el-input__inner) {
    font-size: 14px;
  }

  :deep(.el-select) {
    width: 100%;
  }
}

.tenant-option {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.login-preferences {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 24px;
  margin: -8px 0 14px;
}

.login-preferences :deep(.el-checkbox__label) {
  color: #475467;
  font-size: 13px;
}

.tenant-option small {
  color: var(--el-text-color-secondary);
}

.tenant-reveal-enter-active,
.tenant-reveal-leave-active {
  transition: all 0.2s ease;
}

.tenant-reveal-enter-from,
.tenant-reveal-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.login-button {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  border: none;
  border-radius: 12px;
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.24);
}

.login-button:hover,
.login-button:focus {
  background: linear-gradient(135deg, #1d4ed8, #4338ca);
}
</style>
