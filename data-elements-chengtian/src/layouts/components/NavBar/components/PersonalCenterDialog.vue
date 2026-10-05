<template>
  <el-dialog
    v-model="visible"
    title="个人中心"
    width="620px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @open="loadProfile"
    @closed="resetPasswordForm"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="profile">
        <el-skeleton :loading="profileLoading" animated :rows="5">
          <el-descriptions :column="1" border class="profile-details">
            <el-descriptions-item label="姓名">{{ profile.realName || "—" }}</el-descriptions-item>
            <el-descriptions-item label="登录账号">
              {{ profile.userName || "—" }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号码">{{ profile.phone || "—" }}</el-descriptions-item>
            <el-descriptions-item label="所属机构">
              {{ profile.orgName || "—" }}
            </el-descriptions-item>
            <el-descriptions-item label="机构编码">
              {{ profile.serialNumber || "—" }}
            </el-descriptions-item>
            <el-descriptions-item label="机构路径">
              {{ profile.orgPath || "—" }}
            </el-descriptions-item>
            <el-descriptions-item label="角色标识">
              <div v-if="profile.roles?.length" class="role-list">
                <el-tag v-for="role in profile.roles" :key="role" effect="plain">{{ role }}</el-tag>
              </div>
              <span v-else>—</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-skeleton>
      </el-tab-pane>
      <el-tab-pane label="修改密码" name="password">
        <el-alert
          title="修改成功后，当前账号的登录会话将失效，请使用新密码重新登录。"
          type="info"
          :closable="false"
          class="password-notice"
        />
        <el-form label-width="100px" @submit.prevent="submitPassword">
          <el-form-item label="当前密码" required>
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入当前密码"
            />
          </el-form-item>
          <el-form-item label="新密码" required>
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请输入 6 至 128 位新密码"
            />
          </el-form-item>
          <el-form-item label="确认新密码" required>
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请再次输入新密码"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        v-if="activeTab === 'password'"
        type="primary"
        :loading="savingPassword"
        @click="submitPassword"
      >
        确认修改
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import UserAPI, { type UserInfo } from "@/api/system/user-api";
import { useUserStore } from "@/store";

const visible = defineModel<boolean>({ required: true });
const router = useRouter();
const userStore = useUserStore();
const activeTab = ref("profile");
const profileLoading = ref(false);
const savingPassword = ref(false);
const profile = ref<UserInfo>({ roles: [], perms: [] });
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" });

async function loadProfile() {
  activeTab.value = "profile";
  profileLoading.value = true;
  try {
    const data = await UserAPI.getInfo();
    profile.value = data;
    Object.assign(userStore.userInfo, data);
  } finally {
    profileLoading.value = false;
  }
}

function resetPasswordForm() {
  passwordForm.oldPassword = "";
  passwordForm.newPassword = "";
  passwordForm.confirmPassword = "";
}

async function submitPassword() {
  if (savingPassword.value) return;
  const { oldPassword, newPassword, confirmPassword } = passwordForm;
  if (!oldPassword || !newPassword || !confirmPassword) {
    ElMessage.warning("请完整填写密码信息");
    return;
  }
  if (newPassword.length < 6 || newPassword.length > 128) {
    ElMessage.warning("新密码应为 6 至 128 位");
    return;
  }
  if (newPassword === oldPassword) {
    ElMessage.warning("新密码不能与当前密码相同");
    return;
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  savingPassword.value = true;
  try {
    await UserAPI.changeOwnPassword(oldPassword, newPassword);
    resetPasswordForm();
    visible.value = false;
    await userStore.resetAllState();
    ElMessage.success("密码已修改，请使用新密码重新登录");
    await router.replace("/login");
  } finally {
    savingPassword.value = false;
  }
}
</script>

<style scoped lang="scss">
.profile-details {
  margin-top: 8px;
}

.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.password-notice {
  margin-bottom: 20px;
}
</style>
