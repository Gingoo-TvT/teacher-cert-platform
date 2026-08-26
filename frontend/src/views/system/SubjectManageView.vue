<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, useMessage, type DataTableColumns, type SelectOption, type UploadCustomRequestOptions } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import SubjectSelect from '@/components/SubjectSelect.vue'
import {
  importSubjects,
  listSubjects,
  recordRecentSubject,
  validateSubject,
  type SubjectImportError,
  type SubjectImportResult,
  type TeachingSubject
} from '@/api/subject'
import { renderTableActions } from '@/utils/tableActions'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const loading = ref(true)
const importLoading = ref(false)
const segmentsLoading = ref(true)
const categoriesLoading = ref(false)
const listError = ref('')
const segmentsError = ref('')
const categoriesError = ref('')
const hasLoadedSegments = ref(false)
const hasLoadedCategories = ref(false)
const hasLoadedSubjects = ref(false)
const segments = ref<DictItem[]>([])
const subjects = ref<TeachingSubject[]>([])
const baseSubjects = ref<TeachingSubject[]>([])
const selectedSegment = ref<string | null>(null)
const selectedSubjectCode = ref<string | null>(null)
const selectedSubject = ref<TeachingSubject | null>(null)
const keyword = ref('')
const category = ref<string | null>(null)
const yearVersion = ref('GLOBAL')
const importYearVersion = ref('GLOBAL')
const importResult = ref<SubjectImportResult | null>(null)
const loadedCategoriesKey = ref('')
const loadedSubjectsKey = ref('')
let segmentsRequestId = 0
let categoriesRequestId = 0
let subjectsRequestId = 0

interface SubjectTableRow extends TeachingSubject {
  segmentName: string
  categoryName: string
}

const canImport = computed(() => userStore.hasPerm('subject:import'))
const categoriesQueryKey = computed(() => JSON.stringify([selectedSegment.value || '', yearVersion.value]))
const subjectsQueryKey = computed(() => JSON.stringify([
  selectedSegment.value || '',
  yearVersion.value,
  category.value || '',
  keyword.value
]))
const segmentOptionsReady = computed(() =>
  hasLoadedSegments.value && !segmentsLoading.value && !segmentsError.value && Boolean(selectedSegment.value)
)
const categoryOptionsReady = computed(() =>
  hasLoadedCategories.value
  && loadedCategoriesKey.value === categoriesQueryKey.value
  && !categoriesLoading.value
  && !categoriesError.value
)
const subjectDataFresh = computed(() =>
  hasLoadedSubjects.value
  && loadedSubjectsKey.value === subjectsQueryKey.value
  && !loading.value
  && !listError.value
)
const dependentActionsReady = computed(() => segmentOptionsReady.value && categoryOptionsReady.value && subjectDataFresh.value)

const segmentOptions = computed<SelectOption[]>(() =>
  segments.value.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)

const categoryOptions = computed<SelectOption[]>(() =>
  baseSubjects.value
    .filter((item) => item.isCategory === 1)
    .map((item) => ({ label: `${item.subjectName} ${item.subjectCode}`, value: item.categoryNode || item.subjectCode }))
)

const tableRows = computed<SubjectTableRow[]>(() =>
  subjects.value.map((item) => ({
    ...item,
    segmentName: segmentName(item.segmentCode),
    categoryName: categoryName(item.categoryNode)
  }))
)

const subjectColumns: DataTableColumns<SubjectTableRow> = [
  { title: '学段', key: 'segmentName', width: 130, ellipsis: { tooltip: true } },
  { title: '学科名称', key: 'subjectName', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '学科编码', key: 'subjectCode', minWidth: 170, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.subjectCode) },
  { title: '分类', key: 'categoryName', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '年度', key: 'yearVersion', width: 96, render: (row) => h('span', { class: 'mono' }, row.yearVersion) },
  { title: '类型', key: 'selectable', width: 92, render: (row) => h(StatusTag, { text: row.selectable ? '可选' : '类别' }) },
  { title: '关键词', key: 'keyword', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 92,
    render: (row) =>
      renderTableActions([
        h(
          NButton,
          { size: 'small', quaternary: true, disabled: !row.selectable || !dependentActionsReady.value, onClick: () => chooseSubject(row) },
          { default: () => '选择' }
        )
      ])
  }
]

const errorColumns: DataTableColumns<SubjectImportError> = [
  { title: '行号', key: 'rowNo', width: 80, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.rowNo)) },
  { title: '字段', key: 'field', width: 150, ellipsis: { tooltip: true } },
  { title: '错误值', key: 'errorValue', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '原因', key: 'reason', minWidth: 220, ellipsis: { tooltip: true } }
]

const subjectDetailItems = computed(() => {
  const row = selectedSubject.value
  if (!row) {
    return [
      { label: '当前学段', value: selectedSegment.value ? segmentName(selectedSegment.value) : '-' },
      { label: '当前学科', value: '-' }
    ]
  }
  return [
    { label: '当前学段', value: segmentName(row.segmentCode) },
    { label: '学科名称', value: row.subjectName },
    { label: '学科编码', value: row.subjectCode, mono: true },
    { label: '分类', value: categoryName(row.categoryNode) },
    { label: '年度', value: row.yearVersion, mono: true },
    { label: '类型', value: row.selectable ? '可选' : '类别' },
    { label: '关键词', value: row.keyword || '-', span: 2 }
  ]
})

async function loadSegments() {
  const requestId = ++segmentsRequestId
  segmentsLoading.value = true
  segmentsError.value = ''
  try {
    const res = await listDictItems('teaching_segment', true)
    if (requestId !== segmentsRequestId) return false
    segments.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
    hasLoadedSegments.value = true
    if (!selectedSegment.value || !segments.value.some((item) => item.itemCode === selectedSegment.value)) {
      selectedSegment.value = segments.value[0]?.itemCode || null
      selectedSubjectCode.value = null
      selectedSubject.value = null
      keyword.value = ''
      category.value = null
      subjects.value = []
      baseSubjects.value = []
      hasLoadedSubjects.value = false
      hasLoadedCategories.value = false
      loadedSubjectsKey.value = ''
      loadedCategoriesKey.value = ''
    }
    return true
  } catch (error) {
    if (requestId !== segmentsRequestId) return false
    segmentsError.value = showError(error, '任教学段加载失败')
    return false
  } finally {
    if (requestId === segmentsRequestId) segmentsLoading.value = false
  }
}

async function loadBaseSubjects() {
  const requestId = ++categoriesRequestId
  const segment = selectedSegment.value
  const queryYearVersion = yearVersion.value
  const queryKey = categoriesQueryKey.value
  if (!segment) {
    categoriesLoading.value = false
    hasLoadedCategories.value = false
    loadedCategoriesKey.value = ''
    return
  }
  categoriesLoading.value = true
  categoriesError.value = ''
  try {
    const res = await listSubjects({ segment, yearVersion: queryYearVersion })
    if (requestId !== categoriesRequestId || queryKey !== categoriesQueryKey.value) return
    baseSubjects.value = res.data
    hasLoadedCategories.value = true
    loadedCategoriesKey.value = queryKey
  } catch (error) {
    if (requestId !== categoriesRequestId || queryKey !== categoriesQueryKey.value) return
    categoriesError.value = showError(error, '学科分类加载失败')
  } finally {
    if (requestId === categoriesRequestId) categoriesLoading.value = false
  }
}

async function loadSubjectsData() {
  const requestId = ++subjectsRequestId
  const segment = selectedSegment.value
  const queryYearVersion = yearVersion.value
  const queryCategory = category.value
  const queryKeyword = keyword.value
  const queryKey = subjectsQueryKey.value
  if (!segment) {
    loading.value = false
    subjects.value = []
    if (segmentsError.value) {
      listError.value = '任教学段尚未加载，暂时无法查询任教学科'
      hasLoadedSubjects.value = false
    } else {
      listError.value = ''
      hasLoadedSubjects.value = true
      loadedSubjectsKey.value = queryKey
    }
    return
  }
  loading.value = true
  listError.value = ''
  try {
    const res = await listSubjects({
      segment,
      keyword: queryKeyword,
      category: queryCategory,
      yearVersion: queryYearVersion
    })
    if (requestId !== subjectsRequestId || queryKey !== subjectsQueryKey.value) return
    subjects.value = res.data
    hasLoadedSubjects.value = true
    loadedSubjectsKey.value = queryKey
  } catch (error) {
    if (requestId !== subjectsRequestId || queryKey !== subjectsQueryKey.value) return
    listError.value = showError(error, '任教学科加载失败')
  } finally {
    if (requestId === subjectsRequestId) loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadBaseSubjects(), loadSubjectsData()])
}

async function initializeSubjects() {
  await loadSegments()
  await refreshAll()
}

async function handleSegmentUpdate(value: string | null) {
  selectedSegment.value = value
  selectedSubjectCode.value = null
  selectedSubject.value = null
  keyword.value = ''
  category.value = null
  subjects.value = []
  baseSubjects.value = []
  listError.value = ''
  categoriesError.value = ''
  hasLoadedSubjects.value = false
  hasLoadedCategories.value = false
  loadedSubjectsKey.value = ''
  loadedCategoriesKey.value = ''
  await refreshAll()
}

async function chooseSubject(row: TeachingSubject) {
  if (!selectedSegment.value || !dependentActionsReady.value) {
    message.warning('学段、分类或学科列表尚未就绪，请重试加载后再选择')
    return
  }
  try {
    await validateSubject({ segmentCode: selectedSegment.value, subjectCode: row.subjectCode, yearVersion: yearVersion.value })
    await recordRecentSubject({ segmentCode: selectedSegment.value, subjectCode: row.subjectCode, yearVersion: yearVersion.value })
    selectedSubjectCode.value = row.subjectCode
    selectedSubject.value = row
    message.success('任教学科已选择')
  } catch (error) {
    showError(error, '任教学科校验失败')
  }
}

async function handleUpload(options: UploadCustomRequestOptions) {
  if (!dependentActionsReady.value) {
    options.onError()
    message.warning('学段、分类或学科列表尚未就绪，暂不可导入')
    return
  }
  const file = options.file.file
  if (!file) {
    options.onError()
    message.error('导入文件不能为空')
    return
  }
  importLoading.value = true
  importResult.value = null
  options.onProgress({ percent: 30 })
  try {
    const res = await importSubjects(file, importYearVersion.value)
    importResult.value = res.data
    options.onProgress({ percent: 100 })
    options.onFinish()
    if (res.data.failCount > 0) {
      message.error('任教学科导入存在错误')
    } else {
      message.success('任教学科导入完成')
      yearVersion.value = importYearVersion.value || 'GLOBAL'
      await refreshAll()
    }
  } catch (error) {
    options.onError()
    showError(error, '任教学科导入失败')
  } finally {
    importLoading.value = false
  }
}

function handleSubjectChange(subject: TeachingSubject | null) {
  selectedSubject.value = subject
}

function rowProps(row: object) {
  const item = row as TeachingSubject
  return {
    class: item.subjectCode === selectedSubjectCode.value ? 'is-selected-row' : ''
  }
}

function resetFilters() {
  keyword.value = ''
  category.value = null
  yearVersion.value = 'GLOBAL'
  void refreshAll()
}

function segmentName(code: string) {
  return segments.value.find((item) => item.itemCode === code)?.itemValue || code
}

function categoryName(code?: string | null) {
  if (!code) return '-'
  return baseSubjects.value.find((item) => item.isCategory === 1 && (item.subjectCode === code || item.categoryNode === code))?.subjectName || code
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
  return detail || fallback
}

onMounted(initializeSubjects)
</script>

<template>
  <PageContainer title="任教学科库" description="任教学段与学科标准库维护。">
    <n-alert v-if="segmentsError" type="warning" title="任教学段加载失败" :bordered="false" class="page-section" role="alert">
      {{ selectedSegment ? '任教学科列表仍按上次学段独立加载，但选择与导入暂不可用。' : '暂时无法确定任教学段，学科选择与导入不可用。' }}{{ segmentsError }}
      <n-button text type="warning" size="small" :loading="segmentsLoading" @click="initializeSubjects">重试加载学段</n-button>
    </n-alert>
    <n-alert v-if="categoriesError" type="warning" title="学科分类加载失败" :bordered="false" class="page-section" role="alert">
      学科列表已独立加载，但分类筛选、学科选择与导入暂不可用。{{ categoriesError }}
      <n-button text type="warning" size="small" :loading="categoriesLoading" @click="loadBaseSubjects">重试加载分类</n-button>
    </n-alert>

    <n-grid responsive="self" item-responsive cols="1 1180:12" :x-gap="24" :y-gap="24">
      <n-gi span="1 1180:8" class="page-section">
        <FilterBar :loading="loading || segmentsLoading || categoriesLoading" @submit="refreshAll" @reset="resetFilters">
          <label class="filter-field">
            <span>学段</span>
            <n-select
              v-model:value="selectedSegment"
              :options="segmentOptions"
              :loading="segmentsLoading"
              :disabled="segmentsLoading || Boolean(segmentsError)"
              placeholder="全部学段"
              style="width: 180px; max-width: 100%"
              @update:value="handleSegmentUpdate"
            />
          </label>
          <label class="filter-field">
            <span>年度</span>
            <n-input v-model:value="yearVersion" clearable maxlength="16" placeholder="年度版本" style="width: 140px; max-width: 100%" />
          </label>
          <label class="filter-field">
            <span>分类</span>
            <n-select
              v-model:value="category"
              :options="categoryOptions"
              :loading="categoriesLoading"
              :disabled="!selectedSegment || categoriesLoading || Boolean(categoriesError) || !categoryOptionsReady"
              clearable
              placeholder="分类"
              style="width: 220px; max-width: 100%"
              @update:value="loadSubjectsData"
            />
          </label>
          <label class="filter-field">
            <span>关键词</span>
            <n-input v-model:value="keyword" clearable placeholder="关键词" style="width: 220px; max-width: 100%" @keyup.enter="loadSubjectsData" />
          </label>
        </FilterBar>

        <DataPanel
          title="任教学科"
          :columns="subjectColumns"
          :data="tableRows"
          :total="tableRows.length"
          :loading="loading"
          :error="listError"
          error-title="任教学科加载失败"
          :row-props="rowProps"
          :max-height="620"
          empty-title="暂无任教学科"
          empty-description="当前筛选条件下没有任教学科记录。"
          @refresh="refreshAll"
        />
      </n-gi>

      <n-gi span="1 1180:4" class="page-section side-panel">
        <n-card :bordered="false" class="detail-card">
          <div class="detail-head">
            <div>
              <strong>{{ selectedSubject?.subjectName || '学科详情' }}</strong>
              <span class="muted mono">{{ selectedSubjectCode || '请选择任教学科' }}</span>
            </div>
          </div>
          <n-form label-placement="top">
            <n-form-item label="表单选择器预览">
              <SubjectSelect
                v-model:value="selectedSubjectCode"
                :segment-code="selectedSegment"
                :year-version="yearVersion"
                :disabled="!dependentActionsReady"
                @change="handleSubjectChange"
              />
            </n-form-item>
          </n-form>
          <DetailPanel :items="subjectDetailItems" :columns="2" />
        </n-card>

        <n-card v-if="canImport" :bordered="false" class="detail-card">
          <div class="form-section-title">导入设置</div>
          <n-form label-placement="top">
            <n-form-item label="导入年度">
              <n-input v-model:value="importYearVersion" clearable maxlength="16" />
            </n-form-item>
            <n-form-item label="导入文件">
              <n-upload :custom-request="handleUpload" :show-file-list="false" accept=".xlsx,.xls" :disabled="importLoading || !dependentActionsReady">
                <n-upload-dragger>
                  <n-text>{{ importLoading ? '正在导入学科库' : '点击或拖拽学科库文件到此处导入' }}</n-text>
                  <n-p depth="3">支持 XLSX、XLS；按上方导入年度写入。</n-p>
                </n-upload-dragger>
              </n-upload>
            </n-form-item>
          </n-form>
          <n-space v-if="importResult" :size="8" class="import-tags">
            <n-tag type="info" :bordered="false">总数 {{ importResult.total }}</n-tag>
            <n-tag type="success" :bordered="false">成功 {{ importResult.successCount }}</n-tag>
            <n-tag :type="importResult.failCount > 0 ? 'error' : 'default'" :bordered="false">失败 {{ importResult.failCount }}</n-tag>
          </n-space>
          <DataPanel
            v-if="importResult?.errors.length"
            title="导入错误"
            :columns="errorColumns"
            :data="importResult.errors"
            :total="importResult.errors.length"
            :max-height="260"
            empty-title="暂无错误"
            empty-description="当前导入结果没有错误明细。"
            :show-refresh="false"
          />
        </n-card>
      </n-gi>
    </n-grid>
  </PageContainer>
</template>

<style scoped>
.page-section {
  min-width: 0;
}

.side-panel {
  min-width: 0;
}

.detail-card {
  margin-bottom: var(--space-5);
}

.detail-card :deep(.n-card__content) {
  padding: var(--space-5);
}

.detail-head {
  margin-bottom: var(--space-4);
}

.detail-head strong,
.detail-head span {
  display: block;
}

.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.import-tags {
  margin-bottom: var(--space-4);
}

:deep(.is-selected-row td) {
  background: var(--brand-soft);
}

:deep(.n-upload) {
  max-width: 100%;
}
</style>
