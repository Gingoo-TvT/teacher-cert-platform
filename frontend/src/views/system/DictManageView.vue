<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NInput,
  NInputNumber,
  NPopconfirm,
  NSpace,
  NSwitch,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
  updateDictType,
  type DictItem,
  type DictItemPayload,
  type DictType,
  type DictTypePayload
} from '@/api/dict'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const typeLoading = ref(false)
const itemLoading = ref(false)
const saving = ref(false)
const typeFormRef = ref<FormInst | null>(null)
const itemFormRef = ref<FormInst | null>(null)
const typeKeyword = ref('')
const itemKeyword = ref('')
const typeDrawerVisible = ref(false)
const itemDrawerVisible = ref(false)
const editingTypeId = ref<string | null>(null)
const editingItemId = ref<string | null>(null)
const selectedTypeCode = ref('')
const dictTypes = ref<DictType[]>([])
const dictItems = ref<DictItem[]>([])

interface DictTypeFormState {
  typeCode: string
  typeName: string
  description: string
  sort: number
  status: number
}

interface DictItemFormState {
  typeCode: string
  itemCode: string
  itemValue: string
  parentCode: string
  sort: number
  status: number
  yearVersion: string
  extJson: string
}

const typeForm = reactive<DictTypeFormState>({
  typeCode: '',
  typeName: '',
  description: '',
  sort: 0,
  status: 1
})

const itemForm = reactive<DictItemFormState>({
  typeCode: '',
  itemCode: '',
  itemValue: '',
  parentCode: '',
  sort: 0,
  status: 1,
  yearVersion: 'GLOBAL',
  extJson: ''
})

const typeRules: FormRules = {
  typeCode: [
    { required: true, message: '请输入字典类型编码', trigger: ['blur', 'input'] },
    { pattern: /^[A-Za-z0-9_]+$/, message: '仅支持英文、数字、下划线', trigger: ['blur', 'input'] }
  ],
  typeName: [{ required: true, message: '请输入字典类型名称', trigger: ['blur', 'input'] }]
}

const itemRules: FormRules = {
  itemCode: [{ required: true, message: '请输入字典项编码', trigger: ['blur', 'input'] }],
  itemValue: [{ required: true, message: '请输入字典项值', trigger: ['blur', 'input'] }],
  yearVersion: [{ required: true, message: '请输入年度版本', trigger: ['blur', 'input'] }],
  extJson: [
    {
      validator: (_rule, value: string | null | undefined) => {
        if (!value?.trim()) return true
        try {
          JSON.parse(value)
          return true
        } catch {
          return new Error('扩展 JSON 格式不正确')
        }
      },
      trigger: ['blur']
    }
  ]
}

const canManage = computed(() => userStore.hasPerm('dict:manage'))

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
    { title: '排序', key: 'sort', width: 72 },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManage.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        h(NSpace, { size: 6 }, () => [
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openTypeDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => removeType(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '删除字典类型会由后端校验是否存在字典项。'
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
    { title: '排序', key: 'sort', width: 72 },
    { title: '状态', key: 'status', width: 82, render: (row) => h(StatusTag, { text: row.status === 1 ? '启用' : '停用' }) }
  ]
  if (canManage.value) {
    columns.push({
      title: '操作',
      key: 'actions',
      width: 146,
      render: (row) =>
        h(NSpace, { size: 6 }, () => [
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openItemDrawer(row) }, { default: () => '编辑' }),
          h(
            NPopconfirm,
            { onPositiveClick: () => removeItem(row) },
            {
              trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除该字典项？'
            }
          )
        ])
    })
  }
  return columns
})

function typeRowProps(row: DictType) {
  return {
    class: row.typeCode === selectedTypeCode.value ? 'is-selected-row' : '',
    onClick: () => selectType(row)
  }
}

async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await listDictTypes()
    dictTypes.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.typeCode.localeCompare(b.typeCode))
    if (!selectedTypeCode.value && dictTypes.value.length > 0) selectedTypeCode.value = dictTypes.value[0].typeCode
    if (selectedTypeCode.value && !dictTypes.value.some((item) => item.typeCode === selectedTypeCode.value)) {
      selectedTypeCode.value = dictTypes.value[0]?.typeCode || ''
    }
    await loadItems()
  } catch (error) {
    showError(error, '字典类型加载失败')
  } finally {
    typeLoading.value = false
  }
}

async function loadItems(typeCode = selectedTypeCode.value) {
  if (!typeCode) {
    dictItems.value = []
    return
  }
  itemLoading.value = true
  try {
    const res = await listDictItems(typeCode, false)
    dictItems.value = [...res.data].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.itemCode.localeCompare(b.itemCode))
  } catch (error) {
    showError(error, '字典项加载失败')
  } finally {
    itemLoading.value = false
  }
}

function selectType(row: DictType) {
  selectedTypeCode.value = row.typeCode
  itemKeyword.value = ''
  loadItems(row.typeCode)
}

function resetTypeForm() {
  editingTypeId.value = null
  typeForm.typeCode = ''
  typeForm.typeName = ''
  typeForm.description = ''
  typeForm.sort = 0
  typeForm.status = 1
}

function resetItemForm() {
  editingItemId.value = null
  itemForm.typeCode = selectedTypeCode.value
  itemForm.itemCode = ''
  itemForm.itemValue = ''
  itemForm.parentCode = ''
  itemForm.sort = 0
  itemForm.status = 1
  itemForm.yearVersion = 'GLOBAL'
  itemForm.extJson = ''
}

function openTypeDrawer(row?: DictType) {
  resetTypeForm()
  if (row) {
    editingTypeId.value = row.id
    typeForm.typeCode = row.typeCode
    typeForm.typeName = row.typeName
    typeForm.description = row.description || ''
    typeForm.sort = row.sort || 0
    typeForm.status = row.status
  }
  typeDrawerVisible.value = true
}

function openItemDrawer(row?: DictItem) {
  resetItemForm()
  if (row) {
    editingItemId.value = row.id
    itemForm.typeCode = row.typeCode
    itemForm.itemCode = row.itemCode
    itemForm.itemValue = row.itemValue
    itemForm.parentCode = row.parentCode || ''
    itemForm.sort = row.sort || 0
    itemForm.status = row.status
    itemForm.yearVersion = row.yearVersion || 'GLOBAL'
    itemForm.extJson = row.extJson || ''
  }
  itemDrawerVisible.value = true
}

async function saveType() {
  await typeFormRef.value?.validate()
  saving.value = true
  try {
    const payload: DictTypePayload = {
      typeCode: typeForm.typeCode.trim(),
      typeName: typeForm.typeName.trim(),
      description: cleanOptional(typeForm.description),
      sort: typeForm.sort ?? 0,
      status: typeForm.status ?? 1
    }
    if (editingTypeId.value) await updateDictType(editingTypeId.value, payload)
    else await createDictType(payload)
    message.success('字典类型已保存')
    typeDrawerVisible.value = false
    selectedTypeCode.value = payload.typeCode
    await loadTypes()
  } catch (error) {
    showError(error, '字典类型保存失败')
  } finally {
    saving.value = false
  }
}

async function saveItem() {
  await itemFormRef.value?.validate()
  saving.value = true
  try {
    const payload: DictItemPayload = {
      typeCode: itemForm.typeCode.trim(),
      itemCode: itemForm.itemCode.trim(),
      itemValue: itemForm.itemValue.trim(),
      parentCode: cleanOptional(itemForm.parentCode),
      sort: itemForm.sort ?? 0,
      status: itemForm.status ?? 1,
      yearVersion: cleanOptional(itemForm.yearVersion) || 'GLOBAL',
      extJson: cleanOptional(itemForm.extJson)
    }
    if (editingItemId.value) await updateDictItem(editingItemId.value, payload)
    else await createDictItem(payload)
    message.success('字典项已保存')
    itemDrawerVisible.value = false
    await loadItems(payload.typeCode)
  } catch (error) {
    showError(error, '字典项保存失败')
  } finally {
    saving.value = false
  }
}

async function removeType(row: DictType) {
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
  try {
    await deleteDictItem(row.id)
    message.success('字典项已删除')
    await loadItems(row.typeCode)
  } catch (error) {
    showError(error, '字典项删除失败')
  }
}

function cleanOptional(value: string | null | undefined) {
  const text = value?.trim()
  return text ? text : null
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(loadTypes)
</script>

<template>
  <PageContainer title="数据字典" description="标准字段枚举的唯一来源。管理动作按 dict:manage 显隐，无权限账号保持只读浏览。">
    <template #actions>
      <n-space>
        <n-button secondary @click="loadTypes">刷新</n-button>
        <n-button v-if="canManage" type="primary" @click="openTypeDrawer()">新增类型</n-button>
      </n-space>
    </template>

    <div class="master-detail-grid">
      <section class="page-section">
        <div class="panel-toolbar">
          <n-input v-model:value="typeKeyword" clearable placeholder="搜索类型编码或名称" />
        </div>
        <n-data-table
          :columns="typeColumns"
          :data="filteredTypes"
          :loading="typeLoading"
          :row-key="(row: DictType) => row.id"
          :row-props="typeRowProps"
          size="small"
          striped
          :max-height="620"
        />
      </section>

      <section class="page-section">
        <div class="detail-head">
          <div>
            <strong>{{ selectedType?.typeName || '未选择类型' }}</strong>
            <span class="muted mono">{{ selectedTypeCode || '请选择左侧字典类型' }}</span>
          </div>
          <n-space>
            <n-input v-model:value="itemKeyword" clearable placeholder="搜索字典项" style="width: 220px" />
            <n-button secondary :disabled="!selectedTypeCode" @click="loadItems()">刷新项</n-button>
            <n-button v-if="canManage" type="primary" :disabled="!selectedTypeCode" @click="openItemDrawer()">新增项</n-button>
          </n-space>
        </div>
        <n-data-table
          :columns="itemColumns"
          :data="filteredItems"
          :loading="itemLoading"
          :row-key="(row: DictItem) => row.id"
          size="small"
          striped
          :max-height="620"
        />
      </section>
    </div>

    <n-drawer v-model:show="typeDrawerVisible" :width="420" placement="right">
      <n-drawer-content :title="editingTypeId ? '编辑字典类型' : '新增字典类型'">
        <n-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-placement="top">
          <n-form-item label="类型编码" path="typeCode">
            <n-input v-model:value="typeForm.typeCode" :disabled="!!editingTypeId" maxlength="64" show-count />
          </n-form-item>
          <n-form-item label="类型名称" path="typeName">
            <n-input v-model:value="typeForm.typeName" maxlength="128" show-count />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="typeForm.description" type="textarea" maxlength="255" show-count />
          </n-form-item>
          <div class="form-grid">
            <n-form-item label="排序" path="sort">
              <n-input-number v-model:value="typeForm.sort" :min="0" />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-switch v-model:value="typeForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="typeDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveType">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="itemDrawerVisible" :width="480" placement="right">
      <n-drawer-content :title="editingItemId ? '编辑字典项' : '新增字典项'">
        <n-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-placement="top">
          <n-form-item label="类型编码" path="typeCode">
            <n-input v-model:value="itemForm.typeCode" disabled />
          </n-form-item>
          <n-form-item label="项编码" path="itemCode">
            <n-input v-model:value="itemForm.itemCode" :disabled="!!editingItemId" maxlength="128" show-count />
          </n-form-item>
          <n-form-item label="项值" path="itemValue">
            <n-input v-model:value="itemForm.itemValue" maxlength="255" show-count />
          </n-form-item>
          <div class="form-grid">
            <n-form-item label="父级编码" path="parentCode">
              <n-input v-model:value="itemForm.parentCode" maxlength="128" clearable />
            </n-form-item>
            <n-form-item label="年度版本" path="yearVersion">
              <n-input v-model:value="itemForm.yearVersion" maxlength="16" show-count />
            </n-form-item>
          </div>
          <div class="form-grid">
            <n-form-item label="排序" path="sort">
              <n-input-number v-model:value="itemForm.sort" :min="0" />
            </n-form-item>
            <n-form-item label="状态" path="status">
              <n-switch v-model:value="itemForm.status" :checked-value="1" :unchecked-value="0" />
            </n-form-item>
          </div>
          <n-form-item label="扩展 JSON" path="extJson">
            <n-input v-model:value="itemForm.extJson" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="itemDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveItem">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </PageContainer>
</template>

<style scoped>
.master-detail-grid {
  display: grid;
  grid-template-columns: minmax(340px, 0.8fr) minmax(560px, 1.2fr);
  gap: 16px;
  align-items: start;
}

.page-section {
  min-width: 0;
}

.panel-toolbar {
  margin-bottom: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-head strong,
.detail-head span {
  display: block;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

:deep(.is-selected-row td) {
  background: #eef5ff;
}

@media (max-width: 1180px) {
  .master-detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
