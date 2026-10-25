#!/usr/bin/env bash
# JPM HRMS - prove that a backup can actually be restored.
#
#   scripts/verify-restore.sh [dump-file]     (default: newest dump in BACKUP_DIR)
#
# Restores the dump into a temporary database, checks the schema version and compares row counts
# of the key tables with the live database, then drops the temporary database. Run it regularly
# (e.g. weekly from cron) - an untested backup should be assumed broken.
# Connection settings: PGHOST PGPORT PGUSER PGDATABASE and PGPASSWORD (or ~/.pgpass).
set -euo pipefail

: "${PGDATABASE:?Set PGDATABASE (the live database to compare against)}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
DUMP="${1:-$(ls -1t "$BACKUP_DIR"/jpm-hrms-db-*.dump 2>/dev/null | head -n1 || true)}"
[ -n "$DUMP" ] && [ -f "$DUMP" ] || { echo "No backup found (looked in $BACKUP_DIR)" >&2; exit 1; }
SCRATCH="jpm_hrms_restore_check_$$"
TABLES="candidates interviews communications message_templates hr_remarks candidate_documents app_users departments audit_logs"

cleanup() { dropdb --if-exists "$SCRATCH" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "Verifying $DUMP using temporary database $SCRATCH"
createdb "$SCRATCH"
pg_restore --no-owner --no-privileges --exit-on-error --dbname="$SCRATCH" "$DUMP"

LIVE_V=$(psql -d "$PGDATABASE" -tAc "SELECT max(version::int) FROM flyway_schema_history WHERE success")
REST_V=$(psql -d "$SCRATCH" -tAc "SELECT max(version::int) FROM flyway_schema_history WHERE success")
echo "  schema version: live=$LIVE_V restored=$REST_V"

STATUS=0
for t in $TABLES; do
  live=$(psql -d "$PGDATABASE" -tAc "SELECT count(*) FROM $t")
  rest=$(psql -d "$SCRATCH" -tAc "SELECT count(*) FROM $t")
  mark="ok"
  # The live database may have grown since the backup; fewer restored rows than live is expected then.
  if [ "$rest" -gt "$live" ]; then mark="MORE THAN LIVE?"; STATUS=1; fi
  if [ "$rest" -eq 0 ] && [ "$live" -gt 0 ]; then mark="EMPTY - CHECK"; STATUS=1; fi
  printf '  %-22s live=%-7s restored=%-7s %s\n' "$t" "$live" "$rest" "$mark"
done
[ "$REST_V" = "$LIVE_V" ] || { echo "  WARNING: schema versions differ (backup predates a migration?)"; }

if [ "$STATUS" -eq 0 ]; then echo "RESTORE CHECK PASSED"; else echo "RESTORE CHECK FAILED" >&2; fi
exit "$STATUS"
