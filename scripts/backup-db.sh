#!/usr/bin/env bash
# JPM HRMS - MongoDB backup.
#
# Creates a compressed archive of the database (mongodump --archive --gzip) plus an archive of the uploaded
# documents, checks that the archive is readable, and deletes backups older than RETENTION_DAYS.
#
# Requires the free MongoDB Database Tools (mongodump, mongorestore):
#   https://www.mongodb.com/try/download/database-tools
#
# Settings (never hard-code passwords in this file):
#   MONGODB_URI     connection string, e.g. mongodb+srv://backup-user:<password>@cluster0.xxxx.mongodb.net/jpm_hrms
#                   Use a read-only user (Atlas role "backup" or "read") for backups.
#   BACKUP_DIR      where backups are written           (default ./backups)
#   UPLOAD_DIR      document storage to archive as well  (default: skip)
#   RETENTION_DAYS  delete backups older than this       (default 14)
#
# Example (cron, daily at 01:30; the URI comes from a root-only env file):
#   30 1 * * * . /etc/jpm-hrms/backup.env && BACKUP_DIR=/var/backups/jpm-hrms UPLOAD_DIR=/srv/jpm-hrms/uploads \
#              /opt/jpm-hrms/scripts/backup-db.sh >> /var/log/jpm-hrms-backup.log 2>&1
set -euo pipefail

: "${MONGODB_URI:?Set MONGODB_URI (e.g. mongodb+srv://user:password@cluster/jpm_hrms)}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
umask 077   # backups contain personal data: owner-only permissions

# The URI contains the password: pass it in a private config file, not on the command line (visible in `ps`).
CONFIG="$(mktemp)"
trap 'rm -f "$CONFIG"' EXIT
printf 'uri: "%s"\n' "$MONGODB_URI" > "$CONFIG"

ARCHIVE="$BACKUP_DIR/jpm-hrms-db-$STAMP.archive.gz"
echo "[$(date -Is)] Backing up MongoDB to $ARCHIVE"
mongodump --config="$CONFIG" --archive="$ARCHIVE" --gzip --quiet

# A backup that cannot be read is not a backup: read the whole archive without writing anything.
if ! mongorestore --config="$CONFIG" --archive="$ARCHIVE" --gzip --dryRun --quiet; then
  echo "ERROR: archive $ARCHIVE is not readable" >&2
  exit 1
fi
echo "  archive OK: $(du -h "$ARCHIVE" | cut -f1)"

if [ -n "${UPLOAD_DIR:-}" ] && [ -d "$UPLOAD_DIR" ]; then
  FILES="$BACKUP_DIR/jpm-hrms-uploads-$STAMP.tar.gz"
  tar -czf "$FILES" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
  echo "  documents archived: $FILES ($(du -h "$FILES" | cut -f1))"
  echo "  NOTE: encrypted documents also need DOCUMENT_ENCRYPTION_KEY - keep it in your secrets manager, not with these files."
fi

find "$BACKUP_DIR" -maxdepth 1 -name 'jpm-hrms-*' -type f -mtime +"$RETENTION_DAYS" -print -delete | sed 's/^/  removed old backup: /' || true
echo "[$(date -Is)] Backup finished"
