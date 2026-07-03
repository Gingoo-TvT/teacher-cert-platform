<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  chartAxisColor,
  chartAxisLineColor,
  chartPalette,
  chartSingleBarColor,
  chartSplitLineColor,
  chartTooltipBorderColor
} from '@/theme/tokens'

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
  const hasLongLabels = maxLabelLength > 12
  const hasCrowdedLabels = categoryLabels.length > 10
  const rotate = hasLongLabels || hasCrowdedLabels ? 45 : 0
  const labelWidth = rotate ? Math.min(180, Math.max(112, maxLabelLength * 8)) : 120
  const bottom = rotate ? Math.min(156, Math.max(92, maxLabelLength * 6 + 54)) : Math.max(48, Math.min(76, maxLabelLength * 4 + 32))
  const tooltip = isObject(option.tooltip) ? option.tooltip : {}
  const tooltipTextStyle = isObject(tooltip.textStyle) ? tooltip.textStyle : {}
  const seriesCount = Array.isArray(option.series) ? option.series.length : option.series ? 1 : 0
  const legend = isObject(option.legend) ? option.legend : {}

  return {
    color: [...chartPalette],
    ...option,
    tooltip: {
      trigger: 'axis',
      borderColor: chartTooltipBorderColor,
      borderRadius: 12,
      padding: [10, 12],
      confine: true,
      ...tooltip,
      textStyle: {
        color: chartAxisColor,
        fontSize: 13,
        ...tooltipTextStyle
      }
    },
    legend: seriesCount > 1
      ? {
          top: 0,
          right: 0,
          itemWidth: 10,
          itemHeight: 10,
          textStyle: { color: chartAxisColor },
          ...legend
        }
      : {
          ...legend,
          show: false
        },
    grid: {
      left: 48,
      right: 24,
      top: seriesCount > 1 ? 48 : 28,
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
      axisLine: {
        ...(isObject(axis.axisLine) ? axis.axisLine : {}),
        lineStyle: {
          ...(isObject((axis.axisLine as Record<string, unknown> | undefined)?.lineStyle)
            ? (axis.axisLine as Record<string, Record<string, unknown>>).lineStyle
            : {}),
          color: chartAxisLineColor
        }
      },
      axisLabel: {
        ...(isObject(axis.axisLabel) ? axis.axisLabel : {}),
        interval: 0,
        width: labelWidth,
        overflow: rotate ? 'break' : 'truncate',
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
        },
        axisLine: {
          ...(isObject(axis.axisLine) ? axis.axisLine : {}),
          lineStyle: {
            ...(isObject((axis.axisLine as Record<string, unknown> | undefined)?.lineStyle)
              ? (axis.axisLine as Record<string, Record<string, unknown>>).lineStyle
              : {}),
            color: chartAxisLineColor
          }
        },
        axisLabel: {
          ...(isObject(axis.axisLabel) ? axis.axisLabel : {}),
          color: chartAxisColor
        }
      }
    }) as echarts.EChartsOption['yAxis'],
    series: normalizeSeries(option.series, seriesCount) as echarts.EChartsOption['series']
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

function normalizeSeries(series: echarts.EChartsOption['series'], seriesCount: number) {
  const normalize = (item: unknown) => {
    if (!isObject(item) || item.type !== 'bar') return item
    const itemStyle = isObject(item.itemStyle) ? item.itemStyle : {}
    return {
      barMaxWidth: 34,
      ...item,
      itemStyle: {
        ...itemStyle,
        color: seriesCount === 1 ? chartSingleBarColor : itemStyle.color,
        borderRadius: [4, 4, 0, 0]
      }
    }
  }
  return Array.isArray(series) ? series.map(normalize) : normalize(series)
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
