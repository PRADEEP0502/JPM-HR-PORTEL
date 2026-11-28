<#
  Makes the office PC install new backend versions by itself every night.
  Run once as Administrator from this folder:
      powershell -ExecutionPolicy Bypass -File .\install-auto-update.ps1
  Registers the scheduled task "JPM HR Portal Auto Update" (default 02:00, and when the PC is switched on later if it
  was off at that time). Remove it with:  Unregister-ScheduledTask -TaskName 'JPM HR Portal Auto Update' -Confirm:$false
#>
param(
    [string]$Dir = 'C:\JPM-HRMS',
    [string]$At = '02:00'
)
$ErrorActionPreference = 'Stop'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Right-click PowerShell, choose "Run as administrator", and run this script again.' }
if (-not (Test-Path (Join-Path $Dir 'app.jar'))) { throw "The server is not installed in $Dir yet. Run setup-hrms-pc.ps1 first." }

Copy-Item -Force (Join-Path $PSScriptRoot 'update-hrms.ps1'), (Join-Path $PSScriptRoot 'auto-update-hrms.ps1') $Dir

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$Dir\auto-update-hrms.ps1`" -Dir `"$Dir`""
$trigger = New-ScheduledTaskTrigger -Daily -At $At
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest
Register-ScheduledTask -TaskName 'JPM HR Portal Auto Update' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null

Write-Host "Done. The PC now checks for a new server every night at $At and installs it by itself." -ForegroundColor Green
Write-Host 'Checking now (nothing is changed by this check):'
& (Join-Path $Dir 'auto-update-hrms.ps1') -Dir $Dir -DryRun
Write-Host "The log is in $Dir\logs\update.log"
