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
  <div class="login-shell">
    <section class="brand-panel" aria-label="平台名称">
      <div class="brand-mark">师</div>
      <div class="brand-kicker">广东技术师范大学</div>
      <h1>师范生教育教学能力考核与教师职业能力证书管理平台</h1>
      <p>基本信息、培养过程、材料审核、视频评审、测试结果、证书编号与上报数据的全流程管理。</p>
      <div class="brand-meta">
        <span>真实后端接入</span>
        <span>权限按功能点控制</span>
        <span>全链路留痕</span>
      </div>
    </section>

    <section class="login-panel" aria-label="登录表单">
      <div class="login-form-card">
        <div class="login-heading">
          <span>平台登录</span>
          <strong>欢迎使用</strong>
        </div>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" @keyup.enter="handleLogin">
          <n-form-item label="账号" path="username">
            <n-input v-model:value="form.username" size="large" placeholder="学号 / 工号" />
          </n-form-item>
          <n-form-item label="密码" path="password">
            <n-input v-model:value="form.password" size="large" type="password" show-password-on="click" placeholder="密码" />
          </n-form-item>
          <n-form-item label="验证码" path="captchaCode">
            <div class="captcha-row">
              <n-input v-model:value="form.captchaCode" size="large" maxlength="4" placeholder="验证码" />
              <button class="captcha-button" type="button" :disabled="captchaLoading" @click="loadCaptcha">
                <img v-if="form.captchaImage" :src="form.captchaImage" alt="验证码" />
                <span v-else>刷新</span>
              </button>
            </div>
          </n-form-item>
          <n-button type="primary" block size="large" :loading="loading" @click="handleLogin">登录</n-button>
        </n-form>
      </div>
    </section>

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
.login-shell {
  height: 100vh;
  min-height: 640px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 460px;
  background: #fff;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px;
  color: #fff;
  background:
    linear-gradient(135deg, rgba(13, 38, 85, 0.96), rgba(31, 111, 235, 0.92)),
    linear-gradient(90deg, #0f2e63, #1f6feb);
}

.brand-mark {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 22px;
  font-weight: 800;
}

.brand-kicker {
  margin-top: 28px;
  font-size: 14px;
  opacity: 0.86;
}

.brand-panel h1 {
  max-width: 680px;
  margin: 12px 0 0;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.22;
  font-weight: 700;
  letter-spacing: 0;
}

.brand-panel p {
  max-width: 560px;
  margin: 18px 0 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 15px;
  line-height: 1.8;
}

.brand-meta {
  margin-top: 26px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.brand-meta span {
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: #fff;
}

.login-form-card {
  width: min(100%, 340px);
}

.login-heading {
  margin-bottom: 24px;
}

.login-heading span,
.login-heading strong {
  display: block;
}

.login-heading span {
  color: var(--text-muted);
  font-size: 13px;
}

.login-heading strong {
  margin-top: 4px;
  color: #111827;
  font-size: 24px;
  line-height: 1.35;
  letter-spacing: 0;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: 10px;
  width: 100%;
}

.captcha-button {
  height: 40px;
  padding: 0;
  border: 1px solid #d9dee8;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
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

@media (max-width: 860px) {
  .login-shell {
    min-height: 100vh;
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: 280px;
    padding: 36px 24px;
  }

  .brand-panel h1 {
    font-size: 28px;
  }

  .brand-meta {
    display: none;
  }

  .login-panel {
    align-items: flex-start;
    padding: 32px 24px 48px;
  }
}
</style>
