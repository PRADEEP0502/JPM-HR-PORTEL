# Security checklist

What the application already does, and what you must configure before real candidate data goes in.

## Built in

| Area | Implementation |
|---|---|
| Authentication | Username + password, BCrypt hashes, signed JWT (HS256) with expiry (`JWT_TTL_MINUTES`, default 30 days). The browser keeps the token in `localStorage`, so users stay signed in on that device until they sign out or it expires; signing out removes it. Disabling a user or changing their role still takes effect on the next request. No public sign-up. |
| Brute force | Per-username lockout (5 failures → 15 min) **and** per-IP limit on sign-in (`LOGIN_ATTEMPTS_PER_IP`, default 20 per 5 min). Both in memory, per backend instance. |
| Authorisation | Every endpoint requires a role, checked on the server (`@PreAuthorize`): HR Admin, HR Executive, Interviewer. The role is re-read from the database on every request, so disabling a user takes effect immediately. Interviewers only reach their own assigned interviews; other ids return 404. |
| CSRF | Not applicable: the API is stateless and uses an `Authorization: Bearer` header, no auth cookies. |
| CORS | Only the origins in `CORS_ALLOWED_ORIGINS`. |
| Input | Bean Validation on every request body; whole-number rating 1–10; regex inputs escaped before MongoDB queries; unknown sort keys ignored. |
| Errors | Generic messages; no stack traces or internal details in responses (`server.error.include-*: never`). |
| Files | Extension allow-list + content signature check, random storage keys (no user-controlled paths), private storage, permission-checked and audited downloads, optional ClamAV scanning (fail closed), inline viewing only for PDF / images with a sandbox CSP, everything else served as a download. |
| Sensitive documents | Aadhaar copies need recorded consent and are AES-256-GCM encrypted (`DOCUMENT_ENCRYPTION_KEY`). |
| Response headers | `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` on the API, `X-Content-Type-Options: nosniff`, no caching of files. |
| Audit | Sign-ins (and failures), status / rating / archive changes, interviews, feedback, communication, uploads, views, downloads, deletions, exports, template and settings changes, user changes. |
| Exports | Exclude date of birth, address, salary, internal rating notes and credentials; spreadsheet formula injection is neutralised. |
| Secrets | Only from environment variables; `.env`, service-account keys and credential files are in `.gitignore`. Nothing secret is in `VITE_*` (those are public in the browser bundle). |

## Before going live

- [ ] **HTTPS only** (nginx / load balancer with a valid certificate); redirect HTTP to HTTPS; enable HSTS at the proxy.
- [ ] `JWT_SECRET` set to ≥ 32 random characters (`openssl rand -base64 48`). Startup fails without it outside dev.
- [ ] `DOCUMENT_ENCRYPTION_KEY` set and **stored safely outside the server** — losing it makes encrypted files unreadable.
- [ ] Bootstrap admin password changed after first sign-in, then `BOOTSTRAP_ADMIN_PASSWORD` removed.
- [ ] `DEMO_MODE=false`; any demo rows removed (*Settings → Demo data*); the demo `hr` account disabled if it exists.
- [ ] `CORS_ALLOWED_ORIGINS` = your exact frontend origin(s) only.
- [ ] **MongoDB:** app user with *readWrite on `jpm_hrms` only*; separate read-only backup user; network access limited
      to the backend (and the backup machine); TLS on (always on for Atlas); strong generated passwords.
- [ ] **Files:** `FILE_STORAGE` on persistent storage (`gridfs`, or `local` on a backed-up volume never served by the
      web server); `MALWARE_SCAN_MODE=clamav` with `MALWARE_SCAN_FAIL_CLOSED=true`.
- [ ] Google service-account key stored as a secret file, readable only by the app user; the sheet and upload folder
      shared with the service account as **Viewer**, never public.
- [ ] SMTP credentials in the environment / secrets manager; SPF and DKIM configured for the sending domain.
- [ ] Reverse proxy passes `X-Forwarded-For` and runs on a trusted internal address (the app uses
      `server.forward-headers-strategy=native`, which only trusts internal proxies).
- [ ] Daily `backup-db.sh` + weekly `verify-restore.sh`; a copy of backups kept off the server.
- [ ] Each HR person has their own account with the lowest role they need; disable accounts when people leave.
- [ ] Review *Settings → Audit log* regularly.
- [ ] Never commit `.env`, keys or certificates; rotate any secret that was ever shared in chat or email.

## Known limits

- Rate limits and login lockouts are in memory: they reset on restart and are per instance.
- Malware scanning covers files uploaded in the portal; files uploaded through the Google Form stay in Google Drive.
- Emails are "accepted by the provider", not confirmed as delivered; WhatsApp messages are sent by HR in WhatsApp.
- MongoDB writes are single-document (no multi-document transactions); a crash in the middle of a multi-step action
  (e.g. importing a candidate and its file links) can leave a partial record, which the next sync or HR can correct.
