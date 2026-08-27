<script setup lang="ts">
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { LabelLayout, UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  chartAxisColor,
  chartAxisLineColor,
  chartPalette,
  chartSingleBarColor,
  chartSplitLineColor,
  chartTooltipBorderColor
} from '@/theme/tokens'

use([
  BarChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer
])

const props = defineProps<{
  option: EChartsOption
  height?: string
}>()

const el = ref<HTMLElement | null>(null)
let chart: ReturnType<typeof init> | null = null
let resizeObserver: ResizeObserver | null = null

const chartLayout = computed(() => {
  const labels = firstCategoryLabels(props.option.xAxis)
  const maxLen = labels.reduce((max, label) => Math.max(max, visibleLength(label)), 0)
  const rotate = maxLen > 12 || labels.length > 10 ? 45 : 0
  const labelWidth = rotate ? Math.min(180, Math.max(112, maxLen * 8)) : 120
  const bottom = rotate ? Math.min(156, Math.max(92, maxLen * 6 + 54)) : Math.max(48, Math.min(76, maxLen * 4 + 32))
  const seriesCount = Array.isArray(props.option.series) ? props.option.series.length : props.option.series ? 1 : 0
  const top = seriesCount > 1 ? 48 : 28
  return { rotate, labelWidth, bottom, top }
})

// 旋转/换行的长类目会撑高 grid.bottom；容器高度需同步增高，否则绘图区被压扁。
const resolvedHeight = computed(() => {
  const base = Number.parseInt(props.height || '320', 10) || 320
  if (isPieOption(props.option)) return `${base}px`
  const needed = chartLayout.value.top + 184 + chartLayout.value.bottom
  return `${Math.max(base, needed)}px`
})

const normalizedOption = computed(() =>
  isPieOption(props.option) ? normalizePieOption(props.option) : normalizeOption(props.option)
)

function isPieOption(option: EChartsOption): boolean {
  const series = Array.isArray(option.series) ? option.series[0] : option.series
  return isObject(series) && series.type === 'pie'
}

// 饼/环形图：无坐标轴与 grid，只注入色板 + item 触发的 tooltip + 标签默认。
function normalizePieOption(option: EChartsOption): EChartsOption {
  const tooltip = isObject(option.tooltip) ? option.tooltip : {}
  return {
    color: [...chartPalette],
    ...option,
    tooltip: {
      trigger: 'item',
      borderColor: chartTooltipBorderColor,
      borderRadius: 12,
      padding: [10, 12],
      confine: true,
      textStyle: { color: chartAxisColor, fontSize: 13 },
      ...tooltip
    }
  }
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  if (!el.value) return
  const instance = init(el.value)
  chart = instance
  instance.setOption(normalizedOption.value)
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

function normalizeOption(option: EChartsOption): EChartsOption {
  const { rotate, labelWidth, bottom } = chartLayout.value
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
    })) as EChartsOption['xAxis'],
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
    }) as EChartsOption['yAxis'],
    series: normalizeSeries(option.series, seriesCount, isCategoryAxis(option.yAxis)) as EChartsOption['series']
  }
}

function normalizeAxis(
  axis: unknown,
  normalize: (axis: Record<string, unknown>) => Record<string, unknown>
) {
  if (Array.isArray(axis)) return axis.map((item) => normalize(isObject(item) ? item : {}))
  return normalize(isObject(axis) ? axis : {})
}

function firstCategoryLabels(axis: EChartsOption['xAxis']) {
  const firstAxis = Array.isArray(axis) ? axis[0] : axis
  if (!isObject(firstAxis) || !Array.isArray(firstAxis.data)) return []
  return firstAxis.data.map((item) => {
    if (typeof item === 'string' || typeof item === 'number') return String(item)
    if (isObject(item) && (typeof item.value === 'string' || typeof item.value === 'number')) return String(item.value)
    return ''
  })
}

function isCategoryAxis(axis: unknown): boolean {
  const first = Array.isArray(axis) ? axis[0] : axis
  return isObject(first) && first.type === 'category'
}

function normalizeSeries(series: EChartsOption['series'], seriesCount: number, horizontal = false) {
  const normalize = (item: unknown) => {
    if (!isObject(item) || item.type !== 'bar') return item
    const itemStyle = isObject(item.itemStyle) ? item.itemStyle : {}
    return {
      barMaxWidth: 34,
      ...item,
      itemStyle: {
        ...itemStyle,
        color: seriesCount === 1 ? chartSingleBarColor : itemStyle.color,
        borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]
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
  <div ref="el" :style="{ height: resolvedHeight, width: '100%' }" />
</template>
