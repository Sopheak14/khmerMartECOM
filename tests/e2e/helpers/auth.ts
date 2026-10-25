import { APIRequestContext, Page, expect } from '@playwright/test';

export interface ThrowawayUser {
  email: string;
  password: string;
  fullName: string;
  phone: string;
  token?: string;
  user?: any;
}

export async function createThrowawayUser(
  request: APIRequestContext,
  overrides?: Partial<ThrowawayUser>
): Promise<ThrowawayUser> {
  const timestamp = Date.now();
  const randomSuffix = Math.random().toString(36).substring(2, 7);
  const email = overrides?.email || `e2e_${timestamp}_${randomSuffix}@khmermart.local`;
  const password = overrides?.password || 'Customer@123';
  const fullName = overrides?.fullName || `E2E User ${randomSuffix}`;
  const phone = overrides?.phone || `+85512${Math.floor(100000 + Math.random() * 900000)}`;

  const res = await request.post('http://localhost:4000/api/auth/register', {
    data: {
      fullName,
      email,
      phone,
      password,
      confirmPassword: password,
    },
  });

  if (!res.ok()) {
    const errorBody = await res.text();
    throw new Error(`Failed to create throwaway user: ${res.status()} - ${errorBody}`);
  }

  const json = await res.json();
  return {
    email,
    password,
    fullName,
    phone,
    token: json.data?.accessToken,
    user: json.data?.user,
  };
}

export async function loginViaUI(page: Page, email: string, password: string, locale: 'en' | 'km' = 'en') {
  await page.goto(`/${locale}/login`);
  await page.waitForLoadState('networkidle');
  const emailInput = page.locator('#login-email');
  await expect(emailInput).toBeVisible();
  await emailInput.fill(email);
  const passwordInput = page.locator('#login-password');
  await passwordInput.fill(password);
  if ((await emailInput.inputValue()) !== email) {
    await emailInput.fill(email);
  }
  await page.click('button[type="submit"]');
  await expect(page.locator('[data-testid="account-menu-button"]')).toBeVisible({ timeout: 10000 });
}
