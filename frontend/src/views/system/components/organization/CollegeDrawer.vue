<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { createCollege, updateCollege, type College, type CollegePayload } from '@/api/organization'

const emit = defineEmits<{
  saved: [createdId: string | null]
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const collegeFormRef = ref<FormInst | null>(null)
const editingCollegeId = ref<string | null>(null)

interface CollegeFormState {
  code: string
  name: string
  sort: number
  status: number
}

const collegeForm = reactive<CollegeFormState>({ code: '', name: '', sort: 0, status: 1 })

const collegeRules: FormRules = {
  code: [{ required: true, message: '请输入学院编码', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入学院名称', trigger: ['blur', 'input'] }]
}

function open(row?: College) {
  editingCollegeId.value = row?.id || null
  collegeForm.code = row?.code || ''
  collegeForm.name = row?.name || ''
  collegeForm.sort = row?.sort || 0
  collegeForm.status = row?.status ?? 1
  visible.value = true
}

async function saveCollege() {
  await collegeFormRef.value?.validate()
  saving.value = true
  try {
    const payload: CollegePayload = {
      code: collegeForm.code.trim(),
      name: collegeForm.name.trim(),
      sort: collegeForm.sort ?? 0,
      status: collegeForm.status ?? 1
    }
    let createdId: string | null = null
    if (editingCollegeId.value) await updateCollege(editingCollegeId.value, payload)
    else {
      const res = await createCollege(payload)
      createdId = res.data
    }
    message.success('学院已保存')
    visible.value = false
    emit('saved', createdId)
  } catch (error) {
    showError(error, '学院保存失败')
  } finally {
    saving.value = false
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="visible" :width="560" placement="right">
    <n-drawer-content :title="editingCollegeId ? '编辑学院' : '新增学院'">
      <n-form ref="collegeFormRef" :model="collegeForm" :rules="collegeRules" label-placement="top">
        <div class="form-section-title">基本信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学院编码" path="code">
            <n-input v-model:value="collegeForm.code" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="学院名称" path="name">
            <n-input v-model:value="collegeForm.name" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="排序" path="sort">
            <n-input-number v-model:value="collegeForm.sort" :min="0" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="collegeForm.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveCollege">保存</n-button>
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
