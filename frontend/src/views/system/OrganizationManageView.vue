<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NInput,
  NInputNumber,
  NPopconfirm,
  NSpace,
  NSwitch,
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
import StatusTag from '@/components/StatusTag.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import {
  createCollege,
  createMajor,
  deleteCollege,
  deleteMajor,
  listColleges,
  listMajors,
  listTrainingGoalConfigs,
  replaceMajorTrainingGoals,
  saveTrainingGoalConfig,
  updateCollege,
  updateMajor,
  type College,
  type CollegePayload,
  type Major,
  type MajorPayload,
  type TrainingGoalConfig,
  type TrainingGoalConfigPayload
} from '@/api/organization'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const collegeLoading = ref(false)
const majorLoading = ref(false)
const configLoading = ref(false)
const saving = ref(false)
const collegeDrawerVisible = ref(false)
const majorDrawerVisible = ref(false)
const goalDrawerVisible = ref(false)
const configDrawerVisible = ref(false)
const collegeFormRef = ref<FormInst | null>(null)
const majorFormRef = ref<FormInst | null>(null)
const goalFormRef = ref<FormInst | null>(null)
const configFormRef = ref<FormInst | null>(null)
const editingCollegeId = ref<string | null>(null)
const editingMajorId = ref<string | null>(null)
const selectedCollegeId = ref<string | null>(null)
const selectedMajorId = ref<string | null>(null)
const selectedConfigCode = ref<string | null>(null)
const collegeKeyword = ref('')
const majorKeyword = ref('')
const majorYearVersion = ref('GLOBAL')
const majorStatus = ref<number | null>(null)
const pilotScopeFlag = ref<number | null>(null)
const colleges = ref<College[]>([])
const majors = ref<Major[]>([])
const schoolItems = ref<DictItem[]>([])
const trainingGoals = ref<DictItem[]>([])
const teachingSegments = ref<DictItem[]>([])
const internshipLocations = ref<DictItem[]>([])
const configs = ref<TrainingGoalConfig[]>([])

interface CollegeFormState {
  code: string
  name: string
  sort: number
  status: number
}

interface MajorFormState {
  collegeId: string | null
  internalMajorCode: string
  internalMajorName: string
  secondDisciplineCode: string
  secondDisciplineName: string
  pilotScopeFlag: number
  yearVersion: string
  sort: number
  status: number
}

interface GoalFormState {
  trainingGoalCodes: string[]
}

interface ConfigFormState {
  trainingGoalCode: string | null
  defaultSegment: string | null
  allowedSegments: string[]
  defaultInternshipLocation: string | null
  allowedInternshipLocations: string[]
  status: number
}

const collegeForm = reactive<CollegeFormState>({ code: '', name: '', sort: 0, status: 1 })

const majorForm = reactive<MajorFormState>({
  collegeId: null,
  internalMajorCode: '',
  internalMajorName: '',
  secondDisciplineCode: '',
  secondDisciplineName: '',
  pilotScopeFlag: 0,
  yearVersion: 'GLOBAL',
  sort: 0,
  status: 1
})

const goalForm = reactive<GoalFormState>({ trainingGoalCodes: [] })

const configForm = reactive<ConfigFormState>({
  trainingGoalCode: null,
  defaultSegment: null,
  allowedSegments: [],
  defaultInternshipLocation: null,
  allowedInternshipLocations: [],
  status: 1
})

const collegeRules: FormRules = {
  code: [{ required: true, message: '请输入学院编码', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入学院名称', trigger: ['blur', 'input'] }]
}

const majorRules: FormRules = {
  collegeId: [{ required: true, message: '请选择学院', trigger: ['blur', 'change'] }],
  internalMajorCode: [{ required: true, message: '请输入校内专业代码', trigger: ['blur', 'input'] }],
  internalMajorName: [{ required: true, message: '请输入校内专业名称', trigger: ['blur', 'input'] }],
  yearVersion: [{ required: true, message: '请输入年度版本', trigger: ['blur', 'input'] }]
}

const goalRules: FormRules = {
  trainingGoalCodes: [{ type: 'array', required: true, min: 1, message: '请选择培养目标', trigger: ['change'] }]
}

const configRules: FormRules = {
  trainingGoalCode: [{ required: true, message: '请选择培养目标', trigger: ['change'] }],
  defaultSegment: [{ required: true, message: '请选择默认任教学段', trigger: ['change'] }],
  allowedSegments: [{ type: 'array', required: true, min: 1, message: '请选择允许任教学段', trigger: ['change'] }],
  defaultInternshipLocation: [{ required: true, message: '请选择默认实习地点', trigger: ['change'] }],
  allowedInternshipLocations: [{ type: 'array', required: true, min: 1, message: '请选择允许实习地点', trigger: ['change'] }]
}

const canManageCollege = computed(() => userStore.hasPerm('college:manage'))
const canManageMajor = computed(() => userStore.hasPerm('major:manage'))
const canLoadCollegeList = computed(() => canManageCollege.value || canManageMajor.value)
const hasVisibleSection = computed(() => canLoadCollegeList.value || canManageMajor.value)

const collegeOptions = computed<SelectOption[]>(() =>
  colleges.value.filter((item) => item.status === 1).map((item) => ({ label: item.name, value: item.id }))
)

const trainingGoalOptions = computed<SelectOption[]>(() => trainingGoals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => teachingSegments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const internshipLocationOptions = computed<SelectOption[]>(() => internshipLocations.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))

const schoolText = computed(() => {
  const school = schoolItems.value[0]
  return school ? `${school.itemValue} ${school.itemCode}` : '-'
})

const selectedCollege = computed(() => colleges.value.find((item) => item.id === selectedCollegeId.value) || null)
const canCreateMajor = computed(() => canManageMajor.value && selectedCollege.value?.status === 1)
const selectedMajor = computed(() => majors.value.find((item) => item.id === selectedMajorId.value) || null)

const collegeColumns = computed<DataTableColumns<College>>(() => {
  const columns: DataTableColumns<College> = [
    { title: '学院编码', key: 'code', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.code) },
    { title: '学院名称', key: 'name', minWidth: 160, ellipsis: { tooltip: true } },
    { title: '排序', key: 'sort', width: 72, render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManageCollege.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        renderTableActions([
          h(NButton, { size: 'small', quaternary: true, onClick: () => openCollegeDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => removeCollege(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '删除学院会校验是否存在专业。'
            }
          )
        ])
    })
  }
  return columns
})

const majorColumns = computed<DataTableColumns<Major>>(() => {
  const columns: DataTableColumns<Major> = [
    { title: '专业代码', key: 'internalMajorCode', minWidth: 140, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.internalMajorCode) },
    { title: '专业名称', key: 'internalMajorName', minWidth: 170, ellipsis: { tooltip: true } },
    { title: '学院', key: 'collegeName', minWidth: 150, ellipsis: { tooltip: true } },
    { title: '年度', key: 'yearVersion', width: 96, render: (row) => h('span', { class: 'mono' }, row.yearVersion) },
    { title: '试点', key: 'pilotScopeFlag', width: 80, render: (row) => h(StatusTag, { text: row.pilotScopeFlag === 1 ? '是' : '否' }) },
    {
      title: '培养目标',
      key: 'trainingGoals',
      minWidth: 220,
      render: (row) => h(NSpace, { size: 6 }, () => row.trainingGoals.map((goal) => h('span', { class: 'pill', key: goal.code }, goal.name)))
    },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManageMajor.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 220,
      render: (row) =>
        renderTableActions([
          h(NButton, { size: 'small', quaternary: true, onClick: () => openMajorDrawer(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', quaternary: true, onClick: () => openGoalDrawer(row) }, { default: () => '目标' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => removeMajor(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除该专业？'
            }
          )
        ])
    })
  }
  return columns
})

const configColumns = computed<DataTableColumns<TrainingGoalConfig>>(() => {
  const columns: DataTableColumns<TrainingGoalConfig> = [
    { title: '培养目标', key: 'trainingGoalName', minWidth: 150, ellipsis: { tooltip: true } },
    { title: '默认学段', key: 'defaultSegmentName', minWidth: 130, ellipsis: { tooltip: true } },
    { title: '允许学段', key: 'allowedSegments', minWidth: 220, render: (row) => renderCodeTags(row.allowedSegments, teachingSegments.value) },
    { title: '默认实习地点', key: 'defaultInternshipLocationName', minWidth: 160, ellipsis: { tooltip: true } },
    { title: '允许实习地点', key: 'allowedInternshipLocations', minWidth: 260, render: (row) => renderCodeTags(row.allowedInternshipLocations, internshipLocations.value) },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManageMajor.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 92,
      render: (row) => renderTableActions([h(NButton, { size: 'small', quaternary: true, onClick: () => openConfigDrawer(row) }, { default: () => '编辑' })])
    })
  }
  return columns
})

const selectedConfig = computed(() => configs.value.find((item) => item.trainingGoalCode === selectedConfigCode.value) || configs.value[0] || null)

const collegeDetailItems = computed(() => {
  const row = selectedCollege.value
  if (!row) return []
  return [
    { label: '学院编码', value: row.code, mono: true },
    { label: '学院名称', value: row.name },
    { label: '状态', value: row.status === 1 ? '启用' : '停用' },
    { label: '排序', value: row.sort ?? 0, mono: true }
  ]
})

const majorDetailItems = computed(() => {
  const row = selectedMajor.value
  if (!row) return []
  return [
    { label: '专业代码', value: row.internalMajorCode, mono: true },
    { label: '专业名称', value: row.internalMajorName },
    { label: '学院', value: row.collegeName || selectedCollege.value?.name || '-' },
    { label: '年度', value: row.yearVersion, mono: true },
    { label: '试点', value: row.pilotScopeFlag === 1 ? '是' : '否' },
    { label: '状态', value: row.status === 1 ? '启用' : '停用' },
    { label: '二级学科', value: [row.secondDisciplineCode, row.secondDisciplineName].filter(Boolean).join(' / ') || '-', span: 2 },
    { label: '培养目标', value: row.trainingGoals.map((item) => item.name).join('、') || '-', span: 2 }
  ]
})

const configDetailItems = computed(() => {
  const row = selectedConfig.value
  if (!row) return []
  return [
    { label: '培养目标', value: row.trainingGoalName || row.trainingGoalCode },
    { label: '默认学段', value: row.defaultSegmentName || dictName(teachingSegments.value, row.defaultSegment) },
    { label: '允许学段', value: row.allowedSegments.map((code) => dictName(teachingSegments.value, code)).join('、'), span: 2 },
    { label: '默认实习地点', value: row.defaultInternshipLocationName || dictName(internshipLocations.value, row.defaultInternshipLocation) },
    { label: '状态', value: row.status === 1 ? '启用' : '停用' },
    { label: '允许实习地点', value: row.allowedInternshipLocations.map((code) => dictName(internshipLocations.value, code)).join('、'), span: 2 }
  ]
})

function collegeRowProps(row: object) {
  const item = row as College
  return {
    class: item.id === selectedCollegeId.value ? 'is-selected-row' : '',
    onClick: () => {
      selectedCollegeId.value = item.id
      loadMajors()
    }
  }
}

function majorRowProps(row: object) {
  const item = row as Major
  return {
    class: item.id === selectedMajorId.value ? 'is-selected-row' : '',
    onClick: () => {
      selectedMajorId.value = item.id
    }
  }
}

function configRowProps(row: object) {
  const item = row as TrainingGoalConfig
  return {
    class: item.trainingGoalCode === selectedConfig.value?.trainingGoalCode ? 'is-selected-row' : '',
    onClick: () => {
      selectedConfigCode.value = item.trainingGoalCode
    }
  }
}

function resetCollegeFilters() {
  collegeKeyword.value = ''
  void loadColleges()
}

function resetMajorFilters() {
  majorKeyword.value = ''
  majorYearVersion.value = 'GLOBAL'
  majorStatus.value = null
  pilotScopeFlag.value = null
  void loadMajors()
}

function resetConfigFilters() {
  selectedConfigCode.value = null
  void loadConfigs()
}

async function loadDictionaries() {
  const [school, goals, segments, locations] = await Promise.all([
    listDictItems('school', true),
    listDictItems('training_goal', true),
    listDictItems('teaching_segment', true),
    listDictItems('internship_location', true)
  ])
  schoolItems.value = school.data
  trainingGoals.value = sortDict(goals.data)
  teachingSegments.value = sortDict(segments.data)
  internshipLocations.value = sortDict(locations.data)
}

async function loadColleges() {
  if (!canLoadCollegeList.value) {
    colleges.value = []
    selectedCollegeId.value = null
    return
  }
  collegeLoading.value = true
  try {
    const res = await listColleges(collegeKeyword.value, null)
    colleges.value = res.data
    if (!selectedCollegeId.value && colleges.value.length > 0) selectedCollegeId.value = colleges.value[0].id
    if (selectedCollegeId.value && !colleges.value.some((item) => item.id === selectedCollegeId.value)) {
      selectedCollegeId.value = colleges.value[0]?.id || null
    }
  } catch (error) {
    showError(error, '学院加载失败')
  } finally {
    collegeLoading.value = false
  }
}

async function loadMajors() {
  if (!canManageMajor.value) {
    majors.value = []
    selectedMajorId.value = null
    return
  }
  majorLoading.value = true
  try {
    const res = await listMajors({
      collegeId: selectedCollegeId.value,
      yearVersion: majorYearVersion.value,
      pilotScopeFlag: pilotScopeFlag.value,
      status: majorStatus.value,
      keyword: majorKeyword.value
    })
    majors.value = res.data
    if (selectedMajorId.value && !majors.value.some((item) => item.id === selectedMajorId.value)) selectedMajorId.value = null
  } catch (error) {
    showError(error, '专业加载失败')
  } finally {
    majorLoading.value = false
  }
}

async function loadConfigs() {
  if (!canManageMajor.value) {
    configs.value = []
    return
  }
  configLoading.value = true
  try {
    const res = await listTrainingGoalConfigs(selectedConfigCode.value)
    configs.value = res.data
  } catch (error) {
    showError(error, '培养目标联动配置加载失败')
  } finally {
    configLoading.value = false
  }
}

async function refreshAll() {
  await loadColleges()
  const tasks: Promise<void>[] = []
  if (canManageMajor.value) tasks.push(loadMajors(), loadConfigs())
  await Promise.all(tasks)
}

function openCollegeDrawer(row?: College) {
  editingCollegeId.value = row?.id || null
  collegeForm.code = row?.code || ''
  collegeForm.name = row?.name || ''
  collegeForm.sort = row?.sort || 0
  collegeForm.status = row?.status ?? 1
  collegeDrawerVisible.value = true
}

function openMajorDrawer(row?: Major) {
  if (!row && !canCreateMajor.value) {
    message.warning(selectedCollege.value ? '无权新增专业或学院已停用' : '请选择启用学院')
    return
  }
  editingMajorId.value = row?.id || null
  majorForm.collegeId = row?.collegeId || selectedCollege.value?.id || null
  majorForm.internalMajorCode = row?.internalMajorCode || ''
  majorForm.internalMajorName = row?.internalMajorName || ''
  majorForm.secondDisciplineCode = row?.secondDisciplineCode || ''
  majorForm.secondDisciplineName = row?.secondDisciplineName || ''
  majorForm.pilotScopeFlag = row?.pilotScopeFlag || 0
  majorForm.yearVersion = row?.yearVersion || majorYearVersion.value || 'GLOBAL'
  majorForm.sort = row?.sort || 0
  majorForm.status = row?.status ?? 1
  majorDrawerVisible.value = true
}

function openGoalDrawer(row = selectedMajor.value) {
  if (!row) {
    message.warning('请选择专业')
    return
  }
  selectedMajorId.value = row.id
  goalForm.trainingGoalCodes = row.trainingGoals.map((item) => item.code)
  goalDrawerVisible.value = true
}

function openConfigDrawer(row?: TrainingGoalConfig) {
  configForm.trainingGoalCode = row?.trainingGoalCode || (selectedConfigCode.value ?? null)
  configForm.allowedSegments = [...(row?.allowedSegments || [])]
  configForm.defaultSegment = row?.defaultSegment || configForm.allowedSegments[0] || null
  configForm.allowedInternshipLocations = [...(row?.allowedInternshipLocations || [])]
  configForm.defaultInternshipLocation = row?.defaultInternshipLocation || configForm.allowedInternshipLocations[0] || null
  configForm.status = row?.status ?? 1
  configDrawerVisible.value = true
}

async function saveCollege() {
  await collegeFormRef.value?.validate()
  saving.value = true
  try {
    const payload: CollegePayload = {
      code: collegeForm.code.trim(),
      name: collegeForm.name.trim(),
      sort: collegeForm.sort ?? 0,
      status: collegeForm.status ?? 1
    }
    if (editingCollegeId.value) await updateCollege(editingCollegeId.value, payload)
    else {
      const res = await createCollege(payload)
      selectedCollegeId.value = res.data
    }
    message.success('学院已保存')
    collegeDrawerVisible.value = false
    await refreshAll()
  } catch (error) {
    showError(error, '学院保存失败')
  } finally {
    saving.value = false
  }
}

async function saveMajor() {
  await majorFormRef.value?.validate()
  saving.value = true
  try {
    const payload: MajorPayload = {
      collegeId: majorForm.collegeId || '',
      internalMajorCode: majorForm.internalMajorCode.trim(),
      internalMajorName: majorForm.internalMajorName.trim(),
      secondDisciplineCode: cleanOptional(majorForm.secondDisciplineCode),
      secondDisciplineName: cleanOptional(majorForm.secondDisciplineName),
      pilotScopeFlag: majorForm.pilotScopeFlag ?? 0,
      yearVersion: cleanOptional(majorForm.yearVersion) || 'GLOBAL',
      sort: majorForm.sort ?? 0,
      status: majorForm.status ?? 1
    }
    if (editingMajorId.value) {
      await updateMajor(editingMajorId.value, payload)
      selectedMajorId.value = editingMajorId.value
    } else {
      const res = await createMajor(payload)
      selectedMajorId.value = res.data
    }
    message.success('专业已保存')
    majorDrawerVisible.value = false
    await loadMajors()
  } catch (error) {
    showError(error, '专业保存失败')
  } finally {
    saving.value = false
  }
}

async function saveGoals() {
  await goalFormRef.value?.validate()
  if (!selectedMajorId.value) return
  saving.value = true
  try {
    await replaceMajorTrainingGoals(selectedMajorId.value, goalForm.trainingGoalCodes)
    message.success('专业培养目标已保存')
    goalDrawerVisible.value = false
    await loadMajors()
  } catch (error) {
    showError(error, '专业培养目标保存失败')
  } finally {
    saving.value = false
  }
}

async function saveConfig() {
  await configFormRef.value?.validate()
  if (!configForm.defaultSegment || !configForm.allowedSegments.includes(configForm.defaultSegment)) {
    message.error('默认任教学段必须包含在允许任教学段中')
    return
  }
  if (!configForm.defaultInternshipLocation || !configForm.allowedInternshipLocations.includes(configForm.defaultInternshipLocation)) {
    message.error('默认实习地点必须包含在允许实习地点中')
    return
  }
  saving.value = true
  try {
    const payload: TrainingGoalConfigPayload = {
      trainingGoalCode: configForm.trainingGoalCode || '',
      defaultSegment: configForm.defaultSegment,
      allowedSegments: configForm.allowedSegments,
      defaultInternshipLocation: configForm.defaultInternshipLocation,
      allowedInternshipLocations: configForm.allowedInternshipLocations,
      status: configForm.status ?? 1
    }
    await saveTrainingGoalConfig(payload)
    message.success('联动配置已保存')
    configDrawerVisible.value = false
    await loadConfigs()
  } catch (error) {
    showError(error, '联动配置保存失败')
  } finally {
    saving.value = false
  }
}

async function removeCollege(row: College) {
  try {
    await deleteCollege(row.id)
    message.success('学院已删除')
    if (selectedCollegeId.value === row.id) selectedCollegeId.value = null
    await refreshAll()
  } catch (error) {
    showError(error, '学院删除失败')
  }
}

async function removeMajor(row: Major) {
  try {
    await deleteMajor(row.id)
    message.success('专业已删除')
    if (selectedMajorId.value === row.id) selectedMajorId.value = null
    await loadMajors()
  } catch (error) {
    showError(error, '专业删除失败')
  }
}

function renderCodeTags(codes: string[], dictItems: DictItem[]) {
  return h(NSpace, { size: 6 }, () => codes.map((code) => h('span', { class: 'pill', key: code }, dictName(dictItems, code))))
}

function dictName(items: DictItem[], code: string) {
  return items.find((item) => item.itemCode === code)?.itemValue || code
}

function sortDict(items: DictItem[]) {
  return [...items].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadDictionaries()
  await refreshAll()
})
</script>

<template>
  <PageContainer title="组织与专业" description="维护学校、学院、专业与培养目标配置。">
    <template #actions>
      <n-space>
        <n-tag :bordered="false">学校：{{ schoolText }}</n-tag>
        <n-button v-if="hasVisibleSection" secondary @click="refreshAll">刷新</n-button>
        <n-button v-if="canManageCollege" type="primary" @click="openCollegeDrawer()">新增学院</n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的组织专业分区" class="page-section" />

    <n-tabs v-if="hasVisibleSection" type="line" animated>
      <n-tab-pane v-if="canLoadCollegeList" name="majors" tab="学院与专业">
        <div class="org-layout">
          <div class="page-section">
            <FilterBar :loading="collegeLoading" @submit="loadColleges" @reset="resetCollegeFilters">
              <label class="filter-field">
                <span>学院</span>
                <n-input v-model:value="collegeKeyword" clearable placeholder="学院编码 / 名称" style="width: 220px" @keyup.enter="loadColleges" />
              </label>
            </FilterBar>
            <DataPanel
              title="学院列表"
              :columns="collegeColumns"
              :data="colleges"
              :total="colleges.length"
              :loading="collegeLoading"
              :row-props="collegeRowProps"
              :max-height="620"
              :pagination="false"
              empty-title="暂无学院"
              empty-description="当前筛选条件下没有学院记录。"
              @refresh="loadColleges"
            >
              <template #actions>
                <n-button v-if="canManageCollege" type="primary" size="small" @click="openCollegeDrawer()">新增学院</n-button>
              </template>
            </DataPanel>
            <n-card :bordered="false" class="detail-card">
              <div class="detail-head">
                <div>
                  <strong>{{ selectedCollege?.name || '学院详情' }}</strong>
                  <span class="muted mono">{{ selectedCollege?.code || '请选择学院' }}</span>
                </div>
              </div>
              <DetailPanel v-if="selectedCollege" :items="collegeDetailItems" :columns="2" />
              <n-empty v-else description="请选择学院" />
            </n-card>
          </div>

          <div class="page-section">
            <n-empty v-if="!canManageMajor" description="当前账号没有专业管理权限" />
            <FilterBar v-else :loading="majorLoading" @submit="loadMajors" @reset="resetMajorFilters">
              <label class="filter-field">
                <span>专业</span>
                <n-input v-model:value="majorKeyword" clearable placeholder="专业代码 / 名称" style="width: 220px" @keyup.enter="loadMajors" />
              </label>
              <label class="filter-field">
                <span>年度</span>
                <n-input v-model:value="majorYearVersion" clearable maxlength="16" placeholder="年度" style="width: 120px" />
              </label>
              <label class="filter-field">
                <span>试点</span>
                <n-select
                  v-model:value="pilotScopeFlag"
                  clearable
                  placeholder="全部"
                  style="width: 110px"
                  :options="[
                    { label: '是', value: 1 },
                    { label: '否', value: 0 }
                  ]"
                />
              </label>
              <label class="filter-field">
                <span>状态</span>
                <n-select
                  v-model:value="majorStatus"
                  clearable
                  placeholder="全部"
                  style="width: 110px"
                  :options="[
                    { label: '启用', value: 1 },
                    { label: '停用', value: 0 }
                  ]"
                />
              </label>
            </FilterBar>
            <DataPanel
              v-if="canManageMajor"
              title="专业列表"
              :columns="majorColumns"
              :data="majors"
              :total="majors.length"
              :loading="majorLoading"
              :row-props="majorRowProps"
              :max-height="620"
              :scroll-x="1280"
              empty-title="暂无专业"
              empty-description="当前筛选条件下没有专业记录。"
              @refresh="loadMajors"
            >
              <template #actions>
                <n-button v-if="canManageMajor" type="primary" size="small" :disabled="!canCreateMajor" @click="openMajorDrawer()">新增专业</n-button>
              </template>
            </DataPanel>
            <n-card v-if="canManageMajor" :bordered="false" class="detail-card">
              <div class="detail-head">
                <div>
                  <strong>{{ selectedMajor?.internalMajorName || '专业详情' }}</strong>
                  <span class="muted mono">{{ selectedMajor?.internalMajorCode || '请选择专业' }}</span>
                </div>
              </div>
              <DetailPanel v-if="selectedMajor" :items="majorDetailItems" :columns="2" />
              <n-empty v-else description="请选择专业" />
            </n-card>
          </div>
        </div>
      </n-tab-pane>

      <n-tab-pane v-if="canManageMajor" name="configs" tab="联动配置">
        <div class="config-layout">
          <div class="page-section">
            <FilterBar :loading="configLoading" @submit="loadConfigs" @reset="resetConfigFilters">
              <label class="filter-field">
                <span>培养目标</span>
                <n-select v-model:value="selectedConfigCode" :options="trainingGoalOptions" clearable placeholder="全部培养目标" style="width: 240px" />
              </label>
            </FilterBar>
            <DataPanel
              title="联动配置"
              :columns="configColumns"
              :data="configs"
              :total="configs.length"
              :loading="configLoading"
              :row-props="configRowProps"
              :max-height="620"
              :scroll-x="1120"
              empty-title="暂无联动配置"
              empty-description="当前筛选条件下没有培养目标联动配置。"
              @refresh="loadConfigs"
            >
              <template #actions>
                <n-button v-if="canManageMajor" type="primary" size="small" @click="openConfigDrawer()">新增/维护配置</n-button>
              </template>
            </DataPanel>
          </div>
          <n-card :bordered="false" class="detail-card">
            <div class="detail-head">
              <div>
                <strong>{{ selectedConfig?.trainingGoalName || '配置详情' }}</strong>
                <span class="muted mono">{{ selectedConfig?.trainingGoalCode || '请选择配置' }}</span>
              </div>
            </div>
            <DetailPanel v-if="selectedConfig" :items="configDetailItems" :columns="2" />
            <n-empty v-else description="请选择培养目标配置" />
          </n-card>
        </div>
      </n-tab-pane>
    </n-tabs>

    <n-drawer v-model:show="collegeDrawerVisible" :width="560" placement="right">
      <n-drawer-content :title="editingCollegeId ? '编辑学院' : '新增学院'">
        <n-form ref="collegeFormRef" :model="collegeForm" :rules="collegeRules" label-placement="top">
          <div class="form-section-title">基本信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学院编码" path="code">
              <n-input v-model:value="collegeForm.code" maxlength="64" show-count />
            </n-form-item-gi>
            <n-form-item-gi label="学院名称" path="name">
              <n-input v-model:value="collegeForm.name" maxlength="128" show-count />
            </n-form-item-gi>
            <n-form-item-gi label="排序" path="sort">
              <n-input-number v-model:value="collegeForm.sort" :min="0" />
            </n-form-item-gi>
            <n-form-item-gi label="状态" path="status">
              <n-switch v-model:value="collegeForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="collegeDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveCollege">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="majorDrawerVisible" :width="560" placement="right">
      <n-drawer-content :title="editingMajorId ? '编辑专业' : '新增专业'">
        <n-form ref="majorFormRef" :model="majorForm" :rules="majorRules" label-placement="top">
          <div class="form-section-title">基本信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学院" path="collegeId" :span="2">
              <n-select v-model:value="majorForm.collegeId" :options="collegeOptions" filterable />
            </n-form-item-gi>
            <n-form-item-gi label="校内专业代码" path="internalMajorCode">
              <n-input v-model:value="majorForm.internalMajorCode" maxlength="64" show-count />
            </n-form-item-gi>
            <n-form-item-gi label="校内专业名称" path="internalMajorName">
              <n-input v-model:value="majorForm.internalMajorName" maxlength="128" show-count />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">学科信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="二级学科代码" path="secondDisciplineCode">
              <n-input v-model:value="majorForm.secondDisciplineCode" maxlength="64" show-count />
            </n-form-item-gi>
            <n-form-item-gi label="二级学科名称" path="secondDisciplineName">
              <n-input v-model:value="majorForm.secondDisciplineName" maxlength="128" show-count />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">状态设置</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="年度版本" path="yearVersion">
              <n-input v-model:value="majorForm.yearVersion" maxlength="16" show-count />
            </n-form-item-gi>
            <n-form-item-gi label="试点范围" path="pilotScopeFlag">
              <n-switch v-model:value="majorForm.pilotScopeFlag" :checked-value="1" :unchecked-value="0" />
            </n-form-item-gi>
            <n-form-item-gi label="排序" path="sort">
              <n-input-number v-model:value="majorForm.sort" :min="0" />
            </n-form-item-gi>
            <n-form-item-gi label="状态" path="status">
              <n-switch v-model:value="majorForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="majorDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveMajor">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="goalDrawerVisible" :width="560" placement="right">
      <n-drawer-content title="专业培养目标">
        <n-form ref="goalFormRef" :model="goalForm" :rules="goalRules" label-placement="top">
          <div class="form-section-title">目标设置</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="专业" :span="2">
              <n-input :value="selectedMajor ? `${selectedMajor.internalMajorName} ${selectedMajor.internalMajorCode}` : ''" disabled />
            </n-form-item-gi>
            <n-form-item-gi label="培养目标" path="trainingGoalCodes" :span="2">
              <n-select v-model:value="goalForm.trainingGoalCodes" :options="trainingGoalOptions" multiple filterable />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="goalDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveGoals">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="configDrawerVisible" :width="560" placement="right">
      <n-drawer-content title="培养目标联动配置">
        <n-form ref="configFormRef" :model="configForm" :rules="configRules" label-placement="top">
          <div class="form-section-title">培养目标</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="培养目标" path="trainingGoalCode" :span="2">
              <n-select v-model:value="configForm.trainingGoalCode" :options="trainingGoalOptions" filterable />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">任教学段</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="允许任教学段" path="allowedSegments" :span="2">
              <n-select v-model:value="configForm.allowedSegments" :options="segmentOptions" multiple filterable />
            </n-form-item-gi>
            <n-form-item-gi label="默认任教学段" path="defaultSegment">
              <n-select v-model:value="configForm.defaultSegment" :options="segmentOptions" filterable />
            </n-form-item-gi>
            <n-form-item-gi label="状态" path="status">
              <n-switch v-model:value="configForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">实习地点</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="允许实习地点" path="allowedInternshipLocations" :span="2">
              <n-select v-model:value="configForm.allowedInternshipLocations" :options="internshipLocationOptions" multiple filterable />
            </n-form-item-gi>
            <n-form-item-gi label="默认实习地点" path="defaultInternshipLocation" :span="2">
              <n-select v-model:value="configForm.defaultInternshipLocation" :options="internshipLocationOptions" filterable />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="configDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveConfig">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </PageContainer>
</template>

<style scoped>
.org-layout {
  display: grid;
  grid-template-columns: minmax(360px, 0.7fr) minmax(720px, 1.3fr);
  gap: var(--space-6);
  align-items: start;
}

.config-layout {
  display: grid;
  grid-template-columns: minmax(680px, 1.2fr) minmax(360px, 0.8fr);
  gap: var(--space-6);
  align-items: start;
}

.page-section {
  min-width: 0;
}

.detail-card {
  margin-bottom: var(--space-5);
}

.detail-card :deep(.n-card__content) {
  padding: var(--space-5);
}

.detail-head {
  margin-bottom: var(--space-4);
}

.detail-head strong,
.detail-head span {
  display: block;
}

.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-tag);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
}

:deep(.is-selected-row td) {
  background: var(--brand-soft);
}

@media (max-width: 1220px) {
  .org-layout,
  .config-layout {
    grid-template-columns: 1fr;
  }
}
</style>
