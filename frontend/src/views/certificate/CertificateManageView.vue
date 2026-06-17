<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, NTag, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import {
  correctCertificate,
  generateCertificate,
  listCertificates,
  precheckCertificate,
  reissueCertificate,
  voidCertificate,
  type Certificate,
  type CertificateCorrectPayload,
  type CertificatePrecheck
} from '@/api/certificate'

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const generateVisible = ref(false)
const precheckVisible = ref(false)
const voidVisible = ref(false)
const correctVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref('2026')
const statusFilter = ref<string | null>(null)
const records = ref<Certificate[]>([])
const students = ref<Student[]>([])
const statuses = ref<DictItem[]>([])
const segments = ref<DictItem[]>([])
const goals = ref<DictItem[]>([])
const selected = ref<Certificate | null>(null)
const precheck = ref<CertificatePrecheck | null>(null)
const canGenerate = computed(() => userStore.hasPerm('cert:generate'))
const canCorrect = computed(() => userStore.hasPerm('cert:correct'))
const canVoid = computed(() => userStore.hasPerm('cert:void'))
const canReissue = computed(() => userStore.hasPerm('cert:reissue'))

const generateForm = reactive({
  studentId: '',
  assessmentYear: '2026'
})

const voidForm = reactive({
  reason: ''
})

const correctForm = reactive<CertificateCorrectPayload>({
  certNo: '',
  validUntil: '',
  teachingSegment: '',
  teachingSubjectCode: '',
  teachingSubjectName: '',
  trainingGoal: '',
  reason: ''
})

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const statusOptions = computed<SelectOption[]>(() => statuses.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const segmentOptions = computed<SelectOption[]>(() => segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))
const goalOptions = computed<SelectOption[]>(() => goals.value.map((item) => ({ label: item.itemValue, value: item.itemCode })))

const columns: DataTableColumns<Certificate> = [
  { title: '证书编号', key: 'certNo', width: 190, ellipsis: { tooltip: true }, render: (row) => row.certNo || '-' },
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '学段', key: 'teachingSegment', width: 120, render: (row) => dictLabel(segments.value, row.teachingSegment) },
  { title: '学科', key: 'teachingSubjectName', minWidth: 140, ellipsis: { tooltip: true } },
  { title: '有效期至', key: 'validUntil', width: 120, render: (row) => row.validUntil || '-' },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row) },
  {
    title: '操作',
    key: 'actions',
    width: 330,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        canGenerate.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openPrecheck(row) }, { default: () => '前置' })
          : null,
        canCorrect.value
          ? h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openCorrect(row) }, { default: () => '更正' })
          : null,
        canVoid.value && (row.status === 'GENERATED' || row.status === 'ISSUED')
          ? h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => openVoid(row) }, { default: () => '作废' })
          : null,
        canReissue.value && row.status === 'VOIDED'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => reissue(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'warning' }, { default: () => '重开' }),
                default: () => '重开会生成新证书并关联原编号，是否继续？'
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
  const [studentRes, statusRes, segmentRes, goalRes] = await Promise.all([
    listStudents(),
    listDictItems('certificate_status', true),
    listDictItems('teaching_segment', true),
    listDictItems('training_goal', true)
  ])
  students.value = studentRes.data.records
  statuses.value = statusRes.data
  segments.value = segmentRes.data
  goals.value = goalRes.data
}

function openGenerate() {
  generateForm.studentId = ''
  generateForm.assessmentYear = assessmentYear.value
  precheck.value = null
  generateVisible.value = true
}

async function runPrecheckForForm() {
  if (!generateForm.studentId || !generateForm.assessmentYear) {
    message.error('请选择学生并填写考核年度')
    return
  }
  const res = await precheckCertificate(generateForm.studentId, generateForm.assessmentYear)
  precheck.value = res.data
  if (res.data.passed) message.success('前置条件已满足')
}

async function generate() {
  if (!generateForm.studentId || !generateForm.assessmentYear) {
    message.error('请选择学生并填写考核年度')
    return
  }
  saving.value = true
  try {
    await generateCertificate({ studentId: generateForm.studentId, assessmentYear: generateForm.assessmentYear })
    message.success('证书编号已生成')
    generateVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

async function openPrecheck(row: Certificate) {
  const res = await precheckCertificate(row.studentId, row.assessmentYear)
  precheck.value = res.data
  precheckVisible.value = true
}

function openVoid(row: Certificate) {
  selected.value = row
  voidForm.reason = ''
  voidVisible.value = true
}

async function saveVoid() {
  if (!selected.value || !voidForm.reason.trim()) {
    message.error('请填写作废原因')
    return
  }
  saving.value = true
  try {
    await voidCertificate(selected.value.id, voidForm.reason)
    message.success('已作废')
    voidVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

async function reissue(row: Certificate) {
  await reissueCertificate(row.id)
  message.success('已重开新证书')
  await loadRecords()
}

function openCorrect(row: Certificate) {
  selected.value = row
  Object.assign(correctForm, {
    certNo: row.certNo || '',
    validUntil: row.validUntil || '',
    teachingSegment: row.teachingSegment || '',
    teachingSubjectCode: row.teachingSubjectCode || '',
    teachingSubjectName: row.teachingSubjectName || '',
    trainingGoal: row.trainingGoal || '',
    reason: ''
  })
  correctVisible.value = true
}

async function saveCorrect() {
  if (!selected.value || !correctForm.reason.trim()) {
    message.error('请填写更正原因')
    return
  }
  saving.value = true
  try {
    await correctCertificate(selected.value.id, correctForm)
    message.success('已更正')
    correctVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

function statusTag(row: Certificate) {
  const type = row.status === 'VOIDED' ? 'error' : row.status === 'ARCHIVED' ? 'default' : row.status === 'GENERATED' ? 'warning' : 'success'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel || row.status })
}

function dictLabel(items: DictItem[], code?: string | null) {
  if (!code) return '-'
  return items.find((item) => item.itemCode === code)?.itemValue || code
}

function missingText(items: string[]) {
  return items.length ? items.join('、') : '无'
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
      <n-button v-if="canGenerate" type="primary" @click="openGenerate">生成证书</n-button>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: Certificate) => row.id" :scroll-x="1280" />
  </n-space>

  <n-drawer v-model:show="generateVisible" :width="520">
    <n-drawer-content title="生成证书编号" closable>
      <n-space vertical>
        <n-select v-model:value="generateForm.studentId" filterable :options="studentOptions" placeholder="学生" />
        <n-input v-model:value="generateForm.assessmentYear" placeholder="考核年度" />
        <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :show-icon="false">
          {{ precheck.passed ? '前置条件已满足' : `缺失：${missingText(precheck.missingItems)}` }}
        </n-alert>
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="generateVisible = false">取消</n-button>
          <n-button @click="runPrecheckForForm">前置校验</n-button>
          <n-button type="primary" :loading="saving" @click="generate">生成</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="precheckVisible" preset="dialog" title="证书前置校验">
    <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :show-icon="false">
      {{ precheck.passed ? '前置条件已满足' : `缺失：${missingText(precheck.missingItems)}` }}
    </n-alert>
  </n-modal>

  <n-modal v-model:show="voidVisible" preset="card" title="作废证书" style="width: 520px">
    <n-space vertical>
      <n-input v-model:value="voidForm.reason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="作废原因" />
      <n-space justify="end">
        <n-button @click="voidVisible = false">取消</n-button>
        <n-button type="error" :loading="saving" @click="saveVoid">作废</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-drawer v-model:show="correctVisible" :width="560">
    <n-drawer-content title="证书更正" closable>
      <n-space vertical>
        <n-input v-model:value="correctForm.certNo" placeholder="18位证书编号" />
        <n-input v-model:value="correctForm.validUntil" placeholder="有效期至，如 2029/6/30" />
        <n-select v-model:value="correctForm.teachingSegment" clearable :options="segmentOptions" placeholder="任教学段" />
        <n-input v-model:value="correctForm.teachingSubjectCode" placeholder="任教学科代码" />
        <n-input v-model:value="correctForm.teachingSubjectName" placeholder="任教学科名称" />
        <n-select v-model:value="correctForm.trainingGoal" clearable :options="goalOptions" placeholder="培养目标" />
        <n-input v-model:value="correctForm.reason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="更正原因" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="correctVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveCorrect">保存更正</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>
