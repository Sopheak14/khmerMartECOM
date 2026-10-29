# API Contract Changes & Observations

## 1. Product Average Rating when Review Count is Zero
- **Issue**: Products with `reviewCount == 0` (e.g. Hol Silk Table Runner, product 1004) currently return `avgRating: 5.0` (or default initialized rating) in the backend product catalog and detail responses.
- **Expected**: Backend must return `avgRating: 0.0` (or `null`) when there are no approved reviews (`reviewCount == 0`).
- **Frontend Mitigation**: Frontend has been updated to check `reviewCount === 0`. If 0, it hides the star icon and displays "No reviews yet" (EN) / "មិនទាន់មានការវាយតម្លៃ" (KM) regardless of the backend `avgRating` value.

## 2. Search Autocomplete Promotional Pricing
- **Issue**: `/api/search/suggest` previously returned `product.priceCents` (regular price) even when products were actively on flash sale (e.g., Takeo Hol Silk Sampot Standard at $129.00 vs regular $149.00).
- **Update**: `SearchServiceImpl.java` was updated to look up active flash sale promotions (`PromotionType.FLASH_SALE`) and return effective `salePriceCents` for promotional products.

## 3. Per-Variant Flash Sale Pricing & VariantDTO.salePriceCents
- **Issue**: Previously, flash sale `salePriceCents` was stored and applied only at the product level. When a product had variants with differing prices (e.g. Takeo Hol Silk Sampot Standard at $149 vs Premium at $199), applying the product-level flash sale price ($129) caused the $199 Premium variant to sell for $129 (a $70 discount instead of remaining full price).
- **Rule**: Flash sale prices are per variant. In `promotion_products`, a row with `variant_id` applies only to that variant; a row with `variant_id NULL` applies only to products without variants. A product-level sale price is never applied to a variant with a different price.
- **Contract Additions**:
  - `VariantDTO.salePriceCents`: Additive, optional `Long` field present in the response only when that variant is on an active flash sale (omitted when not on sale).
  - `ProductCardDTO.salePriceCents` & `ProductDetailDTO.salePriceCents`: Populated with the lowest active variant sale price (or product sale price when no variants).

## 4. Admin Promotion Items (per-variant flash sales)
- **Issue**: `AdminPromotionRequest` only accepted `productIds`, so admins could not create the per-variant flash sales that pricing supports (`promotion_products.variant_id`).
- **Contract Additions** (additive):
  - `AdminPromotionRequest.items`: `[{ productId, variantId?, salePriceCents? }]`. `productIds` remains accepted (deprecated) and maps to items without `variantId`; `items` wins if both are sent.
  - `AdminPromotionDto.items`: `[{ productId, productName, variantId?, sku?, attributes?, priceCents, salePriceCents? }]`; `productIds` is kept.
- **Validation** (`400 VALIDATION_ERROR`, `error.details` keyed `items[i].variantId` / `items[i].salePriceCents` / ...): variant must belong to the product; a `FLASH_SALE` item for a product with variants must name a `variantId`; `salePriceCents` must be > 0 and < the variant's (or product's) `priceCents`.

## 5. Multiple Active Flash Sales
- **Change**: Several `FLASH_SALE` promotions can now be active at the same time (previously a second active one broke product pricing). Product/search/home/cart/checkout use all of them; `PublicSettingsDto.flashSale` and `HomeDto.flashSale` headline the soonest-ending one (home lists products from all).
- **Pricing rule**: each cart line gets at most one automatic promotion; among flash sales covering that variant (or product without variants) the lowest sale price wins.
- **Admin guard** (additive error): creating/updating an active `FLASH_SALE` whose items overlap in time with another active `FLASH_SALE` on the same variant (or product without variants) returns `409 CONFLICT` with `error.details["items[i]"]` naming the variant and the other promotion.
