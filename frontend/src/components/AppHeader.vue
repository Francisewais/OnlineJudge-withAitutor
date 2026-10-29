<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Moon, Sunny } from '@element-plus/icons-vue'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'
import { SITE_NAME } from '@/utils/format'
import PasswordDialog from './PasswordDialog.vue'

const auth = useAuth()
const theme = useTheme()
const route = useRoute()
const router = useRouter()
const showPassword = ref(false)

function onCommand(command: string) {
  if (command === 'mine') router.push({ path: '/submissions', query: { mine: '1' } })
  else if (command === 'password') showPassword.value = true
  else if (command === 'logout') {
    auth.logout()
    ElMessage.success('已退出登录')
    if (route.meta.admin) router.push('/problems')
  }
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link to="/" class="brand">
        <img src="/favicon.svg" alt="" />
        <span>{{ SITE_NAME }}</span>
      </router-link>
      <nav class="nav">
        <router-link to="/problems">题库</router-link>
        <router-link to="/submissions">提交记录</router-link>
        <router-link to="/ranking">排行榜</router-link>
        <router-link v-if="auth.isAdmin" to="/admin/problems">题目管理</router-link>
      </nav>
      <div class="header-right">
        <el-tooltip :content="theme.dark ? '切换到浅色' : '切换到深色'">
          <el-button circle text :icon="theme.dark ? Sunny : Moon" @click="theme.toggle()" />
        </el-tooltip>
        <el-dropdown v-if="auth.user" trigger="click" @command="onCommand">
          <span class="user-chip">
            {{ auth.user.username }}
            <el-tag v-if="auth.isAdmin" size="small" effect="plain" disable-transitions>管理员</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>已通过 {{ auth.user.solvedCount }} 题</el-dropdown-item>
              <el-dropdown-item command="mine">我的提交</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-else-if="route.path !== '/login'" type="primary" @click="router.push({ path: '/login', query: { redirect: route.fullPath } })">
          登录 / 注册
        </el-button>
      </div>
    </div>
    <PasswordDialog v-model="showPassword" />
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--header-height);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 100%;
  padding: 0 20px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  text-decoration: none;
  white-space: nowrap;
}
.brand img {
  width: 28px;
  height: 28px;
}
.nav {
  display: flex;
  gap: 4px;
  overflow-x: auto;
}
.nav a {
  padding: 6px 12px;
  border-radius: 6px;
  color: var(--el-text-color-regular);
  text-decoration: none;
  white-space: nowrap;
}
.nav a:hover {
  background: var(--el-fill-color-light);
}
.nav a.router-link-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-primary);
  outline: none;
}
@media (max-width: 640px) {
  .header-inner {
    gap: 12px;
    padding: 0 12px;
  }
  .brand span {
    display: none;
  }
}
</style>
