<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import RegionCascader from '@/components/RegionCascader.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { confirmStudent, listStudents, submitStudent, type Student, type StudentPayload } from '@/api/student'

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInst | null>(null)
const student = ref<Student | null>(null)
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

const rules: FormRules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: ['blur', 'input'] }],
  name: [{ required: true, message: '请输入姓名', trigger: ['blur', 'input'] }],
  gender: [{ required: true, message: '请选择性别', trigger: ['change'] }],
  idCardType: [{ required: true, message: '请选择证件类型', trigger: ['change'] }],
  idCardNo: [{ required: true, message: '请输入证件号码', trigger: ['blur', 'input'] }],
  birthDate: [{ required: true, message: '请输入出生日期', trigger: ['blur', 'input'] }],
  identityType: [{ required: true, message: '请选择身份类型', trigger: ['change'] }]
}

const locked = computed(() => student.value?.locked === 1)
const genderOptions = computed<SelectOption[]>(() => dictOptions(genders.value))
const idCardTypeOptions = computed<SelectOption[]>(() => dictOptions(idCardTypes.value))
const identityTypeOptions = computed<SelectOption[]>(() => dictOptions(identityTypes.value))

async function load() {
  loading.value = true
  try {
    const res = await listStudents()
    student.value = res.data.records[0] || null
    if (student.value) {
      Object.assign(form, {
        studentNo: student.value.studentNo,
        name: student.value.name,
        gender: student.value.gender,
        idCardType: student.value.idCardType,
        idCardNo: student.value.idCardNo.includes('*') ? '' : student.value.idCardNo,
        birthDate: student.value.birthDate,
        identityType: student.value.identityType,
        sourceProvince: student.value.sourceProvince || null,
        sourceCity: student.value.sourceCity || null,
        sourceCounty: student.value.sourceCounty || null,
        sourceFull: student.value.sourceFull || '',
        collegeId: student.value.collegeId,
        grade: student.value.grade || '',
        className: student.value.className || ''
      })
    }
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [genderRes, idTypeRes, identityRes] = await Promise.all([
    listDictItems('gender', true),
    listDictItems('id_card_type', true),
    listDictItems('identity_type', true)
  ])
  genders.value = genderRes.data
  idCardTypes.value = idTypeRes.data
  identityTypes.value = identityRes.data
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await confirmStudent(form)
    message.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (!student.value) return
  await submitStudent(student.value.id)
  message.success('已提交')
  await load()
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

onMounted(async () => {
  await loadOptions()
  await load()
})
</script>

<template>
  <n-spin :show="loading">
    <n-space vertical size="large">
      <n-space align="center">
        <n-h2 style="margin: 0">本人基本信息</n-h2>
        <n-tag v-if="student" :type="student.status === 'PASSED' ? 'success' : 'info'" bordered="false">{{ student.statusLabel }}</n-tag>
        <n-tag v-if="locked" type="warning" bordered="false">关键字段已锁定</n-tag>
      </n-space>

      <n-alert v-if="student?.firstReviewComment || student?.secondReviewComment" type="warning">
        {{ student.firstReviewComment || student.secondReviewComment }}
      </n-alert>

      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" style="max-width: 880px">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="学号" path="studentNo"><n-input v-model:value="form.studentNo" disabled /></n-form-item-gi>
          <n-form-item-gi label="姓名" path="name"><n-input v-model:value="form.name" :disabled="locked" /></n-form-item-gi>
          <n-form-item-gi label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item-gi>
          <n-form-item-gi label="身份类型" path="identityType"><n-select v-model:value="form.identityType" :options="identityTypeOptions" :disabled="locked" /></n-form-item-gi>
          <n-form-item-gi label="证件类型" path="idCardType"><n-select v-model:value="form.idCardType" :options="idCardTypeOptions" :disabled="locked" /></n-form-item-gi>
          <n-form-item-gi label="证件号码" path="idCardNo"><n-input v-model:value="form.idCardNo" :disabled="locked" /></n-form-item-gi>
          <n-form-item-gi label="出生日期" path="birthDate"><n-input v-model:value="form.birthDate" :disabled="locked" /></n-form-item-gi>
          <n-form-item-gi label="班级"><n-input v-model:value="form.className" /></n-form-item-gi>
        </n-grid>
        <n-form-item label="生源地">
          <RegionCascader :value="form.sourceCounty" @change="handleRegionChange" />
        </n-form-item>
      </n-form>

      <n-space>
        <n-button type="primary" :loading="saving" :disabled="locked" @click="save">保存确认</n-button>
        <n-button :disabled="!student || locked" @click="submit">提交审核</n-button>
      </n-space>
    </n-space>
  </n-spin>
</template>
