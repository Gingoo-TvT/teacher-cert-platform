<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { MenuOption } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const activeMenu = computed(() => String(route.name || 'dashboard'))

const menuOptions: MenuOption[] = [
  { label: '首页', key: 'dashboard' },
  {
    label: '基础数据',
    key: 'baseData',
    children: [
      { label: '字典管理', key: 'dictManage' },
      { label: '行政区划', key: 'regionManage' },
      { label: '任教学科库', key: 'subjectManage' },
      { label: '组织与专业', key: 'organizationManage' }
    ]
  }
]

function handleMenuUpdate(key: string) {
  if (router.hasRoute(key)) {
    router.push({ name: key })
  }
}

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
        <n-menu :value="activeMenu" :options="menuOptions" @update:value="handleMenuUpdate" />
      </n-layout-sider>
      <n-layout-content content-style="padding: 20px">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>
