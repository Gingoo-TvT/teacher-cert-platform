<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { createDictType, updateDictType, type DictType, type DictTypePayload } from '@/api/dict'

const emit = defineEmits<{
  saved: [typeCode: string]
}>()

const message = useMessage()

const formRef = ref<FormInst | null>(null)
const visible = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)

interface DictTypeFormState {
  typeCode: string
  typeName: string
  description: string
  sort: number
  status: number
}

const form = reactive<DictTypeFormState>({
  typeCode: '',
  typeName: '',
  description: '',
  sort: 0,
  status: 1
})

const rules: FormRules = {
  typeCode: [
    { required: true, message: '请输入字典类型编码', trigger: ['blur', 'input'] },
    { pattern: /^[A-Za-z0-9_]+$/, message: '仅支持英文、数字、下划线', trigger: ['blur', 'input'] }
  ],
  typeName: [{ required: true, message: '请输入字典类型名称', trigger: ['blur', 'input'] }]
}

function reset() {
  editingId.value = null
  form.typeCode = ''
  form.typeName = ''
  form.description = ''
  form.sort = 0
  form.status = 1
}

function open(row?: DictType) {
  reset()
  if (row) {
    editingId.value = row.id
    form.typeCode = row.typeCode
    form.typeName = row.typeName
    form.description = row.description || ''
    form.sort = row.sort || 0
    form.status = row.status
  }
  visible.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    try {
      await formRef.value?.validate()
    } catch {
      return
    }
    const payload: DictTypePayload = {
      // 后端与 MySQL/Redis/Caffeine 统一使用小写 canonical identity；前端同步收敛，避免保存后选择键漂移。
      typeCode: form.typeCode.trim().toLowerCase(),
      typeName: form.typeName.trim(),
      description: cleanOptional(form.description),
      sort: form.sort ?? 0,
      status: form.status ?? 1
    }
    if (editingId.value) await updateDictType(editingId.value, payload)
    else await createDictType(payload)
    message.success('字典类型已保存')
    visible.value = false
    emit('saved', payload.typeCode)
  } catch (error) {
    showError(error, '字典类型保存失败')
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
    <n-drawer-content :title="editingId ? '编辑字典类型' : '新增字典类型'" :closable="!saving">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" :disabled="saving">
        <div class="form-section-title">基本信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="类型编码" path="typeCode">
            <n-input v-model:value="form.typeCode" :disabled="!!editingId || saving" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="类型名称" path="typeName">
            <n-input v-model:value="form.typeName" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="排序" path="sort">
            <n-input-number v-model:value="form.sort" :min="0" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">补充说明</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="描述" path="description" span="1 480:2">
            <n-input v-model:value="form.description" type="textarea" maxlength="255" show-count />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
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
