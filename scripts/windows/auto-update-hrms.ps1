<#
  Installs the newest JPM HR Portal server when GitHub has one. Run every night by the "JPM HR Portal Auto Update"
  scheduled task (see install-auto-update.ps1), or by hand:
      powershell -ExecutionPolicy Bypass -File C:\JPM-HRMS\auto-update-hrms.ps1 [-DryRun]
  If the new server does not start, the previous one is put back. Everything is written to logs\update.log.
#>
param(
    [string]$Dir = 'C:\JPM-HRMS',
    [string]$HealthUrl = 'http://127.0.0.1:8080/actuator/health',
    [int]$HealthWaitSeconds = 180,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$base = 'https://github.com/PRADEEP0502/JPM-HR-PORTEL/releases/download/backend-latest'
$log = Join-Path $Dir 'logs\update.log'

function Write-Log($message) {
    New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
    "$(Get-Date -Format s)  $message" | Out-File -Append -Encoding utf8 $log
    Write-Host $message
}
function Read-Text($url) {
    $r = Invoke-WebRequest $url -UseBasicParsing
    ([Text.Encoding]::UTF8.GetString([byte[]]$r.RawContentStream.ToArray())).Trim()
}
function Short($v) { if ($v) { $v.Substring(0, [Math]::Min(7, $v.Length)) } else { 'none' } }

try {
    $latest = Read-Text "$base/hrms-backend.version.txt"
    $versionFile = Join-Path $Dir 'version.txt'
    $installed = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { '' }

    if ($latest -eq $installed) {
        Write-Log "Up to date (version $(Short $installed))."
        exit 0
    }
    if ($DryRun) {
        Write-Log "Update available: installed $(Short $installed), newest $(Short $latest). Dry run, nothing changed."
        exit 0
    }

    Write-Log "Updating from $(Short $installed) to $(Short $latest)..."
    & (Join-Path $Dir 'update-hrms.ps1') -Dir $Dir -HealthUrl $HealthUrl -HealthWaitSeconds $HealthWaitSeconds
    if ($LASTEXITCODE -eq 0) {
        Write-Log "Updated: version $(Short $latest) is running."
        exit 0
    }

    # the new server did not come up: go back to the one that was working
    Write-Log 'The new server did not start. Going back to the previous version...'
    $prev = Join-Path $Dir 'app.prev.jar'
    if (-not (Test-Path $prev)) { Write-Log 'There is no previous version to go back to. Check the newest file in logs.'; exit 1 }
    $jar = (Join-Path $Dir 'app.jar').ToLower()
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.ToLower().Contains($jar) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 3
    Copy-Item -Force $prev (Join-Path $Dir 'app.jar')
    if (Test-Path (Join-Path $Dir 'version.prev.txt')) { Copy-Item -Force (Join-Path $Dir 'version.prev.txt') $versionFile }
    $task = Get-ScheduledTask -TaskName 'JPM HR Portal' -ErrorAction SilentlyContinue
    if ($task -and $task.State -ne 'Running') { Start-ScheduledTask -TaskName 'JPM HR Portal' }
    Write-Log "Went back to version $(Short $installed). The bad version $(Short $latest) will be tried again tomorrow."
    exit 1
} catch {
    Write-Log "Update check failed: $($_.Exception.Message)"
    exit 1
}
