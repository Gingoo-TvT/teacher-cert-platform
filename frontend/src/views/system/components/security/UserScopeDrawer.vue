<script setup lang="ts">
import { ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import { assignUserDataScope, type User } from '@/api/security'

defineProps<{
  collegeOptions: SelectOption[]
  majorOptions: SelectOption[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const currentScopeUser = ref<User | null>(null)
const scopeCollegeIds = ref<string[]>([])
const scopeMajorIds = ref<string[]>([])

function open(row: User) {
  currentScopeUser.value = row
  scopeCollegeIds.value = [...row.dataScopeCollegeIds]
  scopeMajorIds.value = [...row.dataScopeMajorIds]
  visible.value = true
}

async function saveUserScope() {
  if (saving.value) return
  if (!currentScopeUser.value) return
  saving.value = true
  try {
    await assignUserDataScope(currentScopeUser.value.id, {
      collegeIds: scopeCollegeIds.value,
      majorIds: scopeMajorIds.value
    })
    message.success('数据范围已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '数据范围保存失败')
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
    <n-drawer-content :title="currentScopeUser ? `数据范围：${currentScopeUser.realName}` : '数据范围'" :closable="!saving">
      <n-form label-placement="top" :disabled="saving">
        <div class="form-section-title">可查看范围</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="授权学院" span="1 480:2">
            <n-select v-model:value="scopeCollegeIds" :options="collegeOptions" multiple filterable />
          </n-form-item-gi>
          <n-form-item-gi label="授权专业" span="1 480:2">
            <n-select v-model:value="scopeMajorIds" :options="majorOptions" multiple filterable />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveUserScope">保存</n-button>
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
