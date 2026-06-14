<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { MenuOption } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const activeMenu = computed(() => String(route.name || 'dashboard'))

interface AppMenuOption {
  label: string
  key: string
  perms?: string[]
  children?: AppMenuOption[]
}

const rawMenuOptions: AppMenuOption[] = [
  { label: '首页', key: 'dashboard' },
  {
    label: '基础数据',
    key: 'baseData',
    children: [
      { label: '字典管理', key: 'dictManage', perms: ['dict:manage'] },
      { label: '行政区划', key: 'regionManage', perms: ['region:manage'] },
      { label: '任教学科库', key: 'subjectManage', perms: ['subject:manage'] },
      { label: '组织与专业', key: 'organizationManage', perms: ['college:manage', 'major:manage'] }
    ]
  },
  {
    label: '系统管理',
    key: 'systemManage',
    children: [{ label: '账号权限', key: 'securityManage', perms: ['system:user:manage', 'system:role:manage', 'system:perm:manage'] }]
  }
]

const menuOptions = computed<MenuOption[]>(() => rawMenuOptions.map(filterMenu).filter(Boolean) as MenuOption[])
const displayName = computed(() => userStore.realName || userStore.username || '未命名用户')

function filterMenu(item: AppMenuOption): AppMenuOption | null {
  const visibleByPerm = !item.perms?.length || userStore.hasAnyPerm(item.perms)
  const children = item.children?.map(filterMenu).filter(Boolean) as AppMenuOption[] | undefined
  if (!visibleByPerm && (!children || children.length === 0)) return null
  return { ...item, children }
}

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
      <n-space align="center" :size="12">
        <n-tag v-if="userStore.mustChangePwd" size="small" type="warning" bordered>初始密码</n-tag>
        <span class="user-name">{{ displayName }}</span>
        <n-button quaternary @click="handleLogout">退出登录</n-button>
      </n-space>
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

<style scoped>
.user-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #4b5563;
}
</style>
