<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { renderTableActions } from '@/utils/tableActions'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  archiveCertificate,
  correctCertificate,
  generateCertificate,
  issueCertificate,
  listCertificates,
  markCertificateExported,
  precheckCertificate,
  reissueCertificate,
  voidCertificate,
  type Certificate,
  type CertificateCorrectPayload,
  type CertificateIssuePayload,
  type CertificatePrecheck
} from '@/api/certificate'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const generateVisible = ref(false)
const precheckVisible = ref(false)
const voidVisible = ref(false)
const correctVisible = ref(false)
const issueVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
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
const canIssue = computed(() => userStore.hasPerm('cert:issue'))
const canView = computed(() => userStore.hasPerm('cert:view'))
const canOpenGenerate = computed(() => canGenerate.value)
const hasVisibleSection = computed(() => canView.value || canOpenGenerate.value)

const generateForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear
})

const voidForm = reactive({
  reason: ''
})

const issueForm = reactive<CertificateIssuePayload>({
  issuer: userStore.realName || '',
  issueDate: todayText()
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
  { title: '签发日期', key: 'issueDate', width: 120, render: (row) => row.issueDate || '-' },
  { title: '有效期至', key: 'validUntil', width: 120, render: (row) => h('span', { class: 'mono' }, row.validUntil || '-') },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { text: row.statusLabel || row.status }) },
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
  const [studentRes, statusRes, segmentRes, goalRes] = await Promise.all([
    canGenerate.value ? listStudents() : Promise.resolve(null),
    listDictItems('certificate_status', true),
    listDictItems('teaching_segment', true),
    listDictItems('training_goal', true)
  ])
  students.value = studentRes?.data.records || []
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
  try {
    const res = await precheckCertificate(generateForm.studentId, generateForm.assessmentYear)
    precheck.value = res.data
    if (res.data.passed) message.success('前置条件已满足')
  } catch (error) {
    showError(error, '前置校验失败')
  }
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
  } catch (error) {
    showError(error, '证书生成失败')
  } finally {
    saving.value = false
  }
}

async function openPrecheck(row: Certificate) {
  try {
    const res = await precheckCertificate(row.studentId, row.assessmentYear)
    precheck.value = res.data
    precheckVisible.value = true
  } catch (error) {
    showError(error, '前置校验失败')
  }
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
  } catch (error) {
    showError(error, '作废失败')
  } finally {
    saving.value = false
  }
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
  } catch (error) {
    showError(error, '更正失败')
  } finally {
    saving.value = false
  }
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

function missingText(items: string[]) {
  return items.length ? items.join('、') : '无'
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
  if (canView.value) await loadRecords()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    if (!generateVisible.value) generateForm.assessmentYear = year
    if (canView.value) await loadRecords()
  }
)
</script>

<template>
  <PageContainer title="证书管理" description="证书生成、签发、导出、归档、更正、作废与重开。">
    <template #actions>
      <n-space>
        <n-button v-if="canView" secondary @click="loadRecords">刷新</n-button>
        <n-button v-if="canOpenGenerate" type="primary" @click="openGenerate">生成证书</n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的证书分区" class="page-section" />

    <n-grid v-if="canView" :cols="5" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="证书总数" :value="summary.total" /></n-gi>
      <n-gi><StatCard label="待签发" :value="summary.generated" tone="warning" /></n-gi>
      <n-gi><StatCard label="已签发" :value="summary.issued" tone="success" /></n-gi>
      <n-gi><StatCard label="已导出" :value="summary.exported" tone="info" /></n-gi>
      <n-gi><StatCard label="已归档" :value="summary.archived" tone="neutral" /></n-gi>
    </n-grid>

    <n-card v-if="canView" :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-input v-model:value="keyword" clearable placeholder="证书编号 / 学号 / 姓名" style="width: 240px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="证书状态" style="width: 150px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
    </n-card>

    <n-data-table
      v-if="canView"
      :columns="columns"
      :data="records"
      :loading="loading"
      :row-key="(row: Certificate) => row.id"
      :scroll-x="1750"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-drawer v-model:show="generateVisible" :width="560">
      <n-drawer-content title="生成证书编号" closable>
        <n-space vertical>
          <n-alert type="info" :bordered="false">
            生成前会聚合基本信息、材料、测试、视频等前置条件；缺项会阻断生成。
          </n-alert>
          <n-select v-model:value="generateForm.studentId" filterable :options="studentOptions" placeholder="学生" />
          <n-input v-model:value="generateForm.assessmentYear" placeholder="考核年度" class="mono-input" />
          <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :bordered="false">
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
      <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :bordered="false">
        {{ precheck.passed ? '前置条件已满足' : `缺失：${missingText(precheck.missingItems)}` }}
      </n-alert>
    </n-modal>

    <n-modal v-model:show="issueVisible" preset="card" title="签发证书" style="width: 520px">
      <n-space vertical>
        <n-alert type="info" :bordered="false">签发权限已并入教务处/全校管理员，不依赖独立签发角色。</n-alert>
        <n-input v-model:value="issueForm.issuer" placeholder="签发人" />
        <n-input v-model:value="issueForm.issueDate" placeholder="签发日期，如 2026/6/30" class="mono-input" />
        <n-space justify="end">
          <n-button @click="issueVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveIssue">签发</n-button>
        </n-space>
      </n-space>
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

    <n-drawer v-model:show="correctVisible" :width="580">
      <n-drawer-content title="证书更正" closable>
        <n-space vertical>
          <n-input v-model:value="correctForm.certNo" placeholder="18位证书编号" class="mono-input" />
          <n-input v-model:value="correctForm.validUntil" placeholder="有效期至，如 2029/6/30" class="mono-input" />
          <n-select v-model:value="correctForm.teachingSegment" clearable :options="segmentOptions" placeholder="任教学段" />
          <n-input v-model:value="correctForm.teachingSubjectCode" placeholder="任教学科代码" class="mono-input" />
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
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
