#!/usr/bin/env bash
# Restore the LOCAL DEV database from a dump made by scripts/backup_db.sh.
# Replaces schema 'public' - ALL current data in the database is lost.
#
# Usage:  scripts/restore_db.sh backups/khmermart-YYYYMMDD-HHMM.sql [--yes]
#   --yes   skip the prompt (otherwise you must type "restore")
# The backend MUST be stopped (this script refuses if it answers) and is never started/stopped here.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/_db_common.sh
. scripts/_db_common.sh

FILE=""; YES=0
for arg in "$@"; do
  case "$arg" in
    --yes) YES=1 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    -*) echo "Unknown argument: $arg" >&2; exit 2 ;;
    *) FILE="$arg" ;;
  esac
done
[ -n "$FILE" ] || { echo "Usage: scripts/restore_db.sh <dump.sql> [--yes]" >&2; exit 2; }
[ -f "$FILE" ] || refuse "dump file '$FILE' not found."

check_db_safety
command -v psql >/dev/null || refuse "psql is not installed."
if curl -s -m 2 -o /dev/null "$BACKEND_URL/api/health"; then
  refuse "a backend is answering at $BACKEND_URL. Stop it first (this script will not stop it), then re-run."
fi

echo "About to REPLACE all data in database '$DB_NAME' at $DB_HOST:$DB_PORT (user $DB_USER) with $FILE."
if [ "$YES" -ne 1 ]; then
  [ -t 0 ] || refuse "no terminal for the confirmation prompt; pass --yes to confirm non-interactively."
  read -r -p 'Type "restore" to continue: ' answer
  [ "$answer" = "restore" ] || refuse "confirmation did not match; nothing was changed."
fi

export PGPASSWORD="$DB_PASSWORD"
PGOPTIONS='-c client_min_messages=warning' psql -v ON_ERROR_STOP=1 -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c 'DROP SCHEMA IF EXISTS public CASCADE;' -c 'CREATE SCHEMA public;'
PGOPTIONS='-c client_min_messages=warning' psql -v ON_ERROR_STOP=1 -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$FILE"

echo "Restored $FILE into '$DB_NAME'. Start the backend again; flush Redis caches if pages look stale."
