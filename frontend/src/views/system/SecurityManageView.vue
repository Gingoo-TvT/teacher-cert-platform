<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type SelectOption,
  type TreeOption
} from 'naive-ui'
import { listColleges, listMajors, type College, type Major } from '@/api/organization'
import {
  assignRolePermissions,
  assignUserDataScope,
  assignUserRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  listRoles,
  listUsers,
  permissionTree,
  resetUserPassword,
  rolePermissions,
  updateRole,
  updateUser,
  type Permission,
  type Role,
  type RolePayload,
  type RolePermissionItem,
  type User,
  type UserPayload
} from '@/api/security'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const userLoading = ref(false)
const roleLoading = ref(false)
const permissionLoading = ref(false)
const saving = ref(false)
const userDrawerVisible = ref(false)
const roleDrawerVisible = ref(false)
const rolePermDrawerVisible = ref(false)
const userScopeDrawerVisible = ref(false)
const userFormRef = ref<FormInst | null>(null)
const roleFormRef = ref<FormInst | null>(null)
const editingUserId = ref<string | null>(null)
const editingRoleId = ref<string | null>(null)
const currentScopeUser = ref<User | null>(null)
const currentPermissionRole = ref<Role | null>(null)
const keyword = ref('')
const roleKeyword = ref('')
const statusFilter = ref<string | null>(null)
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const colleges = ref<College[]>([])
const majors = ref<Major[]>([])
const selectedPermissionIds = ref<string[]>([])
const scopeCollegeIds = ref<string[]>([])
const scopeMajorIds = ref<string[]>([])

const userForm = reactive<UserFormState>({
  username: '',
  realName: '',
  workNo: '',
  email: '',
  phone: '',
  status: 'ENABLED',
  userType: 'STAFF',
  collegeId: null,
  studentId: '',
  roleIds: []
})

const roleForm = reactive<RoleFormState>({
  code: '',
  name: '',
  description: '',
  sort: 0,
  status: 1
})

interface UserFormState {
  username: string
  realName: string
  workNo: string
  email: string
  phone: string
  status: string
  userType: string
  collegeId: string | null
  studentId: string
  roleIds: string[]
}

interface RoleFormState {
  code: string
  name: string
  description: string
  sort: number
  status: number
}

const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: ['blur', 'input'] }],
  realName: [{ required: true, message: '请输入姓名', trigger: ['blur', 'input'] }],
  status: [{ required: true, message: '请选择状态', trigger: ['change'] }],
  userType: [{ required: true, message: '请选择用户类型', trigger: ['change'] }],
  roleIds: [{ type: 'array', required: true, min: 1, message: '至少选择一个角色', trigger: ['change'] }]
}

const roleRules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入角色名称', trigger: ['blur', 'input'] }]
}

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

const permissionOptions = computed<TreeOption[]>(() => permissions.value.map(toTreeOption))
const canManageUsers = computed(() => userStore.hasPerm('system:user:manage'))
const canManageRoles = computed(() => userStore.hasPerm('system:role:manage'))
const canManagePerms = computed(() => userStore.hasPerm('system:perm:manage'))
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
    width: 292,
    render: (row) =>
      h(NSpace, { size: 6 }, () =>
        canManageUsers.value
          ? [
              h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openUserDrawer(row) }, { default: () => '编辑' }),
              h(NButton, { size: 'small', quaternary: true, onClick: () => openScopeDrawer(row) }, { default: () => '范围' }),
              h(
                NPopconfirm,
                { onPositiveClick: () => resetPassword(row) },
                {
                  trigger: () => h(NButton, { size: 'small', quaternary: true }, { default: () => '重置密码' }),
                  default: () => '重置后用户需首次改密。'
                }
              ),
              h(
                NPopconfirm,
                { onPositiveClick: () => removeUser(row) },
                {
                  trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
                  default: () => '确认删除该用户？'
                }
              )
            ]
          : []
      )
  }
]

const roleColumns: DataTableColumns<Role> = [
  { title: '角色编码', key: 'code', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '角色名称', key: 'name', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '说明', key: 'description', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '排序', key: 'sort', width: 72 },
  { title: '状态', key: 'status', width: 82, render: (row) => enabledTag(row.status) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(NSpace, { size: 6 }, () =>
        canManageRoles.value
          ? [
              h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openRoleDrawer(row) }, { default: () => '编辑' }),
              h(NButton, { size: 'small', quaternary: true, onClick: () => openRolePermissionDrawer(row) }, { default: () => '权限' }),
              h(
                NPopconfirm,
                { onPositiveClick: () => removeRole(row) },
                {
                  trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
                  default: () => '确认删除该角色？'
                }
              )
            ]
          : []
      )
  }
]

const permissionColumns: DataTableColumns<Permission> = [
  { title: '权限编码', key: 'code', minWidth: 230, ellipsis: { tooltip: true } },
  { title: '权限名称', key: 'name', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 90 },
  { title: '排序', key: 'sort', width: 72 },
  { title: '状态', key: 'status', width: 82, render: (row) => enabledTag(row.status) }
]

async function loadUsers() {
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
  const [collegeRes, majorRes] = await Promise.all([listColleges(null, 1), listMajors({ status: 1 })])
  colleges.value = collegeRes.data
  majors.value = majorRes.data
}

async function refreshAll() {
  await Promise.all([loadUsers(), loadRoles(), loadPermissions(), loadScopes()])
}

function openUserDrawer(row?: User) {
  editingUserId.value = row?.id || null
  userForm.username = row?.username || ''
  userForm.realName = row?.realName || ''
  userForm.workNo = row?.workNo || ''
  userForm.email = row?.email || ''
  userForm.phone = row?.phone || ''
  userForm.status = row?.status || 'ENABLED'
  userForm.userType = row?.userType || 'STAFF'
  userForm.collegeId = row?.collegeId || null
  userForm.studentId = row?.studentId || ''
  userForm.roleIds = row?.roles.map((role) => role.id) || []
  userDrawerVisible.value = true
}

function openRoleDrawer(row?: Role) {
  editingRoleId.value = row?.id || null
  roleForm.code = row?.code || ''
  roleForm.name = row?.name || ''
  roleForm.description = row?.description || ''
  roleForm.sort = row?.sort || 0
  roleForm.status = row?.status ?? 1
  roleDrawerVisible.value = true
}

async function openRolePermissionDrawer(row: Role) {
  currentPermissionRole.value = row
  selectedPermissionIds.value = []
  rolePermDrawerVisible.value = true
  try {
    const res = await rolePermissions(row.id)
    selectedPermissionIds.value = flattenPermissions(res.data).map((item) => item.id)
  } catch (error) {
    showError(error, '角色权限加载失败')
  }
}

function openScopeDrawer(row: User) {
  currentScopeUser.value = row
  scopeCollegeIds.value = [...row.dataScopeCollegeIds]
  scopeMajorIds.value = [...row.dataScopeMajorIds]
  userScopeDrawerVisible.value = true
}

async function saveUser() {
  await userFormRef.value?.validate()
  saving.value = true
  try {
    const payload: UserPayload = {
      username: userForm.username.trim(),
      realName: userForm.realName.trim(),
      workNo: cleanOptional(userForm.workNo),
      email: cleanOptional(userForm.email),
      phone: cleanOptional(userForm.phone),
      status: userForm.status,
      userType: userForm.userType,
      collegeId: userForm.collegeId,
      studentId: cleanOptional(userForm.studentId),
      roleIds: userForm.roleIds
    }
    if (editingUserId.value) {
      await updateUser(editingUserId.value, payload)
      await assignUserRoles(editingUserId.value, payload.roleIds)
    } else {
      await createUser(payload)
    }
    message.success('用户已保存')
    userDrawerVisible.value = false
    await loadUsers()
  } catch (error) {
    showError(error, '用户保存失败')
  } finally {
    saving.value = false
  }
}

async function saveRole() {
  await roleFormRef.value?.validate()
  saving.value = true
  try {
    const payload: RolePayload = {
      code: roleForm.code.trim(),
      name: roleForm.name.trim(),
      description: cleanOptional(roleForm.description),
      sort: roleForm.sort ?? 0,
      status: roleForm.status ?? 1
    }
    if (editingRoleId.value) {
      await updateRole(editingRoleId.value, payload)
    } else {
      await createRole(payload)
    }
    message.success('角色已保存')
    roleDrawerVisible.value = false
    await loadRoles()
  } catch (error) {
    showError(error, '角色保存失败')
  } finally {
    saving.value = false
  }
}

async function saveRolePermissions() {
  if (!currentPermissionRole.value) return
  saving.value = true
  try {
    const payload: RolePermissionItem[] = selectedPermissionIds.value.map((permissionId) => ({
      permissionId,
      scopeType: defaultScopeFor(permissionId)
    }))
    await assignRolePermissions(currentPermissionRole.value.id, payload)
    message.success('角色权限已保存')
    rolePermDrawerVisible.value = false
  } catch (error) {
    showError(error, '角色权限保存失败')
  } finally {
    saving.value = false
  }
}

async function saveUserScope() {
  if (!currentScopeUser.value) return
  saving.value = true
  try {
    await assignUserDataScope(currentScopeUser.value.id, {
      collegeIds: scopeCollegeIds.value,
      majorIds: scopeMajorIds.value
    })
    message.success('数据范围已保存')
    userScopeDrawerVisible.value = false
    await loadUsers()
  } catch (error) {
    showError(error, '数据范围保存失败')
  } finally {
    saving.value = false
  }
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

function toTreeOption(permission: Permission): TreeOption {
  return {
    key: permission.id,
    label: `${permission.name} ${permission.code}`,
    children: permission.children?.map(toTreeOption)
  }
}

function flattenPermissions(items: Permission[]): Permission[] {
  return items.flatMap((item) => [item, ...flattenPermissions(item.children || [])])
}

function defaultScopeFor(permissionId: string) {
  const permission = flattenPermissions(permissions.value).find((item) => item.id === permissionId)
  const code = permission?.code || ''
  if (code.startsWith('system:')) return 'SYSTEM'
  if (code === 'dict:view' || code === 'notice:view') return 'LOGIN_ALL'
  return 'SCHOOL'
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

function statusName(status: string) {
  return status === 'ENABLED' ? '启用' : status === 'LOCKED' ? '锁定' : '停用'
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(refreshAll)
</script>

<template>
  <PageContainer title="账号权限" description="系统管理员拥有账号、角色、权限矩阵与数据范围维护能力；其他角色只按权限查看可访问内容。">
    <template #actions>
      <n-button secondary @click="refreshAll">刷新</n-button>
    </template>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="用户总数" :value="summary.users" /></n-gi>
      <n-gi><StatCard label="启用用户" :value="summary.enabledUsers" color="#18a058" /></n-gi>
      <n-gi><StatCard label="角色数" :value="summary.roles" color="#2080f0" /></n-gi>
      <n-gi><StatCard label="权限点" :value="summary.permissions" color="#4b5563" /></n-gi>
    </n-grid>

    <n-tabs type="line" animated>
      <n-tab-pane name="users" tab="用户">
        <section class="panel page-section">
          <div class="panel-toolbar">
            <n-space class="filters" :size="10">
              <n-input v-model:value="keyword" clearable placeholder="用户名 / 姓名 / 工号" style="width: 220px" />
              <n-select
                v-model:value="statusFilter"
                clearable
                placeholder="状态"
                style="width: 120px"
                :options="[
                  { label: '启用', value: 'ENABLED' },
                  { label: '锁定', value: 'LOCKED' },
                  { label: '停用', value: 'DISABLED' }
                ]"
              />
              <n-button secondary @click="loadUsers">查询</n-button>
              <n-button v-if="canManageUsers" type="primary" @click="openUserDrawer()">新增用户</n-button>
            </n-space>
          </div>
          <n-data-table
            :columns="userColumns"
            :data="users"
            :loading="userLoading"
            :row-key="(row: User) => row.id"
            size="small"
            striped
            :max-height="620"
          />
        </section>
      </n-tab-pane>

      <n-tab-pane name="roles" tab="角色">
        <section class="panel page-section">
          <div class="panel-toolbar">
            <n-space class="filters" :size="10">
              <n-input v-model:value="roleKeyword" clearable placeholder="角色编码 / 名称" style="width: 220px" />
              <n-button secondary @click="loadRoles">查询</n-button>
              <n-button v-if="canManageRoles" type="primary" @click="openRoleDrawer()">新增角色</n-button>
            </n-space>
          </div>
          <n-data-table
            :columns="roleColumns"
            :data="roles"
            :loading="roleLoading"
            :row-key="(row: Role) => row.id"
            size="small"
            striped
            :max-height="620"
          />
        </section>
      </n-tab-pane>

      <n-tab-pane name="permissions" tab="权限">
        <section class="panel page-section">
          <n-alert v-if="canManagePerms" type="info" :bordered="false" class="page-section">
            权限点由后端迁移维护，本页用于查看权限树并在角色授权中配置矩阵。
          </n-alert>
          <n-data-table
            :columns="permissionColumns"
            :data="permissions"
            :loading="permissionLoading"
            :row-key="(row: Permission) => row.id"
            size="small"
            striped
            :max-height="620"
            default-expand-all
          />
        </section>
      </n-tab-pane>
    </n-tabs>

    <n-drawer v-model:show="userDrawerVisible" :width="520" placement="right">
      <n-drawer-content :title="editingUserId ? '编辑用户' : '新增用户'">
        <n-form ref="userFormRef" :model="userForm" :rules="userRules" label-placement="top">
          <div class="form-grid">
            <n-form-item label="用户名" path="username">
              <n-input v-model:value="userForm.username" maxlength="64" show-count />
            </n-form-item>
            <n-form-item label="姓名" path="realName">
              <n-input v-model:value="userForm.realName" maxlength="128" show-count />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="工号" path="workNo">
              <n-input v-model:value="userForm.workNo" maxlength="64" show-count />
            </n-form-item>
            <n-form-item label="学生ID" path="studentId">
              <n-input v-model:value="userForm.studentId" />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="邮箱" path="email">
              <n-input v-model:value="userForm.email" maxlength="128" show-count />
            </n-form-item>
            <n-form-item label="手机号" path="phone">
              <n-input v-model:value="userForm.phone" maxlength="32" show-count />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="用户类型" path="userType">
              <n-select
                v-model:value="userForm.userType"
                :options="[
                  { label: '教职工', value: 'STAFF' },
                  { label: '学生', value: 'STUDENT' }
                ]"
              />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-select
                v-model:value="userForm.status"
                :options="[
                  { label: '启用', value: 'ENABLED' },
                  { label: '锁定', value: 'LOCKED' },
                  { label: '停用', value: 'DISABLED' }
                ]"
              />
            </n-form-item>
          </div>
          <n-form-item label="所属学院" path="collegeId">
            <n-select v-model:value="userForm.collegeId" :options="collegeOptions" clearable filterable />
          </n-form-item>
          <n-form-item label="角色" path="roleIds">
            <n-select v-model:value="userForm.roleIds" :options="roleOptions" multiple filterable />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="userDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveUser">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="roleDrawerVisible" :width="460" placement="right">
      <n-drawer-content :title="editingRoleId ? '编辑角色' : '新增角色'">
        <n-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-placement="top">
          <n-form-item label="角色编码" path="code">
            <n-input v-model:value="roleForm.code" maxlength="64" show-count />
          </n-form-item>
          <n-form-item label="角色名称" path="name">
            <n-input v-model:value="roleForm.name" maxlength="128" show-count />
          </n-form-item>
          <n-form-item label="说明" path="description">
            <n-input v-model:value="roleForm.description" type="textarea" maxlength="255" show-count />
          </n-form-item>
          <div class="form-grid">
            <n-form-item label="排序" path="sort">
              <n-input-number v-model:value="roleForm.sort" :min="0" />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-switch v-model:value="roleForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="roleDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveRole">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="rolePermDrawerVisible" :width="560" placement="right">
      <n-drawer-content :title="currentPermissionRole ? `角色权限：${currentPermissionRole.name}` : '角色权限'">
        <n-tree
          v-model:checked-keys="selectedPermissionIds"
          :data="permissionOptions"
          checkable
          cascade
          block-line
          :default-expand-all="true"
        />
        <template #footer>
          <n-space justify="end">
            <n-button @click="rolePermDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveRolePermissions">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="userScopeDrawerVisible" :width="520" placement="right">
      <n-drawer-content :title="currentScopeUser ? `数据范围：${currentScopeUser.realName}` : '数据范围'">
        <n-form label-placement="top">
          <n-form-item label="授权学院">
            <n-select v-model:value="scopeCollegeIds" :options="collegeOptions" multiple filterable />
          </n-form-item>
          <n-form-item label="授权专业">
            <n-select v-model:value="scopeMajorIds" :options="majorOptions" multiple filterable />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="userScopeDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveUserScope">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </PageContainer>
</template>

<style scoped>
.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel {
  min-width: 0;
}

.panel-toolbar {
  margin-bottom: 12px;
}

.filters {
  flex-wrap: wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 1120px) {
  .panel-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
