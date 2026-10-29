<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import type { RankItem } from '@/api/types'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const items = ref<RankItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 50
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.ranking(page.value, size)
    items.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

// 高亮自己所在的行
function rowClass({ row }: { row: RankItem }) {
  return row.username === auth.user?.username ? 'me' : ''
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1 class="page-title">排行榜</h1>
    <div class="card">
      <el-table v-loading="loading" :data="items" :row-class-name="rowClass">
        <el-table-column label="排名" width="90">
          <template #default="{ row }">
            <span :class="['rank', row.rank <= 3 ? `top${row.rank}` : '']">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="160" />
        <el-table-column prop="solvedCount" label="通过题数" width="120" />
        <el-table-column prop="submitCount" label="提交次数" width="120" />
      </el-table>
      <div v-if="total > size" class="pagination">
        <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="total" :page-size="size" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-weight: 600;
}
.top1 {
  background: #f5c542;
  color: #fff;
}
.top2 {
  background: #b8c0cc;
  color: #fff;
}
.top3 {
  background: #d89b6a;
  color: #fff;
}
:deep(.me) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}
</style>
