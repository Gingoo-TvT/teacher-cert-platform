<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { confirmStudent, getStudent, submitStudent, type Student, type StudentPayload } from '@/api/student'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const loadError = ref('')
const optionsLoading = ref(false)
const optionsError = ref('')
const saving = ref(false)
const submitting = ref(false)
const formRef = ref<FormInst | null>(null)
const student = ref<Student | null>(null)
const genders = ref<DictItem[]>([])
const idCardTypes = ref<DictItem[]>([])
const identityTypes = ref<DictItem[]>([])
type ProfileState = 'loading' | 'ready' | 'unbound' | 'missing' | 'error'
const profileState = ref<ProfileState>('loading')

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
const actionBusy = computed(() => saving.value || submitting.value)
const optionsReady = computed(() => !optionsLoading.value && !optionsError.value)
const profileReady = computed(() => profileState.value === 'ready' && Boolean(student.value) && !loadError.value)
const canPersist = computed(() => profileReady.value && optionsReady.value && !locked.value)
const canSave = computed(() => canPersist.value && !actionBusy.value)
const canSubmit = computed(() => canPersist.value && !actionBusy.value)
const sensitiveValuesMasked = computed(() => {
  const current = student.value
  return Boolean(current && (current.idCardNo.includes('*') || current.birthDate.includes('*')))
})
const statusText = computed(() => student.value?.statusLabel || student.value?.status || '未建档')
const reviewComment = computed(() => student.value?.firstReviewComment || student.value?.secondReviewComment || '')
const genderOptions = computed<SelectOption[]>(() => dictOptions(genders.value))
const idCardTypeOptions = computed<SelectOption[]>(() => dictOptions(idCardTypes.value))
const identityTypeOptions = computed<SelectOption[]>(() => dictOptions(identityTypes.value))

async function load() {
  const studentId = userStore.currentUser?.studentId
  const hasPreviousProfile = profileState.value === 'ready' && Boolean(student.value)
  loadError.value = ''
  if (!studentId) {
    student.value = null
    profileState.value = 'unbound'
    return
  }
  loading.value = true
  if (!hasPreviousProfile) profileState.value = 'loading'
  try {
    const res = await getStudent(studentId)
    student.value = res.data || null
    if (!student.value) {
      profileState.value = 'missing'
    } else {
      fillForm(student.value)
      profileState.value = 'ready'
    }
  } catch (error) {
    if (isMissingStudent(error)) {
      student.value = null
      profileState.value = 'missing'
    } else {
      loadError.value = showError(error, '本人信息加载失败')
      profileState.value = hasPreviousProfile ? 'ready' : 'error'
    }
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const [genderRes, idTypeRes, identityRes] = await Promise.all([
      listDictItems('gender', true),
      listDictItems('id_card_type', true),
      listDictItems('identity_type', true)
    ])
    genders.value = genderRes.data
    idCardTypes.value = idTypeRes.data
    identityTypes.value = identityRes.data
  } catch (error) {
    optionsError.value = showError(error, '本人信息选项加载失败')
  } finally {
    optionsLoading.value = false
  }
}

async function reloadPage() {
  await Promise.all([loadOptions(), load()])
}

function fillForm(row: Student) {
  Object.assign(form, {
    studentNo: row.studentNo,
    name: row.name,
    gender: row.gender,
    idCardType: row.idCardType,
    idCardNo: row.idCardNo.includes('*') ? '' : row.idCardNo,
    birthDate: row.birthDate.includes('*') ? '' : row.birthDate,
    identityType: row.identityType,
    sourceProvince: row.sourceProvince || null,
    sourceCity: row.sourceCity || null,
    sourceCounty: row.sourceCounty || null,
    sourceFull: row.sourceFull || '',
    collegeId: row.collegeId,
    grade: row.grade || '',
    className: row.className || ''
  })
}

async function save() {
  if (!canPersist.value || actionBusy.value || !formRef.value) return
  saving.value = true
  try {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    await confirmStudent(form)
    message.success('本人信息已保存')
    await load()
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (!canPersist.value || actionBusy.value || !student.value) return
  submitting.value = true
  try {
    await submitStudent(student.value.id)
    message.success('已提交审核')
    await load()
  } catch (error) {
    showError(error, '提交失败')
  } finally {
    submitting.value = false
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

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  const text = detail || fallback
  message.error(text)
  return text
}

function isMissingStudent(error: unknown) {
  return error instanceof Error && error.message === '学生不存在'
}

onMounted(() => {
  void reloadPage()
})
</script>

<template>
  <PageContainer title="本人基本信息" description="核对并确认本人基本信息；证书生成后关键字段锁定。">
    <template #actions>
      <n-space>
        <n-button secondary :loading="loading || optionsLoading" @click="reloadPage">刷新</n-button>
        <n-button :disabled="!canSubmit" :loading="submitting" @click="submit">提交审核</n-button>
        <n-button type="primary" :disabled="!canSave" :loading="saving" @click="save">保存确认</n-button>
      </n-space>
    </template>

    <n-spin :show="loading || optionsLoading">
      <n-space vertical size="large">
        <n-result
          v-if="profileState === 'error'"
          status="error"
          title="本人信息加载失败"
          :description="loadError"
          class="page-section"
        >
          <template #footer>
            <n-button type="primary" :loading="loading || optionsLoading" @click="reloadPage">重试</n-button>
          </template>
        </n-result>

        <n-result
          v-else-if="profileState === 'unbound'"
          status="warning"
          title="账号未绑定学生档案"
          description="当前账号没有关联学生记录，请联系管理员完成账号与学生档案绑定。"
          class="page-section"
        />

        <n-result
          v-else-if="profileState === 'missing'"
          status="info"
          title="暂无学生档案"
          description="账号已关联学生编号，但未找到对应档案，请联系管理员核对。"
          class="page-section"
        />

        <template v-else-if="profileState === 'ready'">
          <n-alert v-if="loadError" type="error" title="本人信息刷新失败" :bordered="false" class="page-section" role="alert">
            {{ loadError }}。以下仍显示上次成功加载的档案，保存和提交暂不可用。
            <n-button text type="error" size="small" :loading="loading" @click="load">重试</n-button>
          </n-alert>

          <n-alert v-if="optionsError" type="warning" :bordered="false" class="page-section">
            本人档案已加载，但表单选项加载失败；保存和提交暂不可用。{{ optionsError }}
            <n-button text type="warning" size="small" :loading="optionsLoading" @click="loadOptions">重试加载选项</n-button>
          </n-alert>

          <n-card :bordered="false" size="small" class="page-section">
          <n-space align="center" :size="10" wrap>
            <StatusTag :text="statusText" />
            <StatusTag v-if="locked" text="已锁定" />
            <span v-if="student" class="mono">{{ student.studentNo }}</span>
            <span v-if="student">{{ student.name }}</span>
          </n-space>
          </n-card>

          <n-alert v-if="locked" type="warning" :bordered="false">
            证书生成后姓名、证件号、身份类型等关键字段已锁定，需走受控更正流程。
          </n-alert>

          <n-alert v-else-if="sensitiveValuesMasked" type="info" :bordered="false">
            证件号和出生日期按默认策略脱敏显示，保存前请重新录入完整值。
          </n-alert>

          <n-alert v-if="reviewComment" type="warning" :bordered="false">
            {{ reviewComment }}
          </n-alert>

          <n-card :bordered="false" class="page-section">
          <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-grid :cols="2" :x-gap="16" responsive="screen">
              <n-form-item-gi label="学号" path="studentNo">
                <n-input v-model:value="form.studentNo" disabled class="mono-input" />
              </n-form-item-gi>
              <n-form-item-gi label="姓名" path="name">
                <n-input v-model:value="form.name" :disabled="locked" />
              </n-form-item-gi>
              <n-form-item-gi label="性别" path="gender">
                <n-select v-model:value="form.gender" :options="genderOptions" :disabled="locked" />
              </n-form-item-gi>
              <n-form-item-gi label="身份类型" path="identityType">
                <n-select v-model:value="form.identityType" :options="identityTypeOptions" :disabled="locked" />
              </n-form-item-gi>
              <n-form-item-gi label="证件类型" path="idCardType">
                <n-select v-model:value="form.idCardType" :options="idCardTypeOptions" :disabled="locked" />
              </n-form-item-gi>
              <n-form-item-gi label="证件号码" path="idCardNo">
                <n-input v-model:value="form.idCardNo" :disabled="locked" class="mono-input" />
              </n-form-item-gi>
              <n-form-item-gi label="出生日期" path="birthDate">
                <n-input v-model:value="form.birthDate" :disabled="locked" placeholder="2000/12/31" class="mono-input" />
              </n-form-item-gi>
              <n-form-item-gi label="班级">
                <n-input v-model:value="form.className" :disabled="locked" />
              </n-form-item-gi>
              <n-form-item-gi label="生源地">
                <RegionCascader :value="form.sourceCounty" :disabled="locked" @change="handleRegionChange" />
              </n-form-item-gi>
              <n-form-item-gi label="生源地文本">
                <n-input :value="form.sourceFull || '-'" disabled />
              </n-form-item-gi>
            </n-grid>
          </n-form>

          <n-divider />

          <n-descriptions :column="2" size="small" bordered>
            <n-descriptions-item label="性别">{{ dictLabel(genders, form.gender) }}</n-descriptions-item>
            <n-descriptions-item label="证件类型">{{ dictLabel(idCardTypes, form.idCardType) }}</n-descriptions-item>
            <n-descriptions-item label="身份类型">{{ dictLabel(identityTypes, form.identityType) }}</n-descriptions-item>
            <n-descriptions-item label="年级/班级">{{ [form.grade, form.className].filter(Boolean).join(' / ') || '-' }}</n-descriptions-item>
          </n-descriptions>
          </n-card>
        </template>
      </n-space>
    </n-spin>
  </PageContainer>
</template>

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
