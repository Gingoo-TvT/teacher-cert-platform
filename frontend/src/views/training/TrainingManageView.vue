<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useMessage, NButton, NSpace, NTag, type DataTableColumns, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import SubjectSelect from '@/components/SubjectSelect.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { listMajors, type Major } from '@/api/organization'
import { useUserStore } from '@/stores/user'
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

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const reviewing = ref<{ profile: TrainingProfile; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const assessmentYear = ref('2026')
const statusFilter = ref<string | null>(null)
const records = ref<TrainingProfile[]>([])
const students = ref<Student[]>([])
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
const selfMode = computed(() => userStore.hasPerm('training:confirm') && !userStore.hasPerm('training:edit'))

const form = reactive<TrainingPayload>({
  studentId: '',
  collegeId: '',
  assessmentYear: '2026',
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

const reviewForm = reactive<ReviewPayload>({
  action: 'PASS',
  comment: ''
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
  segments.value.filter((item) => !allowedSegments.value.length || allowedSegments.value.includes(item.itemCode)).map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const internshipLocationOptions = computed<SelectOption[]>(() =>
  internshipLocations.value
    .filter((item) => !allowedLocations.value.length || allowedLocations.value.includes(item.itemCode))
    .map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const interviewModeOptions = computed<SelectOption[]>(() => dictOptions(interviewModes.value))
const conclusionOptions = computed<SelectOption[]>(() => dictOptions(conclusions.value))

const columns: DataTableColumns<TrainingProfile> = [
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 120, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 100 },
  { title: '专业', key: 'internalMajorName', minWidth: 170, ellipsis: { tooltip: true } },
  { title: '培养目标', key: 'trainingGoal', width: 150, render: (row) => dictLabel(trainingGoals.value, row.trainingGoal) },
  { title: '学段', key: 'teachingSegment', width: 120, render: (row) => dictLabel(segments.value, row.teachingSegment) },
  { title: '学科', key: 'teachingSubjectName', width: 140, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row) },
  {
    title: '操作',
    key: 'actions',
    width: 280,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openDrawer(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' })
      ])
  }
]

watch(
  () => form.trainingGoal,
  async (goal) => {
    if (!goal) return
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
    const res = await listTrainingProfiles({ keyword: keyword.value, status: statusFilter.value, assessmentYear: assessmentYear.value })
    records.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, majorRes, educationRes, goalRes, modeRes, locationRes, segmentRes, interviewRes, conclusionRes] = await Promise.all([
    listStudents(),
    listMajors({ pilotScopeFlag: 1, status: 1 }),
    listDictItems('education_level', true),
    listDictItems('training_goal', true),
    listDictItems('internship_org_mode', true),
    listDictItems('internship_location', true),
    listDictItems('teaching_segment', true),
    listDictItems('interview_org_mode', true),
    listDictItems('ability_test_conclusion', true)
  ])
  students.value = selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records
  majors.value = majorRes.data
  educationLevels.value = educationRes.data
  trainingGoals.value = goalRes.data
  internshipModes.value = modeRes.data
  internshipLocations.value = locationRes.data
  segments.value = segmentRes.data
  interviewModes.value = interviewRes.data
  conclusions.value = conclusionRes.data
}

async function reloadTrainingOptions(goal: string, segment?: string | null) {
  const res = await getTrainingOptions(goal, segment)
  allowedSegments.value = res.data.allowedSegments
  allowedLocations.value = res.data.allowedInternshipLocations
  if (!form.teachingSegment && res.data.defaultSegment) form.teachingSegment = res.data.defaultSegment
  if (!form.internshipLocation && res.data.defaultInternshipLocation) form.internshipLocation = res.data.defaultInternshipLocation
  if (form.teachingSegment && allowedSegments.value.length && !allowedSegments.value.includes(form.teachingSegment)) {
    form.teachingSegment = res.data.defaultSegment || ''
    form.teachingSubjectCode = ''
  }
  if (form.internshipLocation && allowedLocations.value.length && !allowedLocations.value.includes(form.internshipLocation)) {
    form.internshipLocation = res.data.defaultInternshipLocation || ''
  }
}

function openDrawer(row?: TrainingProfile) {
  editingId.value = row?.id || null
  Object.assign(form, {
    studentId: row?.studentId || (selfMode.value ? userStore.currentUser?.studentId || '' : ''),
    collegeId: row?.collegeId || '',
    assessmentYear: row?.assessmentYear || assessmentYear.value || '2026',
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
  drawerVisible.value = true
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
  } finally {
    saving.value = false
  }
}

async function submit(row: TrainingProfile) {
  await submitTrainingProfile(row.id)
  message.success('已提交')
  await loadRecords()
}

function openReview(row: TrainingProfile, stage: 'first' | 'second') {
  reviewing.value = { profile: row, stage }
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
  if (reviewing.value.stage === 'first') await firstReviewTrainingProfile(reviewing.value.profile.id, reviewForm)
  else await secondReviewTrainingProfile(reviewing.value.profile.id, reviewForm)
  message.success('审核完成')
  reviewVisible.value = false
  await loadRecords()
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

function statusTag(row: TrainingProfile) {
  const type = row.status === 'PASSED' ? 'success' : row.status === 'FAILED' ? 'error' : row.status.includes('REJECTED') ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel })
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
        <n-input v-model:value="keyword" clearable placeholder="专业/学科" style="width: 220px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 150px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
      <n-button type="primary" @click="openDrawer()">新增培养信息</n-button>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: TrainingProfile) => row.id" :scroll-x="1320" />
  </n-space>

  <n-drawer v-model:show="drawerVisible" :width="620">
    <n-drawer-content title="专业培养信息" closable>
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学生" path="studentId">
            <n-select v-model:value="form.studentId" :options="studentOptions" :disabled="selfMode" @update:value="handleStudentChange" />
          </n-form-item-gi>
          <n-form-item-gi label="考核年度" path="assessmentYear"><n-input v-model:value="form.assessmentYear" /></n-form-item-gi>
          <n-form-item-gi label="学历层次" path="educationLevel"><n-select v-model:value="form.educationLevel" :options="educationLevelOptions" /></n-form-item-gi>
          <n-form-item-gi label="校内专业">
            <n-select v-model:value="form.internalMajorCode" clearable filterable :options="majorOptions" @update:value="handleMajorChange" />
          </n-form-item-gi>
          <n-form-item-gi label="二级学科代码" path="secondDisciplineCode"><n-input v-model:value="form.secondDisciplineCode" /></n-form-item-gi>
          <n-form-item-gi label="二级学科名称" path="secondDisciplineName"><n-input v-model:value="form.secondDisciplineName" /></n-form-item-gi>
          <n-form-item-gi label="培养目标" path="trainingGoal"><n-select v-model:value="form.trainingGoal" :options="trainingGoalOptions" /></n-form-item-gi>
          <n-form-item-gi label="实习组织方式" path="internshipOrgMode"><n-select v-model:value="form.internshipOrgMode" :options="internshipModeOptions" /></n-form-item-gi>
          <n-form-item-gi label="实习地点" path="internshipLocation"><n-select v-model:value="form.internshipLocation" :options="internshipLocationOptions" /></n-form-item-gi>
          <n-form-item-gi label="任教学段" path="teachingSegment"><n-select v-model:value="form.teachingSegment" :options="segmentOptions" /></n-form-item-gi>
          <n-form-item-gi label="面试组织方式" path="interviewOrgMode"><n-select v-model:value="form.interviewOrgMode" :options="interviewModeOptions" /></n-form-item-gi>
          <n-form-item-gi label="测试结论"><n-select v-model:value="form.abilityTestConclusion" clearable :options="conclusionOptions" /></n-form-item-gi>
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
</template>
