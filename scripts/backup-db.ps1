# JPM HRMS - PostgreSQL backup for Windows servers (same behaviour as backup-db.sh).
# Uses the standard libpq environment variables: PGHOST, PGPORT, PGUSER, PGDATABASE, PGPASSWORD.
# Optional: BACKUP_DIR (default .\backups), UPLOAD_DIR, RETENTION_DAYS (default 14).
# Schedule with Task Scheduler, e.g. daily:  powershell -File C:\jpm-hrms\scripts\backup-db.ps1
$ErrorActionPreference = 'Stop'

if (-not $env:PGDATABASE) { throw 'Set PGDATABASE (e.g. jpm_hrms)' }
$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { '.\backups' }
$retention = if ($env:RETENTION_DAYS) { [int]$env:RETENTION_DAYS } else { 14 }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$dump = Join-Path $backupDir "jpm-hrms-db-$stamp.dump"
Write-Output "Backing up database '$($env:PGDATABASE)' to $dump"
& pg_dump --format=custom --compress=9 --no-owner --no-privileges --file=$dump $env:PGDATABASE
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }

& pg_restore --list $dump | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Dump $dump is not readable" }
Write-Output ("  dump OK: {0:N1} MB" -f ((Get-Item $dump).Length / 1MB))

if ($env:UPLOAD_DIR -and (Test-Path $env:UPLOAD_DIR)) {
    $zip = Join-Path $backupDir "jpm-hrms-uploads-$stamp.zip"
    Compress-Archive -Path $env:UPLOAD_DIR -DestinationPath $zip
    Write-Output "  documents archived: $zip"
    Write-Output '  NOTE: encrypted documents also need DOCUMENT_ENCRYPTION_KEY - keep it in your secrets manager.'
}

Get-ChildItem $backupDir -Filter 'jpm-hrms-*' -File |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$retention) } |
    ForEach-Object { Write-Output "  removed old backup: $($_.Name)"; Remove-Item $_.FullName -Confirm:$false }
Write-Output 'Backup finished'
