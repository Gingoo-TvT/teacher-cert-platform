<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { changePassword, getCaptcha, login } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const pwdFormRef = ref<FormInst | null>(null)
const loading = ref(false)
const captchaLoading = ref(false)
const changePwdVisible = ref(false)
const changingPwd = ref(false)

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
  captchaImage: ''
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: ['blur', 'input'] }],
  password: [{ required: true, message: '请输入密码', trigger: ['blur', 'input'] }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: ['blur', 'input'] }]
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: ['blur', 'input'] }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: ['blur', 'input'] },
    { min: 8, max: 64, message: '新密码长度需为 8-64 位', trigger: ['blur', 'input'] }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: ['blur', 'input'] },
    {
      validator: (_rule, value: string) => value === pwdForm.newPassword || new Error('两次输入的新密码不一致'),
      trigger: ['blur', 'input']
    }
  ]
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    form.captchaId = res.data.captchaId
    form.captchaImage = res.data.image
    form.captchaCode = ''
  } catch (error) {
    showError(error, '验证码加载失败')
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const res = await login({
      username: form.username.trim(),
      password: form.password,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode.trim()
    })
    userStore.applyLogin(res.data)
    if (res.data.mustChangePwd) {
      pwdForm.oldPassword = form.password
      pwdForm.newPassword = ''
      pwdForm.confirmPassword = ''
      changePwdVisible.value = true
      message.warning('请先修改初始密码')
      return
    }
    goRedirect()
  } catch (error) {
    showError(error, '登录失败')
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function submitChangePwd() {
  await pwdFormRef.value?.validate()
  changingPwd.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    message.success('密码已修改')
    changePwdVisible.value = false
    await userStore.loadMe()
    goRedirect()
  } catch (error) {
    showError(error, '密码修改失败')
  } finally {
    changingPwd.value = false
  }
}

function goRedirect() {
  const redirect = (route.query.redirect as string) || '/'
  router.push(redirect)
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="login-wrap">
    <n-card class="login-card" title="平台登录">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" @keyup.enter="handleLogin">
        <n-form-item label="账号" path="username">
          <n-input v-model:value="form.username" placeholder="学号 / 工号" />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input v-model:value="form.password" type="password" show-password-on="click" placeholder="密码" />
        </n-form-item>
        <n-form-item label="验证码" path="captchaCode">
          <div class="captcha-row">
            <n-input v-model:value="form.captchaCode" maxlength="4" placeholder="验证码" />
            <button class="captcha-button" type="button" :disabled="captchaLoading" @click="loadCaptcha">
              <img v-if="form.captchaImage" :src="form.captchaImage" alt="验证码" />
              <span v-else>刷新</span>
            </button>
          </div>
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="handleLogin">登录</n-button>
      </n-form>
    </n-card>

    <n-modal v-model:show="changePwdVisible" preset="dialog" title="修改初始密码" :show-icon="false" :closable="false">
      <n-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-placement="top">
        <n-form-item label="旧密码" path="oldPassword">
          <n-input v-model:value="pwdForm.oldPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input v-model:value="pwdForm.newPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="确认新密码" path="confirmPassword">
          <n-input v-model:value="pwdForm.confirmPassword" type="password" show-password-on="click" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button type="primary" :loading="changingPwd" @click="submitChangePwd">确认修改</n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f6;
}

.login-card {
  width: min(390px, calc(100vw - 32px));
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 118px;
  gap: 10px;
  width: 100%;
}

.captcha-button {
  height: 34px;
  padding: 0;
  border: 1px solid #d9dee8;
  background: #fff;
  cursor: pointer;
}

.captcha-button img {
  display: block;
  width: 112px;
  height: 40px;
  object-fit: contain;
  margin: -4px auto;
}

.captcha-button:disabled {
  cursor: wait;
  opacity: 0.72;
}
</style>
