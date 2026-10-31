import { notFound } from 'next/navigation';
import { setRequestLocale } from 'next-intl/server';
import { ProductDetailView } from '@/components/product/ProductDetailView';
import type { Metadata } from 'next';
import { ProductDetailDTO, ReviewDTO, PublicSettingsDTO } from '@/types/api';

export const dynamic = 'force-dynamic';

interface PageProps {
  params: { locale: string; slug: string };
}

/**
 * Resolves the product before the response starts streaming. [locale]/loading.tsx wraps the page in a Suspense
 * boundary, so a notFound() thrown only from the page body arrives after the 200 status was already sent.
 * Throwing it here makes unknown / deleted products return a real HTTP 404.
 */
export async function generateMetadata({ params: { slug } }: PageProps): Promise<Metadata> {
  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';
  const res = await fetch(`${backendUrl}/products/${slug}`, { cache: 'no-store' });
  if (!res.ok) notFound();
  const json = await res.json();
  if (!json.success || !json.data) notFound();
  return {};
}

export default async function ProductDetailPage({ params: { locale, slug } }: PageProps) {
  setRequestLocale(locale);

  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';

  let product: ProductDetailDTO;
  let reviews: ReviewDTO[] = [];
  let khrRate = 4100;

  const res = await fetch(`${backendUrl}/products/${slug}`, { cache: 'no-store' });
  if (!res.ok) {
    notFound();
  }
  const json = await res.json();
  if (!json.success || !json.data) {
    notFound();
  }
  product = json.data;

  try {
    // Fetch approved reviews and public settings
    const [reviewsRes, settingsRes] = await Promise.all([
      fetch(`${backendUrl}/products/${product.id}/reviews`, { cache: 'no-store' }),
      fetch(`${backendUrl}/settings/public`, { cache: 'no-store' }),
    ]);

    if (reviewsRes.ok) {
      const revJson = await reviewsRes.json();
      if (revJson.success && Array.isArray(revJson.data)) {
        reviews = revJson.data;
      }
    }

    if (settingsRes.ok) {
      const setJson = await settingsRes.json();
      if (setJson.success && setJson.data?.khrRate) {
        khrRate = setJson.data.khrRate;
      }
    }
  } catch {
    // optional enhancements
  }

  return (
    <div className="min-h-screen bg-background">
      <ProductDetailView product={product} reviews={reviews} khrRate={khrRate} />
    </div>
  );
}
