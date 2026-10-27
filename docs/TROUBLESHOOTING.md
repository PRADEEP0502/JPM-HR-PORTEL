# Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend stops at startup: `Timed out ... while waiting for a server` / `Connection refused localhost:27017` | MongoDB is not running or `MONGODB_URI` is wrong | Windows: start the **MongoDB** service (`services.msc`). Atlas: check the URI, the password (URL-encode special characters), and that your IP is allowed under *Network Access*. Or use `EMBEDDED_MONGO=true` in dev. |
| `Authentication failed` from MongoDB | Wrong user/password or the user has no role on `jpm_hrms` | Recreate the Atlas user with *readWrite* on `jpm_hrms`; check the database name in the URI path. |
| Startup fails: `JWT_SECRET must be set` | Running without the `dev` profile and no secret | Set `JWT_SECRET` (≥ 32 random characters). |
| Startup fails: `app.files.storage must be 'local' or 'gridfs'` / `malware-scan.mode must be 'off' or 'clamav'` | Typo in `FILE_STORAGE` / `MALWARE_SCAN_MODE` | Use one of the listed values. |
| First start with embedded MongoDB is slow | Downloading the MongoDB binaries (~600 MB, once) | Wait; they are cached in your home folder (`.embedmongo`). |
| Sign-in says "Too many failed attempts" or "Too many sign-in attempts from this network" | Username lockout (15 min) or per-IP limit (5 min) | Wait and retry; an HR Admin can reset the password under *Settings → Users*. |
| Uploads fail: "Uploads are paused: the malware scanner is not available" | `MALWARE_SCAN_MODE=clamav` but clamd is unreachable (fail closed) | Start ClamAV / check `CLAMAV_HOST` and `CLAMAV_PORT`; ClamAV needs a few minutes after start to load signatures. |
| Upload rejected: "content does not match its extension" | The file is not really a PDF / JPG / PNG (renamed, or a Word file) | Export to PDF and upload again. Allowed types: `UPLOAD_ALLOWED_EXTENSIONS`. |
| "The file is larger than the upload limit" | File > `UPLOAD_MAX_FILE_SIZE` (10 MB) | Compress the PDF, or raise the limit (also raise nginx `client_max_body_size`). |
| Uploaded files disappeared after a redeploy | `FILE_STORAGE=local` on a host with a temporary disk | Use `FILE_STORAGE=gridfs` (or a persistent volume) and restore files from the last backup. |
| "This document could not be decrypted" | `DOCUMENT_ENCRYPTION_KEY` changed or missing | Restore the original key; files encrypted with a lost key cannot be recovered. |
| Résumé viewer shows "cannot be shown in the browser" | The file is not a PDF / image (e.g. an older DOCX) | Use **Download**. |
| Google sync: "Service-account credentials are not configured" | `GOOGLE_SERVICE_ACCOUNT_FILE` not set / unreadable | Set the path to the JSON key and restart. |
| Google sync: 403 / "permission denied" | Sheet or upload folder not shared with the service account | Share them with the service account's email as **Viewer**. |
| New Google Form rows are not imported | Auto-sync off, host asleep, or rows invalid | *Settings → Google Sheets integration → Sync now*; check *Sync history* for skipped rows. |
| WhatsApp button disabled | Mobile number missing or invalid | Fix the number on the profile (10-digit Indian mobiles start with 6–9). |
| Message "No upcoming interview is scheduled" | Interview invitation / reminder needs a future interview | Schedule one, or pick another message type (acknowledgement, selection, rejection, joining). |
| Email button says the service is not configured | `MAIL_HOST` etc. not set | Set the `MAIL_*` variables and restart; use *Send test email*. |
| Gmail SMTP: authentication failed | Normal password used | Turn on 2-Step Verification and create an **App Password**. |
| An Interviewer sees no interviews | The interview is not linked to their account | When scheduling, type the interviewer's name exactly as their account's full name (pick it from the suggestions). Existing interviews: edit the interview and re-enter the name. |
| Browser shows "Cannot reach the server" | Backend down, or frontend pointing at the wrong API | Check the backend; for separate hosts set `VITE_API_BASE_URL` at build time and `CORS_ALLOWED_ORIGINS` on the backend. |
| `backup-db.sh: mongodump: command not found` | MongoDB Database Tools not installed | Install them and make sure `mongodump`, `mongorestore` (and `mongosh` for the verify script) are on the `PATH`. |
| `verify-restore.sh` fails with "not authorized" | The backup user is read-only | Run the verify script with a user that may create and drop databases. |
| Tests fail with "Unresolved compilation problem" | IDE-compiled classes in `target/` | Run `./mvnw clean test`. |
