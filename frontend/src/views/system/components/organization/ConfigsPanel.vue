<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
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
import { listTrainingGoalConfigs, type TrainingGoalConfig } from '@/api/organization'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'
import ConfigDrawer from './ConfigDrawer.vue'

const message = useMessage()
const userStore = useUserStore()

const configLoading = ref(false)
const selectedConfigCode = ref<string | null>(null)
const trainingGoals = ref<DictItem[]>([])
const teachingSegments = ref<DictItem[]>([])
const internshipLocations = ref<DictItem[]>([])
const configs = ref<TrainingGoalConfig[]>([])

const configDrawer = ref<InstanceType<typeof ConfigDrawer> | null>(null)

const canManageMajor = computed(() => userStore.hasPerm('major:manage'))

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
      render: (row) => renderTableActions([h(NButton, { size: 'small', quaternary: true, onClick: () => configDrawer.value?.open(row) }, { default: () => '编辑' })])
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
  const [goals, segments, locations] = await Promise.all([
    listDictItems('training_goal', true),
    listDictItems('teaching_segment', true),
    listDictItems('internship_location', true)
  ])
  trainingGoals.value = sortDict(goals.data)
  teachingSegments.value = sortDict(segments.data)
  internshipLocations.value = sortDict(locations.data)
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

function renderCodeTags(codes: string[], dictItems: DictItem[]) {
  return h(NSpace, { size: 6 }, () => codes.map((code) => h('span', { class: 'pill', key: code }, dictName(dictItems, code))))
}

function dictName(items: DictItem[], code: string) {
  return items.find((item) => item.itemCode === code)?.itemValue || code
}

function sortDict(items: DictItem[]) {
  return [...items].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadDictionaries()
  await loadConfigs()
})

defineExpose({ refresh: loadConfigs })
</script>

<template>
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
          <n-button v-if="canManageMajor" type="primary" size="small" @click="configDrawer?.open()">新增/维护配置</n-button>
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
  .config-layout {
    grid-template-columns: 1fr;
  }
}
</style>
