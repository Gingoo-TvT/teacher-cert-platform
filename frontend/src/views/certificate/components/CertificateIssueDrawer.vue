<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { issueCertificate, type Certificate, type CertificateIssuePayload } from '@/api/certificate'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()
const userStore = useUserStore()

const issueVisible = ref(false)
const saving = ref(false)
const selected = ref<Certificate | null>(null)

const issueForm = reactive<CertificateIssuePayload>({
  issuer: userStore.realName || '',
  issueDate: todayText()
})

function open(row: Certificate) {
  selected.value = row
  issueForm.issuer = userStore.realName || row.issuer || ''
  issueForm.issueDate = todayText()
  issueVisible.value = true
}

async function saveIssue() {
  if (saving.value) return
  if (!selected.value || !issueForm.issuer.trim() || !issueForm.issueDate.trim()) {
    message.error('请填写签发人和签发日期')
    return
  }
  saving.value = true
  try {
    await issueCertificate(selected.value.id, issueForm)
    message.success('已签发')
    issueVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '签发失败')
  } finally {
    saving.value = false
  }
}

function todayText() {
  const now = new Date()
  return `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-modal
    v-model:show="issueVisible"
    preset="card"
    title="签发证书"
    style="width: min(var(--overlay-medium), var(--overlay-modal-max))"
    :closable="!saving"
    :close-on-esc="!saving"
    :mask-closable="!saving"
  >
    <n-space vertical>
      <n-alert type="info" :bordered="false">签发权限已并入教务处/全校管理员，不依赖独立签发角色。</n-alert>
      <n-input v-model:value="issueForm.issuer" placeholder="签发人" :disabled="saving" />
      <n-input v-model:value="issueForm.issueDate" placeholder="签发日期，如 2026/6/30" class="mono-input" :disabled="saving" />
      <n-space justify="end">
        <n-button :disabled="saving" @click="issueVisible = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="saveIssue">签发</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
