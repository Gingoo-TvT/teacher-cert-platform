<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
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
const loadError = ref('')
const hasLoadedSuccessfully = ref(false)
const loadedQueryKey = ref('')
const saving = ref(false)
const importVisible = ref(false)
const examVisible = ref(false)
const validityVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const conclusionFilter = ref<string | null>(null)
const confirmFilter = ref<string | null>(null)
const records = ref<AbilityTestResult[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const conclusions = ref<DictItem[]>([])
const examRows = ref<ExamSubject[]>([])
const importText = ref('')
const importFiles = ref<UploadFileInfo[]>([])
const validityText = ref('')
const selectedValidity = ref<AbilityTestResult | null>(null)
let subjectsRequestId = 0
let validityRequestId = 0
let listRequestSequence = 0

const canImport = computed(() => userStore.hasPerm('test:import'))
const canConfirm = computed(() => userStore.hasPerm('test:confirm'))
const listQueryKey = computed(() => JSON.stringify([
  keyword.value,
  assessmentYear.value,
  conclusionFilter.value || '',
  confirmFilter.value || '',
  page.value,
  size.value
]))
const listDataFresh = computed(() =>
  hasLoadedSuccessfully.value
  && loadedQueryKey.value === listQueryKey.value
  && !loading.value
  && !loadError.value
)
const writeBlocked = computed(() => !listDataFresh.value)

const conclusionOptions = computed<SelectOption[]>(() =>
  conclusions.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const confirmOptions: SelectOption[] = [
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' }
]
// Phase 44e 真分页（P1-1）：records 真分页后只含当页数据，valid/pending/confirmed 三项统计
// 只能反映当页（已在模板标签标注「当页」）；total 改用后端返回的全量 total，不再用 records.length。
const summary = computed(() => {
  const valid = records.value.filter((item) => item.validForCertificate).length
  const pending = records.value.filter((item) => item.confirmStatus !== 'CONFIRMED').length
  const confirmed = records.value.filter((item) => item.confirmStatus === 'CONFIRMED').length
  return { total: total.value, valid, pending, confirmed }
})

const columns: DataTableColumns<AbilityTestResult> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '组织方式', key: 'examOrgModeLabel', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '应考科目', key: 'examSubjects', minWidth: 220, render: (row) => subjectText(row.examSubjects) },
  { title: '成绩', key: 'score', minWidth: 150, align: 'right', ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono numeric tabular-nums' }, row.score || '-') },
  { title: '结论', key: 'conclusion', width: 106, render: (row) => h(StatusTag, { value: row.conclusion, text: row.conclusionLabel || statusLabel(row.conclusion) }) },
  { title: '证书有效', key: 'validForCertificate', width: 108, render: (row) => h(StatusTag, { text: row.validForCertificate ? '有效' : '无效' }) },
  { title: '确认', key: 'confirmStatusLabel', width: 106, render: (row) => h(StatusTag, { value: row.confirmStatus, text: row.confirmStatusLabel || statusLabel(row.confirmStatus) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 270,
    render: (row) =>
      renderTableActions([
        h(NButton, { size: 'small', quaternary: true, onClick: () => showSubjects(row) }, { default: () => '应考' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => showValidity(row) }, { default: () => '有效性' }),
        canConfirm.value && row.id && row.confirmStatus !== 'CONFIRMED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => confirm(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'success', disabled: writeBlocked.value }, { default: () => '确认' }),
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
  const requestSequence = ++listRequestSequence
  const queryKey = listQueryKey.value
  const query = {
    keyword: keyword.value,
    assessmentYear: assessmentYear.value,
    conclusion: conclusionFilter.value,
    confirmStatus: confirmFilter.value,
    page: page.value,
    size: size.value
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await listAbilityTests(query)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    records.value = res.data.records
    total.value = res.data.total
    hasLoadedSuccessfully.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = showError(error, '测试结果加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// 筛选变更（关键词/年度/结论/确认状态）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
function search() {
  page.value = 1
  void loadRecords()
}

function onPageChange(next: number) {
  page.value = next
  void loadRecords()
}

function onPageSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadRecords()
}

async function loadOptions() {
  try {
    const conclusionRes = await listDictItems('ability_test_conclusion', true)
    conclusions.value = conclusionRes.data
  } catch (error) {
    showError(error, '测试结论选项加载失败')
  }
}

async function showSubjects(row: AbilityTestResult) {
  const requestId = ++subjectsRequestId
  try {
    if (row.teachingSegment) {
      const res = await getExamSubjects(row.studentId, row.assessmentYear, row.teachingSegment)
      if (requestId !== subjectsRequestId) return
      examRows.value = res.data
    } else {
      if (requestId !== subjectsRequestId) return
      examRows.value = row.examSubjects
    }
    examVisible.value = true
  } catch (error) {
    if (requestId !== subjectsRequestId) return
    showError(error, '应考科目加载失败')
  }
}

async function showValidity(row: AbilityTestResult) {
  const requestId = ++validityRequestId
  selectedValidity.value = row
  try {
    const res = await getAbilityTestValidity(row.studentId, row.assessmentYear)
    if (requestId !== validityRequestId) return
    validityText.value = res.data.message || (res.data.validForCertificate ? '测试结论有效' : '测试结论未满足证书前置')
    validityVisible.value = true
  } catch (error) {
    if (requestId !== validityRequestId) return
    showError(error, '有效性判定失败')
  }
}

async function confirm(row: AbilityTestResult) {
  if (!row.id || writeBlocked.value) return
  try {
    await confirmAbilityTest(row.id)
    message.success('已确认锁定')
    await loadRecords()
  } catch (error) {
    showError(error, '确认失败')
  }
}

function openImport() {
  if (writeBlocked.value) return
  importText.value = ''
  importFiles.value = []
  importVisible.value = true
}

async function saveImport() {
  if (saving.value || writeBlocked.value) return
  saving.value = true
  try {
    const file = importFiles.value[0]?.file
    if (file) {
      await importAbilityTestFile(file)
    } else {
      const rows = await parseImportRows(importText.value)
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

async function parseImportRows(text: string): Promise<AbilityTestPayload[]> {
  const rows = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (rows.length === 0) throw new Error('导入内容不能为空')
  const studentCache = new Map<string, Student>()
  const parsedRows = rows.map((line) => {
    const [studentNo, year, segment, orgMode, score, conclusion] = line.split(',').map((item) => item.trim())
    return { studentNo, year, segment, orgMode, score, conclusion }
  })
  await Promise.all(
    [...new Set(parsedRows.map((row) => row.studentNo))].map(async (studentNo) => {
      const res = await listStudents({ keyword: studentNo })
      const student = res.data.records.find((item) => item.studentNo === studentNo)
      if (student) studentCache.set(studentNo, student)
    })
  )
  return parsedRows.map((row) => {
    const student = studentCache.get(row.studentNo)
    if (!student) throw new Error(`学生不存在：${row.studentNo}`)
    return {
      studentId: student.id,
      assessmentYear: row.year,
      teachingSegment: row.segment,
      examOrgMode: row.orgMode,
      score: row.score,
      conclusion: row.conclusion
    }
  })
}

function subjectText(subjects: ExamSubject[]) {
  const labels = subjects.filter((item) => item.includedInExam).map((item) => item.subjectLabel)
  return labels.length ? labels.join('、') : '无应考科目'
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  conclusionFilter.value = null
  confirmFilter.value = null
  search()
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  const text = detail || fallback
  message.error(text)
  return text
}

onMounted(() => {
  void Promise.all([loadOptions(), loadRecords()])
})

watch(
  () => yearStore.assessmentYear,
  (year) => {
    assessmentYear.value = year
    search()
  }
)
</script>

<template>
  <PageContainer title="测试结果" description="测试成绩通过导入产生，院校人员确认锁定。">
    <n-grid v-if="hasLoadedSuccessfully" cols="1 440:2 900:4" :x-gap="12" :y-gap="12" responsive="self" class="page-section">
      <n-gi><StatCard label="结果总数" :value="summary.total" /></n-gi>
      <n-gi><StatCard label="证书前置有效（当页）" :value="summary.valid" tone="success" /></n-gi>
      <n-gi><StatCard label="待确认（当页）" :value="summary.pending" tone="warning" /></n-gi>
      <n-gi><StatCard label="已确认（当页）" :value="summary.confirmed" tone="info" /></n-gi>
    </n-grid>

    <FilterBar :loading="loading" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学号 / 姓名 / 成绩" style="width: 220px" @keyup.enter="search" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>结论</span>
        <n-select v-model:value="conclusionFilter" clearable :options="conclusionOptions" placeholder="全部结论" style="width: 150px" />
      </label>
      <label class="filter-field">
        <span>确认</span>
        <n-select v-model:value="confirmFilter" clearable :options="confirmOptions" placeholder="全部状态" style="width: 150px" />
      </label>
    </FilterBar>

    <DataPanel
      title="测试结果列表"
      :columns="columns"
      :data="records"
      :total="total"
      :loading="loading"
      :error="loadError"
      remote
      :page="page"
      :page-size="size"
      empty-title="暂无测试结果"
      empty-description="当前筛选条件下没有测试结果记录。"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button v-if="canImport" type="primary" size="small" :disabled="writeBlocked" @click="openImport">导入测试结果</n-button>
      </template>
      <template v-if="canImport" #emptyAction>
        <n-button type="primary" :disabled="writeBlocked" @click="openImport">导入测试结果</n-button>
      </template>
    </DataPanel>

    <n-modal
      v-model:show="importVisible"
      preset="card"
      title="导入测试结果"
      style="width: min(var(--overlay-wide), var(--overlay-modal-max))"
      :closable="!saving"
      :close-on-esc="!saving"
      :mask-closable="!saving"
    >
      <n-space vertical>
        <n-alert type="info" :bordered="false">
          本阶段不提供手工新建或编辑入口。可上传 Excel/CSV，或粘贴轻量文本：学号,年度,学段,组织方式,成绩,结论。
        </n-alert>
        <n-upload v-model:file-list="importFiles" :max="1" accept=".xlsx,.xls,.csv" :default-upload="false" :disabled="saving">
          <n-upload-dragger>
            <n-text>点击或拖拽测试结果文件到此处上传</n-text>
            <n-p depth="3">支持 XLSX、XLS、CSV；无文件时可直接粘贴文本。</n-p>
          </n-upload-dragger>
        </n-upload>
        <n-input
          v-model:value="importText"
          type="textarea"
          :disabled="saving"
          :autosize="{ minRows: 6, maxRows: 10 }"
          placeholder="无文件时可粘贴：学号,年度,学段,组织方式,成绩,结论"
        />
        <n-space justify="end">
          <n-button :disabled="saving" @click="importVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="writeBlocked" @click="saveImport">导入</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal
      v-model:show="examVisible"
      preset="card"
      title="应考科目口径"
      style="width: min(var(--overlay-wide), var(--overlay-modal-max))"
    >
      <n-data-table :columns="examColumns" :data="examRows" :pagination="false" :scroll-x="400" />
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
</style>
