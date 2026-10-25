# Backend Requests & API Contract Notes

## 1. Product avgRating when reviewCount is 0
- **Observation:** Products with 0 approved customer reviews currently return `avgRating: 5.0` (or non-zero).
- **Request:** When `reviewCount == 0` (no approved reviews), the backend should return `avgRating: 0.0`. The frontend currently handles this client-side by displaying "No reviews yet" / "មិនទាន់មានការវាយតម្លៃនៅឡើយទេ" and omitting stars when `reviewCount == 0`.

## 2. Idempotency Key Handling
- **Observation:** `POST /api/checkout` requires `Idempotency-Key` header and caches the created `CheckoutResponseDto` in Redis under key `"idem:" + userId + ":" + idempotencyKey`.
- **Note:** In-flight concurrent requests before the first request finishes can both result in cache misses if a distributed lock / atomic `SETNX` reservation is not held at the beginning of the checkout transaction. The frontend protects against this via client-side in-flight submission locks (`isSubmittingRef`).

## 3. Flash Sale Pricing for Product Variants
- **Observation:** Adding a higher-tier variant (Laptop Pro 15 32GB/1TB, unit price $1,299.00 / 129900 cents) during an active flash sale causes `PricingServiceImpl` to calculate flash discount based on the base product `product.salePriceCents` (89900 cents / $899.00), reducing the variant's price to $899.00 with a $400.00 discount (`discountCents: 40000`, `totalCents: 89900`).
- **Raw CartDTO from backend:**
```json
{
  "id": 351,
  "items": [
    {
      "id": 99,
      "productId": 1001,
      "variantId": 3,
      "name": "Laptop Pro 15",
      "nameKm": "កុំព្យូទ័រយួរដៃ Laptop Pro 15",
      "imageUrl": "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800",
      "slug": "laptop-pro-15",
      "attributes": {
        "ram": "32GB",
        "color": "Space Gray",
        "storage": "1TB"
      },
      "unitPriceCents": 129900,
      "quantity": 1,
      "lineTotalCents": 129900,
      "available": 10,
      "issues": []
    }
  ],
  "itemCount": 1,
  "subtotalCents": 129900,
  "discountCents": 40000,
  "promotionDiscounts": [
    {
      "label": "Flash Sale Discount",
      "amountCents": 40000
    }
  ],
  "shippingEstimateCents": 0,
  "totalCents": 89900
}
```
- **Backend Root Cause:** In `PricingServiceImpl.java`:
  ```java
  flashDiscount = (item.unitPriceCents() - item.flashSalePriceCents()) * item.quantity();
  ```
  `item.flashSalePriceCents()` is populated from `product.getSalePriceCents()` (89900), assuming base price $999.00 ($100 discount). But for $1,299.00 variants, this calculates `129900 - 89900 = 40000` ($400 discount), effectively charging the base sale price $899.00.
- **Frontend Action Taken:** Frontend strictly preserves backend integrity without modifying `../back_end`. The product detail page reflects the true price charged by the cart ($899.00) and displays the variant regular price ($1,299.00) struck out as the "was" price.
