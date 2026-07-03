<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import { assignUserRoles, createUser, updateUser, type User, type UserPayload } from '@/api/security'

defineProps<{
  roleOptions: SelectOption[]
  collegeOptions: SelectOption[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const userFormRef = ref<FormInst | null>(null)
const editingUserId = ref<string | null>(null)

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

const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: ['blur', 'input'] }],
  realName: [{ required: true, message: '请输入姓名', trigger: ['blur', 'input'] }],
  status: [{ required: true, message: '请选择状态', trigger: ['change'] }],
  userType: [{ required: true, message: '请选择用户类型', trigger: ['change'] }],
  roleIds: [{ type: 'array', required: true, min: 1, message: '至少选择一个角色', trigger: ['change'] }]
}

function open(row?: User) {
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
  visible.value = true
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
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '用户保存失败')
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
  <n-drawer v-model:show="visible" :width="560" placement="right">
    <n-drawer-content :title="editingUserId ? '编辑用户' : '新增用户'">
      <n-form ref="userFormRef" :model="userForm" :rules="userRules" label-placement="top">
        <div class="form-section-title">基本信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="用户名" path="username">
            <n-input v-model:value="userForm.username" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="姓名" path="realName">
            <n-input v-model:value="userForm.realName" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="工号" path="workNo">
            <n-input v-model:value="userForm.workNo" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="学生ID" path="studentId">
            <n-input v-model:value="userForm.studentId" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">联系信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="邮箱" path="email">
            <n-input v-model:value="userForm.email" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="手机号" path="phone">
            <n-input v-model:value="userForm.phone" maxlength="32" show-count />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">账号设置</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="用户类型" path="userType">
            <n-select
              v-model:value="userForm.userType"
              :options="[
                { label: '教职工', value: 'STAFF' },
                { label: '学生', value: 'STUDENT' }
              ]"
            />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-select
              v-model:value="userForm.status"
              :options="[
                { label: '启用', value: 'ENABLED' },
                { label: '锁定', value: 'LOCKED' },
                { label: '停用', value: 'DISABLED' }
              ]"
            />
          </n-form-item-gi>
          <n-form-item-gi label="所属学院" path="collegeId" :span="2">
            <n-select v-model:value="userForm.collegeId" :options="collegeOptions" clearable filterable />
          </n-form-item-gi>
          <n-form-item-gi label="角色" path="roleIds" :span="2">
            <n-select v-model:value="userForm.roleIds" :options="roleOptions" multiple filterable />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveUser">保存</n-button>
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
