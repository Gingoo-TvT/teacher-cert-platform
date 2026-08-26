<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { SelectOption } from 'naive-ui'
import StatusTag from '@/components/StatusTag.vue'

const props = withDefaults(defineProps<{
  show: boolean
  title?: string
  loading?: boolean
  allowFail?: boolean
  summary?: { label: string; value?: string | number | null; status?: string | null }[]
}>(), {
  title: '审核',
  allowFail: false,
  summary: () => []
})

const emit = defineEmits<{
  'update:show': [value: boolean]
  submit: [payload: { action: 'PASS' | 'REJECT' | 'FAIL'; comment: string }]
}>()

const form = reactive({
  action: 'PASS' as 'PASS' | 'REJECT' | 'FAIL',
  comment: ''
})

const actionOptions = computedOptions()
const commentMissing = ref(false)

watch(
  () => props.show,
  (show) => {
    if (!show) return
    form.action = 'PASS'
    form.comment = ''
    commentMissing.value = false
  }
)

watch(
  () => [form.action, form.comment],
  () => {
    if (commentMissing.value && form.comment.trim()) commentMissing.value = false
  }
)

function computedOptions(): SelectOption[] {
  const options: SelectOption[] = [
    { label: '通过', value: 'PASS' },
    { label: '退回', value: 'REJECT' }
  ]
  if (props.allowFail) options.push({ label: '不通过', value: 'FAIL' })
  return options
}

function close() {
  if (props.loading) return
  emit('update:show', false)
}

function submit() {
  if (props.loading) return
  if (form.action !== 'PASS' && !form.comment.trim()) {
    commentMissing.value = true
    return
  }
  emit('submit', { action: form.action, comment: form.comment.trim() })
}

function updateShow(show: boolean) {
  if (props.loading && !show) return
  emit('update:show', show)
}

function text(value?: string | number | null) {
  if (value === null || typeof value === 'undefined' || value === '') return '-'
  return String(value)
}
</script>

<template>
  <!-- n-modal 内容 teleport 到 body，scoped class 选择器不生效；宽度必须用内联 style -->
  <n-modal
    :show="show"
    preset="card"
    :title="title"
    class="review-dialog"
    :bordered="false"
    :closable="!loading"
    :close-on-esc="!loading"
    :mask-closable="!loading"
    style="width: min(var(--overlay-medium), var(--overlay-modal-max))"
    @update:show="updateShow"
  >
    <div class="review-summary">
      <div v-for="item in summary" :key="item.label" class="review-summary__item">
        <span>{{ item.label }}</span>
        <StatusTag v-if="item.status" :value="item.status" />
        <strong v-else>{{ text(item.value) }}</strong>
      </div>
    </div>

    <n-form label-placement="top" :disabled="loading">
      <n-form-item label="审核结论">
        <n-radio-group v-model:value="form.action">
          <n-space>
            <n-radio-button v-for="item in actionOptions" :key="String(item.value)" :value="item.value">
              {{ item.label }}
            </n-radio-button>
          </n-space>
        </n-radio-group>
      </n-form-item>
      <n-form-item
        :label="form.action === 'PASS' ? '审核意见' : '退回或不通过原因'"
        :validation-status="commentMissing ? 'error' : undefined"
        :feedback="commentMissing ? '退回或不通过时必须填写原因' : undefined"
      >
        <n-input v-model:value="form.comment" type="textarea" maxlength="500" show-count placeholder="请输入审核意见" />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="loading" @click="close">取消</n-button>
        <n-button type="primary" :loading="loading" @click="submit">确认</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<style scoped>
.review-dialog {
  width: min(var(--overlay-medium), var(--overlay-modal-max));
}

.review-summary {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-control);
  background: var(--surface-muted);
}

.review-summary__item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
}

.review-summary__item span {
  color: var(--text-secondary);
  text-align: right;
}

.review-summary__item strong {
  min-width: 0;
  color: var(--text);
  font-weight: 500;
  overflow-wrap: anywhere;
}

@media (max-width: 480px) {
  .review-summary__item {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-1);
  }

  .review-summary__item span {
    text-align: left;
  }
}
</style>
