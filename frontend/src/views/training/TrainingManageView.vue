<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import TrainingDrawer from './components/TrainingDrawer.vue'
import TrainingDetail from './components/TrainingDetail.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { listColleges, listMajors, type College, type Major } from '@/api/organization'
import {
  firstReviewTrainingProfile,
  listTrainingProfiles,
  secondReviewTrainingProfile,
  submitTrainingProfile,
  type ReviewPayload,
  type TrainingProfile
} from '@/api/training'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

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
const reviewSaving = ref(false)
const submitting = ref(false)
const reviewVisible = ref(false)
const reviewing = ref<{ profile: TrainingProfile; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const collegeFilter = ref<string | null>(null)
const records = ref<TrainingProfile[]>([])
const trainingTotal = ref(0)
const page = ref(1)
const size = ref(20)
const colleges = ref<College[]>([])
const majors = ref<Major[]>([])
const educationLevels = ref<DictItem[]>([])
const trainingGoals = ref<DictItem[]>([])
const internshipModes = ref<DictItem[]>([])
const internshipLocations = ref<DictItem[]>([])
const segments = ref<DictItem[]>([])
const interviewModes = ref<DictItem[]>([])
const conclusions = ref<DictItem[]>([])
const drawerRef = ref<InstanceType<typeof TrainingDrawer> | null>(null)
const detailRef = ref<InstanceType<typeof TrainingDetail> | null>(null)
let listRequestSequence = 0
let optionsRequestSequence = 0

const statusOptions: SelectOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待初审', value: 'FIRST_REVIEW' },
  { label: '初审退回', value: 'FIRST_REJECTED' },
  { label: '待复审', value: 'SECOND_REVIEW' },
  { label: '复审退回', value: 'SECOND_REJECTED' },
  { label: '复审通过', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' }
]

const canEdit = computed(() => userStore.hasPerm('training:edit'))
const canSelfConfirm = computed(() => userStore.hasPerm('training:confirm'))
const canFirstReview = computed(() => userStore.hasPerm('info:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('info:secondReview'))
const canViewStudents = computed(() => userStore.hasPerm('student:view'))
const selfMode = computed(() => canSelfConfirm.value && !canEdit.value && !userStore.hasPerm('student:view'))
const canCreate = computed(() => canEdit.value || canSelfConfirm.value)
const listQueryKey = computed(() => JSON.stringify([
  keyword.value,
  assessmentYear.value,
  statusFilter.value || '',
  collegeFilter.value || '',
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
const optionsReady = computed(() =>
  hasLoadedOptionsSuccessfully.value && !optionsLoading.value && !optionsError.value
)

const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))

const columns: DataTableColumns<TrainingProfile> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '学院', key: 'collegeId', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => collegeName(row.collegeId) },
  { title: '专业', key: 'internalMajorName', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '培养目标', key: 'trainingGoal', minWidth: 130, render: (row) => dictLabel(trainingGoals.value, row.trainingGoal) },
  { title: '实习地点', key: 'internshipLocation', minWidth: 120, render: (row) => dictLabel(internshipLocations.value, row.internshipLocation) },
  { title: '学段', key: 'teachingSegment', minWidth: 120, render: (row) => dictLabel(segments.value, row.teachingSegment) },
  { title: '学科', key: 'teachingSubjectName', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 220,
    render: (row) =>
      {
        const actions = [
          h(NButton, { size: 'small', type: 'primary', onClick: () => openDetail(row) }, { default: () => '详情' })
        ]
        if (canCreate.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value || !optionsReady.value, onClick: () => openDrawer(row) }, { default: () => '编辑' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value || submitting.value, onClick: () => submit(row) }, { default: () => '提交' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
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
    collegeId: collegeFilter.value,
    assessmentYear: assessmentYear.value,
    page: page.value,
    size: size.value
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await listTrainingProfiles(query)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    records.value = res.data.records
    trainingTotal.value = res.data.total
    hasLoadedSuccessfully.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = showError(error, '专业培养列表加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// 筛选变更（关键词/年度/学院/状态）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
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
    const [
      collegeRes,
      majorRes,
      educationRes,
      goalRes,
      modeRes,
      locationRes,
      segmentRes,
      interviewRes,
      conclusionRes
    ] = await Promise.all([
      canViewStudents.value ? listColleges() : Promise.resolve(null),
      canViewStudents.value ? listMajors({ pilotScopeFlag: 1, status: 1 }) : Promise.resolve(null),
      listDictItems('education_level', true),
      listDictItems('training_goal', true),
      listDictItems('internship_org_mode', true),
      listDictItems('internship_location', true),
      listDictItems('teaching_segment', true),
      listDictItems('interview_org_mode', true),
      listDictItems('ability_test_conclusion', true)
    ])
    if (requestSequence !== optionsRequestSequence) return
    colleges.value = collegeRes?.data || []
    majors.value = majorRes?.data || []
    educationLevels.value = educationRes.data
    trainingGoals.value = goalRes.data
    internshipModes.value = modeRes.data
    internshipLocations.value = locationRes.data
    segments.value = segmentRes.data
    interviewModes.value = interviewRes.data
    conclusions.value = conclusionRes.data
    hasLoadedOptionsSuccessfully.value = true
  } catch (error) {
    if (requestSequence !== optionsRequestSequence) return
    optionsError.value = showError(error, '培养信息选项加载失败')
  } finally {
    if (requestSequence === optionsRequestSequence) optionsLoading.value = false
  }
}

function openDetail(row: TrainingProfile) {
  detailRef.value?.open(row)
}

function openDrawer(row?: TrainingProfile) {
  if (writeBlocked.value || !optionsReady.value) return
  drawerRef.value?.open(row)
}

async function submit(row: TrainingProfile) {
  if (writeBlocked.value || submitting.value) return
  submitting.value = true
  try {
    await submitTrainingProfile(row.id)
    message.success('已提交')
    await loadRecords()
  } catch (error) {
    showError(error, '提交失败')
  } finally {
    submitting.value = false
  }
}

function openReview(row: TrainingProfile, stage: 'first' | 'second') {
  if (writeBlocked.value) return
  reviewing.value = { profile: row, stage }
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
    if (reviewing.value.stage === 'first') await firstReviewTrainingProfile(reviewing.value.profile.id, payload)
    else await secondReviewTrainingProfile(reviewing.value.profile.id, payload)
    message.success('审核完成')
    reviewVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '审核失败')
  } finally {
    reviewSaving.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = null
  collegeFilter.value = null
  search()
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function collegeName(id?: string | null) {
  return colleges.value.find((item) => item.id === id)?.name || id || '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  const text = detail || fallback
  message.error(text)
  return text
}

onMounted(() => {
  void loadOptions()
  void loadRecords()
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
  <PageContainer title="专业培养信息" description="专业培养信息维护，培养目标、学段与学科按标准联动。">
    <n-alert v-if="optionsError" type="warning" :bordered="false" class="page-section">
      培养信息列表已独立加载，但表单选项加载失败；新增和编辑暂不可用。{{ optionsError }}
      <n-button text type="warning" size="small" :loading="optionsLoading" @click="loadOptions">重试加载选项</n-button>
    </n-alert>

    <FilterBar :loading="loading" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学生 / 专业 / 学科" style="width: 220px" @keyup.enter="search" />
      </label>
      <label class="filter-field">
        <span>记录年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>学院</span>
        <n-select v-model:value="collegeFilter" clearable filterable :loading="optionsLoading" :options="collegeOptions" placeholder="全部学院" style="width: 200px" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
    </FilterBar>

    <DataPanel
      title="培养信息列表"
      :columns="columns"
      :data="records"
      :total="trainingTotal"
      :loading="loading"
      :error="loadError"
      remote
      :page="page"
      :page-size="size"
      empty-title="暂无培养信息"
      empty-description="当前筛选条件下没有专业培养信息。"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button v-if="canCreate && records.length > 0" type="primary" size="small" :disabled="writeBlocked || !optionsReady" :loading="optionsLoading" @click="openDrawer()">新增培养信息</n-button>
      </template>
      <template v-if="canCreate" #emptyAction>
        <n-button type="primary" :disabled="writeBlocked || !optionsReady" :loading="optionsLoading" @click="openDrawer()">新增培养信息</n-button>
      </template>
    </DataPanel>

    <TrainingDrawer
      ref="drawerRef"
      :majors="majors"
      :education-levels="educationLevels"
      :training-goals="trainingGoals"
      :internship-modes="internshipModes"
      :internship-locations="internshipLocations"
      :segments="segments"
      :interview-modes="interviewModes"
      :conclusions="conclusions"
      :self-mode="selfMode"
      :assessment-year="assessmentYear"
      @saved="loadRecords"
    />

    <TrainingDetail
      ref="detailRef"
      :colleges="colleges"
      :education-levels="educationLevels"
      :training-goals="trainingGoals"
      :internship-modes="internshipModes"
      :internship-locations="internshipLocations"
      :segments="segments"
      :interview-modes="interviewModes"
      :conclusions="conclusions"
    />

    <ReviewDialog
      v-model:show="reviewVisible"
      :title="reviewing?.stage === 'first' ? '培养信息初审' : '培养信息复审'"
      :loading="reviewSaving"
      allow-fail
      :summary="reviewing ? [
        { label: '学号', value: reviewing.profile.studentNo },
        { label: '姓名', value: reviewing.profile.studentName },
        { label: '当前状态', status: reviewing.profile.status }
      ] : []"
      @submit="saveReview"
    />
  </PageContainer>
</template>
