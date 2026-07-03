<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage, type SelectOption, type UploadFileInfo } from 'naive-ui'
import type { Student } from '@/api/student'
import { useYearStore } from '@/stores/year'
import {
  initVideoUpload,
  mergeVideoUpload,
  uploadVideoChunk,
  type VideoReview
} from '@/api/video'

const props = defineProps<{
  students: Student[]
  assessmentYear: string
}>()

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const message = useMessage()
const yearStore = useYearStore()

const uploadVisible = ref(false)
const fileList = ref<UploadFileInfo[]>([])
const uploading = ref(false)
const uploadProgress = ref(0)

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  durationSeconds: 900,
  chunkSize: 512 * 1024
})

const studentOptions = computed<SelectOption[]>(() =>
  props.students.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)

function open(row?: VideoReview) {
  uploadForm.studentId = row?.studentId || ''
  uploadForm.assessmentYear = row?.assessmentYear || props.assessmentYear
  uploadForm.durationSeconds = row?.durationSeconds || 900
  uploadProgress.value = 0
  fileList.value = []
  uploadVisible.value = true
}

async function uploadVideo() {
  const file = fileList.value[0]?.file
  if (!file || !uploadForm.studentId || !uploadForm.assessmentYear) {
    message.error('请选择学生、年度和视频文件')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    const fileMd5 = await quickHash(file)
    const init = await initVideoUpload({
      studentId: uploadForm.studentId,
      assessmentYear: uploadForm.assessmentYear,
      fileMd5,
      fileName: file.name,
      contentType: file.type || 'video/mp4',
      size: file.size,
      chunkSize: uploadForm.chunkSize,
      durationSeconds: uploadForm.durationSeconds
    })
    if (init.data.instantHit) {
      uploadProgress.value = 100
      message.success(init.data.validationMessage || '秒传命中')
      uploadVisible.value = false
      emit('saved')
      return
    }
    const uploadId = init.data.uploadId
    if (!uploadId) throw new Error('上传会话为空')
    const uploaded = new Set(init.data.uploadedChunks)
    const total = Math.ceil(file.size / uploadForm.chunkSize)
    for (let index = 0; index < total; index += 1) {
      if (uploaded.has(index)) {
        uploadProgress.value = Math.round(((index + 1) / total) * 100)
        continue
      }
      const start = index * uploadForm.chunkSize
      const blob = file.slice(start, Math.min(start + uploadForm.chunkSize, file.size))
      await uploadVideoChunk({ uploadId, index, md5: await quickHash(blob), blob })
      uploadProgress.value = Math.round(((index + 1) / total) * 100)
    }
    const merged = await mergeVideoUpload(uploadId, uploadForm.durationSeconds)
    message[merged.data.status === 'VALIDATION_FAILED' ? 'warning' : 'success'](merged.data.validationMessage || '上传完成')
    uploadVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '上传失败')
  } finally {
    uploading.value = false
  }
}

async function quickHash(blob: Blob) {
  const buffer = await blob.arrayBuffer()
  const bytes = new Uint8Array(buffer)
  let hash = 0
  for (const byte of bytes) hash = (hash * 31 + byte) >>> 0
  return hash.toString(16).padStart(8, '0')
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

watch(
  () => yearStore.assessmentYear,
  (year) => {
    if (!uploadVisible.value) uploadForm.assessmentYear = year
  }
)

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="uploadVisible" :width="560">
    <n-drawer-content title="上传教学能力视频" closable>
      <n-space vertical>
        <n-alert type="info" :bordered="false">
          仅退回、待上传或校验失败状态显示重传入口；其他状态由校验规则禁止重传。
        </n-alert>
        <n-select v-model:value="uploadForm.studentId" :options="studentOptions" filterable placeholder="学生" />
        <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" class="mono-input" />
        <n-input-number v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" />
        <n-upload v-model:file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" />
        <n-progress type="line" :percentage="uploadProgress" indicator-placement="inside" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="uploadVisible = false">取消</n-button>
          <n-button type="primary" :loading="uploading" @click="uploadVideo">开始上传</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
