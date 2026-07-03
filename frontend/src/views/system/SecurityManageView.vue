<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import { listColleges, listMajors, type College, type Major } from '@/api/organization'
import {
  deleteRole, deleteUser, listRoles, listUsers, permissionTree, resetUserPassword,
  type Permission, type Role, type User
} from '@/api/security'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'
import UserDrawer from './components/security/UserDrawer.vue'
import RoleDrawer from './components/security/RoleDrawer.vue'
import RolePermissionDrawer from './components/security/RolePermissionDrawer.vue'
import UserScopeDrawer from './components/security/UserScopeDrawer.vue'

const message = useMessage()
const userStore = useUserStore()

const userLoading = ref(false)
const roleLoading = ref(false)
const permissionLoading = ref(false)
const keyword = ref('')
const roleKeyword = ref('')
const statusFilter = ref<string | null>(null)
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const colleges = ref<College[]>([])
const majors = ref<Major[]>([])

const userDrawerRef = ref<InstanceType<typeof UserDrawer> | null>(null)
const roleDrawerRef = ref<InstanceType<typeof RoleDrawer> | null>(null)
const rolePermDrawerRef = ref<InstanceType<typeof RolePermissionDrawer> | null>(null)
const userScopeDrawerRef = ref<InstanceType<typeof UserScopeDrawer> | null>(null)

const roleOptions = computed<SelectOption[]>(() =>
  roles.value.filter((item) => item.status === 1).map((item) => ({ label: `${item.name} ${item.code}`, value: item.id }))
)

const collegeOptions = computed<SelectOption[]>(() =>
  colleges.value.filter((item) => item.status === 1).map((item) => ({ label: item.name, value: item.id }))
)

const majorOptions = computed<SelectOption[]>(() =>
  majors.value.map((item) => ({
    label: `${item.internalMajorName} ${item.internalMajorCode}`,
    value: item.id
  }))
)

const canManageUsers = computed(() => userStore.hasPerm('system:user:manage'))
const canManageRoles = computed(() => userStore.hasPerm('system:role:manage'))
const canManagePerms = computed(() => userStore.hasPerm('system:perm:manage'))
const hasVisibleSection = computed(() => canManageUsers.value || canManageRoles.value || canManagePerms.value)
const summary = computed(() => ({
  users: users.value.length,
  enabledUsers: users.value.filter((item) => item.status === 'ENABLED').length,
  roles: roles.value.length,
  permissions: flattenPermissions(permissions.value).length
}))

const userColumns: DataTableColumns<User> = [
  { title: '用户名', key: 'username', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'realName', minWidth: 140, ellipsis: { tooltip: true } },
  { title: '类型', key: 'userType', width: 92, render: (row) => userTypeTag(row.userType) },
  { title: '学院', key: 'collegeName', minWidth: 150, ellipsis: { tooltip: true } },
  {
    title: '角色',
    key: 'roles',
    minWidth: 240,
    render: (row) =>
      h(NSpace, { size: 6 }, () =>
        row.roles.map((role) => h(StatusTag, { key: role.id, text: role.name }))
      )
  },
  { title: '状态', key: 'status', width: 92, render: (row) => userStatusTag(row.status) },
  {
    title: '操作',
    key: 'actions',
    width: 230,
    render: (row) =>
      canManageUsers.value
        ? renderTableActions([
              h(NButton, { size: 'small', quaternary: true, onClick: () => userDrawerRef.value?.open(row) }, { default: () => '编辑' }),
              h(NButton, { size: 'small', quaternary: true, onClick: () => userScopeDrawerRef.value?.open(row) }, { default: () => '范围' }),
              h(NPopconfirm, { onPositiveClick: () => resetPassword(row) }, {
                trigger: () => h(NButton, { size: 'small', quaternary: true }, { default: () => '重置密码' }),
                default: () => '重置后用户需首次改密。'
              }),
              h(NPopconfirm, { onPositiveClick: () => removeUser(row) }, {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
                default: () => '确认删除该用户？'
              })
            ])
        : null
  }
]

const roleColumns: DataTableColumns<Role> = [
  { title: '角色编码', key: 'code', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '角色名称', key: 'name', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '说明', key: 'description', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '排序', key: 'sort', width: 72, render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
  { title: '状态', key: 'status', width: 82, render: (row) => enabledTag(row.status) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      canManageRoles.value
        ? renderTableActions([
            h(NButton, { size: 'small', quaternary: true, onClick: () => roleDrawerRef.value?.open(row) }, { default: () => '编辑' }),
            h(NButton, { size: 'small', quaternary: true, onClick: () => rolePermDrawerRef.value?.open(row) }, { default: () => '授权' }),
            h(NPopconfirm, { onPositiveClick: () => removeRole(row) }, {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除该角色？'
            })
          ])
        : null
  }
]

const permissionColumns: DataTableColumns<Permission> = [
  { title: '功能名称', key: 'name', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 90, render: (row) => permissionTypeName(row.type) },
  { title: '路径', key: 'path', minWidth: 220, ellipsis: { tooltip: true }, render: (row) => row.path || '-' },
  { title: '排序', key: 'sort', width: 72, render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
  { title: '状态', key: 'status', width: 82, render: (row) => enabledTag(row.status) }
]

async function loadUsers() {
  if (!canManageUsers.value) {
    users.value = []
    return
  }
  userLoading.value = true
  try {
    const res = await listUsers({ keyword: keyword.value, status: statusFilter.value })
    users.value = res.data.records
  } catch (error) {
    showError(error, '用户加载失败')
  } finally {
    userLoading.value = false
  }
}

async function loadRoles() {
  if (!canManageRoles.value) {
    roles.value = []
    return
  }
  roleLoading.value = true
  try {
    const res = await listRoles(roleKeyword.value)
    roles.value = res.data
  } catch (error) {
    showError(error, '角色加载失败')
  } finally {
    roleLoading.value = false
  }
}

async function loadPermissions() {
  if (!canManagePerms.value) {
    permissions.value = []
    return
  }
  permissionLoading.value = true
  try {
    const res = await permissionTree()
    permissions.value = res.data
  } catch (error) {
    showError(error, '权限树加载失败')
  } finally {
    permissionLoading.value = false
  }
}

async function loadScopes() {
  if (!canManageUsers.value) {
    colleges.value = []
    majors.value = []
    return
  }
  const [collegeRes, majorRes] = await Promise.all([listColleges(null, 1), listMajors({ status: 1 })])
  colleges.value = collegeRes.data
  majors.value = majorRes.data
}

async function refreshAll() {
  const tasks: Promise<void>[] = []
  if (canManageUsers.value) {
    tasks.push(loadUsers(), loadScopes())
  }
  if (canManageRoles.value) tasks.push(loadRoles())
  if (canManagePerms.value) tasks.push(loadPermissions())
  await Promise.all(tasks)
}

async function resetPassword(row: User) {
  try {
    await resetUserPassword(row.id)
    message.success('密码已重置')
    await loadUsers()
  } catch (error) {
    showError(error, '密码重置失败')
  }
}

async function removeUser(row: User) {
  try {
    await deleteUser(row.id)
    message.success('用户已删除')
    await loadUsers()
  } catch (error) {
    showError(error, '用户删除失败')
  }
}

async function removeRole(row: Role) {
  try {
    await deleteRole(row.id)
    message.success('角色已删除')
    await loadRoles()
  } catch (error) {
    showError(error, '角色删除失败')
  }
}

function flattenPermissions(items: Permission[]): Permission[] {
  return items.flatMap((item) => [item, ...flattenPermissions(item.children || [])])
}

function enabledTag(status: number) {
  return h(StatusTag, { text: status === 1 ? '启用' : '停用' })
}

function userStatusTag(status: string) {
  return h(StatusTag, { text: statusName(status) })
}

function userTypeTag(type: string) {
  return h(StatusTag, { text: type === 'STUDENT' ? '学生' : '教职工' })
}

function permissionTypeName(type: string) {
  if (type === 'MENU') return '菜单'
  if (type === 'BUTTON') return '按钮'
  if (type === 'DATA') return '数据'
  return type || '-'
}

function statusName(status: string) {
  return status === 'ENABLED' ? '启用' : status === 'LOCKED' ? '锁定' : '停用'
}

function resetUserFilters() {
  keyword.value = ''
  statusFilter.value = null
  void loadUsers()
}

function resetRoleFilters() {
  roleKeyword.value = ''
  void loadRoles()
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(refreshAll)
</script>

<template>
  <PageContainer title="账号权限" description="账号、角色、权限与数据范围管理。">
    <template #actions>
      <n-button v-if="hasVisibleSection" secondary @click="refreshAll">刷新</n-button>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的账号权限分区" class="page-section" />

    <n-grid v-if="hasVisibleSection" :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-if="canManageUsers"><StatCard label="用户总数" :value="summary.users" /></n-gi>
      <n-gi v-if="canManageUsers"><StatCard label="启用用户" :value="summary.enabledUsers" tone="success" /></n-gi>
      <n-gi v-if="canManageRoles"><StatCard label="角色数" :value="summary.roles" tone="info" /></n-gi>
      <n-gi v-if="canManagePerms"><StatCard label="功能权限" :value="summary.permissions" tone="neutral" /></n-gi>
    </n-grid>

    <n-tabs v-if="hasVisibleSection" type="line" animated>
      <n-tab-pane v-if="canManageUsers" name="users" tab="用户">
        <div class="panel page-section">
          <FilterBar :loading="userLoading" @submit="loadUsers" @reset="resetUserFilters">
            <label class="filter-field">
              <span>用户</span>
              <n-input v-model:value="keyword" clearable placeholder="用户名 / 姓名 / 工号" style="width: 220px" />
            </label>
            <label class="filter-field">
              <span>状态</span>
              <n-select
                v-model:value="statusFilter"
                clearable
                placeholder="全部状态"
                style="width: 120px"
                :options="[
                  { label: '启用', value: 'ENABLED' },
                  { label: '锁定', value: 'LOCKED' },
                  { label: '停用', value: 'DISABLED' }
                ]"
              />
            </label>
          </FilterBar>
          <DataPanel
            title="用户列表"
            :columns="userColumns"
            :data="users"
            :total="users.length"
            :scroll-x="1040"
            :loading="userLoading"
            :max-height="620"
            empty-title="暂无用户"
            empty-description="当前筛选条件下没有用户记录。"
            @refresh="loadUsers"
          >
            <template #actions>
              <n-button v-if="canManageUsers" type="primary" size="small" @click="userDrawerRef?.open()">新增用户</n-button>
            </template>
          </DataPanel>
        </div>
      </n-tab-pane>

      <n-tab-pane v-if="canManageRoles" name="roles" tab="角色">
        <div class="panel page-section">
          <FilterBar :loading="roleLoading" @submit="loadRoles" @reset="resetRoleFilters">
            <label class="filter-field">
              <span>角色</span>
              <n-input v-model:value="roleKeyword" clearable placeholder="角色编码 / 名称" style="width: 220px" />
            </label>
          </FilterBar>
          <DataPanel
            title="角色列表"
            :columns="roleColumns"
            :data="roles"
            :total="roles.length"
            :loading="roleLoading"
            :max-height="620"
            :scroll-x="840"
            empty-title="暂无角色"
            empty-description="当前筛选条件下没有角色记录。"
            @refresh="loadRoles"
          >
            <template #actions>
              <n-button v-if="canManageRoles" type="primary" size="small" @click="roleDrawerRef?.open()">新增角色</n-button>
            </template>
          </DataPanel>
        </div>
      </n-tab-pane>

      <n-tab-pane v-if="canManagePerms" name="permissions" tab="权限">
        <div class="panel page-section">
          <n-alert v-if="canManagePerms" type="info" :bordered="false" class="page-section">
            功能权限为系统预置；本页用于查看权限树并在角色授权中配置。
          </n-alert>
          <DataPanel
            title="权限树"
            :columns="permissionColumns"
            :data="permissions"
            :total="summary.permissions"
            :scroll-x="760"
            :loading="permissionLoading"
            :max-height="620"
            default-expand-all
            empty-title="暂无权限"
            empty-description="当前没有可展示的功能权限。"
            @refresh="loadPermissions"
          />
        </div>
      </n-tab-pane>
    </n-tabs>

    <UserDrawer ref="userDrawerRef" :role-options="roleOptions" :college-options="collegeOptions" @saved="loadUsers" />
    <RoleDrawer ref="roleDrawerRef" @saved="loadRoles" />
    <RolePermissionDrawer ref="rolePermDrawerRef" :permissions="permissions" />
    <UserScopeDrawer ref="userScopeDrawerRef" :college-options="collegeOptions" :major-options="majorOptions" @saved="loadUsers" />
  </PageContainer>
</template>

<style scoped>
.panel {
  min-width: 0;
}
</style>
