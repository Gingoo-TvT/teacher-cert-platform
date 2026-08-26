<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  NButton,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type PaginationProps,
  type SelectOption
} from 'naive-ui'
import { PersonAddOutline } from '@vicons/ionicons5'
import { useRouter } from 'vue-router'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import ReviewDialog from '@/components/ReviewDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import StudentDrawer from './components/StudentDrawer.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { listColleges, type College } from '@/api/organization'
import {
  deleteStudent,
  firstReviewStudent,
  listStudents,
  secondReviewStudent,
  submitStudent,
  type ReviewPayload,
  type Student
} from '@/api/student'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const hasLoadedSuccessfully = ref(false)
const loadedQueryKey = ref('')
const optionsLoading = ref(false)
const optionsError = ref('')
const hasLoadedOptionsSuccessfully = ref(false)
const reviewSaving = ref(false)
const reviewVisible = ref(false)
const detailVisible = ref(false)
const drawerRef = ref<InstanceType<typeof StudentDrawer> | null>(null)
const reviewing = ref<{ student: Student; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const gradeFilter = ref('')
const collegeFilter = ref<string | null>(null)
const records = ref<Student[]>([])
const studentTotal = ref(0)
const page = ref(1)
const size = ref(20)
const colleges = ref<College[]>([])
const genders = ref<DictItem[]>([])
const idCardTypes = ref<DictItem[]>([])
const identityTypes = ref<DictItem[]>([])
const selectedStudent = ref<Student | null>(null)
const compactViewport = ref(false)
let compactViewportQuery: MediaQueryList | null = null
let listRequestSequence = 0
let optionsRequestSequence = 0

const statusOptions: SelectOption[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待初审', value: 'FIRST_REVIEW' },
  { label: '初审退回', value: 'FIRST_REJECTED' },
  { label: '待复审', value: 'SECOND_REVIEW' },
  { label: '复审退回', value: 'SECOND_REJECTED' },
  { label: '复审通过', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' }
]

const canEdit = computed(() => userStore.hasPerm('student:edit'))
const canFirstReview = computed(() => userStore.hasPerm('info:firstReview'))
const canSecondReview = computed(() => userStore.hasPerm('info:secondReview'))
const listQueryKey = computed(() => JSON.stringify([
  keyword.value,
  statusFilter.value || '',
  collegeFilter.value || '',
  gradeFilter.value,
  page.value,
  size.value
]))
const listDataFresh = computed(() =>
  hasLoadedSuccessfully.value
  && loadedQueryKey.value === listQueryKey.value
  && !loading.value
  && !loadError.value
)
const writeBlocked = computed(() => !listDataFresh.value)
const optionsReady = computed(() =>
  hasLoadedOptionsSuccessfully.value && !optionsLoading.value && !optionsError.value
)

const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))
const genderOptions = computed<SelectOption[]>(() => dictOptions(genders.value))
const idCardTypeOptions = computed<SelectOption[]>(() => dictOptions(idCardTypes.value))
const identityTypeOptions = computed<SelectOption[]>(() => dictOptions(identityTypes.value))
const detailColumns = computed(() => compactViewport.value ? 1 : 2)
const tablePagination = computed<PaginationProps>(() => compactViewport.value
  ? {
      pageSlot: 3,
      showSizePicker: false,
      prefix: ({ itemCount }: { itemCount?: number }) => `${itemCount ?? studentTotal.value} 条`
    }
  : {})

const detailItems = computed(() => {
  const row = selectedStudent.value
  if (!row) return []
  return [
    { label: '学号', value: row.studentNo, mono: true },
    { label: '姓名', value: row.name },
    { label: '性别', value: dictLabel(genders.value, row.gender) },
    { label: '身份类型', value: dictLabel(identityTypes.value, row.identityType) },
    { label: '证件类型', value: dictLabel(idCardTypes.value, row.idCardType) },
    { label: '证件号码', value: row.idCardNo, mono: true },
    { label: '出生日期', value: row.birthDate, mono: true },
    { label: '学院', value: collegeName(row.collegeId) },
    { label: '年级/班级', value: [row.grade, row.className].filter(Boolean).join(' / ') || '-' },
    { label: '生源地', value: row.sourceFull || '-', span: 2 },
    { label: '状态', status: row.status },
    { label: '锁定', value: row.locked ? '已锁定' : '未锁定' },
    { label: '初审意见', value: row.firstReviewComment || '-', span: 2 },
    { label: '复审意见', value: row.secondReviewComment || '-', span: 2 }
  ]
})

const columns: DataTableColumns<Student> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo) },
  { title: '姓名', key: 'name', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '性别', key: 'gender', width: 80, render: (row) => dictLabel(genders.value, row.gender) },
  { title: '身份类型', key: 'identityType', minWidth: 140, ellipsis: { tooltip: true }, render: (row) => dictLabel(identityTypes.value, row.identityType) },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.idCardNo) },
  { title: '生源地', key: 'sourceFull', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '年级/班级', key: 'grade', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => [row.grade, row.className].filter(Boolean).join(' / ') || '-' },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  { title: '锁定', key: 'locked', width: 76, render: (row) => h(StatusTag, { text: row.locked ? '已锁定' : '未锁定' }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 220,
    render: (row) =>
      {
        const actions = [
          h(NButton, { size: 'small', type: 'primary', onClick: () => openDetail(row) }, { default: () => '详情' })
        ]
        if (canEdit.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value || !optionsReady.value, onClick: () => openDrawer(row) }, { default: () => '编辑' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => submit(row) }, { default: () => '提交' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, disabled: writeBlocked.value, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        if (canEdit.value) {
          actions.push(
            h(
              NPopconfirm,
              { onPositiveClick: () => remove(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error', disabled: writeBlocked.value }, { default: () => '删除' }),
                default: () => '确认删除该学生？'
              }
            )
          )
        }
        return renderTableActions(actions)
      }
  }
]

async function loadStudents() {
  const requestSequence = ++listRequestSequence
  const queryKey = listQueryKey.value
  const query = {
    keyword: keyword.value,
    status: statusFilter.value,
    collegeId: collegeFilter.value,
    grade: gradeFilter.value,
    page: page.value,
    size: size.value
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await listStudents(query)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    records.value = res.data.records
    studentTotal.value = res.data.total
    hasLoadedSuccessfully.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = showError(error, '学生列表加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// 筛选变更（关键词/状态/学院/年级）→ 回到第 1 页再查（真分页下 total/页码需随筛选重置）。
function search() {
  page.value = 1
  void loadStudents()
}

function onPageChange(next: number) {
  page.value = next
  void loadStudents()
}

function onPageSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadStudents()
}

async function loadOptions() {
  const requestSequence = ++optionsRequestSequence
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const [collegeRes, genderRes, idTypeRes, identityRes] = await Promise.all([
      listColleges(),
      listDictItems('gender', true),
      listDictItems('id_card_type', true),
      listDictItems('identity_type', true)
    ])
    if (requestSequence !== optionsRequestSequence) return
    colleges.value = collegeRes.data
    genders.value = genderRes.data
    idCardTypes.value = idTypeRes.data
    identityTypes.value = identityRes.data
    hasLoadedOptionsSuccessfully.value = true
  } catch (error) {
    if (requestSequence !== optionsRequestSequence) return
    optionsError.value = showError(error, '学生选项加载失败')
  } finally {
    if (requestSequence === optionsRequestSequence) optionsLoading.value = false
  }
}

function openDetail(row: Student) {
  selectedStudent.value = row
  detailVisible.value = true
}

function openDrawer(row?: Student) {
  if (writeBlocked.value || !optionsReady.value) return
  drawerRef.value?.open(row)
}

async function submit(row: Student) {
  if (writeBlocked.value) return
  try {
    await submitStudent(row.id)
    message.success('已提交')
    await loadStudents()
  } catch (error) {
    showError(error, '提交失败')
  }
}

async function remove(row: Student) {
  if (writeBlocked.value) return
  try {
    await deleteStudent(row.id)
    message.success('已删除')
    await loadStudents()
  } catch (error) {
    showError(error, '删除失败')
  }
}

function openReview(row: Student, stage: 'first' | 'second') {
  if (writeBlocked.value) return
  reviewing.value = { student: row, stage }
  reviewVisible.value = true
}

async function saveReview(payload: ReviewPayload) {
  if (!reviewing.value || writeBlocked.value) return
  if (payload.action !== 'PASS' && !payload.comment?.trim()) {
    message.error('退回或不通过必须填写原因')
    return
  }
  reviewSaving.value = true
  try {
    if (reviewing.value.stage === 'first') await firstReviewStudent(reviewing.value.student.id, payload)
    else await secondReviewStudent(reviewing.value.student.id, payload)
    message.success('审核完成')
    reviewVisible.value = false
    await loadStudents()
  } catch (error) {
    showError(error, '审核失败')
  } finally {
    reviewSaving.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  collegeFilter.value = null
  gradeFilter.value = ''
  search()
}

function goImport() {
  void router.push({ name: 'exchangeImport' })
}

function dictOptions(items: DictItem[]): SelectOption[] {
  return items.map((item) => ({ label: item.itemValue, value: item.itemCode }))
}

function dictLabel(items: DictItem[], code?: string | null) {
  return items.find((item) => item.itemCode === code)?.itemValue || code || '-'
}

function collegeName(id?: string | null) {
  return colleges.value.find((item) => item.id === id)?.name || id || '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  const text = detail || fallback
  message.error(text)
  return text
}

function syncCompactViewport(event?: MediaQueryListEvent) {
  compactViewport.value = event?.matches ?? compactViewportQuery?.matches ?? false
}

onMounted(() => {
  compactViewportQuery = window.matchMedia('(max-width: 768px)')
  syncCompactViewport()
  compactViewportQuery.addEventListener('change', syncCompactViewport)
  void loadOptions()
  void loadStudents()
})

onBeforeUnmount(() => {
  compactViewportQuery?.removeEventListener('change', syncCompactViewport)
})
</script>

<template>
  <PageContainer title="学生基本信息" description="学生基本信息查询、初审与复审。">
    <n-alert v-if="optionsError" type="warning" :bordered="false" class="page-section">
      学生列表已独立加载，但表单选项加载失败；新增和编辑暂不可用。{{ optionsError }}
      <n-button text type="warning" size="small" :loading="optionsLoading" @click="loadOptions">重试加载选项</n-button>
    </n-alert>

    <FilterBar :loading="loading" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学号 / 姓名" style="width: 220px" @keyup.enter="search" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
      <label class="filter-field">
        <span>学院</span>
        <n-select v-model:value="collegeFilter" clearable filterable :loading="optionsLoading" :options="collegeOptions" placeholder="全部学院" style="width: 220px" />
      </label>
      <label class="filter-field">
        <span>年级/班级</span>
        <n-input v-model:value="gradeFilter" clearable placeholder="如 2022 / 1 班" style="width: 170px" @keyup.enter="search" />
      </label>
    </FilterBar>

    <DataPanel
      title="学生列表"
      :columns="columns"
      :data="records"
      :total="studentTotal"
      :loading="loading"
      :error="loadError"
      remote
      :page="page"
      :page-size="size"
      :pagination="tablePagination"
      empty-title="暂无学生数据"
      empty-description="当前筛选条件下没有学生记录。"
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
      @refresh="loadStudents"
    >
      <template #actions>
        <n-button v-if="canEdit" type="primary" size="small" :disabled="writeBlocked || !optionsReady" :loading="optionsLoading" @click="openDrawer()">
          <template #icon>
            <n-icon :component="PersonAddOutline" />
          </template>
          新增学生
        </n-button>
      </template>
      <template #emptyAction>
        <n-button type="primary" @click="goImport">去导入</n-button>
      </template>
    </DataPanel>

    <StudentDrawer
      ref="drawerRef"
      :college-options="collegeOptions"
      :gender-options="genderOptions"
      :id-card-type-options="idCardTypeOptions"
      :identity-type-options="identityTypeOptions"
      @saved="loadStudents"
    />

    <n-drawer v-model:show="detailVisible" width="min(var(--overlay-medium), var(--overlay-drawer-max))">
      <n-drawer-content title="学生详情" closable>
        <DetailPanel v-if="selectedStudent" :items="detailItems" :columns="detailColumns" />
      </n-drawer-content>
    </n-drawer>

    <ReviewDialog
      v-model:show="reviewVisible"
      :title="reviewing?.stage === 'first' ? '学生信息初审' : '学生信息复审'"
      :loading="reviewSaving"
      allow-fail
      :summary="reviewing ? [
        { label: '学号', value: reviewing.student.studentNo },
        { label: '姓名', value: reviewing.student.name },
        { label: '当前状态', status: reviewing.student.status }
      ] : []"
      @submit="saveReview"
    />
  </PageContainer>
</template>
