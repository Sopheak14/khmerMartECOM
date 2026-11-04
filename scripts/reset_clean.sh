#!/usr/bin/env bash
# Reset the LOCAL DEV database to a CLEAN STORE (for a live demo / real data entry):
#   drop + re-create the schema -> Flyway migrations -> minimal seed ONLY:
#     1 ADMIN (admin@khmermart.local / Admin@12345), 25 provinces,
#     3 shipping methods (STANDARD, SAME_DAY, EXPRESS), settings (KhmerMart, 4100, KHQR_MODE=MOCK).
#   No products, categories, customers, staff, orders, reviews, coupons, promotions, notifications.
#   Later backend starts will NOT add the demo catalog (the seeder skips once the admin exists).
#   Files in uploads/ are not touched.
#
# Usage:  scripts/reset_clean.sh [--yes]
#   --yes   skip the confirmation prompt
#
# Config (same variables the backend reads; defaults match docker-compose / application.yml):
#   SPRING_PROFILES_ACTIVE  must be "dev" (default)
#   DB_URL      jdbc:postgresql://localhost:5433/khmermart   (host MUST be localhost)
#   DB_USER / DB_PASSWORD   khmermart / khmermart
#   REDIS_HOST / REDIS_PORT localhost / 6380                  (app cache keys are cleared)
#   BACKEND_URL http://localhost:4000                         (refuse to run if it answers)
#
# This script NEVER stops or starts the backend. Stop it yourself first, run this, then start it.
set -euo pipefail

cd "$(dirname "$0")/.."

YES=0
for arg in "$@"; do
  case "$arg" in
    --yes) YES=1 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $arg (use --yes)" >&2; exit 2 ;;
  esac
done

PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5433/khmermart}"
DB_USER="${DB_USER:-khmermart}"
DB_PASSWORD="${DB_PASSWORD:-khmermart}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6380}"
BACKEND_URL="${BACKEND_URL:-http://localhost:4000}"

refuse() { echo "REFUSED: $*" >&2; exit 1; }

# (a) safety checks -----------------------------------------------------------------------------
[ "$PROFILE" = "dev" ] || refuse "SPRING_PROFILES_ACTIVE is '$PROFILE'; this script only runs with the 'dev' profile."

if [[ ! "$DB_URL" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([^?]+) ]]; then
  refuse "cannot parse DB_URL '$DB_URL' (expected jdbc:postgresql://host[:port]/database)."
fi
DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-5432}"
DB_NAME="${BASH_REMATCH[4]}"
case "$DB_HOST" in
  localhost|127.0.0.1|::1) ;;
  *) refuse "database host is '$DB_HOST'; only localhost databases may be reset." ;;
esac
case "$DB_NAME" in
  *prod*|*production*) refuse "database name '$DB_NAME' looks like production." ;;
esac
command -v psql >/dev/null || refuse "psql is not installed."

if curl -s -m 2 -o /dev/null "$BACKEND_URL/api/health"; then
  refuse "a backend is answering at $BACKEND_URL. Stop it first (this script will not stop it), then re-run."
fi

# (b) confirmation ------------------------------------------------------------------------------
echo "About to DROP and re-create schema 'public' in database '$DB_NAME' at $DB_HOST:$DB_PORT (user $DB_USER)"
echo "and seed it with ONLY the admin, provinces, shipping methods and settings. ALL existing data will be lost."
if [ "$YES" -ne 1 ]; then
  [ -t 0 ] || refuse "no terminal for the confirmation prompt; pass --yes to confirm non-interactively."
  read -r -p 'Type "clean" to continue: ' answer
  [ "$answer" = "clean" ] || refuse "confirmation did not match; nothing was changed."
fi

# (c) drop + re-create schema -------------------------------------------------------------------
echo "[1/4] Dropping and re-creating schema public in $DB_NAME ..."
PGOPTIONS='-c client_min_messages=warning' PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c 'DROP SCHEMA IF EXISTS public CASCADE;' -c 'CREATE SCHEMA public;'

# clear app cache/counter keys so a stale cached home page or order counter cannot survive
echo "[2/4] Clearing KhmerMart cache keys in Redis $REDIS_HOST:$REDIS_PORT ..."
if command -v redis-cli >/dev/null && redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
  for pattern in 'home::*' 'categories::*' 'settings::*' 'adminDashboard::*' 'idem:*' 'order:seq:*'; do
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --scan --pattern "$pattern" \
      | xargs -r redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" del >/dev/null
  done
else
  echo "      (redis-cli or Redis not available - skipped; cached pages expire on their own)"
fi

# (d) Flyway + seeder: one-shot run of the app that migrates, seeds, then exits ------------------
echo "[3/4] Running Flyway migrations + seeder (one-shot, exits by itself) ..."
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT
export SPRING_PROFILES_ACTIVE=dev DB_URL DB_USER DB_PASSWORD REDIS_HOST REDIS_PORT
if ! ./mvnw -q -B spring-boot:run \
      -Dspring-boot.run.arguments="--server.port=0 --app.seed.reset=true --app.seed.mode=clean --app.seed.exit-after=true" \
      >"$LOG" 2>&1; then
  tail -40 "$LOG" >&2
  refuse "migration/seed run failed (last log lines above)."
fi
grep -E "Successfully applied|Clean seeding complete" "$LOG" | sed 's/^/      /' || true
grep -q "Clean seeding complete" "$LOG" || { tail -40 "$LOG" >&2; refuse "seeder did not report completion."; }

# (e) verify counts
echo "[4/4] Verifying the clean store ..."
PGPASSWORD="$DB_PASSWORD" psql -q -t -A -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT '      users=' || (SELECT count(*) FROM users) || ' provinces=' || (SELECT count(*) FROM provinces) || ' shipping_methods=' || (SELECT count(*) FROM shipping_methods) || ' products=' || (SELECT count(*) FROM products) || ' orders=' || (SELECT count(*) FROM orders)"

cat <<EOF

==================================================================================
 CLEAN RESET DONE.  The database '$DB_NAME' now holds only the admin account
 (admin@khmermart.local / Admin@12345), provinces, shipping methods and settings.
 >>> RESTART THE BACKEND NOW <<<  (this script did not stop or start it; a backend
 that was running before the reset has stale connections/caches and must be restarted)
 Add your own categories/products from the admin panel. (smoke_test.sh needs the FULL demo
 data - use scripts/reset_demo.sh for that.)
==================================================================================
EOF
