#!/usr/bin/env bash
# JPM HRMS - restore a backup made by backup-db.sh.
#
#   scripts/restore-db.sh <archive-file> [uploads-archive.tar.gz] [upload-parent-dir]
#
# Restores into the database named in MONGODB_URI. Collections in the backup REPLACE the existing ones
# (mongorestore --drop). STOP THE APPLICATION before restoring into the live database.
#   MONGODB_URI   e.g. mongodb+srv://user:<password>@cluster/jpm_hrms (user needs readWrite on that database)
#   SOURCE_DB     database name inside the archive (default jpm_hrms) - set it when restoring into another name
set -euo pipefail

ARCHIVE="${1:?Usage: restore-db.sh <archive-file> [uploads.tar.gz] [upload-parent-dir]}"
[ -f "$ARCHIVE" ] || { echo "No such file: $ARCHIVE" >&2; exit 1; }
: "${MONGODB_URI:?Set MONGODB_URI (the database to restore into)}"
export MONGODB_URI
TARGET_DB="$(printf '%s' "$MONGODB_URI" | sed -E 's#^[a-z+]+://[^/]+/?([^?]*).*#\1#')"
TARGET_DB="${TARGET_DB:-jpm_hrms}"
SOURCE_DB="${SOURCE_DB:-jpm_hrms}"

if [ "${CONFIRM:-}" != "yes" ]; then
  read -r -p "Restore $ARCHIVE into database '$TARGET_DB' (its collections will be replaced)? Type yes: " answer
  [ "$answer" = "yes" ] || { echo "Aborted."; exit 1; }
fi

umask 077
CONFIG="$(mktemp)"
trap 'rm -f "$CONFIG"' EXIT
# mongorestore treats a database in the URI as a filter on the archive, which would skip the renamed restore;
# connect without it and select the source/target namespaces explicitly.
printf 'uri: "%s"\n' "$(printf '%s' "$MONGODB_URI" | sed -E 's#^([a-z+]+://[^/?]+)/?[^?]*#\1/#')" > "$CONFIG"

echo "Restoring $ARCHIVE into $TARGET_DB"
mongorestore --config="$CONFIG" --archive="$ARCHIVE" --gzip --drop --quiet \
  --nsFrom="$SOURCE_DB.*" --nsTo="$TARGET_DB.*"

echo "Restored. Key document counts:"
mongosh --quiet --nodb --eval "
  const db = connect(process.env.MONGODB_URI).getSiblingDB('$TARGET_DB');
  for (const c of ['candidates', 'interviews', 'communications', 'audit_logs', 'app_users']) {
    print('  ' + c.padEnd(16) + db.getCollection(c).countDocuments());
  }"

if [ -n "${2:-}" ]; then
  DEST="${3:?Give the parent directory of UPLOAD_DIR to restore documents into}"
  echo "Restoring documents into $DEST"
  tar -xzf "$2" -C "$DEST"
fi
echo "Done."
