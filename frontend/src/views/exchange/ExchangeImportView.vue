<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
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
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const uploading = ref(false)
const confirming = ref(false)
const activeStep = ref(1)
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

const currentFile = computed(() => fileList.value[0]?.file ?? null)
const hasErrors = computed(() => Boolean(prevalidate.value && prevalidate.value.failCount > 0))
const canConfirm = computed(() => Boolean(prevalidate.value && prevalidate.value.successCount > 0))
const canDownloadTemplate = computed(() => userStore.hasPerm('exchange:template'))
const canPrevalidate = computed(() => userStore.hasPerm('exchange:prevalidate'))
const canImport = computed(() => userStore.hasPerm('exchange:import'))
const canViewBatches = computed(() => canImport.value)

const batchColumns: DataTableColumns<ExchangeBatch> = [
  { title: '批次号', key: 'batchNo', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.batchNo) },
  { title: '类型', key: 'type', width: 90 },
  { title: '时间', key: 'operateTime', width: 170, render: (row) => row.operateTime || '-' },
  { title: '总数', key: 'total', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.total ?? 0)) },
  { title: '成功', key: 'successCount', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.successCount ?? 0)) },
  { title: '失败', key: 'failCount', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.failCount ?? 0)) },
  { title: '策略', key: 'strategy', width: 130, render: (row) => row.strategy || '-' },
  { title: '状态', key: 'status', width: 120, render: (row) => h(StatusTag, { text: row.status }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 210,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        canPrevalidate.value ? h(NButton, { size: 'small', quaternary: true, onClick: () => downloadError(row.id) }, { default: () => '异常报告' }) : null,
        canImport.value && (row.status === 'IMPORTED' || row.status === 'FAILED')
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
  { title: '行号', key: 'rowNo', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.rowNo)) },
  { title: '学号', key: 'studentNo', minWidth: 130, render: (row) => h('span', { class: 'mono' }, row.row.studentNo || '-') },
  { title: '姓名', key: 'name', width: 120, render: (row) => row.row.name || '-' },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.row.idCardNo || '-') },
  { title: '证书编号', key: 'certNo', minWidth: 190, render: (row) => h('span', { class: 'mono' }, row.row.certNo || '-') }
]

const errorColumns: DataTableColumns<ImportError> = [
  { title: '行号', key: 'rowNo', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.rowNo)) },
  { title: '学号', key: 'studentNo', minWidth: 120, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', width: 120, render: (row) => row.studentName || '-' },
  { title: '字段', key: 'fieldName', minWidth: 140 },
  { title: '错误值', key: 'errorValue', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.errorValue || '-' },
  { title: '原因', key: 'errorReason', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '建议', key: 'suggestion', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => row.suggestion || '-' }
]

async function loadBatches() {
  if (!canViewBatches.value) {
    batches.value = []
    return
  }
  loading.value = true
  try {
    const res = await listExchangeBatches('import')
    batches.value = res.data.records
  } catch (error) {
    showError(error, '批次列表加载失败')
  } finally {
    loading.value = false
  }
}

async function downloadTpl() {
  if (!canDownloadTemplate.value) return
  try {
    const blob = await downloadTemplate({ assessmentYear: yearStore.assessmentYear })
    saveBlob(blob, '教育部标准导入模板.xlsx')
    activeStep.value = Math.max(activeStep.value, 2)
  } catch (error) {
    showError(error, '模板下载失败')
  }
}

async function runPrevalidate() {
  if (!canPrevalidate.value) return
  if (!currentFile.value) {
    message.error('请选择Excel文件')
    return
  }
  uploading.value = true
  try {
    const res = await prevalidateExchange(currentFile.value)
    prevalidate.value = res.data
    activeStep.value = res.data.failCount > 0 ? 3 : 4
    message.success(`预校验完成：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadBatches()
  } catch (error) {
    showError(error, '预校验失败')
  } finally {
    uploading.value = false
  }
}

async function confirmImport() {
  if (!canImport.value) return
  if (!prevalidate.value) {
    message.error('请先完成预校验')
    return
  }
  confirming.value = true
  try {
    const res = await confirmExchangeImport(prevalidate.value.batchId, strategy.value)
    message.success(`导入完成：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadBatches()
  } catch (error) {
    showError(error, '确认导入失败')
  } finally {
    confirming.value = false
  }
}

async function downloadCurrentError() {
  if (!canPrevalidate.value) return
  if (!prevalidate.value) return
  await downloadError(prevalidate.value.batchId)
}

async function downloadError(batchId: string) {
  if (!canPrevalidate.value) return
  try {
    const blob = await downloadErrorReport(batchId)
    saveBlob(blob, `${batchId}-异常报告.xlsx`)
  } catch (error) {
    showError(error, '异常报告下载失败')
  }
}

async function rollback(batchId: string) {
  if (!canImport.value) return
  try {
    const res = await rollbackExchangeImport(batchId)
    message.success(`回滚 ${res.data.rolledBackCount} 条，冲突 ${res.data.conflictCount} 条`)
    await loadBatches()
  } catch (error) {
    showError(error, '回滚失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(() => {
  if (canViewBatches.value) void loadBatches()
})
</script>

<template>
  <PageContainer title="导入中心" description="按模板下载、上传预校验、V-01~V-13 异常定位、策略确认导入与批次回滚的四步流程。">
    <template #actions>
      <n-button v-if="canViewBatches" secondary @click="loadBatches">刷新批次</n-button>
    </template>

    <n-steps v-model:current="activeStep" class="page-section">
      <n-step title="模板" description="下载标准 26 列模板" />
      <n-step title="上传" description="上传 Excel 并预校验" />
      <n-step title="异常" description="查看 V-01~V-13 错误表" />
      <n-step title="导入" description="选择策略确认入库" />
    </n-steps>

    <n-grid :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="总行数" :value="prevalidate?.total ?? 0" /></n-gi>
      <n-gi><StatCard label="预校验通过" :value="prevalidate?.successCount ?? 0" tone="success" /></n-gi>
      <n-gi><StatCard label="异常数" :value="prevalidate?.failCount ?? 0" tone="error" /></n-gi>
    </n-grid>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-button v-if="canDownloadTemplate" type="primary" @click="downloadTpl">模板下载</n-button>
        <n-upload v-model:file-list="fileList" :max="1" accept=".xlsx" :default-upload="false">
          <n-button>选择 Excel</n-button>
        </n-upload>
        <n-button v-if="canPrevalidate" type="primary" :loading="uploading" @click="runPrevalidate">预校验</n-button>
        <n-select v-model:value="strategy" :options="strategyOptions" style="width: 170px" />
        <n-button v-if="canImport" :disabled="!canConfirm" :loading="confirming" @click="confirmImport">确认导入</n-button>
        <n-button v-if="canPrevalidate" :disabled="!hasErrors" @click="downloadCurrentError">异常报告</n-button>
      </n-space>
    </n-card>

    <n-tabs type="line" animated>
      <n-tab-pane name="preview" tab="成功预览">
        <n-data-table
          :columns="previewColumns"
          :data="prevalidate?.previewRows ?? []"
          :row-key="(row: ImportPreviewRow) => row.rowNo"
          :scroll-x="860"
          :pagination="{ pageSize: 8 }"
          striped
        />
      </n-tab-pane>
      <n-tab-pane name="errors" tab="异常明细">
        <n-data-table
          :columns="errorColumns"
          :data="prevalidate?.errors ?? []"
          :row-key="(row: ImportError) => `${row.rowNo}-${row.fieldName}`"
          :scroll-x="1120"
          :pagination="{ pageSize: 8 }"
          striped
        />
      </n-tab-pane>
      <n-tab-pane v-if="canViewBatches" name="batches" tab="批次记录">
        <n-data-table
          :columns="batchColumns"
          :data="batches"
          :loading="loading"
          :row-key="(row: ExchangeBatch) => row.id"
          :scroll-x="1180"
          :pagination="{ pageSize: 10 }"
          striped
        />
      </n-tab-pane>
    </n-tabs>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}
</style>
