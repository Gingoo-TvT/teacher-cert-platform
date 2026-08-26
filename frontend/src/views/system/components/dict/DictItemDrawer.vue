<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { createDictItem, updateDictItem, type DictItem, type DictItemPayload } from '@/api/dict'

const props = defineProps<{
  typeCode: string
}>()

const emit = defineEmits<{
  saved: [typeCode: string]
}>()

const message = useMessage()

const formRef = ref<FormInst | null>(null)
const visible = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)

interface DictItemFormState {
  typeCode: string
  itemCode: string
  itemValue: string
  parentCode: string
  sort: number
  status: number
  yearVersion: string
  extJson: string
}

const form = reactive<DictItemFormState>({
  typeCode: '',
  itemCode: '',
  itemValue: '',
  parentCode: '',
  sort: 0,
  status: 1,
  yearVersion: 'GLOBAL',
  extJson: ''
})

const rules: FormRules = {
  itemCode: [{ required: true, message: '请输入字典项编码', trigger: ['blur', 'input'] }],
  itemValue: [{ required: true, message: '请输入字典项值', trigger: ['blur', 'input'] }],
  yearVersion: [{ required: true, message: '请输入年度版本', trigger: ['blur', 'input'] }],
  extJson: [
    {
      validator: (_rule, value: string | null | undefined) => {
        if (!value?.trim()) return true
        try {
          JSON.parse(value)
          return true
        } catch {
          return new Error('扩展 JSON 格式不正确')
        }
      },
      trigger: ['blur']
    }
  ]
}

function reset() {
  editingId.value = null
  form.typeCode = normalizeTypeCode(props.typeCode)
  form.itemCode = ''
  form.itemValue = ''
  form.parentCode = ''
  form.sort = 0
  form.status = 1
  form.yearVersion = 'GLOBAL'
  form.extJson = ''
}

function open(row?: DictItem) {
  reset()
  if (row) {
    editingId.value = row.id
    form.typeCode = normalizeTypeCode(row.typeCode)
    form.itemCode = row.itemCode
    form.itemValue = row.itemValue
    form.parentCode = row.parentCode || ''
    form.sort = row.sort || 0
    form.status = row.status
    form.yearVersion = row.yearVersion || 'GLOBAL'
    form.extJson = row.extJson || ''
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
    const payload: DictItemPayload = {
      typeCode: normalizeTypeCode(form.typeCode),
      itemCode: form.itemCode.trim(),
      itemValue: form.itemValue.trim(),
      parentCode: cleanOptional(form.parentCode),
      sort: form.sort ?? 0,
      status: form.status ?? 1,
      yearVersion: cleanOptional(form.yearVersion) || 'GLOBAL',
      extJson: cleanOptional(form.extJson)
    }
    if (editingId.value) await updateDictItem(editingId.value, payload)
    else await createDictItem(payload)
    message.success('字典项已保存')
    visible.value = false
    emit('saved', payload.typeCode)
  } catch (error) {
    showError(error, '字典项保存失败')
  } finally {
    saving.value = false
  }
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
}

function normalizeTypeCode(value: string) {
  return value.trim().toLowerCase()
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
    <n-drawer-content :title="editingId ? '编辑字典项' : '新增字典项'" :closable="!saving">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" :disabled="saving">
        <div class="form-section-title">基本信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="类型编码" path="typeCode">
            <n-input v-model:value="form.typeCode" disabled />
          </n-form-item-gi>
          <n-form-item-gi label="项编码" path="itemCode">
            <n-input v-model:value="form.itemCode" :disabled="!!editingId || saving" maxlength="128" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="项值" path="itemValue" span="1 480:2">
            <n-input v-model:value="form.itemValue" maxlength="255" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="父级编码" path="parentCode">
            <n-input v-model:value="form.parentCode" maxlength="128" clearable />
          </n-form-item-gi>
          <n-form-item-gi label="年度版本" path="yearVersion">
            <n-input v-model:value="form.yearVersion" maxlength="16" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="排序" path="sort">
            <n-input-number v-model:value="form.sort" :min="0" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">扩展信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="扩展 JSON" path="extJson" span="1 480:2">
            <n-input v-model:value="form.extJson" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" />
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
