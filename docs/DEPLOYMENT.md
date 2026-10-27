# Deployment – free start, then production

The application is portable: everything provider-specific is an environment variable. Start on free tiers for
development and testing, then move each piece to a paid service by changing configuration only.

> **Free tiers change.** Providers change or withdraw free plans, and limits differ by region and account. Check each
> provider's current terms before relying on them. Nothing here promises that a service stays free.

## What runs where

| Piece | What it needs | Free-start options (examples) | Production |
|---|---|---|---|
| **Database** | MongoDB 6+ reachable from the backend | MongoDB Atlas **Free** cluster | Atlas paid dedicated cluster, or self-hosted replica set |
| **Backend** | Java 21+, ~512 MB RAM, outbound HTTPS (Google, SMTP) | A small always-on VM or container host with a free allowance | Paid VM / container service with HTTPS and monitoring |
| **Frontend** | Static files (`frontend/dist`) | Any static host / CDN with a free plan, or the backend's own nginx | Same, behind your domain and HTTPS |
| **Files** | Persistent private storage | `FILE_STORAGE=gridfs` (inside MongoDB) | Persistent disk with backups, GridFS on a paid cluster, or a private cloud bucket (implement `FileStorage`) |
| **Email** | An SMTP account | Google Workspace / Gmail with an App Password (low daily sending limits) | A transactional email provider with a verified domain |
| **Malware scanning** | A ClamAV daemon (needs ~1–2 GB RAM for signatures) | Usually too large for free hosts → leave `MALWARE_SCAN_MODE=off` while testing with non-sensitive files | ClamAV container next to the backend, `MALWARE_SCAN_MODE=clamav` |

### Constraints of typical free hosts

- **Services that sleep** when idle start slowly (the first request after a pause can take a while) and stop the
  scheduled Google Sheets sync while asleep. Use *Sync now* after waking, or an always-on host.
- **Temporary disks** are wiped on restart/redeploy. Never use `FILE_STORAGE=local` there — use `FILE_STORAGE=gridfs`
  so uploaded files live in MongoDB (and are included in `mongodump` backups).
- **One instance only:** login rate limits and the "sync running" flag are kept in memory per instance.
- **Atlas Free:** small fixed storage on shared hardware, limited connections, **no cloud backups** (use the backup
  scripts), and idle clusters can be paused. Files in GridFS count towards the storage limit.

## Step by step: free start

1. **Database – MongoDB Atlas Free**
   1. Create a **Free** cluster in a region near your users (e.g. Mumbai).
   2. *Database Access*: user `jpm_hrms_app`, generated password, role **readWrite on `jpm_hrms` only**.
      A second user `jpm_hrms_backup` with the **backup** (read-only) role for backups.
   3. *Network Access*: allow only your backend's public IP. If the host has no fixed IP you may have to allow a
      wider range — then use a long random password and rotate it.
   4. *Connect → Drivers*: copy the `mongodb+srv://…` string, insert the password, add `/jpm_hrms` before `?`.
2. **Backend**
   ```bash
   cd backend && ./mvnw -B clean package -DskipTests      # produces target/hrms-backend-1.0.0.jar
   java -jar target/hrms-backend-1.0.0.jar                # with the environment below
   ```
   Minimum environment (see `.env.example` for everything):
   ```
   MONGODB_URI=mongodb+srv://jpm_hrms_app:<password>@<cluster>/jpm_hrms?retryWrites=true&w=majority
   JWT_SECRET=<openssl rand -base64 48>
   DOCUMENT_ENCRYPTION_KEY=<openssl rand -base64 32>      # keep a copy in a password manager
   BOOTSTRAP_ADMIN_USERNAME=admin
   BOOTSTRAP_ADMIN_PASSWORD=<strong temporary password>   # remove after first sign-in
   CORS_ALLOWED_ORIGINS=https://<your-frontend-host>
   FILE_STORAGE=gridfs
   ```
   Add `GOOGLE_SERVICE_ACCOUNT_FILE` (mount the key file as a secret file) and `MAIL_*` when ready.
3. **Frontend**
   - Same host as the backend (recommended): serve `frontend/dist` with nginx and proxy `/api` (see README → nginx).
   - Separate static host: build with `VITE_API_BASE_URL=https://<backend-host>` (it is public — no secrets in
     `VITE_*`), and set `CORS_ALLOWED_ORIGINS` on the backend to the frontend's exact origin.
4. **First sign-in:** sign in as the bootstrap admin, change the password (*Settings → My profile*), remove
   `BOOTSTRAP_ADMIN_PASSWORD`, create the HR and Interviewer accounts.
5. **Backups:** schedule `scripts/backup-db.sh` daily from a machine you control (it only needs network access to
   Atlas) and `verify-restore.sh` weekly. On Atlas Free these are your only backups.

## Moving to production (paid) – no code changes

| Change | How |
|---|---|
| Database → paid Atlas cluster | Create the cluster, restore the latest backup into it (`scripts/restore-db.sh`) or use Atlas live migration, change `MONGODB_URI`, restart. Enable cloud backups / point-in-time recovery. |
| Self-hosted MongoDB | Run a **replica set** (3 members) with authentication and TLS, restrict network access, change `MONGODB_URI`. |
| Files → persistent disk | `FILE_STORAGE=local`, `UPLOAD_DIR=/srv/jpm-hrms/uploads` on a persistent, backed-up volume. Older GridFS files stay readable (each document remembers its storage). |
| Files → private cloud bucket | Implement `backend/src/main/java/com/jpm/hrms/document/storage/FileStorage` (e.g. an S3-compatible client with a private bucket and server-side encryption), add a value to `CandidateDocument.Storage`, select it in `FileStorages`. Business code does not change. |
| Malware scanning | Run ClamAV (`clamav/clamav` image), set `MALWARE_SCAN_MODE=clamav`, `CLAMAV_HOST`, keep `MALWARE_SCAN_FAIL_CLOSED=true`. |
| Email | Point `MAIL_*` at a transactional provider with SPF / DKIM for your domain; send a test email. |
| Hosting | Any Java host; put it behind HTTPS (nginx or a load balancer). Keep a single instance unless you move rate limiting to a shared store. |
| WhatsApp automatic sending | Requires the paid WhatsApp Business Platform — **ask before adding** (not implemented). |

Production checklist: [SECURITY.md](SECURITY.md). Problems: [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
