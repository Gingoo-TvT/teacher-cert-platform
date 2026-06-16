<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NSpace, NTag, useMessage, type DataTableColumns, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import RegionCascader from '@/components/RegionCascader.vue'
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

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const reviewVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)
const reviewing = ref<{ student: Student; stage: 'first' | 'second' } | null>(null)
const keyword = ref('')
const statusFilter = ref<string | null>(null)
const records = ref<Student[]>([])
const colleges = ref<College[]>([])
const genders = ref<DictItem[]>([])
const idCardTypes = ref<DictItem[]>([])
const identityTypes = ref<DictItem[]>([])

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

const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))
const genderOptions = computed<SelectOption[]>(() => dictOptions(genders.value))
const idCardTypeOptions = computed<SelectOption[]>(() => dictOptions(idCardTypes.value))
const identityTypeOptions = computed<SelectOption[]>(() => dictOptions(identityTypes.value))

const columns: DataTableColumns<Student> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'name', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '证件号', key: 'idCardNo', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '出生日期', key: 'birthDate', width: 110 },
  { title: '生源地', key: 'sourceFull', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row) },
  { title: '锁定', key: 'locked', width: 76, render: (row) => (row.locked ? h(NTag, { type: 'warning', size: 'small' }, { default: () => '锁定' }) : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 300,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openDrawer(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => submit(row) }, { default: () => '提交' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'first') }, { default: () => '初审' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openReview(row, 'second') }, { default: () => '复审' }),
        h(
          NPopconfirm,
          { onPositiveClick: () => remove(row) },
          {
            trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除该学生？'
          }
        )
      ])
  }
]

async function loadStudents() {
  loading.value = true
  try {
    const res = await listStudents({ keyword: keyword.value, status: statusFilter.value })
    records.value = res.data.records
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
  } finally {
    saving.value = false
  }
}

async function submit(row: Student) {
  await submitStudent(row.id)
  message.success('已提交')
  await loadStudents()
}

async function remove(row: Student) {
  await deleteStudent(row.id)
  message.success('已删除')
  await loadStudents()
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
  if (reviewing.value.stage === 'first') await firstReviewStudent(reviewing.value.student.id, reviewForm)
  else await secondReviewStudent(reviewing.value.student.id, reviewForm)
  message.success('审核完成')
  reviewVisible.value = false
  await loadStudents()
}

function handleRegionChange(payload: { codes: string[]; fullName: string } | null) {
  form.sourceProvince = payload?.codes[0] || null
  form.sourceCity = payload?.codes[1] || null
  form.sourceCounty = payload?.codes[2] || null
  form.sourceFull = payload?.fullName || ''
}

function dictOptions(items: DictItem[]): SelectOption[] {
  return items.map((item) => ({ label: item.itemValue, value: item.itemCode }))
}

function statusTag(row: Student) {
  const type = row.status === 'PASSED' ? 'success' : row.status === 'FAILED' ? 'error' : row.status.includes('REJECTED') ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => row.statusLabel })
}

onMounted(async () => {
  await loadOptions()
  await loadStudents()
})
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between" align="center">
      <n-space>
        <n-input v-model:value="keyword" clearable placeholder="学号/姓名" style="width: 220px" @keyup.enter="loadStudents" />
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 150px" />
        <n-button type="primary" @click="loadStudents">查询</n-button>
      </n-space>
      <n-button type="primary" @click="openDrawer()">新增学生</n-button>
    </n-space>
    <n-data-table :columns="columns" :data="records" :loading="loading" :row-key="(row: Student) => row.id" :scroll-x="1220" />
  </n-space>

  <n-drawer v-model:show="drawerVisible" :width="520">
    <n-drawer-content :title="editingId ? '编辑学生' : '新增学生'" closable>
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学号" path="studentNo"><n-input v-model:value="form.studentNo" /></n-form-item-gi>
          <n-form-item-gi label="姓名" path="name"><n-input v-model:value="form.name" /></n-form-item-gi>
          <n-form-item-gi label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item-gi>
          <n-form-item-gi label="身份类型" path="identityType"><n-select v-model:value="form.identityType" :options="identityTypeOptions" /></n-form-item-gi>
          <n-form-item-gi label="证件类型" path="idCardType"><n-select v-model:value="form.idCardType" :options="idCardTypeOptions" /></n-form-item-gi>
          <n-form-item-gi label="证件号码" path="idCardNo"><n-input v-model:value="form.idCardNo" /></n-form-item-gi>
          <n-form-item-gi label="出生日期" path="birthDate"><n-input v-model:value="form.birthDate" placeholder="1990/6/28" /></n-form-item-gi>
          <n-form-item-gi label="学院" path="collegeId"><n-select v-model:value="form.collegeId" :options="collegeOptions" /></n-form-item-gi>
          <n-form-item-gi label="年级"><n-input v-model:value="form.grade" /></n-form-item-gi>
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
</template>
