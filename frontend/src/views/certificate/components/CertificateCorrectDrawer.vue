<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import {
  correctCertificate,
  type Certificate,
  type CertificateCorrectPayload
} from '@/api/certificate'

defineProps<{
  segmentOptions: SelectOption[]
  goalOptions: SelectOption[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const correctVisible = ref(false)
const saving = ref(false)
const selected = ref<Certificate | null>(null)

const correctForm = reactive<CertificateCorrectPayload>({
  certNo: '',
  validUntil: '',
  teachingSegment: '',
  teachingSubjectCode: '',
  teachingSubjectName: '',
  trainingGoal: '',
  reason: ''
})

function open(row: Certificate) {
  selected.value = row
  Object.assign(correctForm, {
    certNo: row.certNo || '',
    validUntil: row.validUntil || '',
    teachingSegment: row.teachingSegment || '',
    teachingSubjectCode: row.teachingSubjectCode || '',
    teachingSubjectName: row.teachingSubjectName || '',
    trainingGoal: row.trainingGoal || '',
    reason: ''
  })
  correctVisible.value = true
}

async function saveCorrect() {
  if (saving.value) return
  if (!selected.value || !correctForm.reason.trim()) {
    message.error('请填写更正原因')
    return
  }
  saving.value = true
  try {
    await correctCertificate(selected.value.id, correctForm)
    message.success('已更正')
    correctVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '更正失败')
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
    v-model:show="correctVisible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content title="证书更正" :closable="!saving">
      <n-form label-placement="top" :disabled="saving">
        <div class="form-section-title">证书信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="证书编号" span="1 480:2">
            <n-input v-model:value="correctForm.certNo" placeholder="18位证书编号" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="有效期至">
            <n-input v-model:value="correctForm.validUntil" placeholder="如 2029/6/30" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="任教学段">
            <n-select v-model:value="correctForm.teachingSegment" clearable :options="segmentOptions" placeholder="任教学段" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">任教学科</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="学科代码">
            <n-input v-model:value="correctForm.teachingSubjectCode" placeholder="任教学科代码" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="学科名称">
            <n-input v-model:value="correctForm.teachingSubjectName" placeholder="任教学科名称" />
          </n-form-item-gi>
          <n-form-item-gi label="培养目标" span="1 480:2">
            <n-select v-model:value="correctForm.trainingGoal" clearable :options="goalOptions" placeholder="培养目标" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">更正原因</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="原因" span="1 480:2">
            <n-input v-model:value="correctForm.reason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="更正原因" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="correctVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveCorrect">保存更正</n-button>
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

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
