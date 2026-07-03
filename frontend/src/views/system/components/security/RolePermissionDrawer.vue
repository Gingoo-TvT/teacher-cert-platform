<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage, type TreeOption } from 'naive-ui'
import {
  assignRolePermissions,
  rolePermissions,
  type Permission,
  type Role,
  type RolePermissionItem
} from '@/api/security'

const props = defineProps<{
  permissions: Permission[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const currentPermissionRole = ref<Role | null>(null)
const selectedPermissionIds = ref<string[]>([])
// 该角色已授权的每权限真实数据范围（保存时保留，避免用猜测值覆盖全部权限的范围）
const scopeByPermission = ref<Record<string, string>>({})

const permissionOptions = computed<TreeOption[]>(() => props.permissions.map(toTreeOption))

async function open(row: Role) {
  currentPermissionRole.value = row
  selectedPermissionIds.value = []
  visible.value = true
  try {
    const res = await rolePermissions(row.id)
    const granted = flattenPermissions(res.data)
    selectedPermissionIds.value = granted.map((item) => item.id)
    const map: Record<string, string> = {}
    for (const item of granted) {
      if (item.scopeType) map[item.id] = item.scopeType
    }
    scopeByPermission.value = map
  } catch (error) {
    showError(error, '角色权限加载失败')
  }
}

async function saveRolePermissions() {
  if (!currentPermissionRole.value) return
  saving.value = true
  try {
    const payload: RolePermissionItem[] = selectedPermissionIds.value.map((permissionId) => ({
      permissionId,
      scopeType: scopeFor(permissionId)
    }))
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

function scopeFor(permissionId: string) {
  // 优先保留该角色原有的授权范围；新增权限用权限自身定义的范围；再兜底 SCHOOL。
  const existing = scopeByPermission.value[permissionId]
  if (existing) return existing
  const permission = flattenPermissions(props.permissions).find((item) => item.id === permissionId)
  return permission?.scopeType || 'SCHOOL'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="visible" :width="560" placement="right">
    <n-drawer-content :title="currentPermissionRole ? `角色权限：${currentPermissionRole.name}` : '角色权限'">
      <div class="form-section-title">功能授权</div>
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
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveRolePermissions">保存</n-button>
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
</style>
