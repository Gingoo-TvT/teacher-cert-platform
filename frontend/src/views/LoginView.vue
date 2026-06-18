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
  grid-template-columns: minmax(0, 1fr) 480px;
  background: var(--page-bg);
}

.brand-panel {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 72px;
  color: var(--text);
  background:
    radial-gradient(circle at 18% 20%, var(--brand-soft-strong), transparent 34%),
    linear-gradient(135deg, var(--login-brand-start), var(--login-brand-end)),
    var(--brand);
}

.brand-panel::after {
  position: absolute;
  inset: auto 48px 48px auto;
  width: 220px;
  height: 220px;
  border: 1px solid var(--login-panel-line);
  border-radius: 44px;
  transform: rotate(12deg);
  content: "";
}

.brand-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border: 1px solid var(--login-panel-line);
  border-radius: 16px;
  background: var(--login-glass);
  color: var(--brand);
  font-size: 24px;
  font-weight: 600;
  box-shadow: var(--shadow-card);
}

.brand-kicker {
  margin-top: var(--space-8);
  font-size: 14px;
  color: var(--brand-hover);
  font-weight: 500;
}

.brand-panel h1 {
  max-width: 680px;
  margin: var(--space-3) 0 0;
  font-size: 38px;
  line-height: 48px;
  font-weight: 600;
  letter-spacing: 0;
}

.brand-panel p {
  max-width: 560px;
  margin: var(--space-5) 0 0;
  color: var(--login-text-soft);
  font-size: 15px;
  line-height: 1.8;
}

.brand-meta {
  margin-top: var(--space-8);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.brand-meta span {
  padding: 6px 10px;
  border: 1px solid var(--login-panel-line);
  border-radius: 999px;
  background: var(--login-glass);
  color: var(--brand-hover);
  font-size: 12px;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  background: var(--page-bg);
}

.login-form-card {
  width: min(100%, 368px);
  padding: var(--space-8);
  border-radius: var(--radius-card);
  background: var(--surface);
  box-shadow: var(--shadow-card);
}

.login-heading {
  margin-bottom: var(--space-7);
}

.login-heading span,
.login-heading strong {
  display: block;
}

.login-heading span {
  color: var(--text-secondary);
  font-size: 13px;
}

.login-heading strong {
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  letter-spacing: 0;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: var(--space-3);
  width: 100%;
}

.captcha-button {
  height: 40px;
  padding: 0;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-control);
  background: var(--surface);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.captcha-button:hover {
  border-color: var(--brand);
  box-shadow: var(--focus-ring);
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
    padding: var(--space-10) var(--space-6);
  }

  .brand-panel::after {
    display: none;
  }

  .brand-panel h1 {
    font-size: 28px;
    line-height: 36px;
  }

  .brand-meta {
    display: none;
  }

  .login-panel {
    align-items: flex-start;
    padding: var(--space-8) var(--space-6) var(--space-12);
  }

  .login-form-card {
    padding: var(--space-6);
  }
}
</style>
