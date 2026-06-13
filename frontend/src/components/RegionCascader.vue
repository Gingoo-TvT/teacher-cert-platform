<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { CascaderOption } from 'naive-ui'
import { getRegionPath, listRegionChildren, type RegionNode, type RegionPath } from '@/api/region'

const props = withDefaults(
  defineProps<{
    value?: string | null
    disabled?: boolean
    placeholder?: string
    clearable?: boolean
  }>(),
  {
    value: null,
    disabled: false,
    placeholder: '请选择行政区划',
    clearable: true
  }
)

const emit = defineEmits<{
  'update:value': [value: string | null]
  'update:path': [nodes: RegionNode[]]
  'update:fullName': [fullName: string]
  change: [payload: RegionSelection | null]
}>()

export interface RegionSelection {
  code: string
  codes: string[]
  nodes: RegionNode[]
  fullName: string
}

interface RegionOption extends CascaderOption {
  label: string
  value: string
  raw: RegionNode
  children?: RegionOption[]
}

const selectedCode = ref<string | null>(props.value || null)
const options = ref<RegionOption[]>([])
const initializing = ref(false)

watch(
  () => props.value,
  async (value) => {
    if (value === selectedCode.value) return
    if (!value) {
      selectedCode.value = null
      emitEmpty()
      return
    }
    await applyPath(value, false)
  }
)

async function loadRoot() {
  const res = await listRegionChildren()
  options.value = res.data.map(toOption)
}

async function handleLoad(option: CascaderOption) {
  const regionOption = option as RegionOption
  const res = await listRegionChildren(regionOption.value)
  if (res.data.length === 0) {
    regionOption.isLeaf = true
    regionOption.children = undefined
    return
  }
  regionOption.children = res.data.map(toOption)
}

async function handleUpdate(value: string | number | Array<string | number> | null) {
  const code = typeof value === 'string' ? value : null
  selectedCode.value = code
  emit('update:value', code)
  if (!code) {
    emitEmpty()
    return
  }
  try {
    const res = await getRegionPath(code)
    emitSelection(res.data)
  } catch {
    emit('update:path', [])
    emit('update:fullName', '')
  }
}

async function applyPath(code: string, notify: boolean) {
  initializing.value = true
  try {
    const res = await getRegionPath(code)
    await ensurePathOptions(res.data.nodes)
    selectedCode.value = res.data.code
    if (notify) emit('update:value', res.data.code)
    emitSelection(res.data)
  } finally {
    initializing.value = false
  }
}

async function ensurePathOptions(nodes: RegionNode[]) {
  if (options.value.length === 0) {
    await loadRoot()
  }
  let currentOptions = options.value
  for (const node of nodes) {
    let option = currentOptions.find((item) => item.value === node.code)
    if (!option) {
      option = toOption(node)
      currentOptions.push(option)
      currentOptions.sort(sortOptions)
    }
    if (!node.leaf && !option.children) {
      const res = await listRegionChildren(node.code)
      if (res.data.length === 0) {
        option.isLeaf = true
      } else {
        option.children = res.data.map(toOption)
      }
    }
    currentOptions = option.children || []
  }
}

function emitSelection(path: RegionPath) {
  const codes = path.nodes.map((node) => node.code)
  emit('update:path', path.nodes)
  emit('update:fullName', path.fullName)
  emit('change', {
    code: path.code,
    codes,
    nodes: path.nodes,
    fullName: path.fullName
  })
}

function emitEmpty() {
  emit('update:path', [])
  emit('update:fullName', '')
  emit('change', null)
}

function toOption(node: RegionNode): RegionOption {
  return {
    label: `${node.name} ${node.code}`,
    value: node.code,
    raw: node,
    isLeaf: node.leaf
  }
}

function sortOptions(a: RegionOption, b: RegionOption) {
  return (a.raw.sort || 0) - (b.raw.sort || 0) || a.value.localeCompare(b.value)
}

onMounted(async () => {
  await loadRoot()
  if (props.value) {
    await applyPath(props.value, false)
  }
})
</script>

<template>
  <n-cascader
    v-model:value="selectedCode"
    :options="options"
    :loading="initializing"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    remote
    check-strategy="all"
    expand-trigger="click"
    :on-load="handleLoad"
    @update:value="handleUpdate"
  />
</template>
