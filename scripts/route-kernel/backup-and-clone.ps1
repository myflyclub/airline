param(
    [string]$MySqlBin = "C:\Program Files (x86)\MySQL\mysql-5.7.44-winx64\bin",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3307,
    [string]$User = "root",
    [string]$SourceSchema = "airline",
    [string]$Stamp = (Get-Date -Format "yyyyMMdd_HHmmss"),
    [string]$DumpPath = "",
    [switch]$SkipDump,
    [switch]$SkipBaseline,
    [string]$BaselineSchema = "",
    [string]$CandidateSchema = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backupDir = Join-Path $repoRoot "db_backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$mysql = Join-Path $MySqlBin "mysql.exe"
$mysqldump = Join-Path $MySqlBin "mysqldump.exe"
if (!(Test-Path $mysql)) { throw "mysql.exe not found at $mysql" }
if (!$SkipDump.IsPresent -and !(Test-Path $mysqldump)) { throw "mysqldump.exe not found at $mysqldump" }

function Assert-SchemaName {
    param(
        [string]$Name,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Name)) {
        throw "$Label schema name is empty."
    }
    if ($Name.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to create or reuse a clone named airline."
    }
    if ($Name -notmatch '^[A-Za-z0-9_]+$') {
        throw "$Label schema name contains unsupported characters: $Name"
    }
}

function Test-SchemaExists {
    param([string]$Schema)

    $exists = & $mysql `
        --host=$HostName --port=$Port --user=$User `
        --batch --raw --skip-column-names `
        --execute="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$Schema';"

    return ![string]::IsNullOrWhiteSpace($exists)
}

$baseline = if ([string]::IsNullOrWhiteSpace($BaselineSchema)) { "airline_perf_baseline_$Stamp" } else { $BaselineSchema }
$candidate = if ([string]::IsNullOrWhiteSpace($CandidateSchema)) { "airline_perf_candidate_$Stamp" } else { $CandidateSchema }
Assert-SchemaName $baseline "Baseline"
Assert-SchemaName $candidate "Candidate"

if ($baseline.Equals($candidate, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Baseline and candidate schema names must be different."
}

$dump = if ([string]::IsNullOrWhiteSpace($DumpPath)) {
    Join-Path $backupDir "airline_pre_route_kernel_$Stamp.sql"
} else {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DumpPath)
}

if ($SkipDump.IsPresent) {
    if ([string]::IsNullOrWhiteSpace($DumpPath)) {
        throw "-SkipDump requires -DumpPath."
    }
    if (!(Test-Path $dump)) {
        throw "Dump path does not exist: $dump"
    }
} else {
    if ([string]::IsNullOrWhiteSpace($SourceSchema)) {
        throw "Source schema is empty."
    }
    if ($SourceSchema -notmatch '^[A-Za-z0-9_]+$') {
        throw "Source schema contains unsupported characters: $SourceSchema"
    }

    & $mysqldump `
        --host=$HostName --port=$Port --user=$User `
        --single-transaction --routines --triggers --events `
        --result-file=$dump `
        $SourceSchema

    if (!(Test-Path $dump)) { throw "Backup was not created at $dump" }
}

if ($SkipBaseline.IsPresent) {
    if (!(Test-SchemaExists $baseline)) {
        throw "Baseline schema does not exist for -SkipBaseline: $baseline"
    }
} else {
    if (Test-SchemaExists $baseline) {
        throw "Baseline schema already exists: $baseline"
    }

    & $mysql `
        --host=$HostName --port=$Port --user=$User `
        --execute="CREATE DATABASE $baseline CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    cmd /c "`"$mysql`" --host=$HostName --port=$Port --user=$User $baseline < `"$dump`""
    if ($LASTEXITCODE -ne 0) { throw "Import failed for $baseline" }
}

if (Test-SchemaExists $candidate) {
    throw "Candidate schema already exists: $candidate"
}

& $mysql `
    --host=$HostName --port=$Port --user=$User `
    --execute="CREATE DATABASE $candidate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

cmd /c "`"$mysql`" --host=$HostName --port=$Port --user=$User $candidate < `"$dump`""
if ($LASTEXITCODE -ne 0) { throw "Import failed for $candidate" }

foreach ($schema in @($baseline, $candidate)) {
    & $mysql `
        --host=$HostName --port=$Port --user=$User `
        --batch --raw $schema `
        --execute="SELECT DATABASE(); SELECT cycle FROM cycle; SELECT COUNT(*) AS link_count FROM link;"
}

Write-Host "backup=$dump"
Write-Host "baseline=$baseline"
Write-Host "candidate=$candidate"
