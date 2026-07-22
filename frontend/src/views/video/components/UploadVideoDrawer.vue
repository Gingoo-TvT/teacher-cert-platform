<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage, type UploadFileInfo } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import { useYearStore } from '@/stores/year'
import { detectVideoDurationSeconds, formatVideoDuration } from '@/utils/videoDuration'
import { isAbortError, uploadVideoFile } from '@/utils/videoUpload'
import {
  cancelVideoUpload,
  VIDEO_UPLOAD_CHUNK_SIZE,
  type VideoReview
} from '@/api/video'

const props = defineProps<{
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
const activeUploadId = ref<string | null>(null)
const initPending = ref(false)
const cancelling = ref(false)
let uploadController: AbortController | null = null
let abandonRequested = false
const durationDetected = ref(false)
const durationDetectFailed = ref(false)
const durationDetecting = ref(false)
let durationDetection: Promise<number | null> | null = null
let durationDetectionFile: File | null = null
let durationDetectionSequence = 0
const selectedStudentLabel = ref<string | null>(null)

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  durationSeconds: 900,
  chunkSize: VIDEO_UPLOAD_CHUNK_SIZE
})

const durationText = computed(() => `${formatVideoDuration(uploadForm.durationSeconds)} (${uploadForm.durationSeconds}s)`)

function open(row?: VideoReview) {
  uploadForm.studentId = row?.studentId || ''
  uploadForm.assessmentYear = row?.assessmentYear || props.assessmentYear
  uploadForm.durationSeconds = row?.durationSeconds || 900
  selectedStudentLabel.value = row ? `${row.studentNo || ''} ${row.studentName || ''}`.trim() || null : null
  durationDetected.value = Boolean(row?.durationSeconds)
  durationDetectFailed.value = false
  durationDetecting.value = false
  durationDetection = null
  durationDetectionFile = null
  durationDetectionSequence += 1
  uploadProgress.value = 0
  fileList.value = []
  initPending.value = false
  cancelling.value = false
  abandonRequested = false
  uploadVisible.value = true
}

async function handleFileListUpdate(next: UploadFileInfo[]) {
  fileList.value = next
  const file = next[0]?.file
  const sequence = ++durationDetectionSequence
  durationDetected.value = false
  durationDetectFailed.value = false
  durationDetectionFile = file || null
  if (!file) {
    durationDetecting.value = false
    durationDetection = null
    return
  }
  durationDetecting.value = true
  const pending = detectVideoDurationSeconds(file).then((value) => value).catch(() => null)
  durationDetection = pending
  const detected = await pending
  if (sequence !== durationDetectionSequence || fileList.value[0]?.file !== file) return
  durationDetecting.value = false
  if (detected == null) {
    durationDetectFailed.value = true
  } else {
    uploadForm.durationSeconds = detected
    durationDetected.value = true
  }
}

async function uploadVideo() {
  const file = fileList.value[0]?.file
  if (!file || !uploadForm.studentId || !uploadForm.assessmentYear) {
    message.error('请选择学生、年度和视频文件')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  abandonRequested = false
  const controller = new AbortController()
  uploadController = controller
  try {
    let durationSeconds = uploadForm.durationSeconds
    if (durationDetectionFile === file && durationDetection) {
      const detected = await durationDetection
      if (fileList.value[0]?.file !== file) throw new Error('视频文件已变化，请重新开始上传')
      if (detected != null) durationSeconds = detected
    }
    const result = await uploadVideoFile({
      file,
      studentId: uploadForm.studentId,
      assessmentYear: uploadForm.assessmentYear,
      chunkSize: uploadForm.chunkSize,
      durationSeconds,
      signal: controller.signal,
      onInitPending: (pending) => {
        initPending.value = pending
      },
      shouldAbandon: () => abandonRequested,
      onSession: (uploadId) => {
        activeUploadId.value = uploadId
        if (abandonRequested) controller.abort()
      },
      onProgress: (progress) => {
        uploadProgress.value = progress.percentage
      }
    })
    if (abandonRequested) {
      activeUploadId.value = null
      message.warning(result.instantHit ? '视频已完成秒传，无法取消' : '视频已完成定稿，无法取消')
      uploadVisible.value = false
      emit('saved')
      return
    }
    activeUploadId.value = null
    uploadController = null
    if (result.instantHit) {
      uploadProgress.value = 100
      message.success(result.validationMessage || '秒传命中')
      uploadVisible.value = false
      emit('saved')
      return
    }
    if (!result.review) throw new Error('服务端未返回视频校验结果')
    message[result.review.status === 'VALIDATION_FAILED' ? 'warning' : 'success'](result.validationMessage || '上传完成')
    uploadVisible.value = false
    emit('saved')
  } catch (error) {
    if (abandonRequested) {
      if (activeUploadId.value) {
        if (await cancelActiveVideoSession()) uploadVisible.value = false
      } else if (isAbortError(error)) {
        uploadVisible.value = false
      } else {
        showError(error, '无法确认服务端上传会话是否已取消，请重试')
      }
    } else if (!isAbortError(error)) {
      showError(error, '上传失败')
    }
  } finally {
    if (uploadController === controller) uploadController = null
    initPending.value = false
    cancelling.value = false
    uploading.value = false
  }
}

async function cancelOrClose() {
  if (!uploading.value && !activeUploadId.value) {
    uploadVisible.value = false
    return
  }
  abandonRequested = true
  cancelling.value = true
  if (uploading.value && initPending.value && !activeUploadId.value) return
  uploadController?.abort()
  if (await cancelActiveVideoSession()) uploadVisible.value = false
  cancelling.value = false
}

async function cancelActiveVideoSession() {
  const uploadId = activeUploadId.value
  if (!uploadId) return true
  try {
    await cancelVideoUpload(uploadId)
    activeUploadId.value = null
    return true
  } catch (error) {
    showError(error, '取消上传失败')
    return false
  }
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

onBeforeUnmount(() => {
  uploadController?.abort()
})

defineExpose({ open })
</script>

<template>
  <n-drawer v-model:show="uploadVisible" :width="560" :mask-closable="!uploading && !activeUploadId">
    <n-drawer-content title="上传教学能力视频" :closable="!uploading && !activeUploadId">
      <n-space vertical>
        <n-alert type="info" :bordered="false">
          仅退回、待上传或校验失败状态显示重传入口；其他状态由校验规则禁止重传。
        </n-alert>
        <StudentSelect v-model:value="uploadForm.studentId" :selected-label="selectedStudentLabel" placeholder="输入学号或姓名搜索" />
        <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" class="mono-input" />
        <n-alert v-if="!durationDetectFailed" type="info" :bordered="false">
          {{ fileList.length ? (durationDetected ? `时长：${durationText} · 自动识别` : '正在识别视频时长') : '选择视频后自动识别时长' }}
        </n-alert>
        <n-input-number v-else v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" />
        <n-upload :file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" @update:file-list="handleFileListUpdate">
          <n-upload-dragger>
            <n-text>点击或拖拽视频到此处上传</n-text>
            <n-p depth="3">支持 MP4 文件，选择后自动识别时长。</n-p>
          </n-upload-dragger>
        </n-upload>
        <n-progress type="line" :percentage="uploadProgress" indicator-placement="inside" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button :loading="cancelling" @click="cancelOrClose">取消</n-button>
          <n-button type="primary" :loading="uploading" :disabled="durationDetecting" @click="uploadVideo">开始上传</n-button>
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
