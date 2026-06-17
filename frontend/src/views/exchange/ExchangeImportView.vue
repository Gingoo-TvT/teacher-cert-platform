<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, NTag, useMessage, type DataTableColumns, type SelectOption, type UploadFileInfo } from 'naive-ui'
import {
  confirmExchangeImport,
  downloadErrorReport,
  downloadTemplate,
  listExchangeBatches,
  prevalidateExchange,
  rollbackExchangeImport,
  saveBlob,
  type ExchangeBatch,
  type ImportError,
  type ImportPreviewRow,
  type PrevalidateResult
} from '@/api/exchange'

const message = useMessage()
const loading = ref(false)
const uploading = ref(false)
const confirming = ref(false)
const fileList = ref<UploadFileInfo[]>([])
const prevalidate = ref<PrevalidateResult | null>(null)
const strategy = ref('INSERT_ONLY')
const batches = ref<ExchangeBatch[]>([])

const strategyOptions: SelectOption[] = [
  { label: '新增', value: 'INSERT_ONLY' },
  { label: '覆盖', value: 'OVERWRITE' },
  { label: '跳过重复', value: 'SKIP_DUPLICATE' },
  { label: '仅更新空字段', value: 'UPDATE_EMPTY' }
]

const batchColumns: DataTableColumns<ExchangeBatch> = [
  { title: '批次号', key: 'batchNo', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 90 },
  { title: '时间', key: 'operateTime', width: 170, render: (row) => row.operateTime || '-' },
  { title: '总数', key: 'total', width: 80 },
  { title: '成功', key: 'successCount', width: 80 },
  { title: '失败', key: 'failCount', width: 80 },
  { title: '状态', key: 'status', width: 120, render: (row) => statusTag(row.status) },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, onClick: () => downloadError(row.id) }, { default: () => '异常报告' }),
        row.status === 'IMPORTED' || row.status === 'FAILED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => rollback(row.id) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'warning' }, { default: () => '回滚' }),
                default: () => '回滚会按导入追溯恢复数据，已被后续修改的记录会跳过。'
              }
            )
          : null
      ])
  }
]

const previewColumns: DataTableColumns<ImportPreviewRow> = [
  { title: '行号', key: 'rowNo', width: 80 },
  { title: '学号', key: 'studentNo', minWidth: 130, render: (row) => row.row.studentNo || '-' },
  { title: '姓名', key: 'name', width: 120, render: (row) => row.row.name || '-' },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => row.row.idCardNo || '-' },
  { title: '证书编号', key: 'certNo', minWidth: 190, render: (row) => row.row.certNo || '-' }
]

const errorColumns: DataTableColumns<ImportError> = [
  { title: '行号', key: 'rowNo', width: 80 },
  { title: '学号', key: 'studentNo', minWidth: 120, render: (row) => row.studentNo || '-' },
  { title: '姓名', key: 'studentName', width: 120, render: (row) => row.studentName || '-' },
  { title: '字段', key: 'fieldName', minWidth: 140 },
  { title: '错误值', key: 'errorValue', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.errorValue || '-' },
  { title: '原因', key: 'errorReason', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '建议', key: 'suggestion', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => row.suggestion || '-' }
]

const currentFile = computed(() => fileList.value[0]?.file ?? null)

async function loadBatches() {
  loading.value = true
  try {
    const res = await listExchangeBatches('import')
    batches.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function downloadTpl() {
  const blob = await downloadTemplate()
  saveBlob(blob, '教育部标准导入模板.xlsx')
}

async function runPrevalidate() {
  if (!currentFile.value) {
    message.error('请选择Excel文件')
    return
  }
  uploading.value = true
  try {
    const res = await prevalidateExchange(currentFile.value)
    prevalidate.value = res.data
    message.success(`预校验完成：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadBatches()
  } finally {
    uploading.value = false
  }
}

async function confirmImport() {
  if (!prevalidate.value) {
    message.error('请先完成预校验')
    return
  }
  confirming.value = true
  try {
    const res = await confirmExchangeImport(prevalidate.value.batchId, strategy.value)
    message.success(`导入完成：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadBatches()
  } finally {
    confirming.value = false
  }
}

async function downloadCurrentError() {
  if (!prevalidate.value) return
  await downloadError(prevalidate.value.batchId)
}

async function downloadError(batchId: string) {
  const blob = await downloadErrorReport(batchId)
  saveBlob(blob, `${batchId}-异常报告.xlsx`)
}

async function rollback(batchId: string) {
  const res = await rollbackExchangeImport(batchId)
  message.success(`回滚 ${res.data.rolledBackCount} 条，冲突 ${res.data.conflictCount} 条`)
  await loadBatches()
}

function statusTag(status: string) {
  const type = status === 'IMPORTED' || status === 'ROLLED_BACK' || status === 'EXPORTED' ? 'success' : status === 'FAILED' || status === 'PARTIAL_ROLLBACK' ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => status })
}

onMounted(loadBatches)
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between" align="center">
      <n-space>
        <n-upload v-model:file-list="fileList" :max="1" accept=".xlsx" :default-upload="false">
          <n-button>选择Excel</n-button>
        </n-upload>
        <n-button type="primary" :loading="uploading" @click="runPrevalidate">预校验</n-button>
        <n-select v-model:value="strategy" :options="strategyOptions" style="width: 150px" />
        <n-button :disabled="!prevalidate || prevalidate.successCount === 0" :loading="confirming" @click="confirmImport">确认导入</n-button>
        <n-button :disabled="!prevalidate || prevalidate.failCount === 0" @click="downloadCurrentError">异常报告</n-button>
      </n-space>
      <n-button @click="downloadTpl">模板下载</n-button>
    </n-space>

    <n-grid :cols="3" :x-gap="12">
      <n-gi>
        <n-statistic label="总行数" :value="prevalidate?.total ?? 0" />
      </n-gi>
      <n-gi>
        <n-statistic label="预校验通过" :value="prevalidate?.successCount ?? 0" />
      </n-gi>
      <n-gi>
        <n-statistic label="错误数" :value="prevalidate?.failCount ?? 0" />
      </n-gi>
    </n-grid>

    <n-tabs type="line" animated>
      <n-tab-pane name="preview" tab="成功预览">
        <n-data-table :columns="previewColumns" :data="prevalidate?.previewRows ?? []" :row-key="(row: ImportPreviewRow) => row.rowNo" :scroll-x="760" />
      </n-tab-pane>
      <n-tab-pane name="errors" tab="异常明细">
        <n-data-table :columns="errorColumns" :data="prevalidate?.errors ?? []" :row-key="(row: ImportError) => `${row.rowNo}-${row.fieldName}`" :scroll-x="1120" />
      </n-tab-pane>
      <n-tab-pane name="batches" tab="批次记录">
        <n-data-table :columns="batchColumns" :data="batches" :loading="loading" :row-key="(row: ExchangeBatch) => row.id" :scroll-x="1040" />
      </n-tab-pane>
    </n-tabs>
  </n-space>
</template>
