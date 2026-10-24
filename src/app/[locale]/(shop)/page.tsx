import { setRequestLocale } from 'next-intl/server';
import { HomeView } from '@/components/home/HomeView';
import { HomeDTO, PublicSettingsDTO } from '@/types/api';

export const dynamic = 'force-dynamic';

export default async function HomePage({
  params: { locale },
}: {
  params: { locale: string };
}) {
  setRequestLocale(locale);

  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';
  let initialHomeData: HomeDTO | undefined;
  let initialSettingsData: PublicSettingsDTO | undefined;

  try {
    const [homeRes, settingsRes] = await Promise.all([
      fetch(`${backendUrl}/home`, { cache: 'no-store' }),
      fetch(`${backendUrl}/settings/public`, { cache: 'no-store' }),
    ]);

    if (homeRes.ok) {
      const hJson = await homeRes.json();
      if (hJson.success) initialHomeData = hJson.data;
    }

    if (settingsRes.ok) {
      const sJson = await settingsRes.json();
      if (sJson.success) initialSettingsData = sJson.data;
    }
  } catch {
    // Falls back to client query
  }

  return (
    <HomeView
      initialHomeData={initialHomeData}
      initialSettingsData={initialSettingsData}
    />
  );
}
