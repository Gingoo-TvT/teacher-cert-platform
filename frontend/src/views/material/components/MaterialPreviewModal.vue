<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { previewMaterial, type ProcessMaterial } from '@/api/material'

const message = useMessage()

const previewVisible = ref(false)
const previewRow = ref<ProcessMaterial | null>(null)
const previewUrl = ref('')

const previewable = computed(() => {
  const row = previewRow.value
  if (!row) return false
  const name = row.fileName.toLowerCase()
  const type = row.contentType || ''
  return type.includes('pdf') || type.includes('image') || /\.(pdf|jpg|jpeg|png)$/.test(name)
})

async function open(row: ProcessMaterial) {
  previewRow.value = row
  try {
    const res = await previewMaterial(row.id)
    previewUrl.value = res.data
    previewVisible.value = true
  } catch (error) {
    showError(error, '预览地址获取失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open })
</script>

<template>
  <n-modal v-model:show="previewVisible" preset="card" :title="previewRow?.fileName || '材料预览'" style="width: min(960px, 94vw)">
    <n-space vertical>
      <object v-if="previewable" :data="previewUrl" class="preview-frame">
        <iframe :src="previewUrl" class="preview-frame" />
      </object>
      <n-result v-else status="info" title="该文件不支持内联预览" description="非 PDF/JPG/PNG 文件请通过下载链接查看。">
        <template #footer>
          <n-button tag="a" :href="previewUrl" target="_blank" type="primary">打开文件</n-button>
        </template>
      </n-result>
    </n-space>
  </n-modal>
</template>

<style scoped>
.preview-frame {
  width: 100%;
  height: min(70vh, 720px);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-card);
}
</style>
