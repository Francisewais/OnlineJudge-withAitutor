<template>
  <div class="page">
    <el-result icon="warning" title="页面不存在" sub-title="你访问的页面不存在或已被删除">
      <template #extra>
        <el-button type="primary" @click="$router.push('/problems')">返回题库</el-button>
      </template>
    </el-result>
  </div>
</template>
