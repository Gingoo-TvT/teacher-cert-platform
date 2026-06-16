<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import { listDictItems, type DictItem } from '@/api/dict'
import { getExamSubjects, type ExamSubject } from '@/api/exemption'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import {
  confirmAbilityTest,
  getAbilityTestValidity,
  importAbilityTestFile,
  importAbilityTests,
  listAbilityTests,
  saveAbilityTest,
  updateAbilityTest,
  type AbilityTestPayload,
  type AbilityTestResult
} from '@/api/testResult'

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const importVisible = ref(false)
const examVisible = ref(false)
const validityVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref('2026')
const conclusionFilter = ref<string | null>(null)
const confirmFilter = ref<string | null>(null)
const records = ref<AbilityTestResult[]>([])
const students = ref<Student[]>([])
const segments = ref<DictItem[]>([])
const orgModes = ref<DictItem[]>([])
const conclusions = ref<DictItem[]>([])
const selectedRecord = ref<AbilityTestResult | null>(null)
const examRows = ref<ExamSubject[]>([])
const importText = ref('')
const importFiles = ref<UploadFileInfo[]>([])
const validityText = ref('')

const canEdit = computed(() => userStore.hasPerm('test:edit'))
const canImport = computed(() => userStore.hasPerm('test:import'))
const canConfirm = computed(() => userStore.hasPerm('test:confirm'))

const form = reactive<AbilityTestPayload>({
  studentId: '',
  assessmentYear: '2026',
  teachingSegment: '',
  examOrgMode: '',
  score: '',
  conclusion: 'pending_confirm'
})

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const segmentOptions = computed<SelectOption[]>(() =>
  segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const orgModeOptions = computed<SelectOption[]>(() =>
  orgModes.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const conclusionOptions = computed<SelectOption[]>(() =>
  conclusions.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const confirmOptions: SelectOption[] = [
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' }
]

const columns: DataTableColumns<AbilityTestResult> = [
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '组织方式', key: 'examOrgModeLabel', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '应考科目', key: 'examSubjects', minWidth: 220, render: (row) => subjectText(row.examSubjects) },
  { title: '成绩', key: 'score', width: 130, ellipsis: { tooltip: true } },
  { title: '结论', key: 'conclusion', width: 100, render: (row) => conclusionTag(row) },
  { title: '确认', key: 'confirmStatusLabel', width: 95 },
  {
    title: '操作',
    key: 'actions',
    width: 340,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => showSubjects(row) }, { default: () => '应考' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => showValidity(row) }, { default: () => '有效性' }),
        canEdit.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openEdit(row) }, { default: () => '修改' })
          : null,
        canConfirm.value && row.id
          ? h(
              NPopconfirm,
              { onPositiveClick: () => confirm(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'success' }, { default: () => '确认' }),
                default: () => '确认后锁定，是否继续？'
              }
            )
          : null
      ])
  }
]

const examColumns: DataTableColumns<ExamSubject> = [
  { title: '科目', key: 'subjectLabel' },
  { title: '免考通过', key: 'exempted', width: 110, render: (row) => (row.exempted ? '是' : '否') },
  { title: '应考口径', key: 'includedInExam', width: 110, render: (row) => (row.includedInExam ? '应考' : '已移出') }
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
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, segmentRes, orgModeRes, conclusionRes] = await Promise.all([
    listStudents(),
    listDictItems('teaching_segment', true),
    listDictItems('exam_org_mode', true),
    listDictItems('ability_test_conclusion', true)
  ])
  students.value = studentRes.data.records
  segments.value = segmentRes.data
  orgModes.value = orgModeRes.data
  conclusions.value = conclusionRes.data
}

function openCreate() {
  selectedRecord.value = null
  form.studentId = ''
  form.assessmentYear = assessmentYear.value
  form.teachingSegment = ''
  form.examOrgMode = orgModes.value[0]?.itemCode || ''
  form.score = ''
  form.conclusion = 'pending_confirm'
  drawerVisible.value = true
}

function openEdit(row: AbilityTestResult) {
  selectedRecord.value = row
  form.studentId = row.studentId
  form.assessmentYear = row.assessmentYear
  form.teachingSegment = row.teachingSegment || ''
  form.examOrgMode = row.examOrgMode || orgModes.value[0]?.itemCode || ''
  form.score = row.score || ''
  form.conclusion = row.conclusion
  drawerVisible.value = true
}

async function save() {
  if (!form.studentId || !form.assessmentYear || !form.examOrgMode || !form.conclusion) {
    message.error('请选择学生、年度、组织方式和结论')
    return
  }
  saving.value = true
  try {
    if (selectedRecord.value?.id) await updateAbilityTest(form)
    else await saveAbilityTest(form)
    message.success('已保存')
    drawerVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

async function showSubjects(row: AbilityTestResult) {
  if (row.teachingSegment) {
    const res = await getExamSubjects(row.studentId, row.assessmentYear, row.teachingSegment)
    examRows.value = res.data
  } else {
    examRows.value = row.examSubjects
  }
  examVisible.value = true
}

async function showValidity(row: AbilityTestResult) {
  const res = await getAbilityTestValidity(row.studentId, row.assessmentYear)
  validityText.value = res.data.message || (res.data.validForCertificate ? '测试结论有效' : '测试结论未满足证书前置')
  validityVisible.value = true
}

async function confirm(row: AbilityTestResult) {
  if (!row.id) return
  await confirmAbilityTest(row.id)
  message.success('已确认锁定')
  await loadRecords()
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
  } finally {
    saving.value = false
  }
}

function parseImportRows(text: string): AbilityTestPayload[] {
  const rows = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (rows.length === 0) {
    throw new Error('导入内容不能为空')
  }
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

function conclusionTag(row: AbilityTestResult) {
  const type = row.conclusion === 'qualified' || row.conclusion === 'exempted' ? 'success' : row.conclusion === 'unqualified' ? 'error' : 'warning'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.conclusionLabel })
}

function subjectText(subjects: ExamSubject[]) {
  const labels = subjects.filter((item) => item.includedInExam).map((item) => item.subjectLabel)
  return labels.length ? labels.join('、') : '无应考科目'
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between" align="center">
      <n-space>
        <n-input v-model:value="keyword" clearable placeholder="学号/姓名/成绩" style="width: 210px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="conclusionFilter" clearable :options="conclusionOptions" placeholder="结论" style="width: 130px" />
        <n-select v-model:value="confirmFilter" clearable :options="confirmOptions" placeholder="确认状态" style="width: 130px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
      <n-space>
        <n-button v-if="canImport" @click="openImport">导入</n-button>
        <n-button v-if="canEdit" type="primary" @click="openCreate">录入</n-button>
      </n-space>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: AbilityTestResult) => row.id || row.studentId" :scroll-x="1280" />
  </n-space>

  <n-drawer v-model:show="drawerVisible" :width="520">
    <n-drawer-content :title="selectedRecord ? '修改测试结果' : '录入测试结果'" closable>
      <n-space vertical>
        <n-select v-model:value="form.studentId" :options="studentOptions" :disabled="Boolean(selectedRecord)" placeholder="学生" />
        <n-input v-model:value="form.assessmentYear" :disabled="Boolean(selectedRecord)" placeholder="考核年度" />
        <n-select v-model:value="form.teachingSegment" clearable :options="segmentOptions" placeholder="任教学段" />
        <n-select v-model:value="form.examOrgMode" :options="orgModeOptions" placeholder="考试组织方式" />
        <n-input v-model:value="form.score" placeholder="成绩（文本）" />
        <n-select v-model:value="form.conclusion" :options="conclusionOptions" placeholder="结论" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="importVisible" preset="card" title="导入测试结果" style="width: 720px">
    <n-space vertical>
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

  <n-modal v-model:show="examVisible" preset="card" title="应考科目口径" style="width: 640px">
    <n-data-table :columns="examColumns" :data="examRows" :pagination="false" />
  </n-modal>

  <n-modal v-model:show="validityVisible" preset="dialog" title="测试结论有效性">
    {{ validityText }}
  </n-modal>
</template>
