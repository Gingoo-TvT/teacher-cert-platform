<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DetailPanel from '@/components/DetailPanel.vue'
import type { DictItem } from '@/api/dict'
import type { College } from '@/api/organization'
import type { TrainingProfile } from '@/api/training'

const props = defineProps<{
  colleges: College[]
  educationLevels: DictItem[]
  trainingGoals: DictItem[]
  internshipModes: DictItem[]
  internshipLocations: DictItem[]
  segments: DictItem[]
  interviewModes: DictItem[]
  conclusions: DictItem[]
}>()

const detailVisible = ref(false)
const selectedProfile = ref<TrainingProfile | null>(null)
const compactViewport = ref(false)
let compactViewportQuery: MediaQueryList | null = null

const detailColumns = computed(() => compactViewport.value ? 1 : 2)

const detailItems = computed(() => {
  const row = selectedProfile.value
  if (!row) return []
  return [
    { label: '学生', value: [row.studentNo, row.studentName].filter(Boolean).join(' / ') || '-' },
    { label: '学院', value: collegeName(row.collegeId) },
    { label: '考核年度', value: row.assessmentYear, mono: true },
    { label: '校内专业', value: row.internalMajorName || '-' },
    { label: '二级学科', value: [row.secondDisciplineCode, row.secondDisciplineName].filter(Boolean).join(' / ') || '-' },
    { label: '学历层次', value: dictLabel(props.educationLevels, row.educationLevel) },
    { label: '培养目标', value: dictLabel(props.trainingGoals, row.trainingGoal) },
    { label: '实习组织方式', value: dictLabel(props.internshipModes, row.internshipOrgMode) },
    { label: '实习地点', value: dictLabel(props.internshipLocations, row.internshipLocation) },
    { label: '任教学段', value: dictLabel(props.segments, row.teachingSegment) },
    { label: '任教学科', value: [row.teachingSubjectName, row.teachingSubjectCode].filter(Boolean).join(' / ') || '-' },
    { label: '面试组织方式', value: dictLabel(props.interviewModes, row.interviewOrgMode) },
    { label: '测试结论', value: dictLabel(props.conclusions, row.abilityTestConclusion) },
    { label: '状态', status: row.status },
    { label: '初审意见', value: row.firstReviewComment || '-', span: 2 },
    { label: '复审意见', value: row.secondReviewComment || '-', span: 2 }
  ]
})

function open(row: TrainingProfile) {
  selectedProfile.value = row
  detailVisible.value = true
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function collegeName(id?: string | null) {
  return props.colleges.find((item) => item.id === id)?.name || id || '-'
}

function syncCompactViewport(event?: MediaQueryListEvent) {
  compactViewport.value = event?.matches ?? compactViewportQuery?.matches ?? false
}

onMounted(() => {
  compactViewportQuery = window.matchMedia('(max-width: 768px)')
  syncCompactViewport()
  compactViewportQuery.addEventListener('change', syncCompactViewport)
})

onBeforeUnmount(() => {
  compactViewportQuery?.removeEventListener('change', syncCompactViewport)
})

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="detailVisible" width="min(var(--overlay-medium), var(--overlay-drawer-max))">
    <n-drawer-content title="培养信息详情" closable>
      <DetailPanel v-if="selectedProfile" :items="detailItems" :columns="detailColumns" />
    </n-drawer-content>
  </n-drawer>
</template>
