<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import { statusLabel } from '@/constants/statusLabels'
import {
  assignVideoReview,
  assignVideoReviewGroup,
  type ReviewerCandidate,
  type ReviewerGroup,
  type VideoReview
} from '@/api/video'

type AssignMode = 'person' | 'group'

const props = defineProps<{
  reviewers: ReviewerCandidate[]
  groups: ReviewerGroup[]
}>()

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const message = useMessage()

const assignVisible = ref(false)
const assigning = ref<VideoReview | null>(null)

const assignForm = reactive({
  mode: 'person' as AssignMode,
  reviewerIds: [] as string[],
  groupId: ''
})

const reviewerOptions = computed<SelectOption[]>(() =>
  props.reviewers.map((item) => ({ label: `${item.realName} ${item.workNo || item.id}`, value: item.id }))
)
const groupOptions = computed<SelectOption[]>(() =>
  props.groups.filter((item) => item.status === 'ENABLED').map((item) => ({ label: `${item.name} (${item.memberCount}人)`, value: item.id }))
)

function open(row: VideoReview) {
  assigning.value = row
  assignForm.mode = 'person'
  assignForm.reviewerIds = row.tasks.filter((task) => task.reviewerRole === 'REVIEWER').map((task) => task.reviewerId)
  assignForm.groupId = ''
  assignVisible.value = true
}

async function saveAssign() {
  if (!assigning.value) return
  try {
    if (assignForm.mode === 'group') {
      if (!assignForm.groupId) {
        message.error('请选择评审组')
        return
      }
      await assignVideoReviewGroup(assigning.value.id, assignForm.groupId)
    } else {
      if (!assignForm.reviewerIds.length) {
        message.error('请选择评审教师')
        return
      }
      await assignVideoReview(assigning.value.id, assignForm.reviewerIds)
    }
    message.success('已指派评审')
    assignVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '指派失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-modal v-model:show="assignVisible" preset="card" title="指派评审" style="width: 620px">
    <n-space vertical>
      <n-alert v-if="assigning" type="info" :bordered="false">
        {{ assigning.studentNo }} / {{ assigning.studentName }} / 当前状态：{{ assigning.statusLabel || statusLabel(assigning.status) }}
      </n-alert>
      <n-radio-group v-model:value="assignForm.mode">
        <n-radio-button value="person">按人指派</n-radio-button>
        <n-radio-button value="group">按组指派</n-radio-button>
      </n-radio-group>
      <n-select
        v-if="assignForm.mode === 'person'"
        v-model:value="assignForm.reviewerIds"
        multiple
        filterable
        :options="reviewerOptions"
        placeholder="选择评审教师"
      />
      <n-select v-else v-model:value="assignForm.groupId" filterable :options="groupOptions" placeholder="选择评审组" />
      <n-space justify="end">
        <n-button @click="assignVisible = false">取消</n-button>
        <n-button type="primary" @click="saveAssign">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>
