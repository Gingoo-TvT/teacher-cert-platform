<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { MenuOption } from 'naive-ui'

const router = useRouter()
const userStore = useUserStore()

// 菜单占位（Phase 2 接入动态权限菜单）
const menuOptions: MenuOption[] = [{ label: '首页', key: 'dashboard' }]

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <n-layout style="height: 100vh">
    <n-layout-header
      bordered
      style="height: 50px; padding: 0 20px; display: flex; justify-content: space-between; align-items: center"
    >
      <span style="font-weight: 600">师范生考核与教师职业能力证书管理平台</span>
      <n-button quaternary @click="handleLogout">退出登录</n-button>
    </n-layout-header>
    <n-layout has-sider style="height: calc(100vh - 50px)">
      <n-layout-sider bordered :width="220" content-style="padding: 12px 0">
        <n-menu :options="menuOptions" />
      </n-layout-sider>
      <n-layout-content content-style="padding: 20px">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>
