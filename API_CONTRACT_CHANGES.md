# API Contract Changes & Observations

## 1. Product Average Rating when Review Count is Zero
- **Issue**: Products with `reviewCount == 0` (e.g. Dell Latitude 5520) currently return `avgRating: 5.0` (or default initialized rating) in the backend product catalog and detail responses.
- **Expected**: Backend must return `avgRating: 0.0` (or `null`) when there are no approved reviews (`reviewCount == 0`).
- **Frontend Mitigation**: Frontend has been updated to check `reviewCount === 0`. If 0, it hides the star icon and displays "No reviews yet" (EN) / "មិនទាន់មានការវាយតម្លៃ" (KM) regardless of the backend `avgRating` value.

## 2. Search Autocomplete Promotional Pricing
- **Issue**: `/api/search/suggest` previously returned `product.priceCents` (regular price) even when products were actively on flash sale (e.g., Laptop Pro 15 at $899.00 vs regular $999.00).
- **Update**: `SearchServiceImpl.java` was updated to look up active flash sale promotions (`PromotionType.FLASH_SALE`) and return effective `salePriceCents` for promotional products.

## 3. Per-Variant Flash Sale Pricing & VariantDTO.salePriceCents
- **Issue**: Previously, flash sale `salePriceCents` was stored and applied only at the product level. When a product had variants with differing prices (e.g. Laptop Pro 15 base 16GB at $999 vs 32GB variant 3 at $1,299), applying the product-level flash sale price ($899) caused the $1,299 variant to sell for $899 (a $400 discount instead of remaining full price).
- **Rule**: Flash sale prices are per variant. In `promotion_products`, a row with `variant_id` applies only to that variant; a row with `variant_id NULL` applies only to products without variants. A product-level sale price is never applied to a variant with a different price.
- **Contract Additions**:
  - `VariantDTO.salePriceCents`: Additive, optional `Long` field present in the response only when that variant is on an active flash sale (omitted when not on sale).
  - `ProductCardDTO.salePriceCents` & `ProductDetailDTO.salePriceCents`: Populated with the lowest active variant sale price (or product sale price when no variants).
