#!/usr/bin/env bash
# JPM HRMS - prove that a backup can actually be restored.
#
#   scripts/verify-restore.sh [archive-file]     (default: newest archive in BACKUP_DIR)
#
# Restores the archive into a temporary database on the same server, compares the document counts of the key
# collections with the live database, then drops the temporary database. Run it regularly (e.g. weekly from
# cron) - an untested backup should be assumed broken.
#
# Requires the MongoDB Database Tools (mongorestore) and mongosh. The user in MONGODB_URI needs permission to
# create and drop the temporary database (e.g. Atlas role "readWriteAnyDatabase"; not the read-only backup user).
#   MONGODB_URI   connection string of the live database, e.g. mongodb+srv://user:<password>@cluster/jpm_hrms
#   BACKUP_DIR    where backups are stored (default ./backups)
set -euo pipefail

: "${MONGODB_URI:?Set MONGODB_URI (the live database to compare against)}"
export MONGODB_URI
BACKUP_DIR="${BACKUP_DIR:-./backups}"
ARCHIVE="${1:-$(ls -1t "$BACKUP_DIR"/jpm-hrms-db-*.archive.gz 2>/dev/null | head -n1 || true)}"
[ -n "$ARCHIVE" ] && [ -f "$ARCHIVE" ] || { echo "No backup found (looked in $BACKUP_DIR)" >&2; exit 1; }

# Database name = path of the URI (mongodb://host/jpm_hrms?opts -> jpm_hrms).
LIVE_DB="$(printf '%s' "$MONGODB_URI" | sed -E 's#^[a-z+]+://[^/]+/?([^?]*).*#\1#')"
LIVE_DB="${LIVE_DB:-jpm_hrms}"
SCRATCH="jpm_hrms_restore_check_$$"
COLLECTIONS="candidates interviews communications message_templates hr_remarks candidate_documents app_users departments audit_logs counters"

umask 077
CONFIG="$(mktemp)"
printf 'uri: "%s"\n' "$MONGODB_URI" > "$CONFIG"
cleanup() {
  mongosh --quiet --nodb --eval "const c = connect(process.env.MONGODB_URI); c.getSiblingDB('$SCRATCH').dropDatabase()" >/dev/null 2>&1 || true
  rm -f "$CONFIG"
}
trap cleanup EXIT

echo "Verifying $ARCHIVE using temporary database $SCRATCH"
mongorestore --config="$CONFIG" --archive="$ARCHIVE" --gzip --quiet \
  --nsFrom="$LIVE_DB.*" --nsTo="$SCRATCH.*"

# Counts are read with mongosh; the URI is passed through the environment, never on the command line.
COUNTS="$(MONGODB_URI="$MONGODB_URI" mongosh --quiet --nodb --eval "
  const conn = connect(process.env.MONGODB_URI);
  for (const c of '$COLLECTIONS'.split(' ')) {
    print(c + ' ' + conn.getSiblingDB('$LIVE_DB').getCollection(c).countDocuments() + ' '
            + conn.getSiblingDB('$SCRATCH').getCollection(c).countDocuments());
  }")"

STATUS=0
while read -r name live rest; do
  [ -n "$name" ] || continue
  mark="ok"
  # The live database may have grown since the backup; fewer restored documents than live is expected then.
  if [ "$rest" -gt "$live" ]; then mark="MORE THAN LIVE?"; STATUS=1; fi
  if [ "$rest" -eq 0 ] && [ "$live" -gt 0 ]; then mark="EMPTY - CHECK"; STATUS=1; fi
  printf '  %-22s live=%-7s restored=%-7s %s\n' "$name" "$live" "$rest" "$mark"
done <<< "$COUNTS"

if [ "$STATUS" -eq 0 ]; then echo "RESTORE CHECK PASSED"; else echo "RESTORE CHECK FAILED" >&2; fi
exit "$STATUS"
