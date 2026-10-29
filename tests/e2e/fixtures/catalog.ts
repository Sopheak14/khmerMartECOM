/**
 * The single place the E2E suite learns about the seeded demo catalog.
 * Mirrors back_end/src/main/java/com/khmermart/seed/SeedCatalog.java (all money in USD cents).
 * Specs must import from here instead of hard-coding ids, slugs, prices or names.
 */
export const API = 'http://localhost:4000/api';
export const USD_KHR_RATE = 4100;

export const usd = (cents: number) => `$${(cents / 100).toFixed(2)}`;
export const khr = (cents: number) => `${((cents * USD_KHR_RATE) / 100).toLocaleString('en-US')} ៛`;

// ---- categories (slugs as served by the API) ----
export const CATEGORY = {
  silk: { id: 1, slug: 'silk-textiles', name: 'Silk & Textiles', nameKm: 'សូត្រ និងក្រណាត់' },
  spices: { id: 2, slug: 'spices-sauces', name: 'Spices & Sauces', nameKm: 'គ្រឿងទេស និងទឹកជ្រលក់' },
  rice: { id: 3, slug: 'rice-grains', name: 'Rice & Grains', nameKm: 'អង្ករ និងធញ្ញជាតិ' },
  coffeeTea: { id: 4, slug: 'coffee-tea', name: 'Coffee & Tea', nameKm: 'កាហ្វេ និងតែ' },
  snacks: { id: 5, slug: 'snacks', name: 'Snacks & Dried Fruit', nameKm: 'អាហារសម្រន់ និងផ្លែឈើស្ងួត' },
  handicrafts: { id: 6, slug: 'handicrafts', name: 'Handicrafts', nameKm: 'សិប្បកម្ម' },
  beauty: { id: 7, slug: 'beauty-wellness', name: 'Beauty & Wellness', nameKm: 'សម្រស់ និងសុខភាព' },
  books: { id: 8, slug: 'books-culture', name: 'Books & Culture', nameKm: 'សៀវភៅ និងវប្បធម៌' },
} as const;

// ---- hero 1001: Takeo Hol Silk Sampot, variants = color x grade ----
export const HERO = {
  id: 1001,
  slug: 'takeo-hol-silk-sampot',
  name: 'Takeo Hol Silk Sampot',
  nameKm: 'សំពត់ហូលសូត្រ តាកែវ',
  color: 'Ruby Red',
  standardCents: 14900,
  premiumCents: 19900,
  flashCents: 12900,
  variants: {
    redStandard: 1,
    redPremium: 2,
    blueStandard: 3,
    bluePremium: 4,
  },
  stockPerVariant: 50,
  descEn: 'Hand-woven hol silk sampot from Takeo, worn at weddings and ceremonies.',
  descKm: 'សំពត់ហូលសូត្រត្បាញដៃពីខេត្តតាកែវ សម្រាប់ពាក់ក្នុងពិធីមង្គលការ និងពិធីបុណ្យ។',
} as const;

// ---- 1013 Phka Rumduol Jasmine Rice, variants by weight ----
export const RICE = {
  id: 1013,
  slug: 'phka-rumduol-jasmine-rice',
  fiveKgVariant: 5,
  tenKgVariant: 6,
  fiveKgCents: 1200,
  tenKgCents: 2200,
} as const;

// ---- flash sale / seasonal banner ----
export const FLASH = { name: 'Flash Sale 24H' } as const;
export const PEPPER = { id: 1006, slug: 'kampot-black-pepper-100-g', name: 'Kampot Black Pepper 100 g', nameKm: 'ម្រេចខ្មៅកំពត ១០០ ក្រាម', cents: 950, flashCents: 790 } as const;
export const MANGO = { id: 1022, cents: 400, flashCents: 320 } as const;
export const SEASONAL = { name: 'Pchum Ben Festival Sale', nameKm: 'ពិធីបុណ្យភ្ជុំបិណ្ឌ', percent: 10 } as const;

// ---- cheap non-variant product used to place throwaway orders ----
export const COCONUT_OIL = { id: 1031, name: 'Virgin Coconut Oil 250 ml', cents: 700 } as const;

// ---- BOGO on 1033 Lemongrass Soap (no variants) ----
export const SOAP = { id: 1033, cents: 350, bogoDiscountCents: 350, bogoName: 'Buy 1 Get 1 Lemongrass Soap' } as const;

// ---- low stock (dashboard table), threshold 5 ----
export const LOW_STOCK = {
  silverBox: { id: 1027, name: 'Kampong Luong Silver Box', stock: 3 },
  tableRunner: { id: 1004, name: 'Hol Silk Table Runner', stock: 4 },
} as const;

// ---- search / filter demo ----
export const SEARCH = { query: 'kampot', minDollars: 5, maxDollars: 10 } as const;

// ---- coupons and the demo order: Standard 149.00 - 20.00 flash - 5.00 SAVE5 = 124.00 ----
export const COUPON = { save5: 'SAVE5', save5Cents: 500, save5MinOrderCents: 3500, welcome10: 'WELCOME10', freeship: 'FREESHIP' } as const;
export const DEMO_ORDER = {
  totalCents: HERO.flashCents - COUPON.save5Cents,
  totalKhr: ((HERO.flashCents - COUPON.save5Cents) * USD_KHR_RATE) / 100,
} as const;

/** Fetch the live product so specs can assert against the API instead of copies of it. */
export async function fetchProduct(slug: string) {
  const res = await fetch(`${API}/products/${slug}`);
  return (await res.json()).data;
}
