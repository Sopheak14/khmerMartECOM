#!/usr/bin/env python3
"""
KhmerMart — FULL contract conformance check (every endpoint in SHARED API CONTRACT section H).

Usage (backend must be running):
    python3 contract_check_full.py                      # default http://localhost:4000/api/v3/api-docs
    python3 contract_check_full.py http://host:4000/api/v3/api-docs

Compares METHOD + PATH against the live OpenAPI spec (springdoc).
Path parameter names are ignored ({id} == {paymentId}); the /api prefix is optional.
Exit code 0 = all present, 1 = something missing.
"""
import json
import re
import sys
import urllib.request

SPEC_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:4000/api/v3/api-docs"

CONTRACT = {
    "AUTH": [
        ("POST", "/auth/register"), ("POST", "/auth/login"), ("POST", "/auth/refresh"),
        ("POST", "/auth/logout"), ("POST", "/auth/forgot-password"),
        ("POST", "/auth/reset-password"), ("POST", "/auth/verify-email"),
    ],
    "USER": [
        ("GET", "/users/me"), ("PATCH", "/users/me"), ("PATCH", "/users/me/password"),
        ("GET", "/users/me/addresses"), ("POST", "/users/me/addresses"),
        ("PATCH", "/users/me/addresses/{id}"), ("DELETE", "/users/me/addresses/{id}"),
    ],
    "SETTINGS / HOME": [("GET", "/settings/public"), ("GET", "/home")],
    "CATALOG & SEARCH": [
        ("GET", "/categories"), ("GET", "/categories/{slug}"), ("GET", "/products"),
        ("GET", "/products/{idOrSlug}"), ("GET", "/search/suggest"),
    ],
    "REVIEWS": [
        ("GET", "/products/{id}/reviews"), ("POST", "/products/{id}/reviews"),
        ("PATCH", "/reviews/{id}"),
    ],
    "CART": [
        ("GET", "/cart"), ("POST", "/cart/items"), ("PATCH", "/cart/items/{id}"),
        ("DELETE", "/cart/items/{id}"), ("DELETE", "/cart"), ("POST", "/cart/coupon"),
        ("DELETE", "/cart/coupon"), ("POST", "/cart/merge"),
    ],
    "SHIPPING & TRACKING": [
        ("GET", "/shipping/methods"), ("GET", "/shipping/provinces"),
        ("GET", "/track/{trackingNumber}"),
    ],
    "CHECKOUT & ORDERS": [
        ("POST", "/checkout/preview"), ("POST", "/checkout"), ("GET", "/orders"),
        ("GET", "/orders/{orderNumber}"), ("POST", "/orders/{orderNumber}/cancel"),
        ("GET", "/orders/{orderNumber}/invoice.pdf"), ("POST", "/orders/{orderNumber}/reorder"),
    ],
    "PAYMENTS (KHQR)": [
        ("POST", "/payments/khqr/{orderNumber}"), ("POST", "/payments/khqr/{orderNumber}/regenerate"),
        ("GET", "/payments/{paymentId}/status"), ("GET", "/payments/{paymentId}/stream"),
        ("POST", "/payments/{paymentId}/simulate-paid"),
        ("POST", "/payments/bank-transfer/{orderNumber}/slip"),
    ],
    "NOTIFICATIONS & PUSH": [
        ("GET", "/notifications"), ("GET", "/notifications/unread-count"),
        ("PATCH", "/notifications/{id}/read"), ("PATCH", "/notifications/read-all"),
        ("GET", "/push/vapid-public-key"), ("POST", "/push/subscribe"),
    ],
    "ADMIN": [
        ("GET", "/admin/dashboard"),
        ("GET", "/admin/users"), ("PATCH", "/admin/users/{id}"),
        ("GET", "/admin/products"), ("POST", "/admin/products"), ("GET", "/admin/products/{id}"),
        ("PUT", "/admin/products/{id}"), ("DELETE", "/admin/products/{id}"),
        ("POST", "/admin/products/{id}/images"), ("PATCH", "/admin/products/{id}/images/order"),
        ("POST", "/admin/products/import"), ("GET", "/admin/products/export.csv"),
        ("GET", "/admin/categories"), ("POST", "/admin/categories"),
        ("PUT", "/admin/categories/{id}"), ("DELETE", "/admin/categories/{id}"),
        ("GET", "/admin/inventory"), ("POST", "/admin/inventory/{id}/restock"),
        ("POST", "/admin/inventory/{id}/adjust"), ("GET", "/admin/inventory/{id}/movements"),
        ("GET", "/admin/orders"), ("GET", "/admin/orders/{orderNumber}"),
        ("PATCH", "/admin/orders/{orderNumber}/status"), ("POST", "/admin/orders/{orderNumber}/ship"),
        ("POST", "/admin/orders/{orderNumber}/tracking-events"),
        ("GET", "/admin/payments"), ("GET", "/admin/payments/{id}"),
        ("POST", "/admin/payments/{id}/refund"), ("POST", "/admin/payments/{id}/approve-bank-transfer"),
        ("GET", "/admin/payments/export.csv"), ("GET", "/admin/payments/reconciliation"),
        ("GET", "/admin/coupons"), ("POST", "/admin/coupons"),
        ("PUT", "/admin/coupons/{id}"), ("DELETE", "/admin/coupons/{id}"),
        ("GET", "/admin/promotions"), ("POST", "/admin/promotions"),
        ("PUT", "/admin/promotions/{id}"), ("DELETE", "/admin/promotions/{id}"),
        ("GET", "/admin/reviews"), ("PATCH", "/admin/reviews/{id}"),
        ("GET", "/admin/reports/sales"), ("GET", "/admin/reports/sales.csv"),
        ("GET", "/admin/reports/sales.xlsx"),
        ("GET", "/admin/settings"), ("PUT", "/admin/settings"),
        ("GET", "/admin/audit-logs"),
    ],
    "HEALTH": [("GET", "/health")],
}


def norm(path: str) -> str:
    path = re.sub(r"\{[^}]+\}", "{}", path)
    if path.startswith("/api/"):
        path = path[4:]
    return path.rstrip("/") or "/"


def main() -> int:
    try:
        with urllib.request.urlopen(SPEC_URL, timeout=10) as r:
            spec = json.load(r)
    except Exception as e:  # noqa: BLE001
        print(f"❌ Cannot read OpenAPI spec from {SPEC_URL}: {e}")
        print("   Is the backend running? Try: ./mvnw spring-boot:run")
        return 1

    live = set()
    for p, ops in spec.get("paths", {}).items():
        for m in ops:
            if m.lower() in {"get", "post", "put", "patch", "delete"}:
                live.add((m.upper(), norm(p)))

    total = missing = 0
    expected = set()
    for section, endpoints in CONTRACT.items():
        print(f"\n== {section} ==")
        for method, path in endpoints:
            total += 1
            key = (method, norm(path))
            expected.add(key)
            ok = key in live
            missing += 0 if ok else 1
            print(f"  {'✅' if ok else '❌ MISSING'}  {method:6} /api{path}")

    extra = sorted(k for k in live if k not in expected and not k[1].startswith(("/dev/", "/v3/", "/docs")))
    print("\n" + "=" * 70)
    print(f"CONTRACT ENDPOINTS: {total} | PRESENT: {total - missing} | MISSING: {missing}")
    if extra:
        print(f"\nℹ️  {len(extra)} extra endpoint(s) not in the contract (OK if intentional):")
        for m, p in extra:
            print(f"     {m:6} /api{p}")
    print("=" * 70)
    print("RESULT:", "ALL ✅ — backend matches the contract" if missing == 0 else "❌ FIX THE MISSING ENDPOINTS")
    return 0 if missing == 0 else 1


if __name__ == "__main__":
    sys.exit(main())