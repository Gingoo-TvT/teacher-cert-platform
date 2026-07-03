<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import {
  createStudent,
  updateStudent,
  type Student,
  type StudentPayload
} from '@/api/student'

defineProps<{
  collegeOptions: SelectOption[]
  genderOptions: SelectOption[]
  idCardTypeOptions: SelectOption[]
  identityTypeOptions: SelectOption[]
}>()

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const message = useMessage()

const saving = ref(false)
const drawerVisible = ref(false)
const formRef = ref<FormInst | null>(null)
const editingId = ref<string | null>(null)

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

function open(row?: Student) {
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
    emit('saved')
  } catch (error) {
    showError(error, '学生保存失败')
  } finally {
    saving.value = false
  }
}

function handleRegionChange(payload: RegionSelection | null) {
  form.sourceProvince = payload?.codes[0] || null
  form.sourceCity = payload?.codes[1] || null
  form.sourceCounty = payload?.codes[2] || null
  form.sourceFull = payload?.fullName || ''
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
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
