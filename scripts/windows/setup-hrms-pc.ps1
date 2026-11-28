<#
  One-time setup of the JPM HR Portal server on an office Windows PC.
  Run as Administrator from the folder that holds this script:
      powershell -ExecutionPolicy Bypass -File .\setup-hrms-pc.ps1
  It: checks Java 25, creates C:\JPM-HRMS, downloads the server, creates the settings file (you fill it in once),
  and registers a scheduled task that starts the server whenever Windows starts, even before anyone signs in.
  Running it again is safe: the settings file is never overwritten.
#>
param([string]$Dir = 'C:\JPM-HRMS')
$ErrorActionPreference = 'Stop'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Right-click PowerShell, choose "Run as administrator", and run this script again.' }

# 1. Java 25
# java -version prints to stderr; Windows PowerShell 5.1 turns that into an error under 'Stop', so ask through cmd.exe
$javaOk = $false
try { $javaOk = ((& cmd.exe /c 'java -version 2>&1') -join ' ') -match 'version "25' } catch { }
if (-not $javaOk) {
    Write-Host 'Java 25 is not installed. Install it with this command, then open a NEW PowerShell window and run this script again:' -ForegroundColor Yellow
    Write-Host '    winget install --id EclipseAdoptium.Temurin.25.JRE -e'
    Write-Host '(Or download "Temurin 25 JRE, Windows x64 .msi" from https://adoptium.net and tick "Set JAVA_HOME" and "Add to PATH".)'
    exit 1
}

# 2. Folder and scripts
New-Item -ItemType Directory -Force $Dir, (Join-Path $Dir 'config'), (Join-Path $Dir 'logs') | Out-Null
Copy-Item -Force (Join-Path $PSScriptRoot 'run-hrms.ps1'), (Join-Path $PSScriptRoot 'update-hrms.ps1') $Dir

# 3. Server
& (Join-Path $Dir 'update-hrms.ps1') -Dir $Dir -NoRestart

# 4. Settings (secrets) - created once, readable by Administrators and SYSTEM only
$config = Join-Path $Dir 'config\application.properties'
if (-not (Test-Path $config)) {
    @'
# JPM HR Portal - settings for this PC. Keep this file private: it holds the database password and keys.
# Copy the first three values EXACTLY from Render -> JPM-HR-PORTEL -> Environment.
# DOCUMENT_ENCRYPTION_KEY must be the same as on Render, or uploaded Aadhaar files cannot be opened.
MONGODB_URI=
JWT_SECRET=
DOCUMENT_ENCRYPTION_KEY=
CORS_ALLOWED_ORIGINS=https://jpm-hr-portel.vercel.app
FILE_STORAGE=gridfs
APP_TIMEZONE=Asia/Kolkata
DEMO_MODE=false
# Only this PC can reach the server directly; the internet reaches it through Tailscale Funnel.
server.port=8080
server.address=127.0.0.1
'@ | Set-Content -Encoding ascii $config
    & icacls $config /inheritance:r /grant:r 'Administrators:F' 'SYSTEM:F' | Out-Null
    Write-Host "Notepad opens now. Paste the three values after the = signs, save (Ctrl+S) and close Notepad." -ForegroundColor Yellow
    Start-Process notepad $config
    # Notepad on Windows 11 does not tell us when it is closed, so wait for Enter instead of Start-Process -Wait
    [void](Read-Host 'When you have saved the file and closed Notepad, press Enter here')
}
$text = Get-Content $config -Raw
foreach ($key in 'MONGODB_URI', 'JWT_SECRET', 'DOCUMENT_ENCRYPTION_KEY') {
    if ($text -notmatch "(?m)^$key=\S+") { throw "$key is empty in $config. Fill it in and run this script again." }
}

# 5. Start with Windows, as SYSTEM, restart the task if it ever ends
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$Dir\run-hrms.ps1`" -Dir `"$Dir`""
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest
Register-ScheduledTask -TaskName 'JPM HR Portal' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName 'JPM HR Portal'

Write-Host 'Starting the server (about a minute)...'
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 3
    try {
        $health = Invoke-WebRequest 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 5
        if ([Text.Encoding]::UTF8.GetString([byte[]]$health.RawContentStream.ToArray()) -match 'UP') {
            Write-Host 'Done. The server is running and will start by itself whenever Windows starts.' -ForegroundColor Green
            Write-Host 'Next: set up Tailscale Funnel (docs\SELF-HOSTING-WINDOWS.md, step 4).'
            exit 0
        }
    } catch { }
}
Write-Host "The server did not answer yet. Check the newest file in $Dir\logs (a wrong MONGODB_URI is the usual cause)." -ForegroundColor Yellow
