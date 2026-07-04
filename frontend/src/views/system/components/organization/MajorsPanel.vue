<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import {
  deleteCollege,
  deleteMajor,
  listColleges,
  listMajors,
  type College,
  type Major
} from '@/api/organization'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'
import CollegeDrawer from './CollegeDrawer.vue'
import MajorDrawer from './MajorDrawer.vue'
import GoalDrawer from './GoalDrawer.vue'

const message = useMessage()
const userStore = useUserStore()

const collegeLoading = ref(false)
const majorLoading = ref(false)
const selectedCollegeId = ref<string | null>(null)
const selectedMajorId = ref<string | null>(null)
const collegeKeyword = ref('')
const majorKeyword = ref('')
const majorYearVersion = ref('GLOBAL')
const majorStatus = ref<number | null>(null)
const pilotScopeFlag = ref<number | null>(null)
const colleges = ref<College[]>([])
const majors = ref<Major[]>([])
const trainingGoals = ref<DictItem[]>([])

const collegeDrawer = ref<InstanceType<typeof CollegeDrawer> | null>(null)
const majorDrawer = ref<InstanceType<typeof MajorDrawer> | null>(null)
const goalDrawer = ref<InstanceType<typeof GoalDrawer> | null>(null)

const canManageCollege = computed(() => userStore.hasPerm('college:manage'))
const canManageMajor = computed(() => userStore.hasPerm('major:manage'))
const canLoadCollegeList = computed(() => canManageCollege.value || canManageMajor.value)

const collegeOptions = computed<SelectOption[]>(() =>
  colleges.value.filter((item) => item.status === 1).map((item) => ({ label: item.name, value: item.id }))
)

const trainingGoalOptions = computed<SelectOption[]>(() => trainingGoals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))

const selectedCollege = computed(() => colleges.value.find((item) => item.id === selectedCollegeId.value) || null)
const canCreateMajor = computed(() => canManageMajor.value && selectedCollege.value?.status === 1)
const selectedMajor = computed(() => majors.value.find((item) => item.id === selectedMajorId.value) || null)

const collegeColumns = computed<DataTableColumns<College>>(() => {
  const columns: DataTableColumns<College> = [
    { title: '学院编码', key: 'code', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.code) },
    { title: '学院名称', key: 'name', minWidth: 160, ellipsis: { tooltip: true } },
    { title: '排序', key: 'sort', width: 72, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManageCollege.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        renderTableActions([
          h(NButton, { size: 'small', quaternary: true, onClick: () => collegeDrawer.value?.open(row) }, { default: () => '编辑' }),
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
    { title: '专业名称', key: 'internalMajorName', minWidth: 180, ellipsis: { tooltip: true } },
    { title: '学院', key: 'collegeName', minWidth: 160, ellipsis: { tooltip: true } },
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
          h(NButton, { size: 'small', quaternary: true, onClick: () => majorDrawer.value?.open(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', quaternary: true, onClick: () => goalDrawer.value?.open(row) }, { default: () => '目标' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => disableMajor(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'warning' }, { default: () => '停用' }),
              default: () => '确认停用该专业？停用后不再可选用（可在编辑中恢复启用），已有引用不受影响。'
            }
          )
        ])
    })
  }
  return columns
})

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

async function refresh() {
  await loadColleges()
  const tasks: Promise<void>[] = []
  if (canManageMajor.value) tasks.push(loadMajors())
  await Promise.all(tasks)
}

function onCollegeSaved(createdId: string | null) {
  if (createdId) selectedCollegeId.value = createdId
  void refresh()
}

function onMajorSaved(majorId: string) {
  selectedMajorId.value = majorId
  void loadMajors()
}

async function removeCollege(row: College) {
  try {
    await deleteCollege(row.id)
    message.success('学院已删除')
    if (selectedCollegeId.value === row.id) selectedCollegeId.value = null
    await refresh()
  } catch (error) {
    showError(error, '学院删除失败')
  }
}

async function disableMajor(row: Major) {
  try {
    // 后端 DELETE /major/{id} 现为“停用”语义（status→0），行仍在库、引用不孤儿、可恢复启用。
    await deleteMajor(row.id)
    message.success('专业已停用')
    if (selectedMajorId.value === row.id) selectedMajorId.value = null
    await loadMajors()
  } catch (error) {
    showError(error, '专业停用失败')
  }
}

function openCollegeDrawer() {
  collegeDrawer.value?.open()
}

function sortDict(items: DictItem[]) {
  return [...items].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  const res = await listDictItems('training_goal', true)
  trainingGoals.value = sortDict(res.data)
  await refresh()
})

defineExpose({ refresh, openCollegeDrawer })
</script>

<template>
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
          <n-button v-if="canManageCollege" type="primary" size="small" @click="collegeDrawer?.open()">新增学院</n-button>
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
        empty-title="暂无专业"
        empty-description="当前筛选条件下没有专业记录。"
        @refresh="loadMajors"
      >
        <template #actions>
          <n-button v-if="canManageMajor" type="primary" size="small" :disabled="!canCreateMajor" @click="majorDrawer?.open()">新增专业</n-button>
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

  <CollegeDrawer ref="collegeDrawer" @saved="onCollegeSaved" />
  <MajorDrawer
    ref="majorDrawer"
    :college-options="collegeOptions"
    :selected-college="selectedCollege"
    :can-create-major="canCreateMajor"
    :default-year-version="majorYearVersion"
    @saved="onMajorSaved"
  />
  <GoalDrawer
    ref="goalDrawer"
    :training-goal-options="trainingGoalOptions"
    :selected-major="selectedMajor"
    @select="selectedMajorId = $event"
    @saved="loadMajors"
  />
</template>

<style scoped>
.org-layout {
  display: grid;
  grid-template-columns: minmax(360px, 0.7fr) minmax(720px, 1.3fr);
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
  .org-layout {
    grid-template-columns: 1fr;
  }
}
</style>
