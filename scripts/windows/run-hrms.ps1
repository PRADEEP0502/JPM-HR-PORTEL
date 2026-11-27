<#
  Runs the JPM HR Portal server and starts it again if it stops (crash, update).
  Started by the "JPM HR Portal" scheduled task when Windows starts (see setup-hrms-pc.ps1).
  Settings come from config\application.properties next to app.jar; logs go to logs\.
#>
param([string]$Dir = 'C:\JPM-HRMS')

Set-Location $Dir
New-Item -ItemType Directory -Force (Join-Path $Dir 'logs') | Out-Null

while ($true) {
    $log = Join-Path $Dir ("logs\server-{0:yyyy-MM-dd}.log" -f (Get-Date))
    "==== $(Get-Date -Format s) starting server" | Out-File -Append -Encoding utf8 $log
    # 1 GB heap is plenty for the portal; the server listens on 127.0.0.1:8080 (only Tailscale Funnel reaches it)
    & java -Xmx1g -XX:+UseSerialGC -jar (Join-Path $Dir 'app.jar') *>> $log
    "==== $(Get-Date -Format s) server stopped (exit $LASTEXITCODE), restarting in 15 s" | Out-File -Append -Encoding utf8 $log
    # keep only the last 14 days of logs
    Get-ChildItem (Join-Path $Dir 'logs') -Filter 'server-*.log' | Where-Object LastWriteTime -lt (Get-Date).AddDays(-14) | Remove-Item -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 15
}
