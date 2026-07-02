<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
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
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import { renderTableActions } from '@/utils/tableActions'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { listColleges, type College } from '@/api/organization'
import {
  createStudent,
  deleteStudent,
  firstReviewStudent,
  listStudents,
  secondReviewStudent,
  submitStudent,
  updateStudent,
  type ReviewPayload,
  type Student,
  type StudentPayload
} from '@/api/student'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const reviewSaving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const reviewing = ref<{ student: Student; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const gradeFilter = ref('')
const collegeFilter = ref<string | null>(null)
const records = ref<Student[]>([])
const studentTotal = ref(0)
const colleges = ref<College[]>([])
const genders = ref<DictItem[]>([])
const idCardTypes = ref<DictItem[]>([])
const identityTypes = ref<DictItem[]>([])
const selectedStudent = ref<Student | null>(null)

const form = reactive<StudentPayload>({
  studentNo: '',
  name: '',
  gender: '',
  idCardType: '',
  idCardNo: '',
  birthDate: '',
  identityType: '',
  sourceProvince: null,
  sourceCity: null,
  sourceCounty: null,
  sourceFull: '',
  collegeId: '',
  grade: '',
  className: ''
})

const rules: FormRules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入姓名', trigger: ['blur', 'input'] }],
  gender: [{ required: true, message: '请选择性别', trigger: ['change'] }],
  idCardType: [{ required: true, message: '请选择证件类型', trigger: ['change'] }],
  idCardNo: [{ required: true, message: '请输入证件号码', trigger: ['blur', 'input'] }],
  birthDate: [{ required: true, message: '请输入出生日期', trigger: ['blur', 'input'] }],
  identityType: [{ required: true, message: '请选择身份类型', trigger: ['change'] }],
  collegeId: [{ required: true, message: '请选择学院', trigger: ['change'] }]
}

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

const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))
const genderOptions = computed<SelectOption[]>(() => dictOptions(genders.value))
const idCardTypeOptions = computed<SelectOption[]>(() => dictOptions(idCardTypes.value))
const identityTypeOptions = computed<SelectOption[]>(() => dictOptions(identityTypes.value))

const filteredRecords = computed(() => {
  const grade = gradeFilter.value.trim()
  if (!grade) return records.value
  return records.value.filter((row) => [row.grade || '', row.className || ''].some((text) => text.includes(grade)))
})
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
  { title: '生源地', key: 'sourceFull', minWidth: 170, ellipsis: { tooltip: true } },
  { title: '年级/班级', key: 'grade', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => [row.grade, row.className].filter(Boolean).join(' / ') || '-' },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  { title: '锁定', key: 'locked', width: 76, render: (row) => h(StatusTag, { text: row.locked ? '已锁定' : '未锁定' }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 240,
    render: (row) =>
      {
        const actions = [
          h(NButton, { size: 'small', type: 'primary', onClick: () => openDetail(row) }, { default: () => '详情' })
        ]
        if (canEdit.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openDrawer(row) }, { default: () => '编辑' }))
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }))
        }
        if (canFirstReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }))
        }
        if (canSecondReview.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' }))
        }
        if (canEdit.value) {
          actions.push(
            h(
              NPopconfirm,
              { onPositiveClick: () => remove(row) },
              {
                trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
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
  loading.value = true
  try {
    const res = await listStudents({
      keyword: keyword.value,
      status: statusFilter.value,
      collegeId: collegeFilter.value
    })
    records.value = res.data.records
    studentTotal.value = res.data.total
  } catch (error) {
    showError(error, '学生列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [collegeRes, genderRes, idTypeRes, identityRes] = await Promise.all([
    listColleges(),
    listDictItems('gender', true),
    listDictItems('id_card_type', true),
    listDictItems('identity_type', true)
  ])
  colleges.value = collegeRes.data
  genders.value = genderRes.data
  idCardTypes.value = idTypeRes.data
  identityTypes.value = identityRes.data
}

function openDetail(row: Student) {
  selectedStudent.value = row
  detailVisible.value = true
}

function openDrawer(row?: Student) {
  editingId.value = row?.id || null
  Object.assign(form, {
    studentNo: row?.studentNo || '',
    name: row?.name || '',
    gender: row?.gender || '',
    idCardType: row?.idCardType || '',
    idCardNo: row?.idCardNo?.includes('*') ? '' : row?.idCardNo || '',
    birthDate: row?.birthDate || '',
    identityType: row?.identityType || '',
    sourceProvince: row?.sourceProvince || null,
    sourceCity: row?.sourceCity || null,
    sourceCounty: row?.sourceCounty || null,
    sourceFull: row?.sourceFull || '',
    collegeId: row?.collegeId || '',
    grade: row?.grade || '',
    className: row?.className || ''
  })
  drawerVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await updateStudent(editingId.value, form)
    else await createStudent(form)
    message.success('已保存')
    drawerVisible.value = false
    await loadStudents()
  } catch (error) {
    showError(error, '学生保存失败')
  } finally {
    saving.value = false
  }
}

async function submit(row: Student) {
  try {
    await submitStudent(row.id)
    message.success('已提交')
    await loadStudents()
  } catch (error) {
    showError(error, '提交失败')
  }
}

async function remove(row: Student) {
  try {
    await deleteStudent(row.id)
    message.success('已删除')
    await loadStudents()
  } catch (error) {
    showError(error, '删除失败')
  }
}

function openReview(row: Student, stage: 'first' | 'second') {
  reviewing.value = { student: row, stage }
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
  void loadStudents()
}

function goImport() {
  void router.push({ name: 'exchangeImport' })
}

function handleRegionChange(payload: RegionSelection | null) {
  form.sourceProvince = payload?.codes[0] || null
  form.sourceCity = payload?.codes[1] || null
  form.sourceCounty = payload?.codes[2] || null
  form.sourceFull = payload?.fullName || ''
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
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadStudents()
})
</script>

<template>
  <PageContainer title="学生基本信息" description="学生基本信息查询、初审与复审。">
    <FilterBar :loading="loading" @submit="loadStudents" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学号 / 姓名" style="width: 220px" @keyup.enter="loadStudents" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
      <label class="filter-field">
        <span>学院</span>
        <n-select v-model:value="collegeFilter" clearable filterable :options="collegeOptions" placeholder="全部学院" style="width: 220px" />
      </label>
      <label class="filter-field">
        <span>年级/班级</span>
        <n-input v-model:value="gradeFilter" clearable placeholder="如 2022 / 1 班" style="width: 170px" />
      </label>
    </FilterBar>

    <DataPanel
      title="学生列表"
      :columns="columns"
      :data="filteredRecords"
      :total="gradeFilter ? filteredRecords.length : studentTotal"
      :loading="loading"
      :scroll-x="1440"
      empty-title="暂无学生数据"
      empty-description="当前筛选条件下没有学生记录。"
      @refresh="loadStudents"
    >
      <template #actions>
        <n-button v-if="canEdit" type="primary" size="small" @click="openDrawer()">
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

    <n-drawer v-model:show="drawerVisible" :width="560">
      <n-drawer-content :title="editingId ? '编辑学生' : '新增学生'" closable>
        <n-alert v-if="editingId" type="info" :bordered="false" class="page-section">
          如证件号已脱敏显示，请重新录入完整证件号后保存。
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
          <div class="form-section-title">基本信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学号" path="studentNo"><n-input v-model:value="form.studentNo" /></n-form-item-gi>
            <n-form-item-gi label="姓名" path="name"><n-input v-model:value="form.name" /></n-form-item-gi>
            <n-form-item-gi label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item-gi>
            <n-form-item-gi label="身份类型" path="identityType"><n-select v-model:value="form.identityType" :options="identityTypeOptions" /></n-form-item-gi>
            <n-form-item-gi label="证件类型" path="idCardType"><n-select v-model:value="form.idCardType" :options="idCardTypeOptions" /></n-form-item-gi>
            <n-form-item-gi label="证件号码" path="idCardNo"><n-input v-model:value="form.idCardNo" /></n-form-item-gi>
            <n-form-item-gi label="出生日期" path="birthDate"><n-input v-model:value="form.birthDate" placeholder="2000/12/31" /></n-form-item-gi>
          </n-grid>
          <div class="form-section-title">就读信息</div>
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学院" path="collegeId"><n-select v-model:value="form.collegeId" filterable :options="collegeOptions" /></n-form-item-gi>
            <n-form-item-gi label="年级"><n-input v-model:value="form.grade" placeholder="如 2022" /></n-form-item-gi>
            <n-form-item-gi label="班级"><n-input v-model:value="form.className" /></n-form-item-gi>
          </n-grid>
          <div class="form-section-title">生源信息</div>
          <n-form-item label="生源地">
            <RegionCascader :value="form.sourceCounty" @change="handleRegionChange" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="drawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="save">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="detailVisible" :width="620">
      <n-drawer-content title="学生详情" closable>
        <DetailPanel v-if="selectedStudent" :items="detailItems" :columns="2" />
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

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.form-section-title:first-child {
  margin-top: 0;
}
</style>
