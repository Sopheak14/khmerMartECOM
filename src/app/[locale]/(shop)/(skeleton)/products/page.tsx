import { setRequestLocale } from 'next-intl/server';
import { ProductsCatalogView } from '@/components/catalog/ProductsCatalogView';
import { ProductCardDTO, ProductFacets, PaginationMeta, PublicSettingsDTO } from '@/types/api';

export const dynamic = 'force-dynamic';

interface PageProps {
  params: { locale: string };
  searchParams: Record<string, string | string[] | undefined>;
}

export default async function ProductsPage({ params: { locale }, searchParams }: PageProps) {
  setRequestLocale(locale);

  // Build query string for backend API
  const query = new URLSearchParams();
  Object.entries(searchParams).forEach(([k, v]) => {
    if (v !== undefined) {
      if (Array.isArray(v)) {
        v.forEach((val) => query.append(k, val));
      } else {
        query.append(k, v);
      }
    }
  });

  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';

  let initialProducts: ProductCardDTO[] = [];
  let initialFacets: ProductFacets | undefined;
  let initialMeta: PaginationMeta | undefined;
  let khrRate = 4100;

  try {
    const [productsRes, settingsRes] = await Promise.all([
      fetch(`${backendUrl}/products?${query.toString()}`, { cache: 'no-store' }),
      fetch(`${backendUrl}/settings/public`, { cache: 'no-store' }),
    ]);

    if (productsRes.ok) {
      const pJson = await productsRes.json();
      if (pJson.success) {
        initialProducts = pJson.data || [];
        initialMeta = pJson.meta;
        initialFacets = pJson.facets;
      }
    }

    if (settingsRes.ok) {
      const sJson = await settingsRes.json();
      if (sJson.success && sJson.data?.khrRate) {
        khrRate = sJson.data.khrRate;
      }
    }
  } catch {
    // Client view will fall back to React Query
  }

  return (
    <ProductsCatalogView
      initialProducts={initialProducts}
      initialFacets={initialFacets}
      initialMeta={initialMeta}
      khrRate={khrRate}
    />
  );
}
