<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { listColleges, type College } from '@/api/organization'
import {
  exportExchange,
  exportExchangeAttachments,
  listExchangeBatches,
  saveBlob,
  type ExchangeBatch,
  type ExchangeQuery
} from '@/api/exchange'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { formatDateTime } from '@/utils/format'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const exporting = ref(false)
const loading = ref(false)
const batches = ref<ExchangeBatch[]>([])
const statuses = ref<DictItem[]>([])
const segments = ref<DictItem[]>([])
const goals = ref<DictItem[]>([])
const colleges = ref<College[]>([])

const query = reactive<ExchangeQuery>({
  keyword: '',
  assessmentYear: yearStore.assessmentYear,
  collegeId: '',
  internalMajorCode: '',
  className: '',
  trainingGoal: '',
  teachingSegment: '',
  auditStatus: '',
  certStatus: ''
})

const canStandard = computed(() => userStore.hasPerm('exchange:export:standard'))
const canFull = computed(() => userStore.hasPerm('exchange:export:full'))
const canExport = computed(() => canStandard.value || canFull.value)
const exportType = ref('STANDARD')
const exportOptions = computed<SelectOption[]>(() => [
  ...(canStandard.value ? [{ label: '标准上报表', value: 'STANDARD' }] : []),
  ...(canFull.value
    ? [
        { label: '完整审核表', value: 'FULL_REVIEW' },
        { label: '证书获得者汇总表', value: 'CERT_SUMMARY' },
        { label: '异常数据表', value: 'ERROR' }
      ]
    : []),
  ...(canStandard.value ? [{ label: '附件与视频打包', value: 'ATTACHMENT_LIST' }] : [])
])
const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const goalOptions = computed<SelectOption[]>(() => goals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))
const summary = computed(() => ({
  total: batches.value.length,
  exported: batches.value.filter((item) => item.status === 'EXPORTED').length,
  files: batches.value.filter((item) => item.fileName).length
}))

const batchColumns: DataTableColumns<ExchangeBatch> = [
  { title: '批次号', key: 'batchNo', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.batchNo) },
  { title: '类型', key: 'type', width: 90, render: (row) => batchTypeLabel(row.type) },
  { title: '导出项', key: 'strategy', width: 150, render: (row) => exportTypeLabel(row.strategy) },
  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
  { title: '数量', key: 'successCount', width: 90, render: (row) => h('span', { class: 'numeric' }, String(row.successCount ?? 0)) },
  { title: '状态', key: 'status', width: 120, render: (row) => h(StatusTag, { value: row.status, text: batchStatusLabel(row.status) }) },
  { title: '文件', key: 'fileName', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => row.fileName || '-' }
]

async function loadOptions() {
  const [statusRes, segmentRes, goalRes, collegeRes] = await Promise.all([
    listDictItems('certificate_status', true),
    listDictItems('teaching_segment', true),
    listDictItems('training_goal', true),
    listColleges()
  ])
  statuses.value = statusRes.data
  segments.value = segmentRes.data
  goals.value = goalRes.data
  colleges.value = collegeRes.data
}

async function loadBatches() {
  if (!canExport.value) {
    batches.value = []
    return
  }
  loading.value = true
  try {
    const res = await listExchangeBatches('export')
    batches.value = res.data.records
  } catch (error) {
    showError(error, '导出批次加载失败')
  } finally {
    loading.value = false
  }
}

async function runExport() {
  if (!canRunExportType(exportType.value)) return
  exporting.value = true
  try {
    if (exportType.value === 'ATTACHMENT_LIST') {
      const blob = await exportExchangeAttachments(query)
      saveBlob(blob, '附件视频打包.zip')
    } else {
      const blob = await exportExchange(exportType.value, query)
      saveBlob(blob, filename(exportType.value))
    }
    message.success('导出已生成')
    await loadBatches()
  } catch (error) {
    showError(error, '导出失败')
  } finally {
    exporting.value = false
  }
}

function filename(type: string) {
  const names: Record<string, string> = {
    STANDARD: '标准上报表.xlsx',
    FULL_REVIEW: '完整审核表.xlsx',
    CERT_SUMMARY: '证书获得者汇总表.xlsx',
    ERROR: '异常数据表.xlsx'
  }
  return names[type] || '导出数据.xlsx'
}

function resetQuery() {
  Object.assign(query, {
    keyword: '',
    assessmentYear: yearStore.assessmentYear,
    collegeId: '',
    internalMajorCode: '',
    className: '',
    trainingGoal: '',
    teachingSegment: '',
    auditStatus: '',
    certStatus: ''
  })
}

function canRunExportType(type: string) {
  if (type === 'STANDARD' || type === 'ATTACHMENT_LIST') return canStandard.value
  return canFull.value
}

function batchTypeLabel(type?: string | null) {
  const map: Record<string, string> = {
    import: '导入',
    export: '导出',
    IMPORT: '导入',
    EXPORT: '导出'
  }
  return type ? map[type] || type : '-'
}

function exportTypeLabel(value?: string | null) {
  const map: Record<string, string> = {
    STANDARD: '标准上报表',
    FULL_REVIEW: '完整审核表',
    CERT_SUMMARY: '证书获得者汇总表',
    ERROR: '异常数据表',
    ATTACHMENT_LIST: '附件与视频打包'
  }
  return value ? map[value] || value : '-'
}

function batchStatusLabel(value?: string | null) {
  const map: Record<string, string> = {
    EXPORTED: '导出完成',
    COMPLETED: '已完成',
    RUNNING: '运行中',
    FAILED: '失败'
  }
  return value ? map[value] || value : '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  if (!canRunExportType(exportType.value)) exportType.value = exportOptions.value[0]?.value as string || ''
  if (canExport.value) await loadBatches()
})

watch(
  () => yearStore.assessmentYear,
  (year) => {
    query.assessmentYear = year
  }
)
</script>

<template>
  <PageContainer title="导出中心" description="导出标准上报表、完整审核表、证书汇总、异常数据与附件打包。">
    <n-empty v-if="!canExport" description="当前账号没有可访问的导出分区" class="page-section" />

    <n-grid v-if="canExport" :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="导出批次" :value="summary.total" /></n-gi>
      <n-gi><StatCard label="已完成" :value="summary.exported" tone="success" /></n-gi>
      <n-gi><StatCard label="生成文件" :value="summary.files" tone="info" /></n-gi>
    </n-grid>

    <FilterBar v-if="canExport" :loading="exporting" submit-text="导出" @submit="runExport" @reset="resetQuery">
      <label class="filter-field">
        <span>导出类型</span>
        <n-select v-model:value="exportType" :options="exportOptions" placeholder="导出类型" style="width: 190px" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="query.assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="query.keyword" clearable placeholder="学号/姓名/证书编号" style="width: 230px" />
      </label>
      <label class="filter-field">
        <span>学院</span>
        <n-select v-model:value="query.collegeId" clearable filterable :options="collegeOptions" placeholder="全部学院" style="width: 220px" />
      </label>
      <template #more>
        <label class="filter-field">
          <span>专业代码</span>
          <n-input v-model:value="query.internalMajorCode" clearable placeholder="校内专业代码" style="width: 170px" />
        </label>
        <label class="filter-field">
          <span>班级</span>
          <n-input v-model:value="query.className" clearable placeholder="班级" style="width: 150px" />
        </label>
        <label class="filter-field">
          <span>培养目标</span>
          <n-select v-model:value="query.trainingGoal" clearable :options="goalOptions" placeholder="全部培养目标" style="width: 170px" />
        </label>
        <label class="filter-field">
          <span>任教学段</span>
          <n-select v-model:value="query.teachingSegment" clearable :options="segmentOptions" placeholder="全部学段" style="width: 160px" />
        </label>
        <label class="filter-field">
          <span>审核状态</span>
          <n-input v-model:value="query.auditStatus" clearable placeholder="审核状态" style="width: 150px" />
        </label>
        <label class="filter-field">
          <span>证书状态</span>
          <n-select v-model:value="query.certStatus" clearable :options="statusOptions" placeholder="全部证书状态" style="width: 170px" />
        </label>
      </template>
    </FilterBar>

    <DataPanel
      v-if="canExport"
      title="导出批次记录"
      :columns="batchColumns"
      :data="batches"
      :total="batches.length"
      :loading="loading"
      empty-title="暂无导出批次"
      empty-description="执行导出后会生成批次记录。"
      @refresh="loadBatches"
    />
  </PageContainer>
</template>
