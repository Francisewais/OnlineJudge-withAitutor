import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '@/stores/auth'
import { SITE_NAME } from '@/utils/format'
import ProblemList from '@/views/ProblemList.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/problems' },
    { path: '/problems', component: ProblemList, meta: { title: '题库' } },
    { path: '/problems/:id', component: () => import('@/views/ProblemDetail.vue'), props: true },
    { path: '/submissions', component: () => import('@/views/SubmissionList.vue'), meta: { title: '提交记录' } },
    { path: '/submissions/:id', component: () => import('@/views/SubmissionDetail.vue'), props: true },
    { path: '/ranking', component: () => import('@/views/RankingView.vue'), meta: { title: '排行榜' } },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/admin', redirect: '/admin/problems' },
    {
      path: '/admin/problems',
      component: () => import('@/views/admin/AdminProblems.vue'),
      meta: { admin: true, title: '题目管理' },
    },
    {
      path: '/admin/problems/new',
      component: () => import('@/views/admin/ProblemEditor.vue'),
      meta: { admin: true, title: '新建题目' },
    },
    {
      path: '/admin/problems/:id/edit',
      component: () => import('@/views/admin/ProblemEditor.vue'),
      props: true,
      meta: { admin: true, title: '编辑题目' },
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFound.vue'), meta: { title: '页面不存在' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (!to.meta.admin) return true
  const auth = useAuth()
  if (auth.token && !auth.user) await auth.restore()
  if (!auth.loggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.isAdmin) {
    ElMessage.warning('只有管理员才能访问这个页面')
    return '/problems'
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - ${SITE_NAME}` : SITE_NAME
})

export default router
