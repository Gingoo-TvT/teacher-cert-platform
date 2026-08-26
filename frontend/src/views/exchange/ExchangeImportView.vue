<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { renderTableActions } from '@/utils/tableActions'
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
import { formatDateTime } from '@/utils/format'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const batchLoadError = ref('')
const hasLoadedBatchesSuccessfully = ref(false)
const uploading = ref(false)
const confirming = ref(false)
const rollingBackBatchId = ref<string | null>(null)
const prevalidateError = ref('')
const activeStep = ref(1)
const fileList = ref<UploadFileInfo[]>([])
const prevalidate = ref<PrevalidateResult | null>(null)
const strategy = ref('INSERT_ONLY')
const batches = ref<ExchangeBatch[]>([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchSize = ref(20)
const compactViewport = ref(false)
let compactViewportQuery: MediaQueryList | null = null
let batchRequestSequence = 0

const strategyOptions: SelectOption[] = [
  { label: '新增', value: 'INSERT_ONLY' },
  { label: '覆盖', value: 'OVERWRITE' },
  { label: '跳过重复', value: 'SKIP_DUPLICATE' },
  { label: '仅更新空字段', value: 'UPDATE_EMPTY' }
]

const currentFile = computed(() => fileList.value[0]?.file ?? null)
const hasErrors = computed(() => Boolean(prevalidate.value && prevalidate.value.failCount > 0))
const canConfirm = computed(() => Boolean(prevalidate.value && prevalidate.value.successCount > 0 && !prevalidateError.value))
const canDownloadTemplate = computed(() => userStore.hasPerm('exchange:template'))
const canPrevalidate = computed(() => userStore.hasPerm('exchange:prevalidate'))
const canImport = computed(() => userStore.hasPerm('exchange:import'))
const canViewBatches = computed(() => canImport.value)
const operationBusy = computed(() => uploading.value || confirming.value || Boolean(rollingBackBatchId.value))

const batchColumns: DataTableColumns<ExchangeBatch> = [
  { title: '批次号', key: 'batchNo', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.batchNo) },
  { title: '类型', key: 'type', width: 90, render: (row) => batchTypeLabel(row.type) },
  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
  { title: '总数', key: 'total', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.total ?? 0)) },
  { title: '成功', key: 'successCount', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.successCount ?? 0)) },
  { title: '失败', key: 'failCount', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.failCount ?? 0)) },
  { title: '策略', key: 'strategy', width: 130, render: (row) => strategyLabel(row.strategy) },
  { title: '状态', key: 'status', width: 120, render: (row) => h(StatusTag, { value: row.status, text: batchStatusLabel(row.status) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 210,
    render: (row) =>
      renderTableActions([
        canPrevalidate.value ? h(NButton, { size: 'small', quaternary: true, onClick: () => downloadError(row.id) }, { default: () => '异常报告' }) : null,
        canImport.value && (row.status === 'IMPORTED' || row.status === 'FAILED')
          ? h(
              NPopconfirm,
              { onPositiveClick: () => rollback(row.id) },
              {
                trigger: () => h(NButton, {
                  size: 'small',
                  quaternary: true,
                  type: 'warning',
                  disabled: operationBusy.value,
                  loading: rollingBackBatchId.value === row.id
                }, { default: () => '回滚' }),
                default: () => '回滚会按导入追溯恢复数据，已被后续修改的记录会跳过。'
              }
            )
          : null
      ])
  }
]

const previewColumns: DataTableColumns<ImportPreviewRow> = [
  { title: '行号', key: 'rowNo', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.rowNo)) },
  { title: '学号', key: 'studentNo', minWidth: 130, render: (row) => h('span', { class: 'mono' }, row.row.studentNo || '-') },
  { title: '姓名', key: 'name', width: 120, render: (row) => row.row.name || '-' },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.row.idCardNo || '-') },
  { title: '证书编号', key: 'certNo', minWidth: 190, render: (row) => h('span', { class: 'mono' }, row.row.certNo || '-') }
]

const errorColumns: DataTableColumns<ImportError> = [
  { title: '行号', key: 'rowNo', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.rowNo)) },
  { title: '学号', key: 'studentNo', minWidth: 120, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', width: 120, render: (row) => row.studentName || '-' },
  { title: '字段', key: 'fieldName', minWidth: 140 },
  { title: '错误值', key: 'errorValue', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => row.errorValue || '-' },
  { title: '原因', key: 'errorReason', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '建议', key: 'suggestion', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => row.suggestion || '-' }
]

async function loadBatches() {
  const requestSequence = ++batchRequestSequence
  if (!canViewBatches.value) {
    batches.value = []
    batchTotal.value = 0
    batchLoadError.value = ''
    hasLoadedBatchesSuccessfully.value = false
    loading.value = false
    return
  }
  const requestedPage = batchPage.value
  const requestedSize = batchSize.value
  loading.value = true
  batchLoadError.value = ''
  try {
    const res = await listExchangeBatches('import', null, requestedPage, requestedSize)
    if (requestSequence !== batchRequestSequence) return
    batches.value = res.data.records
    batchTotal.value = res.data.total
    hasLoadedBatchesSuccessfully.value = true
  } catch (error) {
    if (requestSequence !== batchRequestSequence) return
    batchLoadError.value = showError(error, '批次列表加载失败')
  } finally {
    if (requestSequence === batchRequestSequence) loading.value = false
  }
}

function onBatchPageChange(next: number) {
  batchPage.value = next
  void loadBatches()
}

function onBatchPageSizeChange(nextSize: number) {
  batchSize.value = nextSize
  batchPage.value = 1
  void loadBatches()
}

async function downloadTpl() {
  if (!canDownloadTemplate.value || operationBusy.value) return
  try {
    const blob = await downloadTemplate({ assessmentYear: yearStore.assessmentYear })
    saveBlob(blob, '教育部标准导入模板.xlsx')
    activeStep.value = Math.max(activeStep.value, 2)
  } catch (error) {
    showError(error, '模板下载失败')
  }
}

async function runPrevalidate() {
  if (!canPrevalidate.value || operationBusy.value) return
  const file = currentFile.value
  if (!file) {
    message.error('请选择Excel文件')
    return
  }
  uploading.value = true
  prevalidateError.value = ''
  try {
    const res = await prevalidateExchange(file)
    prevalidate.value = res.data
    activeStep.value = res.data.failCount > 0 ? 3 : 4
    message.success(`预校验完成：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadBatches()
  } catch (error) {
    prevalidateError.value = showError(error, '预校验失败')
  } finally {
    uploading.value = false
  }
}

async function confirmImport() {
  if (!canImport.value || operationBusy.value) return
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
  if (!canImport.value || operationBusy.value) return
  rollingBackBatchId.value = batchId
  try {
    const res = await rollbackExchangeImport(batchId)
    message.success(`回滚 ${res.data.rolledBackCount} 条，冲突 ${res.data.conflictCount} 条`)
    await loadBatches()
  } catch (error) {
    showError(error, '回滚失败')
  } finally {
    rollingBackBatchId.value = null
  }
}

function resetImport() {
  if (operationBusy.value) return
  fileList.value = []
  prevalidate.value = null
  prevalidateError.value = ''
  strategy.value = 'INSERT_ONLY'
  activeStep.value = 1
}

function onFileListUpdate(next: UploadFileInfo[]) {
  if (operationBusy.value) return
  fileList.value = next
  prevalidate.value = null
  prevalidateError.value = ''
  activeStep.value = next.length ? 2 : 1
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

function strategyLabel(value?: string | null) {
  const map: Record<string, string> = {
    INSERT_ONLY: '新增',
    OVERWRITE: '覆盖',
    SKIP_DUPLICATE: '跳过重复',
    UPDATE_EMPTY: '仅更新空字段',
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
    PREVALIDATED: '预校验通过',
    IMPORTING: '导入中',
    IMPORTED: '导入成功',
    EXPORTED: '导出完成',
    ROLLED_BACK: '已回滚',
    FAILED: '失败',
    COMPLETED: '已完成',
    RUNNING: '运行中'
  }
  return value ? map[value] || value : '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  const text = detail || fallback
  message.error(text)
  return text
}

function syncCompactViewport(event?: MediaQueryListEvent) {
  compactViewport.value = event?.matches ?? compactViewportQuery?.matches ?? false
}

onMounted(() => {
  compactViewportQuery = window.matchMedia('(max-width: 720px)')
  syncCompactViewport()
  compactViewportQuery.addEventListener('change', syncCompactViewport)
  if (canViewBatches.value) void loadBatches()
})

onBeforeUnmount(() => {
  compactViewportQuery?.removeEventListener('change', syncCompactViewport)
})
</script>

<template>
  <PageContainer title="导入中心" description="模板下载、上传预校验、异常定位、确认导入与批次回滚。">
    <template #actions>
      <n-button v-if="canViewBatches" secondary @click="loadBatches">刷新批次</n-button>
    </template>

    <n-steps
      v-model:current="activeStep"
      :vertical="compactViewport"
      :size="compactViewport ? 'small' : 'medium'"
      class="page-section import-steps"
    >
      <n-step title="模板" description="下载标准 26 列模板" />
      <n-step title="上传" description="上传 Excel 并预校验" />
      <n-step title="异常" description="查看校验错误明细" />
      <n-step title="导入" description="选择策略确认入库" />
    </n-steps>

    <n-grid v-if="prevalidate" cols="1 440:2 760:3" responsive="self" :x-gap="12" :y-gap="12" class="page-section import-stats">
      <n-gi><StatCard label="总行数" :value="prevalidate?.total ?? 0" /></n-gi>
      <n-gi><StatCard label="预校验通过" :value="prevalidate?.successCount ?? 0" tone="success" /></n-gi>
      <n-gi><StatCard label="异常数" :value="prevalidate?.failCount ?? 0" tone="error" /></n-gi>
    </n-grid>

    <n-alert
      v-if="prevalidateError"
      type="error"
      :title="prevalidate ? '预校验重试失败，保留上次结果' : '预校验失败'"
      :bordered="false"
      class="page-section"
      role="alert"
    >
      <div class="operation-feedback">
        <span>{{ prevalidateError }}</span>
        <n-button size="small" type="error" secondary :loading="uploading" :disabled="operationBusy || !currentFile" @click="runPrevalidate">
          重试预校验
        </n-button>
      </div>
    </n-alert>

    <FilterBar class="import-workbench" :loading="operationBusy" submit-text="预校验" reset-text="清空" @submit="runPrevalidate" @reset="resetImport">
      <label class="filter-field">
        <span>模板</span>
        <n-button v-if="canDownloadTemplate" type="primary" :disabled="operationBusy" @click="downloadTpl">模板下载</n-button>
      </label>
      <label class="filter-field">
        <span>文件</span>
        <n-upload :file-list="fileList" :max="1" accept=".xlsx" :default-upload="false" :disabled="operationBusy" @update:file-list="onFileListUpdate">
          <n-upload-dragger class="compact-upload">
            <n-text>点击或拖拽 Excel 到此处上传</n-text>
            <n-p depth="3">支持 XLSX 文件，最多 1 个文件。</n-p>
          </n-upload-dragger>
        </n-upload>
      </label>
      <label class="filter-field">
        <span>策略</span>
        <n-select v-model:value="strategy" :options="strategyOptions" :disabled="operationBusy" style="width: 170px" />
      </label>
      <label class="filter-field">
        <span>导入</span>
        <n-button v-if="canImport" :disabled="!canConfirm || operationBusy" :loading="confirming" @click="confirmImport">确认导入</n-button>
      </label>
      <label class="filter-field">
        <span>异常</span>
        <n-button v-if="canPrevalidate" :disabled="!hasErrors || operationBusy" @click="downloadCurrentError">异常报告</n-button>
      </label>
    </FilterBar>

    <n-tabs type="line" animated>
      <n-tab-pane name="preview" tab="成功预览">
        <DataPanel
          title="成功预览"
          :columns="previewColumns"
          :data="prevalidate?.previewRows ?? []"
          :total="prevalidate?.previewRows.length ?? 0"
          :page-size="8"
          :show-refresh="false"
          empty-title="暂无成功预览"
          empty-description="上传文件并完成预校验后显示通过行。"
        />
      </n-tab-pane>
      <n-tab-pane name="errors" tab="异常明细">
        <DataPanel
          title="异常明细"
          :columns="errorColumns"
          :data="prevalidate?.errors ?? []"
          :total="prevalidate?.errors.length ?? 0"
          :page-size="8"
          :show-refresh="false"
          empty-title="暂无异常"
          empty-description="当前预校验结果没有异常明细。"
        />
      </n-tab-pane>
      <n-tab-pane v-if="canViewBatches" name="batches" tab="批次记录">
        <DataPanel
          title="导入批次记录"
          :columns="batchColumns"
          :data="batches"
          :total="batchTotal"
          :loading="loading"
          :initial-loading="loading && !hasLoadedBatchesSuccessfully"
          :error="batchLoadError"
          error-title="批次记录加载失败"
          remote
          :page="batchPage"
          :page-size="batchSize"
          empty-title="暂无导入批次"
          empty-description="完成预校验或导入后会生成批次记录。"
          @update:page="onBatchPageChange"
          @update:page-size="onBatchPageSizeChange"
          @refresh="loadBatches"
        />
      </n-tab-pane>
    </n-tabs>
  </PageContainer>
</template>

<style scoped>
.compact-upload {
  width: min(360px, 100%);
}

.operation-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .import-steps {
    width: 100%;
  }

  .import-workbench :deep(.filter-bar__actions) {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .import-workbench :deep(.filter-bar__actions .n-button),
  .import-workbench :deep(.filter-field > .n-button),
  .import-workbench :deep(.filter-field > .n-upload),
  .compact-upload {
    width: 100%;
  }

  .operation-feedback {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
