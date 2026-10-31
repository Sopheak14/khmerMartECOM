#!/usr/bin/env python3
"""
KhmerMart — FULL contract conformance check (every endpoint in SHARED API CONTRACT section H).

Usage (backend must be running):
    python3 contract_check_full.py                      # default http://localhost:4000/api/v3/api-docs
    python3 contract_check_full.py http://host:4000/api/v3/api-docs

Notes:
    - Contract addition (additive, optional):
        - VariantDTO.salePriceCents: present only when that variant is on an active flash sale.
        - ProductCardDTO/ProductDetailDTO.salePriceCents: lowest active variant sale price (or product sale price when no variants).
    - Flyway migration V3: promotion_products gets nullable variant_id; flash sale prices are per variant.

Checks, against the live OpenAPI spec (springdoc):
  1. every (METHOD, PATH) in CONTRACT below exists. If the path exists but only under a
     different HTTP method (e.g. contract says PATCH, live is PUT) it is reported as
     "METHOD MISMATCH", not just "missing";
  2. every `METHOD /api/path` bullet in docs/API_CONTRACT.md exists live (same method rules),
     so the written contract cannot drift from the controllers;
  3. (info only) live endpoints that the CONTRACT dict / the doc do not mention.
Path parameter names are ignored ({id} == {paymentId}); the /api prefix is optional.
Exit code 0 = all present with matching methods, 1 = anything missing or mismatched.
"""
import json
import pathlib
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
        ("POST", "/admin/payments/{id}/refund"), ("POST", "/admin/payments/{id}/recheck"), ("POST", "/admin/payments/{id}/approve-bank-transfer"),
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
    path = path.split("?", 1)[0]  # documented query strings are not part of the path
    path = re.sub(r"\{[^}]+\}", "{}", path)
    if path.startswith("/api/"):
        path = path[4:]
    return path.rstrip("/") or "/"


DOC_PATH = pathlib.Path(__file__).resolve().parent.parent / "docs" / "API_CONTRACT.md"
DOC_LINE = re.compile(r"`(GET|POST|PUT|PATCH|DELETE)\s+(/api/[^`\s]*)`")


def doc_endpoints() -> list:
    """(METHOD, normalized path) pairs written in docs/API_CONTRACT.md bullets."""
    found = []
    for line in DOC_PATH.read_text(encoding="utf-8").splitlines():
        # Only the leading endpoint of a bullet ("- `POST /api/x`: ..."), not prose mentions.
        m = re.match(r"\s*[-*]\s+" + DOC_LINE.pattern, line)
        if m:
            found.append((m.group(1), norm(m.group(2))))
    return found


def verdict(method: str, path: str, live: set, live_methods: dict) -> str:
    """'' if ok, else a human readable problem."""
    if (method, path) in live:
        return ""
    other = sorted(live_methods.get(path, set()))
    if other:
        return f"METHOD MISMATCH (live has {', '.join(other)})"
    return "MISSING"


def main() -> int:
    try:
        with urllib.request.urlopen(SPEC_URL, timeout=10) as r:
            spec = json.load(r)
    except Exception as e:  # noqa: BLE001
        print(f"❌ Cannot read OpenAPI spec from {SPEC_URL}: {e}")
        print("   Is the backend running? Try: ./mvnw spring-boot:run")
        return 1

    live = set()
    live_methods: dict = {}
    for p, ops in spec.get("paths", {}).items():
        for m in ops:
            if m.lower() in {"get", "post", "put", "patch", "delete"}:
                live.add((m.upper(), norm(p)))
                live_methods.setdefault(norm(p), set()).add(m.upper())

    total = bad = 0
    expected = set()
    for section, endpoints in CONTRACT.items():
        print(f"\n== {section} ==")
        for method, path in endpoints:
            total += 1
            key = (method, norm(path))
            expected.add(key)
            problem = verdict(method, norm(path), live, live_methods)
            bad += 1 if problem else 0
            print(f"  {'✅' if not problem else '❌ ' + problem}  {method:6} /api{path}")

    print("\n== docs/API_CONTRACT.md vs live ==")
    documented = doc_endpoints()
    doc_bad = 0
    for method, path in documented:
        problem = verdict(method, path, live, live_methods)
        if problem:
            doc_bad += 1
            print(f"  ❌ {problem}  {method:6} /api{path}   (written in API_CONTRACT.md)")
    if not doc_bad:
        print(f"  ✅ all {len(documented)} documented endpoints exist with the documented method")
    documented_set = set(documented)

    extra = sorted(k for k in live
                   if k not in expected and k not in documented_set
                   and not k[1].startswith(("/dev/", "/v3/", "/docs")))
    print("\n" + "=" * 70)
    print(f"CONTRACT ENDPOINTS: {total} | OK: {total - bad} | MISSING/MISMATCH: {bad}")
    print(f"DOC ENDPOINTS: {len(documented)} | STALE/MISMATCH: {doc_bad}")
    if extra:
        print(f"\nℹ️  {len(extra)} live endpoint(s) in neither the contract list nor the doc (OK if intentional):")
        for m, p in extra:
            print(f"     {m:6} /api{p}")
    print("=" * 70)
    ok = bad == 0 and doc_bad == 0
    print("RESULT:", "ALL ✅ — backend matches the contract (methods + paths)" if ok
          else "❌ FIX THE MISSING / MISMATCHED ENDPOINTS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
