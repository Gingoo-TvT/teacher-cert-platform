<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import CertificateSelfPanel from './components/CertificateSelfPanel.vue'
import CertificateGenerateDrawer from './components/CertificateGenerateDrawer.vue'
import CertificateIssueDrawer from './components/CertificateIssueDrawer.vue'
import CertificateVoidDrawer from './components/CertificateVoidDrawer.vue'
import CertificateCorrectDrawer from './components/CertificateCorrectDrawer.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatDate } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  archiveCertificate,
  listCertificates,
  markCertificateExported,
  reissueCertificate,
  type Certificate
} from '@/api/certificate'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const records = ref<Certificate[]>([])
const statuses = ref<DictItem[]>([])
const segments = ref<DictItem[]>([])
const goals = ref<DictItem[]>([])

const generateDrawer = ref<InstanceType<typeof CertificateGenerateDrawer> | null>(null)
const issueDrawer = ref<InstanceType<typeof CertificateIssueDrawer> | null>(null)
const voidDrawer = ref<InstanceType<typeof CertificateVoidDrawer> | null>(null)
const correctDrawer = ref<InstanceType<typeof CertificateCorrectDrawer> | null>(null)

const canGenerate = computed(() => userStore.hasPerm('cert:generate'))
const canCorrect = computed(() => userStore.hasPerm('cert:correct'))
const canVoid = computed(() => userStore.hasPerm('cert:void'))
const canReissue = computed(() => userStore.hasPerm('cert:reissue'))
const canIssue = computed(() => userStore.hasPerm('cert:issue'))
const canView = computed(() => userStore.hasPerm('cert:view'))
const canOpenGenerate = computed(() => canGenerate.value)
const hasVisibleSection = computed(() => canView.value || canOpenGenerate.value)
const isStudentMode = computed(() => userStore.roles.includes('STUDENT') && canView.value)
const pageTitle = computed(() => isStudentMode.value ? '我的证书' : '证书管理')
const pageDescription = computed(() => isStudentMode.value ? '查看本人证书编号、有效期与当前状态。' : '证书生成、签发、导出、归档、更正、作废与重开。')

const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const goalOptions = computed<SelectOption[]>(() => goals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const summary = computed(() => {
  const generated = records.value.filter((item) => item.status === 'GENERATED').length
  const issued = records.value.filter((item) => item.status === 'ISSUED').length
  const exported = records.value.filter((item) => item.status === 'EXPORTED').length
  const archived = records.value.filter((item) => item.status === 'ARCHIVED').length
  return { total: records.value.length, generated, issued, exported, archived }
})

const columns: DataTableColumns<Certificate> = [
  { title: '证书编号', key: 'certNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.certNo || '-') },
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '学段', key: 'teachingSegment', minWidth: 120, render: (row) => dictLabel(segments.value, row.teachingSegment) },
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
      {
        const actions = []
        if (canGenerate.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openPrecheck(row) }, { default: () => '前置' }))
        }
        if (canIssue.value && row.status === 'GENERATED') {
          actions.push(h(NButton, { size: 'small', type: 'primary', onClick: () => openIssue(row) }, { default: () => '签发' }))
        }
        if (canView.value && row.status === 'ISSUED') {
          actions.push(confirmButton('已导出', '确认将该证书标记为已导出？', () => markExported(row)))
        }
        if (canView.value && row.status === 'EXPORTED') {
          actions.push(confirmButton('归档', '确认归档该证书？', () => archive(row), 'success'))
        }
        if (canCorrect.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openCorrect(row) }, { default: () => '更正' }))
        }
        if (canVoid.value && (row.status === 'GENERATED' || row.status === 'ISSUED')) {
          actions.push(h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => openVoid(row) }, { default: () => '作废' }))
        }
        if (canReissue.value && row.status === 'VOIDED') {
          actions.push(confirmButton('重开', '重开会生成新证书并关联原编号，是否继续？', () => reissue(row), 'warning'))
        }
        return renderTableActions(actions)
      }
  }
]

async function loadRecords() {
  if (!canView.value) {
    records.value = []
    return
  }
  loading.value = true
  try {
    const res = await listCertificates({
      keyword: keyword.value,
      assessmentYear: assessmentYear.value,
      status: statusFilter.value
    })
    records.value = res.data.records
  } catch (error) {
    showError(error, '证书列表加载失败')
  } finally {
    loading.value = false
  }
}

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

function openGenerate() {
  generateDrawer.value?.open()
}

function openPrecheck(row: Certificate) {
  generateDrawer.value?.openPrecheck(row)
}

function openIssue(row: Certificate) {
  issueDrawer.value?.open(row)
}

async function markExported(row: Certificate) {
  try {
    await markCertificateExported(row.id)
    message.success('已标记导出')
    await loadRecords()
  } catch (error) {
    showError(error, '标记导出失败')
  }
}

async function archive(row: Certificate) {
  try {
    await archiveCertificate(row.id)
    message.success('已归档')
    await loadRecords()
  } catch (error) {
    showError(error, '归档失败')
  }
}

function openVoid(row: Certificate) {
  voidDrawer.value?.open(row)
}

async function reissue(row: Certificate) {
  try {
    await reissueCertificate(row.id)
    message.success('已重开新证书')
    await loadRecords()
  } catch (error) {
    showError(error, '重开失败')
  }
}

function openCorrect(row: Certificate) {
  correctDrawer.value?.open(row)
}

function confirmButton(label: string, text: string, onPositiveClick: () => void, type: 'default' | 'success' | 'warning' = 'default') {
  return h(
    NPopconfirm,
    { onPositiveClick },
    {
      trigger: () => h(NButton, { size: 'small', quaternary: true, type: type === 'default' ? undefined : type }, { default: () => label }),
      default: () => text
    }
  )
}

function dictLabel(items: DictItem[], code?: string | null) {
  if (!code) return '-'
  return items.find((item) => item.itemCode === code)?.itemValue || code
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = null
  void loadRecords()
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  if (canView.value) await loadRecords()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    if (canView.value) await loadRecords()
  }
)
</script>

<template>
  <PageContainer :title="pageTitle" :description="pageDescription">
    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的证书分区" class="page-section" />

    <CertificateSelfPanel v-if="isStudentMode" :records="records" :loading="loading" />

    <n-grid v-if="canView && !isStudentMode" :cols="5" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="证书总数" :value="summary.total" /></n-gi>
      <n-gi><StatCard label="待签发" :value="summary.generated" tone="warning" /></n-gi>
      <n-gi><StatCard label="已签发" :value="summary.issued" tone="success" /></n-gi>
      <n-gi><StatCard label="已导出" :value="summary.exported" tone="info" /></n-gi>
      <n-gi><StatCard label="已归档" :value="summary.archived" tone="neutral" /></n-gi>
    </n-grid>

    <FilterBar v-if="canView && !isStudentMode" :loading="loading" @submit="loadRecords" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="证书编号 / 学号 / 姓名" style="width: 240px" @keyup.enter="loadRecords" />
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
      v-if="canView && !isStudentMode"
      title="证书列表"
      :columns="columns"
      :data="records"
      :total="records.length"
      :loading="loading"
      empty-title="暂无证书"
      empty-description="当前筛选条件下没有证书记录。"
      @refresh="loadRecords"
    >
      <template #actions>
        <n-button v-if="canOpenGenerate" type="primary" size="small" @click="openGenerate">生成证书</n-button>
      </template>
      <template v-if="canOpenGenerate" #emptyAction>
        <n-button type="primary" @click="openGenerate">生成证书</n-button>
      </template>
    </DataPanel>

    <CertificateGenerateDrawer
      ref="generateDrawer"
      :assessment-year="assessmentYear"
      @saved="loadRecords"
    />
    <CertificateIssueDrawer ref="issueDrawer" @saved="loadRecords" />
    <CertificateVoidDrawer ref="voidDrawer" @saved="loadRecords" />
    <CertificateCorrectDrawer
      ref="correctDrawer"
      :segment-options="segmentOptions"
      :goal-options="goalOptions"
      @saved="loadRecords"
    />
  </PageContainer>
</template>
