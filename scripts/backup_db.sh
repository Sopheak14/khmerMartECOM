#!/usr/bin/env bash
# Dump the LOCAL DEV database to backups/khmermart-<YYYYMMDD-HHMM>.sql (backups/ is git-ignored).
# Read-only on the database; the backend may keep running.
#
# Usage:  scripts/backup_db.sh
# Config: DB_URL / DB_USER / DB_PASSWORD (defaults: localhost:5433/khmermart, khmermart/khmermart)
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/_db_common.sh
. scripts/_db_common.sh

case "${1:-}" in -h|--help) sed -n '2,6p' "$0"; exit 0 ;; esac
check_db_safety
command -v pg_dump >/dev/null || refuse "pg_dump is not installed."

mkdir -p backups
OUT="backups/khmermart-$(date +%Y%m%d-%H%M).sql"
[ ! -e "$OUT" ] || refuse "$OUT already exists (one backup per minute); wait a minute."

PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  --clean --if-exists --no-owner --no-privileges -f "$OUT.part"
mv "$OUT.part" "$OUT"
echo "Backup written: $OUT ($(du -h "$OUT" | cut -f1))"
