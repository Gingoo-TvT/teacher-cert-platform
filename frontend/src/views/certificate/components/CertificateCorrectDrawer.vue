<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import SubjectSelect from '@/components/SubjectSelect.vue'
import {
  correctCertificate,
  type Certificate,
  type CertificateCorrectPayload
} from '@/api/certificate'
import type { TeachingSubject } from '@/api/subject'
import { getTrainingOptions } from '@/api/training'

const props = defineProps<{
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
const allowedSegments = ref<string[]>([])
const optionsLoading = ref(false)
const optionsError = ref('')
const loadedOptionsKey = ref('')
let optionsRequestSequence = 0
let restoring = false

const correctForm = reactive<CertificateCorrectPayload>({
  correctionRevision: '',
  certNo: '',
  validUntil: '',
  teachingSegment: '',
  teachingSubjectCode: '',
  teachingSubjectName: '',
  trainingGoal: '',
  reason: ''
})

const currentOptionsKey = computed(() => optionsKey(correctForm.trainingGoal, correctForm.teachingSegment))
const filteredSegmentOptions = computed(() => {
  if (!correctForm.trainingGoal) return []
  return props.segmentOptions.filter((option) =>
    typeof option.value === 'string' && allowedSegments.value.includes(option.value)
  )
})
const linkageReady = computed(() =>
  Boolean(correctForm.trainingGoal && correctForm.teachingSegment && correctForm.teachingSubjectCode)
  && allowedSegments.value.includes(correctForm.teachingSegment || '')
  && loadedOptionsKey.value === currentOptionsKey.value
  && !optionsLoading.value
  && !optionsError.value
)

watch(
  () => correctForm.trainingGoal,
  async (goal) => {
    if (restoring) return
    correctForm.teachingSegment = ''
    clearSubject()
    allowedSegments.value = []
    loadedOptionsKey.value = ''
    optionsError.value = ''
    if (goal) await reloadOptions(goal, '')
  }
)

watch(
  () => correctForm.teachingSegment,
  async (segment, oldSegment) => {
    if (restoring) return
    if (segment !== oldSegment) clearSubject()
    if (correctForm.trainingGoal && segment) {
      await reloadOptions(correctForm.trainingGoal, segment)
    }
  }
)

async function open(row: Certificate) {
  restoring = true
  selected.value = row
  Object.assign(correctForm, {
    correctionRevision: row.correctionRevision,
    certNo: row.certNo || '',
    validUntil: row.validUntil || '',
    teachingSegment: row.teachingSegment || '',
    teachingSubjectCode: row.teachingSubjectCode || '',
    teachingSubjectName: row.teachingSubjectName || '',
    trainingGoal: row.trainingGoal || '',
    reason: ''
  })
  correctVisible.value = true
  await nextTick()
  if (correctForm.trainingGoal) {
    await reloadOptions(correctForm.trainingGoal, correctForm.teachingSegment)
  }
  correctForm.teachingSubjectCode = row.teachingSubjectCode || ''
  correctForm.teachingSubjectName = row.teachingSubjectName || ''
  restoring = false
}

async function saveCorrect() {
  if (saving.value) return
  if (!selected.value || !correctForm.reason.trim()) {
    message.error('请填写更正原因')
    return
  }
  if (!linkageReady.value || !correctForm.teachingSubjectName?.trim()) {
    message.error(optionsError.value || '请按培养目标、任教学段和任教学科联动选择')
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

async function reloadOptions(goal: string, segment?: string | null) {
  const requestSequence = ++optionsRequestSequence
  const key = optionsKey(goal, segment)
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const response = await getTrainingOptions(goal, segment)
    if (requestSequence !== optionsRequestSequence || key !== currentOptionsKey.value) return
    allowedSegments.value = response.data.allowedSegments || []
    loadedOptionsKey.value = key
  } catch (error) {
    if (requestSequence !== optionsRequestSequence || key !== currentOptionsKey.value) return
    optionsError.value = errorText(error, '培养目标联动选项加载失败')
    message.error(optionsError.value)
  } finally {
    if (requestSequence === optionsRequestSequence) optionsLoading.value = false
  }
}

function handleSubjectChange(subject: TeachingSubject | null) {
  correctForm.teachingSubjectCode = subject?.subjectCode || ''
  correctForm.teachingSubjectName = subject?.subjectName || ''
}

function clearSubject() {
  correctForm.teachingSubjectCode = ''
  correctForm.teachingSubjectName = ''
}

function optionsKey(goal?: string | null, segment?: string | null) {
  return `${goal || ''}:${segment || ''}`
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
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
        </n-grid>
        <div class="form-section-title">任教学科</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="培养目标">
            <n-select v-model:value="correctForm.trainingGoal" clearable :options="goalOptions" placeholder="培养目标" />
          </n-form-item-gi>
          <n-form-item-gi label="任教学段">
            <n-select
              v-model:value="correctForm.teachingSegment"
              clearable
              :options="filteredSegmentOptions"
              :loading="optionsLoading"
              :disabled="!correctForm.trainingGoal || optionsLoading"
              placeholder="任教学段"
            />
          </n-form-item-gi>
          <n-form-item-gi label="任教学科" span="1 480:2">
            <SubjectSelect
              v-model:value="correctForm.teachingSubjectCode"
              :segment-code="correctForm.teachingSegment"
              :disabled="optionsLoading || !correctForm.teachingSegment"
              @change="handleSubjectChange"
            />
          </n-form-item-gi>
          <n-form-item-gi label="学科名称" span="1 480:2">
            <n-input :value="correctForm.teachingSubjectName" readonly placeholder="选择任教学科后自动带出" />
          </n-form-item-gi>
        </n-grid>
        <n-alert v-if="optionsError" type="error" class="linkage-alert">{{ optionsError }}</n-alert>
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
          <n-button type="primary" :loading="saving" :disabled="!linkageReady" @click="saveCorrect">保存更正</n-button>
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

.linkage-alert {
  margin-top: var(--space-3);
}
</style>
