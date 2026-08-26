<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import SubjectSelect from '@/components/SubjectSelect.vue'
import type { DictItem } from '@/api/dict'
import type { Major } from '@/api/organization'
import type { Student } from '@/api/student'
import {
  confirmTrainingProfile,
  getTrainingOptions,
  saveTrainingProfile,
  type TrainingPayload,
  type TrainingProfile
} from '@/api/training'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

const props = defineProps<{
  majors: Major[]
  educationLevels: DictItem[]
  trainingGoals: DictItem[]
  internshipModes: DictItem[]
  internshipLocations: DictItem[]
  segments: DictItem[]
  interviewModes: DictItem[]
  conclusions: DictItem[]
  selfMode: boolean
  assessmentYear: string
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const allowedSegments = ref<string[]>([])
const allowedLocations = ref<string[]>([])
const selectedStudentLabel = ref<string | null>(null)
const trainingOptionsLoading = ref(false)
const trainingOptionsError = ref('')
const loadedTrainingOptionsKey = ref('')
let trainingOptionsRequestSequence = 0

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

const majorOptions = computed<SelectOption[]>(() =>
  props.majors.map((item) => ({ label: `${item.internalMajorName} ${item.internalMajorCode}`, value: item.internalMajorCode }))
)
const educationLevelOptions = computed<SelectOption[]>(() => dictOptions(props.educationLevels))
const trainingGoalOptions = computed<SelectOption[]>(() => dictOptions(props.trainingGoals))
const internshipModeOptions = computed<SelectOption[]>(() => dictOptions(props.internshipModes))
const segmentOptions = computed<SelectOption[]>(() =>
  props.segments
    .filter((item) => !allowedSegments.value.length || allowedSegments.value.includes(item.itemCode))
    .map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const internshipLocationOptions = computed<SelectOption[]>(() =>
  props.internshipLocations
    .filter((item) => !allowedLocations.value.length || allowedLocations.value.includes(item.itemCode))
    .map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const interviewModeOptions = computed<SelectOption[]>(() => dictOptions(props.interviewModes))
const conclusionOptions = computed<SelectOption[]>(() => dictOptions(props.conclusions))
const currentTrainingOptionsKey = computed(() => trainingOptionsKey(form.trainingGoal, form.teachingSegment))
const trainingOptionsFresh = computed(() =>
  Boolean(form.trainingGoal)
  && loadedTrainingOptionsKey.value === currentTrainingOptionsKey.value
  && !trainingOptionsLoading.value
  && !trainingOptionsError.value
)

watch(
  () => form.trainingGoal,
  async (goal) => {
    if (!goal) {
      trainingOptionsRequestSequence += 1
      allowedSegments.value = []
      allowedLocations.value = []
      loadedTrainingOptionsKey.value = ''
      trainingOptionsError.value = ''
      trainingOptionsLoading.value = false
      form.teachingSegment = ''
      form.teachingSubjectCode = ''
      form.internshipLocation = ''
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

watch(
  () => yearStore.assessmentYear,
  (year) => {
    if (!drawerVisible.value) form.assessmentYear = year
  }
)

async function open(row?: TrainingProfile) {
  if (saving.value) return
  editingId.value = row?.id || null
  resetForm(row)
  drawerVisible.value = true
  if (form.trainingGoal) await reloadTrainingOptions(form.trainingGoal, form.teachingSegment)
}

async function reloadTrainingOptions(goal: string, segment?: string | null) {
  const requestSequence = ++trainingOptionsRequestSequence
  const queryKey = trainingOptionsKey(goal, segment)
  trainingOptionsLoading.value = true
  trainingOptionsError.value = ''
  try {
    const res = await getTrainingOptions(goal, segment)
    if (requestSequence !== trainingOptionsRequestSequence || queryKey !== currentTrainingOptionsKey.value) return
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
    if (queryKey === currentTrainingOptionsKey.value) loadedTrainingOptionsKey.value = queryKey
  } catch (error) {
    if (requestSequence !== trainingOptionsRequestSequence || queryKey !== currentTrainingOptionsKey.value) return
    trainingOptionsError.value = errorText(error, '培养目标联动选项加载失败')
    message.error(trainingOptionsError.value)
  } finally {
    if (requestSequence === trainingOptionsRequestSequence) trainingOptionsLoading.value = false
  }
}

function resetForm(row?: TrainingProfile) {
  selectedStudentLabel.value = row ? `${row.studentNo || ''} ${row.studentName || ''}`.trim() || null : props.selfMode ? '本人' : null
  Object.assign(form, {
    studentId: row?.studentId || userStore.currentUser?.studentId || '',
    collegeId: row?.collegeId || userStore.currentUser?.collegeId || '',
    assessmentYear: row?.assessmentYear || props.assessmentYear || yearStore.assessmentYear,
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
  loadedTrainingOptionsKey.value = ''
  trainingOptionsError.value = ''
  trainingOptionsLoading.value = false
}

async function save() {
  if (saving.value) return
  if (!trainingOptionsFresh.value) {
    message.error(trainingOptionsError.value || '培养目标联动选项尚未加载成功，请重试后再保存')
    return
  }
  saving.value = true
  try {
    try {
      await formRef.value?.validate()
    } catch {
      // 表单校验失败由字段反馈承接，不应泄漏为页面未处理异常或误报保存失败。
      return
    }
    if (props.selfMode) await confirmTrainingProfile(form)
    else await saveTrainingProfile(form)
    message.success('已保存')
    drawerVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

function handleStudentSelect(student: Student | null) {
  form.collegeId = student?.collegeId || ''
}

function handleMajorChange(code: string | number | null) {
  const major = props.majors.find((item) => item.internalMajorCode === code)
  if (!major) return
  form.internalMajorCode = major.internalMajorCode
  form.internalMajorName = major.internalMajorName
  form.secondDisciplineCode = major.secondDisciplineCode || ''
  form.secondDisciplineName = major.secondDisciplineName || ''
}

function dictOptions(items: DictItem[]): SelectOption[] {
  return items.map((item) => ({ label: item.itemValue, value: item.itemCode }))
}

function trainingOptionsKey(goal: string, segment?: string | null) {
  return JSON.stringify([goal, segment || ''])
}

function errorText(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  return detail || fallback
}

function showError(error: unknown, fallback: string) {
  message.error(errorText(error, fallback))
}

defineExpose({ open })
</script>

<template>
  <n-drawer
    v-model:show="drawerVisible"
    width="min(var(--overlay-wide), var(--overlay-drawer-max))"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content :title="editingId ? '编辑专业培养信息' : '新增专业培养信息'" :closable="!saving">
      <n-alert type="info" :bordered="false" class="page-section">
        任教学科必须先选择学段，再从学科库中选择；培养目标会限制可选学段和实习地点。
      </n-alert>
      <n-alert v-if="form.trainingGoal && trainingOptionsError" type="error" :bordered="false" class="page-section">
        {{ trainingOptionsError }}
        <n-button text type="error" size="small" :loading="trainingOptionsLoading" @click="reloadTrainingOptions(form.trainingGoal, form.teachingSegment)">重试加载联动选项</n-button>
      </n-alert>
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" :disabled="saving">
        <div class="form-section-title">基本信息</div>
        <n-grid cols="1 560:2" responsive="self" :x-gap="16">
          <n-form-item-gi label="学生" path="studentId">
            <StudentSelect
              v-model:value="form.studentId"
              :disabled="selfMode || saving"
              :selected-label="selectedStudentLabel"
              placeholder="输入学号或姓名搜索"
              @select="handleStudentSelect"
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
        </n-grid>
        <div class="form-section-title">学业信息</div>
        <n-grid cols="1 560:2" responsive="self" :x-gap="16">
          <n-form-item-gi label="二级学科代码" path="secondDisciplineCode">
            <n-input v-model:value="form.secondDisciplineCode" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="二级学科名称" path="secondDisciplineName">
            <n-input v-model:value="form.secondDisciplineName" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">培养与考核</div>
        <n-grid cols="1 560:2" responsive="self" :x-gap="16">
          <n-form-item-gi label="培养目标" path="trainingGoal">
            <n-select v-model:value="form.trainingGoal" :options="trainingGoalOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="实习组织方式" path="internshipOrgMode">
            <n-select v-model:value="form.internshipOrgMode" :options="internshipModeOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="实习地点" path="internshipLocation">
            <n-select v-model:value="form.internshipLocation" :options="internshipLocationOptions" :loading="trainingOptionsLoading" :disabled="saving || !trainingOptionsFresh" />
          </n-form-item-gi>
          <n-form-item-gi label="任教学段" path="teachingSegment">
            <n-select v-model:value="form.teachingSegment" :options="segmentOptions" :loading="trainingOptionsLoading" :disabled="saving || !trainingOptionsFresh" />
          </n-form-item-gi>
          <n-form-item-gi label="面试组织方式" path="interviewOrgMode">
            <n-select v-model:value="form.interviewOrgMode" :options="interviewModeOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="测试结论">
            <n-select v-model:value="form.abilityTestConclusion" clearable :options="conclusionOptions" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">任教学科</div>
        <n-grid cols="1" responsive="self">
          <n-form-item-gi label="任教学科" path="teachingSubjectCode">
            <SubjectSelect v-model:value="form.teachingSubjectCode" :segment-code="form.teachingSegment" :disabled="saving || !trainingOptionsFresh" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <div class="training-drawer__footer">
          <n-button :disabled="saving" @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="!trainingOptionsFresh" @click="save">保存</n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: var(--font-size-body);
  font-weight: 600;
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}

.training-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  width: 100%;
}

@media (max-width: 480px) {
  .training-drawer__footer {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .training-drawer__footer .n-button {
    width: 100%;
  }
}
</style>
