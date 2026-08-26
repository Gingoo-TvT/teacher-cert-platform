<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import { saveTrainingGoalConfig, type TrainingGoalConfig, type TrainingGoalConfigPayload } from '@/api/organization'

const props = defineProps<{
  trainingGoalOptions: SelectOption[]
  segmentOptions: SelectOption[]
  internshipLocationOptions: SelectOption[]
  defaultConfigCode: string | null
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const configFormRef = ref<FormInst | null>(null)

interface ConfigFormState {
  trainingGoalCode: string | null
  defaultSegment: string | null
  allowedSegments: string[]
  defaultInternshipLocation: string | null
  allowedInternshipLocations: string[]
  status: number
}

const configForm = reactive<ConfigFormState>({
  trainingGoalCode: null,
  defaultSegment: null,
  allowedSegments: [],
  defaultInternshipLocation: null,
  allowedInternshipLocations: [],
  status: 1
})

const configRules: FormRules = {
  trainingGoalCode: [{ required: true, message: '请选择培养目标', trigger: ['change'] }],
  defaultSegment: [{ required: true, message: '请选择默认任教学段', trigger: ['change'] }],
  allowedSegments: [{ type: 'array', required: true, min: 1, message: '请选择允许任教学段', trigger: ['change'] }],
  defaultInternshipLocation: [{ required: true, message: '请选择默认实习地点', trigger: ['change'] }],
  allowedInternshipLocations: [{ type: 'array', required: true, min: 1, message: '请选择允许实习地点', trigger: ['change'] }]
}

function open(row?: TrainingGoalConfig) {
  configForm.trainingGoalCode = row?.trainingGoalCode || (props.defaultConfigCode ?? null)
  configForm.allowedSegments = [...(row?.allowedSegments || [])]
  configForm.defaultSegment = row?.defaultSegment || configForm.allowedSegments[0] || null
  configForm.allowedInternshipLocations = [...(row?.allowedInternshipLocations || [])]
  configForm.defaultInternshipLocation = row?.defaultInternshipLocation || configForm.allowedInternshipLocations[0] || null
  configForm.status = row?.status ?? 1
  visible.value = true
}

async function saveConfig() {
  if (saving.value) return
  saving.value = true
  try {
    try {
      await configFormRef.value?.validate()
    } catch {
      return
    }
    if (!configForm.defaultSegment || !configForm.allowedSegments.includes(configForm.defaultSegment)) {
      message.error('默认任教学段必须包含在允许任教学段中')
      return
    }
    if (!configForm.defaultInternshipLocation || !configForm.allowedInternshipLocations.includes(configForm.defaultInternshipLocation)) {
      message.error('默认实习地点必须包含在允许实习地点中')
      return
    }
    const payload: TrainingGoalConfigPayload = {
      trainingGoalCode: configForm.trainingGoalCode || '',
      defaultSegment: configForm.defaultSegment,
      allowedSegments: configForm.allowedSegments,
      defaultInternshipLocation: configForm.defaultInternshipLocation,
      allowedInternshipLocations: configForm.allowedInternshipLocations,
      status: configForm.status ?? 1
    }
    await saveTrainingGoalConfig(payload)
    message.success('联动配置已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '联动配置保存失败')
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
    <n-drawer-content title="培养目标联动配置" :closable="!saving">
      <n-form ref="configFormRef" :model="configForm" :rules="configRules" label-placement="top" :disabled="saving">
        <div class="form-section-title">培养目标</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="培养目标" path="trainingGoalCode" span="1 480:2">
            <n-select v-model:value="configForm.trainingGoalCode" :options="trainingGoalOptions" filterable />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">任教学段</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="允许任教学段" path="allowedSegments" span="1 480:2">
            <n-select v-model:value="configForm.allowedSegments" :options="segmentOptions" multiple filterable />
          </n-form-item-gi>
          <n-form-item-gi label="默认任教学段" path="defaultSegment">
            <n-select v-model:value="configForm.defaultSegment" :options="segmentOptions" filterable />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-switch v-model:value="configForm.status" :checked-value="1" :unchecked-value="0" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">实习地点</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="允许实习地点" path="allowedInternshipLocations" span="1 480:2">
            <n-select v-model:value="configForm.allowedInternshipLocations" :options="internshipLocationOptions" multiple filterable />
          </n-form-item-gi>
          <n-form-item-gi label="默认实习地点" path="defaultInternshipLocation" span="1 480:2">
            <n-select v-model:value="configForm.defaultInternshipLocation" :options="internshipLocationOptions" filterable />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveConfig">保存</n-button>
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
