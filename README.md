# JPM HRMS – Recruitment & Candidate Management Dashboard

Internal recruitment dashboard for **JPM – Junior Processing Mill**. Candidates apply through a
**Google Form**. Responses land in a **Google Sheet**, and the backend **synchronizes** them into
**PostgreSQL** through the Google Sheets API. HR staff manage candidates and interviews in a dark,
responsive dashboard, and contact candidates by **phone, WhatsApp and email** using approved templates.

```
Candidate ──► Google Form ──► Google Sheet (private)
                                   │  Sheets API (service account, read-only, server-side only)
                                   ▼
React dashboard ◄── REST + JWT ──► Spring Boot API ──► PostgreSQL
                                   │
                                   ├──► SMTP provider (interview emails)
                                   └──► wa.me click-to-chat link (HR sends from WhatsApp)
```

| Layer    | Stack |
|----------|-------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Lucide icons, Recharts, TanStack Query |
| Backend  | Spring Boot 3.5 (Java 21+), Spring Security (JWT, BCrypt), Spring Data JPA, Flyway, Spring Mail, Apache POI |
| Database | PostgreSQL 14+ (H2 in PostgreSQL mode for local dev; tests run on a real embedded PostgreSQL) |
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
- **Demo mode**: 64 clearly tagged `DEMO` sample candidates in an empty database, with a banner on every page.

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
├── scripts/                     # PostgreSQL backup / restore / restore-verification
├── backend/                     # Spring Boot API  (./mvnw)
│   └── src/main/
│       ├── java/com/jpm/hrms/
│       │   ├── auth/ user/ audit/        # authentication, users, audit trail
│       │   ├── candidate/ document/      # candidates, remarks, files (Aadhaar encryption)
│       │   ├── interview/ department/    # scheduling, departments
│       │   ├── communication/            # templates, rendering, WhatsApp/email/call, history
│       │   ├── dashboard/ report/        # statistics, reports, Excel
│       │   ├── sync/                     # Google Sheets integration
│       │   └── notification/ demo/ config/ common/
│       └── resources/
│           ├── application.yml           # production defaults (env-driven)
│           ├── application-dev.yml       # H2 (PostgreSQL mode) + demo mode
│           └── db/migration/             # Flyway schema V1–V4 (PostgreSQL)
└── frontend/                    # React app  (npm)
    └── src/{pages,components,lib}
```

---

## Local development

**Prerequisites:** JDK 21+ (tested on JDK 25), Node.js 20+. Maven is not needed (`mvnw` is included),
and PostgreSQL is optional for local work.

```bash
# backend (H2 in PostgreSQL mode + demo data) - http://localhost:8080
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run          # Windows: $env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run

# frontend - http://localhost:5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Demo accounts (dev only): `admin` / `Admin@12345` and `hr` / `Hr@12345`. To start fresh, stop the
backend and delete `backend/data/`. Email sending is off in dev unless you set the `MAIL_*` variables;
previews still work.

### Tests

```bash
cd backend && ./mvnw test      # 30 tests on a real embedded PostgreSQL 14 (binaries download via Maven)
cd frontend && npm run build   # type-check + production build
```

Backend tests cover:
- Security and roles.
- Google Sheet parsing and the full sync pipeline (Google API mocked).
- Aadhaar encryption.
- Communication: exact template wording, venue, date and time, URL encoding, invalid mobile numbers,
  missing email, real SMTP sending to an in-process GreenMail server, SMTP failure with retry,
  template versioning, history preservation, and access control.

### PostgreSQL

```sql
CREATE USER jpm_hrms WITH PASSWORD 'a-strong-password';
CREATE DATABASE jpm_hrms OWNER jpm_hrms;
```

Run without the `dev` profile, setting `DB_URL=jdbc:postgresql://<host>:5432/jpm_hrms`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET`, `DOCUMENT_ENCRYPTION_KEY` and the `BOOTSTRAP_ADMIN_*` variables. Flyway
creates and upgrades the schema automatically.

---

## Backups and recovery

Scripts in `scripts/` use the standard PostgreSQL client tools and the libpq variables `PGHOST`,
`PGPORT`, `PGUSER`, `PGDATABASE` and `PGPASSWORD` (or `~/.pgpass`):

| Script | Purpose |
|---|---|
| `backup-db.sh` / `backup-db.ps1` | `pg_dump -Fc` + archive of `UPLOAD_DIR`; checks the dump is readable; owner-only permissions; deletes backups older than `RETENTION_DAYS` (14) |
| `verify-restore.sh` / `verify-restore.ps1` | Restores the newest dump into a temporary database, compares key table row counts and schema version with the live database, then drops it. **Run weekly.** |
| `restore-db.sh` | Restores a dump into a target database (stop the app first); optionally restores documents |

```bash
# daily backup (cron)
30 1 * * *  PGUSER=jpm_hrms PGDATABASE=jpm_hrms BACKUP_DIR=/var/backups/jpm-hrms UPLOAD_DIR=/srv/jpm-hrms/uploads /opt/jpm-hrms/scripts/backup-db.sh
# weekly restore test
0 3 * * 0   PGUSER=jpm_hrms PGDATABASE=jpm_hrms BACKUP_DIR=/var/backups/jpm-hrms /opt/jpm-hrms/scripts/verify-restore.sh
```

Store backups off the server, and keep `DOCUMENT_ENCRYPTION_KEY` in a secrets manager. Without it,
encrypted Aadhaar files in a backup cannot be read.

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

**Required in production:** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (startup fails
without it), `DOCUMENT_ENCRYPTION_KEY`, `CORS_ALLOWED_ORIGINS` and `UPLOAD_DIR`. Add `MAIL_*` for email.

**Checklist:**
- HTTPS only, with `DEMO_MODE=false`. Remove any demo rows under *Settings → Demo data*.
- Change the bootstrap admin password, then unset `BOOTSTRAP_ADMIN_PASSWORD`.
- Schedule `backup-db.sh` daily and `verify-restore.sh` weekly.
- Keep service-account keys and SMTP passwords out of version control.

---

## What has been verified, and what needs your accounts

| Item | Status |
|---|---|
| All dashboard, candidate, interview, report, communication and admin workflows | Tested end to end in a browser and through 30 automated backend tests |
| PostgreSQL schema and queries | Tested on a real PostgreSQL 14 server (embedded) and a standalone PostgreSQL 14 instance |
| Email sending, failure handling and retry | Tested against a local SMTP test server (GreenMail). **Configure your real SMTP provider and send a test email** before relying on it. |
| WhatsApp click-to-chat | URL format and encoding tested. Actual sending happens in the HR user's WhatsApp, and delivery is not tracked. |
| WhatsApp Business Platform (automatic sending) | Not implemented; needs a Meta business setup |
| Google Sheets sync | Pipeline tested with a mocked Google API. **Needs your service account and a real sheet** for a live test. |
| Backup → restore | Scripts tested against a standalone PostgreSQL 14 server (see Backups) |

## Privacy notes
- Only data the form collects is stored, and missing fields are never fabricated.
- **Aadhaar copies** need HR-confirmed candidate consent, are AES-256-GCM encrypted at rest, are
  audited, and can be deleted after verification. The Aadhaar number is not stored, and no UIDAI
  verification is performed. Bank details are not collected.
- Mobile numbers, emails and message content are only available to authenticated HR and admin users.
  Communication records cannot be deleted through the app, and the database blocks deleting a candidate
  who has communication history.
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
