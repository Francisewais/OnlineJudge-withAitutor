<script setup lang="ts">
import type { Difficulty } from '@/api/types'
import { DIFFICULTIES } from '@/utils/verdict'

defineProps<{ difficulty: Difficulty }>()
</script>

<template>
  <el-tag :type="DIFFICULTIES[difficulty].type" effect="plain" size="small" disable-transitions>
    {{ DIFFICULTIES[difficulty].label }}
  </el-tag>
</template>
