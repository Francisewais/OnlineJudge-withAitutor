<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { api } from '@/api'
import type { ProblemSummary } from '@/api/types'
import { acceptRate } from '@/utils/format'
import DifficultyTag from '@/components/DifficultyTag.vue'

const router = useRouter()
const items = ref<ProblemSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.adminProblems({ page: page.value, size, keyword: keyword.value })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function remove(row: ProblemSummary) {
  try {
    await api.deleteProblem(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1 class="page-title">题目管理</h1>
    <div class="card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="router.push('/admin/problems/new')">新建题目</el-button>
        <el-input v-model="keyword" placeholder="搜索题目" clearable :prefix-icon="Search" style="max-width: 240px" @change="() => { page = 1; load() }" />
      </div>
      <el-table v-loading="loading" :data="items">
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column label="题目" min-width="200">
          <template #default="{ row }">
            <router-link :to="`/problems/${row.id}`">{{ row.title }}</router-link>
            <el-tag v-if="!row.visible" type="info" size="small" class="hidden-tag" disable-transitions>隐藏</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="90">
          <template #default="{ row }"><DifficultyTag :difficulty="row.difficulty" /></template>
        </el-table-column>
        <el-table-column label="通过率" width="150">
          <template #default="{ row }">{{ acceptRate(row.acceptedCount, row.submitCount) }}（{{ row.acceptedCount }}/{{ row.submitCount }}）</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/problems/${row.id}/edit`)">编辑</el-button>
            <el-popconfirm title="删除后题目的测试数据和提交记录都会被删除，确定吗？" width="260" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
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
.hidden-tag {
  margin-left: 6px;
}
</style>
