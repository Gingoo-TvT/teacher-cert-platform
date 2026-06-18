<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { chartAxisColor, chartPalette, chartSplitLineColor } from '@/theme/tokens'

const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const normalizedOption = computed(() => normalizeOption(props.option))

function resize() {
  chart?.resize()
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.setOption(normalizedOption.value)
  window.addEventListener('resize', resize)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(el.value)
})

watch(
  normalizedOption,
  (option) => chart?.setOption(option, true),
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  chart?.dispose()
})

function normalizeOption(option: echarts.EChartsOption): echarts.EChartsOption {
  const categoryLabels = firstCategoryLabels(option.xAxis)
  const maxLabelLength = categoryLabels.reduce((max, label) => Math.max(max, visibleLength(label)), 0)
  const rotate = maxLabelLength > 14 || categoryLabels.length > 10 ? 30 : 0
  const labelWidth = rotate ? 86 : 104
  const bottom = rotate ? 82 : Math.max(48, Math.min(72, maxLabelLength * 4 + 32))

  return {
    color: [...chartPalette],
    tooltip: { trigger: 'axis' },
    ...option,
    grid: {
      left: 48,
      right: 24,
      top: 28,
      bottom,
      containLabel: true,
      ...(isObject(option.grid) ? option.grid : {})
    },
    xAxis: normalizeAxis(option.xAxis, (axis) => ({
      type: 'category',
      ...axis,
      axisTick: {
        ...(isObject(axis.axisTick) ? axis.axisTick : {}),
        alignWithLabel: true
      },
      axisLabel: {
        ...(isObject(axis.axisLabel) ? axis.axisLabel : {}),
        interval: 0,
        width: labelWidth,
        overflow: 'truncate',
        hideOverlap: true,
        rotate,
        color: chartAxisColor
      }
    })) as echarts.EChartsOption['xAxis'],
    yAxis: normalizeAxis(option.yAxis, (axis) => {
      const splitLine = isObject(axis.splitLine) ? axis.splitLine : {}
      const lineStyle = isObject(splitLine.lineStyle) ? splitLine.lineStyle : {}
      return {
        type: 'value',
        ...axis,
        min: 0,
        minInterval: 1,
        splitLine: {
          ...splitLine,
          lineStyle: {
            ...lineStyle,
            color: chartSplitLineColor
          }
        }
      }
    }) as echarts.EChartsOption['yAxis']
  }
}

function normalizeAxis(
  axis: unknown,
  normalize: (axis: Record<string, unknown>) => Record<string, unknown>
) {
  if (Array.isArray(axis)) return axis.map((item) => normalize(isObject(item) ? item : {}))
  return normalize(isObject(axis) ? axis : {})
}

function firstCategoryLabels(axis: echarts.EChartsOption['xAxis']) {
  const firstAxis = Array.isArray(axis) ? axis[0] : axis
  if (!isObject(firstAxis) || !Array.isArray(firstAxis.data)) return []
  return firstAxis.data.map((item) => {
    if (typeof item === 'string' || typeof item === 'number') return String(item)
    if (isObject(item) && (typeof item.value === 'string' || typeof item.value === 'number')) return String(item.value)
    return ''
  })
}

function visibleLength(value: string) {
  return value.replace(/\n/g, '').length
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}
</script>

<template>
  <div ref="el" :style="{ height: height || '320px', width: '100%' }" />
</template>
