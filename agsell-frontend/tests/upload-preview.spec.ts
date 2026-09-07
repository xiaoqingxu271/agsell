import { test, expect } from '@playwright/test'

test.describe('文件上传图片预览', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173/admin/login')
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    await page.locator('button[type="submit"]').click()
    await page.waitForURL(/\/admin\/dashboard/)
    await expect(page.locator('text=仪表盘')).toBeVisible({ timeout: 5000 })
  })

  test('分类图标上传后显示预览', async ({ page }) => {
    await page.getByRole('menuitem', { name: '分类管理' }).click()
    await page.waitForURL(/\/admin\/categories/)
    await expect(page.locator('.card-title', { hasText: '分类列表' })).toBeVisible()

    await page.getByRole('button', { name: '新增分类' }).click()
    await page.waitForSelector('dialog:has-text("新增分类")', { state: 'visible' })

    // 验证初始状态：显示"暂无图标"
    await expect(page.locator('text=暂无图标')).toBeVisible()

    // 选择文件
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.getByRole('label', { name: '选择图标' }).click(),
    ])
    await fileChooser.setFiles({
      name: 'test.png',
      mimeType: 'image/png',
      buffer: Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    })

    // 等待上传成功后显示预览图
    await expect(page.locator('.el-image').first()).toBeVisible({ timeout: 10000 })
    const imgSrc = await page.locator('.el-image img').first().getAttribute('src')
    expect(imgSrc).toBeTruthy()
    expect(imgSrc).toMatch(/https?:\/\//)
  })

  test('商品主图上传后显示预览', async ({ page }) => {
    await page.getByRole('menuitem', { name: '商品管理' }).click()
    await page.waitForURL(/\/admin\/products/)
    await expect(page.locator('text=商品管理')).toBeVisible()

    await page.getByRole('button', { name: '新增商品' }).click()
    await page.waitForSelector('dialog:has-text("新增商品")', { state: 'visible' })

    // 验证初始状态：显示"暂无图片"
    await expect(page.locator('text=暂无图片')).toBeVisible()

    // 选择文件
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.getByRole('label', { name: '选择图片' }).first().click(),
    ])
    await fileChooser.setFiles({
      name: 'test.png',
      mimeType: 'image/png',
      buffer: Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    })

    // 等待上传成功后显示预览图
    await expect(page.locator('.el-image').first()).toBeVisible({ timeout: 10000 })
    const imgSrc = await page.locator('.el-image img').first().getAttribute('src')
    expect(imgSrc).toBeTruthy()
    expect(imgSrc).toMatch(/https?:\/\//)
  })

  test('轮播图上传后显示预览', async ({ page }) => {
    await page.getByRole('menuitem', { name: '轮播图管理' }).click()
    await page.waitForURL(/\/admin\/banners/)
    await expect(page.locator('text=轮播图管理')).toBeVisible()

    await page.getByRole('button', { name: '新增轮播图' }).click()
    await page.waitForSelector('dialog:has-text("新增轮播图")', { state: 'visible' })

    // 验证初始状态：显示"暂无图片"
    await expect(page.locator('text=暂无图片')).toBeVisible()

    // 选择文件
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.getByRole('label', { name: '选择图片' }).click(),
    ])
    await fileChooser.setFiles({
      name: 'test.png',
      mimeType: 'image/png',
      buffer: Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    })

    // 等待上传成功后显示预览图
    await expect(page.locator('.el-image').first()).toBeVisible({ timeout: 10000 })
    const imgSrc = await page.locator('.el-image img').first().getAttribute('src')
    expect(imgSrc).toBeTruthy()
    expect(imgSrc).toMatch(/https?:\/\//)
  })
})
