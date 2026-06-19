<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { getExamSubjects, type ExamSubject } from '@/api/exemption'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  confirmAbilityTest,
  getAbilityTestValidity,
  importAbilityTestFile,
  importAbilityTests,
  listAbilityTests,
  type AbilityTestPayload,
  type AbilityTestResult
} from '@/api/testResult'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const importVisible = ref(false)
const examVisible = ref(false)
const validityVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const conclusionFilter = ref<string | null>(null)
const confirmFilter = ref<string | null>(null)
const records = ref<AbilityTestResult[]>([])
const students = ref<Student[]>([])
const conclusions = ref<DictItem[]>([])
const examRows = ref<ExamSubject[]>([])
const importText = ref('')
const importFiles = ref<UploadFileInfo[]>([])
const validityText = ref('')
const selectedValidity = ref<AbilityTestResult | null>(null)

const canImport = computed(() => userStore.hasPerm('test:import'))
const canConfirm = computed(() => userStore.hasPerm('test:confirm'))
const canViewStudents = computed(() => userStore.hasPerm('student:view'))

const conclusionOptions = computed<SelectOption[]>(() =>
  conclusions.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const confirmOptions: SelectOption[] = [
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' }
]
const summary = computed(() => {
  const valid = records.value.filter((item) => item.validForCertificate).length
  const pending = records.value.filter((item) => item.confirmStatus !== 'CONFIRMED').length
  const confirmed = records.value.filter((item) => item.confirmStatus === 'CONFIRMED').length
  return { total: records.value.length, valid, pending, confirmed }
})

const columns: DataTableColumns<AbilityTestResult> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '组织方式', key: 'examOrgModeLabel', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '应考科目', key: 'examSubjects', minWidth: 220, render: (row) => subjectText(row.examSubjects) },
  { title: '成绩', key: 'score', minWidth: 150, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.score || '-') },
  { title: '结论', key: 'conclusion', width: 106, render: (row) => h(StatusTag, { text: row.conclusionLabel || row.conclusion }) },
  { title: '证书有效', key: 'validForCertificate', width: 108, render: (row) => h(StatusTag, { text: row.validForCertificate ? '有效' : '无效' }) },
  { title: '确认', key: 'confirmStatusLabel', width: 106, render: (row) => h(StatusTag, { text: row.confirmStatusLabel || row.confirmStatus }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 270,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        h(NButton, { size: 'small', quaternary: true, onClick: () => showSubjects(row) }, { default: () => '应考' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => showValidity(row) }, { default: () => '有效性' }),
        canConfirm.value && row.id && row.confirmStatus !== 'CONFIRMED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => confirm(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'success' }, { default: () => '确认' }),
                default: () => '确认后锁定测试结果，是否继续？'
              }
            )
          : null
      ])
  }
]

const examColumns: DataTableColumns<ExamSubject> = [
  { title: '科目', key: 'subjectLabel', minWidth: 180 },
  { title: '免考通过', key: 'exempted', width: 110, render: (row) => h(StatusTag, { text: row.exempted ? '通过' : '未通过' }) },
  { title: '应考口径', key: 'includedInExam', width: 110, render: (row) => h(StatusTag, { text: row.includedInExam ? '应考' : '已移出' }) }
]

async function loadRecords() {
  loading.value = true
  try {
    const res = await listAbilityTests({
      keyword: keyword.value,
      assessmentYear: assessmentYear.value,
      conclusion: conclusionFilter.value,
      confirmStatus: confirmFilter.value
    })
    records.value = res.data.records
  } catch (error) {
    showError(error, '测试结果加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, conclusionRes] = await Promise.all([
    canViewStudents.value ? listStudents() : Promise.resolve(null),
    listDictItems('ability_test_conclusion', true)
  ])
  students.value = studentRes?.data.records || []
  conclusions.value = conclusionRes.data
}

async function showSubjects(row: AbilityTestResult) {
  try {
    if (row.teachingSegment) {
      const res = await getExamSubjects(row.studentId, row.assessmentYear, row.teachingSegment)
      examRows.value = res.data
    } else {
      examRows.value = row.examSubjects
    }
    examVisible.value = true
  } catch (error) {
    showError(error, '应考科目加载失败')
  }
}

async function showValidity(row: AbilityTestResult) {
  selectedValidity.value = row
  try {
    const res = await getAbilityTestValidity(row.studentId, row.assessmentYear)
    validityText.value = res.data.message || (res.data.validForCertificate ? '测试结论有效' : '测试结论未满足证书前置')
    validityVisible.value = true
  } catch (error) {
    showError(error, '有效性判定失败')
  }
}

async function confirm(row: AbilityTestResult) {
  if (!row.id) return
  try {
    await confirmAbilityTest(row.id)
    message.success('已确认锁定')
    await loadRecords()
  } catch (error) {
    showError(error, '确认失败')
  }
}

function openImport() {
  importText.value = ''
  importFiles.value = []
  importVisible.value = true
}

async function saveImport() {
  saving.value = true
  try {
    const file = importFiles.value[0]?.file
    if (file) {
      await importAbilityTestFile(file)
    } else {
      const rows = parseImportRows(importText.value)
      await importAbilityTests(rows)
    }
    message.success('导入完成')
    importVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '导入失败')
  } finally {
    saving.value = false
  }
}

function parseImportRows(text: string): AbilityTestPayload[] {
  const rows = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (rows.length === 0) throw new Error('导入内容不能为空')
  return rows.map((line) => {
    const [studentNo, year, segment, orgMode, score, conclusion] = line.split(',').map((item) => item.trim())
    const student = students.value.find((item) => item.studentNo === studentNo)
    if (!student) throw new Error(`学生不存在：${studentNo}`)
    return {
      studentId: student.id,
      assessmentYear: year,
      teachingSegment: segment,
      examOrgMode: orgMode,
      score,
      conclusion
    }
  })
}

function subjectText(subjects: ExamSubject[]) {
  const labels = subjects.filter((item) => item.includedInExam).map((item) => item.subjectLabel)
  return labels.length ? labels.join('、') : '无应考科目'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    await loadRecords()
  }
)
</script>

<template>
  <PageContainer title="测试结果" description="测试成绩通过导入产生，院校人员确认锁定。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadRecords">刷新</n-button>
        <n-button v-if="canImport" type="primary" @click="openImport">导入测试结果</n-button>
      </n-space>
    </template>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="结果总数" :value="summary.total" /></n-gi>
      <n-gi><StatCard label="证书前置有效" :value="summary.valid" tone="success" /></n-gi>
      <n-gi><StatCard label="待确认" :value="summary.pending" tone="warning" /></n-gi>
      <n-gi><StatCard label="已确认" :value="summary.confirmed" tone="info" /></n-gi>
    </n-grid>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-input v-model:value="keyword" clearable placeholder="学号 / 姓名 / 成绩" style="width: 220px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="conclusionFilter" clearable :options="conclusionOptions" placeholder="结论" style="width: 150px" />
        <n-select v-model:value="confirmFilter" clearable :options="confirmOptions" placeholder="确认状态" style="width: 150px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
    </n-card>

    <n-data-table
      :columns="columns"
      :data="records"
      :loading="loading"
      :row-key="(row: AbilityTestResult) => row.id || row.studentId"
      :scroll-x="1460"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-modal v-model:show="importVisible" preset="card" title="导入测试结果" style="width: 760px">
      <n-space vertical>
        <n-alert type="info" :bordered="false">
          本阶段不提供手工新建或编辑入口。可上传 Excel/CSV，或粘贴轻量文本：学号,年度,学段,组织方式,成绩,结论。
        </n-alert>
        <n-upload v-model:file-list="importFiles" :max="1" accept=".xlsx,.xls,.csv" :default-upload="false" />
        <n-input
          v-model:value="importText"
          type="textarea"
          :autosize="{ minRows: 6, maxRows: 10 }"
          placeholder="无文件时可粘贴：学号,年度,学段,组织方式,成绩,结论"
        />
        <n-space justify="end">
          <n-button @click="importVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveImport">导入</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="examVisible" preset="card" title="应考科目口径" style="width: 680px">
      <n-data-table :columns="examColumns" :data="examRows" :pagination="false" />
    </n-modal>

    <n-modal v-model:show="validityVisible" preset="dialog" title="测试结论有效性">
      <n-space vertical>
        <n-alert :type="selectedValidity?.validForCertificate ? 'success' : 'warning'" :bordered="false">
          {{ validityText }}
        </n-alert>
      </n-space>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}
</style>
