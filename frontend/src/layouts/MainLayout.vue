<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { NBadge, NButton, NDropdown, NTag, type MenuOption } from 'naive-ui'
import { unreadNoticeCount } from '@/api/notice'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const collapsed = ref(false)
const unreadCount = ref(0)
let noticeTimer: number | undefined

interface AppMenuLeaf {
  label: string
  key: string
  path: string
  perms?: string[]
}

interface AppMenuGroup {
  label: string
  key: string
  mark: string
  children: AppMenuLeaf[]
}

const rawMenu: AppMenuGroup[] = [
  {
    label: '工作台',
    key: 'g-dashboard',
    mark: '概',
    children: [
      { label: '首页', key: 'dashboard', path: '/' }
    ]
  },
  {
    label: '基本信息',
    key: 'g-basic',
    mark: '基',
    children: [
      { label: '学生基本信息', key: 'studentManage', path: '/students', perms: ['student:view'] },
      { label: '本人基本信息', key: 'studentSelf', path: '/student/self', perms: ['student:confirm'] },
      { label: '专业培养信息', key: 'trainingManage', path: '/training', perms: ['student:view', 'training:confirm'] }
    ]
  },
  {
    label: '材料与免考',
    key: 'g-material',
    mark: '材',
    children: [
      { label: '过程性材料', key: 'materialManage', path: '/materials', perms: ['material:upload', 'material:firstReview', 'material:secondReview', 'material:batchDownload'] },
      { label: '免考管理', key: 'exemptionManage', path: '/exemptions', perms: ['exemption:apply', 'exemption:firstReview', 'exemption:secondReview'] }
    ]
  },
  {
    label: '视频与测试',
    key: 'g-video',
    mark: '评',
    children: [
      { label: '视频评审', key: 'videoReview', path: '/videos', perms: ['video:upload', 'video:score', 'video:assign', 'video:arbitrate', 'video:confirm', 'video:play'] },
      { label: '测试结果', key: 'testResultManage', path: '/tests', perms: ['student:view', 'test:import', 'test:confirm'] }
    ]
  },
  {
    label: '证书与交换',
    key: 'g-cert',
    mark: '证',
    children: [
      { label: '证书管理', key: 'certificateManage', path: '/certificates', perms: ['cert:view', 'cert:generate', 'cert:correct', 'cert:void', 'cert:reissue'] },
      { label: '证书签发', key: 'certificateIssue', path: '/certificate-issue', perms: ['cert:issue'] },
      { label: '导入预校验', key: 'exchangeImport', path: '/exchange/import', perms: ['exchange:template', 'exchange:prevalidate', 'exchange:import'] },
      { label: '导出中心', key: 'exchangeExport', path: '/exchange/export', perms: ['exchange:export:standard', 'exchange:export:full'] }
    ]
  },
  {
    label: '统计与通知',
    key: 'g-stats',
    mark: '统',
    children: [
      { label: '统计报表', key: 'statsReport', path: '/stats', perms: ['stats:view'] },
      { label: '通知中心', key: 'noticeCenter', path: '/notice', perms: ['notice:view'] }
    ]
  },
  {
    label: '基础数据',
    key: 'g-base-data',
    mark: '数',
    children: [
      { label: '字典管理', key: 'dictManage', path: '/system/dicts', perms: ['dict:manage'] },
      { label: '行政区划', key: 'regionManage', path: '/system/regions', perms: ['region:manage'] },
      { label: '任教学科库', key: 'subjectManage', path: '/system/subjects', perms: ['subject:manage'] },
      { label: '组织与专业', key: 'organizationManage', path: '/system/organizations', perms: ['college:manage', 'major:manage'] }
    ]
  },
  {
    label: '系统管理',
    key: 'g-system',
    mark: '系',
    children: [
      { label: '账号权限', key: 'securityManage', path: '/system/security', perms: ['system:user:manage', 'system:role:manage', 'system:perm:manage'] },
      { label: '参数审计备份', key: 'systemAudit', path: '/system/audit', perms: ['system:param:manage', 'audit:view', 'system:backup'] }
    ]
  }
]

const pageTitle = computed(() => String(route.meta.title || '工作台'))
const activeMenu = computed(() => String(route.name || 'dashboard'))
const displayName = computed(() => userStore.realName || userStore.username || '未命名用户')
const roleLabel = computed(() => roleName(userStore.roles[0]))
const roleColor = computed(() => roleColorByCode(userStore.roles[0]))
const canViewNotice = computed(() => userStore.hasPerm('notice:view'))

const menuOptions = computed<MenuOption[]>(() => {
  return rawMenu
    .map((group) => {
      const visibleChildren = group.children.filter(canShowLeaf)
      if (visibleChildren.length === 0) return null
      if (visibleChildren.length === 1 && visibleChildren[0].label === group.label) {
        const leaf = visibleChildren[0]
        return {
          key: leaf.key,
          icon: () => h('span', { class: 'menu-mark' }, group.mark),
          label: () => h(RouterLink, { to: leaf.path }, { default: () => leaf.label })
        }
      }
      return {
        key: group.key,
        icon: () => h('span', { class: 'menu-mark' }, group.mark),
        label: group.label,
        children: visibleChildren.map((leaf) => ({
          key: leaf.key,
          label: () => h(RouterLink, { to: leaf.path }, { default: () => leaf.label })
        }))
      }
    })
    .filter(Boolean) as MenuOption[]
})

const expandedKeys = computed(() => {
  return rawMenu
    .filter((group) => group.children.some((leaf) => leaf.key === route.name && canShowLeaf(leaf)))
    .map((group) => group.key)
})

const userMenu = computed(() => [
  { label: '退出登录', key: 'logout' }
])

onMounted(() => {
  refreshUnread()
  noticeTimer = window.setInterval(refreshUnread, 60000)
})

onBeforeUnmount(() => {
  if (noticeTimer) window.clearInterval(noticeTimer)
})

function canShowLeaf(leaf: AppMenuLeaf) {
  return !leaf.perms?.length || userStore.hasAnyPerm(leaf.perms)
}

async function refreshUnread() {
  if (!canViewNotice.value) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await unreadNoticeCount()
    unreadCount.value = Number(res.data || 0)
  } catch {
    unreadCount.value = 0
  }
}

function openNoticeCenter() {
  router.push({ name: 'noticeCenter' })
}

function onUserMenu(key: string) {
  if (key !== 'logout') return
  userStore.logout()
  router.push('/login')
}

function roleName(code?: string) {
  const map: Record<string, string> = {
    STUDENT: '学生',
    COLLEGE_CLERK: '学院教务员',
    COLLEGE_AUDITOR: '学院负责人',
    REVIEW_TEACHER: '评审教师',
    ACADEMIC_ADMIN: '教务处管理员',
    SYS_ADMIN: '系统管理员'
  }
  return code ? map[code] || code : '未分配角色'
}

function roleColorByCode(code?: string) {
  const map: Record<string, string> = {
    STUDENT: 'var(--role-student)',
    COLLEGE_CLERK: 'var(--role-college-clerk)',
    COLLEGE_AUDITOR: 'var(--role-college-auditor)',
    REVIEW_TEACHER: 'var(--role-review-teacher)',
    ACADEMIC_ADMIN: 'var(--role-academic-admin)',
    SYS_ADMIN: 'var(--role-sys-admin)'
  }
  return code ? map[code] || 'var(--role-sys-admin)' : 'var(--role-sys-admin)'
}
</script>

<template>
  <n-layout has-sider class="app-shell">
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="248"
      :collapsed="collapsed"
      show-trigger
      :native-scrollbar="false"
      class="app-sider"
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="brand">
        <div class="brand-logo">师</div>
        <div v-if="!collapsed" class="brand-text">
          <strong>教师证书平台</strong>
          <span>GD Polytechnic Normal University</span>
        </div>
      </div>
      <n-menu
        :value="activeMenu"
        :default-expanded-keys="expandedKeys"
        :options="menuOptions"
        :collapsed="collapsed"
        :collapsed-width="64"
        :indent="18"
      />
    </n-layout-sider>

    <n-layout>
      <n-layout-header bordered class="app-header">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="header-actions">
          <n-badge v-if="canViewNotice" :value="unreadCount" :max="99" :show-zero="false">
            <n-button quaternary size="small" @click="openNoticeCenter">通知</n-button>
          </n-badge>
          <n-tag v-if="userStore.mustChangePwd" size="small" type="warning" :bordered="false">初始密码</n-tag>
          <n-dropdown :options="userMenu" @select="onUserMenu">
            <button class="user-chip" type="button">
              <span class="avatar" :style="{ background: roleColor }">{{ displayName.slice(0, 1) }}</span>
              <span class="user-meta">
                <strong>{{ displayName }}</strong>
                <span>{{ roleLabel }}</span>
              </span>
            </button>
          </n-dropdown>
        </div>
      </n-layout-header>

      <n-layout-content :native-scrollbar="false" class="app-content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.fullPath" />
        </router-view>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.app-shell {
  height: 100vh;
}

.app-sider {
  background: #fff;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: var(--brand);
}

.brand-logo {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(31, 111, 235, 0.1);
  font-weight: 800;
}

.brand-text {
  min-width: 0;
  line-height: 1.2;
}

.brand-text strong {
  display: block;
  font-size: 15px;
  letter-spacing: 0;
}

.brand-text span {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: var(--text-muted);
  white-space: nowrap;
}

.app-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  background: #fff;
}

.page-title {
  min-width: 0;
  font-size: 16px;
  font-weight: 650;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  height: 40px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  cursor: pointer;
  color: inherit;
}

.avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.user-meta {
  max-width: 160px;
  line-height: 1.2;
  text-align: left;
}

.user-meta strong,
.user-meta span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta strong {
  font-size: 13px;
}

.user-meta span {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-muted);
}

.app-content {
  background: var(--page-bg);
  padding: 20px;
  min-width: 0;
}

:deep(.menu-mark) {
  width: 22px;
  height: 22px;
  display: inline-grid;
  place-items: center;
  border-radius: 6px;
  background: #eef4ff;
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
}
</style>
