import { defineStore } from 'pinia'
import { load, save } from '@/utils/storage'

const KEY = 'oj-theme'

function initialDark(): boolean {
  const saved = load(KEY)
  if (saved) return saved === 'dark'
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

export const useTheme = defineStore('theme', {
  state: () => ({ dark: initialDark() }),
  actions: {
    apply() {
      document.documentElement.classList.toggle('dark', this.dark)
    },
    toggle() {
      this.dark = !this.dark
      save(KEY, this.dark ? 'dark' : 'light')
      this.apply()
    },
  },
})
