<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const username = ref('')
const password = ref('')

function handleLogin() {
  // 占位：Phase 2 接入验证码 + 真实认证接口
  userStore.setToken('dev-token')
  userStore.username = username.value
  const redirect = (route.query.redirect as string) || '/'
  router.push(redirect)
}
</script>

<template>
  <div class="login-wrap">
    <n-card style="width: 380px" title="平台登录">
      <n-form>
        <n-form-item label="账号">
          <n-input v-model:value="username" placeholder="学号 / 工号" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" show-password-on="click" placeholder="密码" />
        </n-form-item>
        <n-button type="primary" block @click="handleLogin">登录</n-button>
      </n-form>
      <template #footer>
        <span style="color: #999; font-size: 12px">Phase 2 接入验证码与真实认证</span>
      </template>
    </n-card>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
</style>
