<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, useMessage, type DataTableColumns, type SelectOption, type UploadCustomRequestOptions } from 'naive-ui'
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
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const loading = ref(false)
const importLoading = ref(false)
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

interface SubjectTableRow extends TeachingSubject {
  segmentName: string
  categoryName: string
}

const canImport = computed(() => userStore.hasPerm('subject:import'))

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
      h(
        NButton,
        { size: 'small', quaternary: true, type: 'primary', disabled: !row.selectable, onClick: () => chooseSubject(row) },
        { default: () => '选择' }
      )
  }
]

const errorColumns: DataTableColumns<SubjectImportError> = [
  { title: '行号', key: 'rowNo', width: 80 },
  { title: '字段', key: 'field', width: 150, ellipsis: { tooltip: true } },
  { title: '错误值', key: 'errorValue', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '原因', key: 'reason', minWidth: 220, ellipsis: { tooltip: true } }
]

async function loadSegments() {
  try {
    const res = await listDictItems('teaching_segment', true)
    segments.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
    if (!selectedSegment.value && segments.value.length > 0) selectedSegment.value = segments.value[0].itemCode
  } catch (error) {
    showError(error, '任教学段加载失败')
  }
}

async function loadBaseSubjects() {
  if (!selectedSegment.value) return
  try {
    const res = await listSubjects({ segment: selectedSegment.value, yearVersion: yearVersion.value })
    baseSubjects.value = res.data
  } catch (error) {
    showError(error, '学科分类加载失败')
  }
}

async function loadSubjectsData() {
  if (!selectedSegment.value) return
  loading.value = true
  try {
    const res = await listSubjects({
      segment: selectedSegment.value,
      keyword: keyword.value,
      category: category.value,
      yearVersion: yearVersion.value
    })
    subjects.value = res.data
  } catch (error) {
    showError(error, '任教学科加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await loadBaseSubjects()
  await loadSubjectsData()
}

async function handleSegmentUpdate(value: string | null) {
  selectedSegment.value = value
  selectedSubjectCode.value = null
  selectedSubject.value = null
  keyword.value = ''
  category.value = null
  subjects.value = []
  baseSubjects.value = []
  await refreshAll()
}

async function chooseSubject(row: TeachingSubject) {
  if (!selectedSegment.value) return
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
}

onMounted(async () => {
  await loadSegments()
  await refreshAll()
})
</script>

<template>
  <PageContainer title="任教学科库" description="学段到学科的标准库，类别节点仅用于分组，具体学科由后端 validate 接口校验。">
    <template #actions>
      <n-space>
        <n-button secondary @click="refreshAll">刷新</n-button>
        <n-upload v-if="canImport" :custom-request="handleUpload" :show-file-list="false" accept=".xlsx,.xls" :disabled="importLoading">
          <n-button type="primary" :loading="importLoading">导入学科库</n-button>
        </n-upload>
      </n-space>
    </template>

    <div class="subject-layout">
      <section class="page-section">
        <div class="panel-toolbar">
          <n-space class="filters" :size="10">
            <n-select
              v-model:value="selectedSegment"
              :options="segmentOptions"
              placeholder="任教学段"
              style="width: 180px"
              @update:value="handleSegmentUpdate"
            />
            <n-input v-model:value="yearVersion" clearable maxlength="16" placeholder="年度版本" style="width: 140px" />
            <n-select v-model:value="category" :options="categoryOptions" clearable placeholder="分类" style="width: 220px" @update:value="loadSubjectsData" />
            <n-input v-model:value="keyword" clearable placeholder="关键词" style="width: 220px" @keyup.enter="loadSubjectsData" />
            <n-button type="primary" secondary @click="refreshAll">查询</n-button>
          </n-space>
        </div>

        <n-data-table
          :columns="subjectColumns"
          :data="tableRows"
          :loading="loading"
          :row-key="(row: SubjectTableRow) => row.id"
          size="small"
          striped
          :max-height="620"
        />
      </section>

      <section class="page-section side-panel">
        <n-space vertical :size="14">
          <n-form label-placement="top">
            <n-form-item label="表单选择器预览">
              <SubjectSelect v-model:value="selectedSubjectCode" :segment-code="selectedSegment" :year-version="yearVersion" @change="handleSubjectChange" />
            </n-form-item>
            <n-form-item v-if="canImport" label="导入年度">
              <n-input v-model:value="importYearVersion" clearable maxlength="16" />
            </n-form-item>
          </n-form>

          <n-descriptions bordered :column="1" size="small">
            <n-descriptions-item label="当前学段">{{ selectedSegment ? segmentName(selectedSegment) : '-' }}</n-descriptions-item>
            <n-descriptions-item label="当前学科">
              {{ selectedSubject ? selectedSubject.subjectName : '-' }}
              <span v-if="selectedSubject" class="mono muted">{{ selectedSubject.subjectCode }}</span>
            </n-descriptions-item>
          </n-descriptions>

          <div v-if="importResult" class="import-result">
            <n-space :size="8">
              <n-tag type="info" :bordered="false">总数 {{ importResult.total }}</n-tag>
              <n-tag type="success" :bordered="false">成功 {{ importResult.successCount }}</n-tag>
              <n-tag :type="importResult.failCount > 0 ? 'error' : 'default'" :bordered="false">失败 {{ importResult.failCount }}</n-tag>
            </n-space>
            <n-data-table
              v-if="importResult.errors.length"
              :columns="errorColumns"
              :data="importResult.errors"
              :row-key="(row: SubjectImportError) => `${row.rowNo}-${row.field}-${row.errorValue}`"
              size="small"
              :max-height="260"
            />
          </div>
        </n-space>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.subject-layout {
  display: grid;
  grid-template-columns: minmax(680px, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
  align-items: start;
}

.page-section {
  min-width: 0;
}

.panel-toolbar {
  margin-bottom: 12px;
}

.filters {
  flex-wrap: wrap;
}

.side-panel {
  min-width: 320px;
}

.import-result {
  display: grid;
  gap: 12px;
}

@media (max-width: 1180px) {
  .subject-layout {
    grid-template-columns: 1fr;
  }
}
</style>
