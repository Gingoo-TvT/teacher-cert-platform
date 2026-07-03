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
import { listStudents, type Student } from '@/api/student'
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
const reviewSaving = ref(false)
const submitting = ref(false)
const reviewVisible = ref(false)
const reviewing = ref<{ profile: TrainingProfile; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const collegeFilter = ref<string | null>(null)
const segmentFilter = ref<string | null>(null)
const records = ref<TrainingProfile[]>([])
const students = ref<Student[]>([])
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

const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))
const segmentFilterOptions = computed<SelectOption[]>(() => dictOptions(segments.value))

const filteredRecords = computed(() => {
  const segment = segmentFilter.value
  if (!segment) return records.value
  return records.value.filter((row) => row.teachingSegment === segment)
})

const columns: DataTableColumns<TrainingProfile> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '学院', key: 'collegeId', minWidth: 150, ellipsis: { tooltip: true }, render: (row) => collegeName(row.collegeId) },
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
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openDrawer(row) }, { default: () => '编辑' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        return renderTableActions(actions)
      }
  }
]

async function loadRecords() {
  loading.value = true
  try {
    const res = await listTrainingProfiles({
      keyword: keyword.value,
      status: statusFilter.value,
      collegeId: collegeFilter.value,
      assessmentYear: assessmentYear.value
    })
    records.value = res.data.records
  } catch (error) {
    showError(error, '专业培养列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [
    studentRes,
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
    canViewStudents.value ? listStudents() : Promise.resolve(null),
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
  students.value = studentRes ? (selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records) : []
  colleges.value = collegeRes?.data || []
  majors.value = majorRes?.data || []
  educationLevels.value = educationRes.data
  trainingGoals.value = goalRes.data
  internshipModes.value = modeRes.data
  internshipLocations.value = locationRes.data
  segments.value = segmentRes.data
  interviewModes.value = interviewRes.data
  conclusions.value = conclusionRes.data
}

function openDetail(row: TrainingProfile) {
  detailRef.value?.open(row)
}

function openDrawer(row?: TrainingProfile) {
  drawerRef.value?.open(row)
}

async function submit(row: TrainingProfile) {
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
  reviewing.value = { profile: row, stage }
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
  segmentFilter.value = null
  void loadRecords()
}

function dictOptions(items: DictItem[]): SelectOption[] {
  return items.map((item) => ({ label: item.itemValue, value: item.itemCode }))
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function collegeName(id?: string | null) {
  return colleges.value.find((item) => item.id === id)?.name || id || '-'
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
  <PageContainer title="专业培养信息" description="专业培养信息维护，培养目标、学段与学科按标准联动。">
    <FilterBar :loading="loading" @submit="loadRecords" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学生 / 专业 / 学科" style="width: 220px" @keyup.enter="loadRecords" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>学院</span>
        <n-select v-model:value="collegeFilter" clearable filterable :options="collegeOptions" placeholder="全部学院" style="width: 200px" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
      <label class="filter-field">
        <span>学段</span>
        <n-select v-model:value="segmentFilter" clearable :options="segmentFilterOptions" placeholder="全部学段" style="width: 150px" />
      </label>
    </FilterBar>

    <DataPanel
      title="培养信息列表"
      :columns="columns"
      :data="filteredRecords"
      :total="filteredRecords.length"
      :loading="loading"
      :scroll-x="1550"
      empty-title="暂无培养信息"
      empty-description="当前筛选条件下没有专业培养信息。"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button v-if="canCreate" type="primary" size="small" @click="openDrawer()">新增培养信息</n-button>
      </template>
      <template v-if="canCreate" #emptyAction>
        <n-button type="primary" @click="openDrawer()">新增培养信息</n-button>
      </template>
    </DataPanel>

    <TrainingDrawer
      ref="drawerRef"
      :students="students"
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
