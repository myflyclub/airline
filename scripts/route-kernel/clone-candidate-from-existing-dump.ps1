param(
    [Parameter(Mandatory = $true)]
    [string]$DumpPath,

    [Parameter(Mandatory = $true)]
    [string]$BaselineSchema,

    [string]$CandidateSchema = "",

    [string]$Stamp = (Get-Date -Format "yyyyMMdd_HHmmss"),

    [string]$MySqlBin = "C:\Program Files (x86)\MySQL\mysql-5.7.44-winx64\bin",

    [string]$HostName = "127.0.0.1",

    [int]$Port = 3307,

    [string]$User = "root"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$candidate = if ([string]::IsNullOrWhiteSpace($CandidateSchema)) {
    "airline_perf_candidate_$Stamp"
} else {
    $CandidateSchema
}

& (Join-Path $PSScriptRoot "backup-and-clone.ps1") `
    -MySqlBin $MySqlBin `
    -HostName $HostName `
    -Port $Port `
    -User $User `
    -Stamp $Stamp `
    -DumpPath $DumpPath `
    -SkipDump `
    -SkipBaseline `
    -BaselineSchema $BaselineSchema `
    -CandidateSchema $candidate
