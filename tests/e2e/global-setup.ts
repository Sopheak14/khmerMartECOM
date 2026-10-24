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
}
