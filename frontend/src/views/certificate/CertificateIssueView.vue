<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, NTag, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
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

const columns: DataTableColumns<Certificate> = [
  { title: '证书编号', key: 'certNo', width: 190, ellipsis: { tooltip: true } },
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '任教学科', key: 'teachingSubjectName', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '签发人', key: 'issuer', width: 110, render: (row) => row.issuer || '-' },
  { title: '签发日期', key: 'issueDate', width: 120, render: (row) => row.issueDate || '-' },
  { title: '有效期至', key: 'validUntil', width: 120, render: (row) => row.validUntil || '-' },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row) },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        canIssue.value && row.status === 'GENERATED'
          ? h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openIssue(row) }, { default: () => '签发' })
          : null,
        canMarkFlow.value && row.status === 'ISSUED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => markExported(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true }, { default: () => '已导出' }),
                default: () => '确认将该证书标记为已导出？'
              }
            )
          : null,
        canMarkFlow.value && row.status === 'EXPORTED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => archive(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'success' }, { default: () => '归档' }),
                default: () => '确认归档该证书？'
              }
            )
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
  } finally {
    saving.value = false
  }
}

async function markExported(row: Certificate) {
  await markCertificateExported(row.id)
  message.success('已标记导出')
  await loadRecords()
}

async function archive(row: Certificate) {
  await archiveCertificate(row.id)
  message.success('已归档')
  await loadRecords()
}

function statusTag(row: Certificate) {
  const type = row.status === 'GENERATED' ? 'warning' : row.status === 'ISSUED' ? 'success' : row.status === 'EXPORTED' ? 'info' : 'default'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel || row.status })
}

function todayText() {
  const now = new Date()
  return `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between" align="center">
      <n-space>
        <n-input v-model:value="keyword" clearable placeholder="证书编号/学号/姓名" style="width: 220px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="证书状态" style="width: 140px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
      <n-tag size="small" :bordered="false">签发后自动计算有效期</n-tag>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: Certificate) => row.id" :scroll-x="1180" />
  </n-space>

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
</template>
