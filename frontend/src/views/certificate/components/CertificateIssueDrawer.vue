<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import { issueCertificate, type Certificate, type CertificateIssuePayload } from '@/api/certificate'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()
const userStore = useUserStore()

const issueVisible = ref(false)
const saving = ref(false)
const selected = ref<Certificate | null>(null)
const issuers = ref<DictItem[]>([])
const issuerLoading = ref(false)
const issuerError = ref('')

const issueForm = reactive<CertificateIssuePayload>({
  issuer: userStore.realName || '',
  issueDate: todayText()
})

const issuerOptions = computed<SelectOption[]>(() =>
  issuers.value.map((item) => ({ label: item.itemValue, value: item.itemValue }))
)
const issuerReady = computed(() =>
  !issuerLoading.value && !issuerError.value && issuerOptions.value.length > 0
)

async function open(row: Certificate) {
  selected.value = row
  issueForm.issuer = ''
  issueForm.issueDate = todayText()
  issueVisible.value = true
  await loadIssuers()
  const values = new Set(issuerOptions.value.map((option) => String(option.value)))
  issueForm.issuer = [row.issuer, userStore.realName].find((value) => value && values.has(value)) || ''
}

async function saveIssue() {
  if (saving.value) return
  if (!issuerReady.value) {
    message.error(issuerError.value || '请先在字典管理中维护启用的证书签发人')
    return
  }
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

async function loadIssuers() {
  issuerLoading.value = true
  issuerError.value = ''
  try {
    const response = await listDictItems('cert_issuer', true)
    issuers.value = response.data.filter((item) => item.yearVersion === 'GLOBAL')
    if (!issuers.value.length) {
      issuerError.value = '暂无启用的证书签发人，请先在字典管理中维护'
    }
  } catch (error) {
    issuers.value = []
    issuerError.value = errorText(error, '证书签发人字典加载失败')
  } finally {
    issuerLoading.value = false
  }
}

function todayText() {
  const now = new Date()
  return `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
}

function showError(error: unknown, fallback: string) {
  message.error(errorText(error, fallback))
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
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
      <n-alert v-if="issuerError" type="warning">{{ issuerError }}</n-alert>
      <n-select
        v-model:value="issueForm.issuer"
        :options="issuerOptions"
        :loading="issuerLoading"
        :disabled="saving || !issuerReady"
        placeholder="请选择签发人"
      />
      <n-input v-model:value="issueForm.issueDate" placeholder="签发日期，如 2026/6/30" class="mono-input" :disabled="saving" />
      <n-space justify="end">
        <n-button :disabled="saving" @click="issueVisible = false">取消</n-button>
        <n-button type="primary" :loading="saving" :disabled="!issuerReady" @click="saveIssue">签发</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
