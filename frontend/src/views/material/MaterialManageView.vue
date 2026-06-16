<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import {
  batchDownloadMaterials,
  deleteMaterial,
  firstReviewMaterial,
  getProcessStatus,
  listMaterials,
  previewMaterial,
  replaceMaterial,
  secondReviewMaterial,
  submitMaterial,
  uploadMaterial,
  type ProcessMaterial
} from '@/api/material'
import type { ReviewPayload } from '@/api/student'

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const uploadVisible = ref(false)
const reviewVisible = ref(false)
const statusVisible = ref(false)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const categoryFilter = ref<string | null>(null)
const assessmentYear = ref('2026')
const records = ref<ProcessMaterial[]>([])
const students = ref<Student[]>([])
const categories = ref<DictItem[]>([])
const fileList = ref<UploadFileInfo[]>([])
const replacing = ref<ProcessMaterial | null>(null)
const reviewing = ref<{ material: ProcessMaterial; stage: 'first' | 'second' } | null>(null)
const processQualified = ref(false)
const canUpload = computed(() => userStore.hasPerm('material:upload'))
const canFirstReview = computed(() => userStore.hasPerm('material:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('material:secondReview'))
const canBatchDownload = computed(() => userStore.hasPerm('material:batchDownload'))
const selfMode = computed(() => canUpload.value && !canFirstReview.value && !canSecondReview.value)

interface StatusRow {
  label: string
  passed: boolean
  total: number
  passedCount: number
  failedCount: number
}

const statusRows = ref<StatusRow[]>([])

const uploadForm = reactive({
  studentId: '',
  assessmentYear: '2026',
  category: ''
})

const reviewForm = reactive<ReviewPayload>({
  action: 'PASS',
  comment: ''
})

const statusOptions: SelectOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待初审', value: 'FIRST_REVIEW' },
  { label: '初审退回', value: 'FIRST_REJECTED' },
  { label: '待复审', value: 'SECOND_REVIEW' },
  { label: '复审退回', value: 'SECOND_REJECTED' },
  { label: '复审通过', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' }
]

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const categoryOptions = computed<SelectOption[]>(() =>
  categories.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)

const columns: DataTableColumns<ProcessMaterial> = [
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '类别', key: 'categoryLabel', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '文件名', key: 'fileName', minWidth: 210, ellipsis: { tooltip: true } },
  { title: '大小', key: 'fileSize', width: 90, render: (row) => formatSize(row.fileSize || 0) },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row) },
  {
    title: '操作',
    key: 'actions',
    width: 360,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => preview(row) }, { default: () => '预览' }),
        canUpload.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReplace(row) }, { default: () => '替换' })
          : null,
        canUpload.value ? h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }) : null,
        canFirstReview.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' })
          : null,
        canSecondReview.value
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' })
          : null,
        canUpload.value
          ? h(
              NPopconfirm,
              { onPositiveClick: () => remove(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
                default: () => '确认删除该材料？'
              }
            )
          : null
      ])
  }
]

const statusColumns: DataTableColumns<StatusRow> = [
  { title: '材料类别', key: 'label' },
  { title: '总数', key: 'total', width: 80 },
  { title: '通过数', key: 'passedCount', width: 90 },
  { title: '不通过数', key: 'failedCount', width: 100 },
  { title: '类别结果', key: 'passed', width: 100, render: (row) => (row.passed ? '通过' : '未通过') }
]

async function loadRecords() {
  loading.value = true
  try {
    const res = await listMaterials({
      keyword: keyword.value,
      status: statusFilter.value,
      category: categoryFilter.value,
      assessmentYear: assessmentYear.value
    })
    records.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, categoryRes] = await Promise.all([listStudents(), listDictItems('material_category', true)])
  students.value = selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records
  categories.value = categoryRes.data
}

function openUpload() {
  replacing.value = null
  uploadForm.studentId = selfMode.value ? userStore.currentUser?.studentId || students.value[0]?.id || '' : ''
  uploadForm.assessmentYear = assessmentYear.value
  uploadForm.category = ''
  fileList.value = []
  uploadVisible.value = true
}

function openReplace(row: ProcessMaterial) {
  replacing.value = row
  uploadForm.studentId = row.studentId
  uploadForm.assessmentYear = row.assessmentYear
  uploadForm.category = row.category
  fileList.value = []
  uploadVisible.value = true
}

async function saveUpload() {
  const file = fileList.value[0]?.file
  if (!uploadForm.studentId || !uploadForm.assessmentYear || !uploadForm.category || !file) {
    message.error('请选择学生、年度、类别和附件')
    return
  }
  saving.value = true
  try {
    if (replacing.value) await replaceMaterial(replacing.value.id, file)
    else await uploadMaterial({ ...uploadForm, file })
    message.success('已保存')
    uploadVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

async function preview(row: ProcessMaterial) {
  const res = await previewMaterial(row.id)
  window.open(res.data, '_blank', 'noopener,noreferrer')
}

async function submit(row: ProcessMaterial) {
  await submitMaterial(row.id)
  message.success('已提交')
  await loadRecords()
}

async function remove(row: ProcessMaterial) {
  await deleteMaterial(row.id)
  message.success('已删除')
  await loadRecords()
}

function openReview(row: ProcessMaterial, stage: 'first' | 'second') {
  reviewing.value = { material: row, stage }
  reviewForm.action = 'PASS'
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function saveReview() {
  if (!reviewing.value) return
  if (reviewForm.action !== 'PASS' && !reviewForm.comment?.trim()) {
    message.error('退回或不通过必须填写原因')
    return
  }
  if (reviewing.value.stage === 'first') await firstReviewMaterial(reviewing.value.material.id, reviewForm)
  else await secondReviewMaterial(reviewing.value.material.id, reviewForm)
  message.success('审核完成')
  reviewVisible.value = false
  await loadRecords()
}

async function showProcessStatus() {
  const studentId = uploadForm.studentId || records.value[0]?.studentId
  if (!studentId) {
    message.error('请选择或查询到一个学生')
    return
  }
  const res = await getProcessStatus(studentId, assessmentYear.value)
  processQualified.value = res.data.qualified
  statusRows.value = res.data.categories.map((item) => ({
    label: item.categoryLabel,
    passed: item.passed,
    total: item.totalCount,
    passedCount: item.passedCount,
    failedCount: item.failedCount
  }))
  statusVisible.value = true
}

async function batchDownload() {
  const res = await batchDownloadMaterials({
    keyword: keyword.value,
    status: statusFilter.value,
    category: categoryFilter.value,
    assessmentYear: assessmentYear.value
  })
  const url = URL.createObjectURL(res.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `process-material-${Date.now()}.zip`
  link.click()
  URL.revokeObjectURL(url)
}

function statusTag(row: ProcessMaterial) {
  const type = row.status === 'PASSED' ? 'success' : row.status === 'FAILED' ? 'error' : row.status.includes('REJECTED') ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel })
}

function formatSize(size: number) {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size} B`
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
        <n-input v-model:value="keyword" clearable placeholder="文件名/类别" style="width: 210px" @keyup.enter="loadRecords" />
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        <n-select v-model:value="categoryFilter" clearable :options="categoryOptions" placeholder="类别" style="width: 190px" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 145px" />
        <n-button type="primary" @click="loadRecords">查询</n-button>
      </n-space>
      <n-space>
        <n-button @click="showProcessStatus">合格判定</n-button>
        <n-button v-if="canBatchDownload" @click="batchDownload">批量下载</n-button>
        <n-button v-if="canUpload" type="primary" @click="openUpload">上传材料</n-button>
      </n-space>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: ProcessMaterial) => row.id" :scroll-x="1280" />
  </n-space>

  <n-drawer v-model:show="uploadVisible" :width="520">
    <n-drawer-content :title="replacing ? '替换材料' : '上传材料'" closable>
      <n-space vertical>
        <n-select v-model:value="uploadForm.studentId" :options="studentOptions" :disabled="Boolean(replacing) || selfMode" placeholder="学生" />
        <n-input v-model:value="uploadForm.assessmentYear" :disabled="Boolean(replacing)" placeholder="考核年度" />
        <n-select v-model:value="uploadForm.category" :options="categoryOptions" :disabled="Boolean(replacing)" placeholder="材料类别" />
        <n-upload v-model:file-list="fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="uploadVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveUpload">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="reviewVisible" preset="dialog" :title="reviewing?.stage === 'first' ? '初审' : '复审'">
    <n-space vertical>
      <n-select
        v-model:value="reviewForm.action"
        :options="[
          { label: '通过', value: 'PASS' },
          { label: '退回', value: 'REJECT' },
          { label: '不通过', value: 'FAIL' }
        ]"
      />
      <n-input v-model:value="reviewForm.comment" type="textarea" placeholder="退回或不通过必须填写原因" />
      <n-space justify="end">
        <n-button @click="reviewVisible = false">取消</n-button>
        <n-button type="primary" @click="saveReview">确认</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="statusVisible" preset="card" title="过程性考核合格判定" style="width: 620px">
    <n-space vertical>
      <n-alert :type="processQualified ? 'success' : 'warning'">
        {{ processQualified ? '四类材料均已复审通过' : '仍有材料类别缺失或未通过' }}
      </n-alert>
      <n-data-table
        :columns="statusColumns"
        :data="statusRows"
        :pagination="false"
      />
    </n-space>
  </n-modal>
</template>
