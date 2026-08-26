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
  if (saving.value) return
  editingId.value = row?.id || null
  Object.assign(form, {
    studentNo: row?.studentNo || '',
    name: row?.name || '',
    gender: row?.gender || '',
    idCardType: row?.idCardType || '',
    idCardNo: row?.idCardNo?.includes('*') ? '' : row?.idCardNo || '',
    birthDate: row?.birthDate?.includes('*') ? '' : row?.birthDate || '',
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
  if (saving.value) return
  saving.value = true
  try {
    try {
      await formRef.value?.validate()
    } catch {
      // 表单校验失败由字段反馈承接，不应泄漏为页面未处理异常或误报保存失败。
      return
    }
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
  <n-drawer
    v-model:show="drawerVisible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content :title="editingId ? '编辑学生' : '新增学生'" :closable="!saving">
      <n-alert v-if="editingId" type="info" :bordered="false" class="page-section">
        如证件号或出生日期已脱敏显示，请重新录入完整证件号和出生日期后保存。
      </n-alert>
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" :disabled="saving">
        <div class="form-section-title">基本信息</div>
        <n-grid cols="1 480:2" responsive="self" :x-gap="16">
          <n-form-item-gi label="学号" path="studentNo"><n-input v-model:value="form.studentNo" /></n-form-item-gi>
          <n-form-item-gi label="姓名" path="name"><n-input v-model:value="form.name" /></n-form-item-gi>
          <n-form-item-gi label="性别" path="gender"><n-select v-model:value="form.gender" :options="genderOptions" /></n-form-item-gi>
          <n-form-item-gi label="身份类型" path="identityType"><n-select v-model:value="form.identityType" :options="identityTypeOptions" /></n-form-item-gi>
          <n-form-item-gi label="证件类型" path="idCardType"><n-select v-model:value="form.idCardType" :options="idCardTypeOptions" /></n-form-item-gi>
          <n-form-item-gi label="证件号码" path="idCardNo"><n-input v-model:value="form.idCardNo" /></n-form-item-gi>
          <n-form-item-gi label="出生日期" path="birthDate"><n-input v-model:value="form.birthDate" placeholder="2000/12/31" /></n-form-item-gi>
        </n-grid>
        <div class="form-section-title">就读信息</div>
        <n-grid cols="1 480:2" responsive="self" :x-gap="16">
          <n-form-item-gi label="学院" path="collegeId"><n-select v-model:value="form.collegeId" filterable :options="collegeOptions" /></n-form-item-gi>
          <n-form-item-gi label="年级"><n-input v-model:value="form.grade" placeholder="如 2022" /></n-form-item-gi>
          <n-form-item-gi label="班级"><n-input v-model:value="form.className" /></n-form-item-gi>
        </n-grid>
        <div class="form-section-title">生源信息</div>
        <n-form-item label="生源地">
          <RegionCascader :value="form.sourceCounty" :disabled="saving" @change="handleRegionChange" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="student-drawer__footer">
          <n-button :disabled="saving" @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </div>
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

.student-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  width: 100%;
}

@media (max-width: 480px) {
  .student-drawer__footer {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-drawer__footer .n-button {
    width: 100%;
  }
}
</style>
