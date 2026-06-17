<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useMessage, type DataTableColumns } from 'naive-ui'
import * as echarts from 'echarts'
import { exportStatsReport, getStatsReport, saveStatsBlob, type StatsDetail, type StatsQuery, type StatsReport, type StatsRow } from '@/api/stats'

interface StatsTypeOption {
  label: string
  value: string
}

const message = useMessage()
const loading = ref(false)
const exporting = ref(false)
const selectedType = ref('submission')
const query = ref<StatsQuery>({ assessmentYear: '2026' })
const report = ref<StatsReport | null>(null)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const statTypes: StatsTypeOption[] = [
  { label: '学院提交进度', value: 'submission' },
  { label: '材料完成率', value: 'materials' },
  { label: '免考统计', value: 'exemptions' },
  { label: '视频评审进度', value: 'videos' },
  { label: '证书生成统计', value: 'certificates' },
  { label: '学段学科交叉', value: 'cross' },
  { label: '异常数据', value: 'anomalies' },
  { label: '导入导出日志', value: 'batches' }
]

const rowColumns: DataTableColumns<StatsRow> = [
  { title: '维度', key: 'dimensionLabel', minWidth: 160 },
  { title: '状态', key: 'statusLabel', minWidth: 140 },
  { title: '数量', key: 'count', minWidth: 100 },
  {
    title: '扩展',
    key: 'values',
    minWidth: 260,
    render: (row) => Object.entries(row.values || {}).map(([key, value]) => `${key}: ${value}`).join(' / ')
  }
]

const detailColumns: DataTableColumns<StatsDetail> = [
  { title: '学号', key: 'studentNo', minWidth: 120 },
  { title: '姓名', key: 'studentName', minWidth: 120 },
  { title: '学院', key: 'collegeName', minWidth: 160 },
  { title: '字段/项目', key: 'fieldName', minWidth: 160 },
  { title: '原因/状态', key: 'errorReason', minWidth: 180 },
  {
    title: '明细',
    key: 'values',
    minWidth: 260,
    render: (row) => Object.entries(row.values || {}).map(([key, value]) => `${key}: ${value}`).join(' / ')
  }
]

const chartRows = computed(() => report.value?.rows || [])
const metrics = computed(() => report.value?.metrics || [])
const details = computed(() => report.value?.details || [])

watch([selectedType, query], () => loadReport(), { deep: true })
watch(chartRows, () => nextTick(renderChart), { deep: true })

onMounted(async () => {
  await loadReport()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

async function loadReport() {
  loading.value = true
  try {
    const res = await getStatsReport(selectedType.value, query.value)
    report.value = res.data
    await nextTick()
    renderChart()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '统计加载失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportStatsReport(selectedType.value, query.value)
    saveStatsBlob(blob, `${report.value?.title || selectedType.value}.xlsx`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  const rows = chartRows.value.slice(0, 20)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 24, bottom: 72 },
    xAxis: {
      type: 'category',
      axisLabel: { rotate: 35 },
      data: rows.map((row) => `${row.dimensionLabel || row.dimension}\n${row.statusLabel || row.status}`)
    },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: rows.map((row) => row.count || 0), itemStyle: { color: '#2f7d6b' } }]
  })
}

function resizeChart() {
  chart?.resize()
}
</script>

<template>
  <n-space vertical :size="16">
    <n-space justify="space-between" align="center">
      <n-space align="center">
        <n-select v-model:value="selectedType" :options="statTypes" style="width: 180px" />
        <n-input v-model:value="query.assessmentYear" placeholder="考核年度" clearable style="width: 120px" />
        <n-input v-model:value="query.keyword" placeholder="学号/姓名/批次" clearable style="width: 180px" />
        <n-input v-model:value="query.className" placeholder="班级" clearable style="width: 140px" />
      </n-space>
      <n-space>
        <n-button :loading="loading" @click="loadReport">刷新</n-button>
        <n-button type="primary" :loading="exporting" @click="handleExport">导出 Excel</n-button>
      </n-space>
    </n-space>

    <n-alert v-if="report?.denominatorRule" type="info" :bordered="false">
      {{ report.denominatorRule }}
    </n-alert>

    <n-grid :cols="4" :x-gap="12" responsive="screen">
      <n-grid-item v-for="metric in metrics" :key="metric.label">
        <n-statistic :label="metric.label" :value="metric.value">
          <template v-if="metric.unit" #suffix>{{ metric.unit }}</template>
        </n-statistic>
      </n-grid-item>
    </n-grid>

    <div ref="chartRef" class="chart"></div>

    <n-data-table :loading="loading" :columns="rowColumns" :data="chartRows" :pagination="{ pageSize: 10 }" />

    <n-data-table
      v-if="details.length"
      :columns="detailColumns"
      :data="details"
      :pagination="{ pageSize: 8 }"
      size="small"
    />
  </n-space>
</template>

<style scoped>
.chart {
  height: 280px;
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
</style>
