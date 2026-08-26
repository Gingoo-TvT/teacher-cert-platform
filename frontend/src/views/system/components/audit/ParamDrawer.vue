<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { updateSystemParam, type SysParam } from '@/api/systemAudit'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const paramDrawerVisible = ref(false)
const saving = ref(false)
const paramFormRef = ref<FormInst | null>(null)
const editingParam = ref<SysParam | null>(null)

const paramForm = reactive({
  paramValue: '',
  description: ''
})

const paramRules: FormRules = {
  paramValue: [{ required: true, message: '请输入参数值', trigger: ['blur', 'input'] }]
}

function open(row: SysParam) {
  editingParam.value = row
  paramForm.paramValue = row.paramValue || ''
  paramForm.description = row.description || ''
  paramDrawerVisible.value = true
}

async function saveParam() {
  if (saving.value) return
  if (!editingParam.value) return
  saving.value = true
  try {
    try {
      await paramFormRef.value?.validate()
    } catch {
      return
    }
    await updateSystemParam(editingParam.value.id, {
      paramValue: paramForm.paramValue.trim(),
      description: paramForm.description.trim() || null
    })
    message.success('参数已更新')
    paramDrawerVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '参数保存失败')
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
  <n-drawer
    v-model:show="paramDrawerVisible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    placement="right"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content :title="editingParam ? editingParam.paramKey : '编辑参数'" :closable="!saving">
      <n-form ref="paramFormRef" :model="paramForm" :rules="paramRules" label-placement="top" :disabled="saving">
        <div class="form-section-title">参数内容</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="参数值" path="paramValue" span="1 480:2">
            <n-input v-model:value="paramForm.paramValue" maxlength="512" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="说明" span="1 480:2">
            <n-input v-model:value="paramForm.description" type="textarea" maxlength="255" show-count />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="paramDrawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveParam">保存</n-button>
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
