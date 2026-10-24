import { setRequestLocale } from 'next-intl/server';
import { HomeView } from '@/components/home/HomeView';

export default async function HomePage({
  params: { locale },
}: {
  params: { locale: string };
}) {
  setRequestLocale(locale);

  return <HomeView />;
}
