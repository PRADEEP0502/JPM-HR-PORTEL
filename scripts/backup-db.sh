#!/usr/bin/env bash
# JPM HRMS - PostgreSQL backup.
#
# Creates a compressed custom-format dump (pg_dump -Fc) plus an archive of the uploaded documents,
# checks that the dump is readable, and deletes backups older than RETENTION_DAYS.
#
# Connection settings come from the standard libpq variables (never hard-code passwords):
#   PGHOST PGPORT PGUSER PGDATABASE, and PGPASSWORD or ~/.pgpass
# Optional:
#   BACKUP_DIR      where backups are written           (default ./backups)
#   UPLOAD_DIR      document storage to archive as well  (default: skip)
#   RETENTION_DAYS  delete backups older than this       (default 14)
#
# Example (cron, daily at 01:30):
#   30 1 * * * PGHOST=localhost PGUSER=jpm_hrms PGDATABASE=jpm_hrms BACKUP_DIR=/var/backups/jpm-hrms \
#              UPLOAD_DIR=/srv/jpm-hrms/uploads /opt/jpm-hrms/scripts/backup-db.sh >> /var/log/jpm-hrms-backup.log 2>&1
set -euo pipefail

: "${PGDATABASE:?Set PGDATABASE (e.g. jpm_hrms)}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
umask 077   # backups contain personal data: owner-only permissions

DUMP="$BACKUP_DIR/jpm-hrms-db-$STAMP.dump"
echo "[$(date -Is)] Backing up database '$PGDATABASE' to $DUMP"
pg_dump --format=custom --compress=9 --no-owner --no-privileges --file="$DUMP" "$PGDATABASE"

# A backup that cannot be read is not a backup.
if ! pg_restore --list "$DUMP" > /dev/null; then
  echo "ERROR: dump $DUMP is not readable" >&2
  exit 1
fi
TABLES=$(pg_restore --list "$DUMP" | grep -c " TABLE DATA " || true)
echo "  dump OK: $(du -h "$DUMP" | cut -f1), $TABLES tables with data"

if [ -n "${UPLOAD_DIR:-}" ] && [ -d "$UPLOAD_DIR" ]; then
  FILES="$BACKUP_DIR/jpm-hrms-uploads-$STAMP.tar.gz"
  tar -czf "$FILES" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
  echo "  documents archived: $FILES ($(du -h "$FILES" | cut -f1))"
  echo "  NOTE: encrypted documents also need DOCUMENT_ENCRYPTION_KEY - keep it in your secrets manager, not with these files."
fi

find "$BACKUP_DIR" -maxdepth 1 -name 'jpm-hrms-*' -type f -mtime +"$RETENTION_DAYS" -print -delete | sed 's/^/  removed old backup: /' || true
echo "[$(date -Is)] Backup finished"
