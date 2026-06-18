<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text?: string | null
  value?: string | null
}>()

const label = computed(() => props.text || props.value || '-')

const MAP: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  DRAFT: 'default',
  WAIT_SUBMIT: 'default',
  WAIT_UPLOAD: 'default',
  VALIDATING: 'info',
  VALIDATION_FAILED: 'error',
  WAIT_REVIEW: 'warning',
  REVIEWING: 'info',
  NEED_REVIEW: 'warning',
  REVIEW_COMPLETED: 'success',
  CONFIRMED: 'success',
  RETURNED: 'warning',
  PASS: 'success',
  FAIL: 'error',
  ENABLED: 'success',
  DISABLED: 'error',
  草稿: 'default',
  待提交: 'default',
  未开始: 'default',
  待初审: 'warning',
  待复审: 'warning',
  待审核: 'warning',
  待确认: 'warning',
  进行中: 'info',
  待评审: 'warning',
  待签发: 'warning',
  待复评: 'warning',
  初审通过: 'info',
  通过: 'success',
  已通过: 'success',
  合格: 'success',
  已签发: 'success',
  已确认: 'success',
  启用: 'success',
  已完成: 'success',
  正常: 'success',
  已生成: 'success',
  已导出: 'info',
  已归档: 'success',
  已退回: 'warning',
  退回: 'error',
  不通过: 'error',
  不合格: 'error',
  已作废: 'error',
  锁定: 'error',
  已锁定: 'warning',
  停用: 'error',
  异常: 'error',
  需复评: 'warning'
}

const type = computed(() => MAP[label.value] ?? 'default')
</script>

<template>
  <n-tag :type="type" size="small" :bordered="false" round>{{ label }}</n-tag>
</template>
