# JPM HRMS – Recruitment & Candidate Management Dashboard

Internal recruitment dashboard for **JPM – Junior Processing Mill**. Candidates apply through a
**Google Form**. Responses land in a **Google Sheet**, and the backend **synchronizes** them into
**MongoDB** through the Google Sheets API. HR staff manage candidates and interviews in a dark,
responsive dashboard, and contact candidates by **phone, WhatsApp and email** using approved templates.

```
Candidate ──► Google Form ──► Google Sheet (private)
                                   │  Sheets API (service account, read-only, server-side only)
                                   ▼
React dashboard ◄── REST + JWT ──► Spring Boot API ──► MongoDB (Atlas or self-hosted)
                                   │
                                   ├──► SMTP provider (interview emails)
                                   └──► wa.me click-to-chat link (HR sends from WhatsApp)
```

| Layer    | Stack |
|----------|-------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Lucide icons, Recharts, TanStack Query |
| Backend  | Spring Boot 3.5 (Java 21+), Spring Security (JWT, BCrypt), Spring Data MongoDB, Spring Mail, Apache POI |
| Database | MongoDB 6+ (MongoDB Atlas, or a self-hosted server; tests run on a real embedded MongoDB 7.0) |
| Google   | Service-account auth (`google-auth-library`), Sheets v4 + Drive v3 REST, read-only scopes |

---

## Features

- **Dashboard**: six live summary cards, pipeline cards that open the full filtered list, today's and
  upcoming interviews, department rail, weekly applications, and five interactive charts. Every number
  is computed from the database.
- **Candidates**: search; filters for department, experience, application status, interview status and
  verification; sortable columns; pagination; Excel export. Each row has **Call · WhatsApp · Email ·
  View Profile** actions, plus a menu for edit, status and resume. Contact buttons are disabled, with
  the reason shown, when the mobile number or email is missing or invalid.
- **Candidate profile**: personal, education, employment and application details, professional and
  family references, documents (Resume / Aadhaar / Certificates / Others), HR remarks, the
  **communication history** and an activity timeline. Only fields present in the source data are shown.
- **Interview scheduling**: pick a candidate. Name, email, mobile and department fill in automatically.
  Choose the date and time; the venue is fixed to the configured default (*JPM – Junior Processing
  Mill*). After saving, a **review step** shows the interview details with the WhatsApp and email
  previews before anything is sent. Includes clash detection, rescheduling, completion and cancellation.
- **Communication** (see below): approved, versioned WhatsApp and email templates with automatic
  placeholder filling, previews, sending, and an immutable history.
- **Departments, Reports** (six reports with Excel export), **Google Forms sync** (scheduled or manual,
  duplicate-safe).
- **Security**: JWT auth, ADMIN/HR roles on every endpoint, login throttling, an audit log, encrypted
  Aadhaar copies, and secrets only in environment variables.
- **Demo mode** (off by default; `DEMO_MODE=true`): 64 clearly tagged `DEMO` sample candidates in an empty database, with a banner on every page.

### Roles

| Capability | ADMIN | HR |
|---|:-:|:-:|
| Dashboard, candidates, profiles, status updates, remarks, documents | ✓ | ✓ |
| Interview scheduling, call / WhatsApp / email candidates, communication history | ✓ | ✓ |
| Reports and Excel exports | ✓ | ✓ |
| Manage communication templates, company name, venue, HR signature; send test email | ✓ | |
| Configure Google Sheets integration, run sync, see sync errors | ✓ | |
| Manage users and departments, view audit log, remove demo data | ✓ | |
| Candidates | Apply through the Google Form only; no dashboard access | |

---

## Candidate communication

### Templates
Both templates use the approved wording. `{{CompanyName}}`, `{{Venue}}` and `{{HRSignature}}` come from
*Settings → Communication templates*, so WhatsApp and email always show the same company name and venue.

```
Dear {{CandidateName}},

Greetings from {{CompanyName}}!

Your interview has been scheduled for the {{Department}} department.

Interview Details            (bold in WhatsApp: *Interview Details*)

Date: {{InterviewDate}}
Time: {{InterviewTime}}
Venue: {{Venue}}
Department: {{Department}}

Please confirm your availability for the interview.

Best regards,
{{HRSignature}}              (default: "HR Department" + "JPM – Junior Processing Mill")
```

The email subject is **Interview Invitation – JPM**.

- **Allowed placeholders:** `CandidateName`, `Department`, `InterviewDate`, `InterviewTime`, `Venue`,
  `CompanyName`, `HRSignature`, `InterviewRound`. Unknown or malformed placeholders are rejected, and
  `CandidateName`, `InterviewDate` and `InterviewTime` are required.
- **No half-filled messages:** if any value is missing (for example no department, or no upcoming
  interview), nothing can be sent and the reason is shown. A message never leaves with an unresolved
  `{{…}}`.
- **Versioning:** templates can only be changed by an admin. Every save creates a **new version**; old
  versions are kept. Each communication record stores the template version and the exact text that was
  used, so history is never rewritten when a template or candidate's details change.

### Channels and honest statuses

| Channel | How it works | Statuses recorded |
|---|---|---|
| **Call** | Logs the call and opens the `tel:` link; HR then records the outcome (connected, no answer, busy…) | Call started (+ outcome, notes) |
| **WhatsApp** | Builds the message, validates the number with its country code, and opens `https://wa.me/<number>?text=<URL-encoded message>`. **HR reviews and presses send in WhatsApp.** | **Prepared**. If HR confirms, **Sent (HR confirmed)**. Delivery is never claimed. |
| **Email** | Sent from the backend through the configured SMTP provider | **Accepted by provider** (with Message-ID) or **Failed** (with error and a **Retry** that keeps the failed record). SMTP does not report inbox delivery, so *Delivered* is never shown. |

**Phone validation:** 10-digit numbers get the default country code (91). Indian mobiles must be 10
digits starting with 6–9, and international numbers must be 11–15 digits. **Email** addresses are
validated before the Email action is enabled. The server re-validates every action.

### Email setup (requires credentials)
Set these on the server and restart (see `.env.example`):

| Provider | `MAIL_HOST` | `MAIL_PORT` | Notes |
|---|---|---|---|
| Gmail / Google Workspace | `smtp.gmail.com` | 587 | Use an **App Password** (2-Step Verification required) |
| Microsoft 365 | `smtp.office365.com` | 587 | SMTP AUTH must be enabled for the mailbox |
| Amazon SES / SendGrid / others | provider host | 587 | Verified sender/domain required |

`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` (the sender address) and `MAIL_FROM_NAME` complete the
setup. Then use **Settings → Communication templates → Send test email**. Credentials are never sent
to the browser, and recipient addresses are not written to the logs.

### WhatsApp
Click-to-chat needs no credentials or account setup. Automatic sending would need the **WhatsApp
Business Platform (Cloud API)**: a verified Meta Business account, a registered sender number,
pre-approved message templates, recorded candidate opt-in, and a webhook for delivery status. It is
**not implemented**. The communication model (statuses, provider message ID, template versions) is
ready for it.

---

## Project structure

```
HRMS/
├── .env.example                 # backend environment variables (no secrets)
├── scripts/                     # MongoDB backup / restore / restore-verification
├── backend/                     # Spring Boot API  (./mvnw)
│   └── src/main/
│       ├── java/com/jpm/hrms/
│       │   ├── auth/ user/ audit/        # authentication, users, audit trail
│       │   ├── candidate/ document/      # candidates, remarks, files (Aadhaar encryption)
│       │   ├── interview/ department/    # scheduling, departments
│       │   ├── communication/            # templates, rendering, WhatsApp/email/call, history
│       │   ├── dashboard/ report/        # statistics, reports, Excel
│       │   ├── sync/                     # Google Sheets integration
│       │   ├── config/                   # MongoDB mapping, indexes, embedded dev server, legacy importer
│       │   └── notification/ demo/ common/
│       └── resources/
│           ├── application.yml           # production defaults (env-driven)
│           └── application-dev.yml       # local MongoDB, dev-only defaults
└── frontend/                    # React app  (npm)
    └── src/{pages,components,lib}
```

---

## Local development

**Prerequisites:** JDK 21+ (tested on JDK 25), Node.js 20+, and **MongoDB** — either the free MongoDB Community
Server on your machine (Windows installs it as the "MongoDB" service on `localhost:27017`) or a free MongoDB Atlas
cluster. Maven is not needed (`mvnw` is included).

```bash
# backend - http://localhost:8080 (uses mongodb://localhost:27017/jpm_hrms)
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run          # Windows: $env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run

# frontend - http://localhost:5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

| Want to… | Set |
|---|---|
| Use MongoDB Atlas while developing | `MONGODB_URI=mongodb+srv://…/jpm_hrms` |
| Run without installing MongoDB at all | `EMBEDDED_MONGO=true` — the app starts a development-only MongoDB (official binaries, ~600 MB download once) with data in `backend/data/mongo` |
| Load fictional sample data | `DEMO_MODE=true` (only into an empty database; every record is tagged `DEMO`) |

Dev sign-in: `admin` / `Admin@12345` (change it after first sign-in). Set `DEMO_MODE=true` to load sample
candidates (and an `hr` / `Hr@12345` account) into an empty database. Email sending is off in dev unless you set the
`MAIL_*` variables; previews still work. Browse the data with MongoDB Compass at `mongodb://localhost:27017`.

### Tests

```bash
cd backend && ./mvnw test      # 31 tests on a real MongoDB 7.0 server started by the test run (never your database)
cd frontend && npm run build   # type-check + production build
```

Backend tests cover:
- Security and roles.
- Google Sheet parsing and the full sync pipeline (Google API mocked).
- Aadhaar encryption.
- Profile Match Rating: validation (whole numbers 1–10 only), filters, sorting with unrated candidates last, audit.
- Communication: exact template wording, venue, date and time, URL encoding, invalid mobile numbers,
  missing email, real SMTP sending to an in-process GreenMail server, SMTP failure with retry,
  template versioning, history preservation, and access control.

---

## Database (MongoDB)

### Collections

| Collection | Contents |
|---|---|
| `candidates` | Applications (Google Form fields, statuses, Profile Match Rating, references) |
| `interviews`, `hr_remarks`, `candidate_documents`, `communications` | Linked to a candidate by `candidateId` |
| `departments`, `app_users` | Configuration and HR accounts (BCrypt password hashes only) |
| `message_templates`, `communication_settings` | Versioned WhatsApp / email templates, company name and venue |
| `integration_settings`, `sync_runs`, `sync_ignored_rows` | Google Sheets sync |
| `audit_logs` | Append-only audit trail |
| `counters` | Next numeric id per collection |

- **Ids** are plain numbers (`/candidates/65`) taken from `counters`; MongoDB ObjectIds are never exposed.
- **Indexes** (search, filters, sorting, unique usernames / department names / candidate codes / sheet rows) are
  created at startup by `config/MongoIndexes.java`. Unique usernames and department names ignore letter case.
- **Types:** dates without a time are stored as `"yyyy-MM-dd"` text, decimals as Decimal128, timestamps in UTC.
- **Files are not stored in MongoDB.** Only their metadata is; the files live in `UPLOAD_DIR` (encrypted when
  `DOCUMENT_ENCRYPTION_KEY` is set) or stay in Google Drive for Google Form uploads.
- **Transactions are not used.** Every write is a single-document write (atomic in MongoDB); concurrent edits to the
  same candidate are caught by optimistic locking (`version`).

### MongoDB Atlas setup (free tier, step by step)

1. Create an account at <https://www.mongodb.com/cloud/atlas> and a **Free** cluster in a region close to you
   (e.g. Mumbai). No card is needed for the free tier.
2. **Database Access → Add user:** `jpm_hrms_app`, a generated password, role *readWrite* on database `jpm_hrms`
   only. Create a second user `jpm_hrms_backup` with the *backup* role for the backup scripts.
3. **Network Access:** add only the public IP of the server that runs the backend. Avoid `0.0.0.0/0`.
4. **Connect → Drivers:** copy the `mongodb+srv://…` string, insert the password, and add `/jpm_hrms` before `?`.
5. Set `MONGODB_URI` on the backend server (environment variable or secrets manager — never in Git) and restart.
   Indexes are created automatically.

**Free tier limits (check the current terms):** MongoDB's
[Free cluster limitations](https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/) page says that
**backups cannot be enabled on Free clusters** (use `mongodump`/`mongorestore`, i.e. the scripts below), that there
is one Free cluster per project with a fixed size on shared hardware, at most 500 connections, and that idle Free
clusters may be paused or deactivated under the Atlas terms. Storage is small (commonly quoted as 512 MB — plenty
for thousands of candidates, because files are stored outside MongoDB). Free tiers are not guaranteed to stay free
or unchanged. For production, move to a paid dedicated cluster for automated cloud backups, point-in-time recovery
and guaranteed resources: change `MONGODB_URI` only — no code changes.

### Migrating existing data to MongoDB (one time)

The previous version stored data in H2 (dev) or PostgreSQL. To copy it into an **empty** MongoDB database, start the
backend once with the old database's JDBC URL. Ids, candidate codes and history are kept; the old database is only
read. The importer refuses to run if MongoDB already has users or candidates.

```bash
# from backend/, with the old backend stopped
LEGACY_JDBC_URL="jdbc:h2:file:./data/hrms-dev-pg;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r" \
LEGACY_JDBC_USERNAME=sa SPRING_PROFILES_ACTIVE=dev java -jar target/hrms-backend-1.0.0.jar
# PostgreSQL: LEGACY_JDBC_URL=jdbc:postgresql://host:5432/jpm_hrms LEGACY_JDBC_USERNAME=… LEGACY_JDBC_PASSWORD=…
```

Check the log for `Legacy import: <table> -> N documents`, then remove the `LEGACY_*` variables and restart.

---

## Backups and recovery

The scripts in `scripts/` use the free [MongoDB Database Tools](https://www.mongodb.com/try/download/database-tools)
(`mongodump`, `mongorestore`) and [mongosh](https://www.mongodb.com/try/download/shell). They read the connection
string from `MONGODB_URI` and pass it to the tools through a private temporary file, never on the command line.

| Script | Purpose |
|---|---|
| `backup-db.sh` / `backup-db.ps1` | `mongodump --archive --gzip` + archive of `UPLOAD_DIR`; reads the whole archive back (`--dryRun`) to check it; owner-only permissions; deletes backups older than `RETENTION_DAYS` (14) |
| `verify-restore.sh` / `verify-restore.ps1` | Restores the newest archive into a temporary database, compares document counts of the key collections with the live database, then drops it. **Run weekly.** |
| `restore-db.sh` | Restores an archive into the database in `MONGODB_URI` (replaces its collections; stop the app first); optionally restores documents |

```bash
# daily backup (cron) - backup.env holds MONGODB_URI for the read-only backup user, readable by root only
30 1 * * *  . /etc/jpm-hrms/backup.env && BACKUP_DIR=/var/backups/jpm-hrms UPLOAD_DIR=/srv/jpm-hrms/uploads /opt/jpm-hrms/scripts/backup-db.sh
# weekly restore test (needs a user that may create/drop the temporary database)
0 3 * * 0   . /etc/jpm-hrms/verify.env && BACKUP_DIR=/var/backups/jpm-hrms /opt/jpm-hrms/scripts/verify-restore.sh
```

**Recovery steps:** stop the backend → `CONFIRM=yes MONGODB_URI=… scripts/restore-db.sh <archive> [uploads.tar.gz <parent-of-UPLOAD_DIR>]`
→ start the backend → sign in and check the latest candidates.

**Limits — read these:** a daily backup means up to a day of changes can be lost; nothing here guarantees zero
data loss. Copy backups off the server (a different machine or private cloud storage). Keep
`DOCUMENT_ENCRYPTION_KEY` in a secrets manager: without it, encrypted Aadhaar files in a backup cannot be read.
On the Atlas Free tier these scripts are the *only* backup; paid Atlas clusters add automated snapshots.
Routine record management uses statuses (e.g. Rejected, On Hold), not deletion; the audit log keeps the history
of important candidate changes.

---

## Connecting Google Forms & Sheets

1. **Create the form**: run the ready-made script in [`google-form/`](google-form/README.md) (creates the form + response sheet in your Google account), or create one manually with questions such as *Full Name, Date of Birth, Mobile Number, Email, Address,
   Qualification, Institution, Passing Year, Previous Company, Total Experience, Previous Designation,
   Skills, Expected Salary, Department, Reference Name, Reference Relationship, Reference Contact,
   Family Reference Name, Relationship with Family Member, Family Reference Contact Number, Upload
   Resume, Aadhaar Card Upload (optional), Educational Certificates*. Department answers must match the
   departments configured in the dashboard. Link the responses to a Google Sheet.
2. Enable the **Google Sheets API** (and the **Drive API** for file uploads) in a Google Cloud project.
3. Create a **service account** with a JSON key, and store it on the server outside the repository.
4. **Share the sheet** (and the form's upload folder) with the service account as **Viewer**. Never
   make it public.
5. Set `GOOGLE_SERVICE_ACCOUNT_FILE` and restart.
6. In *Settings → Google Sheets integration*: enter the spreadsheet ID and tab name, then **Test
   connection**, then **Sync now**. Optionally turn on automatic sync.

Sync is idempotent and never creates duplicates. Invalid rows are skipped and reported once, and
unreadable values are left empty rather than guessed.

---

## Deployment

```bash
cd backend && ./mvnw -B clean package && java -jar target/hrms-backend-1.0.0.jar   # with env vars set
cd frontend && npm ci && npm run build                                              # serve dist/
```

Serve `dist/` and reverse-proxy `/api` to the backend on the same origin:

```nginx
server {
  listen 443 ssl;
  server_name hrms.example.com;
  root /var/www/jpm-hrms/dist;
  client_max_body_size 11m;
  location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto $scheme; }
  location / { try_files $uri /index.html; }
}
```

**Required in production:** `MONGODB_URI`, `JWT_SECRET` (startup fails
without it), `DOCUMENT_ENCRYPTION_KEY`, `CORS_ALLOWED_ORIGINS` and `UPLOAD_DIR`. Add `MAIL_*` for email.

**Checklist:**
- HTTPS only, with `DEMO_MODE=false`. Remove any demo rows under *Settings → Demo data*.
- Change the bootstrap admin password, then unset `BOOTSTRAP_ADMIN_PASSWORD`.
- Schedule `backup-db.sh` daily and `verify-restore.sh` weekly, and copy backups off the server.
- MongoDB: app user with readWrite on `jpm_hrms` only, a separate read-only backup user, network access limited
  to the backend server, TLS (always on for Atlas).
- Keep service-account keys and SMTP passwords out of version control.

---

## What has been verified, and what needs your accounts

| Item | Status |
|---|---|
| All dashboard, candidate, interview, report, communication and admin workflows | 31 automated backend tests on a real MongoDB 7.0 server, plus every API area checked against a MongoDB 8.3 server with migrated data and with 64 demo records |
| MongoDB Atlas | Not tested with a real Atlas cluster (no account available here). The app uses only standard MongoDB features; **create the Free cluster, set `MONGODB_URI` and sign in** to confirm. |
| Data migration from the previous SQL database | Tested: the existing H2 dev database was imported (ids, candidate codes, ratings, settings, audit history kept) |
| Email sending, failure handling and retry | Tested against a local SMTP test server (GreenMail). **Configure your real SMTP provider and send a test email** before relying on it. |
| WhatsApp click-to-chat | URL format and encoding tested. Actual sending happens in the HR user's WhatsApp, and delivery is not tracked. |
| WhatsApp Business Platform (automatic sending) | Not implemented; needs a Meta business setup |
| Google Sheets sync | Pipeline tested with a mocked Google API. **Needs your service account and a real sheet** for a live test. |
| Backup → restore | `backup-db.sh`, `verify-restore.sh` and `restore-db.sh` tested against a MongoDB 8.3 server. The PowerShell versions (`.ps1`) are not yet tested. |

## Privacy notes
- Only data the form collects is stored, and missing fields are never fabricated.
- **Aadhaar copies** need HR-confirmed candidate consent, are AES-256-GCM encrypted at rest, are
  audited, and can be deleted after verification. The Aadhaar number is not stored, and no UIDAI
  verification is performed. Bank details are not collected.
- Mobile numbers, emails and message content are only available to authenticated HR and admin users.
  Communication records cannot be deleted through the app, and the app has no way to delete a candidate
  (only fictional DEMO records can be purged).
- Excel exports exclude date of birth, address and salary. Exports, downloads and every communication
  action are audited.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/change-password` |
| Dashboard | `GET /api/dashboard` |
| Candidates | `GET /api/candidates`, `GET /api/candidates/export`, `GET/PUT /api/candidates/{id}`, `PATCH /api/candidates/{id}/status`, `POST /api/candidates/{id}/remarks`, `POST /api/candidates/{id}/documents`, `GET /api/candidates/{id}/resume`, `GET /api/documents/{id}/download`, `DELETE /api/documents/{id}` |
| Communication | `GET /api/candidates/{id}/compose`, `POST /api/candidates/{id}/communications/{whatsapp,email,call}`, `GET /api/candidates/{id}/communications`, `PATCH /api/communications/{id}/{mark-sent,call-outcome}`, `POST /api/communications/{id}/retry` |
| Templates (admin) | `GET/PUT /api/communication/settings`, `GET/POST /api/communication/templates`, `GET /api/communication/templates/history`, `POST /api/communication/templates/preview`, `POST /api/communication/test-email` |
| Interviews | `GET/POST /api/interviews`, `PUT /api/interviews/{id}`, `PATCH /api/interviews/{id}/status` |
| Departments, Reports | `GET/POST /api/departments`, `PUT/DELETE /api/departments/{id}`, `GET /api/reports/{type}`, `GET /api/reports/{type}/export` |
| Sync, Admin | `/api/sync/*`, `GET/POST/PUT /api/users`, `GET /api/audit-logs`, `DELETE /api/admin/demo-data` |
