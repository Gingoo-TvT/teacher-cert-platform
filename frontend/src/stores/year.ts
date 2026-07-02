import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'assessmentYear'

function defaultYear() {
  return String(new Date().getFullYear())
}

export const useYearStore = defineStore('year', () => {
  const assessmentYear = ref(localStorage.getItem(STORAGE_KEY) || defaultYear())

  const yearOptions = computed(() => {
    const current = Number(defaultYear())
    const selected = Number(assessmentYear.value)
    const years = new Set<number>()
    for (let year = current - 2; year <= current + 2; year += 1) years.add(year)
    if (Number.isFinite(selected)) years.add(selected)
    return [...years].sort((a, b) => b - a).map(String)
  })

  function setYear(year: string) {
    const text = year.trim()
    if (!/^\d{4}$/.test(text)) return
    assessmentYear.value = text
    localStorage.setItem(STORAGE_KEY, text)
  }

  return {
    assessmentYear,
    yearOptions,
    setYear
  }
})
