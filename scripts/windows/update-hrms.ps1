<#
  Downloads the latest JPM HR Portal server (built by GitHub from main) and restarts it.
  Run as Administrator:   powershell -ExecutionPolicy Bypass -File C:\JPM-HRMS\update-hrms.ps1
#>
param(
    [string]$Dir = 'C:\JPM-HRMS',
    [switch]$NoRestart
)
$ErrorActionPreference = 'Stop'
$base = 'https://github.com/PRADEEP0502/JPM-HR-PORTEL/releases/download/backend-latest'
$new = Join-Path $Dir 'app.new.jar'

Write-Host 'Downloading the latest server...'
Invoke-WebRequest "$base/hrms-backend.jar" -OutFile $new -UseBasicParsing
if ((Get-Item $new).Length -lt 10MB) { throw 'The download looks incomplete. Try again.' }
# GitHub serves .txt assets as binary, so Windows PowerShell 5.1 gives bytes: read the raw stream as text
function Read-Text($url) {
    $r = Invoke-WebRequest $url -UseBasicParsing
    ([Text.Encoding]::UTF8.GetString([byte[]]$r.RawContentStream.ToArray())).Trim()
}
$version = Read-Text "$base/hrms-backend.version.txt"

if (-not $NoRestart) {
    Write-Host 'Stopping the server...'
    $jar = (Join-Path $Dir 'app.jar').ToLower()
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.ToLower().Contains($jar) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 3
}
Move-Item -Force $new (Join-Path $Dir 'app.jar')
Set-Content -Encoding ascii (Join-Path $Dir 'version.txt') $version
Write-Host "Installed version $($version.Substring(0, 7))."

if (-not $NoRestart) {
    # run-hrms.ps1 starts it again within 15 seconds; make sure the task itself is running
    if ((Get-ScheduledTask -TaskName 'JPM HR Portal').State -ne 'Running') { Start-ScheduledTask -TaskName 'JPM HR Portal' }
    Write-Host 'Waiting for the server to start (about a minute)...'
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        try {
            if ((Read-Text 'http://127.0.0.1:8080/actuator/health') -match 'UP') {
                Write-Host 'The server is running.' -ForegroundColor Green
                exit 0
            }
        } catch { }
    }
    Write-Host "The server did not answer yet. Check the newest file in $Dir\logs." -ForegroundColor Yellow
}
