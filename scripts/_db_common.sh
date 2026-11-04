# shellcheck shell=bash
# Shared by backup_db.sh / restore_db.sh. Same config variables as reset_demo.sh.
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5433/khmermart}"
DB_USER="${DB_USER:-khmermart}"
DB_PASSWORD="${DB_PASSWORD:-khmermart}"
BACKEND_URL="${BACKEND_URL:-http://localhost:4000}"

refuse() { echo "REFUSED: $*" >&2; exit 1; }

check_db_safety() {
  [ "$PROFILE" = "dev" ] || refuse "SPRING_PROFILES_ACTIVE is '$PROFILE'; only the 'dev' profile is allowed."
  if [[ ! "$DB_URL" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([^?]+) ]]; then
    refuse "cannot parse DB_URL '$DB_URL' (expected jdbc:postgresql://host[:port]/database)."
  fi
  DB_HOST="${BASH_REMATCH[1]}"
  DB_PORT="${BASH_REMATCH[3]:-5432}"
  DB_NAME="${BASH_REMATCH[4]}"
  case "$DB_HOST" in
    localhost|127.0.0.1|::1) ;;
    *) refuse "database host is '$DB_HOST'; only localhost databases are allowed." ;;
  esac
  case "$DB_NAME" in
    *prod*|*production*) refuse "database name '$DB_NAME' looks like production." ;;
  esac
}
