<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NInput,
  NPopconfirm,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import DetailPanel from '@/components/DetailPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import { renderTableActions } from '@/utils/tableActions'
import {
  deleteDictItem,
  deleteDictType,
  listDictItems,
  listDictTypes,
  type DictItem,
  type DictType
} from '@/api/dict'
import { useUserStore } from '@/stores/user'
import DictTypeDrawer from './components/dict/DictTypeDrawer.vue'
import DictItemDrawer from './components/dict/DictItemDrawer.vue'

const message = useMessage()
const userStore = useUserStore()

const typeLoading = ref(true)
const itemLoading = ref(true)
const typeError = ref('')
const itemError = ref('')
const hasLoadedTypes = ref(false)
const hasLoadedItems = ref(false)
const typeKeyword = ref('')
const itemKeyword = ref('')
const selectedTypeCode = ref('')
const dictTypes = ref<DictType[]>([])
const dictItems = ref<DictItem[]>([])
const typeDrawerRef = ref<InstanceType<typeof DictTypeDrawer> | null>(null)
const itemDrawerRef = ref<InstanceType<typeof DictItemDrawer> | null>(null)
const loadedItemsTypeCode = ref('')
let typesRequestId = 0
let itemsRequestId = 0

const canManage = computed(() => userStore.hasPerm('dict:manage'))
const typeDataFresh = computed(() => hasLoadedTypes.value && !typeLoading.value && !typeError.value)
const itemDataFresh = computed(() =>
  hasLoadedItems.value
  && loadedItemsTypeCode.value === selectedTypeCode.value
  && !itemLoading.value
  && !itemError.value
  && typeDataFresh.value
)

const filteredTypes = computed(() => {
  const keyword = typeKeyword.value.trim().toLowerCase()
  if (!keyword) return dictTypes.value
  return dictTypes.value.filter((item) =>
    [item.typeCode, item.typeName, item.description || ''].some((text) => text.toLowerCase().includes(keyword))
  )
})

const filteredItems = computed(() => {
  const keyword = itemKeyword.value.trim().toLowerCase()
  if (!keyword) return dictItems.value
  return dictItems.value.filter((item) =>
    [item.itemCode, item.itemValue, item.parentCode || '', item.yearVersion, item.extJson || ''].some((text) =>
      text.toLowerCase().includes(keyword)
    )
  )
})

const selectedType = computed(() => dictTypes.value.find((item) => item.typeCode === selectedTypeCode.value) || null)

const typeColumns = computed<DataTableColumns<DictType>>(() => {
  const columns: DataTableColumns<DictType> = [
    { title: '类型编码', key: 'typeCode', minWidth: 170, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.typeCode) },
    { title: '类型名称', key: 'typeName', minWidth: 150, ellipsis: { tooltip: true } },
    { title: '排序', key: 'sort', width: 72, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManage.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        renderTableActions([
          h(NButton, { size: 'small', quaternary: true, disabled: !typeDataFresh.value, onClick: () => openTypeDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { disabled: !typeDataFresh.value, onPositiveClick: () => removeType(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error', disabled: !typeDataFresh.value }, { default: () => '删除' }),
              default: () => '删除字典类型会校验是否存在字典项。'
            }
          )
        ])
    })
  }
  return columns
})

const itemColumns = computed<DataTableColumns<DictItem>>(() => {
  const columns: DataTableColumns<DictItem> = [
    { title: '项编码', key: 'itemCode', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.itemCode) },
    { title: '项值', key: 'itemValue', minWidth: 190, ellipsis: { tooltip: true } },
    { title: '父级编码', key: 'parentCode', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => row.parentCode ? h('span', { class: 'mono' }, row.parentCode) : '-' },
    { title: '年度', key: 'yearVersion', width: 104, render: (row) => h('span', { class: 'mono' }, row.yearVersion) },
    { title: '排序', key: 'sort', width: 72, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.sort ?? 0)) },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManage.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        renderTableActions([
          h(NButton, { size: 'small', quaternary: true, disabled: !itemDataFresh.value, onClick: () => openItemDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { disabled: !itemDataFresh.value, onPositiveClick: () => removeItem(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error', disabled: !itemDataFresh.value }, { default: () => '删除' }),
              default: () => '确认删除该字典项？'
            }
          )
        ])
    })
  }
  return columns
})

const typeDetailItems = computed(() => {
  const row = selectedType.value
  if (!row) return []
  return [
    { label: '类型编码', value: row.typeCode, mono: true },
    { label: '类型名称', value: row.typeName },
    { label: '状态', value: row.status === 1 ? '启用' : '停用' },
    { label: '排序', value: row.sort ?? 0, mono: true },
    { label: '说明', value: row.description || '-', span: 2 }
  ]
})

function typeRowProps(row: object) {
  const item = row as DictType
  return {
    class: item.typeCode === selectedTypeCode.value ? 'is-selected-row' : '',
    onClick: () => selectType(item)
  }
}

async function loadTypes() {
  const requestId = ++typesRequestId
  typeLoading.value = true
  typeError.value = ''
  try {
    const res = await listDictTypes()
    if (requestId !== typesRequestId) return
    dictTypes.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.typeCode.localeCompare(b.typeCode))
    hasLoadedTypes.value = true
    if (!selectedTypeCode.value && dictTypes.value.length > 0) selectedTypeCode.value = dictTypes.value[0].typeCode
    if (selectedTypeCode.value && !dictTypes.value.some((item) => item.typeCode === selectedTypeCode.value)) {
      selectedTypeCode.value = dictTypes.value[0]?.typeCode || ''
    }
    await loadItems()
  } catch (error) {
    if (requestId !== typesRequestId) return
    typeError.value = showError(error, '字典类型加载失败')
    if (selectedTypeCode.value) {
      await loadItems()
    } else {
      itemLoading.value = false
      itemError.value = '字典类型尚未加载，暂时无法确定字典项'
    }
  } finally {
    if (requestId === typesRequestId) typeLoading.value = false
  }
}

async function loadItems(typeCode = selectedTypeCode.value) {
  const requestId = ++itemsRequestId
  if (!typeCode) {
    dictItems.value = []
    loadedItemsTypeCode.value = ''
    itemLoading.value = false
    if (typeError.value) {
      itemError.value = '字典类型尚未加载，暂时无法确定字典项'
      hasLoadedItems.value = false
    } else {
      itemError.value = ''
      hasLoadedItems.value = true
    }
    return
  }
  itemLoading.value = true
  itemError.value = ''
  try {
    const res = await listDictItems(typeCode, false)
    if (requestId !== itemsRequestId || typeCode !== selectedTypeCode.value) return
    dictItems.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
    hasLoadedItems.value = true
    loadedItemsTypeCode.value = typeCode
  } catch (error) {
    if (requestId !== itemsRequestId || typeCode !== selectedTypeCode.value) return
    itemError.value = showError(error, '字典项加载失败')
  } finally {
    if (requestId === itemsRequestId) itemLoading.value = false
  }
}

function selectType(row: DictType) {
  const typeChanged = selectedTypeCode.value !== row.typeCode
  selectedTypeCode.value = row.typeCode
  itemKeyword.value = ''
  if (typeChanged) {
    dictItems.value = []
    hasLoadedItems.value = false
    loadedItemsTypeCode.value = ''
  }
  void loadItems(row.typeCode)
}

function resetTypeFilters() {
  typeKeyword.value = ''
}

function resetItemFilters() {
  itemKeyword.value = ''
}

function openTypeDrawer(row?: DictType) {
  if (!typeDataFresh.value) return
  typeDrawerRef.value?.open(row)
}

function openItemDrawer(row?: DictItem) {
  if (!itemDataFresh.value) return
  itemDrawerRef.value?.open(row)
}

function retryItems() {
  if (selectedTypeCode.value) {
    void loadItems()
    return
  }
  void loadTypes()
}

function onTypeSaved(typeCode: string) {
  selectedTypeCode.value = typeCode.trim().toLowerCase()
  loadTypes()
}

function onItemSaved(typeCode: string) {
  loadItems(typeCode)
}

async function removeType(row: DictType) {
  if (!typeDataFresh.value) return
  try {
    await deleteDictType(row.id)
    message.success('字典类型已删除')
    if (selectedTypeCode.value === row.typeCode) selectedTypeCode.value = ''
    await loadTypes()
  } catch (error) {
    showError(error, '字典类型删除失败')
  }
}

async function removeItem(row: DictItem) {
  if (!itemDataFresh.value) return
  try {
    await deleteDictItem(row.id)
    message.success('字典项已删除')
    await loadItems(row.typeCode)
  } catch (error) {
    showError(error, '字典项删除失败')
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
  return detail || fallback
}

onMounted(loadTypes)
</script>

<template>
  <PageContainer title="数据字典" description="维护系统数据字典与标准字段枚举。">
    <n-grid responsive="self" item-responsive cols="1 1180:12" :x-gap="24" :y-gap="24">
      <n-gi span="1 1180:5" class="page-section">
        <FilterBar :loading="typeLoading" @submit="loadTypes" @reset="resetTypeFilters">
          <label class="filter-field">
            <span>类型</span>
            <n-input v-model:value="typeKeyword" clearable placeholder="编码 / 名称" style="width: 220px; max-width: 100%" />
          </label>
        </FilterBar>
        <DataPanel
          title="字典类型"
          :columns="typeColumns"
          :data="filteredTypes"
          :total="filteredTypes.length"
          :loading="typeLoading"
          :error="typeError"
          error-title="字典类型加载失败"
          :row-props="typeRowProps"
          :max-height="620"
          :pagination="false"
          empty-title="暂无字典类型"
          empty-description="当前筛选条件下没有字典类型。"
          @refresh="loadTypes"
        >
          <template #actions>
            <n-button v-if="canManage" type="primary" size="small" :disabled="!typeDataFresh" @click="openTypeDrawer()">新增类型</n-button>
          </template>
        </DataPanel>
      </n-gi>

      <n-gi span="1 1180:7" class="page-section">
        <n-card :bordered="false" class="detail-card">
          <div class="detail-head">
            <div>
              <strong>{{ selectedType?.typeName || '未选择类型' }}</strong>
              <span class="muted mono">{{ selectedTypeCode || '请选择左侧字典类型' }}</span>
            </div>
          </div>
          <DetailPanel v-if="selectedType" :items="typeDetailItems" :columns="2" />
          <n-empty v-else description="请选择左侧字典类型" />
        </n-card>

        <FilterBar :loading="itemLoading" @submit="loadItems()" @reset="resetItemFilters">
          <label class="filter-field">
            <span>字典项</span>
            <n-input v-model:value="itemKeyword" clearable placeholder="编码 / 值 / 父级" style="width: 240px; max-width: 100%" />
          </label>
        </FilterBar>
        <DataPanel
          title="字典项"
          :columns="itemColumns"
          :data="filteredItems"
          :total="filteredItems.length"
          :loading="itemLoading"
          :error="itemError"
          error-title="字典项加载失败"
          :max-height="620"
          :pagination="false"
          empty-title="暂无字典项"
          empty-description="当前字典类型下没有可展示的字典项。"
          @refresh="retryItems"
        >
          <template #actions>
            <n-button v-if="canManage" type="primary" size="small" :disabled="!selectedTypeCode || !itemDataFresh" @click="openItemDrawer()">新增项</n-button>
          </template>
        </DataPanel>
      </n-gi>
    </n-grid>

    <DictTypeDrawer ref="typeDrawerRef" @saved="onTypeSaved" />
    <DictItemDrawer ref="itemDrawerRef" :type-code="selectedTypeCode" @saved="onItemSaved" />
  </PageContainer>
</template>

<style scoped>
.page-section {
  min-width: 0;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.detail-head strong,
.detail-head span {
  display: block;
}

.detail-card {
  margin-bottom: var(--space-5);
}

.detail-card :deep(.n-card__content) {
  padding: var(--space-5);
}

:deep(.is-selected-row td) {
  background: var(--brand-soft);
}

@media (max-width: 1180px) {
  .detail-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
