<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text?: string | null
  value?: string | null
}>()

const label = computed(() => props.text || props.value || '-')

const MAP: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  DRAFT: 'default',
  FIRST_REVIEW: 'warning',
  FIRST_REJECTED: 'warning',
  SECOND_REVIEW: 'warning',
  SECOND_REJECTED: 'warning',
  PASSED: 'success',
  FAILED: 'error',
  GENERATED: 'warning',
  ISSUED: 'success',
  EXPORTED: 'info',
  ARCHIVED: 'success',
  VOIDED: 'error',
  REISSUED: 'warning',
  COMPLETED: 'success',
  ROLLED_BACK: 'warning',
  IMPORTED: 'success',
  PREVALIDATED: 'info',
  LOCKED: 'warning',
  NORMAL: 'success',
  PASS: 'success',
  REJECT: 'error',
  FAIL: 'error',
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
  ENABLED: 'success',
  DISABLED: 'error',
  UNREAD: 'warning',
  READ: 'success',
  草稿: 'default',
  待提交: 'default',
  未开始: 'default',
  未读: 'warning',
  已读: 'success',
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
  初审退回: 'warning',
  复审退回: 'warning',
  退回: 'error',
  不通过: 'error',
  不合格: 'error',
  已作废: 'error',
  已重开: 'warning',
  锁定: 'error',
  已锁定: 'warning',
  未锁定: 'default',
  停用: 'error',
  异常: 'error',
  需复评: 'warning',
  预校验通过: 'info',
  导入成功: 'success',
  已回滚: 'warning',
  失败: 'error'
}

const type = computed(() => MAP[label.value] ?? 'default')
const tagClass = computed(() => ['status-soft', `status-${type.value}`])
</script>

<template>
  <n-tag :type="type" size="small" :bordered="false" :class="tagClass">{{ label }}</n-tag>
</template>
