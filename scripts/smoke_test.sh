#!/usr/bin/env bash
# KhmerMart end-to-end smoke test of a RUNNING backend (teacher's 12-step purchase flow + extras).
#
# Usage:   scripts/smoke_test.sh                      # http://localhost:4000
#          BASE=http://host:4000 scripts/smoke_test.sh
# Needs:   bash, curl, jq, pdftotext (poppler-utils), uuidgen (or /proc), date (GNU)
#
# Catalog under test: the Khmer-only seed (see SeedCatalog.java). The hero product 1001 "Takeo Hol
# Silk Sampot" has 4 variants (Ruby Red / Royal Blue x Standard / Premium); only the two Standard
# variants are on the 7-day flash sale. Every expected number below is derived from the seed
# constants in the block that follows, never typed in as a literal result.
#
# Safe to re-run: registers a NEW throwaway customer (smoke+<timestamp>@khmermart.local) every run,
# never edits the seeded demo accounts, uses KHQR MOCK mode (simulate-paid only), and restores the
# stock it temporarily changes. It does NOT start, stop or restart anything.
# Exit code 0 = every step passed, 1 = first failure (remaining steps are not run).

set -euo pipefail

BASE="${BASE:-http://localhost:4000}"
API="$BASE/api"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@khmermart.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@12345}"
CUSTOMER_PASSWORD="Smoke@12345"
CUSTOMER_PHONE="+85512345678"
STAMP="$(date +%s)"
CUSTOMER_EMAIL="smoke+${STAMP}@khmermart.local"
RECIPIENT="សុភាព"
LINE1="ផ្លូវ ២០០៤ ខណ្ឌពោធិ៍សែនជ័យ"
DISTRICT="ពោធិ៍សែនជ័យ"

# ---- seed constants (mirror com.khmermart.seed.SeedCatalog; all money in USD cents) ----
HERO=1001
V_RED_STD=1; V_RED_PRM=2; V_BLUE_STD=3; V_BLUE_PRM=4          # variant ids of the hero product
STD_CENTS=14900; PRM_CENTS=19900; FLASH_CENTS=12900            # Standard / Premium / Standard on flash
PEPPER_ID=1006; PEPPER_FLASH_CENTS=790                         # Kampot Black Pepper 100 g (flash, product-wide)
MANGO_ID=1022; MANGO_FLASH_CENTS=320                           # Dried Mango 200 g (flash, product-wide)
SAVE5_CENTS=500
SEASONAL_NAME="Pchum Ben Festival Sale"
ROOT_CATEGORIES=8
FLASH_OFF=$((STD_CENTS - FLASH_CENTS))                         # 2000
DEMO_TOTAL=$((FLASH_CENTS - SAVE5_CENTS))                      # 12400 (free STANDARD shipping, subtotal >= 5000)

TMP="$(mktemp -d)"
JAR="$TMP/cookies.txt"
BODY="$TMP/body.json"
HDRS="$TMP/headers.txt"
HTTP=""
SUMMARY=()
CUR_N=""
CUR_NAME=""
FAILED=0

trap 'rc=$?; summary; rm -rf "$TMP"; exit $rc' EXIT

# ---------------------------------------------------------------- helpers
uuid() { cat /proc/sys/kernel/random/uuid; }

summary() {
  echo
  echo "================================ SUMMARY ================================"
  printf '%-6s %-62s %s\n' "STEP" "NAME" "RESULT"
  printf '%-6s %-62s %s\n' "------" "--------------------------------------------------------------" "------"
  local row
  for row in "${SUMMARY[@]:-}"; do
    [ -z "$row" ] && continue
    IFS='|' read -r n name res <<<"$row"
    printf '%-6s %-62s %s\n' "$n" "$name" "$res"
  done
  if [ "$FAILED" -eq 1 ]; then
    printf '%-6s %-62s %s\n' "${CUR_N}" "${CUR_NAME}" "FAIL"
    echo "RESULT: FAILED at step ${CUR_N} (${CUR_NAME})"
  elif [ "${#SUMMARY[@]}" -gt 0 ]; then
    echo "RESULT: ALL ${#SUMMARY[@]} STEPS PASSED"
  fi
  echo "========================================================================="
}

step() { # step <n> <name>
  CUR_N="$1"; CUR_NAME="$2"
  echo
  echo "STEP $1 — $2"
}

pass() { # close current step
  echo "  => PASS"
  SUMMARY+=("${CUR_N}|${CUR_NAME}|PASS")
}

fail() { # fail <message>
  echo "  ✘ $*"
  echo "  => FAIL"
  FAILED=1
  exit 1
}

info() { echo "  · $*"; }

# req METHOD PATH [json-body] [extra curl args...]  -> sets $HTTP and $BODY (auth via $AUTH_HDR)
AUTH_HDR=""
req() {
  local method="$1" path="$2" data="${3:-}"
  shift 3 || shift $#
  local args=(-sS -m 30 -X "$method" -o "$BODY" -D "$HDRS" -w '%{http_code}' -b "$JAR" -c "$JAR")
  [ -n "$AUTH_HDR" ] && args+=(-H "$AUTH_HDR")
  if [ -n "$data" ]; then args+=(-H 'Content-Type: application/json' --data-binary "$data"); fi
  HTTP="$(curl "${args[@]}" "$@" "$API$path")" || fail "curl failed for $method $path (is the backend up at $BASE?)"
  echo "  $method $path -> HTTP $HTTP"
}

j() { jq -r "$1" "$BODY"; }          # extract from last response
jc() { jq -c "$1" "$BODY"; }         # compact JSON of a sub-tree

expect_eq() { # expect_eq <description> <actual> <expected>
  if [ "$2" == "$3" ]; then echo "  ✔ $1: $2"; else fail "$1: expected '$3' but got '$2'"; fi
}
expect_status() { expect_eq "HTTP status" "$HTTP" "$1"; }
expect_true() { # expect_true <description> <condition-result 0/1>
  if [ "$2" -eq 0 ]; then echo "  ✔ $1"; else fail "$1"; fi
}
expect_match() { # expect_match <description> <value> <regex>
  if [[ "$2" =~ $3 ]]; then echo "  ✔ $1: ${2:0:60}"; else fail "$1: '$2' does not match /$3/"; fi
}

login_admin() {
  AUTH_HDR=""
  req POST /auth/login "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email:$e,password:$p}')" >/dev/null
  [ "$HTTP" = "200" ] || fail "admin login failed (HTTP $HTTP)"
  ADMIN_AUTH="Authorization: Bearer $(j .data.accessToken)"
}

as_admin()    { AUTH_HDR="$ADMIN_AUTH"; }
as_customer() { AUTH_HDR="$CUST_AUTH"; }

# inventory row (admin) for a variant: prints "<inventoryId> <onHand> <reserved> <available>"
inv_row() {
  local saved="$AUTH_HDR"; as_admin
  curl -sS -m 30 -H "$ADMIN_AUTH" "$API/admin/inventory?size=1000" -o "$TMP/inv.json"
  jq -r --argjson v "$1" '.data[]|select(.variantId==$v)|"\(.id) \(.quantityOnHand) \(.quantityReserved) \(.available)"' "$TMP/inv.json"
  AUTH_HDR="$saved"
}
inv_field() { # inv_field <variantId> <1=id 2=onHand 3=reserved 4=available>
  inv_row "$1" | awk -v f="$2" '{print $f}'
}
admin_stock() { # admin_stock restock|adjust <inventoryId> <qty> <reason>
  local saved="$AUTH_HDR"; as_admin
  req POST "/admin/inventory/$2/$1" "$(jq -nc --argjson q "$3" --arg r "$4" '{quantity:$q,reason:$r}')" >/dev/null
  AUTH_HDR="$saved"
  [ "$HTTP" = "200" ] || fail "admin $1 of inventory $2 by $3 failed (HTTP $HTTP)"
}
ensure_available() { # ensure_available <variantId> <min>  (top up through the admin restock API)
  local id avail
  id="$(inv_field "$1" 1)"; avail="$(inv_field "$1" 4)"
  [ -n "$id" ] || fail "no inventory row for variant $1"
  if [ "$avail" -lt "$2" ]; then
    admin_stock restock "$id" 20 "smoke test top-up"
    info "variant $1 had only $avail available -> restocked +20"
  fi
}

movements_for() { # movements_for <inventoryId> -> JSON array into $TMP/mov.json
  curl -sS -m 30 -H "$ADMIN_AUTH" "$API/admin/inventory/$1/movements" -o "$TMP/mov.json"
}

# register a customer, add an address, put <variant> x1 in the cart; echo "token addressId"
make_buyer() { # make_buyer <email> <variantId>
  local tok aid
  curl -sS -m 30 -X POST "$API/auth/register" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg e "$1" --arg p "$CUSTOMER_PASSWORD" --arg ph "$CUSTOMER_PHONE" '{email:$e,password:$p,fullName:"Smoke Buyer",phone:$ph}')" -o "$TMP/r.json"
  tok="$(jq -r .data.accessToken "$TMP/r.json")"
  curl -sS -m 30 -X POST "$API/users/me/addresses" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg r "$RECIPIENT" --arg l "$LINE1" --arg d "$DISTRICT" --arg ph "$CUSTOMER_PHONE" '{label:"Home",recipientName:$r,phone:$ph,line1:$l,district:$d,province:"Phnom Penh",isDefault:true}')" -o "$TMP/a.json"
  aid="$(jq -r .data.id "$TMP/a.json")"
  curl -sS -m 30 -X POST "$API/cart/items" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -d "{\"productId\":$HERO,\"variantId\":$2,\"quantity\":1}" -o "$TMP/c.json"
  echo "$tok $aid"
}

# ---------------------------------------------------------------- STEP 0 — preflight
step 0 "Preflight: backend reachable, current build, flash sale active"
req GET /health "" >/dev/null
[ "$HTTP" = "200" ] || fail "backend not reachable at $BASE (HTTP $HTTP) — start it first; this script never starts or restarts anything"
login_admin
as_admin
req GET /admin/promotions/1 "" >/dev/null
expect_status 200
expect_eq "promotion 1 returns per-variant items[] (current build)" "$(j '.data.items|type')" "array"
expect_eq "promotion 1 is a FLASH_SALE" "$(j .data.type)" "FLASH_SALE"
req GET /products/$HERO "" >/dev/null
expect_eq "variant 1 on flash sale (seed data intact, sale not expired)" "$(j '.data.variants[0].salePriceCents')" "$FLASH_CENTS"
ensure_available "$V_RED_STD" 5
ensure_available "$V_BLUE_PRM" 5
pass

# ---------------------------------------------------------------- STEP 1 — health
step 1 "Health (db/redis ok, khqrMode MOCK)"
AUTH_HDR=""
req GET /health "" >/dev/null
expect_status 200
info "body: $(jc .data)"
expect_eq "status" "$(j .data.status)" "ok"
expect_eq "db" "$(j .data.db)" "ok"
expect_eq "redis" "$(j .data.redis)" "ok"
expect_eq "khqrMode" "$(j .data.khqrMode)" "MOCK"
pass

# ---------------------------------------------------------------- STEP 2 — register + login
step 2 "Register + login (accessToken, km_rt HttpOnly cookie)"
AUTH_HDR=""
req POST /auth/register "$(jq -nc --arg e "$CUSTOMER_EMAIL" --arg p "$CUSTOMER_PASSWORD" --arg ph "$CUSTOMER_PHONE" '{email:$e,password:$p,fullName:"Smoke Tester",phone:$ph}')" >/dev/null
expect_status 200
info "registered $CUSTOMER_EMAIL (role $(j .data.user.role))"
req POST /auth/login "$(jq -nc --arg e "$CUSTOMER_EMAIL" --arg p "$CUSTOMER_PASSWORD" '{email:$e,password:$p}')" >/dev/null
expect_status 200
TOKEN="$(j .data.accessToken)"
expect_match "accessToken is a JWT" "$TOKEN" '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'
COOKIE_LINE="$(grep -i '^set-cookie: km_rt=' "$HDRS" | head -1 | tr -d '\r')"
info "${COOKIE_LINE:0:90}..."
expect_true "km_rt cookie is HttpOnly" "$(echo "$COOKIE_LINE" | grep -qi 'HttpOnly'; echo $?)"
CUST_AUTH="Authorization: Bearer $TOKEN"
USER_ID="$(j .data.user.id)"
pass

# ---------------------------------------------------------------- STEP 3 — browse
step 3 "Browse: 8 Khmer categories, search 'silk', hero variants, flash + seasonal"
AUTH_HDR=""
req GET /categories "" >/dev/null
expect_status 200
info "top-level categories: $(jc '[.data[]|.name]')"
expect_eq "root category count" "$(j '.data|length')" "$ROOT_CATEGORIES"
expect_eq "first category" "$(j '.data[0].slug')" "silk-textiles"
req GET "/products?q=silk" "" >/dev/null
expect_status 200
info "search hits: $(jc '[.data[]|{id,name}]' | head -c 300)"
expect_true "search 'silk' finds the hero product" "$(jq -e --argjson h "$HERO" '[.data[]|select(.id==$h)]|length==1' "$BODY" >/dev/null; echo $?)"
req GET /products/$HERO "" >/dev/null
expect_status 200
info "variants: $(jc '[.data.variants[]|{id,attributes,priceCents,salePriceCents}]')"
expect_eq "variant count (color x grade)" "$(j '.data.variants|length')" "4"
expect_eq "Ruby Red Standard salePriceCents" "$(j '.data.variants[0].salePriceCents')" "$FLASH_CENTS"
expect_eq "Ruby Red Premium has no salePriceCents" "$(j '.data.variants[1].salePriceCents')" "null"
expect_eq "Royal Blue Standard salePriceCents" "$(j '.data.variants[2].salePriceCents')" "$FLASH_CENTS"
expect_eq "Royal Blue Premium has no salePriceCents" "$(j '.data.variants[3].salePriceCents')" "null"
expect_eq "Premium price" "$(j '.data.variants[1].priceCents')" "$PRM_CENTS"
req GET /products/$PEPPER_ID "" >/dev/null
expect_eq "Kampot Black Pepper flash price (product-wide)" "$(j .data.salePriceCents)" "$PEPPER_FLASH_CENTS"
req GET /products/$MANGO_ID "" >/dev/null
expect_eq "Dried Mango flash price (product-wide)" "$(j .data.salePriceCents)" "$MANGO_FLASH_CENTS"
req GET /settings/public "" >/dev/null
expect_eq "seasonal banner" "$(j .data.seasonalBanner.title)" "$SEASONAL_NAME"
pass

# ---------------------------------------------------------------- STEP 4 — cart
step 4 "Cart: Standard variant at flash price, Premium variant without flash discount"
as_customer
AVAIL_V1_BEFORE="$(inv_field "$V_RED_STD" 4)"
req POST /cart/items "{\"productId\":$HERO,\"variantId\":$V_RED_STD,\"quantity\":1}" >/dev/null
expect_status 200
expect_eq "cart total with Standard ($STD_CENTS - $FLASH_OFF flash)" "$(j .data.totalCents)" "$FLASH_CENTS"
expect_eq "flash discount" "$(j .data.discountCents)" "$FLASH_OFF"
req POST /cart/items "{\"productId\":$HERO,\"variantId\":$V_RED_PRM,\"quantity\":1}" >/dev/null
expect_status 200
expect_eq "Premium line total (full price)" "$(j '.data.items[]|select(.variantId=='"$V_RED_PRM"')|.lineTotalCents')" "$PRM_CENTS"
expect_eq "discount unchanged by Premium (no flash on it)" "$(j .data.discountCents)" "$FLASH_OFF"
expect_eq "cart subtotal" "$(j .data.subtotalCents)" "$((STD_CENTS + PRM_CENTS))"
expect_eq "cart total" "$(j .data.totalCents)" "$((STD_CENTS + PRM_CENTS - FLASH_OFF))"
info "promotionDiscounts: $(jc .data.promotionDiscounts)"
# keep only the Standard variant: that is the demo order (14900 - 2000 flash - 500 SAVE5 = 12400)
PREMIUM_ITEM_ID="$(j '.data.items[]|select(.variantId=='"$V_RED_PRM"')|.id')"
req DELETE "/cart/items/$PREMIUM_ITEM_ID" "" >/dev/null
expect_status 200
expect_eq "cart back to the Standard variant only" "$(j .data.subtotalCents)" "$STD_CENTS"
pass

# ---------------------------------------------------------------- STEP 5 — coupon
step 5 "Coupon: SAVE5 applies, INVALIDCODE -> 422 COUPON_*"
as_customer
req POST /cart/coupon '{"code":"SAVE5"}' >/dev/null
expect_status 200
expect_eq "coupon code" "$(j .data.coupon.code)" "SAVE5"
expect_eq "coupon value (the $SAVE5_CENTS line)" "$(j .data.coupon.value)" "$SAVE5_CENTS"
expect_eq "discountCents = $FLASH_OFF flash + $SAVE5_CENTS coupon" "$(j .data.discountCents)" "$((FLASH_OFF + SAVE5_CENTS))"
expect_eq "cart total" "$(j .data.totalCents)" "$DEMO_TOTAL"
req POST /cart/coupon '{"code":"INVALIDCODE"}' >/dev/null
expect_status 422
info "error: $(jc .error)"
expect_match "error code" "$(j .error.code)" '^COUPON_'
pass

# ---------------------------------------------------------------- STEP 6 — address
step 6 "Address with Khmer recipient and street"
as_customer
req POST /users/me/addresses "$(jq -nc --arg r "$RECIPIENT" --arg l "$LINE1" --arg d "$DISTRICT" --arg ph "$CUSTOMER_PHONE" '{label:"Home",recipientName:$r,phone:$ph,line1:$l,district:$d,province:"Phnom Penh",isDefault:true}')" >/dev/null
expect_status 200
ADDRESS_ID="$(j .data.id)"
expect_eq "recipientName round-trips" "$(j .data.recipientName)" "$RECIPIENT"
expect_eq "line1 round-trips" "$(j .data.line1)" "$LINE1"
pass

# ---------------------------------------------------------------- STEP 7 — shipping + preview
step 7 "Shipping methods + checkout preview arithmetic"
as_customer
req GET /shipping/methods "" >/dev/null
expect_status 200
info "methods: $(jc '[.data[]|{code,feeCents,freeOverCents}]')"
expect_true "STANDARD method offered" "$([ "$(j '[.data[]|select(.code=="STANDARD")]|length')" -eq 1 ]; echo $?)"
AUTH_HDR=""; req GET /settings/public "" >/dev/null; KHR_RATE="$(j .data.khrRate)"; as_customer
req POST /checkout/preview "$(jq -nc --argjson a "$ADDRESS_ID" '{addressId:$a,shippingMethodCode:"STANDARD",paymentMethod:"KHQR"}')" >/dev/null
expect_status 200
SUB="$(j .data.subtotalCents)"; DISC="$(j .data.discountCents)"; SHIP="$(j .data.shippingCents)"; TOT="$(j .data.totalCents)"; TOTKHR="$(j .data.totalKhr)"
expect_eq "demo order total (14900 - 2000 - 500)" "$TOT" "$DEMO_TOTAL"
info "subtotal=$SUB discount=$DISC shipping=$SHIP total=$TOT totalKhr=$TOTKHR (khrRate=$KHR_RATE)"
expect_eq "subtotal - discounts + shipping = total" "$((SUB - DISC + SHIP))" "$TOT"
EXPECT_KHR="$(awk -v t="$TOT" -v r="$KHR_RATE" 'BEGIN{printf "%.0f", t/100*r}')"
expect_eq "totalKhr = total/100 x khrRate" "$TOTKHR" "$EXPECT_KHR"
expect_eq "totalKhr for the demo order ($DEMO_TOTAL cents x $KHR_RATE)" "$TOTKHR" "$((DEMO_TOTAL * KHR_RATE / 100))"
pass

# ---------------------------------------------------------------- STEP 8 — checkout (KHQR) + idempotency
step 8 "Checkout KHQR with Idempotency-Key (and replay)"
as_customer
IDEM="$(uuid)"
CHECKOUT_BODY="$(jq -nc --argjson a "$ADDRESS_ID" '{addressId:$a,shippingMethodCode:"STANDARD",paymentMethod:"KHQR"}')"
req POST /checkout "$CHECKOUT_BODY" -H "Idempotency-Key: $IDEM" >/dev/null
expect_status 200
ORDER_NO="$(j .data.order.orderNumber)"
ORDER_ID="$(j .data.order.id)"
expect_match "orderNumber format" "$ORDER_NO" '^KM-[0-9]{8}-[0-9]{6}$'
expect_eq "order status" "$(j .data.order.status)" "PENDING_PAYMENT"
expect_eq "order total matches preview" "$(j .data.order.totalCents)" "$TOT"
expect_eq "next action" "$(j .data.next)" "KHQR_PAYMENT"
AVAIL_V1_RESERVED="$(inv_field "$V_RED_STD" 4)"
expect_eq "variant 1 available dropped by 1 (reserved)" "$AVAIL_V1_RESERVED" "$((AVAIL_V1_BEFORE - 1))"
req POST /checkout "$CHECKOUT_BODY" -H "Idempotency-Key: $IDEM" >/dev/null
expect_status 200
expect_eq "same Idempotency-Key -> same orderNumber" "$(j .data.order.orderNumber)" "$ORDER_NO"
expect_eq "replay did not reserve stock twice" "$(inv_field "$V_RED_STD" 4)" "$AVAIL_V1_RESERVED"
pass

# ---------------------------------------------------------------- STEP 9 — KHQR + simulate-paid
step 9 "KHQR payment (MOCK): QR, md5, expiry, status, simulate-paid, SALE movement"
as_customer
req POST "/payments/khqr/$ORDER_NO" "" >/dev/null
expect_status 200
PAYMENT_ID="$(j .data.paymentId)"
QR="$(j .data.qrString)"
expect_eq "qrString starts with 000201" "${QR:0:6}" "000201"
expect_match "md5 present (32 hex)" "$(j .data.md5)" '^[a-fA-F0-9]{32}$'
EXP_EPOCH="$(date -u -d "$(j .data.expiresAt)" +%s)"
DELTA=$((EXP_EPOCH - $(date -u +%s)))
info "expiresAt=$(j .data.expiresAt) (in ${DELTA}s), mode=$(j .data.mode), amountCents=$(j .data.amountCents)"
expect_true "expiresAt is about now + 10 min (540-610s)" "$([ "$DELTA" -ge 540 ] && [ "$DELTA" -le 610 ]; echo $?)"
expect_eq "KHQR mode" "$(j .data.mode)" "MOCK"
expect_eq "amountCents = order total" "$(j .data.amountCents)" "$TOT"
req GET "/payments/$PAYMENT_ID/status" "" >/dev/null
expect_eq "payment status before paying" "$(j .data.status)" "PENDING"
req POST "/payments/$PAYMENT_ID/simulate-paid" "" >/dev/null
expect_status 200
expect_eq "payment status" "$(j .data.status)" "PAID"
expect_eq "order status" "$(j .data.orderStatus)" "CONFIRMED"
INV1="$(inv_field "$V_RED_STD" 1)"
movements_for "$INV1"
expect_true "SALE movement recorded for variant 1 / this order" "$(jq -e --argjson o "$ORDER_ID" '[.data[]|select(.type=="SALE" and .refOrderId==$o)]|length==1' "$TMP/mov.json" >/dev/null; echo $?)"
expect_eq "variant 1 available decreased by 1 vs before checkout" "$(inv_field "$V_RED_STD" 4)" "$((AVAIL_V1_BEFORE - 1))"
pass

# ---------------------------------------------------------------- STEP 10 — admin ship + tracking
step 10 "Admin: ship, tracking event, public tracking page"
login_admin; as_admin
req POST "/admin/orders/$ORDER_NO/ship" '{"carrier":"KhmerMart Express"}' >/dev/null
expect_status 200
expect_eq "order status" "$(j .data.status)" "SHIPPED"
TRACKING="$(j .data.trackingNumber)"
expect_match "tracking number" "$TRACKING" '^KMTRK[0-9]+$'
req POST "/admin/orders/$ORDER_NO/tracking-events" '{"status":"IN_TRANSIT","location":"Phnom Penh Hub","note":"Arrived at sorting hub"}' >/dev/null
expect_status 200
AUTH_HDR=""
req GET "/track/$TRACKING" "" >/dev/null
expect_status 200
info "events: $(jc '[.data.events[]|{status,location}]')"
expect_eq "public tracking shows the added event" "$(j '[.data.events[]|select(.status=="IN_TRANSIT" and .location=="Phnom Penh Hub")]|length')" "1"
pass

# ---------------------------------------------------------------- STEP 11 — notifications, delivered, invoice
step 11 "Notifications, DELIVERED, invoice.pdf (Khmer + coupon line)"
as_customer
req GET /notifications/unread-count "" >/dev/null
expect_status 200
info "unread: $(jc .data)"
expect_true "unread-count > 0" "$([ "$(j .data.count)" -gt 0 ]; echo $?)"
as_admin
req PATCH "/admin/orders/$ORDER_NO/status" '{"status":"DELIVERED","note":"Delivered (smoke test)"}' >/dev/null
expect_status 200
expect_eq "order status" "$(j .data.status)" "DELIVERED"
PDF="$TMP/invoice.pdf"
CT="$(curl -sS -m 60 -H "$CUST_AUTH" -o "$PDF" -w '%{http_code} %{content_type}' "$API/orders/$ORDER_NO/invoice.pdf")"
echo "  GET /orders/$ORDER_NO/invoice.pdf -> $CT"
expect_eq "invoice HTTP status + content-type" "$CT" "200 application/pdf"
expect_eq "PDF magic bytes" "$(head -c 4 "$PDF")" "%PDF"
TEXT="$(pdftotext -layout "$PDF" -)"
expect_true "invoice contains recipient $RECIPIENT" "$(grep -qF "$RECIPIENT" <<<"$TEXT"; echo $?)"
expect_true "invoice contains 'Coupon (SAVE5)'" "$(grep -qF 'Coupon (SAVE5)' <<<"$TEXT"; echo $?)"
info "invoice excerpt: $(grep -E 'Subtotal|Coupon|Total \(USD\)' <<<"$TEXT" | sed 's/  */ /g' | tr '\n' ';')"
pass

# ---------------------------------------------------------------- STEP 12 — review
step 12 "Review: 5 stars accepted, second review rejected"
as_customer
req POST /products/$HERO/reviews '{"rating":5,"title":"Beautiful silk","body":"Smoke test review, lovely hand-woven sampot."}' >/dev/null
expect_status 201
expect_eq "rating" "$(j .data.rating)" "5"
expect_eq "verifiedPurchase" "$(j .data.verifiedPurchase)" "true"
req POST /products/$HERO/reviews '{"rating":4,"title":"Again","body":"Second review should be refused."}' >/dev/null
info "error: $(jc .error)"
expect_eq "second review rejected (HTTP)" "$HTTP" "409"
pass

# ---------------------------------------------------------------- EXTRA A — COD cancel
step X1 "COD order -> cancel -> stock released"
as_customer
AV4_BEFORE="$(inv_field "$V_BLUE_PRM" 4)"
req DELETE /cart "" >/dev/null
req POST /cart/items "{\"productId\":$HERO,\"variantId\":$V_BLUE_PRM,\"quantity\":1}" >/dev/null
expect_status 200
req POST /checkout "$(jq -nc --argjson a "$ADDRESS_ID" '{addressId:$a,shippingMethodCode:"STANDARD",paymentMethod:"COD"}')" -H "Idempotency-Key: $(uuid)" >/dev/null
expect_status 200
COD_NO="$(j .data.order.orderNumber)"; COD_ID="$(j .data.order.id)"
expect_eq "COD order is CONFIRMED" "$(j .data.order.status)" "CONFIRMED"
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) available reserved" "$(inv_field "$V_BLUE_PRM" 4)" "$((AV4_BEFORE - 1))"
req POST "/orders/$COD_NO/cancel" '{"reason":"smoke test"}' >/dev/null
expect_status 200
expect_eq "order status" "$(j .data.status)" "CANCELLED"
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) stock restored" "$(inv_field "$V_BLUE_PRM" 4)" "$AV4_BEFORE"
movements_for "$(inv_field "$V_BLUE_PRM" 1)"
COD_MOVE="$(jq -r --argjson o "$COD_ID" '[.data[]|select(.refOrderId==$o and (.type=="RELEASE" or .type=="RETURN"))|.type]|first' "$TMP/mov.json")"
info "cancel movement for an UNPAID COD order: $COD_MOVE (RETURN is only written when the order was already PAID)"
expect_true "stock-restoring movement (RELEASE/RETURN) written" "$([ "$COD_MOVE" = "RELEASE" ] || [ "$COD_MOVE" = "RETURN" ]; echo $?)"
pass

# ---------------------------------------------------------------- EXTRA B — paid KHQR cancel -> refund
step X2 "Paid KHQR order -> cancel -> payment REFUNDED + RETURN movement"
as_customer
req POST /cart/items "{\"productId\":$HERO,\"variantId\":$V_BLUE_PRM,\"quantity\":1}" >/dev/null
expect_status 200
req POST /checkout "$(jq -nc --argjson a "$ADDRESS_ID" '{addressId:$a,shippingMethodCode:"STANDARD",paymentMethod:"KHQR"}')" -H "Idempotency-Key: $(uuid)" >/dev/null
expect_status 200
K_NO="$(j .data.order.orderNumber)"; K_ID="$(j .data.order.id)"
req POST "/payments/khqr/$K_NO" "" >/dev/null
K_PID="$(j .data.paymentId)"
req POST "/payments/$K_PID/simulate-paid" "" >/dev/null
expect_eq "payment status" "$(j .data.status)" "PAID"
req POST "/orders/$K_NO/cancel" '{"reason":"smoke test refund"}' >/dev/null
expect_status 200
req GET "/orders/$K_NO" "" >/dev/null
expect_eq "order status" "$(j .data.status)" "CANCELLED"
expect_eq "order paymentStatus" "$(j .data.paymentStatus)" "REFUNDED"
as_admin
req GET "/admin/payments/$K_PID" "" >/dev/null
expect_eq "payment status (admin view)" "$(j .data.status)" "REFUNDED"
movements_for "$(inv_field "$V_BLUE_PRM" 1)"
expect_true "RETURN movement recorded for this order" "$(jq -e --argjson o "$K_ID" '[.data[]|select(.type=="RETURN" and .refOrderId==$o)]|length==1' "$TMP/mov.json" >/dev/null; echo $?)"
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) stock restored" "$(inv_field "$V_BLUE_PRM" 4)" "$AV4_BEFORE"
pass

# ---------------------------------------------------------------- EXTRA C — auth boundaries
step X3 "No token -> 401; customer -> admin API -> 403"
AUTH_HDR=""
req GET /users/me "" >/dev/null
expect_status 401
as_customer
req GET /admin/dashboard "" >/dev/null
expect_status 403
expect_eq "error code" "$(j .error.code)" "FORBIDDEN"
pass

# ---------------------------------------------------------------- EXTRA D — qty > stock
step X4 "Quantity above stock -> 409 OUT_OF_STOCK"
as_customer
req DELETE /cart "" >/dev/null
req POST /cart/items "{\"productId\":$HERO,\"variantId\":$V_BLUE_PRM,\"quantity\":9999}" >/dev/null
expect_status 409
info "error: $(jc .error)"
expect_eq "error code" "$(j .error.code)" "OUT_OF_STOCK"
pass

# ---------------------------------------------------------------- EXTRA E — parallel last-3-units
step X5 "10 parallel checkouts for the last 3 units -> exactly 3 succeed"
INV4="$(inv_field "$V_BLUE_PRM" 1)"
AVAIL0="$(inv_field "$V_BLUE_PRM" 4)"
DELTA_STOCK=$((3 - AVAIL0))
if [ "$DELTA_STOCK" -gt 0 ]; then admin_stock restock "$INV4" "$DELTA_STOCK" "smoke test: set last 3 units"
elif [ "$DELTA_STOCK" -lt 0 ]; then admin_stock adjust "$INV4" "$DELTA_STOCK" "smoke test: set last 3 units"; fi
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) available set to exactly 3" "$(inv_field "$V_BLUE_PRM" 4)" "3"
RES="$TMP/par"; mkdir -p "$RES"
for i in $(seq 1 10); do
  read -r t a < <(make_buyer "smoke+${STAMP}-par${i}@khmermart.local" 4)
  echo "$t" >"$RES/tok$i"; echo "$a" >"$RES/addr$i"
done
info "10 buyers ready, each with Royal Blue Premium x1 in the cart; firing together"
export API RES
seq 1 10 | xargs -P 10 -I{} bash -c '
  t=$(cat "$RES/tok{}"); a=$(cat "$RES/addr{}")
  curl -sS -m 60 -X POST "$API/checkout" -H "Authorization: Bearer $t" -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
    -d "{\"addressId\":$a,\"shippingMethodCode\":\"STANDARD\",\"paymentMethod\":\"COD\"}" \
    -o "$RES/out{}.json" -w "%{http_code}" >"$RES/code{}"'
OK=0; OOS=0; OTHER=0
for i in $(seq 1 10); do
  c="$(cat "$RES/code$i")"
  if [ "$c" = "200" ]; then OK=$((OK + 1))
  elif [ "$c" = "409" ] && [ "$(jq -r .error.code "$RES/out$i.json")" = "OUT_OF_STOCK" ]; then OOS=$((OOS + 1))
  else OTHER=$((OTHER + 1)); info "buyer $i unexpected: HTTP $c $(head -c 200 "$RES/out$i.json")"; fi
done
info "results: $OK x 200, $OOS x 409 OUT_OF_STOCK, $OTHER other"
expect_eq "successful checkouts" "$OK" "3"
expect_eq "rejected with 409 OUT_OF_STOCK" "$OOS" "7"
expect_eq "unexpected responses" "$OTHER" "0"
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) available after the race (never negative)" "$(inv_field "$V_BLUE_PRM" 4)" "0"
# cleanup: cancel the 3 winners (releases their reservations), then undo the temporary stock change
for i in $(seq 1 10); do
  if [ "$(cat "$RES/code$i")" = "200" ]; then
    curl -sS -m 30 -X POST "$API/orders/$(jq -r .data.order.orderNumber "$RES/out$i.json")/cancel" \
      -H "Authorization: Bearer $(cat "$RES/tok$i")" -H 'Content-Type: application/json' -d '{"reason":"smoke cleanup"}' -o /dev/null
  fi
done
expect_eq "winners cancelled -> available back to 3" "$(inv_field "$V_BLUE_PRM" 4)" "3"
if [ "$DELTA_STOCK" -ne 0 ]; then admin_stock adjust "$INV4" "$((-DELTA_STOCK))" "smoke test: restore stock"; fi
expect_eq "Royal Blue Premium (variant $V_BLUE_PRM) stock restored to original" "$(inv_field "$V_BLUE_PRM" 4)" "$AVAIL0"
pass
