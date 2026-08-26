<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NIcon,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import { EyeOutline } from '@vicons/ionicons5'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import ExemptionDrawer from './components/ExemptionDrawer.vue'
import ExemptionExamModal from './components/ExemptionExamModal.vue'
import ExemptionPreviewModal from './components/ExemptionPreviewModal.vue'
import ExemptionReplaceModal from './components/ExemptionReplaceModal.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatFileSize } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import type { ReviewPayload } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  deleteExemptionMaterial,
  firstReviewExemption,
  getExemptionSubjects,
  listExemptions,
  secondReviewExemption,
  submitExemption,
  type ExemptionMaterial,
  type ExemptionRequest
} from '@/api/exemption'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const loadError = ref('')
const hasLoadedSuccessfully = ref(false)
const loadedQueryKey = ref('')
const optionsLoading = ref(false)
const optionsError = ref('')
const hasLoadedOptionsSuccessfully = ref(false)
const subjectsLoading = ref(false)
const subjectsError = ref('')
const hasLoadedSubjectsSuccessfully = ref(false)
const loadedSubjectsSegment = ref<string | null>(null)
const reviewSaving = ref(false)
const reviewVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const segmentFilter = ref<string | null>(null)
const records = ref<ExemptionRequest[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const segments = ref<DictItem[]>([])
const subjects = ref<DictItem[]>([])
const bases = ref<DictItem[]>([])
const reviewing = ref<{ record: ExemptionRequest; stage: 'first' | 'second' } | null>(null)

const drawerRef = ref<InstanceType<typeof ExemptionDrawer>>()
const previewModalRef = ref<InstanceType<typeof ExemptionPreviewModal>>()
const replaceModalRef = ref<InstanceType<typeof ExemptionReplaceModal>>()
const examModalRef = ref<InstanceType<typeof ExemptionExamModal>>()
let listRequestSequence = 0
let optionsRequestSequence = 0
let subjectsRequestSequence = 0

const canApply = computed(() => userStore.hasPerm('exemption:apply'))
const canFirstReview = computed(() => userStore.hasPerm('exemption:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('exemption:secondReview'))
const selfMode = computed(() => canApply.value && !canFirstReview.value && !canSecondReview.value)
const listQueryKey = computed(() => JSON.stringify([
  keyword.value,
  assessmentYear.value,
  statusFilter.value || '',
  segmentFilter.value || '',
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
const applicationOptionsReady = computed(() =>
  hasLoadedOptionsSuccessfully.value
  && hasLoadedSubjectsSuccessfully.value
  && !optionsLoading.value
  && !subjectsLoading.value
  && !optionsError.value
  && !subjectsError.value
)
const applicationOptionsFeedback = computed(() => optionsError.value || subjectsError.value)

const statusOptions: SelectOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待初审', value: 'FIRST_REVIEW' },
  { label: '初审退回', value: 'FIRST_REJECTED' },
  { label: '待复审', value: 'SECOND_REVIEW' },
  { label: '复审退回', value: 'SECOND_REJECTED' },
  { label: '复审通过', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' }
]

const segmentOptions = computed<SelectOption[]>(() =>
  segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
// Phase 44e-rollout（P1-1 真分页）：records 真分页后仅为当页数据。「申请科次」改绑后端 total ref（全量口径，
// 不受页大小影响）；passed/removed/pending 三项统计仍按当页 records 计算——只反映当页，非全量口径（与
// launch-readiness-plan.md 附录同类「列表本地算 summary」备注一致：机械铺开阶段不新增后端聚合接口，暂标注）。
const statusSummary = computed(() => {
  const passed = records.value.filter((item) => item.finalStatus === 'PASSED').length
  const removed = records.value.filter((item) => item.includedInExam === 0).length
  const pending = records.value.filter((item) => ['FIRST_REVIEW', 'SECOND_REVIEW'].includes(item.finalStatus)).length
  return { passed, removed, pending }
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
              { size: 'small', type: 'primary', onClick: () => previewModalRef.value?.open(row.materials[0]) },
              { icon: () => h(NIcon, { component: EyeOutline }), default: () => '预览' }
            )
          )
        }
        if (canApply.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => submit(row) }, { default: () => '提交' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReplace(row) }, { default: () => '换佐证' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        if (canApply.value && row.materials[0]) {
          actions.push(
            h(
              NPopconfirm,
              { onPositiveClick: () => removeMaterial(row.materials[0]) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error', disabled: writeBlocked.value }, { default: () => '删佐证' }),
                default: () => '确认删除该佐证？'
              }
            )
          )
        }
        return renderTableActions(actions)
      }
  }
]

async function loadRecords() {
  const requestSequence = ++listRequestSequence
  const queryKey = listQueryKey.value
  const query = {
    keyword: keyword.value,
    status: statusFilter.value,
    assessmentYear: assessmentYear.value,
    teachingSegment: segmentFilter.value,
    page: page.value,
    size: size.value
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await listExemptions(query)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    records.value = res.data.records
    total.value = res.data.total
    hasLoadedSuccessfully.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = showError(error, '免考列表加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// 筛选变更（关键词/状态/学段/年度）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
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
  const requestSequence = ++optionsRequestSequence
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const [segmentRes, basisRes] = await Promise.all([
      listDictItems('teaching_segment', true),
      listDictItems('exemption_basis', true)
    ])
    if (requestSequence !== optionsRequestSequence) return
    segments.value = segmentRes.data
    bases.value = basisRes.data
    hasLoadedOptionsSuccessfully.value = true
  } catch (error) {
    if (requestSequence !== optionsRequestSequence) return
    optionsError.value = showError(error, '免考选项加载失败')
  } finally {
    if (requestSequence === optionsRequestSequence) optionsLoading.value = false
  }
}

async function loadSubjects(segment: string | null) {
  const requestSequence = ++subjectsRequestSequence
  const requestedSegment = segment || null
  subjectsLoading.value = true
  subjectsError.value = ''
  try {
    const res = await getExemptionSubjects(requestedSegment)
    if (requestSequence !== subjectsRequestSequence) return
    subjects.value = res.data
    hasLoadedSubjectsSuccessfully.value = true
    loadedSubjectsSegment.value = requestedSegment
  } catch (error) {
    if (requestSequence !== subjectsRequestSequence) return
    subjectsError.value = showError(error, '免考科目加载失败')
  } finally {
    if (requestSequence === subjectsRequestSequence) subjectsLoading.value = false
  }
}

async function reloadApplicationOptions() {
  await Promise.all([loadOptions(), loadSubjects(segmentFilter.value)])
}

function openApplication() {
  if (writeBlocked.value || !applicationOptionsReady.value) return
  drawerRef.value?.open()
}

function openReplace(row: ExemptionRequest) {
  if (writeBlocked.value) return
  replaceModalRef.value?.open(row)
}

async function removeMaterial(material: ExemptionMaterial) {
  if (writeBlocked.value) return
  try {
    await deleteExemptionMaterial(material.id)
    message.success('已删除佐证')
    await loadRecords()
  } catch (error) {
    showError(error, '佐证删除失败')
  }
}

async function submit(row: ExemptionRequest) {
  if (writeBlocked.value) return
  try {
    await submitExemption(row.id)
    message.success('已提交')
    await loadRecords()
  } catch (error) {
    showError(error, '提交失败')
  }
}

function openReview(row: ExemptionRequest, stage: 'first' | 'second') {
  if (writeBlocked.value) return
  reviewing.value = { record: row, stage }
  reviewVisible.value = true
}

async function saveReview(payload: ReviewPayload) {
  if (!reviewing.value || writeBlocked.value) return
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
  const form = drawerRef.value?.form
  const studentId = form?.studentId || records.value[0]?.studentId || userStore.currentUser?.studentId || ''
  const segment = segmentFilter.value || records.value[0]?.teachingSegment || form?.teachingSegment
  if (!studentId || !segment) {
    message.error('请选择学段并查询到学生记录')
    return
  }
  await examModalRef.value?.open(studentId, assessmentYear.value, segment)
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = null
  segmentFilter.value = null
  void loadSubjects(null)
  search()
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
  const text = detail || fallback
  message.error(text)
  return text
}

onMounted(() => {
  // 并行加载，避免多段串行造成的骨架屏二次闪烁
  void Promise.all([reloadApplicationOptions(), loadRecords()])
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    page.value = 1
    await loadRecords()
  }
)
</script>

<template>
  <PageContainer title="免考管理" description="多科免考申请、佐证上传、二级审核与应考科目展示。">
    <n-alert v-if="applicationOptionsFeedback" type="warning" title="免考申请选项加载失败" :bordered="false" class="page-section" role="alert">
      免考列表仍可查看，但申请入口暂不可用。{{ applicationOptionsFeedback }}
      <n-button text type="warning" size="small" :loading="optionsLoading || subjectsLoading" @click="reloadApplicationOptions">重试加载选项</n-button>
    </n-alert>

    <n-grid v-if="hasLoadedSuccessfully" cols="1 440:2 900:4" :x-gap="12" :y-gap="12" responsive="self" class="page-section">
      <n-gi><StatCard label="申请科次" :value="total" /></n-gi>
      <n-gi><StatCard label="复审通过" :value="statusSummary.passed" tone="success" /></n-gi>
      <n-gi><StatCard label="已移出应考" :value="statusSummary.removed" tone="info" /></n-gi>
      <n-gi><StatCard label="待审核" :value="statusSummary.pending" tone="warning" /></n-gi>
    </n-grid>

    <FilterBar :loading="loading" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="科目 / 依据 / 学生" style="width: 220px" @keyup.enter="search" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>学段</span>
        <n-select v-model:value="segmentFilter" clearable :loading="optionsLoading || subjectsLoading" :options="segmentOptions" placeholder="全部学段" style="width: 150px" @update:value="handleFilterSegmentChange" />
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
      :total="total"
      :loading="loading"
      :error="loadError"
      remote
      :page="page"
      :page-size="size"
      empty-title="暂无免考申请"
      empty-description="当前筛选条件下没有免考申请记录。"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button size="small" @click="showExamSubjects">应考口径</n-button>
        <n-button
          v-if="canApply && records.length > 0"
          type="primary"
          size="small"
          :disabled="writeBlocked || !applicationOptionsReady"
          :loading="optionsLoading || subjectsLoading"
          @click="openApplication"
        >免考申请</n-button>
      </template>
      <template v-if="canApply" #emptyAction>
        <n-button type="primary" :disabled="writeBlocked || !applicationOptionsReady" :loading="optionsLoading || subjectsLoading" @click="openApplication">免考申请</n-button>
      </template>
    </DataPanel>

    <ExemptionDrawer
      ref="drawerRef"
      :subjects="subjects"
      :bases="bases"
      :segment-options="segmentOptions"
      :self-mode="selfMode"
      :assessment-year="assessmentYear"
      :segment-filter="segmentFilter"
      :subjects-loading="subjectsLoading"
      :subjects-error="subjectsError"
      :subjects-loaded-successfully="hasLoadedSubjectsSuccessfully"
      :subjects-loaded-segment="loadedSubjectsSegment"
      @saved="loadRecords"
      @load-subjects="loadSubjects"
    />

    <ExemptionPreviewModal ref="previewModalRef" />

    <ExemptionReplaceModal ref="replaceModalRef" @saved="loadRecords" />

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

    <ExemptionExamModal ref="examModalRef" />
  </PageContainer>
</template>

<style scoped>
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
