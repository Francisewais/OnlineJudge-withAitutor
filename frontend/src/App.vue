<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'

const auth = useAuth()
useTheme().apply()

onMounted(() => {
  auth.restore()
  // http.ts 发现登录过期时会发出这个事件
  window.addEventListener('oj:logout', () => auth.logout())
})
</script>

<template>
  <AppHeader />
  <main class="app-main">
    <router-view />
  </main>
</template>
