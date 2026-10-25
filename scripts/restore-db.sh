#!/usr/bin/env bash
# JPM HRMS - restore a backup made by backup-db.sh.
#
#   scripts/restore-db.sh <dump-file> <target-database> [uploads-archive.tar.gz] [upload-parent-dir]
#
# The target database is created if it does not exist. If it exists, its objects are dropped and
# replaced (pg_restore --clean). STOP THE APPLICATION before restoring into the live database.
# Connection settings: PGHOST PGPORT PGUSER and PGPASSWORD (or ~/.pgpass).
set -euo pipefail

DUMP="${1:?Usage: restore-db.sh <dump-file> <target-database> [uploads.tar.gz] [upload-parent-dir]}"
TARGET="${2:?Usage: restore-db.sh <dump-file> <target-database> [uploads.tar.gz] [upload-parent-dir]}"
[ -f "$DUMP" ] || { echo "No such file: $DUMP" >&2; exit 1; }

if [ "${CONFIRM:-}" != "yes" ]; then
  read -r -p "Restore $DUMP into database '$TARGET' (existing data in it will be replaced)? Type yes: " answer
  [ "$answer" = "yes" ] || { echo "Aborted."; exit 1; }
fi

if ! psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$TARGET'" | grep -q 1; then
  echo "Creating database $TARGET"
  createdb "$TARGET"
fi

echo "Restoring $DUMP into $TARGET"
pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --dbname="$TARGET" "$DUMP"

echo "Restored. Schema version and key row counts:"
psql -d "$TARGET" -c "SELECT max(version::int) AS schema_version FROM flyway_schema_history WHERE success" \
                  -c "SELECT (SELECT count(*) FROM candidates) AS candidates, (SELECT count(*) FROM interviews) AS interviews,
                             (SELECT count(*) FROM communications) AS communications, (SELECT count(*) FROM audit_logs) AS audit_logs"

if [ -n "${3:-}" ]; then
  DEST="${4:?Give the parent directory of UPLOAD_DIR to restore documents into}"
  echo "Restoring documents into $DEST"
  tar -xzf "$3" -C "$DEST"
fi
echo "Done."
