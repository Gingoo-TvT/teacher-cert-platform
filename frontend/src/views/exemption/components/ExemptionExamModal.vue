<script setup lang="ts">
import { h, ref } from 'vue'
import { useMessage, type DataTableColumns } from 'naive-ui'
import StatusTag from '@/components/StatusTag.vue'
import { getExamSubjects, type ExamSubject } from '@/api/exemption'

const message = useMessage()

const examVisible = ref(false)
const loading = ref(false)
const examSubjects = ref<ExamSubject[]>([])

const examColumns: DataTableColumns<ExamSubject> = [
  { title: '科目', key: 'subjectLabel', minWidth: 180 },
  { title: '免考通过', key: 'exempted', width: 110, render: (row) => h(StatusTag, { text: row.exempted ? '通过' : '未通过' }) },
  { title: '应考口径', key: 'includedInExam', width: 110, render: (row) => h(StatusTag, { text: row.includedInExam ? '应考' : '已移出' }) }
]

async function open(studentId: string, assessmentYear: string, segment: string) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getExamSubjects(studentId, assessmentYear, segment)
    examSubjects.value = res.data
    examVisible.value = true
  } catch (error) {
    showError(error, '应考科目加载失败')
  } finally {
    loading.value = false
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
    v-model:show="examVisible"
    preset="card"
    title="应考科目口径"
    style="width: min(var(--overlay-wide), var(--overlay-modal-max))"
  >
    <n-data-table :columns="examColumns" :data="examSubjects" :pagination="false" :scroll-x="400" />
  </n-modal>
</template>
