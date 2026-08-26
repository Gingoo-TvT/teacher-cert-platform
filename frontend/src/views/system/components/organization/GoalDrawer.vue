<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import { replaceMajorTrainingGoals, type Major } from '@/api/organization'

const props = defineProps<{
  trainingGoalOptions: SelectOption[]
  selectedMajor: Major | null
}>()

const emit = defineEmits<{
  select: [majorId: string]
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const goalFormRef = ref<FormInst | null>(null)
const editingMajor = ref<Major | null>(null)

interface GoalFormState {
  trainingGoalCodes: string[]
}

const goalForm = reactive<GoalFormState>({ trainingGoalCodes: [] })

const goalRules: FormRules = {
  trainingGoalCodes: [{ type: 'array', required: true, min: 1, message: '请选择培养目标', trigger: ['change'] }]
}

function open(row?: Major) {
  const target = row ?? props.selectedMajor
  if (!target) {
    message.warning('请选择专业')
    return
  }
  emit('select', target.id)
  editingMajor.value = target
  goalForm.trainingGoalCodes = target.trainingGoals.map((item) => item.code)
  visible.value = true
}

async function saveGoals() {
  if (saving.value) return
  if (!editingMajor.value) return
  saving.value = true
  try {
    try {
      await goalFormRef.value?.validate()
    } catch {
      return
    }
    await replaceMajorTrainingGoals(editingMajor.value.id, goalForm.trainingGoalCodes)
    message.success('专业培养目标已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '专业培养目标保存失败')
  } finally {
    saving.value = false
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-drawer
    v-model:show="visible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    placement="right"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content title="专业培养目标" :closable="!saving">
      <n-form ref="goalFormRef" :model="goalForm" :rules="goalRules" label-placement="top" :disabled="saving">
        <div class="form-section-title">目标设置</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="专业" span="1 480:2">
            <n-input :value="editingMajor ? `${editingMajor.internalMajorName} ${editingMajor.internalMajorCode}` : ''" disabled />
          </n-form-item-gi>
          <n-form-item-gi label="培养目标" path="trainingGoalCodes" span="1 480:2">
            <n-select v-model:value="goalForm.trainingGoalCodes" :options="trainingGoalOptions" multiple filterable />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveGoals">保存</n-button>
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
