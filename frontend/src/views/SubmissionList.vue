<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import type { SubmissionSummary } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { VERDICT_OPTIONS } from '@/utils/verdict'
import { formatMemory, formatTime } from '@/utils/format'
import VerdictTag from '@/components/VerdictTag.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const items = ref<SubmissionSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const mine = ref(route.query.mine === '1')
const username = ref('')
const verdict = ref('')
let timer: number | undefined

async function load(quiet = false) {
  if (!quiet) loading.value = true
  try {
    const res = await api.submissions({
      page: page.value,
      size,
      mine: mine.value && auth.loggedIn,
      username: mine.value ? undefined : username.value,
      verdict: verdict.value,
    })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    if (!quiet) ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
  // 有提交还在评测时，过一会儿自动刷新
  clearTimeout(timer)
  if (items.value.some((s) => s.status !== 'FINISHED')) timer = window.setTimeout(() => load(true), 2000)
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(timer))
watch(() => route.query.mine, (value) => {
  mine.value = value === '1'
  search()
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">提交记录</h1>
    <div class="card">
      <div class="toolbar">
        <el-switch v-if="auth.loggedIn" v-model="mine" active-text="只看我的" @change="search" />
        <el-input v-if="!mine" v-model="username" placeholder="按用户名筛选" clearable style="width: 180px" @change="search" />
        <el-select v-model="verdict" placeholder="全部结果" clearable style="width: 160px" @change="search">
          <el-option v-for="o in VERDICT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="items" row-class-name="clickable" @row-click="(row: SubmissionSummary) => router.push(`/submissions/${row.id}`)">
        <el-table-column prop="id" label="#" width="80" />
        <el-table-column label="题目" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/problems/${row.problemId}`" @click.stop>{{ row.problemId }}. {{ row.problemTitle }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="110" />
        <el-table-column label="结果" width="130">
          <template #default="{ row }">
            <VerdictTag :verdict="row.verdict" :status="row.status" size="small" />
            <span v-if="row.status === 'FINISHED' && row.verdict !== 'CE' && row.verdict !== 'AC'" class="muted passed">
              {{ row.passedCount }}/{{ row.totalCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="80" />
        <el-table-column label="用时" width="90">
          <template #default="{ row }">{{ row.timeMs ?? '-' }} ms</template>
        </el-table-column>
        <el-table-column label="内存" width="100">
          <template #default="{ row }">{{ formatMemory(row.memoryKb) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="total > size" class="pagination">
        <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="size" @current-change="() => load()" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.passed {
  margin-left: 6px;
  font-size: 12px;
}
</style>
