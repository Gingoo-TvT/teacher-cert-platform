<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NIcon,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type SelectOption
} from 'naive-ui'
import { EyeOutline } from '@vicons/ionicons5'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import MaterialSelfPanel from './components/MaterialSelfPanel.vue'
import MaterialUploadDrawer from './components/MaterialUploadDrawer.vue'
import MaterialPreviewModal from './components/MaterialPreviewModal.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { formatFileSize } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  batchDownloadMaterials,
  deleteMaterial,
  firstReviewMaterial,
  getProcessStatus,
  listMaterials,
  secondReviewMaterial,
  submitMaterial,
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

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const reviewSaving = ref(false)
const reviewVisible = ref(false)
const statusVisible = ref(false)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const categoryFilter = ref<string | null>(null)
const assessmentYear = ref(yearStore.assessmentYear)
const records = ref<ProcessMaterial[]>([])
const materialTotal = ref(0)
const page = ref(1)
const size = ref(20)
const categories = ref<DictItem[]>([])
const reviewing = ref<{ material: ProcessMaterial; stage: 'first' | 'second' } | null>(null)
const processQualified = ref(false)
const statusRows = ref<StatusRow[]>([])
const uploadDrawerRef = ref<InstanceType<typeof MaterialUploadDrawer> | null>(null)
const previewModalRef = ref<InstanceType<typeof MaterialPreviewModal> | null>(null)

const canUpload = computed(() => userStore.hasPerm('material:upload'))
const canFirstReview = computed(() => userStore.hasPerm('material:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('material:secondReview'))
const canBatchDownload = computed(() => userStore.hasPerm('material:batchDownload'))
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

const categoryOptions = computed<SelectOption[]>(() =>
  categories.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
// P1-1 真分页 rollout（Phase 44e）：records 现仅为「当页」数据（真分页前是全量），故 passed/pending/rejected
// 这三项不再能代表「全量筛选结果」的通过/待审/退回统计，只反映当页——StatCard 标签相应加「本页」以免误导；
// 「材料总数」改用后端 total（materialTotal，selectPage count 结果），不再受当页截断影响。
const statusSummary = computed(() => {
  const passed = records.value.filter((item) => item.status === 'PASSED').length
  const pending = records.value.filter((item) => ['FIRST_REVIEW', 'SECOND_REVIEW'].includes(item.status)).length
  const rejected = records.value.filter((item) => item.status.includes('REJECTED') || item.status === 'FAILED').length
  return { passed, pending, rejected }
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
  { title: '总数', key: 'total', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.total)) },
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
      assessmentYear: assessmentYear.value,
      page: page.value,
      size: size.value
    })
    records.value = res.data.records
    materialTotal.value = res.data.total
  } catch (error) {
    showError(error, '材料列表加载失败')
  } finally {
    loading.value = false
  }
}

// 筛选变更（关键词/状态/类别/年度）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
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
  const categoryRes = await listDictItems('material_category', true)
  categories.value = categoryRes.data
}

function openUpload(category?: string) {
  uploadDrawerRef.value?.open(undefined, category)
}

function openReplace(row: ProcessMaterial) {
  uploadDrawerRef.value?.open(row)
}

function openPreview(row: ProcessMaterial) {
  previewModalRef.value?.open(row)
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
  const studentId = uploadDrawerRef.value?.getStudentId() || records.value[0]?.studentId || userStore.currentUser?.studentId || ''
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
    const url = URL.createObjectURL(res)
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
  search()
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(() => {
  // 并行加载，避免"选项→列表"两段串行造成的骨架屏二次闪烁
  void Promise.all([loadOptions(), loadRecords()])
})

watch(
  () => yearStore.assessmentYear,
  (year) => {
    assessmentYear.value = year
    search()
  }
)
</script>

<template>
  <PageContainer title="过程性材料" description="四类过程性材料上传、提交与审核；四类均通过即合格。">
    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="材料总数" :value="materialTotal" /></n-gi>
      <n-gi><StatCard label="本页通过" :value="statusSummary.passed" tone="success" /></n-gi>
      <n-gi><StatCard label="本页待审核" :value="statusSummary.pending" tone="warning" /></n-gi>
      <n-gi><StatCard label="本页退回/不通过" :value="statusSummary.rejected" tone="error" /></n-gi>
    </n-grid>

    <MaterialSelfPanel
      v-if="selfMode"
      v-model:assessment-year="assessmentYear"
      :records="records"
      :categories="categories"
      :loading="loading"
      @refresh="loadRecords"
      @status="showProcessStatus"
      @preview="openPreview"
      @upload="openUpload"
      @replace="openReplace"
      @submit="submit"
    />

    <template v-else>
      <FilterBar :loading="loading" @submit="search" @reset="resetFilters">
        <label class="filter-field">
          <span>关键词</span>
          <n-input v-model:value="keyword" clearable placeholder="文件名 / 类别 / 学生" style="width: 220px" @keyup.enter="search" />
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
        :total="materialTotal"
        :loading="loading"
        remote
        :page="page"
        :page-size="size"
        empty-title="暂无材料"
        empty-description="当前筛选条件下没有过程性材料。"
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
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

    <MaterialUploadDrawer
      ref="uploadDrawerRef"
      :category-options="categoryOptions"
      :self-mode="selfMode"
      :assessment-year="assessmentYear"
      @saved="loadRecords"
    />

    <MaterialPreviewModal ref="previewModalRef" />

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
</style>
