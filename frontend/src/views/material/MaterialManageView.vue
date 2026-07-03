<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch, type Component } from 'vue'
import {
  NButton,
  NIcon,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import { AlertCircleOutline, CheckmarkCircleOutline, CloudUploadOutline, DocumentTextOutline, EyeOutline } from '@vicons/ionicons5'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatFileSize } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
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

interface StatusRow {
  label: string
  passed: boolean
  total: number
  passedCount: number
  failedCount: number
}

interface MaterialCard {
  code: string
  label: string
  icon: Component
  record: ProcessMaterial | null
}

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const reviewSaving = ref(false)
const uploadVisible = ref(false)
const reviewVisible = ref(false)
const statusVisible = ref(false)
const previewVisible = ref(false)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const categoryFilter = ref<string | null>(null)
const assessmentYear = ref(yearStore.assessmentYear)
const records = ref<ProcessMaterial[]>([])
const students = ref<Student[]>([])
const categories = ref<DictItem[]>([])
const fileList = ref<UploadFileInfo[]>([])
const replacing = ref<ProcessMaterial | null>(null)
const reviewing = ref<{ material: ProcessMaterial; stage: 'first' | 'second' } | null>(null)
const processQualified = ref(false)
const statusRows = ref<StatusRow[]>([])
const previewRow = ref<ProcessMaterial | null>(null)
const previewUrl = ref('')

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  category: ''
})

const canUpload = computed(() => userStore.hasPerm('material:upload'))
const canFirstReview = computed(() => userStore.hasPerm('material:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('material:secondReview'))
const canBatchDownload = computed(() => userStore.hasPerm('material:batchDownload'))
const canViewStudents = computed(() => userStore.hasPerm('student:view'))
const selfMode = computed(() => canUpload.value && !canFirstReview.value && !canSecondReview.value)

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
const statusSummary = computed(() => {
  const total = records.value.length
  const passed = records.value.filter((item) => item.status === 'PASSED').length
  const pending = records.value.filter((item) => ['FIRST_REVIEW', 'SECOND_REVIEW'].includes(item.status)).length
  const rejected = records.value.filter((item) => item.status.includes('REJECTED') || item.status === 'FAILED').length
  return { total, passed, pending, rejected }
})
const materialCards = computed<MaterialCard[]>(() => {
  const icons = [DocumentTextOutline, CloudUploadOutline, CheckmarkCircleOutline, AlertCircleOutline]
  const source = categories.value.length
    ? categories.value
    : records.value.map((item) => ({ itemCode: item.category, itemValue: item.categoryLabel }) as DictItem)
  return source.slice(0, 4).map((item, index) => ({
    code: item.itemCode,
    label: item.itemValue,
    icon: icons[index] || DocumentTextOutline,
    record: latestMaterial(item.itemCode)
  }))
})
const previewable = computed(() => {
  const row = previewRow.value
  if (!row) return false
  const name = row.fileName.toLowerCase()
  const type = row.contentType || ''
  return type.includes('pdf') || type.includes('image') || /\.(pdf|jpg|jpeg|png)$/.test(name)
})

const columns: DataTableColumns<ProcessMaterial> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '材料类别', key: 'categoryLabel', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => row.categoryLabel || dictLabel(categories.value, row.category) },
  {
    title: '文件',
    key: 'fileName',
    minWidth: 260,
    ellipsis: { tooltip: true },
    render: (row) =>
      h('div', { class: 'file-cell' }, [
        h('span', { class: 'file-name' }, row.fileName || '-'),
        h('span', { class: 'file-size' }, formatFileSize(row.fileSize))
      ])
  },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  { title: '锁定', key: 'locked', width: 78, render: (row) => h(StatusTag, { text: row.locked ? '已锁定' : '未锁定' }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 240,
    render: (row) =>
      {
        const actions = [
          h(
            NButton,
            { size: 'small', type: 'primary', onClick: () => openPreview(row) },
            { icon: () => h(NIcon, { component: EyeOutline }), default: () => '预览' }
          )
        ]
        if (canUpload.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReplace(row) }, { default: () => '替换' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        if (canUpload.value) {
          actions.push(
            h(
              NPopconfirm,
              { onPositiveClick: () => remove(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
                default: () => '确认删除该材料？'
              }
            )
          )
        }
        return renderTableActions(actions)
      }
  }
]

const statusColumns: DataTableColumns<StatusRow> = [
  { title: '材料类别', key: 'label', minWidth: 180 },
  { title: '总数', key: 'total', width: 80, render: (row) => h('span', { class: 'numeric' }, String(row.total)) },
  { title: '复审通过', key: 'passedCount', width: 100 },
  { title: '不通过', key: 'failedCount', width: 90 },
  { title: '类别结果', key: 'passed', width: 110, render: (row) => h(StatusTag, { text: row.passed ? '通过' : '未通过' }) }
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
  } catch (error) {
    showError(error, '材料列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [studentRes, categoryRes] = await Promise.all([
    canViewStudents.value ? listStudents() : Promise.resolve(null),
    listDictItems('material_category', true)
  ])
  students.value = studentRes ? (selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records) : []
  categories.value = categoryRes.data
}

function openUpload(category?: string) {
  replacing.value = null
  uploadForm.studentId = selfMode.value ? userStore.currentUser?.studentId || students.value[0]?.id || '' : ''
  uploadForm.assessmentYear = assessmentYear.value
  uploadForm.category = category || ''
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
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function openPreview(row: ProcessMaterial) {
  previewRow.value = row
  try {
    const res = await previewMaterial(row.id)
    previewUrl.value = res.data
    previewVisible.value = true
  } catch (error) {
    showError(error, '预览地址获取失败')
  }
}

async function submit(row: ProcessMaterial) {
  try {
    await submitMaterial(row.id)
    message.success('已提交')
    await loadRecords()
  } catch (error) {
    showError(error, '提交失败')
  }
}

async function remove(row: ProcessMaterial) {
  try {
    await deleteMaterial(row.id)
    message.success('已删除')
    await loadRecords()
  } catch (error) {
    showError(error, '删除失败')
  }
}

function openReview(row: ProcessMaterial, stage: 'first' | 'second') {
  reviewing.value = { material: row, stage }
  reviewVisible.value = true
}

async function saveReview(payload: ReviewPayload) {
  if (!reviewing.value) return
  if (payload.action !== 'PASS' && !payload.comment?.trim()) {
    message.error('退回或不通过必须填写原因')
    return
  }
  reviewSaving.value = true
  try {
    if (reviewing.value.stage === 'first') await firstReviewMaterial(reviewing.value.material.id, payload)
    else await secondReviewMaterial(reviewing.value.material.id, payload)
    message.success('审核完成')
    reviewVisible.value = false
    await loadRecords()
  } catch (error) {
    showError(error, '审核失败')
  } finally {
    reviewSaving.value = false
  }
}

async function showProcessStatus() {
  const studentId = uploadForm.studentId || records.value[0]?.studentId || userStore.currentUser?.studentId || ''
  if (!studentId) {
    message.error('请选择或查询到一个学生')
    return
  }
  try {
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
  } catch (error) {
    showError(error, '合格判定加载失败')
  }
}

async function batchDownload() {
  try {
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
  } catch (error) {
    showError(error, '批量下载失败')
  }
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  categoryFilter.value = null
  assessmentYear.value = yearStore.assessmentYear
  void loadRecords()
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function latestMaterial(category: string) {
  return records.value
    .filter((item) => item.category === category)
    .sort((a, b) => String(b.uploadTime || '').localeCompare(String(a.uploadTime || '')))[0] || null
}

function rejectComment(row?: ProcessMaterial | null) {
  if (!row) return ''
  if (!row.status.includes('REJECTED') && row.status !== 'FAILED') return ''
  return row.secondReviewComment || row.firstReviewComment || ''
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadRecords()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    if (!uploadVisible.value) uploadForm.assessmentYear = year
    await loadRecords()
  }
)
</script>

<template>
  <PageContainer title="过程性材料" description="四类过程性材料上传、提交与审核；四类均通过即合格。">
    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="材料总数" :value="statusSummary.total" /></n-gi>
      <n-gi><StatCard label="复审通过" :value="statusSummary.passed" tone="success" /></n-gi>
      <n-gi><StatCard label="待审核" :value="statusSummary.pending" tone="warning" /></n-gi>
      <n-gi><StatCard label="退回/不通过" :value="statusSummary.rejected" tone="error" /></n-gi>
    </n-grid>

    <template v-if="selfMode">
      <n-card :bordered="false" class="page-section material-self-toolbar">
        <n-space justify="space-between" align="center">
          <n-space align="center">
            <span class="toolbar-label">考核年度</span>
            <n-input v-model:value="assessmentYear" placeholder="考核年度" class="mono-input" style="width: 130px" />
            <n-button secondary :loading="loading" @click="loadRecords">刷新</n-button>
          </n-space>
          <n-button @click="showProcessStatus">合格判定</n-button>
        </n-space>
      </n-card>

      <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" class="page-section material-card-grid">
        <n-gi v-for="card in materialCards" :key="card.code">
          <n-card :bordered="false" class="material-card">
            <div class="material-card__head">
              <div class="material-card__icon">
                <n-icon :component="card.icon" />
              </div>
              <div class="material-card__title">
                <strong>{{ card.label }}</strong>
                <StatusTag
                  :value="card.record?.status || 'WAIT_UPLOAD'"
                  :text="card.record?.statusLabel || statusLabel(card.record?.status || 'WAIT_UPLOAD')"
                />
              </div>
            </div>
            <div class="material-card__file">
              <span>{{ card.record?.fileName || '尚未上传材料' }}</span>
              <small>{{ formatFileSize(card.record?.fileSize) }}</small>
            </div>
            <n-alert v-if="rejectComment(card.record)" type="warning" :bordered="false">
              退回意见：{{ rejectComment(card.record) }}
            </n-alert>
            <n-space class="material-card__actions">
              <n-button v-if="card.record" secondary size="small" @click="openPreview(card.record)">预览</n-button>
              <n-button size="small" @click="card.record ? openReplace(card.record) : openUpload(card.code)">
                {{ card.record ? '替换' : '上传' }}
              </n-button>
              <n-button v-if="card.record" type="primary" size="small" @click="submit(card.record)">提交</n-button>
            </n-space>
          </n-card>
        </n-gi>
      </n-grid>
    </template>

    <template v-else>
      <FilterBar :loading="loading" @submit="loadRecords" @reset="resetFilters">
        <label class="filter-field">
          <span>关键词</span>
          <n-input v-model:value="keyword" clearable placeholder="文件名 / 类别 / 学生" style="width: 220px" @keyup.enter="loadRecords" />
        </label>
        <label class="filter-field">
          <span>年度</span>
          <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
        </label>
        <label class="filter-field">
          <span>类别</span>
          <n-select v-model:value="categoryFilter" clearable :options="categoryOptions" placeholder="全部类别" style="width: 190px" />
        </label>
        <label class="filter-field">
          <span>状态</span>
          <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
        </label>
      </FilterBar>

      <DataPanel
        title="材料列表"
        :columns="columns"
        :data="records"
        :total="records.length"
        :loading="loading"
        :scroll-x="1440"
        empty-title="暂无材料"
        empty-description="当前筛选条件下没有过程性材料。"
        @refresh="loadRecords"
      >
        <template #actions>
          <n-button size="small" @click="showProcessStatus">合格判定</n-button>
          <n-button v-if="canBatchDownload" size="small" @click="batchDownload">批量下载</n-button>
          <n-button v-if="canUpload" type="primary" size="small" @click="openUpload()">上传材料</n-button>
        </template>
        <template v-if="canUpload" #emptyAction>
          <n-button type="primary" @click="openUpload()">上传材料</n-button>
        </template>
      </DataPanel>
    </template>

    <n-drawer v-model:show="uploadVisible" :width="560">
      <n-drawer-content :title="replacing ? '替换材料' : '上传材料'" closable>
        <n-alert v-if="replacing" type="info" :bordered="false" class="page-section">
          替换材料沿用原学生、年度与材料类别。
        </n-alert>
        <n-form label-placement="top">
          <div class="form-section-title">材料信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学生" :span="2">
              <n-select v-model:value="uploadForm.studentId" :options="studentOptions" :disabled="Boolean(replacing) || selfMode" filterable placeholder="学生" />
            </n-form-item-gi>
            <n-form-item-gi label="考核年度">
              <n-input v-model:value="uploadForm.assessmentYear" :disabled="Boolean(replacing)" placeholder="考核年度" class="mono-input" />
            </n-form-item-gi>
            <n-form-item-gi label="材料类别">
              <n-select v-model:value="uploadForm.category" :options="categoryOptions" :disabled="Boolean(replacing)" placeholder="材料类别" />
            </n-form-item-gi>
          </n-grid>
          <div class="form-section-title">上传文件</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="文件" :span="2">
              <n-upload v-model:file-list="fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="uploadVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveUpload">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="previewVisible" preset="card" :title="previewRow?.fileName || '材料预览'" style="width: min(960px, 94vw)">
      <n-space vertical>
        <object v-if="previewable" :data="previewUrl" class="preview-frame">
          <iframe :src="previewUrl" class="preview-frame" />
        </object>
        <n-result v-else status="info" title="该文件不支持内联预览" description="非 PDF/JPG/PNG 文件请通过下载链接查看。">
          <template #footer>
            <n-button tag="a" :href="previewUrl" target="_blank" type="primary">打开文件</n-button>
          </template>
        </n-result>
      </n-space>
    </n-modal>

    <ReviewDialog
      v-model:show="reviewVisible"
      :title="reviewing?.stage === 'first' ? '材料初审' : '材料复审'"
      :loading="reviewSaving"
      allow-fail
      :summary="reviewing ? [
        { label: '学号', value: reviewing.material.studentNo },
        { label: '姓名', value: reviewing.material.studentName },
        { label: '材料类别', value: reviewing.material.categoryLabel || dictLabel(categories, reviewing.material.category) },
        { label: '当前状态', status: reviewing.material.status }
      ] : []"
      @submit="saveReview"
    />

    <n-modal v-model:show="statusVisible" preset="card" title="过程性考核合格判定" style="width: 680px">
      <n-space vertical>
        <n-alert :type="processQualified ? 'success' : 'warning'" :bordered="false">
          {{ processQualified ? '四类材料均已复审通过' : '仍有材料类别缺失或未复审通过' }}
        </n-alert>
        <n-data-table :columns="statusColumns" :data="statusRows" :pagination="false" />
      </n-space>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.preview-frame {
  width: 100%;
  height: min(70vh, 720px);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-card);
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}

.file-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: var(--text-muted);
  font-size: 12px;
}

.material-self-toolbar :deep(.n-card__content) {
  padding: var(--space-4);
}

.toolbar-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.material-card {
  min-height: 236px;
}

.material-card :deep(.n-card__content) {
  display: flex;
  min-height: 236px;
  flex-direction: column;
  gap: var(--space-3);
}

.material-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.material-card__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
}

.material-card__title {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.material-card__title strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-card__file {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--surface-muted);
}

.material-card__file span,
.material-card__file small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-card__file small {
  color: var(--text-muted);
}

.material-card__actions {
  margin-top: auto;
}
</style>
