import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const perms = ref<string[]>([])
  const username = ref<string>('')

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function logout() {
    token.value = ''
    perms.value = []
    localStorage.removeItem('token')
  }

  function hasPerm(code: string): boolean {
    return perms.value.includes(code)
  }

  return { token, perms, username, setToken, logout, hasPerm }
})
