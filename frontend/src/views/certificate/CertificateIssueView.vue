<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
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
const loading = ref(false)
const saving = ref(false)
const issueVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref('2026')
const statusFilter = ref<string | null>('GENERATED')
const records = ref<Certificate[]>([])
const statuses = ref<DictItem[]>([])
const selected = ref<Certificate | null>(null)
const canIssue = computed(() => userStore.hasPerm('cert:issue'))
const canMarkFlow = computed(() => userStore.hasPerm('cert:view'))

const issueForm = reactive<CertificateIssuePayload>({
  issuer: userStore.realName || '',
  issueDate: todayText()
})

const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
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
  { title: '签发日期', key: 'issueDate', width: 120, render: (row) => row.issueDate || '-' },
  { title: '有效期至', key: 'validUntil', width: 120, render: (row) => h('span', { class: 'mono' }, row.validUntil || '-') },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { text: row.statusLabel || row.status }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 260,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        canIssue.value && row.status === 'GENERATED'
          ? h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openIssue(row) }, { default: () => '签发' })
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
  loading.value = true
  try {
    const res = await listCertificates({
      keyword: keyword.value,
      assessmentYear: assessmentYear.value,
      status: statusFilter.value
    })
    records.value = res.data.records
  } catch (error) {
    showError(error, '签发队列加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const statusRes = await listDictItems('certificate_status', true)
  statuses.value = statusRes.data
}

function openIssue(row: Certificate) {
  selected.value = row
  issueForm.issuer = userStore.realName || row.issuer || ''
  issueForm.issueDate = todayText()
  issueVisible.value = true
}

async function saveIssue() {
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

function confirmButton(label: string, text: string, onPositiveClick: () => void, type: 'default' | 'success' = 'default') {
  return h(
    NPopconfirm,
    { onPositiveClick },
    {
      trigger: () => h(NButton, { size: 'small', quaternary: true, type: type === 'default' ? undefined : type }, { default: () => label }),
      default: () => text
    }
  )
}

function todayText() {
  const now = new Date()
  return `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})
</script>

<template>
  <PageContainer title="证书签发队列" description="签发权限已并入教务处/全校管理员；本页作为证书管理的签发队列视图。">
    <template #actions>
      <n-button secondary @click="loadRecords">刷新</n-button>
    </template>

    <n-grid :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="待签发" :value="summary.waiting" color="#f0a020" /></n-gi>
      <n-gi><StatCard label="已签发" :value="summary.issued" color="#18a058" /></n-gi>
      <n-gi><StatCard label="已导出待归档" :value="summary.exported" color="#2080f0" /></n-gi>
    </n-grid>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-input v-model:value="keyword" clearable placeholder="证书编号 / 学号 / 姓名" style="width: 240px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="证书状态" style="width: 150px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
    </n-card>

    <n-data-table
      :columns="columns"
      :data="records"
      :loading="loading"
      :row-key="(row: Certificate) => row.id"
      :scroll-x="1320"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-modal v-model:show="issueVisible" preset="card" title="签发证书" style="width: 520px">
      <n-space vertical>
        <n-input v-model:value="issueForm.issuer" placeholder="签发人" />
        <n-input v-model:value="issueForm.issueDate" placeholder="签发日期，如 2026/6/30" />
        <n-space justify="end">
          <n-button @click="issueVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveIssue">签发</n-button>
        </n-space>
      </n-space>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}
</style>
