<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NSpace,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { listTrainingGoalConfigs, type TrainingGoalConfig } from '@/api/organization'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'
import ConfigDrawer from './ConfigDrawer.vue'

const userStore = useUserStore()

const configLoading = ref(false)
const dictionaryLoading = ref(false)
const configError = ref('')
const trainingGoalsError = ref('')
const teachingSegmentsError = ref('')
const internshipLocationsError = ref('')
const hasLoadedConfigs = ref(false)
const hasLoadedTrainingGoals = ref(false)
const hasLoadedTeachingSegments = ref(false)
const hasLoadedInternshipLocations = ref(false)
const selectedConfigCode = ref<string | null>(null)
const trainingGoals = ref<DictItem[]>([])
const teachingSegments = ref<DictItem[]>([])
const internshipLocations = ref<DictItem[]>([])
const configs = ref<TrainingGoalConfig[]>([])

const configDrawer = ref<InstanceType<typeof ConfigDrawer> | null>(null)

const canManageMajor = computed(() => userStore.hasPerm('major:manage'))
const configListReady = computed(() => hasLoadedConfigs.value && !configLoading.value && !configError.value)
const dictionaryError = computed(() =>
  [trainingGoalsError.value, teachingSegmentsError.value, internshipLocationsError.value].filter(Boolean).join('；')
)
const dictionariesReady = computed(() =>
  hasLoadedTrainingGoals.value
  && hasLoadedTeachingSegments.value
  && hasLoadedInternshipLocations.value
  && !dictionaryLoading.value
  && !dictionaryError.value
)
const canWriteConfig = computed(() => canManageMajor.value && configListReady.value && dictionariesReady.value)

const trainingGoalOptions = computed<SelectOption[]>(() => trainingGoals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => teachingSegments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const internshipLocationOptions = computed<SelectOption[]>(() => internshipLocations.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))

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
      render: (row) => renderTableActions([h(NButton, { size: 'small', quaternary: true, disabled: !canWriteConfig.value, onClick: () => configDrawer.value?.open(row) }, { default: () => '编辑' })])
    })
  }
  return columns
})

const selectedConfig = computed(() => configs.value.find((item) => item.trainingGoalCode === selectedConfigCode.value) || configs.value[0] || null)

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

function configRowProps(row: object) {
  const item = row as TrainingGoalConfig
  return {
    class: item.trainingGoalCode === selectedConfig.value?.trainingGoalCode ? 'is-selected-row' : '',
    onClick: () => {
      selectedConfigCode.value = item.trainingGoalCode
    }
  }
}

function resetConfigFilters() {
  selectedConfigCode.value = null
  void loadConfigs()
}

async function loadDictionaries() {
  dictionaryLoading.value = true
  await Promise.all([loadTrainingGoals(), loadTeachingSegments(), loadInternshipLocations()])
  dictionaryLoading.value = false
}

async function loadTrainingGoals() {
  try {
    const res = await listDictItems('training_goal', true)
    trainingGoals.value = sortDict(res.data)
    trainingGoalsError.value = ''
    hasLoadedTrainingGoals.value = true
  } catch (error) {
    trainingGoalsError.value = `培养目标：${errorText(error, '选项加载失败')}`
  }
}

async function loadTeachingSegments() {
  try {
    const res = await listDictItems('teaching_segment', true)
    teachingSegments.value = sortDict(res.data)
    teachingSegmentsError.value = ''
    hasLoadedTeachingSegments.value = true
  } catch (error) {
    teachingSegmentsError.value = `任教学段：${errorText(error, '选项加载失败')}`
  }
}

async function loadInternshipLocations() {
  try {
    const res = await listDictItems('internship_location', true)
    internshipLocations.value = sortDict(res.data)
    internshipLocationsError.value = ''
    hasLoadedInternshipLocations.value = true
  } catch (error) {
    internshipLocationsError.value = `实习地点：${errorText(error, '选项加载失败')}`
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
    configError.value = ''
    hasLoadedConfigs.value = true
  } catch (error) {
    configError.value = errorText(error, '培养目标联动配置加载失败')
  } finally {
    configLoading.value = false
  }
}

async function refresh() {
  await Promise.all([loadDictionaries(), loadConfigs()])
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

function errorText(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : ''
  return detail || fallback
}

onMounted(() => {
  void refresh()
})

defineExpose({ refresh })
</script>

<template>
  <n-alert v-if="dictionaryError" type="warning" title="联动选项加载失败" class="dependency-alert" role="alert">
    <div class="dependency-alert__content">
      <span>{{ dictionaryError }}。配置列表仍可查看，新增和编辑暂不可用。</span>
      <n-button size="small" secondary :loading="dictionaryLoading" @click="loadDictionaries">重试</n-button>
    </div>
  </n-alert>

  <n-grid cols="1 1180:20" responsive="self" item-responsive :x-gap="24" :y-gap="24" class="config-layout">
    <n-gi span="1 1180:12" class="page-section">
      <FilterBar :loading="configLoading" @submit="loadConfigs" @reset="resetConfigFilters">
        <label class="filter-field">
          <span>培养目标</span>
          <n-select
            v-model:value="selectedConfigCode"
            :options="trainingGoalOptions"
            :loading="dictionaryLoading"
            :disabled="!hasLoadedTrainingGoals || Boolean(trainingGoalsError)"
            clearable
            placeholder="全部培养目标"
            class="config-filter"
          />
        </label>
      </FilterBar>
      <DataPanel
        title="联动配置"
        :columns="configColumns"
        :data="configs"
        :total="configs.length"
        :loading="configLoading"
        :initial-loading="configLoading && !hasLoadedConfigs"
        :error="configError || undefined"
        :row-props="configRowProps"
        :max-height="620"
        empty-title="暂无联动配置"
        empty-description="当前筛选条件下没有培养目标联动配置。"
        @refresh="loadConfigs"
      >
        <template #actions>
          <n-button v-if="canManageMajor" type="primary" size="small" :disabled="!canWriteConfig" @click="configDrawer?.open()">新增/维护配置</n-button>
        </template>
      </DataPanel>
    </n-gi>
    <n-gi v-if="selectedConfig || configListReady" span="1 1180:8" class="page-section">
      <n-card :bordered="false" class="detail-card">
        <div class="detail-head">
          <div>
            <strong>{{ selectedConfig?.trainingGoalName || '配置详情' }}</strong>
            <span class="muted mono">{{ selectedConfig?.trainingGoalCode || '请选择配置' }}</span>
          </div>
        </div>
        <DetailPanel v-if="selectedConfig" :items="configDetailItems" :columns="2" />
        <n-empty v-else :description="configs.length ? '请选择培养目标配置' : '暂无联动配置可供选择'" />
      </n-card>
    </n-gi>
  </n-grid>

  <ConfigDrawer
    ref="configDrawer"
    :training-goal-options="trainingGoalOptions"
    :segment-options="segmentOptions"
    :internship-location-options="internshipLocationOptions"
    :default-config-code="selectedConfigCode"
    @saved="loadConfigs"
  />
</template>

<style scoped>
.config-layout {
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

.dependency-alert {
  margin-bottom: var(--space-4);
}

.dependency-alert__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.config-filter {
  width: 240px;
  max-width: 100%;
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

@media (max-width: 720px) {
  .dependency-alert__content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
