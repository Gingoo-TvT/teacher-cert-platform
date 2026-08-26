<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import type { DictItem } from '@/api/dict'
import {
  arbitrateVideoReview,
  thirdVideoReview,
  type ReviewerCandidate,
  type VideoReview,
  type VideoScorePayload
} from '@/api/video'

interface DimensionRow {
  code: string
  score: number
}

const props = defineProps<{
  reviewers: ReviewerCandidate[]
  dimensions: DictItem[]
}>()

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const message = useMessage()

const arbitrateVisible = ref(false)
const arbitrating = ref<VideoReview | null>(null)
const saving = ref(false)

const arbitrateForm = reactive({
  mode: 'thirdExpert' as 'thirdExpert' | 'collegeArbitrate',
  reviewerId: '',
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: '',
  dimensions: [] as DimensionRow[]
})

const conclusionOptions: SelectOption[] = [
  { label: '合格', value: 'PASS' },
  { label: '不合格', value: 'FAIL' }
]

const reviewerOptions = computed<SelectOption[]>(() =>
  props.reviewers.map((item) => ({ label: `${item.realName} ${item.workNo || item.id}`, value: item.id }))
)

function open(row: VideoReview) {
  arbitrating.value = row
  arbitrateForm.mode = 'thirdExpert'
  arbitrateForm.reviewerId = props.reviewers[0]?.id || ''
  arbitrateForm.score = 60
  arbitrateForm.conclusion = 'PASS'
  arbitrateForm.comment = ''
  arbitrateForm.dimensions = props.dimensions.map((item) => ({ code: item.itemCode, score: 0 }))
  arbitrateVisible.value = true
}

async function saveArbitrate() {
  if (saving.value) return
  if (!arbitrating.value) return
  saving.value = true
  try {
    if (arbitrateForm.mode === 'thirdExpert') {
      if (!arbitrateForm.reviewerId) {
        message.error('请选择第三专家')
        return
      }
      await thirdVideoReview(arbitrating.value.id, {
        ...scorePayload(),
        reviewerId: arbitrateForm.reviewerId
      })
    } else {
      await arbitrateVideoReview(arbitrating.value.id, {
        finalScore: arbitrateForm.score,
        conclusion: arbitrateForm.conclusion,
        comment: arbitrateForm.comment
      })
    }
    message.success('复评/仲裁已完成')
    arbitrateVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '复评/仲裁失败')
  } finally {
    saving.value = false
  }
}

function scorePayload(): VideoScorePayload {
  const dimensionScores: Record<string, number> = {}
  for (const item of arbitrateForm.dimensions) dimensionScores[item.code] = item.score
  if (Object.keys(dimensionScores).length === 0) {
    for (const item of props.dimensions) dimensionScores[item.itemCode] = 0
  }
  return {
    score: arbitrateForm.score,
    conclusion: arbitrateForm.conclusion,
    comment: arbitrateForm.comment,
    dimensionScores
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
    v-model:show="arbitrateVisible"
    preset="card"
    title="复评/仲裁"
    style="width: min(var(--overlay-medium), var(--overlay-modal-max))"
    :closable="!saving"
    :close-on-esc="!saving"
    :mask-closable="!saving"
  >
    <n-space vertical>
      <n-radio-group v-model:value="arbitrateForm.mode" :disabled="saving">
        <n-radio-button value="thirdExpert">第三专家</n-radio-button>
        <n-radio-button value="collegeArbitrate">学院仲裁</n-radio-button>
      </n-radio-group>
      <n-select v-if="arbitrateForm.mode === 'thirdExpert'" v-model:value="arbitrateForm.reviewerId" :options="reviewerOptions" filterable placeholder="第三专家" :disabled="saving" />
      <n-input-number v-model:value="arbitrateForm.score" :min="0" :max="100" style="width: 100%" placeholder="分数/终分" :disabled="saving" />
      <n-select v-model:value="arbitrateForm.conclusion" :options="conclusionOptions" :disabled="saving" />
      <n-input v-model:value="arbitrateForm.comment" type="textarea" placeholder="意见" :disabled="saving" />
      <n-space justify="end">
        <n-button :disabled="saving" @click="arbitrateVisible = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="saveArbitrate">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>
