<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMessage, type UploadFileInfo } from 'naive-ui'
import { replaceExemptionMaterial, type ExemptionMaterial, type ExemptionRequest } from '@/api/exemption'

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const replaceVisible = ref(false)
const saving = ref(false)
const replacingMaterial = ref<{ record: ExemptionRequest; material: ExemptionMaterial } | null>(null)
const replacementFiles = ref<UploadFileInfo[]>([])

watch(replaceVisible, (visible) => {
  if (visible) return
  replacingMaterial.value = null
  replacementFiles.value = []
})

function open(row: ExemptionRequest) {
  if (!row.materials[0]) {
    message.error('该科还没有佐证')
    return
  }
  replacingMaterial.value = { record: row, material: row.materials[0] }
  replacementFiles.value = []
  replaceVisible.value = true
}

async function saveReplace() {
  if (saving.value) return
  const file = replacementFiles.value[0]?.file
  if (!replacingMaterial.value || !file) {
    message.error('请选择附件')
    return
  }
  saving.value = true
  try {
    await replaceExemptionMaterial(replacingMaterial.value.material.id, file)
    message.success('已替换佐证')
    replacingMaterial.value = null
    replaceVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '佐证替换失败')
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
  <n-modal
    v-model:show="replaceVisible"
    preset="dialog"
    title="替换免考佐证"
    :closable="!saving"
    :close-on-esc="!saving"
    :mask-closable="!saving"
  >
    <n-space vertical>
      <n-alert v-if="replacingMaterial" type="info" :bordered="false">
        {{ replacingMaterial.record.studentNo }} / {{ replacingMaterial.record.subjectLabel }}
      </n-alert>
      <n-upload v-model:file-list="replacementFiles" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" :disabled="saving">
        <n-upload-dragger>
          <n-text>点击或拖拽佐证文件到此处上传</n-text>
          <n-p depth="3">支持 PDF、JPG、JPEG、PNG，最多 1 个文件。</n-p>
        </n-upload-dragger>
      </n-upload>
      <n-space justify="end">
        <n-button :disabled="saving" @click="replaceVisible = false; replacingMaterial = null">取消</n-button>
        <n-button type="primary" :loading="saving" @click="saveReplace">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>
</template>
