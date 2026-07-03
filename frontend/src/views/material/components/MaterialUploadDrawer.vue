<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage, type SelectOption, type UploadFileInfo } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { replaceMaterial, uploadMaterial, type ProcessMaterial } from '@/api/material'

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
  uploadVisible.value = true
}

async function saveUpload() {
  const file = fileList.value[0]?.file
  if (!uploadForm.studentId || !uploadForm.assessmentYear || !uploadForm.category || !file) {
    message.error('请选择学生、年度、类别和附件')
    return
  }
  saving.value = true
  try {
    if (replacing.value) await replaceMaterial(replacing.value.id, file)
    else await uploadMaterial({ ...uploadForm, file })
    message.success('已保存')
    uploadVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
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

defineExpose({ open, getStudentId })
</script>

<template>
  <n-drawer v-model:show="uploadVisible" :width="560">
    <n-drawer-content :title="replacing ? '替换材料' : '上传材料'" closable>
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
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="uploadVisible = false">取消</n-button>
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
