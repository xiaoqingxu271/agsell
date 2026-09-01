import { createRouter, createWebHistory } from 'vue-router'
import { allRoutes, handleAuthGuard, setTitle, clearDynamicRoutes } from './guards'

export { clearDynamicRoutes }

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: allRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to, _from) => {
  setTitle(to.meta)

  try {
    const result = await handleAuthGuard(router.resolve(to.fullPath))
    if (result !== true) return result
  } catch {
    return '/admin/login'
  }
})

export default router
