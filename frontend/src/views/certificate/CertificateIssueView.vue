<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NPopconfirm, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatDate } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  archiveCertificate,
  issueCertificate,
  listCertificates,
  markCertificateExported,
  type Certificate,
  type CertificateIssuePayload
} from '@/api/certificate'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const loadError = ref('')
const hasLoadedSuccessfully = ref(false)
const loadedQueryKey = ref('')
const saving = ref(false)
const issueVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>('GENERATED')
const records = ref<Certificate[]>([])
const certTotal = ref(0)
const page = ref(1)
const size = ref(20)
const statuses = ref<DictItem[]>([])
const issuers = ref<DictItem[]>([])
const issuerLoading = ref(false)
const issuerError = ref('')
const selected = ref<Certificate | null>(null)
const canIssue = computed(() => userStore.hasPerm('cert:issue'))
const canMarkFlow = computed(() => userStore.hasPerm('cert:view'))
const canViewQueue = computed(() => userStore.hasPerm('cert:view'))
let listRequestSequence = 0

const listQueryKey = computed(() => JSON.stringify([
  keyword.value,
  assessmentYear.value,
  statusFilter.value || '',
  page.value,
  size.value
]))
const listDataFresh = computed(() =>
  hasLoadedSuccessfully.value
  && loadedQueryKey.value === listQueryKey.value
  && !loading.value
  && !loadError.value
)
const writeBlocked = computed(() => !listDataFresh.value)

const issueForm = reactive<CertificateIssuePayload>({
  issuer: userStore.realName || '',
  issueDate: todayText()
})

const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const issuerOptions = computed<SelectOption[]>(() =>
  issuers.value.map((item) => ({ label: item.itemValue, value: item.itemValue }))
)
const issuerReady = computed(() =>
  !issuerLoading.value && !issuerError.value && issuerOptions.value.length > 0
)
// Phase 44e-rollout（P1-1 真分页铺开）：records 真分页后仅为当页数据，下列 waiting/issued/exported
// 仅代表当页状态分布，不再是全表统计（与 CertificateManageView.vue 的既有局限一致，暂不新增全量聚合查询）。
const summary = computed(() => ({
  waiting: records.value.filter((item) => item.status === 'GENERATED').length,
  issued: records.value.filter((item) => item.status === 'ISSUED').length,
  exported: records.value.filter((item) => item.status === 'EXPORTED').length
}))

const columns: DataTableColumns<Certificate> = [
  { title: '证书编号', key: 'certNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.certNo || '-') },
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '任教学科', key: 'teachingSubjectName', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '签发人', key: 'issuer', width: 110, render: (row) => row.issuer || '-' },
  { title: '签发日期', key: 'issueDate', width: 120, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDate(row.issueDate)) },
  { title: '有效期至', key: 'validUntil', width: 120, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDate(row.validUntil)) },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 260,
    render: (row) =>
      renderTableActions([
        canIssue.value && row.status === 'GENERATED'
          ? h(NButton, { size: 'small', type: 'primary', disabled: writeBlocked.value, onClick: () => openIssue(row) }, { default: () => '签发' })
          : null,
        canMarkFlow.value && row.status === 'ISSUED'
          ? confirmButton('已导出', '确认将该证书标记为已导出？', () => markExported(row))
          : null,
        canMarkFlow.value && row.status === 'EXPORTED'
          ? confirmButton('归档', '确认归档该证书？', () => archive(row), 'success')
          : null
      ])
  }
]

async function loadRecords() {
  const requestSequence = ++listRequestSequence
  if (!canViewQueue.value) {
    records.value = []
    certTotal.value = 0
    loadError.value = ''
    hasLoadedSuccessfully.value = false
    loadedQueryKey.value = ''
    loading.value = false
    return
  }
  const queryKey = listQueryKey.value
  const query = {
    keyword: keyword.value,
    assessmentYear: assessmentYear.value,
    status: statusFilter.value,
    page: page.value,
    size: size.value
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await listCertificates(query)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    records.value = res.data.records
    certTotal.value = res.data.total
    hasLoadedSuccessfully.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = showError(error, '签发队列加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// 筛选变更（关键词/年度/状态）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
function search() {
  page.value = 1
  void loadRecords()
}

function onPageChange(next: number) {
  page.value = next
  void loadRecords()
}

function onPageSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadRecords()
}

async function loadOptions() {
  try {
    const statusRes = await listDictItems('certificate_status', true)
    statuses.value = statusRes.data
  } catch (error) {
    showError(error, '证书状态选项加载失败')
  }
}

async function openIssue(row: Certificate) {
  if (writeBlocked.value) return
  selected.value = row
  issueForm.issuer = ''
  issueForm.issueDate = todayText()
  issueVisible.value = true
  await loadIssuers()
  const values = new Set(issuerOptions.value.map((option) => String(option.value)))
  issueForm.issuer = [row.issuer, userStore.realName].find((value) => value && values.has(value)) || ''
}

async function saveIssue() {
  if (saving.value || writeBlocked.value) return
  if (!issuerReady.value) {
    message.error(issuerError.value || '请先在字典管理中维护启用的证书签发人')
    return
  }
  if (!selected.value || !issueForm.issuer.trim() || !issueForm.issueDate.trim()) {
    message.error('请填写签发人和签发日期')
    return
  }
  saving.value = true
  try {
    await issueCertificate(selected.value.id, issueForm)
    message.success('已签发')
    issueVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '签发失败')
  } finally {
    saving.value = false
  }
}

async function loadIssuers() {
  issuerLoading.value = true
  issuerError.value = ''
  try {
    const response = await listDictItems('cert_issuer', true)
    issuers.value = response.data.filter((item) => item.yearVersion === 'GLOBAL')
    if (!issuers.value.length) {
      issuerError.value = '暂无启用的证书签发人，请先在字典管理中维护'
    }
  } catch (error) {
    issuers.value = []
    issuerError.value = errorText(error, '证书签发人字典加载失败')
  } finally {
    issuerLoading.value = false
  }
}

async function markExported(row: Certificate) {
  if (writeBlocked.value) return
  try {
    await markCertificateExported(row.id)
    message.success('已标记导出')
    await loadRecords()
  } catch (error) {
    showError(error, '标记导出失败')
  }
}

async function archive(row: Certificate) {
  if (writeBlocked.value) return
  try {
    await archiveCertificate(row.id)
    message.success('已归档')
    await loadRecords()
  } catch (error) {
    showError(error, '归档失败')
  }
}

function confirmButton(label: string, text: string, onPositiveClick: () => void, type: 'default' | 'success' = 'default') {
  return h(
    NPopconfirm,
    { onPositiveClick },
    {
      trigger: () => h(NButton, { size: 'small', quaternary: true, type: type === 'default' ? undefined : type, disabled: writeBlocked.value }, { default: () => label }),
      default: () => text
    }
  )
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = 'GENERATED'
  search()
}

function todayText() {
  const now = new Date()
  return `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
}

function showError(error: unknown, fallback: string) {
  const text = errorText(error, fallback)
  message.error(text)
  return text
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

onMounted(() => {
  void Promise.all([loadOptions(), canViewQueue.value ? loadRecords() : Promise.resolve()])
})

watch(
  () => yearStore.assessmentYear,
  (year) => {
    assessmentYear.value = year
    if (canViewQueue.value) search()
  }
)
</script>

<template>
  <PageContainer title="证书签发队列" description="待签发证书队列。">
    <n-empty v-if="!canViewQueue" description="当前账号没有证书队列查看权限" class="page-section" />

    <n-grid v-if="canViewQueue && hasLoadedSuccessfully" cols="1 440:2 720:3" :x-gap="12" :y-gap="12" responsive="self" class="page-section">
      <n-gi><StatCard label="待签发" :value="summary.waiting" tone="warning" /></n-gi>
      <n-gi><StatCard label="已签发" :value="summary.issued" tone="success" /></n-gi>
      <n-gi><StatCard label="已导出待归档" :value="summary.exported" tone="info" /></n-gi>
    </n-grid>

    <FilterBar v-if="canViewQueue" :loading="loading" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="证书编号 / 学号 / 姓名" style="width: 240px" @keyup.enter="search" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
    </FilterBar>

    <DataPanel
      v-if="canViewQueue"
      title="签发队列"
      :columns="columns"
      :data="records"
      :total="certTotal"
      :loading="loading"
      :error="loadError"
      remote
      :page="page"
      :page-size="size"
      empty-title="暂无待签发证书"
      empty-description="当前筛选条件下没有证书签发记录。"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
      @refresh="loadRecords"
    />

    <n-modal
      v-model:show="issueVisible"
      preset="card"
      title="签发证书"
      style="width: min(var(--overlay-medium), var(--overlay-modal-max))"
      :closable="!saving"
      :close-on-esc="!saving"
      :mask-closable="!saving"
    >
      <n-space vertical>
        <n-alert v-if="issuerError" type="warning">{{ issuerError }}</n-alert>
        <n-select
          v-model:value="issueForm.issuer"
          :options="issuerOptions"
          :loading="issuerLoading"
          :disabled="saving || !issuerReady"
          placeholder="请选择签发人"
        />
        <n-input v-model:value="issueForm.issueDate" placeholder="签发日期，如 2026/6/30" :disabled="saving" />
        <n-space justify="end">
          <n-button :disabled="saving" @click="issueVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="writeBlocked || !issuerReady" @click="saveIssue">签发</n-button>
        </n-space>
      </n-space>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
</style>
