# JPM HRMS - prove that a backup can be restored (Windows version of verify-restore.sh).
# Restores the newest (or given) archive into a temporary database on the same server, compares key document
# counts with the live database, then drops the temporary database.
#   powershell -File scripts\verify-restore.ps1 [path\to\jpm-hrms-db-....archive.gz]
# Requires mongorestore (MongoDB Database Tools) and mongosh on PATH. Settings: MONGODB_URI, BACKUP_DIR.
param([string]$Archive)
$ErrorActionPreference = 'Stop'

if (-not $env:MONGODB_URI) { throw 'Set MONGODB_URI (the live database to compare against)' }
$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { '.\backups' }
if (-not $Archive) {
    $Archive = (Get-ChildItem $backupDir -Filter 'jpm-hrms-db-*.archive.gz' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Archive -or -not (Test-Path $Archive)) { throw "No backup found in $backupDir" }

$liveDb = if ($env:MONGODB_URI -match '^[a-z+]+://[^/]+/([^?]+)') { $Matches[1] } else { 'jpm_hrms' }
$scratch = "jpm_hrms_restore_check_$PID"
$collections = 'candidates interviews communications message_templates hr_remarks candidate_documents app_users departments audit_logs counters'
$failed = $false
$config = New-TemporaryFile
try {
    Set-Content -Path $config -Value ('uri: "{0}"' -f $env:MONGODB_URI) -Encoding ascii
    Write-Output "Verifying $Archive using temporary database $scratch"
    & mongorestore "--config=$config" "--archive=$Archive" --gzip --quiet "--nsFrom=$liveDb.*" "--nsTo=$scratch.*"
    if ($LASTEXITCODE -ne 0) { throw 'mongorestore failed' }

    # mongosh reads the URI from the environment, never from the command line.
    $script = "const conn = connect(process.env.MONGODB_URI); for (const c of '$collections'.split(' ')) { print(c + ' ' + conn.getSiblingDB('$liveDb').getCollection(c).countDocuments() + ' ' + conn.getSiblingDB('$scratch').getCollection(c).countDocuments()); }"
    $rows = & mongosh --quiet --nodb --eval $script
    if ($LASTEXITCODE -ne 0) { throw 'mongosh failed' }
    foreach ($row in $rows) {
        $name, $live, $rest = $row -split ' '
        $live = [int]$live; $rest = [int]$rest
        $mark = 'ok'
        if ($rest -gt $live) { $mark = 'MORE THAN LIVE?'; $failed = $true }
        if ($rest -eq 0 -and $live -gt 0) { $mark = 'EMPTY - CHECK'; $failed = $true }
        Write-Output ('  {0,-22} live={1,-7} restored={2,-7} {3}' -f $name, $live, $rest, $mark)
    }
} finally {
    & mongosh --quiet --nodb --eval "connect(process.env.MONGODB_URI).getSiblingDB('$scratch').dropDatabase()" 2>$null | Out-Null
    Remove-Item $config -Force -ErrorAction SilentlyContinue
}
if ($failed) { Write-Error 'RESTORE CHECK FAILED'; exit 1 } else { Write-Output 'RESTORE CHECK PASSED' }
