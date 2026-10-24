import { notFound } from 'next/navigation';
import { setRequestLocale } from 'next-intl/server';
import { Link } from '@/navigation';
import { ProductsCatalogView } from '@/components/catalog/ProductsCatalogView';
import { CategoryDTO, ProductCardDTO, ProductFacets, PaginationMeta } from '@/types/api';
import { ChevronRight, Home, LayoutGrid } from 'lucide-react';
import Image from 'next/image';

export const dynamic = 'force-dynamic';

interface PageProps {
  params: { locale: string; slug: string };
  searchParams: Record<string, string | string[] | undefined>;
}

export default async function CategoryPage({ params: { locale, slug }, searchParams }: PageProps) {
  setRequestLocale(locale);

  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';

  // 1. Fetch category by slug
  const catRes = await fetch(`${backendUrl}/categories/${slug}`, { cache: 'no-store' });
  if (!catRes.ok) {
    notFound();
  }
  const catJson = await catRes.json();
  if (!catJson.success || !catJson.data) {
    notFound();
  }
  const category: CategoryDTO = catJson.data;

  let parentCategory: CategoryDTO | null = null;
  let initialProducts: ProductCardDTO[] = [];
  let initialFacets: ProductFacets | undefined;
  let initialMeta: PaginationMeta | undefined;
  let khrRate = 4100;

  try {
    // 2. If it has parentId, fetch full category tree to identify parent
    if (category.parentId) {
      const treeRes = await fetch(`${backendUrl}/categories`, { cache: 'no-store' });
      if (treeRes.ok) {
        const treeJson = await treeRes.json();
        if (treeJson.success && Array.isArray(treeJson.data)) {
          parentCategory = treeJson.data.find((c: CategoryDTO) => c.id === category.parentId) || null;
        }
      }
    }

    // 3. Build query for products in this category
    const query = new URLSearchParams();
    query.set('categoryId', String(category.id));

    Object.entries(searchParams).forEach(([k, v]) => {
      if (v !== undefined && k !== 'categoryId') {
        if (Array.isArray(v)) {
          v.forEach((val) => query.append(k, val));
        } else {
          query.append(k, v);
        }
      }
    });

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
    // fallback
  }

  const categoryName = locale === 'km' && category.nameKm ? category.nameKm : category.name;
  const parentName =
    parentCategory && (locale === 'km' && parentCategory.nameKm ? parentCategory.nameKm : parentCategory.name);

  return (
    <div className="min-h-screen bg-background">
      {/* Category Header & Breadcrumbs */}
      <div className="bg-surface border-b border-border">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6">
          {/* Breadcrumbs */}
          <nav
            data-testid="category-breadcrumbs"
            aria-label="Breadcrumb"
            className="flex items-center gap-1.5 text-xs text-muted mb-4 flex-wrap"
          >
            <Link
              href="/"
              className="flex items-center gap-1 hover:text-primary transition-colors font-medium"
            >
              <Home className="w-3.5 h-3.5" />
              <span>{locale === 'km' ? 'ទំព័រដើម' : 'Home'}</span>
            </Link>

            <ChevronRight className="w-3 h-3 text-muted/60" />

            <Link
              href="/products"
              className="hover:text-primary transition-colors font-medium"
            >
              {locale === 'km' ? 'ទំនិញទាំងអស់' : 'Products'}
            </Link>

            {parentCategory && (
              <>
                <ChevronRight className="w-3 h-3 text-muted/60" />
                <Link
                  href={`/category/${parentCategory.slug}`}
                  className="hover:text-primary transition-colors font-medium"
                >
                  {parentName}
                </Link>
              </>
            )}

            <ChevronRight className="w-3 h-3 text-muted/60" />
            <span className="text-foreground font-bold truncate">{categoryName}</span>
          </nav>

          {/* Category Banner & Title */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-card bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
                <LayoutGrid className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight">
                  {categoryName}
                </h1>
                <p className="text-xs text-muted mt-0.5">
                  {locale === 'km'
                    ? `មាន ${category.productCount} ទំនិញក្នុងប្រភេទនេះ`
                    : `${category.productCount} products in this category`}
                </p>
              </div>
            </div>

            {/* Sub-category chips */}
            {category.children && category.children.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {category.children.map((sub) => {
                  const subTitle = locale === 'km' && sub.nameKm ? sub.nameKm : sub.name;
                  return (
                    <Link
                      key={sub.slug}
                      href={`/category/${sub.slug}`}
                      className="text-xs px-3 py-1.5 rounded-full border border-border bg-surface hover:border-primary/50 hover:bg-primary/5 text-foreground transition-all flex items-center gap-1.5"
                    >
                      <span>{subTitle}</span>
                      <span className="text-[10px] text-muted bg-muted/20 px-1.5 py-0.2 rounded-full">
                        {sub.productCount}
                      </span>
                    </Link>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Product Catalog Grid filtered by this category */}
      <ProductsCatalogView
        initialProducts={initialProducts}
        initialFacets={initialFacets}
        initialMeta={initialMeta}
        khrRate={khrRate}
        categoryTitle={categoryName}
      />
    </div>
  );
}
