import { defineStore } from 'pinia'
import { api } from '@/api'
import { getToken, setToken } from '@/api/http'
import type { User } from '@/api/types'

export const useAuth = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: null as User | null,
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN',
  },
  actions: {
    async login(username: string, password: string) {
      this.apply(await api.login(username, password))
    },
    async register(username: string, password: string) {
      this.apply(await api.register(username, password))
    },
    apply(res: { token: string; user: User }) {
      setToken(res.token)
      this.token = res.token
      this.user = res.user
    },
    /** 页面刷新后用保存的 token 取回用户信息；token 失效时 http.ts 会清掉它 */
    async restore() {
      if (!this.token) return
      try {
        this.user = await api.me()
      } catch {
        this.logout()
      }
    },
    logout() {
      setToken(null)
      this.token = null
      this.user = null
    },
  },
})
