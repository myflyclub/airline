param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,

    [string[]]$Tables = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Schema.Equals("airline", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to produce a route-kernel experiment checksum against the default airline schema."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$airlineData = Join-Path $repoRoot "airline-data"
$logDir = Join-Path $repoRoot "logs\route-kernel"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logPath = Join-Path $logDir "checksum_${Schema}_$stamp.log"

$previousDbName = $env:DB_NAME

try {
    $env:DB_NAME = $Schema
    $tableArgs = $Tables -join " "
    $command = if ($tableArgs.Length -gt 0) {
        "runMain com.patson.tools.RouteKernelChecksumReport $tableArgs"
    } else {
        "runMain com.patson.tools.RouteKernelChecksumReport"
    }

    Push-Location $airlineData
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            java -jar ..\sbt-launch.jar $command 2>&1 |
                Tee-Object -FilePath $logPath
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            throw "RouteKernelChecksumReport failed with exit code $exitCode"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:DB_NAME = $previousDbName
}

Write-Host "log=$logPath"
