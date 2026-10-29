<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuth } from '@/stores/auth'
import { SITE_NAME } from '@/utils/format'

const auth = useAuth()
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名' },
    { pattern: /^[A-Za-z0-9_]{3,20}$/, message: '只能包含字母、数字和下划线，长度 3-20' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, max: 64, message: '密码长度需要在 6-64 位之间' },
  ],
  confirm: [
    {
      validator: (_rule, value, callback) =>
        mode.value === 'login' || value === form.password ? callback() : callback(new Error('两次输入的密码不一致')),
    },
  ],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    if (mode.value === 'login') await auth.login(form.username, form.password)
    else await auth.register(form.username, form.password)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功，欢迎！')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/problems'
    router.replace(redirect.startsWith('/') && !redirect.startsWith('/login') ? redirect : '/problems')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="card login-card">
      <h2>{{ mode === 'login' ? '登录' : '注册' }} {{ SITE_NAME }}</h2>
      <el-segmented
        v-model="mode"
        :options="[
          { label: '登录', value: 'login' },
          { label: '注册', value: 'register' },
        ]"
        block
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="login-form" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding: 60px 16px;
}
.login-card {
  width: 100%;
  max-width: 400px;
}
.login-card h2 {
  margin: 0 0 20px;
  text-align: center;
}
.login-form {
  margin-top: 20px;
}
</style>
