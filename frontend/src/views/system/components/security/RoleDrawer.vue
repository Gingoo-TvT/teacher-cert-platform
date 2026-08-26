<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { createRole, updateRole, type Role, type RolePayload } from '@/api/security'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const roleFormRef = ref<FormInst | null>(null)
const editingRoleId = ref<string | null>(null)

interface RoleFormState {
  code: string
  name: string
  description: string
  sort: number
  status: number
}

const roleForm = reactive<RoleFormState>({
  code: '',
  name: '',
  description: '',
  sort: 0,
  status: 1
})

const roleRules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入角色名称', trigger: ['blur', 'input'] }]
}

function open(row?: Role) {
  editingRoleId.value = row?.id || null
  roleForm.code = row?.code || ''
  roleForm.name = row?.name || ''
  roleForm.description = row?.description || ''
  roleForm.sort = row?.sort || 0
  roleForm.status = row?.status ?? 1
  visible.value = true
}

async function saveRole() {
  if (saving.value) return
  saving.value = true
  try {
    try {
      await roleFormRef.value?.validate()
    } catch {
      return
    }
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
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '角色保存失败')
  } finally {
    saving.value = false
  }
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
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
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    placement="right"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content :title="editingRoleId ? '编辑角色' : '新增角色'" :closable="!saving">
      <n-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-placement="top" :disabled="saving">
        <div class="form-section-title">基本信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="角色编码" path="code">
            <n-input v-model:value="roleForm.code" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="角色名称" path="name">
            <n-input v-model:value="roleForm.name" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="说明" path="description" span="1 480:2">
            <n-input v-model:value="roleForm.description" type="textarea" maxlength="255" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="排序" path="sort">
            <n-input-number v-model:value="roleForm.sort" :min="0" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="roleForm.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveRole">保存</n-button>
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
