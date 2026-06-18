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
    { title: '排序', key: 'sort', width: 72 },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManageCollege.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        h(NSpace, { size: 6 }, () => [
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openCollegeDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => removeCollege(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '删除学院会由后端校验是否存在专业。'
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
        h(NSpace, { size: 6 }, () => [
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openMajorDrawer(row) }, { default: () => '编辑' }),
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
      render: (row) => h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openConfigDrawer(row) }, { default: () => '编辑' })
    })
  }
  return columns
})

function collegeRowProps(row: College) {
  return {
    class: row.id === selectedCollegeId.value ? 'is-selected-row' : '',
    onClick: () => {
      selectedCollegeId.value = row.id
      loadMajors()
    }
  }
}

function majorRowProps(row: Major) {
  return {
    class: row.id === selectedMajorId.value ? 'is-selected-row' : '',
    onClick: () => {
      selectedMajorId.value = row.id
    }
  }
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
  await Promise.all([loadMajors(), loadConfigs()])
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
  <PageContainer title="组织与专业" description="维护学校、学院、专业与培养目标联动配置。写操作按 college:manage / major:manage 显隐。">
    <template #actions>
      <n-space>
        <n-tag :bordered="false">学校：{{ schoolText }}</n-tag>
        <n-button secondary @click="refreshAll">刷新</n-button>
        <n-button v-if="canManageCollege" type="primary" @click="openCollegeDrawer()">新增学院</n-button>
      </n-space>
    </template>

    <n-tabs type="line" animated>
      <n-tab-pane name="majors" tab="学院与专业">
        <div class="org-layout">
          <section class="page-section">
            <div class="panel-toolbar">
              <n-input v-model:value="collegeKeyword" clearable placeholder="搜索学院" @keyup.enter="loadColleges" />
              <n-button secondary @click="loadColleges">查询</n-button>
            </div>
            <n-data-table
              :columns="collegeColumns"
              :data="colleges"
              :loading="collegeLoading"
              :row-key="(row: College) => row.id"
              :row-props="collegeRowProps"
              size="small"
              striped
              :max-height="620"
            />
          </section>

          <section class="page-section">
            <div class="panel-toolbar wrap">
              <n-input v-model:value="majorKeyword" clearable placeholder="搜索专业" style="width: 190px" @keyup.enter="loadMajors" />
              <n-input v-model:value="majorYearVersion" clearable maxlength="16" placeholder="年度" style="width: 118px" />
              <n-select
                v-model:value="pilotScopeFlag"
                clearable
                placeholder="试点"
                style="width: 104px"
                :options="[
                  { label: '是', value: 1 },
                  { label: '否', value: 0 }
                ]"
              />
              <n-select
                v-model:value="majorStatus"
                clearable
                placeholder="状态"
                style="width: 104px"
                :options="[
                  { label: '启用', value: 1 },
                  { label: '停用', value: 0 }
                ]"
              />
              <n-button secondary @click="loadMajors">查询</n-button>
              <n-button v-if="canManageMajor" type="primary" :disabled="!canCreateMajor" @click="openMajorDrawer()">新增专业</n-button>
            </div>
            <n-data-table
              :columns="majorColumns"
              :data="majors"
              :loading="majorLoading"
              :row-key="(row: Major) => row.id"
              :row-props="majorRowProps"
              size="small"
              striped
              :max-height="620"
            />
          </section>
        </div>
      </n-tab-pane>

      <n-tab-pane name="configs" tab="联动配置">
        <section class="page-section">
          <div class="panel-toolbar wrap">
            <n-select v-model:value="selectedConfigCode" :options="trainingGoalOptions" clearable placeholder="培养目标" style="width: 240px" @update:value="loadConfigs" />
            <n-button secondary @click="loadConfigs">查询</n-button>
            <n-button v-if="canManageMajor" type="primary" @click="openConfigDrawer()">新增/维护配置</n-button>
          </div>
          <n-data-table
            :columns="configColumns"
            :data="configs"
            :loading="configLoading"
            :row-key="(row: TrainingGoalConfig) => row.trainingGoalCode"
            size="small"
            striped
            :max-height="620"
          />
        </section>
      </n-tab-pane>
    </n-tabs>

    <n-drawer v-model:show="collegeDrawerVisible" :width="420" placement="right">
      <n-drawer-content :title="editingCollegeId ? '编辑学院' : '新增学院'">
        <n-form ref="collegeFormRef" :model="collegeForm" :rules="collegeRules" label-placement="top">
          <n-form-item label="学院编码" path="code">
            <n-input v-model:value="collegeForm.code" maxlength="64" show-count />
          </n-form-item>
          <n-form-item label="学院名称" path="name">
            <n-input v-model:value="collegeForm.name" maxlength="128" show-count />
          </n-form-item>
          <div class="form-grid">
            <n-form-item label="排序" path="sort">
              <n-input-number v-model:value="collegeForm.sort" :min="0" />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-switch v-model:value="collegeForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="collegeDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveCollege">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="majorDrawerVisible" :width="520" placement="right">
      <n-drawer-content :title="editingMajorId ? '编辑专业' : '新增专业'">
        <n-form ref="majorFormRef" :model="majorForm" :rules="majorRules" label-placement="top">
          <n-form-item label="学院" path="collegeId">
            <n-select v-model:value="majorForm.collegeId" :options="collegeOptions" filterable />
          </n-form-item>
          <n-form-item label="校内专业代码" path="internalMajorCode">
            <n-input v-model:value="majorForm.internalMajorCode" maxlength="64" show-count />
          </n-form-item>
          <n-form-item label="校内专业名称" path="internalMajorName">
            <n-input v-model:value="majorForm.internalMajorName" maxlength="128" show-count />
          </n-form-item>
          <div class="form-grid">
            <n-form-item label="二级学科代码" path="secondDisciplineCode">
              <n-input v-model:value="majorForm.secondDisciplineCode" maxlength="64" show-count />
            </n-form-item>
            <n-form-item label="二级学科名称" path="secondDisciplineName">
              <n-input v-model:value="majorForm.secondDisciplineName" maxlength="128" show-count />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="年度版本" path="yearVersion">
              <n-input v-model:value="majorForm.yearVersion" maxlength="16" show-count />
            </n-form-item>
            <n-form-item label="试点范围" path="pilotScopeFlag">
              <n-switch v-model:value="majorForm.pilotScopeFlag" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="排序" path="sort">
              <n-input-number v-model:value="majorForm.sort" :min="0" />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-switch v-model:value="majorForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="majorDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveMajor">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="goalDrawerVisible" :width="460" placement="right">
      <n-drawer-content title="专业培养目标">
        <n-form ref="goalFormRef" :model="goalForm" :rules="goalRules" label-placement="top">
          <n-form-item label="专业">
            <n-input :value="selectedMajor ? `${selectedMajor.internalMajorName} ${selectedMajor.internalMajorCode}` : ''" disabled />
          </n-form-item>
          <n-form-item label="培养目标" path="trainingGoalCodes">
            <n-select v-model:value="goalForm.trainingGoalCodes" :options="trainingGoalOptions" multiple filterable />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="goalDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveGoals">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="configDrawerVisible" :width="520" placement="right">
      <n-drawer-content title="培养目标联动配置">
        <n-form ref="configFormRef" :model="configForm" :rules="configRules" label-placement="top">
          <n-form-item label="培养目标" path="trainingGoalCode">
            <n-select v-model:value="configForm.trainingGoalCode" :options="trainingGoalOptions" filterable />
          </n-form-item>
          <n-form-item label="允许任教学段" path="allowedSegments">
            <n-select v-model:value="configForm.allowedSegments" :options="segmentOptions" multiple filterable />
          </n-form-item>
          <n-form-item label="默认任教学段" path="defaultSegment">
            <n-select v-model:value="configForm.defaultSegment" :options="segmentOptions" filterable />
          </n-form-item>
          <n-form-item label="允许实习地点" path="allowedInternshipLocations">
            <n-select v-model:value="configForm.allowedInternshipLocations" :options="internshipLocationOptions" multiple filterable />
          </n-form-item>
          <n-form-item label="默认实习地点" path="defaultInternshipLocation">
            <n-select v-model:value="configForm.defaultInternshipLocation" :options="internshipLocationOptions" filterable />
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-switch v-model:value="configForm.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item>
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
  gap: 16px;
  align-items: start;
}

.page-section {
  min-width: 0;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-toolbar.wrap {
  flex-wrap: wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: #eef4ff;
  color: var(--brand);
  font-size: 12px;
}

:deep(.is-selected-row td) {
  background: #eef5ff;
}

@media (max-width: 1220px) {
  .org-layout {
    grid-template-columns: 1fr;
  }
}
</style>
