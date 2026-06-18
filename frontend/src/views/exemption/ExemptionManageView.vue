<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
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
const reviewSaving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const examVisible = ref(false)
const replaceVisible = ref(false)
const previewVisible = ref(false)
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
const replacingMaterial = ref<{ record: ExemptionRequest; material: ExemptionMaterial } | null>(null)
const replacementFiles = ref<UploadFileInfo[]>([])
const reviewing = ref<{ record: ExemptionRequest; stage: 'first' | 'second' } | null>(null)
const previewMaterialRow = ref<ExemptionMaterial | null>(null)
const previewUrl = ref('')

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

const reviewActionOptions: SelectOption[] = [
  { label: '通过', value: 'PASS' },
  { label: '退回', value: 'REJECT' },
  { label: '不通过', value: 'FAIL' }
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
const statusSummary = computed(() => {
  const passed = records.value.filter((item) => item.finalStatus === 'PASSED').length
  const removed = records.value.filter((item) => item.includedInExam === 0).length
  const pending = records.value.filter((item) => ['FIRST_REVIEW', 'SECOND_REVIEW'].includes(item.finalStatus)).length
  return { total: records.value.length, passed, removed, pending }
})
const previewable = computed(() => {
  const material = previewMaterialRow.value
  if (!material) return false
  const name = material.fileName.toLowerCase()
  const type = material.contentType || ''
  return type.includes('pdf') || type.includes('image') || /\.(pdf|jpg|jpeg|png)$/.test(name)
})

const columns: DataTableColumns<ExemptionRequest> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '学段', key: 'teachingSegmentLabel', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '免考科目', key: 'subjectLabel', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '依据', key: 'basisLabel', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '佐证', key: 'materials', width: 84, render: (row) => `${row.materials.length} 份` },
  { title: '应考口径', key: 'includedInExam', width: 102, render: (row) => h(StatusTag, { text: row.includedInExam === 0 ? '已移出' : '应考' }) },
  { title: '状态', key: 'finalStatus', width: 108, render: (row) => h(StatusTag, { text: row.statusLabel || row.finalStatus }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 380,
    render: (row) =>
      h(NSpace, { size: 4 }, () => {
        const actions = []
        if (row.materials[0]) {
          actions.push(h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openPreview(row.materials[0]) }, { default: () => '预览' }))
        }
        if (canApply.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReplace(row) }, { default: () => '换佐证' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        if (canApply.value && row.materials[0]) {
          actions.push(
            h(
              NPopconfirm,
              { onPositiveClick: () => removeMaterial(row.materials[0]) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删佐证' }),
                default: () => '确认删除该佐证？'
              }
            )
          )
        }
        return actions
      })
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
    const res = await listExemptions({
      keyword: keyword.value,
      status: statusFilter.value,
      assessmentYear: assessmentYear.value,
      teachingSegment: segmentFilter.value
    })
    records.value = res.data.records
  } catch (error) {
    showError(error, '免考列表加载失败')
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
  try {
    const res = await getExemptionSubjects(segment)
    subjects.value = res.data
  } catch (error) {
    showError(error, '免考科目加载失败')
  }
}

async function handleSegmentChange(value: string | number | null) {
  const segment = typeof value === 'string' ? value : null
  form.teachingSegment = segment || ''
  form.rows = []
  await loadSubjects(segment)
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
  } catch (error) {
    showError(error, '免考申请保存失败')
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
  try {
    await replaceExemptionMaterial(replacingMaterial.value.material.id, file)
    message.success('已替换佐证')
    replacingMaterial.value = null
    replaceVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '佐证替换失败')
  }
}

async function removeMaterial(material: ExemptionMaterial) {
  try {
    await deleteExemptionMaterial(material.id)
    message.success('已删除佐证')
    await loadRecords()
  } catch (error) {
    showError(error, '佐证删除失败')
  }
}

async function openPreview(material: ExemptionMaterial) {
  previewMaterialRow.value = material
  try {
    const res = await previewExemptionMaterial(material.id)
    previewUrl.value = res.data
    previewVisible.value = true
  } catch (error) {
    showError(error, '预览地址获取失败')
  }
}

async function submit(row: ExemptionRequest) {
  try {
    await submitExemption(row.id)
    message.success('已提交')
    await loadRecords()
  } catch (error) {
    showError(error, '提交失败')
  }
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
  reviewSaving.value = true
  try {
    if (reviewing.value.stage === 'first') await firstReviewExemption(reviewing.value.record.id, reviewForm)
    else await secondReviewExemption(reviewing.value.record.id, reviewForm)
    message.success('审核完成')
    reviewVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '审核失败')
  } finally {
    reviewSaving.value = false
  }
}

async function showExamSubjects() {
  const studentId = form.studentId || records.value[0]?.studentId || userStore.currentUser?.studentId || ''
  const segment = segmentFilter.value || records.value[0]?.teachingSegment || form.teachingSegment
  if (!studentId || !segment) {
    message.error('请选择学段并查询到学生记录')
    return
  }
  try {
    const res = await getExamSubjects(studentId, assessmentYear.value, segment)
    examSubjects.value = res.data
    examVisible.value = true
  } catch (error) {
    showError(error, '应考科目加载失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadSubjects(null)
  await loadRecords()
})
</script>

<template>
  <PageContainer title="免考管理" description="支持多科独立申请、每科佐证、二级审核与应考科目口径展示。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadRecords">刷新</n-button>
        <n-button @click="showExamSubjects">应考口径</n-button>
        <n-button v-if="canApply" type="primary" @click="openApply">免考申请</n-button>
      </n-space>
    </template>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><n-card size="small" :bordered="false"><n-statistic label="申请科次" :value="statusSummary.total" /></n-card></n-gi>
      <n-gi><n-card size="small" :bordered="false"><n-statistic label="复审通过" :value="statusSummary.passed" /></n-card></n-gi>
      <n-gi><n-card size="small" :bordered="false"><n-statistic label="已移出应考" :value="statusSummary.removed" /></n-card></n-gi>
      <n-gi><n-card size="small" :bordered="false"><n-statistic label="待审核" :value="statusSummary.pending" /></n-card></n-gi>
    </n-grid>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-input v-model:value="keyword" clearable placeholder="科目 / 依据 / 学生" style="width: 220px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="segmentFilter" clearable :options="segmentOptions" placeholder="学段" style="width: 150px" @update:value="loadSubjects" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 150px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
    </n-card>

    <n-data-table
      :columns="columns"
      :data="records"
      :loading="loading"
      :row-key="(row: ExemptionRequest) => row.id"
      :scroll-x="1520"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-drawer v-model:show="drawerVisible" :width="720">
      <n-drawer-content title="免考申请" closable>
        <n-alert type="info" :bordered="false" class="page-section">
          每个免考科目独立审核，须分别上传佐证；仅复审通过科目会从应考清单中剔除。
        </n-alert>
        <n-space vertical>
          <n-select v-model:value="form.studentId" :options="studentOptions" :disabled="selfMode" filterable placeholder="学生" />
          <n-input v-model:value="form.assessmentYear" placeholder="考核年度" class="mono-input" />
          <n-select v-model:value="form.teachingSegment" :options="segmentOptions" placeholder="任教学段" @update:value="handleSegmentChange" />
          <n-space justify="space-between" align="center">
            <span>免考科目</span>
            <n-button size="small" @click="addSubjectRow">添加科目</n-button>
          </n-space>
          <section v-for="(row, index) in form.rows" :key="index" class="subject-row">
            <n-space vertical>
              <n-space align="center">
                <n-select v-model:value="row.subject" :options="subjectOptions" placeholder="科目" style="width: 210px" />
                <n-select v-model:value="row.basis" :options="basisOptions" placeholder="依据" style="width: 190px" />
                <n-button quaternary type="error" @click="removeSubjectRow(index)">删除</n-button>
              </n-space>
              <n-input v-model:value="row.remark" type="textarea" placeholder="说明" />
              <n-upload v-model:file-list="row.fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
            </n-space>
          </section>
        </n-space>
        <template #footer>
          <n-space justify="end">
            <n-button @click="drawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveApply">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="previewVisible" preset="card" :title="previewMaterialRow?.fileName || '免考佐证预览'" style="width: min(960px, 94vw)">
      <object v-if="previewable" :data="previewUrl" class="preview-frame">
        <iframe :src="previewUrl" class="preview-frame" />
      </object>
      <n-result v-else status="info" title="该文件不支持内联预览" description="非 PDF/JPG/PNG 文件请通过下载链接查看。">
        <template #footer>
          <n-button tag="a" :href="previewUrl" target="_blank" type="primary">打开文件</n-button>
        </template>
      </n-result>
    </n-modal>

    <n-modal v-model:show="replaceVisible" preset="dialog" title="替换免考佐证" @close="replacingMaterial = null">
      <n-space vertical>
        <n-alert v-if="replacingMaterial" type="info" :bordered="false">
          {{ replacingMaterial.record.studentNo }} / {{ replacingMaterial.record.subjectLabel }}
        </n-alert>
        <n-upload v-model:file-list="replacementFiles" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
        <n-space justify="end">
          <n-button @click="replaceVisible = false; replacingMaterial = null">取消</n-button>
          <n-button type="primary" @click="saveReplace">保存</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="reviewVisible" preset="dialog" :title="reviewing?.stage === 'first' ? '免考初审' : '免考复审'">
      <n-space vertical>
        <n-alert v-if="reviewing" type="info" :bordered="false">
          {{ reviewing.record.studentNo }} / {{ reviewing.record.studentName }} / {{ reviewing.record.subjectLabel }} / 当前：{{ reviewing.record.statusLabel }}
        </n-alert>
        <n-select v-model:value="reviewForm.action" :options="reviewActionOptions" />
        <n-input v-model:value="reviewForm.comment" type="textarea" placeholder="退回或不通过必须填写原因" />
        <n-space justify="end">
          <n-button @click="reviewVisible = false">取消</n-button>
          <n-button type="primary" :loading="reviewSaving" @click="saveReview">确认</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="examVisible" preset="card" title="应考科目口径" style="width: 680px">
      <n-data-table :columns="examColumns" :data="examSubjects" :pagination="false" />
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}

.subject-row {
  padding: 12px;
  border: 1px solid var(--shell-border);
  border-radius: 8px;
}

.preview-frame {
  width: 100%;
  height: min(70vh, 720px);
  border: 1px solid var(--shell-border);
  border-radius: 6px;
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
