<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage, type SelectOption, type TreeOption } from 'naive-ui'
import {
  assignRolePermissions,
  isRolePermissionScopeType,
  rolePermissions,
  type Permission,
  type Role,
  type RolePermissionItem,
  type RolePermissionScopeType
} from '@/api/security'

const props = defineProps<{
  permissions: Permission[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const currentPermissionRole = ref<Role | null>(null)
const selectedPermissionIds = ref<string[]>([])
const scopeByPermission = ref<Partial<Record<string, RolePermissionScopeType>>>({})

const scopeOptions: SelectOption[] = [
  { label: '系统（SYSTEM）', value: 'SYSTEM' },
  { label: '全校（SCHOOL）', value: 'SCHOOL' },
  { label: '全体登录用户（LOGIN_ALL）', value: 'LOGIN_ALL' },
  { label: '学院（COLLEGE）', value: 'COLLEGE' },
  { label: '本人（SELF）', value: 'SELF' },
  { label: '已指派（ASSIGNED）', value: 'ASSIGNED' },
  { label: '无数据范围（NONE）', value: 'NONE' }
]

const permissionOptions = computed<TreeOption[]>(() => props.permissions.map(toTreeOption))
const permissionById = computed(() => new Map(
  flattenPermissions(props.permissions).map((permission) => [permission.id, permission])
))
const selectedPermissions = computed(() => selectedPermissionIds.value
  .map((permissionId) => permissionById.value.get(permissionId))
  .filter((permission): permission is Permission => permission !== undefined))
const hasIncompleteScope = computed(() => selectedPermissionIds.value.some(
  (permissionId) => !isRolePermissionScopeType(scopeByPermission.value[permissionId])
))

async function open(row: Role) {
  if (loading.value || saving.value) return
  loaded.value = false
  currentPermissionRole.value = row
  selectedPermissionIds.value = []
  scopeByPermission.value = {}
  visible.value = true
  loading.value = true
  try {
    const res = await rolePermissions(row.id)
    const granted = flattenPermissions(res.data)
    const map: Partial<Record<string, RolePermissionScopeType>> = {}
    for (const item of granted) {
      if (isRolePermissionScopeType(item.scopeType)) map[item.id] = item.scopeType
    }
    scopeByPermission.value = map
    selectedPermissionIds.value = granted.map((item) => item.id)
    loaded.value = true
  } catch (error) {
    showError(error, '角色权限加载失败')
  } finally {
    loading.value = false
  }
}

async function saveRolePermissions() {
  if (saving.value || loading.value || !loaded.value) return
  if (!currentPermissionRole.value) return
  const payload: RolePermissionItem[] = []
  for (const permissionId of selectedPermissionIds.value) {
    if (!permissionById.value.has(permissionId)) {
      message.error('权限数据已变化，请刷新后重试')
      return
    }
    const scopeType = scopeByPermission.value[permissionId]
    if (!isRolePermissionScopeType(scopeType)) {
      message.error('请为每项已选权限设置数据范围')
      return
    }
    payload.push({ permissionId, scopeType })
  }

  saving.value = true
  try {
    await assignRolePermissions(currentPermissionRole.value.id, payload)
    message.success('角色权限已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '角色权限保存失败')
  } finally {
    saving.value = false
  }
}

function toTreeOption(permission: Permission): TreeOption {
  return {
    key: permission.id,
    label: permission.name,
    children: permission.children?.map(toTreeOption)
  }
}

function flattenPermissions(items: Permission[]): Permission[] {
  return items.flatMap((item) => [item, ...flattenPermissions(item.children || [])])
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-drawer
    v-model:show="visible"
    width="min(var(--overlay-wide), var(--overlay-drawer-max))"
    placement="right"
    :mask-closable="!loading && !saving"
    :close-on-esc="!loading && !saving"
  >
    <n-drawer-content
      :title="currentPermissionRole ? `角色权限：${currentPermissionRole.name}` : '角色权限'"
      :closable="!loading && !saving"
    >
      <n-spin :show="loading">
        <div class="form-section-title">功能授权</div>
        <n-tree
          v-model:checked-keys="selectedPermissionIds"
          :data="permissionOptions"
          :disabled="loading || saving"
          checkable
          cascade
          block-line
          :default-expand-all="true"
        />

        <section v-if="selectedPermissions.length" class="scope-section">
          <div class="form-section-title">数据范围</div>
          <div class="scope-list">
            <div v-for="permission in selectedPermissions" :key="permission.id" class="scope-row">
              <div class="scope-permission">
                <span class="scope-name">{{ permission.name }}</span>
                <span class="scope-code">{{ permission.code }}</span>
              </div>
              <n-select
                v-model:value="scopeByPermission[permission.id]"
                :options="scopeOptions"
                placeholder="请选择数据范围"
                aria-label="数据范围"
                :disabled="loading || saving"
              />
            </div>
          </div>
        </section>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="loading || saving" @click="visible = false">取消</n-button>
          <n-button
            type="primary"
            :loading="saving"
            :disabled="loading || !loaded || hasIncompleteScope"
            @click="saveRolePermissions"
          >保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.scope-section {
  margin-top: var(--space-5);
}

.scope-list {
  border-top: 1px solid var(--border);
}

.scope-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border);
}

.scope-permission {
  min-width: 0;
}

.scope-name,
.scope-code {
  display: block;
  overflow-wrap: anywhere;
}

.scope-name {
  color: var(--text);
  font-weight: 500;
}

.scope-code {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 600px) {
  .scope-row {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-2);
  }
}
</style>
