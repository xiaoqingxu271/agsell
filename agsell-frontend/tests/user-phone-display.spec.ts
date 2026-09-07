import { test, expect } from '@playwright/test'

test.describe('用户管理页手机号显示', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173/admin/login')
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    await page.locator('button[type="submit"]').click()
    await page.waitForURL(/\/admin\/dashboard/)
    await expect(page.locator('text=仪表盘')).toBeVisible({ timeout: 5000 })
  })

  test('手机号正确显示', async ({ page }) => {
    await page.getByRole('menuitem', { name: '用户管理' }).click()
    await page.waitForURL(/\/admin\/users/)
    await expect(page.locator('text=用户列表')).toBeVisible({ timeout: 5000 })

    // 等待表格加载
    await page.locator('table').waitFor({ state: 'visible' })

    // 检查手机号列是否显示（应该是 17395837632 或类似手机号格式）
    const phoneCell = page.locator('table td').filter({ hasText: /1\d{10}/ })
    await expect(phoneCell).toBeVisible({ timeout: 5000 })

    // 或者直接检查页面中包含手机号
    await expect(page.getByText('17395837632')).toBeVisible()
  })
})
