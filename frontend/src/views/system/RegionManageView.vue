<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NTag, useMessage, type DataTableColumns } from 'naive-ui'
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
  { title: '代码', key: 'code', width: 120 },
  { title: '层级', key: 'levelName', width: 110 },
  { title: '父级代码', key: 'parentCode', width: 120 },
  { title: '排序', key: 'sort', width: 72 },
  {
    title: '节点',
    key: 'leaf',
    width: 88,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.leaf ? 'default' : 'info', bordered: false },
        { default: () => (row.leaf ? '末级' : '可展开') }
      )
  },
  {
    title: '操作',
    key: 'actions',
    width: 156,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          type: 'primary',
          onClick: () => inspectNode(row)
        },
        { default: () => (row.leaf ? '查看路径' : '查看下级') }
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
  if (!row.leaf) {
    await loadChildren(row.code, row)
  }
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
  <n-space vertical :size="16" class="region-page">
    <div class="page-head">
      <div>
        <n-h2 class="page-title">行政区划</n-h2>
      </div>
      <n-button secondary @click="loadChildren()">省级区划</n-button>
    </div>

    <div class="region-layout">
      <section class="region-panel">
        <div class="panel-toolbar">
          <div class="selected-title">
            <strong>{{ parentLabel }}</strong>
            <n-text depth="3">{{ currentChildren.length }} 个下级区划</n-text>
          </div>
          <n-button secondary :disabled="!selectedNode?.parentCode" @click="loadChildren(selectedNode?.parentCode || null)">
            返回上级
          </n-button>
        </div>
        <n-data-table
          :columns="columns"
          :data="tableRows"
          :loading="loading"
          :row-key="(row: RegionTableRow) => row.code"
          size="small"
          striped
          :max-height="620"
        />
      </section>

      <section class="region-panel detail-panel">
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

          <div class="path-result">
            <n-descriptions bordered :column="1" size="small">
              <n-descriptions-item label="当前代码">{{ regionCode || '-' }}</n-descriptions-item>
              <n-descriptions-item label="完整文本">{{ fullName || '-' }}</n-descriptions-item>
            </n-descriptions>
            <n-space v-if="pathNodes.length" class="path-tags" :size="8">
              <n-tag v-for="node in pathNodes" :key="node.code" type="info" bordered>
                {{ node.name }} · {{ node.code }}
              </n-tag>
            </n-space>
          </div>
        </n-space>
      </section>
    </div>
  </n-space>
</template>

<style scoped>
.region-page {
  min-width: 960px;
}

.page-head,
.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-title {
  margin: 0 0 4px;
}

.region-layout {
  display: grid;
  grid-template-columns: minmax(560px, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
  align-items: start;
}

.region-panel {
  min-width: 0;
}

.panel-toolbar {
  margin-bottom: 12px;
}

.selected-title {
  display: flex;
  flex-direction: column;
  min-width: 180px;
}

.path-result {
  display: grid;
  gap: 12px;
}

.path-tags {
  align-items: center;
}

@media (max-width: 1100px) {
  .region-page {
    min-width: 0;
  }

  .region-layout {
    grid-template-columns: 1fr;
  }

  .page-head,
  .panel-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
