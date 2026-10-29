<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { verdictInfo } from '@/utils/verdict'

const props = defineProps<{
  verdict: string | null | undefined
  status?: string
  size?: 'small' | 'default' | 'large'
}>()
const info = computed(() => verdictInfo(props.verdict, props.status))
const busy = computed(() => props.status === 'PENDING' || props.status === 'JUDGING')
</script>

<template>
  <el-tag :type="info.type" :size="size ?? 'default'" disable-transitions>
    <span class="verdict-tag">
      <el-icon v-if="busy" class="is-loading"><Loading /></el-icon>
      {{ info.label }}
    </span>
  </el-tag>
</template>

<style scoped>
.verdict-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
