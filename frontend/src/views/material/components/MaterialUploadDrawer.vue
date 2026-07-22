<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage, type SelectOption, type UploadFileInfo } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { fingerprintFile, isAbortError, uploadPresignedMultipart, uploadWithPresignedRefresh } from '@/utils/videoUpload'
import {
  cancelMaterialDirectUpload,
  completeMaterialDirectUpload,
  initMaterialDirectUpload,
  MATERIAL_UPLOAD_PART_SIZE,
  replaceMaterial,
  uploadMaterial,
  type MaterialDirectUploadContext,
  type ProcessMaterial
} from '@/api/material'

const props = defineProps<{
  categoryOptions: SelectOption[]
  selfMode: boolean
  assessmentYear: string
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const saving = ref(false)
const uploadVisible = ref(false)
const fileList = ref<UploadFileInfo[]>([])
const replacing = ref<ProcessMaterial | null>(null)
const selectedStudentLabel = ref<string | null>(null)
const uploadProgress = ref(0)
const activeFileId = ref<string | null>(null)
const initPending = ref(false)
const cancelling = ref(false)
let uploadController: AbortController | null = null
let abandonRequested = false

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  category: ''
})

function open(row?: ProcessMaterial, category?: string) {
  if (row) {
    replacing.value = row
    uploadForm.studentId = row.studentId
    uploadForm.assessmentYear = row.assessmentYear
    uploadForm.category = row.category
    selectedStudentLabel.value = `${row.studentNo || ''} ${row.studentName || ''}`.trim() || null
  } else {
    replacing.value = null
    uploadForm.studentId = props.selfMode ? userStore.currentUser?.studentId || '' : ''
    uploadForm.assessmentYear = props.assessmentYear
    uploadForm.category = category || ''
    selectedStudentLabel.value = props.selfMode ? '本人' : null
  }
  fileList.value = []
  uploadProgress.value = 0
  initPending.value = false
  cancelling.value = false
  abandonRequested = false
  uploadVisible.value = true
}

async function saveUpload() {
  const file = fileList.value[0]?.file
  if (!uploadForm.studentId || !uploadForm.assessmentYear || !uploadForm.category || !file) {
    message.error('请选择学生、年度、类别和附件')
    return
  }
  saving.value = true
  uploadProgress.value = 0
  abandonRequested = false
  const controller = new AbortController()
  uploadController = controller
  let context: MaterialDirectUploadContext | null = null
  let initPayload: Parameters<typeof initMaterialDirectUpload>[0] | null = null
  let initStarted = false
  let initResolved = false
  try {
    context = {
      materialId: replacing.value?.id,
      studentId: uploadForm.studentId,
      assessmentYear: uploadForm.assessmentYear,
      category: uploadForm.category
    }
    const requestPayload = {
      ...context,
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      size: file.size,
      fileHash: await fingerprintFile(file, controller.signal),
      partSize: MATERIAL_UPLOAD_PART_SIZE
    }
    initPayload = requestPayload
    let init
    initPending.value = true
    initStarted = true
    try {
      init = await initMaterialDirectUpload(requestPayload, controller.signal)
      initResolved = true
    } finally {
      initPending.value = false
    }
    if (init.data.fileId) activeFileId.value = init.data.fileId
    if (abandonRequested) {
      if (init.data.uploadMode === 'READY' && init.data.fileId) {
        if (await bindCommittedMaterial(init.data.fileId, context)) uploadVisible.value = false
        return
      }
      if (await cancelActiveMaterialSession()) uploadVisible.value = false
      return
    }
    if (init.data.uploadMode === 'SERVER_UPLOAD') {
      if (replacing.value) {
        await replaceMaterial(replacing.value.id, file, controller.signal)
      } else {
        await uploadMaterial({
          studentId: context.studentId,
          assessmentYear: context.assessmentYear,
          category: context.category,
          file
        }, controller.signal)
      }
      uploadProgress.value = 100
      message.success('已保存')
      uploadVisible.value = false
      emit('saved')
      return
    }
    if (!init.data.fileId) throw new Error('服务端未返回直传文件标识，请重新选择文件后再试')
    activeFileId.value = init.data.fileId
    const transferred = await uploadWithPresignedRefresh({
      initialSession: init.data,
      signal: controller.signal,
      upload: (current) => {
        if (current.uploadMode === 'READY') {
          uploadProgress.value = 100
          return Promise.resolve([])
        }
        if (current.uploadMode !== 'PRESIGNED_MULTIPART') {
          throw new Error('材料直传会话模式无效，请重新选择文件')
        }
        return uploadPresignedMultipart({
          file,
          partSize: current.partSize,
          parts: current.parts,
          uploadedParts: current.uploadedParts,
          signal: controller.signal,
          onProgress: (progress) => {
            uploadProgress.value = progress.percentage
          }
        })
      },
      refresh: async () => {
        const refreshed = await initMaterialDirectUpload(requestPayload, controller.signal)
        if (!['PRESIGNED_MULTIPART', 'READY'].includes(refreshed.data.uploadMode) || !refreshed.data.fileId) {
          throw new Error('刷新材料直传地址时会话发生变化，请重新选择文件')
        }
        activeFileId.value = refreshed.data.fileId
        return refreshed.data
      },
      completedPartCount: (current) => current.uploadMode === 'READY'
        ? Number.MAX_SAFE_INTEGER
        : current.uploadedParts.length
    })
    const finalSession = transferred.session
    if (!finalSession.fileId) throw new Error('服务端未返回直传文件标识，请重新选择文件后再试')
    await completeMaterialDirectUpload({
      ...context,
      fileId: finalSession.fileId,
      parts: transferred.result
    }, controller.signal)
    activeFileId.value = null
    uploadController = null
    message.success('已保存')
    uploadVisible.value = false
    emit('saved')
  } catch (error) {
    if (abandonRequested) {
      if (!activeFileId.value && initStarted && !initResolved && initPayload && context) {
        try {
          const recovered = await initMaterialDirectUpload(initPayload)
          if (recovered.data.fileId) activeFileId.value = recovered.data.fileId
          if (recovered.data.uploadMode === 'READY' && recovered.data.fileId) {
            if (await bindCommittedMaterial(recovered.data.fileId, context)) uploadVisible.value = false
            return
          }
          if (recovered.data.uploadMode === 'SERVER_UPLOAD') {
            uploadVisible.value = false
            return
          }
        } catch (recoveryError) {
          showError(recoveryError, '无法确认服务端上传会话是否已取消，请重试')
          return
        }
      }
      if (activeFileId.value) {
        if (await cancelActiveMaterialSession()) uploadVisible.value = false
      } else if (!initStarted || isAbortError(error) || initResolved) {
        uploadVisible.value = false
      } else {
        showError(error, '无法确认服务端上传会话是否已取消，请重试')
      }
    } else if (!isAbortError(error)) {
      showError(error, '保存失败')
    }
  } finally {
    if (uploadController === controller) uploadController = null
    initPending.value = false
    cancelling.value = false
    saving.value = false
  }
}

async function cancelOrClose() {
  if (!saving.value && !activeFileId.value) {
    uploadVisible.value = false
    return
  }
  abandonRequested = true
  cancelling.value = true
  if (saving.value && initPending.value && !activeFileId.value) return
  uploadController?.abort()
  if (await cancelActiveMaterialSession()) uploadVisible.value = false
  cancelling.value = false
}

async function cancelActiveMaterialSession() {
  const fileId = activeFileId.value
  if (!fileId) return true
  try {
    await cancelMaterialDirectUpload(fileId)
    activeFileId.value = null
    return true
  } catch (error) {
    showError(error, '取消上传失败')
    return false
  }
}

async function bindCommittedMaterial(fileId: string, context: MaterialDirectUploadContext) {
  try {
    await completeMaterialDirectUpload({ ...context, fileId, parts: [] })
    activeFileId.value = null
    message.warning('材料已完成定稿，无法取消')
    emit('saved')
    return true
  } catch (error) {
    showError(error, '材料已定稿但绑定失败，请重试')
    return false
  }
}

function getStudentId() {
  return uploadForm.studentId
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

defineExpose({ open, getStudentId })
</script>

<template>
  <n-drawer v-model:show="uploadVisible" :width="560" :mask-closable="!saving && !activeFileId">
    <n-drawer-content :title="replacing ? '替换材料' : '上传材料'" :closable="!saving && !activeFileId">
      <n-alert v-if="replacing" type="info" :bordered="false" class="page-section">
        替换材料沿用原学生、年度与材料类别。
      </n-alert>
      <n-form label-placement="top">
        <div class="form-section-title">材料信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学生" :span="2">
            <StudentSelect
              v-model:value="uploadForm.studentId"
              :disabled="Boolean(replacing) || selfMode"
              :selected-label="selectedStudentLabel"
              placeholder="输入学号或姓名搜索"
            />
          </n-form-item-gi>
          <n-form-item-gi label="考核年度">
            <n-input v-model:value="uploadForm.assessmentYear" :disabled="Boolean(replacing)" placeholder="考核年度" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="材料类别">
            <n-select v-model:value="uploadForm.category" :options="categoryOptions" :disabled="Boolean(replacing)" placeholder="材料类别" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">上传文件</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="文件" :span="2">
            <n-upload v-model:file-list="fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false">
              <n-upload-dragger>
                <n-text>点击或拖拽文件到此处上传</n-text>
                <n-p depth="3">支持 PDF、JPG、JPEG、PNG，最多 1 个文件。</n-p>
              </n-upload-dragger>
            </n-upload>
          </n-form-item-gi>
          <n-form-item-gi v-if="saving" label="上传进度" :span="2">
            <n-progress type="line" :percentage="uploadProgress" indicator-placement="inside" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :loading="cancelling" @click="cancelOrClose">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveUpload">保存</n-button>
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

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
