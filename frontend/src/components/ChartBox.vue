<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function resize() {
  chart?.resize()
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  (option) => chart?.setOption(option, true),
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <div ref="el" :style="{ height: height || '320px', width: '100%' }" />
</template>
