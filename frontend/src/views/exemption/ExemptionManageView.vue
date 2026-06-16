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
import { listStudents, type Student } from '@/api/student'
import type { ReviewPayload } from '@/api/student'
import { useUserStore } from '@/stores/user'
import {
  applyExemption,
  deleteExemptionMaterial,
  firstReviewExemption,
  getExamSubjects,
  getExemptionSubjects,
  listExemptions,
  previewExemptionMaterial,
  replaceExemptionMaterial,
  secondReviewExemption,
  submitExemption,
  uploadExemptionMaterial,
  type ExamSubject,
  type ExemptionApplyItem,
  type ExemptionMaterial,
  type ExemptionRequest
} from '@/api/exemption'

interface SubjectRow {
  subject: string
  basis: string
  remark: string
  fileList: UploadFileInfo[]
}

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const examVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref('2026')
const statusFilter = ref<string | null>(null)
const segmentFilter = ref<string | null>(null)
const records = ref<ExemptionRequest[]>([])
const students = ref<Student[]>([])
const segments = ref<DictItem[]>([])
const subjects = ref<DictItem[]>([])
const bases = ref<DictItem[]>([])
const examSubjects = ref<ExamSubject[]>([])
const replaceVisible = ref(false)
const replacingMaterial = ref<{ record: ExemptionRequest; material: ExemptionMaterial } | null>(null)
const replacementFiles = ref<UploadFileInfo[]>([])
const reviewing = ref<{ record: ExemptionRequest; stage: 'first' | 'second' } | null>(null)

const canApply = computed(() => userStore.hasPerm('exemption:apply'))
const canFirstReview = computed(() => userStore.hasPerm('exemption:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('exemption:secondReview'))
const selfMode = computed(() => canApply.value && !canFirstReview.value && !canSecondReview.value)

const form = reactive({
  studentId: '',
  assessmentYear: '2026',
  teachingSegment: '',
  rows: [] as SubjectRow[]
})

const reviewForm = reactive<ReviewPayload>({
  action: 'PASS',
  comment: ''
})

const statusOptions: SelectOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待初审', value: 'FIRST_REVIEW' },
  { label: '初审退回', value: 'FIRST_REJECTED' },
  { label: '待复审', value: 'SECOND_REVIEW' },
  { label: '复审退回', value: 'SECOND_REJECTED' },
  { label: '复审通过', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' }
]

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const segmentOptions = computed<SelectOption[]>(() =>
  segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const subjectOptions = computed<SelectOption[]>(() =>
  subjects.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const basisOptions = computed<SelectOption[]>(() =>
  bases.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)

const columns: DataTableColumns<ExemptionRequest> = [
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '学段', key: 'teachingSegmentLabel', width: 120, ellipsis: { tooltip: true } },
  { title: '免考科目', key: 'subjectLabel', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '依据', key: 'basisLabel', width: 150, ellipsis: { tooltip: true } },
  { title: '佐证', key: 'materials', width: 95, render: (row) => `${row.materials.length} 份` },
  { title: '应考', key: 'includedInExam', width: 80, render: (row) => (row.includedInExam === 0 ? '移出' : '保留') },
  { title: '状态', key: 'finalStatus', width: 110, render: (row) => statusTag(row) },
  {
    title: '操作',
    key: 'actions',
    width: 380,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        row.materials[0]
          ? h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => preview(row.materials[0]) }, { default: () => '预览' })
          : null,
        canApply.value ? h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }) : null,
        canApply.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReplace(row) }, { default: () => '换佐证' })
          : null,
        canFirstReview.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' })
          : null,
        canSecondReview.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' })
          : null,
        canApply.value && row.materials[0]
          ? h(
              NPopconfirm,
              { onPositiveClick: () => removeMaterial(row.materials[0]) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删佐证' }),
                default: () => '确认删除该佐证？'
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
    const res = await listExemptions({
      keyword: keyword.value,
      status: statusFilter.value,
      assessmentYear: assessmentYear.value,
      teachingSegment: segmentFilter.value
    })
    records.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, segmentRes, basisRes] = await Promise.all([
    listStudents(),
    listDictItems('teaching_segment', true),
    listDictItems('exemption_basis', true)
  ])
  students.value = selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records
  segments.value = segmentRes.data
  bases.value = basisRes.data
}

async function loadSubjects(segment: string | null) {
  const res = await getExemptionSubjects(segment)
  subjects.value = res.data
}

async function handleSegmentChange(value: string | null) {
  form.teachingSegment = value || ''
  form.rows = []
  await loadSubjects(value)
}

function openApply() {
  form.studentId = selfMode.value ? userStore.currentUser?.studentId || students.value[0]?.id || '' : ''
  form.assessmentYear = assessmentYear.value
  form.teachingSegment = segmentFilter.value || ''
  form.rows = []
  if (form.teachingSegment) void loadSubjects(form.teachingSegment)
  drawerVisible.value = true
}

function addSubjectRow() {
  form.rows.push({ subject: '', basis: bases.value[0]?.itemCode || '', remark: '', fileList: [] })
}

function removeSubjectRow(index: number) {
  form.rows.splice(index, 1)
}

async function saveApply() {
  if (!form.studentId || !form.assessmentYear || !form.teachingSegment || form.rows.length === 0) {
    message.error('请选择学生、年度、学段和免考科目')
    return
  }
  const items: ExemptionApplyItem[] = []
  for (const row of form.rows) {
    if (!row.subject || !row.basis || !row.fileList[0]?.file) {
      message.error('每科必须填写依据并上传佐证')
      return
    }
    items.push({ subject: row.subject, basis: row.basis, remark: row.remark })
  }
  saving.value = true
  try {
    const res = await applyExemption({
      studentId: form.studentId,
      assessmentYear: form.assessmentYear,
      teachingSegment: form.teachingSegment,
      items
    })
    await Promise.all(
      res.data.map((id, index) => uploadExemptionMaterial(id, form.rows[index].fileList[0].file as File))
    )
    message.success('已保存免考申请')
    drawerVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

function openReplace(row: ExemptionRequest) {
  if (!row.materials[0]) {
    message.error('该科还没有佐证')
    return
  }
  replacingMaterial.value = { record: row, material: row.materials[0] }
  replacementFiles.value = []
  replaceVisible.value = true
}

async function saveReplace() {
  const file = replacementFiles.value[0]?.file
  if (!replacingMaterial.value || !file) {
    message.error('请选择附件')
    return
  }
  await replaceExemptionMaterial(replacingMaterial.value.material.id, file)
  message.success('已替换佐证')
  replacingMaterial.value = null
  replaceVisible.value = false
  await loadRecords()
}

async function removeMaterial(material: ExemptionMaterial) {
  await deleteExemptionMaterial(material.id)
  message.success('已删除佐证')
  await loadRecords()
}

async function preview(material: ExemptionMaterial) {
  const res = await previewExemptionMaterial(material.id)
  window.open(res.data, '_blank', 'noopener,noreferrer')
}

async function submit(row: ExemptionRequest) {
  await submitExemption(row.id)
  message.success('已提交')
  await loadRecords()
}

function openReview(row: ExemptionRequest, stage: 'first' | 'second') {
  reviewing.value = { record: row, stage }
  reviewForm.action = 'PASS'
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function saveReview() {
  if (!reviewing.value) return
  if (reviewForm.action !== 'PASS' && !reviewForm.comment?.trim()) {
    message.error('退回或不通过必须填写原因')
    return
  }
  if (reviewing.value.stage === 'first') await firstReviewExemption(reviewing.value.record.id, reviewForm)
  else await secondReviewExemption(reviewing.value.record.id, reviewForm)
  message.success('审核完成')
  reviewVisible.value = false
  await loadRecords()
}

async function showExamSubjects() {
  const studentId = form.studentId || records.value[0]?.studentId || userStore.currentUser?.studentId || ''
  const segment = segmentFilter.value || records.value[0]?.teachingSegment || form.teachingSegment
  if (!studentId || !segment) {
    message.error('请选择学段并查询到学生记录')
    return
  }
  const res = await getExamSubjects(studentId, assessmentYear.value, segment)
  examSubjects.value = res.data
  examVisible.value = true
}

function statusTag(row: ExemptionRequest) {
  const type = row.finalStatus === 'PASSED' ? 'success' : row.finalStatus === 'FAILED' ? 'error' : row.finalStatus.includes('REJECTED') ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel })
}

onMounted(async () => {
  await loadOptions()
  await loadSubjects(null)
  await loadRecords()
})
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between" align="center">
      <n-space>
        <n-input v-model:value="keyword" clearable placeholder="科目/依据/说明" style="width: 210px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="segmentFilter" clearable :options="segmentOptions" placeholder="学段" style="width: 150px" @update:value="loadSubjects" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 145px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
      <n-space>
        <n-button @click="showExamSubjects">应考口径</n-button>
        <n-button v-if="canApply" type="primary" @click="openApply">免考申请</n-button>
      </n-space>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: ExemptionRequest) => row.id" :scroll-x="1370" />
  </n-space>

  <n-drawer v-model:show="drawerVisible" :width="680">
    <n-drawer-content title="免考申请" closable>
      <n-space vertical>
        <n-select v-model:value="form.studentId" :options="studentOptions" :disabled="selfMode" placeholder="学生" />
        <n-input v-model:value="form.assessmentYear" placeholder="考核年度" />
        <n-select v-model:value="form.teachingSegment" :options="segmentOptions" placeholder="任教学段" @update:value="handleSegmentChange" />
        <n-space justify="space-between" align="center">
          <span>免考科目</span>
          <n-button size="small" @click="addSubjectRow">添加科目</n-button>
        </n-space>
        <n-space v-for="(row, index) in form.rows" :key="index" vertical class="subject-row">
          <n-space align="center">
            <n-select v-model:value="row.subject" :options="subjectOptions" placeholder="科目" style="width: 210px" />
            <n-select v-model:value="row.basis" :options="basisOptions" placeholder="依据" style="width: 190px" />
            <n-button quaternary type="error" @click="removeSubjectRow(index)">删除</n-button>
          </n-space>
          <n-input v-model:value="row.remark" type="textarea" placeholder="说明" />
          <n-upload v-model:file-list="row.fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
        </n-space>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveApply">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="replaceVisible" preset="dialog" title="替换免考佐证" @close="replacingMaterial = null">
    <n-space vertical>
      <n-upload v-model:file-list="replacementFiles" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
      <n-space justify="end">
        <n-button @click="replaceVisible = false; replacingMaterial = null">取消</n-button>
        <n-button type="primary" @click="saveReplace">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="reviewVisible" preset="dialog" :title="reviewing?.stage === 'first' ? '初审' : '复审'">
    <n-space vertical>
      <n-select
        v-model:value="reviewForm.action"
        :options="[
          { label: '通过', value: 'PASS' },
          { label: '退回', value: 'REJECT' },
          { label: '不通过', value: 'FAIL' }
        ]"
      />
      <n-input v-model:value="reviewForm.comment" type="textarea" placeholder="退回或不通过必须填写原因" />
      <n-space justify="end">
        <n-button @click="reviewVisible = false">取消</n-button>
        <n-button type="primary" @click="saveReview">确认</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="examVisible" preset="card" title="应考科目口径" style="width: 620px">
    <n-data-table :columns="examColumns" :data="examSubjects" :pagination="false" />
  </n-modal>
</template>

<style scoped>
.subject-row {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
</style>
