import { test, expect } from '@playwright/test';
import { createThrowawayUser, loginViaUI } from './helpers/auth';

test.describe('Phase 2 — Auth & Account E2E against Real Backend', () => {

  test('1a & 4: Login as customer1, verify header name, confirm token in memory only, and verify reload persistence', async ({ page }) => {
    // 1. Visit Login page in English
    await page.goto('/en/login');
    await expect(page.locator('h1')).toHaveText(/Sign in to KhmerMart/i);

    // Capture screenshot: login-en
    await page.screenshot({ path: 'docs/screenshots/login-en.png', fullPage: true });

    // Switch to Khmer and capture: login-km
    const langBtn = page.locator('button[aria-label="Switch language"]');
    await langBtn.click();
    await page.waitForURL('**/km/login**');
    await expect(page.locator('h1')).toHaveText(/ចូលគណនី KhmerMart/i);
    await page.screenshot({ path: 'docs/screenshots/login-km.png', fullPage: true });

    // Switch back to English for consistent test flows
    await langBtn.click();
    await page.waitForURL('**/en/login**');

    // Fill credentials
    await page.fill('#login-email', 'customer1@khmermart.local');
    await page.fill('#login-password', 'Customer@123');
    await page.click('button[type="submit"]');

    // Wait for login to complete (header displays user's name)
    const userNameLocator = page.locator('[data-testid="header-user-name"]').first();
    await expect(userNameLocator).toBeVisible({ timeout: 10000 });
    const nameText = await userNameLocator.innerText();
    expect(nameText.length).toBeGreaterThan(0);

    // Requirement 4: Confirm access token is NOT stored in localStorage or sessionStorage
    const storageAudit = await page.evaluate(() => {
      const localKeys = Object.keys(localStorage);
      const sessionKeys = Object.keys(sessionStorage);
      const localToken = localStorage.getItem('token') || localStorage.getItem('accessToken') || localStorage.getItem('access_token');
      const sessionToken = sessionStorage.getItem('token') || sessionStorage.getItem('accessToken') || sessionStorage.getItem('access_token');

      return {
        localKeys,
        sessionKeys,
        hasLocalToken: !!localToken,
        hasSessionToken: !!sessionToken,
      };
    });

    console.log('STORAGE AUDIT RAW OUTPUT:', JSON.stringify(storageAudit, null, 2));
    expect(storageAudit.hasLocalToken).toBe(false);
    expect(storageAudit.hasSessionToken).toBe(false);

    // Reload the page: silent POST /api/auth/refresh with km_rt cookie keeps user logged in
    await page.reload();
    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });
    expect(await page.locator('[data-testid="header-user-name"]').first().innerText()).toBe(nameText);
  });

  test('1b: Logout shows Login button and protected /account redirects to /login?next=/account', async ({ page }) => {
    // Login first
    await page.goto('/en/login');
    await page.fill('#login-email', 'customer1@khmermart.local');
    await page.fill('#login-password', 'Customer@123');
    await page.click('button[type="submit"]');
    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });
    await page.waitForLoadState('networkidle');

    // Click account menu and Logout
    await page.click('[data-testid="account-menu-button"]');
    await page.click('[data-testid="logout-button"]');

    // Verify header now shows Login
    await expect(page.locator('a[href="/en/login"]').first()).toBeVisible({ timeout: 10000 });

    // Access protected page /en/account
    await page.goto('/en/account');

    // Should redirect to /en/login?next=%2Faccount
    await page.waitForURL(/.*login.*next=.*/);
    expect(page.url()).toContain('next=');
  });

  test('1c: Register with invalid data displays backend VALIDATION_ERROR details under each field; valid register logs in', async ({ page }) => {
    await page.goto('/en/register');

    // Fill invalid data
    await page.fill('#register-fullname', 'Invalid User');
    await page.fill('#register-email', 'not-an-email');
    await page.fill('#register-phone', '012345678'); // Missing +855
    await page.fill('#register-password', 'short'); // Short password
    await page.fill('#register-confirm-password', 'short');

    await page.click('button[type="submit"]');

    // Wait for backend validation error messages
    await expect(page.locator('text=Invalid email format').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=Phone number must be Cambodian format').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=Password must be at least 8 characters').first()).toBeVisible({ timeout: 5000 });

    // Capture screenshot: register-errors
    await page.screenshot({ path: 'docs/screenshots/register-errors.png', fullPage: true });

    // Now fill valid data
    const validEmail = `valid_e2e_${Date.now()}@khmermart.local`;
    await page.fill('#register-fullname', 'Valid New Customer');
    await page.fill('#register-email', validEmail);
    await page.fill('#register-phone', '+85512777666');
    await page.fill('#register-password', 'Customer@123');
    await page.fill('#register-confirm-password', 'Customer@123');

    await page.click('button[type="submit"]');

    // Should automatically log in and redirect
    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });
  });

  test('1d: Login wrong password shows friendly error; 6 wrong attempts trigger RATE_LIMITED in EN and KM', async ({ page }) => {
    // 1. In English
    await page.goto('/en/login');
    const bruteEmailEn = `brute_en_${Date.now()}@khmermart.local`;

    // Attempt 1: friendly error
    await page.fill('#login-email', bruteEmailEn);
    await page.fill('#login-password', 'WrongPassword123');
    await page.click('button[type="submit"]');
    await expect(page.locator('[data-testid="login-error-alert"]').first()).toHaveText(/Invalid email or password/i);

    // Attempts 2 to 5
    for (let i = 2; i <= 5; i++) {
      await page.fill('#login-password', `WrongPassword${i}`);
      await page.click('button[type="submit"]');
      await expect(page.locator('[data-testid="login-error-alert"]').first()).toBeVisible();
    }

    // 6th attempt: RATE_LIMITED
    await page.fill('#login-password', 'WrongPassword6');
    await page.click('button[type="submit"]');
    await expect(page.locator('[data-testid="login-error-alert"]').first()).toHaveText(/Too many login attempts\. Please try again later\./i);

    // 2. In Khmer
    await page.goto('/km/login');
    const bruteEmailKm = `brute_km_${Date.now()}@khmermart.local`;

    // 5 wrong attempts
    for (let i = 1; i <= 5; i++) {
      await page.fill('#login-email', bruteEmailKm);
      await page.fill('#login-password', `WrongPass${i}`);
      await page.click('button[type="submit"]');
      await expect(page.locator('[data-testid="login-error-alert"]').first()).toBeVisible();
    }

    // 6th attempt in KM: RATE_LIMITED message in Khmer
    await page.fill('#login-password', 'WrongPass6');
    await page.click('button[type="submit"]');
    await expect(page.locator('[data-testid="login-error-alert"]').first()).toHaveText(/ការប៉ុនប៉ងចូលច្រើនពេក។ សូមព្យាយាមម្តងទៀតនៅពេលក្រោយ។/);
  });

  test('1e: Customer opening /en/admin renders 403 page', async ({ page }) => {
    // Login as Customer
    await page.goto('/en/login');
    await page.fill('#login-email', 'customer1@khmermart.local');
    await page.fill('#login-password', 'Customer@123');
    await page.click('button[type="submit"]');
    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });

    // Attempt to access /en/admin
    await page.goto('/en/admin');

    // Verify 403 page renders properly without crashing
    await expect(page.locator('text=HTTP 403 FORBIDDEN').first()).toBeVisible();
    await expect(page.locator('text=403 — Access Denied').first()).toBeVisible();
    await expect(page.locator('a[href="/en"]').first()).toBeVisible();
  });

  test('1f: Profile: edit name/phone is saved and re-fetch displays new value', async ({ page, request }) => {
    // Isolated throwaway user - never mutate demo accounts
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Go to account profile
    await page.goto('/en/account');
    await expect(page.locator('h1')).toHaveText(/My Account/i);

    // Capture screenshot: account-profile
    await page.screenshot({ path: 'docs/screenshots/account-profile.png', fullPage: true });

    // Update name and phone
    const newName = `Sopheap Updated ${Date.now().toString().slice(-4)}`;
    const newPhone = '+85512888999';

    await page.fill('#profile-fullname', newName);
    await page.fill('#profile-phone', newPhone);
    await page.click('button[type="submit"]');

    // Verify toast or updated heading
    await expect(page.locator(`text=${newName}`).first()).toBeVisible({ timeout: 5000 });

    // Reload page to confirm persistence on re-fetch from GET /api/users/me
    await page.reload();
    await expect(page.locator('#profile-fullname')).toHaveValue(newName);
    await expect(page.locator('#profile-phone')).toHaveValue(newPhone);
  });

  test('1g: Addresses: create (25 provinces in /km), edit, set default (only one default), delete', async ({ page, request }) => {
    // Isolated throwaway user - never mutate demo accounts
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'km');

    // Go to addresses in Khmer
    await page.goto('/km/account/addresses');
    await expect(page.locator('h2')).toHaveText(/អាសយដ្ឋានដែលបានរក្សាទុក/);

    // Click Add New
    await page.click('button:has-text("បន្ថែមអាសយដ្ឋានថ្មី")');

    // Verify Province dropdown has 25 provinces with Khmer names
    const provinceSelect = page.locator('#addr-province');
    await expect(provinceSelect).toBeVisible();
    const options = await provinceSelect.locator('option').allInnerTexts();
    expect(options.length).toBe(25);
    expect(options).toContain('រាជធានីភ្នំពេញ');
    expect(options).toContain('ខេត្តសៀមរាប');

    // Fill address form
    const addrLabel = `ផ្ទះថ្មី ${Date.now().toString().slice(-4)}`;
    await page.fill('#addr-label', addrLabel);
    await page.fill('#addr-recipient', 'សុភា កម្ពុជា');
    await page.fill('#addr-phone', '+85512555666');
    await provinceSelect.selectOption({ label: 'ខេត្តសៀមរាប' });
    await page.fill('#addr-district', 'ក្រុងសៀមរាប');
    await page.fill('#addr-line1', 'ផ្លូវវ៉ាត់បូ សង្កាត់សាលាកំរើក');
    await page.check('#addr-is-default');

    // Save address
    await page.click('button:has-text("រក្សាទុកអាសយដ្ឋាន")');

    // Verify address card appears with DEFAULT badge in Khmer ("ចម្បង")
    const card = page.locator(`[data-testid="address-card"]:has-text("${addrLabel}")`).first();
    await expect(card).toBeVisible({ timeout: 5000 });

    // Capture screenshot: addresses-km
    await page.screenshot({ path: 'docs/screenshots/addresses-km.png', fullPage: true });

    // Verify only ONE address has default badge
    const defaultBadges = await page.locator('[data-testid="default-badge"]').all();
    expect(defaultBadges.length).toBe(1);

    // Edit address: update recipient name
    await card.locator('[data-testid="edit-address-button"]').click();
    await page.fill('#addr-recipient', 'សុភា កែប្រែ');
    await page.click('button:has-text("រក្សាទុកអាសយដ្ឋាន")');
    await expect(page.locator('text=សុភា កែប្រែ').first()).toBeVisible({ timeout: 5000 });

    // Delete the newly created address
    const updatedCard = page.locator(`[data-testid="address-card"]:has-text("${addrLabel}")`).first();
    page.once('dialog', (dialog) => dialog.accept());
    await updatedCard.locator('[data-testid="delete-address-button"]').click();

    // Verify address is removed
    await expect(page.locator(`text=${addrLabel}`)).toHaveCount(0);
  });

  test('1h: Change password with wrong current password shows error under currentPassword field', async ({ page, request }) => {
    // Isolated throwaway user - never mutate demo accounts
    const throwaway = await createThrowawayUser(request);
    await loginViaUI(page, throwaway.email, throwaway.password, 'en');

    // Go to security page
    await page.goto('/en/account/security');

    // Fill with WRONG current password
    await page.fill('#current-password', 'WrongPassword123');
    await page.fill('#new-password', 'ValidNewPassword123');
    await page.fill('#confirm-password', 'ValidNewPassword123');
    await page.click('button[type="submit"]');

    // Verify error appears under currentPassword field
    await expect(page.locator('text=Current password is incorrect').first()).toBeVisible({ timeout: 5000 });
  });

  test('2: Forgot password and Verify email with Mailpit reset link', async ({ page, request }) => {
    // Clear Mailpit inbox
    await request.delete('http://localhost:8025/api/v1/messages');

    // 1. Isolated throwaway user for forgot/reset password
    const throwaway = await createThrowawayUser(request);
    const forgotEmail = throwaway.email;
    await page.goto('/en/forgot-password');
    await page.fill('#forgot-email', forgotEmail);
    await page.click('button[type="submit"]');

    await expect(page.locator('text=a password reset link has been sent to your inbox').first()).toBeVisible({ timeout: 5000 });

    // 2. Poll Mailpit API until reset email arrives
    let msgDetail: any;
    await expect.poll(async () => {
      const res = await request.get('http://localhost:8025/api/v1/messages');
      const json = await res.json();
      const msg = json.messages?.find((m: any) => m.To[0]?.Address?.toLowerCase() === forgotEmail.toLowerCase());
      if (msg) {
        const detailRes = await request.get(`http://localhost:8025/api/v1/message/${msg.ID}`);
        msgDetail = await detailRes.json();
        return msgDetail;
      }
      return null;
    }, { timeout: 10000 }).not.toBeNull();

    console.log('RAW MAILPIT MESSAGE DETAIL:', JSON.stringify({
      id: msgDetail.ID,
      subject: msgDetail.Subject,
      to: msgDetail.To,
      snippet: msgDetail.Snippet,
    }, null, 2));

    // Extract reset link
    const resetUrlMatch = msgDetail.HTML.match(/http:\/\/localhost:3000\/reset-password\?token=([a-zA-Z0-9_-]+)/);
    expect(resetUrlMatch).not.toBeNull();
    const resetUrl = resetUrlMatch![0];
    console.log('RAW EXTRACTED RESET URL:', resetUrl);

    // 3. Navigate to reset link
    await page.goto(resetUrl);
    await expect(page.locator('h1')).toHaveText(/Set new password/i);

    // Set new password
    await page.fill('#reset-new-password', 'NewCustomerPass@123');
    await page.fill('#reset-confirm-password', 'NewCustomerPass@123');
    await page.click('button[type="submit"]');

    await expect(page.locator('text=Your password has been reset successfully').first()).toBeVisible({ timeout: 5000 });

    // 4. Test login with the new password
    await page.goto('/en/login');
    await page.fill('#login-email', forgotEmail);
    await page.fill('#login-password', 'NewCustomerPass@123');
    await page.click('button[type="submit"]');

    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });

    // 5. Test verify-email flow
    // Register a user to trigger verify-email
    const verifyEmailUser = `verify_flow_${Date.now()}@khmermart.local`;
    await page.goto('/en/register');
    await page.fill('#register-fullname', 'Verify User Flow');
    await page.fill('#register-email', verifyEmailUser);
    await page.fill('#register-phone', '+85512111222');
    await page.fill('#register-password', 'Customer@123');
    await page.fill('#register-confirm-password', 'Customer@123');
    await page.click('button[type="submit"]');

    await expect(page.locator('[data-testid="header-user-name"]').first()).toBeVisible({ timeout: 10000 });

    // Poll Mailpit API until verification email arrives
    let verifyMsgDetail: any;
    await expect.poll(async () => {
      const res = await request.get('http://localhost:8025/api/v1/messages');
      const json = await res.json();
      const msg = json.messages?.find((m: any) => m.To[0]?.Address?.toLowerCase() === verifyEmailUser.toLowerCase());
      if (msg) {
        const detailRes = await request.get(`http://localhost:8025/api/v1/message/${msg.ID}`);
        verifyMsgDetail = await detailRes.json();
        return verifyMsgDetail;
      }
      return null;
    }, { timeout: 10000 }).not.toBeNull();

    const verifyUrlMatch = verifyMsgDetail.HTML.match(/http:\/\/localhost:3000\/verify-email\?token=([a-zA-Z0-9_-]+)/);
    expect(verifyUrlMatch).not.toBeNull();
    const verifyUrl = verifyUrlMatch![0];
    console.log('RAW EXTRACTED VERIFY URL:', verifyUrl);

    // Navigate to verify URL
    await page.goto(verifyUrl);
    await expect(page.locator('text=Your email address has been verified successfully').first()).toBeVisible({ timeout: 10000 });
  });

});
