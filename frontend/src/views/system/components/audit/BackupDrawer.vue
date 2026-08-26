<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { triggerBackup } from '@/api/systemAudit'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const backupDrawerVisible = ref(false)
const saving = ref(false)
const backupFormRef = ref<FormInst | null>(null)

const backupForm = reactive({
  backupType: 'mysql',
  scope: 'full',
  remark: ''
})

const backupRules: FormRules = {
  backupType: [{ required: true, message: '请选择备份类型', trigger: ['change'] }]
}

function open() {
  backupForm.backupType = 'mysql'
  backupForm.scope = 'full'
  backupForm.remark = ''
  backupDrawerVisible.value = true
}

async function saveBackup() {
  if (saving.value) return
  saving.value = true
  try {
    try {
      await backupFormRef.value?.validate()
    } catch {
      return
    }
    await triggerBackup({
      backupType: backupForm.backupType,
      scope: backupForm.scope.trim() || null,
      remark: backupForm.remark.trim() || null
    })
    message.success('备份演练记录已写入')
    backupDrawerVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '备份记录写入失败')
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
    v-model:show="backupDrawerVisible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    placement="right"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content title="记录备份演练" :closable="!saving">
      <n-form ref="backupFormRef" :model="backupForm" :rules="backupRules" label-placement="top" :disabled="saving">
        <div class="form-section-title">备份内容</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="备份类型" path="backupType">
            <n-select
              v-model:value="backupForm.backupType"
              :options="[
                { label: 'MySQL', value: 'mysql' },
                { label: 'MinIO', value: 'minio' },
                { label: '全量', value: 'full' }
              ]"
            />
          </n-form-item-gi>
          <n-form-item-gi label="范围">
            <n-input v-model:value="backupForm.scope" maxlength="128" />
          </n-form-item-gi>
          <n-form-item-gi label="备注" span="1 480:2">
            <n-input v-model:value="backupForm.remark" type="textarea" maxlength="500" show-count />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="backupDrawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveBackup">保存</n-button>
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
