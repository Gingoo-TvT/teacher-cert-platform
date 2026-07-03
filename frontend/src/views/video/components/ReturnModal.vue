<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { returnVideoReview, type VideoReview } from '@/api/video'

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const message = useMessage()

const returnVisible = ref(false)
const returning = ref<VideoReview | null>(null)

const returnForm = reactive({
  comment: ''
})

function open(row: VideoReview) {
  returning.value = row
  returnForm.comment = row.status === 'RETURNED' ? row.validationMessage || '' : ''
  returnVisible.value = true
}

async function saveReturn() {
  if (!returning.value) return
  const comment = returnForm.comment.trim()
  if (!comment) {
    message.error('请填写退回意见')
    return
  }
  try {
    await returnVideoReview(returning.value.id, comment)
    message.success('已退回，学生可重新上传')
    returnVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '退回失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-modal v-model:show="returnVisible" preset="dialog" title="退回视频">
    <n-space vertical>
      <n-alert v-if="returning" type="warning" :bordered="false">
        {{ returning.studentNo }} / {{ returning.studentName }}。已确认视频不可退回；其他状态由校验规则处理。
      </n-alert>
      <n-input v-model:value="returnForm.comment" type="textarea" placeholder="请输入退回意见，学生重传时可据此修改" />
      <n-space justify="end">
        <n-button @click="returnVisible = false">取消</n-button>
        <n-button type="warning" @click="saveReturn">退回</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>
