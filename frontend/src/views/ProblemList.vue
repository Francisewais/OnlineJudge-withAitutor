<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Search, WarningFilled } from '@element-plus/icons-vue'
import { api } from '@/api'
import type { Difficulty, ProblemSummary } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { SITE_NAME, acceptRate } from '@/utils/format'
import DifficultyTag from '@/components/DifficultyTag.vue'

const router = useRouter()
const auth = useAuth()
const items = ref<ProblemSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const difficulty = ref<Difficulty | ''>('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.problems({ page: page.value, size, keyword: keyword.value, difficulty: difficulty.value })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
// 登录、退出后刷新做题状态
watch(() => auth.user?.id, load)
</script>

<template>
  <div class="page">
    <section class="hero">
      <div>
        <h1>{{ SITE_NAME }}</h1>
        <p>选一道题开始练习吧。支持 C、C++、Java 和 Python；卡住的时候，可以让 AI 助教给你一点提示。</p>
      </div>
      <div v-if="auth.user" class="hero-stat">
        <div class="hero-number">{{ auth.user.solvedCount }}</div>
        <div class="muted">已通过</div>
      </div>
    </section>

    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索题目名称或标签"
          clearable
          :prefix-icon="Search"
          style="max-width: 280px"
          @change="search"
        />
        <el-radio-group v-model="difficulty" @change="search">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="EASY">简单</el-radio-button>
          <el-radio-button value="MEDIUM">中等</el-radio-button>
          <el-radio-button value="HARD">困难</el-radio-button>
        </el-radio-group>
      </div>

      <el-table v-loading="loading" :data="items" row-class-name="clickable" @row-click="(row: ProblemSummary) => router.push(`/problems/${row.id}`)">
        <el-table-column width="48" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'solved'" content="已通过">
              <el-icon color="var(--el-color-success)" :size="18"><CircleCheckFilled /></el-icon>
            </el-tooltip>
            <el-tooltip v-else-if="row.status === 'attempted'" content="尝试过，还没通过">
              <el-icon color="var(--el-color-warning)" :size="18"><WarningFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column label="题目" min-width="220">
          <template #default="{ row }">
            <div class="title-cell">
              <span class="problem-title">{{ row.title }}</span>
              <el-tag v-for="t in row.tags" :key="t" size="small" type="info" effect="plain" disable-transitions>{{ t }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="90">
          <template #default="{ row }"><DifficultyTag :difficulty="row.difficulty" /></template>
        </el-table-column>
        <el-table-column label="通过率" width="150">
          <template #default="{ row }">
            {{ acceptRate(row.acceptedCount, row.submitCount) }}
            <span class="muted">（{{ row.acceptedCount }}/{{ row.submitCount }}）</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > size" class="pagination">
        <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="size" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 28px 32px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(120deg, #2563eb, #7c3aed);
}
.hero h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
.hero p {
  margin: 0;
  opacity: 0.9;
}
.hero-stat {
  text-align: center;
}
.hero-stat .muted {
  color: rgba(255, 255, 255, 0.8);
}
.hero-number {
  font-size: 36px;
  font-weight: 700;
}
.title-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.problem-title {
  margin-right: 4px;
  font-weight: 500;
}
</style>
