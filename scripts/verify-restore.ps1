# JPM HRMS - prove that a backup can be restored (Windows version of verify-restore.sh).
# Restores the newest (or given) dump into a temporary database, compares key row counts with the
# live database, then drops the temporary database.
#   powershell -File scripts\verify-restore.ps1 [path\to\file.dump]
param([string]$Dump)
$ErrorActionPreference = 'Stop'

if (-not $env:PGDATABASE) { throw 'Set PGDATABASE (the live database to compare against)' }
$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { '.\backups' }
if (-not $Dump) {
    $Dump = (Get-ChildItem $backupDir -Filter 'jpm-hrms-db-*.dump' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Dump -or -not (Test-Path $Dump)) { throw "No backup found in $backupDir" }
$scratch = "jpm_hrms_restore_check_$PID"
$tables = 'candidates', 'interviews', 'communications', 'message_templates', 'hr_remarks', 'candidate_documents', 'app_users', 'departments', 'audit_logs'
$failed = $false
try {
    Write-Output "Verifying $Dump using temporary database $scratch"
    & createdb $scratch; if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
    & pg_restore --no-owner --no-privileges --exit-on-error --dbname=$scratch $Dump
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed' }
    foreach ($t in $tables) {
        $live = [int](& psql -d $env:PGDATABASE -tAc "SELECT count(*) FROM $t")
        $rest = [int](& psql -d $scratch -tAc "SELECT count(*) FROM $t")
        $mark = 'ok'
        if ($rest -gt $live) { $mark = 'MORE THAN LIVE?'; $failed = $true }
        if ($rest -eq 0 -and $live -gt 0) { $mark = 'EMPTY - CHECK'; $failed = $true }
        Write-Output ('  {0,-22} live={1,-7} restored={2,-7} {3}' -f $t, $live, $rest, $mark)
    }
} finally {
    & dropdb --if-exists $scratch 2>$null | Out-Null
}
if ($failed) { Write-Error 'RESTORE CHECK FAILED'; exit 1 } else { Write-Output 'RESTORE CHECK PASSED' }
