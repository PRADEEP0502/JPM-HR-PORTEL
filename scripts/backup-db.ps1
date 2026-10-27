# JPM HRMS - MongoDB backup for Windows servers (same behaviour as backup-db.sh).
# Requires the free MongoDB Database Tools (mongodump, mongorestore) on PATH:
#   https://www.mongodb.com/try/download/database-tools
# Settings: MONGODB_URI (required; use a read-only backup user), BACKUP_DIR (default .\backups), UPLOAD_DIR,
# RETENTION_DAYS (default 14).
# Schedule with Task Scheduler, e.g. daily:  powershell -File C:\jpm-hrms\scripts\backup-db.ps1
$ErrorActionPreference = 'Stop'

if (-not $env:MONGODB_URI) { throw 'Set MONGODB_URI (e.g. mongodb+srv://user:password@cluster/jpm_hrms)' }
$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { '.\backups' }
$retention = if ($env:RETENTION_DAYS) { [int]$env:RETENTION_DAYS } else { 14 }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

# The URI contains the password: pass it in a temporary config file, not on the command line.
$config = New-TemporaryFile
try {
    Set-Content -Path $config -Value ('uri: "{0}"' -f $env:MONGODB_URI) -Encoding ascii
    $archive = Join-Path $backupDir "jpm-hrms-db-$stamp.archive.gz"
    Write-Output "Backing up MongoDB to $archive"
    & mongodump "--config=$config" "--archive=$archive" --gzip --quiet
    if ($LASTEXITCODE -ne 0) { throw 'mongodump failed' }

    # A backup that cannot be read is not a backup: read the whole archive without writing anything.
    & mongorestore "--config=$config" "--archive=$archive" --gzip --dryRun --quiet
    if ($LASTEXITCODE -ne 0) { throw "Archive $archive is not readable" }
    Write-Output ("  archive OK: {0:N2} MB" -f ((Get-Item $archive).Length / 1MB))
} finally {
    Remove-Item $config -Force -ErrorAction SilentlyContinue
}

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
