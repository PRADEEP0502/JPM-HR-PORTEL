# Run the backend on an office Windows PC (Tailscale Funnel)

This guide moves the HR portal's backend from Render to an office Windows PC.

- **The website stays on Vercel and the database stays on MongoDB Atlas.** Only the server part moves.
- **Tailscale Funnel** gives the PC a free, fixed HTTPS address (`https://<pc-name>.<tailnet>.ts.net`). You don't need a
  domain, router changes or a fixed IP.
- **Render stays as a backup.** You can switch back by changing one line in `frontend/vercel.json`.

```
HR staff → jpm-hr-portel.vercel.app → https://<pc>.<tailnet>.ts.net (Tailscale Funnel) → office PC :8080 → MongoDB Atlas
Google Form script ───────────────────↗
```

## What the PC needs

- **Windows 10 or 11, 4 GB RAM or more.** It must stay on all the time; a UPS is strongly recommended.
- **Steady internet.**
- **No sleep:** go to Settings → System → Power, and set **Sleep: Never** and **Turn off screen** to anything you like.
- **Restarts at night only:** go to Settings → Windows Update → Advanced options → **Active hours** and set 8:00 to 22:00,
  so update restarts happen at night. The server starts again by itself after a restart.

## 1. Install Java 25 (once)

Open **PowerShell as administrator** (Start → type PowerShell → right-click → Run as administrator):

```powershell
winget install --id EclipseAdoptium.Temurin.25.JRE -e
```

If `winget` can't find it, download **Temurin 25, JRE, Windows x64, .msi** from https://adoptium.net and install it. Tick
**Add to PATH** and **Set JAVA_HOME**. Then close PowerShell and open a new one as administrator.

## 2. Get the setup scripts

1. Download the scripts:
   https://github.com/PRADEEP0502/JPM-HR-PORTEL/archive/refs/heads/main.zip
2. Unzip the file.
3. Open the folder `scripts\windows`.

## 3. Install the server

1. Have the Render settings ready: **Render → JPM-HR-PORTEL → Environment**. You need the values of `MONGODB_URI`,
   `JWT_SECRET` and `DOCUMENT_ENCRYPTION_KEY`.
   - ⚠️ `DOCUMENT_ENCRYPTION_KEY` must be **exactly** the same as on Render. Otherwise Aadhaar files that were already
     uploaded can't be opened.
2. In the administrator PowerShell, go to the folder and run the setup:

   ```powershell
   cd "<unzipped folder>\scripts\windows"
   powershell -ExecutionPolicy Bypass -File .\setup-hrms-pc.ps1
   ```

3. Notepad opens with `C:\JPM-HRMS\config\application.properties`. Paste the three values after the `=` signs, save,
   and close Notepad.
4. The script then:
   - downloads the server
   - registers the **"JPM HR Portal"** task, which starts the server whenever Windows starts, even before anyone signs in
   - waits until the server says it is running

5. Check it yourself: open http://127.0.0.1:8080/actuator/health in a browser on the PC. It should show `{"status":"UP"}`.

**MongoDB Atlas:** the database must accept connections from the PC. In **Atlas → Network Access**, check the list:
- if it contains `0.0.0.0/0`, you're fine
- otherwise, add the PC's internet address

## 4. Give the PC a public address with Tailscale Funnel

1. Install Tailscale:

   ```powershell
   winget install --id Tailscale.Tailscale -e
   ```

   Sign in from the Tailscale icon in the taskbar. Use the company Google account if you can.
2. In the Tailscale icon menu → **Preferences**, turn on **Run unattended**. This keeps Tailscale working after a restart
   when nobody has signed in to Windows.
3. Turn on the address (administrator PowerShell):

   ```powershell
   tailscale funnel --bg 8080
   ```

   The first time, it shows a link to **enable HTTPS and Funnel** for your account. Open the link, approve it, and run
   the same command again. It then prints the public address, for example
   `https://hr-pc.tail1234.ts.net`. The `--bg` setting is remembered after restarts.
4. Check it from your phone, using mobile data rather than the office Wi-Fi: open `https://<that address>/actuator/health`.
   It should show `{"status":"UP"}`.
5. **Send the address to the developer.** Two places then change to point at it:
   - the website: `frontend/vercel.json`
   - the Google Form script: `DASHBOARD_URL`

   After the change, paste the updated Google Form script into Apps Script. Keep your `FORM_KEY`.

## 5. Keep Render as a backup

Once the portal works through the PC, open **Render → JPM-HR-PORTEL → Settings → Suspend Web Service**. This stops two
servers from running the same scheduled jobs, such as the Google Sheets sync. To use Render again, click **Resume** and
point `frontend/vercel.json` (and `DASHBOARD_URL` in the Google Form script) back to `https://jpm-hr-portel.onrender.com`.

## Updating the server by itself (every night)

Run this once in an administrator PowerShell, from the `scripts\windows` folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\install-auto-update.ps1
```

It registers the task **"JPM HR Portal Auto Update"**. Every night at 02:00 (or when the PC is switched on, if it was off
then) the PC checks whether GitHub has a newer server file and installs it. If the new server does not start within
3 minutes, the previous version is put back and the new one is tried again the next night. Everything is written to
`C:\JPM-HRMS\logs\update.log`. Website changes (Vercel) need nothing on the PC.

Remove it with `Unregister-ScheduledTask -TaskName 'JPM HR Portal Auto Update' -Confirm:$false`. If antivirus ever
removes `auto-update-hrms.ps1` from `C:\JPM-HRMS`, run `install-auto-update.ps1` again.

## Updating the server by hand

GitHub builds a new server file whenever backend code changes. It is the **backend-latest** release. To install it right
away, run this in an administrator PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File C:\JPM-HRMS\update-hrms.ps1
```

The update takes about a minute, and the portal is unavailable during that time. `C:\JPM-HRMS\version.txt` shows which
version is installed.

## If something goes wrong

| Problem | Check |
|---|---|
| The portal shows "server error" | Is the PC on, and is the internet working? Does http://127.0.0.1:8080/actuator/health on the PC show UP? |
| Health check is not UP | Look at the newest file in `C:\JPM-HRMS\logs`. A wrong `MONGODB_URI` or an Atlas Network Access block is the usual cause. |
| Works on the PC but not from a phone | Run `tailscale funnel status`. Is Tailscale signed in, and is **Run unattended** on? |
| Restart the server by hand | Task Scheduler → **JPM HR Portal** → End, then Run. Or restart the PC. |

Backups work as before: `scripts\backup-db.ps1` copies the Atlas database, wherever the server runs.
