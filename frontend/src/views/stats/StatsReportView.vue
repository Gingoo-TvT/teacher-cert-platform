<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import { BarChartOutline, GridOutline, ListOutline, PeopleOutline } from '@vicons/ionicons5'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { exportStatsReport, getStatsReport, saveStatsBlob, type StatsDetail, type StatsQuery, type StatsReport, type StatsRow } from '@/api/stats'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

interface StatsTypeOption {
  label: string
  value: string
  description: string
}

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const exporting = ref(false)
const selectedType = ref('submission')
const report = ref<StatsReport | null>(null)
const query = reactive<StatsQuery>({
  assessmentYear: yearStore.assessmentYear,
  keyword: '',
  className: '',
  teachingSegment: '',
  status: ''
})

const statTypes: StatsTypeOption[] = [
  { label: '学院提交进度', value: 'submission', description: '按学院、专业、班级和学生状态汇总提交进度。' },
  { label: '材料完成率', value: 'materials', description: '四类材料提交、初审、复审和异常明细对账。' },
  { label: '免考统计', value: 'exemptions', description: '按科目、学院和专业统计通过、不通过及明细。' },
  { label: '视频评审进度', value: 'videos', description: '统计上传、待评审、已评审、需复评和专家任务。' },
  { label: '证书生成统计', value: 'certificates', description: '按证书状态汇总待生成、已生成、已签发、已导出、已作废。' },
  { label: '学段学科交叉', value: 'cross', description: '任教学段、任教学科、身份类型和学历层次交叉表。' },
  { label: '异常数据', value: 'anomalies', description: '字段缺失、证件、专业代码、证书编号和学段学科异常定位。' },
  { label: '导入导出日志', value: 'batches', description: '按批次、操作人、时间、成功失败和范围统计。' }
]

const typeOptions: SelectOption[] = statTypes.map((item) => ({ label: item.label, value: item.value }))
const currentType = computed(() => statTypes.find((item) => item.value === selectedType.value) || statTypes[0])
const metrics = computed(() => report.value?.metrics || [])
const rows = computed(() => report.value?.rows || [])
const details = computed(() => report.value?.details || [])
const hasDetails = computed(() => details.value.length > 0)
const canViewStats = computed(() => userStore.hasPerm('stats:view'))
const summary = computed(() => {
  const total = rows.value.reduce((sum, row) => sum + Number(row.count || 0), 0)
  const dimensions = new Set(rows.value.map((row) => row.dimensionLabel || row.dimension).filter(Boolean)).size
  return {
    rowCount: rows.value.length,
    total,
    dimensions,
    detailCount: details.value.length
  }
})

// 图表策略：类目 ≤8 且有数值时画环形图（占比一目了然）；类目过多时不画图，直接看下方列表。
const chartRows = computed(() => rows.value.filter((row) => Number(row.count || 0) > 0))
const showChart = computed(() => chartRows.value.length > 0 && chartRows.value.length <= 8)
const chartOption = computed<EChartsOption>(() => {
  return {
    legend: { show: false },
    series: [
      {
        name: currentType.value.label,
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
        label: { formatter: '{b}\n{c} ({d}%)', color: 'inherit', fontSize: 13, lineHeight: 18 },
        labelLine: { length: 14, length2 : 10 },
        data: chartRows.value.map((row) => ({ name: chartLabel(row), value: Number(row.count || 0) }))
      }
    ]
  }
})

const rowColumns: DataTableColumns<StatsRow> = [
  { title: '统计维度', key: 'dimensionLabel', minWidth: 220, ellipsis: { tooltip: true }, render: (row) => row.dimensionLabel || row.dimension || '-' },
  { title: '状态', key: 'statusLabel', minWidth: 150, ellipsis: { tooltip: true }, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || row.status || '-' }) },
  { title: '数量', key: 'count', width: 104, align: 'right', render: (row) => h('span', { class: 'numeric tabular-nums' }, String(row.count || 0)) },
  { title: '扩展指标', key: 'values', minWidth: 360, render: (row) => renderValues(row.values) }
]

const detailColumns: DataTableColumns<StatsDetail> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true }, render: (row) => row.studentName || '-' },
  { title: '学院', key: 'collegeName', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.collegeName || '-' },
  { title: '字段/项目', key: 'fieldName', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.fieldName || '-' },
  { title: '原因/状态', key: 'errorReason', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => row.errorReason || '-' },
  { title: '明细值', key: 'values', minWidth: 360, render: (row) => renderValues(row.values) }
]

async function loadReport() {
  if (!canViewStats.value) {
    report.value = null
    return
  }
  loading.value = true
  try {
    const res = await getStatsReport(selectedType.value, cleanQuery())
    report.value = res.data
  } catch (error) {
    showError(error, '统计加载失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  if (!canViewStats.value) return
  exporting.value = true
  try {
    const blob = await exportStatsReport(selectedType.value, cleanQuery())
    saveStatsBlob(blob, `${report.value?.title || currentType.value.label}.xlsx`)
    message.success('统计报表已导出')
  } catch (error) {
    showError(error, '导出失败')
  } finally {
    exporting.value = false
  }
}

function selectType(value: string) {
  selectedType.value = value
  if (canViewStats.value) loadReport()
}

function resetQuery() {
  Object.assign(query, {
    assessmentYear: yearStore.assessmentYear,
    keyword: '',
    className: '',
    teachingSegment: '',
    status: ''
  })
  if (canViewStats.value) loadReport()
}

function cleanQuery(): StatsQuery {
  return { ...query }
}

function chartLabel(row: StatsRow) {
  const dimension = row.dimensionLabel || row.dimension || '-'
  const status = row.statusLabel || row.status
  return status ? `${dimension} / ${status}` : dimension
}

function renderValues(values?: Record<string, string>) {
  const entries = Object.entries(values || {})
  if (!entries.length) return '-'
  return h(
    'div',
    { class: 'value-list' },
    entries.map(([key, value]) =>
      h('span', { class: 'value-item' }, [
        h('span', { class: 'value-key' }, `${key}: `),
        h('span', { class: 'mono' }, value || '-')
      ])
    )
  )
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(() => {
  if (canViewStats.value) void loadReport()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    query.assessmentYear = year
    if (canViewStats.value) await loadReport()
  }
)
</script>

<template>
  <PageContainer title="统计报表" description="统计报表，支持图表、表格与 Excel 导出。">
    <template #actions>
      <n-space>
        <n-button v-if="canViewStats" secondary :loading="loading" @click="loadReport">刷新</n-button>
        <n-button v-if="canViewStats" type="primary" :loading="exporting" @click="handleExport">导出 Excel</n-button>
      </n-space>
    </template>

    <n-empty v-if="!canViewStats" description="当前账号没有统计查看权限" class="page-section" />

    <FilterBar v-if="canViewStats" :loading="loading" @submit="loadReport" @reset="resetQuery">
      <label class="filter-field">
        <span>统计类型</span>
        <n-select :value="selectedType" :options="typeOptions" placeholder="统计类型" style="width: 190px" @update:value="selectType" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="query.assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="query.keyword" clearable placeholder="学号/姓名/批次/证书编号" style="width: 240px" @keyup.enter="loadReport" />
      </label>
      <label class="filter-field">
        <span>班级</span>
        <n-input v-model:value="query.className" clearable placeholder="班级" style="width: 150px" @keyup.enter="loadReport" />
      </label>
      <template #more>
        <label class="filter-field">
          <span>任教学段</span>
          <n-input v-model:value="query.teachingSegment" clearable placeholder="任教学段" style="width: 150px" @keyup.enter="loadReport" />
        </label>
        <label class="filter-field">
          <span>状态</span>
          <n-input v-model:value="query.status" clearable placeholder="状态" style="width: 150px" @keyup.enter="loadReport" />
        </label>
      </template>
    </FilterBar>

    <n-grid v-if="canViewStats" :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="统计行数" :value="summary.rowCount" :icon="ListOutline" /></n-gi>
      <n-gi><StatCard label="汇总数量" :value="summary.total" :icon="BarChartOutline" tone="success" /></n-gi>
      <n-gi><StatCard label="维度数" :value="summary.dimensions" :icon="GridOutline" tone="info" /></n-gi>
      <n-gi><StatCard label="明细数" :value="summary.detailCount" :icon="PeopleOutline" tone="warning" /></n-gi>
    </n-grid>

    <n-grid v-if="canViewStats && metrics.length" :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-for="metric in metrics" :key="metric.label">
        <StatCard :label="metric.label" :value="metric.value" :unit="metric.unit" />
      </n-gi>
    </n-grid>

    <n-alert v-if="canViewStats && report?.denominatorRule" type="info" :bordered="false" class="page-section">
      {{ report.denominatorRule }}
    </n-alert>

    <n-card v-if="canViewStats && showChart" :bordered="false" class="page-section chart-card">
      <template #header>
        <n-space vertical :size="2">
          <span>{{ report?.title || currentType.label }}</span>
          <span class="muted">{{ currentType.description }}</span>
        </n-space>
      </template>
      <ChartBox :option="chartOption" height="320px" />
    </n-card>

    <DataPanel
      v-if="canViewStats"
      title="统计汇总"
      :columns="rowColumns"
      :data="rows"
      :total="rows.length"
      :loading="loading"
      empty-title="暂无统计数据"
      empty-description="当前查询条件下没有统计汇总行。"
      @refresh="loadReport"
    />

    <DataPanel
      v-if="canViewStats && hasDetails"
      title="钻取明细"
      :columns="detailColumns"
      :data="details"
      size="small"
      :page-size="8"
      :show-refresh="false"
      empty-title="暂无钻取明细"
      empty-description="当前统计结果没有可钻取明细。"
    />
  </PageContainer>
</template>

<style scoped>
.chart-card :deep(.n-card-header) {
  padding-bottom: 0;
}

.muted {
  font-size: 12px;
  color: var(--text-muted);
}

.value-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  line-height: 20px;
}

.value-item {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.value-key {
  color: var(--text-muted);
  white-space: nowrap;
}
</style>
