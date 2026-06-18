<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type SelectOption
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import { renderTableActions } from '@/utils/tableActions'
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
import { useYearStore } from '@/stores/year'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const reviewing = ref<{ student: Student; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const gradeFilter = ref(yearStore.assessmentYear)
const collegeFilter = ref<string | null>(null)
const records = ref<Student[]>([])
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
  grade: yearStore.assessmentYear,
  className: ''
})

const reviewForm = reactive<ReviewPayload>({
  action: 'PASS',
  comment: ''
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

const reviewActionOptions: SelectOption[] = [
  { label: '通过', value: 'PASS' },
  { label: '退回', value: 'REJECT' },
  { label: '不通过', value: 'FAIL' }
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

const columns: DataTableColumns<Student> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo) },
  { title: '姓名', key: 'name', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '性别', key: 'gender', width: 80, render: (row) => dictLabel(genders.value, row.gender) },
  { title: '身份类型', key: 'identityType', minWidth: 140, ellipsis: { tooltip: true }, render: (row) => dictLabel(identityTypes.value, row.identityType) },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.idCardNo) },
  { title: '生源地', key: 'sourceFull', minWidth: 170, ellipsis: { tooltip: true } },
  { title: '年级/班级', key: 'grade', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => [row.grade, row.className].filter(Boolean).join(' / ') || '-' },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { text: row.statusLabel || row.status }) },
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
    grade: row?.grade || yearStore.assessmentYear,
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
  try {
    if (reviewing.value.stage === 'first') await firstReviewStudent(reviewing.value.student.id, reviewForm)
    else await secondReviewStudent(reviewing.value.student.id, reviewForm)
    message.success('审核完成')
    reviewVisible.value = false
    await loadStudents()
  } catch (error) {
    showError(error, '审核失败')
  }
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

watch(
  () => yearStore.assessmentYear,
  (year) => {
    gradeFilter.value = year
    if (!drawerVisible.value) form.grade = year
  }
)
</script>

<template>
  <PageContainer title="学生基本信息" description="列表支持关键词、状态、学院与年级筛选；初审由教务员执行，复审由学院负责人执行。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadStudents">刷新</n-button>
        <n-button v-if="canEdit" type="primary" @click="openDrawer()">新增学生</n-button>
      </n-space>
    </template>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-input v-model:value="keyword" clearable placeholder="学号 / 姓名" style="width: 220px" @keyup.enter="loadStudents" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 150px" />
        <n-select v-model:value="collegeFilter" clearable filterable :options="collegeOptions" placeholder="学院" style="width: 200px" />
        <n-input v-model:value="gradeFilter" clearable placeholder="学年/年级/班级" style="width: 160px" />
        <n-button type="primary" @click="loadStudents">查询</n-button>
      </n-space>
    </n-card>

    <n-data-table
      :columns="columns"
      :data="filteredRecords"
      :loading="loading"
      :row-key="(row: Student) => row.id"
      :scroll-x="1440"
      :pagination="{ pageSize: 10 }"
      striped
    />

    <n-drawer v-model:show="drawerVisible" :width="620">
      <n-drawer-content :title="editingId ? '编辑学生' : '新增学生'" closable>
        <n-alert v-if="editingId" type="info" :bordered="false" class="page-section">
          如证件号已脱敏显示，请重新录入完整证件号后保存。
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
          <n-grid :cols="2" :x-gap="12">
            <n-form-item-gi label="学号" path="studentNo"><n-input v-model:value="form.studentNo" /></n-form-item-gi>
            <n-form-item-gi label="姓名" path="name"><n-input v-model:value="form.name" /></n-form-item-gi>
            <n-form-item-gi label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item-gi>
            <n-form-item-gi label="身份类型" path="identityType"><n-select v-model:value="form.identityType" :options="identityTypeOptions" /></n-form-item-gi>
            <n-form-item-gi label="证件类型" path="idCardType"><n-select v-model:value="form.idCardType" :options="idCardTypeOptions" /></n-form-item-gi>
            <n-form-item-gi label="证件号码" path="idCardNo"><n-input v-model:value="form.idCardNo" /></n-form-item-gi>
            <n-form-item-gi label="出生日期" path="birthDate"><n-input v-model:value="form.birthDate" placeholder="2000/12/31" /></n-form-item-gi>
            <n-form-item-gi label="学院" path="collegeId"><n-select v-model:value="form.collegeId" filterable :options="collegeOptions" /></n-form-item-gi>
            <n-form-item-gi label="年级"><n-input v-model:value="form.grade" placeholder="2026" /></n-form-item-gi>
            <n-form-item-gi label="班级"><n-input v-model:value="form.className" /></n-form-item-gi>
          </n-grid>
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

    <n-drawer v-model:show="detailVisible" :width="520">
      <n-drawer-content title="学生详情" closable>
        <n-descriptions v-if="selectedStudent" bordered :column="1" size="small">
          <n-descriptions-item label="学号"><span class="mono">{{ selectedStudent.studentNo }}</span></n-descriptions-item>
          <n-descriptions-item label="姓名">{{ selectedStudent.name }}</n-descriptions-item>
          <n-descriptions-item label="性别">{{ dictLabel(genders, selectedStudent.gender) }}</n-descriptions-item>
          <n-descriptions-item label="身份类型">{{ dictLabel(identityTypes, selectedStudent.identityType) }}</n-descriptions-item>
          <n-descriptions-item label="证件类型">{{ dictLabel(idCardTypes, selectedStudent.idCardType) }}</n-descriptions-item>
          <n-descriptions-item label="证件号"><span class="mono">{{ selectedStudent.idCardNo }}</span></n-descriptions-item>
          <n-descriptions-item label="出生日期"><span class="mono">{{ selectedStudent.birthDate }}</span></n-descriptions-item>
          <n-descriptions-item label="学院">{{ collegeName(selectedStudent.collegeId) }}</n-descriptions-item>
          <n-descriptions-item label="生源地">{{ selectedStudent.sourceFull || '-' }}</n-descriptions-item>
          <n-descriptions-item label="状态"><StatusTag :text="selectedStudent.statusLabel || selectedStudent.status" /></n-descriptions-item>
          <n-descriptions-item label="初审意见">{{ selectedStudent.firstReviewComment || '-' }}</n-descriptions-item>
          <n-descriptions-item label="复审意见">{{ selectedStudent.secondReviewComment || '-' }}</n-descriptions-item>
        </n-descriptions>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="reviewVisible" preset="dialog" :title="reviewing?.stage === 'first' ? '学生信息初审' : '学生信息复审'">
      <n-space vertical>
        <n-alert v-if="reviewing" type="info" :bordered="false">
          {{ reviewing.student.studentNo }} / {{ reviewing.student.name }} / 当前状态：{{ reviewing.student.statusLabel }}
        </n-alert>
        <n-select v-model:value="reviewForm.action" :options="reviewActionOptions" />
        <n-input v-model:value="reviewForm.comment" type="textarea" placeholder="退回或不通过必须填写原因" />
        <n-space justify="end">
          <n-button @click="reviewVisible = false">取消</n-button>
          <n-button type="primary" @click="saveReview">确认</n-button>
        </n-space>
      </n-space>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}
</style>
