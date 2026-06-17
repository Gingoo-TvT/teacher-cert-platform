<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import { listDictItems, type DictItem } from '@/api/dict'
import { exportExchange, exportExchangeAttachments, listExchangeBatches, saveBlob, type ExchangeBatch, type ExchangeQuery } from '@/api/exchange'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()
const exporting = ref(false)
const loading = ref(false)
const batches = ref<ExchangeBatch[]>([])
const statuses = ref<DictItem[]>([])
const segments = ref<DictItem[]>([])
const goals = ref<DictItem[]>([])

const query = reactive<ExchangeQuery>({
  keyword: '',
  assessmentYear: '2026',
  collegeId: '',
  internalMajorCode: '',
  className: '',
  trainingGoal: '',
  teachingSegment: '',
  auditStatus: '',
  certStatus: ''
})

const canFull = computed(() => userStore.hasPerm('exchange:export:full'))

const exportOptions = computed<SelectOption[]>(() => [
  { label: '标准上报表', value: 'STANDARD' },
  ...(canFull.value
    ? [
        { label: '完整审核表', value: 'FULL_REVIEW' },
        { label: '证书获得者汇总表', value: 'CERT_SUMMARY' },
        { label: '异常数据表', value: 'ERROR' }
      ]
    : []),
  { label: '附件清单表', value: 'ATTACHMENT_LIST' }
])
const exportType = ref('STANDARD')
const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const goalOptions = computed<SelectOption[]>(() => goals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))

const batchColumns: DataTableColumns<ExchangeBatch> = [
  { title: '批次号', key: 'batchNo', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 90 },
  { title: '导出项', key: 'strategy', width: 150, render: (row) => row.strategy || '-' },
  { title: '时间', key: 'operateTime', width: 170, render: (row) => row.operateTime || '-' },
  { title: '数量', key: 'successCount', width: 90 },
  { title: '状态', key: 'status', width: 120, render: (row) => h(NTag, { size: 'small', type: 'success', bordered: false }, { default: () => row.status }) },
  { title: '文件', key: 'fileName', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.fileName || '-' }
]

async function loadOptions() {
  const [statusRes, segmentRes, goalRes] = await Promise.all([
    listDictItems('certificate_status', true),
    listDictItems('teaching_segment', true),
    listDictItems('training_goal', true)
  ])
  statuses.value = statusRes.data
  segments.value = segmentRes.data
  goals.value = goalRes.data
}

async function loadBatches() {
  loading.value = true
  try {
    const res = await listExchangeBatches('export')
    batches.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function runExport() {
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
    assessmentYear: '2026',
    collegeId: '',
    internalMajorCode: '',
    className: '',
    trainingGoal: '',
    teachingSegment: '',
    auditStatus: '',
    certStatus: ''
  })
}

onMounted(async () => {
  await loadOptions()
  await loadBatches()
})
</script>

<template>
  <n-space vertical size="large">
    <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen">
      <n-gi>
        <n-select v-model:value="exportType" :options="exportOptions" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.assessmentYear" placeholder="考核年度" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.keyword" clearable placeholder="学号/姓名/证书编号" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.collegeId" clearable placeholder="学院ID" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.internalMajorCode" clearable placeholder="校内专业代码" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.className" clearable placeholder="班级" />
      </n-gi>
      <n-gi>
        <n-select v-model:value="query.trainingGoal" clearable :options="goalOptions" placeholder="培养目标" />
      </n-gi>
      <n-gi>
        <n-select v-model:value="query.teachingSegment" clearable :options="segmentOptions" placeholder="任教学段" />
      </n-gi>
      <n-gi>
        <n-input v-model:value="query.auditStatus" clearable placeholder="审核状态" />
      </n-gi>
      <n-gi>
        <n-select v-model:value="query.certStatus" clearable :options="statusOptions" placeholder="证书状态" />
      </n-gi>
      <n-gi>
        <n-space>
          <n-button type="primary" :loading="exporting" @click="runExport">导出</n-button>
          <n-button @click="resetQuery">重置</n-button>
        </n-space>
      </n-gi>
    </n-grid>

    <n-data-table :columns="batchColumns" :data="batches" :loading="loading" :row-key="(row: ExchangeBatch) => row.id" :scroll-x="960" />
  </n-space>
</template>
