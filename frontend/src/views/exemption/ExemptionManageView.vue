<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NIcon,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import { EyeOutline } from '@vicons/ionicons5'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatFileSize } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import type { ReviewPayload } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
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
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const reviewSaving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const examVisible = ref(false)
const replaceVisible = ref(false)
const previewVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
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
const canViewStudents = computed(() => userStore.hasPerm('student:view'))
const selfMode = computed(() => canApply.value && !canFirstReview.value && !canSecondReview.value)

const form = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  teachingSegment: '',
  rows: [] as SubjectRow[]
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
  { title: '学段', key: 'teachingSegmentLabel', minWidth: 120, ellipsis: { tooltip: true }, render: (row) => row.teachingSegmentLabel || dictLabel(segments.value, row.teachingSegment) },
  { title: '免考科目', key: 'subjectLabel', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => row.subjectLabel || dictLabel(subjects.value, row.subject) },
  { title: '依据', key: 'basisLabel', minWidth: 150, ellipsis: { tooltip: true }, render: (row) => row.basisLabel || dictLabel(bases.value, row.basis) },
  { title: '佐证', key: 'materials', minWidth: 170, render: (row) => renderEvidence(row.materials) },
  { title: '应考口径', key: 'includedInExam', width: 102, render: (row) => h(StatusTag, { text: row.includedInExam === 0 ? '已移出' : '应考' }) },
  { title: '状态', key: 'finalStatus', width: 108, render: (row) => h(StatusTag, { value: row.finalStatus, text: row.statusLabel || statusLabel(row.finalStatus) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 240,
    render: (row) =>
      {
        const actions = []
        if (row.materials[0]) {
          actions.push(
            h(
              NButton,
              { size: 'small', type: 'primary', onClick: () => openPreview(row.materials[0]) },
              { icon: () => h(NIcon, { component: EyeOutline }), default: () => '预览' }
            )
          )
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
        return renderTableActions(actions)
      }
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
    canViewStudents.value ? listStudents() : Promise.resolve(null),
    listDictItems('teaching_segment', true),
    listDictItems('exemption_basis', true)
  ])
  students.value = studentRes ? (selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records) : []
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
  reviewVisible.value = true
}

async function saveReview(payload: ReviewPayload) {
  if (!reviewing.value) return
  if (payload.action !== 'PASS' && !payload.comment?.trim()) {
    message.error('退回或不通过必须填写原因')
    return
  }
  reviewSaving.value = true
  try {
    if (reviewing.value.stage === 'first') await firstReviewExemption(reviewing.value.record.id, payload)
    else await secondReviewExemption(reviewing.value.record.id, payload)
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

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = null
  segmentFilter.value = null
  void loadSubjects(null)
  void loadRecords()
}

function handleFilterSegmentChange(value: string | number | null) {
  void loadSubjects(typeof value === 'string' ? value : null)
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function renderEvidence(materials: ExemptionMaterial[]) {
  if (!materials.length) return h('span', { class: 'evidence-muted' }, '无佐证')
  const first = materials[0]
  return h('div', { class: 'evidence-cell' }, [
    h('span', { class: 'evidence-badge' }, `${materials.length} 份`),
    h('span', { class: 'evidence-file' }, `${first.fileName || '-'} · ${formatFileSize(first.fileSize)}`)
  ])
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

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    if (!drawerVisible.value) form.assessmentYear = year
    await loadRecords()
  }
)
</script>

<template>
  <PageContainer title="免考管理" description="多科免考申请、佐证上传、二级审核与应考科目展示。">
    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="申请科次" :value="statusSummary.total" /></n-gi>
      <n-gi><StatCard label="复审通过" :value="statusSummary.passed" tone="success" /></n-gi>
      <n-gi><StatCard label="已移出应考" :value="statusSummary.removed" tone="info" /></n-gi>
      <n-gi><StatCard label="待审核" :value="statusSummary.pending" tone="warning" /></n-gi>
    </n-grid>

    <FilterBar :loading="loading" @submit="loadRecords" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="科目 / 依据 / 学生" style="width: 220px" @keyup.enter="loadRecords" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>学段</span>
        <n-select v-model:value="segmentFilter" clearable :options="segmentOptions" placeholder="全部学段" style="width: 150px" @update:value="handleFilterSegmentChange" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
    </FilterBar>

    <DataPanel
      title="免考申请列表"
      :columns="columns"
      :data="records"
      :total="records.length"
      :loading="loading"
      :scroll-x="1520"
      empty-title="暂无免考申请"
      empty-description="当前筛选条件下没有免考申请记录。"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button size="small" @click="showExamSubjects">应考口径</n-button>
        <n-button v-if="canApply" type="primary" size="small" @click="openApply">免考申请</n-button>
      </template>
      <template v-if="canApply" #emptyAction>
        <n-button type="primary" @click="openApply">免考申请</n-button>
      </template>
    </DataPanel>

    <n-drawer v-model:show="drawerVisible" :width="560">
      <n-drawer-content title="免考申请" closable>
        <n-alert type="info" :bordered="false" class="page-section">
          每个免考科目独立审核，须分别上传佐证；仅复审通过科目会从应考清单中剔除。
        </n-alert>
        <n-form label-placement="top">
          <div class="form-section-title">申请信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学生" :span="2">
              <n-select v-model:value="form.studentId" :options="studentOptions" :disabled="selfMode" filterable placeholder="学生" />
            </n-form-item-gi>
            <n-form-item-gi label="考核年度">
              <n-input v-model:value="form.assessmentYear" placeholder="考核年度" class="mono-input" />
            </n-form-item-gi>
            <n-form-item-gi label="任教学段">
              <n-select v-model:value="form.teachingSegment" :options="segmentOptions" placeholder="任教学段" @update:value="handleSegmentChange" />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">免考科目</div>
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
        </n-form>
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

    <ReviewDialog
      v-model:show="reviewVisible"
      :title="reviewing?.stage === 'first' ? '免考初审' : '免考复审'"
      :loading="reviewSaving"
      allow-fail
      :summary="reviewing ? [
        { label: '学号', value: reviewing.record.studentNo },
        { label: '姓名', value: reviewing.record.studentName },
        { label: '免考科目', value: reviewing.record.subjectLabel || dictLabel(subjects, reviewing.record.subject) },
        { label: '当前状态', status: reviewing.record.finalStatus }
      ] : []"
      @submit="saveReview"
    />

    <n-modal v-model:show="examVisible" preset="card" title="应考科目口径" style="width: 680px">
      <n-data-table :columns="examColumns" :data="examSubjects" :pagination="false" />
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.subject-row {
  padding: var(--space-4);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-card);
}

.preview-frame {
  width: 100%;
  height: min(70vh, 720px);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-card);
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}

.evidence-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.evidence-badge {
  width: fit-content;
  padding: 1px 8px;
  border-radius: 999px;
  color: var(--brand);
  background: var(--brand-soft);
  font-size: 12px;
  line-height: 20px;
}

.evidence-file {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evidence-muted {
  color: var(--text-muted);
}
</style>
