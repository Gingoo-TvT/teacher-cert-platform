<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { exportStatsReport, getStatsReport, saveStatsBlob, type StatsDetail, type StatsQuery, type StatsReport, type StatsRow } from '@/api/stats'

interface StatsTypeOption {
  label: string
  value: string
  description: string
}

const message = useMessage()
const loading = ref(false)
const exporting = ref(false)
const selectedType = ref('submission')
const report = ref<StatsReport | null>(null)
const query = reactive<StatsQuery>({
  assessmentYear: '2026',
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

const chartOption = computed<EChartsOption>(() => {
  const chartRows = rows.value.slice(0, 20)
  return {
    color: ['#2f7d6b'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 76 },
    xAxis: {
      type: 'category',
      axisLabel: { rotate: 35, color: '#6b7280' },
      axisTick: { alignWithLabel: true },
      data: chartRows.map((row) => chartLabel(row))
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name: currentType.value.label,
        type: 'bar',
        barMaxWidth: 34,
        data: chartRows.map((row) => row.count || 0)
      }
    ]
  }
})

const rowColumns: DataTableColumns<StatsRow> = [
  { title: '统计维度', key: 'dimensionLabel', minWidth: 180, render: (row) => row.dimensionLabel || row.dimension || '-' },
  { title: '状态', key: 'statusLabel', minWidth: 140, render: (row) => h(StatusTag, { text: row.statusLabel || row.status || '-' }) },
  { title: '数量', key: 'count', width: 100, render: (row) => h('span', { class: 'mono' }, String(row.count || 0)) },
  { title: '扩展指标', key: 'values', minWidth: 300, render: (row) => renderValues(row.values) }
]

const detailColumns: DataTableColumns<StatsDetail> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true }, render: (row) => row.studentName || '-' },
  { title: '学院', key: 'collegeName', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.collegeName || '-' },
  { title: '字段/项目', key: 'fieldName', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.fieldName || '-' },
  { title: '原因/状态', key: 'errorReason', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => row.errorReason || '-' },
  { title: '明细值', key: 'values', minWidth: 300, render: (row) => renderValues(row.values) }
]

async function loadReport() {
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
  loadReport()
}

function resetQuery() {
  Object.assign(query, {
    assessmentYear: '2026',
    keyword: '',
    className: '',
    teachingSegment: '',
    status: ''
  })
  loadReport()
}

function cleanQuery(): StatsQuery {
  return { ...query }
}

function chartLabel(row: StatsRow) {
  const dimension = row.dimensionLabel || row.dimension || '-'
  const status = row.statusLabel || row.status
  return status ? `${dimension}\n${status}` : dimension
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

onMounted(loadReport)
</script>

<template>
  <PageContainer title="统计报表" description="八类统计报表按当前账号数据范围实时聚合，图表、表格与 Excel 导出共用后端统计口径。">
    <template #actions>
      <n-space>
        <n-button secondary :loading="loading" @click="loadReport">刷新</n-button>
        <n-button type="primary" :loading="exporting" @click="handleExport">导出 Excel</n-button>
      </n-space>
    </template>

    <n-card :bordered="false" size="small" class="page-section">
      <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen">
        <n-gi>
          <n-select :value="selectedType" :options="typeOptions" placeholder="统计类型" @update:value="selectType" />
        </n-gi>
        <n-gi><n-input v-model:value="query.assessmentYear" placeholder="考核年度" /></n-gi>
        <n-gi><n-input v-model:value="query.keyword" clearable placeholder="学号/姓名/批次/证书编号" @keyup.enter="loadReport" /></n-gi>
        <n-gi><n-input v-model:value="query.className" clearable placeholder="班级" @keyup.enter="loadReport" /></n-gi>
        <n-gi><n-input v-model:value="query.teachingSegment" clearable placeholder="任教学段" @keyup.enter="loadReport" /></n-gi>
        <n-gi><n-input v-model:value="query.status" clearable placeholder="状态" @keyup.enter="loadReport" /></n-gi>
        <n-gi>
          <n-space>
            <n-button type="primary" :loading="loading" @click="loadReport">查询</n-button>
            <n-button @click="resetQuery">重置</n-button>
          </n-space>
        </n-gi>
      </n-grid>
    </n-card>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="统计行数" :value="summary.rowCount" /></n-gi>
      <n-gi><StatCard label="汇总数量" :value="summary.total" color="#18a058" /></n-gi>
      <n-gi><StatCard label="维度数" :value="summary.dimensions" color="#2080f0" /></n-gi>
      <n-gi><StatCard label="明细数" :value="summary.detailCount" color="#f0a020" /></n-gi>
    </n-grid>

    <n-grid v-if="metrics.length" :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-for="metric in metrics" :key="metric.label">
        <StatCard :label="metric.label" :value="metric.value" :sub="metric.unit" />
      </n-gi>
    </n-grid>

    <n-alert v-if="report?.denominatorRule" type="info" :bordered="false" class="page-section">
      {{ report.denominatorRule }}
    </n-alert>

    <n-card :bordered="false" class="page-section chart-card">
      <template #header>
        <n-space vertical :size="2">
          <span>{{ report?.title || currentType.label }}</span>
          <span class="muted">{{ currentType.description }}</span>
        </n-space>
      </template>
      <ChartBox :option="chartOption" height="340px" />
    </n-card>

    <n-data-table
      :columns="rowColumns"
      :data="rows"
      :loading="loading"
      :row-key="(row: StatsRow) => `${row.dimension || row.dimensionLabel}-${row.status || row.statusLabel}`"
      :scroll-x="940"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-card v-if="hasDetails" :bordered="false" class="page-section">
      <template #header>钻取明细</template>
      <n-data-table
        :columns="detailColumns"
        :data="details"
        :row-key="(row: StatsDetail) => `${row.studentId || row.studentNo}-${row.fieldName}-${row.errorReason}`"
        :scroll-x="1040"
        :pagination="{ pageSize: 8 }"
        size="small"
        striped
      />
    </n-card>
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
  gap: 6px 12px;
}

.value-item {
  white-space: nowrap;
}

.value-key {
  color: var(--text-muted);
}
</style>
