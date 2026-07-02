<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type SelectOption
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import SubjectSelect from '@/components/SubjectSelect.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { listColleges, listMajors, type College, type Major } from '@/api/organization'
import { listStudents, type Student } from '@/api/student'
import {
  confirmTrainingProfile,
  firstReviewTrainingProfile,
  getTrainingOptions,
  listTrainingProfiles,
  saveTrainingProfile,
  secondReviewTrainingProfile,
  submitTrainingProfile,
  type ReviewPayload,
  type TrainingPayload,
  type TrainingProfile
} from '@/api/training'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const reviewSaving = ref(false)
const submitting = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const reviewing = ref<{ profile: TrainingProfile; stage: 'first' | 'second' } | null>(null)
const selectedProfile = ref<TrainingProfile | null>(null)
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
const allowedSegments = ref<string[]>([])
const allowedLocations = ref<string[]>([])

const form = reactive<TrainingPayload>({
  studentId: '',
  collegeId: '',
  assessmentYear: yearStore.assessmentYear,
  secondDisciplineCode: '',
  secondDisciplineName: '',
  internalMajorCode: '',
  internalMajorName: '',
  educationLevel: '',
  trainingGoal: '',
  internshipOrgMode: '',
  internshipLocation: '',
  teachingSegment: '',
  teachingSubjectCode: '',
  interviewOrgMode: '',
  abilityTestConclusion: ''
})

const rules: FormRules = {
  studentId: [{ required: true, message: '请选择学生', trigger: ['change'] }],
  assessmentYear: [{ required: true, message: '请输入考核年度', trigger: ['blur', 'input'] }],
  secondDisciplineCode: [{ required: true, message: '请输入二级学科代码', trigger: ['blur', 'input'] }],
  secondDisciplineName: [{ required: true, message: '请输入二级学科名称', trigger: ['blur', 'input'] }],
  educationLevel: [{ required: true, message: '请选择学历层次', trigger: ['change'] }],
  trainingGoal: [{ required: true, message: '请选择培养目标', trigger: ['change'] }],
  internshipOrgMode: [{ required: true, message: '请选择实习组织方式', trigger: ['change'] }],
  internshipLocation: [{ required: true, message: '请选择实习地点', trigger: ['change'] }],
  teachingSegment: [{ required: true, message: '请选择任教学段', trigger: ['change'] }],
  teachingSubjectCode: [{ required: true, message: '请选择任教学科', trigger: ['change'] }],
  interviewOrgMode: [{ required: true, message: '请选择面试组织方式', trigger: ['change'] }]
}

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
const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const majorOptions = computed<SelectOption[]>(() =>
  majors.value.map((item) => ({ label: `${item.internalMajorName} ${item.internalMajorCode}`, value: item.internalMajorCode }))
)
const educationLevelOptions = computed<SelectOption[]>(() => dictOptions(educationLevels.value))
const trainingGoalOptions = computed<SelectOption[]>(() => dictOptions(trainingGoals.value))
const internshipModeOptions = computed<SelectOption[]>(() => dictOptions(internshipModes.value))
const segmentOptions = computed<SelectOption[]>(() =>
  segments.value
    .filter((item) => !allowedSegments.value.length || allowedSegments.value.includes(item.itemCode))
    .map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const segmentFilterOptions = computed<SelectOption[]>(() => dictOptions(segments.value))
const internshipLocationOptions = computed<SelectOption[]>(() =>
  internshipLocations.value
    .filter((item) => !allowedLocations.value.length || allowedLocations.value.includes(item.itemCode))
    .map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const interviewModeOptions = computed<SelectOption[]>(() => dictOptions(interviewModes.value))
const conclusionOptions = computed<SelectOption[]>(() => dictOptions(conclusions.value))

const filteredRecords = computed(() => {
  const segment = segmentFilter.value
  if (!segment) return records.value
  return records.value.filter((row) => row.teachingSegment === segment)
})
const detailItems = computed(() => {
  const row = selectedProfile.value
  if (!row) return []
  return [
    { label: '学生', value: [row.studentNo, row.studentName].filter(Boolean).join(' / ') || '-' },
    { label: '学院', value: collegeName(row.collegeId) },
    { label: '考核年度', value: row.assessmentYear, mono: true },
    { label: '校内专业', value: row.internalMajorName || '-' },
    { label: '二级学科', value: [row.secondDisciplineCode, row.secondDisciplineName].filter(Boolean).join(' / ') || '-' },
    { label: '学历层次', value: dictLabel(educationLevels.value, row.educationLevel) },
    { label: '培养目标', value: dictLabel(trainingGoals.value, row.trainingGoal) },
    { label: '实习组织方式', value: dictLabel(internshipModes.value, row.internshipOrgMode) },
    { label: '实习地点', value: dictLabel(internshipLocations.value, row.internshipLocation) },
    { label: '任教学段', value: dictLabel(segments.value, row.teachingSegment) },
    { label: '任教学科', value: [row.teachingSubjectName, row.teachingSubjectCode].filter(Boolean).join(' / ') || '-' },
    { label: '面试组织方式', value: dictLabel(interviewModes.value, row.interviewOrgMode) },
    { label: '测试结论', value: dictLabel(conclusions.value, row.abilityTestConclusion) },
    { label: '状态', status: row.status },
    { label: '初审意见', value: row.firstReviewComment || '-', span: 2 },
    { label: '复审意见', value: row.secondReviewComment || '-', span: 2 }
  ]
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

watch(
  () => form.trainingGoal,
  async (goal) => {
    if (!goal) {
      allowedSegments.value = []
      allowedLocations.value = []
      return
    }
    await reloadTrainingOptions(goal, form.teachingSegment)
  }
)

watch(
  () => form.teachingSegment,
  async (segment, oldSegment) => {
    if (segment !== oldSegment) form.teachingSubjectCode = ''
    if (form.trainingGoal) await reloadTrainingOptions(form.trainingGoal, segment)
  }
)

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

async function reloadTrainingOptions(goal: string, segment?: string | null) {
  try {
    const res = await getTrainingOptions(goal, segment)
    allowedSegments.value = res.data.allowedSegments || []
    allowedLocations.value = res.data.allowedInternshipLocations || []
    if (!form.teachingSegment && res.data.defaultSegment) form.teachingSegment = res.data.defaultSegment
    if (!form.internshipLocation && res.data.defaultInternshipLocation) form.internshipLocation = res.data.defaultInternshipLocation
    if (form.teachingSegment && allowedSegments.value.length && !allowedSegments.value.includes(form.teachingSegment)) {
      form.teachingSegment = res.data.defaultSegment || ''
      form.teachingSubjectCode = ''
    }
    if (form.internshipLocation && allowedLocations.value.length && !allowedLocations.value.includes(form.internshipLocation)) {
      form.internshipLocation = res.data.defaultInternshipLocation || ''
    }
  } catch (error) {
    showError(error, '培养目标联动选项加载失败')
  }
}

function openDetail(row: TrainingProfile) {
  selectedProfile.value = row
  detailVisible.value = true
}

async function openDrawer(row?: TrainingProfile) {
  editingId.value = row?.id || null
  resetForm(row)
  drawerVisible.value = true
  if (form.trainingGoal) await reloadTrainingOptions(form.trainingGoal, form.teachingSegment)
}

function resetForm(row?: TrainingProfile) {
  const selfStudent = selfMode.value ? students.value[0] : null
  Object.assign(form, {
    studentId: row?.studentId || selfStudent?.id || userStore.currentUser?.studentId || '',
    collegeId: row?.collegeId || selfStudent?.collegeId || '',
    assessmentYear: row?.assessmentYear || assessmentYear.value || yearStore.assessmentYear,
    secondDisciplineCode: row?.secondDisciplineCode || '',
    secondDisciplineName: row?.secondDisciplineName || '',
    internalMajorCode: row?.internalMajorCode || '',
    internalMajorName: row?.internalMajorName || '',
    educationLevel: row?.educationLevel || '',
    trainingGoal: row?.trainingGoal || '',
    internshipOrgMode: row?.internshipOrgMode || '',
    internshipLocation: row?.internshipLocation || '',
    teachingSegment: row?.teachingSegment || '',
    teachingSubjectCode: row?.teachingSubjectCode || '',
    interviewOrgMode: row?.interviewOrgMode || '',
    abilityTestConclusion: row?.abilityTestConclusion || ''
  })
  allowedSegments.value = []
  allowedLocations.value = []
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (selfMode.value) await confirmTrainingProfile(form)
    else await saveTrainingProfile(form)
    message.success('已保存')
    drawerVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
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

function handleStudentChange(studentId: string | number | null) {
  const id = typeof studentId === 'string' ? studentId : ''
  const student = students.value.find((item) => item.id === id)
  form.collegeId = student?.collegeId || ''
}

function handleMajorChange(code: string | number | null) {
  const major = majors.value.find((item) => item.internalMajorCode === code)
  if (!major) return
  form.internalMajorCode = major.internalMajorCode
  form.internalMajorName = major.internalMajorName
  form.secondDisciplineCode = major.secondDisciplineCode || ''
  form.secondDisciplineName = major.secondDisciplineName || ''
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
    if (!drawerVisible.value) form.assessmentYear = year
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

    <n-drawer v-model:show="drawerVisible" :width="720">
      <n-drawer-content :title="editingId ? '编辑专业培养信息' : '新增专业培养信息'" closable>
        <n-alert type="info" :bordered="false" class="page-section">
          任教学科必须先选择学段，再从学科库中选择；培养目标会限制可选学段和实习地点。
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
          <n-grid :cols="2" :x-gap="12" responsive="screen">
            <n-form-item-gi label="学生" path="studentId">
              <n-select
                v-model:value="form.studentId"
                :options="studentOptions"
                :disabled="selfMode"
                filterable
                @update:value="handleStudentChange"
              />
            </n-form-item-gi>
            <n-form-item-gi label="考核年度" path="assessmentYear">
              <n-input v-model:value="form.assessmentYear" class="mono-input" />
            </n-form-item-gi>
            <n-form-item-gi label="学历层次" path="educationLevel">
              <n-select v-model:value="form.educationLevel" :options="educationLevelOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="校内专业">
              <n-select v-model:value="form.internalMajorCode" clearable filterable :options="majorOptions" @update:value="handleMajorChange" />
            </n-form-item-gi>
            <n-form-item-gi label="二级学科代码" path="secondDisciplineCode">
              <n-input v-model:value="form.secondDisciplineCode" class="mono-input" />
            </n-form-item-gi>
            <n-form-item-gi label="二级学科名称" path="secondDisciplineName">
              <n-input v-model:value="form.secondDisciplineName" />
            </n-form-item-gi>
            <n-form-item-gi label="培养目标" path="trainingGoal">
              <n-select v-model:value="form.trainingGoal" :options="trainingGoalOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="实习组织方式" path="internshipOrgMode">
              <n-select v-model:value="form.internshipOrgMode" :options="internshipModeOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="实习地点" path="internshipLocation">
              <n-select v-model:value="form.internshipLocation" :options="internshipLocationOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="任教学段" path="teachingSegment">
              <n-select v-model:value="form.teachingSegment" :options="segmentOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="面试组织方式" path="interviewOrgMode">
              <n-select v-model:value="form.interviewOrgMode" :options="interviewModeOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="测试结论">
              <n-select v-model:value="form.abilityTestConclusion" clearable :options="conclusionOptions" />
            </n-form-item-gi>
          </n-grid>
          <n-form-item label="任教学科" path="teachingSubjectCode">
            <SubjectSelect v-model:value="form.teachingSubjectCode" :segment-code="form.teachingSegment" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="drawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="save">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="detailVisible" :width="560">
      <n-drawer-content title="培养信息详情" closable>
        <DetailPanel v-if="selectedProfile" :items="detailItems" :columns="2" />
      </n-drawer-content>
    </n-drawer>

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

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
