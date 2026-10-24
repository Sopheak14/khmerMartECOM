# API Contract Changes & Observations

## 1. Product Average Rating when Review Count is Zero
- **Issue**: Products with `reviewCount == 0` (e.g. Dell Latitude 5520) currently return `avgRating: 5.0` (or default initialized rating) in the backend product catalog and detail responses.
- **Expected**: Backend must return `avgRating: 0.0` (or `null`) when there are no approved reviews (`reviewCount == 0`).
- **Frontend Mitigation**: Frontend has been updated to check `reviewCount === 0`. If 0, it hides the star icon and displays "No reviews yet" (EN) / "មិនទាន់មានការវាយតម្លៃ" (KM) regardless of the backend `avgRating` value.

## 2. Search Autocomplete Promotional Pricing
- **Issue**: `/api/search/suggest` previously returned `product.priceCents` (regular price) even when products were actively on flash sale (e.g., Laptop Pro 15 at $899.00 vs regular $999.00).
- **Update**: `SearchServiceImpl.java` was updated to look up active flash sale promotions (`PromotionType.FLASH_SALE`) and return effective `salePriceCents` for promotional products.
