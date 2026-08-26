<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { voidCertificate, type Certificate } from '@/api/certificate'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const voidVisible = ref(false)
const saving = ref(false)
const selected = ref<Certificate | null>(null)

const voidForm = reactive({
  reason: ''
})

function open(row: Certificate) {
  selected.value = row
  voidForm.reason = ''
  voidVisible.value = true
}

async function saveVoid() {
  if (saving.value) return
  if (!selected.value || !voidForm.reason.trim()) {
    message.error('请填写作废原因')
    return
  }
  saving.value = true
  try {
    await voidCertificate(selected.value.id, voidForm.reason)
    message.success('已作废')
    voidVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '作废失败')
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
  <n-modal
    v-model:show="voidVisible"
    preset="card"
    title="作废证书"
    style="width: min(var(--overlay-narrow), var(--overlay-modal-max))"
    :closable="!saving"
    :close-on-esc="!saving"
    :mask-closable="!saving"
  >
    <n-space vertical>
      <n-input v-model:value="voidForm.reason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="作废原因" :disabled="saving" />
      <n-space justify="end">
        <n-button :disabled="saving" @click="voidVisible = false">取消</n-button>
        <n-button type="error" :loading="saving" @click="saveVoid">作废</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>
