<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, useMessage, type DataTableColumns } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import { getRegionPath, listRegionChildren, type RegionNode } from '@/api/region'

const message = useMessage()

interface RegionTableRow extends RegionNode {
  levelName: string
}

const loading = ref(false)
const pathLoading = ref(false)
const regionCode = ref<string | null>(null)
const codeInput = ref('')
const fullName = ref('')
const pathNodes = ref<RegionNode[]>([])
const selectedParent = ref<string | null>(null)
const selectedNode = ref<RegionNode | null>(null)
const currentChildren = ref<RegionNode[]>([])

const parentLabel = computed(() => {
  if (!selectedParent.value) return '省级区划'
  return selectedNode.value ? `${selectedNode.value.name} ${selectedNode.value.code}` : selectedParent.value
})

const tableRows = computed<RegionTableRow[]>(() =>
  currentChildren.value.map((item) => ({
    ...item,
    levelName: levelName(item.level)
  }))
)

const columns: DataTableColumns<RegionTableRow> = [
  { title: '名称', key: 'name', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '代码', key: 'code', width: 120, render: (row) => h('span', { class: 'mono' }, row.code) },
  { title: '层级', key: 'levelName', width: 100 },
  { title: '父级代码', key: 'parentCode', width: 120, render: (row) => row.parentCode ? h('span', { class: 'mono' }, row.parentCode) : '-' },
  { title: '排序', key: 'sort', width: 72 },
  {
    title: '节点',
    key: 'leaf',
    width: 88,
    render: (row) => h(StatusTag, { text: row.leaf ? '末级' : '可展开' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row) =>
      h(
        NButton,
        { size: 'small', quaternary: true, type: 'primary', onClick: () => inspectNode(row) },
        { default: () => (row.leaf ? '路径' : '下级') }
      )
  }
]

async function loadChildren(parent?: string | null, sourceNode?: RegionNode | null) {
  loading.value = true
  try {
    const res = await listRegionChildren(parent)
    currentChildren.value = res.data
    selectedParent.value = parent || null
    selectedNode.value = sourceNode || null
  } catch (error) {
    showError(error, '行政区划加载失败')
  } finally {
    loading.value = false
  }
}

async function inspectNode(row: RegionNode) {
  await loadPath(row.code)
  if (!row.leaf) await loadChildren(row.code, row)
}

async function loadPath(code = codeInput.value.trim()) {
  if (!code) {
    message.warning('请输入行政区划代码')
    return
  }
  pathLoading.value = true
  try {
    const res = await getRegionPath(code)
    regionCode.value = res.data.code
    codeInput.value = res.data.code
    fullName.value = res.data.fullName
    pathNodes.value = res.data.nodes
  } catch (error) {
    showError(error, '行政区划路径查询失败')
  } finally {
    pathLoading.value = false
  }
}

function handleCascaderChange(selection: RegionSelection | null) {
  if (!selection) {
    fullName.value = ''
    pathNodes.value = []
    codeInput.value = ''
    return
  }
  codeInput.value = selection.code
  fullName.value = selection.fullName
  pathNodes.value = selection.nodes
}

function levelName(level: number) {
  if (level === 1) return '省级'
  if (level === 2) return '地市级'
  if (level === 3) return '区县级'
  return `第${level}级`
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(() => loadChildren())
</script>

<template>
  <PageContainer title="行政区划" description="广东省三级区划联动查询，生源地等表单复用同一 RegionCascader。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadChildren()">回到省级</n-button>
        <n-button secondary :disabled="!selectedNode?.parentCode" @click="loadChildren(selectedNode?.parentCode || null)">
          返回上级
        </n-button>
      </n-space>
    </template>

    <div class="region-layout">
      <section class="page-section">
        <div class="panel-toolbar">
          <div>
            <strong>{{ parentLabel }}</strong>
            <span class="muted">{{ currentChildren.length }} 个下级区划</span>
          </div>
        </div>
        <n-data-table
          :columns="columns"
          :data="tableRows"
          :loading="loading"
          :row-key="(row: RegionTableRow) => row.code"
          size="small"
          striped
          :max-height="640"
        />
      </section>

      <section class="page-section detail-panel">
        <n-space vertical :size="14">
          <n-form label-placement="top">
            <n-form-item label="级联选择">
              <RegionCascader
                v-model:value="regionCode"
                @update:full-name="fullName = $event"
                @update:path="pathNodes = $event"
                @change="handleCascaderChange"
              />
            </n-form-item>
            <n-form-item label="代码反查">
              <n-input-group>
                <n-input v-model:value="codeInput" clearable maxlength="6" placeholder="输入 6 位行政区划代码" />
                <n-button type="primary" :loading="pathLoading" @click="loadPath()">查询</n-button>
              </n-input-group>
            </n-form-item>
          </n-form>

          <n-descriptions bordered :column="1" size="small">
            <n-descriptions-item label="当前代码">
              <span class="mono">{{ regionCode || '-' }}</span>
            </n-descriptions-item>
            <n-descriptions-item label="完整文本">{{ fullName || '-' }}</n-descriptions-item>
          </n-descriptions>

          <n-space v-if="pathNodes.length" :size="8" class="path-tags">
            <n-tag v-for="node in pathNodes" :key="node.code" type="info" :bordered="false">
              {{ node.name }} · <span class="mono">{{ node.code }}</span>
            </n-tag>
          </n-space>
        </n-space>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.region-layout {
  display: grid;
  grid-template-columns: minmax(560px, 1.3fr) minmax(360px, 0.7fr);
  gap: 16px;
  align-items: start;
}

.page-section {
  min-width: 0;
}

.panel-toolbar {
  margin-bottom: 12px;
}

.panel-toolbar strong,
.panel-toolbar span {
  display: block;
}

.detail-panel {
  min-width: 320px;
}

.path-tags {
  align-items: center;
}

@media (max-width: 1080px) {
  .region-layout {
    grid-template-columns: 1fr;
  }
}
</style>
