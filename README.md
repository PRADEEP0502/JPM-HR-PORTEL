# JPM HR Portal – Recruitment Management System

Internal recruitment system for **JPM – Junior Processing Mill**. Candidates apply through a **Google Form**;
responses land in a private **Google Sheet** and the backend **synchronises** them into **MongoDB**. HR staff
review, rate, interview and contact candidates in a responsive web dashboard. Interviewers see only the
interviews assigned to them.

```
Candidate ──► Google Form ──► Google Sheet (private)
                                   │  Sheets / Drive API (service account, read-only, server-side only)
                                   ▼
React dashboard ◄── HTTPS + JWT ──► Spring Boot API ──► MongoDB (Atlas or self-hosted)
                                   │                └─► file storage (private folder or MongoDB GridFS)
                                   ├──► SMTP provider (emails, sent only by an HR action)
                                   └──► wa.me click-to-chat link (HR reviews and presses send in WhatsApp)
```

| Layer | Stack |
|---|---|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Lucide icons, Recharts, TanStack Query |
| Backend | Spring Boot 3.5 (Java 21+), Spring Security (JWT, BCrypt), Spring Data MongoDB, Spring Mail, Apache POI, Maven |
| Database | MongoDB 6+ — MongoDB Atlas or self-hosted (tests run on a real embedded MongoDB 7.0) |
| Files | Storage interface: private server folder or MongoDB GridFS; optional ClamAV malware scanning |
| Google | Service-account auth, Sheets v4 + Drive v3 REST, read-only scopes |

More guides: **[Deployment (free start → production)](docs/DEPLOYMENT.md)** ·
**[Security checklist](docs/SECURITY.md)** · **[Troubleshooting](docs/TROUBLESHOOTING.md)**

---

## Features

- **Dashboard** — KPI cards (total, new, today's, shortlisted, interviews scheduled, selected, rejected, pending,
  pending verification), status pipeline previews, today's and upcoming interviews, department rail and charts.
  Every number is computed from the database.
- **Candidates** — search; filters for department, experience, application status (several at once), interview
  status, verification, **Profile Match Rating**, **application date range** and **archived**; sorting (including
  rating with unrated last, and department name); pagination; **Excel or CSV export**. A table on larger screens,
  cards on phones.
- **Candidate profile** — form details, references, documents with an **in-browser résumé viewer**, HR remarks,
  **status history**, **rating history**, interviews with **interviewer feedback**, communication history and an
  activity timeline. Archive / restore (nothing is deleted).
- **Profile Match Rating (1–10)** — HR-only assessment with guideline bands (1–2 Not Suitable · 3–4 Low · 5–6
  Average · 7–8 Good · 9–10 Excellent), an internal note and full history. Never shown to candidates and never used
  to reject anyone automatically.
- **Recruitment statuses** — Applied, Under Review, Shortlisted, Interview Scheduled, Interview Completed, Selected,
  Rejected, On Hold, Joined. Every change records who, when and an optional note.
- **Pipeline board** — active candidates per status; moving a card uses the same audited status update.
- **Interviews** — scheduling with clash detection, rescheduling, completion, cancellation; link an interview to an
  Interviewer account so they can see it and record feedback.
- **Communication** — WhatsApp and email templates for **application acknowledgement, interview invitation,
  interview reminder, selection, rejection and joining instructions**; versioned, previewed, and only sent by an HR
  action. Honest statuses (WhatsApp is *prepared*, email is *accepted by provider* — never "delivered").
- **Reports** (Excel / CSV) — department-wise, monthly, applications by date, recruitment status, rating
  distribution, selected, selected & joined, rejected, pending verification, interview schedule, recruitment
  activity history.
- **Google Forms sync** — scheduled or manual, duplicate-safe; invalid rows are reported, not guessed.
- **Security** — JWT, role checks on every endpoint, login rate limiting, audit log, private file storage with
  type checks and malware scanning, Aadhaar copies encrypted at rest. See [docs/SECURITY.md](docs/SECURITY.md).
- **Demo mode** (off by default; `DEMO_MODE=true`) — 64 fictional, clearly tagged `DEMO` candidates in an empty
  database, with a banner on every page; removable under *Settings → Demo data*.

> **Not available:** a *job position* filter / report. The Google Form does not ask for a position, so there is no
> position data to filter on. Add a "Position applied for" question to the form first if you need it.

### Roles

| Capability | HR Admin (`ADMIN`) | HR Executive (`HR`) | Interviewer |
|---|:-:|:-:|:-:|
| Dashboard, candidates, profiles, documents, remarks, archive | ✓ | ✓ | |
| Update recruitment status, assign / change Profile Match Rating | ✓ | ✓ | |
| Schedule interviews; call / WhatsApp / email candidates | ✓ | ✓ | |
| Reports and exports | ✓ | ✓ | |
| Communication templates, company name & venue, test email | ✓ | | |
| Google Sheets integration, users & roles, departments, audit log, demo data | ✓ | | |
| Own assigned interviews: limited candidate summary (no contact details, salary, references or rating), résumé, feedback | | | ✓ |
| Candidates | Apply through the Google Form only — no dashboard access | | |

Roles are enforced by the backend on every request. There is no public sign-up; the first HR Admin is created from
`BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` when no users exist, and further users are added under
*Settings → Users*. An interview is linked to an Interviewer account when the interviewer name typed while
scheduling matches that account's full name or username (the form suggests the names).

---

## Candidate communication

`{{CompanyName}}`, `{{Venue}}` (default **JPM – Junior Processing Mill**) and `{{HRSignature}}` come from
*Settings → Communication templates*. No template invents an address, date or salary — where a detail is not
known, the wording says HR will confirm it.

| Message type | Needs an upcoming interview | Default email subject |
|---|:-:|---|
| Application acknowledgement | | Application Received – JPM |
| Interview invitation | ✓ | Interview Invitation – JPM |
| Interview reminder | ✓ | Interview Reminder – JPM |
| Selection notification | | Selection Update – JPM |
| Rejection notification | | Application Update – JPM |
| Joining instructions | | Joining Instructions – JPM |

- **Placeholders:** `CandidateName`, `Department`, `Venue`, `CompanyName`, `HRSignature`, and — only in interview
  messages — `InterviewDate`, `InterviewTime`, `InterviewRound`. Unknown or malformed placeholders are rejected.
  `CandidateName` is always required; interview messages also require the date and time.
- **No half-filled messages:** if a value is missing (e.g. no department or no upcoming interview), nothing can be
  sent and the reason is shown.
- **Versioning:** only HR Admins change templates. Every save creates a new version per message type and channel;
  each communication record keeps the exact text and version used.

| Channel | How it works | Statuses recorded |
|---|---|---|
| **Call** | Logs the call and opens the `tel:` link; HR records the outcome | Call started (+ outcome, notes) |
| **WhatsApp** | Builds the message, validates the number, opens `https://wa.me/<number>?text=…`. **HR reviews and presses send in WhatsApp.** | **Prepared**; **Sent (HR confirmed)** if HR confirms. Delivery is never claimed. |
| **Email** | Sent from the backend through your SMTP provider | **Accepted by provider** (with Message-ID) or **Failed** (with error and **Retry**) |

**Email setup:** set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` and `MAIL_FROM_NAME`
(see `.env.example`; e.g. Gmail / Google Workspace `smtp.gmail.com:587` with an App Password), restart, then use
*Settings → Communication templates → Send test email*. Credentials never reach the browser.

**WhatsApp:** click-to-chat needs no account. Automatic sending would need the WhatsApp Business Platform (a paid,
verified Meta setup with approved templates and candidate opt-in) — **not implemented**; ask before adding it.

---

## Files: résumés and certificates

- Accepted types: **PDF, JPG, JPEG, PNG** by default (`UPLOAD_ALLOWED_EXTENSIONS`), up to 10 MB
  (`UPLOAD_MAX_FILE_SIZE`). The file's actual content must match its extension (e.g. an `.exe` renamed to `.pdf` is
  rejected).
- Files are stored under a **random key** — the uploaded name is only display text, never a path. Storage is
  private; there are no public file URLs. Every view / download goes through the API with a permission check and is
  audited.
- **Storage** (`FILE_STORAGE`): `local` = a private folder (`UPLOAD_DIR`, must be persistent and backed up);
  `gridfs` = inside MongoDB GridFS (no server disk needed, included in `mongodump` backups — use this on hosts with
  temporary disks). Each document remembers where it is stored, so switching later keeps older files readable. A
  private cloud bucket can be added by implementing `document/storage/FileStorage`.
- **Malware scanning** (`MALWARE_SCAN_MODE`): `off` in development; `clamav` scans every upload with a ClamAV daemon
  (free; e.g. the `clamav/clamav` Docker image). With `MALWARE_SCAN_FAIL_CLOSED=true` uploads are refused while the
  scanner is down. Files that came from the Google Form stay in Google Drive and are not re-scanned.
- **Viewer:** PDFs and images open inside the portal from a temporary in-memory link; other types download.
- **Aadhaar copies** require HR-confirmed consent and `DOCUMENT_ENCRYPTION_KEY` (AES-256-GCM at rest).

---

## Local development

**Prerequisites:** JDK 21+ (tested on JDK 25), Node.js 20+, and **MongoDB** — the free MongoDB Community Server on
your machine (Windows installs it as the "MongoDB" service on `localhost:27017`) or a free MongoDB Atlas cluster.
Maven is not needed (`mvnw` is included).

```bash
# backend - http://localhost:8080 (uses mongodb://localhost:27017/jpm_hrms)
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run      # Windows: $env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run

# frontend - http://localhost:5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

| Want to… | Set |
|---|---|
| Use MongoDB Atlas while developing | `MONGODB_URI=mongodb+srv://…/jpm_hrms` |
| Run without installing MongoDB | `EMBEDDED_MONGO=true` — starts a development-only MongoDB (official binaries, ~600 MB download once), data in `backend/data/mongo` |
| Load fictional sample data | `DEMO_MODE=true` (only into an empty database; every record is tagged `DEMO`) |
| Store files in MongoDB instead of a folder | `FILE_STORAGE=gridfs` |

Dev sign-in: `admin` / `Admin@12345` — change it after the first sign-in. Email sending is off in dev unless the
`MAIL_*` variables are set (previews still work). Browse the data with MongoDB Compass at `mongodb://localhost:27017`.

### Tests

```bash
cd backend && ./mvnw clean test   # 41 tests on a real MongoDB 7.0 started by the test run (never your database)
cd frontend && npm run build      # type-check + production build
```

They cover: security and roles (including the Interviewer's limits); login rate limiting; Google Sheet parsing and
the sync pipeline (Google API mocked); statuses with history, archive and filters; Profile Match Rating validation,
filters and sorting; every report and CSV / Excel export; file allow-list, content checks, malware-scan outcomes,
inline viewing, GridFS storage and Aadhaar encryption; ClamAV protocol (fake daemon); communication wording for every
message type, URL encoding, invalid numbers, real SMTP sending to an in-process server, failure and retry, template
versioning and history.

---

## Database (MongoDB)

| Collection | Contents |
|---|---|
| `candidates` | Applications (form fields, statuses, rating, archive flag) |
| `recruitment_status_history`, `profile_rating_history` | Who changed status / rating, when, from what to what |
| `interviews` (incl. interviewer feedback), `hr_remarks`, `candidate_documents` (file metadata only), `communications` | Linked to a candidate by `candidateId` |
| `departments`, `app_users` | Configuration and accounts (BCrypt password hashes only) |
| `message_templates`, `communication_settings` | Versioned templates per message type and channel; company identity |
| `integration_settings`, `sync_runs`, `sync_ignored_rows` | Google Sheets sync |
| `audit_logs` | Append-only audit trail |
| `fs.files`, `fs.chunks` | Uploaded files, only when `FILE_STORAGE=gridfs` |
| `counters` | Next numeric id per collection |

- **Ids** are plain numbers (`/candidates/65`) from `counters`; MongoDB ObjectIds are never exposed. Candidate codes
  are per-year serials (`2026-1`, `2026-2`, …).
- **Indexes** for search, filters and sorting, and unique usernames (case-insensitive), department names, candidate
  codes and sheet rows are created at startup by `config/MongoIndexes.java`.
- **Types:** dates without a time are stored as `"yyyy-MM-dd"`, decimals as Decimal128, timestamps in UTC.
- **Transactions are not used:** every write is a single-document write (atomic in MongoDB); concurrent edits of the
  same candidate are caught by optimistic locking.

**MongoDB Atlas setup, free-tier limits and the move to a paid cluster:** see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

### Migrating data from the previous SQL version (one time)

Start the backend once with the old database's JDBC URL; it copies every table into an **empty** MongoDB database,
keeping ids, candidate codes and history, and refuses to run if MongoDB already has users or candidates.

```bash
# from backend/, with the old backend stopped
LEGACY_JDBC_URL="jdbc:h2:file:./data/hrms-dev-pg;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r" \
LEGACY_JDBC_USERNAME=sa SPRING_PROFILES_ACTIVE=dev java -jar target/hrms-backend-1.0.0.jar
# PostgreSQL: LEGACY_JDBC_URL=jdbc:postgresql://host:5432/jpm_hrms LEGACY_JDBC_USERNAME=… LEGACY_JDBC_PASSWORD=…
```

Check the log for `Legacy import: <table> -> N documents`, then remove the `LEGACY_*` variables.

---

## Backups and recovery

The scripts in `scripts/` use the free [MongoDB Database Tools](https://www.mongodb.com/try/download/database-tools)
(`mongodump`, `mongorestore`) and [mongosh](https://www.mongodb.com/try/download/shell). The connection string comes
from `MONGODB_URI` and is passed through a private temporary file, never on the command line.

| Script | Purpose |
|---|---|
| `backup-db.sh` / `backup-db.ps1` | `mongodump --archive --gzip` (includes GridFS files) + archive of `UPLOAD_DIR`; reads the archive back to check it; owner-only permissions; deletes backups older than `RETENTION_DAYS` (14) |
| `verify-restore.sh` / `verify-restore.ps1` | Restores the newest archive into a temporary database, compares key collection counts with the live database, drops it. **Run weekly.** |
| `restore-db.sh` | Restores an archive into the database in `MONGODB_URI` (stop the app first); optionally restores documents |

```bash
# daily backup (cron) - backup.env holds MONGODB_URI for the read-only backup user, readable by root only
30 1 * * *  . /etc/jpm-hrms/backup.env && BACKUP_DIR=/var/backups/jpm-hrms UPLOAD_DIR=/srv/jpm-hrms/uploads /opt/jpm-hrms/scripts/backup-db.sh
# weekly restore test (needs a user allowed to create/drop the temporary database)
0 3 * * 0   . /etc/jpm-hrms/verify.env && BACKUP_DIR=/var/backups/jpm-hrms /opt/jpm-hrms/scripts/verify-restore.sh
```

**Recovery:** stop the backend → `CONFIRM=yes MONGODB_URI=… scripts/restore-db.sh <archive> [uploads.tar.gz <parent-of-UPLOAD_DIR>]`
→ start the backend → sign in and check the latest candidates.

**Limits — read these.** Nothing here guarantees zero data loss: with daily backups, up to a day of changes can be
lost. Keep a second copy of backups **off the server** (another machine or private cloud storage). Keep
`DOCUMENT_ENCRYPTION_KEY` in a secrets manager — without it, encrypted Aadhaar files in a backup cannot be read.
**The Atlas Free tier has no backups of its own**; these scripts are then the only backup. Paid Atlas clusters add
automated snapshots and point-in-time recovery. Records are archived, not deleted, and the audit log plus the status
and rating histories keep the history of important changes.

---

## Connecting Google Forms & Sheets

1. **Create the form**: run the script in [`google-form/`](google-form/README.md), or create one with questions such
   as *Full Name, Date of Birth, Mobile Number, Email, Address, Qualification, Institution, Passing Year, Previous
   Company, Total Experience, Previous Designation, Skills, Expected Salary, Department, Reference Name, Reference
   Relationship, Reference Contact, Family Reference Name, Relationship with Family Member, Family Reference Contact
   Number, Upload Resume, Aadhaar Card Upload (optional), Educational Certificates*. Department answers must match
   the departments configured in the dashboard. Link the responses to a Google Sheet. Ask only for what recruitment
   needs; keep identity documents optional.
2. Enable the **Google Sheets API** and **Drive API** in a Google Cloud project.
3. Create a **service account** with a JSON key; store it on the server outside the repository.
4. **Share the sheet** (and the form's upload folder) with the service account as **Viewer**. Never make it public.
5. Set `GOOGLE_SERVICE_ACCOUNT_FILE` and restart.
6. In *Settings → Google Sheets integration*: enter the spreadsheet ID and tab, **Test connection**, then **Sync now**.

Sync never creates duplicates; invalid rows are skipped and reported once; unreadable values are left empty.

---

## What has been verified, and what needs your accounts

| Item | Status |
|---|---|
| All dashboard, candidate, pipeline, interview, report, communication, file and admin workflows | 41 automated backend tests on a real MongoDB 7.0, plus every API area checked against a MongoDB 8.3 server with the migrated data and with 64 demo records; frontend type-checks and builds |
| Interviewer role limits | Tested (HR pages refused, other interviewers' interviews return "not found", no contact details or rating exposed) |
| MongoDB Atlas | **Not tested with a real Atlas cluster** (no account here). Only standard MongoDB features are used; create the Free cluster, set `MONGODB_URI` and sign in to confirm. |
| ClamAV scanning | Protocol tested against a fake daemon; **not tested with a real ClamAV** — run one and upload a file to confirm |
| Email | Tested against a local SMTP test server. **Configure your provider and send a test email** before relying on it. |
| WhatsApp click-to-chat | URL format and encoding tested; sending happens in the HR user's WhatsApp, delivery is not tracked |
| Google Sheets sync | Pipeline tested with a mocked Google API; an earlier live sync worked. **Needs `GOOGLE_SERVICE_ACCOUNT_FILE`** to run again. |
| Backup → restore | `.sh` scripts tested against a MongoDB 8.3 server. The PowerShell `.ps1` versions are **not tested** yet. |
| Browser UI | Built and type-checked; the new screens (pipeline, interviewer page, viewer, template types) have **not been clicked through in a browser** in this round |

## Privacy notes

- Only data the form collects is stored; missing fields are never fabricated.
- Aadhaar copies need HR-confirmed consent, are encrypted at rest, audited, and can be deleted after verification.
  The Aadhaar number is not stored and no UIDAI verification is performed. Bank details are not collected.
- Contact details, ratings and messages are visible only to signed-in HR users; Interviewers see a limited summary.
- Exports exclude date of birth, address, salary, internal rating notes and credentials. Exports, views, downloads
  and every communication action are audited.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/change-password` |
| Dashboard, pipeline | `GET /api/dashboard`, `GET /api/pipeline` |
| Candidates | `GET /api/candidates`, `GET /api/candidates/export?format=xlsx\|csv`, `GET/PUT /api/candidates/{id}`, `PATCH /api/candidates/{id}/{status,rating,archive}`, `POST /api/candidates/{id}/remarks`, `POST /api/candidates/{id}/documents`, `GET /api/candidates/{id}/resume[?inline=true]`, `GET /api/documents/{id}/download[?inline=true]`, `DELETE /api/documents/{id}` |
| Communication | `GET /api/candidates/{id}/compose?purpose=…`, `POST /api/candidates/{id}/communications/{whatsapp,email,call}`, `GET /api/candidates/{id}/communications`, `PATCH /api/communications/{id}/{mark-sent,call-outcome}`, `POST /api/communications/{id}/retry` |
| Templates (HR Admin) | `GET/PUT /api/communication/settings`, `GET/POST /api/communication/templates`, `GET /api/communication/templates/history`, `POST /api/communication/templates/preview`, `POST /api/communication/test-email` |
| Interviews | `GET/POST /api/interviews`, `PUT /api/interviews/{id}`, `PATCH /api/interviews/{id}/status`, `GET /api/interviewers` |
| Interviewer | `GET /api/my/interviews`, `GET /api/my/interviews/{id}`, `GET /api/my/interviews/{id}/resume`, `POST /api/my/interviews/{id}/feedback` |
| Departments, reports | `GET/POST /api/departments`, `PUT/DELETE /api/departments/{id}`, `GET /api/reports/{type}`, `GET /api/reports/{type}/export?format=xlsx\|csv` |
| Sync, admin | `/api/sync/*`, `GET/POST/PUT /api/users`, `GET /api/audit-logs`, `DELETE /api/admin/demo-data` |
