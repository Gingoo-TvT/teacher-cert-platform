<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, useMessage, type DataTableColumns } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import RegionCascader, { type RegionSelection } from '@/components/RegionCascader.vue'
import { getRegionPath, listRegionChildren, type RegionNode } from '@/api/region'
import { renderTableActions } from '@/utils/tableActions'

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
  { title: '排序', key: 'sort', width: 72, render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
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
      renderTableActions([
        h(
          NButton,
          { size: 'small', quaternary: true, onClick: () => inspectNode(row) },
          { default: () => (row.leaf ? '路径' : '下级') }
        )
      ])
  }
]

const regionDetailItems = computed(() => [
  { label: '当前代码', value: regionCode.value, mono: true },
  { label: '完整文本', value: fullName.value, span: 2 },
  { label: '路径层级', value: pathNodes.value.length ? `${pathNodes.value.length} 级` : '-' },
  { label: '当前列表', value: parentLabel.value }
])

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

function rowProps(row: object) {
  const item = row as RegionTableRow
  return {
    class: item.code === regionCode.value ? 'is-selected-row' : '',
    onClick: () => {
      regionCode.value = item.code
      codeInput.value = item.code
      fullName.value = item.name
      pathNodes.value = []
    }
  }
}

function resetPathQuery() {
  regionCode.value = null
  codeInput.value = ''
  fullName.value = ''
  pathNodes.value = []
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
  <PageContainer title="行政区划" description="行政区划三级联动查询。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadChildren()">回到省级</n-button>
        <n-button secondary :disabled="!selectedNode?.parentCode" @click="loadChildren(selectedNode?.parentCode || null)">
          返回上级
        </n-button>
      </n-space>
    </template>

    <div class="region-layout">
      <div class="page-section">
        <DataPanel
          :title="parentLabel"
          :columns="columns"
          :data="tableRows"
          :total="tableRows.length"
          :loading="loading"
          :row-props="rowProps"
          :max-height="640"
          :pagination="false"
          empty-title="暂无下级区划"
          empty-description="当前层级下没有可展示的行政区划。"
          @refresh="loadChildren(selectedParent, selectedNode)"
        >
          <template #actions>
            <n-button size="small" secondary @click="loadChildren()">省级</n-button>
            <n-button size="small" secondary :disabled="!selectedNode?.parentCode" @click="loadChildren(selectedNode?.parentCode || null)">
              上级
            </n-button>
          </template>
        </DataPanel>
      </div>

      <div class="page-section detail-panel">
        <FilterBar :loading="pathLoading" @submit="loadPath()" @reset="resetPathQuery">
          <label class="filter-field filter-field--wide">
            <span>级联选择</span>
            <div class="filter-control">
              <RegionCascader
                v-model:value="regionCode"
                @update:full-name="fullName = $event"
                @update:path="pathNodes = $event"
                @change="handleCascaderChange"
              />
            </div>
          </label>
          <template #more>
            <label class="filter-field">
              <span>代码</span>
              <n-input-group>
                <n-input v-model:value="codeInput" clearable maxlength="6" placeholder="输入 6 位行政区划代码" />
              </n-input-group>
            </label>
          </template>
        </FilterBar>

        <n-card :bordered="false" class="detail-card">
          <div class="detail-head">
            <div>
              <strong>{{ fullName || '区划详情' }}</strong>
              <span class="muted mono">{{ regionCode || '请选择或输入行政区划代码' }}</span>
            </div>
          </div>
          <DetailPanel :items="regionDetailItems" :columns="2" />

          <n-space v-if="pathNodes.length" :size="8" class="path-tags">
            <n-tag v-for="node in pathNodes" :key="node.code" type="info" :bordered="false">
              {{ node.name }} · <span class="mono">{{ node.code }}</span>
            </n-tag>
          </n-space>
        </n-card>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.region-layout {
  display: grid;
  grid-template-columns: minmax(560px, 1.3fr) minmax(360px, 0.7fr);
  gap: var(--space-6);
  align-items: start;
}

.page-section {
  min-width: 0;
}

.detail-panel {
  min-width: 320px;
}

.detail-card :deep(.n-card__content) {
  padding: var(--space-5);
}

.detail-head {
  margin-bottom: var(--space-4);
}

.detail-head strong,
.detail-head span {
  display: block;
}

.filter-field--wide {
  flex: 1 1 280px;
}

.filter-control {
  min-width: 280px;
}

.path-tags {
  align-items: center;
  margin-top: var(--space-4);
}

:deep(.is-selected-row td) {
  background: var(--brand-soft);
}

@media (max-width: 1080px) {
  .region-layout {
    grid-template-columns: 1fr;
  }
}
</style>
