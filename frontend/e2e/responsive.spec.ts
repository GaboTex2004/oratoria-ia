import { expect, test, request as apiRequest } from '@playwright/test'
import { randomUUID } from 'node:crypto'

let token = ''

test.beforeAll(async () => {
  const api = await apiRequest.newContext()
  const response = await api.post('http://localhost:8081/api/auth/register', { data: {
    firstName: 'PruebaResponsiveConNombreExtenso', lastName: 'Sintetica',
    email: `browser-${randomUUID()}@example.test`, password: `Test-${randomUUID()}`,
  } })
  expect(response.status()).toBe(201)
  token = (await response.json()).token
  await api.dispose()
})

for (const width of [320, 375, 580, 768, 980, 1440]) {
  test(`responsive real APIs ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width < 768 ? 740 : 900 })
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expectNoOverflow()
    await page.goto('/register')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expectNoOverflow()
    await page.addInitScript(value => localStorage.setItem('oratoria.auth-token', value), token)
    for (const route of ['/home', '/training', '/sessions', '/progress', '/simulations', '/profile', '/plans', '/recommendations', '/body']) {
      await page.goto(route)
      await expect(page.locator('.dashboard-main')).toBeVisible()
      await expect(page.locator('.route-loading')).toHaveCount(0)
      await page.waitForLoadState('networkidle')
      await expectNoOverflow()
    }
    if (width < 781) {
      await page.getByRole('button', { name: 'Abrir menú' }).click()
      await expect(page.locator('.sidebar')).toHaveClass(/is-open/)
      await page.getByRole('link', { name: 'Perfil', exact: true }).click()
      await expect(page).toHaveURL(/profile/)
    }
    await page.screenshot({ path: `../.tools/browser-results/app-${width}.png`, fullPage: true })
    expect(errors).toEqual([])

    async function expectNoOverflow() {
      const dimensions = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
        overflowing: Array.from(document.querySelectorAll('body *')).filter(element => element.getBoundingClientRect().right > innerWidth + 1)
          .slice(0, 8).map(element => element.className) }))
      expect(dimensions.scroll, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.width + 1)
    }
  })
}
