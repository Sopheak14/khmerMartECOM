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

  const registered = (await res.json()).data;
  let token: string | undefined = registered?.accessToken;
  let user: any = registered?.user;

  // Registration now needs the emailed 6-digit code: read it from Mailpit and verify.
  if (registered?.verificationRequired) {
    let code: string | null = null;
    for (let i = 0; i < 40 && !code; i++) {
      const list = await (await request.get('http://localhost:8025/api/v1/messages')).json();
      const msg = list.messages?.find((m: any) => m.To?.[0]?.Address?.toLowerCase() === email.toLowerCase());
      if (msg) {
        const detail = await (await request.get(`http://localhost:8025/api/v1/message/${msg.ID}`)).json();
        code = (detail.Text || detail.HTML || '').match(/\b(\d{6})\b/)?.[1] ?? null;
      }
      if (!code) await new Promise((r) => setTimeout(r, 250));
    }
    if (!code) throw new Error(`No verification email arrived for ${email}`);
    const verify = await request.post('http://localhost:4000/api/auth/verify-email', { data: { email, code } });
    if (!verify.ok()) throw new Error(`verify-email failed: ${verify.status()} ${await verify.text()}`);
    const verified = (await verify.json()).data;
    token = verified?.accessToken;
    user = verified?.user;
  }

  return { email, password, fullName, phone, token, user };
}

export async function loginViaUI(page: Page, email: string, password: string, locale: 'en' | 'km' = 'en') {
  await page.goto(`/${locale}/login`);
  await page.waitForLoadState('networkidle');
  const emailInput = page.locator('#login-email');
  // Under CPU load the page can be served with its CSS/JS chunks still missing (no form rendered). Give it time, then
  // reload once before failing so a stalled asset request does not fail an unrelated test.
  if (!(await emailInput.isVisible({ timeout: 8000 }).catch(() => false))) {
    await page.reload({ waitUntil: 'networkidle' });
  }
  await expect(emailInput).toBeVisible({ timeout: 15000 });
  await emailInput.fill(email);
  const passwordInput = page.locator('#login-password');
  await passwordInput.fill(password);
  if ((await emailInput.inputValue()) !== email) {
    await emailInput.fill(email);
  }
  await page.click('button[type="submit"]');
  await expect(page.locator('[data-testid="account-menu-button"]')).toBeVisible({ timeout: 10000 });
}
