<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import { createMajor, updateMajor, type College, type Major, type MajorPayload } from '@/api/organization'

const props = defineProps<{
  collegeOptions: SelectOption[]
  selectedCollege: College | null
  canCreateMajor: boolean
  defaultYearVersion: string
}>()

const emit = defineEmits<{
  saved: [majorId: string]
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const majorFormRef = ref<FormInst | null>(null)
const editingMajorId = ref<string | null>(null)

interface MajorFormState {
  collegeId: string | null
  internalMajorCode: string
  internalMajorName: string
  secondDisciplineCode: string
  secondDisciplineName: string
  pilotScopeFlag: number
  yearVersion: string
  sort: number
  status: number
}

const majorForm = reactive<MajorFormState>({
  collegeId: null,
  internalMajorCode: '',
  internalMajorName: '',
  secondDisciplineCode: '',
  secondDisciplineName: '',
  pilotScopeFlag: 0,
  yearVersion: 'GLOBAL',
  sort: 0,
  status: 1
})

const majorRules: FormRules = {
  collegeId: [{ required: true, message: '请选择学院', trigger: ['blur', 'change'] }],
  internalMajorCode: [{ required: true, message: '请输入校内专业代码', trigger: ['blur', 'input'] }],
  internalMajorName: [{ required: true, message: '请输入校内专业名称', trigger: ['blur', 'input'] }],
  yearVersion: [{ required: true, message: '请输入年度版本', trigger: ['blur', 'input'] }]
}

function open(row?: Major) {
  if (!row && !props.canCreateMajor) {
    message.warning(props.selectedCollege ? '无权新增专业或学院已停用' : '请选择启用学院')
    return
  }
  editingMajorId.value = row?.id || null
  majorForm.collegeId = row?.collegeId || props.selectedCollege?.id || null
  majorForm.internalMajorCode = row?.internalMajorCode || ''
  majorForm.internalMajorName = row?.internalMajorName || ''
  majorForm.secondDisciplineCode = row?.secondDisciplineCode || ''
  majorForm.secondDisciplineName = row?.secondDisciplineName || ''
  majorForm.pilotScopeFlag = row?.pilotScopeFlag || 0
  majorForm.yearVersion = row?.yearVersion || props.defaultYearVersion || 'GLOBAL'
  majorForm.sort = row?.sort || 0
  majorForm.status = row?.status ?? 1
  visible.value = true
}

async function saveMajor() {
  await majorFormRef.value?.validate()
  saving.value = true
  try {
    const payload: MajorPayload = {
      collegeId: majorForm.collegeId || '',
      internalMajorCode: majorForm.internalMajorCode.trim(),
      internalMajorName: majorForm.internalMajorName.trim(),
      secondDisciplineCode: cleanOptional(majorForm.secondDisciplineCode),
      secondDisciplineName: cleanOptional(majorForm.secondDisciplineName),
      pilotScopeFlag: majorForm.pilotScopeFlag ?? 0,
      yearVersion: cleanOptional(majorForm.yearVersion) || 'GLOBAL',
      sort: majorForm.sort ?? 0,
      status: majorForm.status ?? 1
    }
    const editingId = editingMajorId.value
    let majorId: string
    if (editingId) {
      await updateMajor(editingId, payload)
      majorId = editingId
    } else {
      const res = await createMajor(payload)
      majorId = res.data
    }
    message.success('专业已保存')
    visible.value = false
    emit('saved', majorId)
  } catch (error) {
    showError(error, '专业保存失败')
  } finally {
    saving.value = false
  }
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="visible" :width="560" placement="right">
    <n-drawer-content :title="editingMajorId ? '编辑专业' : '新增专业'">
      <n-form ref="majorFormRef" :model="majorForm" :rules="majorRules" label-placement="top">
        <div class="form-section-title">基本信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学院" path="collegeId" :span="2">
            <n-select v-model:value="majorForm.collegeId" :options="collegeOptions" filterable />
          </n-form-item-gi>
          <n-form-item-gi label="校内专业代码" path="internalMajorCode">
            <n-input v-model:value="majorForm.internalMajorCode" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="校内专业名称" path="internalMajorName">
            <n-input v-model:value="majorForm.internalMajorName" maxlength="128" show-count />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">学科信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="二级学科代码" path="secondDisciplineCode">
            <n-input v-model:value="majorForm.secondDisciplineCode" maxlength="64" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="二级学科名称" path="secondDisciplineName">
            <n-input v-model:value="majorForm.secondDisciplineName" maxlength="128" show-count />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">状态设置</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="年度版本" path="yearVersion">
            <n-input v-model:value="majorForm.yearVersion" maxlength="16" show-count />
          </n-form-item-gi>
          <n-form-item-gi label="试点范围" path="pilotScopeFlag">
            <n-switch v-model:value="majorForm.pilotScopeFlag" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
          <n-form-item-gi label="排序" path="sort">
            <n-input-number v-model:value="majorForm.sort" :min="0" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="majorForm.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveMajor">保存</n-button>
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
</style>
