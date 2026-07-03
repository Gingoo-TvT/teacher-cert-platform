<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { NBadge, NButton, NDropdown, NIcon, NTag, type MenuOption, type SelectOption } from 'naive-ui'
import {
  BarChartOutline,
  HomeOutline,
  FolderOpenOutline,
  NotificationsOutline,
  PersonOutline,
  RibbonOutline,
  ServerOutline,
  SettingsOutline,
  VideocamOutline
} from '@vicons/ionicons5'
import { useNoticeStore } from '@/stores/notice'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const yearStore = useYearStore()
const noticeStore = useNoticeStore()
const collapsed = ref(false)
const unreadCount = computed(() => noticeStore.unreadCount)
let noticeTimer: number | undefined

interface AppMenuLeaf {
  label: string
  key: string
  path: string
  perms?: string[]
  studentOnly?: boolean
}

interface AppMenuGroup {
  label: string
  key: string
  icon: typeof HomeOutline
  children: AppMenuLeaf[]
}

const rawMenu: AppMenuGroup[] = [
  {
    label: '工作台',
    key: 'g-dashboard',
    icon: HomeOutline,
    children: [
      { label: '首页', key: 'dashboard', path: '/' }
    ]
  },
  {
    label: '基本信息',
    key: 'g-basic',
    icon: PersonOutline,
    children: [
      { label: '学生基本信息', key: 'studentManage', path: '/students', perms: ['student:view'] },
      { label: '本人基本信息', key: 'studentSelf', path: '/student/self', perms: ['student:confirm'], studentOnly: true },
      { label: '专业培养信息', key: 'trainingManage', path: '/training', perms: ['student:view', 'training:confirm'] }
    ]
  },
  {
    label: '材料与免考',
    key: 'g-material',
    icon: FolderOpenOutline,
    children: [
      { label: '过程性材料', key: 'materialManage', path: '/materials', perms: ['material:upload', 'material:firstReview', 'material:secondReview', 'material:batchDownload'] },
      { label: '免考管理', key: 'exemptionManage', path: '/exemptions', perms: ['exemption:apply', 'exemption:firstReview', 'exemption:secondReview'] }
    ]
  },
  {
    label: '视频与测试',
    key: 'g-video',
    icon: VideocamOutline,
    children: [
      { label: '视频评审', key: 'videoReview', path: '/videos', perms: ['video:upload', 'video:score', 'video:assign', 'video:arbitrate', 'video:confirm', 'video:play'] },
      { label: '测试结果', key: 'testResultManage', path: '/tests', perms: ['student:view', 'test:import', 'test:confirm'] }
    ]
  },
  {
    label: '证书与交换',
    key: 'g-cert',
    icon: RibbonOutline,
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
    icon: BarChartOutline,
    children: [
      { label: '统计报表', key: 'statsReport', path: '/stats', perms: ['stats:view'] },
      { label: '通知中心', key: 'noticeCenter', path: '/notice', perms: ['notice:view'] }
    ]
  },
  {
    label: '基础数据',
    key: 'g-base-data',
    icon: ServerOutline,
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
    icon: SettingsOutline,
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
const canViewNotice = computed(() => userStore.hasPerm('notice:view'))
const yearOptions = computed<SelectOption[]>(() => yearStore.yearOptions.map((year) => ({ label: year, value: year })))

const menuOptions = computed<MenuOption[]>(() => {
  return rawMenu
    .map((group) => {
      const visibleChildren = group.children.filter(canShowLeaf)
      if (visibleChildren.length === 0) return null
      if (visibleChildren.length === 1 && visibleChildren[0].label === group.label) {
        const leaf = visibleChildren[0]
        return {
          key: leaf.key,
          icon: renderMenuIcon(group.icon),
          label: () => h(RouterLink, { to: leaf.path }, { default: () => leaf.label })
        }
      }
      return {
        key: group.key,
        icon: renderMenuIcon(group.icon),
        label: group.label,
        children: visibleChildren.map((leaf) => ({
          key: leaf.key,
          label: () => renderMenuLabel(leaf)
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
  // 「本人…」自助页仅对学生本人有意义：超级管理员虽持全部权限点，但无学生档案，应隐藏。
  if (leaf.studentOnly && !userStore.roles.includes('STUDENT')) return false
  return !leaf.perms?.length || userStore.hasAnyPerm(leaf.perms)
}

function renderMenuLabel(leaf: AppMenuLeaf) {
  const label = leaf.key === 'noticeCenter' && unreadCount.value > 0
    ? h('span', { class: 'menu-label-with-dot' }, [
        h('span', leaf.label),
        h('span', { class: 'menu-dot' })
      ])
    : leaf.label
  return h(RouterLink, { to: leaf.path }, { default: () => label })
}

function renderMenuIcon(icon: typeof HomeOutline) {
  return () => h(NIcon, { component: icon, size: 19 })
}

async function refreshUnread() {
  if (!canViewNotice.value) {
    noticeStore.reset()
    return
  }
  await noticeStore.refresh()
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
          <div class="year-picker">
            <span>考核学年</span>
            <n-select
              :value="yearStore.assessmentYear"
              :options="yearOptions"
              size="small"
              @update:value="(value: string) => yearStore.setYear(value)"
            />
          </div>
          <n-badge v-if="canViewNotice" :value="unreadCount" :max="99" :show-zero="false" color="var(--brand)">
            <n-button quaternary circle size="small" title="通知中心" @click="openNoticeCenter">
              <template #icon>
                <n-icon :component="NotificationsOutline" />
              </template>
            </n-button>
          </n-badge>
          <n-tag v-if="userStore.mustChangePwd" size="small" type="warning" :bordered="false">初始密码</n-tag>
          <n-dropdown :options="userMenu" @select="onUserMenu">
            <button class="user-chip" type="button">
              <span class="avatar">{{ displayName.slice(0, 1) }}</span>
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
  background: var(--surface);
  border-right: 1px solid var(--border);
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-5);
  color: var(--brand);
  border-bottom: 0;
}

.brand-logo {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-control);
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px var(--brand-border);
}

.brand-text {
  min-width: 0;
  line-height: 1.2;
}

.brand-text strong {
  display: block;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  letter-spacing: 0;
}

.brand-text span {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: .02em;
  white-space: nowrap;
}

.app-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  padding: 0 var(--space-7);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.page-title {
  min-width: 0;
  font-size: 20px;
  line-height: 30px;
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.year-picker {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 180px;
  font-size: 12px;
  color: var(--text-muted);
}

.year-picker :deep(.n-select) {
  width: 96px;
}

.user-chip {
  height: 40px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  cursor: pointer;
  color: inherit;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.user-chip:hover {
  background: var(--brand-soft);
  border-color: var(--border);
}

.avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--brand);
  color: var(--text-inverse);
  font-size: 13px;
  font-weight: 600;
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
  padding: var(--space-7);
  min-width: 0;
}

:deep(.n-menu) {
  padding: var(--space-3) var(--space-3) var(--space-5);
}

:deep(.n-menu .n-menu-item-content) {
  margin: 5px 0;
  min-height: 42px;
  border-radius: 999px;
}

:deep(.n-menu .n-menu-item-content::before) {
  border-radius: 999px;
}

:deep(.n-menu .n-menu-item-content.n-menu-item-content--selected) {
  position: relative;
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px var(--brand-border);
}

:deep(.n-menu .n-menu-item-content-header) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.n-menu .n-submenu-children .n-menu-item-content) {
  padding-left: 46px !important;
}

:deep(.n-layout-toggle-button) {
  border-color: var(--border);
  background: var(--surface);
  color: var(--text-secondary);
  box-shadow: var(--shadow-card);
  border-radius: 999px;
}

:deep(.menu-label-with-dot) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

:deep(.menu-dot) {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft-strong);
}

:deep(.page-container) {
  max-width: 1480px;
}

@media (max-width: 900px) {
  .app-header {
    padding: 0 var(--space-5);
  }

  .header-actions {
    gap: var(--space-2);
  }

  .year-picker > span,
  .user-meta {
    display: none;
  }

  .year-picker {
    min-width: 98px;
  }

  .app-content {
    padding: var(--space-5);
  }
}
</style>
