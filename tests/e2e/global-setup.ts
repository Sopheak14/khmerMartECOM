// Minimum available stock per fixture variant. The suite buys from the finite seeded stock (paid orders cannot be
// undone), so without a top-up repeated runs would eventually fail checkout with OUT_OF_STOCK. Values are the
// seeded quantities (or higher for the variants the tests buy most). Only ever restocks; never lowers stock.
const STOCK_FLOORS: Record<number, number> = { 1: 60, 2: 15, 3: 10, 4: 10, 6: 20, 10: 20, 11: 15, 14: 100 };

async function ensureFixtureStock(api: string) {
  const login = await fetch(`${api}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'admin@khmermart.local', password: 'Admin@12345' }),
  });
  const token = (await login.json()).data?.accessToken;
  if (!token) throw new Error('Global Setup: could not log in as admin to top up fixture stock');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  const res = await fetch(`${api}/admin/inventory?size=200`, { headers });
  const rows: { id: number; variantId?: number; available: number }[] = (await res.json()).data ?? [];
  for (const row of rows) {
    const floor = row.variantId ? STOCK_FLOORS[row.variantId] : undefined;
    if (floor && row.available < floor) {
      await fetch(`${api}/admin/inventory/${row.id}/restock`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ quantity: floor - row.available, reason: 'E2E fixture stock floor' }),
      });
    }
  }
}

export default async function globalSetup() {
  const backendUrl = process.env.BACKEND_URL || 'http://localhost:4000/api/health';
  try {
    const res = await fetch(backendUrl);
    if (!res.ok) {
      throw new Error(`Backend returned status ${res.status}: ${res.statusText}`);
    }
    const data = await res.json();
    if (!data.success || data.data?.status !== 'ok') {
      throw new Error(`Backend health status check failed: ${JSON.stringify(data)}`);
    }
    console.log('✅ Global Setup: Real backend is running and healthy at http://localhost:4000');
  } catch (err: any) {
    console.error('❌ Global Setup FAILED: Backend is not reachable at http://localhost:4000/api/health');
    console.error('Please ensure the Spring Boot backend is started before running E2E tests.');
    throw new Error(`Backend health check failed: ${err.message}`);
  }

  await ensureFixtureStock(backendUrl.replace(/\/health$/, ''));
}
